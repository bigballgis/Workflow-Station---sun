<template>
  <div class="completed-tasks-page">
    <div class="page-header">
      <h1>{{ t('task.completedTasks') }}</h1>
    </div>

    <el-tabs
      v-model="listView"
      class="task-list-tabs"
      @tab-change="onListViewChange"
    >
      <el-tab-pane name="mine">
        <template #label>
          <span>{{ t('task.tab.mine') }}</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="acting">
        <template #label>
          <span class="acting-tab-label">
            {{ t('task.tab.actingForOthers') }}
            <el-badge
              v-if="actingCount > 0"
              :value="actingCount"
              :max="99"
              class="acting-tab-badge"
            />
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 筛选条件 -->
    <div class="portal-card filter-card">
      <el-form
        :inline="true"
        :model="filterForm"
      >
        <el-form-item :label="t('task.processName')">
          <el-input
            v-model="filterForm.keyword"
            :placeholder="t('common.search')"
            clearable
            style="width: 200px;"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item :label="t('task.completedTime')">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            :start-placeholder="t('common.startDate')"
            :end-placeholder="t('common.endDate')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 260px;"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            @click="handleSearch"
          >
            {{ t('common.search') }}
          </el-button>
          <el-button @click="handleReset">
            {{ t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 任务列表：v-loading overlay while paging (Views parity) -->
    <div class="portal-card">
      <el-table
        v-loading="loading"
        class="portal-list-grid"
        :data="displayTaskRows"
        stripe
        table-layout="fixed"
        :span-method="taskSpanMethod"
        :row-class-name="groupRowClassName"
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
          <span v-else>{{ listView === 'acting' ? t('task.tab.noActing') : t('task.noCompletedTasks') }}</span>
        </template>
        <el-table-column
          v-for="(field, idx) in orderedTaskFields"
          :key="field"
          :prop="field"
          :width="colWidth(field, taskWidthFallback(field))"
          show-overflow-tooltip
        >
          <template #header>
            <PortalListColumnHeader
              :label="taskColumnLabel(field)"
              :width="colWidth(field, taskWidthFallback(field))"
              :has-filter="hasFilter(field)"
              :sort-direction="sortDirection(field)"
              :is-grouped="isGrouped(field)"
              :can-move-left="canMoveLeft(field)"
              :can-move-right="canMoveRight(field)"
              :date-like="field === 'createTime' || field === 'completedTime'"
              @sort-asc="onSort(field, 'ASC')"
              @sort-desc="onSort(field, 'DESC')"
              @group-by="onGroup(field)"
              @filter="openFilter(field, taskColumnLabel(field))"
              @clear-filter="onClearColumnFilter(field)"
              @move-left="moveLeft(field)"
              @move-right="moveRight(field)"
              @resize="(w) => onColResize(field, w)"
              @resize-end="onColResizeEnd"
            />
          </template>
          <template #default="{ row }">
            <template v-if="isPortalListGroupHeader(row)">
              <div
                v-if="idx === 0"
                class="group-header-cell"
              >
                <strong>{{ row._groupLabel }}</strong>
                <span class="group-count">({{ row._groupCount }})</span>
              </div>
            </template>
            <template v-else-if="field === 'requestId'">
              <el-link
                type="primary"
                @click="viewTask(row)"
              >
                {{ row.requestId || '-' }}
              </el-link>
            </template>
            <template v-else-if="field === 'taskName'">
              {{ row.taskName }}
            </template>
            <template v-else-if="field === 'currentStepName'">
              {{ row.currentStepName || row.taskName || '-' }}
            </template>
            <template v-else-if="field === 'processDefinitionName'">
              {{ row.processDefinitionName }}
            </template>
            <template v-else-if="field === 'delegatorId'">
              {{ t('task.actingFor', { name: row.delegatorName || row.delegatorId || '-' }) }}
            </template>
            <template v-else-if="field === 'action'">
              <span
                v-if="row.action === 'ACTED_BY_PROXY'"
                style="white-space: nowrap;"
              >
                {{ t('task.actedByProxy', { name: row.delegatorName || row.delegatorId || '-' }) }}
              </span>
              <el-tag
                v-else-if="!row.multiInstanceSubTask"
                :type="getActionTagType(row.action)"
                size="small"
                style="white-space: nowrap;"
              >
                {{ t(`action.${row.action || 'completed'}`) }}
              </el-tag>
              <span v-else>-</span>
            </template>
            <template v-else-if="field === 'createTime'">
              <span style="white-space: nowrap;">{{ formatDate(row.createTime) }}</span>
            </template>
            <template v-else-if="field === 'completedTime'">
              <span style="white-space: nowrap;">{{ formatDate(row.completedTime) }}</span>
            </template>
            <template v-else-if="field === 'durationInMillis'">
              <span style="white-space: nowrap;">{{ formatDuration(row.durationInMillis) }}</span>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <PortalListPagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :disabled="loading"
        :total="pagination.total"
        @change="loadTasks"
      />

      <PortalListFilterDialog
        v-model="filterDialogVisible"
        :title="filterDialogField
          ? `${t('mainTableView.colFilterBy')}: ${filterDialogField.label}`
          : t('mainTableView.colFilterBy')"
        :initial="filterDialogField
          ? colState.filters[filterDialogField.field]
          : null"
        @apply="onApplyColumnFilter"
        @clear="onClearColumnFilter()"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, Loading } from '@element-plus/icons-vue'
import { queryCompletedTasks, TaskInfo } from '@/api/task'
import { formatDate } from '@/utils/dateFormat'
import PortalListPagination from '@/components/portal-list/PortalListPagination.vue'
import PortalListColumnHeader from '@/components/portal-list/PortalListColumnHeader.vue'
import PortalListFilterDialog from '@/components/portal-list/PortalListFilterDialog.vue'
import { usePortalListColumnState } from '@/composables/usePortalListColumnState'
import { PORTAL_LIST_DEFAULT_PAGE_SIZE } from '@/constants/portalListPagination'
import {
  applyGroupHeaders,
  isPortalListGroupHeader,
  portalListGroupSpanMethod,
  type PortalListColumnFilter,
  type PortalListSortDirection,
} from '@/utils/portalListGridRuntime'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const cols = usePortalListColumnState('tasks-completed')
const {
  state: colState,
  filterDialogVisible,
  filterDialogField,
  width: colWidth,
  onResize: onColResize,
  onResizeEnd: onColResizeEnd,
  toggleSort,
  toggleGroup,
  moveLeft,
  moveRight,
  canMoveLeft,
  canMoveRight,
  ensureOrder,
  orderedColumnFields,
  openFilter,
  applyFilter,
  clearFilter,
  hasFilter,
  sortDirection,
  isGrouped,
  activeFilters,
} = cols

/** Completed history path; sortBy forwarded when present (unknown fields may be ignored server-side). */
const SORT_FIELD_MAP: Record<string, string> = {
  requestId: 'requestId',
  taskName: 'taskName',
  currentStepName: 'taskName',
  processDefinitionName: 'processDefinitionName',
  delegatorId: 'delegatorId',
  action: 'action',
  createTime: 'createTime',
  completedTime: 'completedTime',
  durationInMillis: 'durationInMillis',
}

/** FE column → API filter field (TaskQueryColumnFilters whitelist where applicable). */
const FILTER_FIELD_MAP: Record<string, string> = {
  currentStepName: 'currentNode',
  requestId: 'requestId',
  taskName: 'taskName',
  processDefinitionName: 'processDefinitionName',
}

const MINE_FIELDS = [
  'requestId', 'taskName', 'currentStepName', 'processDefinitionName',
  'action', 'createTime', 'completedTime', 'durationInMillis',
]
const ACTING_FIELDS = [
  'requestId', 'taskName', 'currentStepName', 'processDefinitionName',
  'delegatorId', 'action', 'createTime', 'completedTime', 'durationInMillis',
]

const loading = ref(true)
const taskList = ref<TaskInfo[]>([])
const listView = ref<'mine' | 'acting'>(route.query.view === 'proxy' ? 'acting' : 'mine')
const actingCount = ref(0)

const taskDataFields = computed(() =>
  listView.value === 'acting' ? ACTING_FIELDS : MINE_FIELDS,
)
const orderedTaskFields = computed(() => orderedColumnFields(taskDataFields.value))
watch(taskDataFields, (fields) => ensureOrder(fields), { immediate: true })

function taskWidthFallback(field: string): number {
  const map: Record<string, number> = {
    requestId: 140, taskName: 160, currentStepName: 140, processDefinitionName: 150,
    delegatorId: 140, action: 160, createTime: 170, completedTime: 170, durationInMillis: 110,
  }
  return map[field] ?? 140
}

function taskColumnLabel(field: string): string {
  const map: Record<string, string> = {
    requestId: t('task.requestId'),
    taskName: t('task.taskName'),
    currentStepName: t('task.currentStep'),
    processDefinitionName: t('task.processName'),
    delegatorId: t('delegation.delegator'),
    action: t('task.action'),
    createTime: t('task.createTime'),
    completedTime: t('task.completedTime'),
    durationInMillis: t('task.duration'),
  }
  return map[field] ?? field
}

const displayTaskRows = computed(() =>
  applyGroupHeaders(taskList.value as unknown as Record<string, unknown>[], colState.groupBy) as unknown as TaskInfo[],
)

function groupRowClassName({ row }: { row: unknown }) {
  return isPortalListGroupHeader(row) ? 'group-header-row' : ''
}

function taskSpanMethod({ row, columnIndex }: { row: unknown; columnIndex: number }) {
  return portalListGroupSpanMethod(row, columnIndex, orderedTaskFields.value.length, 0)
}

const filterForm = reactive({
  keyword: '',
  dateRange: null as [string, string] | null
})

const pagination = reactive({
  page: 1,
  size: PORTAL_LIST_DEFAULT_PAGE_SIZE,
  total: 0
})

/** Toolbar keyword only — column filters go via `filters` body. */
function resolveKeyword(): string | undefined {
  return filterForm.keyword || undefined
}

function buildColumnFilters(): Record<string, { operator: string; value: string }> | undefined {
  const raw = activeFilters()
  const out: Record<string, { operator: string; value: string }> = {}
  for (const [field, filter] of Object.entries(raw)) {
    const apiField = FILTER_FIELD_MAP[field] ?? field
    out[apiField] = { operator: filter.operator, value: filter.value ?? '' }
  }
  return Object.keys(out).length ? out : undefined
}

const loadActingCount = async () => {
  try {
    const res = await queryCompletedTasks({
      assignmentTypes: ['DELEGATED'],
      page: 0,
      size: 1
    })
    const data = res.data || res
    actingCount.value = Number(data.totalElements || 0)
  } catch {
    // FALLBACK(ux): badge is optional chrome; list load remains authoritative
    actingCount.value = 0
  }
}

const loadTasks = async () => {
  loading.value = true
  try {
    const sortApi = colState.sort?.field
      ? SORT_FIELD_MAP[colState.sort.field]
      : undefined
    const res = await queryCompletedTasks({
      assignmentTypes: listView.value === 'acting' ? ['DELEGATED'] : undefined,
      keyword: resolveKeyword(),
      filters: buildColumnFilters(),
      startTime: filterForm.dateRange?.[0] || undefined,
      endTime: filterForm.dateRange?.[1] || undefined,
      sortBy: sortApi,
      sortDirection: colState.sort?.direction?.toLowerCase(),
      page: pagination.page - 1,
      size: pagination.size
    })
    const data = res.data || res
    taskList.value = data.content || []
    pagination.total = data.totalElements || 0
    if (listView.value === 'acting') {
      actingCount.value = Number(data.totalElements || 0)
    }
  } catch (error) {
    console.error('Failed to load completed tasks:', error)
    taskList.value = []
    pagination.total = 0
    ElMessage.error(t('task.loadFailed'))
  } finally {
    loading.value = false
  }
}

function onSort(field: string, direction: PortalListSortDirection) {
  toggleSort(field, direction)
  pagination.page = 1
  loadTasks()
}

function onGroup(field: string) {
  toggleGroup(field)
  pagination.page = 1
  loadTasks()
}

function onApplyColumnFilter(filter: PortalListColumnFilter) {
  applyFilter(filter)
  pagination.page = 1
  loadTasks()
}

function onClearColumnFilter(field?: string) {
  clearFilter(field)
  pagination.page = 1
  loadTasks()
}

const onListViewChange = () => {
  pagination.page = 1
  const q = { ...route.query } as Record<string, string>
  if (listView.value === 'acting') {
    q.view = 'proxy'
  } else {
    delete q.view
  }
  router.replace({ query: q })
  loadTasks()
}

const handleSearch = () => {
  pagination.page = 1
  loadTasks()
}

const handleReset = () => {
  filterForm.keyword = ''
  filterForm.dateRange = null
  handleSearch()
}

const viewTask = (task: TaskInfo) => {
  const query: Record<string, string> = {}
  if (task.completedTime) {
    query.snapshotTime = task.completedTime
  }
  if (task.taskName) {
    query.snapshotTaskName = task.taskName
  }
  if (task.taskId) {
    query.snapshotTaskId = task.taskId
  }
  if (task.taskDefinitionKey) {
    query.snapshotTaskDefinitionKey = task.taskDefinitionKey
  }
  if ((task as any).processInstanceId) {
    query.processInstanceId = String((task as any).processInstanceId)
  }
  if ((task as any).processDefinitionKey) {
    query.processDefinitionKey = String((task as any).processDefinitionKey)
  }
  router.push({ path: `/tasks/${task.taskId}`, query })
}

const formatDuration = (ms: number | undefined) => {
  if (!ms) return '-'
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  
  if (days > 0) {
    return `${days}d${hours % 24}h`
  } else if (hours > 0) {
    return `${hours}h${minutes % 60}m`
  } else if (minutes > 0) {
    return `${minutes}m`
  } else {
    return `${seconds}s`
  }
}

const getActionTagType = (action: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' => {
  const typeMap: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
    'approved': 'success',
    'rejected': 'danger',
    'transferred': 'warning',
    'delegated': 'info',
    'completed': 'primary'
  }
  return typeMap[action] || 'primary'
}

onMounted(() => {
  void loadTasks()
  if (listView.value !== 'acting') {
    void loadActingCount()
  }
})
</script>

<style lang="scss" scoped>
.acting-tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.acting-tab-badge :deep(.el-badge__content) {
  position: static;
  transform: none;
}

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

.completed-tasks-page {
  .page-header {
    margin-bottom: 20px;
    
    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }
  
  .filter-card {
    margin-bottom: 20px;
    
    .el-form {
      margin-bottom: -18px;
    }
  }
}
</style>
