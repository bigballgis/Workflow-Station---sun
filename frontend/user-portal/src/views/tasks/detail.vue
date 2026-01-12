<template>
  <div class="task-detail-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="$router.back()">返回</el-button>
      <h1>{{ taskInfo.taskName || '任务详情' }}</h1>
      <el-tag :type="getPriorityType(taskInfo.priority)" size="small">
        {{ getPriorityLabel(taskInfo.priority) }}
      </el-tag>
      <el-tag v-if="taskInfo.isOverdue" type="danger" size="small">逾期</el-tag>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="skeleton-content">
      <el-skeleton animated :count="3">
        <template #template>
          <el-skeleton-item variant="rect" style="height: 120px; margin-bottom: 20px;" />
          <el-skeleton-item variant="rect" style="height: 300px; margin-bottom: 20px;" />
          <el-skeleton-item variant="rect" style="height: 200px;" />
        </template>
      </el-skeleton>
    </div>

    <!-- 正常内容 -->
    <div v-else class="content-sections">
      <!-- 第一部分：基本信息 -->
      <div class="section info-section">
        <div class="section-header">
          <el-icon><InfoFilled /></el-icon>
          <span>基本信息</span>
        </div>
        <div class="section-content">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="任务名称">
              {{ taskInfo.taskName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="流程名称">
              {{ taskInfo.processDefinitionName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="发起人">
              {{ taskInfo.initiatorName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatDate(taskInfo.createTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="截止时间">
              <span :class="{ 'overdue': taskInfo.isOverdue }">
                {{ taskInfo.dueDate ? formatDate(taskInfo.dueDate) : '-' }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="当前处理人">
              {{ getCurrentAssigneeDisplay() }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <!-- 第二部分：流程图 -->
      <div class="section workflow-section">
        <div class="section-header">
          <el-icon><Share /></el-icon>
          <span>工作流程图</span>
          <el-tag type="warning" size="small">
            {{ taskInfo.taskName || '待处理' }}
          </el-tag>
        </div>
        <div class="section-content">
          <ProcessDiagram
            v-if="processNodes.length > 0"
            :nodes="processNodes"
            :flows="processFlows"
            :current-node-id="currentNodeId"
            :completed-node-ids="completedNodeIds"
            :show-toolbar="true"
            :show-legend="true"
          />
          <el-empty v-else description="暂无流程定义" />
        </div>
      </div>

      <!-- 第三部分：表单数据 -->
      <div class="section form-section">
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ currentFormName || '任务表单' }}</span>
        </div>
        <div class="section-content">
          <div v-if="formFields.length > 0" class="form-container">
            <FormRenderer
              :fields="formFields"
              v-model="formData"
              label-width="120px"
              :readonly="formReadOnly"
            />
          </div>
          <el-empty v-else description="暂无表单数据" />
        </div>
      </div>

      <!-- 第四部分：流转记录 -->
      <div class="section history-section">
        <div class="section-header">
          <el-icon><Clock /></el-icon>
          <span>流转记录</span>
        </div>
        <div class="section-content">
          <ProcessHistory
            v-if="historyRecords.length > 0"
            :records="historyRecords"
            :show-header="false"
            :show-refresh="false"
          />
          <el-empty v-else description="暂无流转记录" />
        </div>
      </div>

      <!-- 第五部分：操作按钮 -->
      <div class="section action-section">
        <div class="action-buttons">
          <div class="left-actions">
            <el-button @click="$router.back()">返回列表</el-button>
          </div>
          <div class="right-actions">
            <el-button type="success" @click="handleApprove">
              <el-icon><Check /></el-icon> 同意
            </el-button>
            <el-button type="danger" @click="handleReject">
              <el-icon><Close /></el-icon> 拒绝
            </el-button>
            <el-button @click="handleDelegate">
              <el-icon><User /></el-icon> 委托
            </el-button>
            <el-button @click="handleTransfer">
              <el-icon><Switch /></el-icon> 转办
            </el-button>
            <el-button type="warning" @click="handleUrge">
              <el-icon><Bell /></el-icon> 催办
            </el-button>
          </div>
        </div>
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
        <el-button type="primary" @click="submitApprove" :loading="submitting">确定</el-button>
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
        <el-button type="primary" @click="submitAction" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, InfoFilled, Share, Document, Clock, Bell, Check, Close, User, Switch } from '@element-plus/icons-vue'
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
import { processApi } from '@/api/process'
import ProcessDiagram, { type ProcessNode, type ProcessFlow } from '@/components/ProcessDiagram.vue'
import ProcessHistory, { type HistoryRecord } from '@/components/ProcessHistory.vue'
import FormRenderer, { type FormField } from '@/components/FormRenderer.vue'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()

const taskId = route.params.id as string
const loading = ref(true)
const submitting = ref(false)
const taskInfo = ref<Partial<TaskInfo>>({})

// 流程图数据
const processNodes = ref<ProcessNode[]>([])
const processFlows = ref<ProcessFlow[]>([])
const currentNodeId = ref('')
const completedNodeIds = ref<string[]>([])

// 表单数据
const formFields = ref<FormField[]>([])
const formData = ref<Record<string, any>>({})
const currentFormName = ref('')
const formReadOnly = ref(false)

// 流转记录
const historyRecords = ref<HistoryRecord[]>([])

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
  loading.value = true
  try {
    const res = await getTaskDetail(taskId)
    const data = res.data || res
    if (data) {
      taskInfo.value = data
      if (data.variables) formData.value = data.variables
      // 加载功能单元内容（流程图和表单）
      if (data.processDefinitionKey) {
        await loadFunctionUnitContent(data.processDefinitionKey)
      }
      // 加载流转历史
      await loadTaskHistory()
    }
  } catch (error) {
    console.error('Failed to load task detail:', error)
    ElMessage.error('加载任务详情失败')
  } finally {
    loading.value = false
  }
}

const loadTaskHistory = async () => {
  try {
    const res = await getTaskHistory(taskId)
    const data = res.data || res
    if (data && Array.isArray(data)) {
      // 转换为 HistoryRecord 格式
      historyRecords.value = data.map((item: TaskHistoryInfo, index: number) => ({
        id: `history_${index}`,
        nodeId: item.activityId || `node_${index}`,
        nodeName: item.activityName || '未知节点',
        status: getHistoryStatus(item.operationType),
        assigneeName: item.operatorName || '-',
        comment: item.comment,
        createdTime: item.operationTime || '',
        completedTime: item.operationTime
      }))
    }
  } catch (error) {
    console.error('Failed to load task history:', error)
    historyRecords.value = []
  }
}

// 加载功能单元内容
const loadFunctionUnitContent = async (processKey: string) => {
  try {
    const response = await processApi.getFunctionUnitContent(processKey)
    const content = response.data || response
    if (content.error) {
      console.error('Function unit content error:', content.error)
      return
    }
    // 解析流程图
    if (content.processes?.length > 0) {
      parseBpmnXml(content.processes[0].data)
    }
    // 解析表单
    if (content.forms?.length > 0) {
      currentFormName.value = content.forms[0].name
      parseFormConfig(content.forms[0].data)
    }
  } catch (error) {
    console.error('Failed to load function unit content:', error)
  }
}

// 解析 BPMN XML
const parseBpmnXml = (xml: string) => {
  if (!xml) return
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const nodes: ProcessNode[] = []
    const flows: ProcessFlow[] = []
    const completed: string[] = []
    
    // 解析位置信息
    const positionMap = new Map()
    doc.querySelectorAll('BPMNShape, bpmndi\\:BPMNShape').forEach(shape => {
      const bpmnElement = shape.getAttribute('bpmnElement')
      const bounds = shape.querySelector('Bounds, dc\\:Bounds')
      if (bpmnElement && bounds) {
        positionMap.set(bpmnElement, {
          x: parseFloat(bounds.getAttribute('x') || '0'),
          y: parseFloat(bounds.getAttribute('y') || '0'),
          width: parseFloat(bounds.getAttribute('width') || '100'),
          height: parseFloat(bounds.getAttribute('height') || '80')
        })
      }
    })
    
    // 获取当前任务名称
    const currentTaskName = taskInfo.value.taskName || ''
    let foundCurrentNode = false
    
    // 解析开始事件
    doc.querySelectorAll('startEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `start_${index}`
      const pos = positionMap.get(id)
      nodes.push({ id, name: event.getAttribute('name') || '开始', type: 'start', status: 'completed', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      completed.push(id)
    })
    
    // 解析用户任务
    doc.querySelectorAll('userTask').forEach((task, index) => {
      const id = task.getAttribute('id') || `task_${index}`
      const name = task.getAttribute('name') || `任务${index + 1}`
      const pos = positionMap.get(id)
      
      let status: 'completed' | 'current' | 'pending' = 'pending'
      
      if (name === currentTaskName || id === currentTaskName) {
        status = 'current'
        currentNodeId.value = id
        foundCurrentNode = true
      } else if (!foundCurrentNode) {
        status = 'completed'
        completed.push(id)
      }
      
      nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })
    
    // 解析网关
    doc.querySelectorAll('exclusiveGateway, parallelGateway, inclusiveGateway').forEach((gateway, index) => {
      const id = gateway.getAttribute('id') || `gateway_${index}`
      const pos = positionMap.get(id)
      nodes.push({ id, name: gateway.getAttribute('name') || '', type: 'gateway', status: 'pending', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })
    
    // 解析结束事件
    doc.querySelectorAll('endEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `end_${index}`
      const pos = positionMap.get(id)
      nodes.push({ id, name: event.getAttribute('name') || '结束', type: 'end', status: 'pending', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })
    
    // 解析连线路径点
    const waypointsMap = new Map()
    doc.querySelectorAll('BPMNEdge, bpmndi\\:BPMNEdge').forEach(edge => {
      const bpmnElement = edge.getAttribute('bpmnElement')
      if (bpmnElement) {
        const waypoints: Array<{x: number, y: number}> = []
        edge.querySelectorAll('waypoint, di\\:waypoint').forEach(wp => {
          waypoints.push({ x: parseFloat(wp.getAttribute('x') || '0'), y: parseFloat(wp.getAttribute('y') || '0') })
        })
        if (waypoints.length > 0) waypointsMap.set(bpmnElement, waypoints)
      }
    })
    
    // 解析顺序流
    doc.querySelectorAll('sequenceFlow').forEach((flow, index) => {
      const id = flow.getAttribute('id') || `flow_${index}`
      flows.push({ id, sourceRef: flow.getAttribute('sourceRef') || '', targetRef: flow.getAttribute('targetRef') || '', name: flow.getAttribute('name') || '', waypoints: waypointsMap.get(id) })
    })
    
    processNodes.value = nodes
    processFlows.value = flows
    completedNodeIds.value = completed
  } catch (error) {
    console.error('Failed to parse BPMN XML:', error)
  }
}

// 解析表单配置
const parseFormConfig = (configStr: string) => {
  if (!configStr) return
  try {
    const config = typeof configStr === 'string' ? JSON.parse(configStr) : configStr
    const rules = config.rule && Array.isArray(config.rule) ? config.rule : (Array.isArray(config) ? config : null)
    if (rules) formFields.value = rules.map((rule: any) => convertFormCreateRule(rule)).filter(Boolean)
    // 检查表单是否只读
    formReadOnly.value = config.formReadOnly === true || config.formReadOnly === 'true'
  } catch (error) {
    console.error('Failed to parse form config:', error)
  }
}

// 转换表单规则
const convertFormCreateRule = (rule: any): FormField | null => {
  if (!rule || !rule.field) return null
  let dateType = 'date'
  if (rule.props?.type === 'datetime') dateType = 'datetime'
  else if (rule.props?.type === 'daterange') dateType = 'daterange'
  const typeMap: Record<string, string> = { 'input': 'text', 'inputNumber': 'number', 'select': 'select', 'radio': 'radio', 'checkbox': 'checkbox', 'switch': 'switch', 'datePicker': dateType, 'DatePicker': dateType, 'date-picker': dateType, 'el-date-picker': dateType, 'timePicker': 'time', 'cascader': 'cascader' }
  const field: FormField = { key: rule.field, label: rule.title || rule.field, type: typeMap[rule.type] || 'text', required: rule.validate?.some((v: any) => v.required) || false, placeholder: rule.props?.placeholder || '', span: rule.col?.span || 24 }
  if (rule.options) field.options = rule.options.map((opt: any) => ({ label: opt.label || opt.value, value: opt.value }))
  if (rule.type === 'input' && rule.props?.type === 'textarea') { field.type = 'textarea'; field.rows = rule.props?.rows || 3 }
  return field
}

const getHistoryStatus = (operationType: string): 'completed' | 'current' | 'pending' | 'rejected' => {
  const map: Record<string, 'completed' | 'current' | 'pending' | 'rejected'> = {
    'SUBMIT': 'completed',
    'APPROVE': 'completed',
    'REJECT': 'rejected',
    'DELEGATE': 'completed',
    'TRANSFER': 'completed',
    'CLAIM': 'completed',
    'PENDING': 'current'
  }
  return map[operationType] || 'completed'
}

const formatDate = (date?: string) => {
  return date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '-'
}

const getCurrentAssigneeDisplay = () => {
  if (taskInfo.value.assigneeName) {
    return taskInfo.value.assigneeName
  }
  if (taskInfo.value.assignee) {
    return taskInfo.value.assignee
  }
  if (taskInfo.value.candidateUsers) {
    const candidates = taskInfo.value.candidateUsers.split(',')
    if (candidates.length === 1) {
      return candidates[0]
    }
    return `${candidates.join(' / ')} (任一审批)`
  }
  return '-'
}

const getPriorityLabel = (priority?: string) => {
  const map: Record<string, string> = {
    'URGENT': '紧急',
    'HIGH': '高',
    'NORMAL': '普通',
    'LOW': '低'
  }
  return map[priority || ''] || priority || '普通'
}

const getPriorityType = (priority?: string): 'danger' | 'warning' | 'info' | 'success' => {
  const map: Record<string, 'danger' | 'warning' | 'info' | 'success'> = {
    'URGENT': 'danger',
    'HIGH': 'warning',
    'NORMAL': 'info',
    'LOW': 'success'
  }
  return map[priority || ''] || 'info'
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
  submitting.value = true
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
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

const submitAction = async () => {
  if (currentAction.value !== 'urge' && !actionForm.targetUserId) {
    ElMessage.warning('请选择目标用户')
    return
  }
  
  submitting.value = true
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
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadTaskDetail()
})
</script>


<style lang="scss" scoped>
.task-detail-page {
  max-width: 1200px;
  margin: 0 auto;
  
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
  
  .skeleton-content {
    display: flex;
    flex-direction: column;
  }
  
  .content-sections {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }
  
  .section {
    background: white;
    border-radius: 8px;
    border: 1px solid var(--border-color, #e4e7ed);
    
    .section-header {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 16px 20px;
      background: #fafafa;
      border-bottom: 1px solid var(--border-color, #e4e7ed);
      font-size: 16px;
      font-weight: 500;
      color: var(--text-primary);
      
      .el-icon {
        color: var(--hsbc-red, #db0011);
      }
    }
    
    .section-content {
      padding: 20px;
    }
  }
  
  .workflow-section {
    .section-content {
      min-height: 300px;
    }
  }
  
  .form-section {
    .form-container {
      max-width: 800px;
      margin: 0 auto;
    }
  }
  
  .history-section {
    .section-content {
      min-height: 100px;
    }
  }
  
  .action-section {
    position: sticky;
    bottom: 0;
    z-index: 10;
    
    .action-buttons {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px 20px;
      
      .left-actions,
      .right-actions {
        display: flex;
        gap: 12px;
      }
    }
  }
  
  .overdue {
    color: var(--error-red, #f56c6c);
  }
}
</style>
