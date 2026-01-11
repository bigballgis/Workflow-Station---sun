<template>
  <div class="process-start-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="$router.back()">返回</el-button>
      <h1>{{ functionUnitName || '发起流程' }}</h1>
      <el-tag v-if="functionUnitVersion" type="info" size="small">v{{ functionUnitVersion }}</el-tag>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="skeleton-content">
      <el-skeleton animated :count="3">
        <template #template>
          <el-skeleton-item variant="rect" style="height: 300px; margin-bottom: 20px;" />
          <el-skeleton-item variant="rect" style="height: 400px; margin-bottom: 20px;" />
          <el-skeleton-item variant="rect" style="height: 200px;" />
        </template>
      </el-skeleton>
    </div>
    
    <!-- 功能单元已禁用状态 -->
    <div v-else-if="isDisabled" class="disabled-state">
      <el-result icon="warning" title="功能单元已禁用" sub-title="该功能单元已被管理员禁用，暂时无法使用">
        <template #extra>
          <el-button type="primary" @click="$router.back()">返回</el-button>
          <el-button @click="$router.push('/processes')">查看其他流程</el-button>
        </template>
      </el-result>
    </div>
    
    <!-- 访问被拒绝状态 -->
    <div v-else-if="isAccessDenied" class="access-denied-state">
      <el-result icon="error" title="无访问权限" sub-title="您没有访问此功能单元的权限，请联系管理员">
        <template #extra>
          <el-button type="primary" @click="$router.back()">返回</el-button>
          <el-button @click="$router.push('/processes')">查看其他流程</el-button>
        </template>
      </el-result>
    </div>
    
    <!-- 加载错误状态 -->
    <div v-else-if="loadError" class="error-state">
      <el-result icon="error" title="加载失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" @click="loadFunctionUnitContent">重新加载</el-button>
          <el-button @click="$router.back()">返回</el-button>
        </template>
      </el-result>
    </div>
    
    <!-- 正常内容 -->
    <div v-else class="content-sections">
      <!-- 第一部分：实时工作流程图 -->
      <div class="section workflow-section">
        <div class="section-header">
          <el-icon><Share /></el-icon>
          <span>工作流程图</span>
          <el-tag type="success" size="small">开始节点</el-tag>
        </div>
        <div class="section-content">
          <ProcessDiagram
            v-if="processNodes.length > 0"
            :nodes="processNodes"
            :flows="processFlows"
            :current-node-id="currentNodeId"
            :completed-node-ids="[]"
            :show-toolbar="true"
            :show-legend="true"
          />
          <el-empty v-else description="暂无流程定义" />
        </div>
      </div>

      <!-- 第二部分：表单 -->
      <div class="section form-section">
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ currentFormName || '申请表单' }}</span>
        </div>
        <div class="section-content">
          <div v-if="formFields.length > 0" class="form-container">
            <FormRenderer
              ref="formRendererRef"
              :fields="formFields"
              v-model="formData"
              label-width="120px"
            />
          </div>
          <el-empty v-else description="暂无表单配置" />
        </div>
      </div>

      <!-- 第三部分：流转记录 -->
      <div class="section history-section">
        <div class="section-header">
          <el-icon><Clock /></el-icon>
          <span>流转记录</span>
        </div>
        <div class="section-content">
          <ProcessHistory
            :records="historyRecords"
            :show-header="false"
            :show-refresh="false"
          />
        </div>
      </div>

      <!-- 第四部分：动作按钮 -->
      <div class="section action-section">
        <div class="action-buttons">
          <div class="left-actions">
            <el-button @click="handleSaveDraft" :loading="savingDraft">
              <el-icon><FolderOpened /></el-icon> 保存草稿
            </el-button>
            <el-button @click="$router.back()">取消</el-button>
          </div>
          <div class="right-actions">
            <el-button 
              v-for="action in availableActions" 
              :key="action.id"
              :type="action.type || 'default'"
              @click="handleAction(action)"
              :loading="submitting && currentAction === action.id"
            >
              {{ action.label }}
            </el-button>
            <el-button 
              v-if="availableActions.length === 0"
              type="primary" 
              @click="handleSubmit"
              :loading="submitting"
            >
              <el-icon><Promotion /></el-icon> 提交
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Share, Document, Clock, FolderOpened, Promotion } from '@element-plus/icons-vue'
import { processApi } from '@/api/process'
import ProcessDiagram, { type ProcessNode, type ProcessFlow } from '@/components/ProcessDiagram.vue'
import ProcessHistory, { type HistoryRecord } from '@/components/ProcessHistory.vue'
import FormRenderer, { type FormField } from '@/components/FormRenderer.vue'

const route = useRoute()
const router = useRouter()

// 路由参数：key 是功能单元的 ID
const functionUnitId = computed(() => route.params.key as string)
const isDraftMode = computed(() => route.query.draft === 'true')

// 状态
const loading = ref(true)
const loadError = ref('')
const isDisabled = ref(false)
const isAccessDenied = ref(false)
const submitting = ref(false)
const savingDraft = ref(false)
const currentAction = ref('')

// 功能单元信息
const functionUnitName = ref('')
const functionUnitVersion = ref('')
const functionUnitCode = ref('')

// 流程图数据
const processNodes = ref<ProcessNode[]>([])
const processFlows = ref<ProcessFlow[]>([])
const currentNodeId = ref('')
const bpmnXml = ref('')

// 表单数据
const formFields = ref<FormField[]>([])
const formData = ref<Record<string, any>>({})
const currentFormName = ref('')
const formRendererRef = ref<InstanceType<typeof FormRenderer> | null>(null)

// 流转记录
const historyRecords = ref<HistoryRecord[]>([])

// 可用动作
const availableActions = ref<Array<{
  id: string
  label: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  action?: string
}>>([])

// 加载功能单元内容
const loadFunctionUnitContent = async () => {
  loading.value = true
  loadError.value = ''
  isDisabled.value = false
  isAccessDenied.value = false
  
  try {
    const response = await processApi.getFunctionUnitContent(functionUnitId.value)
    const content = response.data || response
    
    if (content.error) {
      loadError.value = content.error
      return
    }
    
    // 设置基本信息
    functionUnitName.value = content.name || ''
    functionUnitVersion.value = content.version || ''
    functionUnitCode.value = content.code || ''
    
    // 解析流程定义
    if (content.processes && content.processes.length > 0) {
      const processData = content.processes[0]
      bpmnXml.value = processData.data
      parseBpmnXml(processData.data)
    }
    
    // 解析表单定义
    if (content.forms && content.forms.length > 0) {
      const formContent = content.forms[0]
      currentFormName.value = formContent.name
      parseFormConfig(formContent.data)
    }
    
    // 初始化流转记录（新流程，只有开始节点）
    initHistoryRecords()
    
    // 初始化动作按钮
    initActionButtons()
    
    // 如果是草稿模式，加载草稿数据
    if (isDraftMode.value) {
      await loadDraftData()
    }
    
  } catch (error: any) {
    console.error('Failed to load function unit content:', error)
    
    // 检查是否是 403 错误（禁用或无权限）
    if (error.response?.status === 403) {
      const message = error.response?.data?.message || ''
      if (message.includes('禁用')) {
        isDisabled.value = true
      } else {
        isAccessDenied.value = true
      }
    } else {
      loadError.value = error.message || '加载功能单元内容失败'
    }
  } finally {
    loading.value = false
  }
}

// 加载草稿数据
const loadDraftData = async () => {
  try {
    const response = await processApi.getDraft(functionUnitCode.value || functionUnitId.value)
    const draft = response.data || response
    if (draft && draft.formData) {
      formData.value = draft.formData
      ElMessage.success('已加载草稿数据')
    }
  } catch (error) {
    console.error('Failed to load draft:', error)
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
    
    // 首先解析 BPMN DI 部分获取位置信息
    const positionMap = new Map<string, { x: number; y: number; width: number; height: number }>()
    
    // 查找所有 BPMNShape 元素（包含位置信息）
    const bpmnShapes = doc.querySelectorAll('BPMNShape, bpmndi\\:BPMNShape')
    bpmnShapes.forEach(shape => {
      const bpmnElement = shape.getAttribute('bpmnElement')
      if (bpmnElement) {
        // 查找 Bounds 子元素
        const bounds = shape.querySelector('Bounds, dc\\:Bounds')
        if (bounds) {
          const x = parseFloat(bounds.getAttribute('x') || '0')
          const y = parseFloat(bounds.getAttribute('y') || '0')
          const width = parseFloat(bounds.getAttribute('width') || '100')
          const height = parseFloat(bounds.getAttribute('height') || '80')
          positionMap.set(bpmnElement, { x, y, width, height })
        }
      }
    })
    
    // 解析开始事件
    const startEvents = doc.querySelectorAll('startEvent')
    startEvents.forEach((event, index) => {
      const id = event.getAttribute('id') || `start_${index}`
      const name = event.getAttribute('name') || '开始'
      const pos = positionMap.get(id)
      nodes.push({ 
        id, 
        name, 
        type: 'start', 
        status: 'current',
        x: pos?.x,
        y: pos?.y,
        width: pos?.width,
        height: pos?.height
      })
      if (index === 0) currentNodeId.value = id
    })
    
    // 解析用户任务
    const userTasks = doc.querySelectorAll('userTask')
    userTasks.forEach((task, index) => {
      const id = task.getAttribute('id') || `task_${index}`
      const name = task.getAttribute('name') || `任务${index + 1}`
      const pos = positionMap.get(id)
      nodes.push({ 
        id, 
        name, 
        type: 'task', 
        status: 'pending',
        x: pos?.x,
        y: pos?.y,
        width: pos?.width,
        height: pos?.height
      })
    })
    
    // 解析服务任务
    const serviceTasks = doc.querySelectorAll('serviceTask')
    serviceTasks.forEach((task, index) => {
      const id = task.getAttribute('id') || `service_${index}`
      const name = task.getAttribute('name') || `服务${index + 1}`
      const pos = positionMap.get(id)
      nodes.push({ 
        id, 
        name, 
        type: 'task', 
        status: 'pending',
        x: pos?.x,
        y: pos?.y,
        width: pos?.width,
        height: pos?.height
      })
    })
    
    // 解析网关
    const gateways = doc.querySelectorAll('exclusiveGateway, parallelGateway, inclusiveGateway')
    gateways.forEach((gateway, index) => {
      const id = gateway.getAttribute('id') || `gateway_${index}`
      const name = gateway.getAttribute('name') || ''
      const pos = positionMap.get(id)
      nodes.push({ 
        id, 
        name, 
        type: 'gateway', 
        status: 'pending',
        x: pos?.x,
        y: pos?.y,
        width: pos?.width,
        height: pos?.height
      })
    })
    
    // 解析结束事件
    const endEvents = doc.querySelectorAll('endEvent')
    endEvents.forEach((event, index) => {
      const id = event.getAttribute('id') || `end_${index}`
      const name = event.getAttribute('name') || '结束'
      const pos = positionMap.get(id)
      nodes.push({ 
        id, 
        name, 
        type: 'end', 
        status: 'pending',
        x: pos?.x,
        y: pos?.y,
        width: pos?.width,
        height: pos?.height
      })
    })
    
    // 解析连线的路径点（waypoints）
    const waypointsMap = new Map<string, Array<{ x: number; y: number }>>()
    const bpmnEdges = doc.querySelectorAll('BPMNEdge, bpmndi\\:BPMNEdge')
    bpmnEdges.forEach(edge => {
      const bpmnElement = edge.getAttribute('bpmnElement')
      if (bpmnElement) {
        const waypoints: Array<{ x: number; y: number }> = []
        const waypointElements = edge.querySelectorAll('waypoint, di\\:waypoint')
        waypointElements.forEach(wp => {
          const x = parseFloat(wp.getAttribute('x') || '0')
          const y = parseFloat(wp.getAttribute('y') || '0')
          waypoints.push({ x, y })
        })
        if (waypoints.length > 0) {
          waypointsMap.set(bpmnElement, waypoints)
        }
      }
    })
    
    // 解析顺序流
    const sequenceFlows = doc.querySelectorAll('sequenceFlow')
    sequenceFlows.forEach((flow, index) => {
      const id = flow.getAttribute('id') || `flow_${index}`
      const sourceRef = flow.getAttribute('sourceRef') || ''
      const targetRef = flow.getAttribute('targetRef') || ''
      const name = flow.getAttribute('name') || ''
      const waypoints = waypointsMap.get(id)
      flows.push({ id, sourceRef, targetRef, name, waypoints })
    })
    
    processNodes.value = nodes
    processFlows.value = flows
    
  } catch (error) {
    console.error('Failed to parse BPMN XML:', error)
  }
}

// 解析表单配置 - 将 form-create 规则转换为 FormRenderer 字段
const parseFormConfig = (configStr: string) => {
  if (!configStr) return
  
  try {
    const config = typeof configStr === 'string' ? JSON.parse(configStr) : configStr
    console.log('Parsing form config:', config)
    
    // 支持两种格式：
    // 1. { rule: [...], options: {...} } - form-create 设计器格式
    // 2. 直接的规则数组 [...]
    let rules = null
    if (config.rule && Array.isArray(config.rule)) {
      rules = config.rule
    } else if (Array.isArray(config)) {
      rules = config
    }
    
    if (rules) {
      // 将 form-create 规则转换为 FormRenderer 字段格式
      formFields.value = rules.map((rule: any) => convertFormCreateRule(rule)).filter(Boolean)
      console.log('Parsed form fields:', formFields.value)
    }
  } catch (error) {
    console.error('Failed to parse form config:', error)
  }
}

// 将 form-create 规则转换为 FormRenderer 字段
const convertFormCreateRule = (rule: any): FormField | null => {
  if (!rule || !rule.field) return null
  
  // 确定日期类型
  let dateType = 'date'
  if (rule.props?.type === 'datetime' || rule.props?.type === 'datetimerange') {
    dateType = 'datetime'
  } else if (rule.props?.type === 'daterange') {
    dateType = 'daterange'
  }
  
  const typeMap: Record<string, string> = {
    'input': 'text',
    'inputNumber': 'number',
    'select': 'select',
    'radio': 'radio',
    'checkbox': 'checkbox',
    'switch': 'switch',
    'datePicker': dateType,
    'DatePicker': dateType,
    'date-picker': dateType,
    'el-date-picker': dateType,
    'timePicker': 'time',
    'TimePicker': 'time',
    'time-picker': 'time',
    'el-time-picker': 'time',
    'cascader': 'cascader',
    'rate': 'number',
    'slider': 'number'
  }
  
  const field: FormField = {
    key: rule.field,
    label: rule.title || rule.field,
    type: typeMap[rule.type] || 'text',
    required: rule.validate?.some((v: any) => v.required) || false,
    placeholder: rule.props?.placeholder || `请输入${rule.title || rule.field}`,
    span: rule.col?.span || 24
  }
  
  // 处理选项
  if (rule.options) {
    field.options = rule.options.map((opt: any) => ({
      label: opt.label || opt.value,
      value: opt.value
    }))
  }
  
  // 处理 textarea
  if (rule.type === 'input' && rule.props?.type === 'textarea') {
    field.type = 'textarea'
    field.rows = rule.props?.rows || 3
  }
  
  // 处理数字输入
  if (rule.type === 'inputNumber') {
    field.min = rule.props?.min
    field.max = rule.props?.max
    field.step = rule.props?.step
    field.precision = rule.props?.precision
  }
  
  // 处理默认值
  if (rule.value !== undefined) {
    field.defaultValue = rule.value
  }
  
  // 调试输出
  console.log('Converting rule:', rule.type, '->', field.type, rule)
  
  return field
}

// 初始化流转记录
const initHistoryRecords = () => {
  historyRecords.value = [
    {
      id: 'init',
      nodeId: 'start',
      nodeName: '发起申请',
      status: 'current',
      createdTime: new Date().toISOString()
    }
  ]
}

// 初始化动作按钮
const initActionButtons = () => {
  availableActions.value = [
    {
      id: 'submit',
      label: '提交申请',
      type: 'primary',
      action: 'submit'
    }
  ]
}

// 保存草稿
const handleSaveDraft = async () => {
  savingDraft.value = true
  try {
    await processApi.saveDraft(functionUnitCode.value || functionUnitId.value, formData.value)
    ElMessage.success('草稿保存成功')
  } catch (error: any) {
    ElMessage.error(error.message || '保存草稿失败')
  } finally {
    savingDraft.value = false
  }
}

// 处理动作
const handleAction = async (action: { id: string; label: string; action?: string }) => {
  if (action.action === 'submit') {
    await handleSubmit()
  }
}

// 提交流程
const handleSubmit = async () => {
  // 验证表单
  if (formRendererRef.value) {
    const valid = await formRendererRef.value.validate()
    if (!valid) {
      ElMessage.warning('请完善表单信息')
      return
    }
  }
  
  submitting.value = true
  currentAction.value = 'submit'
  
  try {
    await processApi.startProcess(functionUnitCode.value || functionUnitId.value, {
      formData: formData.value,
      priority: 'NORMAL'
    })
    
    // 提交成功后删除草稿
    try {
      await processApi.deleteDraft(functionUnitCode.value || functionUnitId.value)
    } catch (e) {
      // 忽略删除草稿失败
    }
    
    ElMessage.success('流程提交成功')
    router.push('/my-applications')
    
  } catch (error: any) {
    ElMessage.error(error.message || '提交失败')
  } finally {
    submitting.value = false
    currentAction.value = ''
  }
}

onMounted(() => {
  loadFunctionUnitContent()
})
</script>

<style lang="scss" scoped>
.process-start-page {
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
  
  .error-state {
    padding: 40px 0;
  }
  
  .disabled-state,
  .access-denied-state {
    padding: 60px 0;
    background: white;
    border-radius: 8px;
    border: 1px solid var(--border-color);
  }
  
  .content-sections {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }
  
  .section {
    background: white;
    border-radius: 8px;
    border: 1px solid var(--border-color);
    
    .section-header {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 16px 20px;
      background: #fafafa;
      border-bottom: 1px solid var(--border-color);
      font-size: 16px;
      font-weight: 500;
      color: var(--text-primary);
      
      .el-icon {
        color: var(--hsbc-red);
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
}
</style>
