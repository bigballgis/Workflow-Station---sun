<template>
  <div class="batch-designed-form" :style="{ paddingInline: `${(options?.row?.gutter ?? 0) / 2}px` }">
  <NativeForm :rule="runtimeRules" :option="runtimeOptions" locale="en" @mounted="mounted" @change="changed" />
  </div>
</template>
<script setup lang="ts">
import { computed, reactive, defineComponent, h, inject, provide, ref, shallowRef, watch, nextTick, type InjectionKey, type VNodeChild } from 'vue'
import { afterEvent } from '../formEvents/async'
import formCreate from '@form-create/element-ui'
import '@form-create/element-ui/src/style/index.css'
import DesignedFormTitle from './DesignedFormTitle.vue'
import { batchRuleChildren, batchLayoutAliases } from './batchFormRules'
import { isEmptyBatchEvent } from './controlContract'
import type { BatchEventContext } from './eventContext'
import { batchLookupComponentKey } from './lookupComponent'
import { formDateValueFormat } from '../formEvents/fieldValue'

const props = defineProps<{
  rules: Array<Record<string, any>>
  options?: Record<string, any>
  initialValues?: Record<string, unknown>
  readonlyField: (name: string) => boolean
  adaptedFields?: string[]
  eventContext?: BatchEventContext
}>()
const emit = defineEmits<{ (event: 'change', name: string, value: unknown): void }>()
const slots = defineSlots<{ assignment(props: { rule: Record<string, any> }): VNodeChild; field(props: { rule: Record<string, any>; value: unknown; update: (value: unknown) => void; events: Record<string, (...args: unknown[]) => void> }): VNodeChild }>()
const ready = ref(false)
const nativeApi = shallowRef<{ setValue(values: Record<string, unknown>): void; mergeRule(field: string, rule: Record<string, unknown>): void; fields(): string[]; el(field: string): HTMLElement | { focus?: () => void; $el?: HTMLElement } | undefined }>()
const eventContext = props.eventContext
let syncing = false
let syncGeneration = 0
const renderedState = new Map<string, string>()
watch(() => [eventContext?.version(), eventContext?.session(), eventContext?.pending?.()], syncSession, { flush: 'post' })
function mounted(api: NonNullable<typeof nativeApi.value>) { nativeApi.value = api; ready.value = true; syncSession() }
watch(() => props.rules, () => { renderedState.clear(); void nextTick(syncSession) })
function syncSession() {
  const session = eventContext?.session()
  const api = nativeApi.value
  if (!ready.value || !api || !session) return
  const ownFields = new Set(api.fields())
  const generation = ++syncGeneration
  syncing = true
  try {
    api.setValue(Object.fromEntries([...ownFields].map(field => [field, session.data[field]])))
    function visit(rules: Array<Record<string, any>>, inheritedReadonly = false) {
      for (const rule of rules) {
        const locked = inheritedReadonly || rule.disabled === true || rule.readonly === true || rule.props?.disabled === true || rule.props?.readonly === true
        if (ownFields.has(rule.field)) {
          const fieldProps = { ...rule.props, disabled: locked || props.readonlyField(rule.field) || session!.disabled.get(rule.field) === true }
          if (rule.type === 'lookup') fieldProps.reloadNonce = session!.refreshes.get(rule.field) ?? 0
          if (session!.lookupFilters.has(rule.field)) {
            const config = typeof fieldProps.lookupConfig === 'string' ? JSON.parse(fieldProps.lookupConfig) : fieldProps.lookupConfig
            if (config) fieldProps.lookupConfig = JSON.stringify({ ...config, filterConditions: [...(config.filterConditions ?? []), ...session!.lookupFilters.get(rule.field)!] })
          }
          const patch: Record<string, any> = { props: fieldProps, wrap: { ...rule.wrap, error: session!.errors.get(rule.field) },
            hidden: rule._batchModeHidden === true || session!.hidden.get(rule.field) === true,
            display: session!.display.get(rule.field) !== false,
            title: session!.labels.get(rule.field) ?? rule.title,
            info: session!.infos.has(rule.field) ? { info: session!.infos.get(rule.field), show: !!session!.infos.get(rule.field) } : rule.info,
          }
          patch.options = session!.choices.get(rule.field) ?? rule.options ?? rule.props?.options ?? []
          if (session!.required.has(rule.field)) {
            patch.validate = (rule.validate ?? []).filter((entry: Record<string, unknown>) => !entry.required)
            if (session!.required.get(rule.field)) patch.validate.push({ required: true })
          }
          if (props.adaptedFields?.includes(rule.field)) patch._batchOriginal = { ...rule, ...patch }
          const fingerprint = JSON.stringify(patch)
          if (renderedState.get(rule.field) !== fingerprint) {
            renderedState.set(rule.field, fingerprint)
            api!.mergeRule(rule.field, patch)
          }
        }
        visit(batchRuleChildren(rule), locked)
        for (const control of Array.isArray(rule.control) ? rule.control : []) if (Array.isArray(control.rule)) visit(control.rule, locked)
      }
    }
    visit(props.rules)
  } finally {
    // form-create emits value changes from queued watchers, after setValue returns.
    void nextTick(() => { if (generation === syncGeneration) syncing = false })
  }
  if (!eventContext?.pending?.() && session.focusField && ownFields.has(session.focusField)) {
    const field = session.focusField
    session.focusField = ''
    void nextTick(() => {
      const control = api.el(field)
      if (control && !(control instanceof HTMLElement) && typeof control.focus === 'function') control.focus()
      else {
        const root = control instanceof HTMLElement ? control : control?.$el
        const selector = 'input, textarea, select, button, [tabindex]'
        ;(root?.matches(selector) ? root : root?.querySelector<HTMLElement>(selector))?.focus()
      }
    })
  }
}
// An isolated instance keeps batch adapters out of DW's authoring canvas registry.
const NativeForm = formCreate.factory(true)
// Batch enforces designer/permission locks itself. DW's inherited authoring parser
// would promote a script's temporary props.disabled into a sticky rule.disabled.
for (const type of ['input', 'inputNumber', 'select', 'switch', 'datePicker']) {
  NativeForm.parser(type, { merge: true, mergeProp() {} })
}
const lookupComponent = inject(batchLookupComponentKey, null)
if (lookupComponent) NativeForm.component('batchLookup', lookupComponent)
const assignmentKey: InjectionKey<(rule: Record<string, any>) => VNodeChild> = Symbol('batch-assignment')
provide(assignmentKey, rule => slots.assignment?.({ rule }))
NativeForm.component('batchAssignment', defineComponent({
  props: ['formCreateInject'],
  inheritAttrs: false,
  setup(bridgeProps, { attrs }) {
    const renderAssignment = inject(assignmentKey)!
    return () => h('div', attrs, [renderAssignment(bridgeProps.formCreateInject.rule._batchOriginal)])
  },
}))
const fieldKey: InjectionKey<(args: any) => VNodeChild> = Symbol('batch-field')
provide(fieldKey, args => slots.field?.(args))
NativeForm.component('batchField', defineComponent({
  inheritAttrs: false,
  props: ['formCreateInject', 'modelValue'], emits: ['update:modelValue'],
  setup(bridgeProps, { emit: update, expose }) {
    const renderField = inject(fieldKey)!
    const root = ref<HTMLElement>()
    expose({ focus: () => root.value?.querySelector('input')?.focus() })
    return () => h('div', { ref: root, style: { width: '100%' } }, [renderField({ rule: bridgeProps.formCreateInject.rule._batchOriginal, value: bridgeProps.modelValue,
      update: (value: unknown) => update('update:modelValue', value), events: bridgeProps.formCreateInject.rule.on ?? {} })])
  },
}))
NativeForm.component('fcTitle', DesignedFormTitle)
NativeForm.component('batchText', defineComponent({
  props: ['content'], setup(textProps) { return () => h('p', String(textProps.content ?? '')) },
}))
NativeForm.componentAlias({ ...batchLayoutAliases, number: 'el-input-number',
  divider: 'el-divider', elDivider: 'el-divider', fcAlert: 'el-alert', alert: 'el-alert' })
const runtimeOptions = computed(() => ({
  ...Object.fromEntries(Object.entries(props.options ?? {}).filter(([key]) => !key.startsWith('on') && !key.startsWith('before'))),
  form: { ...props.options?.form, labelWidth: props.options?.form?.labelWidth ?? 'auto' },
  beforeFetch: async (config: unknown, data: unknown) => {
    const result = await eventContext?.beforeFetch(config, data)
    // The native global hook cancels only rejected promises, not a bare false.
    return result === false ? Promise.reject(false) : result
  },
  submitBtn: false, resetBtn: false,
}))
const runtimeRules = computed(() => {
  const rules = formCreate.copyRules(props.rules) as Array<Record<string, any>>
  function visit(items: Array<Record<string, any>>, inheritedReadonly = false) {
    for (const rule of items) {
      const original = { ...rule }
      const names = new Set([...Object.keys(rule.on ?? {}), ...Object.keys(rule._on ?? {})])
      delete rule.on; delete rule._on; delete rule.hook; delete rule._hook
      if (eventContext) rule.hook = Object.fromEntries(['hidden', 'deleted', 'titleClick', 'watch'].filter(name => !isEmptyBatchEvent(original.hook?.[name]) || !isEmptyBatchEvent(original._hook?.[name])).map(name => [name,
        (...args: unknown[]) => name === 'deleted' ? nextTick(() => eventContext.event(original, name, args, 'hook')) : eventContext.event(original, name, args, 'hook')]))
      if (eventContext) rule.on = Object.fromEntries([...names].filter(name => name !== 'change').map(name => [name,
        (...args: unknown[]) => eventContext.event(original, name, args)]))
      const locked = inheritedReadonly || rule.disabled === true || rule.readonly === true || rule.props?.disabled === true || rule.props?.readonly === true
      if (rule.type === 'miAssignment') {
        const original = { ...rule, props: { ...rule.props, disabled: locked } }
        rule.type = 'batchAssignment'; rule.input = false; rule._batchOriginal = original; delete rule.children
      } else {
        if (!rule.field) {
          if (rule.type === 'text') { rule.type = 'batchText'; rule.props = { ...rule.props, content: rule.props?.content ?? rule.title } }
          if (rule.type === 'title') { rule.type = 'fcTitle'; rule.props = { ...rule.props, title: rule.props?.title ?? rule.title } }
          if (rule.type === 'space' || rule.type === 'fcSpace') rule.type = 'div'
        }
        if (rule.field) {
          if (rule.type === 'lookup' && !props.adaptedFields?.includes(rule.field)) {
            if (!lookupComponent) throw new Error('Batch Lookup renderer is missing')
            rule.type = 'batchLookup'
          }
          if (rule.type === 'text') rule.type = 'input'
          if (rule.type === 'date' || rule.type === 'datetime') {
            rule.props = { type: rule.type, ...rule.props }; rule.type = 'datePicker'
          }
          if (rule.type === 'datePicker') rule.props = { ...rule.props, valueFormat: rule.props?.valueFormat ?? formDateValueFormat(rule.props?.type ?? 'date') }
          if (locked || props.readonlyField(rule.field)) rule.props = { ...rule.props, disabled: true }
          if (props.adaptedFields?.includes(rule.field)) {
            rule._batchOriginal = { ...rule }; rule.type = 'batchField'
          }
          if (Object.prototype.hasOwnProperty.call(props.initialValues ?? {}, rule.field)) rule.value = props.initialValues![rule.field]
        }
        visit(batchRuleChildren(rule), locked)
        for (const control of Array.isArray(rule.control) ? rule.control : []) {
          if (Array.isArray(control.rule)) visit(control.rule, locked)
        }
      }
    }
  }
  visit(rules)
  // Native field-value watchers require reactive rules for script backfills to reach the controls.
  return reactive(rules)
})
function changed(name: string, value: unknown, _rule: unknown, _api: unknown, _setFlag?: boolean, added?: boolean) {
  // Form initialization and mounting a conditional field are not user edits.
  if (!ready.value || syncing || added || props.readonlyField(name)) return
  if (eventContext) {
    if (JSON.stringify(eventContext.session()?.data[name]) === JSON.stringify(value)) return
    const result = eventContext.change(name, value)
    void afterEvent(result, accepted => { if (accepted) emit('change', name, value) })
    return
  }
  emit('change', name, value)
}
</script>
