import { describe, it, expect } from 'vitest'
import { applyUnionFindMergeToBindingList } from '../subTableRowHelpers'

/**
 * Regression: My Request sub-tables (e.g. Participants) shuffled order between page refreshes because
 * the merge preserved whichever async source populated the rows first. The align step now sorts the
 * merged snapshot by a stable per-row key (id_idw → sub_task_id → id) so order is deterministic.
 */
describe('applyUnionFindMergeToBindingList — deterministic row order', () => {
  const makeBinding = (bindingId: number, rows: any[]) => ({
    bindingId,
    tableId: 700,
    tableName: 'participants',
    data: rows,
  })

  it('sorts MI rows by id_idw regardless of incoming order', () => {
    const reversed = [
      { id_idw: 'Test-000019', name: 'b' },
      { id_idw: 'Test-000018', name: 'a' },
    ]
    const all = [makeBinding(1, reversed)] as any[]
    applyUnionFindMergeToBindingList(all)
    expect(all[0].data.map((r: any) => r.id_idw)).toEqual(['Test-000018', 'Test-000019'])
  })

  it('produces the same order whichever binding chunk was seen first', () => {
    const orderA = [
      makeBinding(1, [{ id_idw: 'Test-000018', name: 'a' }]),
      makeBinding(2, [{ id_idw: 'Test-000019', name: 'b' }]),
    ] as any[]
    const orderB = [
      makeBinding(2, [{ id_idw: 'Test-000019', name: 'b' }]),
      makeBinding(1, [{ id_idw: 'Test-000018', name: 'a' }]),
    ] as any[]
    applyUnionFindMergeToBindingList(orderA)
    applyUnionFindMergeToBindingList(orderB)
    const idsA = orderA[0].data.map((r: any) => r.id_idw)
    const idsB = orderB[0].data.map((r: any) => r.id_idw)
    expect(idsA).toEqual(['Test-000018', 'Test-000019'])
    expect(idsB).toEqual(['Test-000018', 'Test-000019'])
  })

  it('is numeric-aware (Test-000002 before Test-000010)', () => {
    const all = [
      makeBinding(1, [
        { id_idw: 'Test-000010', name: 'ten' },
        { id_idw: 'Test-000002', name: 'two' },
      ]),
    ] as any[]
    applyUnionFindMergeToBindingList(all)
    expect(all[0].data.map((r: any) => r.id_idw)).toEqual(['Test-000002', 'Test-000010'])
  })

  it('falls back to id when id_idw is absent', () => {
    const all = [
      makeBinding(1, [
        { id: 'b41ba72c', file: 'c' },
        { id: 'a3149247', file: 'a' },
      ]),
    ] as any[]
    applyUnionFindMergeToBindingList(all)
    expect(all[0].data.map((r: any) => r.id)).toEqual(['a3149247', 'b41ba72c'])
  })

  it('leaves rows untouched when no stable key is present on every row', () => {
    const rows = [{ name: 'z' }, { name: 'a' }]
    const all = [makeBinding(1, rows)] as any[]
    applyUnionFindMergeToBindingList(all)
    expect(all[0].data.map((r: any) => r.name)).toEqual(['z', 'a'])
  })
})
