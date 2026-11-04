/**
 * Script visibility (api.hidden / display) for Form Preview tree — extracted from FormPreviewItems.
 */

import {
  computed,
  inject,
  onBeforeUnmount,
  provide,
  ref,
  shallowReactive,
  type ComputedRef,
  type Ref,
} from 'vue'
import {
  PREVIEW_EVENT_VISIBILITY_KEY,
  applyPreviewOverlayToRules,
  applyPreviewRequiredToRules,
  applyPreviewVisibilityToRules,
  collectFieldKeysFromRules,
  registerPreviewVisibilityBridge,
  unregisterPreviewVisibilityBridge,
  runPreviewFormEvent,
  type PreviewEventVisibilityCtx,
} from '@/components/designer/previewEventVisibility'
import type { FormPreviewItem } from '@/components/designer/formPreviewTypes'
import type { PortalFormRequiredState, PortalFormVisibilityState } from '@/utils/formCreateEventRuntime'
import { isEffectivelyDisabled, isEffectivelyRequired } from '@/utils/formCreateEventRuntime'
import { subTableComponentEventFieldKey } from '@/utils/formCreateComponentEvents'
import { useFormEventOverlayBags } from '@/composables/formDesigner/useFormEventOverlayBags'
import { useI18n } from 'vue-i18n'
import { walkFormCreateRules } from '@/utils/formDesigner'
import { isFormCreateRuleReadonly } from '@/utils/formCreateRuleUtils'
import { isTableAuditField } from '@/utils/tableAuditFields'

export function useFormPreviewEventVisibility(deps: {
  items: () => FormPreviewItem[]
  previewOption: () => Record<string, unknown>
  previewModel: ComputedRef<Record<string, unknown>>
  isMyRequestsPreview: ComputedRef<boolean>
  emitPreviewData: (value: Record<string, unknown>) => void
}): {
  previewVisibilityRenderTick: ComputedRef<number>
  visiblePreviewRules: (rules: unknown[]) => unknown[]
  isPreviewFieldVisible: (fieldKey: string) => boolean
  isPreviewSubTableVisible: (item: Extract<FormPreviewItem, { kind: 'subTable' }>) => boolean
  previewVisibilityBridge: () => {
    state: PortalFormVisibilityState
    notify: () => void
    getAllFieldKeys: () => string[]
  }
  effectivePreviewOption: ComputedRef<Record<string, unknown>>
  eventVisibilityTick: Ref<number>
  formNotifications: Ref<import('@/utils/formCreateEventOverlays').FormEventNotification[]>
  scriptLookupFiltersFor: (fieldKey: string) => import('@/utils/formCreateEventOverlays').FormEventLookupFilter[]
  scriptLabelOverlay: (fieldKey: string) => string | undefined
  showFormEventBanners: boolean
} {
  const overlaysApi = useFormEventOverlayBags({
    getAllFieldKeys: () => collectPreviewVisibilityKeys(deps.items()),
    getDesignerOptions: () => [],
    getDesignerLabel: (fieldKey) => fieldKey,
  })
  const { t } = useI18n()
  const scriptErrors = ref<Record<string, string>>({})
  const executions = new Set<{ cancel(): void; wait(): Promise<boolean> }>()
  function snapshotExecution() {
    return { hidden: [...eventVisibilityState.hidden], display: [...eventVisibilityState.display], required: [...eventRequiredState.flags],
      errors: { ...scriptErrors.value }, overlays: overlaysApi.snapshotOverlays() }
  }
  function restoreExecution(raw: unknown) {
    const saved = raw as ReturnType<typeof snapshotExecution>
    eventVisibilityState.hidden = new Map(saved.hidden); eventVisibilityState.display = new Map(saved.display)
    eventRequiredState.flags = new Map(saved.required); scriptErrors.value = saved.errors
    overlaysApi.restoreOverlays(saved.overlays); eventVisibilityTick.value++; eventRequiredTick.value++
  }

  const parentEventVisibility = inject(PREVIEW_EVENT_VISIBILITY_KEY, null)
  const writableFields = computed(() => {
    const fields = new Map<string, boolean>()
    const visit = (items: FormPreviewItem[]) => items.forEach(item => {
      if (item.kind === 'fields') walkFormCreateRules(item.rule, rule => {
        if (typeof rule.field === 'string') fields.set(rule.field, !isFormCreateRuleReadonly(rule) && !isTableAuditField(rule.field))
      })
      if (item.kind === 'lookup') fields.set(item.field, !item.readonly && !isFormCreateRuleReadonly(item.rule))
      if (item.kind === 'card') visit(item.items)
    })
    visit(deps.items())
    return fields
  })
  const eventVisibilityTick = ref(0)
  const eventRequiredTick = ref(0)
  const eventVisibilityState = parentEventVisibility?.state
    ?? shallowReactive<PortalFormVisibilityState>({
      hidden: new Map<string, boolean>(),
      display: new Map<string, boolean>(),
    })
  const eventRequiredState = parentEventVisibility?.requiredState
    ?? shallowReactive<PortalFormRequiredState>({
      flags: new Map<string, boolean>(),
    })
  const previewVisibilityRenderTick = computed(() =>
    (parentEventVisibility ? parentEventVisibility.tick.value : eventVisibilityTick.value)
    + eventRequiredTick.value
    + (parentEventVisibility?.requiredTick?.value ?? 0)
    + overlaysApi.overlayTick.value
    + (parentEventVisibility?.overlayTick.value ?? 0),
  )

  function notifyPreviewEventVisibility(): void {
    if (parentEventVisibility) {
      parentEventVisibility.notify()
      return
    }
    eventVisibilityState.hidden = new Map(eventVisibilityState.hidden)
    eventVisibilityState.display = new Map(eventVisibilityState.display)
    eventVisibilityTick.value++
  }

  function notifyPreviewEventRequired(): void {
    if (parentEventVisibility) {
      parentEventVisibility.notifyRequired()
      return
    }
    eventRequiredState.flags = new Map(eventRequiredState.flags)
    eventRequiredTick.value++
  }

  function isPreviewFieldRequired(fieldKey: string, designerRequired: boolean): boolean {
    void eventRequiredTick.value
    void parentEventVisibility?.requiredTick.value
    return isEffectivelyRequired(fieldKey, designerRequired, eventRequiredState.flags)
  }

  function isPreviewFieldVisible(fieldKey: string): boolean {
    void eventVisibilityTick.value
    void parentEventVisibility?.tick.value
    if (eventVisibilityState.hidden.get(fieldKey) === true) return false
    if (eventVisibilityState.display.get(fieldKey) === false) return false
    return true
  }

  function collectPreviewVisibilityKeys(nodes: FormPreviewItem[]): string[] {
    const keys: string[] = []
    for (const node of nodes) {
      if (node.kind === 'lookup' && node.field) keys.push(String(node.field))
      if (node.kind === 'subTable' && node.binding?.bindingId != null) {
        keys.push(subTableComponentEventFieldKey(node.binding.bindingId))
      }
      if (node.kind === 'fields' && Array.isArray(node.rule)) {
        keys.push(...collectFieldKeysFromRules(node.rule))
      }
      if (node.kind === 'card') keys.push(...collectPreviewVisibilityKeys(node.items))
    }
    return keys
  }

  const previewEventVisibility: PreviewEventVisibilityCtx = parentEventVisibility ?? {
    readonlyField: field => deps.isMyRequestsPreview.value || writableFields.value.get(field) !== true,
    assertWritable: field => {
      if (deps.isMyRequestsPreview.value || writableFields.value.get(field) !== true) throw new Error(`Event field write denied: ${field}`)
    },
    get scopeKey() { return previewEventVisibility },
    snapshot: snapshotExecution,
    restore: restoreExecution,
    registerExecution: host => executions.add(host),
    executionChanged: (pending, error) => {
      const bag = overlaysApi.buildOverlays().notifications!
      bag.clear('form-event-operation')
      if (pending || error) bag.set({ uniqueId: 'form-event-operation', level: error ? 'ERROR' : 'INFO',
        message: error instanceof Error ? error.message : error ? String(error) : t('subTable.batchEventPending') })
    },
    fieldErrors: { setFieldError: (field, message) => { scriptErrors.value = { ...scriptErrors.value, [field]: message } },
      clearFieldError: field => { const next = { ...scriptErrors.value }; delete next[field]; scriptErrors.value = next } },
    state: eventVisibilityState,
    tick: eventVisibilityTick,
    notify: notifyPreviewEventVisibility,
    getAllFieldKeys: () => collectPreviewVisibilityKeys(deps.items()),
    isFieldVisible: isPreviewFieldVisible,
    requiredState: eventRequiredState,
    requiredTick: eventRequiredTick,
    notifyRequired: notifyPreviewEventRequired,
    overlays: overlaysApi.buildOverlays(),
    overlayTick: overlaysApi.overlayTick,
    formNotifications: overlaysApi.formNotifications,
    eventDisabledState: overlaysApi.eventDisabledState,
    scriptOptionsFor: overlaysApi.scriptOptionsFor,
    scriptLabelOverlay: overlaysApi.scriptLabelOverlay,
    scriptLookupFiltersFor: overlaysApi.scriptLookupFiltersFor,
    hasScriptLookupFilter: overlaysApi.hasScriptLookupFilter,
  }
  if (!parentEventVisibility) {
    provide(PREVIEW_EVENT_VISIBILITY_KEY, previewEventVisibility)
    registerPreviewVisibilityBridge(previewEventVisibility)
    onBeforeUnmount(() => { executions.forEach(host => host.cancel()); unregisterPreviewVisibilityBridge(previewEventVisibility) })
  }

  const visibleRulesCache = new WeakMap<unknown[], { tick: number; rules: unknown[] }>()
  function visiblePreviewRules(rules: unknown[]): unknown[] {
    const tick = previewVisibilityRenderTick.value
    const cached = visibleRulesCache.get(rules)
    if (cached?.tick === tick) return cached.rules
    const bags = parentEventVisibility ?? previewEventVisibility
    const result = applyPreviewOverlayToRules(
      applyPreviewRequiredToRules(
        applyPreviewVisibilityToRules(rules, isPreviewFieldVisible),
        isPreviewFieldRequired,
      ),
      {
        isDisabled: (fieldKey, designerDisabled) =>
          isEffectivelyDisabled(fieldKey, designerDisabled, bags.eventDisabledState.flags),
        optionsFor: bags.scriptOptionsFor,
        labelFor: bags.scriptLabelOverlay,
        infoFor: field => bags.overlays.info?.state.get(field),
        errorFor: field => scriptErrors.value[field],
      },
    )
    visibleRulesCache.set(rules, { tick, rules: result })
    return result
  }

  function isPreviewSubTableVisible(
    item: Extract<FormPreviewItem, { kind: 'subTable' }>,
  ): boolean {
    const bindingId = item.binding?.bindingId
    if (bindingId == null) return true
    return isPreviewFieldVisible(subTableComponentEventFieldKey(bindingId))
  }

  function previewVisibilityBridge() {
    return previewEventVisibility
  }

  const effectivePreviewOption = computed(() => {
    const original = deps.previewOption()
    const eventDeps = { getFormData: () => deps.previewModel.value,
      applyPatch: (patch: Record<string, unknown>) => deps.emitPreviewData({ ...deps.previewModel.value, ...patch }), getVisibility: previewVisibilityBridge }
    const run = (name: string, extra?: { config?: unknown; data?: unknown }) => runPreviewFormEvent(original[name] ?? (name === 'onCreated' ? '$FNX:;' : undefined), '__form__', null, eventDeps, extra)
    const option: Record<string, unknown> = { ...original, beforeSubmit: async (...args: unknown[]) => {
      for (const execution of executions) if (!await execution.wait()) return false
      if (Object.keys(scriptErrors.value).length) return false
      if (await run('beforeSubmit', { data: args[0] }) === false) return false
      if (await run('onSubmit', { data: args[0] }) === false) return false
      return Object.keys(scriptErrors.value).length === 0
    } }
    option.onSubmit = () => undefined // User script is awaited above, before native submit accepts data.
    option.onChange = () => undefined // Field dispatcher owns component hooks and form onChange as one operation.
    option.onCreated = () => run('onCreated')
    option.onReload = () => run('onReload')
    option.onReset = async () => { executions.forEach(host => host.cancel()); return run('onReset') }
    option.beforeFetch = (config: unknown, data: unknown) => run('beforeFetch', { config, data })
    const mounted = original.onMounted
    option.onMounted = () => {
      const source = typeof mounted === 'function' && '__hermesPreviewMountedSource' in mounted
        ? (mounted as { __hermesPreviewMountedSource: unknown }).__hermesPreviewMountedSource : mounted
      return runPreviewFormEvent(source, '__form__', null, eventDeps)
    }
    const base = !deps.isMyRequestsPreview.value
      ? option
      : {
          ...option,
          form: {
            ...(option.form && typeof option.form === 'object' ? option.form : {}),
            disabled: true,
          },
        }
    return base
  })

  return {
    previewVisibilityRenderTick,
    visiblePreviewRules,
    isPreviewFieldVisible,
    isPreviewSubTableVisible,
    previewVisibilityBridge,
    effectivePreviewOption,
    eventVisibilityTick,
    formNotifications: overlaysApi.formNotifications,
    scriptLookupFiltersFor: previewEventVisibility.scriptLookupFiltersFor,
    scriptLabelOverlay: previewEventVisibility.scriptLabelOverlay,
    showFormEventBanners: !parentEventVisibility,
  }
}
