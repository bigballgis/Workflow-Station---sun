import type { Ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { completeTask, delegateTask, transferTask, urgeTask } from '@/api/task'
import { userApi, type UserOption } from '@/api/user'
import {
  resolveAssigneeFieldForBinding,
  allSubTableRowsHaveAssignee
} from '@/utils/subTableAssignment'
import { normalizeSubTableName } from './shared'

export function useTaskActions(options: {
  taskId: string
  taskInfo: Ref<Record<string, any>>
  subTableBindings: Ref<any[]>
  formData: Ref<Record<string, any>>
  submitting: Ref<boolean>
  approveDialogVisible: Ref<boolean>
  approveDialogTitle: Ref<string>
  currentApproveAction: Ref<string>
  approveForm: { comment: string }
  actionDialogVisible: Ref<boolean>
  actionDialogTitle: Ref<string>
  currentAction: Ref<string>
  actionForm: { targetUserId: string; reason: string }
  userOptions: Ref<UserOption[]>
  userSearchLoading: Ref<boolean>
  loadTaskDetail: () => Promise<void>
}) {
  const { t } = useI18n()
  const router = useRouter()

  function validateSubTableAssigneesForComplete(): boolean {
    for (const b of options.subTableBindings.value) {
      const af = resolveAssigneeFieldForBinding(b.columns, b.tableName)
      if (!af) continue
      if (!allSubTableRowsHaveAssignee(b.data || [], af)) {
        ElMessage.warning(t('task.allParticipantsMustHaveAssignee'))
        return false
      }
    }
    return true
  }

  async function searchUsers(keyword: string) {
    options.userSearchLoading.value = true
    try {
      const data = await userApi.searchUsers(keyword)
      options.userOptions.value = (data || []) as UserOption[]
    } catch (error) {
      console.error('Failed to search users:', error)
    } finally {
      options.userSearchLoading.value = false
    }
  }

  function onActionDialogOpened() {
    searchUsers('')
  }

  function handleApprove() {
    if (!validateSubTableAssigneesForComplete()) return
    options.currentApproveAction.value = 'APPROVE'
    options.approveDialogTitle.value = t('task.approve')
    options.approveForm.comment = ''
    options.approveDialogVisible.value = true
  }

  function handleReject() {
    options.currentApproveAction.value = 'REJECT'
    options.approveDialogTitle.value = t('task.reject')
    options.approveForm.comment = ''
    options.approveDialogVisible.value = true
  }

  function handleDelegate() {
    options.currentAction.value = 'delegate'
    options.actionDialogTitle.value = t('task.delegate')
    options.actionForm.targetUserId = ''
    options.actionForm.reason = ''
    options.userOptions.value = []
    options.actionDialogVisible.value = true
  }

  function handleTransfer() {
    options.currentAction.value = 'transfer'
    options.actionDialogTitle.value = t('task.transfer')
    options.actionForm.targetUserId = ''
    options.actionForm.reason = ''
    options.userOptions.value = []
    options.actionDialogVisible.value = true
  }

  function handleUrge() {
    options.currentAction.value = 'urge'
    options.actionDialogTitle.value = t('task.urge')
    options.actionForm.reason = ''
    options.actionDialogVisible.value = true
  }

  async function submitApprove() {
    if (options.currentApproveAction.value === 'APPROVE' && !validateSubTableAssigneesForComplete()) return
    options.submitting.value = true
    try {
      const variables: Record<string, any> = {}
      if (options.currentApproveAction.value === 'APPROVE') {
        variables.approval_result = 'approved'
        variables.approved = true
      } else if (options.currentApproveAction.value === 'REJECT') {
        variables.approval_result = 'rejected'
        variables.approved = false
      }
      if (options.approveForm.comment) {
        variables.approval_comment = options.approveForm.comment
      }
      const currentFormData: Record<string, any> = {}
      for (const key of Object.keys(options.formData.value)) {
        if (!key.startsWith('__') && !variables[key]) {
          currentFormData[key] = options.formData.value[key]
        }
      }
      const mergedSub: Record<string, any> = { ...(options.formData.value.__subTables__ || {}) }
      for (const b of options.subTableBindings.value) {
        mergedSub[b.bindingId] = b.data
        mergedSub[String(b.bindingId)] = b.data
        if (b.tableName) {
          mergedSub[b.tableName] = b.data
          mergedSub[normalizeSubTableName(b.tableName)] = b.data
        }
      }
      const participantsBinding = options.subTableBindings.value.find(
        b => b.tableName === 'participants' || resolveAssigneeFieldForBinding(b.columns, b.tableName)
      )
      if (participantsBinding) {
        mergedSub.participants = participantsBinding.data
      }
      currentFormData.__subTables__ = mergedSub
      Object.assign(variables, currentFormData)
      await completeTask(options.taskId, {
        taskId: options.taskId,
        action: options.currentApproveAction.value,
        comment: options.approveForm.comment,
        variables,
        formData: currentFormData
      })
      ElMessage.success(t('task.operationSuccess'))
      options.approveDialogVisible.value = false
      router.push('/tasks')
    } catch {
      ElMessage.error(t('task.operationFailed'))
    } finally {
      options.submitting.value = false
    }
  }

  async function submitAction() {
    if (options.currentAction.value !== 'urge' && !options.actionForm.targetUserId) {
      ElMessage.warning(t('task.selectUser'))
      return
    }
    options.submitting.value = true
    try {
      if (options.currentAction.value === 'delegate') {
        await delegateTask(options.taskId, options.actionForm.targetUserId, options.actionForm.reason)
        ElMessage.success(t('task.delegateSuccess'))
      } else if (options.currentAction.value === 'transfer') {
        await transferTask(options.taskId, options.actionForm.targetUserId, options.actionForm.reason)
        ElMessage.success(t('task.transferSuccess'))
      } else if (options.currentAction.value === 'urge') {
        await urgeTask(options.taskId, options.actionForm.reason)
        ElMessage.success(t('task.urgeSuccess'))
      }
      options.actionDialogVisible.value = false
      if (options.currentAction.value === 'transfer') {
        router.push('/tasks')
      } else {
        options.loadTaskDetail()
      }
    } catch {
      ElMessage.error(t('task.operationFailed'))
    } finally {
      options.submitting.value = false
    }
  }

  return {
    validateSubTableAssigneesForComplete,
    searchUsers,
    onActionDialogOpened,
    handleApprove,
    handleReject,
    handleDelegate,
    handleTransfer,
    handleUrge,
    submitApprove,
    submitAction
  }
}
