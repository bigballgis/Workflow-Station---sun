import { describe, expect, it, vi } from 'vitest'
import type { FormDefinition, TableDefinition } from '@/api/functionUnit'
import type { RelationTableDTO } from '@/api/relationTable'
import {
  EMAIL_VAR_GROUP_LOOKUP,
  EMAIL_VAR_GROUP_SUBTABLES,
  buildEmailLookupVariableGroups,
  resolveEmailVariableGroupLabel,
  useEmailTemplateVariables,
} from '../useEmailTemplateVariables'

const { getTables, getForms, getAvailableTables, textOptions } = vi.hoisted(() => ({
  getTables: vi.fn(),
  getForms: vi.fn(),
  getAvailableTables: vi.fn(),
  textOptions: vi.fn(),
}))

vi.mock('@/api/functionUnit', () => ({
  functionUnitApi: {
    getTables,
    getForms,
  },
}))

vi.mock('@/api/relationTable', () => ({
  relationTableBindingApi: {
    getAvailableTables,
  },
}))

vi.mock('@/api/connection', () => ({
  connectionApi: {
    textOptions,
  },
}))

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

describe('useEmailTemplateVariables.load', () => {
  const mainTable = {
    id: 1,
    tableName: 'email_inbound_case',
    tableDisplayName: 'Inbound Email Case',
    tableType: 'MAIN',
    fieldDefinitions: [
      { fieldName: 'sender_email', displayName: 'From', dataType: 'VARCHAR', nullable: true, isPrimaryKey: false },
    ],
  } as TableDefinition

  it('keeps MAIN field tokens when forms catalog fails', async () => {
    getTables.mockResolvedValue({ data: [mainTable] })
    getForms.mockRejectedValue(new Error('forms unavailable'))
    getAvailableTables.mockRejectedValue(new Error('rt unavailable'))
    textOptions.mockResolvedValue({ data: [] })

    const { groups, load } = useEmailTemplateVariables(50015)
    await load()

    expect(groups.value.some(g => g.options.some(o => o.token === '${sender_email}'))).toBe(true)
  })

  it('reads functionUnitId from a getter so later ids are not stuck at setup', async () => {
    let functionUnitId = 0
    getTables.mockImplementation(async (id: number) => {
      expect(id).toBe(50015)
      return { data: [mainTable] }
    })
    getForms.mockResolvedValue({ data: [] })
    getAvailableTables.mockResolvedValue({ data: [] })
    textOptions.mockResolvedValue({
      data: [{ varKey: 'smtp.from', displayName: 'SMTP From' }],
    })

    const { groups, load } = useEmailTemplateVariables(() => functionUnitId)
    functionUnitId = 50015
    await load()

    expect(groups.value[0]?.options[0]?.token).toBe('${sender_email}')
    expect(groups.value.some(g => g.options.some(o => o.token === '${env:smtp.from}'))).toBe(true)
  })
})
