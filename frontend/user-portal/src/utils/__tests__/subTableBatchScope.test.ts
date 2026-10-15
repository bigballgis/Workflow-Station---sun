import { describe, expect, it } from 'vitest'
import { batchMembershipBindings, batchScopeIdentity } from '../subTableBatchScope'
import type { SubTableBatchChange } from '../subTableBatchChanges'

const change: SubTableBatchChange = { batchControlId: 'grid', bindingId: '101', operation: 'DELETE', rowPatches: [] }
const binding = { bindingId: 101, tableId: 9, primaryKeyFields: ['live_key'], foreignKeyField: 'live_fk', data: [] }
const capability = { batchControlId: 'grid', bindingId: '101', capabilities: { allowBatchDelete: true }, originalCrud: {},
  batchForm: { schemaVersion: 1, rule: [{ type: 'input', field: 'description' }] },
  rowDesign: { binding: { tableId: 9, tableName: 'lines', bindingType: 'SUB', bindingMode: 'EDITABLE', bindingLinkMode: 'structuralFk',
    primaryKeyFields: ['published_key'], foreignKeyField: 'published_fk', fieldDefinitions: [] }, fields: [], parentTablesById: {} },
}

describe('batch membership scope transport', () => {
  it.each(['EDIT', 'ASSIGN'] as const)('freezes binding mode for pending %s despite live design drift', operation => {
    const live = { ...binding, bindingMode: 'READONLY' }
    const [result] = batchMembershipBindings([live], [{ ...change, operation }], { grid: capability })
    expect(result?.bindingMode).toBe('EDITABLE')
    expect(result?.primaryKeyFields).toEqual(['published_key'])
    expect(live.bindingMode).toBe('READONLY')
  })
  it('matches numeric and string identities consistently with the server scope contract', () => {
    expect(batchScopeIdentity({ code: 7 }, ['code'])).toBe(batchScopeIdentity({ code: '7' }, ['code']))
    expect(batchScopeIdentity({ code: '8' }, ['code'])).not.toBe(batchScopeIdentity({ code: 7 }, ['code']))
  })
  it('uses frozen PK/FK while keeping live page rows private and unchanged', () => {
    const [result] = batchMembershipBindings([binding], [change], { grid: capability })
    expect(result?.primaryKeyFields).toEqual(['published_key'])
    expect(result?.foreignKeyField).toBe('published_fk')
    expect(result?.data).toBe(binding.data)
    expect(binding.primaryKeyFields).toEqual(['live_key'])
  })
  it('does not change the old request path without membership declarations', () => {
    expect(batchMembershipBindings([binding], [], {})[0]).toBe(binding)
  })
  it('rejects a missing frozen contract rather than using live DW', () => {
    expect(() => batchMembershipBindings([binding], [change], {})).toThrow('frozen metadata')
  })
})
