import { describe, expect, it } from 'vitest'
import {
  deriveColumnsFromRelationFieldDefinitions,
  resolveSubTableSchemaByTableId,
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
})
