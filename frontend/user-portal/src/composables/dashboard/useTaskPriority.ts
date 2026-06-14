import { useI18n } from 'vue-i18n'

// 最近任务优先级：翻译标签与 CSS 类名
export function useTaskPriority() {
  const { t } = useI18n()

  // 将优先级转换为翻译键
  const getPriorityLabel = (priority: any): string => {
    if (!priority) return t('task.normal')

    // 如果是字符串，直接使用
    if (typeof priority === 'string') {
      const upperPriority = priority.toUpperCase()
      if (['URGENT', 'HIGH', 'NORMAL', 'LOW'].includes(upperPriority)) {
        return t(`task.${upperPriority.toLowerCase()}`)
      }
    }

    // 如果是数字，映射到对应的优先级
    if (typeof priority === 'number') {
      if (priority >= 75) return t('task.urgent')
      if (priority >= 50) return t('task.high')
      if (priority >= 25) return t('task.normal')
      return t('task.low')
    }

    return t('task.normal')
  }

  // 获取优先级 CSS 类名
  const getPriorityClass = (priority: any): string => {
    if (!priority) return 'normal'

    // 如果是字符串，直接使用
    if (typeof priority === 'string') {
      const upperPriority = priority.toUpperCase()
      if (['URGENT', 'HIGH', 'NORMAL', 'LOW'].includes(upperPriority)) {
        return upperPriority.toLowerCase()
      }
    }

    // 如果是数字，映射到对应的优先级
    if (typeof priority === 'number') {
      if (priority >= 75) return 'urgent'
      if (priority >= 50) return 'high'
      if (priority >= 25) return 'normal'
      return 'low'
    }

    return 'normal'
  }

  return {
    getPriorityLabel,
    getPriorityClass
  }
}
