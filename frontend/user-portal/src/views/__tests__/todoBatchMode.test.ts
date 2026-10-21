import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import TodoTasks from '../tasks/index.vue'
import BatchTaskGroups from '@/components/tasks/BatchTaskGroups.vue'
import ListColumnHeader from '@platform-shared/list/ListColumnHeader.vue'
import { queryTodoTasks, getBatchTaskGroups, batchCompleteTasks, claimBatch, type BatchTaskGroup } from '@/api/task'
import { TODO_TOOLBAR_QUERY_KEY } from '@/composables/tasks/todoToolbarQuery'

vi.mock('@/api/task', () => ({
  queryTodoTasks: vi.fn(),
  batchUrgeTasks: vi.fn(),
  claimBatch: vi.fn(),
  unclaimBatch: vi.fn(),
  claimTask: vi.fn(),
  unclaimTask: vi.fn(),
  getBatchTaskGroups: vi.fn(),
  batchCompleteTasks: vi.fn(),
}))

const pendingStore = vi.hoisted(() => ({
  count: 15,
  syncCountFromListTotal: vi.fn(),
  fetchPendingCount: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/stores/pendingTask', () => ({ usePendingTaskStore: () => pendingStore }))

vi.mock('@/stores/userPreference', () => ({
  useUserPreferenceStore: () => ({
    autoClaimOnOpen: false,
    autoPreviewOnOpen: false,
    saving: false,
    load: vi.fn().mockResolvedValue(undefined),
  }),
}))

const session = vi.hoisted(() => ({ user: null as { roles: string[]; systemAdministrator: boolean } | null }))
vi.mock('@/api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/auth')>()),
  getStoredUser: () => session.user,
}))

const routeState = vi.hoisted(() => ({ query: {} as Record<string, string> }))
const routerMock = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))
vi.mock('vue-router', () => ({
  useRouter: () => routerMock,
  useRoute: () => routeState,
}))

const messageBox = vi.hoisted(() => ({
  prompt: vi.fn(),
  confirm: vi.fn(),
  alert: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessageBox: messageBox,
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

const GROUP: BatchTaskGroup = {
  groupKey: 'fu:cat-1023',
  processDefinitionKey: 'purchase',
  functionUnitName: '采购申请',
  version: '1.0.23',
  taskDefinitionKey: 'dept',
  nodeName: '部门审批',
  count: 2,
  actions: [
    { actionId: 'a-ok', actionName: 'Approve Purchase', actionType: 'APPROVE', icon: 'check', buttonColor: '#2E7D32', requireComment: false },
    { actionId: 'a-no', actionName: 'Reject Purchase', actionType: 'REJECT', requireComment: true },
  ],
}
const SECOND_STEP: BatchTaskGroup = {
  ...GROUP,
  groupKey: 'fu:cat-1022',
  version: '1.0.22',
  count: 1,
}

const ROWS = [
  { taskId: 't1', requestId: 'PR-1', assignmentType: 'USER', bpmnAssigneeType: 'INITIATOR', assignee: 'u1', assigneeName: 'Dev Tester' },
  { taskId: 't2', requestId: 'PR-2', assignmentType: 'USER', bpmnAssigneeType: 'INITIATOR', assignee: 'u1', assigneeName: 'Dev Tester' },
]

const TODO_COLUMNS = ['requestId', 'functionUnitCode', 'taskName', 'assignmentType', 'assigneeName', 'createTime']
  .map((field) => ({ field, label: field, kind: 'TEXT', operators: ['contains'], filterable: true, sortable: true }))

function mountPage() {
  const i18n = createI18n({ legacy: false, locale: 'en', missingWarn: false, fallbackWarn: false, messages: { en: {} } })
  return mount(TodoTasks, {
    global: {
      plugins: [i18n],
      stubs: {
        'el-table': {
          name: 'ElTableStub',
          emits: ['selection-change'],
          template: '<div><slot /><slot name="empty" /></div>',
        },
        'el-table-column': { template: '<div><slot name="header" /></div>' },
        'el-tabs': { template: '<div><slot /></div>' },
        'el-tab-pane': { template: '<div><slot name="label" /></div>' },
        'el-dialog': true,
        'el-button': {
          props: ['type'],
          inheritAttrs: false,
          template: '<button v-bind="$attrs" :data-type="type" @click="$emit(\'click\')"><slot /></button>',
        },
        'el-link': { template: '<a @click="$emit(\'click\')"><slot /></a>' },
        'el-tag': { template: '<span class="tag-stub"><slot /></span>' },
        'el-switch': true,
        'el-input': true,
        'el-select': true,
        'el-option': true,
        'el-icon': true,
        ListFilterDialog: true,
        ListPagination: true,
        PortalHelpLink: true,
        TodoClaimRowActions: true,
        TaskReassignDialog: true,
      },
    },
  })
}

describe('To Do batch actions tab', () => {
  beforeEach(() => {
    sessionStorage.clear()
    session.user = null
    vi.mocked(queryTodoTasks).mockReset().mockResolvedValue({
      data: { columns: TODO_COLUMNS, content: ROWS, page: 0, size: 20, totalElements: 2 },
    } as never)
    vi.mocked(getBatchTaskGroups).mockReset().mockResolvedValue({ data: [GROUP] } as never)
    vi.mocked(batchCompleteTasks).mockReset()
      .mockResolvedValue({ data: { succeeded: ['t1', 't2'], failed: [] } } as never)
    pendingStore.syncCountFromListTotal.mockClear()
    pendingStore.fetchPendingCount.mockClear()
    messageBox.prompt.mockReset()
    messageBox.confirm.mockReset()
    routerMock.push.mockClear()
  })

  it('lists task groups on the batch tab and drills in via the route query', async () => {
    routeState.query = { tab: 'batch' }
    const w = mountPage()
    await flushPromises()
    expect(w.find('[data-test="batch-task-groups"]').exists()).toBe(true)
    expect(w.findAll('[data-test="batch-group-row"]')).toHaveLength(1)
    expect(w.text()).toContain('部门审批')
    await w.get('[data-test="batch-group-open"]').trigger('click')
    expect(routerMock.push).toHaveBeenCalledWith({
      query: { tab: 'batch', group: 'fu:cat-1023', node: 'dept' },
    })
  })

  it('a claim on the All To Do tab reloads the Batch Actions groups (a claimed pool task can join one)', async () => {
    vi.mocked(claimBatch).mockResolvedValue({
      data: { claimed: 1, skipped: 0, failed: 0, remaining: 0, attemptedTaskIds: [] },
    } as never)
    messageBox.confirm.mockResolvedValue('confirm')
    routeState.query = {}
    const w = mountPage()
    await flushPromises()

    w.findComponent({ name: 'ElTableStub' }).vm.$emit('selection-change', [{ ...ROWS[0], assignee: null, claimable: true }])
    await flushPromises()
    await w.get('[data-test="todo-claim-selected-btn"]').trigger('click')
    await flushPromises()

    const claimedAt = vi.mocked(claimBatch).mock.invocationCallOrder[0]
    expect(claimedAt).toBeDefined()
    expect(Math.max(...vi.mocked(getBatchTaskGroups).mock.invocationCallOrder)).toBeGreaterThan(claimedAt)
  })

  it('task group columns resize by dragging the shared header handle, and the width is remembered', async () => {
    routeState.query = { tab: 'batch' }
    const w = mountPage()
    await flushPromises()
    const groupsTable = w.findComponent(BatchTaskGroups)
    const headers = groupsTable.findAllComponents(ListColumnHeader)
    expect(headers).toHaveLength(5)
    expect(groupsTable.findAll('.col-resize-handle')).toHaveLength(5)

    headers[1].vm.$emit('width-change', 333)
    headers[1].vm.$emit('width-commit')
    await flushPromises()

    expect(groupsTable.findAll('colgroup col')[1].attributes('style')).toContain('width: 333px')
    expect(sessionStorage.getItem('portal-list-layout:todo-batch-groups-v1')).toContain('"taskName":333')
  })

  it('merges the Function Unit cell across all of its step rows', async () => {
    vi.mocked(getBatchTaskGroups).mockResolvedValue({ data: [GROUP, SECOND_STEP] } as never)
    routeState.query = { tab: 'batch' }
    const w = mountPage()
    await flushPromises()
    const rows = w.findAll('[data-test="batch-group-row"]')
    expect(rows).toHaveLength(2)
    const fu = rows[0].get('.batch-groups__fu')
    expect(fu.text()).toContain('采购申请')
    expect(fu.attributes('rowspan')).toBe('2')
    expect(rows[1].find('.batch-groups__fu').exists()).toBe(false)
  })

  it('a drilled-in group queries the To Do list scoped to the group and leaves the badge alone', async () => {
    routeState.query = { tab: 'batch', group: 'fu:cat-1023', node: 'dept' }
    const w = mountPage()
    await flushPromises()
    expect(vi.mocked(queryTodoTasks).mock.calls[0][0]).toMatchObject({
      batchGroupKey: 'fu:cat-1023',
      batchTaskDefinitionKey: 'dept',
    })
    expect(pendingStore.syncCountFromListTotal).not.toHaveBeenCalled()
    // Opened straight into the group (link / refresh): the badge still gets the real To Do total.
    expect(pendingStore.fetchPendingCount).toHaveBeenCalledTimes(1)
    expect(w.find('[data-test="batch-group-head"]').exists()).toBe(true)
  })

  it('moves the group-wide columns into the header and drops the assignment-type filter', async () => {
    sessionStorage.setItem(TODO_TOOLBAR_QUERY_KEY, JSON.stringify({ keyword: '', assignmentTypes: ['DELEGATED'], priorities: [] }))
    routeState.query = { tab: 'batch', group: 'fu:cat-1023', node: 'dept' }
    const w = mountPage()
    await flushPromises()
    const headers = w.findAllComponents({ name: 'ListColumnHeader' }).map((h) => h.props('column').field)
    expect(headers).toEqual(['requestId', 'createTime'])
    const facts = w.get('[data-test="batch-group-facts"]').text()
    expect(facts).toContain('部门审批')
    expect(facts).toContain('task.processInitiator')
    expect(facts).toContain('Dev Tester')
    expect(w.find('[data-test="todo-assignment-types"]').exists()).toBe(false)
    expect(vi.mocked(queryTodoTasks).mock.calls[0][0]).not.toHaveProperty('assignmentTypes')
    // Claim operations are not part of a batch group: no (empty) Action column there.
    expect(w.find('.todo-claim-action-header').exists()).toBe(false)
  })

  it('the full To Do list keeps every column and never sends batch scope', async () => {
    routeState.query = {}
    const w = mountPage()
    await flushPromises()
    expect(vi.mocked(queryTodoTasks).mock.calls[0][0]).not.toHaveProperty('batchGroupKey')
    expect(pendingStore.syncCountFromListTotal).toHaveBeenCalledWith(2)
    expect(pendingStore.fetchPendingCount).not.toHaveBeenCalled()
    expect(w.findAllComponents({ name: 'ListColumnHeader' })).toHaveLength(6)
    // The full To Do keeps the per-row claim / unclaim / force-unclaim / reassign column.
    expect(w.find('.todo-claim-action-header').exists()).toBe(true)
  })

  /**
   * Allowed Roles hide an action here exactly as on the task page; a group whose every action is
   * hidden for this session is not offered (nor counted, nor marked on its rows).
   */
  it('applies Allowed Roles to the batch actions and drops a group left with none', async () => {
    session.user = { roles: ['CLERK'], systemAdministrator: false }
    const managerOnly = JSON.stringify({ allowedRoles: ['MANAGER'] })
    vi.mocked(getBatchTaskGroups).mockResolvedValue({ data: [
      { ...GROUP, actions: [GROUP.actions[0], { ...GROUP.actions[1], configJson: managerOnly }] },
      { ...SECOND_STEP, actions: [{ ...GROUP.actions[0], configJson: managerOnly }] },
    ] } as never)
    routeState.query = { tab: 'batch' }
    const w = mountPage()
    await flushPromises()

    const rows = w.findAll('[data-test="batch-group-row"]')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('Approve Purchase')
    expect(rows[0].text()).not.toContain('Reject Purchase')
    const groupOfRow = (w.vm as unknown as { groupOfRow: (row: object) => BatchTaskGroup | undefined }).groupOfRow
    expect(groupOfRow({ batchGroupKey: SECOND_STEP.groupKey, taskDefinitionKey: 'dept' })).toBeUndefined()
  })

  it('shows a System Administrator every batch action, Allowed Roles notwithstanding', async () => {
    session.user = { roles: [], systemAdministrator: true }
    const managerOnly = JSON.stringify({ allowedRoles: ['MANAGER'] })
    vi.mocked(getBatchTaskGroups).mockResolvedValue({ data: [
      { ...GROUP, actions: GROUP.actions.map((a) => ({ ...a, configJson: managerOnly })) },
    ] } as never)
    routeState.query = { tab: 'batch' }
    const w = mountPage()
    await flushPromises()

    const row = w.get('[data-test="batch-group-row"]')
    expect(row.text()).toContain('Approve Purchase')
    expect(row.text()).toContain('Reject Purchase')
  })

  /** The mark must agree with the Batch tab: server flag and a listed group, never one alone. */
  it('flags a To Do row only when its batch group is listed', async () => {
    routeState.query = {}
    const w = mountPage()
    await flushPromises()
    const groupOfRow = (w.vm as unknown as { groupOfRow: (row: object) => BatchTaskGroup | undefined }).groupOfRow
    expect(groupOfRow({ batchGroupKey: 'fu:cat-1023', taskDefinitionKey: 'dept' })).toEqual(GROUP)
    expect(groupOfRow({ batchGroupKey: null, taskDefinitionKey: 'dept' })).toBeUndefined()
    // Flagged by the server, but the Batch tab does not list that group (e.g. no approve/reject action).
    expect(groupOfRow({ batchGroupKey: 'fu:cat-1023', taskDefinitionKey: 'other-node' })).toBeUndefined()
  })

  it('a width dragged on the full To Do list does not pin the group view columns', async () => {
    sessionStorage.setItem('portal-list-layout:todo-tasks-v4', JSON.stringify({ v: 4, columnWidths: { requestId: 130 } }))
    const requestIdWidth = (w: ReturnType<typeof mountPage>) => w.findAllComponents({ name: 'ListColumnHeader' })
      .find((h) => h.props('column').field === 'requestId')?.props('width')

    routeState.query = {}
    const all = mountPage()
    await flushPromises()
    expect(requestIdWidth(all)).toBe(130)
    all.unmount()

    routeState.query = { tab: 'batch', group: 'fu:cat-1023', node: 'dept' }
    const group = mountPage()
    await flushPromises()
    expect(requestIdWidth(group)).not.toBe(130)
  })

  it('renders the node actions as configured in DW, not as fixed batch buttons', async () => {
    routeState.query = { tab: 'batch', group: 'fu:cat-1023', node: 'dept' }
    const w = mountPage()
    await flushPromises()
    w.findComponent({ name: 'ElTableStub' }).vm.$emit('selection-change', ROWS)
    await flushPromises()
    const [approve, reject] = w.findAll('[data-test="batch-complete-btn"]')
    expect(approve.text()).toBe('Approve Purchase')
    expect(approve.attributes('data-type')).toBe('primary')
    expect(approve.attributes('style')).toContain('--el-button-bg-color: #2E7D32')
    expect(reject.text()).toBe('Reject Purchase')
    expect(reject.attributes('data-type')).toBe('danger')
  })

  it('confirms, then completes the selected tasks with the action and no comment', async () => {
    routeState.query = { tab: 'batch', group: 'fu:cat-1023', node: 'dept' }
    messageBox.confirm.mockResolvedValue('confirm')
    const w = mountPage()
    await flushPromises()
    w.findComponent({ name: 'ElTableStub' }).vm.$emit('selection-change', ROWS)
    await flushPromises()
    const buttons = w.findAll('[data-test="batch-complete-btn"]')
    expect(buttons).toHaveLength(2)
    await buttons[0].trigger('click')
    await flushPromises()
    expect(messageBox.confirm).toHaveBeenCalled()
    expect(messageBox.prompt).not.toHaveBeenCalled()
    expect(batchCompleteTasks).toHaveBeenCalledWith(['t1', 't2'], 'a-ok', undefined)
    expect(pendingStore.fetchPendingCount).toHaveBeenCalled()
  })

  it('asks one shared comment for a comment-required action', async () => {
    routeState.query = { tab: 'batch', group: 'fu:cat-1023', node: 'dept' }
    messageBox.prompt.mockResolvedValue({ value: '  not in budget ' })
    const w = mountPage()
    await flushPromises()
    w.findComponent({ name: 'ElTableStub' }).vm.$emit('selection-change', ROWS)
    await flushPromises()
    await w.findAll('[data-test="batch-complete-btn"]')[1].trigger('click')
    await flushPromises()
    expect(messageBox.prompt).toHaveBeenCalled()
    expect(batchCompleteTasks).toHaveBeenCalledWith(['t1', 't2'], 'a-no', 'not in budget')
  })

  it('cancelling the confirmation sends nothing', async () => {
    routeState.query = { tab: 'batch', group: 'fu:cat-1023', node: 'dept' }
    messageBox.confirm.mockRejectedValue('cancel')
    const w = mountPage()
    await flushPromises()
    w.findComponent({ name: 'ElTableStub' }).vm.$emit('selection-change', ROWS)
    await flushPromises()
    await w.findAll('[data-test="batch-complete-btn"]')[0].trigger('click')
    await flushPromises()
    expect(batchCompleteTasks).not.toHaveBeenCalled()
  })
})
