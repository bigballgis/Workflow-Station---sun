import { describe, it, expect, vi } from 'vitest'
import { usePagination } from '@/composables/usePagination'

describe('usePagination', () => {
  const makeFetch = (data: any[], total = data.length) =>
    vi.fn().mockResolvedValue({ content: data, totalElements: total })

  it('fetches data on handleSearch and populates state', async () => {
    const fetchFn = makeFetch([{ id: 1 }, { id: 2 }], 2)
    const { data, total, loading, handleSearch } = usePagination(fetchFn, {})

    await handleSearch()
    expect(data.value).toEqual([{ id: 1 }, { id: 2 }])
    expect(total.value).toBe(2)
    expect(loading.value).toBe(false)
  })

  it('exposes error ref on fetch failure', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new Error('fail'))
    const { error, handleSearch } = usePagination(fetchFn, {})

    await handleSearch()
    expect(error.value).toBe('fail')
  })

  it('handleReset restores default query and refetches', async () => {
    const fetchFn = makeFetch([{ id: 1 }])
    const { query, handleSearch, handleReset } = usePagination(fetchFn, { keyword: 'test' })

    query.keyword = 'changed'
    await handleReset()
    expect(query.keyword).toBe('test')
  })

  it('page is 0-based in API call', async () => {
    const fetchFn = vi.fn().mockResolvedValue({ content: [], totalElements: 0 })
    const { query, handleSearch } = usePagination(fetchFn, {})

    query.page = 3
    await handleSearch()
    expect(fetchFn).toHaveBeenCalledWith(expect.objectContaining({ page: 2 }))
  })

  it('filters empty string params', async () => {
    const fetchFn = vi.fn().mockResolvedValue({ content: [], totalElements: 0 })
    const { query, handleSearch } = usePagination(fetchFn, { status: '' })

    await handleSearch()
    const params = fetchFn.mock.calls[0][0]
    expect(params).not.toHaveProperty('status')
  })
})
