import { describe, expect, it } from 'vitest'
import {
  deriveColumnsFromRelationFieldDefinitions,
  mergeMissingTableFieldColumns,
  resolveSubTableSchemaByTableId,
  resolveSubListViewColumnsForBinding,
  defaultAttachmentListColumns,
} from '../subTableAddDialogHelpers'

describe('subTableSchemaResolution', () => {
  it('deriveColumnsFromRelationFieldDefinitions maps FILE to upload', () => {
    const cols = deriveColumnsFromRelationFieldDefinitions([
      { fieldName: 'id', dataType: 'VARCHAR', description: 'id', sortOrder: 0 },
      { fieldName: 'main_id', dataType: 'VARCHAR', description: 'main_id', sortOrder: 1 },
      { fieldName: 'file', dataType: 'FILE', description: 'file', sortOrder: 2 },
    ])
    expect(cols.map(c => c.field)).toEqual(['id', 'main_id', 'file'])
    expect(cols[2].type).toBe('upload')
  })

  it('defaultAttachmentListColumns provides id/main_id/file for empty copied-form subListViews', () => {
    const cols = defaultAttachmentListColumns()
    expect(cols.map(c => c.field)).toEqual(['id', 'main_id', 'file'])
    expect(cols[2]?.type).toBe('upload')
  })

  it('mergeMissingTableFieldColumns keeps designed columns untouched (DW parity)', () => {
    const designed = [
      { field: 'name', label: 'name' },
      { field: 'id', label: 'id' },
      { field: 'assignee', label: 'assignee' },
    ]
    const tableFields = [
      { fieldName: 'name', dataType: 'VARCHAR', description: 'Name', sortOrder: 0 },
      { fieldName: 'id_idw', dataType: 'VARCHAR', description: 'id_idw', sortOrder: 1 },
      { fieldName: 'main_id', dataType: 'VARCHAR', description: 'main id', sortOrder: 2 },
      { fieldName: 'test', dataType: 'VARCHAR', description: 'test', sortOrder: 3 },
      { fieldName: 'created_at', dataType: 'TIMESTAMP', description: 'created_at', sortOrder: 4 },
      { fieldName: 'updated_by', dataType: 'VARCHAR', description: 'updated_by', sortOrder: 5 },
    ]
    const cols = mergeMissingTableFieldColumns(designed, tableFields)
    expect(cols.map(c => c.field)).toEqual(['name', 'id', 'assignee'])
  })

  it('mergeMissingTableFieldColumns falls back to table schema when no designed columns', () => {
    const tableFields = [
      { fieldName: 'name', dataType: 'VARCHAR', description: 'Name', sortOrder: 0 },
      { fieldName: 'created_at', dataType: 'TIMESTAMP', description: 'created_at', sortOrder: 1 },
    ]
    const cols = mergeMissingTableFieldColumns([], tableFields)
    expect(cols.map(c => c.field)).toEqual(['name', 'created_at'])
    expect(cols.find(c => c.field === 'created_at')?.readonly).toBe(true)
  })

  it('resolveSubTableSchemaByTableId finds sibling form schema for same tableId', () => {
    const contentForms = [
      {
        name: 'subform_copy',
        data: JSON.stringify({
          subForms: { '104': { rule: [] } },
          subListViews: { '104': { columns: [] } },
        }),
        tableBindings: [{ bindingId: 104, tableId: 74 }],
      },
      {
        name: 'subform',
        data: JSON.stringify({
          subForms: {
            '103': {
              rule: [
                { field: 'id', type: 'input', title: 'id' },
                { field: 'main_id', type: 'input', title: 'main_id' },
                { field: 'file', type: 'upload', title: 'file' },
              ],
            },
          },
          subListViews: {
            '103': {
              columns: [
                { fieldName: 'id', comment: 'id', dataType: 'VARCHAR', columnType: 'field' },
                { fieldName: 'main_id', comment: 'main_id', dataType: 'VARCHAR', columnType: 'field' },
                { fieldName: 'file', comment: 'file', dataType: 'FILE', columnType: 'field' },
              ],
            },
          },
        }),
        tableBindings: [{ bindingId: 103, tableId: 74 }],
      },
    ]
    const alt = resolveSubTableSchemaByTableId(74, contentForms, 104)
    expect(alt?.bindingId).toBe(103)
    expect(alt?.formConfig.subListViews?.['103']?.columns?.length).toBe(3)
  })

  it('resolveSubListViewColumnsForBinding rejects PK-only stub when subForm has more fields', () => {
    const formConfig = {
      subListViews: {
        64: { columns: [{ fieldName: 'id_idw', columnType: 'field', comment: 'id' }] },
        62: {
          columns: [
            { fieldName: 'id', columnType: 'field' },
            { fieldName: 'name', columnType: 'field' },
            { fieldName: 'assignee', columnType: 'field' },
          ],
        },
      },
    }
    const subFormFields = ['id_idw', 'name', 'assignee']
    expect(resolveSubListViewColumnsForBinding(formConfig, 64, subFormFields)).toBeNull()
  })

  it('resolveSubListViewColumnsForBinding keeps intentional multi-column designer list', () => {
    const formConfig = {
      subListViews: {
        64: {
          columns: [
            { fieldName: 'id_idw', columnType: 'field' },
            { fieldName: 'name', columnType: 'field' },
          ],
        },
      },
    }
    const cols = resolveSubListViewColumnsForBinding(formConfig, 64, ['id_idw', 'name', 'assignee'])
    expect(cols?.map(c => c.fieldName)).toEqual(['id_idw', 'name'])
  })
})
