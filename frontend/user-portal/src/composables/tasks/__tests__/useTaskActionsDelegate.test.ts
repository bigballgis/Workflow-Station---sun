import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

const pushMock = vi.fn()
const delegateTaskMock = vi.fn(async () => ({}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))
vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  },
}))
vi.mock('@/api/task', () => ({
  completeTask: vi.fn(),
  delegateTask: (...args: unknown[]) => delegateTaskMock(...args),
  transferTask: vi.fn(),
  urgeTask: vi.fn(),
}))
vi.mock('@/api/user', () => ({
  userApi: {
    searchUsers: vi.fn(async () => []),
  },
}))
vi.mock('@/utils/subTableAssignment', () => ({
  resolveAssigneeFieldForBinding: () => 'assignee_user_id',
  allSubTableRowsHaveAssignee: () => true,
}))
import { ElMessage } from 'element-plus'
import { useTaskActions } from '../useTaskActions'

describe('useTaskActions submitAction delegate payload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function createActions(actionForm: Record<string, string>) {
    return useTaskActions({
      taskId: 'task-1',
      taskInfo: ref({}),
      subTableBindings: ref([]),
      formData: ref({}),
      submitting: ref(false),
      approveDialogVisible: ref(false),
      approveDialogTitle: ref(''),
      currentApproveAction: ref(''),
      approveForm: { comment: '' },
      actionDialogVisible: ref(true),
      actionDialogTitle: ref(''),
      currentAction: ref('delegate'),
      actionForm: actionForm as any,
      userOptions: ref([]),
      userSearchLoading: ref(false),
      loadTaskDetail: vi.fn(async () => {}),
    })
  }

  it('posts USER delegate body', async () => {
    const actions = createActions({
      targetUserId: 'user-b',
      reason: 'leave',
      targetType: 'USER',
    })
    await actions.submitAction()
    expect(delegateTaskMock).toHaveBeenCalledWith('task-1', {
      delegatedTargetType: 'USER',
      delegatedTo: 'user-b',
      reason: 'leave',
    })
  })

  it('posts BU_ROLE delegate body with codes', async () => {
    const actions = createActions({
      targetUserId: '',
      reason: 'coverage',
      targetType: 'BU_ROLE',
      delegatedBuCode: 'HK',
      delegatedRoleCode: 'APPROVER',
    })
    await actions.submitAction()
    expect(delegateTaskMock).toHaveBeenCalledWith('task-1', {
      delegatedTargetType: 'BU_ROLE',
      delegatedBuCode: 'HK',
      delegatedRoleCode: 'APPROVER',
      reason: 'coverage',
    })
  })

  it('warns when BU_ROLE pair is incomplete', async () => {
    const actions = createActions({
      targetUserId: '',
      reason: '',
      targetType: 'BU_ROLE',
      delegatedBuCode: 'HK',
      delegatedRoleCode: '',
    })
    await actions.submitAction()
    expect(delegateTaskMock).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()
  })
})
