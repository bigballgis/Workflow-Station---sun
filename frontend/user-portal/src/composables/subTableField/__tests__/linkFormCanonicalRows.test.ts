import { describe, expect, it } from 'vitest'
import { collectNestedSavedRowsForLinkForm } from '../subTableLinkFormFields'
import type { SubTableBinding } from '../subTableFieldTypes'

describe('linked form canonical nested rows', () => {
  const binding = { bindingId: 17, tableName: 'Display label', designerTableName: 'renamed_children',
    primaryKeyFields: ['entry_key'], formFields: [{ key: 'amount', type: 'number', label: 'Amount' }] } as SubTableBinding

  it('uses the declared table store rather than a different table with overlapping fields', () => {
    const owned = [{ entry_key: 'owned', amount: 3 }]
    expect(collectNestedSavedRowsForLinkForm({ 'dw:renamed_children': owned,
      'dw:unrelated': [{ entry_key: 'foreign', amount: 9 }] }, binding, 17, 'Display label')).toEqual(owned)
  })

  it('keeps an explicitly empty canonical collection empty', () => {
    expect(collectNestedSavedRowsForLinkForm({ 'dw:renamed_children': [],
      '17': [{ entry_key: 'stale', amount: 9 }] }, binding, 17, 'Display label')).toEqual([])
  })
})
