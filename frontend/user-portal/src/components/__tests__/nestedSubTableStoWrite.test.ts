import { describe, it, expect } from 'vitest'
import { mergeNestedSubTableRowsIntoSto } from '../formRendererHelpers'
import { pullNestedRowsForBindingFromParentRows } from '@/composables/tasks/subTableNestedRows'

/**
 * PortalFormFields nested sub-table editing gap: rows edited in a nested SubTableField
 * must be written into the host row's __subTables__ with the same key convention the
 * Link Form persistence writer (saveLinkedFormData) and the nested-rows reader
 * (pullNestedRowsForBindingFromParentRows) use.
 */
describe('mergeNestedSubTableRowsIntoSto', () => {
  const binding = { bindingId: 66, tableName: 'grandchild_table' }
  const rows = [{ id: 1, name: 'row-a' }, { id: 2, name: 'row-b' }]

  it('writes rows under exactly one canonical key', () => {
    // 一张表一个 key：不再同时写 bindingId 与表名两份副本。
    const sto = mergeNestedSubTableRowsIntoSto([], binding, rows)
    expect(sto['dw:grandchild_table']).toBe(rows)
    expect(Object.keys(sto)).toEqual(['dw:grandchild_table'])
  })

  it('writes nothing when the table name is blank (key unresolvable — never guesses)', () => {
    const sto = mergeNestedSubTableRowsIntoSto([], { bindingId: 66, tableName: '  ' }, rows)
    expect(Object.keys(sto)).toEqual([])
  })

  it('preserves sibling slices from existing sources', () => {
    const parentRow = { __subTables__: { '70': [{ id: 9 }], other_table: [{ id: 9 }] } }
    const sto = mergeNestedSubTableRowsIntoSto([parentRow], binding, rows)
    expect(sto['70']).toEqual([{ id: 9 }])
    expect(sto['other_table']).toEqual([{ id: 9 }])
    expect(sto['dw:grandchild_table']).toBe(rows)
  })

  it('later sources win over earlier ones (model over stale parentRow)', () => {
    const parentRow = { __subTables__: { '70': [{ id: 1, v: 'stale' }] } }
    const model = { __subTables__: { '70': [{ id: 1, v: 'fresh' }] } }
    const sto = mergeNestedSubTableRowsIntoSto([parentRow, model], binding, rows)
    expect(sto['70']).toEqual([{ id: 1, v: 'fresh' }])
  })

  it('ignores null / non-object sources and array-shaped __subTables__', () => {
    const badArray = { __subTables__: [{ id: 1 }] as unknown }
    const sto = mergeNestedSubTableRowsIntoSto(
      [null, undefined, {}, badArray as Record<string, unknown>],
      binding,
      rows,
    )
    expect(sto['dw:grandchild_table']).toBe(rows)
    expect(Object.keys(sto).sort()).toEqual(['dw:grandchild_table'])
  })

  it('round-trips through pullNestedRowsForBindingFromParentRows (the runtime reader)', () => {
    const sto = mergeNestedSubTableRowsIntoSto([], binding, rows)
    const hostRow = { id: 42, __subTables__: sto }
    const resolved = pullNestedRowsForBindingFromParentRows(
      { bindingId: 66, tableName: 'grandchild_table', tableId: null },
      [hostRow],
    )
    expect(resolved).toEqual(rows)
  })

  it('round-trips by table name alone when reader binding id differs (copied-binding scenario)', () => {
    const sto = mergeNestedSubTableRowsIntoSto([], binding, rows)
    const hostRow = { id: 42, __subTables__: sto }
    const resolved = pullNestedRowsForBindingFromParentRows(
      { bindingId: 999, tableName: 'grandchild_table', tableId: null },
      [hostRow],
    )
    expect(resolved).toEqual(rows)
  })
})
