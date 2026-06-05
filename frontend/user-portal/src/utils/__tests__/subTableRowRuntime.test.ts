import { describe, expect, it, vi } from 'vitest'
import {
  applyFieldDefinitionsToFormFields,
  applyFkPresentationToDialogColumns,
  applyMiParticipantRowSeedToInitialRow,
  filterStructuralFkMetasForBinding,
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
