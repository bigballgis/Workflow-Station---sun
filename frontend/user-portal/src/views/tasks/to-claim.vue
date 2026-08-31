<template>
  <div class="tasks-page">
    <div class="page-header">
      <h1 class="page-title-row">
        <span>{{ t('task.toClaimTitle') }}</span>
        <PortalHelpLink
          path="/up-tasks-to-claim"
          :aria-label="t('task.toClaimGuideLinkAria')"
          test-id="to-claim-guide-link"
        />
      </h1>
      <p class="page-subtitle">
        {{ t('task.toClaimHint') }}
      </p>
    </div>

    <div
      v-loading="loading"
      class="portal-card"
    >
      <TodoListToolbar
        v-model:assignment-types="filterForm.assignmentTypes"
        v-model:priorities="filterForm.priorities"
        v-model:keyword="filterForm.keyword"
        :show-assignment-types="false"
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
            :span-method="spanMethod(1 + (leftoverWidth > 0 ? 1 : 0))"
            :row-class-name="rowClassName"
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
              <span v-else>{{ t('task.noTasksToClaim') }}</span>
            </template>
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
                  :grouped="groupBy === col.field"
                  :filtered="!!columnFilters[col.field]"
                  :width="widthOf(col.field)"
                  :show-move="displayColumns.length > 1"
                  :can-move-left="colIndex > 0"
                  :can-move-right="colIndex < displayColumns.length - 1"
                  @sort-change="(direction: 'ASC' | 'DESC') => onSort(col.field, direction)"
                  @clear-sort="onClearSort"
                  @group-change="(grouped: boolean) => onGroup(col.field, grouped)"
                  @filter-open="openFilter(col.field)"
                  @clear-filter="onClearFilter(col.field)"
                  @move="(direction: 'left' | 'right') => moveColumn(col.field, direction)"
                  @width-change="(width: number) => setWidth(col.field, width)"
                  @width-commit="persistWidths"
                />
              </template>
              <template #default="{ row }">
                <template v-if="isListGroupHeaderRow(row)">
                  <div class="group-header-cell">
                    <strong>{{ groupHeaderLabel(row._groupLabel) }}</strong>
                    <span class="group-count">({{ row._groupCount }})</span>
                  </div>
                </template>
                <el-link
                  v-else-if="col.field === 'requestId'"
                  type="primary"
                  @click="viewTask(row)"
                >
                  {{ row.requestId || '-' }}
                </el-link>
                <template v-else-if="col.field === 'currentStepName'">
                  {{ row.currentStepName || row.taskName || '-' }}
                </template>
                <template v-else-if="col.field === 'assigneeName'">
                  <el-tag
                    v-if="row.assignee"
                    :type="row.claimedByCurrentUser ? 'success' : 'info'"
                    size="small"
                    data-test="to-claim-claimed-by"
                  >
                    {{ row.claimedByCurrentUser ? t('task.claimedByMe') : (row.assigneeName || row.assignee) }}
                  </el-tag>
                  <span v-else>-</span>
                </template>
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
            <el-table-column
              :width="CLAIM_ACTION_WIDTH"
              class-name="to-claim-action-col"
            >
              <template #header>
                <span class="to-claim-action-header">{{ t('task.action') }}</span>
              </template>
              <template #default="{ row }">
                <template v-if="!isListGroupHeaderRow(row)">
                  <el-button
                    v-if="row.claimable"
                    type="primary"
                    size="small"
                    :loading="actingTaskId === row.taskId"
                    data-test="to-claim-claim-btn"
                    @click="handleClaim(row)"
                  >
                    {{ t('task.claim') }}
                  </el-button>
                  <el-button
                    v-else-if="row.claimedByCurrentUser"
                    size="small"
                    :loading="actingTaskId === row.taskId"
                    data-test="to-claim-unclaim-btn"
                    @click="handleUnclaim(row)"
                  >
                    {{ t('task.unclaim') }}
                  </el-button>
                  <el-button
                    v-else-if="row.canForceUnclaim"
                    type="warning"
                    size="small"
                    :loading="actingTaskId === row.taskId"
                    data-test="to-claim-force-unclaim-btn"
                    @click="handleForceUnclaim(row)"
                  >
                    {{ t('task.forceUnclaim') }}
                  </el-button>
                  <span
                    v-else
                    class="to-claim-locked"
                    data-test="to-claim-locked"
                  >{{ t('task.heldByOther') }}</span>
                </template>
              </template>
            </el-table-column>
            <el-table-column
              v-if="leftoverWidth > 0"
              :width="leftoverWidth"
              class-name="list-col-spacer"
            />
          </el-table>
        </div>
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
import { queryToClaimTasks, type TaskInfo, type TodoTaskQueryRequest } from '@/api/task'
import { usePortalListGrid } from '@/composables/list/usePortalListGrid'
import { useTaskClaimActions } from '@/composables/tasks/useTaskClaimActions'
import { formatDate } from '@/utils/dateFormat'
import { taskPriorityBand, taskPriorityCssClass } from '@/utils/taskPriority'
import { usePendingTaskStore } from '@/stores/pendingTask'
import PortalHelpLink from '@/components/PortalHelpLink.vue'

const CLAIM_ACTION_WIDTH = 180

const TO_CLAIM_COL_WIDTHS: Record<string, number> = {
  requestId: 140,
  taskName: 160,
  currentStepName: 160,
  processDefinitionName: 160,
  initiatorName: 120,
  assigneeName: 140,
  priority: 100,
  createTime: 170,
  dueDate: 180,
}

const pendingTaskStore = usePendingTaskStore()
const { t } = useI18n()
const router = useRouter()
const loading = ref(true)
const actingTaskId = ref<string | null>(null)
const filterForm = reactive({
  assignmentTypes: [] as string[],
  priorities: [] as string[],
  keyword: '',
})

const {
  displayColumns,
  displayRows,
  groupBy,
  columnFilters,
  sort,
  filterDialog,
  pagination,
  activeFilterColumn,
  activeFilter,
  gridScrollRef,
  gridFits, leftoverWidth,
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
  applyGroup,
  rowClassName,
  spanMethod,
  groupHeaderLabel,
  isListGroupHeaderRow,
} = usePortalListGrid<TaskInfo>({
  storageKey: 'portal-list-layout:to-claim-tasks',
  extraWidth: CLAIM_ACTION_WIDTH,
  defaultWidthOf: (field) => TO_CLAIM_COL_WIDTHS[field] ?? 120,
})

const loadTasks = async () => {
  const seq = beginQuery()
  loading.value = true
  try {
    const res = await queryToClaimTasks(toClaimQueryBody())
    if (!isCurrentQuery(seq)) return
    applyPage(res.data, 'to-claim/query response is missing its column declaration')
  } catch (error) {
    if (!isCurrentQuery(seq)) return
    if (!(error as { response?: unknown })?.response) {
      ElMessage.error(error instanceof Error ? error.message : t('task.loadFailed'))
    }
  } finally {
    if (isCurrentQuery(seq)) loading.value = false
  }
}

const { claim, unclaim, forceUnclaim } = useTaskClaimActions({
  reload: async () => {
    await Promise.all([loadTasks(), pendingTaskStore.fetchPendingCount()])
  },
  actingTaskId,
})

function toClaimQueryBody(): TodoTaskQueryRequest {
  const body: TodoTaskQueryRequest = { ...buildQuery() }
  const keyword = filterForm.keyword.trim()
  if (keyword) body.keyword = keyword
  if (filterForm.priorities.length > 0) body.priorities = filterForm.priorities
  return body
}

function handleSearch() {
  pagination.page = 1
  loadTasks()
}

function handleReset() {
  filterForm.priorities = []
  filterForm.keyword = ''
  for (const field of Object.keys(columnFilters.value)) {
    clearFilter(field)
  }
  clearSort()
  if (groupBy.value) {
    applyGroup(groupBy.value, false)
  }
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

function onGroup(field: string, grouped: boolean) {
  applyGroup(field, grouped)
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

const viewTask = (task: TaskInfo) => {
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

    .page-title-row {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .page-subtitle {
      margin: 6px 0 0;
      font-size: 13px;
      color: var(--text-secondary);
    }
  }

  .to-claim-action-header {
    font-weight: 500;
  }

  .to-claim-locked {
    color: var(--text-secondary);
    font-size: 12px;
  }

  .overdue {
    color: var(--error-red);
  }
}
</style>
