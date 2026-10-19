import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import RelationTables from '../relation-tables/index.vue'
import { relationTableApi } from '@/api/relationTable'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
  createI18n: () => ({ global: { t: (key: string) => key } }),
}))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {}, params: {} }) }))
vi.mock('@/api/relationTable', () => ({
  relationTableApi: {
    getVisibleTables: vi.fn(),
    queryTableData: vi.fn(),
    getFieldDefinitions: vi.fn(),
    changeStatusBatch: vi.fn(),
  },
}))
vi.mock('@/composables/list/searchListFilterUsers', () => ({
  searchListFilterUsers: vi.fn().mockResolvedValue([]),
}))

const api = vi.mocked(relationTableApi)

async function mountWithBothRowsSelected(permissionLevel: 'READ_WRITE' | 'READONLY') {
  api.getVisibleTables.mockResolvedValue({ data: [
    { id: 11, tableName: 'orders', displayName: 'Orders', status: 'DEPLOYED', enabled: true, portalVisible: true, currentVersion: 1, permissionLevel },
  ] } as never)
  const wrapper = mount(RelationTables, {
    global: { plugins: [ElementPlus], stubs: { LookupField: true, LookupViewDisplay: true } },
  })
  await flushPromises()
  wrapper.findComponent({ name: 'ElTable' }).vm.$emit('selection-change', [
    { code: 'A-1', status: 'ACTIVE' },
    { code: 'A-2', status: 'ACTIVE' },
  ])
  await flushPromises()
  return wrapper
}

const buttonWith = (wrapper: Awaited<ReturnType<typeof mountWithBothRowsSelected>>, label: string) =>
  wrapper.findAll('button').find(b => b.text() === label)

beforeEach(() => {
  vi.clearAllMocks()
  api.getFieldDefinitions.mockResolvedValue({ data: [{ fieldName: 'code', isPrimaryKey: true }] } as never)
  api.queryTableData.mockResolvedValue({
    data: { columns: [], content: [], totalElements: 0, page: 0, size: 20 },
  } as never)
  api.changeStatusBatch.mockResolvedValue({ data: { updated: 2 } } as never)
})

describe('relation tables — batch Active / Inactive', () => {
  it('sets every selected row inactive in one request after confirmation', async () => {
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    const w = await mountWithBothRowsSelected('READ_WRITE')

    await buttonWith(w, 'relationTableBatch.inactive')!.trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalledTimes(1)
    expect(api.changeStatusBatch).toHaveBeenCalledWith(11, ['A-1', 'A-2'], 'INACTIVE')
    w.unmount()
  })

  it('does nothing when the inactive confirmation is cancelled', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
    const w = await mountWithBothRowsSelected('READ_WRITE')

    await buttonWith(w, 'relationTableBatch.inactive')!.trigger('click')
    await flushPromises()

    expect(api.changeStatusBatch).not.toHaveBeenCalled()
    w.unmount()
  })

  it('reactivates selected rows without a confirmation', async () => {
    const confirm = vi.spyOn(ElMessageBox, 'confirm')
    const w = await mountWithBothRowsSelected('READ_WRITE')

    await buttonWith(w, 'relationTableBatch.active')!.trigger('click')
    await flushPromises()

    expect(confirm).not.toHaveBeenCalled()
    expect(api.changeStatusBatch).toHaveBeenCalledWith(11, ['A-1', 'A-2'], 'ACTIVE')
    w.unmount()
  })

  it('hides the batch buttons for a read-only role', async () => {
    const w = await mountWithBothRowsSelected('READONLY')

    expect(buttonWith(w, 'relationTableBatch.inactive')).toBeUndefined()
    expect(buttonWith(w, 'relationTableBatch.active')).toBeUndefined()
    w.unmount()
  })
})
