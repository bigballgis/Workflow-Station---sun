import { describe, expect, it } from 'vitest'
import { applyBatchDraft } from '../subTableBatchDraft'

describe('applyBatchDraft', () => {
  const rows = [
    { id: 'A', name: 'first', quantity: 1, hidden: 'keep' },
    { id: 'B', name: 'second', quantity: 2, hidden: 'keep-too' },
  ]

  it('keeps complete rows while applying individual and uniform values', () => {
    const result = applyBatchDraft(rows, (row) => String(row.id), {
      rowPatches: [{ rowId: 'A', fields: { name: { kind: 'set', value: 'different' } } }],
      uniformPatches: [{ rowIds: ['A', 'B'], fields: { quantity: { kind: 'set', value: 0 } } }],
    })

    expect(result).toEqual([
      { id: 'A', name: 'different', quantity: 0, hidden: 'keep' },
      { id: 'B', name: 'second', quantity: 0, hidden: 'keep-too' },
    ])
    expect(rows[0]).toEqual({ id: 'A', name: 'first', quantity: 1, hidden: 'keep' })
  })

  it('distinguishes explicit clear from false, empty string and zero', () => {
    const result = applyBatchDraft([{ id: 'A', bool: true, text: 'x', count: 3, nullable: 'value' }], (row) => String(row.id), {
      rowPatches: [{ rowId: 'A', fields: {
        bool: { kind: 'set', value: false },
        text: { kind: 'set', value: '' },
        count: { kind: 'set', value: 0 },
        nullable: { kind: 'clear' },
      } }],
    })

    expect(result[0]).toEqual({ id: 'A', bool: false, text: '', count: 0, nullable: null })
  })

  it('normalizes an edited and deleted row to delete', () => {
    const result = applyBatchDraft(rows, (row) => String(row.id), {
      rowPatches: [{ rowId: 'A', fields: { name: { kind: 'set', value: 'must-not-return' } } }],
      deletedRowIds: ['A'],
    })

    expect(result).toEqual([{ id: 'B', name: 'second', quantity: 2, hidden: 'keep-too' }])
  })
})
