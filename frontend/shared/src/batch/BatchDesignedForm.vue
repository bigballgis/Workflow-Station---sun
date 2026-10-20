<template>
  <div class="batch-designed-form" :style="{ paddingInline: `${(options?.row?.gutter ?? 0) / 2}px` }">
  <NativeForm :rule="runtimeRules" :option="runtimeOptions" locale="en" @mounted="ready = true" @change="changed" />
  </div>
</template>
<script setup lang="ts">
import { computed, defineComponent, h, inject, provide, ref, type InjectionKey, type VNodeChild } from 'vue'
import formCreate from '@form-create/element-ui'
import '@form-create/element-ui/src/style/index.css'
import DesignedFormTitle from './DesignedFormTitle.vue'
import { batchRuleChildren, batchLayoutAliases } from './batchFormRules'

const props = defineProps<{
  rules: Array<Record<string, any>>
  options?: Record<string, any>
  initialValues?: Record<string, unknown>
  readonlyField: (name: string) => boolean
  adaptedFields?: string[]
}>()
const emit = defineEmits<{ (event: 'change', name: string, value: unknown): void }>()
const slots = defineSlots<{ assignment(props: { rule: Record<string, any> }): VNodeChild; field(props: { rule: Record<string, any>; value: unknown; update: (value: unknown) => void }): VNodeChild }>()
const ready = ref(false)
// An isolated instance keeps batch adapters out of DW's authoring canvas registry.
const NativeForm = formCreate.factory(true)
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
  props: ['formCreateInject', 'modelValue'], emits: ['update:modelValue'],
  setup(bridgeProps, { emit: update }) {
    const renderField = inject(fieldKey)!
    return () => renderField({ rule: bridgeProps.formCreateInject.rule._batchOriginal, value: bridgeProps.modelValue,
      update: (value: unknown) => update('update:modelValue', value) })
  },
}))
NativeForm.component('fcTitle', DesignedFormTitle)
NativeForm.component('batchText', defineComponent({
  props: ['content'], setup(textProps) { return () => h('p', String(textProps.content ?? '')) },
}))
NativeForm.componentAlias({ ...batchLayoutAliases, number: 'el-input-number',
  divider: 'el-divider', elDivider: 'el-divider', fcAlert: 'el-alert', alert: 'el-alert' })
const runtimeOptions = computed(() => ({
  ...props.options,
  form: { labelWidth: 'auto', ...props.options?.form },
  submitBtn: false, resetBtn: false,
}))
const runtimeRules = computed(() => {
  const rules = formCreate.copyRules(props.rules) as Array<Record<string, any>>
  function visit(items: Array<Record<string, any>>, inheritedReadonly = false) {
    for (const rule of items) {
      const locked = inheritedReadonly || rule.props?.disabled === true || rule.props?.readonly === true
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
          if (rule.type === 'text') rule.type = 'input'
          if (rule.type === 'date' || rule.type === 'datetime') {
            rule.props = { type: rule.type, ...rule.props }; rule.type = 'datePicker'
          }
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
  return rules
})
function changed(name: string, value: unknown, _rule: unknown, _api: unknown, _setFlag?: boolean, added?: boolean) {
  // Form initialization and mounting a conditional field are not user edits.
  if (ready.value && !added && !props.readonlyField(name)) emit('change', name, value)
}
</script>
