import { describe, expect, it } from 'vitest'
import { firstBatchFieldError } from '../subTableBatchValidation'

describe('batch field validation', () => {
  const fields = [{ field: 'count', label: 'Count', type: 'inputNumber', required: true, options: [], min: 0, max: 10 }]
  it('rejects a later invalid row before any batch is applied', () => {
    expect(firstBatchFieldError([{ count: 2 }, { count: 11 }], fields)).toEqual({ row: 2, field: 'count', label: 'Count' })
  })
  it('preserves false and zero as valid explicitly assigned values', () => {
    expect(firstBatchFieldError([{ count: 0 }], fields)).toBeNull()
    expect(firstBatchFieldError([{ enabled: false }], [{ field: 'enabled', label: 'Enabled', type: 'switch', required: true, options: [] }])).toBeNull()
  })
  it('rejects blank required values and numeric strings', () => {
    expect(firstBatchFieldError([{ count: null }], fields)).not.toBeNull()
    expect(firstBatchFieldError([{ count: '2' }], fields)).not.toBeNull()
  })
  it('enforces saved designer validators before applying any row', () => {
    const validated = [{ ...fields[0]!, validators: [{ type: 'integer', min: 2, max: 4 }] }]
    expect(firstBatchFieldError([{ count: 2 }, { count: 1 }], validated)?.row).toBe(2)
    expect(firstBatchFieldError([{ count: 2.5 }], validated)).not.toBeNull()
    expect(firstBatchFieldError([{ count: 3 }], validated)).toBeNull()
  })
})
