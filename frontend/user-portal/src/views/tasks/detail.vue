<template>
  <div class="task-detail-page">
    <!-- Page header -->
    <div class="page-header">
      <el-button
        :icon="ArrowLeft"
        @click="$router.back()"
      >
        {{ t('common.back') }}
      </el-button>
      <h1>{{ taskInfo.taskName || t('task.detail') }}</h1>
      <el-tag
        :type="getPriorityType(taskInfo.priority)"
        size="small"
      >
        {{ getPriorityLabel(taskInfo.priority) }}
      </el-tag>
      <el-tag
        v-if="taskInfo.isOverdue"
        type="danger"
        size="small"
      >
        {{ t('task.overdue') }}
      </el-tag>
    </div>

    <!-- Loading state -->
    <div
      v-if="loading"
      class="skeleton-content"
    >
      <el-skeleton
        animated
        :count="3"
      >
        <template #template>
          <el-skeleton-item
            variant="rect"
            style="height: 120px; margin-bottom: 20px;"
          />
          <el-skeleton-item
            variant="rect"
            style="height: 300px; margin-bottom: 20px;"
          />
          <el-skeleton-item
            variant="rect"
            style="height: 200px;"
          />
        </template>
      </el-skeleton>
    </div>

    <!-- Task loading error -->
    <div
      v-else-if="taskError"
      class="error-content"
    >
      <el-result
        icon="warning"
        :title="taskError"
      >
        <template #extra>
          <el-button
            type="primary"
            @click="$router.back()"
          >
            {{ t('common.back') }}
          </el-button>
          <el-button @click="loadTaskDetail">
            {{ t('common.reset') }}
          </el-button>
        </template>
      </el-result>
    </div>

    <!-- Main content -->
    <div
      v-else
      class="content-sections"
    >
      <!-- Section 1: Basic info -->
      <TaskBasicInfo
        :task-info="taskInfo"
        :format-date="formatDate"
        :get-current-assignee-display="getCurrentAssigneeDisplay"
      />

      <!-- Section 2: Process diagram -->
      <div class="section workflow-section">
        <div class="section-header">
          <el-icon><Share /></el-icon>
          <span>{{ t('task.workflowDiagram') }}</span>
          <el-tag
            v-if="!isCompletedTask"
            type="warning"
            size="small"
          >
            {{ taskInfo.taskName || t('task.pending') }}
          </el-tag>
        </div>
        <div class="section-content">
          <el-alert
            v-if="processError"
            :title="processError"
            type="warning"
            show-icon
            :closable="false"
          />
          <ProcessDiagram
            v-else-if="bpmnXml || processNodes.length > 0"
            :nodes="processNodes"
            :flows="processFlows"
            :bpmn-xml="bpmnXml"
            :current-node-id="currentNodeId"
            :completed-node-ids="completedNodeIds"
            :selected-node-id="selectedNodeId ?? ''"
            :show-toolbar="true"
            :show-legend="true"
            :show-current-step="!isCompletedTask"
            @node-click="handleNodeClick"
          />
          <el-empty
            v-else
            :description="t('task.noProcessDefinition')"
          />
        </div>
      </div>

      <!-- Selected node form (click a node in the diagram to show its form) -->
      <div
        v-if="selectedNodeId && selectedNodeForm"
        class="section form-section"
      >
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ selectedNodeForm.formName }}</span>
          <el-tag
            v-if="selectedNodeForm.isCurrentTask && !isCompletedTask"
            type="warning"
            size="small"
          >
            {{ t('task.currentStep') }}
          </el-tag>
          <el-tag
            v-else
            type="info"
            size="small"
          >
            {{ t('task.readonly') }}
          </el-tag>
          <el-button
            size="small"
            style="margin-left: auto;"
            @click="clearNodeSelection"
          >
            {{ t('common.back') }}
          </el-button>
        </div>
        <div class="section-content">
          <div
            v-if="selectedNodeForm.fields.length > 0 || selectedNodeForm.tabs.length > 0"
            class="form-container"
          >
            <FormRenderer
              :fields="selectedNodeForm.isCurrentTask ? formFields : selectedNodeForm.fields"
              :tabs="selectedNodeForm.isCurrentTask ? formTabs : selectedNodeForm.tabs"
              :model-value="selectedNodeForm.isCurrentTask ? formData : selectedNodeForm.values"
              :label-width="formLabelWidth"
              :readonly="selectedNodeForm.isCurrentTask ? formReadOnly : true"
              :sub-table-bindings="selectedNodeForm.isCurrentTask ? subTableBindings : selectedNodeForm.subTableBindings"
              :linked-sub-table-bindings="selectedNodeForm.isCurrentTask ? linkableSubTableBindings : undefined"
              :preview-sub-tables="selectedNodeForm.isCurrentTask ? isMiSubTaskMode : true"
              :task-id="selectedNodeForm.isCurrentTask ? effectiveTaskId : undefined"
              :allow-sub-table-assign="selectedNodeForm.isCurrentTask ? allowSubTableAssignForCurrentTask : false"
              :suppress-link-form-initial-data="selectedNodeForm.isCurrentTask ? (isMiSubTaskMode && !isCompletedTask) : true"
              :show-link-form-dialog-footer="selectedNodeForm.isCurrentTask ? (!isCompletedTask && !formReadOnly) : false"
              @update:model-value="val => { if (selectedNodeForm.isCurrentTask) formData = { ...formData, ...val } }"
              @update:sub-table-data="(bindingId, rows) => { if (selectedNodeForm.isCurrentTask) syncMainSubTableRows(bindingId, rows) }"
            />
          </div>
          <el-empty
            v-else
            :description="t('task.noFormData')"
          />
        </div>
      </div>
      <!-- Node selected but no form bound -->
      <div
        v-else-if="selectedNodeId && !selectedNodeForm"
        class="section form-section"
      >
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ selectedNodeId }}</span>
        </div>
        <div class="section-content">
          <el-empty :description="t('task.noFormBound')" />
          <div style="text-align: center; margin-top: 8px;">
            <el-button
              size="small"
              @click="clearNodeSelection"
            >
              {{ t('common.back') }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- Task 17.1 / 17.4: Collapsible Process Form panel -->
      <div
        v-if="showProcessFormPanel && processFormData"
        class="section process-form-section"
      >
        <el-collapse v-model="processFormCollapse">
          <el-collapse-item
            :title="isReturnToRequester ? t('process.processForm') : t('process.processFormReadonly')"
            name="processForm"
          >
            <div class="section-content">
              <FormRenderer
                v-if="processFormFields.length > 0 || processFormTabs.length > 0"
                :fields="processFormFields"
                :tabs="processFormTabs"
                :model-value="processFormValues"
                :label-width="formLabelWidth"
                :readonly="!processFormEditable"
                @update:model-value="val => processFormValues = { ...processFormValues, ...val }"
              />
              <el-empty
                v-else
                :description="t('task.noFormData')"
              />
              <div
                v-if="processFormEditable"
                class="process-form-actions"
                style="margin-top: 16px; text-align: right;"
              >
                <el-button
                  type="primary"
                  :loading="submitting"
                  @click="handleProcessFormSubmit"
                >
                  {{ t('common.submit') }}
                </el-button>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <!-- Section 3: Form data (hide normal task form card when previewing a selected node) -->
      <div
        v-if="!selectedNodeId && (!isMiSubTaskMode || formFields.length > 0 || formTabs.length > 0)"
        class="section form-section"
      >
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ currentFormName || t('task.taskForm') }}</span>
        </div>
        <div class="section-content">
          <div
            v-if="formFields.length > 0 || formTabs.length > 0"
            class="form-container"
          >
            <FormRenderer
              :fields="formFields"
              :tabs="formTabs"
              :model-value="formData"
              :label-width="formLabelWidth"
              :readonly="formReadOnly"
              :sub-table-bindings="subTableBindings"
              :linked-sub-table-bindings="linkableSubTableBindings"
              :preview-sub-tables="isMiSubTaskMode"
              :task-id="effectiveTaskId"
              :allow-sub-table-assign="allowSubTableAssignForCurrentTask"
              :suppress-link-form-initial-data="isMiSubTaskMode && !isCompletedTask"
              :show-link-form-dialog-footer="!isCompletedTask && !formReadOnly"
              @update:model-value="val => formData = { ...formData, ...val }"
              @update:sub-table-data="syncMainSubTableRows"
            />
          </div>
          <el-empty :description="t('task.noFormData')" />
        </div>
      </div>

      <!-- Task 17.3: Completed task snapshot comparison view -->
      <TaskSnapshotSection
        :is-completed-task="isCompletedTask"
        :completed-form-data="completedFormData"
        :form-fields="formFields"
        :form-tabs="formTabs"
      />

      <!-- Task 19.2: Change history panel (title and collapse handled internally by ChangeHistoryPanel) -->
      <div
        v-if="taskInfo.processInstanceId"
        class="section change-history-section"
      >
        <ChangeHistoryPanel
          :process-instance-id="taskInfo.processInstanceId"
          :snapshot-time="completedHistorySnapshotTime"
          :task-instance-id="completedHistoryTaskId"
        />
      </div>

      <!-- Section 4: Flow history -->
      <TaskHistorySection
        :history-records="historyRecords"
        :history-error="historyError"
      />

      <!-- Section 5: Action buttons (hidden for completed tasks) -->
      <TaskActionBar
        :is-completed-task="isCompletedTask"
        :show-implicit-save-action="showImplicitSaveAction"
        :saving-task-form="savingTaskForm"
        :actions="taskInfo.actions"
        :get-button-type="getButtonType"
        :get-icon-component="getIconComponent"
        :get-action-label="getActionLabel"
        @save="saveCurrentTaskForm"
        @custom-action="handleCustomAction"
        @approve="handleApprove"
@reject="handleReject" @delegate="handleDelegate" @transfer="handleTransfer" @urge="handleUrge"
      />
    </div>

    <!-- Approval dialog -->
    <ApproveDialog
      v-model="approveDialogVisible"
      :title="approveDialogTitle"
      :form-data="approveForm"
      :submitting="submitting"
      @confirm="submitApprove"
    />

    <!-- Delegate/Transfer dialog -->
    <ActionDialog
      v-model="actionDialogVisible"
      :title="actionDialogTitle"
      :current-action="currentAction"
      :form-data="actionForm"
      :user-options="userOptions"
      :submitting="submitting"
      @confirm="submitAction"
      @opened="onActionDialogOpened"
    />

    <!-- N8N Action dialog -->
    <N8nActionDialog
      v-model:visible="n8nActionDialogVisible"
      :action-definition="n8nActionDefinition"
      :task-id="effectiveTaskId"
      :process-instance-id="taskInfo.processInstanceId || ''"
      :initial-data="n8nInitialData"
      @executed="handleN8nActionExecuted"
    />

    <!-- Form popup dialog -->
    <FormPopupDialog
      v-model="formPopupVisible"
      :title="formPopupTitle"
      :width="formPopupWidth"
      :fields="formPopupFields"
      :tabs="formPopupTabs"
      :form-data="formPopupData"
      :label-width="formPopupLabelWidth"
      :readonly="formPopupReadOnly"
      :submitting="submitting"
      @update:form-data="val => formPopupData = { ...formPopupData, ...val }"
@submit="submitFormPopup"
    />

    <!-- MI subtask fill-form dialog -->
    <MiFillDialog
      v-model="miFillDialogVisible"
      :title="currentFormName || t('task.taskForm')"
      :fields="formFields"
      :tabs="formTabs"
      :form-data="miFillDialogData"
      :label-width="formLabelWidth"
      :form-read-only="formReadOnly"
      :dialog-read-only="miFillDialogReadOnly"
      :sub-table-bindings="miFillSubTableBindings"
      :is-mi-sub-task-mode="isMiSubTaskMode"
:is-completed-task="isCompletedTask" @update:form-data="val => miFillDialogData = { ...miFillDialogData, ...val }" @update:sub-table-data="syncMiFillSubTableRows" @confirm="saveMiFillDialog"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { 
  getTaskDetail, 
  getTaskHistory, 
  TaskInfo, 
  TaskHistoryInfo,
  TaskActionInfo
} from '@/api/task'
import { processApi } from '@/api/process'
import ProcessDiagram, { type ProcessNode, type ProcessFlow } from '@/components/ProcessDiagram.vue'
import ProcessHistory, { type HistoryRecord } from '@/components/ProcessHistory.vue'
import FormRenderer, { type FormField, type FormTab } from '@/components/FormRenderer.vue'
import SubTableField from '@/components/SubTableField.vue'
import N8nActionDialog from '@/components/N8nActionDialog.vue'
import {
  resolveAssigneeFieldForBinding,
  allSubTableRowsHaveAssignee
} from '@/utils/subTableAssignment'
import dayjs from 'dayjs'
import ChangeHistoryPanel from '@/components/ChangeHistoryPanel.vue'
import TaskBasicInfo from '@/components/tasks/TaskBasicInfo.vue'
import ApproveDialog from '@/components/tasks/ApproveDialog.vue'
import ActionDialog from '@/components/tasks/ActionDialog.vue'
import FormPopupDialog from '@/components/tasks/FormPopupDialog.vue'
import MiFillDialog from '@/components/tasks/MiFillDialog.vue'
import TaskSnapshotSection from '@/components/tasks/TaskSnapshotSection.vue'
import TaskHistorySection from '@/components/tasks/TaskHistorySection.vue'
import TaskActionBar from '@/components/tasks/TaskActionBar.vue'
import { useTaskForm } from '@/composables/tasks/useTaskForm'
import { useBpmnParser } from '@/composables/tasks/useBpmnParser'
import { useTaskDisplay } from '@/composables/tasks/useTaskDisplay'
import { useTaskActions } from '@/composables/tasks/useTaskActions'
import { useCustomActions } from '@/composables/tasks/useCustomActions'
import {
  getProcessFormData,
  submitProcessFormUpdate,
  getTaskFormData as fetchTaskFormData,
  submitTaskForm as apiSubmitTaskForm,
  getCompletedTaskFormData,
  type ProcessFormData,
  type TaskFormData as TaskFormDataDTO,
  type CompletedTaskFormData,
} from '@/api/processForm'
import { relationTableApi } from '@/api/relationTable'
import { unwrapUserLikeValueToDisplayString, extractUserIdFromCellValue } from '@/components/subTableAddDialogHelpers'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const taskId = route.params.id as string
const fallbackProcessInstanceId = computed(() => {
  const v = route.query.processInstanceId
  return typeof v === 'string' && v.trim() ? v.trim() : ''
})

const loading = ref(true)
const submitting = ref(false)
const taskInfo = ref<Partial<TaskInfo>>({})
const effectiveTaskId = computed(() => {
  const currentTaskId = (taskInfo.value as Record<string, unknown>)?.taskId
  return typeof currentTaskId === 'string' && currentTaskId.trim().length > 0 ? currentTaskId : taskId
})

// Display helpers
const taskDisplay = useTaskDisplay(taskInfo as any)
const {
  getHistoryStatus,
  getHistoryAction,
  formatDate,
  getCurrentAssigneeDisplay,
  getPriorityLabel,
  getPriorityType,
  getButtonType,
  getActionLabel,
  getIconComponent
} = taskDisplay

// Error state
const taskError = ref<string | null>(null)
const processError = ref<string | null>(null)
const historyError = ref<string | null>(null)

// Node-to-form mapping for diagram click interaction
const selectedNodeId = ref<string | null>(null)
interface NodeFormInfo {
  formName: string
  isCurrentTask: boolean
  fields: FormField[]
  tabs: FormTab[]
  values: Record<string, any>
  subTableBindings: PreviousFormEntry['subTableBindings']
}
const nodeFormMap = ref<Map<string, NodeFormInfo>>(new Map())
const selectedNodeForm = computed<NodeFormInfo | null>(() => {
  if (!selectedNodeId.value) return null
  return nodeFormMap.value.get(selectedNodeId.value) ?? null
})

// Previous node forms (read-only display, ordered)
interface PreviousFormEntry {
  formId: string
  formName: string
  labelWidth: string
  fields: FormField[]
  tabs: FormTab[]
  subTableBindings: Array<{
    bindingId: number
    tableId?: number | null
    bindingType: string
    bindingMode: string
    foreignKeyField: string | null
    tableName: string
    physicalTableName?: string
    tableType: string
    tableDescription: string
    columns: Array<{ field: string; label: string; type?: string; props?: Record<string, any> }>
    formFields?: FormField[]
    formOptions?: Record<string, any>
    data: any[]
  }>
}
const previousForms = ref<PreviousFormEntry[]>([])

// Sub-table bindings for the current form
const subTableBindings = ref<Array<{
  bindingId: number
  tableId?: number | null
  bindingType: string
  bindingMode: string
  foreignKeyField: string | null
  tableName: string
  physicalTableName?: string
  tableType: string
  tableDescription: string
  columns: Array<{ field: string; label: string; type?: string; props?: Record<string, any> }>
  formFields?: FormField[]
  formOptions?: Record<string, any>
  data: any[]
}>>([])

function resolveSubFormDesign(binding: any, subForms?: Record<string, any>): { formFields: FormField[]; formOptions?: Record<string, any> } {
  const design =
    binding.subFormConfig ||
    subForms?.[binding.bindingId] ||
    subForms?.[String(binding.bindingId)] ||
    {}
  const rule = Array.isArray(design.rule) ? design.rule : []
  return {
    formFields: rule.length > 0 ? extractFieldsRecursive(rule) : [],
    formOptions: design.options
  }
}

const linkableSubTableBindings = computed(() => [
  ...subTableBindings.value,
  ...previousForms.value.flatMap(form => form.subTableBindings)
])

function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

function subTableBindingMatches(
  target: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null },
  source: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null }
): boolean {
  const targetPhysicalName = normalizeSubTableName(target.physicalTableName)
  const sourcePhysicalName = normalizeSubTableName(source.physicalTableName)
  if (targetPhysicalName && sourcePhysicalName && targetPhysicalName === sourcePhysicalName) return true
  const targetName = normalizeSubTableName(target.tableName)
  const sourceName = normalizeSubTableName(source.tableName)
  const samePhysicalTable = target.tableId != null && source.tableId != null && Number(target.tableId) === Number(source.tableId)
  return target.bindingId === source.bindingId || samePhysicalTable || (!!targetName && targetName === sourceName)
}

function cloneSubTableRows(rows: any[]): any[] {
  try {
    return JSON.parse(JSON.stringify(rows))
  } catch {
    return rows.map(row => ({ ...row }))
  }
}

function cloneSubTableBindings<T extends Array<{ data: any[] }>>(bindings: T): T {
  return bindings.map(binding => ({
    ...binding,
    data: cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
  })) as T
}

/**
 * Merge sub-table rows by PK (id / rowId). Later rows win on field conflicts.
 * Used when MI isolation leaves one binding with a single participant row but the same bindingId
 * still holds all participants in __subTables__ / previousForms — overwrite would drop other rows.
 */
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

/** After MI isolation, __subTables__ must carry all participants again; current binding only has this MI row. */
function rebuildIsolatedSubTablesPayload(): Record<string, any> {
  const subTables: Record<string, any> = {}
  const ingest = (bindings: typeof subTableBindings.value) => {
    for (const binding of bindings) {
      const rows = cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
      const canonical = String(binding.bindingId)
      const prev = getSavedSubTableRows(subTables, binding)
      const merged = mergeSubTableRowsByRowId(prev, rows)
      const out = cloneSubTableRows(merged)
      subTables[binding.bindingId] = out
      subTables[canonical] = out
      if (binding.tableName) {
        subTables[binding.tableName] = out
        subTables[normalizeSubTableName(binding.tableName)] = out
      }
    }
  }
  for (const pf of previousForms.value) {
    ingest(pf.subTableBindings)
  }
  ingest(subTableBindings.value)
  return subTables
}

function syncMainSubTableRows(bindingId: number, rows: any[]) {
  const source = subTableBindings.value.find(b => b.bindingId === bindingId)
  if (!source) return

  const nextRows = Array.isArray(rows) ? rows : []
  const sync = (binding: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null; data: any[] }) => {
    if (subTableBindingMatches(binding, source)) {
      binding.data = binding === source ? nextRows : cloneSubTableRows(nextRows)
    }
  }
  subTableBindings.value.forEach(sync)
  // Never push current-task sub-table edits into previousForms — those are read-only snapshots
  // (MI isolation + matching bindingIds would wipe other sub-tasks' rows).

  const subTables = { ...((formData.value.__subTables__ as Record<string, any>) || {}) }
  const existing = getSavedSubTableRows(subTables, source)
  const merged = isMiSubTaskMode.value ? mergeSubTableRowsByRowId(existing, nextRows) : nextRows
  const out = cloneSubTableRows(merged)
  subTables[source.bindingId] = out
  subTables[String(source.bindingId)] = out
  if (source.tableName) {
    subTables[source.tableName] = out
    subTables[normalizeSubTableName(source.tableName)] = out
  }
  formData.value = { ...formData.value, __subTables__: subTables }
  scheduleSubTableAutosave()
}

function getSavedSubTableRows(savedSubTables: any, binding: { bindingId: number; tableName: string }): any[] | undefined {
  if (!savedSubTables || typeof savedSubTables !== 'object') return undefined
  const saved =
    savedSubTables[binding.bindingId] ??
    savedSubTables[String(binding.bindingId)] ??
    savedSubTables[binding.tableName] ??
    savedSubTables[normalizeSubTableName(binding.tableName)]
  return Array.isArray(saved) ? saved : undefined
}

function applySavedRowsToBindings<T extends Array<{ bindingId: number; tableName: string; data: any[] }>>(bindings: T, savedSubTables: any): T {
  if (!savedSubTables || typeof savedSubTables !== 'object') return bindings
  bindings.forEach(binding => {
    const saved = getSavedSubTableRows(savedSubTables, binding)
    if (saved) {
      binding.data = cloneSubTableRows(saved)
    }
  })
  return bindings
}

function applyCompletedSnapshotToForm(data: CompletedTaskFormData | null) {
  const snapshotValues = (data?.snapshot?.fieldValues || {}) as Record<string, any>
  formData.value = { ...snapshotValues }

  const savedSubTables = snapshotValues.__subTables__
  applySavedRowsToBindings(subTableBindings.value, savedSubTables)
  previousForms.value.forEach(form => {
    applySavedRowsToBindings(form.subTableBindings, savedSubTables)
  })

  if (savedSubTables && typeof savedSubTables === 'object' && nodeFormMap.value.size > 0) {
    const nextMap = new Map(nodeFormMap.value)
    nextMap.forEach(info => {
      info.values = { ...snapshotValues }
      applySavedRowsToBindings(info.subTableBindings, savedSubTables)
    })
    nodeFormMap.value = nextMap
  }
}

function hydrateCurrentSubTablesFromPreviousForms() {
  for (const current of subTableBindings.value) {
    if (Array.isArray(current.data) && current.data.length > 0) continue
    const previous = previousForms.value
      .flatMap(form => form.subTableBindings)
      .find(binding =>
        binding.data?.length > 0 &&
        subTableBindingMatches(current, binding)
      )
    if (previous) {
      current.data = cloneSubTableRows(previous.data)
    }
  }
}

/** Only show sub-table Assign on the BPMN "Assign Participants" user task; initiator/other tasks only fill rows without per-row assignment */
const allowSubTableAssignForCurrentTask = computed(() => {
  const tdk = (taskInfo.value as { taskDefinitionKey?: string }).taskDefinitionKey || ''
  return tdk === 'Task_AssignParticipants'
})

function isParticipantsBinding(binding: { tableName: string }): boolean {
  const tn = (binding.tableName || '').toLowerCase()
  return tn === 'participants' || tn.endsWith('participants')
}

/** MI isolation: participant rows match by PK; related sub-table rows match by FK to participant (not by sub-row id). */
function miRowBelongsToCurrentParticipant(
  row: any,
  myRowId: number,
  binding: { tableName: string; foreignKeyField?: string }
): boolean {
  if (!row || typeof row !== 'object') return false
  if (isParticipantsBinding(binding)) {
    return Number(row.id) === myRowId || Number(row.rowId) === myRowId
  }
  const fk = binding.foreignKeyField
  if (fk && row[fk] != null && row[fk] !== '' && !Number.isNaN(Number(row[fk]))) {
    return Number(row[fk]) === myRowId
  }
  const fallbackFkKeys = ['participant_id', 'participantId', 'parent_id', 'parentId', 'meeting_participant_id']
  for (const k of fallbackFkKeys) {
    if (row[k] != null && row[k] !== '' && !Number.isNaN(Number(row[k])) && Number(row[k]) === myRowId) {
      return true
    }
  }
  return false
}

const isMiSubTask = (taskData: any): boolean => {
  const defKey = String(taskData?.taskDefinitionKey || '')
  if (defKey.startsWith('MI_UserTask_')) {
    return true
  }
  const vars = taskData?.variables || {}
  return !!(vars?._currentItem || vars?.currentItem)
}

const isMiSubTaskMode = ref(false)

// MI subtask fill-form dialog state
const miFillDialogVisible = ref(false)
const miFillDialogData = ref<Record<string, any>>({})
const miFillSubTableBindings = ref<typeof subTableBindings.value>([])
const miFilled = ref(false)
const miFillDialogReadOnly = ref(false)

function isolateMiSubTaskData(taskData: any) {
  const currentItem = taskData?.variables?._currentItem as { rowId?: number; assigneeId?: string } | undefined
  if (currentItem?.rowId == null) {
    return
  }

  const myRowId = Number(currentItem.rowId)
  if (Number.isNaN(myRowId)) {
    return
  }

  // Multi-instance data isolation: only the **current** task form is scoped to this participant.
  // Previous-node forms stay read-only with full snapshot (other sub-tasks' sub form2 data must remain visible).
  for (const binding of subTableBindings.value) {
    const rows = Array.isArray(binding.data) ? binding.data : []
    binding.data = rows.filter((row: any) => miRowBelongsToCurrentParticipant(row, myRowId, binding))
  }

  const myRow = subTableBindings.value
    .flatMap((b: any) => b.data || [])
    .find((row: any) => Number(row?.id) === myRowId || Number(row?.rowId) === myRowId)

  const originalFormData = { ...formData.value }
  const cleanedFormData: Record<string, any> = {}
  const systemKeys = Object.keys(originalFormData).filter(
    key =>
      key.startsWith('_') ||
      key.startsWith('__') ||
      key === 'initiator' ||
      key === 'meeting_id' ||
      key === 'mainRecordId' ||
      key === 'approval_result' ||
      key === 'approved'
  )
  for (const key of systemKeys) {
    cleanedFormData[key] = originalFormData[key]
  }

  const formKeys = getCurrentFormFieldKeys()
  for (const key of formKeys) {
    if (myRow && Object.prototype.hasOwnProperty.call(myRow, key)) {
      cleanedFormData[key] = (myRow as Record<string, any>)[key]
    } else {
      cleanedFormData[key] = null
    }
  }

  // Preserve previous form field values (readonly display of parent task data)
  const prevFormFieldKeys = new Set<string>()
  previousForms.value.forEach((pf: any) => {
    ;(pf.fields || []).forEach((f: any) => { if (f?.key) prevFormFieldKeys.add(String(f.key)) })
    ;(pf.tabs || []).forEach((tab: any) => {
      ;(tab?.fields || []).forEach((f: any) => { if (f?.key) prevFormFieldKeys.add(String(f.key)) })
    })
  })
  for (const key of prevFormFieldKeys) {
    if (!(key in cleanedFormData) && Object.prototype.hasOwnProperty.call(originalFormData, key)) {
      cleanedFormData[key] = originalFormData[key]
    }
  }

  cleanedFormData.__subTables__ = rebuildIsolatedSubTablesPayload()
  if (myRow && typeof myRow === 'object') {
    const rowRec = myRow as Record<string, unknown>
    const nextRowSub: Record<string, unknown> = {}
    const rebuilt = cleanedFormData.__subTables__ as Record<string, unknown>
    for (const binding of subTableBindings.value) {
      const saved = getSavedSubTableRows(rebuilt, binding)
      const rows = cloneSubTableRows(Array.isArray(saved) ? saved : [])
      nextRowSub[binding.bindingId] = rows
      nextRowSub[String(binding.bindingId)] = rows
      if (binding.tableName) {
        nextRowSub[binding.tableName] = rows
        nextRowSub[normalizeSubTableName(binding.tableName)] = rows
      }
    }
    rowRec.__subTables__ = nextRowSub
  }
  formData.value = cleanedFormData
}

/** Completed-task detail: task header uses assigneeName from engine; sub-table rows may only have user id — align display for the assignee row. */
function applyTaskAssigneeNameToMatchingSubTableRows(taskData: { assignee?: unknown; assigneeName?: unknown }) {
  const rawAid = taskData?.assignee
  const rawAname = taskData?.assigneeName
  const aid = extractUserIdFromCellValue(rawAid)
  if (!aid) return
  const displayName =
    typeof rawAname === 'string' || typeof rawAname === 'number'
      ? String(rawAname).trim()
      : unwrapUserLikeValueToDisplayString(rawAname)
  if (!displayName || displayName === '-') return
  const na = aid
  const apply = (bindings: typeof subTableBindings.value) => {
    for (const b of bindings) {
      const af = resolveAssigneeFieldForBinding(b.columns, b.tableName)
      if (!af) continue
      for (const r of b.data || []) {
        if (!r || typeof r !== 'object') continue
        const rec = r as Record<string, unknown>
        if (extractUserIdFromCellValue(rec[af]) !== na) continue
        const dn = rec.assignee_display_name
        if (dn == null || String(dn).trim() === '') {
          rec.assignee_display_name = displayName
        }
      }
    }
  }
  apply(subTableBindings.value)
  for (const pf of previousForms.value) {
    apply(pf.subTableBindings)
  }
}

function openMiFillDialog(row: any) {
  miFillDialogData.value = { ...formData.value }
  miFillSubTableBindings.value = cloneSubTableBindings(subTableBindings.value)
  miFillDialogReadOnly.value = false
  miFillDialogVisible.value = true
}

function syncMiFillSubTableRows(bindingId: number, rows: any[]) {
  const target = miFillSubTableBindings.value.find(binding => binding.bindingId === bindingId)
  if (!target) return
  const nextRows = Array.isArray(rows) ? rows : []
  target.data = nextRows

  const subTables = { ...((miFillDialogData.value.__subTables__ as Record<string, any>) || {}) }
  const existing = getSavedSubTableRows(subTables, target)
  const merged = mergeSubTableRowsByRowId(existing, nextRows)
  const out = cloneSubTableRows(merged)
  subTables[target.bindingId] = out
  subTables[String(target.bindingId)] = out
  if (target.tableName) {
    subTables[target.tableName] = out
    subTables[normalizeSubTableName(target.tableName)] = out
  }
  miFillDialogData.value = { ...miFillDialogData.value, __subTables__: subTables }
}

async function saveMiFillDialog() {
  const subTables = { ...((miFillDialogData.value.__subTables__ as Record<string, any>) || {}) }
  const subTableData: Record<string, Array<Record<string, unknown>>> = {}

  // Persist MI form field values into the participant row so that
  // the backend stores the complete row and the Detail dialog in
  // completed-tasks view can render the filled data.
  if (isMiSubTaskMode.value) {
    const formKeys = getCurrentFormFieldKeys()
    const miValues: Record<string, any> = {}
    for (const key of formKeys) {
      if (miFillDialogData.value[key] !== undefined) {
        miValues[key] = miFillDialogData.value[key]
      }
    }
    const mergeIntoRows = (rows: any[]) => {
      if (!Array.isArray(rows)) return
      for (const row of rows) Object.assign(row, miValues)
    }
    for (const b of miFillSubTableBindings.value) mergeIntoRows(b.data)
  }

  for (const binding of miFillSubTableBindings.value) {
    const rows = cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
    const existing = getSavedSubTableRows(subTables, binding)
    const merged = mergeSubTableRowsByRowId(existing, rows)
    const out = cloneSubTableRows(merged)
    subTables[binding.bindingId] = out
    subTables[String(binding.bindingId)] = out
    subTableData[String(binding.bindingId)] = out
    if (binding.tableName) {
      subTables[binding.tableName] = out
      subTables[normalizeSubTableName(binding.tableName)] = out
      subTableData[binding.tableName] = out
    }
  }

  const nextFormData = { ...formData.value, ...miFillDialogData.value, __subTables__: subTables }

  submitting.value = true
  try {
    await apiSubmitTaskForm(taskId, {
      formData: nextFormData,
      subTableData,
      baselineValues: taskFormDTO.value?.fieldValues || {}
    })
    formData.value = nextFormData
    miFilled.value = true
    miFillDialogVisible.value = false
    ElMessage.success(t('task.operationSuccess'))
  } catch {
    ElMessage.error(t('task.operationFailed'))
  } finally {
    submitting.value = false
  }
}

// Lookup config fallback map (from rt_lookup_configs)
const lookupDbConfigs = ref<Record<string, { tableId: number; searchFields: string[]; displayField: string; viewFields: any[] }>>({})

// Relation view configs from configJson (designed in developer-workstation)
const relationViewConfigs = ref<Record<string, { viewFields: any[]; allFields: any[] }>>({})

// Flow history records
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

// User search
const userOptions = ref<any[]>([])
const userSearchLoading = ref(false)

// ========== useTaskActions composable ==========

// composable blocks moved below (after loadTaskDetail)


// ── Node click handlers for diagram ──────────────────────────────────────
const handleNodeClick = (node: ProcessNode) => {
  if (selectedNodeId.value === node.id) {
    // Clicking the same node again deselects
    clearNodeSelection()
  } else {
    selectedNodeId.value = node.id
  }
}

const clearNodeSelection = () => {
  selectedNodeId.value = null
}

// Form popup state

// ========== useCustomActions composable ==========

// Form popup state
// (n8n/form popup state now provided by useCustomActions below)

// Task 17: Process Form / Task Form separation state
const processFormData = ref<ProcessFormData | null>(null)
const showProcessFormPanel = ref(false)
const processFormCollapse = ref<string[]>([])  // empty = collapsed
const processFormEditable = ref(false)
const processFormFields = ref<FormField[]>([])
const processFormTabs = ref<FormTab[]>([])
const processFormValues = ref<Record<string, any>>({})

// Task 17.2: Task Form data
const taskFormDTO = ref<TaskFormDataDTO | null>(null)
const hasConfiguredSaveAction = computed(() =>
  (taskInfo.value.actions || []).some(action => (action.actionType || '').trim().toUpperCase() === 'SAVE')
)
const showImplicitSaveAction = computed(() =>
  false
)

// Task 17.3: Completed task snapshot
const completedFormData = ref<CompletedTaskFormData | null>(null)
const isCompletedTask = ref(false)

const bpmnParser = useBpmnParser({ taskInfo: taskInfo as any, historyRecords, isCompletedTask })
const { processNodes, processFlows, completedNodeIds, currentNodeId, bpmnXml, parseBpmnXml, parseBpmnXmlAndGetFormId, parseBpmnXmlAndGetPreviousFormIds } = bpmnParser

const taskForm = useTaskForm({ subTableBindings, isMiSubTaskMode, isCompletedTask, effectiveTaskId, taskFormDTO: taskFormDTO as any })
const { formFields, formTabs, formData, currentFormName, formReadOnly, formLabelWidth, savingTaskForm, saveCurrentTaskForm, scheduleSubTableAutosave, getCurrentFormFieldKeys, clearAutosaveTimer: clearFormAutosaveTimer } = taskForm

// Task 17.4: Return_To_Requester state
const isReturnToRequester = ref(false)

function isCompletedTaskData(taskData: any): boolean {
  return taskData?.endTime != null ||
    taskData?.completedTime != null ||
    taskData?.completed === true ||
    String(taskData?.status || '').toUpperCase() === 'COMPLETED'
}

function hasCompletedSnapshotRoute(): boolean {
  return typeof route.query.snapshotTime === 'string' ||
    typeof route.query.snapshotTaskId === 'string'
}

const completedHistorySnapshotTime = computed(() => (
  isCompletedTask.value && typeof route.query.snapshotTime === 'string'
    ? route.query.snapshotTime
    : ''
))

const completedHistoryTaskId = computed(() => (
  isCompletedTask.value && typeof route.query.snapshotTaskId === 'string'
    ? route.query.snapshotTaskId
    : taskId
))

function isWithinCompletedSnapshot(itemTime?: string | null): boolean {
  if (!isCompletedTask.value || !completedHistorySnapshotTime.value) return true
  if (!itemTime) return true
  const item = dayjs(itemTime)
  const cutoff = dayjs(completedHistorySnapshotTime.value)
  if (!item.isValid() || !cutoff.isValid()) return true
  return item.valueOf() <= cutoff.valueOf()
}

function shouldKeepCompletedHistoryItem(item: TaskHistoryInfo): boolean {
  if (!isCompletedTask.value || !hasCompletedSnapshotRoute()) return true
  if (completedHistoryTaskId.value && item.taskId === completedHistoryTaskId.value) return true
  return isWithinCompletedSnapshot(item.operationTime)
}

const loadTaskDetail = async () => {
  loading.value = true
  taskError.value = null
  try {
    const res = await getTaskDetail(taskId)
    const data = res.data || res
    if (data) {
      taskInfo.value = data
      isCompletedTask.value = isCompletedTaskData(data) || hasCompletedSnapshotRoute()
      if (isCompletedTask.value) {
        formReadOnly.value = true
        currentNodeId.value = ''
      }
      if (data.variables) formData.value = data.variables
      // Load flow history first, as diagram parsing needs history records
      await loadTaskHistory()
      
      // Then load function unit content (diagram and forms)
      if (data.processDefinitionKey) {
        await loadFunctionUnitContent(data.processDefinitionKey)
      }

      // Task 17: Load Process Form and Task Form data
      await loadProcessAndTaskFormData(data)

      if (isMiSubTask(data)) {
        isMiSubTaskMode.value = true
        isolateMiSubTaskData(data)
        const formKeys = getCurrentFormFieldKeys()
        miFilled.value = formKeys.some(key => {
          const val = formData.value[key]
          return val != null && val !== '' && val !== false
        })
      }
      if (isCompletedTask.value) {
        applyTaskAssigneeNameToMatchingSubTableRows(data)
      }
    }
  } catch (error: any) {
    console.error('Failed to load task detail:', error)
    // Show different error messages based on error status code
    const status = error.response?.status
    if (status === 404) {
      taskError.value = t('task.notFound')
    } else if (status === 403) {
      // Completed tasks should still be able to render workflow diagram for process participants.
      // Fallback to process detail (read-only) when task-level permission is denied.
      if (fallbackProcessInstanceId.value) {
        try {
          const pr = await processApi.getProcessDetail(fallbackProcessInstanceId.value)
          const p = (pr as any).data || pr
          if (p) {
            taskInfo.value = {
              taskId,
              id: taskId,
              taskName: String(route.query.snapshotTaskName || ''),
              processInstanceId: p.id,
              processDefinitionKey: p.processDefinitionKey || (route.query.processDefinitionKey as any),
              variables: p.variables || {}
            } as any
            isCompletedTask.value = true
            formReadOnly.value = true
            currentNodeId.value = ''
            if (p.variables) formData.value = p.variables
            // diagram needs history records + bpmn xml
            await loadTaskHistory()
            const key = (taskInfo.value as any).processDefinitionKey
            if (key) {
              await loadFunctionUnitContent(String(key))
            }
            await loadProcessAndTaskFormData({ ...(taskInfo.value as any), processInstanceId: p.id, id: taskId })
            loading.value = false
            return
          }
        } catch (e) {
          console.warn('[detail] Fallback process detail failed:', e)
        }
      }
      taskError.value = t('task.noPermission')
    } else {
      taskError.value = t('task.serverError')
    }
    ElMessage.error(taskError.value)
  } finally {
    loading.value = false
  }
}

const taskActions = useTaskActions({
  taskId,
  taskInfo: taskInfo as any,
  subTableBindings,
  formData,
  submitting,
  approveDialogVisible,
  approveDialogTitle,
  currentApproveAction,
  approveForm,
  actionDialogVisible,
  actionDialogTitle,
  currentAction,
  actionForm,
  userOptions,
  userSearchLoading,
  loadTaskDetail
})
const {
  validateSubTableAssigneesForComplete,
  searchUsers,
  onActionDialogOpened,
  handleApprove,
  handleReject,
  handleDelegate,
  handleTransfer,
  handleUrge,
  submitApprove,
  submitAction
} = taskActions

const customActions = useCustomActions({
  taskInfo: taskInfo as any,
  subTableBindings,
  formData,
  submitting,
  saveCurrentTaskForm,
  validateSubTableAssigneesForComplete,
  approveDialogVisible,
  approveDialogTitle,
  currentApproveAction,
  approveForm,
  loadTaskDetail
})
const {
  n8nActionDialogVisible,
  n8nActionDefinition,
  n8nInitialData,
  formPopupVisible,
  formPopupTitle,
  formPopupFields,
  formPopupTabs,
  formPopupData,
  formPopupReadOnly,
  formPopupWidth,
  formPopupLabelWidth,
  currentFormPopupAction: currentFormPopupActionRef,
  handleCustomAction,
  handleN8nActionExecuted,
  openFormPopup,
  submitFormPopup
} = customActions


const loadTaskHistory = async () => {
  historyError.value = null
  try {
    const res = await getTaskHistory(taskId)
    const data = res.data || res
    if (data && Array.isArray(data)) {
      const visibleHistory = data.filter(shouldKeepCompletedHistoryItem)
      // Convert to HistoryRecord format (keep gateway records for diagram status determination)
      historyRecords.value = visibleHistory.map((item: TaskHistoryInfo, index: number) => ({
        id: `history_${index}`,
        nodeId: item.activityId || `node_${index}`,
        nodeName: item.activityName || t('task.unknownNode'),
        status: getHistoryStatus(item.operationType),
        action: getHistoryAction(item.operationType),
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

// Load function unit content
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
    
    // Parse process diagram
    if (content.processes?.length > 0) {
      // First get the current node formId and formName
      currentFormInfo = parseBpmnXmlAndGetFormId(content.processes[0].data)
      bpmnXml.value = content.processes[0].data
      parseBpmnXml(content.processes[0].data)
    }
    
    // Parse forms - select the correct form based on the current node formId
    if (content.forms?.length > 0) {
      let selectedForm = content.forms[0] // Default to first
      
      // Prefer matching formId to sourceId (original form ID)
      if (currentFormInfo.formId) {
        const matchedForm = content.forms.find((f: any) => 
          String(f.sourceId) === currentFormInfo.formId
        )
        if (matchedForm) {
          selectedForm = matchedForm
          console.log('Matched form by sourceId:', currentFormInfo.formId, '->', selectedForm.name)
        } else {
          // If sourceId match fails, try matching by formName
          if (currentFormInfo.formName) {
            const matchedByName = content.forms.find((f: any) => f.name === currentFormInfo.formName)
            if (matchedByName) {
              selectedForm = matchedByName
              console.log('Matched form by name:', currentFormInfo.formName)
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
      console.log('[Form] selected form:', selectedForm.name, 'sourceId:', selectedForm.sourceId, 'formId from BPMN:', currentFormInfo.formId, 'readOnly:', currentFormInfo.readOnly)

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
        } catch (e) { console.warn('[task] Failed to load lookup configs:', e) }
      }

      // Parse relationViews from configJson BEFORE parseFormConfig so lookup view fields are available
      try {
        const cfg = typeof selectedForm.data === 'string' ? JSON.parse(selectedForm.data) : (selectedForm.data || {})
        relationViewConfigs.value = cfg.relationViews || {}
      } catch { relationViewConfigs.value = {} }

      parseFormConfig(selectedForm.data)
      
      // If BPMN explicitly marks readOnly, override the form config value
      if (currentFormInfo.readOnly) {
        formReadOnly.value = true
      }
      
      // Parse subForms from configJson
      let subForms: Record<string, any> = {}
      let formConfigForSubTables: Record<string, any> = {}
      try {
        const cfg = typeof selectedForm.data === 'string' ? JSON.parse(selectedForm.data) : (selectedForm.data || {})
        formConfigForSubTables = cfg
        subForms = cfg.subForms || {}
      } catch {}

      // Load sub-table bindings for this form (SUB and RELATED, not PRIMARY)
      const bindings: typeof subTableBindings.value = []
      const tableBindings: any[] = selectedForm.tableBindings || []
      console.log('[SubTable] selectedForm:', selectedForm.name, 'tableBindings:', JSON.stringify(tableBindings))
      for (const b of tableBindings) {
        if (b.bindingType === 'PRIMARY') continue
        const columns = deriveColumnsFromBinding(b, subForms, formConfigForSubTables)
        const subFormDesign = resolveSubFormDesign(b, subForms)
        bindings.push({
          bindingId: b.bindingId,
          tableId: b.tableId ?? null,
          bindingType: b.bindingType,
          bindingMode: b.bindingMode,
          foreignKeyField: b.foreignKeyField,
          tableName: b.tableDisplayName || b.tableName,
          physicalTableName: b.tableName,
          tableType: b.tableType,
          tableDescription: b.tableDescription,
          columns,
          formFields: subFormDesign.formFields,
          formOptions: subFormDesign.formOptions,
          data: []
        })
      }
      console.log('[SubTable] bindings to render:', bindings.length, bindings.map(b => b.tableName))
      // Note: JSON serialization converts keys to string; search by both number and string
      console.log('[SubTable] formData.value keys:', Object.keys(formData.value))
      console.log('[SubTable] formData.value.__subTables__:', JSON.stringify(formData.value.__subTables__))
      console.log('[SubTable] bindings bindingIds:', bindings.map(b => b.bindingId))
      const savedSubTables = formData.value.__subTables__
      if (savedSubTables && typeof savedSubTables === 'object') {
        bindings.forEach(binding => {
          const saved = getSavedSubTableRows(savedSubTables, binding)
          console.log('[SubTable] binding', binding.bindingId, '-> saved:', JSON.stringify(saved))
          if (saved) {
            binding.data = cloneSubTableRows(saved)
          }
        })
      } else {
        console.warn('[SubTable] no __subTables__ found in formData.value')
      }
      // When subForms have no rule, columns are empty causing no columns/assignee inference; infer columns from loaded row data
      bindings.forEach(binding => {
        if ((!binding.columns || binding.columns.length === 0) && binding.data?.length) {
          const row0 = binding.data[0]
          if (row0 && typeof row0 === 'object') {
            binding.columns = Object.keys(row0).map(k => ({
              field: k,
              label: k,
              type: 'text' as const
            }))
          }
        }
      })
      subTableBindings.value = bindings
      // Collect all distinct forms bound to nodes before the current one (read-only display)
      // Only consider when the current node successfully matched its own form
      if (content.processes?.length > 0 && (currentFormInfo.formId || currentFormInfo.formName)) {
        const prevFormIds = parseBpmnXmlAndGetPreviousFormIds(content.processes[0].data)
        const collectedPrevForms: PreviousFormEntry[] = []

        for (const info of prevFormIds) {
          // Skip forms identical to the current one
          let prevForm: any = null
          if (info.formId) {
            if (info.formId === String(selectedForm.sourceId)) continue
            prevForm = content.forms.find((f: any) => String(f.sourceId) === info.formId)
          }
          if (!prevForm && info.formName) {
            if (info.formName === selectedForm.name) continue
            prevForm = content.forms.find((f: any) => f.name === info.formName)
          }
          // fallback: match form by BPMN node name
          if (!prevForm && (info as any).taskName) {
            if ((info as any).taskName === selectedForm.name) continue
            prevForm = content.forms.find((f: any) => f.name === (info as any).taskName)
          }
          if (!prevForm || prevForm.id === selectedForm.id) continue
          // Deduplicate (show each form only once)
          if (collectedPrevForms.some(e => e.formId === String(prevForm.id))) continue

          // Parse form fields
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

          // Parse sub-table bindings
          let prevSubForms: Record<string, any> = {}
          let prevConfigForSubTables: Record<string, any> = {}
          try {
            const cfg = typeof prevForm.data === 'string' ? JSON.parse(prevForm.data) : (prevForm.data || {})
            prevConfigForSubTables = cfg
            prevSubForms = cfg.subForms || {}
          } catch {}
          const prevBindings: PreviousFormEntry['subTableBindings'] = []
          for (const b of (prevForm.tableBindings || [])) {
            if (b.bindingType === 'PRIMARY') continue
            const cols = deriveColumnsFromBinding(b, prevSubForms, prevConfigForSubTables)
            const subFormDesign = resolveSubFormDesign(b, prevSubForms)
            const binding = {
              bindingId: b.bindingId, tableId: b.tableId ?? null, bindingType: b.bindingType, bindingMode: b.bindingMode,
              foreignKeyField: b.foreignKeyField, tableName: b.tableDisplayName || b.tableName, physicalTableName: b.tableName,
              tableType: b.tableType, tableDescription: b.tableDescription, columns: cols,
              formFields: subFormDesign.formFields,
              formOptions: subFormDesign.formOptions,
              data: [] as any[]
            }
            if (savedSubTables) {
              const saved = getSavedSubTableRows(savedSubTables, binding)
              if (saved) binding.data = cloneSubTableRows(saved)
            }
            prevBindings.push(binding)
          }

          collectedPrevForms.push({
            formId: String(prevForm.id),
            formName: prevForm.name,
            labelWidth: formLabelWidth.value,
            fields: parsedFields,
            tabs: parsedTabs,
            subTableBindings: prevBindings
          })
        }

        previousForms.value = collectedPrevForms
        hydrateCurrentSubTablesFromPreviousForms()
      } else {
        previousForms.value = []
      }

      // Build node-to-form map for diagram click interaction
      try {
        const newMap = new Map<string, NodeFormInfo>()
        const bpmnData = content.processes[0]?.data
        if (bpmnData) {
          const parser = new DOMParser()
          const doc = parser.parseFromString(bpmnData, 'text/xml')
          const allElements = doc.getElementsByTagName('*')
          for (let i = 0; i < allElements.length; i++) {
            const el = allElements[i]
            const localName = el.localName || el.nodeName.split(':').pop()
            if (localName !== 'userTask' && localName !== 'subProcess' && localName !== 'serviceTask') continue
            const nodeId = el.getAttribute('id') || ''
            if (!nodeId) continue
            // Extract formId/formName from node properties
            let formId: string | null = null
            let formName: string | null = null
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
            // Try to match a form from content.forms
            let matchedForm: any = null
            if (formId) {
              matchedForm = content.forms.find((f: any) => String(f.sourceId) === formId)
            }
            if (!matchedForm && formName) {
              matchedForm = content.forms.find((f: any) => f.name === formName)
            }
            if (!matchedForm) continue

            // Parse form fields
            const nodeFields: FormField[] = []
            const nodeTabs: FormTab[] = []
            const nodeBindings: PreviousFormEntry['subTableBindings'] = []
            try {
              const cfg = typeof matchedForm.data === 'string' ? JSON.parse(matchedForm.data) : (matchedForm.data || {})
              const rules = cfg.rule && Array.isArray(cfg.rule) ? cfg.rule : (Array.isArray(cfg) ? cfg : null)
              if (rules) {
                const tabsRule = rules.find((r: any) => r.type === 'el-tabs')
                if (tabsRule?.children) {
                  for (const tabPane of tabsRule.children) {
                    if (tabPane.type === 'el-tab-pane' && tabPane.props) {
                      const tabFields: FormField[] = []
                      if (tabPane.children) tabFields.push(...extractFieldsRecursive(tabPane.children))
                      nodeTabs.push({ name: tabPane.props.name || `tab_${nodeTabs.length}`, label: tabPane.props.label || `Tab ${nodeTabs.length + 1}`, fields: tabFields })
                    }
                  }
                } else {
                  nodeFields.push(...extractFieldsRecursive(rules))
                }
              }
              // Parse sub-table bindings
              let subForms: Record<string, any> = {}
              let configForSubTables: Record<string, any> = {}
              try {
                configForSubTables = cfg
                subForms = cfg.subForms || {}
              } catch {}
              for (const b of (matchedForm.tableBindings || [])) {
                if (b.bindingType === 'PRIMARY') continue
                const cols = deriveColumnsFromBinding(b, subForms, configForSubTables)
                const savedSubTables = formData.value.__subTables__
                const binding = {
                  bindingId: b.bindingId, bindingType: b.bindingType, bindingMode: b.bindingMode,
                  foreignKeyField: b.foreignKeyField, tableName: b.tableDisplayName || b.tableName, physicalTableName: b.tableName,
                  tableType: b.tableType, tableDescription: b.tableDescription, columns: cols, data: [] as any[]
                }
                if (savedSubTables) {
                  const saved = getSavedSubTableRows(savedSubTables, binding)
                  if (saved) binding.data = cloneSubTableRows(saved)
                }
                nodeBindings.push(binding)
              }
            } catch {}

            const nodeName = el.getAttribute('name') || nodeId
            const currentDefKey = (taskInfo.value as any).taskDefinitionKey || ''
            const isCurrentTask = !isCompletedTask.value && (nodeId === currentDefKey || nodeName === taskInfo.value.taskName)

            newMap.set(nodeId, {
              formName: matchedForm.name || nodeName,
              isCurrentTask,
              fields: nodeFields,
              tabs: nodeTabs,
              values: { ...formData.value },
              subTableBindings: nodeBindings
            })
          }
        }
        nodeFormMap.value = newMap
      } catch (e) {
        console.warn('[NodeFormMap] Failed to build:', e)
      }
    }
  } catch (error: any) {
    console.error('Failed to load function unit content:', error)
    // 403 error indicates function unit is disabled or no permission
    if (error.response?.status === 403) {
      processError.value = t('task.noPermission')
    } else {
      processError.value = t('task.processLoadFailed')
    }
  }
}

// Task 17: Load Process Form and Task Form data
const loadProcessAndTaskFormData = async (taskData: any) => {
  const processInstanceId = taskData.processInstanceId
  const currentTaskId = taskData.id || taskId
  const isCompleted = isCompletedTaskData(taskData) || hasCompletedSnapshotRoute()
  const miSubTask = isMiSubTask(taskData)

  // 17.1: Load Process Form data
  if (processInstanceId) {
    try {
      const pfRes = await getProcessFormData(processInstanceId)
      const pfData = (pfRes as any).data || pfRes
      if (pfData) {
        processFormData.value = pfData
        processFormValues.value = pfData.fieldValues || {}

        // 17.4: Return_To_Requester state detection
        if (pfData.processState === 'Return_To_Requester' && pfData.editable) {
          isReturnToRequester.value = true
          processFormEditable.value = true
          processFormCollapse.value = ['processForm'] // Auto-expand
        }

        // Parse Process Form layout
        if (pfData.configJson) {
          parseProcessFormConfig(pfData.configJson)
        }
      }
    } catch (e) {
      console.warn('[detail] Failed to load process form data:', e)
    }
  }

  // 17.2 / 17.3: Load Task Form data
  if (currentTaskId) {
    if (isCompleted) {
      // 17.3: Completed task — load snapshot
      isCompletedTask.value = true
      formReadOnly.value = true
      try {
        const ctRes = await getCompletedTaskFormData(currentTaskId)
        const ctData = (ctRes as any).data || ctRes
        if (ctData) {
          completedFormData.value = ctData
          applyCompletedSnapshotToForm(ctData)
        }
      } catch (e) {
        console.warn('[detail] Failed to load completed task form data:', e)
      }
    } else {
      // 17.2: Active task — load Task Form
      try {
        const tfRes = await fetchTaskFormData(currentTaskId)
        const tfData = (tfRes as any).data || tfRes
        if (tfData) {
          taskFormDTO.value = tfData
          // FormStageBinding readOnly flag takes highest priority
          if (tfData.formReadOnly === true) {
            formReadOnly.value = true
          }
          if (tfData.formName) {
            currentFormName.value = tfData.formName
          }
          if (tfData.configJson) {
            parseFormConfig(tfData.configJson as any)
          }
          // If Task Form config exists, use fieldPermissions to control field editability
          if (tfData.configJson && tfData.fieldPermissions) {
            // When all field permissions are READONLY, force entire form to read-only.
            // Previously showed "Read Only" label but did not actually disable input.
            const perms = Object.values(tfData.fieldPermissions || {})
            if (perms.length > 0 && perms.every((p: any) => String(p).toUpperCase() === 'READONLY')) {
              formReadOnly.value = true
            }
            // Multi-instance sub-tasks do not directly merge process variable field values to avoid cross-contamination between sub-tasks.
            // Row-level data is merged later in loadTaskDetail by _currentItem.rowId.
            if (miSubTask) {
              return
            }
            // Task Form field values come from process variables
            if (tfData.fieldValues) {
              formData.value = { ...formData.value, ...tfData.fieldValues }
            }
          }
        }
      } catch (e) {
        console.warn('[detail] Failed to load task form data:', e)
      }
    }
  }
}

// Parse Process Form config into FormRenderer fields
const parseProcessFormConfig = (configJson: Record<string, unknown>) => {
  try {
    const config = configJson
    const rules = (config as any).rule && Array.isArray((config as any).rule)
      ? (config as any).rule
      : (Array.isArray(config) ? config : null)
    if (!rules) return

    const tabsRule = rules.find((r: any) => r.type === 'el-tabs')
    if (tabsRule?.children && Array.isArray(tabsRule.children)) {
      const tabs: FormTab[] = []
      for (const tabPane of tabsRule.children) {
        if (tabPane.type === 'el-tab-pane' && tabPane.props) {
          const tabFields: FormField[] = []
          if (tabPane.children) tabFields.push(...extractFieldsRecursive(tabPane.children))
          tabs.push({
            name: tabPane.props.name || `tab_${tabs.length}`,
            label: tabPane.props.label || `Tab ${tabs.length + 1}`,
            fields: tabFields,
          })
        }
      }
      processFormTabs.value = tabs
      processFormFields.value = []
    } else {
      processFormTabs.value = []
      processFormFields.value = extractFieldsRecursive(rules)
    }
  } catch (e) {
    console.error('[detail] Failed to parse process form config:', e)
  }
}

// Task 17.4: Submit Process Form update (Return_To_Requester state)
const handleProcessFormSubmit = async () => {
  if (!taskInfo.value.processInstanceId) return
  submitting.value = true
  try {
    await submitProcessFormUpdate(taskInfo.value.processInstanceId, processFormValues.value)
    ElMessage.success(t('task.operationSuccess'))
    // Refresh page data
    await loadTaskDetail()
  } catch (e: any) {
    if (e.response?.status === 403) {
      ElMessage.warning(t('process.notInReturnState'))
    } else {
      ElMessage.error(t('task.operationFailed'))
    }
  } finally {
    submitting.value = false
  }
}

// ===== BPMN parsing functions moved to useBpmnParser composable =====
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
        // Tab layout
        const tabs: FormTab[] = []
        
        for (const tabPane of tabsRule.children) {
          if (tabPane.type === 'el-tab-pane' && tabPane.props) {
            const tabName = tabPane.props.name || `tab_${tabs.length}`
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
    // Check if form is read-only
    formReadOnly.value = config.formReadOnly === true || config.formReadOnly === 'true'
  } catch (error) {
    console.error('Failed to parse form config:', error)
  }
}

// Derive display columns for a sub-table binding based on table metadata
const deriveColumnsFromBinding = (binding: any, subForms?: Record<string, any>, config?: Record<string, any>): Array<{ field: string; label: string; type?: string; required?: boolean; options?: Array<{ label: string; value: any }>; props?: Record<string, any> }> => {
  // Consistent with process/start: prefer subFormConfig on binding, then configJson.subForms (supports string/number key)
  const subFormRule =
    binding.subFormConfig?.rule ||
    subForms?.[binding.bindingId]?.rule ||
    subForms?.[String(binding.bindingId)]?.rule
  if (subFormRule && Array.isArray(subFormRule) && subFormRule.length > 0) {
    const subFormColumns = subFormRule.map((r: any) => {
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
        // fallback: pass through unknown types directly so SubTableAddDialog can handle them
        type = r.type as any
      }

      console.log(`[deriveColumns] field=${r.field} r.type=${r.type} → type=${type}`)

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
      // 'tree' and 'elTreeSelect' store tree data in props.data — map to treeData
      if (rProps.data !== undefined) passProps.treeData = rProps.data
      // pass through nodeKey and showCheckbox for tree type
      if (rProps.nodeKey !== undefined) passProps.nodeKey = rProps.nodeKey
      if (rProps.showCheckbox !== undefined) passProps.showCheckbox = rProps.showCheckbox
      if (rProps.props !== undefined) passProps.labelProps = rProps.props
      // cascader: map props.props to cascaderProps if not already set
      if (type === 'cascader' && rProps.props && !passProps.cascaderProps) passProps.cascaderProps = rProps.props

      if (type === 'lookup') {
        let lookupCfg: any = {}
        try {
          const raw = rProps.lookupConfig
          lookupCfg = typeof raw === 'string' ? JSON.parse(raw || '{}') : (raw || {})
        } catch { lookupCfg = {} }
        const dbCfg = lookupDbConfigs.value[r.field]
        const relationView = lookupCfg.bindingId ? relationViewConfigs.value[lookupCfg.bindingId] : undefined
        passProps.lookupConfig = rProps.lookupConfig || '{}'
        passProps.tableId = lookupCfg.tableId || dbCfg?.tableId || 0
        passProps.searchFields = lookupCfg.searchFields || dbCfg?.searchFields || []
        passProps.displayField = lookupCfg.displayFields?.[0] || dbCfg?.displayField || ''
        passProps.displayFields = lookupCfg.displayFields || []
        passProps.selectedDisplayField = lookupCfg.selectedDisplayField || lookupCfg.displayField || ''
        passProps.filterConditions = Array.isArray(lookupCfg.filterConditions) ? lookupCfg.filterConditions : []
        passProps.viewFields = lookupCfg.showBackfillView === false
          ? []
          : (relationView?.viewFields || dbCfg?.viewFields || [])
        passProps.showBackfillView = lookupCfg.showBackfillView !== false
      }

      // Sync options into props.options so SubTableAddDialog can read from col.props?.options
      if (options) passProps.options = options

      const required = r.validate?.some((v: any) => v.required) || false
      // form-create uses `disabled` to mark a field as read-only
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
    const listColumns =
      config?.subListViews?.[binding.bindingId]?.columns ||
      config?.subListViews?.[String(binding.bindingId)]?.columns
    if (Array.isArray(listColumns) && listColumns.length > 0) {
      const ruleByField = new Map(subFormRule.map((ruleItem: any) => [ruleItem?.field, ruleItem]))
      const subFormColumnByField = new Map(subFormColumns.map(col => [col.field, col]))
      const assigneeField = resolveAssigneeFieldForBinding(
        subFormColumns as Array<{ field?: string }>,
        binding.tableDisplayName || binding.tableName
      )
      return listColumns.map((column: any) => {
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
              boundSubTableName: column.boundSubTableName
            }
          }
        }
        if (column.columnType === 'lookup') {
          const label = column.columnLabel || column.comment || 'Lookup'
          const field = isSyntheticLookupField(column.fieldName) && isAssigneeLikeLabel(label) && assigneeField
            ? assigneeField
            : (column.fieldName || `lookup:${binding.bindingId}`)
          return {
            field,
            label,
            type: 'lookup',
            minWidth: 260,
            props: buildLookupColumnProps(column.lookupConfig || '{}')
          }
        }

        const fieldRule = ruleByField.get(column.fieldName)
        const baseColumn = subFormColumnByField.get(column.fieldName)
        if (fieldRule?.type === 'lookup' || fieldRule?.props?.lookupConfig || baseColumn?.type === 'lookup') {
          return {
            ...(baseColumn || {}),
            field: column.fieldName,
            label: column.comment || column.columnLabel || baseColumn?.label || fieldRule?.title || column.fieldName,
            type: 'lookup',
            minWidth: column.minWidth || baseColumn?.minWidth || 260,
            placeholder: fieldRule?.props?.placeholder || baseColumn?.placeholder,
            props: buildLookupColumnProps(fieldRule?.props?.lookupConfig || baseColumn?.props?.lookupConfig || '{}')
          }
        }

        return {
          ...(baseColumn || {}),
          field: column.fieldName,
          label: column.comment || column.columnLabel || baseColumn?.label || column.fieldName,
          minWidth: column.minWidth || baseColumn?.minWidth || 100
        }
      })
    }
    return subFormColumns
  }
  return []
}

function isSyntheticLookupField(fieldName?: string): boolean {
  return !fieldName || String(fieldName).startsWith('lookup:')
}

function isAssigneeLikeLabel(label?: string): boolean {
  const normalized = String(label || '').trim().toLowerCase()
  return /assignee|处理人|負責人|经办人|經辦人/.test(normalized)
}

function buildLookupColumnProps(rawLookupConfig: unknown): Record<string, any> {
  let lookupCfg: any = {}
  try {
    lookupCfg = typeof rawLookupConfig === 'string' ? JSON.parse(rawLookupConfig || '{}') : (rawLookupConfig || {})
  } catch { lookupCfg = {} }
  const relationView = lookupCfg.bindingId ? relationViewConfigs.value[lookupCfg.bindingId] : undefined
  return {
    lookupConfig: typeof rawLookupConfig === 'string' ? rawLookupConfig : JSON.stringify(lookupCfg || {}),
    tableId: lookupCfg.tableId || 0,
    searchFields: lookupCfg.searchFields || [],
    displayField: lookupCfg.displayFields?.[0] || '',
    displayFields: lookupCfg.displayFields || [],
    selectedDisplayField: lookupCfg.selectedDisplayField || lookupCfg.displayField || '',
    filterConditions: Array.isArray(lookupCfg.filterConditions) ? lookupCfg.filterConditions : [],
    viewFields: lookupCfg.showBackfillView === false ? [] : (relationView?.viewFields || []),
    showBackfillView: lookupCfg.showBackfillView !== false
  }
}

// form-create runtime-only nodes. Layout containers must still be traversed so
// task detail keeps the same visible fields and sub-table positions as preview.
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
    console.log(`Field ${rule.field} options:`, JSON.stringify(field.options))
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
  if (rule.type === 'userSelect' || rule.type === 'user') {
    field.type = 'user'
  }
  console.log(`convertFormCreateRule: field=${rule.field}, type=${field.type}, hasOptions=${!!field.options}`)
  return field
}

// display helpers moved to useTaskDisplay composable
// action handlers moved to useTaskActions composable

// custom action handlers moved to useCustomActions composable

onMounted(() => {
  loadTaskDetail()
})

onBeforeUnmount(() => {
  clearFormAutosaveTimer()
})
</script>


<style lang="scss" scoped>
.task-detail-page {
  width: 100%;
  max-width: 100%;
  margin: 0;
  box-sizing: border-box;
  
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

  .process-form-section {
    :deep(.el-collapse-item__header) {
      font-size: 16px;
      font-weight: 500;
      padding: 0 20px;
      background: #fafafa;
    }
    :deep(.el-collapse-item__content) {
      padding: 20px;
    }
  }

  .snapshot-section,
  .change-history-section {
    .section-content {
      padding: 20px;
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

<style lang="scss">
/* Transfer/Delegate dialog: prevent label wrapping, left-align */
.task-action-form .el-form-item__label {
  white-space: nowrap;
  text-align: left;
}
/* Reason Description: slightly larger label and textarea */
.task-action-form .task-action-reason-item .el-form-item__label {
  font-size: 14px;
}
.task-action-form .task-action-reason-item .el-textarea__inner {
  min-height: 100px;
  width: 100%;
  font-size: 14px;
}
</style>
