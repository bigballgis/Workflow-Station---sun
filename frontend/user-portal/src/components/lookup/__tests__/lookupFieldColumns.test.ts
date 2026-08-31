import { describe, expect, it } from 'vitest'
import { buildVisibleColumns, lookupTableContentWidth } from '../lookupFieldColumns'

const viewFields = [
  { fieldName: 'username', displayLabel: 'Username', sortOrder: 0, visible: true },
  { fieldName: 'display_name', displayLabel: 'Display Name', sortOrder: 1, visible: true, columnWidth: 160 },
]

describe('lookupFieldColumns', () => {
  it('uses viewFields displayLabel and columnWidth for displayFields', () => {
    const cols = buildVisibleColumns({
      displayFields: ['username', 'display_name'],
      searchFields: ['id'],
      displayField: 'display_name',
      viewFields,
    })
    expect(cols).toEqual([
      { prop: 'username', label: 'Username', width: undefined },
      { prop: 'display_name', label: 'Display Name', width: 160 },
    ])
  })

  it('falls back to the field name when displayLabel is missing', () => {
    const cols = buildVisibleColumns({
      searchFields: ['email'],
      displayField: '',
      viewFields: [],
    })
    expect(cols).toEqual([{ prop: 'email', label: 'email', width: undefined }])
  })

  it('adds the multi-select checkbox column to content width', () => {
    expect(lookupTableContentWidth([{ prop: 'a', label: 'A', width: undefined }], false)).toBe(120)
    expect(lookupTableContentWidth([{ prop: 'a', label: 'A', width: undefined }], true)).toBe(160)
  })
})
