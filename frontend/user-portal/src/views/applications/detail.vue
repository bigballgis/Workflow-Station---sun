<template>
  <div class="application-detail-page">
    <!-- Page header -->
    <div class="page-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" @click="$router.back()">{{ t('applicationDetail.back') }}</el-button>
        <h1>{{ processInfo.processDefinitionName || t('applicationDetail.applicationDetail') }}</h1>
        <el-tag :type="getStatusType(processInfo.status)" size="small">{{ getStatusLabel(processInfo.status) }}</el-tag>
      </div>
      <el-button :icon="Refresh" @click="loadProcessDetail" :loading="loading">{{ t('applicationDetail.refresh') }}</el-button>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="skeleton-content">
      <el-skeleton animated :count="3">
        <template #template>
          <el-skeleton-item variant="rect" style="height: 120px; margin-bottom: 20px;" />
          <el-skeleton-item variant="rect" style="height: 300px; margin-bottom: 20px;" />
          <el-skeleton-item variant="rect" style="height: 200px;" />
        </template>
      </el-skeleton>
    </div>

    <!-- Main content -->
    <div v-else class="content-sections">
      <!-- Section 1: Basic info -->
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

      <!-- Section 2: Process diagram -->
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
            v-if="bpmnXml || processNodes.length > 0"
            :nodes="processNodes"
            :flows="processFlows"
            :bpmn-xml="bpmnXml"
            :current-node-id="currentNodeId"
            :completed-node-ids="completedNodeIds"
            :show-toolbar="true"
            :show-legend="true"
          />
          <el-empty v-else :description="t('applicationDetail.noProcessDefinition')" />
        </div>
      </div>

      <!-- Section 3: Previous node forms (read-only, displayed in order) -->
      <template v-for="prevForm in previousForms" :key="prevForm.formId">
        <!-- MI subtask form: no card rendered, only sub-tables (participants etc.) -->
        <template v-if="prevForm.isMiSubTask">
          <div v-for="binding in prevForm.subTableBindings" :key="binding.bindingId" class="sub-table-section">
            <SubTableField
              :title="binding.tableName"
              :columns="binding.columns"
              v-model="binding.data"
              :editable="false"
              :assignee-field="hasAssignmentData(binding.data) ? 'assignee_user_id' : undefined"
              :show-task-status="hasTaskStatusData(binding.data)"
              :show-view-detail="hasSubTaskFormSchema && hasTaskStatusData(binding.data)"
              @viewDetail="(row: any) => openSubTaskDetailDialog(row)"
            />
          </div>
        </template>
        <div v-else class="section form-section">
          <div class="section-header">
            <el-icon><Document /></el-icon>
            <span>{{ prevForm.formName }}</span>
            <el-tag type="info" size="small">{{ t('applicationDetail.completed') }}</el-tag>
          </div>
          <div class="section-content">
            <div v-if="prevForm.fields.length > 0 || prevForm.tabs.length > 0" class="form-container">
              <FormRenderer
                :fields="prevForm.fields"
                :tabs="prevForm.tabs"
                v-model="formData"
                :label-width="prevForm.labelWidth"
                :readonly="true"
              />
            </div>
            <template v-if="prevForm.subTableBindings.length > 0">
              <div v-for="binding in prevForm.subTableBindings" :key="binding.bindingId" class="sub-table-section">
                <SubTableField
                  :title="binding.tableName"
                  :columns="binding.columns"
                  v-model="binding.data"
                  :editable="false"
                  :assignee-field="hasAssignmentData(binding.data) ? 'assignee_user_id' : undefined"
                  :show-task-status="hasTaskStatusData(binding.data)"
                  :show-view-detail="hasSubTaskFormSchema && hasTaskStatusData(binding.data)"
                  @viewDetail="(row: any) => openSubTaskDetailDialog(row)"
                />
              </div>
            </template>
          </div>
        </div>
      </template>

      <!-- Form data (MI subtask skips the standalone card; form fields are shown via the sub-table Detail button dialog) -->
      <div v-if="!currentFormIsMiSubTask" class="section form-section">
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ currentFormName || t('applicationDetail.applicationForm') }}</span>
        </div>
        <div class="section-content">
          <div v-if="formFields.length > 0 || formTabs.length > 0" class="form-container">
            <FormRenderer
              :key="`app-form-${processId}`"
              :fields="formFields"
              :tabs="formTabs"
              v-model="formData"
              :label-width="formLabelWidth"
              :readonly="true"
              :subTableBindings="subTableBindings"
              @update:subTableData="(id: number, rows: any[]) => { const b = subTableBindings.find(x => x.bindingId === id); if (b) b.data = rows }"
            />
          </div>
          <el-empty v-else :description="t('applicationDetail.noFormData')" />

          <!-- Sub-tables (SUB / RELATED bindings) -->
          <template v-if="bottomSubTableBindings.length > 0">
            <div
              v-for="binding in bottomSubTableBindings"
              :key="binding.bindingId"
              class="sub-table-section"
            >
              <SubTableField
                :title="binding.tableName"
                :columns="binding.columns"
                v-model="binding.data"
                :editable="false"
                :assignee-field="hasAssignmentData(binding.data) ? 'assignee_user_id' : undefined"
                :show-task-status="hasTaskStatusData(binding.data)"
                :show-view-detail="hasSubTaskFormSchema && hasTaskStatusData(binding.data)"
                @viewDetail="(row: any) => openSubTaskDetailDialog(row)"
              />
            </div>
          </template>
        </div>
      </div>

      <!-- Change history panel (title and collapse handled internally by ChangeHistoryPanel) -->
      <div class="section change-history-section">
        <ChangeHistoryPanel :process-instance-id="processId" />
      </div>

      <!-- Section 4: Flow history -->
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

      <!-- Sub-task form detail dialog -->
      <el-dialog
        v-model="subTaskDetailVisible"
        :title="subTaskDetailTitle"
        width="600px"
        destroy-on-close
      >
        <div v-if="subTaskDetailFields.length > 0" class="form-container">
          <FormRenderer
            :fields="subTaskDetailFields"
            :tabs="[]"
            v-model="subTaskDetailData"
            label-width="160px"
            :readonly="true"
          />
        </div>
        <el-empty v-else :description="t('applicationDetail.noFormData')" />
        <template #footer>
          <el-button @click="subTaskDetailVisible = false">{{ t('applicationDetail.close') }}</el-button>
        </template>
      </el-dialog>

      <!-- Section 5: Action buttons -->
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
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, InfoFilled, Share, Document, Clock, Bell, RefreshLeft, Refresh } from '@element-plus/icons-vue'
import { processApi, type ProcessInstance } from '@/api/process'
import ProcessDiagram, { type ProcessNode, type ProcessFlow } from '@/components/ProcessDiagram.vue'
import ProcessHistory, { type HistoryRecord } from '@/components/ProcessHistory.vue'
import FormRenderer, { type FormField, type FormTab } from '@/components/FormRenderer.vue'
import SubTableField from '@/components/SubTableField.vue'
import ChangeHistoryPanel from '@/components/ChangeHistoryPanel.vue'
import { formatDate } from '@/utils/dateFormat'
import { relationTableApi } from '@/api/relationTable'
import { isRejectedName } from '@/utils/statusMatcher'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const processId = route.params.id as string
// Snapshot timestamp from completed tasks entry, used to only show process state up to that point
const snapshotTime = route.query.snapshotTime as string | undefined
// Snapshot task name from completed tasks entry, used to highlight that node as current
const snapshotTaskName = route.query.snapshotTaskName as string | undefined

/** Consistent with request interceptor; used to determine if the initiator is viewing their own application */
function getPortalUserId(): string | null {
  let userId = localStorage.getItem('userId')
  if (!userId) {
    const userStr = localStorage.getItem('user')
    if (userStr) {
      try {
        const user = JSON.parse(userStr)
        userId = user.userId || user.id
      } catch {
        /* ignore */
      }
    }
  }
  return userId || null
}

const loading = ref(true)
const urging = ref(false)
const withdrawing = ref(false)
const processInfo = ref<ProcessInstance>({} as ProcessInstance)

// Process diagram data
const processNodes = ref<ProcessNode[]>([])
const processFlows = ref<ProcessFlow[]>([])
const currentNodeId = ref('')
const completedNodeIds = ref<string[]>([])
const bpmnXml = ref('')

// Form data
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

const placedBindingIds = computed((): Set<number> => {
  const ids = new Set<number>()
  const collect = (fields: any[]) => fields.forEach((f: any) => {
    if (f.type === 'subTable' && f._bindingId != null) ids.add(f._bindingId)
  })
  collect(formFields.value)
  formTabs.value.forEach((tab: any) => collect(tab.fields))
  return ids
})

const bottomSubTableBindings = computed(() =>
  subTableBindings.value.filter(b => !placedBindingIds.value.has(b.bindingId))
)

// Lookup config fallback map (from rt_lookup_configs)
const lookupDbConfigs = ref<Record<string, { tableId: number; searchFields: string[]; displayField: string; viewFields: any[] }>>({})

// Relation view configs from configJson (designed in developer-workstation)
const relationViewConfigs = ref<Record<string, { viewFields: any[]; allFields: any[] }>>({})

// Previous node forms (read-only display, ordered)
interface PreviousFormEntry {
  formId: string
  formName: string
  labelWidth: string
  fields: FormField[]
  tabs: FormTab[]
  isMiSubTask: boolean
  subTableBindings: Array<{
    bindingId: number
    bindingType: string
    bindingMode: string
    foreignKeyField: string | null
    tableName: string
    tableType: string
    tableDescription: string
    columns: Array<{ field: string; label: string; type?: string }>
    data: any[]
  }>
}
const previousForms = ref<PreviousFormEntry[]>([])

// Sub-task form detail dialog
const subTaskDetailVisible = ref(false)
const subTaskDetailTitle = ref('')
const subTaskDetailFields = ref<FormField[]>([])
const subTaskDetailData = ref<Record<string, any>>({})
const subTaskFormSchema = ref<any>(null)
const subTaskFormId = ref<string | null>(null)
const currentFormIsMiSubTask = ref(false)

const hasSubTaskFormSchema = computed(() => !!subTaskFormSchema.value)

function hasTaskStatusData(rows: any[]): boolean {
  if (!Array.isArray(rows) || rows.length === 0) return false
  if (snapshotTaskName) {
    return rows.some(r => r && r.task_status === 'COMPLETED')
  }
  return rows.some(r => r && r.task_status !== undefined)
}

function openSubTaskDetailDialog(row: any) {
  if (!subTaskFormSchema.value) return
  const schema = subTaskFormSchema.value
  const fields = extractFieldsRecursive(
    schema.rule && Array.isArray(schema.rule) ? schema.rule : (Array.isArray(schema) ? schema : [])
  )
  subTaskDetailFields.value = fields

  const mergedData: Record<string, any> = { ...row }
  // Fallback: for MI form fields absent from the row, use process-level variables.
  // This covers data saved before the row-level merge was introduced.
  for (const f of fields) {
    if (f.key && (mergedData[f.key] === undefined || mergedData[f.key] === null)) {
      if (formData.value[f.key] !== undefined) {
        mergedData[f.key] = formData.value[f.key]
      }
    }
  }
  subTaskDetailData.value = mergedData

  const formTitle = subTaskFormSchema.value._formName || t('applicationDetail.subTaskFormTitle')
  subTaskDetailTitle.value = row.assignee_display_name
    ? `${formTitle} — ${row.assignee_display_name}`
    : formTitle
  subTaskDetailVisible.value = true
}

// Flow history records
const historyRecords = ref<HistoryRecord[]>([])

const getCurrentAssigneeDisplay = () => {
  // Direct assignee
  if (processInfo.value.currentAssignee) {
    return processInfo.value.currentAssignee
  }
  // Candidate users (counter-sign scenario)
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

// Find the next node name after a completed task, using flow history and the BPMN diagram
const findNextNodeName = (taskName: string): string | null => {
  const taskNode = processNodes.value.find(n => n.name === taskName)
  if (!taskNode) return null

  // For direct connections to the next userTask (e.g. "Submit Request"), return immediately.
  // For gateway paths, use processInfo.currentNode (the DB-recorded final node) to resolve the branch.
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
        // Non-gateway node: if there's only one outgoing path, return directly
        if (outFlows.length === 1 && candidates.length === 0) return target.name
        candidates.push(target.name)
      }
    }
  }

  if (candidates.length === 0) return null
  if (candidates.length === 1) return candidates[0]

  // Multiple candidates (gateway branches): prefer the DB-recorded final node
  if (originalCurrentNode && candidates.includes(originalCurrentNode)) {
    return originalCurrentNode
  }
  // Otherwise return the first candidate
  return candidates[0]
}

// Load process details
const loadProcessDetail = async () => {
  loading.value = true
  try {
    const res = await processApi.getProcessDetail(processId)
    const data = res.data || res
    if (data) {
      processInfo.value = data
      if (data.variables) formData.value = data.variables
      
      // Load flow history first
      await loadProcessHistory()
      
      // Then load function unit content (including BPMN parsing)
      if (data.processDefinitionKey) {
        try {
          await loadFunctionUnitContent(data.processDefinitionKey)
        } catch (error) {
          console.error('Failed to load function unit content, but continuing:', error)
        }
      }

      // MI sub-task data isolation for completed task view:
      // When a sub-task assignee views the process from Completed Tasks,
      // filter sub-table rows to only show the row assigned to them.
      if (snapshotTaskName) {
        const viewerId = getPortalUserId()
        const initiatorId = (data.startUserId || '').trim()
        if (viewerId && viewerId !== initiatorId) {
          const filterByAssignee = (bindings: typeof subTableBindings.value) => {
            for (const binding of bindings) {
              if (binding.data && binding.data.length > 0 && hasAssignmentData(binding.data)) {
                const filtered = binding.data.filter(
                  (row: any) => row.assignee_user_id === viewerId
                )
                if (filtered.length > 0) {
                  binding.data = filtered
                }
              }
            }
          }
          filterByAssignee(subTableBindings.value)
          for (const prevForm of previousForms.value) {
            filterByAssignee(prevForm.subTableBindings)
          }
        }
      }

      // Snapshot mode (RUNNING only): compute the next node after snapshotTaskName via BPMN and mark it as current; skip for terminated processes
      if (snapshotTaskName && data.status === 'RUNNING' && processNodes.value.length > 0 && processFlows.value.length > 0) {
        const nextNodeName = findNextNodeName(snapshotTaskName)
        if (nextNodeName) {
          processInfo.value = { ...processInfo.value, currentNode: nextNodeName }
          const nextNode = processNodes.value.find(n => n.name === nextNodeName)
          if (nextNode) {
            if (nextNode.type === 'end') {
              if (isRejectedName(nextNodeName)) {
                nextNode.status = 'rejected'
              } else {
                nextNode.status = 'completed'
                if (!completedNodeIds.value.includes(nextNode.id)) {
                  completedNodeIds.value.push(nextNode.id)
                }
              }
            } else {
              // Non-end event (e.g. next userTask): mark as current (orange)
              nextNode.status = 'current'
              // Remove from completed list to prevent downstream gateways from being incorrectly marked green
              completedNodeIds.value = completedNodeIds.value.filter(id => id !== nextNode.id)
              // Reset downstream nodes that were only marked completed because the current node was their predecessor
              const downstreamIds = processFlows.value.filter(f => f.sourceRef === nextNode.id).map(f => f.targetRef)
              for (const targetId of downstreamIds) {
                const targetNode = processNodes.value.find(n => n.id === targetId)
                const completedIncoming = processFlows.value.filter(f => f.targetRef === targetId && completedNodeIds.value.includes(f.sourceRef))
                const wasOnlyCurrent = completedIncoming.length === 0 && (targetNode?.status === 'completed' || completedNodeIds.value.includes(targetId))
                if (targetNode && wasOnlyCurrent) {
                  targetNode.status = 'pending'
                  completedNodeIds.value = completedNodeIds.value.filter(id => id !== targetId)
                }
              }
            }
            currentNodeId.value = nextNode.id
          }
        }
      }
    }
  } catch (error: any) {
    console.error('Failed to load process detail:', error)
    ElMessage.error(t('applicationDetail.loadFailed'))
  } finally {
    loading.value = false
  }
}

// Load function unit content
const loadFunctionUnitContent = async (processKey: string) => {
  try {
    const response = await processApi.getFunctionUnitContent(processKey)
    const content = response.data || response
    if (content.error) {
      console.error('Function unit content error:', content.error)
      return
    }
    
    let currentFormInfo: { formId: string | null, formName: string | null } = { formId: null, formName: null }
    /** Initiator viewing their own application (non-task snapshot): only show the first userTask form, avoiding showing approval node forms as the application form */
    let useInitiatorFormOnly = false

    if (content.processes?.length > 0) {
      const xml = content.processes[0].data
      const viewerId = getPortalUserId()
      const initiatorId = (processInfo.value.startUserId || '').trim()
      useInitiatorFormOnly =
        !!viewerId &&
        !!initiatorId &&
        viewerId.trim() === initiatorId &&
        !snapshotTaskName &&
        !snapshotTime

      if (useInitiatorFormOnly) {
        currentFormInfo = parseBpmnXmlAndGetFirstUserTaskFormInfo(xml)
        // When parsing fails, keep empty; fall back to forms[0] below to avoid reverting to current node and selecting the approval form
      } else {
        currentFormInfo = parseBpmnXmlAndGetFormId(xml)
      }
      bpmnXml.value = xml
      parseBpmnXml(xml)
    }
    
    if (content.forms?.length > 0) {      // Select the correct form based on the current node formId
      let selectedForm = content.forms[0] // Default to first
      
      // Prefer matching formId to sourceId (original form ID)
      if (currentFormInfo.formId) {
        const matchedForm = content.forms.find((f: any) => 
          String(f.sourceId) === currentFormInfo.formId
        )
        if (matchedForm) {
          selectedForm = matchedForm
        } else {
          if (currentFormInfo.formName) {
            const matchedByName = content.forms.find((f: any) => f.name === currentFormInfo.formName)
            if (matchedByName) {
              selectedForm = matchedByName
            }
          }
        }
      } else if (currentFormInfo.formName) {
        // If no formId, try matching by formName
        const matchedForm = content.forms.find((f: any) => f.name === currentFormInfo.formName)
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
        } catch (e) { console.warn('[app] Failed to load lookup configs:', e) }
      }

      // Parse relationViews from configJson BEFORE parseFormConfig so lookup view fields are available
      try {
        const cfg = typeof selectedForm.data === 'string' ? JSON.parse(selectedForm.data) : (selectedForm.data || {})
        relationViewConfigs.value = cfg.relationViews || {}
      } catch { relationViewConfigs.value = {} }

      parseFormConfig(selectedForm.data)

      // Parse subForms from configJson
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
      // Restore sub-table data from variables
      // Note: JSON serialization converts keys to string; search by both number and string
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

      // Detect multi-instance subtask form.
      // Primary: use BPMN (userTask with multiInstanceLoopCharacteristics) for accurate identification.
      // Fallback: pick the form different from selectedForm (for Detail dialog only, not for filtering).
      subTaskFormSchema.value = null
      subTaskFormId.value = null
      if (content.forms?.length > 1) {
        let detected = false
        if (content.processes?.length > 0) {
          const miFormSourceId = findMiSubTaskFormIdFromBpmn(content.processes[0].data)
          if (miFormSourceId) {
            const taskForm = content.forms.find((f: any) => String(f.sourceId) === miFormSourceId)
            if (taskForm) {
              try {
                const cfg = typeof taskForm.data === 'string' ? JSON.parse(taskForm.data) : (taskForm.data || {})
                cfg._formName = taskForm.name
                subTaskFormSchema.value = cfg
                subTaskFormId.value = String(taskForm.id)
                detected = true
              } catch { /* ignore parse errors */ }
            }
          }
        }
        if (!detected) {
          const taskForm = content.forms.find((f: any) =>
            f.id !== selectedForm.id && f.name !== selectedForm.name
          )
          if (taskForm) {
            try {
              const cfg = typeof taskForm.data === 'string' ? JSON.parse(taskForm.data) : (taskForm.data || {})
              cfg._formName = taskForm.name
              subTaskFormSchema.value = cfg
              // Do NOT set subTaskFormId here — the fallback detection can pick the
              // wrong form (e.g. main form when selectedForm is already the subtask form),
              // so we only use this for the Detail dialog, never for filtering previousForms.
            } catch { /* ignore parse errors */ }
          }
        }
      }

      currentFormIsMiSubTask.value = !!(
        (subTaskFormId.value && String(selectedForm.id) === subTaskFormId.value) ||
        (subTaskFormSchema.value && subTaskFormSchema.value._formName === selectedForm.name)
      )

      // Collect forms bound to nodes before the current one (read-only display); skip when initiator views own application (avoid duplicate application + approval form display)
      if (content.processes?.length > 0 && !useInitiatorFormOnly) {
        const prevFormIds = parseBpmnXmlAndGetPreviousFormIds(content.processes[0].data)
        const collectedPrevForms: PreviousFormEntry[] = []
        const savedSubTables = formData.value.__subTables__

        for (const info of prevFormIds) {
          let prevForm: any = null
          if (info.formId) {
            if (info.formId === String(selectedForm.sourceId)) continue
            prevForm = content.forms.find((f: any) => String(f.sourceId) === info.formId)
          }
          if (!prevForm && info.formName) {
            if (info.formName === selectedForm.name) continue
            prevForm = content.forms.find((f: any) => f.name === info.formName)
          }
          if (!prevForm && (info as any).taskName) {
            if ((info as any).taskName === selectedForm.name) continue
            prevForm = content.forms.find((f: any) => f.name === (info as any).taskName)
          }
          if (!prevForm || prevForm.id === selectedForm.id) continue
          if (collectedPrevForms.some(e => e.formId === String(prevForm.id))) continue
          // Skip the subtask form — its content is shown via the Detail button
          // in the participants sub-table. But never skip a form that has sub-table
          // bindings (it carries the participants table needed for display).
          const isKnownMiSubTaskForm =
            (subTaskFormId.value && String(prevForm.id) === subTaskFormId.value) ||
            (subTaskFormSchema.value && prevForm.name === subTaskFormSchema.value._formName)
          if (isKnownMiSubTaskForm) {
            const bindings = prevForm.tableBindings || []
            if (!bindings.some((b: any) => b.bindingType !== 'PRIMARY')) {
              continue
            }
          }

          const parsedFields: FormField[] = []
          const parsedTabs: FormTab[] = []
          try {
            const cfg = typeof prevForm.data === 'string' ? JSON.parse(prevForm.data) : (prevForm.data || {})
            const rules = cfg.rule && Array.isArray(cfg.rule) ? cfg.rule : (Array.isArray(cfg) ? cfg : null)
            if (rules) {
              const tabsRule = rules.find((r: any) => r.type === 'el-tabs')
              if (tabsRule?.children) {
                for (const tabPane of tabsRule.children) {
                  if (tabPane.type === 'el-tab-pane' && tabPane.props) {
                    const tabFields: FormField[] = []
                    if (tabPane.children) tabFields.push(...extractFieldsRecursive(tabPane.children))
                    parsedTabs.push({ name: tabPane.props.name || `tab_${parsedTabs.length}`, label: tabPane.props.label || `Tab ${parsedTabs.length + 1}`, fields: tabFields })
                  }
                }
              } else {
                parsedFields.push(...extractFieldsRecursive(rules))
              }
            }
          } catch {}

          let prevSubForms: Record<string, any> = {}
          try {
            const cfg = typeof prevForm.data === 'string' ? JSON.parse(prevForm.data) : (prevForm.data || {})
            prevSubForms = cfg.subForms || {}
          } catch {}
          const prevBindings: PreviousFormEntry['subTableBindings'] = []
          for (const b of (prevForm.tableBindings || [])) {
            if (b.bindingType === 'PRIMARY') continue
            const cols = deriveColumnsFromBinding(b, prevSubForms)
            const binding = {
              bindingId: b.bindingId, bindingType: b.bindingType, bindingMode: b.bindingMode,
              foreignKeyField: b.foreignKeyField, tableName: b.tableDisplayName || b.tableName,
              tableType: b.tableType, tableDescription: b.tableDescription, columns: cols, data: [] as any[]
            }
            if (savedSubTables) {
              const saved = savedSubTables[b.bindingId] ?? savedSubTables[String(b.bindingId)]
              if (Array.isArray(saved)) binding.data = saved
            }
            prevBindings.push(binding)
          }

          collectedPrevForms.push({
            formId: String(prevForm.id),
            formName: prevForm.name,
            labelWidth: formLabelWidth.value,
            fields: isKnownMiSubTaskForm ? [] : parsedFields,
            tabs: isKnownMiSubTaskForm ? [] : parsedTabs,
            isMiSubTask: !!isKnownMiSubTaskForm,
            subTableBindings: prevBindings
          })
        }

        previousForms.value = collectedPrevForms
      } else {
        previousForms.value = []
      }
    }
  } catch (error) {
    console.error('Failed to load function unit content:', error)
  }
}

// Parse BPMN XML and get the current node formId and formName
const parseBpmnXmlAndGetFormId = (xml: string): { formId: string | null, formName: string | null } => {
  if (!xml) return { formId: null, formName: null }
  
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    // Snapshot mode (from Completed Tasks): use snapshotTaskName; otherwise use currentNode
    const currentNodeName = snapshotTaskName || processInfo.value.currentNode || ''
    
    const allElements = doc.getElementsByTagName('*')

    // Collect all userTasks and sequenceFlows
    const tasks = new Map<string, { name: string; formId: string | null; formName: string | null }>()
    const flows: Array<{ source: string; target: string }> = []

    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if (localName === 'userTask') {
        const id = el.getAttribute('id') || ''
        const name = el.getAttribute('name') || ''
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
        tasks.set(id, { name, formId, formName })
        // Direct match on current node
        if (name === currentNodeName || id === currentNodeName) {
          return { formId, formName }
        }
      } else if (localName === 'sequenceFlow') {
        flows.push({ source: el.getAttribute('sourceRef') || '', target: el.getAttribute('targetRef') || '' })
      }
    }

    // Current node is not a userTask (e.g. process completed, currentNode = "End")
    // Find the last userTask: node whose outgoing edges do not point to any other userTask
    const taskIds = new Set(tasks.keys())
    for (const [id, info] of tasks) {
      const outTargets = flows.filter(f => f.source === id).map(f => f.target)
      const hasUserTaskSuccessor = outTargets.some(t => taskIds.has(t))
      if (!hasUserTaskSuccessor) {
        return { formId: info.formId, formName: info.formName }
      }
    }
    // Final fallback: take the last one
    const last = [...tasks.values()].pop()
    if (last) return { formId: last.formId, formName: last.formName }
  } catch (error) {
    console.error('Failed to parse BPMN for formId:', error)
  }
  
  return { formId: null, formName: null }
}

/** BFS from startEvent to find the first userTask-bound form (initiator application content) */
const parseBpmnXmlAndGetFirstUserTaskFormInfo = (xml: string): { formId: string | null, formName: string | null } => {
  if (!xml) return { formId: null, formName: null }
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const allElements = doc.getElementsByTagName('*')
    const tasks = new Map<string, { name: string; formId: string | null; formName: string | null }>()
    const flows: Array<{ source: string; target: string }> = []

    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if (localName === 'userTask') {
        const id = el.getAttribute('id') || ''
        const name = el.getAttribute('name') || ''
        let formId: string | null = null
        let formName: string | null = null
        const props = el.getElementsByTagName('*')
        for (let j = 0; j < props.length; j++) {
          const p = props[j]
          const ln = p.localName || p.nodeName.split(':').pop()
          if (ln === 'property' || ln === 'values') {
            const n = p.getAttribute('name')
            const v = p.getAttribute('value')
            if (n === 'formId' && v) formId = v
            if (n === 'formName' && v) formName = v
          }
        }
        tasks.set(id, { name, formId, formName })
      } else if (localName === 'sequenceFlow') {
        flows.push({ source: el.getAttribute('sourceRef') || '', target: el.getAttribute('targetRef') || '' })
      }
    }

    let startId = ''
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      if ((el.localName || el.nodeName.split(':').pop()) === 'startEvent') {
        startId = el.getAttribute('id') || ''
        break
      }
    }

    const forwardAdj = new Map<string, string[]>()
    for (const f of flows) {
      if (!forwardAdj.has(f.source)) forwardAdj.set(f.source, [])
      forwardAdj.get(f.source)!.push(f.target)
    }

    if (!startId) return { formId: null, formName: null }

    const visited = new Set<string>()
    const queue: string[] = [startId]
    visited.add(startId)

    while (queue.length > 0) {
      const node = queue.shift()!
      if (tasks.has(node)) {
        const info = tasks.get(node)!
        return { formId: info.formId, formName: info.formName }
      }
      for (const next of forwardAdj.get(node) || []) {
        if (!visited.has(next)) {
          visited.add(next)
          queue.push(next)
        }
      }
    }
  } catch (e) {
    console.error('Failed to parse BPMN for first user task form:', e)
  }
  return { formId: null, formName: null }
}

// Parse BPMN XML: return form info bound to all nodes before the current node, in topological order (deduplicated)
const parseBpmnXmlAndGetPreviousFormIds = (xml: string): Array<{ formId: string | null, formName: string | null, taskName: string | null }> => {
  if (!xml) return []
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const allElements = doc.getElementsByTagName('*')
    const currentNodeName = snapshotTaskName || processInfo.value.currentNode || ''

    const tasks = new Map<string, { name: string; formId: string | null; formName: string | null }>()
    const flows: Array<{ source: string; target: string }> = []

    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if (localName === 'userTask') {
        const id = el.getAttribute('id') || ''
        const name = el.getAttribute('name') || ''
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
        tasks.set(id, { name, formId, formName })
      } else if (localName === 'sequenceFlow') {
        flows.push({ source: el.getAttribute('sourceRef') || '', target: el.getAttribute('targetRef') || '' })
      }
    }
    const taskIds = new Set(tasks.keys())
    let currentId = ''
    for (const [id, info] of tasks) {
      if (info.name === currentNodeName || id === currentNodeName) { currentId = id; break }
    }
    // If no match (process completed, currentNode = "End"), find the last userTask (no outgoing edges to other userTasks)
    if (!currentId) {
      const taskIds = new Set(tasks.keys())
      // Find node with no outgoing edges to other userTasks (the last userTask in the process)
      for (const [id] of tasks) {
        const outTargets = flows.filter(f => f.source === id).map(f => f.target)
        const hasUserTaskSuccessor = outTargets.some(t => taskIds.has(t))
        if (!hasUserTaskSuccessor) { currentId = id; break }
      }
      // Still not found, take the last one
      if (!currentId) currentId = [...tasks.keys()].pop() || ''
    }
    if (!currentId) return []

    // Find startEvent
    let startId = ''
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      if ((el.localName || el.nodeName.split(':').pop()) === 'startEvent') {
        startId = el.getAttribute('id') || ''; break
      }
    }

    const forwardAdj = new Map<string, string[]>()
    for (const f of flows) {
      if (!forwardAdj.has(f.source)) forwardAdj.set(f.source, [])
      forwardAdj.get(f.source)!.push(f.target)
    }

    // BFS from start, collecting userTasks encountered before reaching currentId
    const visited = new Set<string>()
    const queue: string[] = [startId]
    const orderedPrevTaskIds: string[] = []
    visited.add(startId)

    while (queue.length > 0) {
      const node = queue.shift()!
      if (node === currentId) break
      if (tasks.has(node)) orderedPrevTaskIds.push(node)
      for (const next of (forwardAdj.get(node) || [])) {
        if (!visited.has(next)) { visited.add(next); queue.push(next) }
      }
    }

    const result: Array<{ formId: string | null, formName: string | null, taskName: string | null }> = []
    const seenKeys = new Set<string>()
    for (const taskId of orderedPrevTaskIds) {
      const info = tasks.get(taskId)
      if (!info) continue
      // Prefer formId, then formName, finally taskName as fallback key
      const key = info.formId || info.formName || info.name || ''
      if (!key || seenKeys.has(key)) continue
      seenKeys.add(key)
      result.push({ formId: info.formId, formName: info.formName, taskName: info.name || null })
    }
    return result
  } catch (e) {
    console.error('Failed to parse BPMN for previous formIds:', e)
  }
  return []
}

/** Find the formId (sourceId) of the MI subtask's userTask from BPMN XML. */
const findMiSubTaskFormIdFromBpmn = (xml: string): string | null => {
  if (!xml) return null
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const allElements = doc.getElementsByTagName('*')

    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if (localName !== 'userTask') continue

      const children = el.getElementsByTagName('*')
      let isMultiInstance = false
      for (let j = 0; j < children.length; j++) {
        const childLocal = children[j].localName || children[j].nodeName.split(':').pop()
        if (childLocal === 'multiInstanceLoopCharacteristics') {
          isMultiInstance = true
          break
        }
      }
      // Fallback: developer-workstation uses "MI_" prefix convention for multi-instance tasks
      if (!isMultiInstance) {
        const taskId = el.getAttribute('id') || ''
        if (taskId.startsWith('MI_')) {
          isMultiInstance = true
        }
      }
      if (!isMultiInstance) continue

      for (let j = 0; j < children.length; j++) {
        const p = children[j]
        const ln = p.localName || p.nodeName.split(':').pop()
        if ((ln === 'property' || ln === 'values') && p.getAttribute('name') === 'formId') {
          return p.getAttribute('value')
        }
      }
    }
  } catch (e) {
    console.error('Failed to find MI subtask formId from BPMN:', e)
  }
  return null
}

// Parse BPMN XML
const parseBpmnXml = (xml: string) => {
  if (!xml) return
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const nodes: ProcessNode[] = []
    const flows: ProcessFlow[] = []
    const completed: string[] = []
    // Only enable snapshot view while process is RUNNING; show real completed state when ended (avoid orange Current Step)
    const snapshotActive = !!(snapshotTaskName && processInfo.value.status === 'RUNNING')

    // Parse position info
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
    
    // Create mapping from node name to history record status
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
    
    // Check for approval or rejection operations
    const hasApproval = historyRecords.value.some(h => h.status === 'completed' && (h.nodeName.includes('Approval') || h.nodeName.includes('Approval')))
    const hasRejection = historyRecords.value.some(h => h.status === 'rejected')
    
    // Get current node name
    const currentNodeName = processInfo.value.currentNode || ''
    let foundCurrentNode = false

    // Detect subProcess elements and determine which have been entered
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

    // Build a map of subProcess ID → Element (handle both prefixed and unprefixed selectors)
    const subProcessMap = new Map<string, Element>()
    const allElements = doc.getElementsByTagName('*')
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if (localName === 'subProcess') {
        const spId = el.getAttribute('id')
        if (spId) subProcessMap.set(spId, el)
      }
    }

    const enteredSubProcesses = new Set<string>()
    for (const [spId, sp] of subProcessMap) {
      const spName = sp.getAttribute('name') || ''
      if (spName && historyRecords.value.some(h => h.nodeName === spName)) {
        enteredSubProcesses.add(spId)
        continue
      }
      const childElements = sp.getElementsByTagName('*')
      for (let i = 0; i < childElements.length; i++) {
        const childLocal = childElements[i].localName || childElements[i].nodeName.split(':').pop()
        if (childLocal !== 'userTask' && childLocal !== 'serviceTask') continue
        const taskName = childElements[i].getAttribute('name') || ''
        const taskId = childElements[i].getAttribute('id') || ''
        if (taskName === currentNodeName || historyRecords.value.some(h => h.nodeName === taskName || h.nodeId === taskId)) {
          enteredSubProcesses.add(spId)
          break
        }
      }
    }

    // Parse start events (subprocess-internal starts are pending until the subprocess is entered)
    doc.querySelectorAll('startEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `start_${index}`
      const pos = positionMap.get(id)
      const parentSpId = getParentSubProcessId(event)
      let startStatus: 'completed' | 'pending' = 'completed'
      if (parentSpId && !enteredSubProcesses.has(parentSpId)) {
        startStatus = 'pending'
      }
      nodes.push({ id, name: event.getAttribute('name') || t('task.startNode'), type: 'start', status: startStatus, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      if (startStatus === 'completed') {
        completed.push(id)
      }
    })
    
    // Parse user tasks
    doc.querySelectorAll('userTask').forEach((task, index) => {
      const id = task.getAttribute('id') || `task_${index}`
      const name = task.getAttribute('name') || t('task.taskFallbackName', { index: index + 1 })
      const pos = positionMap.get(id)
      
      let status: 'completed' | 'current' | 'pending' | 'rejected' = 'pending'
      
      // Prefer status from history records
      const historyStatus = nodeStatusMap.get(name)
      if (snapshotActive) {
        // Snapshot mode: only show status up to snapshotTaskName
        if (name === snapshotTaskName || id === snapshotTaskName) {
          status = 'completed'
          completed.push(id)
          foundCurrentNode = true
        } else if (!foundCurrentNode) {
          // Nodes before snapshotTaskName: determine from history, or treat as completed
          if (historyStatus) {
            status = historyStatus
          } else {
            status = 'completed'
          }
          if (status === 'completed' || status === 'rejected') {
            completed.push(id)
          }
        } else {
          // Nodes after snapshotTaskName: keep as pending
          status = 'pending'
        }
      } else if (historyStatus) {
        status = historyStatus
        if (status === 'completed' || status === 'rejected') {
          completed.push(id)
        }
      } else if (processInfo.value.status === 'COMPLETED') {
        // Process completed: only mark nodes that were actually executed (matched via history records)
        const historyMatch = historyRecords.value.find(h => h.nodeName === name || h.nodeId === id)
        if (historyMatch) {
          status = historyMatch.status === 'rejected' ? 'rejected' : 'completed'
          completed.push(id)
        }
      } else if (processInfo.value.status === 'RUNNING') {
        // Process running: determine status based on current node name
        if (name === currentNodeName || id === currentNodeName) {
          status = 'current'
          currentNodeId.value = id
          foundCurrentNode = true
        } else if (!foundCurrentNode) {
          // Nodes before current: only mark as completed if found in history records
          // Avoid incorrectly marking gateway-skipped branch nodes as completed
          const historyMatch = historyRecords.value.find(h => h.nodeName === name || h.nodeId === id)
          if (historyMatch && (historyMatch.status === 'completed' || historyMatch.status === 'rejected')) {
            status = historyMatch.status
            completed.push(id)
          }
        }
      }
      
      nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })
    
    // Parse service tasks
    doc.querySelectorAll('serviceTask').forEach((task, index) => {
      const id = task.getAttribute('id') || `service_${index}`
      const name = task.getAttribute('name') || t('applicationDetail.serviceFallbackName', { index: index + 1 })
      const pos = positionMap.get(id)
      const historyStatus = nodeStatusMap.get(name)
      const status = historyStatus === 'completed' ? 'completed' : 'pending'
      nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      if (status === 'completed') completed.push(id)
    })
    
    // Parse subProcess elements
    let spIdx = 0
    for (const [spId, sp] of subProcessMap) {
      const name = sp.getAttribute('name') || ''
      const pos = positionMap.get(spId)

      let spStatus: 'completed' | 'current' | 'pending' = 'pending'
      if (enteredSubProcesses.has(spId)) {
        const childElements = sp.getElementsByTagName('*')
        let hasCurrentChild = false
        let allChildrenDone = true
        let userTaskCount = 0
        for (let i = 0; i < childElements.length; i++) {
          const childLocal = childElements[i].localName || childElements[i].nodeName.split(':').pop()
          if (childLocal !== 'userTask') continue
          userTaskCount++
          const taskName = childElements[i].getAttribute('name') || ''
          const taskId = childElements[i].getAttribute('id') || ''
          if (taskName === currentNodeName || taskId === currentNodeName) {
            hasCurrentChild = true
            break
          }
          const historyMatch = historyRecords.value.find(h => h.nodeName === taskName || h.nodeId === taskId)
          if (!historyMatch || (historyMatch.status !== 'completed' && historyMatch.status !== 'rejected')) {
            allChildrenDone = false
          }
        }
        if (!userTaskCount) allChildrenDone = false
        spStatus = hasCurrentChild ? 'current' : allChildrenDone ? 'completed' : 'current'
      }
      nodes.push({ id: spId, name, type: 'subprocess', status: spStatus, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      if (spStatus === 'completed') completed.push(spId)
      spIdx++
    }

    // Pre-parse sequence flows (used for subsequent gateway status determination)
    const earlyFlows: Array<{sourceRef: string, targetRef: string}> = []
    doc.querySelectorAll('sequenceFlow').forEach(flow => {
      earlyFlows.push({
        sourceRef: flow.getAttribute('sourceRef') || '',
        targetRef: flow.getAttribute('targetRef') || ''
      })
    })

    // Parse gateways
    doc.querySelectorAll('exclusiveGateway, parallelGateway, inclusiveGateway').forEach((gateway, index) => {
      const id = gateway.getAttribute('id') || `gateway_${index}`
      const name = gateway.getAttribute('name') || ''
      const pos = positionMap.get(id)
      
      // Determine gateway status from history records
      let status: 'completed' | 'pending' = 'pending'
      if (snapshotActive) {
        // Snapshot mode: check if the gateway incoming nodes are completed
        if (completedNodeNames.has(name)) {
          status = 'completed'
        } else {
          // Check for completed incoming nodes (via sequenceFlow)
          const incomingSourceIds = earlyFlows.filter(f => f.targetRef === id).map(f => f.sourceRef)
          const hasCompletedSource = incomingSourceIds.some(srcId => completed.includes(srcId))
          if (hasCompletedSource) {
            status = 'completed'
          }
        }
      } else if (completedNodeNames.has(name)) {
        status = 'completed'
      } else if (processInfo.value.status === 'COMPLETED') {
        // Process completed: only mark gateways on the actually executed path
        const incomingSourceIds = earlyFlows.filter(f => f.targetRef === id).map(f => f.sourceRef)
        const hasCompletedSource = incomingSourceIds.some(srcId => completed.includes(srcId))
        if (hasCompletedSource) {
          status = 'completed'
        }
      } else {
        // Check for completed incoming nodes (via sequenceFlow)
        const incomingSourceIds = earlyFlows.filter(f => f.targetRef === id).map(f => f.sourceRef)
        const hasCompletedSource = incomingSourceIds.some(srcId => completed.includes(srcId))
        if (hasCompletedSource) {
          status = 'completed'
        }
      }
      
      nodes.push({ id, name, type: 'gateway', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      if (status === 'completed') completed.push(id)
    })
    
    // Parse end events
    doc.querySelectorAll('endEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `end_${index}`
      const name = event.getAttribute('name') || t('task.endNode')
      const pos = positionMap.get(id)
      const parentSpId = getParentSubProcessId(event)
      
      // Check if end node should be marked as completed
      let status: 'completed' | 'pending' | 'rejected' = 'pending'

      // SubProcess-internal endEvents stay pending when the subProcess hasn't been entered
      if (parentSpId && !enteredSubProcesses.has(parentSpId)) {
        status = 'pending'
      } else if (completedNodeNames.has(name)) {
        // Match by exact node ID first to avoid cross-process name collision
        const idMatch = historyRecords.value.find(h => h.nodeId === id)
        if (idMatch) {
          status = isRejectedName(name) ? 'rejected' : 'completed'
        } else if (!parentSpId) {
          status = isRejectedName(name) ? 'rejected' : 'completed'
        }
      } else if (snapshotActive) {
        status = 'pending'
      } else if (processInfo.value.status === 'COMPLETED') {
        if (name === currentNodeName) {
          status = isRejectedName(name) ? 'rejected' : 'completed'
        }
      } else if (processInfo.value.status === 'REJECTED') {
        if (isRejectedName(name)) {
          status = 'rejected'
        }
      }
      nodes.push({ id, name, type: 'end', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      if (status === 'completed' || status === 'rejected') completed.push(id)
    })
    
    // Parse connector waypoints
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
    
    // Parse sequence flows
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

// Parse form configuration
const parseFormConfig = (configStr: string) => {
  if (!configStr) return
  try {
    const config = typeof configStr === 'string' ? JSON.parse(configStr) : configStr
    const rules = config.rule && Array.isArray(config.rule) ? config.rule : (Array.isArray(config) ? config : null)
    if (rules) {
      // Extract labelWidth config (ignore backend config, use fixed value to prevent label truncation)
      // if (config.options?.form?.labelWidth) {
      //   formLabelWidth.value = config.options.form.labelWidth
      // }
      
      // Check for el-tabs structure
      const tabsRule = rules.find((r: any) => r.type === 'el-tabs')
      
      if (tabsRule && tabsRule.children && Array.isArray(tabsRule.children)) {
        // Tab layout (consistent with processes/start.vue: pass entire tabPane.children to extractFieldsRecursive to avoid duplicate/mixed tabs)
        const tabs: FormTab[] = []
        
        for (const tabPane of tabsRule.children) {
          if (tabPane.type === 'el-tab-pane' && tabPane.props) {
            let tabName: string
            const rawName = tabPane.props.name
            if (rawName === undefined || rawName === null || rawName === '') {
              tabName = `tab_${tabs.length}`
            } else {
              tabName = String(rawName)
            }
            let uniqueName = tabName
            let dup = 0
            while (tabs.some(t => t.name === uniqueName)) {
              uniqueName = `${tabName}__${++dup}`
            }
            tabName = uniqueName

            const tabLabel = tabPane.props.label || `Tab ${tabs.length + 1}`
            const tabFields: FormField[] =
              tabPane.children && Array.isArray(tabPane.children)
                ? extractFieldsRecursive(tabPane.children)
                : []
            
            tabs.push({ name: tabName, label: tabLabel, fields: tabFields })
          }
        }
        
        formTabs.value = tabs
        formFields.value = []
      } else {
        // No tab layout, use flat mode
        formTabs.value = []
        formFields.value = extractFieldsRecursive(rules)
      }
    }
  } catch (error) {
    console.error('Failed to parse form config:', error)
  }
}

// form-create proprietary container: do not recurse into its subtree (consistent with start.vue)
const FC_SKIP_TYPES = new Set(['group', 'subForm', 'tableForm', 'tableFormColumn'])

// Recursively extract fields
const extractFieldsRecursive = (items: any[]): FormField[] => {
  const fields: FormField[] = []
  for (const item of items) {
    if (item.type === 'subTable' && item._bindingId != null) {
      fields.push({
        key: `__subTable_${item._bindingId}`,
        label: '',
        type: 'subTable',
        _bindingId: item._bindingId,
        span: 24,
      })
    } else if (item.type === 'lookup' && item.field) {
      let lookupCfg: any = {}
      try {
        const raw = item.props?.lookupConfig
        lookupCfg = typeof raw === 'string' ? JSON.parse(raw || '{}') : (raw || {})
      } catch { lookupCfg = {} }
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
        _lookupViewFields: resolvedViewFields
      }
      fields.push(field)
    } else if (FC_SKIP_TYPES.has(item.type)) {
      continue
    } else if (item.type === 'el-row' || item.type === 'el-col') {
      if (item.children && Array.isArray(item.children)) {
        fields.push(...extractFieldsRecursive(item.children))
      }
    } else if (item.field) {
      const field = convertFormCreateRule(item)
      if (field) fields.push(field)
    } else if (item.children && Array.isArray(item.children)) {
      fields.push(...extractFieldsRecursive(item.children))
    }
  }
  return fields
}

// Convert form rules
const convertFormCreateRule = (rule: any): FormField | null => {
  if (!rule || !rule.field) return null
  let dateType = 'date'
  if (rule.props?.type === 'datetime') dateType = 'datetime'
  else if (rule.props?.type === 'daterange') dateType = 'daterange'
  const typeMap: Record<string, string> = { 'input': 'text', 'inputNumber': 'number', 'select': 'select', 'radio': 'radio', 'checkbox': 'checkbox', 'switch': 'switch', 'datePicker': dateType, 'DatePicker': dateType, 'date-picker': dateType, 'el-date-picker': dateType, 'timePicker': 'time', 'cascader': 'cascader', 'rate': 'rate', 'slider': 'slider', 'colorPicker': 'colorPicker', 'treeSelect': 'treeselect', 'upload': 'upload', 'editor': 'editor', 'signature': 'signature', 'transfer': 'transfer' }
  const field: FormField = { key: rule.field, label: rule.title || rule.field, type: typeMap[rule.type] || 'text', required: rule.validate?.some((v: any) => v.required) || false, placeholder: rule.props?.placeholder || '', span: rule.col?.span || 24 }
  const rawOptions = rule.options || rule.props?.options
  if (rawOptions) {
    if (rule.type === 'cascader') {
      field.options = rawOptions
    } else {
      field.options = rawOptions.map((opt: any) => ({ label: opt.label || opt.value, value: opt.value }))
    }
  }
  if (rule.type === 'cascader') { field.cascaderProps = rule.props?.props || rule.props?.cascaderProps }
  if (rule.type === 'input' && rule.props?.type === 'textarea') { field.type = 'textarea'; field.rows = rule.props?.rows || 3 }
  if (rule.type === 'input' && rule.props?.type === 'password') { field.type = 'password' }
  if (rule.type === 'timePicker' && rule.props?.isRange === true) { field.type = 'timerange' }
  if (rule.type === 'rate') { field.max = rule.props?.max || 5 }
  if (rule.type === 'slider') { field.min = rule.props?.min ?? 0; field.max = rule.props?.max ?? 100; field.step = rule.props?.step || 1 }
  if (rule.type === 'upload') {
    const action = rule.props?.action
    field.uploadUrl = (action && action !== '/') ? action : '/api/v1/upload'
    field.uploadAccept = rule.props?.accept || '.jpg,.jpeg,.png,.pdf,.docx,.xlsx'
    field.uploadLimit = rule.props?.limit || 1
  }
  return field
}

// Derive display columns for a sub-table binding based on designed fields
const deriveColumnsFromBinding = (binding: any, subForms?: Record<string, any>): Array<{ field: string; label: string; type?: string; required?: boolean; options?: Array<{ label: string; value: any }>; props?: Record<string, any> }> => {
  const subFormRule = subForms?.[binding.bindingId]?.rule
  if (subFormRule && Array.isArray(subFormRule) && subFormRule.length > 0) {
    return subFormRule.map((r: any) => {
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
      } else if (r.type === 'treeSelect') {
        type = 'treeselect'
      } else if (r.type === 'elTreeSelect') {
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
      } else {
        type = r.type as any
      }

      // Collect options from rule.options or rule.props.options
      const rawOptions = r.options || rProps.options
      const options = rawOptions
        ? (type === 'cascader' ? rawOptions : rawOptions.map((o: any) => ({ label: o.label ?? o.value, value: o.value })))
        : undefined

      // Pass through relevant props
      const passProps: Record<string, any> = {}
      const propKeys = [
        'action', 'accept', 'multiple', 'precision', 'min', 'max', 'rows', 'maxlength', 'fileNameTargetField',
        'isRange', 'valueFormat', 'startPlaceholder', 'endPlaceholder', 'treeData', 'checkStrictly',
        'showAlpha', 'allowHalf', 'step', 'cascaderProps', 'leftTitle', 'rightTitle',
      ]
      for (const key of propKeys) {
        if (rProps[key] !== undefined) passProps[key] = rProps[key]
      }
      if (rProps.data !== undefined) passProps.treeData = rProps.data
      if (rProps.nodeKey !== undefined) passProps.nodeKey = rProps.nodeKey
      if (rProps.showCheckbox !== undefined) passProps.showCheckbox = rProps.showCheckbox
      if (rProps.props !== undefined) passProps.labelProps = rProps.props
      // cascader: map props.props to cascaderProps if not already set
      if (type === 'cascader' && rProps.props && !passProps.cascaderProps) passProps.cascaderProps = rProps.props

      // Sync options into props.options so SubTableAddDialog can read from col.props?.options
      if (options) passProps.options = options

      const required = r.validate?.some((v: any) => v.required) || false

      return {
        field: r.field,
        label: r.title || r.field,
        type,
        required,
        ...(options ? { options } : {}),
        ...(Object.keys(passProps).length > 0 ? { props: passProps } : {}),
      }
    })
  }
  return []
}

// Load flow history
const loadProcessHistory = async () => {
  try {
    const response = await processApi.getProcessHistory(processId)
    const historyData = response.data || response
    if (historyData && Array.isArray(historyData)) {

      // Running + snapshot: only keep records up to this task; completed processes show full history
      let filteredData = historyData
      if (snapshotTaskName && processInfo.value.status === 'RUNNING') {
        // Find the last occurrence of snapshotTaskName in the history list (sorted by time) and truncate there
        const snapshotIdx = historyData.map((item: any) => item.activityName || item.taskName).lastIndexOf(snapshotTaskName)
        if (snapshotIdx >= 0) {
          filteredData = historyData.slice(0, snapshotIdx + 1)
        } else if (snapshotTime) {
          // activityName match failed (might be a BPMN element ID), fall back to time-based truncation
          const cutoff = new Date(snapshotTime).getTime()
          const timeIdx = historyData.map((item: any) => new Date(item.operationTime || 0).getTime()).lastIndexOf(cutoff)
          if (timeIdx >= 0) {
            filteredData = historyData.slice(0, timeIdx + 1)
          } else {
            // Keep all records with operationTime <= snapshotTime
            filteredData = historyData.filter((item: any) => {
              const t = new Date(item.operationTime || 0).getTime()
              return t <= cutoff
            })
          }
        }
      }

      // Convert to HistoryRecord format (keep gateway records for diagram status determination)
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
    } else {
      initHistoryRecords()
    }
  } catch (error: any) {
    console.error('Failed to load process history:', error)
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

// Initialize flow history records
const initHistoryRecords = () => {
  const records: HistoryRecord[] = [{ id: 'submit', nodeId: 'start', nodeName: t('applicationDetail.submitApplication'), status: 'completed', assigneeName: processInfo.value.startUserName || processInfo.value.startUserId, createdTime: processInfo.value.startTime || '' }]
  if (processInfo.value.status === 'RUNNING') records.push({ id: 'current', nodeId: 'task', nodeName: processInfo.value.currentNode || t('applicationDetail.pendingApproval'), status: 'current', assigneeName: processInfo.value.currentAssignee || t('applicationDetail.unassigned'), createdTime: '' })
  else if (processInfo.value.status === 'COMPLETED') records.push({ id: 'end', nodeId: 'end', nodeName: t('applicationDetail.processEnded'), status: 'completed', createdTime: processInfo.value.endTime || '' })
  else if (processInfo.value.status === 'WITHDRAWN') records.push({ id: 'withdrawn', nodeId: 'withdrawn', nodeName: t('applicationDetail.processWithdrawn'), status: 'rejected', assigneeName: processInfo.value.startUserName || processInfo.value.startUserId, createdTime: processInfo.value.endTime || '' })
  historyRecords.value = records
}

// Urge
const handleUrge = async () => {
  urging.value = true
  try { await processApi.urgeProcess(processId); ElMessage.success(t('applicationDetail.urgeSuccess')) }
  catch { ElMessage.error(t('applicationDetail.urgeFailed')) }
  finally { urging.value = false }
}

// Withdraw
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

const hasAssignmentData = (rows: any[]): boolean => {
  if (!Array.isArray(rows) || rows.length === 0) return false
  return rows.some(r => r && (r.assignee_display_name || r.assignee_user_id))
}

onMounted(() => { loadProcessDetail() })
</script>

<style lang="scss" scoped>
.application-detail-page {
  width: 100%;
  max-width: 100%;
  margin: 0;
  box-sizing: border-box;
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
  .change-history-section .section-content { padding: 20px; }
  .history-section .section-content { min-height: 100px; }
  .action-section { position: sticky; bottom: 0; z-index: 10;
    .action-buttons { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; .left-actions, .right-actions { display: flex; gap: 12px; } }
  }
}
</style>
