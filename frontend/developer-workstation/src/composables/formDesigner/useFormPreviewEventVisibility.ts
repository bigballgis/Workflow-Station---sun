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
  withPreviewVisibilityFormOnChange,
  type PreviewEventVisibilityCtx,
} from '@/components/designer/previewEventVisibility'
import type { FormPreviewItem } from '@/components/designer/formPreviewTypes'
import type { PortalFormRequiredState, PortalFormVisibilityState } from '@/utils/formCreateEventRuntime'
import { isEffectivelyDisabled, isEffectivelyRequired } from '@/utils/formCreateEventRuntime'
import { subTableComponentEventFieldKey } from '@/utils/formCreateComponentEvents'
import { useFormEventOverlayBags } from '@/composables/formDesigner/useFormEventOverlayBags'

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

  const parentEventVisibility = inject(PREVIEW_EVENT_VISIBILITY_KEY, null)
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
    onBeforeUnmount(() => unregisterPreviewVisibilityBridge(previewEventVisibility))
  }

  function visiblePreviewRules(rules: unknown[]): unknown[] {
    void previewVisibilityRenderTick.value
    const bags = parentEventVisibility ?? previewEventVisibility
    return applyPreviewOverlayToRules(
      applyPreviewRequiredToRules(
        applyPreviewVisibilityToRules(rules, isPreviewFieldVisible),
        isPreviewFieldRequired,
      ),
      {
        isDisabled: (fieldKey, designerDisabled) =>
          isEffectivelyDisabled(fieldKey, designerDisabled, bags.eventDisabledState.flags),
        optionsFor: bags.scriptOptionsFor,
        labelFor: bags.scriptLabelOverlay,
      },
    )
  }

  function isPreviewSubTableVisible(
    item: Extract<FormPreviewItem, { kind: 'subTable' }>,
  ): boolean {
    const bindingId = item.binding?.bindingId
    if (bindingId == null) return true
    return isPreviewFieldVisible(subTableComponentEventFieldKey(bindingId))
  }

  function previewVisibilityBridge() {
    return {
      state: eventVisibilityState,
      notify: notifyPreviewEventVisibility,
      getAllFieldKeys: () => collectPreviewVisibilityKeys(deps.items()),
      requiredState: eventRequiredState,
      notifyRequired: notifyPreviewEventRequired,
      overlays: previewEventVisibility.overlays,
    }
  }

  const effectivePreviewOption = computed(() => {
    const option = deps.previewOption()
    const base = !deps.isMyRequestsPreview.value
      ? option
      : {
          ...option,
          form: {
            ...(option.form && typeof option.form === 'object' ? option.form : {}),
            disabled: true,
          },
        }
    if (deps.isMyRequestsPreview.value || parentEventVisibility) return base
    return withPreviewVisibilityFormOnChange(base, {
      getFormData: () => deps.previewModel.value,
      applyPatch: (patch) =>
        deps.emitPreviewData({ ...deps.previewModel.value, ...patch }),
      getVisibility: () => previewVisibilityBridge(),
    })
  })

  return {
    previewVisibilityRenderTick,
    visiblePreviewRules,
    isPreviewFieldVisible,
    isPreviewSubTableVisible,
    previewVisibilityBridge,
    effectivePreviewOption,
    eventVisibilityTick,
    formNotifications: previewEventVisibility.formNotifications,
    scriptLookupFiltersFor: previewEventVisibility.scriptLookupFiltersFor,
    scriptLabelOverlay: previewEventVisibility.scriptLabelOverlay,
    showFormEventBanners: !parentEventVisibility,
  }
}
