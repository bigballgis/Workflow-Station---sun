import { describe, expect, it } from 'vitest'
import {
  applyLookupFixedFilters,
  getLookupFilterMatchOptions,
  normalizeLookupFilterCondition,
  rowMatchesLookupFilter,
} from './lookupFilterConditions'

describe('lookupFilterConditions', () => {
  it('normalizes legacy conditions without matchType', () => {
    expect(normalizeLookupFilterCondition({ fieldName: 'sex', value: 'true' })).toEqual({
      fieldName: 'sex',
      value: 'true',
      matchType: 'eq',
    })
  })

  it('applies contains filter case-insensitively', () => {
    const rows = [{ name: 'Alice Smith' }, { name: 'Bob' }]
    const filtered = applyLookupFixedFilters(rows, [
      { fieldName: 'name', value: 'alice', matchType: 'contains' },
    ])
    expect(filtered).toEqual([{ name: 'Alice Smith' }])
  })

  it('matches boolean values exactly', () => {
    expect(rowMatchesLookupFilter(true, 'true', 'eq')).toBe(true)
    expect(rowMatchesLookupFilter('false', 'true', 'eq')).toBe(false)
  })

  it('limits boolean fields to exact match only', () => {
    expect(getLookupFilterMatchOptions('BOOLEAN')).toEqual([{ value: 'eq', label: 'Exact' }])
  })
})
