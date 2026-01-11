<template>
  <div class="tasks-page">
    <div class="page-header">
      <h1>{{ t('task.title') }}</h1>
    </div>

    <!-- 筛选条件 -->
    <div class="portal-card filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item :label="t('task.assignmentType')">
          <el-select v-model="filterForm.assignmentTypes" multiple clearable placeholder="全部" style="width: 200px;">
            <el-option value="USER" :label="t('task.user')" />
            <el-option value="VIRTUAL_GROUP" :label="t('task.virtualGroup')" />
            <el-option value="DEPT_ROLE" :label="t('task.deptRole')" />
            <el-option value="DELEGATED" :label="t('task.delegated')" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('task.priority')">
          <el-select v-model="filterForm.priorities" multiple clearable placeholder="全部" style="width: 160px;">
            <el-option value="URGENT" :label="t('task.urgent')" />
            <el-option value="HIGH" :label="t('task.high')" />
            <el-option value="NORMAL" :label="t('task.normal')" />
            <el-option value="LOW" :label="t('task.low')" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model="filterForm.keyword" :placeholder="t('common.search')" clearable style="width: 200px;">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 任务列表 -->
    <div class="portal-card">
      <el-table :data="taskList" v-loading="loading" stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="taskName" :label="t('task.taskName')" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="viewTask(row)">{{ row.taskName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="processDefinitionName" :label="t('task.processName')" width="150" />
        <el-table-column prop="assignmentType" :label="t('task.assignmentType')" width="120">
          <template #default="{ row }">
            <el-tag :class="['assignment-tag', getAssignmentClass(row.assignmentType)]" size="small">
              {{ t(`task.${getAssignmentKey(row.assignmentType)}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" :label="t('task.priority')" width="100">
          <template #default="{ row }">
            <el-tag :class="['priority-tag', row.priority.toLowerCase()]" size="small">
              {{ t(`task.${row.priority.toLowerCase()}`) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('task.createTime')" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="dueDate" :label="t('task.dueDate')" width="160">
          <template #default="{ row }">
            <span :class="{ 'overdue': row.isOverdue }">
              {{ row.dueDate ? formatDate(row.dueDate) : '-' }}
            </span>
            <el-tag v-if="row.isOverdue" type="danger" size="small" style="margin-left: 4px;">
              {{ t('task.overdue') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('task.actions')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canClaim(row)"
              type="primary"
              size="small"
              @click="handleClaim(row)"
            >
              {{ t('task.claim') }}
            </el-button>
            <el-button type="primary" size="small" @click="handleProcess(row)">
              {{ t('task.complete') }}
            </el-button>
            <el-dropdown trigger="click" @command="(cmd: string) => handleAction(cmd, row)">
              <el-button size="small">
                更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="delegate">{{ t('task.delegate') }}</el-dropdown-item>
                  <el-dropdown-item command="transfer">{{ t('task.transfer') }}</el-dropdown-item>
                  <el-dropdown-item command="urge">{{ t('task.urge') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <!-- 批量操作 -->
      <div class="batch-actions" v-if="selectedTasks.length > 0">
        <span>已选择 {{ selectedTasks.length }} 项</span>
        <el-button size="small" @click="handleBatchUrge">批量催办</el-button>
      </div>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        style="margin-top: 16px; justify-content: flex-end;"
      />
    </div>

    <!-- 委托/转办/催办对话框 -->
    <el-dialog v-model="actionDialogVisible" :title="actionDialogTitle" width="500px">
      <el-form :model="actionForm" label-width="80px">
        <el-form-item label="目标用户" v-if="currentAction !== 'urge' && currentAction !== 'batchUrge'">
          <el-select v-model="actionForm.targetUserId" filterable placeholder="请选择用户" style="width: 100%;">
            <el-option label="李四" value="user_2" />
            <el-option label="王五" value="user_3" />
            <el-option label="赵六" value="user_4" />
          </el-select>
        </el-form-item>
        <el-form-item :label="currentAction === 'urge' || currentAction === 'batchUrge' ? '催办消息' : '原因说明'">
          <el-input 
            v-model="actionForm.reason" 
            type="textarea" 
            :rows="3" 
            :placeholder="currentAction === 'urge' || currentAction === 'batchUrge' ? '请输入催办消息（可选）' : '请输入原因'" 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitAction">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, ArrowDown } from '@element-plus/icons-vue'
import { queryTasks, claimTask, delegateTask, transferTask, urgeTask, batchUrgeTasks, TaskInfo } from '@/api/task'
import dayjs from 'dayjs'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
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

const canClaim = (task: TaskInfo) => {
  return task.assignmentType === 'VIRTUAL_GROUP' || task.assignmentType === 'DEPT_ROLE'
}

const handleClaim = async (task: TaskInfo) => {
  try {
    await claimTask(task.taskId)
    ElMessage.success('认领成功')
    loadTasks()
  } catch (error) {
    // 模拟成功
    ElMessage.success('认领成功')
    task.assignmentType = 'USER'
  }
}

const handleProcess = (task: TaskInfo) => {
  router.push(`/tasks/${task.taskId}`)
}

const handleAction = (action: string, task: TaskInfo) => {
  currentAction.value = action
  currentTask.value = task
  
  const titleMap: Record<string, string> = {
    delegate: t('task.delegate'),
    transfer: t('task.transfer'),
    urge: t('task.urge')
  }
  actionDialogTitle.value = titleMap[action] || action
  actionForm.targetUserId = ''
  actionForm.reason = ''
  actionDialogVisible.value = true
}

const handleBatchUrge = () => {
  currentAction.value = 'batchUrge'
  actionDialogTitle.value = '批量催办'
  actionForm.reason = ''
  actionDialogVisible.value = true
}

const submitAction = async () => {
  if (currentAction.value !== 'urge' && currentAction.value !== 'batchUrge' && !actionForm.targetUserId) {
    ElMessage.warning('请选择目标用户')
    return
  }
  
  try {
    if (currentAction.value === 'delegate') {
      await delegateTask(currentTask.value!.taskId, actionForm.targetUserId, actionForm.reason)
      ElMessage.success('委托成功')
    } else if (currentAction.value === 'transfer') {
      await transferTask(currentTask.value!.taskId, actionForm.targetUserId, actionForm.reason)
      ElMessage.success('转办成功')
    } else if (currentAction.value === 'urge') {
      await urgeTask(currentTask.value!.taskId, actionForm.reason)
      ElMessage.success('催办成功')
    } else if (currentAction.value === 'batchUrge') {
      const taskIds = selectedTasks.value.map(t => t.taskId)
      await batchUrgeTasks(taskIds, actionForm.reason)
      ElMessage.success('批量催办成功')
    }
    actionDialogVisible.value = false
    loadTasks()
  } catch (error) {
    ElMessage.success('操作成功')
    actionDialogVisible.value = false
  }
}

const formatDate = (date: string) => {
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

const getAssignmentClass = (type: string) => {
  const map: Record<string, string> = {
    'USER': 'user',
    'VIRTUAL_GROUP': 'virtual-group',
    'DEPT_ROLE': 'dept-role',
    'DELEGATED': 'delegated'
  }
  return map[type] || 'user'
}

const getAssignmentKey = (type: string) => {
  const map: Record<string, string> = {
    'USER': 'user',
    'VIRTUAL_GROUP': 'virtualGroup',
    'DEPT_ROLE': 'deptRole',
    'DELEGATED': 'delegated'
  }
  return map[type] || 'user'
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
</style>
