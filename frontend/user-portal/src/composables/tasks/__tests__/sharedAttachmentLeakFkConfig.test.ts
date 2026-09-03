import { describe, expect, it } from 'vitest'
import { finalizeSharedProcessSubTableBindingRows } from '../sharedProcessSubTableFilters'
import { setActiveMiKindTableIds } from '../useMiConfig'
import { isSharedAttachmentFileBinding } from '../subTableBindingKinds'

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
/** 附件表的身份由设计器列类型决定：必须有 data_type='FILE' 的列。 */
const FILE_FIELD = { fieldName: 'file', dataType: 'FILE' }
const ATTACHMENT_PK = { fieldName: 'idfa', isPrimaryKey: true }

describe('共享附件泄漏过滤 — 结构外键按配置识别', () => {
  /**
   * 一张表**同时**声明了指向主表和指向 collection 的外键时，它按 participant-child 处理
   * （`isSharedAttachmentFileBinding` 返回 false），走参与者分片而不是共享附件路径。
   *
   * <p>这一条锁的是分类本身：曾经只要表名叫 attachment 就当共享附件，于是一张**按参与者
   * 私有**的附件表会被全案共享，参与者互相看到对方的附件。
   */
  it('同时指向 collection 的附件表不按共享附件处理（参与者私有）', () => {
    setActiveMiKindTableIds({ miCollectionTableId: 50331, primaryTableId: 50332 })
    const perParticipant = {
      tableName: 'attachment', tableId: 50330, foreignKeyField: 'main_idva',
      columns: ATTACHMENT_COLUMNS,
      fieldDefinitions: [
        ATTACHMENT_PK, FILE_FIELD,
        { fieldName: 'sub_task_idqc', isForeignKey: true, refTableId: 50331 },
      ],
    }
    expect(isSharedAttachmentFileBinding(perParticipant as never)).toBe(false)
  })

  /**
   * 真正的共享附件表（外键指向**主表**）：它自己的外键**不能**被当成「指向参与者」。
   *
   * <p>这是现场 bug：`main_idvab → main` 被判成参与者外键，assignment 任务加的附件
   * 保存进了库、刷新却渲染 0 行。判据必须按 collection 的 tableId 约束。
   */
  it('指向主表的外键不是参与者外键，附件行必须保留', () => {
    setActiveMiKindTableIds({ miCollectionTableId: 50331, primaryTableId: 50332 })
    const binding = {
      tableName: 'attachment', tableId: 50330, foreignKeyField: 'main_idva',
      columns: ATTACHMENT_COLUMNS,
      fieldDefinitions: [
        ATTACHMENT_PK, FILE_FIELD,
        { fieldName: 'main_idva', isForeignKey: true, refTableId: 50332 },
      ],
    }
    const saved = [{ idfa: 'y', main_idva: 'Meeting-000002', file: [] }]
    expect(finalizeSharedProcessSubTableBindingRows(saved, binding as never)).toHaveLength(1)
  })

  it('真正的附件行保留', () => {
    const binding = {
      tableName: 'attachment', tableId: 50330, foreignKeyField: 'main_idva',
      columns: ATTACHMENT_COLUMNS,
      fieldDefinitions: [
        ATTACHMENT_PK, FILE_FIELD,
        { fieldName: 'main_idva', isForeignKey: true, refTableId: 50332 },
      ],
    }
    const real = [{ idfa: 'z', main_idva: 'Meeting-000002', file: 'a.pdf' }]
    expect(finalizeSharedProcessSubTableBindingRows(real, binding as never)).toHaveLength(1)
  })

  it('原 #ghost-row bug 的只有 id 的行仍被挡住', () => {
    // 主键改名成 idfa：主键列不算「本表有数据」，靠 primaryKeyFields 配置识别
    const binding = {
      tableName: 'attachment', columns: ATTACHMENT_COLUMNS, primaryKeyFields: ['idfa'],
      fieldDefinitions: [ATTACHMENT_PK, FILE_FIELD],
    }
    expect(finalizeSharedProcessSubTableBindingRows(
      [{ idfa: '9d4e0000-0000-4000-8000-000000000001' }], binding as never)).toEqual([])
  })
})
