import { describe, it, expect } from 'vitest'
import { buildLookupPreviewMockRows } from '../lookupPreviewMockRows'
import { applyLookupFixedFilters } from '../lookupFilterConditions'

describe('buildLookupPreviewMockRows cascade filter', () => {
  it('does not stamp filter values onto every row (cascade filter can narrow)', () => {
    const rows = buildLookupPreviewMockRows({
      displayFields: ['status_name'],
      searchFields: ['status_id'],
      ensureFields: ['status_code'],
      filterConditions: [{ fieldName: 'status_code', value: 'Sample 1', matchType: 'eq' }],
    })
    expect(rows).toHaveLength(3)
    // Per-row distinct join values — not all equal to the active filter.
    expect(rows.map(r => r.status_code)).toEqual(['Sample 1', 'Sample 2', 'Sample 3'])
    const filtered = applyLookupFixedFilters(rows, [
      { fieldName: 'status_code', value: 'Sample 1', matchType: 'eq' },
    ])
    expect(filtered).toHaveLength(1)
    expect(filtered[0].status_name).toBe('Sample 1')
  })

  it('includes parent join fromColumn on mock rows for cascade', () => {
    const rows = buildLookupPreviewMockRows({
      displayFields: ['name'],
      ensureFields: ['code'],
    })
    expect(rows[0]).toHaveProperty('code', 'Sample 1')
    expect(rows[1]).toHaveProperty('code', 'Sample 2')
  })
})
