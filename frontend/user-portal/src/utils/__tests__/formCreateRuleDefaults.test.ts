import { describe, expect, it } from 'vitest'

import {
  applyRuleDefaultToFormField,
  applyTableFieldDefaultsToRulesAndModel,
  resolveRuleDefaultValue,
  seedFormDataFromRules,
} from '../formCreateRuleDefaults'

describe('formCreateRuleDefaults (portal)', () => {
  it('applyRuleDefaultToFormField sets field.defaultValue from rule.props.value', () => {
    const field: { defaultValue?: unknown } = {}
    const rule = {
      type: 'select',
      field: 'select',
      props: {
        value: '1',
        options: [{ label: 'Option01', value: 1 }],
      },
    } as Record<string, unknown>
    applyRuleDefaultToFormField(field, rule)
    expect(field.defaultValue).toBe(1)
  })

  it('seedFormDataFromRules seeds model for FormRenderer-like init', () => {
    const model: Record<string, unknown> = {}
    seedFormDataFromRules(
      [{ type: 'input', field: 'id', value: 'AUTO-1' } as Record<string, unknown>],
      model,
      true,
    )
    expect(model.id).toBe('AUTO-1')
  })

  it('applyRuleDefaultToFormField prefers Table Design over stale rule.props.value', () => {
    const field: { defaultValue?: unknown } = {}
    const rule = {
      type: 'select',
      field: 'select',
      props: {
        value: 1,
        options: [
          { label: 'Option01', value: 1 },
          { label: 'Option02', value: 2 },
        ],
      },
    } as Record<string, unknown>
    applyRuleDefaultToFormField(field, rule, { fieldName: 'select', defaultValue: '2' })
    expect(field.defaultValue).toBe(2)
  })

  it('applyTableFieldDefaultsToRulesAndModel fills select from table metadata', () => {
    const rule = {
      type: 'select',
      field: 'select',
      props: { options: [{ label: 'Option01', value: 1 }] },
    } as Record<string, unknown>
    const model: Record<string, unknown> = {}
    applyTableFieldDefaultsToRulesAndModel(
      [rule],
      [{ fieldName: 'select', defaultValue: '1' }],
      model,
      true,
    )
    expect(model.select).toBe(1)
  })
})
