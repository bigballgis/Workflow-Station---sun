import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import Applications from '../applications/index.vue'
import { processApi } from '@/api/process'
import type { ListColumnMeta } from '@platform-shared/list/columnMeta'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
  createI18n: () => ({ global: { t: (key: string) => key } }),
}))
// The view reads ?status= so the dashboard's MY REQUESTS figures can deep-link a tab.
const routeQuery: Record<string, string> = {}
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))
vi.mock('@/api/process', () => ({
  processApi: {
    queryMyApplications: vi.fn(),
    getDraftList: vi.fn(),
    getMyApplications: vi.fn(),
  },
}))
vi.mock('@/composables/list/searchListFilterUsers', () => ({
  searchListFilterUsers: vi.fn().mockResolvedValue([]),
}))

const api = vi.mocked(processApi)

const COLUMNS: ListColumnMeta[] = [
  { field: 'requestId', label: 'application.requestId', kind: 'TEXT', filterable: true, sortable: true, operators: ['contains', 'eq'] },
  { field: 'businessKey', label: 'application.processTitle', kind: 'TEXT', filterable: true, sortable: true, operators: ['contains', 'eq'] },
  { field: 'currentAssignee', label: 'application.currentAssignee', kind: 'USER', filterable: true, sortable: true, operators: ['eq', 'ne'] },
  { field: 'startTime', label: 'application.startTime', kind: 'DATETIME', filterable: true, sortable: true, operators: ['on', 'between'] },
  { field: 'status', label: 'application.status', kind: 'ENUM', filterable: true, sortable: true, operators: ['eq', 'ne'], options: [{ value: 'RUNNING', label: 'application.running' }] },
]

const lastRequest = () => api.queryMyApplications.mock.calls[api.queryMyApplications.mock.calls.length - 1][0]

let wrapper: VueWrapper | null = null

async function mountPage() {
  wrapper = mount(Applications, {
    global: { plugins: [ElementPlus] },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  for (const key of Object.keys(routeQuery)) delete routeQuery[key]
  api.queryMyApplications.mockResolvedValue({
    data: { columns: COLUMNS, content: [], totalElements: 0, page: 0, size: 20 },
  } as never)
  api.getDraftList.mockResolvedValue({ data: [] } as never)
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

describe('my requests — shared list query state', () => {
  it('renders headers from the declared columns and keeps a frontend-only actions column', async () => {
    const w = await mountPage()
    const headers = w.findAllComponents({ name: 'ListColumnHeader' })
    expect(headers.map((h) => h.props('column').field)).toEqual(COLUMNS.map((c) => c.field))
    expect(w.text()).toContain('common.actions')
  })

  it('queries without a status tab filter on All, then ANDs the tab with column filters', async () => {
    const w = await mountPage()
    expect(lastRequest().status).toBeUndefined()
    expect(lastRequest().page).toBe(0)

    const tabs = w.findAll('.el-tabs__item')
    const running = tabs.find((tab) => tab.text().includes('application.running'))
    expect(running).toBeTruthy()
    await running!.trigger('click')
    await flushPromises()
    expect(lastRequest().status).toBe('RUNNING')
    expect(lastRequest().page).toBe(0)

    const header = w.findAllComponents({ name: 'ListColumnHeader' })[1]
    header.vm.$emit('filter-open')
    await flushPromises()
    w.findComponent({ name: 'ListFilterDialog' }).vm.$emit('apply', { operator: 'contains', value: 'leave' })
    await flushPromises()
    expect(lastRequest().status).toBe('RUNNING')
    expect(lastRequest().filters).toEqual([{ field: 'businessKey', operator: 'contains', value: 'leave' }])
    expect(lastRequest().page).toBe(0)
  })

  it('requestId filter and sort reset to the first page', async () => {
    const w = await mountPage()
    const requestIdHeader = w.findAllComponents({ name: 'ListColumnHeader' })[0]
    requestIdHeader.vm.$emit('filter-open')
    await flushPromises()
    w.findComponent({ name: 'ListFilterDialog' }).vm.$emit('apply', { operator: 'contains', value: 'ATM-DC' })
    await flushPromises()
    expect(lastRequest().page).toBe(0)
    expect(lastRequest().filters).toEqual([{ field: 'requestId', operator: 'contains', value: 'ATM-DC' }])

    requestIdHeader.vm.$emit('sort-change', 'ASC')
    await flushPromises()
    expect(lastRequest().sortField).toBe('requestId')
    expect(lastRequest().sortDirection).toBe('ASC')
    expect(lastRequest().page).toBe(0)
  })

  it('applying sort resets to the first page', async () => {
    const w = await mountPage()
    const header = w.findAllComponents({ name: 'ListColumnHeader' })[1]
    header.vm.$emit('sort-change', 'DESC')
    await flushPromises()
    expect(lastRequest().sortField).toBe('businessKey')
    expect(lastRequest().sortDirection).toBe('DESC')
    expect(lastRequest().page).toBe(0)
  })

  it('does not call the paged query when opening Drafts', async () => {
    const w = await mountPage()
    const callsBefore = api.queryMyApplications.mock.calls.length
    const tabs = w.findAll('.el-tabs__item')
    const draft = tabs.find((tab) => tab.text().includes('application.draftBox'))
    expect(draft).toBeTruthy()
    await draft!.trigger('click')
    await flushPromises()
    expect(api.queryMyApplications.mock.calls.length).toBe(callsBefore)
    expect(api.getDraftList).toHaveBeenCalled()
  })
}, 20_000)

describe('my requests — ?status= deep link from the dashboard figures', () => {
  it('opens the Running tab and filters the first query when status=RUNNING', async () => {
    routeQuery.status = 'RUNNING'
    const w = await mountPage()
    expect(lastRequest().status).toBe('RUNNING')
    expect(w.find('.el-tabs__item.is-active').text()).toContain('application.running')
  })

  it('opens the Completed tab when status=COMPLETED', async () => {
    routeQuery.status = 'COMPLETED'
    const w = await mountPage()
    expect(lastRequest().status).toBe('COMPLETED')
    expect(w.find('.el-tabs__item.is-active').text()).toContain('application.completed')
  })

  it('falls back to All for an unknown status instead of querying it', async () => {
    routeQuery.status = 'bogus'
    const w = await mountPage()
    expect(lastRequest().status).toBeUndefined()
    expect(w.find('.el-tabs__item.is-active').text()).toContain('common.all')
  })

  it('opens Drafts without calling the paged query when status=DRAFT', async () => {
    routeQuery.status = 'DRAFT'
    await mountPage()
    expect(api.queryMyApplications).not.toHaveBeenCalled()
    expect(api.getDraftList).toHaveBeenCalled()
  })
})
