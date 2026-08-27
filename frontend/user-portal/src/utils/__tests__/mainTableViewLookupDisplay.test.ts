import { describe, expect, it } from 'vitest'
import type { MainTableViewFieldColumn } from '@/api/mainTableView'
import {
  extractLookupPrimaryKey,
  formatLookupAwareMainTableViewCell,
} from '../mainTableViewLookupDisplay'

/** Cell formatting reads only the lookup hints, so the capability declaration is fixed noise here. */
function column(overrides: Partial<MainTableViewFieldColumn>): MainTableViewFieldColumn {
  return {
    fieldName: 'f',
    displayLabel: 'F',
    kind: 'TEXT',
    filterable: false,
    sortable: false,
    operators: [],
    ...overrides,
  }
}

describe('mainTableViewLookupDisplay', () => {
  it('extracts PK from scalar and object lookup values', () => {
    expect(extractLookupPrimaryKey('uuid-1')).toBe('uuid-1')
    expect(extractLookupPrimaryKey({ id: 'uuid-2', full_name: 'Alice' })).toBe('uuid-2')
    expect(extractLookupPrimaryKey(null)).toBeNull()
  })

  it('formats lookup_display columns from hydrated row attributes', () => {
    const col = column({
      fieldName: 't@full_name',
      displayLabel: 'Full Name',
      columnType: 'lookup_display',
      isLookup: true,
      lookupTableId: -1_000_000_001,
      lookupSourceField: 't',
      lookupDisplayField: 'full_name',
    })
    expect(
      formatLookupAwareMainTableViewCell(col, 'uuid-1', {
        id: 'uuid-1',
        full_name: 'Alice Chen',
      }),
    ).toBe('Alice Chen')
    expect(
      formatLookupAwareMainTableViewCell(col, 'uuid-1', {
        id: 'uuid-1',
        fullName: 'Bob Lee',
      }),
    ).toBe('Bob Lee')
  })

  it('formats source lookup columns with selectedDisplayField', () => {
    const col = column({
      fieldName: 't',
      displayLabel: 't',
      columnType: 'field',
      isLookup: true,
      lookupTableId: -1_000_000_001,
      lookupSelectedDisplayField: 'full_name',
      lookupSearchFields: ['username', 'full_name'],
    })
    expect(
      formatLookupAwareMainTableViewCell(col, 'uuid-1', {
        id: 'uuid-1',
        full_name: 'Alice Chen',
        username: 'alice',
      }),
    ).toBe('Alice Chen')
  })

  it('falls back to raw scalar when hydration misses', () => {
    const col = column({
      fieldName: 't@email',
      displayLabel: 'Email',
      columnType: 'lookup_display',
      isLookup: true,
      lookupTableId: -1_000_000_001,
      lookupSourceField: 't',
      lookupDisplayField: 'email',
    })
    expect(formatLookupAwareMainTableViewCell(col, 'uuid-missing', null)).toBe('uuid-missing')
  })
})
