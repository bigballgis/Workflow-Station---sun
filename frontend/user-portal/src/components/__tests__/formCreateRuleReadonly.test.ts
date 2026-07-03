import { describe, expect, it } from 'vitest'
import { isFormCreateRuleReadonly, isFormFieldReadonly, type FormField } from '../formRendererHelpers'

describe('isFormCreateRuleReadonly', () => {
  it('honours designer props.readonly', () => {
    expect(isFormCreateRuleReadonly({ props: { readonly: true } })).toBe(true)
    expect(isFormCreateRuleReadonly({ props: { readonly: false } })).toBe(false)
    expect(isFormCreateRuleReadonly({
      readonly: false,
      disabled: true,
      props: { readonly: false, disabled: true },
    })).toBe(false)
  })

  it('forces system audit fields readonly (values are server-filled at insert/update)', () => {
    for (const field of ['created_at', 'created_by', 'updated_at', 'updated_by']) {
      expect(isFormCreateRuleReadonly({ field, props: {} })).toBe(true)
    }
    expect(isFormCreateRuleReadonly({ field: 'normal_field', props: {} })).toBe(false)
  })
})

describe('isFormFieldReadonly', () => {
  it('combines form-level and field-level readonly', () => {
    const field = { key: 'a', label: 'A', type: 'text', readonly: true } as FormField
    expect(isFormFieldReadonly(field, false)).toBe(true)
    expect(isFormFieldReadonly({ key: 'b', label: 'B', type: 'text' }, true)).toBe(true)
    expect(isFormFieldReadonly({ key: 'c', label: 'C', type: 'text' }, false)).toBe(false)
  })
})
