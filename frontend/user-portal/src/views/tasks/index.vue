<template>
  <div class="tasks-page">
    <div class="page-header">
      <h1>{{ t('task.title') }}</h1>
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
        <el-form-item
          v-if="listView === 'mine'"
          :label="t('task.assignmentType')"
        >
          <el-select
            v-model="filterForm.assignmentTypes"
            multiple
            clearable
            :placeholder="t('common.all')"
            style="width: 200px;"
          >
            <el-option
              value="USER"
              :label="t('task.user')"
            />
            <el-option
              value="VIRTUAL_GROUP"
              :label="t('task.virtualGroup')"
            />
            <el-option
              value="DEPT_ROLE"
              :label="t('task.deptRole')"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('task.priority')">
          <el-select
            v-model="filterForm.priorities"
            multiple
            clearable
            :placeholder="t('common.all')"
            style="width: 160px;"
          >
            <el-option
              value="URGENT"
              :label="t('task.urgent')"
            />
            <el-option
              value="HIGH"
              :label="t('task.high')"
            />
            <el-option
              value="NORMAL"
              :label="t('task.normal')"
            />
            <el-option
              value="LOW"
              :label="t('task.low')"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
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
        @selection-change="handleSelectionChange"
      >
        <template #empty>
          <div
            v-if="loading"
            class="tasks-table-empty-loading"
          >
            <el-icon class="tasks-table-empty-loading__icon is-loading">
              <Loading />
            </el-icon>
            <span>{{ t('common.loading') }}</span>
          </div>
          <span v-else>{{ listView === 'acting' ? t('task.tab.noActing') : t('task.noTasks') }}</span>
        </template>
        <el-table-column
          type="selection"
          width="50"
          :selectable="(row) => !isPortalListGroupHeader(row)"
        />
        <el-table-column
          v-for="(field, idx) in orderedTaskFields"
          :key="field"
          :prop="field"
          :width="colWidth(field, taskWidthFallback(field))"
          :show-overflow-tooltip="field !== 'assignmentType' && field !== 'priority' && field !== 'dueDate'"
          :class-name="field === 'assignmentType' ? 'no-wrap-header' : undefined"
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
              :date-like="field === 'createTime' || field === 'dueDate'"
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
            <template v-else-if="field === 'assignmentType'">
              <el-tag
                :class="['assignment-tag', getAssignmentClass(row)]"
                size="small"
              >
                {{ t(`task.${getAssignmentKey(row)}`) }}
              </el-tag>
            </template>
            <template v-else-if="field === 'delegatorId'">
              {{ t('task.actingFor', { name: row.delegatorName || row.delegatorId || '-' }) }}
            </template>
            <template v-else-if="field === 'initiatorName'">
              {{ row.initiatorName || '-' }}
            </template>
            <template v-else-if="field === 'priority'">
              <el-tag
                :class="['priority-tag', getPriorityClass(row.priority)]"
                size="small"
              >
                {{ getPriorityLabel(row.priority) }}
              </el-tag>
            </template>
            <template v-else-if="field === 'createTime'">
              {{ formatDate(row.createTime) }}
            </template>
            <template v-else-if="field === 'dueDate'">
              <span :class="{ 'overdue': row.isOverdue }">
                {{ row.dueDate ? formatDate(row.dueDate) : '-' }}
              </span>
              <el-tag
                v-if="row.isOverdue"
                type="danger"
                size="small"
                style="margin-left: 4px;"
              >
                {{ t('task.overdue') }}
              </el-tag>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <!-- 批量操作 -->
      <div
        v-if="selectedTasks.length > 0"
        class="batch-actions"
      >
        <span>{{ t('task.selected', { count: selectedTasks.length }) }}</span>
        <el-button
          size="small"
          @click="handleBatchUrge"
        >
          {{ t('task.batchUrge') }}
        </el-button>
      </div>

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

    <!-- 委托/转办/催办对话框 -->
    <el-dialog
      v-model="actionDialogVisible"
      :title="actionDialogTitle"
      width="500px"
      class="task-action-dialog"
    >
      <el-form
        :model="actionForm"
        label-width="auto"
        label-position="left"
        class="task-action-form"
      >
        <el-form-item
          v-if="currentAction !== 'urge' && currentAction !== 'batchUrge'"
          :label="t('task.targetUser')"
        >
          <el-select
            v-model="actionForm.targetUserId"
            filterable
            :placeholder="t('task.selectUser')"
            style="width: 100%;"
          >
            <el-option
              label="Li Si"
              value="user_2"
            />
            <el-option
              label="Wang Wu"
              value="user_3"
            />
            <el-option
              label="Zhao Liu"
              value="user_4"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          :label="currentAction === 'urge' || currentAction === 'batchUrge' ? t('task.urgeMessage') : t('task.reasonDescription')"
          class="task-action-reason-item"
        >
          <el-input 
            v-model="actionForm.reason" 
            type="textarea" 
            :rows="5" 
            :placeholder="currentAction === 'urge' || currentAction === 'batchUrge' ? t('task.urgeMessagePlaceholder') : t('task.reasonPlaceholder')" 
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, Loading } from '@element-plus/icons-vue'
import { queryTasks, delegateTask, transferTask, urgeTask, batchUrgeTasks, TaskInfo } from '@/api/task'
import { formatDate } from '@/utils/dateFormat'
import { usePendingTaskStore } from '@/stores/pendingTask'
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

const pendingTaskStore = usePendingTaskStore()
const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const cols = usePortalListColumnState('tasks-pending')
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

/** Backend TaskQueryComponent whitelist; unknown fields may fall back server-side. */
const SORT_FIELD_MAP: Record<string, string> = {
  requestId: 'requestId',
  taskName: 'taskName',
  currentStepName: 'taskName',
  processDefinitionName: 'processDefinitionName',
  assignmentType: 'assignmentType',
  delegatorId: 'delegatorId',
  initiatorName: 'initiatorName',
  priority: 'priority',
  createTime: 'createTime',
  dueDate: 'dueDate',
}

/** FE column → API filter field (TaskQueryColumnFilters whitelist). */
const FILTER_FIELD_MAP: Record<string, string> = {
  currentStepName: 'currentNode',
  requestId: 'requestId',
  taskName: 'taskName',
  processDefinitionName: 'processDefinitionName',
  initiatorName: 'initiatorName',
  priority: 'priority',
  assignmentType: 'assignmentType',
}

const MINE_FIELDS = [
  'requestId', 'taskName', 'currentStepName', 'processDefinitionName',
  'assignmentType', 'initiatorName', 'priority', 'createTime', 'dueDate',
]
const ACTING_FIELDS = [
  'requestId', 'taskName', 'currentStepName', 'processDefinitionName',
  'delegatorId', 'initiatorName', 'priority', 'createTime', 'dueDate',
]

function taskWidthFallback(field: string): number {
  const map: Record<string, number> = {
    requestId: 140, taskName: 160, currentStepName: 140, processDefinitionName: 150,
    assignmentType: 140, delegatorId: 140, initiatorName: 120, priority: 100,
    createTime: 160, dueDate: 140,
  }
  return map[field] ?? 140
}

function taskColumnLabel(field: string): string {
  const map: Record<string, string> = {
    requestId: t('task.requestId'),
    taskName: t('task.taskName'),
    currentStepName: t('task.currentStep'),
    processDefinitionName: t('task.processName'),
    assignmentType: t('task.assignmentType'),
    delegatorId: t('delegation.delegator'),
    initiatorName: t('task.initiator'),
    priority: t('task.priority'),
    createTime: t('task.createTime'),
    dueDate: t('task.dueDate'),
  }
  return map[field] ?? field
}

const loading = ref(true)
const taskList = ref<TaskInfo[]>([])
const selectedTasks = ref<TaskInfo[]>([])
const listView = ref<'mine' | 'acting'>(route.query.view === 'proxy' ? 'acting' : 'mine')
const actingCount = ref(0)

const taskDataFields = computed(() =>
  listView.value === 'acting' ? ACTING_FIELDS : MINE_FIELDS,
)
const orderedTaskFields = computed(() => orderedColumnFields(taskDataFields.value))

watch(taskDataFields, (fields) => ensureOrder(fields), { immediate: true })

/** Client group headers on current server page (API has no groupCounts yet). */
const displayTaskRows = computed(() =>
  applyGroupHeaders(taskList.value as unknown as Record<string, unknown>[], colState.groupBy) as unknown as TaskInfo[],
)

function groupRowClassName({ row }: { row: unknown }) {
  return isPortalListGroupHeader(row) ? 'group-header-row' : ''
}

function taskSpanMethod({ row, columnIndex }: { row: unknown; columnIndex: number }) {
  return portalListGroupSpanMethod(row, columnIndex, orderedTaskFields.value.length, 1)
}

const filterForm = reactive({
  assignmentTypes: [] as string[],
  priorities: [] as string[],
  keyword: ''
})

const pagination = reactive({
  page: 1,
  size: PORTAL_LIST_DEFAULT_PAGE_SIZE,
  total: 0
})

const actionDialogVisible = ref(false)
const actionDialogTitle = ref('')
const currentAction = ref('')
const currentTask = ref<TaskInfo | null>(null)
const actionForm = reactive({
  targetUserId: '',
  reason: ''
})

/** Toolbar keyword only — column filters go via `filters` body. */
function resolveKeyword(): string | undefined {
  return filterForm.keyword || undefined
}

/** Map active column filters to TaskQueryRequest.filters. */
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
    const res = await queryTasks({
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
    const assignmentTypes =
      listView.value === 'acting'
        ? ['DELEGATED']
        : filterForm.assignmentTypes.length > 0
          ? filterForm.assignmentTypes
          : undefined
    const sortApi = colState.sort?.field
      ? SORT_FIELD_MAP[colState.sort.field]
      : undefined
    const res = await queryTasks({
      assignmentTypes,
      priorities: filterForm.priorities.length > 0 ? filterForm.priorities : undefined,
      keyword: resolveKeyword(),
      filters: buildColumnFilters(),
      sortBy: sortApi,
      sortDirection: colState.sort?.direction?.toLowerCase(),
      page: pagination.page - 1,
      size: pagination.size
    })
    // API 返回格式: { success: true, data: { content: [], totalElements: 0 } }
    const data = res.data || res
    taskList.value = data.content || []
    pagination.total = data.totalElements || 0
    if (listView.value === 'mine') {
      pendingTaskStore.syncCountFromListTotal(data.totalElements as number | undefined)
    } else {
      actingCount.value = Number(data.totalElements || 0)
    }
  } catch (error) {
    console.error('Failed to load tasks:', error)
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
  filterForm.assignmentTypes = []
  filterForm.priorities = []
  filterForm.keyword = ''
  handleSearch()
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
  if (currentAction.value !== 'urge' && currentAction.value !== 'batchUrge' && !actionForm.targetUserId) {
    ElMessage.warning(t('task.selectUser'))
    return
  }
  
  try {
    if (currentAction.value === 'delegate') {
      await delegateTask(currentTask.value!.taskId, actionForm.targetUserId, actionForm.reason)
      ElMessage.success(t('common.success'))
    } else if (currentAction.value === 'transfer') {
      await transferTask(currentTask.value!.taskId, actionForm.targetUserId, actionForm.reason)
      ElMessage.success(t('common.success'))
    } else if (currentAction.value === 'urge') {
      await urgeTask(currentTask.value!.taskId, actionForm.reason)
      ElMessage.success(t('common.success'))
    } else if (currentAction.value === 'batchUrge') {
      const taskIds = selectedTasks.value.map(t => t.taskId)
      await batchUrgeTasks(taskIds, actionForm.reason)
      ElMessage.success(t('common.success'))
    }
    actionDialogVisible.value = false
    loadTasks()
  } catch (error) {
    console.error('Task action failed:', error)
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
  // 设计器 FIXED_BU_ROLE：引擎运行时多为 CANDIDATE_USERS，需用 BPMN 扩展区分「固定 BU+角色」池
  if (bpmn === 'FIXED_BU_ROLE') {
    return 'fixedBuRole'
  }
  if (bpmn === 'BU_ROLE') {
    return 'buRole'
  }
  const type = task.assignmentType
  const map: Record<string, string> = {
    'USER': 'user',
    'VIRTUAL_GROUP': 'virtualGroup',
    'DEPT_ROLE': 'deptRole',
    'DELEGATED': 'delegated',
    'CANDIDATE_USERS': 'candidateUsers'
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
    buRole: 'virtual-group'
  }
  return map[key] || 'user'
}

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

onMounted(() => {
  void loadTasks()
  if (listView.value !== 'acting') {
    void loadActingCount()
  }
})
</script>

<style lang="scss" scoped>
.tasks-page {
  .page-header {
    margin-bottom: 20px;
    
    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }

  .acting-tab-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .acting-tab-badge :deep(.el-badge__content) {
    position: static;
    transform: none;
  }
  
  .filter-card {
    margin-bottom: 20px;
    
    .el-form {
      margin-bottom: -18px;
    }
  }

  .tasks-table-empty-loading {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: var(--text-secondary);
    padding: 24px 0;

    &__icon {
      font-size: 18px;
    }
  }
  
  .batch-actions {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-top: 16px;
    padding: 12px 16px;
    background: #f5f7fa;
    border-radius: 4px;
    
    span {
      color: var(--text-secondary);
    }
  }
  
  .overdue {
    color: var(--error-red);
  }
}

.tasks-page :deep(.no-wrap-header .cell) {
  white-space: nowrap !important;
  overflow: visible !important;
}
</style>

<style lang="scss">
/* 弹窗挂载到 body，需用全局样式 */
.task-action-form .el-form-item__label {
  white-space: nowrap;
}
</style>
