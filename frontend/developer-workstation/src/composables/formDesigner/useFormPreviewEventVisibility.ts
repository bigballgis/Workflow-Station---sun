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
  applyPreviewVisibilityToRules,
  collectFieldKeysFromRules,
  registerPreviewVisibilityBridge,
  unregisterPreviewVisibilityBridge,
  withPreviewVisibilityFormOnChange,
  type PreviewEventVisibilityCtx,
} from '@/components/designer/previewEventVisibility'
import type { FormPreviewItem } from '@/components/designer/formPreviewTypes'
import type { PortalFormVisibilityState } from '@/utils/formCreateEventRuntime'
import { subTableComponentEventFieldKey } from '@/utils/formCreateComponentEvents'

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
} {
  const parentEventVisibility = inject(PREVIEW_EVENT_VISIBILITY_KEY, null)
  const eventVisibilityTick = ref(0)
  const eventVisibilityState = parentEventVisibility?.state
    ?? shallowReactive<PortalFormVisibilityState>({
      hidden: new Map<string, boolean>(),
      display: new Map<string, boolean>(),
    })
  const previewVisibilityRenderTick = computed(() =>
    parentEventVisibility ? parentEventVisibility.tick.value : eventVisibilityTick.value,
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
  }
  if (!parentEventVisibility) {
    provide(PREVIEW_EVENT_VISIBILITY_KEY, previewEventVisibility)
    registerPreviewVisibilityBridge(previewEventVisibility)
    onBeforeUnmount(() => unregisterPreviewVisibilityBridge(previewEventVisibility))
  }

  function visiblePreviewRules(rules: unknown[]): unknown[] {
    void previewVisibilityRenderTick.value
    return applyPreviewVisibilityToRules(rules, isPreviewFieldVisible)
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
  }
}
