import { describe, expect, it } from 'vitest'
import { buildFkCatalogGroups, flattenFkCatalogItems } from '../mainTableViewFkCatalog'
import type { TableDefinition } from '@/api/functionUnit'

describe('mainTableViewFkCatalog', () => {
  const caseTable: TableDefinition = {
    id: 10,
    tableName: 'hmdc_case',
    tableDisplayName: 'HMDC Case',
    tableType: 'MAIN',
    fieldDefinitions: [
      {
        fieldName: 'case_number',
        displayName: 'Case Number',
        dataType: 'VARCHAR',
        nullable: false,
        isPrimaryKey: true,
      },
      {
        fieldName: 'legal_hold',
        displayName: 'Legal Hold',
        dataType: 'VARCHAR',
        nullable: true,
        isPrimaryKey: false,
      },
    ],
  }

  const attachmentTable: TableDefinition = {
    id: 20,
    tableName: 'hmdc_attachment',
    tableDisplayName: 'HMDC Attachment',
    tableType: 'SUB',
    fieldDefinitions: [
      {
        fieldName: 'file_name',
        displayName: 'File',
        dataType: 'VARCHAR',
        nullable: true,
        isPrimaryKey: false,
      },
      {
        fieldName: 'case_id',
        displayName: 'Case ID',
        dataType: 'VARCHAR',
        nullable: true,
        isPrimaryKey: false,
        isForeignKey: true,
        refTableId: 10,
        refPrimaryKeyFields: ['case_number'],
      },
    ],
  }

  it('builds related column groups from structural FKs', () => {
    const groups = buildFkCatalogGroups(attachmentTable, [caseTable, attachmentTable])
    expect(groups).toHaveLength(1)
    expect(groups[0].sourceField).toBe('case_id')
    expect(groups[0].tableName).toBe('HMDC Case')
    expect(groups[0].relationKind).toBe('fk')
    expect(groups[0].fields.map(f => f.lookupDisplayField)).toEqual(
      expect.arrayContaining(['case_number', 'legal_hold']),
    )
  })

  it('flattens to synthetic case_id@attr field names', () => {
    const flat = flattenFkCatalogItems(
      buildFkCatalogGroups(attachmentTable, [caseTable, attachmentTable]),
    )
    expect(flat.find(f => f.lookupDisplayField === 'legal_hold')?.fieldName)
      .toBe('case_id@legal_hold')
    expect(flat.find(f => f.lookupDisplayField === 'legal_hold')?.columnType)
      .toBe('fk_display')
  })

  it('returns empty when owning table has no FKs', () => {
    expect(buildFkCatalogGroups(caseTable, [caseTable])).toEqual([])
  })
})
