import { describe, expect, it } from 'vitest'
import { createBatchControlId } from '../batchSubTableConfig'

describe('createBatchControlId', () => {
  it('creates a non-empty, stable-format id for each new sub-table control', () => {
    const first = createBatchControlId()
    const second = createBatchControlId()

    expect(first).toMatch(/^stbc_[0-9a-f-]+$/i)
    expect(second).toMatch(/^stbc_[0-9a-f-]+$/i)
    expect(second).not.toBe(first)
  })
})
