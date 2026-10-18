import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { ListColumnFilter } from '@platform-shared/list/columnMeta'
import { queryTodoTasks, batchUrgeTasks, type TaskInfo, type TodoTaskQueryRequest } from '@/api/task'
import { usePortalListGrid } from '@/composables/list/usePortalListGrid'
import { useTaskClaimActions } from '@/composables/tasks/useTaskClaimActions'
import {
  readTodoToolbarQuery,
  writeTodoToolbarQuery,
} from '@/composables/tasks/todoToolbarQuery'
import { useUserPreferenceStore } from '@/stores/userPreference'
import { taskPriorityBand, taskPriorityCssClass } from '@/utils/taskPriority'
import { usePendingTaskStore } from '@/stores/pendingTask'

export const CLAIM_ACTION_WIDTH = 224
/** Uppercase "Current Assignee" is wider than the TEXT header-fit floor (168). */
const CURRENT_ASSIGNEE_COL_WIDTH = 220

const TODO_VISIBLE_FIELDS = [
  'requestId',
  'functionUnitCode',
  'taskName',
  'assignmentType',
  'assigneeName',
  'createTime',
] as const

/**
 * Inside one「可批量操作」group these are the same for every row (one node of one process
 * version, all handled by me), so the page shows them once in the group header instead.
 */
export const BATCH_GROUP_COMMON_FIELDS: readonly string[] = [
  'functionUnitCode',
  'taskName',
  'assignmentType',
  'assigneeName',
]
const BATCH_GROUP_VISIBLE_FIELDS = TODO_VISIBLE_FIELDS.filter(
  (field) => !BATCH_GROUP_COMMON_FIELDS.includes(field),
)

export interface TodoBatchGroupRef {
  groupKey: string
  taskDefinitionKey: string
}

/**
 * @param batchGroup when it returns a group, the same grid lists only that「可批量操作」group's tasks
 *   (To Do columns minus BATCH_GROUP_COMMON_FIELDS, no claim column); the server applies the
 *   batch eligibility filter. The group view keeps its own widths/order: a width dragged for the
 *   six To Do columns would otherwise pin the group's two columns and push the slack elsewhere.
 */
export function useTodoTasksPage(batchGroup: () => TodoBatchGroupRef | null = () => null) {
  const pendingTaskStore = usePendingTaskStore()
  const preferenceStore = useUserPreferenceStore()
  const { t } = useI18n()
  const router = useRouter()
  const loading = ref(true)
  const selectedTasks = ref<TaskInfo[]>([])
  const storedToolbarQuery = readTodoToolbarQuery()
  const filterForm = reactive({
    assignmentTypes: storedToolbarQuery.assignmentTypes,
    priorities: storedToolbarQuery.priorities,
    keyword: storedToolbarQuery.keyword,
  })

  const grid = usePortalListGrid<TaskInfo>({
    storageKey: () => (batchGroup() ? 'portal-list-layout:todo-batch-group-v1' : 'portal-list-layout:todo-tasks-v4'),
    extraWidth: () => 50 + (batchGroup() ? 0 : CLAIM_ACTION_WIDTH),
    visibleFields: () => (batchGroup() ? BATCH_GROUP_VISIBLE_FIELDS : TODO_VISIBLE_FIELDS),
    defaultWidthOf: (field) => (field === 'assigneeName' ? CURRENT_ASSIGNEE_COL_WIDTH : undefined),
  })

  const actionDialogVisible = ref(false)
  const actionDialogTitle = ref('')
  const currentAction = ref('')
  const actionForm = reactive({
    targetUserId: '',
    reason: '',
  })
  const actingTaskId = ref<string | null>(null)
  const claimAllBusy = ref(false)

  const loadTasks = async () => {
    const seq = grid.beginQuery()
    loading.value = true
    try {
      const res = await queryTodoTasks(todoQueryBody())
      if (!grid.isCurrentQuery(seq)) return
      grid.applyPage(res.data, 'todo/query response is missing its column declaration')
      // A batch group total is a subset; only the full To Do total may drive the menu badge.
      if (!batchGroup()) pendingTaskStore.syncCountFromListTotal(res.data.totalElements)
    } catch (error) {
      if (!grid.isCurrentQuery(seq)) return
      if (!(error as { response?: unknown })?.response) {
        ElMessage.error(error instanceof Error ? error.message : t('task.loadFailed'))
      }
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
    }
  }

  const { claim, unclaim, forceUnclaim, reassign, claimAll, unclaimAll, claimSelected, unclaimSelected, prepareTodoOpen } =
    useTaskClaimActions({
      reload: loadTasks,
      actingTaskId,
      submitting: claimAllBusy,
    })

  function todoQueryBody(): TodoTaskQueryRequest {
    const body: TodoTaskQueryRequest = { ...grid.buildQuery() }
    const keyword = filterForm.keyword.trim()
    if (keyword) body.keyword = keyword
    if (filterForm.priorities.length > 0) body.priorities = filterForm.priorities
    const group = batchGroup()
    if (group) {
      body.batchGroupKey = group.groupKey
      body.batchTaskDefinitionKey = group.taskDefinitionKey
    } else if (filterForm.assignmentTypes.length > 0) {
      // A group has one assignment type, so its toolbar hides this filter; a value kept
      // from the All To Do tab must not silently empty the group.
      body.assignmentTypes = filterForm.assignmentTypes
    }
    return body
  }

  function persistToolbarQuery() {
    writeTodoToolbarQuery({
      keyword: filterForm.keyword,
      assignmentTypes: filterForm.assignmentTypes,
      priorities: filterForm.priorities,
    })
  }

  function handleSearch() {
    grid.pagination.page = 1
    persistToolbarQuery()
    loadTasks()
  }

  function handleReset() {
    filterForm.assignmentTypes = []
    filterForm.priorities = []
    filterForm.keyword = ''
    for (const field of Object.keys(grid.columnFilters.value)) {
      grid.clearFilter(field)
    }
    grid.clearSort()
    handleSearch()
  }

  function onSort(field: string, direction: 'ASC' | 'DESC') {
    grid.applySort(field, direction)
    loadTasks()
  }

  function onClearSort() {
    grid.clearSort()
    loadTasks()
  }

  function onClearFilter(field: string) {
    grid.clearFilter(field)
    loadTasks()
  }

  function onFilterApply(filter: ListColumnFilter) {
    grid.applyFilter(filter)
    loadTasks()
  }

  function onFilterClear() {
    onClearFilter(grid.filterDialog.field)
  }

  const handleSelectionChange = (selection: TaskInfo[]) => {
    selectedTasks.value = selection
    grid.handleGridSelectionChange(selection)
  }

  const selectedClaimableIds = computed(() =>
    selectedTasks.value.flatMap((task) => (task.claimable && task.taskId ? [task.taskId] : [])),
  )

  const selectedHeldIds = computed(() =>
    selectedTasks.value.flatMap((task) =>
      task.claimedByCurrentUser && task.taskId ? [task.taskId] : []),
  )

  function handleClaimSelected() {
    return claimSelected(selectedClaimableIds.value, selectedTasks.value.length)
  }

  function handleUnclaimSelected() {
    return unclaimSelected(selectedHeldIds.value, selectedTasks.value.length)
  }

  const viewTask = async (task: TaskInfo) => {
    persistToolbarQuery()
    await preferenceStore.load()
    try {
      await prepareTodoOpen(task, preferenceStore.autoClaimOnOpen)
    } catch {
      return
    }
    router.push(`/tasks/${task.taskId}`)
  }

  function handleClaim(task: TaskInfo) {
    return claim(task.taskId)
  }

  function handleUnclaim(task: TaskInfo) {
    return unclaim(task.taskId, task.assignmentType, task.assignee)
  }

  function handleForceUnclaim(task: TaskInfo) {
    return forceUnclaim(task.taskId, task.assignmentType, task.assignee, task.assigneeName)
  }

  const reassignDialogVisible = ref(false)
  const reassignTask = ref<TaskInfo | null>(null)

  function handleReassign(task: TaskInfo) {
    reassignTask.value = task
    reassignDialogVisible.value = true
  }

  function confirmReassign(targetUserId: string) {
    const task = reassignTask.value
    if (!task?.taskId) {
      return Promise.resolve()
    }
    return reassign(task.taskId, targetUserId).then(() => {
      reassignDialogVisible.value = false
      reassignTask.value = null
    })
  }

  function onAutoClaimChange(value: string | number | boolean) {
    return preferenceStore.setAutoClaimOnOpen(value === true)
  }

  function onAutoPreviewChange(value: string | number | boolean) {
    return preferenceStore.setAutoPreviewOnOpen(value === true)
  }

  const handleBatchUrge = () => {
    currentAction.value = 'batchUrge'
    actionDialogTitle.value = t('task.batchUrge')
    actionForm.reason = ''
    actionDialogVisible.value = true
  }

  const submitAction = async () => {
    try {
      if (currentAction.value === 'batchUrge') {
        const taskIds = selectedTasks.value.map((task) => task.taskId)
        await batchUrgeTasks(taskIds, actionForm.reason)
        ElMessage.success(t('common.success'))
      }
      actionDialogVisible.value = false
      loadTasks()
    } catch (error) {
      const msg =
        (error as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message
        ?? (error as { message?: string })?.message
        ?? t('common.error')
      ElMessage.error(msg)
    }
  }

  const getPriorityLabel = (priority: string | number | undefined): string => {
    return t(`task.${taskPriorityBand(priority).toLowerCase()}`)
  }

  const getPriorityClass = (priority: string | number | undefined): string => {
    return taskPriorityCssClass(priority)
  }

  let skipActivateReload = true
  onMounted(() => {
    void preferenceStore.load()
    loadTasks()
  })
  onActivated(() => {
    if (skipActivateReload) {
      skipActivateReload = false
      return
    }
    loadTasks()
  })

  return {
    t,
    preferenceStore,
    loading,
    selectedTasks,
    selectedClaimableIds,
    selectedHeldIds,
    filterForm,
    ...grid,
    actionDialogVisible,
    actionDialogTitle,
    actionForm,
    actingTaskId,
    claimAllBusy,
    loadTasks,
    handleSearch,
    handleReset,
    onSort,
    onClearSort,
    onClearFilter,
    onFilterApply,
    onFilterClear,
    handleSelectionChange,
    viewTask,
    handleClaim,
    handleUnclaim,
    handleForceUnclaim,
    handleReassign,
    confirmReassign,
    reassignDialogVisible,
    reassignTask,
    handleClaimAll: claimAll,
    handleUnclaimAll: unclaimAll,
    handleClaimSelected,
    handleUnclaimSelected,
    onAutoClaimChange,
    onAutoPreviewChange,
    handleBatchUrge,
    submitAction,
    getPriorityLabel,
    getPriorityClass,
  }
}
