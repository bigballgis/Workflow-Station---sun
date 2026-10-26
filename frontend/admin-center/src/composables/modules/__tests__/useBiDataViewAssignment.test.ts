import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useBiDataViewAssignment } from '../useBiDataViewAssignment'
import { biManagementApi, type DataViewAssignmentResponse } from '@/api/biManagement'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (k: string) => k }) }))
vi.mock('@/utils/notify', () => ({ notifyError: vi.fn(), notifySuccess: vi.fn(), notifyConfirm: vi.fn() }))
vi.mock('@/api/biManagement', () => ({ biManagementApi: { dataViewAssignment: { query: vi.fn(), listFunctionUnits: vi.fn() } } }))
const page = (id: string) => ({ columns: [{ field: 'viewName', label: 'View', kind: 'TEXT', sortable: true,
  filterable: true, operators: ['contains'], options: [] }], content: [{ id } as DataViewAssignmentResponse], page: 0, size: 20, totalElements: 1 })
function setup() {
  let state!: ReturnType<typeof useBiDataViewAssignment>
  const wrapper = mount(defineComponent({ setup() { state = useBiDataViewAssignment(); return () => null } }))
  return { state, wrapper }
}
describe('Data View list server query', () => {
  beforeEach(() => { vi.clearAllMocks(); sessionStorage.clear() })
  it('sends typed column filters, sort and FU identity together before paging', async () => {
    vi.mocked(biManagementApi.dataViewAssignment.query).mockResolvedValue(page('result') as never)
    const { state, wrapper } = setup()
    state.query.functionUnitId = 50029; state.query.dashboardTitle = '  Report  '
    state.openFilter('enabled'); state.applyFilter({ operator: 'eq', value: 'false' })
    state.applySort('viewName', 'DESC'); state.pagination.page = 3
    await state.load()
    expect(biManagementApi.dataViewAssignment.query).toHaveBeenCalledWith({
      page: 2, size: 20, dashboardTitle: 'Report', functionUnitId: 50029,
      filters: [{ field: 'enabled', operator: 'eq', value: 'false' }], sortField: 'viewName', sortDirection: 'DESC',
    })
    expect(state.query).not.toHaveProperty('enabled')
    expect(state.displayRows.value[0].id).toBe('result'); wrapper.unmount()
  })
  it('discards a late earlier response instead of replacing the latest filtered page', async () => {
    let complete!: (value: never) => void
    vi.mocked(biManagementApi.dataViewAssignment.query).mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
      .mockResolvedValueOnce(page('latest') as never)
    const { state, wrapper } = setup(); const old = state.load(); await state.load()
    complete(page('stale') as never); await old
    expect(state.displayRows.value[0].id).toBe('latest'); expect(state.loading.value).toBe(false)
    wrapper.unmount()
  })
  it('toolbar search returns to the first page and preserves column filters', async () => {
    vi.mocked(biManagementApi.dataViewAssignment.query).mockResolvedValue(page('result') as never)
    const { state, wrapper } = setup(); state.openFilter('viewName'); state.applyFilter({ operator: 'contains', value: 'Sales' })
    state.pagination.page = 4; state.search(); await nextTick()
    expect(state.pagination.page).toBe(1)
    expect(biManagementApi.dataViewAssignment.query).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0,
      filters: [{ field: 'viewName', operator: 'contains', value: 'Sales' }] }))
    wrapper.unmount()
  })
})
