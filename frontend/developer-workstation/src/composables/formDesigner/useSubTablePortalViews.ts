import { computed, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import type { FormDefinition } from '@/api/functionUnit'
import type { SubTableListColumnDTO } from './useSubTableViews'

/**
 * Per-binding portalViews configuration. Stored in selectedForm.configJson.subTablePortalViews
 * and surfaced as the small editor above each sub-table tab's inner tabs. Lets designers
 * configure user-portal display for sub-tables that have no widget on the main canvas
 * (e.g. a sub-table accessed only via Link Form from another sub-table's list view).
 */
export type PortalViewsValue = {
  assigneeTodo: 'formBelowTable' | 'tableOnly'
  assigneeTodoFormSource?: { type: 'subForm' | 'linkForm' | 'formId'; formId?: number | string | null; linkFormColumnId?: number | string | null }
  initiatorRequest: 'mirrorTodo' | 'summaryWithLinkFormModal' | 'tableOnly'
}

const DEFAULT_BINDING_PORTAL_VIEWS: PortalViewsValue = {
  assigneeTodo: 'tableOnly',
  assigneeTodoFormSource: { type: 'subForm', formId: null, linkFormColumnId: null },
  initiatorRequest: 'mirrorTodo'
}

/**
 * Expose every Link Form column on every SUB binding's list view so that
 * SubTablePortalViewsEditor can let designers PICK which Link Form column drives
 * `assigneeTodoFormSource.type='linkForm'` (rather than silently picking the first).
 */
export type DesignerLinkFormColumnInfo = {
  /**
   * Stable per-column identifier persisted in portalViews.
   * - Negative number: a "generic" Link Form column auto-keyed to a binding
   *   (`-bindingId`, see SubTableListView.vue#genericLinkFormComponentId).
   * - Positive number: an id from `dw_link_form_components` (curated link form widget).
   * - String: fieldName fallback when no componentId is available
   *   (e.g. `linkForm:-5`).
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

interface UseSubTablePortalViewsOptions {
  selectedForm: Ref<FormDefinition | null>
  designerSubBindings: ComputedRef<Array<{ bindingId: number; bindingType: string; tableName: string }>>
  subTableViewState: Ref<Record<number, { allFields: unknown[]; viewFields: SubTableListColumnDTO[] }>>
  resolveDesignerBindingDisplayName: (bindingId: unknown) => string
  scheduleAutoSave: () => void
}

/**
 * Per-binding user-portal display configuration (portalViews) for sub-tables,
 * plus the Link Form column inventory the portalViews editor picks from.
 */
export function useSubTablePortalViews(options: UseSubTablePortalViewsOptions) {
  const {
    selectedForm, designerSubBindings, subTableViewState,
    resolveDesignerBindingDisplayName, scheduleAutoSave,
  } = options

  const subTablePortalViewsState = ref<Record<number, PortalViewsValue>>({})

  function getBindingPortalViews(bindingId: number): PortalViewsValue {
    return (
      subTablePortalViewsState.value[bindingId]
      || (selectedForm.value?.configJson?.subTablePortalViews?.[bindingId] as PortalViewsValue | undefined)
      || { ...DEFAULT_BINDING_PORTAL_VIEWS, assigneeTodoFormSource: { ...DEFAULT_BINDING_PORTAL_VIEWS.assigneeTodoFormSource! } }
    )
  }

  function updateBindingPortalViews(bindingId: number, val: PortalViewsValue) {
    subTablePortalViewsState.value = {
      ...subTablePortalViewsState.value,
      [bindingId]: val
    }
    scheduleAutoSave()
  }

  /**
   * Effective portalViews for a subTable widget in Form Preview: binding-level bar
   * merged with rule.props.portalViews (canvas widget wins per field).
   */
  function mergePortalViewsForPreview(ruleItem: any, bindingId: number): PortalViewsValue {
    const base = getBindingPortalViews(bindingId)
    const ov = ruleItem?.props?.portalViews
    if (!ov || typeof ov !== 'object') {
      return base
    }
    /** Matches form-create subTable rule defaults (developer-workstation main.ts); not a designer override. */
    if (
      'assigneeTodo' in ov &&
      'initiatorRequest' in ov &&
      'assigneeTodoFormSource' in ov &&
      ov.assigneeTodo === 'tableOnly' &&
      ov.initiatorRequest === 'mirrorTodo' &&
      ov.assigneeTodoFormSource &&
      typeof ov.assigneeTodoFormSource === 'object' &&
      ov.assigneeTodoFormSource.type === 'subForm' &&
      (ov.assigneeTodoFormSource.formId == null || ov.assigneeTodoFormSource.formId === '') &&
      (ov.assigneeTodoFormSource.linkFormColumnId == null || ov.assigneeTodoFormSource.linkFormColumnId === '')
    ) {
      return base
    }
    const assigneeTodo: PortalViewsValue['assigneeTodo'] =
      ov.assigneeTodo === 'formBelowTable'
        ? 'formBelowTable'
        : ov.assigneeTodo === 'tableOnly'
          ? 'tableOnly'
          : base.assigneeTodo
    let initiatorRequest: PortalViewsValue['initiatorRequest'] = base.initiatorRequest
    if (ov.initiatorRequest === 'summaryWithLinkFormModal') initiatorRequest = 'summaryWithLinkFormModal'
    else if (ov.initiatorRequest === 'tableOnly') initiatorRequest = 'tableOnly'
    else if (ov.initiatorRequest === 'mirrorTodo') initiatorRequest = 'mirrorTodo'
    const bSrc = base.assigneeTodoFormSource || { type: 'subForm' as const, formId: null, linkFormColumnId: null }
    const oSrc = ov.assigneeTodoFormSource && typeof ov.assigneeTodoFormSource === 'object' ? ov.assigneeTodoFormSource : null
    const mergedOut = {
      assigneeTodo,
      initiatorRequest,
      assigneeTodoFormSource: {
        type: oSrc?.type === 'linkForm' ? ('linkForm' as const) : ('subForm' as const),
        formId: (oSrc?.formId ?? bSrc.formId) ?? null,
        linkFormColumnId: (oSrc?.linkFormColumnId ?? bSrc.linkFormColumnId) ?? null,
      },
    }
    return mergedOut
  }

  /** Return true if a column "looks like" a Link Form column across the various shapes we've
   *  seen in saved forms — direct designer output uses `columnType:'linkForm'`, older forms
   *  may carry `dataType:'LINK_FORM'`, and some persisted shapes only have `fieldName` like
   *  `linkForm:-5`. Accept any of these so the picker doesn't miss real columns. */
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

  /** Reactive map used by the binding-level portalViews bar (prop) and inject for fc-designer panels. */
  const designerLinkFormColumnsMap = computed(computeDesignerLinkFormColumns)

  return {
    subTablePortalViewsState,
    getBindingPortalViews,
    updateBindingPortalViews,
    mergePortalViewsForPreview,
    designerLinkFormColumnsMap,
  }
}
