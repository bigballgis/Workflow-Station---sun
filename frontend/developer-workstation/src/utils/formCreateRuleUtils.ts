/**
 * form-create rule helpers — align Designer Preview with Portal field readonly semantics.
 * Designer stores per-field read-only as props.readonly; form-create preview respects props.disabled.
 */

export function isFormCreateRuleReadonly(rule: unknown): boolean {
  if (!rule || typeof rule !== 'object') return false
  const r = rule as Record<string, unknown>
  const props = (r.props as Record<string, unknown> | undefined) || {}
  return (
    r.disabled === true ||
    r.readonly === true ||
    props.disabled === true ||
    props.readonly === true
  )
}

/** Designer "Hide" toggle — form-create `rule.hidden` / live canvas `_hidden` (also props.hide for legacy). */
export function isFormCreateRuleHidden(rule: unknown): boolean {
  if (!rule || typeof rule !== 'object') return false
  const r = rule as Record<string, unknown>
  const props = (r.props as Record<string, unknown> | undefined) || {}
  return (
    r.hidden === true ||
    r._hidden === true ||
    r.display === false ||
    r._display === false ||
    props.hidden === true ||
    props.hide === true
  )
}

/** Map designer readonly → form-create disabled for Preview / sub-form dialogs. */
export function applyFormCreateRuleReadonly(rule: unknown): unknown {
  if (!rule || typeof rule !== 'object') return rule
  const r = rule as Record<string, unknown>
  const children = r.children
  const mappedChildren = Array.isArray(children)
    ? mapFormCreateRulesReadonlyDeep(children)
    : children
  const readonly = isFormCreateRuleReadonly(r)
  const childrenChanged = mappedChildren !== children

  if (!readonly && !childrenChanged) return rule

  const props = (r.props as Record<string, unknown> | undefined) || {}
  const { readonly: _omitReadonly, disabled: _omitDisabled, ...restProps } = props
  const next: Record<string, unknown> = readonly
    ? {
        ...r,
        disabled: true,
        props: { ...restProps, disabled: true },
      }
    : { ...r }

  if (childrenChanged) {
    next.children = mappedChildren
  }
  return next
}

export function mapFormCreateRulesReadonlyDeep(rules: unknown[]): unknown[] {
  if (!Array.isArray(rules)) return []
  return rules.map((rule) => applyFormCreateRuleReadonly(rule))
}

/** Remove designer `disabled`; migrate legacy disabled → props.readonly before persist/load. */
export function stripFormCreateRuleDisabled(rule: unknown): unknown {
  if (!rule || typeof rule !== 'object') return rule
  const r = rule as Record<string, unknown>
  const props = { ...((r.props as Record<string, unknown> | undefined) || {}) }
  const hadDisabled = r.disabled === true || props.disabled === true
  const readonly = props.readonly === true || r.readonly === true || hadDisabled

  delete props.disabled
  const next: Record<string, unknown> = { ...r, props }
  delete next.disabled

  if (readonly) {
    props.readonly = true
    next.props = props
  }

  if (Array.isArray(r.children)) {
    next.children = stripFormCreateRulesDisabledDeep(r.children as unknown[])
  }
  return next
}

export function stripFormCreateRulesDisabledDeep(rules: unknown[]): unknown[] {
  if (!Array.isArray(rules)) return []
  return rules.map((rule) => stripFormCreateRuleDisabled(rule))
}
