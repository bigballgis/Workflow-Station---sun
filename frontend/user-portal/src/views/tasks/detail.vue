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

      <div
        v-if="detailUiPhase === 1"
        class="section form-section"
      >
        <el-skeleton
          animated
          :rows="6"
        />
      </div>

      <!-- Section 2: Process diagram (async chunk + viewport gate — bpmn-js init is expensive) -->
      <div
        v-if="detailUiPhase >= 3"
        ref="workflowSectionRef"
        class="section workflow-section"
      >
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
            v-if="processError && !bpmnXml && processNodes.length === 0"
            :title="processError"
            type="warning"
            show-icon
            :closable="false"
          />
          <template v-if="bpmnXml || processNodes.length > 0">
            <el-skeleton
              v-if="!diagramInViewport"
              animated
              :rows="4"
            />
            <Suspense v-else>
              <ProcessDiagramAsync
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
              <template #fallback>
                <el-skeleton
                  animated
                  :rows="4"
                />
              </template>
            </Suspense>
          </template>
          <el-empty
            v-else
            :description="t('task.noProcessDefinition')"
          />
        </div>
      </div>

      <!-- Selected node form (click a node in the diagram to show its form) -->
      <div
        v-if="detailUiPhase >= 2 && selectedNodeId && selectedNodeForm"
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
              :function-unit-id="functionUnitIdRef"
              :primary-table-binding="primaryTableBinding ?? undefined"
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
        v-else-if="detailUiPhase >= 2 && selectedNodeId && !selectedNodeForm"
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
        v-if="detailUiPhase >= 2 && showProcessFormPanel && processFormData"
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
                :function-unit-id="functionUnitIdRef"
                :primary-table-binding="primaryTableBinding ?? undefined"
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
        v-if="detailUiPhase >= 2 && !selectedNodeId && (!isMiSubTaskMode || formFields.length > 0 || formTabs.length > 0 || formFieldsAfterTabs.length > 0 || subTableBindings.length > 0)"
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
              v-if="formRenderReady"
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
              :function-unit-id="functionUnitIdRef"
              :primary-table-binding="primaryTableBinding ?? undefined"
              @update:model-value="val => formData = { ...formData, ...val }"
              @update:sub-table-data="syncMainSubTableRows"
              @save="saveCurrentTaskFormWithMiPersist"
            />
          </div>
          <el-empty :description="t('task.noFormData')" />
        </div>
      </div>

      <!-- Task 17.3: Completed task snapshot comparison view -->
      <TaskSnapshotSection
        v-if="detailUiPhase >= 3"
        :is-completed-task="isCompletedTask"
        :completed-form-data="completedFormData"
        :form-fields="formFields"
        :form-tabs="formTabs"
      />

      <!-- Task 19.2: Change history panel (title and collapse handled internally by ChangeHistoryPanel) -->
      <div
        v-if="detailUiPhase >= 3 && taskInfo.processInstanceId"
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
        v-if="detailUiPhase >= 3"
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
        @save="saveCurrentTaskFormWithMiPersist"
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
import { onMounted, onBeforeUnmount, computed, nextTick, watch, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  getTaskDetail,
  TaskActionInfo
} from '@/api/task'
import { processApi } from '@/api/process'
import type { ProcessNode, ProcessFlow } from '@/components/ProcessDiagram.vue'

/** Lazy-load bpmn-js (~190kB gzip) only when the diagram section enters the viewport. */
const ProcessDiagramAsync = defineAsyncComponent(
  () => import('@/components/ProcessDiagram.vue'),
)
import FormRenderer from '@/components/FormRenderer.vue'
import SubTableField from '@/components/SubTableField.vue'
import N8nActionDialog from '@/components/N8nActionDialog.vue'
import {
  allSubTableRowsHaveAssignee
} from '@/utils/subTableAssignment'
import {
  cloneSubTableRows,
  coerceSubTablesVariableToMap,
  enrichChildBindingRowsFromParentsNestedSubTables,
  isMiParticipantScopedSubTableBinding,
  miParentRowAlignsWithChildRow,
} from '@/composables/tasks/shared'
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
import { clearBpmnParseCache } from '@/utils/bpmnParseCache'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'
import { reconcilePortalWorkspaceSession } from '@/api/auth'
import type { TaskDetailCtx } from '@/composables/taskDetail/context'
import { createTaskDetailState } from '@/composables/taskDetail/useTaskDetailState'
import {
  cloneSubTableRows as cloneSubTableRowsImpl,
  cloneAndFlattenSubTablesMap,
  yieldToMain,
} from '@/composables/taskDetail/subTableRowUtils'
import { createTaskDetailFormSchema } from '@/composables/taskDetail/useTaskDetailFormSchema'
import { createTaskDetailFieldExtraction } from '@/composables/taskDetail/useTaskDetailFieldExtraction'
import { createTaskDetailLinkTargets } from '@/composables/taskDetail/useTaskDetailLinkTargets'
import { createTaskDetailMiScope } from '@/composables/taskDetail/useTaskDetailMiScope'
import { createTaskDetailSubTableHydration } from '@/composables/taskDetail/useTaskDetailSubTableHydration'
import { createTaskDetailSubTableSync } from '@/composables/taskDetail/useTaskDetailSubTableSync'
import { createTaskDetailMiLinkChild } from '@/composables/taskDetail/useTaskDetailMiLinkChild'
import { createTaskDetailMiBackfill } from '@/composables/taskDetail/useTaskDetailMiBackfill'
import { createTaskDetailMiIsolation } from '@/composables/taskDetail/useTaskDetailMiIsolation'
import { createTaskDetailMiResync } from '@/composables/taskDetail/useTaskDetailMiResync'
import { createTaskDetailMiPersist } from '@/composables/taskDetail/useTaskDetailMiPersist'
import { createTaskDetailLayoutSync } from '@/composables/taskDetail/useTaskDetailLayoutSync'
import { createTaskDetailNodeFormMap } from '@/composables/taskDetail/useTaskDetailNodeFormMap'
import { createTaskDetailDiagram } from '@/composables/taskDetail/useTaskDetailDiagram'
import { createTaskDetailFuLoader } from '@/composables/taskDetail/useTaskDetailFuLoader'
import { createTaskDetailPrevForms } from '@/composables/taskDetail/useTaskDetailPrevForms'
import { createTaskDetailFormsLoader } from '@/composables/taskDetail/useTaskDetailFormsLoader'
import { createTaskDetailPopupHelpers } from '@/composables/taskDetail/useTaskDetailPopup'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const taskId = route.params.id as string
const fallbackProcessInstanceId = computed(() => {
  const v = route.query.processInstanceId
  return typeof v === 'string' && v.trim() ? v.trim() : ''
})

// Page state (refs / computeds) — declarations live in composables/taskDetail/useTaskDetailState.ts
const state = createTaskDetailState({ taskId })
const {
  loading,
  detailUiPhase,
  workflowSectionRef,
  diagramInViewport,
  submitting,
  taskInfo,
  effectiveTaskId,
  taskError,
  processError,
  historyError,
  selectedNodeId,
  nodeFormMap,
  formRenderReady,
  fuFormSubTableFields,
  lastBindingRelationTableMap,
  selectedNodeForm,
  previousForms,
  subTableBindings,
  linkableSubTableBindings,
  isMiSubTaskMode,
  miFullSubTablesSnapshotRef,
  miFillDialogVisible,
  miFillDialogData,
  miFillSubTableBindings,
  miFilled,
  miFillDialogReadOnly,
  historyRecords,
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
  processFormData,
  processFormCollapse,
  processFormEditable,
  processFormFields,
  processFormTabs,
  processFormValues,
  processFormSubTableBindings,
  processFormFormConfig,
  processFormNativeSubTableBindingIds,
  taskFormDTO,
  showImplicitSaveAction,
  completedFormData,
  isCompletedTask,
  miSubProcessScopeName,
  isReturnToRequester,
  showProcessFormPanel,
  primaryReadOnly,
  primaryTableBinding,
  functionUnitIdRef,
  mainFormConfig,
  mainFormNativeSubTableBindingIds,
  allowSubTableAssignForCurrentTask,
} = state

watch(
  () => detailUiPhase.value >= 3,
  (active) => {
    if (!active) {
      diagramInViewport.value = false
      disconnectDiagramViewportObserver()
      return
    }
    void buildNodeFormMapIfNeeded()
    void nextTick(() => {
      connectDiagramViewportObserver()
      // Fallback: if observer misses (already in viewport), still mount diagram after phase 3.
      setTimeout(() => {
        if (detailUiPhase.value >= 3 && !diagramInViewport.value && (bpmnXml.value || processNodes.value.length > 0)) {
          diagramInViewport.value = true
        }
      }, 400)
    })
  },
)

// Display helpers
const taskDisplay = useTaskDisplay(taskInfo as any)
const {
  formatDate,
  getCurrentAssigneeDisplay,
  getPriorityLabel,
  getPriorityType,
  getButtonType,
  getActionLabel,
  getIconComponent
} = taskDisplay

const hasConfiguredSaveAction = computed(() =>
  (taskInfo.value.actions || []).some(action => (action.actionType || '').trim().toUpperCase() === 'SAVE')
)

const bpmnParser = useBpmnParser({ taskInfo: taskInfo as any, historyRecords, isCompletedTask })
const { processNodes, processFlows, completedNodeIds, currentNodeId, bpmnXml, parseBpmnXml } = bpmnParser

const taskForm = useTaskForm({ subTableBindings, isMiSubTaskMode, isCompletedTask, effectiveTaskId, taskFormDTO: taskFormDTO as any, bindingRelationTableMap: lastBindingRelationTableMap, miSubProcessScopeName })
const { formFields, formTabs, formFieldsAfterTabs, formData, currentFormName, formReadOnly, formLabelWidth, formFormOptions, savingTaskForm, saveCurrentTaskForm, buildCurrentTaskFormSubmitPayload, getCurrentFormFieldKeys, clearAutosaveTimer: clearFormAutosaveTimer } = taskForm

/** Local alias preserves the original setup-scope shadowing of the shared import. */
const cloneSubTableRows = cloneSubTableRowsImpl

/**
 * Shared mutable context — the function clusters extracted to
 * composables/taskDetail/* register themselves here. Cross-module calls
 * resolve through ctx at invocation time (post-setup), mirroring the
 * original single-file function hoisting semantics.
 */
const ctx = {
  ...state,
  t,
  route,
  taskId,
  taskForm,
  bpmn: bpmnParser,
  display: taskDisplay,
} as unknown as TaskDetailCtx
Object.assign(ctx, createTaskDetailMiScope(ctx))
Object.assign(ctx, createTaskDetailFormSchema(ctx))
Object.assign(ctx, createTaskDetailFieldExtraction(ctx))
Object.assign(ctx, createTaskDetailLinkTargets(ctx))
Object.assign(ctx, createTaskDetailSubTableHydration(ctx))
Object.assign(ctx, createTaskDetailSubTableSync(ctx))
Object.assign(ctx, createTaskDetailMiLinkChild(ctx))
Object.assign(ctx, createTaskDetailMiBackfill(ctx))
Object.assign(ctx, createTaskDetailMiIsolation(ctx))
Object.assign(ctx, createTaskDetailMiResync(ctx))
Object.assign(ctx, createTaskDetailMiPersist(ctx))
Object.assign(ctx, createTaskDetailLayoutSync(ctx))
Object.assign(ctx, createTaskDetailNodeFormMap(ctx))
Object.assign(ctx, createTaskDetailDiagram(ctx))
Object.assign(ctx, createTaskDetailPrevForms(ctx))
Object.assign(ctx, createTaskDetailFuLoader(ctx))
Object.assign(ctx, createTaskDetailFormsLoader(ctx))

const {
  currentMiRowId,
  rehydrateSharedAttachmentBindings,
  resolveCurrentMiParticipantRowIdFromTaskVars,
  isMiSubTask,
  sanitizeMiCollectionBindingsData,
  isolateMiSubTaskData,
  applyMiParticipantFilterToCurrentSubTableBindings,
  resyncMiParticipantSubTablesFromVariables,
  mergePriorStepSubTablesAfterMiIsolate,
  hydrateMiLinkChildBindingsFromFullSnapshot,
  scopeMiSubTaskBindingsToCurrentParticipant,
  syncMiLinkChildRowsIntoParentNested,
  rehydrateSharedProcessSubTableBindings,
  patchFormDataSubTablesFromCurrentBindings,
  stripNestedFromAllTaskBindings,
  markBindingRowsNonReactive,
  applyTaskAssigneeNameToMatchingSubTableRows,
  syncMainSubTableRows,
  syncFormLayoutWithSubTableBindings,
  forceSeedMiCollectionBindingForCurrentParticipant,
  mergeMiParticipantScalarsFromForm,
  protectMainRecordScalarsInSubmitPayload,
  saveCurrentTaskFormWithMiPersist,
  openMiFillDialog,
  syncMiFillSubTableRows,
  saveMiFillDialog,
  buildNodeFormMapIfNeeded,
  disconnectDiagramViewportObserver,
  connectDiagramViewportObserver,
  loadFunctionUnitContent,
  prefetchProcessAndTaskFormData,
  loadProcessAndTaskFormData,
  handleProcessFormSubmit,
  isCompletedTaskData,
  hasCompletedSnapshotRoute,
  completedHistorySnapshotTime,
  completedHistoryTaskId,
  loadTaskHistory,
  scheduleDetailUiPhases,
} = ctx

// ── Node click handlers for diagram ──────────────────────────────────────

const handleNodeClick = (node: ProcessNode) => {
  if (selectedNodeId.value === node.id) {
    clearNodeSelection()
    return
  }
  void buildNodeFormMapIfNeeded().then(() => {
    selectedNodeId.value = node.id
  })
}

const clearNodeSelection = () => {
  selectedNodeId.value = null
}

const loadTaskDetail = async () => {
  loading.value = true
  formRenderReady.value = false
  fuFormSubTableFields.value = []
  detailUiPhase.value = 0
  diagramInViewport.value = false
  disconnectDiagramViewportObserver()
  ctx.deferredNodeFormMapContent = null
  nodeFormMap.value = new Map()
  // #1446: in-place reload (e.g. after MI save) must start from the same blank slate as a fresh
  // mount — stale previous-step rows / pre-save binding rows would otherwise re-enter the MI
  // merge candidates (mergePriorStepSubTablesAfterMiIsolate seeds from current binding data)
  // and win over the refetched values.
  previousForms.value = []
  subTableBindings.value = []
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
      const processSubTablesSnapshot =
        data.variables?.__subTables__ && typeof data.variables.__subTables__ === 'object'
          ? (JSON.parse(JSON.stringify(data.variables.__subTables__)) as Record<string, unknown>)
          : null
      const st0 = coerceSubTablesVariableToMap(formData.value.__subTables__)
      if (st0) {
        formData.value = { ...formData.value, __subTables__: st0 }
      }

      // Parallel fetch: history, FU content, process/task forms — do not block FU/form CPU on history.
      const historyPromise = loadTaskHistory().then(() => {
        if (bpmnXml.value) parseBpmnXml(bpmnXml.value)
      })
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

      const [prefetchedFu, prefetchedForms] = await Promise.all([
        fuFetchPromise,
        formPrefetchPromise,
      ])
      // Pre-compute flattened sub-tables once — shared by FU load, rehydrate, and MI resync.
      const preFlattenedSubTables = processSubTablesSnapshot
        ? cloneAndFlattenSubTablesMap(processSubTablesSnapshot)
        : undefined
      if (data.processDefinitionKey) {
        functionUnitIdRef.value = String(data.processDefinitionKey)
        await loadFunctionUnitContent(
          data.processDefinitionKey,
          prefetchedFu ?? undefined,
          preFlattenedSubTables,
        )
      }

      await loadProcessAndTaskFormData(data, prefetchedForms)
      rehydrateSharedProcessSubTableBindings(processSubTablesSnapshot ?? undefined, preFlattenedSubTables)

      const miIsolatePromise = isMiSubTask(data)
        ? (async () => {
            isMiSubTaskMode.value = true
            const preIsolateTopLevelForDiagram: Record<string, unknown> = { ...formData.value }
            const miFullSubTablesSnapshot =
              processSubTablesSnapshot ??
              (formData.value.__subTables__ && typeof formData.value.__subTables__ === 'object'
                ? (JSON.parse(JSON.stringify(formData.value.__subTables__)) as Record<string, unknown>)
                : null)
            // Persist-side guard source: flatten so nested participant rows are reachable per binding.
            miFullSubTablesSnapshotRef.value = miFullSubTablesSnapshot
              ? (preFlattenedSubTables ?? cloneAndFlattenSubTablesMap(miFullSubTablesSnapshot))
              : null
            await isolateMiSubTaskData(data)
            await yieldToMain()
            enrichChildBindingRowsFromParentsNestedSubTables(subTableBindings.value)
            await yieldToMain()
            const miRowIdAfterEnrich = resolveCurrentMiParticipantRowIdFromTaskVars(data?.variables ?? {})
            if (miRowIdAfterEnrich != null) {
              applyMiParticipantFilterToCurrentSubTableBindings(miRowIdAfterEnrich)
            }
            // Enrich re-aggregates nested rows across peer parents — scope again to this MI element (one task ↔ one participant row).
            const miVarsRef = data?.variables ?? {}
            const miRowIdPostEnrich = resolveCurrentMiParticipantRowIdFromTaskVars(miVarsRef)
            if (miRowIdPostEnrich != null) {
              await resyncMiParticipantSubTablesFromVariables(
                miRowIdPostEnrich,
                miFullSubTablesSnapshot,
                preFlattenedSubTables,
              )
            }
            await yieldToMain()
            rehydrateSharedAttachmentBindings(
              subTableBindings.value,
              preIsolateTopLevelForDiagram,
              miFullSubTablesSnapshotRef.value ?? preFlattenedSubTables ?? miFullSubTablesSnapshot,
            )
            mergePriorStepSubTablesAfterMiIsolate(miRowIdPostEnrich ?? null)
            if (miRowIdPostEnrich != null) {
              hydrateMiLinkChildBindingsFromFullSnapshot(miRowIdPostEnrich)
            }
            if (miRowIdPostEnrich != null) {
              scopeMiSubTaskBindingsToCurrentParticipant(subTableBindings.value, miRowIdPostEnrich)
              for (const pf of previousForms.value) {
                scopeMiSubTaskBindingsToCurrentParticipant(
                  pf.subTableBindings as typeof subTableBindings.value,
                  miRowIdPostEnrich,
                )
              }
            } else {
              sanitizeMiCollectionBindingsData(subTableBindings.value)
              for (const pf of previousForms.value) {
                sanitizeMiCollectionBindingsData(pf.subTableBindings as typeof subTableBindings.value)
              }
            }
            patchFormDataSubTablesFromCurrentBindings()
            // nodeFormMap refresh deferred until diagram panel (buildNodeFormMapIfNeeded)
            const formKeys = getCurrentFormFieldKeys()
            miFilled.value = formKeys.some(key => {
              const val = formData.value[key]
              return val != null && val !== '' && val !== false
            })
          })().catch((err: unknown) => {
            console.error('[detail] MI isolate failed:', err)
            throw err
          })
        : Promise.resolve()

      if (isCompletedTask.value) {
        applyTaskAssigneeNameToMatchingSubTableRows(data)
      }

      // History feeds diagram node status — load in background so MI form hydrate does not block the shell.
      await miIsolatePromise
      // Safe only after MI nested slices are merged — stripping earlier breaks link-form / participant isolation.
      stripNestedFromAllTaskBindings()
      if (isMiSubTaskMode.value && currentMiRowId.value != null) {
        hydrateMiLinkChildBindingsFromFullSnapshot(currentMiRowId.value)
        for (const b of subTableBindings.value) {
          if (!isMiParticipantScopedSubTableBinding(b)) continue
          syncMiLinkChildRowsIntoParentNested(
            { bindingId: b.bindingId, tableName: b.tableName ?? '' },
            cloneSubTableRows(Array.isArray(b.data) ? b.data : []),
          )
        }
        scopeMiSubTaskBindingsToCurrentParticipant(subTableBindings.value, currentMiRowId.value)
        for (const pf of previousForms.value) {
          scopeMiSubTaskBindingsToCurrentParticipant(
            pf.subTableBindings as typeof subTableBindings.value,
            currentMiRowId.value,
          )
        }
        patchFormDataSubTablesFromCurrentBindings()
      }
      markBindingRowsNonReactive()
      syncFormLayoutWithSubTableBindings()
      forceSeedMiCollectionBindingForCurrentParticipant()
      await yieldToMain()
      void historyPromise.catch((err: unknown) => {
        console.warn('[detail] Background history load failed:', err)
      })
    }
  } catch (error: any) {
    console.error('Failed to load task detail:', error)
    const status = error.response?.status
    const msg = resolveUserFacingHttpMessage(error, t)
    const notFound = status === 404 || /task not found/i.test(msg)
    const forbidden =
      status === 403 ||
      /permission|denied|do not have permission|无权|無權/i.test(msg)
    if (notFound) {
      taskError.value = t('task.notFound')
    } else if (forbidden) {
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
            const historyPromise = loadTaskHistory().then(() => {
              if (bpmnXml.value) parseBpmnXml(bpmnXml.value)
            })
            const key = (taskInfo.value as any).processDefinitionKey
            const fuFetchPromise = key
              ? processApi
                  .getFunctionUnitContent(String(key))
                  .then(r => (r as { data?: unknown }).data ?? r)
                  .catch(() => null)
              : Promise.resolve(null)
            const fallbackTask = { ...(taskInfo.value as any), processInstanceId: p.id, id: taskId }
            const formPrefetchPromise = prefetchProcessAndTaskFormData(fallbackTask)
            const prefetchedFu = await fuFetchPromise
            if (key) {
              await loadFunctionUnitContent(String(key), prefetchedFu ?? undefined)
            }
            await loadProcessAndTaskFormData(fallbackTask, await formPrefetchPromise)
            await historyPromise
            loading.value = false
            scheduleDetailUiPhases()
            return
          }
        } catch (e) {
          console.warn('[detail] Fallback process detail failed:', e)
        }
      }
      taskError.value = t('task.noPermission')
    } else {
      taskError.value = msg || t('task.serverError')
    }
    ElMessage.error(taskError.value)
  } finally {
    loading.value = false
    if (!taskError.value) {
      formRenderReady.value = true
      scheduleDetailUiPhases()
    }
  }
}
ctx.loadTaskDetail = loadTaskDetail

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
  prepareBeforeComplete: async () => {
    if (isMiSubTaskMode.value) {
      await mergeMiParticipantScalarsFromForm()
      return
    }
    if (!formReadOnly.value && allowSubTableAssignForCurrentTask.value) {
      patchFormDataSubTablesFromCurrentBindings()
    }
  },
  buildFormPayloadForComplete: () => {
    const payload = buildCurrentTaskFormSubmitPayload()
    if (isMiSubTaskMode.value) {
      protectMainRecordScalarsInSubmitPayload(payload)
    }
    return payload.formData
  },
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

/** FORM_POPUP helper callbacks — extracted to composables/taskDetail/useTaskDetailPopup.ts (behavior unchanged). */
const popupHelpers = createTaskDetailPopupHelpers(ctx)

const customActions = useCustomActions({
  taskInfo: taskInfo as any,
  subTableBindings,
  formData,
  submitting,
  saveCurrentTaskForm: saveCurrentTaskFormWithMiPersist,
  validateSubTableAssigneesForComplete,
  approveDialogVisible,
  approveDialogTitle,
  currentApproveAction,
  approveForm,
  loadTaskDetail,
  resolveFormPopupContent: popupHelpers.resolveFormPopupContent,
  preparePopupContext: popupHelpers.preparePopupContext,
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

// display helpers moved to useTaskDisplay composable
// action handlers moved to useTaskActions composable

// custom action handlers moved to useCustomActions composable

onMounted(async () => {
  await reconcilePortalWorkspaceSession()
  await loadTaskDetail()
})

onBeforeUnmount(() => {
  clearFormAutosaveTimer()
  clearBpmnParseCache()
  disconnectDiagramViewportObserver()
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
