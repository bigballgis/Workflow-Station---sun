import { type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { claimTask, unclaimTask } from '@/api/task'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'

/**
 * Claim / Unclaim for BU Role pool tasks. Shared by Tasks to Claim and task detail.
 * Callers pass skipGlobalErrorHandler APIs so this composable owns the toast.
 */
export function useTaskClaimActions(options: {
  reload: () => Promise<void>
  actingTaskId?: Ref<string | null>
  submitting?: Ref<boolean>
}) {
  const { t } = useI18n()

  async function run(taskId: string, action: () => Promise<unknown>, successKey: string): Promise<void> {
    if (options.actingTaskId) {
      options.actingTaskId.value = taskId
    }
    if (options.submitting) {
      options.submitting.value = true
    }
    try {
      await action()
      ElMessage.success(t(successKey))
      await options.reload()
    } catch (error) {
      ElMessage.error(resolveUserFacingHttpMessage(error, (key) => t(key)))
      await options.reload()
    } finally {
      if (options.actingTaskId) {
        options.actingTaskId.value = null
      }
      if (options.submitting) {
        options.submitting.value = false
      }
    }
  }

  function claim(taskId: string): Promise<void> {
    if (typeof taskId !== 'string' || !taskId.trim()) {
      ElMessage.error(t('task.notFound'))
      return Promise.resolve()
    }
    return run(taskId, () => claimTask(taskId), 'task.claimSuccess')
  }

  function unclaim(taskId: string, assignmentType: string, assignee: string): Promise<void> {
    return run(
      taskId,
      () => unclaimTask(taskId, assignmentType, assignee),
      'task.unclaimSuccess',
    )
  }

  async function forceUnclaim(
    taskId: string,
    assignmentType: string,
    assignee: string,
    holderName?: string,
  ): Promise<void> {
    try {
      await ElMessageBox.confirm(
        t('task.forceUnclaimConfirm', { user: holderName || assignee || '-' }),
        t('task.forceUnclaim'),
        { type: 'warning' },
      )
    } catch {
      return
    }
    return run(
      taskId,
      () => unclaimTask(taskId, assignmentType, assignee),
      'task.forceUnclaimSuccess',
    )
  }

  return { claim, unclaim, forceUnclaim }
}
