import { describe, expect, it } from 'vitest'
import type { ListColumnMeta } from '../columnMeta'
import { operatorsFor } from '../columnMeta'
import { applyClientListQuery, cellMatchesFilter } from '../clientListQuery'

const nameCol: ListColumnMeta = {
  field: 'name',
  label: 'Name',
  kind: 'TEXT',
  filterable: true,
  sortable: true,
  operators: operatorsFor('TEXT'),
}

describe('clientListQuery', () => {
  it('filters and sorts the loaded page then slices', () => {
    const rows = [
      { name: 'Ann', joinedAt: '2026-01-01' },
      { name: 'Bob', joinedAt: '2026-03-01' },
      { name: 'Ada', joinedAt: '2026-02-01' },
    ]
    const page = applyClientListQuery({
      rows,
      columns: [nameCol],
      getValue: (row, field) => (row as Record<string, string>)[field],
      filters: { name: { operator: 'startsWith', value: 'A' } },
      sort: { field: 'name', direction: 'ASC' },
      page: 1,
      size: 10,
    })
    expect(page.totalElements).toBe(2)
    expect(page.content.map((r) => r.name)).toEqual(['Ada', 'Ann'])
  })

  it('matches a calendar-day DATETIME filter', () => {
    expect(cellMatchesFilter('2026-08-27T10:00:00', 'DATETIME', { operator: 'on', value: '2026-08-27' })).toBe(true)
    expect(cellMatchesFilter('2026-08-26T10:00:00', 'DATETIME', { operator: 'on', value: '2026-08-27' })).toBe(false)
  })

  it('refuses a filter on a column the header did not declare filterable', () => {
    expect(() => applyClientListQuery({
      rows: [{ name: 'Ann' }],
      columns: [{ ...nameCol, filterable: false, operators: [] }],
      getValue: (row) => row.name,
      filters: { name: { operator: 'eq', value: 'Ann' } },
      sort: { field: null, direction: null },
      page: 1,
      size: 10,
    })).toThrow(/not filterable/)
  })
})
