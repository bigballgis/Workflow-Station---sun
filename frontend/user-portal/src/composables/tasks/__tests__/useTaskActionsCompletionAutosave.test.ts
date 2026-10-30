import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
const mocks = vi.hoisted(() => ({ complete: vi.fn(), status: vi.fn(), save: vi.fn(), message: Object.assign(vi.fn(), { error: vi.fn(), warning: vi.fn() }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: mocks.message }))
vi.mock('@/api/task', () => ({ completeTask: mocks.complete, getCompletionOperationStatus: mocks.status, delegateTask: vi.fn(), transferTask: vi.fn(), urgeTask: vi.fn() }))
vi.mock('@/api/user', () => ({ userApi: { searchUsers: vi.fn() } }))
vi.mock('@/api/processForm', () => ({ submitTaskForm: mocks.save, getTaskFormData: vi.fn() }))
vi.mock('@/utils/subTableAssignment', () => ({ resolveAssigneeFieldForBinding: () => 'reviewer', allSubTableRowsHaveAssignee: () => true }))
import { useTaskActions } from '../useTaskActions'
import { createSubTableAutosave } from '../subTableAutosave'
import { CompletionDeliveryUnknown } from '@/utils/completionOperation'

describe('completion status recovery restores actual subtable saves', () => {
  beforeEach(() => { vi.clearAllMocks(); sessionStorage.clear(); localStorage.clear() })
  it.each(['NOT_ACCEPTED', 'PENDING_SYNC', 'SYNC_CONFLICT', 'READY'])('unknown followed by %s preserves the correct autosave behavior', async (state) => {
    const bindings = ref([{ bindingId: 1, tableName: 'renamed_rows', columns: [], data: [{ custom_key: 'A', amount: 1 }] }])
    const formData = ref({})
    const autosave = createSubTableAutosave({ formReadOnly: ref(false), isCompletedTask: ref(false), isMiSubTaskMode: ref(false),
      effectiveTaskId: ref('recovery-task'), subTableBindings: bindings, formData, taskFormDTO: ref(null), loadedSubTableBaseline: ref({}),
      buildSubTableSubmitPayload: () => ({ formData: { __subTables__: { 'dw:renamed_rows': bindings.value[0].data } }, subTableBindingScopes: [] }), delayMs: 0 })
    const actions = useTaskActions({ taskId: 'recovery-task', taskInfo: ref({}), subTableBindings: bindings, formData,
      submitting: ref(false), approveDialogVisible: ref(true), approveDialogTitle: ref(''), currentApproveAction: ref('APPROVE'), approveForm: { comment: '' },
      actionDialogVisible: ref(false), actionDialogTitle: ref(''), currentAction: ref(''), actionForm: { targetUserId: '', reason: '' },
      userOptions: ref([]), userSearchLoading: ref(false), loadTaskDetail: vi.fn(), prepareBeforeComplete: autosave.syncSubTableRowVersionsBeforeSubmit,
      onSubmitSettled: autosave.resumeSubTableAutosave })
    mocks.complete.mockRejectedValue(new CompletionDeliveryUnknown('synthetic-operation'))
    await actions.submitApprove()
    expect(actions.completionState.value?.state).toBe('DELIVERY_UNKNOWN')
    autosave.scheduleSubTableAutosave(); await new Promise(resolve => setTimeout(resolve, 10))
    expect(mocks.save).not.toHaveBeenCalled()
    mocks.status.mockResolvedValue({ data: { operationId: 'synthetic-operation', taskId: state === 'NOT_ACCEPTED' ? null : 'recovery-task', state } })
    await actions.refreshCompletion()
    bindings.value[0].data[0].amount = 8
    autosave.scheduleSubTableAutosave(); await new Promise(resolve => setTimeout(resolve, 10))
    if (state === 'NOT_ACCEPTED') expect(mocks.save).toHaveBeenCalledWith('recovery-task', expect.objectContaining({ formData: { __subTables__: { 'dw:renamed_rows': [{ custom_key: 'A', amount: 8, platformRowUuid: expect.any(String) }] } } }))
    else expect(mocks.save).not.toHaveBeenCalled()
  })
})
