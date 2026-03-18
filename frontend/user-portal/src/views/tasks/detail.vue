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

      <!-- 第三部分：申请内容（start 节点表单，只读） -->
      <div v-if="hasStartForm" class="section form-section">
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ startFormName || t('task.applicationContent') }}</span>
          <el-tag type="info" size="small">{{ t('task.readonly') }}</el-tag>
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
              :label-width="formLabelWidth"
              :readonly="formReadOnly"
            />
          </div>
          <el-empty v-else :description="t('task.noFormData')" />

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
                :editable="!formReadOnly && binding.bindingMode === 'EDITABLE'"
              />
            </div>
          </template>
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
            :records="historyRecords.filter(r => !r.activityType?.includes('Gateway'))"
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
            <!-- 有配置自定义 Actions 时显示自定义按钮 -->
            <template v-if="taskInfo.actions && taskInfo.actions.length > 0">
              <el-button
                v-for="action in taskInfo.actions"
                :key="action.actionId"
                :type="getButtonType(action.buttonColor)"
                @click="handleCustomAction(action)"
              >
                <el-icon v-if="action.icon"><component :is="getIconComponent(action.icon)" /></el-icon>
                {{ action.actionName }}
              </el-button>
            </template>
            <!-- 未配置自定义 Actions 时显示默认审批按钮 -->
            <template v-else-if="taskInfo.actions === undefined || taskInfo.actions === null">
              <el-button type="success" @click="handleApprove">
                <el-icon><Check /></el-icon> {{ t('task.approve') }}
              </el-button>
              <el-button type="danger" @click="handleReject">
                <el-icon><Close /></el-icon> {{ t('task.reject') }}
              </el-button>
            </template>
            <!-- 转办、委托、催办始终显示 -->
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

    <!-- N8N Action 对话框 -->
    <N8nActionDialog
      v-model:visible="n8nActionDialogVisible"
      :action-definition="n8nActionDefinition"
      :task-id="taskId"
      :process-instance-id="taskInfo.processInstanceId || ''"
      :initial-data="n8nInitialData"
      @executed="handleN8nActionExecuted"
    />

    <!-- 表单弹窗对话框 -->
    <el-dialog v-model="formPopupVisible" :title="formPopupTitle" :width="formPopupWidth" append-to-body>
      <div v-if="formPopupFields.length > 0 || formPopupTabs.length > 0" class="form-popup-container">
        <FormRenderer
          :fields="formPopupFields"
          :tabs="formPopupTabs"
          v-model="formPopupData"
          :label-width="formPopupLabelWidth"
          :readonly="formPopupReadOnly"
        />
      </div>
      <el-empty v-else :description="t('task.noFormData')" />
      <template #footer>
        <el-button @click="formPopupVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-if="!formPopupReadOnly" type="primary" @click="submitFormPopup" :loading="submitting">
          {{ t('common.submit') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { 
  ArrowLeft, 
  InfoFilled, 
  Share, 
  Document, 
  Clock, 
  Bell, 
  Check, 
  Close, 
  User, 
  Switch,
  CircleCheck,
  CircleClose,
  Files,
  Warning
} from '@element-plus/icons-vue'
import { 
  getTaskDetail, 
  getTaskHistory, 
  completeTask, 
  delegateTask, 
  transferTask, 
  urgeTask,
  TaskInfo, 
  TaskHistoryInfo,
  TaskActionInfo
} from '@/api/task'
import { processApi } from '@/api/process'
import { useUserStore } from '@/stores/user'
import ProcessDiagram, { type ProcessNode, type ProcessFlow } from '@/components/ProcessDiagram.vue'
import ProcessHistory, { type HistoryRecord } from '@/components/ProcessHistory.vue'
import FormRenderer, { type FormField, type FormTab } from '@/components/FormRenderer.vue'
import SubTableField from '@/components/SubTableField.vue'
import N8nActionDialog from '@/components/N8nActionDialog.vue'
import type { ActionDefinition } from '@/components/N8nActionDialog.vue'
import { applyAutoFill } from '@/utils/n8nAutoFillEngine'
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
const formLabelWidth = ref('160px')

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

// Sub-table bindings for the current form
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

// 表单弹窗状态
const formPopupVisible = ref(false)
const formPopupTitle = ref('')
const formPopupFields = ref<FormField[]>([])
const formPopupTabs = ref<FormTab[]>([])
const formPopupData = ref<Record<string, any>>({})
const formPopupReadOnly = ref(false)
const formPopupWidth = ref('800px')
const formPopupLabelWidth = ref('160px')
const currentFormPopupAction = ref<TaskActionInfo | null>(null)

// N8N Action 对话框状态
const n8nActionDialogVisible = ref(false)
const n8nActionDefinition = ref<ActionDefinition>({ id: 0 })
const n8nInitialData = ref<Record<string, any> | undefined>(undefined)

const loadTaskDetail = async () => {
  loading.value = true
  taskError.value = null
  try {
    const res = await getTaskDetail(taskId)
    const data = res.data || res
    if (data) {
      taskInfo.value = data
      if (data.variables) formData.value = data.variables
      
      // 先加载流转历史，因为解析流程图需要用到历史记录
      await loadTaskHistory()
      
      // 然后加载功能单元内容（流程图和表单）
      if (data.processDefinitionKey) {
        await loadFunctionUnitContent(data.processDefinitionKey)
      }
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
      // 转换为 HistoryRecord 格式（保留 gateway 记录用于图表状态判断）
      historyRecords.value = data.map((item: TaskHistoryInfo, index: number) => ({
        id: `history_${index}`,
        nodeId: item.activityId || `node_${index}`,
        nodeName: item.activityName || t('task.unknownNode'),
        status: getHistoryStatus(item.operationType),
        assigneeName: item.operatorName || '-',
        comment: item.comment,
        createdTime: item.operationTime || '',
        completedTime: item.operationTime,
        activityType: item.activityType || ''
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
    console.log('[FU] raw response keys:', Object.keys(response as any))
    const content = (response as any).data || response
    console.log('[FU] content keys:', Object.keys(content as any), 'forms count:', (content as any).forms?.length)
    if ((content as any).error) {
      console.error('Function unit content error:', content.error)
      processError.value = t('task.processLoadFailed')
      return
    }
    
    let currentFormInfo: { formId: string | null, formName: string | null, readOnly: boolean } = { formId: null, formName: null, readOnly: false }
    
    // 解析流程图
    if (content.processes?.length > 0) {
      // 先获取当前节点的 formId 和 formName
      currentFormInfo = parseBpmnXmlAndGetFormId(content.processes[0].data)
      parseBpmnXml(content.processes[0].data)
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
      console.log('[Form] selected form:', selectedForm.name, 'sourceId:', selectedForm.sourceId, 'formId from BPMN:', currentFormInfo.formId, 'readOnly:', currentFormInfo.readOnly)
      parseFormConfig(selectedForm.data)
      
      // 如果 BPMN 中明确标记了 readOnly，覆盖表单配置中的值
      if (currentFormInfo.readOnly) {
        formReadOnly.value = true
      }
      
      // Parse subForms from configJson for column definitions
      let subForms: Record<string, any> = {}
      try {
        const cfg = typeof selectedForm.data === 'string' ? JSON.parse(selectedForm.data) : (selectedForm.data || {})
        subForms = cfg.subForms || {}
      } catch {}

      // Load sub-table bindings for this form (SUB and RELATED, not PRIMARY)
      const bindings: typeof subTableBindings.value = []
      const tableBindings: any[] = selectedForm.tableBindings || []
      console.log('[SubTable] selectedForm:', selectedForm.name, 'tableBindings:', JSON.stringify(tableBindings))
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
      console.log('[SubTable] bindings to render:', bindings.length, bindings.map(b => b.tableName))
      // 注意：JSON 序列化后 key 变为 string，需同时用 number 和 string 查找
      console.log('[SubTable] formData.value keys:', Object.keys(formData.value))
      console.log('[SubTable] formData.value.__subTables__:', JSON.stringify(formData.value.__subTables__))
      console.log('[SubTable] bindings bindingIds:', bindings.map(b => b.bindingId))
      const savedSubTables = formData.value.__subTables__
      if (savedSubTables && typeof savedSubTables === 'object') {
        bindings.forEach(binding => {
          const saved = savedSubTables[binding.bindingId] ?? savedSubTables[String(binding.bindingId)]
          console.log('[SubTable] binding', binding.bindingId, '-> saved:', JSON.stringify(saved))
          if (Array.isArray(saved)) {
            binding.data = saved
          }
        })
      } else {
        console.warn('[SubTable] no __subTables__ found in formData.value')
      }
      subTableBindings.value = bindings

      // 如果当前节点的表单与 start 节点不同，额外加载 start 节点表单（只读展示申请内容）
      // 只有当前节点成功匹配到了专属表单（currentFormInfo 有值）时才考虑显示 start form
      if (content.processes?.length > 0 && (currentFormInfo.formId || currentFormInfo.formName)) {
        const startFormInfo = parseBpmnXmlAndGetStartFormId(content.processes[0].data)
        const startFormId = startFormInfo.formId
        const startFormName2 = startFormInfo.formName
        let startForm: any = null

        if (startFormId && String(selectedForm.sourceId) !== startFormId) {
          startForm = content.forms.find((f: any) => String(f.sourceId) === startFormId)
          if (!startForm && startFormName2) {
            startForm = content.forms.find((f: any) => f.name === startFormName2)
          }
        } else if (!startFormId && startFormName2 && selectedForm.name !== startFormName2) {
          startForm = content.forms.find((f: any) => f.name === startFormName2)
        }

        // 额外保护：如果找到的 startForm 和当前 selectedForm 是同一个，不显示
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
            // if (cfg.options?.form?.labelWidth) startFormLabelWidth.value = cfg.options.form.labelWidth
          } catch {}

          const startBindings: typeof startSubTableBindings.value = []
          for (const b of (startForm.tableBindings || [])) {
            if (b.bindingType === 'PRIMARY') continue
            const cols = deriveColumnsFromBinding(b, startSubForms)
            const binding = {
              bindingId: b.bindingId, bindingType: b.bindingType, bindingMode: b.bindingMode,
              foreignKeyField: b.foreignKeyField, tableName: b.tableName, tableType: b.tableType,
              tableDescription: b.tableDescription, columns: cols, data: [] as any[]
            }
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
const parseBpmnXmlAndGetFormId = (xml: string): { formId: string | null, formName: string | null, readOnly: boolean } => {
  if (!xml) return { formId: null, formName: null, readOnly: false }
  
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const currentTaskDefinitionKey = (taskInfo.value as any).taskDefinitionKey || ''
    const currentTaskName = taskInfo.value.taskName || ''
    
    console.log('[BPMN] matching task: taskDefinitionKey=', currentTaskDefinitionKey, 'taskName=', currentTaskName)
    
    // 查找所有 userTask 节点
    const allElements = doc.getElementsByTagName('*')
    
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      
      if (localName === 'userTask') {
        const bpmnId = el.getAttribute('id') || ''
        const bpmnName = el.getAttribute('name') || ''
        
        // 优先用 taskDefinitionKey (BPMN element id) 匹配，再用 taskName 匹配
        const isMatch = (currentTaskDefinitionKey && bpmnId === currentTaskDefinitionKey)
          || (!currentTaskDefinitionKey && bpmnName === currentTaskName)
        
        console.log('[BPMN] userTask id=', bpmnId, 'name=', bpmnName, 'isMatch=', isMatch)
        
        if (isMatch) {
          // 查找 formId、formName 和 formReadOnly 属性
          let formId: string | null = null
          let formName: string | null = null
          let readOnly = false
          
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
              if (name === 'formReadOnly' && value === 'true') {
                readOnly = true
              }
            }
          }
          
          console.log('[BPMN] matched userTask, formId=', formId, 'formName=', formName, 'readOnly=', readOnly)
          return { formId, formName, readOnly }
        }
      }
    }
    console.warn('[BPMN] no userTask matched for taskDefinitionKey=', currentTaskDefinitionKey, 'taskName=', currentTaskName)
  } catch (error) {
    console.error('Failed to parse BPMN for formId:', error)
  }
  
  return { formId: null, formName: null, readOnly: false }
}

// 解析 BPMN XML 并获取 start 节点后第一个用户任务的 formId（复用 start.vue 的逻辑）
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
    if (!rules) return
    const tabsRule = rules.find((r: any) => r.type === 'el-tabs')
    if (tabsRule?.children && Array.isArray(tabsRule.children)) {
      const tabs: FormTab[] = []
      for (const tabPane of tabsRule.children) {
        if (tabPane.type === 'el-tab-pane' && tabPane.props) {
          const tabFields: FormField[] = []
          if (tabPane.children) tabFields.push(...extractFieldsRecursive(tabPane.children))
          tabs.push({ name: tabPane.props.name || `tab_${tabs.length}`, label: tabPane.props.label || `Tab ${tabs.length + 1}`, fields: tabFields })
        }
      }
      startFormTabs.value = tabs
      startFormFields.value = []
    } else {
      startFormTabs.value = []
      startFormFields.value = extractFieldsRecursive(rules)
    }
  } catch (e) {
    console.error('Failed to parse start form config:', e)
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
    
    // 从历史记录中获取已完成的节点ID
    const completedNodeIds = new Set<string>()
    const completedNodeNames = new Set<string>()
    
    // 收集所有已完成的节点ID和名称
    historyRecords.value.forEach(record => {
      if (record.nodeId && record.status === 'completed') {
        completedNodeIds.add(record.nodeId)
      }
      if (record.nodeName && record.status === 'completed') {
        completedNodeNames.add(record.nodeName)
      }
    })
    
    // 检查是否有批准或拒绝的操作
    const hasApproval = historyRecords.value.some(h => h.status === 'completed' && h.nodeName.includes('Approval'))
    const hasRejection = historyRecords.value.some(h => h.status === 'rejected')
    
    // 获取当前任务名称
    const currentTaskName = taskInfo.value.taskName || ''
    let currentNodeFound = false
    
    // 解析开始事件
    doc.querySelectorAll('startEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `start_${index}`
      const pos = positionMap.get(id)
      // 开始节点总是已完成
      nodes.push({ id, name: event.getAttribute('name') || t('task.startNode'), type: 'start', status: 'completed', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      completed.push(id)
    })
    
    // 解析用户任务
    doc.querySelectorAll('userTask').forEach((task, index) => {
      const id = task.getAttribute('id') || `task_${index}`
      const name = task.getAttribute('name') || t('task.taskFallbackName', { index: index + 1 })
      const pos = positionMap.get(id)
      
      let status: 'completed' | 'current' | 'pending' = 'pending'
      
      // 检查是否是当前任务
      if (name === currentTaskName || id === currentTaskName) {
        status = 'current'
        currentNodeId.value = id
        currentNodeFound = true
      } 
      // 检查历史记录中是否已完成
      else if (completedNodeIds.has(id) || completedNodeNames.has(name)) {
        status = 'completed'
        completed.push(id)
      }
      // 如果还没找到当前节点，且历史记录中有这个节点，标记为已完成
      else if (!currentNodeFound) {
        // 通过节点名称匹配历史记录
        const historyMatch = historyRecords.value.find(h => h.nodeName === name)
        if (historyMatch && historyMatch.status === 'completed') {
          status = 'completed'
          completed.push(id)
        }
      }
      
      nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })
    
    // 解析网关
    doc.querySelectorAll('exclusiveGateway, parallelGateway, inclusiveGateway').forEach((gateway, index) => {
      const id = gateway.getAttribute('id') || `gateway_${index}`
      const name = gateway.getAttribute('name') || ''
      const pos = positionMap.get(id)
      
      // 检查网关是否在已完成的节点中
      let status: 'completed' | 'pending' = 'pending'
      if (completedNodeIds.has(id) || completedNodeNames.has(name)) {
        status = 'completed'
        completed.push(id)
      } else if (hasApproval && !currentNodeFound) {
        // 如果有已完成的审批任务，网关也应该标记为已完成
        status = 'completed'
        completed.push(id)
      }
      
      nodes.push({ id, name, type: 'gateway', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })
    
    // 解析结束事件
    doc.querySelectorAll('endEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `end_${index}`
      const name = event.getAttribute('name') || t('task.endNode')
      const pos = positionMap.get(id)
      
      // 检查结束节点是否应该标记为已完成
      let status: 'completed' | 'pending' | 'rejected' = 'pending'
      const isRejectedEnd = name.toLowerCase().includes('rejected') || name.toLowerCase().includes('拒绝')
      
      if (completedNodeIds.has(id) || completedNodeNames.has(name)) {
        // Rejected 结束节点用红色，其他用绿色
        status = isRejectedEnd ? 'rejected' : 'completed'
        completed.push(id)
      } else {
        // 通过节点名称匹配历史记录
        const historyMatch = historyRecords.value.find(h => h.nodeName === name && h.status === 'completed')
        if (historyMatch) {
          status = isRejectedEnd ? 'rejected' : 'completed'
          completed.push(id)
        } else if (hasApproval && !currentNodeFound) {
          // 如果有已完成的审批且没有当前任务，根据结束节点名称判断
          if (name.toLowerCase().includes('approved') || name.toLowerCase().includes('通过')) {
            status = 'completed'
            completed.push(id)
          }
        } else if (hasRejection && !currentNodeFound) {
          // 如果有拒绝操作，标记拒绝结束节点为红色
          if (isRejectedEnd) {
            status = 'rejected'
            completed.push(id)
          }
        }
      }
      
      nodes.push({ id, name, type: 'end', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
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
    
    console.log('=== BPMN Parse Result ===')
    console.log('Nodes:', nodes.map(n => ({ id: n.id, name: n.name, status: n.status })))
    console.log('Completed IDs:', completed)
    console.log('Current Node ID:', currentNodeId.value)
    console.log('History Records:', historyRecords.value)
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
    // 检查表单是否只读
    formReadOnly.value = config.formReadOnly === true || config.formReadOnly === 'true'
  } catch (error) {
    console.error('Failed to parse form config:', error)
  }
}

// Derive display columns for a sub-table binding based on table metadata
const deriveColumnsFromBinding = (binding: any, subForms?: Record<string, any>): Array<{ field: string; label: string; type?: string; props?: Record<string, any> }> => {
  // First try to use designed fields from configJson.subForms
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
  const typeMap: Record<string, string> = { 'input': 'text', 'inputNumber': 'number', 'select': 'select', 'radio': 'radio', 'checkbox': 'checkbox', 'switch': 'switch', 'datePicker': dateType, 'DatePicker': dateType, 'date-picker': dateType, 'el-date-picker': dateType, 'timePicker': 'time', 'cascader': 'cascader', 'upload': 'upload' }
  const field: FormField = { key: rule.field, label: rule.title || rule.field, type: typeMap[rule.type] || 'text', required: rule.validate?.some((v: any) => v.required) || false, placeholder: rule.props?.placeholder || '', span: rule.col?.span || 24 }
  if (rule.options) {
    field.options = rule.options.map((opt: any) => ({ label: opt.label || opt.value, value: opt.value }))
    console.log(`Field ${rule.field} options:`, JSON.stringify(field.options))
  }
  if (rule.type === 'input' && rule.props?.type === 'textarea') { field.type = 'textarea'; field.rows = rule.props?.rows || 3 }
  if (rule.type === 'upload') {
    const action = rule.props?.action
    field.uploadUrl = (action && action !== '/') ? action : '/api/v1/upload'
    field.uploadAccept = rule.props?.accept || '.jpg,.jpeg,.png,.pdf,.docx,.xlsx'
    field.uploadLimit = rule.props?.limit || 1
  }
  console.log(`convertFormCreateRule: field=${rule.field}, type=${field.type}, hasOptions=${!!field.options}`)
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

const formatDate = (date?: string | number[]) => {
  if (!date) return '-'
  if (Array.isArray(date)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = date
    const d = dayjs(new Date(year, month - 1, day, hour, minute, second))
    return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : '-'
  }
  const d = dayjs(date)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : '-'
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
  approveDialogTitle.value = t('task.approve')
  approveForm.comment = ''
  approveDialogVisible.value = true
}

const handleReject = () => {
  currentApproveAction.value = 'REJECT'
  approveDialogTitle.value = t('task.reject')
  approveForm.comment = ''
  approveDialogVisible.value = true
}

const handleDelegate = () => {
  currentAction.value = 'delegate'
  actionDialogTitle.value = t('task.delegate')
  actionForm.targetUserId = ''
  actionForm.reason = ''
  actionDialogVisible.value = true
}

const handleTransfer = () => {
  currentAction.value = 'transfer'
  actionDialogTitle.value = t('task.transfer')
  actionForm.targetUserId = ''
  actionForm.reason = ''
  actionDialogVisible.value = true
}

const handleUrge = () => {
  currentAction.value = 'urge'
  actionDialogTitle.value = t('task.urge')
  actionForm.reason = ''
  actionDialogVisible.value = true
}

const submitApprove = async () => {
  submitting.value = true
  try {
    // 根据审批动作设置流程变量
    const variables: Record<string, any> = {}
    
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
    
    // 收集当前表单数据（如 Approval Form 中的 additional_information）
    const currentFormData: Record<string, any> = {}
    for (const key of Object.keys(formData.value)) {
      // 排除系统字段和 start 表单已有的字段，只收集当前审批表单的字段
      if (!key.startsWith('__') && !variables[key]) {
        currentFormData[key] = formData.value[key]
      }
    }

    await completeTask(taskId, {
      taskId: taskId,
      action: currentApproveAction.value,
      comment: approveForm.comment,
      variables: variables,
      formData: currentFormData
    })
    ElMessage.success(t('task.operationSuccess'))
    approveDialogVisible.value = false
    router.push('/tasks')
  } catch (error) {
    ElMessage.error(t('task.operationFailed'))
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
      ElMessage.success(t('task.delegateSuccess'))
    } else if (currentAction.value === 'transfer') {
      await transferTask(taskId, actionForm.targetUserId, actionForm.reason)
      ElMessage.success(t('task.transferSuccess'))
    } else if (currentAction.value === 'urge') {
      await urgeTask(taskId, actionForm.reason)
      ElMessage.success(t('task.urgeSuccess'))
    }
    actionDialogVisible.value = false
    loadTaskDetail()
  } catch (error) {
    ElMessage.error(t('task.operationFailed'))
  } finally {
    submitting.value = false
  }
}

// 处理自定义操作按钮
const handleCustomAction = (action: TaskActionInfo) => {
  console.log('Custom action clicked:', action)
  
  // 根据 actionType 处理不同类型的操作
  switch (action.actionType) {
    case 'APPROVE':
      currentApproveAction.value = 'APPROVE'
      approveDialogTitle.value = action.actionName
      approveForm.comment = ''
      approveDialogVisible.value = true
      break
    
    case 'REJECT':
      currentApproveAction.value = 'REJECT'
      approveDialogTitle.value = action.actionName
      approveForm.comment = ''
      approveDialogVisible.value = true
      break
    
    case 'FORM_POPUP':
      // 解析 configJson 获取 formId
      try {
        const config = action.configJson ? JSON.parse(action.configJson) : {}
        console.log('Form popup config:', config)
        openFormPopup(action, config)
      } catch (error) {
        console.error('Failed to parse configJson:', error)
        ElMessage.error(t('task.configParseFailed'))
      }
      break
    
    case 'N8N_ACTION':
      // 解析 configJson，根据 inputMapping 中的 sourceType 自动收集数据
      const n8nAutoData: Record<string, any> = {}
      try {
        const n8nConfig = action.configJson ? JSON.parse(action.configJson) : {}
        const n8nInputMapping = n8nConfig.inputMapping || []
        for (const param of n8nInputMapping) {
          if (param.sourceType === 'sub_table' && param.sourceBindingId && param.sourceField) {
            const targetBinding = subTableBindings.value.find(b => 
              b.bindingId === param.sourceBindingId || String(b.bindingId) === String(param.sourceBindingId)
            )
            if (targetBinding) {
              const files: string[] = []
              for (const row of targetBinding.data) {
                const val = row[param.sourceField]
                if (val) {
                  if (typeof val === 'string') {
                    files.push(val)
                  } else if (Array.isArray(val)) {
                    val.forEach((f: any) => files.push(f.url || f.response?.url || f.name || String(f)))
                  } else if (val.url) {
                    files.push(val.url)
                  }
                }
              }
              if (files.length > 0) {
                n8nAutoData[param.paramName] = files
              }
            }
          }
        }
      } catch (e) {
        console.error('Failed to parse N8N action config for auto-fill:', e)
      }
      n8nActionDefinition.value = {
        id: Number(action.actionId) || 0,
        actionName: action.actionName,
        configJson: action.configJson
      }
      n8nInitialData.value = Object.keys(n8nAutoData).length > 0 ? n8nAutoData : undefined
      n8nActionDialogVisible.value = true
      break
    
    default:
      ElMessage.warning(t('task.unknownActionType', { type: action.actionType }))
  }
}

// N8N Action 执行完成回调
const handleN8nActionExecuted = (data: Record<string, any> | null) => {
  try {
    const n8nOutput = data?.outputData || data
    if (!n8nOutput) return

    const configJson = n8nActionDefinition.value?.configJson
      ? JSON.parse(n8nActionDefinition.value.configJson)
      : null

    const frontendOutputMapping = configJson?.frontendOutputMapping
    if (!frontendOutputMapping || !Array.isArray(frontendOutputMapping) || frontendOutputMapping.length === 0) {
      return
    }

    const result = applyAutoFill(n8nOutput, frontendOutputMapping, subTableBindings.value, formData.value)

    subTableBindings.value = result.updatedBindings as typeof subTableBindings.value
    formData.value = result.updatedFormData

    if (result.filledCount > 0) {
      ElMessage.success(t('processStart.n8nAutoFillSuccess', { count: result.filledCount }))
    }
  } catch (e) {
    console.error('[handleN8nActionExecuted] Error:', e)
  }
}

// 打开表单弹窗
const openFormPopup = async (action: TaskActionInfo, config: any) => {
  try {
    currentFormPopupAction.value = action
    formPopupTitle.value = config.popupTitle || action.actionName
    formPopupWidth.value = config.popupWidth || '800px'
    formPopupReadOnly.value = config.readOnly === true || config.readOnly === 'true'
    formPopupData.value = {}
    
    // 获取表单配置
    if (config.formId) {
      // 从功能单元内容中获取表单配置
      const functionUnitId = taskInfo.value.processDefinitionKey
      if (functionUnitId) {
        try {
          const res = await processApi.getFunctionUnitContents(functionUnitId, 'FORM')
          const forms = res.data || []
          
          // 查找对应的表单
          const formContent = forms.find((f: any) => {
            // 尝试从 source_id 匹配
            return f.sourceId === String(config.formId) || f.contentName === config.formName
          })
          
          if (formContent && formContent.contentData) {
            // 解析表单配置
            const formConfig = typeof formContent.contentData === 'string' 
              ? JSON.parse(formContent.contentData) 
              : formContent.contentData
            
            // 使用与主表单相同的解析逻辑
            parseFormPopupConfig(formConfig)
            formPopupVisible.value = true
          } else {
            ElMessage.error(t('task.formNotFound', { name: config.formName || config.formId }))
          }
        } catch (error) {
          console.error('Failed to load form:', error)
          ElMessage.error(t('task.formLoadFailed'))
        }
      }
    } else {
      ElMessage.error(t('task.formMissingId'))
    }
  } catch (error) {
    console.error('Failed to open form popup:', error)
    ElMessage.error(t('task.formOpenFailed'))
  }
}

// 解析表单弹窗配置 - 复用 parseFormConfig 的逻辑
const parseFormPopupConfig = (configInput: any) => {
  try {
    // 确保 config 是对象（可能传入字符串）
    const config = typeof configInput === 'string' ? JSON.parse(configInput) : configInput
    console.log('parseFormPopupConfig: type of config =', typeof config, ', keys =', Object.keys(config || {}))
    
    const rules = config.rule && Array.isArray(config.rule) ? config.rule : (Array.isArray(config) ? config : null)
    if (rules) {
      console.log('Form popup rules count:', rules.length)
      rules.forEach((r: any, i: number) => {
        console.log(`Rule[${i}]: type=${r.type}, field=${r.field}, hasOptions=${!!r.options}, optionsCount=${r.options?.length || 0}`)
      })
      
      // 提取 labelWidth 配置
      if (config.options?.form?.labelWidth) {
        formPopupLabelWidth.value = config.options.form.labelWidth
      }
      
      // 检查是否有 el-tabs 结构
      const tabsRule = rules.find((r: any) => r.type === 'el-tabs' || r.type === 'ElTabPane' || r.type === 'el-tab-pane')
      
      if (tabsRule && tabsRule.children && Array.isArray(tabsRule.children)) {
        const tabs: FormTab[] = []
        for (const tabPane of tabsRule.children) {
          if ((tabPane.type === 'el-tab-pane' || tabPane.type === 'ElTabPane') && tabPane.props) {
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
        formPopupTabs.value = tabs
        formPopupFields.value = []
      } else {
        formPopupTabs.value = []
        formPopupFields.value = extractFieldsRecursive(rules)
      }
      
      console.log('Popup fields result:', formPopupFields.value.map(f => ({ key: f.key, type: f.type, hasOptions: !!f.options, optionsCount: f.options?.length })))
    } else {
      console.warn('parseFormPopupConfig: no rules found in config')
    }
  } catch (error) {
    console.error('Failed to parse form popup config:', error)
  }
}

// 提交表单弹窗
const submitFormPopup = async () => {
  try {
    submitting.value = true
    
    // TODO: 根据 action 类型处理表单数据
    // 可能需要调用不同的 API 或更新流程变量
    
    ElMessage.success(t('task.formSubmitSuccess'))
    formPopupVisible.value = false
    
    // 刷新任务详情
    await loadTaskDetail()
  } catch (error) {
    console.error('Failed to submit form popup:', error)
    ElMessage.error(t('task.formSubmitFailed'))
  } finally {
    submitting.value = false
  }
}

// 获取按钮类型（Element Plus 的 type）
const getButtonType = (buttonColor?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' | '' => {
  const colorMap: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    'primary': 'primary',
    'success': 'success',
    'warning': 'warning',
    'danger': 'danger',
    'info': 'info'
  }
  return colorMap[buttonColor || ''] || 'primary'
}

// 获取图标组件
const getIconComponent = (iconName?: string) => {
  if (!iconName) return null
  
  const iconMap: Record<string, any> = {
    'check': markRaw(Check),
    'check-circle': markRaw(CircleCheck),
    'times-circle': markRaw(CircleClose),
    'close': markRaw(Close),
    'file-alt': markRaw(Files),
    'files': markRaw(Files),
    'warning': markRaw(Warning),
    'bell': markRaw(Bell),
    'user': markRaw(User)
  }
  
  return iconMap[iconName] || markRaw(Check)
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

    .sub-table-section {
      margin-top: 16px;
    }
  }
  
  .form-popup-container {
    width: 100%;
    max-height: 60vh;
    overflow-y: auto;
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
