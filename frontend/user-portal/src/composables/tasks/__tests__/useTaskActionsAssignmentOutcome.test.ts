import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
const mocks = vi.hoisted(() => ({
  complete: vi.fn(), push: vi.fn(),
  message: Object.assign(vi.fn(), { success: vi.fn(), warning: vi.fn(), error: vi.fn() }),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: mocks.message }))
vi.mock('@/api/task', () => ({ completeTask: mocks.complete, delegateTask: vi.fn(), transferTask: vi.fn(), urgeTask: vi.fn() }))
vi.mock('@/api/user', () => ({ userApi: { searchUsers: vi.fn() } }))
import { useTaskActions } from '../useTaskActions'

function setup() {
  const visible = ref(true)
  const submitting = ref(false)
  return { visible, submitting, actions: useTaskActions({
    taskId: 'synthetic-task', taskInfo: ref({}), subTableBindings: ref([]), formData: ref({}),
    submitting, approveDialogVisible: visible, approveDialogTitle: ref(''), currentApproveAction: ref('APPROVE'),
    approveForm: { comment: '' }, actionDialogVisible: ref(false), actionDialogTitle: ref(''), currentAction: ref(''),
    actionForm: { targetUserId: '', reason: '' }, userOptions: ref([]), userSearchLoading: ref(false), loadTaskDetail: vi.fn(),
  }) }
}
describe('completion assignment observation', () => {
  beforeEach(() => { vi.clearAllMocks() })
  it.each(['FAILED', 'UNCONFIRMED'])('shows %s without retrying or claiming action failure', async status => {
    mocks.complete.mockResolvedValue({ data: { batchAssignmentVerification: { status, issues: [] } } })
    const { actions, visible, submitting } = setup()
    await actions.submitApprove()
    expect(mocks.complete).toHaveBeenCalledTimes(1)
    expect(mocks.message).toHaveBeenCalledWith(expect.objectContaining({
      type: 'warning', duration: 0, showClose: true,
      message: status === 'FAILED' ? 'task.batchAssignmentFailed' : 'task.batchAssignmentUnconfirmed',
    }))
    expect(mocks.message.success).not.toHaveBeenCalled()
    expect(mocks.message.error).not.toHaveBeenCalled()
    expect(visible.value).toBe(false)
    expect(submitting.value).toBe(false)
    expect(mocks.push).toHaveBeenCalledWith('/tasks')
  })
  it('keeps ordinary completion feedback when the server has no new observation', async () => {
    mocks.complete.mockResolvedValue({})
    await setup().actions.submitApprove()
    expect(mocks.message.success).toHaveBeenCalledWith('task.operationSuccess')
    expect(mocks.message).not.toHaveBeenCalled()
    expect(mocks.push).toHaveBeenCalledWith('/tasks')
  })
})
