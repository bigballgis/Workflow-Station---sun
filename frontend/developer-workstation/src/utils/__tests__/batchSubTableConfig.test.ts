import { describe, expect, it } from 'vitest'
import {
  createBatchControlId,
  hasEnabledBatchOperation,
  validateEnabledBatchControlIds,
  validateEnabledBatchForms,
} from '../batchSubTableConfig'

describe('createBatchControlId', () => {
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
