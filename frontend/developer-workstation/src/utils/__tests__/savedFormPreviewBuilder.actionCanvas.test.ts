import { describe, expect, it } from 'vitest'
import type { FormDefinition, TableDefinition } from '@/api/functionUnit'
import { buildSavedFormPreviewItems } from '../savedFormPreviewBuilder'

const ACTION_RULE = [
  { type: 'input', field: 'remark_type', title: 'Remark Type', props: {} },
]

describe('buildSavedFormPreviewItems ACTION canvas', () => {
  it('renders ACTION subForms when top-level rule is empty', () => {
    const form = {
      id: 10,
      formName: 'Popup Form',
      formType: 'ACTION',
      configJson: {
        rule: [],
        subForms: { '20': { rule: ACTION_RULE, options: {} } },
      },
      tableBindings: [
        { id: 20, tableId: 30, bindingType: 'ACTION', bindingMode: 'EDITABLE', tableName: 'action_table', sortOrder: 1 },
      ],
    } as FormDefinition
    const tables = [{
      id: 30,
      tableName: 'action_table',
      tableDisplayName: 'Action Table',
      tableType: 'ACTION',
      fieldDefinitions: [{ fieldName: 'remark_type', dataType: 'VARCHAR' }],
    }] as TableDefinition[]

    const items = buildSavedFormPreviewItems({ form, tables, t: (key) => key })
    expect(JSON.stringify(items)).toContain('Remark Type')
  })
})
