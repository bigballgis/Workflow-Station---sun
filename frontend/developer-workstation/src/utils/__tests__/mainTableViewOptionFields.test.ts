import { describe, expect, it } from 'vitest'
import type { FormDefinition } from '@/api/functionUnit'
import { collectStaticOptionFields } from '../mainTableViewOptionFields'

function form(configJson: Record<string, unknown>, boundTableId?: number): FormDefinition {
  return { id: 1, formName: 'Main', formType: 'TASK', configJson, boundTableId } as unknown as FormDefinition
}

const yesNo = [{ label: 'Y', value: '1' }, { label: 'N', value: '2' }]

describe('mainTableViewOptionFields', () => {
  it('reads top-level rules for the PRIMARY table and subForms for sub-table bindings', () => {
    const entry = {
      form: form({
        rule: [
          { type: 'select', field: 'status', options: [{ label: 'Open', value: 'O' }] },
          { type: 'group', children: [{ type: 'radio', field: 'channel', options: yesNo }] },
        ],
        subForms: {
          '527': [{ type: 'select', field: 'case_type', options: yesNo }, { type: 'checkbox', field: 'tags', options: yesNo }],
          '529': [{ type: 'select', field: 'kind', options: yesNo }],
        },
      }),
      bindings: [
        { id: 521, tableId: 50329, bindingType: 'PRIMARY' },
        { id: 527, tableId: 50327, bindingType: 'SUB' },
        { id: 529, tableId: 50328, bindingType: 'SUB' },
      ],
    }
    expect([...collectStaticOptionFields([entry], 50329)]).toEqual(['status', 'channel'])
    expect([...collectStaticOptionFields([entry], 50327)]).toEqual(['case_type', 'tags'])
    expect([...collectStaticOptionFields([entry], 50326)]).toEqual([])
  })

  it('falls back to the legacy boundTableId when a form has no PRIMARY binding', () => {
    const entry = { form: form({ rule: [{ type: 'select', field: 'loan_type', options: yesNo }] }, 5), bindings: [] }
    expect([...collectStaticOptionFields([entry], 5)]).toEqual(['loan_type'])
    expect([...collectStaticOptionFields([entry], 6)]).toEqual([])
  })

  it('ignores widgets without static options and forms without config', () => {
    const entry = {
      form: form({ rule: [
        { type: 'input', field: 'remark' },
        { type: 'select', field: 'branch', options: [], effect: { fetch: '/api/branches' } },
        { type: 'select', field: 'broken', options: [{ label: 'no value' }] },
      ] }),
      bindings: [{ id: 1, tableId: 5, bindingType: 'PRIMARY' }],
    }
    const empty = { form: { id: 2, formName: 'Empty', formType: 'TASK' } as unknown as FormDefinition, bindings: [{ id: 2, tableId: 5, bindingType: 'PRIMARY' }] }
    expect(collectStaticOptionFields([entry, empty], 5).size).toBe(0)
  })
})
