import { describe, expect, it } from 'vitest'
import { sameSubTableRow } from '@/composables/tasks/shared'

/**
 * 「删掉一个 kk，另一个 kk 变成了 u」（2026-09-03 真机实测，task 9c46d613）。
 *
 * <p>那不是「删不掉」，而是**删错了行**：删除只拿到 el-table 的渲染下标 `scope.$index`，
 * 而确认框是异步的 —— 用户确认期间数组可能已经变化（别处保存、重新 hydrate、行序变动），
 * 此时 `splice(i, 1)` 删掉的是另一行，剩下的值看起来就「错位」了。
 *
 * <p>两行业务字段完全相同（都是 `kk`）时最容易暴露：肉眼分不出删掉的是哪一条，
 * 但它们的 `idqcxma`（设计器主键）不同 —— 所以定位必须按**身份**，不能按下标。
 */
describe('sub-table row delete must target the row, not the index', () => {
  const PK = ['idqcxma']

  const mkRows = () => [
    { idqcxma: 'ee396ddb', age: 'kk', sub_task_idqcxma: 'Test-000012' },
    { idqcxma: 'edaefde2', age: 'kk', sub_task_idqcxma: 'Test-000012' },
    { idqcxma: 'c49e666c', age: 'u', sub_task_idqcxma: 'Test-000012' },
  ]

  /** useSubTableRowDialog.deleteRow 的定位逻辑（身份优先，下标兜底）。 */
  function resolveDeleteIndex(rows: any[], i: number, row?: any): number {
    let idx = -1
    if (row) {
      idx = rows.indexOf(row)
      if (idx < 0) idx = rows.findIndex(r => sameSubTableRow(r, row, PK))
    }
    if (idx < 0) {
      if (i < 0 || i >= rows.length) return -1
      idx = i
    }
    return idx
  }

  it('index-only deletion removes the WRONG row once the array shifted', () => {
    const rows = mkRows()
    // 用户点的是第 2 行（edaefde2）；确认期间前面插入/移除导致行序变化
    const clicked = rows[1]
    rows.splice(0, 1) // 数组已变：现在下标 1 指向 c49e666c
    // 旧行为：只信下标
    const staleIndex = 1
    rows.splice(staleIndex, 1)
    expect(rows.map(r => r.idqcxma)).toEqual(['edaefde2'])
    // 被删的是 c49e666c，而用户点的是 edaefde2 —— 删错了
    expect(rows.some(r => r.idqcxma === clicked.idqcxma)).toBe(true)
  })

  it('identity-based deletion removes exactly the clicked row', () => {
    const rows = mkRows()
    const clicked = rows[1] // edaefde2，与 rows[0] 的 age 同为 kk
    const idx = resolveDeleteIndex(rows, 1, clicked)
    rows.splice(idx, 1)
    expect(rows.map(r => r.idqcxma)).toEqual(['ee396ddb', 'c49e666c'])
    // 另一条 kk 的值必须原样保留 —— 不能变成 u
    expect(rows.find(r => r.idqcxma === 'ee396ddb')?.age).toBe('kk')
  })

  it('identity wins even when the stale index points elsewhere', () => {
    const rows = mkRows()
    const clicked = rows[2] // c49e666c
    const idx = resolveDeleteIndex(rows, 0, clicked) // 故意给错的下标
    rows.splice(idx, 1)
    expect(rows.map(r => r.idqcxma)).toEqual(['ee396ddb', 'edaefde2'])
  })

  it('two rows identical in business fields are still told apart by PK', () => {
    const a = { idqcxma: 'ee396ddb', age: 'kk' }
    const b = { idqcxma: 'edaefde2', age: 'kk' }
    expect(sameSubTableRow(a, b, PK)).toBe(false)
    expect(sameSubTableRow(a, { ...a }, PK)).toBe(true)
  })
})
