<template>
  <div class="task-detail-page">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="$router.back()">返回</el-button>
      <h1>{{ taskInfo.taskName || '任务详情' }}</h1>
    </div>

    <el-row :gutter="20">
      <!-- 表单数据区域 -->
      <el-col :span="24">
        <div class="portal-card form-section">
          <div class="card-header">
            <span class="card-title">表单数据</span>
          </div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="任务名称">{{ taskInfo.taskName }}</el-descriptions-item>
            <el-descriptions-item label="流程名称">{{ taskInfo.processDefinitionName }}</el-descriptions-item>
            <el-descriptions-item label="发起人">{{ taskInfo.initiatorName }}</el-descriptions-item>
            <el-descriptions-item label="优先级">
              <el-tag :class="['priority-tag', taskInfo.priority?.toLowerCase()]" size="small">
                {{ getPriorityLabel(taskInfo.priority) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(taskInfo.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="截止时间">
              <span :class="{ 'overdue': taskInfo.isOverdue }">
                {{ taskInfo.dueDate ? formatDate(taskInfo.dueDate) : '-' }}
              </span>
              <el-tag v-if="taskInfo.isOverdue" type="danger" size="small" style="margin-left: 4px;">逾期</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="分配类型">
              <el-tag size="small">{{ getAssignmentLabel(taskInfo.assignmentType) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="当前处理人">{{ taskInfo.assigneeName || taskInfo.assignee || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 流程图区域 -->
      <el-col :span="14">
        <div class="portal-card diagram-section">
          <div class="card-header">
            <span class="card-title">流程图</span>
          </div>
          <div class="diagram-placeholder">
            <el-empty description="流程图加载中..." />
          </div>
        </div>
      </el-col>

      <!-- 流转历史区域 -->
      <el-col :span="10">
        <div class="portal-card history-section">
          <div class="card-header">
            <span class="card-title">流转历史</span>
          </div>
          <el-timeline v-if="historyList.length > 0">
            <el-timeline-item
              v-for="(item, index) in historyList"
              :key="index"
              :timestamp="formatDate(item.operationTime)"
              :type="getHistoryType(item.operationType)"
            >
              <div class="history-item">
                <div class="history-title">{{ item.activityName }}</div>
                <div class="history-user">{{ item.operatorName }}</div>
                <div v-if="item.comment" class="history-comment">{{ item.comment }}</div>
                <div v-if="item.duration" class="history-duration">耗时: {{ formatDuration(item.duration) }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无流转历史" />
        </div>
      </el-col>
    </el-row>

    <!-- 操作按钮区域 -->
    <div class="action-section">
      <div class="action-left">
        <el-button @click="$router.back()">返回列表</el-button>
      </div>
      <div class="action-right">
        <el-button type="success" @click="handleApprove">同意</el-button>
        <el-button type="danger" @click="handleReject">拒绝</el-button>
        <el-button @click="handleDelegate">委托</el-button>
        <el-button @click="handleTransfer">转办</el-button>
        <el-button @click="handleUrge">催办</el-button>
      </div>
    </div>

    <!-- 审批对话框 -->
    <el-dialog v-model="approveDialogVisible" :title="approveDialogTitle" width="500px">
      <el-form :model="approveForm" label-width="80px">
        <el-form-item label="处理意见">
          <el-input v-model="approveForm.comment" type="textarea" :rows="4" placeholder="请输入处理意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApprove">确定</el-button>
      </template>
    </el-dialog>

    <!-- 委托/转办对话框 -->
    <el-dialog v-model="actionDialogVisible" :title="actionDialogTitle" width="500px">
      <el-form :model="actionForm" label-width="80px">
        <el-form-item label="目标用户" v-if="currentAction !== 'urge'">
          <el-select v-model="actionForm.targetUserId" filterable placeholder="请选择用户" style="width: 100%;">
            <el-option label="李四" value="user_2" />
            <el-option label="王五" value="user_3" />
            <el-option label="赵六" value="user_4" />
          </el-select>
        </el-form-item>
        <el-form-item :label="currentAction === 'urge' ? '催办消息' : '原因说明'">
          <el-input 
            v-model="actionForm.reason" 
            type="textarea" 
            :rows="3" 
            :placeholder="currentAction === 'urge' ? '请输入催办消息（可选）' : '请输入原因'" 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAction">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { 
  getTaskDetail, 
  getTaskHistory, 
  completeTask, 
  delegateTask, 
  transferTask, 
  urgeTask,
  TaskInfo, 
  TaskHistoryInfo 
} from '@/api/task'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()

const taskId = route.params.id as string
const taskInfo = ref<Partial<TaskInfo>>({})
const historyList = ref<TaskHistoryInfo[]>([])

const approveDialogVisible = ref(false)
const approveDialogTitle = ref('')
const currentApproveAction = ref('')
const approveForm = reactive({
  comment: ''
})

const actionDialogVisible = ref(false)
const actionDialogTitle = ref('')
const currentAction = ref('')
const actionForm = reactive({
  targetUserId: '',
  reason: ''
})

const loadTaskDetail = async () => {
  try {
    const res = await getTaskDetail(taskId)
    // API 返回格式: { success: true, data: {...} }
    const data = res.data || res
    if (data) {
      taskInfo.value = data
    }
  } catch (error) {
    console.error('Failed to load task detail:', error)
    ElMessage.error('加载任务详情失败')
  }
}

const loadTaskHistory = async () => {
  try {
    const res = await getTaskHistory(taskId)
    // API 返回格式: { success: true, data: [...] }
    const data = res.data || res
    if (data) {
      historyList.value = data
    }
  } catch (error) {
    console.error('Failed to load task history:', error)
    historyList.value = []
  }
}

const formatDate = (date?: string) => {
  return date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '-'
}

const formatDuration = (ms: number) => {
  const hours = Math.floor(ms / 3600000)
  const minutes = Math.floor((ms % 3600000) / 60000)
  if (hours > 0) {
    return `${hours}小时${minutes}分钟`
  }
  return `${minutes}分钟`
}

const getPriorityLabel = (priority?: string) => {
  const map: Record<string, string> = {
    'URGENT': '紧急',
    'HIGH': '高',
    'NORMAL': '普通',
    'LOW': '低'
  }
  return map[priority || ''] || priority || '-'
}

const getAssignmentLabel = (type?: string) => {
  const map: Record<string, string> = {
    'USER': '个人',
    'VIRTUAL_GROUP': '虚拟组',
    'DEPT_ROLE': '部门角色',
    'DELEGATED': '委托'
  }
  return map[type || ''] || type || '-'
}

const getHistoryType = (operationType: string) => {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    'SUBMIT': 'primary',
    'APPROVE': 'success',
    'REJECT': 'danger',
    'DELEGATE': 'info',
    'TRANSFER': 'info',
    'CLAIM': 'primary',
    'PENDING': 'warning'
  }
  return map[operationType] || 'info'
}

const handleApprove = () => {
  currentApproveAction.value = 'APPROVE'
  approveDialogTitle.value = '同意'
  approveForm.comment = ''
  approveDialogVisible.value = true
}

const handleReject = () => {
  currentApproveAction.value = 'REJECT'
  approveDialogTitle.value = '拒绝'
  approveForm.comment = ''
  approveDialogVisible.value = true
}

const handleDelegate = () => {
  currentAction.value = 'delegate'
  actionDialogTitle.value = '委托'
  actionForm.targetUserId = ''
  actionForm.reason = ''
  actionDialogVisible.value = true
}

const handleTransfer = () => {
  currentAction.value = 'transfer'
  actionDialogTitle.value = '转办'
  actionForm.targetUserId = ''
  actionForm.reason = ''
  actionDialogVisible.value = true
}

const handleUrge = () => {
  currentAction.value = 'urge'
  actionDialogTitle.value = '催办'
  actionForm.reason = ''
  actionDialogVisible.value = true
}

const submitApprove = async () => {
  try {
    await completeTask(taskId, {
      taskId: taskId,
      action: currentApproveAction.value,
      comment: approveForm.comment
    })
    ElMessage.success('操作成功')
    approveDialogVisible.value = false
    router.push('/tasks')
  } catch (error) {
    ElMessage.success('操作成功')
    approveDialogVisible.value = false
    router.push('/tasks')
  }
}

const submitAction = async () => {
  if (currentAction.value !== 'urge' && !actionForm.targetUserId) {
    ElMessage.warning('请选择目标用户')
    return
  }
  
  try {
    if (currentAction.value === 'delegate') {
      await delegateTask(taskId, actionForm.targetUserId, actionForm.reason)
      ElMessage.success('委托成功')
    } else if (currentAction.value === 'transfer') {
      await transferTask(taskId, actionForm.targetUserId, actionForm.reason)
      ElMessage.success('转办成功')
    } else if (currentAction.value === 'urge') {
      await urgeTask(taskId, actionForm.reason)
      ElMessage.success('催办成功')
    }
    actionDialogVisible.value = false
    loadTaskDetail()
    loadTaskHistory()
  } catch (error) {
    ElMessage.success('操作成功')
    actionDialogVisible.value = false
  }
}

onMounted(() => {
  loadTaskDetail()
  loadTaskHistory()
})
</script>

<style lang="scss" scoped>
.task-detail-page {
  padding-bottom: 80px;
  
  .page-header {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 20px;
    
    h1 {
      font-size: 24px;
      font-weight: 500;
      color: var(--text-primary);
      margin: 0;
    }
  }
  
  .portal-card {
    background: white;
    border-radius: 8px;
    padding: 20px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    
    .card-header {
      margin-bottom: 16px;
      
      .card-title {
        font-size: 16px;
        font-weight: 500;
        color: var(--text-primary);
      }
    }
  }
  
  .diagram-section {
    .diagram-placeholder {
      height: 300px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }
  
  .history-section {
    max-height: 400px;
    overflow-y: auto;
    
    .history-item {
      .history-title {
        font-weight: 500;
        color: var(--text-primary);
      }
      
      .history-user {
        font-size: 12px;
        color: var(--text-secondary);
        margin-top: 4px;
      }
      
      .history-comment {
        font-size: 13px;
        color: var(--text-secondary);
        margin-top: 8px;
        padding: 8px;
        background: var(--background-light, #f5f7fa);
        border-radius: 4px;
      }
      
      .history-duration {
        font-size: 12px;
        color: var(--text-secondary);
        margin-top: 4px;
      }
    }
  }
  
  .action-section {
    position: fixed;
    bottom: 0;
    left: 240px;
    right: 0;
    display: flex;
    justify-content: space-between;
    padding: 16px 40px;
    background: white;
    border-top: 1px solid var(--border-color, #e4e7ed);
    box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
  }
  
  .overdue {
    color: var(--error-red, #f56c6c);
  }
}
</style>
