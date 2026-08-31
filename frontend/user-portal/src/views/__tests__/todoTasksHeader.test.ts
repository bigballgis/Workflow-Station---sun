import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import TodoTasks from '../tasks/index.vue'
import TodoListToolbar from '../tasks/TodoListToolbar.vue'
import { queryTodoTasks } from '@/api/task'

vi.mock('@/api/task', () => ({
  queryTodoTasks: vi.fn(),
  batchUrgeTasks: vi.fn(),
  claimBatch: vi.fn(),
  unclaimBatch: vi.fn(),
  claimTask: vi.fn(),
  unclaimTask: vi.fn(),
}))

vi.mock('@/stores/pendingTask', () => ({
  usePendingTaskStore: () => ({
    syncCountFromListTotal: vi.fn(),
  }),
}))

const preferenceMocks = vi.hoisted(() => ({
  autoClaimOnOpen: false,
  saving: false,
  load: vi.fn().mockResolvedValue(undefined),
  setAutoClaimOnOpen: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('@/stores/userPreference', () => ({
  useUserPreferenceStore: () => preferenceMocks,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

const api = vi.mocked(queryTodoTasks)

function mountPage() {
  const i18n = createI18n({
    legacy: false,
    locale: 'en',
    missingWarn: false,
    fallbackWarn: false,
    messages: {
      en: {
        common: {
          loading: 'Loading',
          cancel: 'Cancel',
          confirm: 'Confirm',
          success: 'OK',
          error: 'Error',
          reason: 'Reason',
          search: 'Search',
          reset: 'Reset',
          all: 'All',
        },
        task: {
          title: 'To Do',
          noTasks: 'None',
          loadFailed: 'Failed',
          selected: '{count} selected',
          batchUrge: 'Batch',
          overdue: 'Overdue',
          urgent: 'Urgent',
          high: 'High',
          normal: 'Normal',
          low: 'Low',
          user: 'User',
          buRole: 'BU + role',
          virtualGroup: 'Virtual Group',
          deptRole: 'Dept Role',
          delegated: 'Delegated',
          claimAll: 'Claim all',
          unclaimAll: 'Unclaim all',
          claim: 'Claim',
          unclaim: 'Unclaim',
          claimSelectedEmpty: 'None claimable',
          unclaimSelectedEmpty: 'None held',
          autoClaimOnOpen: 'Auto-claim on open',
          todoGuideLinkAria: 'Open To Do guideline',
          action: 'Action',
          requestId: 'Request ID',
          functionUnit: 'Function Unit',
          taskName: 'Task Name',
          currentStep: 'Step',
          processName: 'Process',
          assignmentType: 'Type',
          initiator: 'Initiator',
          priority: 'Priority',
          createTime: 'Created',
          dueDate: 'Due',
        },
      },
    },
  })
  return mount(TodoTasks, {
    global: {
      plugins: [i18n],
      stubs: {
        'el-table': { template: '<div><slot /><slot name="empty" /></div>' },
        'el-table-column': { template: '<div><slot name="header" /></div>' },
        'el-dialog': true,
        'el-input': {
          props: ['modelValue', 'placeholder'],
          emits: ['update:modelValue', 'clear'],
          template:
            '<input data-test="todo-search" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
        'el-select': true,
        'el-option': true,
        'el-button': {
          inheritAttrs: false,
          template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>',
        },
        'el-switch': {
          inheritAttrs: false,
          template: '<button v-bind="$attrs" type="button" />',
        },
        'el-link': true,
        'el-tag': true,
        'el-icon': true,
        'el-dropdown': { template: '<div><slot /><slot name="dropdown" /></div>' },
        'el-dropdown-menu': { template: '<div><slot /></div>' },
        'el-dropdown-item': { template: '<div><slot /></div>' },
        ListFilterDialog: true,
        ListPagination: true,
        PortalHelpLink: true,
        TodoClaimRowActions: true,
      },
    },
  })
}

describe('To Do shared list', () => {
  beforeEach(() => {
    sessionStorage.clear()
    api.mockReset()
    preferenceMocks.load.mockClear()
    api.mockResolvedValue({
      data: {
        columns: [
          { field: 'requestId', label: 'task.requestId', kind: 'TEXT', operators: ['contains'], filterable: true, sortable: true },
          { field: 'functionUnitCode', label: 'task.functionUnit', kind: 'TEXT', operators: ['contains'], filterable: true, sortable: true },
          { field: 'taskName', label: 'task.taskName', kind: 'TEXT', operators: ['contains'], filterable: true, sortable: true },
          { field: 'assignmentType', label: 'task.assignmentType', kind: 'ENUM', operators: ['eq'], filterable: true, sortable: true },
          { field: 'createTime', label: 'task.createTime', kind: 'DATETIME', operators: ['on'], filterable: true, sortable: true },
          { field: 'processDefinitionName', label: 'task.processName', kind: 'TEXT', operators: ['contains'], filterable: true, sortable: true },
          { field: 'initiatorName', label: 'task.initiator', kind: 'TEXT', operators: ['contains'], filterable: true, sortable: true },
          { field: 'priority', label: 'task.priority', kind: 'ENUM', operators: ['eq'], filterable: true, sortable: true },
          { field: 'dueDate', label: 'task.dueDate', kind: 'DATETIME', operators: ['on'], filterable: true, sortable: true },
        ],
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
      },
    } as never)
  })

  it('loads via /tasks/todo/query and renders ListColumnHeader chrome', async () => {
    const w = mountPage()
    await flushPromises()
    expect(api).toHaveBeenCalled()
    const body = api.mock.calls[0][0]
    expect(body).toMatchObject({ page: 0, size: 20 })
    expect(body).not.toHaveProperty('keyword')
    expect(body).not.toHaveProperty('assignmentTypes')
    expect(body).not.toHaveProperty('priorities')
    expect(w.text()).toContain('To Do')
    expect(w.text()).toContain('Type')
    expect(w.text()).not.toContain('Priority')
    expect(w.text()).toContain('Function Unit')
    expect(w.text()).not.toContain('Initiator')
    const headers = w.findAllComponents({ name: 'ListColumnHeader' })
    expect(headers).toHaveLength(5)
    expect(headers.every((h) => h.find('.list-col-header').exists())).toBe(true)
    expect(w.get('[data-test="todo-reset-btn"]').text()).toContain('Reset')
    expect(w.get('[data-test="todo-claim-all-btn"]').text()).toContain('Claim all')
    expect(w.get('[data-test="todo-unclaim-all-btn"]').text()).toContain('Unclaim all')
    // 用 find().exists() 而不是 get().exists()：get() 找不到时自己就抛了，
    // 在它返回值上再断言 exists 恒为 true（vue-test-utils 正是因此把该方法从
    // get() 的返回类型里 Omit 掉）。find() 才是「可能不存在」的那个查询。
    expect(w.find('[data-test="todo-auto-claim-switch"]').exists()).toBe(true)
    expect(preferenceMocks.load).toHaveBeenCalled()
  })

  it('sends trimmed keyword and resets to page 1', async () => {
    const w = mountPage()
    await flushPromises()
    api.mockClear()
    const input = w.get('[data-test="todo-search"]')
    await input.setValue('  请假  ')
    await input.trigger('keydown.enter')
    await flushPromises()
    expect(api).toHaveBeenCalled()
    expect(api.mock.calls[0][0]).toMatchObject({ page: 0, size: 20, keyword: '请假' })
  })

  it('sends assignmentTypes on search without a Priority toolbar filter', async () => {
    const w = mountPage()
    await flushPromises()
    api.mockClear()
    const toolbar = w.getComponent(TodoListToolbar)
    await toolbar.vm.$emit('update:assignmentTypes', ['USER', 'DELEGATED'])
    await toolbar.vm.$emit('search')
    await flushPromises()
    expect(api.mock.calls[0][0]).toMatchObject({
      page: 0,
      size: 20,
      assignmentTypes: ['USER', 'DELEGATED'],
    })
    expect(api.mock.calls[0][0]).not.toHaveProperty('priorities')
    expect(w.find('[data-test="todo-priorities"]').exists()).toBe(false)
  })

  it('reset clears toolbar fields and omits them from the next query', async () => {
    const w = mountPage()
    await flushPromises()
    const toolbar = w.getComponent(TodoListToolbar)
    await toolbar.vm.$emit('update:assignmentTypes', ['USER'])
    await toolbar.vm.$emit('update:keyword', '请假')
    await toolbar.vm.$emit('search')
    await flushPromises()
    api.mockClear()
    await w.get('[data-test="todo-reset-btn"]').trigger('click')
    await flushPromises()
    const body = api.mock.calls[0][0]
    expect(body).not.toHaveProperty('keyword')
    expect(body).not.toHaveProperty('assignmentTypes')
    expect(body).not.toHaveProperty('priorities')
  })

  it('is named Tasks so PortalLayout keep-alive can cache the list', () => {
    const w = mountPage()
    expect(w.vm.$.type.name).toBe('Tasks')
    w.unmount()
  })

  it('restores toolbar query after remount so returning from a task keeps search', async () => {
    const first = mountPage()
    await flushPromises()
    const toolbar = first.getComponent(TodoListToolbar)
    await toolbar.vm.$emit('update:assignmentTypes', ['USER'])
    await toolbar.vm.$emit('update:priorities', ['HIGH'])
    await toolbar.vm.$emit('update:keyword', '请假')
    await toolbar.vm.$emit('search')
    await flushPromises()
    first.unmount()

    api.mockClear()
    const second = mountPage()
    await flushPromises()
    expect((second.get('[data-test="todo-search"]').element as HTMLInputElement).value).toBe('请假')
    expect(api.mock.calls[0][0]).toMatchObject({
      keyword: '请假',
      assignmentTypes: ['USER'],
      priorities: ['HIGH'],
    })
    second.unmount()
  })

  it('does not restore toolbar query after Reset then remount', async () => {
    const first = mountPage()
    await flushPromises()
    const toolbar = first.getComponent(TodoListToolbar)
    await toolbar.vm.$emit('update:keyword', '请假')
    await toolbar.vm.$emit('search')
    await flushPromises()
    await first.get('[data-test="todo-reset-btn"]').trigger('click')
    await flushPromises()
    first.unmount()

    api.mockClear()
    const second = mountPage()
    await flushPromises()
    expect((second.get('[data-test="todo-search"]').element as HTMLInputElement).value).toBe('')
    expect(api.mock.calls[0][0]).not.toHaveProperty('keyword')
    second.unmount()
  })
})
