import { describe, it, expect, vi } from 'vitest'
import { fetchLookupRowByPrimaryKey } from '../fetchLookupRowByPrimaryKey'
import { relationTableApi } from '@/api/relationTable'

vi.mock('@/api/relationTable', () => ({
  relationTableApi: {
    searchForLookup: vi.fn(),
    getFieldDefinitions: vi.fn().mockResolvedValue({ data: [{ fieldName: 'id', isPrimaryKey: true }] }),
  },
}))

describe('fetchLookupRowByPrimaryKey', () => {
  it('uses metadata identity even when the search column has a different name', async () => {
    vi.mocked(relationTableApi.getFieldDefinitions).mockResolvedValueOnce({ data: [{ fieldName: 'business_key', isPrimaryKey: true, dataType: 'VARCHAR' }] })
    vi.mocked(relationTableApi.searchForLookup).mockResolvedValue({ data: [{ business_key: 'K-1', label: 'Chosen' }] } as never)
    expect(await fetchLookupRowByPrimaryKey(902, 'K-1', { searchFields: ['label'], displayField: 'label' })).toEqual({ business_key: 'K-1', label: 'Chosen' })
    expect(relationTableApi.searchForLookup).toHaveBeenLastCalledWith(902, expect.objectContaining({ filterConditions: [{ fieldName: 'business_key', value: 'K-1', matchType: 'eq' }] }))
  })
  it('resolves a saved value even when its row is INACTIVE', async () => {
    vi.mocked(relationTableApi.searchForLookup).mockResolvedValue({ data: [{ id: 7, name: 'Retired' }] } as never)

    const row = await fetchLookupRowByPrimaryKey(901, 7, { searchFields: ['id'], displayField: 'name' })

    expect(row).toEqual({ id: 7, name: 'Retired' })
    expect(relationTableApi.searchForLookup).toHaveBeenCalledWith(901, expect.objectContaining({
      includeInactive: true,
    }))
  })
})
