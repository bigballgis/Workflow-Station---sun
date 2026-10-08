import { describe, expect, it, vi } from 'vitest'
import {
  allocateChildRowAutoPrimaryKeys,
  applyFieldDefinitionsToFormFields,
  applyFieldPermissionsToDialogColumns,
  applyFkPresentationToDialogColumns,
  applyMiParticipantRowSeedToInitialRow,
  filterStructuralFkMetasForBinding,
  finalizeSubTableRowOnSave,
  prepareSubTableAddRow,
  repairMisassignedPrimaryKeyFromParentId,
  seedLinkChildForeignKeysFromParentRow,
  toFieldFkMetas,
  buildRowAddContext,
  ensureParentRowsForChildAdd,
} from '../subTableRowRuntime'
import { applyFkToInitialRow } from '../tableFkRuntime'

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

describe('missing structural FK feedback', () => {
  it('names the missing FK and parent table when a child row cannot be saved', async () => {
    const seen: Array<{ key: string; params?: Record<string, unknown> }> = []
    const result = await finalizeSubTableRowOnSave({
      row: { file_name: 'alice.doc' },
      fieldDefinitions: [{
        fieldName: 'party_id',
        isForeignKey: true,
        refTableId: 50200,
        refPrimaryKeyFields: ['id'],
      }],
      rowAddContext: { primaryFormData: {}, ancestorRowsByTableId: {} },
      tableId: 50348,
      tableDisplayName: 'Party Files',
      parentTableDisplayNamesById: { 50200: 'P0 Dual Party' },
      t: ((key: string, params?: Record<string, unknown>) => {
        seen.push({ key, params })
        return key
      }) as any,
    } as any)

    expect(result.ok).toBe(false)
    expect(seen).toContainEqual({
      key: 'subTable.fkGuardMissingParents',
      params: {
        childTableName: 'Party Files',
        missingDetails: 'party_id (P0 Dual Party)',
      },
    })
  })

  it('does not skip the binding-configured parent FK in a scoped main-form context', async () => {
    const result = await finalizeSubTableRowOnSave({
      row: { file_name: 'alice.doc' },
      fieldDefinitions: [
        { fieldName: 'id', isPrimaryKey: true, pkGeneration: { strategy: 'uuid' } },
        { fieldName: 'case_id', isForeignKey: true, refTableId: 501, refPrimaryKeyFields: ['id'] },
        { fieldName: 'party_id', isForeignKey: true, refTableId: 502, refPrimaryKeyFields: ['id'] },
      ],
      rowAddContext: buildRowAddContext(
        { id: 'Case-1' },
        [{ tableId: 501, bindingType: 'PRIMARY' }],
      ),
      tableId: 503,
      tableDisplayName: 'Party files',
      bindingLinkMode: 'structuralFk',
      bindingForeignKeyField: 'party_id',
      primaryKeyFields: ['id'],
      parentTableDisplayNamesById: { 502: 'P0 Dual Party' },
      allocatePrimaryKeys: vi.fn(async () => ['File-1']),
      t: ((key: string, params?: Record<string, unknown>) =>
        key === 'subTable.fkGuardMissingParents'
          ? `missing ${String(params?.missingDetails)}`
          : key) as any,
    } as any)

    expect(result).toEqual({ ok: false, message: 'missing party_id (P0 Dual Party)' })
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

  it('coerces inputNumber to text for customFormat and legacy datePrefixedSequence PK', () => {
    const datePrefixed = applyFkPresentationToDialogColumns(
      [{ field: 'id_idw', label: 'id', type: 'number', required: true }],
      [],
      [{
        fieldName: 'id_idw',
        isPrimaryKey: true,
        pkGeneration: { strategy: 'datePrefixedSequence' },
      }],
    )
    expect(datePrefixed.allColumns[0].type).toBe('text')
    const custom = applyFkPresentationToDialogColumns(
      [{ field: 'id_idw', label: 'id', type: 'number', required: true }],
      [],
      [{
        fieldName: 'id_idw',
        isPrimaryKey: true,
        pkGeneration: { strategy: 'customFormat' },
      }],
    )
    expect(custom.allColumns[0].type).toBe('text')
  })
})

describe('applyFieldPermissionsToDialogColumns', () => {
  const columns = [
    { field: 'name', label: 'Name', type: 'text' as const },
    { field: 'bu_code', label: 'Business Unit', type: 'select' as const },
    { field: 'role_code', label: 'Role', type: 'select' as const },
  ]

  it('marks READONLY composite-keyed fields readonly, leaves EDITABLE/unlisted fields untouched', () => {
    const out = applyFieldPermissionsToDialogColumns(columns, 50544, {
      '50544:bu_code': 'READONLY',
      '50544:role_code': 'READONLY',
      '50544:name': 'EDITABLE',
    })
    expect(out.find(c => c.field === 'bu_code')?.readonly).toBe(true)
    expect(out.find(c => c.field === 'role_code')?.readonly).toBe(true)
    expect(out.find(c => c.field === 'name')?.readonly).toBeFalsy()
  })

  it('does not apply another binding\'s composite key to this binding\'s same-named field', () => {
    const out = applyFieldPermissionsToDialogColumns(columns, 50544, {
      '50999:bu_code': 'READONLY',
    })
    expect(out.find(c => c.field === 'bu_code')?.readonly).toBeFalsy()
  })

  it('passes through unchanged when no composite key exists for this binding (backward compatible)', () => {
    const out = applyFieldPermissionsToDialogColumns(columns, 50544, { name: 'READONLY' })
    expect(out).toEqual(columns)
  })

  it('passes through unchanged when fieldPermissions or bindingId is absent', () => {
    expect(applyFieldPermissionsToDialogColumns(columns, undefined, { '50544:name': 'READONLY' })).toEqual(columns)
    expect(applyFieldPermissionsToDialogColumns(columns, 50544, null)).toEqual(columns)
  })

  it('never turns an already-readonly column (e.g. auto-PK) editable', () => {
    const readonlyCol = [{ field: 'id_idw', label: 'Id', type: 'text' as const, readonly: true }]
    const out = applyFieldPermissionsToDialogColumns(readonlyCol, 50544, { '50544:id_idw': 'EDITABLE' })
    expect(out[0]?.readonly).toBe(true)
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
      ['id_idw'],   // 父表设计器主键，必须显式传入
    )
    expect(row.id_idw).toBe('Test-000044')
  })

  it('repairMisassignedPrimaryKeyFromParentId clears People id wrongly copied from parent', () => {
    const row = repairMisassignedPrimaryKeyFromParentId(
      { id: 'Test-000044', sub_task_id: 'Test-000044', sex: true },
      [{ fieldName: 'id', isPrimaryKey: true }],
      'Test-000044',
      ['id_idw'],
    )
    expect(row.id).toBeUndefined()
    expect(row.sub_task_id).toBe('Test-000044')
  })

  it('父表主键叫 row_id 时同样受保护（不再写死 id_idw）', () => {
    // 回归：守卫此前只认字面量 'id_idw'，主键叫 row_id 的表（ATM_Transaction）
    // 会把 collection 自己的主键当成「误copy」删掉。
    const row = repairMisassignedPrimaryKeyFromParentId(
      { row_id: 'R-7', name: 'dev' },
      [{ fieldName: 'row_id', isPrimaryKey: true }],
      'R-7',
      ['row_id'],
    )
    expect(row.row_id).toBe('R-7')
  })

  it('未传父表主键时什么都不删（不猜列名，也不抛错）', () => {
    // 这个函数是**删值**的：无从判断该保护哪个主键时，保持原样才是安全的一侧。
    // 抛错会中断整个 Save（共享附件等 binding 本就没有设计器 PK）。
    const row = repairMisassignedPrimaryKeyFromParentId(
      { id_idw: 'Test-000044' },
      [{ fieldName: 'id_idw', isPrimaryKey: true }],
      'Test-000044',
    )
    expect(row.id_idw).toBe('Test-000044')
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

describe('dual-binding FK parent context', () => {
  const MAIN = 50100
  const PARTY = 50200
  const fileFks = toFieldFkMetas([
    { fieldName: 'case_id', isForeignKey: true, refTableId: MAIN, refPrimaryKeyFields: ['id'] },
    { fieldName: 'party_id', isForeignKey: true, refTableId: PARTY, refPrimaryKeyFields: ['id'] },
  ])

  it('fills only the current binding filter FK unless another source is explicit', async () => {
    const context = buildRowAddContext(
      { id: 'Case-1' },
      [
        { tableId: MAIN, bindingType: 'PRIMARY' },
        { bindingId: 50704, tableId: PARTY, bindingType: 'SUB', data: [{ id: 'Party-1' }] },
      ],
    )
    const result = await finalizeSubTableRowOnSave({
      row: { file_name: 'party.doc' },
      fieldDefinitions: [
        { fieldName: 'id', isPrimaryKey: true, pkGeneration: { strategy: 'uuid' } },
        { fieldName: 'case_id', isForeignKey: true, refTableId: MAIN, refPrimaryKeyFields: ['id'] },
        { fieldName: 'party_id', isForeignKey: true, refTableId: PARTY, refPrimaryKeyFields: ['id'] },
      ],
      rowAddContext: context,
      tableId: 50348,
      filterFkFieldName: 'party_id',
      allocatePrimaryKeys: vi.fn(async () => ['File-1']),
    } as any)

    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.row.party_id).toBe('Party-1')
      expect(result.row.case_id).toBeUndefined()
    }
  })

  it('also fills an additional FK when fkFillSources explicitly declares it', async () => {
    const context = buildRowAddContext(
      { id: 'Case-1' },
      [
        { tableId: MAIN, bindingType: 'PRIMARY' },
        { bindingId: 50704, tableId: PARTY, bindingType: 'SUB', data: [{ id: 'Party-1' }] },
      ],
    )
    const result = await finalizeSubTableRowOnSave({
      row: { file_name: 'nested.doc' },
      fieldDefinitions: [
        { fieldName: 'id', isPrimaryKey: true, pkGeneration: { strategy: 'uuid' } },
        { fieldName: 'case_id', isForeignKey: true, refTableId: MAIN, refPrimaryKeyFields: ['id'] },
        { fieldName: 'party_id', isForeignKey: true, refTableId: PARTY, refPrimaryKeyFields: ['id'] },
      ],
      rowAddContext: context,
      tableId: 50348,
      filterFkFieldName: 'party_id',
      fkFillSources: [{ fieldName: 'case_id', kind: 'PRIMARY' }],
      allocatePrimaryKeys: vi.fn(async () => ['File-2']),
    })

    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.row.party_id).toBe('Party-1')
      expect(result.row.case_id).toBe('Case-1')
    }
  })

  it('does not copy MAIN pk onto a nested party_id when the party ancestor is absent', () => {
    const row = applyFkToInitialRow({}, fileFks, {
      primaryFormData: { id: 'Case-1', title: 'x' },
      ancestorRowsByTableId: { [MAIN]: { id: 'Case-1', title: 'x' } },
    })
    expect(row.case_id).toBe('Case-1')
    expect(row.party_id).toBeUndefined()
  })

  it('stamps party_id from the unique sibling party row of the current filter', () => {
    const ctx = buildRowAddContext(
      { id: 'Case-1' },
      [
        { tableId: MAIN, bindingType: 'PRIMARY' },
        { bindingId: 50704, tableId: PARTY, bindingType: 'SUB', data: [{ id: 'Party-1' }] },
        { bindingId: 50706, tableId: 50348, bindingType: 'SUB', filterFkRefTableId: PARTY, data: [] },
      ],
      null,
      null,
      { bindingId: 50706, tableId: 50348, filterFkRefTableId: PARTY },
    )
    const row = applyFkToInitialRow({}, fileFks, ctx)
    expect(row.case_id).toBe('Case-1')
    expect(row.party_id).toBe('Party-1')
  })

  it('does not invent a nested parent PK for a filter target that is not in context', async () => {
    const allocate = vi.fn(async (payload: { tableId: number; fieldName: string }) => {
      if (payload.tableId === MAIN) return ['Case-new']
      if (payload.tableId === PARTY) return ['Party-invented']
      return []
    })
    const ensured = await ensureParentRowsForChildAdd({
      fkMetas: fileFks,
      rowAddContext: {
        primaryFormData: {},
        ancestorRowsByTableId: { [MAIN]: {} },
      },
      parentTablesById: {
        [MAIN]: { fieldDefinitions: [{ fieldName: 'id', isPrimaryKey: true, pkGeneration: { strategy: 'uuid' } }] },
        [PARTY]: { fieldDefinitions: [{ fieldName: 'id', isPrimaryKey: true, pkGeneration: { strategy: 'uuid' } }] },
      },
      allocatePrimaryKeys: allocate,
      primaryTableId: MAIN,
    })
    expect(allocate.mock.calls.every(c => c[0].tableId !== PARTY)).toBe(true)
    expect(ensured.rowAddContext.ancestorRowsByTableId?.[PARTY]).toBeUndefined()
  })

  it('stamps party_id from the host parent when a saved sibling party is also in context', () => {
    const ctx = buildRowAddContext(
      { id: 'Case-1' },
      [{ tableId: MAIN, bindingType: 'PRIMARY' }],
      { id: 'Party-A' },
      PARTY,
    )
    ctx.contextFrames = [
      ...(ctx.contextFrames ?? []),
      { tableId: PARTY, row: { id: 'Party-B' }, role: 'FILTER_SIBLING' },
    ]
    const row = applyFkToInitialRow({}, fileFks, ctx)
    expect(row.case_id).toBe('Case-1')
    expect(row.party_id).toBe('Party-A')
  })
})
