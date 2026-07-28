import { describe, expect, it } from 'vitest'
import type { FormDefinition } from '@/api/functionUnit'
import type { RelationTableDTO } from '@/api/relationTable'
import {
  EMAIL_VAR_GROUP_LOOKUP,
  EMAIL_VAR_GROUP_SUBTABLES,
  buildEmailLookupVariableGroups,
  resolveEmailVariableGroupLabel,
} from '../useEmailTemplateVariables'

describe('buildEmailLookupVariableGroups', () => {
  const relationTables: RelationTableDTO[] = [
    {
      id: 10,
      tableName: 'sys_users',
      displayName: 'User',
      status: 'DEPLOYED',
      enabled: true,
      portalVisible: false,
      currentVersion: 1,
      fieldDefinitions: [
        {
          id: 1,
          fieldName: 'name',
          dataType: 'VARCHAR',
          nullable: true,
          isPrimaryKey: false,
          displayName: 'Name',
          sortOrder: 1,
        },
        {
          id: 2,
          fieldName: 'email',
          dataType: 'VARCHAR',
          nullable: true,
          isPrimaryKey: false,
          displayName: 'Email',
          sortOrder: 2,
        },
      ],
    },
  ]

  it('emits lookupField tokens for each RT attribute', () => {
    const forms: FormDefinition[] = [
      {
        id: 1,
        formName: 'Main',
        configJson: {
          rule: [
            {
              type: 'lookup',
              field: 'user',
              title: 'Assignee',
              props: {
                lookupConfig: JSON.stringify({ tableId: 10, tableName: 'sys_users' }),
              },
            },
          ],
        },
      } as FormDefinition,
    ]

    const groups = buildEmailLookupVariableGroups(forms, relationTables)
    expect(groups).toHaveLength(1)
    expect(groups[0].label).toBe(`${EMAIL_VAR_GROUP_LOOKUP}:Assignee`)
    expect(groups[0].options.map(o => o.token)).toEqual(
      expect.arrayContaining(['${lookupField:user:name}', '${lookupField:user:email}']),
    )
  })
})

describe('resolveEmailVariableGroupLabel', () => {
  const t = (key: string, params?: Record<string, unknown>) => {
    if (key === 'emailTemplate.subTableGroup') return 'Sub-tables'
    if (key === 'emailTemplate.lookupGroup') return `Lookup — ${params?.source}`
    return key
  }

  it('maps sentinel labels', () => {
    expect(resolveEmailVariableGroupLabel(EMAIL_VAR_GROUP_SUBTABLES, t)).toBe('Sub-tables')
    expect(resolveEmailVariableGroupLabel(`${EMAIL_VAR_GROUP_LOOKUP}:User`, t)).toBe('Lookup — User')
    expect(resolveEmailVariableGroupLabel('Main Table', t)).toBe('Main Table')
  })
})
