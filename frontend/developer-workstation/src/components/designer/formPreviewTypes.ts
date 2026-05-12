/** Sub-table portal display (designer rule.props + binding-level subTablePortalViews). */
export interface SubTablePortalViewsPreview {
  assigneeTodo: 'formBelowTable' | 'tableOnly'
  initiatorRequest: 'mirrorTodo' | 'summaryWithLinkFormModal' | 'tableOnly'
  assigneeTodoFormSource?: {
    type?: 'subForm' | 'linkForm' | 'formId'
    formId?: number | string | null
    linkFormColumnId?: number | string | null
  }
}

export interface PreviewSubTableBinding {
  bindingId: number
  bindingType: string
  bindingMode: string
  tableName: string
  tableType: string
  tableDescription: string
  rule: any[]
  option?: any
  columns: any[]
  subMode?: string
  /** Effective portal views for this sub-table widget (rule overrides binding-level). */
  portalViews?: SubTablePortalViewsPreview
}

/**
 * My Requests ≠ Same as To Do → show two previews (assignee vs initiator).
 */
export function isDualPortalSubTablePreview(binding: PreviewSubTableBinding): boolean {
  const v = binding.portalViews
  if (!v) return false
  const init = v.initiatorRequest
  if (init == null || init === 'mirrorTodo') return false
  return true
}

export function initiatorPreviewIsSummary(binding: PreviewSubTableBinding): boolean {
  return binding.portalViews?.initiatorRequest === 'summaryWithLinkFormModal'
}

export type FormPreviewItem =
  | { kind: 'fields'; rule: any[]; modelKey: string }
  | { kind: 'subTable'; binding: PreviewSubTableBinding }
  | { kind: 'relationTable'; tableName: string; fields: Array<{ label: string; value: string }> }
  | { kind: 'lookup'; label: string; placeholder: string; searchFields: string[]; displayFields: string[]; selectedDisplayField?: string; filterConditions?: any[]; viewFields: any[]; fieldDefs: any[]; showBackfillView?: boolean; bindingId?: number }
  | { kind: 'card'; title: string; items: FormPreviewItem[]; modelKey: string }
