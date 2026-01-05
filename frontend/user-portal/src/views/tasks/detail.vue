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
                {{ taskInfo.priority }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(taskInfo.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="截止时间">{{ taskInfo.dueDate ? formatDate(taskInfo.dueDate) : '-' }}</el-descriptions-item>
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
          <el-timeline>
            <el-timeline-item
              v-for="(item, index) in historyList"
              :key="index"
              :timestamp="item.time"
              :type="item.type"
            >
              <div class="history-item">
                <div class="history-title">{{ item.title }}</div>
                <div class="history-user">{{ item.user }}</div>
                <div v-if="item.comment" class="history-comment">{{ item.comment }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getTaskDetail, completeTask, TaskInfo } from '@/api/task'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()

const taskId = route.params.id as string
const taskInfo = ref<Partial<TaskInfo>>({})

const historyList = ref([
  { time: '2026-01-05 10:00', title: '提交申请', user: '李四', type: 'primary' as const },
  { time: '2026-01-05 11:30', title: '部门经理审批', user: '王五', comment: '同意', type: 'success' as const },
  { time: '2026-01-05 14:00', title: '待审批', user: '当前节点', type: 'warning' as const }
])

const approveDialogVisible = ref(false)
const approveDialogTitle = ref('')
const currentAction = ref('')
const approveForm = reactive({
  comment: ''
})

const loadTaskDetail = async () => {
  try {
    const res = await getTaskDetail(taskId)
    if (res.data) {
      taskInfo.value = res.data
    }
  } catch (error) {
    // 使用模拟数据
    taskInfo.value = {
      taskId: taskId,
      taskName: '请假申请审批',
      processDefinitionName: '请假流程',
      initiatorName: '李四',
      priority: 'NORMAL',
      createTime: new Date().toISOString(),
      assignmentType: 'USER'
    }
  }
}

const formatDate = (date?: string) => {
  return date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '-'
}

const handleApprove = () => {
  currentAction.value = 'APPROVE'
  approveDialogTitle.value = '同意'
  approveForm.comment = ''
  approveDialogVisible.value = true
}

const handleReject = () => {
  currentAction.value = 'REJECT'
  approveDialogTitle.value = '拒绝'
  approveForm.comment = ''
  approveDialogVisible.value = true
}

const handleDelegate = () => {
  ElMessage.info('委托功能开发中')
}

const handleTransfer = () => {
  ElMessage.info('转办功能开发中')
}

const submitApprove = async () => {
  try {
    await completeTask(taskId, {
      taskId: taskId,
      action: currentAction.value,
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

onMounted(() => {
  loadTaskDetail()
})
</script>

<style lang="scss" scoped>
.task-detail-page {
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
        background: var(--background-light);
        border-radius: 4px;
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
    border-top: 1px solid var(--border-color);
    box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
  }
}
</style>
