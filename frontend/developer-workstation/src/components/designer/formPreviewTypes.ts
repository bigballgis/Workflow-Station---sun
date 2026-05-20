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

/** Render sub-table preview (table + Add) when list columns or sub-form rule exist. */
export function hasSubTablePreviewSurface(binding: PreviewSubTableBinding): boolean {
  return (binding.columns?.length ?? 0) > 0 || (binding.rule?.length ?? 0) > 0
}

/** Column shape from list-view designer or preview column builder (linkForm target metadata). */
export type LinkFormColumnLike = {
  type?: string
  columnType?: string
  field?: string
  fieldName?: string
  componentId?: number | string
  boundSubTableBindingId?: number | null
  props?: {
    componentId?: number | string
    boundSubTableBindingId?: number | null
    formRule?: any[]
    formOption?: any
  }
}

function isLinkFormColumn(col: LinkFormColumnLike | null | undefined): boolean {
  if (!col || typeof col !== 'object') return false
  if (col.type === 'linkForm' || col.columnType === 'linkForm') return true
  const fn = col.fieldName ?? col.field
  return typeof fn === 'string' && fn.startsWith('linkForm:')
}

function linkFormColumnId(col: LinkFormColumnLike): string | null {
  const cid = col.props?.componentId ?? col.componentId
  if (cid != null && String(cid).trim() !== '') return String(cid)
  const fn = col.fieldName ?? col.field
  if (typeof fn === 'string' && fn.startsWith('linkForm:')) return fn
  return null
}

function linkFormTargetBindingId(col: LinkFormColumnLike): number | null {
  const raw = col.props?.boundSubTableBindingId ?? col.boundSubTableBindingId
  if (raw == null || raw === '') return null
  const n = Number(raw)
  return Number.isFinite(n) ? n : null
}

/**
 * Resolve which form schema backs the assignee "form below table" strip.
 * Mirrors user-portal {@code FormRenderer.resolveInlineFormSourceBinding}:
 * - `subForm` (default): own binding's form design
 * - `linkForm`: Link Form column's target sub-table form (e.g. subtable2)
 */
export function resolveInlineFormBelowDesign(params: {
  ownBindingId?: number
  ownRule: any[]
  ownOption?: any
  columns: LinkFormColumnLike[]
  portalViews?: SubTablePortalViewsPreview | null
  resolveSubTableFormDesign?: (bindingId: number) => { rule: any[]; options?: any }
}): { rule: any[]; option?: any } {
  const fallback = { rule: params.ownRule || [], option: params.ownOption }
  const pv = params.portalViews
  if (pv?.assigneeTodo !== 'formBelowTable') return fallback

  const source = pv.assigneeTodoFormSource
  const cols = (params.columns || []).filter(isLinkFormColumn)
  const ownBindingId = params.ownBindingId

  const pickTargetBindingId = (): number | null => {
    const pickedKey =
      source?.linkFormColumnId != null && String(source.linkFormColumnId).trim() !== ''
        ? String(source.linkFormColumnId)
        : null

    if (source?.type === 'linkForm') {
      if (pickedKey) {
        for (const col of cols) {
          if (linkFormColumnId(col) !== pickedKey) continue
          return linkFormTargetBindingId(col)
        }
      }
      for (const col of cols) {
        const targetId = linkFormTargetBindingId(col)
        if (targetId != null) return targetId
      }
    }

    // Legacy (user-portal): form below table + Link Form column → inline target sub-table.
    for (const col of cols) {
      const targetId = linkFormTargetBindingId(col)
      if (targetId == null) continue
      if (ownBindingId == null || targetId !== ownBindingId) return targetId
    }

    return null
  }

  const targetBindingId = pickTargetBindingId()
  if (targetBindingId == null) return fallback

  for (const col of cols) {
    if (linkFormTargetBindingId(col) !== targetBindingId) continue
    const embeddedRule = col.props?.formRule
    if (Array.isArray(embeddedRule) && embeddedRule.length > 0) {
      return { rule: embeddedRule, option: col.props?.formOption ?? params.ownOption }
    }
  }

  const design = params.resolveSubTableFormDesign?.(targetBindingId)
  if (design && Array.isArray(design.rule) && design.rule.length > 0) {
    return { rule: design.rule, option: design.options ?? params.ownOption }
  }

  return fallback
}

/** Convenience wrapper for {@link PreviewSubTableBinding} in Form Preview. */
export function resolvePreviewInlineFormBelowDesign(
  binding: PreviewSubTableBinding,
  resolveSubTableFormDesign?: (bindingId: number) => { rule: any[]; options?: any },
): { rule: any[]; option?: any } {
  return resolveInlineFormBelowDesign({
    ownBindingId: binding.bindingId,
    ownRule: binding.rule || [],
    ownOption: binding.option,
    columns: binding.columns || [],
    portalViews: binding.portalViews,
    resolveSubTableFormDesign,
  })
}

export type FormPreviewItem =
  | { kind: 'fields'; rule: any[]; modelKey: string }
  | { kind: 'subTable'; binding: PreviewSubTableBinding }
  | { kind: 'relationTable'; tableName: string; fields: Array<{ label: string; value: string }> }
  | { kind: 'lookup'; label: string; placeholder: string; searchFields: string[]; displayFields: string[]; selectedDisplayField?: string; filterConditions?: any[]; viewFields: any[]; fieldDefs: any[]; showBackfillView?: boolean; bindingId?: number }
  | { kind: 'card'; title: string; items: FormPreviewItem[]; modelKey: string }
