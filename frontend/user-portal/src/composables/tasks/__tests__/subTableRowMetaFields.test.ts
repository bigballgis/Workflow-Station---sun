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
  mergeSubTableSlicesForRelationTableId,
  collectSubTableSliceRowsForRelationTableId,
  miLinkChildRowBelongsToParticipant,
  mergeSubTableRowsByRowId,
  mergeAllSlicesForSharedProcessSubTableBinding,
  buildBindingIdToRelationTableIdMap,
  stripSubTableRowMetaFields,
  applySharedAttachmentFinalizeAndMaterialize,
  collectForeignSubTableRowIdsFromVariables,
  hydrateBindingsRowsFromVariablesBySharedRelationTableId,
  isSharedAttachmentFileBinding,
  resolveSubTableRowsForBinding,
} from '../shared'

import kkLiveSubTables from './fixtures/kk-4f31baaf-subTables.json'

describe('subTableRowMetaFields', () => {
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
    const foreignIds = collectForeignSubTableRowIdsFromVariables(kkLiveSubTables, rtMap)
    expect(foreignIds.has('343')).toBe(false)
    expect(foreignIds.has('633')).toBe(false)
    expect(foreignIds.has('77777')).toBe(false)
    expect(foreignIds.has('44')).toBe(true)
    expect(foreignIds.has('666')).toBe(true)

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

  it('isMiDashboardSubTableBinding detects designer MI columns only', () => {
    expect(
      isMiDashboardSubTableBinding({
        tableName: 'attachment',
        columns: [{ field: 'id' }, { field: 'name' }],
      }),
    ).toBe(false)
    expect(
      isMiDashboardSubTableBinding({
        tableName: 'participants',
        columns: [{ field: 'id' }],
      }),
    ).toBe(true)
    expect(
      isMiDashboardSubTableBinding({
        tableName: 'subtable1',
        columns: [{ field: 'assignee_user_id' }, { field: 'task_status' }],
      }),
    ).toBe(true)
    expect(
      isMiDashboardSubTableBinding({
        tableName: 'HMDC Transaction',
        columns: [{ field: 'row_id' }, { field: 'assignee_id' }],
      }),
    ).toBe(true)
    expect(
      isMiDashboardSubTableBinding({
        tableName: 'HMDC Transaction',
        columns: [{ field: 'row_id' }, { field: 'sub_task_status' }],
      }),
    ).toBe(true)
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

  it('isMiParticipantScopedSubTableBinding treats main-table FK attachment as shared', () => {
    expect(
      isMiParticipantScopedSubTableBinding({
        tableName: 'attachment',
        foreignKeyField: 'main_id',
        columns: [{ field: 'id' }, { field: 'file' }],
      }),
    ).toBe(false)
    expect(
      isMiParticipantScopedSubTableBinding({
        tableName: 'subtable1',
        foreignKeyField: 'id_idw',
        columns: [{ field: 'assignee_user_id' }],
      }),
    ).toBe(true)
    expect(
      isMiParticipantScopedSubTableBinding({
        tableName: 'subtable2',
        foreignKeyField: 'id',
        columns: [{ field: 'sex' }],
      }),
    ).toBe(true)
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

  it('collectForeignSubTableRowIdsFromVariables ignores nested attachment rows under MI parent', () => {
    const rtMap = buildBindingIdToRelationTableIdMap([
      {
        tableBindings: [
          { bindingId: 66, tableId: 20 },
          { bindingId: 103, tableId: 74 },
          { bindingId: 104, tableId: 74 },
        ],
      },
    ])
    const subTables = {
      66: [
        {
          id: 44,
          name: '44',
          task_status: 'IN_PROGRESS',
          assignee_user_id: 'u1',
          __subTables__: {
            104: [
              { id: 343, file: '/a.pdf', main_id: '' },
              { id: 633, file: '/b.pdf', main_id: '' },
            ],
            attachment: [{ id: '77777', file: '/c.pdf', main_id: '' }],
          },
        },
      ],
      103: [
        { id: '77777', file: '/c.pdf', main_id: '' },
        { id: 633, file: '/b.pdf', main_id: '' },
        { id: 343, file: '/a.pdf', main_id: '' },
      ],
    }
    const foreignIds = collectForeignSubTableRowIdsFromVariables(subTables, rtMap)
    expect(foreignIds.has('44')).toBe(true)
    expect(foreignIds.has('343')).toBe(false)
    expect(foreignIds.has('633')).toBe(false)
    expect(foreignIds.has('77777')).toBe(false)

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
      flattened: subTables,
      bindingTableById: rtMap,
    })
    expect(bindings[0]!.data.length).toBe(3)
    expect(bindings[0]!.data.map((r: { id: unknown }) => String(r.id)).sort()).toEqual([
      '343',
      '633',
      '77777',
    ])
  })

  it('collectForeignSubTableRowIdsFromVariables gathers subtable slice ids only, not attachment slice ids', () => {
    const rtMap = buildBindingIdToRelationTableIdMap([
      { tableBindings: [{ bindingId: 66, tableId: 20 }, { bindingId: 104, tableId: 74 }] },
    ])
    const subTables = {
      66: [{ id: 666, name: '66', task_status: 'COMPLETED' }],
      subtable: [{ id: 44, name: '44', task_status: 'IN_PROGRESS' }],
      103: [
        { id: '77777', file: '/api/v1/upload/files/a.pdf' },
        { id: 633, file: '/api/v1/upload/files/c.pdf' },
      ],
    }
    const foreignIds = collectForeignSubTableRowIdsFromVariables(subTables, rtMap)
    expect(foreignIds.has('666')).toBe(true)
    expect(foreignIds.has('44')).toBe(true)
    expect(foreignIds.has('77777')).toBe(false)
    expect(foreignIds.has('633')).toBe(false)
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

  it('finalizeSharedProcessSubTableBindingRows keeps attachment rows when ids appear in foreign subtable set', () => {
    const rtMap = buildBindingIdToRelationTableIdMap([
      { tableBindings: [{ bindingId: 69, tableId: 20 }, { bindingId: 104, tableId: 74 }] },
    ])
    const subTables = {
      69: [
        { id: '7135ba28-378a-4e24-92f7-f10e7445deea', file: '/api/v1/upload/files/a.pdf' },
        { id: 'Test-000059', id_idw: 'Test-000059', name: '33', task_status: 'IN_PROGRESS' },
      ],
      104: [
        { id: '7135ba28-378a-4e24-92f7-f10e7445deea', file: '/api/v1/upload/files/a.pdf' },
        { id: 'ba552499-cf91-4e2d-a5bb-4d479dec51c9', file: '/api/v1/upload/files/b.jpg' },
        { id: '20fb0bc5-63ef-437c-ae7d-7101bd628918', file: '/api/v1/upload/files/c.pdf' },
      ],
    }
    const foreignIds = collectForeignSubTableRowIdsFromVariables(subTables, rtMap)
    expect(foreignIds.has('7135ba28-378a-4e24-92f7-f10e7445deea')).toBe(false)
    const attachmentBinding = {
      tableName: 'attachment',
      tableId: 74,
      foreignKeyField: 'main_id',
      columns: [{ field: 'file' }],
    }
    const out = finalizeSharedProcessSubTableBindingRows(subTables['104'], attachmentBinding, {
      foreignSubTableRowIds: foreignIds,
    })
    expect(out).toHaveLength(3)
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

  it('mergeSubTableSlicesForRelationTableId merges only same tableId slices', () => {
    const rtMap = buildBindingIdToRelationTableIdMap([
      { tableBindings: [{ bindingId: 103, tableId: 74 }, { bindingId: 66, tableId: 20 }] },
    ])
    const subTables = {
      '103': [{ id: 1, file: 'a.pdf' }],
      '66': [{ id: 343, name: '3', id_idw: 343, task_status: 'IN_PROGRESS' }],
      attachment: [{ id: 2, file: 'b.pdf' }],
    }
    const merged = mergeSubTableSlicesForRelationTableId(subTables, 74, rtMap, ['id'], 'attachment')
    expect(merged).toHaveLength(2)
    expect(merged).toEqual(expect.arrayContaining([
      { id: 1, file: 'a.pdf' },
      { id: 2, file: 'b.pdf' },
    ]))
  })

  it('mergeAllSlicesForSharedProcessSubTableBinding collects nested-only attachment rows and sibling binding 103', () => {
    const rtMap = buildBindingIdToRelationTableIdMap([
      { tableBindings: [{ bindingId: 103, tableId: 74 }, { bindingId: 104, tableId: 74 }] },
    ])
    const attachmentBinding = {
      bindingId: 104,
      tableId: 74,
      tableName: 'attachment',
      primaryKeyFields: ['id'],
    }
    const subTables = {
      '66': [
        {
          id: 343,
          name: '3',
          id_idw: 343,
          task_status: 'IN_PROGRESS',
          __subTables__: {
            '103': [{ id: 666, main_id: '', file: '/api/v1/upload/files/x.pdf?originalName=a.pdf' }],
            attachment: [{ id: 667, main_id: '', file: '/api/v1/upload/files/y.pdf?originalName=b.pdf' }],
          },
        },
      ],
    }
    const merged = mergeAllSlicesForSharedProcessSubTableBinding(subTables, attachmentBinding, rtMap)
    expect(merged).toHaveLength(2)
    expect(merged).toEqual(expect.arrayContaining([
      { id: 666, main_id: '', file: '/api/v1/upload/files/x.pdf?originalName=a.pdf' },
      { id: 667, main_id: '', file: '/api/v1/upload/files/y.pdf?originalName=b.pdf' },
    ]))
  })

  it('applySharedAttachmentFinalizeAndMaterialize does not project primary fileupload into attachment rows', () => {
    const binding = {
      bindingId: 104,
      tableId: 74,
      tableName: 'attachment',
      foreignKeyField: 'main_id',
      columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
      primaryKeyFields: ['id'],
      data: [] as any[],
    }
    applySharedAttachmentFinalizeAndMaterialize(
      [binding],
      {
        id: 343,
        fileupload: '/api/v1/upload/files/99029219.pdf?originalName=invoice.pdf',
      },
    )
    expect(binding.data).toEqual([])
  })

  it('applySharedAttachmentFinalizeAndMaterialize keeps existing attachment rows when primary fileupload is set', () => {
    const binding = {
      bindingId: 104,
      tableId: 74,
      tableName: 'attachment',
      foreignKeyField: 'main_id',
      columns: [{ field: 'file' }],
      primaryKeyFields: ['id'],
      data: [{ id: 1, main_id: '19', file: '/api/v1/upload/files/a.pdf' }],
    }
    applySharedAttachmentFinalizeAndMaterialize(
      [binding],
      { fileupload: '/api/v1/upload/files/b.pdf?originalName=other.pdf' },
    )
    expect(binding.data).toEqual([{ id: 1, main_id: '19', file: '/api/v1/upload/files/a.pdf' }])
  })

  it('applySharedAttachmentFinalizeAndMaterialize drops MI rows without projecting primary fileupload', () => {
    const bindings = [
      {
        bindingId: 104,
        tableId: 74,
        tableName: 'attachment',
        foreignKeyField: 'main_id',
        columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
        primaryKeyFields: ['id'],
        data: [{ id: 343, name: '3', id_idw: 343, task_status: 'IN_PROGRESS' }],
      },
    ]
    applySharedAttachmentFinalizeAndMaterialize(bindings, {
      id: 343,
      fileupload: '/api/v1/upload/files/x.pdf?originalName=a.pdf',
    })
    expect(bindings[0]!.data).toEqual([])
  })

  it('applySharedAttachmentFinalizeAndMaterialize replaces thin per-binding slice with full shared merge', () => {
    const rtMap = new Map<number, number | null>([
      [66, 20],
      [103, 74],
      [104, 74],
    ])
    const flat = {
      66: [{ id: 666, name: '66', task_status: 'COMPLETED' }],
      103: [
        { id: '77777', file: '/api/v1/upload/files/a.pdf', main_id: '' },
        { id: 666, file: '/api/v1/upload/files/b.pdf', main_id: '', name: '66' },
        { id: 633, file: '/api/v1/upload/files/c.pdf', main_id: '' },
      ],
      104: [
        { id: 666, file: '/api/v1/upload/files/b.pdf', main_id: '', name: '66' },
        { id: 666, file: '/api/v1/upload/files/d.pdf', main_id: '' },
      ],
    }
    const bindings = [
      {
        bindingId: 103,
        tableId: 74,
        tableName: 'attachment',
        foreignKeyField: 'main_id',
        columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
        primaryKeyFields: ['id'],
        data: [],
      },
      {
        bindingId: 104,
        tableId: 74,
        tableName: 'attachment',
        foreignKeyField: 'main_id',
        columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
        primaryKeyFields: ['id'],
        data: [{ id: 666, file: '/api/v1/upload/files/d.pdf', main_id: '' }],
      },
    ]
    applySharedAttachmentFinalizeAndMaterialize(bindings, {}, {
      flattened: flat,
      bindingTableById: rtMap,
    })
    expect(bindings[1]!.data).toHaveLength(2)
    expect(bindings[1]!.data.map((r: { id: unknown }) => String(r.id)).sort()).toEqual(['633', '77777'])
  })

  it('enrichChildBindingRowsFromParentsNestedSubTables does not overwrite shared attachment binding rows', () => {
    const bindings = [
      {
        bindingId: 104,
        tableId: 74,
        tableName: 'attachment',
        physicalTableName: 'attachment',
        foreignKeyField: 'main_id',
        columns: [{ field: 'id' }, { field: 'file' }],
        primaryKeyFields: ['id'],
        data: [
          { id: '343', file: '/a.pdf', main_id: '' },
          { id: '633', file: '/b.pdf', main_id: '' },
        ],
      },
      {
        bindingId: 69,
        tableId: 20,
        tableName: 'subtable',
        columns: [{ field: 'name' }],
        data: [],
      },
    ]
    enrichChildBindingRowsFromParentsNestedSubTables(bindings)
    expect(bindings[0]!.data.map((r: { id: unknown }) => String(r.id)).sort()).toEqual(['343', '633'])
  })

  it('hydrateBindingsRowsFromVariablesBySharedRelationTableId does not merge sole unclaimed slice from another relation table (HMDC)', () => {
    const rtMap = new Map<number, number | null>([
      [271, 112],
      [273, 114],
    ])
    const saved = {
      271: [
        {
          row_id: '455656',
          card_number: '',
          arn: '',
        },
      ],
      273: [{ file: '/api/v1/upload/files/test.jpg' }],
    }
    const bindings = [
      {
        bindingId: 271,
        tableId: 112,
        tableName: 'HMDC Transaction',
        physicalTableName: 'HMDC_Transaction',
        columns: [{ field: 'row_id' }, { field: 'card_number' }],
        primaryKeyFields: ['row_id'],
        data: [...saved[271]!],
      },
      {
        bindingId: 273,
        tableId: 114,
        tableName: 'HMDC Attachment',
        physicalTableName: 'HMDC_Attachment',
        columns: [{ field: 'file' }],
        data: [...saved[273]!],
      },
    ]
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(bindings, saved, rtMap)
    expect(bindings[0]!.data).toHaveLength(1)
    expect(bindings[0]!.data[0]!.row_id).toBe('455656')
    expect(bindings[0]!.data[0]!.file).toBeUndefined()
  })

  it('collectSubTableSliceRowsForRelationTableId returns RAW rows (no collapse-by-id) across same-tableId slices', () => {
    const rtMap = new Map<number, number | null>([
      [30, 20],
      [63, 20],
      [64, 99], // different relation table — must be excluded
    ])
    const saved = {
      // current form People slice — current participant Test-000058 row (age ii66)
      30: [{ id: 'Test-000058', sub_task_id: 'Test-000058', age: 'ii66' }],
      // previous form People slice — OTHER participant Test-000057 row that happens to SHARE id
      63: [{ id: 'Test-000058', sub_task_id: 'Test-000057', age: 'ii' }],
      // Sub Task collection (different tableId) — must NOT be pulled into People
      64: [{ id: 'Test-000058', sub_task_id: 'Test-000057', name: '222' }],
    }
    const rows = collectSubTableSliceRowsForRelationTableId(saved, 20, rtMap, 'People', 'people')
    // Both colliding People rows preserved RAW (not collapsed by id), Sub Task slice excluded.
    expect(rows).toHaveLength(2)
    expect(rows.some(r => r.sub_task_id === 'Test-000058' && r.age === 'ii66')).toBe(true)
    expect(rows.some(r => r.sub_task_id === 'Test-000057' && r.age === 'ii')).toBe(true)
    expect(rows.some(r => r.name === '222')).toBe(false)
  })

  it('participant filter BEFORE merge preserves current participant People against PK-colliding other participant', () => {
    const myRowId = 'Test-000058'
    const rtMap = new Map<number, number | null>([
      [30, 20],
      [63, 20],
    ])
    const saved = {
      30: [{ id: 'Test-000058', sub_task_id: 'Test-000058', age: 'ii66', sex: true }],
      63: [{ id: 'Test-000058', sub_task_id: 'Test-000057', age: 'ii', sex: true }],
    }
    // RAW collect then filter to current participant, then merge — the correct order.
    const raw = collectSubTableSliceRowsForRelationTableId(saved, 20, rtMap, 'People')
    const scoped = raw.filter(r => miLinkChildRowBelongsToParticipant(r, myRowId))
    const merged = mergeSubTableRowsByRowId([], scoped, ['id'])
    expect(merged).toHaveLength(1)
    expect(merged[0]!.sub_task_id).toBe('Test-000058')
    expect(merged[0]!.age).toBe('ii66')

    // CONTRAST: collapsing by id BEFORE the filter loses the current participant's row (the bug).
    const collapsedFirst = mergeSubTableSlicesForRelationTableId(saved, 20, rtMap, ['id'], 'People')
    const wronglyScoped = collapsedFirst.filter(r => miLinkChildRowBelongsToParticipant(r, myRowId))
    expect(wronglyScoped).toHaveLength(0)
  })

  it('hydrateBindingsRowsFromVariablesBySharedRelationTableId excludeBinding skips participant-scoped People (no cross-participant re-merge)', () => {
    const rtMap = new Map<number, number | null>([
      [30, 20],
      [63, 20],
    ])
    const saved = {
      30: [{ id: 'Test-000058', sub_task_id: 'Test-000058', age: 'ii66' }],
      63: [{ id: 'Test-000058', sub_task_id: 'Test-000057', age: 'ii' }],
    }
    const bindings = [
      {
        bindingId: 30,
        tableId: 20,
        tableName: 'People',
        physicalTableName: 'people',
        foreignKeyField: 'sub_task_id',
        primaryKeyFields: ['id'],
        columns: [{ field: 'id' }, { field: 'sub_task_id' }, { field: 'age' }],
        data: [{ id: 'Test-000058', sub_task_id: 'Test-000058', age: 'ii66' }],
      },
    ]
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(bindings as any, saved, rtMap, {
      excludeBinding: () => true,
    })
    // Excluded — data left untouched, NOT polluted by binding 63's other-participant row.
    expect(bindings[0]!.data).toHaveLength(1)
    expect((bindings[0]!.data[0] as any).sub_task_id).toBe('Test-000058')
    expect((bindings[0]!.data[0] as any).age).toBe('ii66')
  })

  it('hydrateBindingsRowsFromVariablesBySharedRelationTableId skips shared attachment (no per-binding split)', () => {
    const rtMap = new Map<number, number | null>([
      [103, 74],
      [104, 74],
    ])
    const saved = {
      103: [
        { id: '77777', file: '/api/v1/upload/files/a.pdf' },
        { id: 633, file: '/api/v1/upload/files/c.pdf' },
      ],
      104: [{ id: 666, file: '/api/v1/upload/files/b.pdf' }],
    }
    const bindings = [
      {
        bindingId: 103,
        tableId: 74,
        tableName: 'attachment',
        foreignKeyField: 'main_id',
        columns: [{ field: 'file' }],
        data: [],
      },
      {
        bindingId: 104,
        tableId: 74,
        tableName: 'attachment',
        foreignKeyField: 'main_id',
        columns: [{ field: 'file' }],
        data: [],
      },
    ]
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(bindings, saved, rtMap)
    expect(bindings[0]!.data).toHaveLength(0)
    expect(bindings[1]!.data).toHaveLength(0)
  })

  it('resolveSubTableRowsForBinding merges initiator slice 64 assignee into assignment binding 66', () => {
    const saved = {
      '64': [
        {
          name: '1111',
          id_idw: 'Test-000057',
          main_id: 'f0e6956a-6982-4448-ae1c-db7971a37990',
          assignee: { id: 'user-dev', display_name: 'Developer Tester' },
          assignee_display_name: 'Developer Tester',
        },
      ],
      '66': [
        {
          name: '1111',
          id_idw: 'Test-000057',
          main_id: 'f0e6956a-6982-4448-ae1c-db7971a37990',
        },
      ],
    }
    const rtMap = buildBindingIdToRelationTableIdMap([
      { tableBindings: [{ bindingId: 64, tableId: 20 }, { bindingId: 66, tableId: 20 }] },
    ])
    const binding = {
      bindingId: 66,
      tableId: 20,
      tableName: 'Sub Task',
      columns: [
        { field: 'main_id' },
        { field: 'id_idw' },
        { field: 'name' },
        { field: 'assignee' },
      ],
    }
    const rows = resolveSubTableRowsForBinding(saved, binding, {
      bindingTableById: rtMap,
      mergeSiblingSlices: true,
    })
    expect(rows).toHaveLength(1)
    expect(rows![0].assignee).toEqual({ id: 'user-dev', display_name: 'Developer Tester' })
    expect(rows![0].assignee_display_name).toBe('Developer Tester')
  })

  it('finalizeSharedProcessSubTableBindingRows preserves assignee on MI dashboard bindings', () => {
    const rows = [
      {
        id_idw: 'Test-000057',
        assignee: { id: 'user-dev', display_name: 'Developer Tester' },
        assignee_display_name: 'Developer Tester',
        task_status: 'PENDING',
      },
    ]
    const out = finalizeSharedProcessSubTableBindingRows(rows, {
      tableName: 'Sub Task',
      columns: [{ field: 'assignee' }, { field: 'task_status' }],
    })
    expect(out[0].assignee).toEqual({ id: 'user-dev', display_name: 'Developer Tester' })
    expect(out[0].assignee_display_name).toBe('Developer Tester')
    expect(out[0].task_status).toBe('PENDING')
  })
})
