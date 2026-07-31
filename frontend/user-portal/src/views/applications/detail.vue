<template>
  <div class="application-detail-page">
    <!-- Page header -->
    <div class="page-header">
      <div class="header-left">
        <el-button
          :icon="ArrowLeft"
          @click="$router.back()"
        >
          {{ t('applicationDetail.back') }}
        </el-button>
        <h1>{{ processInfo.processDefinitionName || t('applicationDetail.applicationDetail') }}</h1>
        <el-tag
          :type="getStatusType(processInfo.status)"
          size="small"
        >
          {{ getStatusLabel(processInfo.status) }}
        </el-tag>
      </div>
      <el-button
        :icon="Refresh"
        :loading="loading"
        @click="loadProcessDetail"
      >
        {{ t('applicationDetail.refresh') }}
      </el-button>
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

    <!-- Main content -->
    <div
      v-else
      class="content-sections"
    >
      <!-- Section 1: Basic info -->
      <div class="section info-section">
        <div class="section-header">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ t('applicationDetail.basicInfo') }}</span>
        </div>
        <div class="section-content">
          <el-descriptions
            :column="3"
            border
          >
            <el-descriptions-item
              v-if="processInfo.requestId"
              :label="t('applicationDetail.requestId')"
            >
              {{ processInfo.requestId }}
            </el-descriptions-item>
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
              {{ displayCurrentStepLabel }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('applicationDetail.currentAssignee')">
              {{ getCurrentAssigneeDisplay() }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <!-- Section 2: Process diagram (collapsible; default expanded) -->
      <WorkflowDiagramCollapsibleSection
        :title="t('applicationDetail.workflowDiagram')"
      >
        <template #badge>
          <el-tag
            :type="getNodeStatusType(processInfo.status)"
            size="small"
          >
            {{ workflowDiagramBadgeLabel }}
          </el-tag>
        </template>
        <el-skeleton
          v-if="!diagramReady && bpmnXml"
          animated
          :rows="4"
        />
        <ProcessDiagram
          v-else-if="diagramReady && (bpmnXml || processNodes.length > 0)"
          :nodes="processNodes"
          :flows="processFlows"
          :bpmn-xml="bpmnXml"
          :current-node-id="currentNodeId"
          :completed-node-ids="completedNodeIds"
          :selected-node-id="selectedNodeId ?? ''"
          :show-toolbar="true"
          :show-legend="true"
          @node-click="handleWorkflowNodeClick"
        />
        <el-empty
          v-else-if="diagramReady"
          :description="t('applicationDetail.noProcessDefinition')"
        />
      </WorkflowDiagramCollapsibleSection>
<!-- Detail navigation: history panels are lazy so their content and requests do not block General. -->
      <el-tabs
        v-model="activeDetailTab"
        class="detail-navigation"
      >
        <el-tab-pane
          :label="t('process.general')"
          name="general"
          lazy
        >
          <!-- Click a workflow node to preview that step's bound form (read-only) -->
          <div
            v-if="selectedNodeId && selectedNodeForm"
            class="section form-section"
          >
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ selectedNodeForm.formName }}</span>
          <el-tag
            v-if="selectedNodeForm.isCurrentStep"
            type="warning"
            size="small"
          >
            {{ t('applicationDetail.currentStep') }}
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
            @click="clearDiagramNodeSelection"
          >
            {{ t('applicationDetail.back') }}
          </el-button>
        </div>
        <div class="section-content">
          <div
            v-if="
              (selectedNodeForm.isCurrentStep && (formFields.length > 0 || formTabs.length > 0))
                || (!selectedNodeForm.isCurrentStep && (selectedNodeForm.fields.length > 0 || selectedNodeForm.tabs.length > 0))
            "
            class="form-container"
          >
            <FormRenderer
              :key="`diagram-node-${selectedNodeId}-${processId}`"
              :process-instance-id="processId"
              :model-value="selectedNodeForm.isCurrentStep ? formData : selectedNodeForm.values"
              :fields="selectedNodeForm.isCurrentStep ? formFields : selectedNodeForm.fields"
              :tabs="selectedNodeForm.isCurrentStep ? formTabs : selectedNodeForm.tabs"
              :label-width="formLabelWidth"
              :readonly="true"
              :sub-table-bindings="
                selectedNodeForm.isCurrentStep ? subTableBindings : selectedNodeForm.subTableBindings
              "
              :linked-sub-table-bindings="diagramSelectedLinkableBindings"
              :native-sub-table-binding-ids="
                selectedNodeForm.isCurrentStep
                  ? mainFormNativeSubTableBindingIds
                  : selectedNodeForm.nativeSubTableBindingIds
              "
              :form-config="
                selectedNodeForm.isCurrentStep ? mainFormConfig : selectedNodeForm.formConfig
              "
              view-context="initiatorRequest"
              :initiator-snapshot-mode="!!snapshotTaskName"
              @view-subtask-detail="(row: any, sib?: any[]) => openSubTaskDetailDialog(row, sib)"
            />
          </div>
          <el-empty
            v-else
            :description="t('applicationDetail.noFormData')"
          />

          <template v-if="diagramSelectedBottomSubTables.length > 0">
            <div
              v-for="binding in diagramSelectedBottomSubTables"
              :key="`diag-${binding.bindingId}`"
              class="sub-table-section"
            >
              <SubTableField
                v-model="binding.data"
                :title="binding.tableName"
                :columns="binding.columns"
                :editable="false"
                :primary-form-data="formData"
                :assignee-field="resolveBindingAssigneeField(binding)"
                :show-task-status="shouldShowBindingTaskStatus(binding)"
                :show-view-detail="shouldShowBindingDetailsModal(binding)"
                :compact-lookup-cells="bindingCompactLookupCells(binding)"
                :linked-sub-table-bindings="diagramSelectedLinkableBindings"
                @view-detail="(row: any) => openSubTaskDetailDialog(row, binding.data)"
              />
              <SubTableInlineForm
                v-if="shouldShowBindingFormBelow(binding)"
                :title="binding.tableName"
                :fields="binding.formFields || []"
                :current-row="binding.data && binding.data.length === 1 ? binding.data[0] : null"
                :readonly="true"
                :sub-table-bindings="
                  selectedNodeForm.isCurrentStep ? subTableBindings : selectedNodeForm.subTableBindings
                "
                :linked-sub-table-bindings="diagramSelectedLinkableBindings"
                suppress-link-only-standalone-sub-tables
              />
            </div>
          </template>
</div>
          </div>
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
              @click="clearDiagramNodeSelection"
            >
              {{ t('applicationDetail.back') }}
            </el-button>
          </div>
        </div>
          </div>

          <!-- Form data (Completed Tasks renders the same form as To Do, but readonly) -->
          <div
            v-if="showCurrentFormSection && !selectedNodeId"
            class="section form-section"
          >
        <div class="section-header">
          <el-icon><Document /></el-icon>
          <span>{{ currentFormName || t('applicationDetail.applicationForm') }}</span>
        </div>
        <div class="section-content">
          <div
            v-if="formFields.length > 0 || formTabs.length > 0 || formFieldsAfterTabs.length > 0"
            class="form-container"
          >
            <FormRenderer
              :key="`app-form-${processId}`"
              :process-instance-id="processId"
              v-model="formData"
              :fields="formFields"
              :tabs="formTabs"
              :fields-after-tabs="formFieldsAfterTabs"
              :label-width="formLabelWidth"
              :readonly="true"
              :sub-table-bindings="subTableBindings"
              :linked-sub-table-bindings="linkableSubTableBindings"
              :native-sub-table-binding-ids="mainFormNativeSubTableBindingIds"
              :form-config="mainFormConfig"
              view-context="initiatorRequest"
              :initiator-snapshot-mode="!!snapshotTaskName"
              :function-unit-id="functionUnitIdRef"
              :primary-table-binding="primaryTableBinding ?? undefined"
              @view-subtask-detail="(row: any, sib?: any[]) => openSubTaskDetailDialog(row, sib)"
              @update:sub-table-data="(id: number, rows: any[]) => { const b = subTableBindings.find(x => x.bindingId === id); if (b) b.data = rows }"
            />
          </div>
          <el-empty
            v-else
            :description="t('applicationDetail.noFormData')"
          />

          <!-- Sub-tables (SUB / RELATED bindings) -->
          <template v-if="bottomSubTableBindings.length > 0">
            <div
              v-for="binding in bottomSubTableBindings"
              :key="binding.bindingId"
              class="sub-table-section"
            >
              <SubTableField
                v-model="binding.data"
                :title="binding.tableName"
                :columns="binding.columns"
                :editable="false"
                :primary-form-data="formData"
                :assignee-field="resolveBindingAssigneeField(binding)"
                :show-task-status="shouldShowBindingTaskStatus(binding)"
                :show-view-detail="shouldShowBindingDetailsModal(binding)"
                :compact-lookup-cells="bindingCompactLookupCells(binding)"
                :linked-sub-table-bindings="linkableSubTableBindings"
                @view-detail="(row: any) => openSubTaskDetailDialog(row, binding.data)"
              />
              <!--
                Unplaced binding's "form below table" — driven by binding-level portalViews.initiatorRequest
                (with mirrorTodo falling through to assigneeTodo). Read-only because My Request is a
                snapshot view of the initiator's request.
              -->
              <SubTableInlineForm
                v-if="shouldShowBindingFormBelow(binding)"
                :title="binding.tableName"
                :fields="binding.formFields || []"
                :current-row="binding.data && binding.data.length === 1 ? binding.data[0] : null"
                :readonly="true"
                :sub-table-bindings="subTableBindings"
                :linked-sub-table-bindings="linkableSubTableBindings"
                suppress-link-only-standalone-sub-tables
              />
            </div>
          </template>
        </div>
          </div>
</el-tab-pane>

        <el-tab-pane
          :label="t('changeHistory.title')"
          name="change-history"
          lazy
        >
          <!-- Data is loaded only after this tab is first opened. -->
          <div class="section change-history-section">
            <ChangeHistoryPanel
              :process-instance-id="processId"
              :show-header="false"
              :sensitive-mask-lookup="sensitiveMaskLookup"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane
          :label="t('applicationDetail.flowHistory')"
          name="flow-history"
          lazy
        >
          <div class="section history-section">
            <div class="section-content">
              <ProcessHistory
                :records="historyRecords.filter(r => !r.activityType?.includes('Gateway'))"
                :show-header="false"
                :show-refresh="false"
              />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- Sub-task form detail dialog -->
      <el-dialog
        v-model="subTaskDetailVisible"
        :title="subTaskDetailTitle"
        width="600px"
        destroy-on-close
      >
        <div
          v-if="subTaskDetailFields.length > 0"
          class="form-container"
        >
          <FormRenderer
            v-model="subTaskDetailData"
            :fields="subTaskDetailFields"
            :tabs="[]"
            label-width="auto"
            :readonly="true"
            :sub-table-bindings="subTaskDetailSubTableBindings"
            :linked-sub-table-bindings="subTaskDetailLinkableBindings"
            view-context="initiatorRequest"
          />
        </div>
        <el-empty
          v-else
          :description="t('applicationDetail.noFormData')"
        />
        <template #footer>
          <el-button @click="subTaskDetailVisible = false">
            {{ t('applicationDetail.close') }}
          </el-button>
        </template>
      </el-dialog>

      <!-- Section 5: Action buttons -->
      <div
        v-if="processInfo.status === 'RUNNING'"
        class="section action-section"
      >
        <div class="action-buttons">
          <div class="left-actions">
            <el-button @click="$router.back()">
              {{ t('applicationDetail.back') }}
            </el-button>
          </div>
          <div class="right-actions">
            <el-button
              type="warning"
              :loading="urging"
              @click="handleUrge"
            >
              <el-icon><Bell /></el-icon> {{ t('applicationDetail.urge') }}
            </el-button>
            <el-button
              type="danger"
              :loading="withdrawing"
              @click="handleWithdraw"
            >
              <el-icon><RefreshLeft /></el-icon> {{ t('applicationDetail.withdraw') }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, InfoFilled, Document, Bell, RefreshLeft, Refresh } from '@element-plus/icons-vue'
import WorkflowDiagramCollapsibleSection from '@/components/WorkflowDiagramCollapsibleSection.vue'
import ProcessDiagram from '@/components/ProcessDiagram.vue'
import ProcessHistory from '@/components/ProcessHistory.vue'
import FormRenderer from '@/components/FormRenderer.vue'
import SubTableField from '@/components/SubTableField.vue'
import SubTableInlineForm from '@/components/SubTableInlineForm.vue'
import ChangeHistoryPanel from '@/components/ChangeHistoryPanel.vue'
import { formatDate } from '@/utils/dateFormat'
import { buildSensitiveMaskLookup } from '@/utils/sensitiveMaskLookup'
import { createApplicationDetailState } from '@/composables/applicationDetail/useApplicationDetailState'
import type { ApplicationDetailCtx } from '@/composables/applicationDetail/context'
import { resolveBindingAssigneeField } from '@/composables/applicationDetail/subTableRowHelpers'
import { createApplicationDetailColumns } from '@/composables/applicationDetail/useApplicationDetailColumns'
import { createApplicationDetailFormSchema } from '@/composables/applicationDetail/useApplicationDetailFormSchema'
import { createApplicationDetailLinkBindings } from '@/composables/applicationDetail/useApplicationDetailLinkBindings'
import { createApplicationDetailMiHydration } from '@/composables/applicationDetail/useApplicationDetailMiHydration'
import { createApplicationDetailMiScope } from '@/composables/applicationDetail/useApplicationDetailMiScope'
import { createApplicationDetailSubTaskDialog } from '@/composables/applicationDetail/useApplicationDetailSubTaskDialog'
import { createApplicationDetailBpmnCurrentForm } from '@/composables/applicationDetail/useApplicationDetailBpmnCurrentForm'
import { createApplicationDetailNodeFormMap } from '@/composables/applicationDetail/useApplicationDetailNodeFormMap'
import { createApplicationDetailBottomBindings } from '@/composables/applicationDetail/useApplicationDetailBottomBindings'
import { createApplicationDetailPreviousForms } from '@/composables/applicationDetail/useApplicationDetailPreviousForms'
import { createApplicationDetailHistory } from '@/composables/applicationDetail/useApplicationDetailHistory'
import { createApplicationDetailSecondary } from '@/composables/applicationDetail/useApplicationDetailSecondary'
import { createApplicationDetailLoaders } from '@/composables/applicationDetail/useApplicationDetailLoaders'
import { createApplicationDetailActions } from '@/composables/applicationDetail/useApplicationDetailActions'
import { createApplicationDetailDiagramParser } from '@/composables/applicationDetail/useApplicationDetailDiagramParser'
import type { ProcessNode } from '@/components/ProcessDiagram.vue'

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
const activeDetailTab = ref('general')

/**
 * Shared context: state refs + late-registered cross-module functions. Created once per
 * component instance; composables register their functions onto it so cross-module calls
 * (`ctx.fn(...)`) resolve at invocation time, mirroring the original single-file hoisting.
 */
const ctx = createApplicationDetailState({
  processId,
  snapshotTime,
  snapshotTaskName,
  snapshotTaskId,
  snapshotTaskDefinitionKey,
}) as ApplicationDetailCtx
ctx.t = t
ctx.router = router

Object.assign(ctx, createApplicationDetailColumns(ctx))
Object.assign(ctx, createApplicationDetailFormSchema(ctx))
Object.assign(ctx, createApplicationDetailLinkBindings(ctx))
Object.assign(ctx, createApplicationDetailMiHydration(ctx))
Object.assign(ctx, createApplicationDetailMiScope(ctx))
Object.assign(ctx, createApplicationDetailDiagramParser(ctx))
Object.assign(ctx, createApplicationDetailSubTaskDialog(ctx))
Object.assign(ctx, createApplicationDetailBpmnCurrentForm(ctx))
Object.assign(ctx, createApplicationDetailNodeFormMap(ctx))
Object.assign(ctx, createApplicationDetailBottomBindings(ctx))
Object.assign(ctx, createApplicationDetailPreviousForms(ctx))
Object.assign(ctx, createApplicationDetailHistory(ctx))
Object.assign(ctx, createApplicationDetailSecondary(ctx))
Object.assign(ctx, createApplicationDetailLoaders(ctx))
Object.assign(ctx, createApplicationDetailActions(ctx))

const {
  loading,
  urging,
  withdrawing,
  processInfo,
  processNodes,
  processFlows,
  currentNodeId,
  completedNodeIds,
  bpmnXml,
  diagramReady,
  formFields,
  formTabs,
  formFieldsAfterTabs,
  formData,
  currentFormName,
  formLabelWidth,
  subTableBindings,
  primaryTableBinding,
  functionUnitIdRef,
  mainFormNativeSubTableBindingIds,
  mainFormConfig,
  selectedNodeId,
  selectedNodeForm,
  linkableSubTableBindings,
  subTaskDetailVisible,
  subTaskDetailTitle,
  subTaskDetailFields,
  subTaskDetailData,
  subTaskDetailSubTableBindings,
  subTaskDetailLinkableBindings,
  showCurrentFormSection,
  historyRecords,
  displayCurrentStepLabel,
  workflowDiagramBadgeLabel,
  getCurrentAssigneeDisplay,
  getStatusType,
  getStatusLabel,
  getNodeStatusType,
  handleUrge,
  handleWithdraw,
  loadProcessDetail,
  handleDiagramNodeClick,
  clearDiagramNodeSelection,
  openSubTaskDetailDialog,
  shouldShowBindingDetailsModal,
  shouldShowBindingTaskStatus,
  shouldShowBindingFormBelow,
  bindingCompactLookupCells,
  bottomSubTableBindings,
  diagramSelectedLinkableBindings,
  diagramSelectedBottomSubTables,
  previousForms,
} = ctx

/** Mask configs for Change History (display-only; covers main + previous node forms). */
const sensitiveMaskLookup = computed(() => buildSensitiveMaskLookup({
  formFields: formFields.value,
  formTabs: [
    ...(formTabs.value || []),
    ...(selectedNodeForm.value?.tabs || []),
    ...((previousForms.value || []).flatMap((f: { tabs?: Array<{ fields?: unknown[] }> }) => f.tabs || [])),
  ],
  formFieldsAfterTabs: formFieldsAfterTabs.value,
  extraFieldLists: [
    selectedNodeForm.value?.fields,
    ...((previousForms.value || []).map((f: { formFields?: unknown[] }) => f.formFields)),
  ],
  subTableBindings: [
    ...(subTableBindings.value || []),
    ...(selectedNodeForm.value?.subTableBindings || []),
    ...((previousForms.value || []).flatMap((f: { subTableBindings?: unknown[] }) => f.subTableBindings || [])),
  ],
  formConfigJsons: [
    mainFormConfig.value,
    selectedNodeForm.value?.formConfig,
    ...((previousForms.value || []).map((f: { formConfig?: unknown }) => f.formConfig)),
    ...((ctx.cachedContentForms || []).map((f: { configJson?: unknown }) => {
      const raw = f?.configJson
      if (typeof raw === 'string') {
        // FALLBACK(ux): malformed configJson skips mask enrichment only; CH still shows values.
        try { return JSON.parse(raw) } catch { return null }
      }
      return raw
    })),
  ],
}))

const handleWorkflowNodeClick = (node: ProcessNode) => {
  activeDetailTab.value = 'general'
  handleDiagramNodeClick(node)
}

onMounted(() => { loadProcessDetail() })
</script>

<style lang="scss" scoped>
@use '@/styles/detail-navigation' as detailNavigation;

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
  .detail-navigation {
    @include detailNavigation.compact-detail-navigation(var(--border-color));
  }
  .form-section .form-container { width: 100%; }
  .form-section .sub-table-section { margin-top: 16px; }
  .change-history-section .section-content { padding: 20px; }
  .history-section .section-content { min-height: 100px; }
  .action-section { position: sticky; bottom: 0; z-index: 10;
    .action-buttons { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; .left-actions, .right-actions { display: flex; gap: 12px; } }
  }
}
</style>
