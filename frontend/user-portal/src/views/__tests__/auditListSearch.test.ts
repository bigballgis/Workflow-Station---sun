import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { ref } from 'vue'
import AuditRequests from '../audit/index.vue'
import { processApi } from '@/api/process'

vi.mock('@/api/process', () => ({
  processApi: {
    getAuditFunctionUnits: vi.fn(),
    queryFunctionUnitApplications: vi.fn(),
  },
}))

vi.mock('@platform-shared/list/useListColumnLayout', () => ({
  useListColumnLayout: () => ({
    gridScrollRef: { value: null },
    gridFits: { value: true },
    gridTableHeight: { value: 400 },
    gridInnerStyle: { value: {} },
    widthOf: () => 120,
    setWidth: () => undefined,
    persistWidths: () => undefined,
  }),
}))

const routeParams = ref({ functionUnitCode: 'expense' })

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: routeParams.value,
    query: {},
    name: 'AuditRequests',
    path: `/audit/${routeParams.value.functionUnitCode}`,
  }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}))

const api = vi.mocked(processApi.queryFunctionUnitApplications)
const fuApi = vi.mocked(processApi.getAuditFunctionUnits)

function emptyPage() {
  return {
    data: {
      columns: [
        { field: 'requestId', label: 'application.requestId', kind: 'TEXT', operators: ['contains'], filterable: true, sortable: true },
        { field: 'businessKey', label: 'application.processTitle', kind: 'TEXT', operators: ['contains'], filterable: true, sortable: true },
        { field: 'startUserName', label: 'audit.initiator', kind: 'USER', operators: ['eq'], filterable: true, sortable: true },
      ],
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
    },
  }
}

function mountPage() {
  const i18n = createI18n({
    legacy: false,
    locale: 'en',
    missingWarn: false,
    fallbackWarn: false,
    messages: {
      en: {
        common: { loading: 'Loading', search: 'Search', all: 'All' },
        audit: {
          title: 'Audit',
          initiator: 'Raised by',
          noRequests: 'None',
          noAccess: 'No access',
          noAccessHint: 'Ask admin',
        },
        application: {
          running: 'Running',
          completed: 'Completed',
          withdrawn: 'Withdrawn',
          rejected: 'Rejected',
          requestId: 'Request ID',
          processTitle: 'Title',
          currentStep: 'Step',
          currentAssignee: 'Assignee',
          startTime: 'Start',
          status: 'Status',
        },
      },
    },
  })
  return mount(AuditRequests, {
    global: {
      plugins: [i18n],
      stubs: {
        'el-table': { template: '<div><slot /><slot name="empty" /></div>' },
        'el-table-column': { template: '<div><slot name="header" /></div>' },
        'el-tabs': { template: '<div><slot /></div>' },
        'el-tab-pane': true,
        'el-alert': true,
        'el-input': {
          props: ['modelValue', 'placeholder'],
          emits: ['update:modelValue', 'clear'],
          template:
            '<input data-test="audit-search" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
        'el-icon': true,
        'el-link': true,
        'el-tag': true,
        ListFilterDialog: true,
        ListPagination: true,
        ListColumnHeader: true,
      },
    },
  })
}

describe('Audit All Requests toolbar search', () => {
  beforeEach(() => {
    routeParams.value = { functionUnitCode: 'expense' }
    fuApi.mockReset()
    fuApi.mockResolvedValue({
      data: [{ functionUnitId: '1', functionUnitCode: 'expense', functionUnitName: 'Expense' }],
    } as never)
    api.mockReset()
    api.mockResolvedValue(emptyPage() as never)
  })

  it('omits keyword on the first load', async () => {
    const w = mountPage()
    await flushPromises()
    expect(api).toHaveBeenCalled()
    const body = api.mock.calls[0][1]
    expect(body).toMatchObject({ page: 0, size: 20 })
    expect(body).not.toHaveProperty('keyword')
    expect(w.get('[data-test="audit-search"]').exists()).toBe(true)
  })

  it('sends trimmed keyword and resets to page 1', async () => {
    const w = mountPage()
    await flushPromises()
    api.mockClear()
    const input = w.get('[data-test="audit-search"]')
    await input.setValue('  请假  ')
    await input.trigger('keydown.enter')
    await flushPromises()
    expect(api).toHaveBeenCalled()
    expect(api.mock.calls[0][0]).toBe('expense')
    expect(api.mock.calls[0][1]).toMatchObject({ page: 0, size: 20, keyword: '请假' })
  })

  it('omits keyword when the box is cleared', async () => {
    const w = mountPage()
    await flushPromises()
    const input = w.get('[data-test="audit-search"]')
    await input.setValue('请假')
    await input.trigger('keydown.enter')
    await flushPromises()
    api.mockClear()
    await input.setValue('')
    await input.trigger('keydown.enter')
    await flushPromises()
    const body = api.mock.calls[0][1]
    expect(body).not.toHaveProperty('keyword')
  })
})
