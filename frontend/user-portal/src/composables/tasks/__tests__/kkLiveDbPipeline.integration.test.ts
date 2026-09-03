/**
 * Integration: live DB __subTables__ for process 4f31baaf through attachment hydration pipeline.
 */
import { describe, expect, it } from 'vitest'
import liveSubTables from './fixtures/kk-live-db-subTables.json'
import {
  applySharedAttachmentFinalizeAndMaterialize,
  buildBindingIdToRelationTableIdMap,
  enrichChildBindingRowsFromParentsNestedSubTables,
  finalizeSharedProcessSubTableBindingRows,
  flattenNestedSubTableRowsIntoPayload,
  isSharedAttachmentFileBinding,
} from '../shared'
import { defaultAttachmentListColumns } from '@/components/subTableAddDialogHelpers'

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

function simulateApplicationsDetailLoad(flat: Record<string, unknown>) {
  flattenNestedSubTableRowsIntoPayload(flat)
  const bindings = [
    {
      bindingId: 69,
      tableId: 20,
      tableName: 'subtable',
      physicalTableName: 'subtable',
      foreignKeyField: null,
      columns: [{ field: 'id' }, { field: 'name' }],
      primaryKeyFields: ['id'],
      data: [] as any[],
    },
    {
      bindingId: 104,
      tableId: 74,
      tableName: 'attachment',
      physicalTableName: 'attachment',
      foreignKeyField: 'main_id',
      columns: defaultAttachmentListColumns(),
      // 共享附件按设计器列类型认（data_type='FILE'）——真实 binding payload 带这份元数据
      fieldDefinitions: [
        { fieldName: 'id', isPrimaryKey: true },
        { fieldName: 'main_id', isForeignKey: true },
        { fieldName: 'file', dataType: 'FILE' },
      ],
      primaryKeyFields: ['id'],
      data: [] as any[],
    },
  ]
  // mimic getSavedSubTableRowsFromVariables for 104
  const slice104 = flat['104']
  if (Array.isArray(slice104)) bindings[1]!.data = [...slice104]

  enrichChildBindingRowsFromParentsNestedSubTables(bindings)
  applySharedAttachmentFinalizeAndMaterialize(bindings, { id: 343 }, {
    flattened: flat,
    bindingTableById: rtMap,
  })
  return bindings.find(b => b.bindingId === 104)!
}

describe('kk live DB pipeline (4f31baaf)', () => {

  it('isSharedAttachmentFileBinding true for binding 104 (designer FILE column)', () => {
    expect(
      isSharedAttachmentFileBinding({
        bindingId: 104,
        tableId: 74,
        tableName: 'attachment',
        physicalTableName: 'attachment',
        foreignKeyField: 'main_id',
        columns: defaultAttachmentListColumns(),
        // 判据是设计器列类型，不再是表名 / tableId 74 / main_id
        fieldDefinitions: [
          { fieldName: 'id', isPrimaryKey: true },
          { fieldName: 'main_id', isForeignKey: true },
          { fieldName: 'file', dataType: 'FILE' },
        ],
      }),
    ).toBe(true)
  })

  it('simulateApplicationsDetailLoad yields 343, 633, 77777 on binding 104 (live DB id_idw)', () => {
    const flat = structuredClone(liveSubTables) as Record<string, unknown>
    const att = simulateApplicationsDetailLoad(flat)
    const ids = att.data.map((r: { id: unknown }) => String(r.id)).sort()
    expect(ids).toEqual(['343', '633', '77777'])
  })

  it('drops attachment row 666 when id matches foreign subtable id even with file', () => {
    const flat = structuredClone(liveSubTables) as Record<string, unknown>
    flattenNestedSubTableRowsIntoPayload(flat)
    const binding = {
      bindingId: 104,
      tableId: 74,
      tableName: 'attachment',
      foreignKeyField: 'main_id',
      columns: defaultAttachmentListColumns(),
      primaryKeyFields: ['id'],
    }
    const rows = Array.isArray(flat['104']) ? [...(flat['104'] as any[])] : []
    // 注册表已删除：666 由「有 name 但 name 不在附件列里」这条防线挡下（已实测）
    const out = finalizeSharedProcessSubTableBindingRows(rows, binding)
    expect(out.some((r: { id: unknown }) => String(r.id) === '666')).toBe(false)
  })

  it('finalize alone does not wipe merged attachment rows', () => {
    const flat = structuredClone(liveSubTables) as Record<string, unknown>
    flattenNestedSubTableRowsIntoPayload(flat)
    const binding = {
      bindingId: 104,
      tableId: 74,
      tableName: 'attachment',
      physicalTableName: 'attachment',
      foreignKeyField: 'main_id',
      columns: defaultAttachmentListColumns(),
      primaryKeyFields: ['id'],
    }
    const rows = Array.isArray(flat['104']) ? [...(flat['104'] as any[])] : []
    const out = finalizeSharedProcessSubTableBindingRows(rows, binding, {
    })
    expect(out.map((r: { id: unknown }) => String(r.id)).sort()).toEqual(['343', '633', '77777'])
  })
})
