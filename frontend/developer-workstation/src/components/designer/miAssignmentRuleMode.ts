import {
  DEMO_BU_OPTIONS,
  DEMO_ROLE_OPTIONS,
  fieldsHiddenByMode,
  fieldsOwnedByMode,
  isAssignmentConfigured,
  type AssignmentConfig,
  type AssignmentMode,
} from '@/utils/miAssignmentConfig'
import { isFormCreateRuleHidden } from '@/utils/formCreateRuleUtils'

/**
 * Assignment Mode rule shaping for Preview hosts that render a sub-form as a *runtime*
 * surface — one mode selected, only that mode's pickers inside the block.
 *
 * Extracted from SubTableFormDialog when the Inline Form block (FormPreviewItems) needed
 * the same shaping: both Preview paths show the same sub-form, so they must shape it the
 * same way. SubTableFormDialog still carries its own copy of these three functions;
 * KEEP THE TWO IN SYNC until it is switched over to this module.
 *
 * The designer canvas is deliberately NOT a consumer: there every field the container
 * holds is shown at once, because mode switching is a runtime concern.
 */

/** A container rule for sub-forms designed before Assignment Mode owned its fields. */
function makeAssignmentContainerRule(children: any[]): any {
  return { type: 'miAssignment', props: {}, children }
}

/**
 * Give the Assignment Mode container its children, so the block and its pickers
 * render as one nested unit. Mirrors nestAssignmentFieldsIntoContainer() plus
 * ensureAssignmentBlockPlaced() in the Portal — keep in sync.
 *
 * Forms saved before the container existed keep assignee / BU / role as siblings;
 * here they are folded into the container (inserted at the first owned field when
 * no container rule exists yet). Nothing is dropped: every field rule survives,
 * only its depth in the tree changes, so bindings and validation stay intact.
 */
function nestAssignmentChildren(list: any[], order: string[]): any[] {
  if (order.length === 0) return list
  const owned = new Set(order)
  // Author order wins: children already inside the container keep their arrangement,
  // and only fields still loose outside it get appended. The designer owns placement,
  // so preview must not re-sort into contract order.
  const nestedFirst: any[] = []
  const looseByField = new Map<string, any>()
  for (const rule of list) {
    if (rule?.type === 'miAssignment') {
      for (const child of (rule.children ?? [])) {
        if (child?.field && owned.has(child.field)) nestedFirst.push(child)
      }
    } else if (rule?.field && owned.has(rule.field)) {
      looseByField.set(rule.field, rule)
    }
  }
  const alreadyNested = new Set(nestedFirst.map(child => child?.field))
  const appended = order
    .filter(field => !alreadyNested.has(field))
    .map(field => looseByField.get(field))
    .filter(Boolean)
  const children = [...nestedFirst, ...appended]
  if (children.length === 0) return list

  const rest = list.filter(rule => !(rule?.field && owned.has(rule.field)))
  const containerAt = rest.findIndex(rule => rule?.type === 'miAssignment')
  if (containerAt >= 0) {
    return rest.map(rule =>
      rule?.type === 'miAssignment' ? { ...rule, children } : rule)
  }
  // No container yet — place it where its first field already sat, counting only
  // the non-owned rules before that point so nothing shifts unexpectedly.
  const anchorAt = list.findIndex(rule => rule?.field && owned.has(rule.field))
  if (anchorAt < 0) return list
  const keptBefore = list
    .slice(0, anchorAt)
    .filter(rule => !(rule?.field && owned.has(rule.field))).length
  return [
    ...rest.slice(0, keptBefore),
    makeAssignmentContainerRule(children),
    ...rest.slice(keptBefore),
  ]
}

/**
 * Keep only the active mode's assignment fields and nest them into the container.
 * Returns the input rules untouched when the sub-table has no assignment contract.
 */
export function filterRuleByAssignMode(
  rules: any[],
  mode: AssignmentMode,
  config?: AssignmentConfig,
): any[] {
  const configured = !!config && isAssignmentConfigured(config)
  const hidden = configured ? fieldsHiddenByMode(mode, config!) : new Set<string>()
  // Children are the active mode's fields only; the other mode's are filtered out.
  const order = configured ? fieldsOwnedByMode(mode, config!) : []
  const walk = (list: any[]): any[] =>
    nestAssignmentChildren(
      (list || [])
        .filter(r => {
          // Single-mode setups keep the container too: it holds their one picker.
          // The designer's standard Hide toggle still wins — an authored-hidden block
          // must not render just because the sub-table has a valid assignment contract.
          if (r?.type === 'miAssignment') return configured && !isFormCreateRuleHidden(r)
          return !(r?.field && hidden.has(r.field))
        })
        .map(r => {
          if (!r) return r
          if (r.type === 'miAssignment') {
            // Drop children hidden by the active mode before nesting re-seats them.
            const kept = (r.children ?? []).filter(
              (child: any) => !(child?.field && hidden.has(child.field)))
            return { ...r, children: kept }
          }
          return Array.isArray(r.children) ? { ...r, children: walk(r.children) } : r
        }),
      order,
    )
  return walk(rules)
}

/**
 * Preview has no admin-center query, so the BU / Role pickers are stocked with the
 * shared demo options — the same ones the sub-table grid uses to render labels back.
 * Mutates in place: the caller owns a cloned rule tree.
 */
export function injectDemoBuRoleOptions(rules: any[], config?: AssignmentConfig): void {
  if (!config) return
  const walk = (list: any[]) => {
    for (const r of list || []) {
      if (r && config.buField && r.field === config.buField) r.options = DEMO_BU_OPTIONS
      else if (r && config.roleField && r.field === config.roleField) r.options = DEMO_ROLE_OPTIONS
      if (r && Array.isArray(r.children)) walk(r.children)
    }
  }
  walk(rules)
}

/**
 * Widest label either mode can show, in px, measured off the RAW rule so the floor does
 * not depend on the active mode.
 *
 * `labelWidth: 'auto'` re-measures against whichever fields are currently visible, so
 * switching assignment mode ("Assignee" ⇄ "Business Unit" / "Role") moves every other
 * row's input edge sideways. Publishing this as a floor keeps the column already wide
 * enough for the other branch, so nothing shifts on toggle; `auto` still governs above
 * it, so a genuinely longer label elsewhere is never clipped or wrapped.
 *
 * Returns '' when there is nothing to pin (no contract, or no titled owned field).
 */
export function measureAssignmentLabelMinWidth(
  rawRule: any[],
  config?: AssignmentConfig,
): string {
  if (!config || !isAssignmentConfigured(config)) return ''
  const owned = new Set(
    [config.assigneeField, config.buField, config.roleField]
      .filter((field): field is string => !!field),
  )
  // Titles come from the raw rule so hidden-by-mode fields are included too.
  const titles: string[] = []
  const walk = (list: any[]) => {
    for (const rule of list || []) {
      if (rule?.field && owned.has(rule.field) && rule.title) titles.push(String(rule.title))
      if (Array.isArray(rule?.children)) walk(rule.children)
    }
  }
  walk(rawRule || [])
  if (titles.length === 0) return ''

  const ruler = document.createElement('span')
  ruler.style.cssText =
    'position:absolute;visibility:hidden;white-space:nowrap;left:-9999px;top:-9999px;font:14px sans-serif;'
  document.body.appendChild(ruler)
  let widest = 0
  for (const title of titles) {
    ruler.textContent = title
    widest = Math.max(widest, ruler.getBoundingClientRect().width)
  }
  ruler.remove()
  // Element Plus adds the label's right padding on top of the text itself.
  return widest > 0 ? `${Math.ceil(widest) + 12}px` : ''
}
