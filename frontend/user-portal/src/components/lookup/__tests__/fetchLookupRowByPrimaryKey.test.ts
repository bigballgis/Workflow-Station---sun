import { describe, it, expect, vi } from 'vitest'
import { fetchLookupRowByPrimaryKey } from '../fetchLookupRowByPrimaryKey'
import { relationTableApi } from '@/api/relationTable'

vi.mock('@/api/relationTable', () => ({
  relationTableApi: {
    searchForLookup: vi.fn(),
  },
}))

describe('fetchLookupRowByPrimaryKey', () => {
  it('resolves a saved value even when its row is INACTIVE', async () => {
    vi.mocked(relationTableApi.searchForLookup).mockResolvedValue({ data: [{ id: 7, name: 'Retired' }] } as never)

    const row = await fetchLookupRowByPrimaryKey(901, 7, { searchFields: ['id'], displayField: 'name' })

    expect(row).toEqual({ id: 7, name: 'Retired' })
    expect(relationTableApi.searchForLookup).toHaveBeenCalledWith(901, expect.objectContaining({
      includeInactive: true,
    }))
  })
})
