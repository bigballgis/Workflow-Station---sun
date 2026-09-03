import { describe, expect, it, afterEach } from 'vitest'
import { filterRowsForSharedProcessSubTableBinding } from '../sharedProcessSubTableFilters'
import { setActiveMiKindTableIds } from '../useMiConfig'

/**
 * 现场回归：assignment 任务加了一条 Attachment，**保存成功写进了库**，刷新却渲染 0 行。
 *
 * <p>根因：「这一行带着指向 MI 参与者的结构外键 ⇒ 是别的表泄漏进来的行」这条判据，
 * 解析外键列时没有传 collection 的 tableId。不传时 `resolveMiChildStructuralFkColumns`
 * 会把该表的**每一个**设计器外键都当成「指向参与者」，于是共享附件表指向**主表**的外键
 * （`main_idvab → main`，tableId 50332）也命中，整行被当泄漏行丢掉。
 *
 * <p>该判据的语义是「指向 MI 参与者」，必须按 collection（50331）约束。
 */
const ATTACHMENT_BINDING = {
  bindingId: 50542,
  tableId: 50330,
  tableName: 'attachment',
  foreignKeyField: 'main_idvab',
  bindingLinkMode: 'structuralFk',
  primaryKeyFields: ['idfav'],
  fieldDefinitions: [
    { fieldName: 'idfav', isPrimaryKey: true, dataType: 'VARCHAR' },
    // 指向主表，不是指向 collection
    { fieldName: 'main_idvab', isForeignKey: true, refTableId: 50332, dataType: 'VARCHAR' },
    { fieldName: 'file', dataType: 'FILE' },
  ],
  columns: [{ field: 'idfav' }, { field: 'main_idvab' }, { field: 'file' }],
}

/** 现场那一行：file 还没上传（空数组），但 main_idvab 有值。 */
const SAVED_ROW = {
  file: [] as unknown[],
  idfav: '8a08ee9c-3867-4434-9509-d3ee58e65ebb',
  row_id: '6eca9f98-7bdb-47c0-8a9d-183ed225a13d',
  main_idvab: 'Meeting-000001',
}

afterEach(() => setActiveMiKindTableIds({ miCollectionTableId: null, primaryTableId: null }))

describe('共享附件：指向主表的外键不是「参与者外键」', () => {
  it('MI 流程里，附件行不因指向主表的外键被当成泄漏行丢掉', () => {
    setActiveMiKindTableIds({ miCollectionTableId: 50331, primaryTableId: 50332 })
    const out = filterRowsForSharedProcessSubTableBinding([SAVED_ROW], ATTACHMENT_BINDING as never)
    expect(out).toHaveLength(1)
  })

  it('解析不到 collection 时同样保留（非 MI 流程没有参与者概念）', () => {
    setActiveMiKindTableIds({ miCollectionTableId: null, primaryTableId: null })
    const out = filterRowsForSharedProcessSubTableBinding([SAVED_ROW], ATTACHMENT_BINDING as never)
    expect(out).toHaveLength(1)
  })

  /**
   * 泄漏行：**没有本表自己的业务数据**（main_idvab 空、没上传文件），只剩一个主键 UUID。
   * 这正是 #ghost-row 当初要挡的形态 —— 表格里会渲染成一行「-」。
   *
   * <p>注意不能用「带 sub_task_idqc 的行」来构造这个用例：只要附件表自己声明了指向
   * collection 的外键，它就**不是**共享附件表（`isSharedAttachmentFileBinding` 返回 false），
   * 泄漏过滤根本不会作用于它 —— 那是 participant-child 的分片路径。
   */
  it('没有本表数据的幽灵行仍被丢掉', () => {
    setActiveMiKindTableIds({ miCollectionTableId: 50331, primaryTableId: 50332 })
    const ghost = { idfav: 'aaaa0000-0000-4000-8000-000000000001' }
    const out = filterRowsForSharedProcessSubTableBinding([ghost], ATTACHMENT_BINDING as never)
    expect(out).toEqual([])
  })
})
