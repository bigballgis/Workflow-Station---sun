import { describe, expect, it } from 'vitest'
import {
  applyFormCreateRuleReadonly,
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
})
