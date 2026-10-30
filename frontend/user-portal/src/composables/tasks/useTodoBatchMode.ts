import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchCompleteTasks,
  getBatchTaskGroups,
  type BatchTaskAction,
  type BatchTaskGroup,
  type TaskInfo,
} from '@/api/task'
import { getStoredUser } from '@/api/auth'
import type { TodoBatchGroupRef } from '@/composables/tasks/useTodoTasksPage'
import { isActionVisibleForSession } from '@/utils/actionAllowedRoles'
import { BatchCompletionDeliveryUnknown } from '@/api/batchTaskCompletion'
import { useBatchCompletionRecovery } from './useBatchCompletionRecovery'
import { reportBatchCompletion } from './batchCompletionFeedback'
import { pickHttpErrorBodyMessage } from '@/utils/httpErrorMessage'

export type TodoTab = 'all' | 'batch'

/**
 * Allowed Roles hide an action for this session exactly as on the task page (TaskActionBar).
 * A group left with no visible action is not offered at all, so its count and row marks go too.
 */
function visibleToSession(list: BatchTaskGroup[]): BatchTaskGroup[] {
  const user = getStoredUser()
  const roles = user?.roles ?? []
  const systemAdministrator = user?.systemAdministrator === true
  return list
    .map((g) => ({
      ...g,
      actions: g.actions.filter((a) => isActionVisibleForSession(a.configJson, roles, systemAdministrator)),
    }))
    .filter((g) => g.actions.length > 0)
}

/**
 * To Do「可批量操作」: tab + group drill-in live in the route query (?tab=batch&pd=&node=) so
 * Back / refresh return to the same place. The drilled-in task list is the normal To Do grid
 * filtered server-side to the group (see useTodoTasksPage batchGroup).
 */
export function useTodoBatchMode() {
  const route = useRoute()
  const router = useRouter()
  const { t } = useI18n()
  const recovery = useBatchCompletionRecovery()

  const groups = ref<BatchTaskGroup[]>([])
  const groupsLoading = ref(false)
  /** The action whose batch request is in flight; one at a time. */
  const runningActionId = ref<string | null>(null)
  const batchRunning = computed(() => runningActionId.value !== null)

  const activeTab = computed<TodoTab>(() => (route.query.tab === 'batch' ? 'batch' : 'all'))

  const groupRef = computed<TodoBatchGroupRef | null>(() => {
    if (activeTab.value !== 'batch') return null
    const group = typeof route.query.group === 'string' ? route.query.group : ''
    const node = typeof route.query.node === 'string' ? route.query.node : ''
    return group && node ? { groupKey: group, taskDefinitionKey: node } : null
  })

  const activeGroup = computed(() => {
    const wanted = groupRef.value
    if (!wanted) return null
    return groups.value.find((g) =>
      g.groupKey === wanted.groupKey && g.taskDefinitionKey === wanted.taskDefinitionKey) ?? null
  })

  const batchTotal = computed(() => groups.value.reduce((sum, g) => sum + g.count, 0))

  /**
   * The Batch Actions group a To Do row can be processed in. The server flags the row with the
   * same eligibility rule; the group must also be listed (a node offering no approve/reject
   * action has none), so the flag never points at a group the Batch tab does not show.
   */
  function groupOfRow(row: TaskInfo): BatchTaskGroup | undefined {
    if (!row.batchGroupKey) return undefined
    return groups.value.find((g) =>
      g.groupKey === row.batchGroupKey && g.taskDefinitionKey === row.taskDefinitionKey)
  }

  async function loadGroups() {
    groupsLoading.value = true
    try {
      const res = await getBatchTaskGroups()
      groups.value = visibleToSession(res.data ?? [])
    } catch (error) {
      if (!(error as { response?: unknown })?.response) {
        ElMessage.error(error instanceof Error ? error.message : t('task.loadFailed'))
      }
    } finally {
      groupsLoading.value = false
    }
  }

  function switchTab(tab: TodoTab) {
    void router.replace({ query: tab === 'batch' ? { tab: 'batch' } : {} })
  }

  function openGroup(group: BatchTaskGroup) {
    void router.push({ query: { tab: 'batch', group: group.groupKey, node: group.taskDefinitionKey } })
  }

  function backToGroups() {
    void router.push({ query: { tab: 'batch' } })
  }

  /**
   * Asks for the shared comment (requireComment) or a confirmation, then completes the selected
   * tasks one by one on the server. Resolves true when anything ran (caller reloads).
   */
  async function runBatchAction(action: BatchTaskAction, tasks: TaskInfo[]): Promise<boolean> {
    if (tasks.length === 0 || batchRunning.value) return false
    const title = t('task.batchActionTitle', { action: action.actionName, count: tasks.length })
    let comment: string | undefined
    try {
      if (action.requireComment) {
        const { value } = await ElMessageBox.prompt(
          action.confirmMessage || t('task.batchCommentPrompt'),
          title,
          {
            inputType: 'textarea',
            inputPlaceholder: t('task.commentPlaceholder'),
            inputValidator: (v: string) => (v && v.trim() ? true : t('task.commentRequired')),
            confirmButtonText: t('common.confirm'),
            cancelButtonText: t('common.cancel'),
          },
        )
        comment = value.trim()
      } else {
        await ElMessageBox.confirm(
          action.confirmMessage || t('task.batchConfirm', { action: action.actionName, count: tasks.length }),
          title,
          { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') },
        )
      }
    } catch {
      return false
    }

    runningActionId.value = action.actionId
    try {
      const res = await batchCompleteTasks(tasks.map((task) => task.taskId), action.actionId, comment)
      recovery.completed(res.data.succeeded)
      recovery.record(res.data.pending ?? [])
      await reportBatchCompletion(res.data, tasks, title, t)
      return true
    } catch (error) {
      if (error instanceof BatchCompletionDeliveryUnknown) {
        recovery.record(Object.entries(error.operations).map(([taskId, operationId]) => ({ taskId, operationId, state: 'DELIVERY_UNKNOWN' })))
        ElMessage.warning(t('task.assignmentCompletion.batchUnknown'))
      } else ElMessage.error(pickHttpErrorBodyMessage((error as { response?: { data?: unknown } })?.response?.data)
        || (error instanceof Error ? error.message : t('common.error')))
      return true
    } finally {
      runningActionId.value = null
    }
  }

  return {
    pendingCompletions: recovery.rows, refreshBatchCompletion: recovery.refresh,
    groups,
    groupsLoading,
    batchRunning,
    runningActionId,
    activeTab,
    groupRef,
    activeGroup,
    batchTotal,
    groupOfRow,
    loadGroups,
    switchTab,
    openGroup,
    backToGroups,
    runBatchAction,
  }
}
