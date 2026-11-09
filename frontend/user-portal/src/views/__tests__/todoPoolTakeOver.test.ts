import { describe, it, expect, vi, beforeEach } from 'vitest'
import { defineComponent } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import TodoTasks from '../tasks/index.vue'
import { queryTodoTasks, getBatchTaskGroups, reassignTask } from '@/api/task'

vi.mock('@/api/task', () => ({
  queryTodoTasks: vi.fn(),
  batchUrgeTasks: vi.fn(),
  claimBatch: vi.fn(),
  unclaimBatch: vi.fn(),
  claimTask: vi.fn(),
  unclaimTask: vi.fn(),
  reassignTask: vi.fn(),
  getBatchTaskGroups: vi.fn(),
  batchCompleteTasks: vi.fn(),
}))

vi.mock('@/stores/pendingTask', () => ({
  usePendingTaskStore: () => ({ count: 3, syncCountFromListTotal: vi.fn(), fetchPendingCount: vi.fn() }),
}))

vi.mock('@/stores/userPreference', () => ({
  useUserPreferenceStore: () => ({
    autoClaimOnOpen: false,
    autoPreviewOnOpen: false,
    saving: false,
    load: vi.fn().mockResolvedValue(undefined),
  }),
}))

vi.mock('@/api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/auth')>()),
  getStoredUser: () => ({ userId: 'u-me', roles: [] }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ query: {} }),
}))

const messageBox = vi.hoisted(() => ({ confirm: vi.fn(), prompt: vi.fn(), alert: vi.fn() }))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessageBox: messageBox,
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

/** A BU Role pool task whose holder lost Portal entry, seen by a pool member who has entry. */
const LOST_ENTRY_ROW = {
  taskId: 't-lost',
  requestId: 'PR-7',
  assignmentType: 'BU_ROLE',
  claimPoolTask: true,
  assignee: 'u-gone',
  assigneeName: 'Gone User',
  assigneeLostPortalEntry: true,
  canTakeOver: true,
  canReassign: true,
  candidateUserIds: ['u-gone', 'u-me', 'u-no-entry'],
  reassignTargetUserIds: ['u-me', 'u-other'],
}

const TODO_COLUMNS = ['requestId', 'assigneeName']
  .map((field) => ({ field, label: field, kind: 'TEXT', operators: ['contains'], filterable: true, sortable: true }))

function mountPage() {
  const i18n = createI18n({ legacy: false, locale: 'en', missingWarn: false, fallbackWarn: false, messages: { en: {} } })
  return mount(TodoTasks, {
    global: {
      plugins: [i18n],
      stubs: {
        'el-table': { template: '<div><slot /><slot name="empty" /></div>' },
        // Render one row through the default slot so the cell / row-action components mount.
        'el-table-column': defineComponent({
          setup: () => ({ row: LOST_ENTRY_ROW }),
          template: '<div><slot name="header" /><slot :row="row" /></div>',
        }),
        'el-tabs': { template: '<div><slot /></div>' },
        'el-tab-pane': { template: '<div><slot name="label" /></div>' },
        'el-dialog': true,
        'el-button': { inheritAttrs: false, template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
        'el-link': true,
        'el-tag': { template: '<span class="tag-stub"><slot /></span>' },
        'el-switch': true,
        'el-input': true,
        'el-select': true,
        'el-option': true,
        'el-icon': true,
        'el-dropdown': defineComponent({
          name: 'ElDropdown',
          emits: ['command'],
          template: '<div><slot /><slot name="dropdown" /></div>',
        }),
        'el-dropdown-menu': { template: '<div><slot /></div>' },
        'el-dropdown-item': { inheritAttrs: false, template: '<div v-bind="$attrs"><slot /></div>' },
        ListFilterDialog: true,
        ListPagination: true,
        PortalHelpLink: true,
        TaskReassignDialog: true,
        TodoBatchUrgeDialog: true,
      },
    },
  })
}

describe('To Do — pool task whose holder lost Portal entry', () => {
  beforeEach(() => {
    sessionStorage.clear()
    messageBox.confirm.mockReset()
    vi.mocked(reassignTask).mockReset().mockResolvedValue({ data: {} } as never)
    vi.mocked(getBatchTaskGroups).mockReset().mockResolvedValue({ data: [] } as never)
    vi.mocked(queryTodoTasks).mockReset().mockResolvedValue({
      data: { columns: TODO_COLUMNS, content: [LOST_ENTRY_ROW], page: 0, size: 20, totalElements: 1 },
    } as never)
  })

  it('flags the holder on the row and takes the task over for the signed-in user after a confirm', async () => {
    messageBox.confirm.mockResolvedValue('confirm')
    const w = mountPage()
    await flushPromises()

    expect(w.find('[data-test="todo-assignee-lost-entry"]').exists()).toBe(true)
    await w.get('[data-test="todo-take-over-btn"]').trigger('click')
    await flushPromises()

    expect(messageBox.confirm).toHaveBeenCalledWith('task.takeOverConfirm', 'task.takeOver', expect.anything())
    expect(reassignTask).toHaveBeenCalledWith('t-lost', 'u-me')
  })

  it('the reassign dialog offers reassignTargetUserIds, not the engine candidates', async () => {
    const w = mountPage()
    await flushPromises()

    // With Take over on the row, Reassign sits under "More".
    expect(w.find('[data-test="todo-more-reassign"]').exists()).toBe(true)
    w.findComponent({ name: 'TodoClaimRowActions' }).findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'reassign')
    await flushPromises()
    const dialog = w.findComponent({ name: 'TaskReassignDialog' })
    expect(dialog.props('targetUserIds')).toEqual(['u-me', 'u-other'])
  })
})
