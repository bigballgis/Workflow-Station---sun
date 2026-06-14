/**
 * Sub-table portal display resolution — default views, normalization, and the
 * widget/binding merge that mirrors developer-workstation FormDesigner preview.
 */

import type {
  PortalViewContext,
  SubTableAssigneeTodoMode,
  SubTableFormSourceType,
  SubTableInitiatorRequestMode,
  SubTablePortalViews,
} from './formRendererTypes'

/**
 * Default portalViews applied when a subTable rule node carries no `props.portalViews`
 * (i.e. legacy forms designed before this feature). Keeps current runtime behavior:
 * just render the sub-table, no nested form-below, no Details modal forced.
 */
export const DEFAULT_PORTAL_VIEWS: SubTablePortalViews = Object.freeze({
  assigneeTodo: 'tableOnly',
  assigneeTodoFormSource: { type: 'subForm' as SubTableFormSourceType, formId: null },
  initiatorRequest: 'mirrorTodo'
} as SubTablePortalViews)

/**
 * Resolve the effective display mode at a given view context.
 * - In My Request, `mirrorTodo` falls through to the assigneeTodo mode.
 * - Missing portalViews falls back to DEFAULT_PORTAL_VIEWS ("tableOnly" everywhere).
 *
 * Accepts `Partial<SubTablePortalViews>` so callers can pass binding-level fragments
 * (loaded from form configJson) without normalizing first; missing properties fall
 * through to DEFAULT_PORTAL_VIEWS values.
 */
export function resolveSubTableDisplayMode(
  portalViews: Partial<SubTablePortalViews> | undefined | null,
  context: PortalViewContext
): SubTableAssigneeTodoMode | 'summaryWithLinkFormModal' {
  const pv = portalViews && typeof portalViews === 'object' ? portalViews : DEFAULT_PORTAL_VIEWS
  if (context === 'assigneeTodo') {
    return pv.assigneeTodo === 'tableOnly' ? 'tableOnly' : 'formBelowTable'
  }
  // initiatorRequest
  if (pv.initiatorRequest === 'summaryWithLinkFormModal') return 'summaryWithLinkFormModal'
  if (pv.initiatorRequest === 'tableOnly') return 'tableOnly'
  // mirrorTodo (default) → fall through to assigneeTodo
  return pv.assigneeTodo === 'tableOnly' ? 'tableOnly' : 'formBelowTable'
}

/**
 * Coerce arbitrary `props.portalViews` into a typed object with safe defaults.
 * Missing or malformed input falls back to DEFAULT_PORTAL_VIEWS (tableOnly + mirrorTodo)
 * so legacy forms preserve current behavior.
 */
export function normalizePortalViews(input: Partial<SubTablePortalViews> | undefined | null): SubTablePortalViews {
  if (!input || typeof input !== 'object') {
    return { ...DEFAULT_PORTAL_VIEWS, assigneeTodoFormSource: { ...DEFAULT_PORTAL_VIEWS.assigneeTodoFormSource! } }
  }
  const assigneeTodo: SubTableAssigneeTodoMode =
    input.assigneeTodo === 'formBelowTable' ? 'formBelowTable' : 'tableOnly'
  let initiatorRequest: SubTableInitiatorRequestMode
  if (input.initiatorRequest === 'summaryWithLinkFormModal') {
    initiatorRequest = 'summaryWithLinkFormModal'
  } else if (input.initiatorRequest === 'tableOnly') {
    initiatorRequest = 'tableOnly'
  } else {
    initiatorRequest = 'mirrorTodo'
  }
  const srcType: SubTableFormSourceType =
    input.assigneeTodoFormSource?.type === 'linkForm'
      ? 'linkForm'
      : input.assigneeTodoFormSource?.type === 'formId'
        ? 'formId'
        : 'subForm'
  const formId = input.assigneeTodoFormSource?.formId ?? null
  // Preserve the designer's Link Form column pick so runtime resolution can target
  // a specific column instead of falling back to the first match.
  const linkFormColumnId = input.assigneeTodoFormSource?.linkFormColumnId ?? null
  return {
    assigneeTodo,
    assigneeTodoFormSource: { type: srcType, formId, linkFormColumnId },
    initiatorRequest
  }
}

/**
 * Form-create injects a full default {@code props.portalViews} on every subTable widget (see
 * developer-workstation {@code main.ts} rule). That object must not count as an intentional canvas
 * override — otherwise it always wins merge over {@code configJson.subTablePortalViews[bindingId]}
 * (designers often configure display only on the binding bar, especially inside nested sub-forms).
 *
 * Partial widgets (e.g. only {@code assigneeTodo: 'tableOnly'}) are treated as explicit overrides.
 */
function isImplicitFactorySubTablePortalViews(raw: Partial<SubTablePortalViews> | undefined | null): boolean {
  if (!raw || typeof raw !== 'object') return false
  if (!('assigneeTodo' in raw) || !('initiatorRequest' in raw) || !('assigneeTodoFormSource' in raw)) return false
  if (raw.assigneeTodo !== 'tableOnly') return false
  if (raw.initiatorRequest !== 'mirrorTodo') return false
  const fs = raw.assigneeTodoFormSource
  if (!fs || typeof fs !== 'object') return false
  if (fs.type !== 'subForm') return false
  const fid = fs.formId as unknown
  if (fid != null && fid !== '') return false
  const lid = fs.linkFormColumnId as unknown
  if (lid != null && lid !== '') return false
  return true
}

/**
 * Merge canvas `rule.props.portalViews` with `configJson.subTablePortalViews[bindingId]` the same way
 * developer-workstation {@code FormDesigner.mergePortalViewsForPreview} does — so Portal runtime matches
 * what designers see after editing only the sub-table binding bar (画布节点仍留着默认 tableOnly 时不再覆盖绑定上的 form below)。
 */
export function mergeSubTablePortalViewsForRuntime(
  widgetPv: Partial<SubTablePortalViews> | undefined,
  bindingPvRaw: Partial<SubTablePortalViews> | Record<string, unknown> | null | undefined
): SubTablePortalViews {
  const base = normalizePortalViews(bindingPvRaw as Partial<SubTablePortalViews> | undefined)
  if (!widgetPv || typeof widgetPv !== 'object') {
    return base
  }
  if (isImplicitFactorySubTablePortalViews(widgetPv)) {
    return base
  }

  const ov = widgetPv
  const assigneeTodo: SubTableAssigneeTodoMode =
    ov.assigneeTodo === 'formBelowTable'
      ? 'formBelowTable'
      : ov.assigneeTodo === 'tableOnly'
        ? 'tableOnly'
        : base.assigneeTodo

  let initiatorRequest = base.initiatorRequest
  if (ov.initiatorRequest === 'summaryWithLinkFormModal') {
    initiatorRequest = 'summaryWithLinkFormModal'
  } else if (ov.initiatorRequest === 'tableOnly') {
    initiatorRequest = 'tableOnly'
  } else if (ov.initiatorRequest === 'mirrorTodo') {
    initiatorRequest = 'mirrorTodo'
  }

  const bSrc = base.assigneeTodoFormSource ?? { type: 'subForm' as SubTableFormSourceType, formId: null, linkFormColumnId: null }
  const oSrc = ov.assigneeTodoFormSource && typeof ov.assigneeTodoFormSource === 'object' ? ov.assigneeTodoFormSource : null
  const mergedType: SubTableFormSourceType =
    oSrc?.type === 'linkForm'
      ? 'linkForm'
      : oSrc?.type === 'formId'
        ? 'formId'
        : bSrc.type

  return normalizePortalViews({
    assigneeTodo,
    initiatorRequest,
    assigneeTodoFormSource: {
      type: mergedType,
      formId: (oSrc?.formId ?? bSrc.formId) ?? null,
      linkFormColumnId: (oSrc?.linkFormColumnId ?? bSrc.linkFormColumnId) ?? null
    }
  })
}
