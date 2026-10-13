import { describe, expect, it } from 'vitest'
import { applyBatchAssignment, applyBatchDraft } from '../subTableBatchDraft'

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

  it('keeps new rows separate from existing-row identity patches', () => {
    const result = applyBatchDraft(rows, (row) => String(row.id), {
      rowPatches: [{ rowId: 'A', fields: { quantity: { kind: 'set', value: 3 } } }],
      addedRows: [{ name: 'new', quantity: 1 }],
    })

    expect(result).toEqual([
      { id: 'A', name: 'first', quantity: 3, hidden: 'keep' },
      { id: 'B', name: 'second', quantity: 2, hidden: 'keep-too' },
      { name: 'new', quantity: 1 },
    ])
  })

  it('uses the configured assignment fields and clears the inactive mode atomically', () => {
    const config = { assigneeField: 'assigned_to', roleField: 'role_code', buField: 'bu_code' }
    const role = applyBatchAssignment(
      { id: 'A', assigned_to: 'old-user', role_code: null, bu_code: null, untouched: 'keep' }, config,
      { mode: 'role', roleCode: 'REVIEWER', businessUnitCode: 'EU' },
    )
    expect(role).toEqual({ id: 'A', assigned_to: null, role_code: 'REVIEWER', bu_code: 'EU', untouched: 'keep' })

    const person = applyBatchAssignment(role, config, { mode: 'person', assigneeId: 'user-2' })
    expect(person).toEqual({ id: 'A', assigned_to: 'user-2', role_code: null, bu_code: null, untouched: 'keep' })
  })

  it('fails closed when the frozen assignment mapping does not support the chosen mode', () => {
    expect(() => applyBatchAssignment({}, { roleField: 'role_code' }, { mode: 'person', assigneeId: 'user-1' }))
      .toThrow('no configured assignee field')
    expect(() => applyBatchAssignment({}, { assigneeField: 'assigned_to' }, { mode: 'role', roleCode: 'REVIEWER' }))
      .toThrow('no configured role field')
  })
})
