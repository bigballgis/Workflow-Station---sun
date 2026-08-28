<template>
  <div class="tasks-page">
    <div class="page-header">
      <h1>{{ t('task.title') }}</h1>
    </div>

    <div
      v-loading="loading"
      class="portal-card"
    >
      <TodoListToolbar
        v-model:assignment-types="filterForm.assignmentTypes"
        v-model:keyword="filterForm.keyword"
        @search="handleSearch"
        @reset="handleReset"
      />
      <div
        ref="gridScrollRef"
        class="list-data-grid-scroll"
      >
        <div
          class="list-data-grid-inner"
          :style="gridInnerStyle"
        >
          <el-table
            :data="displayRows"
            stripe
            :fit="false"
            table-layout="fixed"
            style="width: 100%;"
            class="list-data-grid"
            :class="{ 'list-data-grid--fit': gridFits }"
            scrollbar-always-on
            :height="gridTableHeight || '100%'"
            @selection-change="handleSelectionChange"
          >
            <template #empty>
              <div
                v-if="loading"
                class="table-empty-loading"
              >
                <el-icon class="table-empty-loading__icon is-loading">
                  <Loading />
                </el-icon>
                <span>{{ t('common.loading') }}</span>
              </div>
              <span v-else>{{ t('task.noTasks') }}</span>
            </template>
            <!-- Do not set `fixed`: EP's left overlay fills the viewport and hides the data columns. -->
            <el-table-column
              type="selection"
              width="50"
              
            />
            <el-table-column
              v-for="(col, colIndex) in displayColumns"
              :key="col.field"
              :prop="col.field"
              :width="widthOf(col.field)"
              show-overflow-tooltip
            >
              <template #header>
                <ListColumnHeader
                  :column="col"
                  :sort="sort.field === col.field ? sort.direction : null"
                  :filtered="!!columnFilters[col.field]"
                  :width="widthOf(col.field)"
                  :show-move="displayColumns.length > 1"
                  :can-move-left="colIndex > 0"
                  :can-move-right="colIndex < displayColumns.length - 1"
                  @sort-change="(direction: 'ASC' | 'DESC') => onSort(col.field, direction)"
                  @clear-sort="onClearSort"
                  @filter-open="openFilter(col.field)"
                  @clear-filter="onClearFilter(col.field)"
                  @move="(direction: 'left' | 'right') => moveColumn(col.field, direction)"
                  @width-change="(width: number) => setWidth(col.field, width)"
                  @width-commit="persistWidths"
                />
              </template>
              <template #default="{ row }">
                <el-link
                  v-if="col.field === 'requestId'"
                  type="primary"
                  @click="viewTask(row)"
                >
                  {{ row.requestId || '-' }}
                </el-link>
                <span
                  v-else-if="col.field === 'functionUnitCode'"
                >
                  {{ row.functionUnitName || row.functionUnitCode || '-' }}
                </span>
                <span
                  v-else-if="col.field === 'assignmentType'"
                  class="assignment-type"
                  :class="getAssignmentClass(row)"
                >
                  {{ t(`task.${getAssignmentKey(row)}`) }}
                </span>
                <span
                  v-else-if="col.field === 'priority'"
                  class="priority"
                  :class="getPriorityClass(row.priority)"
                >
                  {{ getPriorityLabel(row.priority) }}
                </span>
                <span
                  v-else-if="col.field === 'createTime' || col.field === 'dueDate'"
                  style="white-space: nowrap;"
                  :class="{ overdue: col.field === 'dueDate' && row.isOverdue }"
                >
                  {{ formatDate(row[col.field]) }}
                  <el-tag
                    v-if="col.field === 'dueDate' && row.isOverdue"
                    type="danger"
                    size="small"
                    style="margin-left: 4px;"
                  >
                    {{ t('task.overdue') }}
                  </el-tag>
                </span>
                <template v-else>
                  {{ row[col.field as keyof TaskInfo] || '-' }}
                </template>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div
        v-if="selectedTasks.length > 0"
        class="batch-actions"
      >
        <span>{{ t('task.selected', { count: selectedTasks.length }) }}</span>
        <el-button
          type="warning"
          size="small"
          @click="handleBatchUrge"
        >
          {{ t('task.batchUrge') }}
        </el-button>
      </div>

      <ListPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        :loading="loading"
        @change="loadTasks"
      />
    </div>

    <ListFilterDialog
      v-model:visible="filterDialog.visible"
      :column="activeFilterColumn"
      :filter="activeFilter"
      @apply="onFilterApply"
      @clear="onFilterClear"
    />

    <el-dialog
      v-model="actionDialogVisible"
      :title="actionDialogTitle"
      width="500px"
      class="task-action-form"
    >
      <el-form
        :model="actionForm"
        label-width="100px"
      >
        <el-form-item
          v-if="currentAction !== 'urge' && currentAction !== 'batchUrge'"
          :label="t('task.targetUser')"
          required
        >
          <el-input
            v-model="actionForm.targetUserId"
            :placeholder="t('task.enterUserId')"
          />
        </el-form-item>
        <el-form-item :label="t('common.reason')">
          <el-input
            v-model="actionForm.reason"
            type="textarea"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          @click="submitAction"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import TodoListToolbar from './TodoListToolbar.vue'
import ListColumnHeader from '@platform-shared/list/ListColumnHeader.vue'
import ListFilterDialog from '@platform-shared/list/ListFilterDialog.vue'
import ListPagination from '@platform-shared/list/ListPagination.vue'
import type { ListColumnFilter } from '@platform-shared/list/columnMeta'
import { queryTodoTasks, batchUrgeTasks, type TaskInfo, type TodoTaskQueryRequest } from '@/api/task'
import { usePortalListGrid } from '@/composables/list/usePortalListGrid'
import { formatDate } from '@/utils/dateFormat'
import { taskPriorityBand, taskPriorityCssClass } from '@/utils/taskPriority'
import { usePendingTaskStore } from '@/stores/pendingTask'


const pendingTaskStore = usePendingTaskStore()
const { t } = useI18n()
const router = useRouter()
const loading = ref(true)
const selectedTasks = ref<TaskInfo[]>([])
const filterForm = reactive({
  assignmentTypes: [] as string[],
  keyword: '',
})

/** Temporarily hide Process Name / Initiator / Priority / Due Date. Keep in sync with TodoTaskColumnSpec.VISIBLE_FIELDS. */
const TODO_VISIBLE_FIELDS = [
  'requestId',
  'functionUnitCode',
  'taskName',
  'assignmentType',
  'createTime',
] as const

const {
  displayColumns,
  displayRows,
  columnFilters,
  sort,
  filterDialog,
  pagination,
  activeFilterColumn,
  activeFilter,
  gridScrollRef,
  gridFits,
  gridTableHeight,
  gridInnerStyle,
  widthOf,
  setWidth,
  persistWidths,
  beginQuery,
  isCurrentQuery,
  applyPage,
  buildQuery,
  moveColumn,
  openFilter,
  applyFilter,
  clearFilter,
  applySort,
  clearSort,
} = usePortalListGrid<TaskInfo>({
  storageKey: 'portal-list-layout:todo-tasks-v2',
  extraWidth: 50,
  visibleFields: TODO_VISIBLE_FIELDS,
})

const actionDialogVisible = ref(false)
const actionDialogTitle = ref('')
const currentAction = ref('')
const actionForm = reactive({
  targetUserId: '',
  reason: '',
})

const loadTasks = async () => {
  const seq = beginQuery()
  loading.value = true
  try {
    const res = await queryTodoTasks(todoQueryBody())
    if (!isCurrentQuery(seq)) return
    applyPage(res.data, 'todo/query response is missing its column declaration')
    pendingTaskStore.syncCountFromListTotal(res.data.totalElements)
  } catch (error) {
    if (!isCurrentQuery(seq)) return
    if (!(error as { response?: unknown })?.response) {
      ElMessage.error(error instanceof Error ? error.message : t('task.loadFailed'))
    }
  } finally {
    if (isCurrentQuery(seq)) loading.value = false
  }
}

function todoQueryBody(): TodoTaskQueryRequest {
  const body: TodoTaskQueryRequest = { ...buildQuery() }
  const keyword = filterForm.keyword.trim()
  if (keyword) body.keyword = keyword
  if (filterForm.assignmentTypes.length > 0) body.assignmentTypes = filterForm.assignmentTypes
  return body
}

function handleSearch() {
  pagination.page = 1
  loadTasks()
}

function handleReset() {
  filterForm.assignmentTypes = []
  filterForm.keyword = ''
  for (const field of Object.keys(columnFilters.value)) {
    clearFilter(field)
  }
  clearSort()
  handleSearch()
}

function onSort(field: string, direction: 'ASC' | 'DESC') {
  applySort(field, direction)
  loadTasks()
}

function onClearSort() {
  clearSort()
  loadTasks()
}


function onClearFilter(field: string) {
  clearFilter(field)
  loadTasks()
}

function onFilterApply(filter: ListColumnFilter) {
  applyFilter(filter)
  loadTasks()
}

function onFilterClear() {
  onClearFilter(filterDialog.field)
}

const handleSelectionChange = (selection: TaskInfo[]) => {
  selectedTasks.value = selection
}

const viewTask = (task: TaskInfo) => {
  router.push(`/tasks/${task.taskId}`)
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

const getAssignmentKey = (task: TaskInfo) => {
  const bpmn = task.bpmnAssigneeType?.trim().toUpperCase()
  if (bpmn === 'INITIATOR' || bpmn === 'PROCESS_INITIATOR') {
    return 'processInitiator'
  }
  if (bpmn === 'FIXED_BU_ROLE') {
    return 'fixedBuRole'
  }
  if (bpmn === 'BU_ROLE') {
    return 'buRole'
  }
  const type = task.assignmentType
  const map: Record<string, string> = {
    USER: 'user',
    VIRTUAL_GROUP: 'virtualGroup',
    DEPT_ROLE: 'deptRole',
    DELEGATED: 'delegated',
    CANDIDATE_USERS: 'candidateUsers',
  }
  return map[type] || 'user'
}

const getAssignmentClass = (task: TaskInfo) => {
  const key = getAssignmentKey(task)
  const map: Record<string, string> = {
    user: 'user',
    processInitiator: 'user',
    virtualGroup: 'virtual-group',
    deptRole: 'dept-role',
    delegated: 'delegated',
    candidateUsers: 'virtual-group',
    fixedBuRole: 'virtual-group',
    buRole: 'virtual-group',
  }
  return map[key] || 'user'
}

const getPriorityLabel = (priority: string | number | undefined): string => {
  return t(`task.${taskPriorityBand(priority).toLowerCase()}`)
}

const getPriorityClass = (priority: string | number | undefined): string => {
  return taskPriorityCssClass(priority)
}

onMounted(() => {
  loadTasks()
})
</script>

<style lang="scss">
@import '@/styles/listDataGrid.scss';
</style>

<style lang="scss" scoped>
.table-empty-loading {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  padding: 24px 0;

  &__icon {
    font-size: 18px;
  }
}

.tasks-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;

  :deep(.portal-card) {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .page-header {
    margin-bottom: 20px;
    flex-shrink: 0;

    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }

  .batch-actions {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-top: 12px;
    padding: 12px 16px;
    background: #f5f7fa;
    border-radius: 4px;
    flex-shrink: 0;

    span {
      color: var(--text-secondary);
    }
  }

  .overdue {
    color: var(--error-red);
  }
}
</style>

<style lang="scss">
.task-action-form .el-form-item__label {
  white-space: nowrap;
}
</style>
