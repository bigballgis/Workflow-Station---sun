import { describe, expect, it } from 'vitest'
import {
  createBatchControlId,
  hasEnabledBatchOperation,
  initializeBatchControlIdOnEnable,
  validateEnabledBatchControlIds,
  validateEnabledBatchForms,
  findBatchFormIssue,
} from '../batchSubTableConfig'

describe('createBatchControlId', () => {
  it('gives different actionable reasons for an absent batch form and assignment component', () => {
    const rule = { title: 'New lines', _bindingId: 101, props: { allowBatchAssign: true } }
    expect(findBatchFormIssue([rule], {})).toEqual({ key: 'batchMissingForm', params: { subTable: 'New lines' } })
    expect(findBatchFormIssue([rule], { '101': { schemaVersion: 1, rule: [{ type: 'input', field: 'name' }] } }))
      .toEqual({ key: 'batchMissingAssignment', params: { subTable: 'New lines' } })
  })
  it('initializes a legacy control only on explicit boolean opt-in and retains it across toggles', () => {
    const rule = { props: { allowBatchEdit: false } } as { batchControlId?: string; props: Record<string, unknown> }
    for (const value of [undefined, false, 'true', 1]) initializeBatchControlIdOnEnable({ value, rule })
    expect(rule.batchControlId).toBeUndefined()
    initializeBatchControlIdOnEnable({ value: true, rule })
    const id = rule.batchControlId
    expect(id).toMatch(/^stbc_[0-9a-f-]+$/i)
    expect(rule.props.batchControlId).toBe(id)
    initializeBatchControlIdOnEnable({ value: false, rule })
    initializeBatchControlIdOnEnable({ value: true, rule })
    expect(rule.batchControlId).toBe(id)
  })

  it('does not repair malformed or duplicated existing identities on activation', () => {
    for (const id of ['', null, 'existing']) {
      const rule = { batchControlId: id, props: {} }
      initializeBatchControlIdOnEnable({ value: true, rule })
      expect(rule.batchControlId).toBe(id)
      const propsRule = { props: { batchControlId: id } }
      initializeBatchControlIdOnEnable({ value: true, rule: propsRule })
      expect(propsRule.props.batchControlId).toBe(id)
    }
    const a = { props: {} }; const b = { props: {} }
    initializeBatchControlIdOnEnable({ value: true, rule: a })
    initializeBatchControlIdOnEnable({ value: true, rule: b })
    expect(validateEnabledBatchControlIds([a, b].map(rule => ({ ...rule, props: { ...rule.props, allowBatchEdit: true } })))).toBe(true)
  })

  it('creates a non-empty, stable-format id for each new sub-table control', () => {
    const first = createBatchControlId()
    const second = createBatchControlId()

    expect(first).toMatch(/^stbc_[0-9a-f-]+$/i)
    expect(second).toMatch(/^stbc_[0-9a-f-]+$/i)
    expect(second).not.toBe(first)
  })

  it('enables batch operations only for the boolean true value', () => {
    expect(hasEnabledBatchOperation({ props: { allowBatchEdit: true } })).toBe(true)
    expect(hasEnabledBatchOperation({ props: { allowBatchEdit: 'true' } })).toBe(false)
    expect(hasEnabledBatchOperation({ props: { allowBatchEdit: 1 } })).toBe(false)
  })

  it('requires a unique ID only for controls with an enabled batch operation', () => {
    expect(validateEnabledBatchControlIds([
      { props: { allowBatchEdit: false } },
      { props: { allowBatchEdit: true, batchControlId: 'one' } },
    ])).toBe(true)
    expect(validateEnabledBatchControlIds([{ props: { allowBatchEdit: true } }])).toBe(false)
    expect(validateEnabledBatchControlIds([
      { props: { allowBatchAdd: true, batchControlId: 'same' } },
      { props: { allowBatchDelete: true, batchControlId: 'same' } },
    ])).toBe(false)
  })

  it('requires a versioned, non-empty independent batch form for enabled controls', () => {
    const enabled = [{ _bindingId: 101, props: { allowBatchEdit: true } }]
    expect(validateEnabledBatchForms(enabled, {
      '101': { schemaVersion: 1, rule: [{ type: 'input', field: 'name' }] },
    })).toBe(true)
    expect(validateEnabledBatchForms(enabled, undefined)).toBe(false)
    expect(validateEnabledBatchForms(enabled, {
      '101': { schemaVersion: 2, rule: [{ type: 'input' }] },
    })).toBe(false)
    expect(validateEnabledBatchForms(enabled, {
      '101': { schemaVersion: 1, rule: [] },
    })).toBe(false)
  })

  it('requires the dedicated assignment component when Batch Assign is enabled', () => {
    const assign = [{ _bindingId: 101, props: { allowBatchAssign: true } }]
    expect(validateEnabledBatchForms(assign, {
      '101': { schemaVersion: 1, rule: [{ type: 'input', field: 'name' }] },
    })).toBe(false)
    expect(validateEnabledBatchForms(assign, {
      '101': { schemaVersion: 1, rule: [{ type: 'fcRow', children: [{ type: 'miAssignment' }] }] },
    })).toBe(true)
  })
})
