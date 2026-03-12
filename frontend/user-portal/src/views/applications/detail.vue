<template>
  <div class="application-detail-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" @click="$router.back()">{{ t('applicationDetail.back') }}</el-button>
        <h1>{{ processInfo.processDefinitionName || t('applicationDetail.applicationDetail') }}</h1>
        <el-tag :type="getStatusType(processInfo.status)" size="small">{{ getStatusLabel(processInfo.status) }}</el-tag>
      </div>
      <el-button :icon="Refresh" @click="loadProcessDetail" :loading="loading">{{ t('applicationDetail.refresh') }}</el-button>
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
          <span>{{ t('applicationDetail.basicInfo') }}</span>
        </div>
        <div class="section-content">
          <el-descriptions :column="3" border>
            <el-descriptions-item :label="t('applicationDetail.processTitle')">
              {{ processInfo.businessKey || processInfo.processDefinitionName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('applicationDetail.processType')">
              {{ processInfo.processDefinitionName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('applicationDetail.initiator')">
              {{ processInfo.startUserName || processInfo.startUserId || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('applicationDetail.initiateTime')">
              {{ formatDate(processInfo.startTime) }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('applicationDetail.currentStep')">
              {{ processInfo.currentNode || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('applicationDetail.currentAssignee')">
              {{ getCurrentAssigneeDisplay() }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <!-- 第二部分：流程图 -->
      <div class="section workflow-section">
        <div class="section-header">
          <el-icon><Share /></el-icon>
          <span>{{ t('applicationDetail.workflowDiagram') }}</span>
          <el-tag :type="getNodeStatusType(processInfo.status)" size="small">
            {{ processInfo.currentNode || t('applicationDetail.pending') }}
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
          <el-empty v-else :description="t('applicationDetail.noProcessDefinition')" />
        </div>
      </div>

      <!-- 第三部分：申请内容（start 节点表单，只读） -->
      <div v-if="hasStartForm" class="section form-section">
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ startFormName || t('applicationDetail.applicationForm') }}</span>
          <el-tag type="info" size="small">{{ t('applicationDetail.completed') }}</el-tag>
        </div>
        <div class="section-content">
          <div v-if="startFormFields.length > 0 || startFormTabs.length > 0" class="form-container">
            <FormRenderer
              :fields="startFormFields"
              :tabs="startFormTabs"
              v-model="formData"
              :label-width="startFormLabelWidth"
              :readonly="true"
            />
          </div>
          <template v-if="startSubTableBindings.length > 0">
            <div v-for="binding in startSubTableBindings" :key="binding.bindingId" class="sub-table-section">
              <SubTableField
                :title="binding.tableName"
                :columns="binding.columns"
                v-model="binding.data"
                :editable="false"
              />
            </div>
          </template>
        </div>
      </div>

      <!-- 表单数据 -->
      <div class="section form-section">
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ currentFormName || t('applicationDetail.applicationForm') }}</span>
        </div>
        <div class="section-content">
          <div v-if="formFields.length > 0 || formTabs.length > 0" class="form-container">
            <FormRenderer
              :fields="formFields"
              :tabs="formTabs"
              v-model="formData"
              :label-width="formLabelWidth"
              :readonly="true"
            />
          </div>
          <el-empty v-else :description="t('applicationDetail.noFormData')" />

          <!-- Sub-tables (SUB / RELATED bindings) -->
          <template v-if="subTableBindings.length > 0">
            <div
              v-for="binding in subTableBindings"
              :key="binding.bindingId"
              class="sub-table-section"
            >
              <SubTableField
                :title="binding.tableName"
                :columns="binding.columns"
                v-model="binding.data"
                :editable="false"
              />
            </div>
          </template>
        </div>
      </div>

      <!-- 第四部分：流转记录 -->
      <div class="section history-section">
        <div class="section-header">
          <el-icon><Clock /></el-icon>
          <span>{{ t('applicationDetail.flowHistory') }}</span>
        </div>
        <div class="section-content">
          <ProcessHistory
            :records="historyRecords.filter(r => !r.activityType?.includes('Gateway'))"
            :show-header="false"
            :show-refresh="false"
          />
        </div>
      </div>

      <!-- 第五部分：操作按钮 -->
      <div v-if="processInfo.status === 'RUNNING'" class="section action-section">
        <div class="action-buttons">
          <div class="left-actions">
            <el-button @click="$router.back()">{{ t('applicationDetail.back') }}</el-button>
          </div>
          <div class="right-actions">
            <el-button type="warning" @click="handleUrge" :loading="urging">
              <el-icon><Bell /></el-icon> {{ t('applicationDetail.urge') }}
            </el-button>
            <el-button type="danger" @click="handleWithdraw" :loading="withdrawing">
              <el-icon><RefreshLeft /></el-icon> {{ t('applicationDetail.withdraw') }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, InfoFilled, Share, Document, Clock, Bell, RefreshLeft, Refresh } from '@element-plus/icons-vue'
import { processApi, type ProcessInstance } from '@/api/process'
import ProcessDiagram, { type ProcessNode, type ProcessFlow } from '@/components/ProcessDiagram.vue'
import ProcessHistory, { type HistoryRecord } from '@/components/ProcessHistory.vue'
import FormRenderer, { type FormField, type FormTab } from '@/components/FormRenderer.vue'
import SubTableField from '@/components/SubTableField.vue'
import { formatDate } from '@/utils/dateFormat'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const processId = route.params.id as string
// 从 completed tasks 进来时携带的快照时间，用于只展示该时刻之前的流程状态
const snapshotTime = route.query.snapshotTime as string | undefined
// 从 completed tasks 进来时携带的任务名称，用于高亮该节点为 current
const snapshotTaskName = route.query.snapshotTaskName as string | undefined

const loading = ref(true)
const urging = ref(false)
const withdrawing = ref(false)
const processInfo = ref<ProcessInstance>({} as ProcessInstance)

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
const formLabelWidth = ref('160px')

// Sub-table bindings
const subTableBindings = ref<Array<{
  bindingId: number
  bindingType: string
  bindingMode: string
  foreignKeyField: string | null
  tableName: string
  tableType: string
  tableDescription: string
  columns: Array<{ field: string; label: string; type?: string }>
  data: any[]
}>>([])

// 申请内容（start 节点表单，只读展示）
const startFormFields = ref<FormField[]>([])
const startFormTabs = ref<FormTab[]>([])
const startFormName = ref('')
const startFormLabelWidth = ref('160px')
const startSubTableBindings = ref<Array<{
  bindingId: number
  bindingType: string
  bindingMode: string
  foreignKeyField: string | null
  tableName: string
  tableType: string
  tableDescription: string
  columns: Array<{ field: string; label: string; type?: string }>
  data: any[]
}>>([])
const hasStartForm = ref(false)

// 流转记录
const historyRecords = ref<HistoryRecord[]>([])

const getCurrentAssigneeDisplay = () => {
  // 如果有直接分配的处理人
  if (processInfo.value.currentAssignee) {
    return processInfo.value.currentAssignee
  }
  // 如果有候选用户（或签场景）
  if (processInfo.value.candidateUsers) {
    const candidates = processInfo.value.candidateUsers.split(',')
    if (candidates.length === 1) {
      return candidates[0]
    }
    return `${candidates.join(' / ')} (${t('applicationDetail.anyApprove')})`
  }
  return '-'
}

const getStatusType = (status?: string): 'success' | 'warning' | 'info' | 'danger' => {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger'> = { RUNNING: 'warning', COMPLETED: 'success', WITHDRAWN: 'info', REJECTED: 'danger' }
  return map[status || ''] || 'info'
}

const getStatusLabel = (status?: string) => {
  const map: Record<string, string> = { RUNNING: t('applicationDetail.running'), COMPLETED: t('applicationDetail.completed'), WITHDRAWN: t('applicationDetail.withdrawn'), REJECTED: t('applicationDetail.rejected') }
  return map[status || ''] || status || '-'
}

const getNodeStatusType = (status?: string): 'success' | 'warning' | 'info' => {
  if (status === 'COMPLETED') return 'success'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

// 根据流转历史和 BPMN 流程图，找到指定任务完成后的下一个节点名称
const findNextNodeName = (taskName: string): string | null => {
  const taskNode = processNodes.value.find(n => n.name === taskName)
  if (!taskNode) return null

  // 对于 "Submit Request" 这种直接连接到下一个 userTask 的情况，直接返回
  // 对于经过网关的情况，需要根据流程实际走过的路径来判断
  // 我们可以利用原始的 processInfo.currentNode（数据库中的最终节点）来辅助判断
  const originalCurrentNode = processInfo.value.currentNode || ''

  const visited = new Set<string>()
  const queue = [taskNode.id]
  const candidates: string[] = []

  while (queue.length > 0) {
    const nodeId = queue.shift()!
    if (visited.has(nodeId)) continue
    visited.add(nodeId)

    const outFlows = processFlows.value.filter(f => f.sourceRef === nodeId)
    for (const flow of outFlows) {
      const target = processNodes.value.find(n => n.id === flow.targetRef)
      if (!target) continue
      if (target.type === 'gateway') {
        queue.push(target.id)
      } else {
        // 非网关节点：如果只有一条出路，直接返回
        if (outFlows.length === 1 && candidates.length === 0) return target.name
        candidates.push(target.name)
      }
    }
  }

  if (candidates.length === 0) return null
  if (candidates.length === 1) return candidates[0]

  // 多个候选（网关分支）：优先匹配数据库中记录的最终节点
  if (originalCurrentNode && candidates.includes(originalCurrentNode)) {
    return originalCurrentNode
  }
  // 否则返回第一个
  return candidates[0]
}

// 加载流程详情
const loadProcessDetail = async () => {
  console.log('=== loadProcessDetail called for processId:', processId)
  loading.value = true
  try {
    const res = await processApi.getProcessDetail(processId)
    console.log('=== Process detail response:', res)
    const data = res.data || res
    if (data) {
      processInfo.value = data
      if (data.variables) formData.value = data.variables
      
      // 先加载流转历史
      await loadProcessHistory()
      
      // 然后加载功能单元内容（包括 BPMN 解析）
      if (data.processDefinitionKey) {
        try {
          await loadFunctionUnitContent(data.processDefinitionKey)
        } catch (error) {
          console.error('Failed to load function unit content, but continuing:', error)
        }
      }

      // 快照模式：用 BPMN 流程图计算 snapshotTaskName 之后的下一个节点作为 currentNode
      if (snapshotTaskName && processNodes.value.length > 0 && processFlows.value.length > 0) {
        const nextNodeName = findNextNodeName(snapshotTaskName)
        if (nextNodeName) {
          processInfo.value = { ...processInfo.value, currentNode: nextNodeName }
          // 把下一个节点在流程图中标记为相应状态
          const nextNode = processNodes.value.find(n => n.name === nextNodeName)
          if (nextNode) {
            if (nextNode.type === 'end') {
              // 结束事件：Rejected 用红色，其他（如 Approved）用绿色
              if (nextNodeName.toLowerCase().includes('rejected') || nextNodeName.toLowerCase().includes('拒绝')) {
                nextNode.status = 'rejected'
              } else {
                nextNode.status = 'completed'
                if (!completedNodeIds.value.includes(nextNode.id)) {
                  completedNodeIds.value.push(nextNode.id)
                }
              }
            } else {
              // 非结束事件（如下一个 userTask）：标记为 current（橘色）
              nextNode.status = 'current'
            }
            currentNodeId.value = nextNode.id
          }
        }
      }
    }
  } catch (error) {
    console.error('Failed to load process detail:', error)
    ElMessage.error(t('applicationDetail.loadFailed'))
  } finally {
    loading.value = false
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
    
    let currentFormInfo: { formId: string | null, formName: string | null } = { formId: null, formName: null }
    
    if (content.processes?.length > 0) {
      // 解析 BPMN 并获取当前节点的 formId 和 formName
      currentFormInfo = parseBpmnXmlAndGetFormId(content.processes[0].data)
      parseBpmnXml(content.processes[0].data)
    }
    
    if (content.forms?.length > 0) {
      // 根据当前节点的 formId 选择正确的表单
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

      // Parse subForms from configJson for column definitions
      let subForms: Record<string, any> = {}
      try {
        const cfg = typeof selectedForm.data === 'string' ? JSON.parse(selectedForm.data) : (selectedForm.data || {})
        subForms = cfg.subForms || {}
      } catch {}

      // Load sub-table bindings (SUB and RELATED, not PRIMARY)
      const bindings: typeof subTableBindings.value = []
      const tableBindings: any[] = selectedForm.tableBindings || []
      for (const b of tableBindings) {
        if (b.bindingType === 'PRIMARY') continue
        const columns = deriveColumnsFromBinding(b, subForms)
        bindings.push({
          bindingId: b.bindingId,
          bindingType: b.bindingType,
          bindingMode: b.bindingMode,
          foreignKeyField: b.foreignKeyField,
          tableName: b.tableDisplayName || b.tableName,
          tableType: b.tableType,
          tableDescription: b.tableDescription,
          columns,
          data: []
        })
      }
      // 从 variables 中恢复子表数据
      // 注意：JSON 序列化后 key 变为 string，需同时用 number 和 string 查找
      const savedSubTables = formData.value.__subTables__
      if (savedSubTables && typeof savedSubTables === 'object') {
        bindings.forEach(binding => {
          const saved = savedSubTables[binding.bindingId] ?? savedSubTables[String(binding.bindingId)]
          if (Array.isArray(saved)) {
            binding.data = saved
          }
        })
      }
      subTableBindings.value = bindings

      // 额外加载 start 节点表单（Request Form），只读展示申请内容
      // 当当前节点有专属表单时，start form 作为额外的只读区域展示
      // 当匹配不到当前节点表单时（如流程已完成），也尝试加载 start form
      if (content.processes?.length > 0) {
        const startFormInfo = parseBpmnXmlAndGetStartFormId(content.processes[0].data)
        const startFormId = startFormInfo.formId
        const startFormName2 = startFormInfo.formName
        let startForm: any = null

        if (currentFormInfo.formId || currentFormInfo.formName) {
          // 当前节点有专属表单，只在 start form 不同时才额外展示
          if (startFormId && String(selectedForm.sourceId) !== startFormId) {
            startForm = content.forms.find((f: any) => String(f.sourceId) === startFormId)
            if (!startForm && startFormName2) {
              startForm = content.forms.find((f: any) => f.name === startFormName2)
            }
          } else if (!startFormId && startFormName2 && selectedForm.name !== startFormName2) {
            startForm = content.forms.find((f: any) => f.name === startFormName2)
          }
        } else {
          // 当前节点没有匹配到专属表单（如流程已完成），尝试找 start form
          // 如果 start form 和默认选中的表单不同，则额外展示
          if (startFormId) {
            const matched = content.forms.find((f: any) => String(f.sourceId) === startFormId)
            if (matched && matched.id !== selectedForm.id) {
              startForm = matched
            } else if (!matched && startFormName2) {
              const matchedByName = content.forms.find((f: any) => f.name === startFormName2)
              if (matchedByName && matchedByName.id !== selectedForm.id) {
                startForm = matchedByName
              }
            }
          } else if (startFormName2) {
            const matched = content.forms.find((f: any) => f.name === startFormName2)
            if (matched && matched.id !== selectedForm.id) {
              startForm = matched
            }
          }
        }

        // 如果找到的 startForm 和当前 selectedForm 是同一个，不显示
        if (startForm && startForm.id === selectedForm.id) {
          startForm = null
        }

        if (startForm) {
          startFormName.value = startForm.name
          startFormLabelWidth.value = formLabelWidth.value
          parseStartFormConfig(startForm.data)

          let startSubForms: Record<string, any> = {}
          try {
            const cfg = typeof startForm.data === 'string' ? JSON.parse(startForm.data) : (startForm.data || {})
            startSubForms = cfg.subForms || {}
          } catch {}

          const startBindings: typeof startSubTableBindings.value = []
          for (const b of (startForm.tableBindings || [])) {
            if (b.bindingType === 'PRIMARY') continue
            const cols = deriveColumnsFromBinding(b, startSubForms)
            const binding = {
              bindingId: b.bindingId, bindingType: b.bindingType, bindingMode: b.bindingMode,
              foreignKeyField: b.foreignKeyField, tableName: b.tableDisplayName || b.tableName, tableType: b.tableType,
              tableDescription: b.tableDescription, columns: cols, data: [] as any[]
            }
            const savedSubTables = formData.value.__subTables__
            if (savedSubTables) {
              const saved = savedSubTables[b.bindingId] ?? savedSubTables[String(b.bindingId)]
              if (Array.isArray(saved)) binding.data = saved
            }
            startBindings.push(binding)
          }
          startSubTableBindings.value = startBindings
          hasStartForm.value = true
        } else {
          hasStartForm.value = false
        }
      }
    }
  } catch (error) {
    console.error('Failed to load function unit content:', error)
  }
}

// 解析 BPMN XML 并获取当前节点的 formId 和 formName
const parseBpmnXmlAndGetFormId = (xml: string): { formId: string | null, formName: string | null } => {
  if (!xml) return { formId: null, formName: null }
  
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    // 快照模式（从 Completed Tasks 进来）用 snapshotTaskName；否则用 currentNode
    const currentNodeName = snapshotTaskName || processInfo.value.currentNode || ''
    
    // 查找所有 userTask 节点
    const allElements = doc.getElementsByTagName('*')
    
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      
      if (localName === 'userTask') {
        const taskId = el.getAttribute('id') || ''
        const taskName = el.getAttribute('name') || ''
        
        // 检查是否是当前节点
        if (taskName === currentNodeName || taskId === currentNodeName) {
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

// 解析 BPMN XML 并获取 start 节点后第一个用户任务的 formId
const parseBpmnXmlAndGetStartFormId = (xml: string): { formId: string | null, formName: string | null } => {
  if (!xml) return { formId: null, formName: null }
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const allElements = doc.getElementsByTagName('*')
    let startEventId: string | null = null
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      if ((el.localName || el.nodeName.split(':').pop()) === 'startEvent') {
        startEventId = el.getAttribute('id')
        break
      }
    }
    if (!startEventId) return { formId: null, formName: null }
    let firstTaskId: string | null = null
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      if ((el.localName || el.nodeName.split(':').pop()) === 'sequenceFlow' && el.getAttribute('sourceRef') === startEventId) {
        firstTaskId = el.getAttribute('targetRef')
        break
      }
    }
    if (!firstTaskId) return { formId: null, formName: null }
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      if ((el.localName || el.nodeName.split(':').pop()) === 'userTask' && el.getAttribute('id') === firstTaskId) {
        let formId: string | null = null, formName: string | null = null
        const props = el.getElementsByTagName('*')
        for (let j = 0; j < props.length; j++) {
          const p = props[j]
          const ln = p.localName || p.nodeName.split(':').pop()
          if (ln === 'property' || ln === 'values') {
            const n = p.getAttribute('name'), v = p.getAttribute('value')
            if (n === 'formId' && v) formId = v
            if (n === 'formName' && v) formName = v
          }
        }
        return { formId, formName }
      }
    }
  } catch (e) {
    console.error('Failed to parse BPMN for start formId:', e)
  }
  return { formId: null, formName: null }
}

// 解析 start 节点表单配置（只提取字段，不影响当前节点表单）
const parseStartFormConfig = (configStr: string) => {
  if (!configStr) return
  try {
    const config = typeof configStr === 'string' ? JSON.parse(configStr) : configStr
    const rules = config.rule && Array.isArray(config.rule) ? config.rule : (Array.isArray(config) ? config : null)
    if (rules) {
      const tabsRule = rules.find((r: any) => r.type === 'el-tabs')
      if (tabsRule && tabsRule.children && Array.isArray(tabsRule.children)) {
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
        startFormTabs.value = tabs
        startFormFields.value = []
      } else {
        startFormTabs.value = []
        startFormFields.value = extractFieldsRecursive(rules)
      }
    }
  } catch (error) {
    console.error('Failed to parse start form config:', error)
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
    
    // 创建节点名称到历史记录状态的映射
    const nodeStatusMap = new Map<string, 'completed' | 'current' | 'pending' | 'rejected'>()
    const completedNodeNames = new Set<string>()
    historyRecords.value.forEach(record => {
      if (record.nodeName) {
        nodeStatusMap.set(record.nodeName, record.status)
        if (record.status === 'completed') {
          completedNodeNames.add(record.nodeName)
        }
      }
    })
    
    // 检查是否有批准或拒绝的操作
    const hasApproval = historyRecords.value.some(h => h.status === 'completed' && (h.nodeName.includes('Approval') || h.nodeName.includes('审批')))
    const hasRejection = historyRecords.value.some(h => h.status === 'rejected')
    
    // 获取当前节点名称
    const currentNodeName = processInfo.value.currentNode || ''
    let foundCurrentNode = false
    
    // 解析开始事件
    doc.querySelectorAll('startEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `start_${index}`
      const pos = positionMap.get(id)
      nodes.push({ id, name: event.getAttribute('name') || t('task.startNode'), type: 'start', status: 'completed', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      completed.push(id)
    })
    
    // 解析用户任务
    doc.querySelectorAll('userTask').forEach((task, index) => {
      const id = task.getAttribute('id') || `task_${index}`
      const name = task.getAttribute('name') || t('task.taskFallbackName', { index: index + 1 })
      const pos = positionMap.get(id)
      
      let status: 'completed' | 'current' | 'pending' | 'rejected' = 'pending'
      
      // 优先从历史记录中获取状态
      const historyStatus = nodeStatusMap.get(name)
      if (snapshotTaskName) {
        // 快照模式：只显示到 snapshotTaskName 为止的状态
        if (name === snapshotTaskName || id === snapshotTaskName) {
          status = 'completed'
          completed.push(id)
          foundCurrentNode = true
        } else if (!foundCurrentNode) {
          // snapshotTaskName 之前的节点：从历史记录判断，或视为已完成
          if (historyStatus) {
            status = historyStatus
          } else {
            status = 'completed'
          }
          if (status === 'completed' || status === 'rejected') {
            completed.push(id)
          }
        } else {
          // snapshotTaskName 之后的节点：保持 pending
          status = 'pending'
        }
      } else if (historyStatus) {
        status = historyStatus
        if (status === 'completed' || status === 'rejected') {
          completed.push(id)
        }
      } else if (processInfo.value.status === 'COMPLETED') {
        // 流程已完成，所有 userTask 都视为已完成
        status = 'completed'
        completed.push(id)
      } else if (processInfo.value.status === 'RUNNING') {
        // 流程进行中，根据当前节点名称判断
        if (name === currentNodeName || id === currentNodeName) {
          status = 'current'
          currentNodeId.value = id
          foundCurrentNode = true
        } else if (!foundCurrentNode) {
          // 当前节点之前的节点：仅当历史记录中有该节点时才标记为已完成
          // 避免将被网关跳过的分支节点错误标记为已完成
          const historyMatch = historyRecords.value.find(h => h.nodeName === name || h.nodeId === id)
          if (historyMatch && (historyMatch.status === 'completed' || historyMatch.status === 'rejected')) {
            status = historyMatch.status
            completed.push(id)
          }
        }
      }
      
      nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })
    
    // 解析服务任务
    doc.querySelectorAll('serviceTask').forEach((task, index) => {
      const id = task.getAttribute('id') || `service_${index}`
      const name = task.getAttribute('name') || t('applicationDetail.serviceFallbackName', { index: index + 1 })
      const pos = positionMap.get(id)
      const historyStatus = nodeStatusMap.get(name)
      const status = historyStatus === 'completed' ? 'completed' : 'pending'
      nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      if (status === 'completed') completed.push(id)
    })
    
    // 提前解析顺序流（用于后续网关状态判断）
    const earlyFlows: Array<{sourceRef: string, targetRef: string}> = []
    doc.querySelectorAll('sequenceFlow').forEach(flow => {
      earlyFlows.push({
        sourceRef: flow.getAttribute('sourceRef') || '',
        targetRef: flow.getAttribute('targetRef') || ''
      })
    })

    // 解析网关
    doc.querySelectorAll('exclusiveGateway, parallelGateway, inclusiveGateway').forEach((gateway, index) => {
      const id = gateway.getAttribute('id') || `gateway_${index}`
      const name = gateway.getAttribute('name') || ''
      const pos = positionMap.get(id)
      
      // 根据历史记录判断网关状态
      let status: 'completed' | 'pending' = 'pending'
      if (snapshotTaskName) {
        // 快照模式：检查网关的入口节点是否已完成
        if (completedNodeNames.has(name)) {
          status = 'completed'
        } else {
          // 检查是否有已完成的入口节点（通过 sequenceFlow）
          const incomingSourceIds = earlyFlows.filter(f => f.targetRef === id).map(f => f.sourceRef)
          const hasCompletedSource = incomingSourceIds.some(srcId => completed.includes(srcId))
          if (hasCompletedSource) {
            status = 'completed'
          }
        }
      } else if (completedNodeNames.has(name)) {
        status = 'completed'
      } else if (processInfo.value.status === 'COMPLETED') {
        // 流程已完成，网关视为已完成
        status = 'completed'
      } else {
        // 检查是否有已完成的入口节点（通过 sequenceFlow）
        const incomingSourceIds = earlyFlows.filter(f => f.targetRef === id).map(f => f.sourceRef)
        const hasCompletedSource = incomingSourceIds.some(srcId => completed.includes(srcId))
        if (hasCompletedSource) {
          status = 'completed'
        }
      }
      
      nodes.push({ id, name, type: 'gateway', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      if (status === 'completed') completed.push(id)
    })
    
    // 解析结束事件
    doc.querySelectorAll('endEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `end_${index}`
      const name = event.getAttribute('name') || t('task.endNode')
      const pos = positionMap.get(id)
      
      // 检查结束节点是否应该标记为已完成
      let status: 'completed' | 'pending' | 'rejected' = 'pending'
      
      // 优先从历史记录中获取状态
      if (completedNodeNames.has(name)) {
        // Rejected 结束节点用红色，其他用绿色
        if (name.toLowerCase().includes('rejected') || name.toLowerCase().includes('拒绝')) {
          status = 'rejected'
        } else {
          status = 'completed'
        }
      } else if (snapshotTaskName) {
        // 快照模式：结束节点保持 pending，除非在历史记录中
        status = 'pending'
      } else if (processInfo.value.status === 'COMPLETED') {
        // 流程已完成，标记实际到达的结束节点
        if (name === currentNodeName) {
          // Rejected 结束节点用红色，其他用绿色
          if (name.toLowerCase().includes('rejected') || name.toLowerCase().includes('拒绝')) {
            status = 'rejected'
          } else {
            status = 'completed'
          }
        }
      } else if (processInfo.value.status === 'REJECTED') {
        // 流程被拒绝，只标记 Rejected 结束节点
        if (name.toLowerCase().includes('rejected') || name.toLowerCase().includes('拒绝')) {
          status = 'rejected'
        }
      }
      nodes.push({ id, name, type: 'end', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      if (status === 'completed' || status === 'rejected') completed.push(id)
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
    if (rules) {
      // 提取 labelWidth 配置（忽略后端配置，使用固定值避免 label 被截断）
      // if (config.options?.form?.labelWidth) {
      //   formLabelWidth.value = config.options.form.labelWidth
      // }
      
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

// Derive display columns for a sub-table binding based on designed fields
const deriveColumnsFromBinding = (binding: any, subForms?: Record<string, any>): Array<{ field: string; label: string; type?: string; props?: Record<string, any> }> => {
  const subFormRule = subForms?.[binding.bindingId]?.rule
  if (subFormRule && Array.isArray(subFormRule) && subFormRule.length > 0) {
    return subFormRule.map((r: any) => {
      let type: 'text' | 'number' | 'date' | 'upload' | undefined
      if (r.type === 'inputNumber') type = 'number'
      else if (r.type === 'datePicker') type = 'date'
      else if (r.type === 'upload') type = 'upload'
      return { field: r.field, label: r.title || r.field, type, props: r.props }
    })
  }
  return []
}

// 加载流转历史
const loadProcessHistory = async () => {
  try {
    console.log('=== [HISTORY] Loading process history for:', processId)
    console.log('=== [HISTORY] Calling API: /api/portal/processes/' + processId + '/history')
    
    // 使用 processApi 调用后端API获取流程历史
    const response = await processApi.getProcessHistory(processId)
    console.log('=== [HISTORY] Process history response:', response)
    console.log('=== [HISTORY] Response type:', typeof response)
    console.log('=== [HISTORY] Response keys:', Object.keys(response))
    
    const historyData = response.data || response
    console.log('=== [HISTORY] History data:', historyData)
    console.log('=== [HISTORY] Is array:', Array.isArray(historyData))
    console.log('=== [HISTORY] Data type:', typeof historyData)
    
    if (historyData && Array.isArray(historyData)) {
      console.log('=== [HISTORY] Processing', historyData.length, 'history records')
      console.log('=== [HISTORY] First record:', historyData[0])

      // 如果有快照任务名（从 completed tasks 进来），只保留到该任务为止的记录
      let filteredData = historyData
      if (snapshotTaskName) {
        // 找到 snapshotTaskName 在历史列表中最后一次出现的位置（按时间排序），截断到该位置
        const snapshotIdx = historyData.map((item: any) => item.activityName || item.taskName).lastIndexOf(snapshotTaskName)
        if (snapshotIdx >= 0) {
          filteredData = historyData.slice(0, snapshotIdx + 1)
        } else if (snapshotTime) {
          // activityName 匹配失败（可能是 BPMN element ID），改用时间截断
          const cutoff = new Date(snapshotTime).getTime()
          const timeIdx = historyData.map((item: any) => new Date(item.operationTime || 0).getTime()).lastIndexOf(cutoff)
          if (timeIdx >= 0) {
            filteredData = historyData.slice(0, timeIdx + 1)
          } else {
            // 保留所有 operationTime <= snapshotTime 的记录
            filteredData = historyData.filter((item: any) => {
              const t = new Date(item.operationTime || 0).getTime()
              return t <= cutoff
            })
          }
        }
      }

      // 转换为 HistoryRecord 格式（保留 gateway 记录用于图表状态判断）
      historyRecords.value = filteredData.map((item: any, index: number) => ({
        id: `history_${index}`,
        nodeId: item.activityId || `node_${index}`,
        nodeName: item.activityName || item.taskName || t('applicationDetail.unknownNode'),
        status: getHistoryStatus(item.operationType),
        assigneeName: item.operatorName || '-',
        comment: item.comment,
        createdTime: item.operationTime || '',
        completedTime: item.operationTime,
        activityType: item.activityType || ''
      }))
      console.log('=== [HISTORY] Converted history records:', historyRecords.value.length)
      console.log('=== [HISTORY] First converted record:', historyRecords.value[0])
      console.log('=== [HISTORY] historyRecords.value:', historyRecords.value)
    } else {
      console.warn('=== [HISTORY] History data is not an array, falling back to initHistoryRecords')
      console.warn('=== [HISTORY] historyData:', historyData)
      initHistoryRecords()
    }
  } catch (error) {
    console.error('=== [HISTORY] Failed to load process history:', error)
    console.error('=== [HISTORY] Error details:', error)
    // 回退到简单的历史记录
    initHistoryRecords()
  }
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

// 初始化流转记录
const initHistoryRecords = () => {
  const records: HistoryRecord[] = [{ id: 'submit', nodeId: 'start', nodeName: t('applicationDetail.submitApplication'), status: 'completed', assigneeName: processInfo.value.startUserName || processInfo.value.startUserId, createdTime: processInfo.value.startTime || '' }]
  if (processInfo.value.status === 'RUNNING') records.push({ id: 'current', nodeId: 'task', nodeName: processInfo.value.currentNode || t('applicationDetail.pendingApproval'), status: 'current', assigneeName: processInfo.value.currentAssignee || t('applicationDetail.unassigned'), createdTime: '' })
  else if (processInfo.value.status === 'COMPLETED') records.push({ id: 'end', nodeId: 'end', nodeName: t('applicationDetail.processEnded'), status: 'completed', createdTime: processInfo.value.endTime || '' })
  else if (processInfo.value.status === 'WITHDRAWN') records.push({ id: 'withdrawn', nodeId: 'withdrawn', nodeName: t('applicationDetail.processWithdrawn'), status: 'rejected', assigneeName: processInfo.value.startUserName || processInfo.value.startUserId, createdTime: processInfo.value.endTime || '' })
  historyRecords.value = records
}

// 催办
const handleUrge = async () => {
  urging.value = true
  try { await processApi.urgeProcess(processId); ElMessage.success(t('applicationDetail.urgeSuccess')) }
  catch { ElMessage.error(t('applicationDetail.urgeFailed')) }
  finally { urging.value = false }
}

// 撤回
const handleWithdraw = async () => {
  try {
    await ElMessageBox.confirm(t('applicationDetail.withdrawConfirm'), t('applicationDetail.withdrawConfirmTitle'), { type: 'warning' })
    withdrawing.value = true
    await processApi.withdrawProcess(processId, t('applicationDetail.userWithdraw'))
    ElMessage.success(t('applicationDetail.withdrawSuccess'))
    router.push('/my-applications')
  } catch (error: any) { if (error !== 'cancel') ElMessage.error(t('applicationDetail.withdrawFailed')) }
  finally { withdrawing.value = false }
}

onMounted(() => { loadProcessDetail() })
</script>

<style lang="scss" scoped>
.application-detail-page {
  max-width: 1200px;
  margin: 0 auto;
  .page-header { 
    display: flex; 
    align-items: center; 
    justify-content: space-between;
    gap: 16px; 
    margin-bottom: 20px; 
    
    .header-left {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    
    h1 { 
      font-size: 24px; 
      font-weight: 500; 
      color: var(--text-primary); 
      margin: 0; 
    } 
  }
  .skeleton-content { display: flex; flex-direction: column; }
  .content-sections { display: flex; flex-direction: column; gap: 20px; }
  .section { background: white; border-radius: 8px; border: 1px solid var(--border-color);
    .section-header { display: flex; align-items: center; gap: 8px; padding: 16px 20px; background: #fafafa; border-bottom: 1px solid var(--border-color); font-size: 16px; font-weight: 500; color: var(--text-primary); .el-icon { color: var(--hsbc-red); } }
    .section-content { padding: 20px; }
  }
  .workflow-section .section-content { min-height: 300px; }
  .form-section .form-container { width: 100%; }
  .form-section .sub-table-section { margin-top: 16px; }
  .history-section .section-content { min-height: 100px; }
  .action-section { position: sticky; bottom: 0; z-index: 10;
    .action-buttons { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; .left-actions, .right-actions { display: flex; gap: 12px; } }
  }
}
</style>
