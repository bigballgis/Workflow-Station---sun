import { markRaw, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import { Check, CircleCheck, CircleClose, Close, Files, Warning, Bell, User } from '@element-plus/icons-vue'
import type { TaskActionInfo } from '@/api/task'

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
    }
    return map[operationType] || 'completed'
  }

  function getHistoryAction(operationType: string): 'approve' | 'reject' | 'transfer' | 'delegate' | 'withdraw' | 'submit' | 'return' | 'draft' | undefined {
    const map: Record<string, 'approve' | 'reject' | 'transfer' | 'delegate' | 'withdraw' | 'submit' | 'return' | 'draft'> = {
      'SUBMIT': 'submit',
      'APPROVE': 'approve',
      'REJECT': 'reject',
      'TRANSFER': 'transfer',
      'DELEGATE': 'delegate',
      'WITHDRAW': 'withdraw',
      'RETURN': 'return',
      'DRAFT': 'draft',
      'DRAFT_TASK': 'draft',
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
    const map: Record<string, string> = {
      'URGENT': t('task.urgent'),
      'HIGH': t('task.high'),
      'NORMAL': t('task.normal'),
      'LOW': t('task.low')
    }
    return map[priority || ''] || priority || t('task.normal')
  }

  function getPriorityType(priority?: string): 'danger' | 'warning' | 'info' | 'success' {
    const map: Record<string, 'danger' | 'warning' | 'info' | 'success'> = {
      'URGENT': 'danger',
      'HIGH': 'warning',
      'NORMAL': 'info',
      'LOW': 'success'
    }
    return map[priority || ''] || 'info'
  }

  function getButtonType(buttonColor?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' | '' {
    const colorMap: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
      'primary': 'primary',
      'success': 'success',
      'warning': 'warning',
      'danger': 'danger',
      'info': 'info'
    }
    return colorMap[buttonColor || ''] || 'primary'
  }

  function getActionLabel(action: TaskActionInfo): string {
    return (action.actionType || '').trim().toUpperCase() === 'SAVE' ? t('common.save') : action.actionName
  }

  function getIconComponent(iconName?: string) {
    if (!iconName) return null
    const iconMap: Record<string, any> = {
      'check': markRaw(Check),
      'check-circle': markRaw(CircleCheck),
      'times-circle': markRaw(CircleClose),
      'close': markRaw(Close),
      'file-alt': markRaw(Files),
      'files': markRaw(Files),
      'warning': markRaw(Warning),
      'bell': markRaw(Bell),
      'user': markRaw(User)
    }
    return iconMap[iconName] || markRaw(Check)
  }

  return {
    getHistoryStatus,
    getHistoryAction,
    formatDate,
    getCurrentAssigneeDisplay,
    getPriorityLabel,
    getPriorityType,
    getButtonType,
    getActionLabel,
    getIconComponent
  }
}
