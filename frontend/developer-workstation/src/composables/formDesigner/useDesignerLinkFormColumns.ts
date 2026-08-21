import { computed } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import type { FormDefinition } from '@/api/functionUnit'
import type { SubTableListColumnDTO } from './useSubTableViews'

/**
 * Inventory of the Link Form columns declared on every SUB binding's list view.
 *
 * <p>Provided to the fc-designer panels so a sub-table column can reference the
 * Link Form it opens.
 */
export type DesignerLinkFormColumnInfo = {
  /**
   * Stable per-column identifier.
   * - Negative number: a "generic" Link Form column auto-keyed to a binding
   *   (`-bindingId`, see SubTableListView.vue#genericLinkFormComponentId).
   * - Positive number: an id from `dw_link_form_components` (curated link form widget).
   * - String: fieldName fallback when no componentId is available (e.g. `linkForm:-5`).
   */
  componentId: number | string
  /** SUB binding that OWNS this list view column. */
  sourceBindingId: number
  sourceBindingName: string
  /** SUB binding the Link Form column targets — its form schema is what would render below. */
  boundSubTableBindingId: number | null
  boundSubTableName: string | null
  columnLabel: string
  linkText: string
}

interface UseDesignerLinkFormColumnsOptions {
  selectedForm: Ref<FormDefinition | null>
  designerSubBindings: ComputedRef<Array<{ bindingId: number; bindingType: string; tableName: string }>>
  subTableViewState: Ref<Record<number, { allFields: unknown[]; viewFields: SubTableListColumnDTO[] }>>
  resolveDesignerBindingDisplayName: (bindingId: unknown) => string
}

export function useDesignerLinkFormColumns(options: UseDesignerLinkFormColumnsOptions) {
  const {
    selectedForm,
    designerSubBindings,
    subTableViewState,
    resolveDesignerBindingDisplayName,
  } = options

  /**
   * Whether a column "looks like" a Link Form column. Saved forms carry several
   * shapes: direct designer output uses `columnType:'linkForm'`, older forms may
   * carry `dataType:'LINK_FORM'`, and some persisted shapes only have a
   * `fieldName` such as `linkForm:-5`. Accept any of them so real columns are
   * not missed.
   */
  function isLinkFormListColumn(c: any): boolean {
    if (!c || typeof c !== 'object') return false
    if (c.columnType === 'linkForm') return true
    if (typeof c.dataType === 'string' && c.dataType.toUpperCase() === 'LINK_FORM') return true
    if (typeof c.fieldName === 'string' && c.fieldName.startsWith('linkForm:')) return true
    if (c.componentId != null && (c.boundSubTableBindingId != null || c.linkedFormId != null)) return true
    return false
  }

  function computeDesignerLinkFormColumns(): Record<number, DesignerLinkFormColumnInfo[]> {
    const result: Record<number, DesignerLinkFormColumnInfo[]> = {}
    const subs = designerSubBindings.value.filter(b => b.bindingType === 'SUB')
    const configListViews = selectedForm.value?.configJson?.subListViews || {}
    for (const b of subs) {
      const liveCols = subTableViewState.value[b.bindingId]?.viewFields
      const savedEntry = configListViews[b.bindingId] ?? configListViews[String(b.bindingId)]
      const savedCols = (savedEntry as any)?.columns
      const cols = Array.isArray(liveCols) && liveCols.length > 0 ? liveCols : (savedCols || [])
      const linkCols: DesignerLinkFormColumnInfo[] = []
      for (const c of cols) {
        if (!isLinkFormListColumn(c)) continue
        const rawCid = (c as any).componentId
        const componentId = rawCid != null ? Number(rawCid) : NaN
        const stableId: number | string | null = Number.isFinite(componentId) && componentId !== 0
          ? componentId
          : ((c as any).fieldName ? String((c as any).fieldName) : null)
        if (stableId == null) continue
        linkCols.push({
          componentId: stableId,
          sourceBindingId: b.bindingId,
          sourceBindingName: b.tableName,
          boundSubTableBindingId: (c as any).boundSubTableBindingId ?? null,
          boundSubTableName: (c as any).boundSubTableName
            || resolveDesignerBindingDisplayName((c as any).boundSubTableBindingId)
            || null,
          columnLabel: (c as any).columnLabel || (c as any).displayName || (c as any).linkText || `linkForm:${stableId}`,
          linkText: (c as any).linkText || ''
        })
      }
      if (linkCols.length > 0) result[b.bindingId] = linkCols
    }
    return result
  }

  const designerLinkFormColumnsMap = computed(computeDesignerLinkFormColumns)

  return { designerLinkFormColumnsMap }
}
