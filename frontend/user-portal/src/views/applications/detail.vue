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

      <!-- Section 3a: Previous node forms shown ABOVE the current form (non-initiator views).
           Initiator's own My Request keeps current form first; sibling forms render in Section 3b. -->
      <template v-for="prevForm in previousFormsAbove" :key="prevForm.formId">
        <!-- MI subtask form: no card rendered, only sub-tables (participants etc.) -->
        <template v-if="prevForm.isMiSubTask">
          <div class="section form-section">
            <div class="section-header">
              <el-icon><Document /></el-icon>
              <span>{{ prevForm.formName }}</span>
              <el-tag v-if="prevForm.isActiveMiSubTaskStep" type="warning" size="small">{{ t('applicationDetail.currentStep') }}</el-tag>
              <el-tag v-else type="info" size="small">{{ t('applicationDetail.completed') }}</el-tag>
            </div>
            <div class="section-content">
              <div v-if="prevForm.fields.length > 0 || prevForm.tabs.length > 0" class="form-container">
                <FormRenderer
                  :fields="prevForm.fields"
                  :tabs="prevForm.tabs"
                  v-model="formData"
                  :label-width="prevForm.labelWidth"
                  :readonly="true"
                  :subTableBindings="prevForm.subTableBindings"
                  :linked-sub-table-bindings="linkableSubTableBindingsForPrevious(prevForm)"
                  @update:subTableData="(id: number, rows: any[]) => { const b = prevForm.subTableBindings.find(x => x.bindingId === id); if (b) b.data = rows }"
                />
              </div>
              <template v-if="unplacedSubTableBindings(prevForm).length > 0">
                <div
                  v-for="binding in unplacedSubTableBindings(prevForm)"
                  :key="binding.bindingId"
                  class="sub-table-section"
                >
                  <SubTableField
                    :title="binding.tableName"
                    :columns="binding.columns"
                    v-model="binding.data"
                    :editable="false"
                    :assignee-field="hasAssignmentData(binding.data) ? 'assignee_user_id' : undefined"
                    :show-task-status="false"
                    :show-view-detail="hasSubTaskFormSchema && hasTaskStatusData(binding.data)"
                    :linked-sub-table-bindings="linkableSubTableBindingsForPrevious(prevForm)"
                    @viewDetail="(row: any) => openSubTaskDetailDialog(row)"
                  />
                </div>
              </template>
            </div>
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
                :subTableBindings="prevForm.subTableBindings"
                :linked-sub-table-bindings="linkableSubTableBindingsForPrevious(prevForm)"
                @update:subTableData="(id: number, rows: any[]) => { const b = prevForm.subTableBindings.find(x => x.bindingId === id); if (b) b.data = rows }"
              />
            </div>
            <template v-if="unplacedSubTableBindings(prevForm).length > 0">
              <div v-for="binding in unplacedSubTableBindings(prevForm)" :key="binding.bindingId" class="sub-table-section">
                <SubTableField
                  :title="binding.tableName"
                  :columns="binding.columns"
                  v-model="binding.data"
                  :editable="false"
                  :assignee-field="hasAssignmentData(binding.data) ? 'assignee_user_id' : undefined"
                  :show-task-status="false"
                  :show-view-detail="hasSubTaskFormSchema && hasTaskStatusData(binding.data)"
                  :linked-sub-table-bindings="linkableSubTableBindingsForPrevious(prevForm)"
                  @viewDetail="(row: any) => openSubTaskDetailDialog(row)"
                />
              </div>
            </template>
          </div>
        </div>
      </template>

      <!-- Form data (Completed Tasks renders the same form as To Do, but readonly) -->
      <div v-if="showCurrentFormSection" class="section form-section">
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
              :linked-sub-table-bindings="linkableSubTableBindings"
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
                :show-task-status="false"
                :show-view-detail="hasSubTaskFormSchema && hasTaskStatusData(binding.data)"
                :linked-sub-table-bindings="linkableSubTableBindings"
                @viewDetail="(row: any) => openSubTaskDetailDialog(row)"
              />
            </div>
          </template>
        </div>
      </div>

      <!-- Section 3b: Previous node forms shown BELOW the current form (initiator's own My Request).
           Order follows BPMN BFS; only runtime-previous steps (+ MI forms with matching completed history). -->
      <template v-for="prevForm in previousFormsBelow" :key="`below-${prevForm.formId}`">
        <template v-if="prevForm.isMiSubTask">
          <div class="section form-section">
            <div class="section-header">
              <el-icon><Document /></el-icon>
              <span>{{ prevForm.formName }}</span>
              <el-tag v-if="prevForm.isActiveMiSubTaskStep" type="warning" size="small">{{ t('applicationDetail.currentStep') }}</el-tag>
              <el-tag v-else type="info" size="small">{{ t('applicationDetail.completed') }}</el-tag>
            </div>
            <div class="section-content">
              <div v-if="prevForm.fields.length > 0 || prevForm.tabs.length > 0" class="form-container">
                <FormRenderer
                  :fields="prevForm.fields"
                  :tabs="prevForm.tabs"
                  v-model="formData"
                  :label-width="prevForm.labelWidth"
                  :readonly="true"
                  :subTableBindings="prevForm.subTableBindings"
                  :linked-sub-table-bindings="linkableSubTableBindingsForPrevious(prevForm)"
                  @update:subTableData="(id: number, rows: any[]) => { const b = prevForm.subTableBindings.find(x => x.bindingId === id); if (b) b.data = rows }"
                />
              </div>
              <template v-if="unplacedSubTableBindings(prevForm).length > 0">
                <div
                  v-for="binding in unplacedSubTableBindings(prevForm)"
                  :key="binding.bindingId"
                  class="sub-table-section"
                >
                  <SubTableField
                    :title="binding.tableName"
                    :columns="binding.columns"
                    v-model="binding.data"
                    :editable="false"
                    :assignee-field="hasAssignmentData(binding.data) ? 'assignee_user_id' : undefined"
                    :show-task-status="false"
                    :show-view-detail="hasSubTaskFormSchema && hasTaskStatusData(binding.data)"
                    :linked-sub-table-bindings="linkableSubTableBindingsForPrevious(prevForm)"
                    @viewDetail="(row: any) => openSubTaskDetailDialog(row)"
                  />
                </div>
              </template>
            </div>
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
                :subTableBindings="prevForm.subTableBindings"
                :linked-sub-table-bindings="linkableSubTableBindingsForPrevious(prevForm)"
                @update:subTableData="(id: number, rows: any[]) => { const b = prevForm.subTableBindings.find(x => x.bindingId === id); if (b) b.data = rows }"
              />
            </div>
            <template v-if="unplacedSubTableBindings(prevForm).length > 0">
              <div v-for="binding in unplacedSubTableBindings(prevForm)" :key="binding.bindingId" class="sub-table-section">
                <SubTableField
                  :title="binding.tableName"
                  :columns="binding.columns"
                  v-model="binding.data"
                  :editable="false"
                  :assignee-field="hasAssignmentData(binding.data) ? 'assignee_user_id' : undefined"
                  :show-task-status="false"
                  :show-view-detail="hasSubTaskFormSchema && hasTaskStatusData(binding.data)"
                  :linked-sub-table-bindings="linkableSubTableBindingsForPrevious(prevForm)"
                  @viewDetail="(row: any) => openSubTaskDetailDialog(row)"
                />
              </div>
            </template>
          </div>
        </div>
      </template>

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
const snapshotTaskId = route.query.snapshotTaskId as string | undefined
const snapshotTaskDefinitionKey = route.query.snapshotTaskDefinitionKey as string | undefined

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
  tableId?: number | null
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
  return collectPlacedBindingIds(formFields.value, formTabs.value)
})

const bottomSubTableBindings = computed(() =>
  subTableBindings.value.filter(b => !placedBindingIds.value.has(b.bindingId))
)

function collectPlacedBindingIds(fields: any[], tabs: Array<{ fields: any[] }> = []): Set<number> {
  const ids = new Set<number>()
  const collect = (items: any[]) => items.forEach((f: any) => {
    if (f.type === 'subTable' && f._bindingId != null) ids.add(f._bindingId)
    if (Array.isArray(f.children)) collect(f.children)
  })
  collect(fields)
  tabs.forEach(tab => collect(tab.fields))
  return ids
}

// Walk the form rule (raw config tree) and collect every `_bindingId` referenced as a `subTable` node.
// Used to decide whether an "unplaced" binding (no rule placement) should still be rendered.
function collectRuleBindingIds(rules: any[]): Set<number> {
  const ids = new Set<number>()
  const walk = (items: any[]) => {
    if (!Array.isArray(items)) return
    for (const r of items) {
      if (!r) continue
      if (r.type === 'subTable') {
        const id = r._bindingId ?? r.props?._bindingId
        if (id != null) ids.add(Number(id))
      }
      if (Array.isArray(r.children)) walk(r.children)
    }
  }
  walk(rules)
  return ids
}

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
  /** Initiator My Request: MI sub-task userTask is the runtime current step (not in "previous" slice). */
  isActiveMiSubTaskStep?: boolean
  subTableBindings: Array<{
    bindingId: number
    tableId?: number | null
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
// Initiator viewing own My Request: flip the section order so current form (form y) renders first,
// followed by sibling forms (subform → subform_copy) in BPMN BFS order.
const initiatorOwnView = ref(false)
const previousFormsAbove = computed<PreviousFormEntry[]>(() => initiatorOwnView.value ? [] : previousForms.value)
const previousFormsBelow = computed<PreviousFormEntry[]>(() => initiatorOwnView.value ? previousForms.value : [])

function unplacedSubTableBindings(prevForm: PreviousFormEntry): PreviousFormEntry['subTableBindings'] {
  const placedIds = collectPlacedBindingIds(prevForm.fields, prevForm.tabs)
  return prevForm.subTableBindings.filter(b => !placedIds.has(b.bindingId))
}

/** Align with tasks/detail.vue: variables may key __subTables__ by table name or binding id. */
function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

function getSavedSubTableRowsFromVariables(
  savedSubTables: Record<string, any> | null | undefined,
  rawBinding: { bindingId: number; tableName?: string; tableDisplayName?: string }
): any[] | undefined {
  if (!savedSubTables || typeof savedSubTables !== 'object') return undefined
  const keys = [
    rawBinding.bindingId,
    String(rawBinding.bindingId),
    rawBinding.tableDisplayName,
    rawBinding.tableName,
    rawBinding.tableName ? normalizeSubTableName(rawBinding.tableName) : '',
    rawBinding.tableDisplayName ? normalizeSubTableName(rawBinding.tableDisplayName) : ''
  ]
  for (const key of keys) {
    if (key === '' || key == null) continue
    const v = savedSubTables[key as string]
    if (Array.isArray(v)) return v
  }
  return undefined
}

/** Merge rows by id/rowId so a sparse MI binding can be united with the full list under another bindingId (copied form). */
function mergeSubTableRowsByRowId(existing: any[] | undefined, incoming: any[]): any[] {
  const byId = new Map<string, any>()
  const add = (r: any) => {
    if (!r || typeof r !== 'object') return
    const rawId = (r as Record<string, unknown>).id ?? (r as Record<string, unknown>).rowId
    if (rawId == null || String(rawId).trim() === '') return
    const k = String(rawId)
    const cur = byId.get(k)
    byId.set(k, cur ? { ...cur, ...r } : { ...r })
  }
  for (const r of existing || []) add(r)
  for (const r of incoming || []) add(r)
  return Array.from(byId.values())
}

type SubTableBindingAlignable = { tableId?: number | null; tableName: string; data: any[] }

/**
 * Copied forms (e.g. subform_copy) get a new bindingId while runtime data still lives under the original key;
 * MI may only persist one row under the new id — merge all bindings that share tableId (or display name) for My Request.
 */
function alignProcessSubTableBindingsBySharedTable() {
  const all: SubTableBindingAlignable[] = [
    ...(subTableBindings.value as SubTableBindingAlignable[]),
    ...previousForms.value.flatMap(f => f.subTableBindings as SubTableBindingAlignable[])
  ]
  const groups = new Map<string, SubTableBindingAlignable[]>()
  for (const b of all) {
    const key =
      b.tableId != null && !Number.isNaN(Number(b.tableId))
        ? `tid:${Number(b.tableId)}`
        : `tn:${normalizeSubTableName(b.tableName)}`
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(b)
  }
  for (const group of groups.values()) {
    if (group.length < 2) continue
    let merged: any[] = []
    for (const b of group) {
      merged = mergeSubTableRowsByRowId(merged, Array.isArray(b.data) ? b.data : [])
    }
    if (merged.length === 0) continue
    const snapshot = merged.map(r => ({ ...r }))
    for (const b of group) {
      b.data = snapshot
    }
  }
}

// Link-form columns need access to other bindings as fallback data sources.
// Keep the contract aligned with `tasks/detail.vue` (linkableSubTableBindings).
const linkableSubTableBindings = computed<any[]>(() => [
  ...(subTableBindings.value as any[]),
  ...previousForms.value.flatMap(form => (form.subTableBindings as any[]))
])

/** Same as tasks/detail.vue: link-form `.find()` must resolve prev-form bindings before current (empty MI slice). */
function linkableSubTableBindingsForPrevious(prevForm: PreviousFormEntry) {
  const pid = prevForm.formId
  const otherPrev = previousForms.value
    .filter(p => p.formId !== pid)
    .flatMap(p => p.subTableBindings as any[])
  return [
    ...(prevForm.subTableBindings as any[]),
    ...(subTableBindings.value as any[]),
    ...otherPrev
  ]
}

// Sub-task form detail dialog
const subTaskDetailVisible = ref(false)
const subTaskDetailTitle = ref('')
const subTaskDetailFields = ref<FormField[]>([])
const subTaskDetailData = ref<Record<string, any>>({})
const subTaskFormSchema = ref<any>(null)
const subTaskFormId = ref<string | null>(null)
const currentFormIsMiSubTask = ref(false)
const showCurrentFormSection = computed(() => !currentFormIsMiSubTask.value || !!snapshotTaskName)

const hasSubTaskFormSchema = computed(() => !!subTaskFormSchema.value)

const getMiRows = (): any[] => [
  ...subTableBindings.value.flatMap(binding => binding.data || []),
  ...previousForms.value.flatMap(form => form.subTableBindings.flatMap(binding => binding.data || []))
]

const hasIncompleteMiRows = (): boolean => {
  const rows = getMiRows().filter((row: any) => row && row.task_status !== undefined)
  return rows.length > 0 && rows.some((row: any) => String(row.task_status || '').toUpperCase() !== 'COMPLETED')
}

const hasCompletedMiRows = (): boolean => {
  const rows = getMiRows().filter((row: any) => row && row.task_status !== undefined)
  return rows.length > 0 && rows.every((row: any) => String(row.task_status || '').toUpperCase() === 'COMPLETED')
}

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

function getCurrentFormFieldKeys(): string[] {
  const keys = new Set<string>()
  formFields.value.forEach((field: any) => {
    if (field?.key) keys.add(String(field.key))
  })
  formTabs.value.forEach((tab: any) => {
    ;(tab?.fields || []).forEach((field: any) => {
      if (field?.key) keys.add(String(field.key))
    })
  })
  return Array.from(keys)
}

function hydrateCurrentFormDataFromCompletedSubTaskRows() {
  const formKeys = getCurrentFormFieldKeys()
  if (formKeys.length === 0) return

  const rows = [
    ...subTableBindings.value.flatMap(binding => binding.data || []),
    ...previousForms.value.flatMap(form => form.subTableBindings.flatMap(binding => binding.data || []))
  ]
  const viewerId = getPortalUserId()
  const completedRows = rows.filter((row: any) => row?.task_status === 'COMPLETED')
  const viewerRows = viewerId
    ? completedRows.filter((row: any) => row?.assignee_user_id === viewerId)
    : []
  const row = (viewerRows.length > 0 ? viewerRows : completedRows)[0]
  if (!row) return

  const nextData = { ...formData.value }
  for (const key of formKeys) {
    if (Object.prototype.hasOwnProperty.call(row, key)) {
      nextData[key] = row[key]
    }
  }
  formData.value = nextData
}

// Flow history records
const historyRecords = ref<HistoryRecord[]>([])
const snapshotActivityId = ref<string | null>(snapshotTaskDefinitionKey || null)

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

const getSubProcessUserTaskIds = (subProcessId: string): string[] => {
  if (!bpmnXml.value || !subProcessId) return []
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(bpmnXml.value, 'text/xml')
    const allElements = doc.getElementsByTagName('*')
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if (localName !== 'subProcess' || el.getAttribute('id') !== subProcessId) continue
      const childIds: string[] = []
      const children = el.getElementsByTagName('*')
      for (let j = 0; j < children.length; j++) {
        const childLocalName = children[j].localName || children[j].nodeName.split(':').pop()
        if (childLocalName === 'userTask') {
          const childId = children[j].getAttribute('id')
          if (childId) childIds.push(childId)
        }
      }
      return childIds
    }
  } catch (error) {
    console.warn('Failed to resolve subprocess user tasks:', error)
  }
  return []
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
      // filter sub-table rows to only show their COMPLETED row(s).
      // This handles the case where the same person is assigned multiple sub-tasks:
      // only the row whose task was actually completed is shown.
      if (snapshotTaskName) {
        const viewerId = getPortalUserId()
        if (viewerId) {
          const filterByAssignee = (bindings: typeof subTableBindings.value) => {
            for (const binding of bindings) {
              if (binding.data && binding.data.length > 0 && hasAssignmentData(binding.data)) {
                const filtered = binding.data.filter(
                  (row: any) => row.assignee_user_id === viewerId && row.task_status === 'COMPLETED'
                )
                if (filtered.length > 0) {
                  binding.data = filtered
                } else {
                  const byAssignee = binding.data.filter(
                    (row: any) => row.assignee_user_id === viewerId
                  )
                  if (byAssignee.length > 0) {
                    binding.data = byAssignee
                  }
                }
              }
            }
          }
          filterByAssignee(subTableBindings.value)
          for (const prevForm of previousForms.value) {
            filterByAssignee(prevForm.subTableBindings)
          }
        }
        hydrateCurrentFormDataFromCompletedSubTaskRows()
      }

      // Completed Tasks is a readonly snapshot of the completed task itself; do not advance the diagram to the next active node.
      if (snapshotTaskName) {
        currentNodeId.value = ''
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
      initiatorOwnView.value = useInitiatorFormOnly

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
      let selectedFormConfig: Record<string, any> = {}
      try {
        const cfg = typeof selectedForm.data === 'string' ? JSON.parse(selectedForm.data) : (selectedForm.data || {})
        selectedFormConfig = cfg
        relationViewConfigs.value = cfg.relationViews || {}
      } catch {
        selectedFormConfig = {}
        relationViewConfigs.value = {}
      }

      parseFormConfig(selectedForm.data)

      // Parse subForms from configJson
      // Load sub-table bindings (SUB and RELATED, not PRIMARY).
      // Designer marks FORM_ONLY bindings (e.g. `subtable2` in kk) — they're meant to be embedded
      // inline (form rule subTable node) or accessed via a LinkForm column on a sibling table.
      // If neither happens (binding not placed in rule), drop it instead of rendering as fallback.
      const bindings: typeof subTableBindings.value = []
      const tableBindings: any[] = selectedForm.tableBindings || []
      const selectedFormRuleIds = collectRuleBindingIds(
        Array.isArray(selectedFormConfig?.rule) ? selectedFormConfig!.rule : []
      )
      for (const b of tableBindings) {
        if (b.bindingType === 'PRIMARY') continue
        const columns = deriveColumnsFromBinding(b, selectedFormConfig)
        if (!Array.isArray(columns) || columns.length === 0) continue
        const placed = selectedFormRuleIds.has(Number(b.bindingId))
        const isFormOnly = String(b.subMode || '').toUpperCase() === 'FORM_ONLY'
        if (!placed && isFormOnly) continue
        bindings.push({
          bindingId: b.bindingId,
          tableId: b.tableId != null ? Number(b.tableId) : null,
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
      const savedSubTables = formData.value.__subTables__
      if (savedSubTables && typeof savedSubTables === 'object') {
        for (const binding of bindings) {
          const raw = tableBindings.find((x: any) => Number(x.bindingId) === Number(binding.bindingId))
          if (!raw) continue
          const saved = getSavedSubTableRowsFromVariables(savedSubTables, {
            bindingId: raw.bindingId,
            tableName: raw.tableName,
            tableDisplayName: raw.tableDisplayName
          })
          if (saved) binding.data = saved
        }
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

      // Collect additional node forms (read-only display).
      // Initiator My Request: preserve global BFS order but only forms before currentNode in BPMN; for
      // multi-instance sub-task forms (e.g. subform_copy), also require a completed flow-history row
      // so a wrong currentNode fallback cannot surface future steps early.
      // Non-initiator: list previous forms from BPMN vs current node only.
      if (content.processes?.length > 0) {
        const xml = content.processes[0].data
        let initiatorSliceIndex: number | null = null
        const normHistNameInit = (s: string | null | undefined) =>
          (s || '').trim().replace(/\s+/g, ' ')
        const prevFormIds = useInitiatorFormOnly
          ? (() => {
              const allOrdered = parseBpmnXmlAndGetAllFormIds(xml)
              const curRaw = snapshotTaskName || processInfo.value.currentNode || ''
              const curN = normHistNameInit(curRaw)
              // Prefer deep BFS index so subprocess userTasks (e.g. subform_copy) are not lost:
              // shallow parseBpmnXmlAndGetPreviousFormIds never enters MI subprocess, so completedKeys
              // used to filter them all out.
              if (curRaw && String(curRaw).trim()) {
                const idx = findInitiatorCurrentStepIndexInAllOrdered(xml, curRaw, allOrdered)
                if (idx != null && idx >= 0) {
                  initiatorSliceIndex = idx
                  return allOrdered.slice(0, idx)
                }
              }
              const completedKeys = new Set(
                parseBpmnXmlAndGetPreviousFormIds(xml)
                  .map(i => i.formId || i.formName || i.taskName || '')
                  .filter(k => k.length > 0)
              )
              let ordered = allOrdered.filter(i =>
                completedKeys.has(i.formId || i.formName || i.taskName || '')
              )
              // Shallow-BPMN fallback only: guard MI forms with flow history so a wrong currentNode
              // cannot surface future steps early.
              const reachedHistoryNames = new Set(
                historyRecords.value
                  .filter(h => h.status === 'completed' || h.status === 'current')
                  .map(h => normHistNameInit(h.nodeName))
                  .filter(n => n.length > 0)
              )
              if (reachedHistoryNames.size > 0) {
                ordered = ordered.filter((info) => {
                  const prevFormGuess = content.forms.find((f: any) =>
                    (info.formId && String(f.sourceId) === info.formId) ||
                    (info.formName && f.name === info.formName) ||
                    (info.taskName && f.name === info.taskName)
                  )
                  const isMiTaskForm =
                    !!prevFormGuess &&
                    !!(
                      (subTaskFormId.value && String(prevFormGuess.id) === subTaskFormId.value) ||
                      (subTaskFormSchema.value && prevFormGuess.name === subTaskFormSchema.value._formName)
                    )
                  if (!isMiTaskForm) return true
                  const t = normHistNameInit(info.taskName)
                  if (!t.length) return true
                  return reachedHistoryNames.has(t)
                })
              }
              return ordered
            })()
          : parseBpmnXmlAndGetPreviousFormIds(xml)
        const collectedPrevForms: PreviousFormEntry[] = []

        for (const info of prevFormIds) {
          let prevForm: any = null
          let skipReason: string | null = null
          if (info.formId) {
            if (info.formId === String(selectedForm.sourceId)) { skipReason = 'sourceIdEqSelected'; }
            else prevForm = content.forms.find((f: any) => String(f.sourceId) === info.formId)
          }
          if (!skipReason && !prevForm && info.formName) {
            if (info.formName === selectedForm.name) { skipReason = 'formNameEqSelected'; }
            else prevForm = content.forms.find((f: any) => f.name === info.formName)
          }
          if (!skipReason && !prevForm && (info as any).taskName) {
            if ((info as any).taskName === selectedForm.name) { skipReason = 'taskNameEqSelected'; }
            else prevForm = content.forms.find((f: any) => f.name === (info as any).taskName)
          }
          if (!skipReason && (!prevForm || prevForm.id === selectedForm.id)) skipReason = !prevForm ? 'noFormMatch' : 'idEqSelected'
          if (!skipReason && collectedPrevForms.some(e => e.formId === String(prevForm.id))) skipReason = 'duplicate'
          // Skip the subtask form — its content is shown via the Detail button
          // in the participants sub-table. But never skip a form that has sub-table
          // bindings (it carries the participants table needed for display).
          const isKnownMiSubTaskForm = !skipReason && !!(
            (subTaskFormId.value && String(prevForm.id) === subTaskFormId.value) ||
            (subTaskFormSchema.value && prevForm.name === subTaskFormSchema.value._formName)
          )
          if (!skipReason && isKnownMiSubTaskForm) {
            const bindings = prevForm.tableBindings || []
            if (!bindings.some((b: any) => b.bindingType !== 'PRIMARY')) {
              skipReason = 'miSubTaskNoNonPrimaryBindings'
            }
          }
          if (skipReason) continue

          collectedPrevForms.push(
            buildPreviousFormEntry(prevForm, { isKnownMiSubTask: !!isKnownMiSubTaskForm })
          )
        }

        if (
          useInitiatorFormOnly &&
          initiatorSliceIndex != null &&
          processInfo.value.status === 'RUNNING' &&
          (subTaskFormId.value || subTaskFormSchema.value)
        ) {
          const orderedFull = parseBpmnXmlAndGetAllFormIds(xml)
          const atCur = orderedFull[initiatorSliceIndex]
          if (atCur) {
            let curForm = content.forms.find(
              (f: any) =>
                (atCur.formId && String(f.sourceId) === atCur.formId) ||
                (atCur.formName && f.name === atCur.formName) ||
                (atCur.taskName && f.name === atCur.taskName)
            )
            if (!curForm && atCur.formId && content.processes?.[0]?.data) {
              const miSid = findMiSubTaskFormIdFromBpmn(content.processes[0].data)
              if (miSid && String(atCur.formId) === String(miSid)) {
                if (subTaskFormId.value) {
                  curForm = content.forms.find((f: any) => String(f.id) === subTaskFormId.value)
                }
                if (!curForm && subTaskFormSchema.value?._formName) {
                  curForm = content.forms.find((f: any) => f.name === subTaskFormSchema.value._formName)
                }
              }
            }
            const matchesMiForm =
              !!curForm &&
              !!(
                (subTaskFormId.value && String(curForm.id) === subTaskFormId.value) ||
                (subTaskFormSchema.value && curForm.name === subTaskFormSchema.value._formName)
              )
            if (matchesMiForm && !collectedPrevForms.some(e => e.formId === String(curForm.id))) {
              collectedPrevForms.push(
                buildPreviousFormEntry(curForm, { isKnownMiSubTask: true, isActiveMiSubTaskStep: true })
              )
            }
          }
        }

        previousForms.value = collectedPrevForms
      } else {
        previousForms.value = []
      }
      alignProcessSubTableBindingsBySharedTable()
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
    const currentNodeName = snapshotActivityId.value || snapshotTaskDefinitionKey || snapshotTaskName || processInfo.value.currentNode || ''
    
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
        // Direct match on current node (whitespace-normalized for robustness)
        const normName = currentNodeName.trim().replace(/\s+/g, ' ')
        const normBpmnName = name.trim().replace(/\s+/g, ' ')
        if (normBpmnName === normName || id === currentNodeName) {
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
    const normNodeName = currentNodeName.trim().replace(/\s+/g, ' ')
    for (const [id, info] of tasks) {
      const normInfoName = info.name.trim().replace(/\s+/g, ' ')
      if (normInfoName === normNodeName || id === currentNodeName) { currentId = id; break }
    }
    // If no match (process completed, currentNode = "End"), find the last userTask (no outgoing edges to other userTasks)
    if (!currentId) {
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

// Parse all userTask-bound forms in BPMN graph order (BFS from startEvent through sequenceFlows
// then descend into entered subProcesses). Ensures correct topological order so My Request shows
// y → subform → subform_copy regardless of XML element ordering.
const parseBpmnXmlAndGetAllFormIds = (xml: string): Array<{ formId: string | null, formName: string | null, taskName: string | null }> => {
  if (!xml) return []
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const allElements = doc.getElementsByTagName('*')

    type FlowNode = { id: string; localName: string; el: Element; parentSubProc: string | null }
    const nodes = new Map<string, FlowNode>()
    const flows: Array<{ source: string; target: string }> = []

    const localNameOf = (el: Element) => el.localName || el.nodeName.split(':').pop() || ''
    const getDirectParentSubProcessId = (element: Element): string | null => {
      let node: Node | null = element.parentNode
      while (node && node.nodeType === 1) {
        const el = node as Element
        const ln = localNameOf(el)
        if (ln === 'subProcess') return el.getAttribute('id')
        if (ln === 'process' || ln === 'definitions') return null
        node = el.parentNode
      }
      return null
    }

    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const ln = localNameOf(el)
      if (ln === 'sequenceFlow') {
        flows.push({ source: el.getAttribute('sourceRef') || '', target: el.getAttribute('targetRef') || '' })
        continue
      }
      const id = el.getAttribute('id')
      if (!id) continue
      if (ln === 'userTask' || ln === 'startEvent' || ln === 'endEvent' || ln === 'subProcess'
          || ln === 'serviceTask' || ln === 'exclusiveGateway' || ln === 'parallelGateway'
          || ln === 'inclusiveGateway' || ln === 'task' || ln === 'eventBasedGateway'
          || ln === 'intermediateCatchEvent' || ln === 'intermediateThrowEvent') {
        nodes.set(id, { id, localName: ln, el, parentSubProc: getDirectParentSubProcessId(el) })
      }
    }

    const adj = new Map<string, string[]>()
    for (const f of flows) {
      if (!f.source || !f.target) continue
      if (!adj.has(f.source)) adj.set(f.source, [])
      adj.get(f.source)!.push(f.target)
    }

    const startIds: string[] = []
    for (const [id, n] of nodes) {
      if (n.localName === 'startEvent' && !n.parentSubProc) startIds.push(id)
    }

    const collectUserTaskInfo = (el: Element): { formId: string | null; formName: string | null; taskName: string | null } => {
      const taskName = el.getAttribute('name') || null
      let formId: string | null = null
      let formName: string | null = null
      const props = el.getElementsByTagName('*')
      for (let j = 0; j < props.length; j++) {
        const p = props[j]
        const ln = localNameOf(p)
        if (ln === 'property' || ln === 'values') {
          const n = p.getAttribute('name')
          const v = p.getAttribute('value')
          if (n === 'formId' && v) formId = v
          if (n === 'formName' && v) formName = v
        }
      }
      return { formId, formName, taskName }
    }

    const result: Array<{ formId: string | null, formName: string | null, taskName: string | null }> = []
    const seenKeys = new Set<string>()
    const visited = new Set<string>()
    const queue: string[] = [...startIds]
    for (const s of startIds) visited.add(s)

    const orderedSubProcessVisits: string[] = []
    while (queue.length > 0) {
      const id = queue.shift()!
      const n = nodes.get(id)
      if (!n) continue
      if (n.localName === 'userTask') {
        const info = collectUserTaskInfo(n.el)
        const key = info.formId || info.formName || info.taskName || ''
        if (key && !seenKeys.has(key)) {
          seenKeys.add(key)
          result.push(info)
        }
      } else if (n.localName === 'subProcess') {
        orderedSubProcessVisits.push(id)
      }
      for (const next of (adj.get(id) || [])) {
        if (!visited.has(next)) { visited.add(next); queue.push(next) }
      }
    }

    // After top-level traversal, descend into each entered subProcess in encounter order
    // so its inner userTasks (e.g. subform_copy in MI) come after the subProcess's siblings.
    for (const spId of orderedSubProcessVisits) {
      const sp = nodes.get(spId)
      if (!sp) continue
      const innerStartIds: string[] = []
      const innerVisited = new Set<string>()
      for (const [id, n] of nodes) {
        if (n.parentSubProc === spId && n.localName === 'startEvent') {
          innerStartIds.push(id)
          innerVisited.add(id)
        }
      }
      const innerQueue = [...innerStartIds]
      while (innerQueue.length > 0) {
        const id = innerQueue.shift()!
        const n = nodes.get(id)
        if (!n) continue
        if (n.localName === 'userTask') {
          const info = collectUserTaskInfo(n.el)
          const key = info.formId || info.formName || info.taskName || ''
          if (key && !seenKeys.has(key)) {
            seenKeys.add(key)
            result.push(info)
          }
        }
        for (const next of (adj.get(id) || [])) {
          if (!innerVisited.has(next)) { innerVisited.add(next); innerQueue.push(next) }
        }
      }
    }

    return result
  } catch (e) {
    console.error('Failed to parse BPMN for all formIds:', e)
    return []
  }
}

/**
 * Map processInfo.currentNode to an index in parseBpmnXmlAndGetAllFormIds order.
 * Flowable often exposes taskDefinitionKey (BPMN userTask id) while the UI shows the task "name";
 * a plain string compare against taskName/formName then misses and subform_copy never appears.
 */
function findInitiatorCurrentStepIndexInAllOrdered(
  xml: string,
  curRaw: string,
  allOrdered: Array<{ formId: string | null; formName: string | null; taskName: string | null }>
): number | null {
  const norm = (s: string | null | undefined) => (s || '').trim().replace(/\s+/g, ' ')
  const curTrim = String(curRaw || '').trim()
  const curN = norm(curRaw)
  if (!curTrim && !curN) return null

  let idx = allOrdered.findIndex(
    info =>
      norm(info.taskName) === curN ||
      norm(info.formName) === curN ||
      (info.formId != null && String(info.formId) === curTrim)
  )
  if (idx >= 0) return idx

  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const allElements = doc.getElementsByTagName('*')
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if (localName !== 'userTask') continue
      const taskDefKey = el.getAttribute('id') || ''
      const tname = el.getAttribute('name') || ''
      if (taskDefKey !== curTrim && norm(tname) !== curN) continue
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
      const hit = allOrdered.findIndex(
        info =>
          (formId != null && info.formId === formId) ||
          (formName != null && norm(info.formName) === norm(formName)) ||
          (norm(info.taskName) === norm(tname) && norm(tname).length > 0)
      )
      if (hit >= 0) return hit
    }
  } catch {
    /* ignore */
  }

  return null
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
      if (localName === 'subProcess') {
        const children = el.getElementsByTagName('*')
        const isMultiInstanceSubProcess = Array.from(children).some(child => {
          const childLocal = child.localName || child.nodeName.split(':').pop()
          return childLocal === 'multiInstanceLoopCharacteristics'
        })
        if (!isMultiInstanceSubProcess) continue

        for (let j = 0; j < children.length; j++) {
          const child = children[j]
          const childLocal = child.localName || child.nodeName.split(':').pop()
          if (childLocal !== 'userTask') continue
          const props = child.getElementsByTagName('*')
          for (let k = 0; k < props.length; k++) {
            const p = props[k]
            const propLocal = p.localName || p.nodeName.split(':').pop()
            if ((propLocal === 'property' || propLocal === 'values') && p.getAttribute('name') === 'formId') {
              return p.getAttribute('value')
            }
          }
        }
      }
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
    const snapshotNodeKey = snapshotActivityId.value || snapshotTaskDefinitionKey || snapshotTaskName || ''
    const snapshotActive = !!(snapshotNodeKey && processInfo.value.status === 'RUNNING')

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
    
    // Get current node name (normalized for robust matching)
    const currentNodeName = processInfo.value.currentNode || ''
    const normNodeName = currentNodeName.trim().replace(/\s+/g, ' ')
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
      if ((spName && spName === currentNodeName) || spId === currentNodeName) {
        enteredSubProcesses.add(spId)
        continue
      }
      if (spName && normNodeName && spName.trim().replace(/\s+/g, ' ') === normNodeName) {
        enteredSubProcesses.add(spId)
        continue
      }
      const childElements = sp.getElementsByTagName('*')
      for (let i = 0; i < childElements.length; i++) {
        const childLocal = childElements[i].localName || childElements[i].nodeName.split(':').pop()
        if (childLocal !== 'userTask' && childLocal !== 'serviceTask') continue
        const taskName = childElements[i].getAttribute('name') || ''
        const taskId = childElements[i].getAttribute('id') || ''
        const taskNameNorm = taskName.trim().replace(/\s+/g, ' ')
        if (
          taskName === currentNodeName ||
          (!!normNodeName && taskNameNorm === normNodeName) ||
          historyRecords.value.some(h => h.nodeName === taskName || h.nodeId === taskId)
        ) {
          enteredSubProcesses.add(spId)
          break
        }
      }
    }

    // Detect active multi-instance subprocesses whose child tasks are still running
    const activeMultiInstanceSubProcesses = new Set<string>()
    if (processInfo.value.status === 'RUNNING' && !snapshotActive) {
      for (const [spId, sp] of subProcessMap) {
        if (!enteredSubProcesses.has(spId)) continue
        const spChildren = sp.getElementsByTagName('*')
        let isMultiInstance = false
        for (let i = 0; i < spChildren.length; i++) {
          const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
          if (childLocal === 'multiInstanceLoopCharacteristics') {
            isMultiInstance = true
            break
          }
        }
        if (!isMultiInstance) continue
        const spName = sp.getAttribute('name') || ''
        const sn = spName.trim().replace(/\s+/g, ' ')
        if ((spName && (!!normNodeName && sn === normNodeName)) || spId === currentNodeName) {
          activeMultiInstanceSubProcesses.add(spId)
          continue
        }
        for (let i = 0; i < spChildren.length; i++) {
          const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
          if (childLocal !== 'userTask') continue
          const taskName = spChildren[i].getAttribute('name') || ''
          const taskId = spChildren[i].getAttribute('id') || ''
          const tn = taskName.trim().replace(/\s+/g, ' ')
          if (
            tn === normNodeName ||
            taskId === currentNodeName.trim() ||
            taskName === currentNodeName ||
            (taskName && nodeStatusMap.get(taskName) === 'current')
          ) {
            activeMultiInstanceSubProcesses.add(spId)
            break
          }
        }
      }

      doc.querySelectorAll('userTask').forEach(taskEl => {
        const uid = (taskEl.getAttribute('id') || '').trim()
        const unameNorm = (taskEl.getAttribute('name') || '').trim().replace(/\s+/g, ' ')
        const curTrim = currentNodeName.trim()
        const matchesOpen =
          (!!normNodeName && unameNorm === normNodeName) ||
          uid === curTrim ||
          uid === currentNodeName
        if (!matchesOpen) return
        let walker: Node | null = taskEl.parentNode
        while (walker && walker.nodeType === 1) {
          const wrap = walker as Element
          const lname = wrap.localName || wrap.nodeName.split(':').pop()
          if (lname === 'subProcess') {
            const sid = wrap.getAttribute('id') || ''
            if (sid && enteredSubProcesses.has(sid)) {
              const desc = wrap.getElementsByTagName('*')
              let hasMi = false
              for (let di = 0; di < desc.length; di++) {
                const ln = desc[di].localName || desc[di].nodeName.split(':').pop()
                if (ln === 'multiInstanceLoopCharacteristics') {
                  hasMi = true
                  break
                }
              }
              if (hasMi) activeMultiInstanceSubProcesses.add(sid)
            }
          }
          if (lname === 'process' || lname === 'definitions') break
          walker = wrap.parentNode
        }
      })
    }
    // Completed multi-instance subprocesses: entered MI subprocesses where all child userTasks are done
    const completedMultiInstanceSubProcesses = new Set<string>()
    for (const [spId, sp] of subProcessMap) {
      if (!enteredSubProcesses.has(spId)) continue
      if (activeMultiInstanceSubProcesses.has(spId)) continue
      const spChildren = sp.getElementsByTagName('*')
      let isMultiInstance = false
      for (let i = 0; i < spChildren.length; i++) {
        const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
        if (childLocal === 'multiInstanceLoopCharacteristics') {
          isMultiInstance = true
          break
        }
      }
      if (!isMultiInstance) continue
      let allDone = true
      let userTaskCount = 0
      for (let i = 0; i < spChildren.length; i++) {
        const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
        if (childLocal !== 'userTask') continue
        userTaskCount++
        const taskName = spChildren[i].getAttribute('name') || ''
        const taskId = spChildren[i].getAttribute('id') || ''
        const historyMatch = historyRecords.value.find(h => h.nodeName === taskName || h.nodeId === taskId)
        if (!historyMatch || (historyMatch.status !== 'completed' && historyMatch.status !== 'rejected')) {
          allDone = false
          break
        }
      }
      if (userTaskCount > 0 && allDone) {
        completedMultiInstanceSubProcesses.add(spId)
      }
    }

    // Completed-task snapshot: multi-instance subprocess with a single userTask that matches snapshotTaskName
    const completedSnapshotSingleTaskSubProcesses = new Set<string>()
    if (snapshotTaskName) {
      for (const [spId, sp] of subProcessMap) {
        if (!enteredSubProcesses.has(spId)) continue
        const spChildren = sp.getElementsByTagName('*')
        let isMultiInstance = false
        let userTaskCount = 0
        let snapshotMatchesChild = false
        for (let i = 0; i < spChildren.length; i++) {
          const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
          if (childLocal === 'multiInstanceLoopCharacteristics') {
            isMultiInstance = true
          }
          if (childLocal === 'userTask') {
            userTaskCount++
            const taskName = spChildren[i].getAttribute('name') || ''
            const taskId = spChildren[i].getAttribute('id') || ''
            if (taskName === snapshotNodeKey || taskId === snapshotNodeKey) {
              snapshotMatchesChild = true
            }
          }
        }
        if (isMultiInstance && userTaskCount === 1 && snapshotMatchesChild) {
          completedSnapshotSingleTaskSubProcesses.add(spId)
        }
      }
    }

    // Parse start events (subprocess-internal starts are pending until the subprocess is entered)
    doc.querySelectorAll('startEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `start_${index}`
      const pos = positionMap.get(id)
      const parentSpId = getParentSubProcessId(event)
      let startStatus: 'completed' | 'current' | 'pending' = 'completed'
      if (parentSpId && !enteredSubProcesses.has(parentSpId)) {
        startStatus = 'pending'
      } else if (parentSpId && completedSnapshotSingleTaskSubProcesses.has(parentSpId)) {
        startStatus = 'completed'
      } else if (parentSpId && activeMultiInstanceSubProcesses.has(parentSpId)) {
        startStatus = 'current'
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
      const parentSpId = getParentSubProcessId(task)

      // Prefer status from history records
      const historyStatus = nodeStatusMap.get(name)
      if (snapshotActive) {
        // Snapshot mode: only show status up to snapshotTaskName
        if (name === snapshotNodeKey || id === snapshotNodeKey) {
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
      } else if (parentSpId && activeMultiInstanceSubProcesses.has(parentSpId)) {
        status = 'current'
        currentNodeId.value = id
        foundCurrentNode = true
      } else if (processInfo.value.status === 'COMPLETED') {
        // Process completed: only mark nodes that were actually executed (matched via history records)
        const historyMatch = historyRecords.value.find(h => h.nodeName === name || h.nodeId === id)
        if (historyMatch) {
          status = historyMatch.status === 'rejected' ? 'rejected' : 'completed'
          completed.push(id)
        }
      } else if (processInfo.value.status === 'RUNNING') {
        // Process running: determine status based on current node name.
        // Normalize whitespace for robust comparison, then fall back to matching by taskDefinitionKey.
        const normName = currentNodeName.trim().replace(/\s+/g, ' ')
        const normBpmnName = name.trim().replace(/\s+/g, ' ')
        if (normBpmnName === normName || id === currentNodeName) {
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
      let isMiSubProcess = false
      const spDescForMi = sp.getElementsByTagName('*')
      for (let mi = 0; mi < spDescForMi.length; mi++) {
        const miLocal = spDescForMi[mi].localName || spDescForMi[mi].nodeName.split(':').pop()
        if (miLocal === 'multiInstanceLoopCharacteristics') {
          isMiSubProcess = true
          break
        }
      }
      // Ended process: MI subprocess must show completed (green), not Current Step; Flowable still reports last activity as currentNode.
      if (
        processInfo.value.status === 'COMPLETED' &&
        enteredSubProcesses.has(spId) &&
        isMiSubProcess
      ) {
        spStatus = 'completed'
      } else if (processInfo.value.status === 'COMPLETED' && hasCompletedMiRows()) {
        spStatus = 'completed'
      } else if (processInfo.value.status === 'RUNNING' && hasIncompleteMiRows()) {
        spStatus = 'current'
      } else if (snapshotActive && completedSnapshotSingleTaskSubProcesses.has(spId)) {
        spStatus = 'completed'
      } else if (enteredSubProcesses.has(spId)) {
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
          // While RUNNING, currentNode match means activities still in this subprocess. When COMPLETED, same name/id is often the last finished task; do not mark as current.
          if (
            processInfo.value.status !== 'COMPLETED' &&
            (taskName === currentNodeName || taskId === currentNodeName)
          ) {
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
      const parentSpId = getParentSubProcessId(gateway)
      
      // Determine gateway status from history records
      let status: 'completed' | 'current' | 'pending' = 'pending'
      if (parentSpId && activeMultiInstanceSubProcesses.has(parentSpId)) {
        status = 'current'
      } else if (snapshotActive) {
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
      let status: 'completed' | 'current' | 'pending' | 'rejected' = 'pending'

      // SubProcess-internal endEvents stay pending when the subProcess hasn't been entered
      if (parentSpId && !enteredSubProcesses.has(parentSpId)) {
        status = 'pending'
      } else if (parentSpId && completedSnapshotSingleTaskSubProcesses.has(parentSpId)) {
        status = 'completed'
      } else if (parentSpId && activeMultiInstanceSubProcesses.has(parentSpId)) {
        status = 'pending'
      } else if (parentSpId && completedMultiInstanceSubProcesses.has(parentSpId)) {
        status = 'completed'
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

// form-create runtime-only nodes. Layout containers such as group/el-row/el-col
// must be traversed so application detail matches developer workstation preview.
const FC_SKIP_TYPES = new Set(['subForm', 'tableForm', 'tableFormColumn'])

// Recursively extract fields
const extractFieldsRecursive = (items: any[]): FormField[] => {
  const fields: FormField[] = []
  for (const item of items) {
    const bindingId = item._bindingId ?? item.props?._bindingId
    if (item.type === 'subTable' && bindingId != null) {
      fields.push({
        key: `__subTable_${bindingId}`,
        label: '',
        type: 'subTable',
        _bindingId: Number(bindingId),
        span: 24,
      })
    } else if (isCardRule(item)) {
      fields.push({
        key: getLayoutKey(item, fields.length, 'card'),
        label: getLayoutLabel(item),
        type: 'card',
        span: item.col?.span || 24,
        children: item.children && Array.isArray(item.children)
          ? extractFieldsRecursive(item.children)
          : [],
      } as any)
      continue
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
        _lookupSelectedDisplayField: lookupCfg.selectedDisplayField || lookupCfg.displayField || '',
        _lookupFilterConditions: Array.isArray(lookupCfg.filterConditions) ? lookupCfg.filterConditions : [],
        _lookupViewFields: resolvedViewFields
      }
      fields.push(field)
    } else if (FC_SKIP_TYPES.has(item.type)) {
      continue
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

// Derive display columns for a sub-table binding from the designer config.
// My Request must only show columns configured in developer-workstation.
const deriveColumnsFromBinding = (binding: any, formConfig?: Record<string, any>): Array<{ field: string; label: string; type?: string; required?: boolean; options?: Array<{ label: string; value: any }>; props?: Record<string, any> }> => {
  const listColumns =
    formConfig?.subListViews?.[binding.bindingId]?.columns ||
    formConfig?.subListViews?.[String(binding.bindingId)]?.columns
  if (Array.isArray(listColumns) && listColumns.length > 0) {
    return listColumns
      .filter((col: any) => col && col.fieldName)
      .map((col: any) => ({
        field: col.fieldName,
        label: col.columnLabel || col.comment || col.fieldName,
        type: mapDesignerColumnType(col.dataType, col.columnType),
        ...(col.linkText || col.componentId ? { props: { linkText: col.linkText, componentId: col.componentId } } : {}),
      }))
  }

  const subFormRule =
    binding.subFormConfig?.rule ||
    formConfig?.subForms?.[binding.bindingId]?.rule ||
    formConfig?.subForms?.[String(binding.bindingId)]?.rule
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

/** Build a read-only PreviousFormEntry from designer form metadata (shared by history + live MI step). */
function buildPreviousFormEntry(
  prevForm: any,
  options: { isKnownMiSubTask: boolean; isActiveMiSubTaskStep?: boolean }
): PreviousFormEntry {
  const savedSubTables = formData.value.__subTables__
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
            parsedTabs.push({
              name: tabPane.props.name || `tab_${parsedTabs.length}`,
              label: tabPane.props.label || `Tab ${parsedTabs.length + 1}`,
              fields: tabFields
            })
          }
        }
      } else {
        parsedFields.push(...extractFieldsRecursive(rules))
      }
    }
  } catch { /* ignore */ }

  let prevFormConfig: Record<string, any> = {}
  try {
    const cfg = typeof prevForm.data === 'string' ? JSON.parse(prevForm.data) : (prevForm.data || {})
    prevFormConfig = cfg || {}
  } catch { /* ignore */ }
  const prevBindings: PreviousFormEntry['subTableBindings'] = []
  const prevRuleBindingIds = collectRuleBindingIds(
    Array.isArray(prevFormConfig?.rule) ? prevFormConfig!.rule : []
  )
  for (const b of (prevForm.tableBindings || [])) {
    if (b.bindingType === 'PRIMARY') continue
    const cols = deriveColumnsFromBinding(b, prevFormConfig)
    if (!Array.isArray(cols) || cols.length === 0) continue
    const placed = prevRuleBindingIds.has(Number(b.bindingId))
    const isFormOnly = String(b.subMode || '').toUpperCase() === 'FORM_ONLY'
    if (!placed && isFormOnly) continue
    const binding = {
      bindingId: b.bindingId,
      tableId: b.tableId != null ? Number(b.tableId) : null,
      bindingType: b.bindingType,
      bindingMode: b.bindingMode,
      foreignKeyField: b.foreignKeyField,
      tableName: b.tableDisplayName || b.tableName,
      tableType: b.tableType,
      tableDescription: b.tableDescription,
      columns: cols,
      data: [] as any[]
    }
    if (savedSubTables) {
      const saved = getSavedSubTableRowsFromVariables(savedSubTables, {
        bindingId: b.bindingId,
        tableName: b.tableName,
        tableDisplayName: b.tableDisplayName
      })
      if (Array.isArray(saved)) binding.data = saved
    }
    prevBindings.push(binding)
  }

  return {
    formId: String(prevForm.id),
    formName: prevForm.name,
    labelWidth: formLabelWidth.value,
    fields: parsedFields,
    tabs: parsedTabs,
    isMiSubTask: options.isKnownMiSubTask,
    ...(options.isActiveMiSubTaskStep === true ? { isActiveMiSubTaskStep: true } : {}),
    subTableBindings: prevBindings
  }
}

function mapDesignerColumnType(dataType?: string, columnType?: string): string | undefined {
  if (columnType === 'linkForm') return 'linkForm'
  if (columnType === 'lookup') return 'lookup'
  const normalized = String(dataType || '').toUpperCase()
  if (normalized === 'BIGINT' || normalized === 'INTEGER' || normalized === 'DECIMAL' || normalized === 'NUMBER') return 'number'
  if (normalized === 'BOOLEAN') return 'switch'
  if (normalized === 'DATE') return 'date'
  if (normalized === 'TIMESTAMP' || normalized === 'DATETIME') return 'datetime'
  if (normalized === 'TEXT') return 'textarea'
  return 'text'
}

// Load flow history
const loadProcessHistory = async () => {
  try {
    const response = await processApi.getProcessHistory(processId)
    const historyData = response.data || response
    if (historyData && Array.isArray(historyData)) {

      // Running + snapshot: only keep records up to this task; completed processes show full history
      let filteredData = historyData
      if (snapshotTaskId) {
        const snapshotRecord = historyData.find((item: any) => String(item.taskId || '') === snapshotTaskId)
        if (snapshotRecord?.activityId) {
          snapshotActivityId.value = String(snapshotRecord.activityId)
        }
      }
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
