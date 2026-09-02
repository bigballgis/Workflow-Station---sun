import { describe, expect, it } from 'vitest'
import { finalizeSharedProcessSubTableBindingRows } from '../sharedProcessSubTableFilters'

/**
 * 共享附件表的「泄漏行」防线之一是：带**结构外键指向 MI 参与者**的行不是附件行。
 *
 * <p>该判据原先读一张写死的列名表（sub_task_id / participant_id / …）。列名配置化之后，
 * 这个调用点若不把 binding 的字段定义传进去，判据就恒返回 null —— 防线静默失效，
 * 只剩「非 id 列全空」那条兜底，于是一条**恰好填了附件列**的 link-child 行会漏进附件表格。
 *
 * <p>现场列名（改名后）：attachment 主键 idfa、外键 main_idva→main；people 外键 sub_task_idqc→subtable。
 */
const ATTACHMENT_COLUMNS = [{ field: 'idfa' }, { field: 'main_idva' }, { field: 'file' }]

describe('共享附件泄漏过滤 — 结构外键按配置识别', () => {
  it('link-child 行即使填满附件列也不得进入附件表格', () => {
    const binding = {
      tableName: 'attachment', tableId: 50330, foreignKeyField: 'main_idva',
      columns: ATTACHMENT_COLUMNS,
      fieldDefinitions: [{ fieldName: 'sub_task_idqc', isForeignKey: true, refTableId: 50331 }],
    }
    const leaked = [{ idfa: 'y', sub_task_idqc: 'Test-000003', main_idva: 'Meeting-000002' }]
    expect(finalizeSharedProcessSubTableBindingRows(leaked, binding as never)).toEqual([])
  })

  it('真正的附件行保留', () => {
    const binding = {
      tableName: 'attachment', tableId: 50330, foreignKeyField: 'main_idva',
      columns: ATTACHMENT_COLUMNS,
      fieldDefinitions: [{ fieldName: 'main_idva', isForeignKey: true, refTableId: 50332 }],
    }
    const real = [{ idfa: 'z', main_idva: 'Meeting-000002', file: 'a.pdf' }]
    expect(finalizeSharedProcessSubTableBindingRows(real, binding as never)).toHaveLength(1)
  })

  it('原 #ghost-row bug 的只有 id 的行仍被挡住', () => {
    // 主键改名成 idfa：主键列不算「本表有数据」，靠 primaryKeyFields 配置识别
    const binding = {
      tableName: 'attachment', columns: ATTACHMENT_COLUMNS, primaryKeyFields: ['idfa'],
    }
    expect(finalizeSharedProcessSubTableBindingRows(
      [{ idfa: '9d4e0000-0000-4000-8000-000000000001' }], binding as never)).toEqual([])
  })
})
