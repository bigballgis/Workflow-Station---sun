import type { Ref } from 'vue'
import type { Router } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { completeTask, getReturnableActivities } from '@/api/task'
import { processApi } from '@/api/process'
import { resolveRollbackTargetActivityId } from '@/utils/taskReturnTarget'
import type { TaskActionInfo } from '@/api/task'

/**
 * Return-style action handlers (ROLLBACK / DRAFT / WITHDRAW) extracted verbatim
 * from useCustomActions. Built as a factory so the host composable injects its
 * reactive deps; behavior is unchanged. API calls go through the same task /
 * process request wrappers as before.
 */
export function createCustomActionReturnFlows(deps: {
  t: (key: string, named?: Record<string, unknown>) => string
  router: Router
  taskInfo: Ref<Record<string, any>>
  submitting: Ref<boolean>
}) {
  const { t, router, taskInfo, submitting } = deps

  async function handleReturnToActivityAction(
    action: TaskActionInfo,
    targetStep: string,
    completeAction: 'DRAFT' | 'RETURN',
    messages: {
      noTask: string
      noTarget: string
      confirm: (node: string) => string
      confirmTitle: string
      success: string
      failed: string
    },
  ) {
    const taskId =
      (taskInfo.value?.id ?? taskInfo.value?.taskId) as string | undefined
    if (!taskId) {
      ElMessage.error(messages.noTask)
      return
    }
    let config: Record<string, unknown> = {}
    try {
      config = action.configJson ? JSON.parse(action.configJson) : {}
    } catch {
      config = {}
    }

    let comment = ''
    if (config.requireComment === true) {
      try {
        const { value } = await ElMessageBox.prompt(
          t('task.commentPlaceholder'),
          t('task.return'),
          {
            confirmButtonText: t('common.confirm'),
            cancelButtonText: t('common.cancel'),
            inputValidator: (v) =>
              v != null && String(v).trim() !== '' ? true : t('task.commentRequired'),
          },
        )
        comment = String(value).trim()
      } catch {
        return
      }
    }

    submitting.value = true
    try {
      const res = await getReturnableActivities(taskId)
      const activities = res?.data
      const list = Array.isArray(activities) ? activities : []
      const target = resolveRollbackTargetActivityId(targetStep, config, list)
      if (!target) {
        ElMessage.error(messages.noTarget)
        return
      }
      const nodeLabel = target.taskName || target.activityId
      const confirmMsg =
        (typeof config.confirmMessage === 'string' && config.confirmMessage.trim())
          ? String(config.confirmMessage).trim()
          : messages.confirm(nodeLabel)
      try {
        await ElMessageBox.confirm(confirmMsg, messages.confirmTitle, { type: 'warning' })
      } catch {
        return
      }
      await completeTask(taskId, {
        taskId,
        action: completeAction,
        comment,
        returnActivityId: target.activityId,
      })
      ElMessage.success(messages.success)
      await router.push('/tasks')
    } catch (err: unknown) {
      const msg =
        err && typeof err === 'object' && 'message' in err
        && typeof (err as { message: unknown }).message === 'string'
          ? (err as { message: string }).message
          : messages.failed
      ElMessage.error(msg)
    } finally {
      submitting.value = false
    }
  }

  async function handleRollbackAction(action: TaskActionInfo) {
    let config: Record<string, unknown> = {}
    try {
      config = action.configJson ? JSON.parse(action.configJson) : {}
    } catch {
      config = {}
    }
    const targetStep =
      typeof config.targetStep === 'string' && config.targetStep.trim()
        ? config.targetStep
        : 'previous'
    await handleReturnToActivityAction(action, targetStep, 'RETURN', {
      noTask: t('task.rollbackNoTask'),
      noTarget: t('task.rollbackNoTarget'),
      confirm: (node) => t('task.rollbackConfirm', { node }),
      confirmTitle: t('task.rollbackConfirmTitle'),
      success: t('task.rollbackSuccess'),
      failed: t('task.rollbackFailed'),
    })
  }

  async function handleDraftAction(action: TaskActionInfo) {
    await handleReturnToActivityAction(action, 'first', 'DRAFT', {
      noTask: t('task.draftNoTask'),
      noTarget: t('task.draftNoTarget'),
      confirm: (node) => t('task.draftConfirm', { node }),
      confirmTitle: t('task.draftConfirmTitle'),
      success: t('task.draftSuccess'),
      failed: t('task.draftFailed'),
    })
  }

  async function handleWithdrawAction(action: TaskActionInfo) {
    const processId = taskInfo.value?.processInstanceId as string | undefined
    if (!processId) {
      ElMessage.error(t('task.withdrawNoProcess'))
      return
    }
    let config: Record<string, unknown> = {}
    try {
      config = action.configJson ? JSON.parse(action.configJson) : {}
    } catch {
      config = {}
    }
    const confirmMsg =
      (typeof config.confirmMessage === 'string' && config.confirmMessage.trim())
        ? config.confirmMessage
        : t('applicationDetail.withdrawConfirm')
    try {
      await ElMessageBox.confirm(confirmMsg, t('applicationDetail.withdrawConfirmTitle'), {
        type: 'warning',
      })
    } catch {
      return
    }
    let reason =
      (typeof config.defaultReason === 'string' && config.defaultReason.trim())
        ? config.defaultReason
        : t('applicationDetail.userWithdraw')
    if (config.requireComment === true || config.requireReason === true) {
      try {
        const { value } = await ElMessageBox.prompt(
          t('task.commentPlaceholder'),
          t('task.reason'),
          {
            confirmButtonText: t('common.confirm'),
            cancelButtonText: t('common.cancel'),
            inputValidator: (v) => (v != null && String(v).trim() !== '' ? true : t('task.commentRequired')),
          },
        )
        reason = String(value).trim()
      } catch {
        return
      }
    }
    submitting.value = true
    try {
      await processApi.withdrawProcess(processId, reason)
      const successMsg =
        (typeof config.successMessage === 'string' && config.successMessage.trim())
          ? config.successMessage
          : t('applicationDetail.withdrawSuccess')
      ElMessage.success(successMsg)
      await router.push('/tasks')
    } catch (err: unknown) {
      const msg =
        err && typeof err === 'object' && 'message' in err && typeof (err as { message: unknown }).message === 'string'
          ? (err as { message: string }).message
          : t('applicationDetail.withdrawFailed')
      ElMessage.error(msg)
    } finally {
      submitting.value = false
    }
  }

  return { handleReturnToActivityAction, handleRollbackAction, handleDraftAction, handleWithdrawAction }
}
