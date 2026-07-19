import { describe, expect, it } from 'vitest'
import type { FormDefinition } from '@/api/functionUnit'
import type { RelationTableDTO } from '@/api/relationTable'
import {
  buildLookupCatalogGroups,
  flattenLookupCatalogItems,
} from '../mainTableViewLookupCatalog'
import { lookupDisplayFieldName } from '@/api/mainTableView'

describe('mainTableViewLookupCatalog', () => {
  const relationTables: RelationTableDTO[] = [
    {
      id: -1_000_000_001,
      tableName: 'sys_users',
      displayName: 'User',
      status: 'DEPLOYED',
      enabled: true,
      portalVisible: false,
      currentVersion: 1,
      fieldDefinitions: [
        { id: 1, fieldName: 'id', dataType: 'VARCHAR', nullable: false, isPrimaryKey: true, displayName: 'User ID', sortOrder: 1 },
        { id: 2, fieldName: 'full_name', dataType: 'VARCHAR', nullable: true, isPrimaryKey: false, displayName: 'Full Name', sortOrder: 2 },
        { id: 3, fieldName: 'email', dataType: 'VARCHAR', nullable: true, isPrimaryKey: false, displayName: 'Email', sortOrder: 3 },
      ],
    },
  ]

  it('builds lookup groups from form designer lookup widgets', () => {
    const forms: FormDefinition[] = [
      {
        id: 1,
        formName: 'HMDC Case Form',
        configJson: {
          rule: [
            {
              type: 'lookup',
              field: 't',
              title: 'Assignee User',
              props: {
                lookupConfig: JSON.stringify({
                  tableId: -1_000_000_001,
                  tableName: 'sys_users',
                  selectedDisplayField: 'full_name',
                  searchFields: ['username', 'full_name'],
                }),
              },
            },
          ],
        },
      } as FormDefinition,
    ]

    const groups = buildLookupCatalogGroups(forms, relationTables)
    expect(groups).toHaveLength(1)
    expect(groups[0].sourceField).toBe('t')
    expect(groups[0].tableName).toBe('sys_users')
    expect(groups[0].fields.map(f => f.lookupDisplayField)).toEqual(
      expect.arrayContaining(['full_name', 'email', 'id']),
    )

    const flat = flattenLookupCatalogItems(groups)
    expect(flat.some(f => f.fieldName === lookupDisplayFieldName('t', 'full_name'))).toBe(true)
    expect(flat.find(f => f.lookupDisplayField === 'full_name')?.columnType).toBe('lookup_display')
  })

  it('ignores non-lookup widgets and missing tableId', () => {
    const forms: FormDefinition[] = [
      {
        id: 2,
        formName: 'X',
        configJson: {
          rule: [
            { type: 'input', field: 'caseNumber' },
            { type: 'lookup', field: 'broken', props: { lookupConfig: '{}' } },
          ],
        },
      } as FormDefinition,
    ]
    expect(buildLookupCatalogGroups(forms, relationTables)).toEqual([])
  })
})
