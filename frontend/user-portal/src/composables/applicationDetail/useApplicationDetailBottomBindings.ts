import { computed, type ComputedRef } from 'vue'
import { collectPlacedSubTableBindingIds } from '@/components/formRendererHelpers'
import type { ApplicationDetailState } from './useApplicationDetailState'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailBottomBindingsFns {
  shouldRenderBottomUnplacedSubTable: (
    binding: { bindingId: number; bindingType?: string; subMode?: string; portalViews?: Record<string, unknown> | null },
    placed: Set<number>,
    bindings: Array<{ bindingId: number; bindingType?: string; subMode?: string; columns?: Array<{ type?: string; props?: Record<string, unknown> }>; portalViews?: Record<string, unknown> | null }>,
    nativeBindingIds: ReadonlySet<number>,
    formConfig?: Record<string, unknown> | null,
  ) => boolean
  placedBindingIds: ComputedRef<Set<number>>
  bottomSubTableBindings: ComputedRef<ApplicationDetailState['subTableBindings']['value']>
  diagramSelectedLinkableBindings: ComputedRef<any[] | undefined>
  diagramSelectedBottomSubTables: ComputedRef<PreviousFormSubTableBindings>
}

type PreviousFormSubTableBindings = ApplicationDetailState['previousForms']['value'][number]['subTableBindings']

export function createApplicationDetailBottomBindings(ctx: ApplicationDetailCtx): ApplicationDetailBottomBindingsFns {
  const {
    formFields,
    formTabs,
    formFieldsAfterTabs,
    subTableBindings,
    previousForms,
    mainFormNativeSubTableBindingIds,
    mainFormConfig,
    selectedNodeForm,
    linkableSubTableBindings,
  } = ctx

  /**
   * Portal-design-parity (see `portal-design-parity.mdc`): User Portal MUST mirror the DW Form
   * Designer Preview. DW Preview renders only sub-tables placed in `rule` (and transitive
   * link-form targets via `subListViews` columns) — see `FormDesigner.vue:buildPreviewItems`.
   * Unplaced bindings (orphans, stale designer state, RELATED lookup targets, link-form-only
   * bindings whose host column was deleted in a later designer save) are NEVER surfaced as
   * standalone bottom tables in the Designer Preview, so they MUST NOT be surfaced here either.
   *
   * The early-return gates below are retained for clarity (and to keep the call surface intact)
   * but the function intentionally returns `false` unconditionally — the bottom "unplaced
   * fallback" section is no longer rendered for any binding. Callers that legitimately need to
   * expose a sub-table should place it in the form's `rule` or reference it via a `linkForm`
   * column in `subListViews`.
   */
  function shouldRenderBottomUnplacedSubTable(
    binding: { bindingId: number; bindingType?: string; subMode?: string; portalViews?: Record<string, unknown> | null },
    placed: Set<number>,
    bindings: Array<{ bindingId: number; bindingType?: string; subMode?: string; columns?: Array<{ type?: string; props?: Record<string, unknown> }>; portalViews?: Record<string, unknown> | null }>,
    nativeBindingIds: ReadonlySet<number>,
    formConfig?: Record<string, unknown> | null,
  ): boolean {
    // Argument references — retained so future legacy reactivation can re-enable specific gates.
    void binding
    void placed
    void bindings
    void nativeBindingIds
    void formConfig
    return false
  }

  const placedBindingIds = computed((): Set<number> => {
    return collectPlacedSubTableBindingIds(formFields.value, formTabs.value, formFieldsAfterTabs.value)
  })

  const bottomSubTableBindings = computed(() => {
    const nativeIds = new Set(mainFormNativeSubTableBindingIds.value.map(Number))
    const placed = placedBindingIds.value
    return subTableBindings.value.filter(b =>
      shouldRenderBottomUnplacedSubTable(b, placed, subTableBindings.value, nativeIds, mainFormConfig.value),
    )
  })

  /** Link-form fallback when previewing a diagram node's form */
  const diagramSelectedLinkableBindings = computed<any[] | undefined>(() => {
    const sf = selectedNodeForm.value
    if (!sf) return undefined
    if (sf.isCurrentStep) return linkableSubTableBindings.value
    return [
      ...(sf.subTableBindings as any[]),
      ...previousForms.value.flatMap(form => form.subTableBindings as any[])
    ]
  })

  /** Unplaced sub-tables for the diagram-selected form (mirrors bottomSubTableBindings) */
  const diagramSelectedBottomSubTables = computed<PreviousFormSubTableBindings>(() => {
    const sf = selectedNodeForm.value
    if (!sf) return []
    const fields = sf.isCurrentStep ? formFields.value : sf.fields
    const tabs = sf.isCurrentStep ? formTabs.value : sf.tabs
    const afterTabs = sf.isCurrentStep ? formFieldsAfterTabs.value : sf.fieldsAfterTabs
    const bindings = sf.isCurrentStep ? subTableBindings.value : sf.subTableBindings
    const placed = collectPlacedSubTableBindingIds(fields, tabs, afterTabs)
    const nativeIds = new Set(
      (sf.isCurrentStep ? mainFormNativeSubTableBindingIds.value : sf.nativeSubTableBindingIds).map(Number),
    )
    return bindings.filter((b: { bindingId: number; subMode?: string }) =>
      shouldRenderBottomUnplacedSubTable(
        b,
        placed,
        bindings as any[],
        nativeIds,
        sf.isCurrentStep ? mainFormConfig.value : sf.formConfig,
      ),
    )
  })

  return {
    shouldRenderBottomUnplacedSubTable,
    placedBindingIds,
    bottomSubTableBindings,
    diagramSelectedLinkableBindings,
    diagramSelectedBottomSubTables,
  }
}
