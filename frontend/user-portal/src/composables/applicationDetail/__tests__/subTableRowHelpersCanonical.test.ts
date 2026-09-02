import { describe, expect, it } from 'vitest'
import { getSavedSubTableRowsFromVariables } from '../subTableRowHelpers'

/**
 * My Request（申请详情）读 `__subTables__` 的入口。
 *
 * <p>回归背景：规范 key 改造只切了任务保存链路，本函数仍只按 binding id 取值，
 * 于是新实例（`__subTables__` 只有 `dw:subtable`）在 My Request 上解析为 undefined，
 * MI 子表整块不渲染 —— `verify-myrequest-details-modal.mjs` 实测报
 * "application … has no MI collection rows on My Request"。
 *
 * <p>旧实例仍是 binding id key，故过渡期两种都要认。
 */

const participantsBinding = {
  bindingId: 50627,
  tableName: 'subtable',            // 设计器表名
  tableDisplayName: 'Participants', // 展示名 —— 不参与 key
}

describe('getSavedSubTableRowsFromVariables — 规范 key', () => {
  it('新实例：只有规范 key 时也能解析出行（回归 My Request 空白）', () => {
    const rows = [{ id_idwvvbz: 'Test-000016' }, { id_idwvvbz: 'Test-000017' }]
    expect(getSavedSubTableRowsFromVariables({ 'dw:subtable': rows }, participantsBinding))
      .toEqual(rows)
  })

  it('同一张表的不同 binding 解析到同一份数据', () => {
    // 实测 subtable 被 6 个 binding 绑定；My Request 会按各节点表单的 binding 反复解析。
    const store = { 'dw:subtable': [{ id_idwvvbz: 'Test-000016' }] }
    for (const bindingId of [50539, 50544, 50612, 50617, 50625, 50627]) {
      expect(getSavedSubTableRowsFromVariables(store, { ...participantsBinding, bindingId }))
        .toHaveLength(1)
    }
  })

  it('规范 key 命中时，旧 binding key 的陈旧副本不参与', () => {
    const resolved = getSavedSubTableRowsFromVariables(
      {
        'dw:subtable': [{ id_idwvvbz: 'Test-000016', name: 'EDITED' }],
        50627: [{ id_idwvvbz: 'Test-000016', name: 'STALE' }],
      },
      participantsBinding,
    )
    expect(resolved?.[0]?.name).toBe('EDITED')
  })

  it('RT binding 解析到 rt: 命名空间，不去 dw: 里找', () => {
    // 调用方若不透传 relationTableId/Name，binding 会被当成 DW 表，rt: 切片永远解析不到。
    const rtRows = [{ id: 1 }]
    const resolved = getSavedSubTableRowsFromVariables(
      { 'rt:test': rtRows, 'dw:test': [{ id: 999 }] },
      { bindingId: 50541, tableName: 'test', relationTableId: 1, relationTableName: 'test' },
    )
    expect(resolved).toEqual(rtRows)
  })

  it('过渡期：旧实例的 binding id key 仍然认', () => {
    const rows = [{ id_idwvvbz: 'Test-000006' }]
    expect(getSavedSubTableRowsFromVariables({ 50627: rows }, participantsBinding)).toEqual(rows)
    expect(getSavedSubTableRowsFromVariables({ '50627': rows }, participantsBinding)).toEqual(rows)
  })

  it('不按表名兜底 —— 共享展示名 key 无从判断行属于哪个 binding', () => {
    const rows = [{ id_idwvvbz: 'Test-000006' }]
    expect(getSavedSubTableRowsFromVariables({ subtable: rows }, participantsBinding)).toBeUndefined()
    expect(getSavedSubTableRowsFromVariables({ Participants: rows }, participantsBinding)).toBeUndefined()
  })

  it('输入无效或无数据时返回 undefined', () => {
    expect(getSavedSubTableRowsFromVariables(null, participantsBinding)).toBeUndefined()
    expect(getSavedSubTableRowsFromVariables({}, participantsBinding)).toBeUndefined()
    expect(getSavedSubTableRowsFromVariables({ 'dw:subtable': 'nope' as any }, participantsBinding))
      .toBeUndefined()
  })
})
