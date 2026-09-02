import { describe, expect, it } from 'vitest'
import { rowMatchesMiExpansionId } from '../miLinkChildIdentity'
import { findMiIsolatedParentRow, findSubTableRowByMiExpansionId } from '../miLinkChildRows'

/**
 * MI 参与者行的定位：**按设计器主键**匹配，不靠猜列名。
 *
 * <p>此前 `rowMatchesMiExpansionId` 只在 `['id_idw', 'rowId', 'id', 'ID', 'RowId']` 里找，
 * 主键叫别的名字的表（实测 ATM_Transaction 是 `row_id`、subtable 是 `id_idwvvbz`）
 * 匹配不到自己的行 —— 且**只在那些 FU 上复现**。
 */

const PK = ['id_idwvvbz']

describe('rowMatchesMiExpansionId — 设计器主键优先', () => {
  it('主键不叫 id_idw 时，按配置的主键匹配', () => {
    const row = { id_idwvvbz: 'Test-000016', name: 'mine' }
    expect(rowMatchesMiExpansionId(row, 'Test-000016', PK)).toBe(true)
    // 不传主键：跳过主键匹配，只走跨字段兜底（id_idw/rowId/id...）。
    // 这里 row 的 PK 叫 id_idwvvbz，不在兜底名单里 → 匹配不到。
    // **不抛错**：本函数会被逐个 peer binding 调用，共享附件等本就没有设计器 PK，
    // 抛错会中断整个 Save（实测用户点 Save 报 MI_CONFIG_MISSING）。
    expect(rowMatchesMiExpansionId(row, 'Test-000016')).toBe(false)
  })

  it('PK 叫 row_id 的表（ATM_Transaction）同样生效', () => {
    const row = { row_id: 'R-7' }
    expect(rowMatchesMiExpansionId(row, 'R-7', ['row_id'])).toBe(true)
  })

  it('不匹配别人的行', () => {
    const row = { id_idwvvbz: 'Test-000017' }
    expect(rowMatchesMiExpansionId(row, 'Test-000016', PK)).toBe(false)
  })

  it('名字列表保留为兜底：rowId 是设计器 PK 而行只有 SQL id 时仍可匹配', () => {
    expect(rowMatchesMiExpansionId({ id: 6532 }, 6532, PK)).toBe(true)
  })

  it('空 miRowId 不匹配', () => {
    expect(rowMatchesMiExpansionId({ id_idwvvbz: 'X' }, '', PK)).toBe(false)
  })
})

describe('findSubTableRowByMiExpansionId', () => {
  it('在多参与者行里按主键挑出自己的那行', () => {
    const rows = [{ id_idwvvbz: 'Test-000016' }, { id_idwvvbz: 'Test-000017' }]
    expect(findSubTableRowByMiExpansionId(rows, 'Test-000017', PK))
      .toEqual({ id_idwvvbz: 'Test-000017' })
  })
})

describe('findMiIsolatedParentRow — 单行排他守卫', () => {
  it('唯一那行是别的参与者时返回 null（不把别人的行当自己的）', () => {
    // 回归：守卫此前只读 rec.id_idw，PK 叫别的名字时恒为空 → 守卫失效 → 返回别人的行。
    const rows = [{ id_idwvvbz: 'Test-000017', name: 'theirs' }]
    expect(findMiIsolatedParentRow(rows, 'Test-000016', PK)).toBeNull()
  })

  it('唯一那行就是自己时返回该行', () => {
    const rows = [{ id_idwvvbz: 'Test-000016', name: 'mine' }]
    expect(findMiIsolatedParentRow(rows, 'Test-000016', PK))
      .toEqual({ id_idwvvbz: 'Test-000016', name: 'mine' })
  })

  it('唯一那行没有主键值时放行（保存前的在编辑行）', () => {
    const rows = [{ name: 'in-progress' }]
    expect(findMiIsolatedParentRow(rows, 'Test-000016', PK)).toEqual({ name: 'in-progress' })
  })

  it('多行且无一匹配时返回 null', () => {
    const rows = [{ id_idwvvbz: 'A' }, { id_idwvvbz: 'B' }]
    expect(findMiIsolatedParentRow(rows, 'Test-000016', PK)).toBeNull()
  })
})
