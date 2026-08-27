import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import TodoTasks from '../tasks/index.vue'
import TodoListToolbar from '../tasks/TodoListToolbar.vue'
import { queryTodoTasks } from '@/api/task'

vi.mock('@/api/task', () => ({
  queryTodoTasks: vi.fn(),
  batchUrgeTasks: vi.fn(),
}))

vi.mock('@/stores/pendingTask', () => ({
  usePendingTaskStore: () => ({
    syncCountFromListTotal: vi.fn(),
  }),
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
          virtualGroup: 'Virtual Group',
          deptRole: 'Dept Role',
          delegated: 'Delegated',
          requestId: 'Request ID',
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
        'el-link': true,
        'el-tag': true,
        'el-icon': true,
        'el-dropdown': { template: '<div><slot /><slot name="dropdown" /></div>' },
        'el-dropdown-menu': { template: '<div><slot /></div>' },
        'el-dropdown-item': { template: '<div><slot /></div>' },
        ListFilterDialog: true,
        ListPagination: true,
      },
    },
  })
}

describe('To Do shared list', () => {
  beforeEach(() => {
    api.mockReset()
    api.mockResolvedValue({
      data: {
        columns: [
          { field: 'requestId', label: 'task.requestId', kind: 'TEXT', operators: ['contains'], filterable: true, sortable: true },
          { field: 'taskName', label: 'task.taskName', kind: 'TEXT', operators: ['contains'], filterable: true, sortable: true },
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
    expect(w.text()).toContain('Priority')
    const headers = w.findAllComponents({ name: 'ListColumnHeader' })
    expect(headers.length).toBeGreaterThan(0)
    expect(headers.every((h) => h.find('.list-col-header').exists())).toBe(true)
    expect(w.get('[data-test="todo-reset-btn"]').text()).toContain('Reset')
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

  it('sends assignmentTypes and priorities on search', async () => {
    const w = mountPage()
    await flushPromises()
    api.mockClear()
    const toolbar = w.getComponent(TodoListToolbar)
    await toolbar.vm.$emit('update:assignmentTypes', ['USER', 'DELEGATED'])
    await toolbar.vm.$emit('update:priorities', ['HIGH'])
    await toolbar.vm.$emit('search')
    await flushPromises()
    expect(api.mock.calls[0][0]).toMatchObject({
      page: 0,
      size: 20,
      assignmentTypes: ['USER', 'DELEGATED'],
      priorities: ['HIGH'],
    })
  })

  it('reset clears toolbar fields and omits them from the next query', async () => {
    const w = mountPage()
    await flushPromises()
    const toolbar = w.getComponent(TodoListToolbar)
    await toolbar.vm.$emit('update:assignmentTypes', ['USER'])
    await toolbar.vm.$emit('update:priorities', ['URGENT'])
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
})
