import { describe, expect, it } from 'vitest'
import { buildSendTaskAttachmentFieldOptions } from '../sendTaskAttachmentFieldOptions'
import type { FormDefinition, TableDefinition } from '@/api/functionUnit'

describe('buildSendTaskAttachmentFieldOptions', () => {
  const mainTable = {
    id: 1,
    tableName: 'HMDC_Case',
    tableDisplayName: 'HMDC Case',
    tableType: 'MAIN',
    fieldDefinitions: [
      { fieldName: 'case_no', displayName: 'Case No', dataType: 'VARCHAR' },
      { fieldName: 'file', displayName: 'file', dataType: 'VARCHAR' },
      { fieldName: 'contract', displayName: 'Contract', dataType: 'FILE' },
    ],
  } as TableDefinition

  it('includes MAIN FILE columns', () => {
    const options = buildSendTaskAttachmentFieldOptions([mainTable], [], [])
    expect(options.map(o => o.value)).toContain('main:contract')
  })

  it('includes MAIN fields that have form Upload widgets even when dataType is not FILE', () => {
    const form = {
      id: 10,
      configJson: {
        rule: [
          { type: 'input', field: 'case_no', title: 'Case No' },
          { type: 'upload', field: 'file', title: 'Upload' },
        ],
      },
    } as FormDefinition

    const options = buildSendTaskAttachmentFieldOptions([mainTable], [form], [])
    expect(options.map(o => o.value)).toEqual(
      expect.arrayContaining(['main:contract', 'main:file']),
    )
    expect(options.find(o => o.value === 'main:file')?.group).toBe('HMDC Case')
  })

  it('does not include upload widgets for fields that are not on MAIN table', () => {
    const form = {
      id: 10,
      configJson: {
        rule: [{ type: 'upload', field: 'sub_only_file', title: 'Sub File' }],
      },
    } as FormDefinition

    const options = buildSendTaskAttachmentFieldOptions([mainTable], [form], [])
    expect(options.map(o => o.value)).not.toContain('main:sub_only_file')
  })

  it('includes SUB table FILE fields via PROCESS binding', () => {
    const subTable = {
      id: 2,
      tableName: 'HMDC_Attachment',
      tableDisplayName: 'HMDC Attachment',
      tableType: 'SUB',
      fieldDefinitions: [
        { fieldName: 'file', displayName: 'File', dataType: 'FILE' },
      ],
    } as TableDefinition
    const form = {
      id: 10,
      formType: 'PROCESS',
      configJson: { rule: [] },
      tableBindings: [
        {
          id: 271,
          tableId: 2,
          bindingType: 'SUB',
          bindingMode: 'EDITABLE',
          sortOrder: 1,
        },
      ],
    } as FormDefinition

    const options = buildSendTaskAttachmentFieldOptions(
      [mainTable, subTable],
      [form],
      [],
    )
    expect(options.map(o => o.value)).toContain('sub:271:file')
    expect(options.find(o => o.value === 'sub:271:file')?.group).toContain('HMDC Attachment')
  })
})
