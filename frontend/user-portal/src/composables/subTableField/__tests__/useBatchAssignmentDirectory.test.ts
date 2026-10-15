import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { permissionApi } from '@/api/permission'
import { useBatchAssignmentDirectory } from '../useBatchAssignmentDirectory'

const requestGet = vi.hoisted(() => vi.fn<(url: string, config?: unknown) => Promise<unknown>>())
vi.mock('@/api/request', () => ({ request: { get: requestGet } }))
vi.mock('@/api/permission', () => ({ permissionApi: { getBusinessUnitsTree: vi.fn(), getBusinessUnitRoles: vi.fn(), getAvailableRoles: vi.fn() } }))
function directory() {
  let result!: ReturnType<typeof useBatchAssignmentDirectory>
  const wrapper = mount(defineComponent({ setup() { result = useBatchAssignmentDirectory(key => key); return () => null } }))
  return { wrapper, directory: result }
}
afterEach(() => { vi.useRealTimers(); vi.clearAllMocks() })
describe('batch assignment directory', () => {
  it('uses actual BU codes and refuses a code-less BU rather than substituting its ID', async () => {
    vi.mocked(permissionApi.getBusinessUnitsTree).mockResolvedValue({ data: [{ id: 'query-id', name: 'unit', code: 'unit-code' }, { id: 'no-code', name: 'unit2' }] } as never)
    const { wrapper, directory: value } = directory()
    await value.loadBusinessUnits()
    expect(value.requireBusinessUnitCode('query-id')).toBe('unit-code')
    expect(() => value.requireBusinessUnitCode('no-code')).toThrow()
    wrapper.unmount()
  })
  it('does not turn malformed directory envelopes into a legal empty result', async () => {
    vi.mocked(permissionApi.getAvailableRoles).mockResolvedValue({ data: null } as never)
    const { wrapper, directory: value } = directory()
    await expect(value.loadRoles()).rejects.toThrow('common.operationFailed')
    vi.mocked(permissionApi.getAvailableRoles).mockResolvedValue({ data: [] } as never)
    await expect(value.loadRoles()).resolves.toEqual([])
    wrapper.unmount()
  })
  it('debounces searches, exposes transport failure and clears loading', async () => {
    vi.useFakeTimers()
    requestGet.mockRejectedValue({ response: { status: 503 } })
    const { wrapper, directory: value } = directory()
    value.searchUsers('row:one', 'first'); value.searchUsers('row:one', 'second')
    await vi.advanceTimersByTimeAsync(299)
    expect(requestGet).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1); await flushPromises()
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/tasks/users/search', expect.objectContaining({ params: { keyword: 'second' } }))
    expect(value.error.value).toBe('api.serviceUnavailable')
    expect(value.userLoading['row:one']).toBe(false)
    wrapper.unmount()
  })
  it('never lets an older search overwrite a newer result and cancels pending timers on unmount', async () => {
    vi.useFakeTimers()
    let resolveOld!: (result: unknown) => void
    requestGet.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const { wrapper, directory: value } = directory()
    value.searchUsers('row:one', 'old'); await vi.advanceTimersByTimeAsync(300)
    requestGet.mockResolvedValueOnce({ data: [{ id: 'new-id', username: 'new-user' }] })
    value.searchUsers('row:one', 'new'); await vi.advanceTimersByTimeAsync(300)
    resolveOld({ data: [{ id: 'old-id', username: 'old-user' }] }); await flushPromises()
    expect(value.userOptions['row:one']?.map(user => user.id)).toEqual(['new-id'])
    value.searchUsers('row:one', 'not-sent'); wrapper.unmount(); await vi.advanceTimersByTimeAsync(300)
    expect(requestGet).toHaveBeenCalledTimes(2)
  })
  it('isolates searches and options for different rows and the uniform selector', async () => {
    vi.useFakeTimers()
    requestGet.mockImplementation(async (_url, config) => ({ data: [{ id: (config as { params: { keyword: string } }).params.keyword, username: 'synthetic' }] }))
    const { wrapper, directory: value } = directory()
    value.searchUsers('row:one', 'person-A'); value.searchUsers('row:two', 'person-B'); value.searchUsers('uniform', 'person-C')
    await vi.advanceTimersByTimeAsync(300)
    expect(requestGet).toHaveBeenCalledTimes(3)
    expect(value.userOptions['row:one']?.[0]?.id).toBe('person-A')
    expect(value.userOptions['row:two']?.[0]?.id).toBe('person-B')
    expect(value.userOptions.uniform?.[0]?.id).toBe('person-C')
    wrapper.unmount()
  })
})
