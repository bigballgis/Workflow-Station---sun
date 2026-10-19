import { describe, expect, it, vi } from 'vitest'
import { prepareBatchAdd, type BatchAddOptions } from '../subTableBatchAdd'

function options(): BatchAddOptions {
  return {
    rows: [{ description: 'first' }, { description: 'second' }],
    tableId: 20, primaryTableId: 19, primaryKeyFields: ['item_key'],
    bindingLinkMode: 'structuralFk', autoEnsurePrimaryRecord: true,
    rowAddContext: { primaryFormData: {}, ancestorRowsByTableId: { 19: {} } },
    fieldDefinitions: [
      { fieldName: 'item_key', isPrimaryKey: true, pkGeneration: { strategy: 'uuid' } },
      { fieldName: 'case_reference', isForeignKey: true, refTableId: 19, refPrimaryKeyFields: ['case_key'] },
    ],
    parentTablesById: { 19: { fieldDefinitions: [{ fieldName: 'case_key', isPrimaryKey: true, pkGeneration: { strategy: 'uuid' } }] } },
    allocatePrimaryKeys: async payload => payload.tableId === 19 ? ['P1'] : ['C1', 'C2'],
  }
}

describe('prepareBatchAdd', () => {
  it('prepares one parent patch and unique child keys without mutating the page', async () => {
    const input = options()
    const allocate = vi.fn(input.allocatePrimaryKeys)
    const prepared = await prepareBatchAdd({ ...input, allocatePrimaryKeys: allocate })
    expect(prepared.primaryFormDataPatch).toEqual({ case_key: 'P1' })
    expect(prepared.rows).toEqual([
      { description: 'first', case_reference: 'P1', item_key: 'C1' },
      { description: 'second', case_reference: 'P1', item_key: 'C2' },
    ])
    expect(input.rowAddContext.primaryFormData).toEqual({})
    expect(input.rows).toEqual([{ description: 'first' }, { description: 'second' }])
    expect(allocate.mock.calls).toEqual([
      [{ tableId: 19, fieldName: 'case_key', scopeKey: undefined }],
      [{ tableId: 20, fieldName: 'item_key', count: 2 }],
    ])
  })

  it('rejects incomplete allocation without exposing partial page mutations', async () => {
    const input = options()
    input.allocatePrimaryKeys = async () => ['P1']
    await expect(prepareBatchAdd(input)).rejects.toThrow('incomplete result')
    expect(input.rowAddContext.primaryFormData).toEqual({})
    expect(input.rows).toEqual([{ description: 'first' }, { description: 'second' }])
  })

  it('does not allocate for MI participant membership', async () => {
    const input = options()
    const allocate = vi.fn(input.allocatePrimaryKeys)
    await expect(prepareBatchAdd({ ...input, bindingLinkMode: 'miParticipantRow', allocatePrimaryKeys: allocate }))
      .rejects.toThrow('MI participant membership')
    expect(allocate).not.toHaveBeenCalled()
  })

  it('rejects duplicate manual keys before allocating any parent or child sequence', async () => {
    const input = options()
    input.fieldDefinitions![0]!.pkGeneration = { strategy: 'manual' }
    input.rows = [{ item_key: 'DUP' }, { item_key: 'DUP' }]
    const allocate = vi.fn(input.allocatePrimaryKeys)
    await expect(prepareBatchAdd({ ...input, allocatePrimaryKeys: allocate })).rejects.toThrow('duplicate manual')
    expect(allocate).not.toHaveBeenCalled()
  })
})
