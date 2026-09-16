import { describe, expect, it } from 'vitest'
import {
  buildBindingScope,
  stampCanonicalStoreRows,
  storeKeysSharedByMultipleBindings,
} from '../subTableCanonicalStamp'

const meeting = {
  bindingId: 101,
  tableName: 'attachment',
  primaryKeyFields: ['id'],
}
const participant = {
  bindingId: 202,
  tableName: 'attachment',
  primaryKeyFields: ['id'],
}

describe('subTableCanonicalStamp', () => {
  it('marks a table as shared only when two bindings use the same store key', () => {
    expect(storeKeysSharedByMultipleBindings([meeting, participant])).toEqual(new Set(['dw:attachment']))
    expect(storeKeysSharedByMultipleBindings([meeting])).toEqual(new Set())
  })

  it('keeps Meeting rows when a later Participant binding writes a subset', () => {
    const subTables: Record<string, unknown> = {}
    const subTableData: Record<string, Array<Record<string, unknown>>> = {}
    const shared = storeKeysSharedByMultipleBindings([meeting, participant])
    stampCanonicalStoreRows(
      subTables,
      subTableData,
      meeting,
      [
        { id: 'X', main_id: 'M001' },
        { id: 'Y', main_id: 'M001', participant_id: 'P-A' },
        { id: 'Z', main_id: 'M001', participant_id: 'P-B' },
      ],
      shared,
    )
    stampCanonicalStoreRows(
      subTables,
      subTableData,
      participant,
      [{ id: 'Y', main_id: 'M001', participant_id: 'P-A', title: 'updated' }],
      shared,
    )
    const rows = subTables['dw:attachment'] as Array<Record<string, unknown>>
    expect(rows.map(r => r.id)).toEqual(['X', 'Y', 'Z'])
    expect(rows.find(r => r.id === 'Y')?.title).toBe('updated')
  })

  it('emits a scope with the binding id and remaining row keys', () => {
    const scope = buildBindingScope(participant, [{ id: 'Y', participant_id: 'P-A' }], false)
    expect(scope).toEqual({
      bindingId: '202',
      storeKey: 'dw:attachment',
      rowKeys: [{ id: 'Y' }],
      emptied: false,
    })
  })

  it('does not replace a unique store key with a merge of a previous table', () => {
    const other = { bindingId: 9, tableName: 'people', primaryKeyFields: ['id'] }
    const subTables: Record<string, unknown> = {}
    const subTableData: Record<string, Array<Record<string, unknown>>> = {}
    const shared = storeKeysSharedByMultipleBindings([meeting, other])
    stampCanonicalStoreRows(subTables, subTableData, meeting, [{ id: 'X' }], shared)
    stampCanonicalStoreRows(subTables, subTableData, other, [{ id: 'P1' }], shared)
    expect(subTables['dw:attachment']).toEqual([{ id: 'X' }])
    expect(subTables['dw:people']).toEqual([{ id: 'P1' }])
  })
})
