import { describe, expect, it } from 'vitest'
import { findMiIsolatedParentRow, findSubTableRowByMiExpansionId } from '@/composables/tasks/miLinkChildRows'
import { rowMatchesMiExpansionId } from '@/composables/tasks/miLinkChildIdentity'
import { repairMisassignedPrimaryKeyFromParentId } from '@/utils/subTableRowRuntime/primaryKeyAllocation'
import {
  clearActiveMiConfig,
  resolveSubTablePrimaryKeyFields,
  setActiveMiConfig,
} from '@/composables/tasks/useMiConfig'
import { afterEach } from 'vitest'

/**
 * 回归：MI 任务点 Save 报 `MI_CONFIG_MISSING`。
 *
 * <p>现场（task 30fb0670 / FU fu-20260422-23tfag）的 binding 实测：
 * <pre>
 *   50549 PRIMARY main       primaryKeyFields=['id']
 *   50553 SUB     attachment primaryKeyFields=['id']
 *   50627 SUB     subtable   primaryKeyFields=['id_idwnn']   ← 用户配置过的子任务主键
 *   50550 RELATED test       primaryKeyFields=null           ← 关联表，本来就没有设计器主键
 *   50551 RELATED sys_users  primaryKeyFields=null           ← 平台虚拟只读用户表
 * </pre>
 *
 * <p>子任务**确实配了主键**（`id_idwnn`），后端也正确下发了。报错来自
 * 50550 / 50551 这两个 **relation table** binding：行匹配会逐个遍历 peer binding，
 * 而关联表不是设计器子表、合法地没有 `primaryKeyFields`。
 * 之前在那里抛 `MiConfigMissingError`，整个 Save 被中断。
 *
 * <p>所以"配置缺失就报错"必须限定在**只有配置能回答且猜错会损坏数据**的地方；
 * 对"本来就没有主键的合法 binding"要按各自安全方向降级，而不是抛错。
 */

/** 用户现场的 binding 集合。 */
const BINDINGS = [
  { bindingId: 50549, tableName: 'main', primaryKeyFields: ['id'] },
  { bindingId: 50553, tableName: 'attachment', primaryKeyFields: ['id'] },
  { bindingId: 50627, tableName: 'subtable', primaryKeyFields: ['id_idwnn'] },
  { bindingId: 50550, tableName: 'test', primaryKeyFields: null },        // relation table
  { bindingId: 50551, tableName: 'sys_users', primaryKeyFields: null },   // 虚拟只读表
]

describe('Save 路径遍历 peer binding 时不得因关联表无主键而中断', () => {
  it('逐个 binding 做行匹配：无主键的关联表只是不匹配，不抛错', () => {
    const rows = [{ id_idwnn: 'Test-000001', name: 'mine' }]
    for (const b of BINDINGS) {
      expect(() =>
        findSubTableRowByMiExpansionId(rows, 'Test-000001', b.primaryKeyFields),
      ).not.toThrow()
    }
    // 配了主键的子任务 binding 正常匹配到自己的行
    expect(findSubTableRowByMiExpansionId(rows, 'Test-000001', ['id_idwnn']))
      .toEqual({ id_idwnn: 'Test-000001', name: 'mine' })
    // 关联表没有主键 → 匹配不到（而不是抛错）
    expect(findSubTableRowByMiExpansionId(rows, 'Test-000001', null)).toBeNull()
  })

  it('rowMatchesMiExpansionId 对无主键 binding 不抛错', () => {
    for (const b of BINDINGS) {
      expect(() =>
        rowMatchesMiExpansionId({ id_idwnn: 'Test-000001' }, 'Test-000001', b.primaryKeyFields),
      ).not.toThrow()
    }
    expect(rowMatchesMiExpansionId({ id_idwnn: 'T1' }, 'T1', ['id_idwnn'])).toBe(true)
  })

  it('findMiIsolatedParentRow：无主键时拒绝该行，而不是抛错、也不是放行', () => {
    // 单行分支的语义是「就一行，姑且当成我的」。无从判定归属时必须拒绝 ——
    // 放行 = 把别人的行交给当前用户编辑。
    const someoneElse = [{ id_idwnn: 'Test-000002', name: 'theirs' }]
    expect(() => findMiIsolatedParentRow(someoneElse, 'Test-000001', null)).not.toThrow()
    expect(findMiIsolatedParentRow(someoneElse, 'Test-000001', null)).toBeNull()
    // 有主键时排他判定照常生效
    expect(findMiIsolatedParentRow(someoneElse, 'Test-000001', ['id_idwnn'])).toBeNull()
    expect(findMiIsolatedParentRow([{ id_idwnn: 'Test-000001' }], 'Test-000001', ['id_idwnn']))
      .toEqual({ id_idwnn: 'Test-000001' })
  })

  it('repairMisassignedPrimaryKeyFromParentId：父表主键未知时什么都不删', () => {
    // 这个函数是删值的，不知该保护谁时保持原样才安全。
    const row = { id_idwnn: 'Test-000001', name: 'keep' }
    expect(() => repairMisassignedPrimaryKeyFromParentId(
      row, [{ fieldName: 'id_idwnn', isPrimaryKey: true }], 'Test-000001', null,
    )).not.toThrow()
    expect(repairMisassignedPrimaryKeyFromParentId(
      row, [{ fieldName: 'id_idwnn', isPrimaryKey: true }], 'Test-000001', null,
    )).toEqual(row)
  })

  it('用户改过主键名（id_idwnn）后，子任务行仍按配置的主键定位', () => {
    // 改主键字段名不是报错原因：配置驱动的匹配对任意列名都成立。
    const rows = [{ id_idwnn: 'Test-000001' }, { id_idwnn: 'Test-000002' }]
    expect(findSubTableRowByMiExpansionId(rows, 'Test-000002', ['id_idwnn']))
      .toEqual({ id_idwnn: 'Test-000002' })
  })
})

/**
 * 主键校验的**判据是表的种类**，不是调用位置：
 *   - MI 子任务表（Sub-Task Config 的 `subTableName` 指向的那张）——要按行拆分子任务，
 *     **一定要有主键**，缺失 = 配置错误 → 抛错。
 *   - 其它表（关联表 / sys_users 虚拟表 / 共享附件 / 普通子表）——**主键是可选的**，
 *     没有往往只是这张表不需要主键 → 返回 null，跳过与主键相关的判定。
 */
describe('resolveSubTablePrimaryKeyFields — 按表的种类决定要不要报错', () => {
  afterEach(() => clearActiveMiConfig())

  /** 现场 FU：Sub-Task Config 的 subTableName = subtable。 */
  const scope = {
    subTableName: 'subtable',
    assigneeField: 'assignee',
    rowIdVariable: 'currentItem.rowId',
    miTaskStatusField: null,
    miTaskCurrentNodeField: null,
    collectionVariable: null,
    elementVariable: 'currentItem',
  }

  it('子任务表配了主键 → 返回配置的主键（用户改成 id_idwnn 也照常）', () => {
    setActiveMiConfig(scope as any)
    expect(resolveSubTablePrimaryKeyFields(
      { tableName: 'subtable', primaryKeyFields: ['id_idwnn'] },
    )).toEqual(['id_idwnn'])
  })

  it('子任务表**没有**主键 → 抛错（拆不了子任务，是真的配置错误）', () => {
    setActiveMiConfig(scope as any)
    expect(() => resolveSubTablePrimaryKeyFields(
      { tableName: 'subtable', primaryKeyFields: null },
    )).toThrow(/MI_CONFIG_MISSING/)
  })

  it('关联表 / 虚拟表没有主键 → 返回 null，不抛错', () => {
    setActiveMiConfig(scope as any)
    // 现场就是这两个 binding 触发了误报，打断了用户的 Save
    expect(resolveSubTablePrimaryKeyFields({ tableName: 'test', primaryKeyFields: null })).toBeNull()
    expect(resolveSubTablePrimaryKeyFields({ tableName: 'sys_users', primaryKeyFields: null })).toBeNull()
  })

  it('共享附件等普通子表没有主键 → 返回 null，不抛错', () => {
    setActiveMiConfig(scope as any)
    expect(resolveSubTablePrimaryKeyFields({ tableName: 'attachment', primaryKeyFields: null })).toBeNull()
  })

  it('未注册 MI 配置（非 MI 表单）→ 一律不抛错', () => {
    expect(resolveSubTablePrimaryKeyFields({ tableName: 'subtable', primaryKeyFields: null })).toBeNull()
  })

  it('physicalTableName 也参与子任务表判定（展示名可能不同）', () => {
    setActiveMiConfig(scope as any)
    expect(() => resolveSubTablePrimaryKeyFields(
      { tableName: 'Participants', physicalTableName: 'subtable', primaryKeyFields: null },
    )).toThrow(/MI_CONFIG_MISSING/)
  })
})

/**
 * link-child 行（People 式）的归属由**结构 FK** 决定，不是它自己的主键。
 *
 * <p>回归：用户给 People 子表加了两行、Save 后刷新全没了。People 行的主键是行 UUID，
 * 而排他守卫拿它和参与者 id 比 —— 对 link-child 行**恒不相等**，于是刚存进去的行被判成
 * "别人的"，页面渲染 0 行，下一次保存又把它当陈旧数据丢掉。
 */
describe('findMiIsolatedParentRow — link-child 按结构 FK 判归属', () => {
  const myRow = { id: '1b526ca5-08ec-4c7f-b1b7-60c8a8567f15', age: '1', sub_task_id: 'Test-000002' }

  it('结构 FK 指向我 → 返回该行（哪怕主键是 UUID、与参与者 id 不等）', () => {
    expect(findMiIsolatedParentRow([myRow], 'Test-000002', ['id'])).toEqual(myRow)
  })

  it('结构 FK 指向别人 → 拒绝', () => {
    expect(findMiIsolatedParentRow([myRow], 'Test-000001', ['id'])).toBeNull()
  })

  it('没有结构 FK 时仍按主键做排他判定', () => {
    const collectionRow = { id_idw: 'Test-000002', name: 'mine' }
    expect(findMiIsolatedParentRow([collectionRow], 'Test-000002', ['id_idw'])).toEqual(collectionRow)
    expect(findMiIsolatedParentRow([{ id_idw: 'Test-000009' }], 'Test-000002', ['id_idw'])).toBeNull()
  })
})
