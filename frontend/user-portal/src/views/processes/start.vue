<template>
  <div class="process-start-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <el-button
        :icon="ArrowLeft"
        @click="$router.back()"
      >
        {{ t('processStart.back') }}
      </el-button>
      <h1>{{ functionUnitName || t('processStart.startProcess') }}</h1>
      <el-tag
        v-if="functionUnitVersion"
        type="info"
        size="small"
      >
        v{{ functionUnitVersion }}
      </el-tag>
    </div>

    <!-- 加载状态：与待办一致 — 轻量提示，非整页骨架 -->
    <div
      v-if="loading"
      class="process-start-loading"
    >
      <el-icon
        class="is-loading"
        :size="32"
      >
        <Loading />
      </el-icon>
      <span>{{ t('common.loading') }}</span>
    </div>
    
    <!-- 功能单元已禁用状态 -->
    <div
      v-else-if="isDisabled"
      class="disabled-state"
    >
      <el-result
        icon="warning"
        :title="t('processStart.disabledTitle')"
        :sub-title="t('processStart.disabledSubtitle')"
      >
        <template #extra>
          <el-button
            type="primary"
            @click="$router.back()"
          >
            {{ t('processStart.back') }}
          </el-button>
          <el-button @click="$router.push('/processes')">
            {{ t('processStart.viewOtherProcesses') }}
          </el-button>
        </template>
      </el-result>
    </div>
    
    <!-- 访问被拒绝状态 -->
    <div
      v-else-if="isAccessDenied"
      class="access-denied-state"
    >
      <el-result
        icon="error"
        :title="t('processStart.accessDeniedTitle')"
        :sub-title="t('processStart.accessDeniedSubtitle')"
      >
        <template #extra>
          <el-button
            type="primary"
            @click="$router.back()"
          >
            {{ t('processStart.back') }}
          </el-button>
          <el-button @click="$router.push('/processes')">
            {{ t('processStart.viewOtherProcesses') }}
          </el-button>
        </template>
      </el-result>
    </div>
    
    <!-- 加载错误状态 -->
    <div
      v-else-if="loadError"
      class="error-state"
    >
      <el-result
        icon="error"
        :title="t('processStart.loadFailedTitle')"
        :sub-title="loadError"
      >
        <template #extra>
          <el-button
            type="primary"
            @click="loadFunctionUnitContent"
          >
            {{ t('processStart.reload') }}
          </el-button>
          <el-button @click="$router.back()">
            {{ t('processStart.back') }}
          </el-button>
        </template>
      </el-result>
    </div>

    <!-- 无 PROCESS form 警告状态 -->
    <div
      v-else-if="noProcessForm"
      class="no-process-form-state"
    >
      <el-result
        icon="warning"
        :title="t('process.noProcessFormTitle')"
        :sub-title="t('process.noProcessForm')"
      >
        <template #extra>
          <el-button
            type="primary"
            @click="$router.back()"
          >
            {{ t('processStart.back') }}
          </el-button>
          <el-button @click="$router.push('/processes')">
            {{ t('processStart.viewOtherProcesses') }}
          </el-button>
        </template>
      </el-result>
    </div>
    
    <!-- 正常内容 -->
    <div
      v-else
      class="content-sections"
    >
      <el-alert
        v-if="workspaceStartBlocked"
        type="warning"
        show-icon
        :closable="false"
        class="workspace-guard-alert"
      >
        <template #title>
          {{ t('processStart.workspaceGuardTitle') }}
        </template>
        {{ t('processStart.workspaceGuardHint') }}
      </el-alert>
      <!-- 第一部分：实时工作流程图 -->
      <div class="section workflow-section">
        <div class="section-header">
          <el-icon><Share /></el-icon>
          <span>{{ t('processStart.workflowDiagram') }}</span>
          <el-tag
            type="success"
            size="small"
          >
            {{ t('processStart.startNodeTag') }}
          </el-tag>
        </div>
        <div class="section-content">
          <ProcessDiagram
            v-if="bpmnXml || processNodes.length > 0"
            :nodes="processNodes"
            :flows="processFlows"
            :bpmn-xml="bpmnXml"
            :current-node-id="currentNodeId"
            :completed-node-ids="completedNodeIds"
            :show-toolbar="true"
            :show-legend="true"
          />
          <el-empty
            v-else
            :description="t('processStart.noProcessDefinition')"
          />
        </div>
      </div>

      <!-- 第二部分：表单 -->
      <div class="section form-section">
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ currentFormName || t('processStart.applicationForm') }}</span>
        </div>
        <div class="section-content">
          <div
            v-if="formFields.length > 0 || formTabs.length > 0"
            class="form-container"
          >
            <FormRenderer
              ref="formRendererRef"
              v-model="formData"
              :fields="formFields"
              :tabs="formTabs"
              :label-width="formLabelWidth"
              :label-position="formLabelPosition"
              :sub-table-bindings="subTableBindings"
              @update:sub-table-data="(id: number, rows: any[]) => { const b = subTableBindings.find(x => x.bindingId === id); if (b) b.data = rows }"
            />
          </div>
          <el-empty
            v-else
            :description="t('processStart.noFormConfig')"
          />
        </div>
      </div>

      <!-- 第三部分：流转记录 -->
      <div class="section history-section">
        <div class="section-header">
          <el-icon><Clock /></el-icon>
          <span>{{ t('processStart.flowHistory') }}</span>
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
            <el-button
              :loading="savingDraft"
              @click="handleSaveDraft"
            >
              <el-icon><FolderOpened /></el-icon> {{ t('processStart.saveDraft') }}
            </el-button>
            <el-button @click="$router.back()">
              {{ t('processStart.cancel') }}
            </el-button>
          </div>
          <div class="right-actions">
            <el-button 
              v-for="action in availableActions" 
              :key="action.id"
              :type="action.type || 'default'"
              :disabled="workspaceStartBlocked && isSubmitLikeAction(action)"
              :loading="submitting && currentAction === action.id"
              @click="handleAction(action)"
            >
              {{ action.label }}
            </el-button>
            <el-button 
              v-if="availableActions.length === 0"
              type="primary" 
              :disabled="workspaceStartBlocked"
              :loading="submitting"
              @click="handleSubmit"
            >
              <el-icon><Promotion /></el-icon> {{ t('processStart.submit') }}
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- N8N Action 对话框 -->
    <N8nActionDialog
      v-model:visible="n8nActionDialogVisible"
      :action-definition="n8nActionDefinition"
      :task-id="''"
      :process-instance-id="''"
      :initial-data="n8nInitialData"
      @executed="handleN8nActionExecuted"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Share, Document, Clock, FolderOpened, Promotion, Loading } from '@element-plus/icons-vue'
import { processApi } from '@/api/process'
import ProcessDiagram, { type ProcessNode, type ProcessFlow } from '@/components/ProcessDiagram.vue'
import ProcessHistory, { type HistoryRecord } from '@/components/ProcessHistory.vue'
import FormRenderer, { type FormField, type FormTab } from '@/components/FormRenderer.vue'
import { normalizePortalViews } from '@/components/formRendererHelpers'
import N8nActionDialog from '@/components/N8nActionDialog.vue'
import type { ActionDefinition } from '@/components/N8nActionDialog.vue'
import { applyAutoFill } from '@/utils/n8nAutoFillEngine'
import { relationTableApi } from '@/api/relationTable'
import { isDisabledMessage } from '@/utils/statusMatcher'
import { getUser } from '@/api/auth'
import { isProcessStartBlockedByWorkspace } from '@/utils/workspaceProcessGuard'
import {
  normalizeSubTableName,
  resolveSubTablePrimaryKeyFields,
  flattenNestedSubTableRowsIntoPayload,
  normalizeSubTableRowsForBinding
} from '@/composables/tasks/shared'
import {
  buildLookupColumnProps,
  enrichLookupColumnPropsFromSubFormRule,
  mergeListViewFieldColumn,
  parseLookupConfig,
  resolveSubListViewColumnsForBinding,
} from '@/components/subTableAddDialogHelpers'
import { resolveAssigneeFieldForBinding } from '@/utils/subTableAssignment'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

/** 本地用户信息随页面加载读取；切换工作台后整页刷新会更新 */
const workspaceStartBlocked = computed(() => isProcessStartBlockedByWorkspace(getUser()))

function isSubmitLikeAction(action: { action?: string; actionType?: string }) {
  return action.actionType === 'PROCESS_SUBMIT' || action.action === 'submit'
}

// 路由参数：key 是功能单元的 ID
const functionUnitId = computed(() => route.params.key as string)
const isDraftMode = computed(() => route.query.draft === 'true')

// 状态
const loading = ref(true)
const loadError = ref('')
const isDisabled = ref(false)
const isAccessDenied = ref(false)
const noProcessForm = ref(false)
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
const completedNodeIds = ref<string[]>([])
const bpmnXml = ref('')

// 表单数据
const formFields = ref<FormField[]>([])
const formTabs = ref<FormTab[]>([])
const formData = ref<Record<string, any>>({})
const currentFormName = ref('')
const formLabelWidth = ref('160px')
const formLabelPosition = ref<'left' | 'right' | 'top'>('left')
const formRendererRef = ref<InstanceType<typeof FormRenderer> | null>(null)

// Sub-table bindings for the start form
const subTableBindings = ref<Array<{
  bindingId: number
  tableId?: number | null
  bindingType: string
  bindingMode: string
  tableName: string
  tableType: string
  tableDescription: string
  /** Designer PK columns from tableBindings (admin-center); avoids hardcoding id/rowId. */
  primaryKeyFields?: string[]
  columns: Array<{ field: string; label: string; type?: string }>
  portalViews?: Record<string, unknown> | null
  data: any[]
}>>([])

/** Match task detail / autosave: key __subTables__ by binding id and table display name so downstream forms with new bindingIds can resolve rows. */
function buildStartFormSubTablesPayload(): Record<string, unknown> {
  const subTables: Record<string, unknown> = {}
  for (const b of subTableBindings.value) {
    const rows = normalizeSubTableRowsForBinding(Array.isArray(b.data) ? b.data : [])
    subTables[b.bindingId] = rows
    subTables[String(b.bindingId)] = rows
    const tn = String(b.tableName || '').trim()
    if (tn) {
      subTables[tn] = rows
      subTables[normalizeSubTableName(tn)] = rows
    }
  }
  flattenNestedSubTableRowsIntoPayload(subTables)
  return subTables
}

const placedBindingIds = computed((): Set<number> => {
  const ids = new Set<number>()
  const collect = (fields: any[]) => fields.forEach((f: any) => {
    if (f.type === 'subTable' && f._bindingId != null) ids.add(f._bindingId)
    if (Array.isArray(f.children)) collect(f.children)
  })
  collect(formFields.value)
  formTabs.value.forEach((tab: any) => collect(tab.fields))
  return ids
})

/**
 * Same closure rule as developer-workstation FormDesigner preview: only SUB/RELATED
 * bindings that are explicitly placed (subTable rule) or required by linkForm columns
 * on an included binding are kept — never show “orphan” bindings at page bottom.
 */
function computeNeededSubTableBindingIds(
  placed: Set<number>,
  allBindings: Array<{
    bindingId: number
    columns?: Array<{ type?: string; props?: Record<string, any> }>
  }>
): Set<number> {
  const needed = new Set<number>(placed)
  let changed = true
  while (changed) {
    changed = false
    for (const b of allBindings) {
      if (!needed.has(b.bindingId)) continue
      for (const col of b.columns || []) {
        if (col.type === 'linkForm') {
          const boundId = col.props?.boundSubTableBindingId
          if (boundId != null) {
            const n = Number(boundId)
            if (!needed.has(n)) {
              needed.add(n)
              changed = true
            }
          }
        }
      }
    }
  }
  return needed
}

// Lookup config fallback map (from rt_lookup_configs)
const lookupDbConfigs = ref<Record<string, { tableId: number; searchFields: string[]; displayField: string; viewFields: any[] }>>({})

// Relation view configs from configJson (designed in developer-workstation)
const relationViewConfigs = ref<Record<string, { viewFields: any[]; allFields: any[] }>>({})

// 流转记录
const historyRecords = ref<HistoryRecord[]>([])

// 可用动作
const availableActions = ref<Array<{
  id: string
  label: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  action?: string
  actionType?: string
  configJson?: string
}>>([])

// N8N Action 对话框状态
const n8nActionDialogVisible = ref(false)
const n8nActionDefinition = ref<ActionDefinition>({ id: 0 })
const n8nInitialData = ref<Record<string, any> | undefined>(undefined)

// 加载功能单元内容
const loadFunctionUnitContent = async () => {
  loading.value = true
  loadError.value = ''
  isDisabled.value = false
  isAccessDenied.value = false
  noProcessForm.value = false
  
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
    
    let startFormInfo: { formId: string | null, formName: string | null, actionIds: string[] | null } = { formId: null, formName: null, actionIds: null }
    
    // 解析流程定义
    if (content.processes && content.processes.length > 0) {
      const processData = content.processes[0]
      bpmnXml.value = processData.data
      // 先获取开始节点后第一个用户任务的 formId 和 formName
      startFormInfo = parseBpmnXmlAndGetStartFormId(processData.data)
      parseBpmnXml(processData.data)
    }
    
    // 解析表单定义 - 根据开始节点的 formId 选择正确的表单
    if (content.forms && content.forms.length > 0) {
      // 功能单元内容中的表单来自 admin-center FormContentDTO：字段为 type（如 "FORM"），无 formType
      const hasProcessForm = content.forms.some(
        (f: any) => f.formType === 'PROCESS' || f.type === 'FORM' || f.type === 'PROCESS'
      )
      if (!hasProcessForm) {
        noProcessForm.value = true
        return
      }

      let selectedForm = content.forms[0] // 默认第一个
      
      // 优先使用 formId 匹配 sourceId（原始表单ID）
      if (startFormInfo.formId) {
        const matchedForm = content.forms.find((f: any) => 
          String(f.sourceId) === startFormInfo.formId
        )
        if (matchedForm) {
          selectedForm = matchedForm
          console.log('Matched form by sourceId:', startFormInfo.formId, '->', selectedForm.name)
        } else {
          // 如果 sourceId 匹配失败，尝试用 formName 匹配
          if (startFormInfo.formName) {
            const matchedByName = content.forms.find((f: any) => f.name === startFormInfo.formName)
            if (matchedByName) {
              selectedForm = matchedByName
              console.log('Matched form by name:', startFormInfo.formName)
            }
          }
        }
      } else if (startFormInfo.formName) {
        // 如果没有 formId，尝试用 formName 匹配
        const matchedForm = content.forms.find((f: any) => f.name === startFormInfo.formName)
        if (matchedForm) {
          selectedForm = matchedForm
        }
      }
      
      currentFormName.value = selectedForm.name

      // Load lookup configs from rt_lookup_configs before parsing form
      lookupDbConfigs.value = {}
      if (selectedForm.sourceId) {
        try {
          const lcRes = await relationTableApi.getLookupConfigs(Number(selectedForm.sourceId))
          for (const lc of (lcRes.data || [])) {
            let sf: string[] = []
            try { sf = typeof lc.searchFields === 'string' ? JSON.parse(lc.searchFields || '[]') : (lc.searchFields || []) } catch { sf = [] }
            lookupDbConfigs.value[lc.componentId] = { tableId: lc.tableId, searchFields: sf, displayField: lc.displayField || '', viewFields: lc.viewFields || [] }
          }
        } catch (e) { console.warn('[start] Failed to load lookup configs:', e) }
      }

      // Parse relationViews from configJson BEFORE parseFormConfig so lookup view fields are available
      try {
        const cfg = typeof selectedForm.data === 'string' ? JSON.parse(selectedForm.data) : (selectedForm.data || {})
        relationViewConfigs.value = cfg.relationViews || {}
      } catch { relationViewConfigs.value = {} }

      parseFormConfig(selectedForm.data)
      
      // Parse subForms from configJson
      let subForms: Record<string, any> = {}
      let formConfigForPk: Record<string, any> = {}
      try {
        const cfg = typeof selectedForm.data === 'string' ? JSON.parse(selectedForm.data) : (selectedForm.data || {})
        subForms = cfg.subForms || {}
        formConfigForPk = cfg || {}
      } catch {}

      console.log('[start] tableBindings:', selectedForm.tableBindings?.length, 'subForms keys:', Object.keys(subForms))

      // Load sub-table bindings (SUB / RELATED, skip PRIMARY)
      const subTablePortalViewsPayload = formConfigForPk.subTablePortalViews || {}
      const bindings: typeof subTableBindings.value = []
      for (const b of (selectedForm.tableBindings || [])) {
        if (b.bindingType === 'PRIMARY') continue
        const tid = (b as { tableId?: number | null }).tableId
        const bindingPortalViews =
          subTablePortalViewsPayload[b.bindingId]
          ?? subTablePortalViewsPayload[String(b.bindingId)]
          ?? null
        bindings.push({
          bindingId: b.bindingId,
          tableId: tid != null ? Number(tid) : null,
          bindingType: b.bindingType,
          bindingMode: b.bindingMode,
          tableName: b.tableDisplayName || b.tableName,
          tableType: b.tableType,
          tableDescription: b.tableDescription,
          primaryKeyFields: resolveSubTablePrimaryKeyFields(
            (b as { primaryKeyFields?: string[] }).primaryKeyFields,
            b.bindingId,
            formConfigForPk
          ),
          columns: deriveColumnsFromBinding(b, subForms, formConfigForPk),
          portalViews: bindingPortalViews,
          data: []
        })
      }

      // Fallback: tableBindings 为空但 subForms 有数据时，直接从 subForms 构建
      if (bindings.length === 0 && Object.keys(subForms).length > 0) {
        console.log('[start] tableBindings empty, building from subForms fallback')
        for (const [bindingIdStr, subForm] of Object.entries(subForms)) {
          const bindingId = Number(bindingIdStr)
          if (!subForm || !Array.isArray((subForm as any).rule)) continue
          const fakeBinding = { bindingId, subFormConfig: subForm }
          bindings.push({
            bindingId,
            tableId: null,
            bindingType: 'SUB',
            bindingMode: 'EDITABLE',
            tableName: 'Request Items',
            tableType: 'SUB',
            tableDescription: '',
            primaryKeyFields: undefined,
            columns: deriveColumnsFromBinding(fakeBinding, subForms, formConfigForPk),
            data: []
          })
        }
      }

      const neededBindingIds = computeNeededSubTableBindingIds(placedBindingIds.value, bindings)
      subTableBindings.value = bindings.filter(b => neededBindingIds.has(b.bindingId))
      console.log('[start] subTableBindings built (designer-placed + linkForm closure):', subTableBindings.value.map(b => ({ id: b.bindingId, cols: b.columns.length })))
    }
    
    // 初始化流转记录（新流程，只有开始节点）
    initHistoryRecords()
    
    // 初始化动作按钮（使用 BPMN 中提取的 actionIds）
    await initActionButtons(startFormInfo.actionIds)
    
    // 如果是草稿模式，加载草稿数据
    if (isDraftMode.value) {
      await loadDraftData()
    }
    
  } catch (error: any) {
    console.error('Failed to load function unit content:', error)
    
    // 检查是否是 403 错误（禁用或无权限）
    if (error.response?.status === 403) {
      const message = error.response?.data?.message || ''
      if (isDisabledMessage(message)) {
        isDisabled.value = true
      } else {
        isAccessDenied.value = true
      }
    } else {
      loadError.value = error.message || t('processStart.loadFailed')
    }
  } finally {
    loading.value = false
  }
}

// 解析 BPMN XML 并获取开始节点后第一个用户任务的 formId
const parseBpmnXmlAndGetStartFormId = (xml: string): { formId: string | null, formName: string | null, actionIds: string[] | null } => {
  if (!xml) return { formId: null, formName: null, actionIds: null }
  
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    
    // 查找开始事件
    const allElements = doc.getElementsByTagName('*')
    let startEventId: string | null = null
    
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      
      if (localName === 'startEvent') {
        startEventId = el.getAttribute('id')
        break
      }
    }
    
    if (!startEventId) return { formId: null, formName: null, actionIds: null }
    
    // 查找从开始事件出发的顺序流
    let firstTaskId: string | null = null
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      
      if (localName === 'sequenceFlow') {
        const sourceRef = el.getAttribute('sourceRef')
        if (sourceRef === startEventId) {
          firstTaskId = el.getAttribute('targetRef')
          break
        }
      }
    }
    
    if (!firstTaskId) return { formId: null, formName: null, actionIds: null }
    
    // 查找第一个用户任务的 formId、formName 和 actionIds
    let formId: string | null = null
    let formName: string | null = null
    let actionIds: string[] | null = null
    
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      
      if (localName === 'userTask') {
        const taskId = el.getAttribute('id')
        
        if (taskId === firstTaskId) {
          // 查找 formId、formName 和 actionIds 属性
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
              if (name === 'actionIds' && value) {
                try {
                  // actionIds 格式: "[46,47]" 或 "46,47"
                  const cleaned = value.replace(/[\[\]\s]/g, '')
                  actionIds = cleaned.split(',').filter(Boolean)
                } catch (e) {
                  console.error('Failed to parse actionIds:', value, e)
                }
              }
            }
          }
          break
        }
      }
    }
    
    return { formId, formName, actionIds }
  } catch (error) {
    console.error('Failed to parse BPMN for start formId:', error)
  }
  
  return { formId: null, formName: null, actionIds: null }
}

// 加载草稿数据
const loadDraftData = async () => {
  try {
    const response = await processApi.getDraft(functionUnitCode.value || functionUnitId.value)
    const draft = response.data || response
    if (draft && draft.formData) {
      const { __subTables__, ...mainFormData } = draft.formData
      formData.value = mainFormData
      // 恢复子表数据
      // 注意：JSON 序列化后 key 变为 string，需同时用 number 和 string 查找
      if (__subTables__ && typeof __subTables__ === 'object') {
        const st = JSON.parse(JSON.stringify(__subTables__)) as Record<string, unknown>
        flattenNestedSubTableRowsIntoPayload(st)
        subTableBindings.value.forEach(binding => {
          const saved = st[binding.bindingId] ?? st[String(binding.bindingId)]
          if (Array.isArray(saved)) {
            binding.data = normalizeSubTableRowsForBinding(saved)
          }
        })
      }
      ElMessage.success(t('processStart.draftLoaded'))
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
    const completed: string[] = []
    
    // Parse position info from BPMN DI
    const positionMap = new Map<string, { x: number; y: number; width: number; height: number }>()
    doc.querySelectorAll('BPMNShape, bpmndi\\:BPMNShape').forEach(shape => {
      const bpmnElement = shape.getAttribute('bpmnElement')
      if (bpmnElement) {
        const bounds = shape.querySelector('Bounds, dc\\:Bounds')
        if (bounds) {
          positionMap.set(bpmnElement, {
            x: parseFloat(bounds.getAttribute('x') || '0'),
            y: parseFloat(bounds.getAttribute('y') || '0'),
            width: parseFloat(bounds.getAttribute('width') || '100'),
            height: parseFloat(bounds.getAttribute('height') || '80')
          })
        }
      }
    })

    const getParentSubProcessId = (element: Element): string | null => {
      let node: Node | null = element.parentNode
      while (node && node.nodeType === 1) {
        const el = node as Element
        const localName = el.localName || el.nodeName.split(':').pop()
        if (localName === 'subProcess') return el.getAttribute('id')
        if (localName === 'process' || localName === 'definitions') return null
        node = el.parentNode
      }
      return null
    }

    // Pre-parse sequence flows for BFS to find the first userTask
    const seqFlows: Array<{sourceRef: string, targetRef: string}> = []
    doc.querySelectorAll('sequenceFlow').forEach(flow => {
      seqFlows.push({
        sourceRef: flow.getAttribute('sourceRef') || '',
        targetRef: flow.getAttribute('targetRef') || ''
      })
    })

    // Collect element types by ID for BFS traversal
    const allElements = doc.getElementsByTagName('*')
    const elementTypeById = new Map<string, string>()
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const id = el.getAttribute('id')
      const localName = el.localName || el.nodeName.split(':').pop() || ''
      if (id) elementTypeById.set(id, localName)
    }

    // BFS from main-process startEvents to find the first userTask
    let firstUserTaskId = ''
    const mainStartIds: string[] = []
    doc.querySelectorAll('startEvent').forEach(event => {
      if (!getParentSubProcessId(event)) {
        mainStartIds.push(event.getAttribute('id') || '')
      }
    })
    const visited = new Set<string>(mainStartIds)
    const queue = [...mainStartIds]
    while (queue.length > 0 && !firstUserTaskId) {
      const currentId = queue.shift()!
      const elType = elementTypeById.get(currentId)
      if (elType === 'userTask') {
        firstUserTaskId = currentId
        break
      }
      for (const f of seqFlows) {
        if (f.sourceRef === currentId && !visited.has(f.targetRef)) {
          visited.add(f.targetRef)
          queue.push(f.targetRef)
        }
      }
    }

    // Parse start events: main-process starts are completed, subprocess starts are pending
    doc.querySelectorAll('startEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `start_${index}`
      const name = event.getAttribute('name') || t('task.startNode')
      const pos = positionMap.get(id)
      const parentSpId = getParentSubProcessId(event)
      const status = parentSpId ? 'pending' : 'completed'
      nodes.push({ id, name, type: 'start', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      if (status === 'completed') completed.push(id)
    })

    currentNodeId.value = firstUserTaskId

    // Parse user tasks: the first userTask is current, rest are pending
    doc.querySelectorAll('userTask').forEach((task, index) => {
      const id = task.getAttribute('id') || `task_${index}`
      const name = task.getAttribute('name') || t('task.taskFallbackName', { index: index + 1 })
      const pos = positionMap.get(id)
      const status = (id === firstUserTaskId) ? 'current' : 'pending'
      nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })
    
    // Parse service tasks
    doc.querySelectorAll('serviceTask').forEach((task, index) => {
      const id = task.getAttribute('id') || `service_${index}`
      const name = task.getAttribute('name') || t('processStart.serviceFallbackName', { index: index + 1 })
      const pos = positionMap.get(id)
      nodes.push({ id, name, type: 'task', status: 'pending', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })

    // Parse subProcess elements
    const subProcessMap = new Map<string, Element>()
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if (localName === 'subProcess') {
        const spId = el.getAttribute('id')
        if (spId) subProcessMap.set(spId, el)
      }
    }
    for (const [spId] of subProcessMap) {
      const pos = positionMap.get(spId)
      const sp = subProcessMap.get(spId)!
      const name = sp.getAttribute('name') || ''
      nodes.push({ id: spId, name, type: 'subprocess', status: 'pending', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    }
    
    // Parse gateways
    doc.querySelectorAll('exclusiveGateway, parallelGateway, inclusiveGateway').forEach((gateway, index) => {
      const id = gateway.getAttribute('id') || `gateway_${index}`
      const name = gateway.getAttribute('name') || ''
      const pos = positionMap.get(id)
      nodes.push({ id, name, type: 'gateway', status: 'pending', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })
    
    // Parse end events
    doc.querySelectorAll('endEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `end_${index}`
      const name = event.getAttribute('name') || t('task.endNode')
      const pos = positionMap.get(id)
      nodes.push({ id, name, type: 'end', status: 'pending', x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })

    completedNodeIds.value = completed

    
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
      // 提取 labelWidth 配置（忽略后端配置，使用固定值避免 label 被截断）
      // if (config.options?.form?.labelWidth) {
      //   formLabelWidth.value = config.options.form.labelWidth
      // }
      // 提取 labelPosition 配置
      if (config.options?.form?.labelPosition) {
        formLabelPosition.value = config.options.form.labelPosition
      }
      
      // 检查是否有 el-tabs 结构
      const tabsRule = rules.find((r: any) => r.type === 'el-tabs')
      
      if (tabsRule && tabsRule.children && Array.isArray(tabsRule.children)) {
        // 有 Tab 布局
        const tabs: FormTab[] = []
        
        for (const tabPane of tabsRule.children) {
          if (tabPane.type === 'el-tab-pane' && tabPane.props) {
            const tabName = tabPane.props.name || `tab_${tabs.length}`
            const tabLabel = tabPane.props.label || `Tab ${tabs.length + 1}`
            
            // 提取该 Tab 下的字段
            const tabFields: FormField[] = []
            if (tabPane.children && Array.isArray(tabPane.children)) {
              tabFields.push(...extractFieldsRecursive(tabPane.children))
            }
            
            tabs.push({
              name: tabName,
              label: tabLabel,
              fields: tabFields
            })
          }
        }
        
        formTabs.value = tabs
        formFields.value = [] // 清空平铺字段
        console.log('Parsed form tabs:', tabs)
      } else {
        // 无 Tab 布局，使用平铺模式
        formTabs.value = []
        formFields.value = extractFieldsRecursive(rules)
        console.log('Parsed form fields (flat):', formFields.value)
      }
    }
  } catch (error) {
    console.error('Failed to parse form config:', error)
  }
}

// 递归提取字段。form-create 的 subForm/tableForm/tableFormColumn 为包装节点：不生成字段，
// 但必须落到下方对 `children` 的递归，否则子表行内字段全部丢失。
const FC_SKIP_TYPES = new Set(['subForm', 'tableForm', 'tableFormColumn'])

const extractFieldsRecursive = (items: any[]): FormField[] => {
  const fields: FormField[] = []
  for (const item of items) {
    const bindingId = item._bindingId ?? item.props?._bindingId
    if (item.type === 'subTable' && bindingId != null) {
      const rawPv = item.props?.portalViews
      const hasWidgetPortalViews =
        rawPv != null && typeof rawPv === 'object' && Object.keys(rawPv).length > 0
      fields.push({
        key: `__subTable_${bindingId}`,
        label: '',
        type: 'subTable',
        _bindingId: Number(bindingId),
        ...(hasWidgetPortalViews ? { portalViews: normalizePortalViews(rawPv) } : {}),
        span: 24
      })
    } else if (isCardRule(item)) {
      fields.push({
        key: getLayoutKey(item, fields.length, 'card'),
        label: getLayoutLabel(item),
        type: 'card',
        span: item.col?.span || 24,
        children: item.children && Array.isArray(item.children)
          ? extractFieldsRecursive(item.children)
          : []
      } as any)
      continue
    } else if (item.type === 'lookup' && item.field) {
      // Lookup field — parse config from form-create rule props.lookupConfig
      let lookupCfg: any = {}
      try {
        const raw = item.props?.lookupConfig
        lookupCfg = typeof raw === 'string' ? JSON.parse(raw || '{}') : (raw || {})
      } catch { lookupCfg = {} }
      // Merge with rt_lookup_configs fallback
      const dbCfg = lookupDbConfigs.value[item.field]
      // Resolve view fields: prefer configJson.relationViews (designed in developer-workstation),
      // then fall back to rt_view_fields (from getLookupConfigs)
      let resolvedViewFields: any[] = []
      if (lookupCfg.bindingId && relationViewConfigs.value[lookupCfg.bindingId]) {
        resolvedViewFields = relationViewConfigs.value[lookupCfg.bindingId].viewFields || []
      }
      if (!resolvedViewFields.length) {
        resolvedViewFields = dbCfg?.viewFields || []
      }
      const field: any = {
        key: item.field,
        label: item.title || item.field,
        type: 'lookup',
        placeholder: item.props?.placeholder || 'Click to search',
        span: item.col?.span || 24,
        _lookupTableId: lookupCfg.tableId || dbCfg?.tableId || 0,
        _lookupSearchFields: (lookupCfg.searchFields?.length ? lookupCfg.searchFields : null) || dbCfg?.searchFields || [],
        _lookupDisplayField: (lookupCfg.displayFields?.[0]) || dbCfg?.displayField || '',
        _lookupDisplayFields: lookupCfg.displayFields || [],
        _lookupSelectedDisplayField: lookupCfg.selectedDisplayField || lookupCfg.displayField || '',
        _lookupFilterConditions: Array.isArray(lookupCfg.filterConditions) ? lookupCfg.filterConditions : [],
        _lookupViewFields: lookupCfg.showBackfillView === false ? [] : resolvedViewFields,
        _lookupShowBackfillView: lookupCfg.showBackfillView !== false
      }
      fields.push(field)
    } else if (FC_SKIP_TYPES.has(item.type)) {
      // Traverse children only; `continue` would drop nested sub-table row fields.
    } else if (item.field) {
      const field = convertFormCreateRule(item)
      if (field) fields.push(field)
    }
    if (item.children && Array.isArray(item.children)) {
      fields.push(...extractFieldsRecursive(item.children))
    }
  }
  return fields
}

const isCardRule = (item: any): boolean => ['el-card', 'elCard', 'card'].includes(item.type)
const getLayoutKey = (item: any, index: number, fallback: string): string =>
  String(item.field || item.name || item.id || `__layout_${fallback}_${index}`)
const getLayoutLabel = (item: any): string =>
  String(item.title || item.props?.header || item.props?.title || '')

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
    'rate': 'rate',
    'slider': 'slider',
    'colorPicker': 'colorPicker',
    'treeSelect': 'treeselect',
    'upload': 'upload',
    'editor': 'editor',
    'signature': 'signature',
    'transfer': 'transfer'
  }
  
  const field: FormField = {
    key: rule.field,
    label: rule.title || rule.field,
    type: typeMap[rule.type] || 'text',
    required: rule.validate?.some((v: any) => v.required) || false,
    placeholder: rule.props?.placeholder || '',
    span: rule.col?.span || 24
  }
  
  // 处理选项 (rule.options or rule.props.options)
  const rawOptions = rule.options || rule.props?.options
  if (rawOptions) {
    if (rule.type === 'cascader') {
      // Cascader needs full hierarchical options with children
      field.options = rawOptions
    } else {
      field.options = rawOptions.map((opt: any) => ({
        label: opt.label || opt.value,
        value: opt.value
      }))
    }
  }

  // 处理级联选择器 props
  if (rule.type === 'cascader') {
    field.cascaderProps = rule.props?.props || rule.props?.cascaderProps
  }
  
  // 处理 textarea
  if (rule.type === 'input' && rule.props?.type === 'textarea') {
    field.type = 'textarea'
    field.rows = rule.props?.rows || 3
  }

  // 处理 password
  if (rule.type === 'input' && rule.props?.type === 'password') {
    field.type = 'password'
  }

  // 处理 timePicker isRange → timerange
  if (rule.type === 'timePicker' && rule.props?.isRange === true) {
    field.type = 'timerange'
  }
  
  // 处理数字输入
  if (rule.type === 'inputNumber') {
    field.min = rule.props?.min
    field.max = rule.props?.max
    field.step = rule.props?.step
    field.precision = rule.props?.precision
  }

  // 处理评分
  if (rule.type === 'rate') { field.max = rule.props?.max || 5 }

  // 处理滑块
  if (rule.type === 'slider') { field.min = rule.props?.min ?? 0; field.max = rule.props?.max ?? 100; field.step = rule.props?.step || 1 }
  
  // 处理默认值
  if (rule.value !== undefined) {
    field.defaultValue = rule.value
  }

  // 处理文件上传
  if (rule.type === 'upload') {
    const action = rule.props?.action
    field.uploadUrl = (action && action !== '/') ? action : '/api/v1/upload'
    field.uploadAccept = rule.props?.accept || '.jpg,.jpeg,.png,.pdf,.docx,.xlsx'
    field.uploadLimit = rule.props?.limit || 1
  }

  if (rule.type === 'userSelect' || rule.type === 'user') {
    field.type = 'user'
  }
  
  // 调试输出
  console.log('Converting rule:', rule.type, '->', field.type, rule)
  
  return field
}

function isSyntheticLookupField(fieldName?: string): boolean {
  return !fieldName || String(fieldName).startsWith('lookup:')
}

function isAssigneeLikeLabel(label?: string): boolean {
  const normalized = String(label || '').trim().toLowerCase()
  return /assignee|处理人|負責人|经办人|經辦人/.test(normalized)
}

// Derive display columns for a sub-table binding based on table type
const deriveColumnsFromBinding = (
  binding: any,
  subForms?: Record<string, any>,
  formConfig?: Record<string, any>,
): Array<{ field: string; label: string; type?: string; required?: boolean; options?: Array<{ label: string; value: any }>; props?: Record<string, any> }> => {
  const subFormRule =
    binding.subFormConfig?.rule ||
    subForms?.[binding.bindingId]?.rule ||
    subForms?.[String(binding.bindingId)]?.rule

  const subFormColumns =
    subFormRule && Array.isArray(subFormRule) && subFormRule.length > 0
      ? subFormRule.map((r: any) => {
        const rProps = r.props || {}
        let type: string | undefined

        if (r.type === 'input') {
          if (rProps.type === 'textarea') type = 'textarea'
          else if (rProps.type === 'password') type = 'password'
          else type = 'text'
        } else if (r.type === 'inputNumber') {
          type = 'number'
        } else if (r.type === 'select') {
          type = 'select'
        } else if (r.type === 'radio') {
          type = 'radio'
        } else if (r.type === 'switch') {
          type = 'switch'
        } else if (r.type === 'datePicker') {
          type = rProps.type === 'datetime' ? 'datetime' : 'date'
        } else if (r.type === 'timePicker') {
          type = rProps.isRange === true ? 'timerange' : 'time'
        } else if (r.type === 'treeSelect' || r.type === 'elTreeSelect') {
          type = 'treeselect'
        } else if (r.type === 'tree') {
          type = 'tree'
        } else if (r.type === 'upload') {
          type = 'upload'
        } else if (r.type === 'userSelect' || r.type === 'user') {
          type = 'user'
        } else if (r.type === 'departmentSelect' || r.type === 'department') {
          type = 'department'
        } else if (r.type === 'colorPicker') {
          type = 'colorPicker'
        } else if (r.type === 'rate') {
          type = 'rate'
        } else if (r.type === 'slider') {
          type = 'slider'
        } else if (r.type === 'editor') {
          type = 'editor'
        } else if (r.type === 'signature') {
          type = 'signature'
        } else if (r.type === 'transfer') {
          type = 'transfer'
        } else if (r.type === 'cascader') {
          type = 'cascader'
        } else if (r.type === 'lookup') {
          type = 'lookup'
        } else {
          type = r.type as any
        }

        const rawOptions = r.options || rProps.options
        const options = rawOptions
          ? (type === 'cascader' ? rawOptions : rawOptions.map((o: any) => ({ label: o.label ?? o.value, value: o.value })))
          : undefined

        const passProps: Record<string, any> = {}
        const propKeys = [
          'action', 'accept', 'multiple', 'precision', 'min', 'max', 'rows', 'maxlength', 'fileNameTargetField',
          'isRange', 'valueFormat', 'startPlaceholder', 'endPlaceholder', 'treeData', 'checkStrictly',
          'showAlpha', 'allowHalf', 'step', 'cascaderProps', 'leftTitle', 'rightTitle',
          'boundSubTableBindingId',
        ]
        for (const key of propKeys) {
          if (rProps[key] !== undefined) passProps[key] = rProps[key]
        }
        if (rProps.data !== undefined) passProps.treeData = rProps.data
        if (rProps.nodeKey !== undefined) passProps.nodeKey = rProps.nodeKey
        if (rProps.showCheckbox !== undefined) passProps.showCheckbox = rProps.showCheckbox
        if (rProps.props !== undefined) passProps.labelProps = rProps.props
        if (type === 'cascader' && rProps.props && !passProps.cascaderProps) passProps.cascaderProps = rProps.props

        if (type === 'lookup') {
          const dbCfg = lookupDbConfigs.value[r.field]
          const lookupCfg = parseLookupConfig(rProps.lookupConfig)
          const relationView = lookupCfg.bindingId ? relationViewConfigs.value[lookupCfg.bindingId] : undefined
          Object.assign(
            passProps,
            buildLookupColumnProps(rProps.lookupConfig || '{}', {
              dbCfg,
              relationViewFields: relationView?.viewFields as Array<Record<string, unknown>> | undefined,
            }),
          )
          if (typeof rProps.selectedDisplayField === 'string' && rProps.selectedDisplayField.trim() !== '') {
            passProps.selectedDisplayField = rProps.selectedDisplayField.trim()
            passProps._lookupSelectedDisplayField = rProps.selectedDisplayField.trim()
          }
        }

        if (options) passProps.options = options

        const required = r.validate?.some((v: any) => v.required) || false
        const readonly = r.disabled === true || rProps.disabled === true

        return {
          field: r.field,
          label: r.title || r.field,
          type,
          required,
          ...(readonly ? { readonly } : {}),
          ...(options ? { options } : {}),
          ...(Object.keys(passProps).length > 0 ? { props: passProps } : {}),
        }
      })
      : []

  const config = formConfig || {}
  const listColumns = resolveSubListViewColumnsForBinding(
    config,
    binding.bindingId,
    subFormColumns.map(col => col.field),
  )

  if (Array.isArray(listColumns) && listColumns.length > 0) {
    const ruleByField = new Map(
      (Array.isArray(subFormRule) ? subFormRule : []).map((ruleItem: any) => [ruleItem?.field, ruleItem]),
    )
    const subFormColumnByField = new Map(subFormColumns.map(col => [col.field, col]))
    const assigneeField = resolveAssigneeFieldForBinding(
      subFormColumns as Array<{ field?: string }>,
      binding.tableDisplayName || binding.tableName,
    )
    return enrichLookupColumnPropsFromSubFormRule(
      listColumns.map((column: any) => {
      if (column.columnType === 'linkForm') {
        return {
          field: column.fieldName || `linkForm:${column.componentId || binding.bindingId}`,
          label: column.columnLabel || column.comment || column.linkText || 'Link Form',
          type: 'linkForm',
          minWidth: column.minWidth || 120,
          props: {
            linkText: column.linkText || 'Details',
            componentId: column.componentId,
            boundSubTableBindingId: column.boundSubTableBindingId,
            boundSubTableName: column.boundSubTableName,
          },
        }
      }
      if (column.columnType === 'lookup') {
        const label = column.columnLabel || column.comment || 'Lookup'
        const field =
          isSyntheticLookupField(column.fieldName) && isAssigneeLikeLabel(label) && assigneeField
            ? assigneeField
            : (column.fieldName || `lookup:${binding.bindingId}`)
        const listLookupCfg = parseLookupConfig(column.lookupConfig || '{}')
        const relationView = listLookupCfg.bindingId
          ? relationViewConfigs.value[listLookupCfg.bindingId]
          : undefined
        return {
          field,
          label,
          type: 'lookup',
          minWidth: 260,
          props: buildLookupColumnProps(column.lookupConfig || '{}', {
            relationViewFields: relationView?.viewFields as Array<Record<string, unknown>> | undefined,
          }),
        }
      }

      const fieldRule = ruleByField.get(column.fieldName)
      const baseColumn = subFormColumnByField.get(column.fieldName)
      if (fieldRule?.type === 'lookup' || fieldRule?.props?.lookupConfig || baseColumn?.type === 'lookup') {
        const rawCfg = fieldRule?.props?.lookupConfig || baseColumn?.props?.lookupConfig || '{}'
        const mergedLookupCfg = parseLookupConfig(rawCfg)
        const dbCfg = lookupDbConfigs.value[column.fieldName]
        const relationView = mergedLookupCfg.bindingId
          ? relationViewConfigs.value[mergedLookupCfg.bindingId]
          : undefined
        return {
          ...(baseColumn || {}),
          field: column.fieldName,
          label: column.comment || column.columnLabel || baseColumn?.label || fieldRule?.title || column.fieldName,
          type: 'lookup',
          minWidth: column.minWidth || baseColumn?.minWidth || 260,
          placeholder: fieldRule?.props?.placeholder || baseColumn?.placeholder,
          props: buildLookupColumnProps(rawCfg, {
            dbCfg,
            relationViewFields: relationView?.viewFields as Array<Record<string, unknown>> | undefined,
          }),
        }
      }

      return mergeListViewFieldColumn(column, baseColumn, fieldRule)
    }),
      subFormRule,
    )
  }

  return enrichLookupColumnPropsFromSubFormRule(subFormColumns, subFormRule)
}

// 初始化流转记录
const initHistoryRecords = () => {
  historyRecords.value = [
    {
      id: 'init',
      nodeId: 'start',
      nodeName: t('processStart.initiateApplication'),
      status: 'current',
      createdTime: new Date().toISOString()
    }
  ]
}

// 初始化动作按钮 - 从 BPMN actionIds 获取自定义动作
const initActionButtons = async (actionIds: string[] | null) => {
  if (actionIds && actionIds.length > 0) {
    try {
      const response = await processApi.getActionsByIds(actionIds)
      const actions = response.data || response
      if (Array.isArray(actions) && actions.length > 0) {
        availableActions.value = actions.map((action: any) => {
          // 根据 actionType 设置按钮颜色
          let btnType: 'primary' | 'success' | 'warning' | 'danger' | 'info' | undefined
          switch (action.actionType) {
            case 'PROCESS_SUBMIT': btnType = 'primary'; break
            case 'APPROVE': btnType = 'success'; break
            case 'REJECT': btnType = 'danger'; break
            case 'N8N_ACTION': btnType = 'warning'; break
            default: btnType = action.buttonColor || undefined
          }
          return {
            id: action.id,
            label: action.actionName,
            type: btnType,
            action: action.actionType,
            actionType: action.actionType,
            configJson: action.configJson
          }
        })
        console.log('Loaded custom action buttons:', availableActions.value)
        return
      }
    } catch (error) {
      console.error('Failed to load action definitions, falling back to default:', error)
    }
  }
  
  // 回退：默认提交按钮
  availableActions.value = [
    {
      id: 'submit',
      label: t('processStart.submitApplication'),
      type: 'primary',
      action: 'submit',
      actionType: 'PROCESS_SUBMIT'
    }
  ]
}

// 保存草稿
const handleSaveDraft = async () => {
  savingDraft.value = true
  try {
    // Include sub-table data in draft
    const draftData = {
      ...formData.value,
      __subTables__: buildStartFormSubTablesPayload()
    }
    await processApi.saveDraft(functionUnitCode.value || functionUnitId.value, draftData)
    ElMessage.success(t('processStart.draftSaved'))
  } catch (error: any) {
    ElMessage.error(error.message || t('processStart.draftSaveFailed'))
  } finally {
    savingDraft.value = false
  }
}

// 处理动作按钮点击
const handleAction = async (action: { id: string; label: string; action?: string; actionType?: string; configJson?: string }) => {
  if (workspaceStartBlocked.value && isSubmitLikeAction(action)) {
    ElMessage.warning(t('processStart.workspaceGuardToast'))
    return
  }
  switch (action.actionType) {
    case 'PROCESS_SUBMIT':
      await handleSubmit()
      break
    case 'N8N_ACTION':
      // 解析 configJson，根据 inputMapping 中的 sourceType 自动收集数据
      const n8nInitData: Record<string, any> = {}
      try {
        const config = action.configJson ? JSON.parse(action.configJson) : {}
        const inputMapping = config.inputMapping || []
        for (const param of inputMapping) {
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
                n8nInitData[param.paramName] = files
              }
            }
          }
        }
      } catch (e) {
        console.error('Failed to parse N8N action config for auto-fill:', e)
      }
      n8nActionDefinition.value = {
        id: Number(action.id) || 0,
        actionName: action.label,
        configJson: action.configJson
      }
      n8nInitialData.value = Object.keys(n8nInitData).length > 0 ? n8nInitData : undefined
      n8nActionDialogVisible.value = true
      break
    default:
      // 对于未知类型，尝试作为提交处理
      if (action.action === 'submit') {
        await handleSubmit()
      } else {
        ElMessage.warning(t('process.unknownActionType', { type: action.actionType || action.action }))
      }
  }
}

// N8N Action 执行完成回调 - 自动填充识别结果
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

// 提交流程
const handleSubmit = async () => {
  if (workspaceStartBlocked.value) {
    ElMessage.warning(t('processStart.workspaceGuardToast'))
    return
  }
  // 验证表单
  if (formRendererRef.value) {
    const valid = await formRendererRef.value.validate()
    if (!valid) {
      ElMessage.warning(t('processStart.pleaseCompleteForm'))
      return
    }
  }
  
  submitting.value = true
  currentAction.value = 'submit'
  
  try {
    const procKey = functionUnitCode.value || functionUnitId.value
    await processApi.startProcess(procKey, {
      processDefinitionKey: procKey,
      formData: {
        ...formData.value,
        __subTables__: buildStartFormSubTablesPayload()
      },
      priority: 'NORMAL'
    })
    
    // 提交成功后删除草稿
    try {
      await processApi.deleteDraft(functionUnitCode.value || functionUnitId.value)
    } catch (e) {
      // 忽略删除草稿失败
    }
    
    ElMessage.success(t('processStart.processSubmitSuccess'))
    
    // Task 16.2: 提交成功后清除 FormRenderer 自动保存数据
    if (formRendererRef.value) {
      formRendererRef.value.clearAutoSave()
    }
    
    router.push('/my-applications')
    
  } catch (error: any) {
    // HTTP 错误已由 api/request 拦截器展示（含后端 error.message）
    if (!error?.response) {
      ElMessage.error(error?.message || t('processStart.submitFailed'))
    }
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
  width: 100%;
  max-width: 100%;
  margin: 0;
  box-sizing: border-box;

  .workspace-guard-alert {
    margin-bottom: 16px;
  }
  
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
  
  .process-start-loading {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    min-height: 280px;
    color: var(--text-secondary);
    font-size: 14px;
    background: var(--background-white, #fff);
    border: 1px solid var(--border-color);
    border-radius: 8px;
  }
  
  .error-state {
    padding: 40px 0;
  }
  
  .disabled-state,
  .access-denied-state,
  .no-process-form-state {
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
}
</style>
