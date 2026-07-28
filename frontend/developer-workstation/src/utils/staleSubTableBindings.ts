import { walkFormCreateRules } from '@/utils/formDesigner'
import { normalizeBindingId } from '@/utils/bindingDisplayHelpers'

/** Collect `_bindingId` values from subTable rules (top-level or props). */
export function collectSubTableBindingIds(rules: unknown[]): number[] {
  const ids = new Set<number>()
  walkFormCreateRules(rules as any[], (rule) => {
    if (rule?.type !== 'subTable') return
    const raw = rule._bindingId ?? (rule.props as Record<string, unknown> | undefined)?._bindingId
    const id = normalizeBindingId(raw as number | string | null | undefined)
    if (id != null) ids.add(id)
  })
  return [...ids]
}

/** Binding ids referenced by subTable placeholders but missing from the form's current bindings. */
export function collectStaleSubTableBindingIds(
  rules: unknown[],
  knownBindingIds: Iterable<number>,
): number[] {
  const known = new Set(
    [...knownBindingIds].filter((id) => Number.isFinite(id)),
  )
  return collectSubTableBindingIds(rules).filter((id) => !known.has(id))
}
