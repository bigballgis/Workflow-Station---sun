import { useI18n } from 'vue-i18n'
import { taskPriorityBand, taskPriorityCssClass } from '@/utils/taskPriority'

export function useTaskPriority() {
  const { t } = useI18n()

  const getPriorityLabel = (priority: string | number | null | undefined): string => {
    return t(`task.${taskPriorityBand(priority).toLowerCase()}`)
  }

  const getPriorityClass = (priority: string | number | null | undefined): string => {
    return taskPriorityCssClass(priority)
  }

  return {
    getPriorityLabel,
    getPriorityClass
  }
}
