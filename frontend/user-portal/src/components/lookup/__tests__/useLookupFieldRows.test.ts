import { describe, it, expect, vi, beforeEach } from 'vitest'
import { relationTableApi } from '@/api/relationTable'
import { useLookupFieldRows } from '../useLookupFieldRows'

vi.mock('@/api/relationTable', () => ({
  relationTableApi: {
    searchForLookup: vi.fn().mockResolvedValue({ data: [] }),
  },
}))

function params(overrides: Partial<Parameters<typeof useLookupFieldRows>[0]> = {}) {
  return {
    tableId: () => -1_000_000_001,
    searchFields: () => ['id', 'username'],
    displayField: () => 'display_name',
    filterConditions: () => [],
    prefetchLimit: () => 200 as number | undefined,
    remoteFilter: () => true,
    ...overrides,
  }
}

describe('useLookupFieldRows', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('stops after prefetchLimit even when the page is full', async () => {
    vi.mocked(relationTableApi.searchForLookup).mockResolvedValue({
      data: Array.from({ length: 200 }, (_, i) => ({ id: String(i) })),
    })
    const api = useLookupFieldRows(params())
    await api.loadInitial()
    expect(relationTableApi.searchForLookup).toHaveBeenCalledTimes(1)
    expect(api.allRows.value).toHaveLength(200)
  })

  it('passes the keyword to the search API', async () => {
    vi.mocked(relationTableApi.searchForLookup).mockResolvedValue({
      data: [{ id: '1', username: 'lisi' }],
    })
    const api = useLookupFieldRows(params())
    await api.searchRemote('li')
    expect(relationTableApi.searchForLookup).toHaveBeenCalledWith(
      -1_000_000_001,
      expect.objectContaining({ keyword: 'li', limit: 200 }),
    )
    expect(api.allRows.value).toEqual([{ id: '1', username: 'lisi' }])
  })
})
