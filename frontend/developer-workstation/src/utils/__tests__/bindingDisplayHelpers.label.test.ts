import { describe, expect, it } from 'vitest'
import { formatSubTableBindingOptionLabel } from '../bindingDisplayHelpers'

describe('formatSubTableBindingOptionLabel', () => {
  it('distinguishes two bindings of the same table by filter FK', () => {
    const file = {
      tableName: 'p0_dual_file',
      tableDisplayName: 'P0 Dual File',
      tableDescription: 'Files bound twice (case vs party)',
    }
    expect(formatSubTableBindingOptionLabel({ ...file, foreignKeyField: 'case_id' }))
      .toBe('P0 Dual File (case_id)')
    expect(formatSubTableBindingOptionLabel({ ...file, foreignKeyField: 'party_id' }))
      .toBe('P0 Dual File (party_id)')
  })

  it('falls back to table description when the binding has no filter FK', () => {
    expect(formatSubTableBindingOptionLabel({
      tableName: 'p0_dual_party',
      tableDisplayName: 'P0 Dual Party',
      tableDescription: 'Parties of the case',
    })).toBe('P0 Dual Party (Parties of the case)')
  })
})
