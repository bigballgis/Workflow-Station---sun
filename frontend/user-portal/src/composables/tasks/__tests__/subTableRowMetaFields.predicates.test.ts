import { describe, expect, it } from 'vitest'
import {
  enrichChildBindingRowsFromParentsNestedSubTables,
  filterRowsForSharedProcessSubTableBinding,
  finalizeSharedProcessSubTableBindingRows,
  filterRowsForMiParticipantSubTableBinding,
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
  expansionKeyMatchesParticipantRow,
  isSubTableMiDashboardRow,
  isSubTableRowMetaField,
  buildBindingIdToRelationTableIdMap,
  stripSubTableRowMetaFields,
  applySharedAttachmentFinalizeAndMaterialize,
  isSharedAttachmentFileBinding,
} from '../shared'

import kkLiveSubTables from './fixtures/kk-4f31baaf-subTables.json'

describe('subTableRowMetaFields predicates', () => {
  it('live kk process 4f31baaf hydrates attachment binding 104 with 343/633/77777', () => {
    const rtMap = buildBindingIdToRelationTableIdMap([
      {
        tableBindings: [
          { bindingId: 64, tableId: 20 },
          { bindingId: 66, tableId: 20 },
          { bindingId: 69, tableId: 20 },
          { bindingId: 90, tableId: 21 },
          { bindingId: 103, tableId: 74 },
          { bindingId: 104, tableId: 74 },
        ],
      },
    ])
    // 外来 id 注册表已删除（它只对历史数字键有效，而键早已规范化为 dw:/rt:）。
    // 现在由「结构外键指向参与者」「本表列上无数据」两条防线兜住，断言直接看最终过滤结果。

    const bindings = [
      {
        bindingId: 104,
        tableId: 74,
        tableName: 'attachment',
        physicalTableName: 'attachment',
        foreignKeyField: 'main_id',
        columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
        primaryKeyFields: ['id'],
        data: [],
      },
    ]
    applySharedAttachmentFinalizeAndMaterialize(bindings, { id: 343 }, {
      flattened: kkLiveSubTables,
      bindingTableById: rtMap,
    })
    expect(bindings[0]!.data.map((r: { id: unknown }) => String(r.id)).sort()).toEqual([
      '343',
      '633',
      '77777',
    ])
  })

  it('isSubTableRowMetaField flags MI dashboard keys', () => {
    expect(isSubTableRowMetaField('assignee')).toBe(true)
    expect(isSubTableRowMetaField('task_status')).toBe(true)
    expect(isSubTableRowMetaField('__subTables__')).toBe(true)
    expect(isSubTableRowMetaField('name')).toBe(false)
    expect(isSubTableRowMetaField('id_idw')).toBe(false)
  })

  it('stripSubTableRowMetaFields removes runtime keys from plain related-table rows', () => {
    const cleaned = stripSubTableRowMetaFields({
      id: 343,
      name: '3',
      id_idw: 343,
      assignee: { id: 'u1' },
      task_status: 'IN_PROGRESS',
      task_current_node: 'sub form2',
      __subTables__: {},
    })
    expect(cleaned).toEqual({ id: 343, name: '3', id_idw: 343 })
  })

  /**
   * 分类只认配置。历史上这里断言的是「表名叫 participants」「列里有 task_status /
   * assignee_user_id」这类猜测 —— 那些字面量已随启发式一并删除（demo FU 改名后它们两个方向
   * 都答错），断言随之改为配置判据。
   */
  it('isMiDashboardSubTableBinding 只认设计器配置（Link Mode / Sub-Task Config 列）', () => {
    // 表名叫 participants 不再算数
    expect(
      isMiDashboardSubTableBinding({ tableName: 'participants', columns: [{ field: 'id' }] }),
    ).toBe(false)
    // 列名撞 task_status / assignee_user_id 也不再算数
    expect(
      isMiDashboardSubTableBinding({
        tableName: 'subtable1',
        columns: [{ field: 'assignee_user_id' }, { field: 'task_status' }],
      }),
    ).toBe(false)
    // 设计器 Link Mode = MI Participant Row —— 唯一权威声明
    expect(
      isMiDashboardSubTableBinding({ tableName: 'anything', bindingLinkMode: 'miParticipantRow' }),
    ).toBe(true)
    // Sub-Task Config 显式配置的状态列（调用方传入）也算配置
    expect(
      isMiDashboardSubTableBinding(
        { tableName: 'HMDC Transaction', columns: [{ field: 'row_id' }, { field: 'my_status' }] },
        { statusField: 'my_status', currentNodeField: null },
      ),
    ).toBe(true)
    expect(
      isMiDashboardSubTableBinding({ tableName: 'attachment', columns: [{ field: 'name' }] }),
    ).toBe(false)
  })

  it('expansionKeyMatchesParticipantRow requires designer primary key fields', () => {
    expect(expansionKeyMatchesParticipantRow({ row_id: 232424 }, 232424, ['row_id'])).toBe(true)
    expect(expansionKeyMatchesParticipantRow({ row_id: '57666' }, 57666, ['row_id'])).toBe(true)
    expect(expansionKeyMatchesParticipantRow({ row_id: 57666 }, 232424, ['row_id'])).toBe(false)
    expect(expansionKeyMatchesParticipantRow({ row_id: 232424 }, 232424)).toBe(false)
    expect(expansionKeyMatchesParticipantRow({ row_id: 232424 }, 232424, [])).toBe(false)
  })

  it('isSubTableMiDashboardRow recognizes MCY assignee_id and sub_task_status', () => {
    expect(isSubTableMiDashboardRow({ assignee_id: 'u-1' })).toBe(true)
    expect(isSubTableMiDashboardRow({ assignee: 'user-e2e-lina' })).toBe(true)
    expect(isSubTableMiDashboardRow({ sub_task_status: 'IN_PROGRESS' })).toBe(true)
    expect(isSubTableMiDashboardRow({ card_number: '4111' })).toBe(false)
  })

  it('isSharedAttachmentFileBinding recognizes tableId 74 even when list columns omit file', () => {
    expect(
      isSharedAttachmentFileBinding({
        bindingId: 104,
        tableId: 74,
        tableName: 'attachment',
        foreignKeyField: 'main_id',
        columns: [{ field: 'id' }, { field: 'main_id' }],
      }),
    ).toBe(true)
  })

  /**
   * child vs shared 只能靠**字段级 FK 的 refTableId 指向谁**区分 —— 两者的 bindingLinkMode
   * 都是 structuralFk。FK 列名（main_id / id_idw / id）不再参与判定。
   */
  it('isMiParticipantScopedSubTableBinding 按 FK 指向判定 child vs shared', () => {
    const CTX = { miCollectionTableId: 50331, primaryTableId: 50332 }
    // attachment：FK 指向主表 => shared
    expect(
      isMiParticipantScopedSubTableBinding(
        {
          tableName: 'attachment',
          foreignKeyField: 'main_idv',
          fieldDefinitions: [{ fieldName: 'main_idv', isForeignKey: true, refTableId: 50332 }],
        },
        CTX,
      ),
    ).toBe(false)
    // people：FK 指向 collection => participant-child
    expect(
      isMiParticipantScopedSubTableBinding(
        {
          tableName: 'people',
          foreignKeyField: 'idq',
          fieldDefinitions: [{ fieldName: 'sub_task_idq', isForeignKey: true, refTableId: 50331 }],
        },
        CTX,
      ),
    ).toBe(true)
    // collection 自己（Link Mode 声明）
    expect(
      isMiParticipantScopedSubTableBinding({ tableName: 'subtable', bindingLinkMode: 'miParticipantRow' }, CTX),
    ).toBe(true)
    // FK 列名撞旧名单但没有配置 => 不再判为 scoped（旧启发式已删）
    expect(
      isMiParticipantScopedSubTableBinding({ tableName: 'subtable2', foreignKeyField: 'id_idw' }, CTX),
    ).toBe(false)
  })

  it('enrichChildBindingRowsFromParentsNestedSubTables does not leak MI fields into non-MI child rows', () => {
    const bindings = [
      {
        bindingId: 1,
        tableName: 'subtable1',
        data: [
          {
            id_idw: 88,
            assignee_user_id: 'u1',
            task_status: 'IN_PROGRESS',
            task_current_node: 'sub form2',
            __subTables__: {
              '90': [
                {
                  id: 343,
                  name: '3',
                  id_idw: 343,
                  assignee: { id: 'u1' },
                  task_status: 'IN_PROGRESS',
                },
              ],
            },
          },
        ],
        columns: [{ field: 'assignee_user_id' }, { field: 'task_status' }],
      },
      {
        bindingId: 90,
        tableName: 'attachment',
        tableId: 90,
        data: [{ id: 343, name: '3', id_idw: 343 }],
        columns: [{ field: 'id' }, { field: 'name' }, { field: 'id_idw' }],
      },
    ]
    enrichChildBindingRowsFromParentsNestedSubTables(bindings)
    const childRow = bindings[1].data[0] as Record<string, unknown>
    expect(childRow.id).toBe(343)
    expect(childRow.name).toBe('3')
    expect(childRow.assignee).toBeUndefined()
    expect(childRow.task_status).toBeUndefined()
    expect(childRow.task_current_node).toBeUndefined()
    expect(childRow.__subTables__).toBeUndefined()
  })

  it('filterRowsForSharedProcessSubTableBinding drops MI subtable rows leaked onto attachment', () => {
    const attachmentBinding = {
      foreignKeyField: 'main_id',
      tableName: 'attachment',
      columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
    }
    const leaked = [
      {
        id: 343,
        name: '3',
        id_idw: 343,
        assignee: { id: 'u1' },
        task_status: 'IN_PROGRESS',
      },
      {
        id: 666,
        main_id: '',
        file: '/api/v1/upload/files/x.pdf?originalName=a.pdf',
      },
    ]
    expect(filterRowsForSharedProcessSubTableBinding(leaked, attachmentBinding)).toEqual([leaked[1]])
    expect(finalizeSharedProcessSubTableBindingRows(leaked, attachmentBinding)).toEqual([
      { id: 666, main_id: '', file: '/api/v1/upload/files/x.pdf?originalName=a.pdf' },
    ])
  })

  it('keeps attachment rows that carry file plus stale MI meta only (strip meta, drop subtable name/id)', () => {
    const attachmentBinding = {
      foreignKeyField: 'main_id',
      tableName: 'attachment',
      columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
    }
    const polluted = [
      {
        id: 666,
        file: '/api/v1/upload/files/x.pdf?originalName=a.pdf',
        name: '66',
        id_idw: 666,
        assignee: { id: 'u1' },
        task_status: 'IN_PROGRESS',
        task_current_node: 'sub form1',
      },
    ]
    expect(filterRowsForSharedProcessSubTableBinding(polluted, attachmentBinding)).toHaveLength(0)
    expect(finalizeSharedProcessSubTableBindingRows(polluted, attachmentBinding)).toEqual([])
  })



  it('filterRowsForMiParticipantSubTableBinding drops attachment-only rows leaked into subtable grid', () => {
    const subtableBinding = {
      tableName: 'subtable',
      columns: [{ field: 'id' }, { field: 'name' }, { field: 'task_status' }],
    }
    const rows = [
      { id: 44, name: '44', task_status: 'IN_PROGRESS' },
      { id: 633, file: '/api/v1/upload/files/c.pdf' },
      { id: '77777', file: '/api/v1/upload/files/a.pdf' },
    ]
    const out = filterRowsForMiParticipantSubTableBinding(rows, subtableBinding)
    expect(out).toHaveLength(1)
    expect(out[0]).toMatchObject({ id: 44, name: '44' })
  })

  it('keeps data-bearing rows whose id_idw PK is not a display column (plain sub-table, non-MI)', () => {
    // Regression: id_idw is the DW default sub-table PK; a list view that omits it must not
    // lose every row on task detail (Review-step "Shipment shows no data").
    const shipmentBinding = {
      tableName: 'Shipment',
      physicalTableName: 'nst_shipment',
      columns: [{ field: 'shipment_name' }, { field: 'carrier' }],
    }
    const rows = [
      { id_idw: '790ddf3f-6b07', shipment_name: '111', carrier: '222', order_id: 'a5f8' },
      { id_idw: '8e0a1176-013a', shipment_name: '333', carrier: '444', order_id: 'a5f8' },
    ]
    expect(filterRowsForSharedProcessSubTableBinding(rows, shipmentBinding)).toHaveLength(2)
    // Id-only ghosts (no own column data) still drop.
    const ghosts = [{ id_idw: 'ghost-1', order_id: 'a5f8' }]
    expect(filterRowsForSharedProcessSubTableBinding(ghosts, shipmentBinding)).toHaveLength(0)
  })

  it('filterRowsForMiParticipantSubTableBinding drops pure attachment rows even when list has file column', () => {
    const subtableBinding = {
      tableName: 'Sub Task',
      columns: [{ field: 'id' }, { field: 'name' }, { field: 'file' }, { field: 'task_status' }],
    }
    const rows = [
      { id: 'Test-000059', id_idw: 'Test-000059', name: '33', task_status: 'IN_PROGRESS', file: '/a.pdf' },
      { id: '7135ba28-378a-4e24-92f7-f10e7445deea', file: '/api/v1/upload/files/a.pdf' },
      { id: 'ba552499-cf91-4e2d-a5bb-4d479dec51c9', file: '/api/v1/upload/files/b.jpg' },
    ]
    const out = filterRowsForMiParticipantSubTableBinding(rows, subtableBinding)
    expect(out).toHaveLength(1)
    expect(out[0]).toMatchObject({ id: 'Test-000059', name: '33' })
  })


  it('filterRowsForMiParticipantSubTableBinding preserves assignee on MI rows', () => {
    const subtableBinding = {
      tableName: 'subtable',
      columns: [{ field: 'id' }, { field: 'assignee' }, { field: 'task_status' }],
    }
    const rows = [
      {
        id: 44,
        name: '44',
        task_status: 'IN_PROGRESS',
        assignee: { id: 'u1', full_name: 'Qing Q Q Liu' },
      },
    ]
    const out = filterRowsForMiParticipantSubTableBinding(rows, subtableBinding)
    expect(out[0]).toMatchObject({
      id: 44,
      assignee: { id: 'u1', full_name: 'Qing Q Q Liu' },
    })
  })
})
