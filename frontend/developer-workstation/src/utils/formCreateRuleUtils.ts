/**
 * form-create rule helpers — align Designer Preview with Portal field readonly semantics.
 * Designer stores per-field read-only as props.readonly; form-create preview respects props.disabled.
 */

import { getRuleChildren } from '@/utils/formDesigner'

/** User explicitly turned Readonly off in fc-designer (must win over stale parser-injected disabled). */
export function isFormCreateRuleExplicitlyEditable(rule: unknown): boolean {
  if (!rule || typeof rule !== 'object') return false
  const r = rule as Record<string, unknown>
  const props = (r.props as Record<string, unknown> | undefined) || {}
  return r.readonly === false || props.readonly === false
}

export function isFormCreateRuleReadonly(rule: unknown): boolean {
  if (!rule || typeof rule !== 'object') return false
  if (isFormCreateRuleExplicitlyEditable(rule)) return false
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
  const props = (r.props as Record<string, unknown> | undefined) || {}
  const readonly = isFormCreateRuleReadonly(r)
  const childrenChanged = mappedChildren !== children

  if (isFormCreateRuleExplicitlyEditable(r)) {
    const { disabled: _omitDisabled, readonly: _omitReadonly, ...restProps } = props
    const next: Record<string, unknown> = {
      ...r,
      readonly: false,
      props: { ...restProps, readonly: false },
    }
    delete next.disabled
    if (childrenChanged) {
      next.children = mappedChildren
    }
    return next
  }

  if (!readonly && !childrenChanged) return rule

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

function replaceRuleChildren(rule: Record<string, unknown>, mappedChildren: unknown[]): Record<string, unknown> {
  if (Array.isArray(rule.children)) {
    return { ...rule, children: mappedChildren }
  }
  const props = rule.props as Record<string, unknown> | undefined
  if (props) {
    for (const key of ['children', 'list', 'items', 'fields'] as const) {
      if (Array.isArray(props[key])) {
        return { ...rule, props: { ...props, [key]: mappedChildren } }
      }
    }
  }
  return rule
}

/** Apply readonly→disabled mapping on a single node (no child recursion). */
function mapReadonlyOnNode(rule: Record<string, unknown>): Record<string, unknown> {
  const props = (rule.props as Record<string, unknown> | undefined) || {}
  const readonly = isFormCreateRuleReadonly(rule)

  if (isFormCreateRuleExplicitlyEditable(rule)) {
    const { disabled: _omitDisabled, readonly: _omitReadonly, ...restProps } = props
    const next: Record<string, unknown> = {
      ...rule,
      readonly: false,
      props: { ...restProps, readonly: false },
    }
    delete next.disabled
    return next
  }

  if (!readonly) return rule

  const { readonly: _omitReadonly, disabled: _omitDisabled, ...restProps } = props
  return {
    ...rule,
    disabled: true,
    props: { ...restProps, disabled: true },
  }
}

export function mapFormCreateRulesReadonlyDeep(rules: unknown[]): unknown[] {
  if (!Array.isArray(rules)) return []
  const visited = new WeakSet<object>()

  function mapItem(rule: unknown): unknown {
    if (!rule || typeof rule !== 'object') return rule
    if (visited.has(rule)) return rule
    visited.add(rule)

    const r = rule as Record<string, unknown>
    const children = getRuleChildren(r)
    const mappedChildren = children.length ? children.map(mapItem) : []
    const childrenChanged = children.length > 0 && mappedChildren.some((c, i) => c !== children[i])
    const withChildren = childrenChanged
      ? replaceRuleChildren(r, mappedChildren)
      : r
    return mapReadonlyOnNode(withChildren)
  }

  return rules.map(mapItem)
}

/** Remove designer `disabled`; migrate legacy disabled → props.readonly before persist/load. */
export function stripFormCreateRuleDisabled(rule: unknown): unknown {
  if (!rule || typeof rule !== 'object') return rule
  const r = rule as Record<string, unknown>
  const props = { ...((r.props as Record<string, unknown> | undefined) || {}) }
  const explicitlyOff = r.readonly === false || props.readonly === false
  const explicitlyOn = r.readonly === true || props.readonly === true
  const hadDisabled = r.disabled === true || props.disabled === true
  const readonly = !explicitlyOff && (explicitlyOn || hadDisabled)

  delete props.disabled
  const next: Record<string, unknown> = { ...r, props }
  delete next.disabled

  if (explicitlyOff) {
    props.readonly = false
    next.readonly = false
    next.props = props
  } else if (readonly) {
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
