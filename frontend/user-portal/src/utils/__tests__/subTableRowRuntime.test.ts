import { describe, expect, it, vi } from 'vitest'
import {
  allocateChildRowAutoPrimaryKeys,
  applyFieldDefinitionsToFormFields,
  applyFkPresentationToDialogColumns,
  applyMiParticipantRowSeedToInitialRow,
  filterStructuralFkMetasForBinding,
  finalizeSubTableRowOnSave,
  prepareSubTableAddRow,
  repairMisassignedPrimaryKeyFromParentId,
  seedLinkChildForeignKeysFromParentRow,
  toFieldFkMetas,
} from '../subTableRowRuntime'

describe('filterStructuralFkMetasForBinding', () => {
  const metas = toFieldFkMetas([
    {
      fieldName: 'row_id',
      isForeignKey: true,
      refTableId: 113,
      refPrimaryKeyFields: ['case_number'],
    },
    {
      fieldName: 'case_id',
      isForeignKey: true,
      refTableId: 113,
      refPrimaryKeyFields: ['case_number'],
    },
  ])

  it('keeps all metas for structuralFk mode', () => {
    expect(
      filterStructuralFkMetasForBinding(metas, {
        bindingLinkMode: 'structuralFk',
        bindingForeignKeyField: 'row_id',
      }),
    ).toHaveLength(2)
  })

  it('excludes legacy participant field for miParticipantRow mode', () => {
    const filtered = filterStructuralFkMetasForBinding(metas, {
      bindingLinkMode: 'miParticipantRow',
      bindingForeignKeyField: 'row_id',
    })
    expect(filtered.map(m => m.fieldName)).toEqual(['case_id'])
  })
})

describe('applyFkPresentationToDialogColumns auto-PK', () => {
  it('coerces inputNumber to text for prefixedSequence PK', () => {
    const { allColumns } = applyFkPresentationToDialogColumns(
      [{ field: 'id_idw', label: 'id', type: 'number', required: true }],
      [],
      [{
        fieldName: 'id_idw',
        isPrimaryKey: true,
        pkGeneration: { strategy: 'prefixedSequence', prefix: 'Test-', padWidth: 6 },
      }],
    )
    expect(allColumns[0].readonly).toBe(true)
    expect(allColumns[0].type).toBe('text')
  })

  it('coerces inputNumber to text for dailyDateSequence PK', () => {
    const { allColumns } = applyFkPresentationToDialogColumns(
      [{ field: 'id_idw', label: 'id', type: 'number', required: true }],
      [],
      [{
        fieldName: 'id_idw',
        isPrimaryKey: true,
        pkGeneration: { strategy: 'dailyDateSequence', padWidth: 4 },
      }],
    )
    expect(allColumns[0].readonly).toBe(true)
    expect(allColumns[0].type).toBe('text')
  })

  it('coerces inputNumber to text for monthlyDateSequence PK', () => {
    const { allColumns } = applyFkPresentationToDialogColumns(
      [{ field: 'id_idw', label: 'id', type: 'number', required: true }],
      [],
      [{
        fieldName: 'id_idw',
        isPrimaryKey: true,
        pkGeneration: { strategy: 'monthlyDateSequence', padWidth: 4 },
      }],
    )
    expect(allColumns[0].readonly).toBe(true)
    expect(allColumns[0].type).toBe('text')
  })
})

describe('applyFieldDefinitionsToFormFields', () => {
  it('coerces subForm inputNumber to text for auto uuid PK', () => {
    const out = applyFieldDefinitionsToFormFields(
      [{ key: 'id', type: 'number', label: 'id' }],
      [{ fieldName: 'id', isPrimaryKey: true }],
    )
    expect(out[0].type).toBe('text')
    expect(out[0].readonly).toBe(true)
  })
})

describe('seedLinkChildForeignKeysFromParentRow', () => {
  it('seeds sub_task_id from parent id_idw but not row PK when foreignKeyField is also PK', () => {
    const row = seedLinkChildForeignKeysFromParentRow(
      { sex: true, age: '12' },
      [
        { fieldName: 'id', isPrimaryKey: true },
        { fieldName: 'sub_task_id', isForeignKey: true, refTableId: 20, refPrimaryKeyFields: ['id_idw'] },
      ],
      {
        bindingForeignKeyField: 'id',
        bindingLinkMode: 'structuralFk',
        primaryKeyFields: ['id'],
        parentParticipantRow: { id_idw: 'Test-000017', name: 'dev' },
        parentTableId: 20,
        legacyFkSeed: 'Test-000017',
      },
    )
    expect(row.id).toBeUndefined()
    expect(row.sub_task_id).toBe('Test-000017')
    expect(row.sex).toBe(true)
  })

  // #1446 跟进：id_idw 是 MI collection 的参与者主键；link-child（People，行 PK=id）一旦被种入
  // id_idw 即成 #1435 腐坏镜像 → hydration 拒绝绑定 → 每次 Save 重新分配 UUID（id 漂移）。
  it('never seeds id_idw onto a link-child row whose own PK is not id_idw (legacy fk path)', () => {
    const row = seedLinkChildForeignKeysFromParentRow(
      { sex: true, age: '344' },
      [
        { fieldName: 'id', isPrimaryKey: true },
        { fieldName: 'sub_task_id', isForeignKey: true, refTableId: 20, refPrimaryKeyFields: ['id_idw'] },
      ],
      {
        bindingForeignKeyField: 'id_idw',
        bindingLinkMode: 'structuralFk',
        primaryKeyFields: ['id'],
        parentParticipantRow: { id_idw: 'Test-000076' },
        parentTableId: 20,
        legacyFkSeed: 'Test-000076',
      },
    )
    expect(row.id_idw).toBeUndefined()
    expect(row.sub_task_id).toBe('Test-000076')
  })

  it('never seeds id_idw via designer fieldDef FK meta when row PK is not id_idw', () => {
    const row = seedLinkChildForeignKeysFromParentRow(
      { sex: true },
      [
        { fieldName: 'id', isPrimaryKey: true },
        { fieldName: 'id_idw', isForeignKey: true, refTableId: 20, refPrimaryKeyFields: ['id_idw'] },
        { fieldName: 'sub_task_id', isForeignKey: true, refTableId: 20, refPrimaryKeyFields: ['id_idw'] },
      ],
      {
        bindingForeignKeyField: null,
        bindingLinkMode: 'structuralFk',
        primaryKeyFields: ['id'],
        parentParticipantRow: { id_idw: 'Test-000076' },
        parentTableId: 20,
        legacyFkSeed: 'Test-000076',
      },
    )
    expect(row.id_idw).toBeUndefined()
    expect(row.sub_task_id).toBe('Test-000076')
  })

  it('repairMisassignedPrimaryKeyFromParentId does not strip collection id_idw PK', () => {
    const row = repairMisassignedPrimaryKeyFromParentId(
      { id_idw: 'Test-000044', name: 'dev', assignee: { id: 'u1' } },
      [{ fieldName: 'id_idw', isPrimaryKey: true }],
      'Test-000044',
    )
    expect(row.id_idw).toBe('Test-000044')
  })

  it('repairMisassignedPrimaryKeyFromParentId clears People id wrongly copied from parent', () => {
    const row = repairMisassignedPrimaryKeyFromParentId(
      { id: 'Test-000044', sub_task_id: 'Test-000044', sex: true },
      [{ fieldName: 'id', isPrimaryKey: true }],
      'Test-000044',
    )
    expect(row.id).toBeUndefined()
    expect(row.sub_task_id).toBe('Test-000044')
  })
})

describe('applyMiParticipantRowSeedToInitialRow', () => {
  it('seeds row_id PK from MI participant without allocating a new id', () => {
    const row = applyMiParticipantRowSeedToInitialRow(
      { file: '' },
      {
        bindingLinkMode: 'miParticipantRow',
        bindingForeignKeyField: 'row_id',
        primaryKeyFields: ['row_id'],
        fieldDefinitions: [
          { fieldName: 'row_id', isPrimaryKey: true },
          { fieldName: 'case_id', isForeignKey: true, refTableId: 113, refPrimaryKeyFields: ['case_number'] },
        ],
        miParticipantRowId: '455656',
        miParentParticipantRow: { row_id: '455656', case_number: 'CASE-9' },
        miParentTableId: 112,
      },
    )
    expect(row.row_id).toBe('455656')
  })
})

describe('prepareSubTableAddRow miParticipantRow', () => {
  it('does not block add when only excluded participant FK is missing from context', async () => {
    const result = await prepareSubTableAddRow({
      columns: [{ field: 'case_id', label: 'Case ID', type: 'input' }],
      fieldDefinitions: [
        {
          fieldName: 'row_id',
          isForeignKey: true,
          refTableId: 113,
          refPrimaryKeyFields: ['case_number'],
        },
        {
          fieldName: 'case_id',
          isForeignKey: true,
          refTableId: 113,
          refPrimaryKeyFields: ['case_number'],
        },
      ],
      rowAddContext: {
        primaryFormData: { case_number: 'CASE-1' },
        ancestorRowsByTableId: { 113: { case_number: 'CASE-1' } },
      },
      bindingLinkMode: 'miParticipantRow',
      bindingForeignKeyField: 'row_id',
      requireFkGuard: true,
    })

    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.initialRow.case_id).toBe('CASE-1')
      expect(result.initialRow.row_id).toBeUndefined()
    }
  })

  it('defers all PK allocate until Save when deferPkAllocationUntilSave', async () => {
    const allocate = vi.fn().mockResolvedValue(['Test-000001'])
    const fieldDefinitions = [
      {
        fieldName: 'id_idw',
        isPrimaryKey: true,
        pkGeneration: { strategy: 'prefixedSequence', prefix: 'Test-', padWidth: 6 },
      },
      {
        fieldName: 'main_id',
        isForeignKey: true,
        refTableId: 19,
        refPrimaryKeyFields: ['id'],
        fkDisplayMode: 'readonly',
      },
    ]
    const result = await prepareSubTableAddRow({
      columns: [
        { field: 'id_idw', label: 'id', type: 'number', required: true },
        { field: 'main_id', label: 'main id', type: 'text', required: true },
      ],
      fieldDefinitions,
      rowAddContext: {
        primaryFormData: { id: '9f3e1925-25bf-4f06-a39a-3ba2dcb87b13' },
        ancestorRowsByTableId: { 19: { id: '9f3e1925-25bf-4f06-a39a-3ba2dcb87b13' } },
      },
      tableId: 20,
      bindingLinkMode: 'miParticipantRow',
      bindingForeignKeyField: 'id_idw',
      primaryKeyFields: ['id_idw'],
      deferPkAllocationUntilSave: true,
      allocatePrimaryKeys: allocate,
      functionUnitId: 'Process_1_KK',
    })
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.initialRow.id_idw == null || result.initialRow.id_idw === '').toBe(true)
      expect(result.initialRow.main_id).toBe('9f3e1925-25bf-4f06-a39a-3ba2dcb87b13')
      expect(result.dialogColumns.find(c => c.field === 'id_idw')?.type).toBe('text')
      expect(result.dialogColumns.find(c => c.field === 'id_idw')?.readonly).toBe(true)
    }
    expect(allocate).not.toHaveBeenCalled()

    const saved = await finalizeSubTableRowOnSave({
      row: { ...(result.ok ? result.initialRow : {}), name: '33' },
      fieldDefinitions,
      rowAddContext: {
        primaryFormData: { id: '9f3e1925-25bf-4f06-a39a-3ba2dcb87b13' },
        ancestorRowsByTableId: { 19: { id: '9f3e1925-25bf-4f06-a39a-3ba2dcb87b13' } },
      },
      tableId: 20,
      allocatePrimaryKeys: allocate,
      functionUnitId: 'Process_1_KK',
      bindingLinkMode: 'miParticipantRow',
      bindingForeignKeyField: 'id_idw',
      primaryKeyFields: ['id_idw'],
      autoEnsurePrimaryRecord: true,
    })
    expect(saved.ok).toBe(true)
    if (saved.ok) {
      expect(saved.row.id_idw).toBe('Test-000001')
      expect(saved.row.main_id).toBe('9f3e1925-25bf-4f06-a39a-3ba2dcb87b13')
    }
    expect(allocate).toHaveBeenCalledWith({
      tableId: 20,
      fieldName: 'id_idw',
      scopeKey: 'Process_1_KK',
    })
  })

  it('finalizeSubTableRowOnSave allocates main PK before child PK when main row is empty', async () => {
    const allocate = vi.fn(async (payload: { tableId: number; fieldName: string }) => {
      if (payload.tableId === 19 && payload.fieldName === 'id') return ['main-uuid-1']
      if (payload.tableId === 20 && payload.fieldName === 'id_idw') return ['Test-000002']
      return []
    })
    const fieldDefinitions = [
      {
        fieldName: 'id_idw',
        isPrimaryKey: true,
        pkGeneration: { strategy: 'prefixedSequence', prefix: 'Test-', padWidth: 6 },
      },
      {
        fieldName: 'main_id',
        isForeignKey: true,
        refTableId: 19,
        refPrimaryKeyFields: ['id'],
        fkDisplayMode: 'readonly',
      },
    ]
    const addOpen = await prepareSubTableAddRow({
      columns: [
        { field: 'id_idw', label: 'id', type: 'text' },
        { field: 'main_id', label: 'main id', type: 'text' },
      ],
      fieldDefinitions,
      rowAddContext: { primaryFormData: {}, ancestorRowsByTableId: { 19: {} } },
      tableId: 20,
      primaryTableId: 19,
      parentTablesById: {
        19: {
          fieldDefinitions: [{
            fieldName: 'id',
            isPrimaryKey: true,
            pkGeneration: { strategy: 'uuid' },
          }],
        },
      },
      autoEnsurePrimaryRecord: true,
      deferPkAllocationUntilSave: true,
      allocatePrimaryKeys: allocate,
      functionUnitId: 'Process_1_KK',
    })
    expect(addOpen.ok).toBe(true)
    expect(allocate).not.toHaveBeenCalled()

    const saved = await finalizeSubTableRowOnSave({
      row: { name: 'row-1' },
      fieldDefinitions,
      rowAddContext: { primaryFormData: {}, ancestorRowsByTableId: { 19: {} } },
      tableId: 20,
      primaryTableId: 19,
      parentTablesById: {
        19: {
          fieldDefinitions: [{
            fieldName: 'id',
            isPrimaryKey: true,
            pkGeneration: { strategy: 'uuid' },
          }],
        },
      },
      autoEnsurePrimaryRecord: true,
      allocatePrimaryKeys: allocate,
      functionUnitId: 'Process_1_KK',
    })
    expect(saved.ok).toBe(true)
    if (saved.ok) {
      expect(saved.primaryFormDataPatch).toEqual({ id: 'main-uuid-1' })
      expect(saved.row.main_id).toBe('main-uuid-1')
      expect(saved.row.id_idw).toBe('Test-000002')
    }
    expect(allocate.mock.calls[0]?.[0]).toEqual(expect.objectContaining({ tableId: 19, fieldName: 'id' }))
    expect(allocate.mock.calls[1]?.[0]).toEqual(expect.objectContaining({ tableId: 20, fieldName: 'id_idw' }))
  })

  it('seeds attachment row_id from MI participant and skips PK allocate for that field', async () => {
    const allocate = vi.fn()
    const result = await prepareSubTableAddRow({
      columns: [{ field: 'file', label: 'File', type: 'upload' }],
      fieldDefinitions: [
        { fieldName: 'row_id', isPrimaryKey: true },
        { fieldName: 'case_id', isForeignKey: true, refTableId: 113, refPrimaryKeyFields: ['case_number'] },
      ],
      rowAddContext: {
        primaryFormData: { case_number: 'CASE-1' },
        ancestorRowsByTableId: { 113: { case_number: 'CASE-1' } },
      },
      tableId: 114,
      bindingLinkMode: 'miParticipantRow',
      bindingForeignKeyField: 'row_id',
      primaryKeyFields: ['row_id'],
      miParticipantRowId: '455656',
      miParentParticipantRow: { row_id: '455656' },
      miParentTableId: 112,
      allocatePrimaryKeys: allocate,
    })
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.initialRow.row_id).toBe('455656')
      expect(result.initialRow.case_id).toBe('CASE-1')
    }
    expect(allocate).not.toHaveBeenCalled()
  })
})
