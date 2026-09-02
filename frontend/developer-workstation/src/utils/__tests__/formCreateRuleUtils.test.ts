import { describe, expect, it } from 'vitest'
import {
  applyFormCreateRuleReadonly,
  isFormCreateRuleExplicitlyEditable,
  isFormCreateRuleReadonly,
  isFormCreateRuleHidden,
  mapFormCreateRulesReadonlyDeep,
  stripFormCreateRuleDisabled,
} from '../formCreateRuleUtils'

describe('formCreateRuleUtils', () => {
  it('detects props.readonly from designer', () => {
    expect(isFormCreateRuleReadonly({ field: 'x', props: { readonly: true } })).toBe(true)
    expect(isFormCreateRuleReadonly({ field: 'x', props: { disabled: true } })).toBe(true)
    expect(isFormCreateRuleReadonly({ field: 'x', disabled: true })).toBe(true)
    expect(isFormCreateRuleReadonly({ field: 'x', props: {} })).toBe(false)
  })

  it('explicit readonly false wins over stale parser disabled', () => {
    expect(isFormCreateRuleReadonly({
      field: 'case_number',
      readonly: false,
      disabled: true,
      props: { disabled: true, readonly: false },
    })).toBe(false)
    expect(isFormCreateRuleExplicitlyEditable({ props: { readonly: false } })).toBe(true)
  })

  it('clears disabled when mapping explicitly editable rules for preview', () => {
    const mapped = applyFormCreateRuleReadonly({
      type: 'input',
      field: 'case_number',
      readonly: false,
      disabled: true,
      props: { disabled: true, readonly: false },
    }) as Record<string, unknown>
    expect(mapped.disabled).toBeUndefined()
    expect(mapped.readonly).toBe(false)
    expect((mapped.props as Record<string, unknown>).disabled).toBeUndefined()
    expect((mapped.props as Record<string, unknown>).readonly).toBe(false)
  })

  it('detects designer Hide (rule.hidden)', () => {
    expect(isFormCreateRuleHidden({ field: 'x', hidden: true })).toBe(true)
    expect(isFormCreateRuleHidden({ field: 'x', _hidden: true })).toBe(true)
    expect(isFormCreateRuleHidden({ field: 'x', _display: false })).toBe(true)
    expect(isFormCreateRuleHidden({ field: 'x', props: { hide: true } })).toBe(true)
    expect(isFormCreateRuleHidden({ field: 'x' })).toBe(false)
  })

  it('maps readonly to form-create disabled for preview', () => {
    const mapped = applyFormCreateRuleReadonly({
      type: 'select',
      field: 'case_type',
      props: { readonly: true },
    }) as Record<string, unknown>
    expect(mapped.disabled).toBe(true)
    expect((mapped.props as Record<string, unknown>).disabled).toBe(true)
    expect((mapped.props as Record<string, unknown>).readonly).toBeUndefined()
  })

  it('recurses into card children', () => {
    const rules = mapFormCreateRulesReadonlyDeep([
      {
        type: 'elCard',
        children: [{ type: 'input', field: 'a', props: { readonly: true } }],
      },
    ]) as Array<Record<string, unknown>>
    const child = (rules[0].children as Array<Record<string, unknown>>)[0]
    expect(child.disabled).toBe(true)
  })

  it('strips disabled and migrates to readonly on persist', () => {
    const stripped = stripFormCreateRuleDisabled({
      type: 'select',
      field: 'x',
      disabled: true,
      props: { disabled: true, placeholder: 'Pick' },
    }) as Record<string, unknown>
    expect(stripped.disabled).toBeUndefined()
    expect((stripped.props as Record<string, unknown>).disabled).toBeUndefined()
    expect((stripped.props as Record<string, unknown>).readonly).toBe(true)
    expect((stripped.props as Record<string, unknown>).placeholder).toBe('Pick')
  })

  it('preserves sub-table per-operation permission props through persist strip', () => {
    // 子表逐操作权限存于 rule.props.allowAdd/allowEdit/allowDelete；
    // 保存时 stripFormCreateRuleDisabled 只处理 disabled/readonly，不得丢掉这些标志。
    const stripped = stripFormCreateRuleDisabled({
      type: 'subTable',
      _bindingId: 50114,
      props: { allowAdd: true, allowEdit: true, allowDelete: false, _bindingId: 50114 },
    }) as Record<string, unknown>
    const props = stripped.props as Record<string, unknown>
    expect(props.allowAdd).toBe(true)
    expect(props.allowEdit).toBe(true)
    expect(props.allowDelete).toBe(false)
  })

  it('does not re-persist readonly when user explicitly turned it off', () => {
    const stripped = stripFormCreateRuleDisabled({
      type: 'input',
      field: 'case_number',
      readonly: false,
      disabled: true,
      props: { disabled: true, readonly: false },
    }) as Record<string, unknown>
    expect(stripped.disabled).toBeUndefined()
    expect(stripped.readonly).toBe(false)
    expect((stripped.props as Record<string, unknown>).disabled).toBeUndefined()
    expect((stripped.props as Record<string, unknown>).readonly).toBe(false)
  })
})

/**
 * The Assignment Mode block declares `input: false`, so form-create forwards nothing to
 * it and marking only the container `disabled` left its pickers editable. The block owns
 * those fields, so its Readonly toggle must reach them.
 */
describe('Assignment Mode readonly cascades to the fields the block owns', () => {
  const build = (containerProps: Record<string, unknown>) => ([{
    type: 'miAssignment',
    props: containerProps,
    children: [
      { type: 'input', field: 'assignee', title: 'Assignee', props: {} },
      { type: 'select', field: 'bu_code', title: 'Business Unit', props: {} },
    ],
  }])
  const disabledOf = (out: unknown[]) =>
    ((out[0] as { children: Array<{ props?: { disabled?: unknown } }> }).children)
      .map(child => child.props?.disabled === true)

  it('disables the pickers when the container is readonly', () => {
    expect(disabledOf(mapFormCreateRulesReadonlyDeep(build({ readonly: true })))).toEqual([true, true])
  })

  it('leaves them editable when the container is not', () => {
    expect(disabledOf(mapFormCreateRulesReadonlyDeep(build({})))).toEqual([false, false])
  })

  it('lets a child that explicitly turned Readonly off win', () => {
    const rules = build({ readonly: true })
    rules[0].children[0].props = { readonly: false }
    expect(disabledOf(mapFormCreateRulesReadonlyDeep(rules))).toEqual([false, true])
  })

  it('does NOT cascade for ordinary containers — a readonly card keeps its children as they were', () => {
    const out = mapFormCreateRulesReadonlyDeep([{
      type: 'card',
      props: { readonly: true },
      children: [{ type: 'input', field: 'x', props: {} }],
    }]) as Array<{ children: Array<{ props?: { disabled?: unknown } }> }>
    expect(out[0].children[0].props?.disabled).not.toBe(true)
  })
})
