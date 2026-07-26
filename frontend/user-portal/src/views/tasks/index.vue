<template>
  <div class="tasks-page">
    <div class="page-header">
      <h1>{{ t('task.title') }}</h1>
    </div>

    <!-- 筛选条件 -->
    <div class="portal-card filter-card">
      <el-form
        :inline="true"
        :model="filterForm"
      >
        <el-form-item :label="t('task.assignmentType')">
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
            <el-option
              value="DELEGATED"
              :label="t('task.delegated')"
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

    <!-- 任务列表：先渲染表格结构，数据异步填充（加载中仅在空表时提示，翻页时保留上一页数据直至返回） -->
    <div class="portal-card">
      <el-table
        :data="taskList"
        stripe
        table-layout="fixed"
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
          <span v-else>{{ t('task.noTasks') }}</span>
        </template>
        <el-table-column
          type="selection"
          width="50"
        />
        <el-table-column
          prop="requestId"
          :label="t('task.requestId')"
          min-width="130"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <el-link
              type="primary"
              @click="viewTask(row)"
            >
              {{ row.requestId || '-' }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column
          prop="taskName"
          :label="t('task.taskName')"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          prop="currentStepName"
          :label="t('task.currentStep')"
          min-width="130"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.currentStepName || row.taskName || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="processDefinitionName"
          :label="t('task.processName')"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          prop="assignmentType"
          :label="t('task.assignmentType')"
          width="130"
          :show-overflow-tooltip="false"
          class-name="no-wrap-header"
        >
          <template #default="{ row }">
            <el-tag
              :class="['assignment-tag', getAssignmentClass(row)]"
              size="small"
            >
              {{ t(`task.${getAssignmentKey(row)}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="initiatorName"
          :label="t('task.initiator')"
          width="100"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.initiatorName || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="priority"
          :label="t('task.priority')"
          width="80"
        >
          <template #default="{ row }">
            <el-tag
              :class="['priority-tag', getPriorityClass(row.priority)]"
              size="small"
            >
              {{ getPriorityLabel(row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="createTime"
          :label="t('task.createTime')"
          width="150"
        >
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="dueDate"
          :label="t('task.dueDate')"
          width="130"
        >
          <template #default="{ row }">
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

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        :disabled="loading"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end;"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, Loading } from '@element-plus/icons-vue'
import { queryTasks, delegateTask, transferTask, urgeTask, batchUrgeTasks, TaskInfo } from '@/api/task'
import { formatDate } from '@/utils/dateFormat'
import { usePendingTaskStore } from '@/stores/pendingTask'

const pendingTaskStore = usePendingTaskStore()
const { t } = useI18n()
const router = useRouter()

const loading = ref(true)
const taskList = ref<TaskInfo[]>([])
const selectedTasks = ref<TaskInfo[]>([])

const filterForm = reactive({
  assignmentTypes: [] as string[],
  priorities: [] as string[],
  keyword: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
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

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await queryTasks({
      assignmentTypes: filterForm.assignmentTypes.length > 0 ? filterForm.assignmentTypes : undefined,
      priorities: filterForm.priorities.length > 0 ? filterForm.priorities : undefined,
      keyword: filterForm.keyword || undefined,
      page: pagination.page - 1,
      size: pagination.size
    })
    // API 返回格式: { success: true, data: { content: [], totalElements: 0 } }
    const data = res.data || res
    taskList.value = data.content || []
    pagination.total = data.totalElements || 0
    pendingTaskStore.syncCountFromListTotal(data.totalElements as number | undefined)
  } catch (error) {
    console.error('Failed to load tasks:', error)
    taskList.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
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

const handleSizeChange = () => {
  pagination.page = 1
  loadTasks()
}

const handlePageChange = () => {
  loadTasks()
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
  loadTasks()
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
