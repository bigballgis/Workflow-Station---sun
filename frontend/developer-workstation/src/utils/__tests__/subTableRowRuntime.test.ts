import { describe, expect, it, vi } from 'vitest'
import { applyFkPresentationToDialogColumns, prepareSubTableAddRow } from '@/utils/subTableRowRuntime'

describe('applyFkPresentationToDialogColumns', () => {
  it('marks auto-generated PK columns readonly', () => {
    const { visibleColumns, allColumns } = applyFkPresentationToDialogColumns(
      [{ field: 'id', label: 'ID', type: 'text' }],
      [],
      [{
        fieldName: 'id',
        isPrimaryKey: true,
        pkGeneration: { strategy: 'autoIncrement' },
      }],
    )

    expect(allColumns[0].readonly).toBe(true)
    expect(visibleColumns[0].readonly).toBe(true)
  })

  it('leaves manual PK editable', () => {
    const { allColumns } = applyFkPresentationToDialogColumns(
      [{ field: 'id', label: 'ID', type: 'text' }],
      [],
      [{
        fieldName: 'id',
        isPrimaryKey: true,
        pkGeneration: { strategy: 'manual' },
      }],
    )

    expect(allColumns[0].readonly).toBeUndefined()
  })

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

  it('coerces inputNumber to text for customFormat PK', () => {
    const { allColumns } = applyFkPresentationToDialogColumns(
      [{ field: 'id_idw', label: 'id', type: 'number', required: true }],
      [],
      [{
        fieldName: 'id_idw',
        isPrimaryKey: true,
        pkGeneration: { strategy: 'customFormat', format: '{DATETIME:yyyy-dd-MM}-{SEQNUM:4}' },
      }],
    )
    expect(allColumns[0].readonly).toBe(true)
    expect(allColumns[0].type).toBe('text')
  })
})

describe('prepareSubTableAddRow autoEnsurePrimaryRecord', () => {
  it('previews a dual binding with only its declared filter FK by default', async () => {
    const result = await prepareSubTableAddRow({
      columns: [
        { field: 'case_id', label: 'Case', type: 'text' },
        { field: 'party_id', label: 'Party', type: 'text' },
      ],
      fieldDefinitions: [
        { fieldName: 'case_id', isForeignKey: true, refTableId: 19, refPrimaryKeyFields: ['id'] },
        { fieldName: 'party_id', isForeignKey: true, refTableId: 21, refPrimaryKeyFields: ['id'] },
      ],
      rowAddContext: {
        primaryFormData: { id: 'Case-1' },
        ancestorRowsByTableId: {
          19: { id: 'Case-1' },
          21: { id: 'Party-1' },
        },
      },
      tableId: 20,
      filterFkFieldName: 'party_id',
    })

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.initialRow.party_id).toBe('Party-1')
    expect(result.initialRow.case_id).toBe('')
  })

  it('allocates main table PK before opening sub-table add when main row is empty', async () => {
    const allocatePrimaryKeys = vi.fn(async (payload: { tableId: number; fieldName: string }) => {
      if (payload.tableId === 19 && payload.fieldName === 'id') return ['main-uuid-1']
      if (payload.tableId === 20 && payload.fieldName === 'id_idw') return ['sub-uuid-1']
      return []
    })

    const result = await prepareSubTableAddRow({
      columns: [{ field: 'id_idw', label: 'id', type: 'text' }, { field: 'name', label: 'name', type: 'text' }],
      fieldDefinitions: [
        {
          fieldName: 'id_idw',
          isForeignKey: true,
          refTableId: 19,
          refPrimaryKeyFields: ['id'],
          fkDisplayMode: 'readonly',
        },
        {
          fieldName: 'name',
        },
      ],
      rowAddContext: {
        primaryFormData: {},
        ancestorRowsByTableId: { 19: {} },
      },
      tableId: 20,
      tableDisplayName: 'Sub Task',
      primaryTableDisplayName: 'Main',
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
      allocatePrimaryKeys,
    })

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.primaryFormDataPatch).toEqual({ id: 'main-uuid-1' })
    expect(result.initialRow.id_idw).toBe('main-uuid-1')
    expect(allocatePrimaryKeys).toHaveBeenCalledWith(expect.objectContaining({ tableId: 19, fieldName: 'id' }))
  })

  it('still blocks when main PK is manual and empty', async () => {
    const result = await prepareSubTableAddRow({
      columns: [{ field: 'main_id', label: 'main_id', type: 'text' }],
      fieldDefinitions: [{
        fieldName: 'main_id',
        isForeignKey: true,
        refTableId: 19,
        refPrimaryKeyFields: ['id'],
      }],
      rowAddContext: { primaryFormData: {}, ancestorRowsByTableId: { 19: {} } },
      tableId: 20,
      primaryTableId: 19,
      parentTablesById: {
        19: {
          fieldDefinitions: [{
            fieldName: 'id',
            isPrimaryKey: true,
            pkGeneration: { strategy: 'manual' },
          }],
        },
      },
      autoEnsurePrimaryRecord: true,
      allocatePrimaryKeys: vi.fn(async () => ['should-not-be-used']),
      t: (key) => key,
    })

    expect(result.ok).toBe(false)
    if (result.ok) return
    expect(result.message).toBe('subTable.fkGuardMainNotReady')
  })

  it('allocates sub-table PK into initialRow', async () => {
    const allocatePrimaryKeys = vi.fn(async (payload: { tableId: number; fieldName: string }) => {
      if (payload.tableId === 20 && payload.fieldName === 'id_idw') return ['9001']
      return []
    })

    const result = await prepareSubTableAddRow({
      columns: [{ field: 'id_idw', label: 'id', type: 'number' }, { field: 'name', label: 'name', type: 'text' }],
      fieldDefinitions: [{
        fieldName: 'id_idw',
        isPrimaryKey: true,
        pkGeneration: { strategy: 'autoIncrement' },
      }, {
        fieldName: 'name',
      }],
      rowAddContext: { primaryFormData: { id: 'main-uuid' }, ancestorRowsByTableId: { 19: { id: 'main-uuid' } } },
      tableId: 20,
      allocatePrimaryKeys,
      requireFkGuard: false,
    })

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.initialRow.id_idw).toBe('9001')
  })
})
