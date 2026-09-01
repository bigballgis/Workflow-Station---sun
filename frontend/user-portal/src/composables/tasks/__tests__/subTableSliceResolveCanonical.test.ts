import { describe, expect, it } from 'vitest'
import { resolveSubTableRowsForBinding } from '../subTableSliceResolve'

/**
 * 规范 key（`dw:<name>` / `rt:<name>`）是单一真相源：命中即返回，不再叠加兄弟切片、不做名字兜底。
 *
 * 兄弟切片叠加与名字兜底都只是在补偿旧结构的「一 binding 一份 + 一别名一份」扇出
 * （FU 50005 的 subtable 表曾占 9 个 key，且实测会分叉：50539 有 2 行而 50544 只有 1 行）。
 */

const participantsBinding = {
  bindingId: 50627,
  tableName: 'subtable',          // 设计器表名
  tableDisplayName: 'Participants', // 展示名 —— 不参与 key
  primaryKeyFields: ['id_idwvvbz'],
} as any

describe('resolveSubTableRowsForBinding — 规范 key 优先', () => {
  it('命中规范 key 时直接返回该切片', () => {
    const rows = [{ id_idwvvbz: 'Test-000005' }, { id_idwvvbz: 'Test-000006' }]
    const resolved = resolveSubTableRowsForBinding({ 'dw:subtable': rows }, participantsBinding)
    expect(resolved).toBe(rows)
  })

  it('规范 key 存在时，旧 key 的分叉副本一律不参与', () => {
    // 这正是「改了值又变回旧值」的现场：旧 binding key 里留着编辑前的行。
    const canonical = [{ id_idwvvbz: 'Test-000006', name: 'EDITED' }]
    const resolved = resolveSubTableRowsForBinding(
      {
        'dw:subtable': canonical,
        50627: [{ id_idwvvbz: 'Test-000006', name: 'STALE' }],
        50539: [{ id_idwvvbz: 'Test-000006', name: 'STALE' }],
        subtable: [{ id_idwvvbz: 'Test-000006', name: 'STALE' }],
        Participants: [{ id_idwvvbz: 'Test-000006', name: 'STALE' }],
      },
      participantsBinding,
    )
    expect(resolved).toBe(canonical)
    expect(resolved?.[0]?.name).toBe('EDITED')
  })

  it('空数组也算命中 —— 表确实没有行，不该回退到旧 key', () => {
    // 旧的 tryKey 要求 length > 0 才算命中，会让「刚被清空的表」回退到陈旧副本。
    const resolved = resolveSubTableRowsForBinding(
      { 'dw:subtable': [], 50627: [{ id_idwvvbz: 'Test-000006' }] },
      participantsBinding,
    )
    expect(resolved).toEqual([])
  })

  it('同一张表的不同 binding 解析到同一份数据', () => {
    const rows = [{ id_idwvvbz: 'Test-000006' }]
    const store = { 'dw:subtable': rows }
    for (const bindingId of [50539, 50544, 50612, 50617, 50625, 50627]) {
      expect(resolveSubTableRowsForBinding(store, { ...participantsBinding, bindingId }))
        .toBe(rows)
    }
  })

  it('RT binding 解析到 rt: 命名空间', () => {
    const rows = [{ id: 1 }]
    const resolved = resolveSubTableRowsForBinding(
      { 'rt:test': rows },
      { bindingId: 50541, relationTableId: 1, relationTableName: 'test' } as any,
    )
    expect(resolved).toBe(rows)
  })

  it('没有规范 key 时不报错（交回既有解析链）', () => {
    expect(resolveSubTableRowsForBinding({}, participantsBinding)).toBeUndefined()
    expect(resolveSubTableRowsForBinding(null, participantsBinding)).toBeUndefined()
  })
})
