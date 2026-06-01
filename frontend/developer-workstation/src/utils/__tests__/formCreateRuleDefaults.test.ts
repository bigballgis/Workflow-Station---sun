import { describe, expect, it } from 'vitest'

import {
  applyTableFieldDefaultToRule,
  applyTableFieldDefaultsToRulesAndModel,
  coerceDefaultValueForRuleType,
  resolveRuleDefaultValue,
  seedFormDataFromRules,
  walkRulesApplyTableFieldDefaultsToPersistedRules,
} from '../formCreateRuleDefaults'

describe('formCreateRuleDefaults', () => {
  const selectRule = {
    type: 'select',
    field: 'select',
    props: {
      value: '1',
      options: [
        { label: 'Option01', value: 1 },
        { label: 'Option02', value: 2 },
      ],
    },
  } as Record<string, unknown>

  it('resolveRuleDefaultValue reads props.value (Basis default)', () => {
    expect(resolveRuleDefaultValue(selectRule)).toBe(1)
  })

  it('coerceDefaultValueForRuleType matches option label', () => {
    const rule = {
      type: 'select',
      options: [{ label: 'test select', value: 1 }],
    } as Record<string, unknown>
    expect(coerceDefaultValueForRuleType('select', 'test select', rule)).toBe(1)
  })

  it('applyTableFieldDefaultToRule sets rule.value from dw field default', () => {
    const rule = { type: 'select', field: 'select', options: [{ label: 'A', value: 1 }] } as Record<
      string,
      unknown
    >
    applyTableFieldDefaultToRule(rule, { defaultValue: '1' })
    expect(rule.value).toBe(1)
  })

  it('seedFormDataFromRules fills empty preview model keys', () => {
    const target: Record<string, unknown> = {}
    seedFormDataFromRules([selectRule], target, true)
    expect(target.select).toBe(1)
  })

  it('seedFormDataFromRules does not overwrite existing values when onlyIfEmpty', () => {
    const target: Record<string, unknown> = { select: 2 }
    seedFormDataFromRules([selectRule], target, true)
    expect(target.select).toBe(2)
  })

  it('applyTableFieldDefaultsToRulesAndModel uses Table Design default when rule has none', () => {
    const rule = {
      type: 'select',
      field: 'select',
      props: {
        options: [
          { label: 'Option01', value: 1 },
          { label: 'Option02', value: 2 },
        ],
      },
    } as Record<string, unknown>
    const target: Record<string, unknown> = {}
    applyTableFieldDefaultsToRulesAndModel(
      [rule],
      [{ fieldName: 'select', defaultValue: '1' }],
      target,
      true,
    )
    expect(target.select).toBe(1)
    expect(rule.value).toBe(1)
  })

  it('walkRulesApplyTableFieldDefaultsToPersistedRules writes rule.value for portal', () => {
    const rule = {
      type: 'select',
      field: 'status',
      props: { options: [{ label: 'A', value: 'x' }] },
    } as Record<string, unknown>
    walkRulesApplyTableFieldDefaultsToPersistedRules(
      [rule],
      [{ fieldName: 'status', defaultValue: 'x' }],
    )
    expect(rule.value).toBe('x')
  })

  it('tableOverridesRule replaces stale persisted rule default with latest Table Design default', () => {
    const rule = {
      type: 'select',
      field: 'select',
      value: 1,
      props: {
        value: 1,
        options: [
          { label: 'Option01', value: 1 },
          { label: 'Option02', value: 2 },
        ],
      },
    } as Record<string, unknown>
    const target: Record<string, unknown> = { select: 1 }
    applyTableFieldDefaultsToRulesAndModel(
      [rule],
      [{ fieldName: 'select', defaultValue: '2' }],
      target,
      true,
      { tableOverridesRule: true },
    )
    expect(target.select).toBe(2)
    expect(rule.value).toBe(2)
  })

  it('walkRulesApplyTableFieldDefaultsToPersistedRules overwrites stale rule.value', () => {
    const rule = {
      type: 'select',
      field: 'select',
      value: 1,
      props: {
        value: 1,
        options: [
          { label: 'Option01', value: 1 },
          { label: 'Option02', value: 2 },
        ],
      },
    } as Record<string, unknown>
    walkRulesApplyTableFieldDefaultsToPersistedRules(
      [rule],
      [{ fieldName: 'select', defaultValue: '2' }],
    )
    expect(rule.value).toBe(2)
  })
})
