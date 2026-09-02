import { describe, it, expect } from 'vitest'
import { miParentRowAlignsWithChildRow } from '../miLinkChildIdentity'
import { scopeMiLinkChildRowsForParentRow } from '../miLinkChildRows'

/**
 * 现场回归（fu-20260422-23tfag，participant Test-000004）：
 * MI collection 的设计器主键叫 `id_idwxwc`，People 的结构外键叫 `sub_task_idqc`。
 *
 * 改动前 miParentRowAlignsWithChildRow 只认 id_idw / rowId / participant_id / participantId / id，
 * 于是 parentPk 恒为 undefined → 子行明明 sub_task_idqc === 宿主 id_idwxwc 也判 false，
 * 嵌套切片被过滤成 0 行，刷新后用户刚存的 People 行全部消失。
 */
describe('MI 宿主行按设计器主键匹配子行', () => {
  const fkConfig = {
    fieldDefinitions: [
      { fieldName: 'idqc', isPrimaryKey: true },
      { fieldName: 'sub_task_idqc', isForeignKey: true, refTableId: 50331 },
    ],
    primaryKeyFields: ['idqc'],
  }
  const host = { id_idwxwc: 'Test-000004', name: 'z' }
  const mine = { idqc: 'u-1', sub_task_idqc: 'Test-000004', age: 'E2E-AGE-42' }
  const theirs = { idqc: 'u-2', sub_task_idqc: 'Test-000003', age: '9' }

  it('主键改名后仍能认出自己的子行', () => {
    expect(miParentRowAlignsWithChildRow(mine, mine, fkConfig, ['id_idwxwc'])).toBeDefined()
    expect(miParentRowAlignsWithChildRow(host, mine, fkConfig, ['id_idwxwc'])).toBe(true)
  })

  it('别的参与者的行仍然被排除', () => {
    expect(miParentRowAlignsWithChildRow(host, theirs, fkConfig, ['id_idwxwc'])).toBe(false)
  })

  it('切片按宿主行收敛：只留自己的行', () => {
    const out = scopeMiLinkChildRowsForParentRow(host, [mine, theirs], fkConfig, ['id_idwxwc'])
    expect(out).toEqual([mine])
  })

  it('没传设计器主键时退回历史列名，老 FU 行为不变', () => {
    const legacyHost = { id_idw: 'Test-000004' }
    const legacyChild = { id: 'u-1', sub_task_id: 'Test-000004' }
    const legacyCfg = {
      fieldDefinitions: [{ fieldName: 'sub_task_id', isForeignKey: true, refTableId: 1 }],
    }
    expect(miParentRowAlignsWithChildRow(legacyHost, legacyChild, legacyCfg)).toBe(true)
  })
})
