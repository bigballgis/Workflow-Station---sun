import { describe, expect, it } from 'vitest'
import {
  createBatchControlId,
  hasEnabledBatchOperation,
  validateEnabledBatchControlIds,
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
})
