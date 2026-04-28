<template>
  <div class="task-detail-page">
    <!-- Page header -->
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="$router.back()">{{ t('common.back') }}</el-button>
      <h1>{{ taskInfo.taskName || t('task.detail') }}</h1>
      <el-tag :type="getPriorityType(taskInfo.priority)" size="small">
        {{ getPriorityLabel(taskInfo.priority) }}
      </el-tag>
      <el-tag v-if="taskInfo.isOverdue" type="danger" size="small">{{ t('task.overdue') }}</el-tag>
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

    <!-- Task loading error -->
    <div v-else-if="taskError" class="error-content">
      <el-result icon="warning" :title="taskError">
        <template #extra>
          <el-button type="primary" @click="$router.back()">{{ t('common.back') }}</el-button>
          <el-button @click="loadTaskDetail">{{ t('common.reset') }}</el-button>
        </template>
      </el-result>
    </div>

    <!-- Main content -->
    <div v-else class="content-sections">
      <!-- Section 1: Basic info -->
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

      <!-- Section 2: Process diagram -->
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
            v-else-if="bpmnXml || processNodes.length > 0"
            :nodes="processNodes"
            :flows="processFlows"
            :bpmn-xml="bpmnXml"
            :current-node-id="currentNodeId"
            :completed-node-ids="completedNodeIds"
            :selected-node-id="selectedNodeId ?? ''"
            :show-toolbar="true"
            :show-legend="true"
            @node-click="handleNodeClick"
          />
          <el-empty v-else :description="t('task.noProcessDefinition')" />
        </div>
      </div>

      <!-- Selected node form (click a node in the diagram to show its form) -->
      <div v-if="selectedNodeId && selectedNodeForm" class="section form-section">
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ selectedNodeForm.formName }}</span>
          <el-tag v-if="selectedNodeForm.isCurrentTask" type="warning" size="small">{{ t('task.currentStep') }}</el-tag>
          <el-tag v-else type="info" size="small">{{ t('task.readonly') }}</el-tag>
          <el-button size="small" @click="clearNodeSelection" style="margin-left: auto;">{{ t('common.back') }}</el-button>
        </div>
        <div class="section-content">
          <div v-if="selectedNodeForm.fields.length > 0 || selectedNodeForm.tabs.length > 0" class="form-container">
            <FormRenderer
              :fields="selectedNodeForm.fields"
              :tabs="selectedNodeForm.tabs"
              :model-value="selectedNodeForm.values"
              :label-width="formLabelWidth"
              :readonly="true"
              :subTableBindings="selectedNodeForm.subTableBindings"
            />
          </div>
          <el-empty v-else :description="t('task.noFormData')" />
        </div>
      </div>
      <!-- Node selected but no form bound -->
      <div v-else-if="selectedNodeId && !selectedNodeForm" class="section form-section">
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ selectedNodeId }}</span>
        </div>
        <div class="section-content">
          <el-empty :description="`No Form Bound`" />
          <div style="text-align: center; margin-top: 8px;">
            <el-button size="small" @click="clearNodeSelection">{{ t('common.back') }}</el-button>
          </div>
        </div>
      </div>

      <!-- Task 17.1 / 17.4: Collapsible Process Form panel -->
      <div v-if="showProcessFormPanel && processFormData" class="section process-form-section">
        <el-collapse v-model="processFormCollapse">
          <el-collapse-item :title="isReturnToRequester ? t('process.processForm') : t('process.processFormReadonly')" name="processForm">
            <div class="section-content">
              <FormRenderer
                v-if="processFormFields.length > 0 || processFormTabs.length > 0"
                :fields="processFormFields"
                :tabs="processFormTabs"
                :model-value="processFormValues"
                @update:model-value="val => processFormValues = { ...processFormValues, ...val }"
                :label-width="formLabelWidth"
                :readonly="!processFormEditable"
              />
              <el-empty v-else :description="t('task.noFormData')" />
              <div v-if="processFormEditable" class="process-form-actions" style="margin-top: 16px; text-align: right;">
                <el-button type="primary" @click="handleProcessFormSubmit" :loading="submitting">
                  {{ t('common.submit') }}
                </el-button>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <!-- Section 3: Previous node forms (read-only, displayed in order) -->
      <template v-for="prevForm in previousForms" :key="prevForm.formId">
        <div v-if="!selectedNodeId" class="section form-section">
          <div class="section-header">
            <el-icon><Document /></el-icon>
            <span>{{ prevForm.formName }}</span>
            <el-tag type="info" size="small">{{ t('task.readonly') }}</el-tag>
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
              />
            </div>
            <template v-if="previousBottomSubTableBindings(prevForm).length > 0">
              <div v-for="binding in previousBottomSubTableBindings(prevForm)" :key="binding.bindingId" class="sub-table-section">
                <SubTableField
                  :title="binding.tableName"
                  :columns="binding.columns"
                  v-model="binding.data"
                  :editable="false"
                  :show-fill-button="isMiSubTaskMode && isParticipantsBinding(binding)"
                  :fill-button-label="t('task.addParticipantInfoForm')"
                  :linked-sub-table-bindings="prevForm.subTableBindings"
                  @fillForm="(row: any) => openMiFillDialog(row)"
                  @update:linked-sub-table-data="(bindingId: number, rows: any[]) => syncPreviousLinkedSubTableRows(prevForm, bindingId, rows)"
                />
              </div>
            </template>
          </div>
        </div>
      </template>

      <!-- Section 3: Form data (hide normal task form card when previewing a selected node) -->
      <div
        v-if="!selectedNodeId && (!isMiSubTaskMode || bottomSubTableBindings.length > 0 || formFields.length > 0 || formTabs.length > 0)"
        class="section form-section"
      >
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ currentFormName || t('task.taskForm') }}</span>
        </div>
        <div class="section-content">
          <div v-if="formFields.length > 0 || formTabs.length > 0" class="form-container">
            <FormRenderer
              :fields="formFields"
              :tabs="formTabs"
              :model-value="formData"
              @update:model-value="val => formData = { ...formData, ...val }"
              :label-width="formLabelWidth"
              :readonly="formReadOnly"
              :subTableBindings="subTableBindings"
              :preview-sub-tables="isMiSubTaskMode"
              :task-id="effectiveTaskId"
              :allow-sub-table-assign="allowSubTableAssignForCurrentTask"
              @update:subTableData="syncMainSubTableRows"
            />
          </div>
          <el-empty v-else-if="!bottomSubTableBindings.length" :description="t('task.noFormData')" />

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
                :editable="!isMiSubTaskMode && !formReadOnly && binding.bindingMode === 'EDITABLE'"
                :task-id="effectiveTaskId"
                :assignee-field="resolveAssigneeFieldForBinding(binding.columns, binding.tableName)"
                :show-assign-button="allowSubTableAssignForCurrentTask && !!effectiveTaskId && !!resolveAssigneeFieldForBinding(binding.columns, binding.tableName)"
                :can-assign="allowSubTableAssignForCurrentTask && !formReadOnly && binding.bindingMode === 'EDITABLE' && !!effectiveTaskId && !!resolveAssigneeFieldForBinding(binding.columns, binding.tableName)"
                :show-fill-button="isMiSubTaskMode && !formReadOnly"
                :fill-button-label="isParticipantsBinding(binding) ? t('task.addParticipantInfoForm') : undefined"
                :linked-sub-table-bindings="subTableBindings"
                @update:model-value="(rows: any[]) => syncMainSubTableRows(binding.bindingId, rows)"
                @update:linked-sub-table-data="syncMainSubTableRows"
                @fillForm="(row: any) => openMiFillDialog(row)"
              />
            </div>
          </template>
        </div>
      </div>

      <!-- Task 17.3: Completed task snapshot comparison view -->
      <div v-if="isCompletedTask && completedFormData?.snapshot" class="section snapshot-section">
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ t('task.completedSnapshot') }}</span>
        </div>
        <div class="section-content">
          <SnapshotDiffRenderer
            :snapshot-values="completedFormData.snapshot.fieldValues || {}"
            :live-values="completedFormData.liveValues || {}"
            :fields="formFields.length > 0 ? formFields : (formTabs.flatMap(tab => tab.fields) || [])"
            :show-live-values="completedFormData.showLiveValues ?? true"
          />
        </div>
      </div>

      <!-- Task 19.2: Change history panel (title and collapse handled internally by ChangeHistoryPanel) -->
      <div v-if="taskInfo.processInstanceId" class="section change-history-section">
        <ChangeHistoryPanel :process-instance-id="taskInfo.processInstanceId" />
      </div>

      <!-- Section 4: Flow history -->
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
            collapsible
            :default-visible-count="1"
          />
          <el-empty v-else :description="t('task.noFlowHistory')" />
        </div>
      </div>

      <!-- Section 5: Action buttons (hidden for completed tasks) -->
      <div v-if="!isCompletedTask" class="section action-section">
        <div class="action-buttons">
          <div class="left-actions">
            <el-button @click="$router.back()">{{ t('task.backToList') }}</el-button>
          </div>
          <div class="right-actions">
            <el-button
              v-if="showImplicitSaveAction"
              type="primary"
              :loading="savingTaskForm"
              @click="saveCurrentTaskForm"
            >
              {{ t('common.save') }}
            </el-button>
            <!-- Show custom buttons when custom Actions are configured -->
            <template v-if="taskInfo.actions && taskInfo.actions.length > 0">
              <el-button
                v-for="action in taskInfo.actions"
                :key="action.actionId"
                :type="getButtonType(action.buttonColor)"
                @click="handleCustomAction(action)"
              >
                <el-icon v-if="action.icon"><component :is="getIconComponent(action.icon)" /></el-icon>
                {{ getActionLabel(action) }}
              </el-button>
            </template>
            <!-- Show default approval buttons when no custom Actions are configured -->
            <template v-else-if="taskInfo.actions === undefined || taskInfo.actions === null">
              <el-button type="success" @click="handleApprove">
                <el-icon><Check /></el-icon> {{ t('task.approve') }}
              </el-button>
              <el-button type="danger" @click="handleReject">
                <el-icon><Close /></el-icon> {{ t('task.reject') }}
              </el-button>
            </template>
            <!-- Transfer, delegate, urge always shown -->
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

    <!-- Approval dialog -->
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

    <!-- Delegate/Transfer dialog -->
    <el-dialog v-model="actionDialogVisible" :title="actionDialogTitle" width="500px" @opened="onActionDialogOpened" class="task-action-dialog">
      <el-form :model="actionForm" label-width="120px" label-position="left" class="task-action-form">
        <el-form-item :label="t('task.targetUser')" v-show="currentAction !== 'urge'">
          <el-select 
            v-model="actionForm.targetUserId" 
            :placeholder="t('task.selectUser')" 
            :teleported="false"
            style="width: 100%;"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="user.name + ' (' + user.username + ')'"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="currentAction === 'urge' ? t('task.urgeMessage') : t('task.reasonDescription')" class="task-action-reason-item">
          <el-input 
            v-model="actionForm.reason" 
            type="textarea" 
            :rows="5" 
            :placeholder="currentAction === 'urge' ? t('task.urgeMessagePlaceholder') : t('task.reasonPlaceholder')" 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitAction" :loading="submitting">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

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

    <!-- MI subtask fill-form dialog -->
    <el-dialog
      v-model="miFillDialogVisible"
      :title="currentFormName || t('task.taskForm')"
      width="600px"
      destroy-on-close
    >
      <div v-if="formFields.length > 0 || formTabs.length > 0" class="form-popup-container">
        <FormRenderer
          :fields="formFields"
          :tabs="formTabs"
          v-model="miFillDialogData"
          :label-width="formLabelWidth"
          :readonly="formReadOnly || miFillDialogReadOnly"
          :subTableBindings="miFillSubTableBindings"
          :preview-sub-tables="true"
          @update:subTableData="syncMiFillSubTableRows"
        />
      </div>
      <el-empty v-else :description="t('task.noFormData')" />
      <template #footer>
        <el-button @click="miFillDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-if="!formReadOnly && !miFillDialogReadOnly" type="primary" @click="saveMiFillDialog">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, markRaw, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { 
  ArrowLeft, 
  ArrowDown,
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
import { userApi, type UserOption } from '@/api/user'
import ProcessDiagram, { type ProcessNode, type ProcessFlow } from '@/components/ProcessDiagram.vue'
import ProcessHistory, { type HistoryRecord } from '@/components/ProcessHistory.vue'
import FormRenderer, { type FormField, type FormTab } from '@/components/FormRenderer.vue'
import SubTableField from '@/components/SubTableField.vue'
import N8nActionDialog from '@/components/N8nActionDialog.vue'
import type { ActionDefinition } from '@/components/N8nActionDialog.vue'
import { applyAutoFill } from '@/utils/n8nAutoFillEngine'
import {
  resolveAssigneeFieldForBinding,
  allSubTableRowsHaveAssignee
} from '@/utils/subTableAssignment'
import dayjs from 'dayjs'
import SnapshotDiffRenderer from '@/components/SnapshotDiffRenderer.vue'
import ChangeHistoryPanel from '@/components/ChangeHistoryPanel.vue'
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
import { isRejectedName } from '@/utils/statusMatcher'

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const router = useRouter()

const taskId = route.params.id as string

const agentDebugLog = (runId: string, hypothesisId: string, location: string, message: string, data: Record<string, any>) => {
  const payload = JSON.stringify({ sessionId: 'b88427', runId, hypothesisId, location, message, data, timestamp: Date.now() })
  fetch('http://127.0.0.1:7683/ingest/1fc88847-d32b-4694-9f56-a337ecc92dd3', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': 'b88427' }, body: payload }).catch(() => {
    try { navigator.sendBeacon('http://127.0.0.1:7683/ingest/1fc88847-d32b-4694-9f56-a337ecc92dd3', new Blob([payload], { type: 'application/json' })) } catch {}
  })
}

const loading = ref(true)
const submitting = ref(false)
const savingTaskForm = ref(false)
const taskInfo = ref<Partial<TaskInfo>>({})
const effectiveTaskId = computed(() => {
  const currentTaskId = (taskInfo.value as Record<string, unknown>)?.taskId
  return typeof currentTaskId === 'string' && currentTaskId.trim().length > 0 ? currentTaskId : taskId
})

// Error state
const taskError = ref<string | null>(null)
const processError = ref<string | null>(null)
const historyError = ref<string | null>(null)

// Process diagram data
const processNodes = ref<ProcessNode[]>([])
const processFlows = ref<ProcessFlow[]>([])
const currentNodeId = ref('')
const completedNodeIds = ref<string[]>([])
const bpmnXml = ref('')

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

// Form data
const formFields = ref<FormField[]>([])
const formTabs = ref<FormTab[]>([])
const formData = ref<Record<string, any>>({})
const currentFormName = ref('')
const formReadOnly = ref(false)
const formLabelWidth = ref('160px')

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
  tableType: string
  tableDescription: string
  columns: Array<{ field: string; label: string; type?: string; props?: Record<string, any> }>
  formFields?: FormField[]
  formOptions?: Record<string, any>
  data: any[]
}>>([])

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

function collectLinkBoundBindingIds(bindings: Array<{ columns?: Array<{ type?: string; props?: Record<string, any> }> }>): Set<number> {
  const ids = new Set<number>()
  bindings.forEach(binding => {
    binding.columns?.forEach(column => {
      const boundId = column.type === 'linkForm' ? column.props?.boundSubTableBindingId : null
      if (boundId != null) ids.add(Number(boundId))
    })
  })
  return ids
}

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

const linkBoundBindingIds = computed(() => collectLinkBoundBindingIds(subTableBindings.value))

const bottomSubTableBindings = computed(() =>
  subTableBindings.value.filter(b => !placedBindingIds.value.has(b.bindingId) && !linkBoundBindingIds.value.has(b.bindingId))
)

function collectPlacedBindingIds(fields: any[]): Set<number> {
  const ids = new Set<number>()
  const collect = (items: any[]) => items.forEach((f: any) => {
    if (f.type === 'subTable' && f._bindingId != null) ids.add(f._bindingId)
    if (Array.isArray(f.children)) collect(f.children)
  })
  collect(fields)
  return ids
}

function previousBottomSubTableBindings(prevForm: PreviousFormEntry) {
  const ids = collectPlacedBindingIds([
    ...(prevForm.fields || []),
    ...(prevForm.tabs || []).flatMap(tab => tab.fields || [])
  ])
  const linkBoundIds = collectLinkBoundBindingIds(prevForm.subTableBindings)
  return prevForm.subTableBindings.filter(binding => !ids.has(binding.bindingId) && !linkBoundIds.has(binding.bindingId))
}

function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

function subTableBindingMatches(
  target: { bindingId: number; tableName: string; tableId?: number | null },
  source: { bindingId: number; tableName: string; tableId?: number | null }
): boolean {
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

function syncMainSubTableRows(bindingId: number, rows: any[]) {
  const source = subTableBindings.value.find(b => b.bindingId === bindingId)
  if (!source) return

  const nextRows = Array.isArray(rows) ? rows : []
  const sync = (binding: { bindingId: number; tableName: string; tableId?: number | null; data: any[] }) => {
    if (subTableBindingMatches(binding, source)) {
      binding.data = binding === source ? nextRows : cloneSubTableRows(nextRows)
    }
  }
  subTableBindings.value.forEach(sync)
  previousForms.value.forEach(form => form.subTableBindings.forEach(sync))

  const subTables = { ...((formData.value.__subTables__ as Record<string, any>) || {}) }
  subTables[source.bindingId] = nextRows
  subTables[String(source.bindingId)] = nextRows
  if (source.tableName) {
    subTables[source.tableName] = nextRows
    subTables[normalizeSubTableName(source.tableName)] = nextRows
  }
  formData.value = { ...formData.value, __subTables__: subTables }
  scheduleSubTableAutosave()
}

function syncPreviousLinkedSubTableRows(prevForm: PreviousFormEntry, bindingId: number, rows: any[]) {
  const source = prevForm.subTableBindings.find(binding => binding.bindingId === bindingId)
  if (!source) return
  source.data = Array.isArray(rows) ? rows : []
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
let subTableAutosaveTimer: ReturnType<typeof setTimeout> | null = null

function buildSubTableSubmitPayload() {
  const subTables: Record<string, any> = { ...((formData.value.__subTables__ as Record<string, any>) || {}) }
  const subTableData: Record<string, Array<Record<string, unknown>>> = {}

  for (const binding of subTableBindings.value) {
    const rows = cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
    subTables[binding.bindingId] = rows
    subTables[String(binding.bindingId)] = rows
    subTableData[String(binding.bindingId)] = rows
    if (binding.tableName) {
      subTables[binding.tableName] = rows
      subTables[normalizeSubTableName(binding.tableName)] = rows
      subTableData[binding.tableName] = rows
    }
  }

  return {
    formData: { __subTables__: subTables },
    subTableData
  }
}

function buildCurrentTaskFormSubmitPayload() {
  const subTablePayload = buildSubTableSubmitPayload()
  return {
    formData: {
      ...formData.value,
      ...subTablePayload.formData
    },
    subTableData: subTablePayload.subTableData,
    baselineValues: taskFormDTO.value?.fieldValues || {}
  }
}

async function saveCurrentTaskForm() {
  if (formReadOnly.value || !effectiveTaskId.value) return
  savingTaskForm.value = true
  try {
    await apiSubmitTaskForm(effectiveTaskId.value, buildCurrentTaskFormSubmitPayload())
    ElMessage.success(t('task.operationSuccess'))
  } catch (error) {
    console.error('[TaskForm] save failed:', error)
    ElMessage.error(t('task.operationFailed'))
  } finally {
    savingTaskForm.value = false
  }
}

function scheduleSubTableAutosave() {
  if (formReadOnly.value || isCompletedTask.value || isMiSubTaskMode.value) return
  if (!effectiveTaskId.value) return
  if (subTableAutosaveTimer) clearTimeout(subTableAutosaveTimer)

  subTableAutosaveTimer = setTimeout(async () => {
    subTableAutosaveTimer = null
    try {
      await apiSubmitTaskForm(effectiveTaskId.value, {
        ...buildSubTableSubmitPayload(),
        baselineValues: {}
      })
    } catch (error) {
      console.error('[SubTable] autosave failed:', error)
      ElMessage.error(t('task.operationFailed'))
    }
  }, 400)
}

function getCurrentFormFieldKeys(): string[] {
  const keys = new Set<string>()
  formFields.value.forEach((f: any) => {
    if (f?.key) keys.add(String(f.key))
  })
  formTabs.value.forEach((tab: any) => {
    ;(tab?.fields || []).forEach((f: any) => {
      if (f?.key) keys.add(String(f.key))
    })
  })
  return Array.from(keys)
}

function isolateMiSubTaskData(taskData: any) {
  const currentItem = taskData?.variables?._currentItem as { rowId?: number; assigneeId?: string } | undefined
  if (currentItem?.rowId == null) return

  const myRowId = Number(currentItem.rowId)
  if (Number.isNaN(myRowId)) return

  // #region agent log
  agentDebugLog('pre-fix', 'H4,H5', 'tasks/detail.vue:759', 'before MI subtask row isolation', {
    myRowId,
    formDataSubTableKeys: formData.value.__subTables__ ? Object.keys(formData.value.__subTables__) : [],
    currentBindings: subTableBindings.value.map(binding => ({
      bindingId: binding.bindingId,
      tableId: binding.tableId,
      tableName: binding.tableName,
      rows: binding.data?.length || 0,
      rowKeySets: (binding.data || []).slice(0, 3).map((row: any) => Object.keys(row || {}))
    })),
    previousForms: previousForms.value.map(form => ({
      formName: form.formName,
      bindings: form.subTableBindings.map(binding => ({
        bindingId: binding.bindingId,
        tableId: binding.tableId,
        tableName: binding.tableName,
        rows: binding.data?.length || 0,
        rowKeySets: (binding.data || []).slice(0, 3).map((row: any) => Object.keys(row || {}))
      }))
    }))
  })
  // #endregion

  // Multi-instance data isolation: each sub-task only sees its own participant row.
  for (const binding of subTableBindings.value) {
    const rows = Array.isArray(binding.data) ? binding.data : []
    binding.data = rows.filter(
      (row: any) => Number(row?.id) === myRowId || Number(row?.rowId) === myRowId
    )
  }
  for (const prevForm of previousForms.value) {
    for (const binding of prevForm.subTableBindings) {
      const rows = Array.isArray(binding.data) ? binding.data : []
      binding.data = rows.filter(
        (row: any) => Number(row?.id) === myRowId || Number(row?.rowId) === myRowId
      )
    }
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

  formData.value = cleanedFormData
  // #region agent log
  agentDebugLog('pre-fix', 'H4,H5', 'tasks/detail.vue:825', 'after MI subtask row isolation', {
    myRowId,
    formDataKeys: Object.keys(formData.value),
    formDataSubTableKeys: formData.value.__subTables__ ? Object.keys(formData.value.__subTables__) : [],
    currentBindings: subTableBindings.value.map(binding => ({
      bindingId: binding.bindingId,
      tableId: binding.tableId,
      tableName: binding.tableName,
      rows: binding.data?.length || 0
    })),
    previousForms: previousForms.value.map(form => ({
      formName: form.formName,
      bindings: form.subTableBindings.map(binding => ({
        bindingId: binding.bindingId,
        tableId: binding.tableId,
        tableName: binding.tableName,
        rows: binding.data?.length || 0
      }))
    }))
  })
  // #endregion
}

function openMiFillDialog(row: any) {
  miFillDialogData.value = { ...formData.value }
  miFillSubTableBindings.value = cloneSubTableBindings(subTableBindings.value)
  // #region agent log
  agentDebugLog('subform-copy-initial', 'S3,S4', 'tasks/detail.vue:826', 'open MI fill dialog data summary', {
    rowKeys: row && typeof row === 'object' ? Object.keys(row) : [],
    rowSubTableKeys: row?.__subTables__ ? Object.keys(row.__subTables__) : [],
    formDataSubTableKeys: formData.value.__subTables__ ? Object.keys(formData.value.__subTables__) : [],
    clonedBindingIds: miFillSubTableBindings.value.map(binding => binding.bindingId),
    clonedBindingRowCounts: miFillSubTableBindings.value.map(binding => ({ bindingId: binding.bindingId, rows: binding.data?.length || 0 })),
    placedBindingIds: Array.from(placedBindingIds.value),
    isMiSubTaskMode: isMiSubTaskMode.value
  })
  // #endregion
  miFillDialogReadOnly.value = false
  miFillDialogVisible.value = true
}

function syncMiFillSubTableRows(bindingId: number, rows: any[]) {
  const target = miFillSubTableBindings.value.find(binding => binding.bindingId === bindingId)
  if (!target) return
  const nextRows = Array.isArray(rows) ? rows : []
  target.data = nextRows

  const subTables = { ...((miFillDialogData.value.__subTables__ as Record<string, any>) || {}) }
  subTables[target.bindingId] = nextRows
  subTables[String(target.bindingId)] = nextRows
  if (target.tableName) {
    subTables[target.tableName] = nextRows
    subTables[normalizeSubTableName(target.tableName)] = nextRows
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
    for (const pf of previousForms.value) {
      for (const b of pf.subTableBindings) mergeIntoRows(b.data)
    }
  }

  for (const binding of miFillSubTableBindings.value) {
    const rows = cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
    subTables[binding.bindingId] = rows
    subTables[String(binding.bindingId)] = rows
    subTableData[String(binding.bindingId)] = rows
    if (binding.tableName) {
      subTables[binding.tableName] = rows
      subTables[normalizeSubTableName(binding.tableName)] = rows
      subTableData[binding.tableName] = rows
    }
  }

  const nextFormData = { ...formData.value, ...miFillDialogData.value, __subTables__: subTables }
  // #region agent log
  agentDebugLog('subform-copy-initial', 'S4,S5', 'tasks/detail.vue:884', 'save MI fill dialog payload summary', {
    miDialogSubTableKeys: miFillDialogData.value.__subTables__ ? Object.keys(miFillDialogData.value.__subTables__) : [],
    outputSubTableKeys: Object.keys(subTables),
    subTableDataKeys: Object.keys(subTableData),
    bindingRowCounts: miFillSubTableBindings.value.map(binding => ({ bindingId: binding.bindingId, rows: binding.data?.length || 0 })),
    nextFormDataKeys: Object.keys(nextFormData)
  })
  // #endregion

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

/** Validate on "Assign Participants" node only: every sub-table row must be assigned (aligns with backend Task_AssignParticipants + buildParticipantsCollection) */
function validateSubTableAssigneesForComplete(): boolean {
  const tdk = (taskInfo.value as { taskDefinitionKey?: string }).taskDefinitionKey || ''
  if (tdk !== 'Task_AssignParticipants') {
    return true
  }
  for (const b of subTableBindings.value) {
    const af = resolveAssigneeFieldForBinding(b.columns, b.tableName)
    if (!af) continue
    const rows = b.data || []
    if (!allSubTableRowsHaveAssignee(rows, af)) {
      ElMessage.warning(t('task.allParticipantsMustHaveAssignee'))
      return false
    }
  }
  return true
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
const searchUsers = async (keyword: string) => {
  userSearchLoading.value = true
  try {
    const result = await userApi.searchUsers(keyword || '')
    console.log('[detail] searchUsers result:', result, 'length:', result.length)
    // Assign new array directly to ensure reactivity is triggered
    userOptions.value = [...result]
    console.log('[detail] userOptions.value after assign:', userOptions.value.length)
  } catch (e) {
    console.error('Failed to search users:', e)
    userOptions.value = []
  } finally {
    userSearchLoading.value = false
  }
}

const onActionDialogOpened = () => {
  if (currentAction.value !== 'urge') {
    searchUsers('')
  }
}

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
const formPopupVisible = ref(false)
const formPopupTitle = ref('')
const formPopupFields = ref<FormField[]>([])
const formPopupTabs = ref<FormTab[]>([])
const formPopupData = ref<Record<string, any>>({})
const formPopupReadOnly = ref(false)
const formPopupWidth = ref('800px')
const formPopupLabelWidth = ref('160px')
const currentFormPopupAction = ref<TaskActionInfo | null>(null)

// N8N Action dialog state
const n8nActionDialogVisible = ref(false)
const n8nActionDefinition = ref<ActionDefinition>({ id: 0 })
const n8nInitialData = ref<Record<string, any> | undefined>(undefined)

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
  !formReadOnly.value && !hasConfiguredSaveAction.value
)

// Task 17.3: Completed task snapshot
const completedFormData = ref<CompletedTaskFormData | null>(null)
const isCompletedTask = ref(false)

// Task 17.4: Return_To_Requester state
const isReturnToRequester = ref(false)

const loadTaskDetail = async () => {
  loading.value = true
  taskError.value = null
  try {
    const res = await getTaskDetail(taskId)
    const data = res.data || res
    if (data) {
      taskInfo.value = data
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
        // #region agent log
        agentDebugLog('subform-copy-initial', 'S1,S3', 'tasks/detail.vue:1040', 'MI subtask detail mode summary after isolation', {
          formKeys,
          miFilled: miFilled.value,
          formDataKeys: Object.keys(formData.value),
          formDataSubTableKeys: formData.value.__subTables__ ? Object.keys(formData.value.__subTables__) : [],
          subTableBindingIds: subTableBindings.value.map(binding => binding.bindingId),
          bottomBindingIds: bottomSubTableBindings.value.map(binding => binding.bindingId),
          placedBindingIds: Array.from(placedBindingIds.value)
        })
        // #endregion
        // #region agent log
        agentDebugLog('post-main-render-fix', 'R1,R2,R3', 'tasks/detail.vue:1124', 'MI subtask render path summary', {
          currentFormName: currentFormName.value,
          fieldTypes: formFields.value.map((field: any) => ({
            type: field.type,
            label: field.label,
            bindingId: field._bindingId,
            children: Array.isArray(field.children) ? field.children.map((child: any) => ({ type: child.type, label: child.label, bindingId: child._bindingId })) : []
          })),
          tabFieldTypes: formTabs.value.map((tab: any) => ({
            label: tab.label,
            fields: (tab.fields || []).map((field: any) => ({ type: field.type, label: field.label, bindingId: field._bindingId }))
          })),
          subTableBindings: subTableBindings.value.map(binding => ({
            bindingId: binding.bindingId,
            tableId: binding.tableId,
            tableName: binding.tableName,
            bindingMode: binding.bindingMode,
            columnTypes: binding.columns.map(column => ({ field: column.field, label: column.label, type: column.type })),
            rows: binding.data.length
          })),
          placedBindingIds: Array.from(placedBindingIds.value),
          bottomBindingIds: bottomSubTableBindings.value.map(binding => binding.bindingId)
        })
        // #endregion
      }
    }
  } catch (error: any) {
    console.error('Failed to load task detail:', error)
    // Show different error messages based on error status code
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
      // Convert to HistoryRecord format (keep gateway records for diagram status determination)
      historyRecords.value = data.map((item: TaskHistoryInfo, index: number) => ({
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
      // #region agent log
      agentDebugLog('subform-copy-initial', 'S1,S2,S3', 'tasks/detail.vue:1227', 'task detail form and subtable binding summary', {
        selectedFormName: selectedForm.name,
        sourceId: selectedForm.sourceId,
        formFieldCount: formFields.value.length,
        tabCount: formTabs.value.length,
        placedBindingIds: Array.from(placedBindingIds.value),
        bindingIds: bindings.map(binding => binding.bindingId),
        bindingRowCounts: bindings.map(binding => ({
          bindingId: binding.bindingId,
          tableId: binding.tableId,
          tableName: binding.tableName,
          rows: binding.data?.length || 0,
          columns: binding.columns?.length || 0,
          linkColumns: binding.columns
            ?.filter((column: any) => column.type === 'linkForm')
            .map((column: any) => ({
              field: column.field,
              label: column.label,
              props: column.props
            })) || []
        })),
        savedSubTableKeys: savedSubTables && typeof savedSubTables === 'object' ? Object.keys(savedSubTables) : [],
        currentTaskDefinitionKey: taskInfo.value.taskDefinitionKey
      })
      // #endregion
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
              foreignKeyField: b.foreignKeyField, tableName: b.tableDisplayName || b.tableName,
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
        // #region agent log
        agentDebugLog('pre-fix', 'H3,H4,H5', 'tasks/detail.vue:1358', 'previous forms and current subtable hydration summary', {
          selectedFormName: selectedForm.name,
          currentFormInfo,
          previousFormNames: previousForms.value.map(form => form.formName),
          previousBindings: previousForms.value.map(form => ({
            formName: form.formName,
            bindings: form.subTableBindings.map(binding => ({
              bindingId: binding.bindingId,
              tableId: binding.tableId,
              tableName: binding.tableName,
              rows: binding.data?.length || 0,
              columns: binding.columns?.length || 0,
              rowKeySets: (binding.data || []).slice(0, 3).map((row: any) => Object.keys(row || {}))
            }))
          })),
          currentBindings: subTableBindings.value.map(binding => ({
            bindingId: binding.bindingId,
            tableId: binding.tableId,
            tableName: binding.tableName,
            rows: binding.data?.length || 0,
            columns: binding.columns?.length || 0
          })),
          formDataSubTableKeys: formData.value.__subTables__ ? Object.keys(formData.value.__subTables__) : []
        })
        // #endregion
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
                  foreignKeyField: b.foreignKeyField, tableName: b.tableDisplayName || b.tableName,
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
            const isCurrentTask = nodeId === currentDefKey || nodeName === taskInfo.value.taskName

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
  const isCompleted =
    taskData.endTime != null ||
    taskData.completedTime != null ||
    taskData.completed === true ||
    String(taskData.status || '').toUpperCase() === 'COMPLETED'
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

// Parse BPMN XML and get the current node formId and formName
const parseBpmnXmlAndGetFormId = (xml: string): { formId: string | null, formName: string | null, readOnly: boolean } => {
  if (!xml) return { formId: null, formName: null, readOnly: false }
  
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const currentTaskDefinitionKey = (taskInfo.value as any).taskDefinitionKey || ''
    const currentTaskName = taskInfo.value.taskName || ''
    
    console.log('[BPMN] matching task: taskDefinitionKey=', currentTaskDefinitionKey, 'taskName=', currentTaskName)
    
    // Find all userTask nodes
    const allElements = doc.getElementsByTagName('*')
    
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      
      if (localName === 'userTask') {
        const bpmnId = el.getAttribute('id') || ''
        const bpmnName = el.getAttribute('name') || ''
        
        // Prefer matching by taskDefinitionKey (BPMN element id), then by taskName
        const isMatch = (currentTaskDefinitionKey && bpmnId === currentTaskDefinitionKey)
          || (!currentTaskDefinitionKey && bpmnName === currentTaskName)
        
        console.log('[BPMN] userTask id=', bpmnId, 'name=', bpmnName, 'isMatch=', isMatch)
        
        if (isMatch) {
          // Find formId, formName, and formReadOnly properties
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

// Parse BPMN XML: return form info bound to all nodes before the current one, in topological order (deduplicated)
const parseBpmnXmlAndGetPreviousFormIds = (xml: string): Array<{ formId: string | null, formName: string | null, taskName: string | null }> => {
  if (!xml) return []
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const allElements = doc.getElementsByTagName('*')
    const currentTaskDefinitionKey = (taskInfo.value as any).taskDefinitionKey || ''
    const currentTaskName = taskInfo.value.taskName || ''

    // Collect all userTasks and sequenceFlows
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

    // Find the current node id
    let currentId = ''
    for (const [id, info] of tasks) {
      const isMatch = (currentTaskDefinitionKey && id === currentTaskDefinitionKey)
        || (!currentTaskDefinitionKey && info.name === currentTaskName)
      if (isMatch) { currentId = id; break }
    }
    if (!currentId) return []

    // Reverse BFS: find all nodes that can reach currentId (i.e. predecessor nodes), in order
    const reverseAdj = new Map<string, string[]>()
    for (const f of flows) {
      if (!reverseAdj.has(f.target)) reverseAdj.set(f.target, [])
      reverseAdj.get(f.target)!.push(f.source)
    }

    // Forward BFS from start to currentId, collecting userTasks on the path (in visit order)
    const forwardAdj = new Map<string, string[]>()
    for (const f of flows) {
      if (!forwardAdj.has(f.source)) forwardAdj.set(f.source, [])
      forwardAdj.get(f.source)!.push(f.target)
    }

    // Find startEvent
    let startId = ''
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      if ((el.localName || el.nodeName.split(':').pop()) === 'startEvent') {
        startId = el.getAttribute('id') || ''
        break
      }
    }

    // BFS from start, collecting userTasks encountered before reaching currentId
    const visited = new Set<string>()
    const queue: string[] = [startId]
    const orderedPrevTaskIds: string[] = []
    visited.add(startId)

    while (queue.length > 0) {
      const node = queue.shift()!
      if (node === currentId) break
      if (tasks.has(node) && node !== currentId) {
        orderedPrevTaskIds.push(node)
      }
      for (const next of (forwardAdj.get(node) || [])) {
        if (!visited.has(next)) {
          visited.add(next)
          queue.push(next)
        }
      }
    }

    // Return in order, deduplicated (each formId/formName appears only once)
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

// Parse BPMN XML
const parseBpmnXml = (xml: string) => {
  if (!xml) return
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xml, 'text/xml')
    const nodes: ProcessNode[] = []
    const flows: ProcessFlow[] = []
    const completed: string[] = []
    
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
    
    // Get completed node IDs from history records
    const completedHistoryIds = new Set<string>()
    const completedNodeNames = new Set<string>()
    
    // Collect all completed node IDs and names
    historyRecords.value.forEach(record => {
      if (record.nodeId && record.status === 'completed') {
        completedHistoryIds.add(record.nodeId)
      }
      if (record.nodeName && record.status === 'completed') {
        completedNodeNames.add(record.nodeName)
      }
    })
    
    // Check for approval or rejection operations
    const hasApproval = historyRecords.value.some(h => h.status === 'completed' && h.nodeName.includes('Approval'))
    const hasRejection = historyRecords.value.some(h => h.status === 'rejected')
    
    // Get current task name
    const currentTaskName = taskInfo.value.taskName || ''
    let currentNodeFound = false

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
      if ((spName && spName === currentTaskName) || spId === currentTaskName) {
        enteredSubProcesses.add(spId)
        continue
      }
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
        if (taskName === currentTaskName || historyRecords.value.some(h => h.nodeName === taskName || h.nodeId === taskId)) {
          enteredSubProcesses.add(spId)
          break
        }
      }
    }

    // Detect active multi-instance subprocesses whose child tasks are still running
    const activeMultiInstanceSubProcesses = new Set<string>()
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
      if ((spName && spName === currentTaskName) || spId === currentTaskName) {
        activeMultiInstanceSubProcesses.add(spId)
        continue
      }
      for (let i = 0; i < spChildren.length; i++) {
        const childLocal = spChildren[i].localName || spChildren[i].nodeName.split(':').pop()
        if (childLocal !== 'userTask') continue
        const taskName = spChildren[i].getAttribute('name') || ''
        const taskId = spChildren[i].getAttribute('id') || ''
        if (taskName === currentTaskName || taskId === currentTaskName ||
            historyRecords.value.some(h => h.nodeName === taskName && h.status === 'current')) {
          activeMultiInstanceSubProcesses.add(spId)
          break
        }
      }
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
    
    // Parse start events (subprocess-internal starts are pending until the subprocess is entered)
    doc.querySelectorAll('startEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `start_${index}`
      const pos = positionMap.get(id)
      const parentSpId = getParentSubProcessId(event)
      let startStatus: 'completed' | 'current' | 'pending' = 'completed'
      if (parentSpId && !enteredSubProcesses.has(parentSpId)) {
        startStatus = 'pending'
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
      
      let status: 'completed' | 'current' | 'pending' = 'pending'
      
      // Check if this is the current task
      if (name === currentTaskName || id === currentTaskName) {
        status = 'current'
        currentNodeId.value = id
        currentNodeFound = true
      } 
      // Check if completed in history records
      else if (completedHistoryIds.has(id) || completedNodeNames.has(name)) {
        status = 'completed'
        completed.push(id)
      }
      // If current node not found yet and this node appears in history, mark as completed
      else if (!currentNodeFound) {
        // Match history records by node name
        const historyMatch = historyRecords.value.find(h => h.nodeName === name)
        if (historyMatch && historyMatch.status === 'completed') {
          status = 'completed'
          completed.push(id)
        }
      }
      
      nodes.push({ id, name, type: 'task', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })

    // Parse subProcess elements
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
          if (taskName === currentTaskName || taskId === currentTaskName) {
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
      
      let status: 'completed' | 'pending' = 'pending'
      if (completedHistoryIds.has(id) || completedNodeNames.has(name)) {
        status = 'completed'
        completed.push(id)
      } else {
        // Check for completed incoming nodes (via sequenceFlow)
        const incomingSourceIds = earlyFlows.filter(f => f.targetRef === id).map(f => f.sourceRef)
        const hasCompletedSource = incomingSourceIds.some(srcId => completed.includes(srcId))
        if (hasCompletedSource) {
          status = 'completed'
          completed.push(id)
        }
      }
      
      nodes.push({ id, name, type: 'gateway', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
    })
    
    // Parse end events
    doc.querySelectorAll('endEvent').forEach((event, index) => {
      const id = event.getAttribute('id') || `end_${index}`
      const name = event.getAttribute('name') || t('task.endNode')
      const pos = positionMap.get(id)
      const parentSpId = getParentSubProcessId(event)
      
      // Check if end node should be marked as completed
      let status: 'completed' | 'current' | 'pending' | 'rejected' = 'pending'
      const isRejectedEnd = isRejectedName(name)

      // SubProcess-internal endEvents stay pending when the subProcess hasn't been entered
      if (parentSpId && !enteredSubProcesses.has(parentSpId)) {
        // keep 'pending'
      } else if (parentSpId && activeMultiInstanceSubProcesses.has(parentSpId)) {
        status = 'current'
      } else if (parentSpId && completedMultiInstanceSubProcesses.has(parentSpId)) {
        status = 'completed'
        completed.push(id)
      } else if (completedHistoryIds.has(id) || completedNodeNames.has(name)) {
        // Rejected end nodes use red, others use green
        status = isRejectedEnd ? 'rejected' : 'completed'
        completed.push(id)
      } else {
        // Match history records by node name
        const historyMatch = historyRecords.value.find(h => h.nodeName === name && h.status === 'completed')
        if (historyMatch) {
          status = isRejectedEnd ? 'rejected' : 'completed'
          completed.push(id)
        } else if (hasApproval && !currentNodeFound) {
          // If there are completed approvals and no current task, determine by end node name
          if (name.toLowerCase().includes('approved') || name.toLowerCase().includes('Approved')) {
            status = 'completed'
            completed.push(id)
          }
        } else if (hasRejection && !currentNodeFound) {
          // If there are rejection operations, mark rejected end nodes as red
          if (isRejectedEnd) {
            status = 'rejected'
            completed.push(id)
          }
        }
      }
      
      nodes.push({ id, name, type: 'end', status, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
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

      return {
        field: r.field,
        label: r.title || r.field,
        type,
        required,
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

const getHistoryAction = (operationType: string): 'approve' | 'reject' | 'transfer' | 'delegate' | 'withdraw' | 'submit' | undefined => {
  const map: Record<string, 'approve' | 'reject' | 'transfer' | 'delegate' | 'withdraw' | 'submit'> = {
    'SUBMIT': 'submit',
    'APPROVE': 'approve',
    'REJECT': 'reject',
    'TRANSFER': 'transfer',
    'DELEGATE': 'delegate',
  }
  return map[operationType]
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
  if (!validateSubTableAssigneesForComplete()) return
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
  userOptions.value = []
  actionDialogVisible.value = true
}

const handleTransfer = () => {
  currentAction.value = 'transfer'
  actionDialogTitle.value = t('task.transfer')
  actionForm.targetUserId = ''
  actionForm.reason = ''
  userOptions.value = []
  actionDialogVisible.value = true
}

const handleUrge = () => {
  currentAction.value = 'urge'
  actionDialogTitle.value = t('task.urge')
  actionForm.reason = ''
  actionDialogVisible.value = true
}

const submitApprove = async () => {
  if (currentApproveAction.value === 'APPROVE' && !validateSubTableAssigneesForComplete()) {
    return
  }
  submitting.value = true
  try {
    // Set process variables based on approval action
    const variables: Record<string, any> = {}
    
    if (currentApproveAction.value === 'APPROVE') {
      variables.approval_result = 'approved'
      variables.approved = true
    } else if (currentApproveAction.value === 'REJECT') {
      variables.approval_result = 'rejected'
      variables.approved = false
    }
    
    // Add approval comment
    if (approveForm.comment) {
      variables.approval_comment = approveForm.comment
    }
    
    // Collect current form data (e.g. additional_information in Approval Form)
    const currentFormData: Record<string, any> = {}
    for (const key of Object.keys(formData.value)) {
      // Exclude system fields and fields already in the start form; only collect current approval form fields
      if (!key.startsWith('__') && !variables[key]) {
        currentFormData[key] = formData.value[key]
      }
    }

    // Multi-instance: backend buildParticipantsCollection relies on __subTables__ (keyed by table name "participants" or bindingId)
    const mergedSub: Record<string, any> = { ...(formData.value.__subTables__ || {}) }
    for (const b of subTableBindings.value) {
      mergedSub[b.bindingId] = b.data
      mergedSub[String(b.bindingId)] = b.data
      if (b.tableName) {
        mergedSub[b.tableName] = b.data
        mergedSub[normalizeSubTableName(b.tableName)] = b.data
      }
    }
    const participantsBinding = subTableBindings.value.find(
      b => b.tableName === 'participants' || resolveAssigneeFieldForBinding(b.columns, b.tableName)
    )
    if (participantsBinding) {
      mergedSub.participants = participantsBinding.data
    }
    currentFormData.__subTables__ = mergedSub

    // Merge form data into variables to ensure backend saves do not lose data
    Object.assign(variables, currentFormData)

    console.log('[submitApprove] formData.value keys:', Object.keys(formData.value))
    console.log('[submitApprove] currentFormData:', JSON.stringify(currentFormData))
    console.log('[submitApprove] variables:', JSON.stringify(variables))

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
    if (currentAction.value === 'transfer') {
      router.push('/tasks')
    } else {
      loadTaskDetail()
    }
  } catch (error) {
    ElMessage.error(t('task.operationFailed'))
  } finally {
    submitting.value = false
  }
}

// Handle custom action buttons
const handleCustomAction = (action: TaskActionInfo) => {
  console.log('Custom action clicked:', action)
  
  // Handle different action types based on actionType
  const actionType = (action.actionType || '').trim().toUpperCase()
  switch (actionType) {
    case 'SAVE':
      saveCurrentTaskForm()
      break

    case 'APPROVE':
      if (!validateSubTableAssigneesForComplete()) return
      currentApproveAction.value = 'APPROVE'
      approveDialogTitle.value = action.actionName
      approveForm.comment = ''
      approveDialogVisible.value = true
      break

    // Designer "submit/complete" actions (e.g. "Complete Assignment", "Submit Meeting" on task nodes) follow the same completion flow as APPROVE
    case 'PROCESS_SUBMIT':
      if (!validateSubTableAssigneesForComplete()) return
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
      // Parse configJson to get formId
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
      // Parse configJson, auto-collect data based on inputMapping sourceType
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

// N8N Action execution callback
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

// Open form popup
const openFormPopup = async (action: TaskActionInfo, config: any) => {
  try {
    currentFormPopupAction.value = action
    formPopupTitle.value = config.popupTitle || action.actionName
    formPopupWidth.value = config.popupWidth || '800px'
    formPopupReadOnly.value = config.readOnly === true || config.readOnly === 'true'
    formPopupData.value = {}
    
    // Get form configuration
    if (config.formId) {
      // Get form config from function unit content
      const functionUnitId = taskInfo.value.processDefinitionKey
      if (functionUnitId) {
        try {
          const res = await processApi.getFunctionUnitContents(functionUnitId, 'FORM')
          const forms = res.data || []
          
          // Find the matching form
          const formContent = forms.find((f: any) => {
            // Try matching by source_id
            return f.sourceId === String(config.formId) || f.contentName === config.formName
          })
          
          if (formContent && formContent.contentData) {
            // Parse form configuration
            const formConfig = typeof formContent.contentData === 'string' 
              ? JSON.parse(formContent.contentData) 
              : formContent.contentData
            
            // Use same parsing logic as the main form
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

// Parse form popup config - reuse parseFormConfig logic
const parseFormPopupConfig = (configInput: any) => {
  try {
    // Ensure config is an object (may be passed as string)
    const config = typeof configInput === 'string' ? JSON.parse(configInput) : configInput
    console.log('parseFormPopupConfig: type of config =', typeof config, ', keys =', Object.keys(config || {}))
    
    const rules = config.rule && Array.isArray(config.rule) ? config.rule : (Array.isArray(config) ? config : null)
    if (rules) {
      console.log('Form popup rules count:', rules.length)
      rules.forEach((r: any, i: number) => {
        console.log(`Rule[${i}]: type=${r.type}, field=${r.field}, hasOptions=${!!r.options}, optionsCount=${r.options?.length || 0}`)
      })
      
      // Extract labelWidth config
      if (config.options?.form?.labelWidth) {
        formPopupLabelWidth.value = config.options.form.labelWidth
      }
      
      // Check for el-tabs structure
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

// Submit form popup
const submitFormPopup = async () => {
  try {
    submitting.value = true
    
    // TODO: Handle form data based on action type
    // May need to call different APIs or update process variables
    
    ElMessage.success(t('task.formSubmitSuccess'))
    formPopupVisible.value = false
    
    // Refresh task details
    await loadTaskDetail()
  } catch (error) {
    console.error('Failed to submit form popup:', error)
    ElMessage.error(t('task.formSubmitFailed'))
  } finally {
    submitting.value = false
  }
}

// Get button type (Element Plus type)
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

function getActionLabel(action: TaskActionInfo): string {
  return (action.actionType || '').trim().toUpperCase() === 'SAVE' ? t('common.save') : action.actionName
}

// Get icon component
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

onBeforeUnmount(() => {
  if (subTableAutosaveTimer) {
    clearTimeout(subTableAutosaveTimer)
    subTableAutosaveTimer = null
  }
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
