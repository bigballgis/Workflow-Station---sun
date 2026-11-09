import type { Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import type { TaskActionInfo } from '@/api/task'
import { actionIconComponent } from '@/utils/actionIcon'
import { taskPriorityBand, type TaskPriorityBand } from '@/utils/taskPriority'
import { useUserStore } from '@/stores/user'

export function useTaskDisplay(taskInfo: Ref<Record<string, any>>) {
  const { t } = useI18n()

  function getHistoryStatus(operationType: string): 'completed' | 'current' | 'pending' | 'rejected' {
    const map: Record<string, 'completed' | 'current' | 'pending' | 'rejected'> = {
      'SUBMIT': 'completed',
      'APPROVE': 'completed',
      'REJECT': 'rejected',
      'DELEGATE': 'completed',
      'TRANSFER': 'completed',
      'CLAIM': 'current',
      'PENDING': 'pending',
      'RETURN': 'completed',
      'DRAFT': 'completed',
      'DRAFT_TASK': 'completed',
      'SEND': 'completed',
    }
    return map[operationType] || 'completed'
  }

  function getHistoryAction(operationType: string): 'approve' | 'reject' | 'transfer' | 'delegate' | 'withdraw' | 'submit' | 'return' | 'draft' | 'send' | undefined {
    const map: Record<string, 'approve' | 'reject' | 'transfer' | 'delegate' | 'withdraw' | 'submit' | 'return' | 'draft' | 'send'> = {
      'SUBMIT': 'submit',
      'APPROVE': 'approve',
      'REJECT': 'reject',
      'TRANSFER': 'transfer',
      'DELEGATE': 'delegate',
      'WITHDRAW': 'withdraw',
      'RETURN': 'return',
      'DRAFT': 'draft',
      'DRAFT_TASK': 'draft',
      'SEND': 'send',
    }
    return map[operationType]
  }

  function formatDate(date?: string | number[]) {
    if (!date) return '-'
    if (Array.isArray(date)) {
      const [year, month, day, hour = 0, minute = 0, second = 0] = date
      const d = dayjs(new Date(year, month - 1, day, hour, minute, second))
      return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : '-'
    }
    const d = dayjs(date)
    return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : '-'
  }

  function sameIdentity(left?: string, right?: string): boolean {
    if (!left || !right) return false
    return left.trim() === right.trim()
  }

  function isDelegatedTask(): boolean {
    const info = taskInfo.value
    if (info.delegated === true) return true
    if (info.delegatedTo && String(info.delegatedTo).trim()) return true
    return !!(info.delegatedBuCode && info.delegatedRoleCode)
  }

  /** D-55: a single-task delegation keeps its target but says when it is not in effect. */
  function getDelegationStatusDisplay(): string {
    if (!isDelegatedTask()) return ''
    const status = getDelegationTargetDisplay()
    const reason = taskInfo.value.delegationInactiveReason
    if (reason === 'DELEGATOR_LOST_PORTAL_ENTRY') return `${status} · ${t('delegation.inactiveDelegatorLostPortalEntry')}`
    if (reason === 'DELEGATE_LOST_PORTAL_ENTRY') return `${status} · ${t('delegation.inactiveDelegateLostPortalEntry')}`
    return status
  }

  function getDelegationTargetDisplay(): string {
    const info = taskInfo.value
    const userStore = useUserStore()
    const me = userStore.userInfo?.id
    const username = userStore.userInfo?.username
    const isAssignee = sameIdentity(me, info.assignee) || sameIdentity(username, info.assignee)
    const isUserDelegatee = sameIdentity(me, info.delegatedTo) || sameIdentity(username, info.delegatedTo)
    if (isUserDelegatee && !isAssignee) {
      const name = info.assigneeName || info.assignee || info.delegatorName || info.delegatorId || '-'
      return t('task.onBehalfOf', { name })
    }
    const buRole = String(info.delegatedTargetType || '').toUpperCase() === 'BU_ROLE'
      || (info.delegatedBuCode && info.delegatedRoleCode)
    if (buRole) {
      return t('task.delegatedToBuRole', {
        bu: info.delegatedBuName || info.delegatedBuCode || '-',
        role: info.delegatedRoleName || info.delegatedRoleCode || '-',
      })
    }
    const name = info.delegatedToName || info.delegatedTo || '-'
    return t('task.delegatedToUser', { name })
  }

  function getCurrentAssigneeDisplay() {
    if (taskInfo.value.assigneeName) return taskInfo.value.assigneeName
    if (taskInfo.value.assignee) return taskInfo.value.assignee
    if (taskInfo.value.candidateUsers) {
      const candidates = taskInfo.value.candidateUsers.split(',')
      if (candidates.length === 1) return candidates[0]
      return `${candidates.join(' / ')} (${t('task.anyApprove')})`
    }
    return '-'
  }

  function getPriorityLabel(priority?: string) {
    return t(`task.${taskPriorityBand(priority).toLowerCase()}`)
  }

  function getPriorityType(priority?: string): 'danger' | 'warning' | 'info' | 'success' {
    const map: Record<TaskPriorityBand, 'danger' | 'warning' | 'info' | 'success'> = {
      URGENT: 'danger',
      HIGH: 'warning',
      NORMAL: 'info',
      LOW: 'success',
    }
    return map[taskPriorityBand(priority)]
  }

  function getActionLabel(action: TaskActionInfo): string {
    return (action.actionType || '').trim().toUpperCase() === 'SAVE' ? t('common.save') : action.actionName
  }

  function getIconComponent(iconName?: string) {
    return actionIconComponent(iconName)
  }

  return {
    getHistoryStatus,
    getHistoryAction,
    formatDate,
    getCurrentAssigneeDisplay,
    getDelegationStatusDisplay,
    getPriorityLabel,
    getPriorityType,
    getActionLabel,
    getIconComponent
  }
}
