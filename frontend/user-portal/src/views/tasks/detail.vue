<template>
  <div class="task-detail-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="$router.back()">{{ t('common.back') }}</el-button>
      <h1>{{ taskInfo.taskName || t('task.detail') }}</h1>
      <el-tag :type="getPriorityType(taskInfo.priority)" size="small">
        {{ getPriorityLabel(taskInfo.priority) }}
      </el-tag>
      <el-tag v-if="taskInfo.isOverdue" type="danger" size="small">{{ t('task.overdue') }}</el-tag>
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

    <!-- 任务加载错误 -->
    <div v-else-if="taskError" class="error-content">
      <el-result icon="warning" :title="taskError">
        <template #extra>
          <el-button type="primary" @click="$router.back()">{{ t('common.back') }}</el-button>
          <el-button @click="loadTaskDetail">{{ t('common.reset') }}</el-button>
        </template>
      </el-result>
    </div>

    <!-- 正常内容 -->
    <div v-else class="content-sections">
      <!-- 第一部分：基本信息 -->
      <div class="section info-section">
        <div class="section-header">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ t('task.basicInfo') }}</span>
        </div>
        <div class="section-content">
          <el-descriptions :column="3" border>
            <el-descriptions-item :label="t('task.taskName')">
              {{ taskInfo.taskName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('task.processName')">
              {{ taskInfo.processDefinitionName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('task.initiator')">
              {{ taskInfo.initiatorName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('task.createTime')">
              {{ formatDate(taskInfo.createTime) }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('task.dueDate')">
              <span :class="{ 'overdue': taskInfo.isOverdue }">
                {{ taskInfo.dueDate ? formatDate(taskInfo.dueDate) : '-' }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item :label="t('task.currentAssignee')">
              {{ getCurrentAssigneeDisplay() }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <!-- 第二部分：流程图 -->
      <div class="section workflow-section">
        <div class="section-header">
          <el-icon><Share /></el-icon>
          <span>{{ t('task.workflowDiagram') }}</span>
          <el-tag type="warning" size="small">
            {{ taskInfo.taskName || t('task.pending') }}
          </el-tag>
        </div>
        <div class="section-content">
          <el-alert v-if="processError" :title="processError" type="warning" show-icon :closable="false" />
          <ProcessDiagram
            v-else-if="processNodes.length > 0"
            :nodes="processNodes"
            :flows="processFlows"
            :current-node-id="currentNodeId"
            :completed-node-ids="completedNodeIds"
            :show-toolbar="true"
            :show-legend="true"
          />
          <el-empty v-else :description="t('task.noProcessDefinition')" />
        </div>
      </div>

      <!-- 第三部分：表单数据 -->
      <div class="section form-section">
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ currentFormName || t('task.taskForm') }}</span>
        </div>
        <div class="section-content">
          <div v-if="formFields.length > 0 || formTabs.length > 0" class="form-container">
            <FormRenderer
              :fields="formFields"
              :tabs="formTabs"
              v-model="formData"
              label-width="120px"
              :readonly="formReadOnly"
            />
          </div>
          <el-empty v-else :description="t('task.noFormData')" />
        </div>
      </div>

      <!-- 第四部分：流转记录 -->
      <div class="section history-section">
        <div class="section-header">
          <el-icon><Clock /></el-icon>
          <span>{{ t('task.flowHistory') }}</span>
        </div>
        <div class="section-content">
          <el-alert v-if="historyError" :title="historyError" type="warning" show-icon :closable="false" />
          <ProcessHistory
            v-else-if="historyRecords.length > 0"
            :records="historyRecords"
            :show-header="false"
            :show-refresh="false"
          />
          <el-empty v-else :description="t('task.noFlowHistory')" />
        </div>
      </div>

      <!-- 第五部分：操作按钮 -->
      <div class="section action-section">
        <div class="action-buttons">
          <div class="left-actions">
            <el-button @click="$router.back()">{{ t('task.backToList') }}</el-button>
          </div>
          <div class="right-actions">
            <el-button type="success" @click="handleApprove">
              <el-icon><Check /></el-icon> {{ t('task.approve') }}
            </el-button>
            <el-button type="danger" @click="handleReject">
              <el-icon><Close /></el-icon> {{ t('task.reject') }}
            </el-button>
            <el-button @click="handleDelegate">
              <el-icon><User /></el-icon> {{ t('task.delegate') }}
            </el-button>
            <el-button @click="handleTransfer">
              <el-icon><Switch /></el-icon> {{ t('task.transfer') }}
            </el-button>
            <el-button type="warning" @click="handleUrge">
              <el-icon><Bell /></el-icon> {{ t('task.urge') }}
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 审批对话框 -->
    <el-dialog v-model="approveDialogVisible" :title="approveDialogTitle" width="500px">
      <el-form :model="approveForm" label-width="80px">
        <el-form-item :label="t('task.comment')">
          <el-input v-model="approveForm.comment" type="textarea" :rows="4" :placeholder="t('task.commentPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitApprove" :loading="submitting">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 委托/转办对话框 -->
    <el-dialog v-model="actionDialogVisible" :title="actionDialogTitle" width="500px">
      <el-form :model="actionForm" label-width="80px">
        <el-form-item :label="t('task.targetUser')" v-if="currentAction !== 'urge'">
          <el-select v-model="actionForm.targetUserId" filterable :placeholder="t('task.selectUser')" style="width: 100%;">
            <el-option label="Li Si" value="user_2" />
            <el-option label="Wang Wu" value="user_3" />
            <el-option label="Zhao Liu" value="user_4" />
          </el-select>
        </el-form-item>
        <el-form-item :label="currentAction === 'urge' ? t('task.urgeMessage') : t('task.reasonDescription')">
          <el-input 
            v-model="actionForm.reason" 
            type="textarea" 
            :rows="3" 
            :placeholder="currentAction === 'urge' ? t('task.urgeMessagePlaceholder') : t('task.reasonPlaceholder')" 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitAction" :loading="submitting">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
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
import { historyApi } from '@/api/history'
import { useUserStore } from '@/stores/user'
import ProcessDiagram, { type ProcessNode, type ProcessFlow } from '@/components/ProcessDiagram.vue'
import ProcessHistory, { type HistoryRecord } from '@/components/ProcessHistory.vue'
import FormRenderer, { type FormField, type FormTab } from '@/components/FormRenderer.vue'
import dayjs from 'dayjs'

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const router = useRouter()

const taskId = route.params.id as string
const loading = ref(true)
const submitting = ref(false)
const taskInfo = ref<Partial<TaskInfo>>({})

// 错误状态
const taskError = ref<string | null>(null)
const processError = ref<string | null>(null)
const historyError = ref<string | null>(null)

// 流程图数据
const processNodes = ref<ProcessNode[]>([])
const processFlows = ref<ProcessFlow[]>([])
const currentNodeId = ref('')
const completedNodeIds = ref<string[]>([])

// 表单数据
const formFields = ref<FormField[]>([])
const formTabs = ref<FormTab[]>([])
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
  taskError.value = null
  try {
    const res = await getTaskDetail(taskId)
    const data = res.data || res
    if (data) {
      taskInfo.value = data
      if (data.variables) formData.value = data.variables
      // 加载功能单元内容（流程图和表单）
      // 优先使用 functionUnitId，如果没有则使用 processDefinitionKey
      const functionUnitIdOrKey = data.functionUnitId || data.processDefinitionKey
      if (functionUnitIdOrKey) {
        await loadFunctionUnitContent(functionUnitIdOrKey)
      }
      // 加载流转历史
      await loadTaskHistory()
    }
  } catch (error: any) {
    console.error('Failed to load task detail:', error)
    // 根据错误状态码显示不同的错误消息
    const status = error.response?.status
    if (status === 404) {
      taskError.value = t('task.notFound')
    } else if (status === 403) {
      taskError.value = t('task.noPermission')
    } else {
      taskError.value = t('task.serverError')
    }
    ElMessage.error(taskError.value)
  } finally {
    loading.value = false
  }
}

const loadTaskHistory = async () => {
  historyError.value = null
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
    historyError.value = t('task.historyLoadFailed')
    historyRecords.value = []
  }
}

// 加载功能单元内容
const loadFunctionUnitContent = async (processKey: string) => {
  processError.value = null
  try {
    const response = await processApi.getFunctionUnitContent(processKey)
    const content = response.data || response
    if (content.error) {
      console.error('Function unit content error:', content.error)
      processError.value = t('task.processLoadFailed')
      return
    }
    
    let currentFormInfo: { formId: string | null, formName: string | null } = { formId: null, formName: null }
    
    // 解析流程图
    if (content.processes?.length > 0) {
      // 先获取当前节点的 formId 和 formName
      currentFormInfo = parseBpmnXmlAndGetFormId(content.processes[0].data)
      await parseBpmnXml(content.processes[0].data)
    }
    
    // 解析表单 - 根据当前节点的 formId 选择正确的表单
    if (content.forms?.length > 0) {
      let selectedForm = content.forms[0] // 默认第一个
      
      // 优先使用 formId 匹配 sourceId（原始表单ID）
      if (currentFormInfo.formId) {
        const matchedForm = content.forms.find((f: any) => 
          String(f.sourceId) === currentFormInfo.formId
        )
        if (matchedForm) {
          selectedForm = matchedForm
          console.log('Matched form by sourceId:', currentFormInfo.formId, '->', selectedForm.name)
        } else {
          // 如果 sourceId 匹配失败，尝试用 formName 匹配
          if (currentFormInfo.formName) {
            const matchedByName = content.forms.find((f: any) => f.name === currentFormInfo.formName)
            if (matchedByName) {
              selectedForm = matchedByName
              console.log('Matched form by name:', currentFormInfo.formName)
            }
          }
        }
      } else if (currentFormInfo.formName) {
        // 如果没有 formId，尝试用 formName 匹配
        const matchedForm = content.forms.find((f: any) => f.name === currentFormInfo.formName)
        if (matchedForm) {
          selectedForm = matchedForm
        }
      }
      
      currentFormName.value = selectedForm.name
      parseFormConfig(selectedForm.data)
    }
  } catch (error: any) {
    console.error('Failed to load function unit content:', error)
    // 403 错误表示功能单元被禁用或无权限
    if (error.response?.status === 403) {
      processError.value = t('task.noPermission')
    } else {
      processError.value = t('task.processLoadFailed')
    }
  }
}

// 解析 BPMN XML 并获取当前节点的 formId 和 formName
const parseBpmnXmlAndGetFormId = (xml: string): { formId: string | null, formName: string | null } => {
  if (!xml) return { formId: null, formName: null }
  
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const currentTaskName = taskInfo.value.taskName || ''
    
    // 查找所有 userTask 节点
    const allElements = doc.getElementsByTagName('*')
    
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      
      if (localName === 'userTask') {
        const taskId = el.getAttribute('id') || ''
        const taskName = el.getAttribute('name') || ''
        
        // 检查是否是当前任务节点
        if (taskName === currentTaskName || taskId === currentTaskName) {
          // 查找 formId 和 formName 属性
          let formId: string | null = null
          let formName: string | null = null
          
          const taskProps = el.getElementsByTagName('*')
          for (let j = 0; j < taskProps.length; j++) {
            const prop = taskProps[j]
            const propLocalName = prop.localName || prop.nodeName.split(':').pop()
            
            if (propLocalName === 'property' || propLocalName === 'values') {
              const name = prop.getAttribute('name')
              const value = prop.getAttribute('value')
              
              if (name === 'formId' && value) {
                formId = value
              }
              if (name === 'formName' && value) {
                formName = value
              }
            }
          }
          
          return { formId, formName }
        }
      }
    }
  } catch (error) {
    console.error('Failed to parse BPMN for formId:', error)
  }
  
  return { formId: null, formName: null }
}

// 解析 BPMN XML
const parseBpmnXml = async (xml: string) => {
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
    
    // 从后端获取实际执行的活动ID列表
    let executedActivityIds: string[] = []
    if (taskInfo.value.processInstanceId) {
      try {
        executedActivityIds = await historyApi.getExecutedActivityIds(taskInfo.value.processInstanceId)
        console.log('Executed activity IDs:', executedActivityIds)
      } catch (error) {
        console.error('Failed to load executed activities, using fallback logic:', error)
      }
    }
    
    // 获取当前任务名称
    const currentTaskName = taskInfo.value.taskName || ''
    
    // 解析开始事件
    doc.querySelectorAll('startEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `start_${index}`
      const pos = positionMap.get(id)
      const isExecuted = executedActivityIds.includes(id)
      nodes.push({ 
        id, 
        name: event.getAttribute('name') || '开始', 
        type: 'start', 
        status: isExecuted ? 'completed' : 'pending', 
        x: pos?.x, 
        y: pos?.y, 
        width: pos?.width, 
        height: pos?.height 
      })
      if (isExecuted) completed.push(id)
    })
    
    // 解析用户任务
    doc.querySelectorAll('userTask').forEach((task, index) => {
      const id = task.getAttribute('id') || `task_${index}`
      const name = task.getAttribute('name') || `任务${index + 1}`
      const pos = positionMap.get(id)
      
      let status: 'completed' | 'current' | 'pending' = 'pending'
      
      // 判断是否为当前节点
      if (name === currentTaskName || id === currentTaskName) {
        status = 'current'
        currentNodeId.value = id
      } else if (executedActivityIds.includes(id)) {
        // 使用后端返回的已执行活动列表
        status = 'completed'
        completed.push(id)
      }
      
      nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })
    
    // 解析网关
    doc.querySelectorAll('exclusiveGateway, parallelGateway, inclusiveGateway').forEach((gateway, index) => {
      const id = gateway.getAttribute('id') || `gateway_${index}`
      const pos = positionMap.get(id)
      const isExecuted = executedActivityIds.includes(id)
      nodes.push({ 
        id, 
        name: gateway.getAttribute('name') || '', 
        type: 'gateway', 
        status: isExecuted ? 'completed' : 'pending', 
        x: pos?.x, 
        y: pos?.y, 
        width: pos?.width, 
        height: pos?.height 
      })
      if (isExecuted) completed.push(id)
    })
    
    // 解析结束事件
    doc.querySelectorAll('endEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `end_${index}`
      const pos = positionMap.get(id)
      const isExecuted = executedActivityIds.includes(id)
      nodes.push({ 
        id, 
        name: event.getAttribute('name') || '结束', 
        type: 'end', 
        status: isExecuted ? 'completed' : 'pending', 
        x: pos?.x, 
        y: pos?.y, 
        width: pos?.width, 
        height: pos?.height 
      })
      if (isExecuted) completed.push(id)
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
      
      // 如果连线已执行，也添加到 completed 列表中
      if (executedActivityIds.includes(id)) {
        completed.push(id)
      }
    })
    
    processNodes.value = nodes
    processFlows.value = flows
    completedNodeIds.value = completed
  } catch (error) {
    console.error('Failed to parse BPMN XML:', error)
    processError.value = t('task.processLoadFailed')
  }
}

// 解析表单配置
const parseFormConfig = (configStr: string) => {
  if (!configStr) return
  try {
    const config = typeof configStr === 'string' ? JSON.parse(configStr) : configStr
    const rules = config.rule && Array.isArray(config.rule) ? config.rule : (Array.isArray(config) ? config : null)
    if (rules) {
      // 检查是否有 el-tabs 结构
      const tabsRule = rules.find((r: any) => r.type === 'el-tabs')
      
      if (tabsRule && tabsRule.children && Array.isArray(tabsRule.children)) {
        // 有 Tab 布局
        const tabs: FormTab[] = []
        
        for (const tabPane of tabsRule.children) {
          if (tabPane.type === 'el-tab-pane' && tabPane.props) {
            const tabName = tabPane.props.name || `tab_${tabs.length}`
            const tabLabel = tabPane.props.label || `Tab ${tabs.length + 1}`
            
            const tabFields: FormField[] = []
            if (tabPane.children && Array.isArray(tabPane.children)) {
              for (const item of tabPane.children) {
                if (item.field) {
                  const field = convertFormCreateRule(item)
                  if (field) tabFields.push(field)
                }
                if (item.children && Array.isArray(item.children)) {
                  tabFields.push(...extractFieldsRecursive(item.children))
                }
              }
            }
            
            tabs.push({ name: tabName, label: tabLabel, fields: tabFields })
          }
        }
        
        formTabs.value = tabs
        formFields.value = []
      } else {
        // 无 Tab 布局，使用平铺模式
        formTabs.value = []
        formFields.value = extractFieldsRecursive(rules)
      }
    }
    // 检查表单是否只读
    formReadOnly.value = config.formReadOnly === true || config.formReadOnly === 'true'
  } catch (error) {
    console.error('Failed to parse form config:', error)
  }
}

// 递归提取字段
const extractFieldsRecursive = (items: any[]): FormField[] => {
  const fields: FormField[] = []
  for (const item of items) {
    if (item.field) {
      const field = convertFormCreateRule(item)
      if (field) fields.push(field)
    }
    if (item.children && Array.isArray(item.children)) {
      fields.push(...extractFieldsRecursive(item.children))
    }
  }
  return fields
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
    return `${candidates.join(' / ')} (${t('task.anyApprove')})`
  }
  return '-'
}

const getPriorityLabel = (priority?: string) => {
  const map: Record<string, string> = {
    'URGENT': t('task.urgent'),
    'HIGH': t('task.high'),
    'NORMAL': t('task.normal'),
    'LOW': t('task.low')
  }
  return map[priority || ''] || priority || t('task.normal')
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
    // 根据审批动作设置流程变量
    const variables: Record<string, any> = {
      ...formData.value  // 包含所有表单数据，包括子表单
    }
    
    if (currentApproveAction.value === 'APPROVE') {
      variables.approval_result = 'approved'
      variables.approved = true
    } else if (currentApproveAction.value === 'REJECT') {
      variables.approval_result = 'rejected'
      variables.approved = false
    }
    
    // 添加审批意见
    if (approveForm.comment) {
      variables.approval_comment = approveForm.comment
    }
    
    // 记录日志以便调试
    console.log('Submitting task with variables:', variables)
    
    await completeTask(taskId, {
      taskId: taskId,
      userId: userStore.userInfo?.username || 'admin',
      action: currentApproveAction.value,
      comment: approveForm.comment,
      variables: variables
    })
    ElMessage.success('操作成功')
    approveDialogVisible.value = false
    router.push('/tasks')
  } catch (error) {
    console.error('Failed to complete task:', error)
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

const submitAction = async () => {
  if (currentAction.value !== 'urge' && !actionForm.targetUserId) {
    ElMessage.warning(t('task.selectUser'))
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
  
  .error-content {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 400px;
    background: white;
    border-radius: 8px;
    border: 1px solid var(--border-color, #e4e7ed);
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
      width: 100%;
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
