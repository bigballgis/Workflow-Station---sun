import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

const pushMock = vi.fn()
const completeTaskMock = vi.fn(async () => ({}))

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
  },
}))

vi.mock('@/api/task', () => ({
  completeTask: (...args: any[]) => completeTaskMock(...args),
  delegateTask: vi.fn(),
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

import { useTaskActions } from '../useTaskActions'

describe('useTaskActions submitApprove __subTables__ canonicalization', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('persists only canonical bindingId keys instead of alias keys', async () => {
    const taskActions = useTaskActions({
      taskId: 'task-1',
      taskInfo: ref({}),
      subTableBindings: ref([
        { bindingId: 69, tableName: 'participants', columns: [{ fieldName: 'assignee_user_id' }], data: [{ id: 1 }] },
        { bindingId: 30, tableName: 'subtable2', columns: [], data: [{ id: 2 }] },
      ]),
      formData: ref({
        fieldA: 'x',
        __subTables__: {
          '69': [{ id: 1 }],
          participants: [{ id: 1 }],
          subtable2: [{ id: 2 }],
        },
      }),
      submitting: ref(false),
      approveDialogVisible: ref(true),
      approveDialogTitle: ref(''),
      currentApproveAction: ref('APPROVE'),
      approveForm: { comment: '' },
      actionDialogVisible: ref(false),
      actionDialogTitle: ref(''),
      currentAction: ref(''),
      actionForm: { targetUserId: '', reason: '' },
      userOptions: ref([]),
      userSearchLoading: ref(false),
      loadTaskDetail: vi.fn(async () => {}),
    })

    await taskActions.submitApprove()

    expect(completeTaskMock).toHaveBeenCalledTimes(1)
    const payload = completeTaskMock.mock.calls[0]?.[1]
    const subTables = payload?.formData?.__subTables__ ?? {}
    expect(Object.keys(subTables).sort()).toEqual(['30', '69'])
    expect(subTables.participants).toBeUndefined()
    expect(subTables.subtable2).toBeUndefined()
  })
})

