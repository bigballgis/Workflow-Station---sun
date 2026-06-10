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
            :show-legend="diagramSuppressMode === 'none'"
            :show-current-step="diagramSuppressMode !== 'full' && !isCompletedTask"
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
              ref="diagramFormRendererRef"
              :fields="selectedNodeForm.isCurrentTask ? formFields : selectedNodeForm.fields"
              :tabs="selectedNodeForm.isCurrentTask ? formTabs : selectedNodeForm.tabs"
              :model-value="selectedNodeForm.isCurrentTask ? formData : selectedNodeForm.values"
              :label-width="formLabelWidth"
              :readonly="selectedNodeForm.isCurrentTask ? formReadOnly : true"
              :primary-read-only="selectedNodeForm.isCurrentTask ? primaryReadOnly : false"
              :sub-table-bindings="selectedNodeForm.isCurrentTask ? subTableBindings : selectedNodeForm.subTableBindings"
              :linked-sub-table-bindings="selectedNodeForm.isCurrentTask ? linkableSubTableBindings : selectedNodeForm.subTableBindings"
              :native-sub-table-binding-ids="selectedNodeForm.isCurrentTask ? mainFormNativeSubTableBindingIds : selectedNodeForm.nativeSubTableBindingIds"
              :form-config="selectedNodeForm.isCurrentTask ? mainFormConfig : selectedNodeForm.formConfig"
              :form-options="selectedNodeForm.isCurrentTask ? formFormOptions : undefined"
              :preview-sub-tables="selectedNodeForm.isCurrentTask ? isMiSubTaskMode : true"
              :task-id="selectedNodeForm.isCurrentTask ? effectiveTaskId : undefined"
              :allow-sub-table-assign="selectedNodeForm.isCurrentTask ? allowSubTableAssignForCurrentTask : false"
              :suppress-link-form-initial-data="selectedNodeForm.isCurrentTask ? (isMiSubTaskMode && !isCompletedTask) : false"
              :show-link-form-dialog-footer="selectedNodeForm.isCurrentTask ? (!isCompletedTask && !formReadOnly) : false"
              view-context="assigneeTodo"
              :current-mi-row-id="currentMiRowId"
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
                v-if="processFormFields.length > 0 || processFormTabs.length > 0 || processFormSubTableBindings.length > 0"
                ref="processFormRendererRef"
                :fields="processFormFields"
                :tabs="processFormTabs"
                :model-value="processFormValues"
                :label-width="formLabelWidth"
                :readonly="!processFormEditable"
                :primary-read-only="primaryReadOnly"
                :sub-table-bindings="processFormSubTableBindings"
                :linked-sub-table-bindings="processFormSubTableBindings"
                :native-sub-table-binding-ids="processFormNativeSubTableBindingIds"
                :form-config="processFormFormConfig"
                view-context="initiatorRequest"
                :show-link-form-dialog-footer="processFormEditable"
                @update:model-value="val => processFormValues = { ...processFormValues, ...val }"
                @update:sub-table-data="(bindingId: number, rows: any[]) => {
                  const target = processFormSubTableBindings.find((b: any) => Number(b?.bindingId) === Number(bindingId))
                  if (target) target.data = rows
                }"
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
        v-if="!selectedNodeId && (!isMiSubTaskMode || formFields.length > 0 || formTabs.length > 0 || formFieldsAfterTabs.length > 0 || subTableBindings.length > 0)"
        class="section form-section"
      >
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ currentFormName || t('task.taskForm') }}</span>
        </div>
        <div class="section-content">
          <div
            v-if="formFields.length > 0 || formTabs.length > 0 || formFieldsAfterTabs.length > 0 || subTableBindings.length > 0"
            class="form-container"
          >
            <FormRenderer
              ref="taskFormRendererRef"
              :fields="formFields"
              :tabs="formTabs"
              :fields-after-tabs="formFieldsAfterTabs"
              :model-value="formData"
              :label-width="formLabelWidth"
              :readonly="formReadOnly"
              :primary-read-only="primaryReadOnly"
              :sub-table-bindings="subTableBindings"
              :linked-sub-table-bindings="linkableSubTableBindings"
              :native-sub-table-binding-ids="mainFormNativeSubTableBindingIds"
              :form-config="mainFormConfig"
              :form-options="formFormOptions"
              :preview-sub-tables="isMiSubTaskMode"
              :task-id="effectiveTaskId"
              :allow-sub-table-assign="allowSubTableAssignForCurrentTask"
              :suppress-link-form-initial-data="isMiSubTaskMode && !isCompletedTask"
              :show-link-form-dialog-footer="!isCompletedTask && !formReadOnly"
              view-context="assigneeTodo"
              :current-mi-row-id="currentMiRowId"
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
      :sub-table-bindings="formPopupSubTableBindings"
      :linked-sub-table-bindings="formPopupLinkedSubTableBindings ?? formPopupSubTableBindings"
      :native-sub-table-binding-ids="formPopupNativeSubTableBindingIds"
      :form-config="formPopupFormConfig"
      :view-context="formPopupViewContext"
      @update:form-data="val => formPopupData = { ...formPopupData, ...val }"
      @update:sub-table-data="handleFormPopupSubTableUpdate"
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
import { ref, reactive, onMounted, onBeforeUnmount, computed } from 'vue'
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
import { normalizePortalViews, collectLeafFormFieldKeys, isFormCreateRuleReadonly, isFormCreateRuleHidden, isRowRule, isColRule, getRuleChildren, getRowGutter, getColSpan, extractRowColumnFields, parseFormRulesLayout, isTabsRule, isCardRule, isCollapseRule, convertAuxiliaryLayoutField, extractTabsFromTabsRule, extractCollapsePanelsFromRule, findTabsRule, isTabPaneRule, getLayoutKey, getLayoutLabel } from '@/components/formRendererHelpers'
import { applyRuleDefaultToFormField } from '@/utils/formCreateRuleDefaults'
import { applyFormCreateValidationToFormField, isFormCreateRuleRequired } from '@/utils/formCreateValidateRules'
import SubTableField from '@/components/SubTableField.vue'
import N8nActionDialog from '@/components/N8nActionDialog.vue'
import {
  resolveAssigneeFieldForBinding,
  allSubTableRowsHaveAssignee
} from '@/utils/subTableAssignment'
import {
  cloneSubTableRows,
  mergeSubTableRowsByRowId,
  resolveSubTablePrimaryKeyFields,
  hydrateChildSubTablesFromParentsNestedRows,
  flattenNestedSubTableRowsIntoPayload,
  buildBindingIdToRelationTableIdMap,
  hydrateBindingsRowsFromVariablesBySharedRelationTableId,
  enrichChildBindingRowsFromParentsNestedSubTables,
  collectNestedSlicesForBindingFromSubTablesWalk,
  coerceSubTablesVariableToMap,
  collectSubTableSliceArraysDeep,
  scrubMiCorruptLinkChildRowsForParent,
  mergeAllSubTableSlicesFromVariables,
  mergeSubTableSlicesForRelationTableId,
  mergeAllSlicesForSharedProcessSubTableBinding,
  isSubTableRowMetaField,
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
  stripSubTableRowMetaFields,
  finalizeSharedProcessSubTableBindingRows,
  applySharedAttachmentFinalizeAndMaterialize,
  isSharedAttachmentFileBinding,
} from '@/composables/tasks/shared'
import {
  rowMatchesSubTablePrimaryKey,
  bindingMatchesMiSubTableName,
  resolveMiSubProcessScopeFromBpmn,
  findBindingForMiSubTableName,
  extractMiParticipantRowIdFromCurrentItem,
  hasConfiguredPrimaryKeyFields,
  describeSubTableBindingLabel,
  miParticipantRowIdsEqual,
  type MiSubProcessScopeConfig,
  type MiParticipantRowId,
} from '@/composables/tasks/miSubProcessScope'
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
import { unwrapUserLikeValueToDisplayString, extractUserIdFromCellValue, mergeListViewFieldColumn, inferColumnTypeFromFieldAndValue, deriveColumnsFromRelationFieldDefinitions, buildRelationTableFieldIndexFromDataTables, resolveSubTableSchemaByTableId, resolveSubListViewColumnsForBinding, defaultAttachmentListColumns, SHARED_ATTACHMENT_RELATION_TABLE_ID, type RelationFieldDef } from '@/components/subTableAddDialogHelpers'
import { clearBpmnParseCache, getCachedBpmnDocument } from '@/utils/bpmnParseCache'
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
const taskFormRendererRef = ref<InstanceType<typeof FormRenderer> | null>(null)
const diagramFormRendererRef = ref<InstanceType<typeof FormRenderer> | null>(null)
const processFormRendererRef = ref<InstanceType<typeof FormRenderer> | null>(null)

async function validateMainTaskForm(): Promise<boolean> {
  if (formReadOnly.value) return true
  const activeRenderer = selectedNodeId.value && selectedNodeForm.value?.isCurrentTask
    ? diagramFormRendererRef.value
    : taskFormRendererRef.value
  if (!activeRenderer) return true
  return activeRenderer.validate()
}
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
  /**
   * Designer configJson for this node's form — required so FormRenderer can resolve
   * Link Form targets (subListViews) and apply the per-binding portalViews merge
   * (mergeSubTablePortalViewsForRuntime). Without it the snapshot view silently
   * drops Link Form widgets and falls back to non-portalized columns
   * (#1395 / portal-design-parity).
   */
  formConfig: Record<string, any>
  /**
   * Binding ids declared directly on this form's tableBindings (non-PRIMARY,
   * pre-Link-Form-merge). FormRenderer uses this to distinguish "native"
   * sub-tables (placed widgets) from Link Form targets pulled in via
   * mergeLinkFormTargetBindingsInto.
   */
  nativeSubTableBindingIds: number[]
}
const nodeFormMap = ref<Map<string, NodeFormInfo>>(new Map())
/** Cached from last successful loadFunctionUnitContent — refreshes nodeFormMap after loadProcessAndTaskFormData merges variables. */
const lastBindingRelationTableMap = ref<Map<number, number | null>>(new Map())

function rehydrateSharedAttachmentBindings(
  bindings: typeof subTableBindings.value,
  topLevelValues: Record<string, unknown> | null | undefined,
  flattened?: Record<string, unknown> | null,
) {
  applySharedAttachmentFinalizeAndMaterialize(bindings, topLevelValues, {
    flattened: flattened ?? coerceSubTablesVariableToMap(formData.value.__subTables__),
    bindingTableById: lastBindingRelationTableMap.value,
  })
}

/** All forms in the current function unit — used to resolve sub-table schema by shared {@code tableId}. */
let cachedContentForms: any[] = []
let cachedRelationTableFieldIndex = new Map<number, RelationFieldDef[]>()
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
    portalViews?: Record<string, any> | null
    primaryKeyFields?: string[]
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
  // Per-binding portalViews loaded from form configJson.subTablePortalViews[bindingId].
  // Used as fallback for SubTable rule nodes without portalViews on rule.props, and as the
  // primary source for unplaced bindings (e.g. accessed only via Link Form).
  portalViews?: Record<string, any> | null
  /** Designer PK columns (admin-center tableBindings); sub-table row merge / identity. */
  primaryKeyFields?: string[]
  data: any[]
}>>([])

function resolveSubFormDesign(binding: any, subForms?: Record<string, any>): { formFields: FormField[]; formOptions?: Record<string, any> } {
  const design =
    binding.subFormConfig ||
    subForms?.[binding.bindingId] ||
    subForms?.[String(binding.bindingId)] ||
    {}
  let rule = Array.isArray(design.rule) ? design.rule : []
  let options = design.options
  if (rule.length === 0 && binding.tableId != null && Number.isFinite(Number(binding.tableId))) {
    const alt = resolveSubTableSchemaByTableId(Number(binding.tableId), cachedContentForms, binding.bindingId)
    if (alt) {
      const altDesign = alt.subForms[alt.bindingId] ?? alt.subForms[String(alt.bindingId)] ?? {}
      if (Array.isArray(altDesign.rule) && altDesign.rule.length > 0) {
        rule = altDesign.rule
        options = altDesign.options ?? options
      }
    }
  }
  return {
    formFields: rule.length > 0 ? extractFieldsRecursive(rule) : [],
    formOptions: options
  }
}

const linkableSubTableBindings = computed(() => [
  ...subTableBindings.value,
  ...previousForms.value.flatMap(form => form.subTableBindings)
])

function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

/** Same form / node may place multiple sub-tables backed by identical relation-table metadata — never resolve {@code __subTables__} by display/physical/tableId keys then (everyone steals the same slice). */
function bindingIdsPreferStrictSubTableLookup(
  bindings: Array<{ bindingId: number; tableId?: number | null; tableName: string; physicalTableName?: string }>,
): Set<number> {
  const ambiguous = new Set<number>()
  if (!Array.isArray(bindings) || bindings.length <= 1) return ambiguous

  const bump = (m: Map<string, Set<number>>, key: string, bid: number) => {
    if (!key) return
    const nk = normalizeSubTableName(key)
    if (!nk) return
    let s = m.get(nk)
    if (!s) {
      s = new Set()
      m.set(nk, s)
    }
    s.add(bid)
  }

  const buckets = new Map<string, Set<number>>()
  for (const b of bindings) {
    bump(buckets, b.tableName, b.bindingId)
    if (typeof b.physicalTableName === 'string' && b.physicalTableName.trim())
      bump(buckets, b.physicalTableName, b.bindingId)
    if (b.tableId != null && Number.isFinite(Number(b.tableId))) {
      bump(buckets, `__rtid:${Number(b.tableId)}`, b.bindingId)
    }
  }
  for (const s of buckets.values()) {
    if (s.size > 1) {
      for (const id of s) ambiguous.add(id)
    }
  }
  return ambiguous
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

/** After MI isolation, __subTables__ must carry all participants again; current binding only has this MI row. */
function rebuildIsolatedSubTablesPayload(): Record<string, any> {
  const subTables: Record<string, any> = {}
  const ingest = (bindings: typeof subTableBindings.value) => {
    const collision = bindingIdsPreferStrictSubTableLookup(bindings)
    for (const binding of bindings) {
      const rows = cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
      const canonical = String(binding.bindingId)
      const prev = getSavedSubTableRows(subTables, binding, collision.has(binding.bindingId))
      const merged = mergeSubTableRowsByRowId(prev, rows, binding.primaryKeyFields)
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

function patchMiParentRowsWithNestedChildSlice(
  parentRows: any[],
  myRowId: MiParticipantRowId,
  childBinding: { bindingId: number; tableName: string },
  childSlice: any[],
): any[] {
  if (!Array.isArray(parentRows)) return parentRows
  const collectionPk = miCollectionPrimaryKeyFields()
  return parentRows.map(row => {
    if (!row || typeof row !== 'object') return row
    const rec = row as Record<string, unknown>
    if (
      !rowMatchesSubTablePrimaryKey(row, myRowId, collectionPk) &&
      !miRowBelongsToCurrentParticipant(row, myRowId, {
        tableName: childBinding.tableName,
        primaryKeyFields: collectionPk,
      })
    ) {
      return row
    }
    const nest = {
      ...(rec.__subTables__ && typeof rec.__subTables__ === 'object'
        ? (rec.__subTables__ as Record<string, unknown>)
        : {})
    }
    const slice = cloneSubTableRows(childSlice)
    nest[childBinding.bindingId] = slice
    nest[String(childBinding.bindingId)] = slice
    if (childBinding.tableName) {
      nest[childBinding.tableName] = slice
      nest[normalizeSubTableName(childBinding.tableName)] = slice
    }
    return { ...rec, __subTables__: nest }
  })
}

/** MI link-form child rows must live under {@code parentRow.__subTables__[childBindingId]} for reload / diagram, not only top-level slice. */
function syncMiLinkChildRowsIntoParentNested(
  childBinding: { bindingId: number; tableName: string },
  childRows: any[]
) {
  const rid = currentMiRowId.value
  if (rid == null) return
  const myRowId = rid

  for (const parentBinding of subTableBindings.value) {
    if (parentBinding.bindingId === childBinding.bindingId) continue
    parentBinding.data = patchMiParentRowsWithNestedChildSlice(
      Array.isArray(parentBinding.data) ? parentBinding.data : [],
      myRowId,
      childBinding,
      childRows
    )
  }
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
  const strictSlices = bindingIdsPreferStrictSubTableLookup(subTableBindings.value)
  const existing = getSavedSubTableRows(subTables, source, strictSlices.has(source.bindingId))
  const merged = isMiSubTaskMode.value
    ? mergeSubTableRowsByRowId(existing, nextRows, source.primaryKeyFields)
    : nextRows
  const out = cloneSubTableRows(merged)
  subTables[source.bindingId] = out
  subTables[String(source.bindingId)] = out
  if (source.tableName) {
    subTables[source.tableName] = out
    subTables[normalizeSubTableName(source.tableName)] = out
  }

  if (isMiSubTaskMode.value) {
    syncMiLinkChildRowsIntoParentNested(
      { bindingId: source.bindingId, tableName: source.tableName },
      out
    )
    const ambiguous = bindingIdsPreferStrictSubTableLookup(subTableBindings.value)
    for (const parentBinding of subTableBindings.value) {
      if (parentBinding.bindingId === source.bindingId) continue
      const parentRows = cloneSubTableRows(
        Array.isArray(parentBinding.data) ? parentBinding.data : []
      )
      subTables[parentBinding.bindingId] = parentRows
      subTables[String(parentBinding.bindingId)] = parentRows
      if (parentBinding.tableName) {
        subTables[parentBinding.tableName] = parentRows
        subTables[normalizeSubTableName(parentBinding.tableName)] = parentRows
      }
    }
  }

  formData.value = { ...formData.value, __subTables__: subTables }
  scheduleSubTableAutosave()
}

function rehydrateSharedProcessSubTableBindings(
  savedSubTablesSource?: Record<string, unknown> | null,
) {
  const savedMap = coerceSubTablesVariableToMap(
    savedSubTablesSource ?? formData.value.__subTables__,
  )

  if (!savedMap) {
    patchFormDataSubTablesFromCurrentBindings()
    return
  }

  const flattened = JSON.parse(JSON.stringify(savedMap)) as Record<string, unknown>
  flattenNestedSubTableRowsIntoPayload(flattened)
  const rtMap = lastBindingRelationTableMap.value

  const applyTo = (bindings: typeof subTableBindings.value) => {
    for (const binding of bindings) {
      if (isMiParticipantScopedSubTableBinding(binding)) continue
      const merged = mergeAllSlicesForSharedProcessSubTableBinding(flattened, binding, rtMap)
      const existing = Array.isArray(binding.data) ? binding.data : []
      const combined =
        merged.length > 0
          ? mergeSubTableRowsByRowId(existing, merged, binding.primaryKeyFields ?? null)
          : existing
      binding.data = finalizeSharedProcessSubTableBindingRows(combined, binding)
    }
  }

  applyTo(subTableBindings.value)
  for (const pf of previousForms.value) {
    applyTo(pf.subTableBindings)
  }
  for (const info of nodeFormMap.value.values()) {
    rehydrateSharedAttachmentBindings(
      info.subTableBindings,
      formData.value as Record<string, unknown>,
      savedMap,
    )
  }
  patchFormDataSubTablesFromCurrentBindings()
}

function getSavedSubTableRows(
  savedSubTables: any,
  binding: { bindingId: number; tableName: string; physicalTableName?: string },
  forbidNameFallback = false,
): any[] | undefined {
  if (!savedSubTables || typeof savedSubTables !== 'object') return undefined

  const byBindingId =
    savedSubTables[binding.bindingId] ?? savedSubTables[String(binding.bindingId)]
  if (Array.isArray(byBindingId)) {
    return byBindingId
  }
  if (forbidNameFallback) {
    return undefined
  }
  const phys = binding.physicalTableName
  const saved =
    savedSubTables[binding.tableName] ??
    savedSubTables[normalizeSubTableName(binding.tableName)] ??
    (phys ? savedSubTables[phys] ?? savedSubTables[normalizeSubTableName(phys)] : undefined)
  return Array.isArray(saved) ? saved : undefined
}

function applySavedRowsToBindings<T extends Array<{ bindingId: number; tableName: string; data: any[] }>>(bindings: T, savedSubTables: any): T {
  if (!savedSubTables || typeof savedSubTables !== 'object') return bindings
  const ambiguous = bindingIdsPreferStrictSubTableLookup(bindings)
  bindings.forEach(binding => {
    const saved = getSavedSubTableRows(savedSubTables, binding, ambiguous.has(binding.bindingId))
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

  alignProcessSubTableBindingsBySharedTable()
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

type SubTableBindingAlignable = {
  bindingId?: number
  tableId?: number | null
  tableName: string
  data: any[]
  primaryKeyFields?: string[]
}

/** Union-find + merged snapshot assignment — shared by full-cardinality align vs diagram-only align. */
function applyUnionFindMergedRowSnapshots(all: SubTableBindingAlignable[]): void {
  if (all.length === 0) return

  const parent = all.map((_, i) => i)
  const find = (i: number): number => {
    if (parent[i] !== i) parent[i] = find(parent[i])
    return parent[i]
  }
  const union = (i: number, j: number) => {
    const ri = find(i)
    const rj = find(j)
    if (ri !== rj) parent[ri] = rj
  }

  for (let i = 0; i < all.length; i++) {
    for (let j = i + 1; j < all.length; j++) {
      const a = all[i]!
      const b = all[j]!
      const tnA = normalizeSubTableName(a.tableName)
      const tnB = normalizeSubTableName(b.tableName)
      const tidA = a.tableId != null && !Number.isNaN(Number(a.tableId)) ? Number(a.tableId) : null
      const tidB = b.tableId != null && !Number.isNaN(Number(b.tableId)) ? Number(b.tableId) : null
      const sameById = tidA != null && tidB != null && tidA === tidB
      const sameByName = tnA.length > 0 && tnA === tnB
      if (!sameById && !sameByName) continue

      const bidA = a.bindingId
      const bidB = b.bindingId
      if (
        bidA !== undefined &&
        bidB !== undefined &&
        bidA !== bidB &&
        Array.isArray(a.data) &&
        Array.isArray(b.data) &&
        a.data.length > 0 &&
        b.data.length > 0
      ) {
        continue
      }
      union(i, j)
    }
  }

  const byRoot = new Map<number, SubTableBindingAlignable[]>()
  for (let i = 0; i < all.length; i++) {
    const r = find(i)
    if (!byRoot.has(r)) byRoot.set(r, [])
    byRoot.get(r)!.push(all[i]!)
  }

  for (const group of byRoot.values()) {
    let pkFields: string[] | undefined
    for (const b of group) {
      const pks = b.primaryKeyFields
      if (!pkFields?.length && Array.isArray(pks) && pks.length > 0) {
        pkFields = pks.map(f => String(f).trim()).filter(Boolean)
      }
    }
    let merged: any[] = []
    for (const b of group) {
      merged = mergeSubTableRowsByRowId(merged, Array.isArray(b.data) ? b.data : [], pkFields)
    }
    if (merged.length === 0) continue
    const snapshot = merged.map(r => ({ ...r }))
    for (const b of group) {
      b.data = snapshot
    }
  }
}

/**
 * Diagram-only align: copied-binding id mismatches still merge inside {@link nodeFormMap}, without touching
 * live {@link subTableBindings} (MI isolation must not be widened by a post-isolate node refresh).
 */
function alignNodeFormMapSubTableBindingsOnly() {
  if (nodeFormMap.value.size === 0) return
  nodeFormMap.value.forEach(info => {
    const chunk = info.subTableBindings as SubTableBindingAlignable[]
    if (chunk.length === 0) return
    applyUnionFindMergedRowSnapshots(chunk)
    enrichChildBindingRowsFromParentsNestedSubTables(chunk as any)
  })
}

/**
 * Copied Task Forms (e.g. subform_copy) get a new bindingId while runtime __subTables__ still keys by the
 * initiator binding id — union-find merge by shared tableId / normalized display name (same idea as My Request).
 * Includes diagram node bindings so clicking nodes stays consistent.
 */
function alignProcessSubTableBindingsBySharedTable() {
  const partitions: SubTableBindingAlignable[][] = []
  if ((subTableBindings.value as SubTableBindingAlignable[]).length > 0) {
    partitions.push(subTableBindings.value as SubTableBindingAlignable[])
  }
  previousForms.value.forEach(f => {
    if ((f.subTableBindings as SubTableBindingAlignable[]).length > 0) {
      partitions.push(f.subTableBindings as SubTableBindingAlignable[])
    }
  })
  nodeFormMap.value.forEach(info => {
    if ((info.subTableBindings as SubTableBindingAlignable[]).length > 0) {
      partitions.push(info.subTableBindings as SubTableBindingAlignable[])
    }
  })

  if (partitions.length === 0) return

  partitions.forEach(p => {
    applyUnionFindMergedRowSnapshots(p)
    enrichChildBindingRowsFromParentsNestedSubTables(p as any)
  })

  backfillEmptySubTableBindingsFromVariables()
}

/**
 * BPM diagram clicks render {@link nodeFormMap}. That map is built in {@link loadFunctionUnitContent}, but
 * {@link loadProcessAndTaskFormData} may merge additional {@code fieldValues}/{@code __subTables__} afterwards —
 * refresh snapshots so historical nodes (e.g. assignment/submit "sub form1") show the same rows as live variables.
 *
 * {@code topLevelValuesSource}: when MI isolation has cleared fields on {@link formData} that still belong on
 * earlier steps (diagram uses {@code selectedNodeForm.values} for read-only nodes), pass the pre-isolate snapshot here.
 */
function refreshNodeFormMapFromFormData(opts?: {
  subTablesSource?: Record<string, unknown> | null
  topLevelValuesSource?: Record<string, unknown> | null
}) {
  if (nodeFormMap.value.size === 0) return
  const valuesBase = (opts?.topLevelValuesSource ?? formData.value) as Record<string, any>
  const raw = opts?.subTablesSource ?? formData.value.__subTables__
  if (!raw || typeof raw !== 'object') {
    const nextEarly = new Map<string, NodeFormInfo>()
    for (const [nodeId, info] of nodeFormMap.value.entries()) {
      const bindings = info.subTableBindings.map(b => ({
        ...b,
        data: cloneSubTableRows(Array.isArray(b.data) ? b.data : []),
      }))
      applySharedAttachmentFinalizeAndMaterialize(bindings, valuesBase, {
        flattened: null,
        bindingTableById: lastBindingRelationTableMap.value,
      })
      nextEarly.set(nodeId, { ...info, values: { ...valuesBase }, subTableBindings: bindings })
    }
    nodeFormMap.value = nextEarly
    return
  }
  const flattened = JSON.parse(JSON.stringify(raw)) as Record<string, unknown>
  flattenNestedSubTableRowsIntoPayload(flattened)
  const rtMap = lastBindingRelationTableMap.value
  const next = new Map<string, NodeFormInfo>()
  for (const [nodeId, info] of nodeFormMap.value.entries()) {
    const bindings = info.subTableBindings.map(b => ({
      ...b,
      data: [] as PreviousFormEntry['subTableBindings'][0]['data'],
    }))
    const ambiguousNodeRefresh = bindingIdsPreferStrictSubTableLookup(bindings as any[])
    bindings.forEach(binding => {
      const saved = getSavedSubTableRows(flattened, binding, ambiguousNodeRefresh.has(binding.bindingId))
      if (saved) binding.data = cloneSubTableRows(saved)
    })
    hydrateChildSubTablesFromParentsNestedRows(bindings as any, flattened, rtMap.size > 0 ? rtMap : undefined)
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(bindings as any, flattened, rtMap)
    enrichChildBindingRowsFromParentsNestedSubTables(bindings as any)
    applySharedAttachmentFinalizeAndMaterialize(bindings, valuesBase, {
      flattened,
      bindingTableById: lastBindingRelationTableMap.value,
    })
    next.set(nodeId, {
      ...info,
      values: { ...valuesBase },
      subTableBindings: bindings,
    })
  }
  nodeFormMap.value = next
  alignNodeFormMapSubTableBindingsOnly()
}

/** When no binding key matches variables, pick a saved row list by column / sub-form field overlap. */
function collectSubTableBindingMatchKeys(b: {
  columns?: Array<{ field?: string }>
  formFields?: FormField[]
}): Set<string> {
  const fieldSet = new Set<string>()
  for (const c of b.columns || []) {
    if (typeof c?.field === 'string' && c.field.length > 0) fieldSet.add(c.field)
  }
  const walkFormFields = (fields?: FormField[]) => {
    if (!Array.isArray(fields)) return
    for (const f of fields) {
      if (f.type === 'tabs' && Array.isArray(f.tabs)) {
        for (const tab of f.tabs) walkFormFields(tab.fields)
      } else if (f.type === 'collapse' && Array.isArray(f.collapsePanels)) {
        for (const panel of f.collapsePanels) walkFormFields(panel.fields)
      } else if (f.type === 'card' || f.type === 'row' || f.type === 'col') walkFormFields(f.children)
      else if (typeof f.key === 'string' && f.key.length > 0) fieldSet.add(f.key)
    }
  }
  walkFormFields(b.formFields)
  return fieldSet
}

const SUB_TABLE_MI_PLACEHOLDER_KEYS = new Set([
  'assignee_user_id',
  'assignee_display_name',
  'task_status',
  'task_id',
  'task_definition_key'
])

function pickSubTableRowValueIgnoreKeyCase(o: Record<string, unknown>, key: string): unknown {
  if (Object.prototype.hasOwnProperty.call(o, key)) return o[key]
  const want = key.toLowerCase()
  for (const rk of Object.keys(o)) {
    if (rk.toLowerCase() === want) return o[rk]
  }
  return undefined
}

function subTableRowsLackSavedFieldPayload(rows: unknown[] | undefined, fieldKeys: Set<string>): boolean {
  if (fieldKeys.size === 0) return false
  if (!Array.isArray(rows) || rows.length === 0) return true
  const checkKeys = [...fieldKeys].filter(k => !SUB_TABLE_MI_PLACEHOLDER_KEYS.has(k))
  if (checkKeys.length === 0) return true
  for (const row of rows) {
    if (!row || typeof row !== 'object') continue
    const o = row as Record<string, unknown>
    for (const k of checkKeys) {
      const v = pickSubTableRowValueIgnoreKeyCase(o, k)
      if (v === undefined || v === null || v === '') continue
      if (typeof v === 'boolean') return false
      if (typeof v === 'number' && !Number.isNaN(v)) return false
      if (typeof v === 'string' && v.trim() !== '') return false
    }
  }
  return true
}

function backfillEmptySubTableBindingsFromVariables() {
  const savedMap = coerceSubTablesVariableToMap(formData.value.__subTables__)
  if (!savedMap) return
  formData.value = { ...formData.value, __subTables__: savedMap }
  const sliceArrays = collectSubTableSliceArraysDeep(savedMap)

  const all = [
    ...subTableBindings.value,
    ...previousForms.value.flatMap(f => f.subTableBindings),
    ...Array.from(nodeFormMap.value.values()).flatMap(n => n.subTableBindings)
  ]

  for (const b of all) {
    const fieldKeys = collectSubTableBindingMatchKeys(b as { columns?: Array<{ field?: string }>; formFields?: FormField[] })
    if (fieldKeys.size === 0) continue

    if (
      Array.isArray(b.data) &&
      b.data.length > 0 &&
      !subTableRowsLackSavedFieldPayload(b.data, fieldKeys)
    ) {
      continue
    }

    let best: any[] | null = null
    let bestScore = 0
    for (const val of sliceArrays) {
      if (!Array.isArray(val) || val.length === 0) continue
      const row0 = val[0]
      if (!row0 || typeof row0 !== 'object') continue
      const row0KeysLower = new Set(Object.keys(row0 as object).map(k => k.toLowerCase()))
      let score = 0
      for (const k of fieldKeys) {
        if (row0KeysLower.has(k.toLowerCase())) score++
      }
      const threshold =
        fieldKeys.size <= 2 ? 1 : Math.min(fieldKeys.size, Math.max(2, Math.ceil(fieldKeys.size * 0.25)))
      if (score >= threshold && score > bestScore) {
        bestScore = score
        best = val as any[]
      }
    }
    if (best) {
      b.data = best.map((r: any) => (r && typeof r === 'object' ? { ...r } : r))
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

/** When slice binding metadata is missing, match MI rows via FK columns to the participant row id. */
function miIncomingRowLikelyForParticipant(row: unknown, myRowId: MiParticipantRowId): boolean {
  if (!row || typeof row !== 'object') return false
  const collectionPk = miCollectionPrimaryKeyFields()
  if (hasConfiguredPrimaryKeyFields(collectionPk) && rowMatchesSubTablePrimaryKey(row, myRowId, collectionPk)) {
    return true
  }
  const rec = row as Record<string, unknown>
  const fkKeys = ['participant_id', 'participantId', 'parent_id', 'parentId', 'meeting_participant_id']
  for (const k of fkKeys) {
    const v = rec[k]
    if (v != null && v !== '' && miParticipantRowIdsEqual(v, myRowId)) return true
  }
  return false
}

/**
 * Merge portal Task Form API {@code fieldValues} into {@link formData}.
 * Non-MI: same shallow merge as before (omit null {@code __subTables__} from API).
 * MI: merge {@code __subTables__} slices row-wise for the current participant so link-form / nested rows are not dropped
 * (Flowable variables often only carry thin MI expansion rows; persisted form state may live on the task form DTO).
 */
function mergeIncomingTaskFormFieldValues(fieldValues: Record<string, any>, taskData: any) {
  if (!fieldValues || typeof fieldValues !== 'object') return
  const miSubTask = isMiSubTask(taskData)

  if (!miSubTask) {
    const incoming = { ...fieldValues }
    const incomingSub = incoming.__subTables__
    if (incomingSub && typeof incomingSub === 'object' && !Array.isArray(incomingSub)) {
      const mergedSub: Record<string, unknown> = {
        ...((formData.value.__subTables__ as Record<string, unknown>) || {}),
      }
      for (const [sliceKey, val] of Object.entries(incomingSub)) {
        if (!Array.isArray(val)) continue
        const kid = Number(sliceKey)
        const bindingHint =
          (Number.isFinite(kid) ? subTableBindings.value.find(b => b.bindingId === kid) : null) ??
          subTableBindings.value.find(
            b => normalizeSubTableName(b.tableName) === normalizeSubTableName(String(sliceKey)),
          )
        if (bindingHint && isMiParticipantScopedSubTableBinding(bindingHint)) {
          const prevRaw = mergedSub[sliceKey] ?? mergedSub[String(sliceKey)]
          const prevRows = Array.isArray(prevRaw) ? cloneSubTableRows(prevRaw as any[]) : []
          const mergedRows = mergeSubTableRowsByRowId(prevRows, val as any[], bindingHint.primaryKeyFields ?? null)
          mergedSub[sliceKey] = mergedRows
          mergedSub[String(sliceKey)] = mergedRows
          continue
        }
        const prevRaw = mergedSub[sliceKey] ?? mergedSub[String(sliceKey)]
        const prevRows = Array.isArray(prevRaw) ? cloneSubTableRows(prevRaw as any[]) : []
        const mergedRows = mergeSubTableRowsByRowId(prevRows, val as any[], bindingHint?.primaryKeyFields ?? null)
        if (mergedRows.length === 0 && prevRows.length > 0) {
          mergedSub[sliceKey] = prevRows
          mergedSub[String(sliceKey)] = prevRows
        } else {
          mergedSub[sliceKey] = mergedRows
          mergedSub[String(sliceKey)] = mergedRows
        }
        const bn = bindingHint?.tableName
        if (bn) {
          mergedSub[bn] = mergedSub[sliceKey]
          mergedSub[normalizeSubTableName(bn)] = mergedSub[sliceKey]
        }
      }
      delete incoming.__subTables__
      formData.value = { ...formData.value, ...incoming, __subTables__: mergedSub }
    } else {
      if (incoming.__subTables__ == null) delete incoming.__subTables__
      formData.value = { ...formData.value, ...incoming }
    }
    return
  }

  const vars = taskData?.variables || {}
  const myRowId = resolveCurrentMiParticipantRowIdFromTaskVars(vars)

  const incomingFull = { ...fieldValues }
  const incomingSub = incomingFull.__subTables__
  const mergedSub: Record<string, unknown> = {
    ...((formData.value.__subTables__ as Record<string, unknown>) || {}),
  }

  if (incomingSub && typeof incomingSub === 'object') {
    for (const [sliceKey, val] of Object.entries(incomingSub)) {
      if (!Array.isArray(val)) continue
      let rows = cloneSubTableRows(val as any[])
      const kid = Number(sliceKey)
      const bindingHint =
        (Number.isFinite(kid) ? subTableBindings.value.find(b => b.bindingId === kid) : null) ??
        subTableBindings.value.find(
          b => normalizeSubTableName(b.tableName) === normalizeSubTableName(String(sliceKey)),
        )

      if (myRowId != null) {
        const scopeToParticipant =
          bindingHint && isMiParticipantScopedSubTableBinding(bindingHint)
        if (scopeToParticipant) {
          const filt = rows.filter((row: any) =>
            rowBelongsToCurrentMiScope(row, myRowId, bindingHint),
          )
          rows = filt.length > 0 ? filt : rows.length === 1 ? rows : filt
        }
      }

      const prevRaw = mergedSub[sliceKey] ?? mergedSub[String(sliceKey)]
      const prevRows = Array.isArray(prevRaw) ? cloneSubTableRows(prevRaw as any[]) : []
      const pk = bindingHint?.primaryKeyFields ?? null
      const mergedRows = mergeSubTableRowsByRowId(prevRows, rows, pk)
      mergedSub[sliceKey] = mergedRows
      mergedSub[String(sliceKey)] = mergedRows
      const bn = bindingHint?.tableName
      if (bn) {
        mergedSub[bn] = mergedRows
        mergedSub[normalizeSubTableName(bn)] = mergedRows
      }
    }
  }

  delete incomingFull.__subTables__
  formData.value = {
    ...formData.value,
    ...incomingFull,
    __subTables__: mergedSub,
  }
}

/** MI isolation: participant-scoped sub-tables only; main-table-linked tables (e.g. attachment) stay shared. */
function rowBelongsToCurrentMiScope(
  row: unknown,
  myRowId: MiParticipantRowId,
  binding: {
    tableName: string
    physicalTableName?: string
    foreignKeyField?: string | null
    primaryKeyFields?: string[]
    columns?: Array<{ field?: string }>
    bindingId?: number | string
  },
): boolean {
  const scope = miSubProcessScope.value
  if (scope && bindingMatchesMiSubTableName(binding, scope.subTableName)) {
    const pk = binding.primaryKeyFields ?? miCollectionPrimaryKeyFields()
    if (!hasConfiguredPrimaryKeyFields(pk)) {
      warnMiMissingPrimaryKey(binding)
      return false
    }
    return rowMatchesSubTablePrimaryKey(row, myRowId, pk)
  }
  if (!isMiParticipantScopedSubTableBinding(binding)) return true
  return miRowBelongsToCurrentParticipant(row, myRowId, binding)
}

/** MI isolation: participant rows match by PK; related sub-table rows match by FK to participant (not by sub-row id). */
function miRowBelongsToCurrentParticipant(
  row: any,
  myRowId: MiParticipantRowId,
  binding: {
    tableName: string
    foreignKeyField?: string
    primaryKeyFields?: string[]
    physicalTableName?: string
    bindingId?: number | string
  },
): boolean {
  if (!row || typeof row !== 'object') return false
  if (isParticipantsBinding(binding)) {
    const pks = binding.primaryKeyFields ?? miCollectionPrimaryKeyFields()
    if (!hasConfiguredPrimaryKeyFields(pks)) {
      warnMiMissingPrimaryKey(binding)
      return false
    }
    return rowMatchesSubTablePrimaryKey(row, myRowId, pks)
  }
  const fk = binding.foreignKeyField
  const fkStr = fk ? String(fk).trim() : ''
  /**
   * Designer metadata often sets {@code foreignKeyField} to the relation table's PK column ({@code id}).
   * That is the row's own id, not the MI participant row id — comparing it to {@code myRowId} yields false
   * and incorrectly skips {@code participant_id} / nested-FK fallbacks (see MI subflow subtable2 across nodes).
   */
  const fkLooksLikeRowPrimaryKey =
    (Array.isArray(binding.primaryKeyFields) &&
      binding.primaryKeyFields.length > 0 &&
      binding.primaryKeyFields.some(p => String(p).trim() === fkStr)) ||
    (fkStr.toLowerCase() === 'id' && !isParticipantsBinding(binding))
  if (fk && !fkLooksLikeRowPrimaryKey && row[fk] != null && row[fk] !== '') {
    if (miParticipantRowIdsEqual(row[fk], myRowId)) return true
  }
  const fallbackFkKeys = ['participant_id', 'participantId', 'parent_id', 'parentId', 'meeting_participant_id']
  for (const k of fallbackFkKeys) {
    if (row[k] != null && row[k] !== '' && miParticipantRowIdsEqual(row[k], myRowId)) {
      return true
    }
  }
  const pksRel = binding.primaryKeyFields
  const collPk = miCollectionPrimaryKeyFields()
  const pkForMatch = pksRel?.length ? pksRel : collPk
  if (hasConfiguredPrimaryKeyFields(pkForMatch)) {
    return rowMatchesSubTablePrimaryKey(row, myRowId, pkForMatch)
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

/**
 * Current MI participant row id from {@code _currentItem} + designer {@code primaryKeyFields}
 * (single PK scalar or composite {@code v1|v2|...} from {@code rowKey}).
 */
const currentMiRowId = computed<MiParticipantRowId | null>(() => {
  const vars = (taskInfo.value as { variables?: Record<string, unknown> })?.variables
  return resolveCurrentMiParticipantRowIdFromTaskVars(vars)
})

// MI subtask fill-form dialog state
const miFillDialogVisible = ref(false)
const miFillDialogData = ref<Record<string, any>>({})
const miFillSubTableBindings = ref<typeof subTableBindings.value>([])
const miFilled = ref(false)
const miFillDialogReadOnly = ref(false)

function isolateMiSubTaskData(taskData: any) {
  const myRowId = resolveCurrentMiParticipantRowIdFromTaskVars(taskData?.variables)
  if (myRowId == null) {
    return
  }

  warnMiCollectionPrimaryKeyIfNeeded()

  const collectionPk = miCollectionPrimaryKeyFields()
  const ambiguousCurrentBindings = bindingIdsPreferStrictSubTableLookup(subTableBindings.value)

  // Multi-instance data isolation: only the **current** task form is scoped to this participant.
  // Previous-node forms stay read-only with full snapshot (other sub-tasks' sub form2 data must remain visible).
  for (const binding of subTableBindings.value) {
    if (!isMiParticipantScopedSubTableBinding(binding)) continue
    const rows = Array.isArray(binding.data) ? binding.data : []
    binding.data = rows.filter((row: any) => rowBelongsToCurrentMiScope(row, myRowId, binding))
  }

  let myRow: any = undefined
  outer_myrow: for (const b of subTableBindings.value) {
    const rows = Array.isArray(b.data) ? b.data : []
    for (const row of rows) {
      if (
        rowMatchesSubTablePrimaryKey(row, myRowId, collectionPk) ||
        miRowBelongsToCurrentParticipant(row, myRowId, b)
      ) {
        myRow = row
        break outer_myrow
      }
    }
  }

  const originalFormData = { ...formData.value }

  /** Process variables often carry start-step scalars at top level; MI rows may omit them until persisted on the row. */
  if (myRow && typeof myRow === 'object') {
    const rec = myRow as Record<string, any>
    const seedFieldNames = new Set<string>()
    for (const binding of subTableBindings.value) {
      const cols = binding.columns as Array<{ field?: string }> | undefined
      if (Array.isArray(cols)) {
        for (const col of cols) {
          const fk = col?.field
          if (typeof fk === 'string' && fk.length > 0) seedFieldNames.add(fk)
        }
      }
      const ff = (binding as { formFields?: unknown }).formFields
      const walk = (arr: unknown) => {
        if (!Array.isArray(arr)) return
        for (const f of arr as Array<{ key?: unknown; children?: unknown; fields?: unknown }>) {
          if (f?.key != null && String(f.key).trim() !== '') seedFieldNames.add(String(f.key))
          walk(f.children)
          walk(f.fields)
        }
      }
      walk(ff)
    }
    for (const fk of seedFieldNames) {
      if (fk.startsWith('__')) continue
      const cur = rec[fk]
      if (cur != null && cur !== '') continue
      if (!Object.prototype.hasOwnProperty.call(originalFormData, fk)) continue
      const seed = originalFormData[fk]
      if (seed == null || seed === '') continue
      rec[fk] = seed
    }
  }

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
    } else if (Object.prototype.hasOwnProperty.call(originalFormData, key)) {
      cleanedFormData[key] = originalFormData[key]
    } else {
      cleanedFormData[key] = null
    }
  }

  // Preserve previous form field values (readonly display of parent task data)
  const prevFormFieldKeys = new Set<string>()
  previousForms.value.forEach((pf: any) => {
    for (const key of collectLeafFormFieldKeys(pf.fields || [], pf.tabs || [])) {
      prevFormFieldKeys.add(key)
    }
  })
  for (const key of prevFormFieldKeys) {
    if (!(key in cleanedFormData) && Object.prototype.hasOwnProperty.call(originalFormData, key)) {
      cleanedFormData[key] = originalFormData[key]
    }
  }

  /**
   * Designer-side diagnostic — surface main-form values that no MI sub-task or previous-form field
   * can receive. Two failure modes silently drop data without this warning:
   *   1. designer named the MI sub-task field differently from the main form (no implicit aliasing),
   *   2. BPMN parsing missed the upstream userTask (e.g. previousForms ended up empty) so the value
   *      is never displayed even read-only.
   * Only logged in dev to avoid noise in production; check console when fields appear blank.
   */
  if (import.meta.env.DEV) {
    const currentFormKeySet = new Set<string>(formKeys)
    const orphanFields: string[] = []
    for (const key of Object.keys(originalFormData)) {
      if (!key || key.startsWith('_') || key.startsWith('__')) continue
      if (key === 'initiator' || key === 'mainRecordId' || key === 'meeting_id'
          || key === 'approval_result' || key === 'approved') continue
      const value = originalFormData[key]
      if (value == null || value === '' || typeof value === 'function') continue
      if (currentFormKeySet.has(key) || prevFormFieldKeys.has(key)) continue
      orphanFields.push(key)
    }
    if (orphanFields.length > 0) {
      console.warn(
        '[MI sub-task] Main-form variables present but unreachable from the current sub-task form ' +
        'or any previous-form field. Designers: check that sub-task field keys match the main form, ' +
        'or that upstream userTasks have a formId attached. Orphan keys:',
        orphanFields,
        { taskId: (taskData?.id || taskData?.taskId), taskDefinitionKey: taskData?.taskDefinitionKey }
      )
    }
  }

  cleanedFormData.__subTables__ = rebuildIsolatedSubTablesPayload()
  if (myRow && typeof myRow === 'object') {
    const rowRec = myRow as Record<string, unknown>
    const prevNestRaw = rowRec.__subTables__
    const prevNest: Record<string, unknown> =
      prevNestRaw && typeof prevNestRaw === 'object' ? { ...(prevNestRaw as object) } : {}
    const nextRowSub: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(prevNest)) {
      if (Array.isArray(v)) {
        nextRowSub[k] = cloneSubTableRows(v as any[])
      }
    }
    const rebuilt = cleanedFormData.__subTables__ as Record<string, unknown>
    const origSt =
      originalFormData.__subTables__ && typeof originalFormData.__subTables__ === 'object'
        ? (originalFormData.__subTables__ as Record<string, unknown>)
        : null

    for (const binding of subTableBindings.value) {
      const forbid = ambiguousCurrentBindings.has(binding.bindingId)
      const saved = getSavedSubTableRows(rebuilt, binding, forbid)
      const rowsFromRebuilt = cloneSubTableRows(Array.isArray(saved) ? saved : [])
      const fromPrev = getSavedSubTableRows(nextRowSub as any, binding, forbid) ?? []
      const pk = binding.primaryKeyFields ?? null

      // Later operands win merge conflicts — put thin rebuilt first, richer prev/orig/nested last
      let merged = mergeSubTableRowsByRowId([], rowsFromRebuilt, pk)
      merged = mergeSubTableRowsByRowId(merged, fromPrev, pk)

      if (origSt) {
        const origSlice = getSavedSubTableRows(origSt, binding, forbid)
        if (Array.isArray(origSlice) && origSlice.length > 0) {
          const toMerge = isMiParticipantScopedSubTableBinding(binding)
            ? origSlice.filter((row: any) => rowBelongsToCurrentMiScope(row, myRowId, binding))
            : origSlice
          merged = mergeSubTableRowsByRowId(merged, cloneSubTableRows(toMerge), pk)
        }

        const nestedSlices = collectNestedSlicesForBindingFromSubTablesWalk(origSt, binding)
        for (const chunk of nestedSlices) {
          const toMerge = isMiParticipantScopedSubTableBinding(binding)
            ? (chunk as any[]).filter((row: any) => rowBelongsToCurrentMiScope(row, myRowId, binding))
            : (chunk as any[])
          if (toMerge.length === 0) continue
          merged = mergeSubTableRowsByRowId(merged, cloneSubTableRows(toMerge), pk)
        }
      }

      /**
       * {@code rowsFromRebuilt} carries previous-form snapshots (other participants' submitted rows) and
       * {@code fromPrev} is cloned from raw {@code variables.__subTables__} where slices may pool every
       * MI participant's data. Without filtering, the current participant's {@code __subTables__} (and the
       * link-form inline subtable2) would surface other sub-tasks' rows — see MI Subtask Demo where
       * sub form1 of participant 2 was pre-filled with participant 1's age/sex values.
       */
      const scopedMerged = isMiParticipantScopedSubTableBinding(binding)
        ? merged.filter((row: any) => rowBelongsToCurrentMiScope(row, myRowId, binding))
        : merged

      const rows = cloneSubTableRows(scopedMerged)
      nextRowSub[binding.bindingId] = rows
      nextRowSub[String(binding.bindingId)] = rows
      if (binding.tableName) {
        nextRowSub[binding.tableName] = rows
        nextRowSub[normalizeSubTableName(binding.tableName)] = rows
      }
    }
    rowRec.__subTables__ = nextRowSub
    for (const binding of subTableBindings.value) {
      const forbid = ambiguousCurrentBindings.has(binding.bindingId)
      const nestRows = getSavedSubTableRows(nextRowSub as any, binding, forbid)
      if (nestRows?.length) {
        binding.data = cloneSubTableRows(
          mergeSubTableRowsByRowId(binding.data, nestRows, binding.primaryKeyFields ?? null)
        )
      }
    }
  }
  formData.value = cleanedFormData
}

/**
 * {@link enrichChildBindingRowsFromParentsNestedSubTables} unions nested child slices from **every** peer parent
 * binding; in MI sub-tasks that can resurrect other instances' rows. Re-filter by {@link miRowBelongsToCurrentParticipant}.
 */
function applyMiParticipantFilterToCurrentSubTableBindings(myRowId: MiParticipantRowId) {
  for (const binding of subTableBindings.value) {
    if (!isMiParticipantScopedSubTableBinding(binding)) continue
    const rows = Array.isArray(binding.data) ? binding.data : []
    binding.data = cloneSubTableRows(rows.filter((row: any) => rowBelongsToCurrentMiScope(row, myRowId, binding)))
  }
}

/**
 * After MI isolation + task-form merge, copied-form binding ids may still miss {@code __subTables__} slices that
 * hold prior-step link-child fields (sub form1 → subtable2). Re-hydrate from the full variables snapshot and
 * nest link-child rows under the participant parent row for inline form-below-table.
 */
function resyncMiParticipantSubTablesFromVariables(
  myRowId: MiParticipantRowId,
  subTablesSource?: Record<string, unknown> | null,
) {
  const savedMap = coerceSubTablesVariableToMap(subTablesSource ?? formData.value.__subTables__)
  if (!savedMap) return

  const flattened = JSON.parse(JSON.stringify(savedMap)) as Record<string, unknown>
  flattenNestedSubTableRowsIntoPayload(flattened)
  scrubMiCorruptLinkChildRowsForParent(flattened, myRowId)

  const rtMap = lastBindingRelationTableMap.value
  const ambiguous = bindingIdsPreferStrictSubTableLookup(subTableBindings.value)

  for (const binding of subTableBindings.value) {
    const byKey = getSavedSubTableRows(flattened, binding, ambiguous.has(binding.bindingId)) ?? []
    const participantScoped = isMiParticipantScopedSubTableBinding(binding)
    const tableIdRaw =
      binding.tableId != null ? Number(binding.tableId) : rtMap.get(binding.bindingId)
    const siblingSlices =
      !participantScoped
        ? mergeAllSlicesForSharedProcessSubTableBinding(flattened, binding, rtMap)
        : mergeAllSubTableSlicesFromVariables(flattened, binding.primaryKeyFields ?? null)
    const candidateRows = [...byKey, ...siblingSlices]
    const scoped = participantScoped
      ? candidateRows.filter((row: unknown) => rowBelongsToCurrentMiScope(row, myRowId, binding))
      : candidateRows
    const existing = Array.isArray(binding.data) ? binding.data : []
    binding.data = cloneSubTableRows(
      mergeSubTableRowsByRowId(existing, scoped, binding.primaryKeyFields ?? null),
    )
  }

  hydrateChildSubTablesFromParentsNestedRows(
    subTableBindings.value,
    flattened,
    rtMap.size > 0 ? rtMap : undefined,
  )
  hydrateBindingsRowsFromVariablesBySharedRelationTableId(subTableBindings.value, flattened, rtMap)
  enrichChildBindingRowsFromParentsNestedSubTables(subTableBindings.value)
  applyMiParticipantFilterToCurrentSubTableBindings(myRowId)

  applySharedAttachmentFinalizeAndMaterialize(
    subTableBindings.value,
    formData.value as Record<string, unknown>,
    { flattened, bindingTableById: rtMap },
  )
  for (const binding of subTableBindings.value) {
    if (isMiParticipantScopedSubTableBinding(binding)) continue
    if (isSharedAttachmentFileBinding(binding)) continue
    binding.data = finalizeSharedProcessSubTableBindingRows(binding.data, binding)
  }

  for (const binding of subTableBindings.value) {
    const rows = Array.isArray(binding.data) ? binding.data : []
    /**
     * MI link-form child bindings (e.g. subtable2) must be synced even when their MI-filtered slice is
     * empty — otherwise stale rows from other participants linger under parentRow.__subTables__ and
     * pre-fill the inline link form on first open (MI Subtask Demo, sub form1 of participant 2).
     */
    if (rows.length > 0 || isMiParticipantScopedSubTableBinding(binding)) {
      syncMiLinkChildRowsIntoParentNested(
        { bindingId: binding.bindingId, tableName: binding.tableName },
        cloneSubTableRows(rows),
      )
    }
  }
  patchFormDataSubTablesFromCurrentBindings()
}

/** After MI refilter, align {@link formData}.__subTables__ keys for current bindings so autosave/submit matches the grid. */
function patchFormDataSubTablesFromCurrentBindings() {
  const tbl: Record<string, any> = { ...((formData.value.__subTables__ as Record<string, any>) || {}) }
  for (const binding of subTableBindings.value) {
    const rows = cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
    tbl[binding.bindingId] = rows
    tbl[String(binding.bindingId)] = rows
    if (binding.tableName) {
      tbl[binding.tableName] = rows
      tbl[normalizeSubTableName(binding.tableName)] = rows
    }
    const phys = binding.physicalTableName
    if (phys) {
      tbl[phys] = rows
      tbl[normalizeSubTableName(phys)] = rows
    }
  }
  formData.value = { ...formData.value, __subTables__: tbl }
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
  const ambiguousMiDialog = bindingIdsPreferStrictSubTableLookup(miFillSubTableBindings.value)
  const existing = getSavedSubTableRows(subTables, target, ambiguousMiDialog.has(target.bindingId))
  const merged = mergeSubTableRowsByRowId(existing, nextRows, target.primaryKeyFields)
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
  const ambiguousMiDialogSave = bindingIdsPreferStrictSubTableLookup(miFillSubTableBindings.value)

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
    const existing = getSavedSubTableRows(subTables, binding, ambiguousMiDialogSave.has(binding.bindingId))
    const merged = mergeSubTableRowsByRowId(existing, rows, binding.primaryKeyFields)
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
    await apiSubmitTaskForm(effectiveTaskId.value, {
      formData: nextFormData,
      subTableData,
      baselineValues: taskFormDTO.value?.fieldValues || {}
    })
    formData.value = nextFormData
    miFilled.value = true
    miFillDialogVisible.value = false
    ElMessage.success(t('task.operationSuccess'))
  } catch (e) {
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
    clearNodeSelection()
    return
  }
  selectedNodeId.value = node.id
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
const processFormCollapse = ref<string[]>([])  // empty = collapsed
const processFormEditable = ref(false)
const processFormFields = ref<FormField[]>([])
const processFormTabs = ref<FormTab[]>([])
const processFormValues = ref<Record<string, any>>({})
/**
 * Sub-table bindings parsed from `pfData.subTableBindings` enriched with
 * configJson (subForms / subTablePortalViews) — feeds FormRenderer so the
 * Process Form panel renders sub-table widgets at parity with the Designer
 * Form Preview (portal-design-parity).
 */
const processFormSubTableBindings = ref<typeof subTableBindings.value>([])
/** Designer configJson for the process form (powers Link Form / portalViews merge). */
const processFormFormConfig = ref<Record<string, unknown>>({})
/** Native (non-link-target) binding ids on the process form. */
const processFormNativeSubTableBindingIds = ref<number[]>([])

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

const processFormState = computed(() => processFormData.value?.processState)
const bpmnParser = useBpmnParser({
  taskInfo: taskInfo as any,
  historyRecords,
  isCompletedTask,
  processState: processFormState,
})
const {
  processNodes,
  processFlows,
  completedNodeIds,
  currentNodeId,
  bpmnXml,
  diagramSuppressMode,
  parseBpmnXml,
  parseBpmnXmlAndGetFormId,
  parseBpmnXmlAndGetPreviousFormIds,
} = bpmnParser

const miSubProcessScope = ref<MiSubProcessScopeConfig | null>(null)

function refreshMiSubProcessScopeFromBpmn() {
  const xml = bpmnXml.value
  if (!xml) {
    miSubProcessScope.value = null
    return
  }
  const ti = taskInfo.value as { taskDefinitionKey?: string; taskName?: string }
  miSubProcessScope.value = resolveMiSubProcessScopeFromBpmn(xml, {
    userTaskId: ti?.taskDefinitionKey ?? null,
    userTaskName: ti?.taskName ?? null,
  })
}

function miCollectionPrimaryKeyFields(): string[] | undefined {
  const scope = miSubProcessScope.value
  if (!scope?.subTableName) return undefined
  const b = findBindingForMiSubTableName(subTableBindings.value, scope.subTableName)
  return b?.primaryKeyFields ?? undefined
}

const miMissingPrimaryKeyWarned = new Set<string>()

function warnMiMissingPrimaryKey(binding: {
  tableName?: string
  physicalTableName?: string
  bindingId?: number | string
}) {
  const label = describeSubTableBindingLabel(binding)
  const key = label || 'unknown'
  if (miMissingPrimaryKeyWarned.has(key)) return
  miMissingPrimaryKeyWarned.add(key)
  ElMessage.error(t('task.miPrimaryKeyNotConfigured', { table: label || key }))
}

function warnMiCollectionPrimaryKeyIfNeeded(): void {
  const scope = miSubProcessScope.value
  if (!scope?.subTableName) return
  const b = findBindingForMiSubTableName(subTableBindings.value, scope.subTableName)
  if (!b) return
  if (hasConfiguredPrimaryKeyFields(b.primaryKeyFields)) return
  warnMiMissingPrimaryKey(b)
}

/** Resolve MI participant id for task variables using designer PK + {@code _currentItem.rowKey}. */
function resolveCurrentMiParticipantRowIdFromTaskVars(
  vars?: Record<string, unknown> | null,
): MiParticipantRowId | null {
  const ci = (vars?._currentItem ?? vars?.currentItem) as Record<string, unknown> | undefined
  const scope = miSubProcessScope.value
  const pk = miCollectionPrimaryKeyFields()
  return extractMiParticipantRowIdFromCurrentItem(ci, pk, {
    rowIdVariable: scope?.rowIdVariable ?? 'currentItem.rowId',
  })
}

const taskForm = useTaskForm({ subTableBindings, isMiSubTaskMode, isCompletedTask, effectiveTaskId, taskFormDTO: taskFormDTO as any })
const { formFields, formTabs, formFieldsAfterTabs, formData, currentFormName, formReadOnly, formLabelWidth, formFormOptions, savingTaskForm, saveCurrentTaskForm, scheduleSubTableAutosave, getCurrentFormFieldKeys, clearAutosaveTimer: clearFormAutosaveTimer } = taskForm

// Task 17.4: Return_To_Requester state
const isReturnToRequester = ref(false)

/**
 * Show the Process Form panel iff we have process form data AND the user can
 * actually act on it (Return_To_Requester editable mode). Prior code kept this
 * as a never-flipped `ref(false)`, which silently dead-coded the entire panel.
 * Read-only completed-task snapshots are surfaced by TaskSnapshotSection,
 * so this panel is reserved for the editable Return_To_Requester scenario.
 */
const showProcessFormPanel = computed(() =>
  !!processFormData.value && processFormEditable.value,
)

// Whether the PRIMARY table binding has bindingMode READONLY.
// When true, main form fields are disabled but sub-tables retain their own editability.
const primaryReadOnly = ref(false)

/**
 * Designer configJson for the currently selected task's main form. Required by
 * FormRenderer to resolve Link Form targets (subListViews / boundSubTableBindingId)
 * and to drive mergeSubTablePortalViewsForRuntime portalViews merges. Mirrors
 * applications/detail.vue's mainFormConfig (#1395 / portal-design-parity).
 */
const mainFormConfig = ref<Record<string, any>>({})
/**
 * Binding ids declared directly on the main form's tableBindings (non-PRIMARY,
 * pre-Link-Form-merge). Lets FormRenderer distinguish "native" sub-tables placed
 * on this form's canvas from Link Form targets pulled in from other FU forms.
 */
const mainFormNativeSubTableBindingIds = ref<number[]>([])

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
  clearBpmnParseCache()
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
      const processSubTablesSnapshot =
        data.variables?.__subTables__ && typeof data.variables.__subTables__ === 'object'
          ? (JSON.parse(JSON.stringify(data.variables.__subTables__)) as Record<string, unknown>)
          : null
      const st0 = coerceSubTablesVariableToMap(formData.value.__subTables__)
      if (st0) {
        formData.value = { ...formData.value, __subTables__: st0 }
      }

      // Parallel fetch: history (required before BPMN parse), FU content, process/task forms
      const historyPromise = loadTaskHistory()
      const fuFetchPromise = data.processDefinitionKey
        ? processApi
            .getFunctionUnitContent(data.processDefinitionKey)
            .then(r => (r as { data?: unknown }).data ?? r)
            .catch((err: unknown) => {
              console.error('Failed to prefetch function unit content:', err)
              return null
            })
        : Promise.resolve(null)
      const formPrefetchPromise = prefetchProcessAndTaskFormData(data)

      await historyPromise
      const prefetchedFu = await fuFetchPromise
      if (data.processDefinitionKey) {
        await loadFunctionUnitContent(data.processDefinitionKey, prefetchedFu ?? undefined)
      }

      await loadProcessAndTaskFormData(data, await formPrefetchPromise)
      rehydrateSharedProcessSubTableBindings(processSubTablesSnapshot ?? undefined)

      if (isMiSubTask(data)) {
        isMiSubTaskMode.value = true
        const preIsolateTopLevelForDiagram: Record<string, unknown> = { ...formData.value }
        const miFullSubTablesSnapshot =
          processSubTablesSnapshot ??
          (formData.value.__subTables__ && typeof formData.value.__subTables__ === 'object'
            ? (JSON.parse(JSON.stringify(formData.value.__subTables__)) as Record<string, unknown>)
            : null)
        isolateMiSubTaskData(data)
        enrichChildBindingRowsFromParentsNestedSubTables(subTableBindings.value)
        // Enrich re-aggregates nested rows across peer parents — scope again to this MI element (one task ↔ one participant row).
        const miVarsRef = data?.variables ?? {}
        const miRowIdPostEnrich = resolveCurrentMiParticipantRowIdFromTaskVars(miVarsRef)
        if (miRowIdPostEnrich != null) {
          resyncMiParticipantSubTablesFromVariables(miRowIdPostEnrich, miFullSubTablesSnapshot)
        }
        rehydrateSharedAttachmentBindings(
          subTableBindings.value,
          preIsolateTopLevelForDiagram,
          miFullSubTablesSnapshot,
        )
        patchFormDataSubTablesFromCurrentBindings()
        refreshNodeFormMapFromFormData({
          subTablesSource: miFullSubTablesSnapshot ?? undefined,
          topLevelValuesSource: preIsolateTopLevelForDiagram
        })
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
            const stP = coerceSubTablesVariableToMap(formData.value.__subTables__)
            if (stP) {
              formData.value = { ...formData.value, __subTables__: stP }
            }
            const historyPromise = loadTaskHistory()
            const key = (taskInfo.value as any).processDefinitionKey
            const fuFetchPromise = key
              ? processApi
                  .getFunctionUnitContent(String(key))
                  .then(r => (r as { data?: unknown }).data ?? r)
                  .catch(() => null)
              : Promise.resolve(null)
            const fallbackTask = { ...(taskInfo.value as any), processInstanceId: p.id, id: taskId }
            const formPrefetchPromise = prefetchProcessAndTaskFormData(fallbackTask)
            await historyPromise
            const prefetchedFu = await fuFetchPromise
            if (key) {
              await loadFunctionUnitContent(String(key), prefetchedFu ?? undefined)
            }
            await loadProcessAndTaskFormData(fallbackTask, await formPrefetchPromise)
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
  taskId: effectiveTaskId,
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
  loadTaskDetail,
  validateTaskForm: validateMainTaskForm,
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
  loadTaskDetail,
  /**
   * FORM_POPUP target form resolution — prefer cachedContentForms (full
   * tableBindings included) so the popup renders with full sub-table parity
   * without an extra round-trip. Closure captures cachedContentForms by reference;
   * it's populated by loadFunctionUnitContent well before any action button can fire.
   */
  resolveFormPopupContent: (_action, config) => {
    if (!config?.formId) return null
    const forms = cachedContentForms
    if (!Array.isArray(forms) || forms.length === 0) return null
    return (
      forms.find((f: any) => String(f.sourceId) === String(config.formId))
        || (config.formName ? forms.find((f: any) => f.name === config.formName) : null)
        || null
    )
  },
  /**
   * FORM_POPUP rendering context — reuse the main-form helpers so popup canvas
   * (subTable / lookup / card / Link Form columns) renders at parity with the
   * Designer Form Preview. Replaces the legacy convertFormCreateRuleSimple path
   * that silently dropped any non-primitive widget (#1394).
   */
  preparePopupContext: (formContent, formConfig) => {
    const cfg = formConfig as Record<string, any>
    const rules = cfg.rule && Array.isArray(cfg.rule) ? cfg.rule : (Array.isArray(cfg) ? cfg : null)
    if (!rules) return null

    const popupTabs: FormTab[] = []
    const popupFields: FormField[] = []
    const tabsRule = findTabsRule(rules)
    if (tabsRule?.children && Array.isArray(tabsRule.children)) {
      for (const tabPane of tabsRule.children as Record<string, unknown>[]) {
        if (!isTabPaneRule(tabPane) || !tabPane.props) continue
        const tabFields: FormField[] =
          tabPane.children && Array.isArray(tabPane.children)
            ? extractFieldsRecursive(tabPane.children)
            : []
        popupTabs.push({
          name: tabPane.props.name || `tab_${popupTabs.length}`,
          label: tabPane.props.label || `Tab ${popupTabs.length + 1}`,
          fields: tabFields,
        })
      }
    } else {
      popupFields.push(...extractFieldsRecursive(rules))
    }

    const subForms = cfg.subForms || {}
    const subTablePortalViewsCfg = cfg.subTablePortalViews || {}
    const tableBindings: any[] = (formContent as any).tableBindings || []
    const popupBindings: typeof subTableBindings.value = []
    const nativeIds: number[] = []
    for (const b of tableBindings) {
      if (b.bindingType === 'PRIMARY') continue
      const cols = deriveColumnsFromBinding(b, subForms, cfg)
      const subFormDesign = resolveSubFormDesign(b, subForms)
      const bindingPortalViews =
        subTablePortalViewsCfg[b.bindingId] ?? subTablePortalViewsCfg[String(b.bindingId)] ?? null
      popupBindings.push({
        bindingId: b.bindingId,
        tableId: b.tableId ?? null,
        bindingType: b.bindingType,
        bindingMode: b.bindingMode,
        foreignKeyField: b.foreignKeyField,
        tableName: b.tableDisplayName || b.tableName,
        physicalTableName: b.tableName,
        tableType: b.tableType,
        tableDescription: b.tableDescription,
        columns: cols,
        formFields: subFormDesign.formFields,
        formOptions: subFormDesign.formOptions,
        portalViews: bindingPortalViews,
        primaryKeyFields: resolveSubTablePrimaryKeyFields(b.primaryKeyFields, b.bindingId, cfg),
        data: [],
      })
      const bid = Number(b.bindingId)
      if (Number.isFinite(bid)) nativeIds.push(bid)
    }
    // Link Form targets in the popup form may reference bindings declared in
    // other FU forms (e.g. subtable2). Merge those so SubTableField's Link Form
    // modal can resolve them at parity with the main form.
    mergeLinkFormTargetBindingsInto(popupBindings, cachedContentForms, cfg, subForms)

    return {
      fields: popupFields,
      tabs: popupTabs,
      subTableBindings: popupBindings,
      linkedSubTableBindings: popupBindings,
      nativeSubTableBindingIds: nativeIds,
      formConfig: cfg,
      viewContext: 'assigneeTodo' as const,
    }
  },
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
  formPopupSubTableBindings,
  formPopupLinkedSubTableBindings,
  formPopupNativeSubTableBindingIds,
  formPopupFormConfig,
  formPopupViewContext,
  currentFormPopupAction: currentFormPopupActionRef,
  handleCustomAction,
  handleN8nActionExecuted,
  openFormPopup,
  submitFormPopup,
  handleFormPopupSubTableUpdate,
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

// Load function unit content (optional prefetched payload avoids duplicate HTTP when parallel with history)
const loadFunctionUnitContent = async (processKey: string, prefetchedContent?: any) => {
  processError.value = null
  try {
    const content: any =
      prefetchedContent ??
      (await processApi.getFunctionUnitContent(processKey).then(r => (r as any).data || r))
    if (content.error) {
      console.error('Function unit content error:', content.error)
      processError.value = t('task.processLoadFailed')
      return
    }

    cachedContentForms = content.forms || []
    cachedRelationTableFieldIndex = buildRelationTableFieldIndexFromDataTables(content.dataTables)
    
    let currentFormInfo: { formId: string | null, formName: string | null, readOnly: boolean } = { formId: null, formName: null, readOnly: false }
    
    // Parse process diagram
    if (content.processes?.length > 0) {
      // First get the current node formId and formName
      currentFormInfo = parseBpmnXmlAndGetFormId(content.processes[0].data)
      bpmnXml.value = content.processes[0].data
      parseBpmnXml(content.processes[0].data)
      refreshMiSubProcessScopeFromBpmn()
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
        } else {
          // If sourceId match fails, try matching by formName
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
      let subTablePortalViews: Record<string, any> = {}
      try {
        const cfg = typeof selectedForm.data === 'string' ? JSON.parse(selectedForm.data) : (selectedForm.data || {})
        formConfigForSubTables = cfg
        subForms = cfg.subForms || {}
        // Per-binding portal display config (configured in FormDesigner's sub-table tab).
        subTablePortalViews = cfg.subTablePortalViews || {}
      } catch {}

      // Load sub-table bindings for this form (SUB and RELATED, not PRIMARY)
      const bindings: typeof subTableBindings.value = []
      const tableBindings: any[] = selectedForm.tableBindings || []

      // Persist main-form designer context for selected-node FormRenderer parity (#1395):
      //  - mainFormConfig powers Link Form / portalViews resolution
      //  - mainFormNativeSubTableBindingIds tags "placed-on-canvas" bindings vs merged Link Form targets
      mainFormConfig.value = formConfigForSubTables
      mainFormNativeSubTableBindingIds.value = tableBindings
        .filter((b: any) => b.bindingType !== 'PRIMARY')
        .map((b: any) => Number(b.bindingId))
        .filter((n: number) => Number.isFinite(n))

      // When the PRIMARY table binding has bindingMode READONLY, force primary form fields read-only
      // without affecting sub-table editability (sub-tables check their own bindingMode).
      // (This is set via Form Designer → Manage Table Bindings → Edit → Binding Mode)
      const primaryBinding = tableBindings.find((b: any) => b.bindingType === 'PRIMARY')
      if (primaryBinding?.bindingMode === 'READONLY') {
        primaryReadOnly.value = true
      }

      for (const b of tableBindings) {
        if (b.bindingType === 'PRIMARY') continue
        const columns = deriveColumnsFromBinding(b, subForms, formConfigForSubTables)
        const subFormDesign = resolveSubFormDesign(b, subForms)
        // Per-binding portalViews lookup tolerates both numeric and string keys
        // (JSON.parse always yields strings, but designer code may have stored numeric keys).
        const bindingPortalViews =
          subTablePortalViews[b.bindingId] ?? subTablePortalViews[String(b.bindingId)] ?? null
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
          portalViews: bindingPortalViews,
          primaryKeyFields: resolveSubTablePrimaryKeyFields(
            b.primaryKeyFields,
            b.bindingId,
            formConfigForSubTables
          ),
          data: []
        })
      }
      // Link Form columns may reference bindings omitted from this form's tableBindings; merge from FU forms so bindingMap resolves subtable2.
      mergeLinkFormTargetBindingsInto(bindings, content.forms, formConfigForSubTables, subForms)
      const bindingRelationTableMap = buildBindingIdToRelationTableIdMap(content.forms as any[])
      lastBindingRelationTableMap.value = bindingRelationTableMap
      const rawSubTables = coerceSubTablesVariableToMap(formData.value.__subTables__)
      if (rawSubTables) {
        const flattened = JSON.parse(JSON.stringify(rawSubTables)) as Record<string, unknown>
        flattenNestedSubTableRowsIntoPayload(flattened)
        const ciLoad = (formData.value._currentItem ?? formData.value.currentItem) as
          | Record<string, unknown>
          | undefined
        const scopeLoad = miSubProcessScope.value
        const collBindingLoad = scopeLoad?.subTableName
          ? bindings.find(b => bindingMatchesMiSubTableName(b, scopeLoad.subTableName))
          : undefined
        const ridLoad = extractMiParticipantRowIdFromCurrentItem(ciLoad, collBindingLoad?.primaryKeyFields, {
          rowIdVariable: scopeLoad?.rowIdVariable ?? 'currentItem.rowId',
        })
        if (ridLoad != null) {
          scrubMiCorruptLinkChildRowsForParent(flattened, ridLoad)
        }
        formData.value = { ...formData.value, __subTables__: flattened }
      }
      const savedSubTables = formData.value.__subTables__
      if (savedSubTables && typeof savedSubTables === 'object') {
        const ambiguousMain = bindingIdsPreferStrictSubTableLookup(bindings)
        bindings.forEach(binding => {
          const saved = getSavedSubTableRows(savedSubTables, binding, ambiguousMain.has(binding.bindingId))
          if (saved) {
            binding.data = cloneSubTableRows(saved)
          }
        })
      }
      hydrateChildSubTablesFromParentsNestedRows(
        bindings,
        savedSubTables && typeof savedSubTables === 'object' ? (savedSubTables as Record<string, unknown>) : null,
        bindingRelationTableMap
      )
      if (savedSubTables && typeof savedSubTables === 'object') {
        hydrateBindingsRowsFromVariablesBySharedRelationTableId(
          bindings,
          savedSubTables as Record<string, unknown>,
          bindingRelationTableMap
        )
      }
      enrichChildBindingRowsFromParentsNestedSubTables(bindings)
      // When subForms have no rule, columns are empty causing no columns/assignee inference; infer columns from loaded row data
      bindings.forEach(binding => {
        if ((!binding.columns || binding.columns.length === 0) && binding.data?.length) {
          const row0 = binding.data[0]
          if (row0 && typeof row0 === 'object') {
            binding.columns = Object.keys(row0)
              .filter(k => !isSubTableRowMetaField(k))
              .map(k => {
              const inferred = inferColumnTypeFromFieldAndValue(k, row0[k])
              if (inferred === 'upload') {
                return mergeListViewFieldColumn(
                  { fieldName: k, comment: k, dataType: 'FILE' },
                  { field: k, label: k },
                  null,
                )
              }
              return { field: k, label: k, type: 'text' as const }
            })
          }
        }
        if (!isMiDashboardSubTableBinding(binding) && Array.isArray(binding.data)) {
          if (isMiParticipantScopedSubTableBinding(binding)) {
            binding.data = binding.data.map(row =>
              row && typeof row === 'object'
                ? stripSubTableRowMetaFields(row as Record<string, unknown>)
                : row
            )
          } else {
            binding.data = finalizeSharedProcessSubTableBindingRows(binding.data, binding)
          }
        }
      })
      if (savedSubTables && typeof savedSubTables === 'object') {
        applySharedAttachmentFinalizeAndMaterialize(
          bindings,
          formData.value as Record<string, unknown>,
          {
            flattened: savedSubTables as Record<string, unknown>,
            bindingTableById: bindingRelationTableMap,
          },
        )
      }
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
              const tabsRule = findTabsRule(rules)
              if (tabsRule?.children) {
                for (const tabPane of tabsRule.children as Record<string, unknown>[]) {
                  if (isTabPaneRule(tabPane) && tabPane.props) {
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
          let prevSubTablePortalViews: Record<string, any> = {}
          try {
            const cfg = typeof prevForm.data === 'string' ? JSON.parse(prevForm.data) : (prevForm.data || {})
            prevConfigForSubTables = cfg
            prevSubForms = cfg.subForms || {}
            prevSubTablePortalViews = cfg.subTablePortalViews || {}
          } catch {}
          const prevBindings: PreviousFormEntry['subTableBindings'] = []
          for (const b of (prevForm.tableBindings || [])) {
            if (b.bindingType === 'PRIMARY') continue
            const cols = deriveColumnsFromBinding(b, prevSubForms, prevConfigForSubTables)
            const subFormDesign = resolveSubFormDesign(b, prevSubForms)
            const bindingPortalViews =
              prevSubTablePortalViews[b.bindingId] ?? prevSubTablePortalViews[String(b.bindingId)] ?? null
            const binding = {
              bindingId: b.bindingId, tableId: b.tableId ?? null, bindingType: b.bindingType, bindingMode: b.bindingMode,
              foreignKeyField: b.foreignKeyField, tableName: b.tableDisplayName || b.tableName, physicalTableName: b.tableName,
              tableType: b.tableType, tableDescription: b.tableDescription, columns: cols,
              formFields: subFormDesign.formFields,
              formOptions: subFormDesign.formOptions,
              portalViews: bindingPortalViews,
              primaryKeyFields: resolveSubTablePrimaryKeyFields(
                b.primaryKeyFields,
                b.bindingId,
                prevConfigForSubTables
              ),
              data: [] as any[]
            }
            prevBindings.push(binding)
          }
          const ambiguousPrev = bindingIdsPreferStrictSubTableLookup(prevBindings as any[])
          if (savedSubTables && typeof savedSubTables === 'object') {
            for (const binding of prevBindings) {
              const saved = getSavedSubTableRows(savedSubTables, binding, ambiguousPrev.has(binding.bindingId))
              if (saved) binding.data = cloneSubTableRows(saved)
            }
          }
          hydrateChildSubTablesFromParentsNestedRows(
            prevBindings,
            savedSubTables && typeof savedSubTables === 'object' ? (savedSubTables as Record<string, unknown>) : null,
            bindingRelationTableMap
          )
          if (savedSubTables && typeof savedSubTables === 'object') {
            hydrateBindingsRowsFromVariablesBySharedRelationTableId(
              prevBindings,
              savedSubTables as Record<string, unknown>,
              bindingRelationTableMap
            )
          }
          enrichChildBindingRowsFromParentsNestedSubTables(prevBindings)
          rehydrateSharedAttachmentBindings(
            prevBindings,
            formData.value as Record<string, unknown>,
            savedSubTables && typeof savedSubTables === 'object'
              ? (savedSubTables as Record<string, unknown>)
              : null,
          )

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
        rehydrateSharedProcessSubTableBindings(savedSubTables as Record<string, unknown>)
      } else {
        previousForms.value = []
      }

      // Build node-to-form map for diagram click interaction
      try {
        const newMap = new Map<string, NodeFormInfo>()
        const bpmnData = content.processes[0]?.data
        if (bpmnData) {
          const doc = getCachedBpmnDocument(bpmnData)
          const allElements = doc?.getElementsByTagName('*')
          if (allElements) for (let i = 0; i < allElements.length; i++) {
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
            // Embedded subprocess shapes usually have no form reference on the container — Flowable/Camunda
            // attach formKey to the first inner userTask. Diagram clicks use subprocess id → nodeFormMap.
            if (localName === 'subProcess' && !formId && !formName) {
              const innerTasks = el.getElementsByTagName('userTask')
              for (let k = 0; k < innerTasks.length; k++) {
                let fid: string | null = null
                let fnm: string | null = null
                const inner = innerTasks[k]
                const iprops = inner.getElementsByTagName('*')
                for (let j = 0; j < iprops.length; j++) {
                  const p = iprops[j]
                  const ln = p.localName || p.nodeName.split(':').pop()
                  if (ln === 'property' || ln === 'values') {
                    const n = p.getAttribute('name'), v = p.getAttribute('value')
                    if (n === 'formId' && v) fid = v
                    if (n === 'formName' && v) fnm = v
                  }
                }
                if (fid || fnm) {
                  formId = fid
                  formName = fnm
                  break
                }
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
            // Hoisted so the newMap.set below can attach them to NodeFormInfo —
            // FormRenderer needs configJson (Link Form / portalViews) and the
            // native binding-id set to render the snapshot at parity with the
            // current task form (#1395 / portal-design-parity).
            let nodeFormConfig: Record<string, any> = {}
            const nodeNativeIds: number[] = []
            try {
              const cfg = typeof matchedForm.data === 'string' ? JSON.parse(matchedForm.data) : (matchedForm.data || {})
              nodeFormConfig = cfg
              const rules = cfg.rule && Array.isArray(cfg.rule) ? cfg.rule : (Array.isArray(cfg) ? cfg : null)
              if (rules) {
    const tabsRule = findTabsRule(rules)
    if (tabsRule?.children) {
      for (const tabPane of tabsRule.children as Record<string, unknown>[]) {
        if (isTabPaneRule(tabPane) && tabPane.props) {
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
              let subTablePortalViewsNode: Record<string, any> = {}
              try {
                configForSubTables = cfg
                subForms = cfg.subForms || {}
                subTablePortalViewsNode = cfg.subTablePortalViews || {}
              } catch {}
              for (const b of (matchedForm.tableBindings || [])) {
                if (b.bindingType === 'PRIMARY') continue
                const cols = deriveColumnsFromBinding(b, subForms, configForSubTables)
                const subFormDesign = resolveSubFormDesign(b, subForms)
                const bindingPortalViews =
                  subTablePortalViewsNode[b.bindingId] ?? subTablePortalViewsNode[String(b.bindingId)] ?? null
                const binding = {
                  bindingId: b.bindingId, tableId: b.tableId ?? null, bindingType: b.bindingType, bindingMode: b.bindingMode,
                  foreignKeyField: b.foreignKeyField, tableName: b.tableDisplayName || b.tableName, physicalTableName: b.tableName,
                  tableType: b.tableType, tableDescription: b.tableDescription, columns: cols,
                  formFields: subFormDesign.formFields,
                  formOptions: subFormDesign.formOptions,
                  portalViews: bindingPortalViews,
                  primaryKeyFields: resolveSubTablePrimaryKeyFields(
                    b.primaryKeyFields,
                    b.bindingId,
                    configForSubTables
                  ),
                  data: [] as any[]
                }
                nodeBindings.push(binding)
                const bid = Number(b.bindingId)
                if (Number.isFinite(bid)) nodeNativeIds.push(bid)
              }
              // Capture native bindings BEFORE Link Form merge so FormRenderer
              // can distinguish "placed on this form's canvas" vs "pulled in
              // as a Link Form target from another FU form".
              mergeLinkFormTargetBindingsInto(nodeBindings as any, content.forms, configForSubTables, subForms)
              const ambiguousNodeDiagram = bindingIdsPreferStrictSubTableLookup(nodeBindings as any[])
              const _stForNested = formData.value.__subTables__
              if (_stForNested && typeof _stForNested === 'object') {
                nodeBindings.forEach(binding => {
                  const saved = getSavedSubTableRows(_stForNested, binding, ambiguousNodeDiagram.has(binding.bindingId))
                  if (saved) binding.data = cloneSubTableRows(saved)
                })
              }
              hydrateChildSubTablesFromParentsNestedRows(
                nodeBindings,
                _stForNested && typeof _stForNested === 'object' ? (_stForNested as Record<string, unknown>) : null,
                bindingRelationTableMap
              )
              if (_stForNested && typeof _stForNested === 'object') {
                hydrateBindingsRowsFromVariablesBySharedRelationTableId(
                  nodeBindings,
                  _stForNested as Record<string, unknown>,
                  bindingRelationTableMap
                )
              }
              enrichChildBindingRowsFromParentsNestedSubTables(nodeBindings)
              rehydrateSharedAttachmentBindings(
                nodeBindings,
                formData.value as Record<string, unknown>,
                _stForNested && typeof _stForNested === 'object'
                  ? (_stForNested as Record<string, unknown>)
                  : null,
              )
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
              subTableBindings: nodeBindings,
              formConfig: nodeFormConfig,
              nativeSubTableBindingIds: nodeNativeIds,
            })
          }
        }
        nodeFormMap.value = newMap
        alignProcessSubTableBindingsBySharedTable()
      } catch (e) {
        console.warn('[NodeFormMap] Failed to build:', e)
        alignProcessSubTableBindingsBySharedTable()
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

type PrefetchedTaskForms = {
  pfData: ProcessFormData | null
  tfData: TaskFormDataDTO | null
  ctData: CompletedTaskFormData | null
}

/** Start process + task form HTTP in parallel with history / function-unit fetch. */
function prefetchProcessAndTaskFormData(taskData: any): Promise<PrefetchedTaskForms> {
  const processInstanceId = taskData.processInstanceId
  const currentTaskId = taskData.id || taskId
  const isCompleted = isCompletedTaskData(taskData) || hasCompletedSnapshotRoute()

  const pfPromise = processInstanceId
    ? getProcessFormData(processInstanceId)
        .then(r => ((r as { data?: ProcessFormData }).data ?? r) as ProcessFormData)
        .catch((e: unknown) => {
          console.warn('[detail] Failed to load process form data:', e)
          return null
        })
    : Promise.resolve(null)

  const taskFormPromise = currentTaskId
    ? (isCompleted ? getCompletedTaskFormData(currentTaskId) : fetchTaskFormData(currentTaskId))
        .then(r => {
          if (isCompleted) {
            return ((r as { data?: CompletedTaskFormData }).data ?? r) as CompletedTaskFormData
          }
          return ((r as { data?: TaskFormDataDTO }).data ?? r) as TaskFormDataDTO
        })
        .catch((e: unknown) => {
          console.warn(
            `[detail] Failed to load ${isCompleted ? 'completed' : 'task'} form data:`,
            e,
          )
          return null
        })
    : Promise.resolve(null)

  return Promise.all([pfPromise, taskFormPromise]).then(([pfData, taskFormRaw]) => ({
    pfData,
    tfData: !isCompleted ? (taskFormRaw as TaskFormDataDTO | null) : null,
    ctData: isCompleted ? (taskFormRaw as CompletedTaskFormData | null) : null,
  }))
}

// Task 17: Load Process Form and Task Form data
const loadProcessAndTaskFormData = async (taskData: any, prefetched?: PrefetchedTaskForms) => {
  const currentTaskId = taskData.id || taskId
  const isCompleted = isCompletedTaskData(taskData) || hasCompletedSnapshotRoute()
  const { pfData, tfData, ctData } = prefetched ?? (await prefetchProcessAndTaskFormData(taskData))

  if (pfData) {
    processFormData.value = pfData
    processFormValues.value = pfData.fieldValues || {}

    const normalizedProcessState = String(pfData.processState ?? '').trim().toUpperCase()
    if (normalizedProcessState === 'RETURN_TO_REQUESTER' && pfData.editable) {
      isReturnToRequester.value = true
      processFormEditable.value = true
      processFormCollapse.value = ['processForm']
      if (bpmnXml.value) {
        parseBpmnXml(bpmnXml.value)
      }
    }

    if (pfData.configJson) {
      parseProcessFormConfig(pfData.configJson)
      buildProcessFormSubTableBindings(pfData)
    } else {
      processFormSubTableBindings.value = []
      processFormFormConfig.value = {}
      processFormNativeSubTableBindingIds.value = []
    }
  } else {
    processFormSubTableBindings.value = []
    processFormFormConfig.value = {}
    processFormNativeSubTableBindingIds.value = []
  }

  if (currentTaskId) {
    if (isCompleted) {
      isCompletedTask.value = true
      formReadOnly.value = true
      if (ctData) {
        completedFormData.value = ctData
        applyCompletedSnapshotToForm(ctData)
      }
    } else if (tfData) {
      taskFormDTO.value = tfData
      const taskFormNodeReadOnly = tfData.formReadOnly === true
      if (taskFormNodeReadOnly) {
        formReadOnly.value = true
      }
      if (tfData.formName) {
        currentFormName.value = tfData.formName
      }
      if (tfData.configJson) {
        parseFormConfig(tfData.configJson as any)
      }
      if (taskFormNodeReadOnly) {
        formReadOnly.value = true
      }
      if (tfData.configJson && tfData.fieldPermissions) {
        const perms = Object.values(tfData.fieldPermissions || {})
        if (perms.length > 0 && perms.every((p: unknown) => String(p).toUpperCase() === 'READONLY')) {
          formReadOnly.value = true
        }
      }
      if (tfData.fieldValues) {
        mergeIncomingTaskFormFieldValues(tfData.fieldValues as Record<string, any>, taskData)
      }
    }
  }
  refreshNodeFormMapFromFormData()
  rehydrateSharedProcessSubTableBindings()
}

// Parse Process Form config into FormRenderer fields
const parseProcessFormConfig = (configJson: Record<string, unknown>) => {
  try {
    const config = configJson
    const rules = (config as any).rule && Array.isArray((config as any).rule)
      ? (config as any).rule
      : (Array.isArray(config) ? config : null)
    if (!rules) return

    const tabsRule = findTabsRule(rules)
    if (tabsRule?.children && Array.isArray(tabsRule.children)) {
      const tabs: FormTab[] = []
      for (const tabPane of tabsRule.children as Record<string, unknown>[]) {
        if (isTabPaneRule(tabPane) && tabPane.props) {
          const props = tabPane.props as Record<string, unknown>
          const tabFields: FormField[] = []
          if (tabPane.children) tabFields.push(...extractFieldsRecursive(tabPane.children as Record<string, unknown>[]))
          tabs.push({
            name: String(props.name || `tab_${tabs.length}`),
            label: String(props.label || `Tab ${tabs.length + 1}`),
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

/**
 * Build FormRenderer-ready sub-table bindings for the Process Form panel.
 *
 * The backend ProcessFormData.subTableBindings DTO carries only the minimal
 * fields (bindingId / tableName / columns / data). Designer-side metadata
 * needed by FormRenderer (subForms row layout, portalViews, primaryKeyFields)
 * lives in configJson. Merge them here so the panel renders at parity with
 * the Designer Form Preview / main task form (portal-design-parity).
 *
 * Skips PRIMARY bindings (those drive flat fields, not sub-table widgets) and
 * coerces bindingId to Number for downstream binding lookup.
 */
function buildProcessFormSubTableBindings(pfData: ProcessFormData) {
  const cfg = (pfData.configJson || {}) as Record<string, any>
  const subForms = (cfg.subForms || {}) as Record<string, any>
  const subTablePortalViewsCfg = (cfg.subTablePortalViews || {}) as Record<string, any>
  const bindings: typeof subTableBindings.value = []
  const nativeIds: number[] = []
  for (const b of (pfData.subTableBindings || [])) {
    if (b.bindingType === 'PRIMARY') continue
    // resolveSubFormDesign signature: ({ bindingId }, subForms) → uses bindingId to look up cfg.subForms[bindingId].
    const subFormDesign = resolveSubFormDesign({ bindingId: b.bindingId } as any, subForms)
    const bindingPortalViews =
      subTablePortalViewsCfg[b.bindingId as any]
        ?? subTablePortalViewsCfg[String(b.bindingId)]
        ?? null
    bindings.push({
      bindingId: b.bindingId,
      tableId: null,
      bindingType: b.bindingType,
      bindingMode: b.bindingMode,
      foreignKeyField: null,
      tableName: (b as any).tableDisplayName || b.tableName,
      physicalTableName: b.tableName,
      tableType: '',
      tableDescription: '',
      columns: Array.isArray(b.columns) ? (b.columns as any[]) : [],
      formFields: subFormDesign.formFields,
      formOptions: subFormDesign.formOptions,
      portalViews: bindingPortalViews,
      primaryKeyFields: resolveSubTablePrimaryKeyFields(null, b.bindingId, cfg),
      data: Array.isArray(b.data) ? (b.data as any[]) : [],
    } as any)
    const bid = Number(b.bindingId)
    if (Number.isFinite(bid)) nativeIds.push(bid)
  }
  processFormSubTableBindings.value = bindings
  processFormFormConfig.value = cfg
  processFormNativeSubTableBindingIds.value = nativeIds
}

// Task 17.4: Submit Process Form update (Return_To_Requester state)
const handleProcessFormSubmit = async () => {
  if (!taskInfo.value.processInstanceId) return
  if (processFormRendererRef.value) {
    const valid = await processFormRendererRef.value.validate()
    if (!valid) {
      ElMessage.warning(t('processStart.pleaseCompleteForm'))
      return
    }
  }
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
      
      const layout = parseFormRulesLayout(rules, (items) => extractFieldsRecursive(items))
      formTabs.value = layout.tabs
      formFields.value = layout.fields
      formFieldsAfterTabs.value = layout.fieldsAfterTabs
      formFormOptions.value = (config.options && typeof config.options === 'object') ? config.options : {}
    }
    // Check if form is read-only (never clear node/BPMN/task-form readonly already applied)
    formReadOnly.value =
      formReadOnly.value === true
      || config.formReadOnly === true
      || config.formReadOnly === 'true'
  } catch (error) {
    console.error('Failed to parse form config:', error)
  }
}

function isPortalSharedAttachmentTableBinding(b: {
  bindingId?: number
  tableId?: number | null
  tableName?: string
  foreignKeyField?: string | null
}): boolean {
  const tableIdNum = b.tableId != null ? Number(b.tableId) : NaN
  const tn = normalizeSubTableName(String(b.tableName ?? ''))
  if (Number.isFinite(tableIdNum) && tableIdNum === SHARED_ATTACHMENT_RELATION_TABLE_ID) return true
  if (tn === 'attachment') return true
  return String(b.foreignKeyField ?? '').trim().toLowerCase() === 'main_id' && tn === 'attachment'
}

// Derive display columns for a sub-table binding based on table metadata
const deriveColumnsFromBinding = (binding: any, subForms?: Record<string, any>, config?: Record<string, any>): Array<{ field: string; label: string; type?: string; required?: boolean; options?: Array<{ label: string; value: any }>; props?: Record<string, any> }> => {
  // Consistent with process/start: prefer subFormConfig on binding, then configJson.subForms (supports string/number key)
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

      const required = isFormCreateRuleRequired(r as Record<string, unknown>)
      // form-create uses `disabled` to mark a field as read-only
      const readonly = isFormCreateRuleReadonly(r)

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

  const listColumns = resolveSubListViewColumnsForBinding(
    config,
    binding.bindingId,
    subFormColumns.map(c => c.field),
  )

  // 与 applications/detail 一致：只要有「列表视图」列（含 linkForm），即使用之；不能强依赖 subForm 行布局存在，否则 Link 列不会进运行时而无法解析 subtable2。
  if (Array.isArray(listColumns) && listColumns.length > 0) {
    const ruleByField = new Map(
      (Array.isArray(subFormRule) ? subFormRule : []).map((ruleItem: any) => [ruleItem?.field, ruleItem])
    )
    const subFormColumnByField = new Map(subFormColumns.map(col => [col.field, col]))
    const assigneeField = resolveAssigneeFieldForBinding(
      subFormColumns as Array<{ field?: string }>,
      binding.tableDisplayName || binding.tableName
    )
    const mappedOut = listColumns.map((column: any) => {
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

      return mergeListViewFieldColumn(column, baseColumn, fieldRule)
    })
    return mappedOut
  }

  if (subFormColumns.length > 0) return subFormColumns

  const tableId = binding.tableId != null ? Number(binding.tableId) : NaN
  if (Number.isFinite(tableId)) {
    const alt = resolveSubTableSchemaByTableId(tableId, cachedContentForms, binding.bindingId)
    if (alt) {
      const fromAlt = deriveColumnsFromBinding(
        { ...binding, bindingId: alt.bindingId },
        alt.subForms,
        alt.formConfig,
      )
      if (fromAlt.length > 0) return fromAlt
    }
    const tableFields = cachedRelationTableFieldIndex.get(tableId)
    if (tableFields?.length) {
      const fromTable = deriveColumnsFromRelationFieldDefinitions(tableFields)
      if (fromTable.length > 0) return fromTable
    }
  }

  if (isPortalSharedAttachmentTableBinding(binding)) {
    return defaultAttachmentListColumns()
  }

  return subFormColumns
}

/** Owning form JSON for a binding id (any form in the function unit may declare the binding). */
function findRawBindingInForms(
  forms: any[] | undefined,
  bindingId: number
): { raw: any; formConfig: Record<string, any> } | null {
  if (!forms?.length) return null
  for (const f of forms) {
    const list = f.tableBindings || []
    const hit = list.find((x: any) => Number(x.bindingId) === Number(bindingId))
    if (hit) {
      let formConfig: Record<string, any> = {}
      try {
        formConfig = typeof f.data === 'string' ? JSON.parse(f.data || '{}') : (f.data || {})
      } catch {
        formConfig = {}
      }
      return { raw: hit, formConfig }
    }
  }
  return null
}

function linkTargetHasLocalSchema(tid: number, formConfig: Record<string, any>, subForms: Record<string, any>): boolean {
  const sid = String(tid)
  const sf = subForms?.[tid] ?? subForms?.[sid]
  if (sf?.rule && Array.isArray(sf.rule) && sf.rule.length > 0) return true
  const lv = formConfig?.subListViews?.[tid] ?? formConfig?.subListViews?.[sid]
  if (lv?.columns && Array.isArray(lv.columns) && lv.columns.length > 0) return true
  return false
}

/**
 * subForms/subListViews for a binding id may be saved under **another** form in the same function unit
 * (e.g. subtable2 edited on form B while the user task runs form A that only links to it).
 */
function resolveSubTableSchemaSourceForTarget(
  tid: number,
  preferFormConfig: Record<string, any>,
  preferSubForms: Record<string, any>,
  contentForms: any[] | undefined
): {
  formConfig: Record<string, any>
  subForms: Record<string, any>
  origin: 'local' | 'crossForm'
  sourceFormName?: string
} | null {
  if (linkTargetHasLocalSchema(tid, preferFormConfig, preferSubForms)) {
    return { formConfig: preferFormConfig, subForms: preferSubForms, origin: 'local' }
  }
  if (!contentForms?.length) return null
  for (const f of contentForms) {
    let formConfig: Record<string, any> = {}
    try {
      formConfig = typeof f.data === 'string' ? JSON.parse(f.data || '{}') : (f.data || {})
    } catch {
      formConfig = {}
    }
    const sf = formConfig.subForms || {}
    if (linkTargetHasLocalSchema(tid, formConfig, sf)) {
      return {
        formConfig,
        subForms: sf,
        origin: 'crossForm',
        sourceFormName: f.name != null ? String(f.name) : undefined
      }
    }
  }
  const found = findRawBindingInForms(contentForms, tid)
  const tableId = found?.raw?.tableId != null ? Number(found.raw.tableId) : NaN
  if (Number.isFinite(tableId)) {
    const alt = resolveSubTableSchemaByTableId(tableId, contentForms, tid)
    if (alt) {
      return {
        formConfig: alt.formConfig,
        subForms: alt.subForms,
        origin: 'crossForm',
        sourceFormName: undefined,
      }
    }
  }
  return null
}

/**
 * Link Form targets may reference bindings omitted from the active form's tableBindings slice.
 * Pull those definitions from any form in the function unit so FormRenderer can resolve inline subtable2.
 * If the target never appears in any FU form's tableBindings (API slice gap), fall back to the **current**
 * form's configJson.subForms / subListViews — designer often still stores subtable2 schema there.
 */
function mergeLinkFormTargetBindingsInto(
  bindings: typeof subTableBindings.value,
  contentForms: any[] | undefined,
  localFormConfig: Record<string, any>,
  localSubForms: Record<string, any>
) {
  const known = new Set(bindings.map(b => Number(b.bindingId)))
  let changed = true
  while (changed) {
    changed = false
    const targetIds = new Set<number>()
    const targetNameHint = new Map<number, string | undefined>()
    for (const b of bindings) {
      for (const col of b.columns || []) {
        if ((col as { type?: string }).type !== 'linkForm') continue
        const rawTid = (col as { props?: { boundSubTableBindingId?: number | string; boundSubTableName?: string } }).props
          ?.boundSubTableBindingId
        if (rawTid == null || rawTid === '') continue
        const n = Number(rawTid)
        if (Number.isNaN(n)) continue
        targetIds.add(n)
        if (!targetNameHint.has(n)) {
          const nm = (col as { props?: { boundSubTableName?: string } }).props?.boundSubTableName
          targetNameHint.set(n, nm != null && String(nm).trim() !== '' ? String(nm) : undefined)
        }
      }
    }
    for (const tid of targetIds) {
      if (known.has(tid)) continue
      const found = findRawBindingInForms(contentForms, tid)
      if (found) {
        const { raw, formConfig } = found
        if (raw.bindingType === 'PRIMARY') continue
        const sf = formConfig.subForms || {}
        const schemaSrc =
          resolveSubTableSchemaSourceForTarget(tid, formConfig, sf, contentForms) ??
          ({ formConfig, subForms: sf, origin: 'local' as const } as const)
        const effFormConfig = schemaSrc.formConfig
        const effSubForms = schemaSrc.subForms
        const columns = deriveColumnsFromBinding(raw, effSubForms, effFormConfig)
        const subFormDesign = resolveSubFormDesign(raw, effSubForms)
        const stpv = effFormConfig.subTablePortalViews || {}
        const bindingPortalViews = stpv[raw.bindingId] ?? stpv[String(raw.bindingId)] ?? null
        bindings.push({
          bindingId: raw.bindingId,
          tableId: raw.tableId ?? null,
          bindingType: raw.bindingType,
          bindingMode: raw.bindingMode,
          foreignKeyField: raw.foreignKeyField,
          tableName: raw.tableDisplayName || raw.tableName,
          physicalTableName: raw.tableName,
          tableType: raw.tableType,
          tableDescription: raw.tableDescription,
          columns,
          formFields: subFormDesign.formFields,
          formOptions: subFormDesign.formOptions,
          portalViews: bindingPortalViews,
          primaryKeyFields: resolveSubTablePrimaryKeyFields(
            raw.primaryKeyFields,
            raw.bindingId,
            effFormConfig
          ),
          data: []
        })
        known.add(Number(raw.bindingId))
        changed = true
        continue
      }
      const syntheticSchema = resolveSubTableSchemaSourceForTarget(
        tid,
        localFormConfig,
        localSubForms,
        contentForms
      )
      if (!syntheticSchema) {
        continue
      }
      const hint = targetNameHint.get(tid)
      const tableLabel = hint && String(hint).trim() ? String(hint).trim() : `binding_${tid}`
      const synthetic = {
        bindingId: tid,
        tableId: null as number | null,
        bindingType: 'SUB',
        bindingMode: 'EDITABLE',
        foreignKeyField: null as string | null,
        tableName: tableLabel,
        physicalTableName: tableLabel,
        tableType: '',
        tableDescription: ''
      }
      const columns = deriveColumnsFromBinding(synthetic, syntheticSchema.subForms, syntheticSchema.formConfig)
      const subFormDesign = resolveSubFormDesign(synthetic, syntheticSchema.subForms)
      const stpvSchema = syntheticSchema.formConfig.subTablePortalViews || {}
      const bindingPortalViews = stpvSchema[tid] ?? stpvSchema[String(tid)] ?? null
      bindings.push({
        bindingId: tid,
        tableId: null,
        bindingType: synthetic.bindingType,
        bindingMode: synthetic.bindingMode,
        foreignKeyField: synthetic.foreignKeyField,
        tableName: tableLabel,
        physicalTableName: synthetic.physicalTableName,
        tableType: synthetic.tableType,
        tableDescription: synthetic.tableDescription,
        columns,
        formFields: subFormDesign.formFields,
        formOptions: subFormDesign.formOptions,
        portalViews: bindingPortalViews,
        primaryKeyFields: resolveSubTablePrimaryKeyFields(null, tid, syntheticSchema.formConfig),
        data: []
      })
      known.add(tid)
      changed = true
    }
  }
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

// form-create runtime-only nodes: do not emit as fields, but **must** fall through to the
// `item.children` recursion below — sub-table row layouts are wrapped in subForm/tableForm.
const FC_SKIP_TYPES = new Set(['subForm', 'tableForm', 'tableFormColumn'])

// Recursively extract fields
const extractFieldsRecursive = (items: any[]): FormField[] => {
  const fields: FormField[] = []
  for (const item of items) {
    if (item.field && isFormCreateRuleHidden(item)) {
      continue
    }
    const bindingId = item._bindingId ?? item.props?._bindingId
    if (item.type === 'subTable' && bindingId != null) {
      if (isFormCreateRuleHidden(item)) {
        continue
      }
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
      continue
    }
    const auxField = convertAuxiliaryLayoutField(item, fields.length)
    if (auxField) {
      fields.push(auxField)
      continue
    }
    if (isRowRule(item)) {
      fields.push({
        key: getLayoutKey(item, fields.length, 'row'),
        label: '',
        type: 'row',
        span: 24,
        gutter: getRowGutter(item),
        children: extractRowColumnFields(item, (children) => extractFieldsRecursive(children)),
      } as any)
      continue
    } else if (isColRule(item)) {
      fields.push({
        key: getLayoutKey(item, fields.length, 'col'),
        label: '',
        type: 'col',
        span: getColSpan(item),
        children: extractFieldsRecursive(getRuleChildren(item)),
      } as any)
      continue
    }
    if (isTabsRule(item)) {
      const nestedTabs = extractTabsFromTabsRule(item, (children) => extractFieldsRecursive(children))
      if (nestedTabs.length > 0) {
        fields.push({
          key: getLayoutKey(item, fields.length, 'tabs'),
          label: '',
          type: 'tabs',
          span: 24,
          tabs: nestedTabs,
        } as any)
      }
      continue
    }
    if (isCollapseRule(item)) {
      const collapsePanels = extractCollapsePanelsFromRule(item, (children) => extractFieldsRecursive(children))
      if (collapsePanels.length > 0) {
        fields.push({
          key: getLayoutKey(item, fields.length, 'collapse'),
          label: '',
          type: 'collapse',
          span: 24,
          collapsePanels,
        } as any)
      }
      continue
    }
    if (isCardRule(item)) {
      fields.push({
        key: getLayoutKey(item, fields.length, 'card'),
        label: getLayoutLabel(item),
        type: 'card',
        span: item.col?.span || 24,
        children: extractFieldsRecursive(getRuleChildren(item))
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
        _lookupViewFields: lookupCfg.showBackfillView === false ? [] : resolvedViewFields,
        _lookupShowBackfillView: lookupCfg.showBackfillView !== false
      }
      if (isFormCreateRuleReadonly(item)) {
        field.readonly = true
      }
      fields.push(field)
    } else if (FC_SKIP_TYPES.has(item.type)) {
      // Traverse children only (see block below); `continue` would drop all nested row fields.
    } else if (item.field) {
      const field = convertFormCreateRule(item)
      if (field) fields.push(field)
    }
    const childItems = getRuleChildren(item)
    if (childItems.length > 0) {
      if (FC_SKIP_TYPES.has(item.type) || (!isCardRule(item) && !isRowRule(item) && !isColRule(item) && !isTabsRule(item) && !isCollapseRule(item))) {
        fields.push(...extractFieldsRecursive(childItems))
      }
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
  const field: FormField = { key: rule.field, label: rule.title || rule.field, type: typeMap[rule.type] || 'text', placeholder: rule.props?.placeholder || '', span: rule.col?.span || 24 }
  applyFormCreateValidationToFormField(field, rule as Record<string, unknown>)
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
  if (rule.type === 'userSelect' || rule.type === 'user') {
    field.type = 'user'
  }
  if (isFormCreateRuleReadonly(rule)) {
    field.readonly = true
  }
  applyRuleDefaultToFormField(field, rule as Record<string, unknown>)
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
  clearBpmnParseCache()
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
