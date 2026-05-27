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

      <!-- Section 2: Process diagram -->
      <div class="section workflow-section">
        <div class="section-header">
          <el-icon><Share /></el-icon>
          <span>{{ t('applicationDetail.workflowDiagram') }}</span>
          <el-tag
            :type="getNodeStatusType(processInfo.status)"
            size="small"
          >
            {{ workflowDiagramBadgeLabel }}
          </el-tag>
        </div>
        <div class="section-content">
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
            @node-click="handleDiagramNodeClick"
          />
          <el-empty
            v-else-if="diagramReady"
            :description="t('applicationDetail.noProcessDefinition')"
          />
        </div>
      </div>

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
            v-if="formFields.length > 0 || formTabs.length > 0"
            class="form-container"
          >
            <FormRenderer
              :key="`app-form-${processId}`"
              v-model="formData"
              :fields="formFields"
              :tabs="formTabs"
              :label-width="formLabelWidth"
              :readonly="true"
              :sub-table-bindings="subTableBindings"
              :linked-sub-table-bindings="linkableSubTableBindings"
              :native-sub-table-binding-ids="mainFormNativeSubTableBindingIds"
              :form-config="mainFormConfig"
              view-context="initiatorRequest"
              :initiator-snapshot-mode="!!snapshotTaskName"
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
        <div
          v-if="subTaskDetailFields.length > 0"
          class="form-container"
        >
          <FormRenderer
            v-model="subTaskDetailData"
            :fields="subTaskDetailFields"
            :tabs="[]"
            label-width="160px"
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
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, InfoFilled, Share, Document, Clock, Bell, RefreshLeft, Refresh } from '@element-plus/icons-vue'
import { processApi, type ProcessInstance } from '@/api/process'
import ProcessDiagram, { type ProcessNode, type ProcessFlow } from '@/components/ProcessDiagram.vue'
import ProcessHistory, { type HistoryRecord } from '@/components/ProcessHistory.vue'
import FormRenderer, { type FormField, type FormTab } from '@/components/FormRenderer.vue'
import {
  normalizePortalViews,
  resolveSubTableDisplayMode,
  collectLinkFormTargetBindingIdsFromSubListViews,
  filterLinkOnlyStandaloneSubTableFields,
  collectLeafFormFieldKeys,
} from '@/components/formRendererHelpers'
import SubTableField from '@/components/SubTableField.vue'
import SubTableInlineForm from '@/components/SubTableInlineForm.vue'
import ChangeHistoryPanel from '@/components/ChangeHistoryPanel.vue'
import { formatDate } from '@/utils/dateFormat'
import { relationTableApi } from '@/api/relationTable'
import { isRejectedName } from '@/utils/statusMatcher'
import { resolveAssigneeFieldForBinding } from '@/utils/subTableAssignment'
import {
  mergeListViewFieldColumn,
  deriveColumnsFromRelationFieldDefinitions,
  buildRelationTableFieldIndexFromDataTables,
  resolveSubTableSchemaByTableId,
  resolveSubListViewColumnsForBinding,
  defaultAttachmentListColumns,
  SHARED_ATTACHMENT_RELATION_TABLE_ID,
  type RelationFieldDef,
} from '@/components/subTableAddDialogHelpers'
import {
  mergeSubTableRowsByRowId,
  mergeAllSubTableSlicesFromVariables,
  subTableVariablesIncludeMiRows,
  dropSubsumedSubTableRows,
  resolveSubTablePrimaryKeyFields,
  hydrateChildSubTablesFromParentsNestedRows,
  flattenNestedSubTableRowsIntoPayload,
  buildBindingIdToRelationTableIdMap,
  hydrateBindingsRowsFromVariablesBySharedRelationTableId,
  enrichChildBindingRowsFromParentsNestedSubTables,
  coerceSubTablesVariableToMap,
  collectSubTableSliceArraysDeep,
  cloneSubTableRows,
  pullNestedRowsForBindingFromParentRows,
  applySharedAttachmentFinalizeAndMaterialize,
  isSharedAttachmentFileBinding,
  isFileOnlySubTableBinding,
  isMiParticipantScopedSubTableBinding,
  filterRowsForMiParticipantSubTableBinding,
  filterRowsForSharedProcessSubTableBinding,
} from '@/composables/tasks/shared'
import {
  resolveMiSubProcessScopeFromBpmn,
  findBindingForMiSubTableName,
  filterBindingsToMiParticipantRow,
  resolveViewerParticipantRowIdFromCollectionBinding,
  hasConfiguredPrimaryKeyFields,
  describeSubTableBindingLabel,
  type MiSubProcessScopeConfig,
} from '@/composables/tasks/miSubProcessScope'
import { USER_ID_KEY, USER_KEY } from '@/api/auth'
import { clearBpmnParseCache, getCachedBpmnDocument } from '@/utils/bpmnParseCache'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const processId = route.params.id as string
// Snapshot timestamp from completed tasks entry, used to only show process state up to that point
const snapshotTime = route.query.snapshotTime as string | undefined
// Snapshot task name from completed tasks entry, used to highlight that node as current
const snapshotTaskName = route.query.snapshotTaskName as string | undefined
/** True when the viewer is the process initiator on My Request (not Completed Tasks snapshot). */
const isInitiatorMyRequestView = ref(false)
const snapshotTaskId = route.query.snapshotTaskId as string | undefined
const snapshotTaskDefinitionKey = route.query.snapshotTaskDefinitionKey as string | undefined

/** Consistent with request interceptor; used to determine if the initiator is viewing their own application */
function getPortalUserId(): string | null {
  let userId = localStorage.getItem(USER_ID_KEY)
  if (!userId) {
    const userStr = localStorage.getItem(USER_KEY)
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

/** 已完成等终态下无「当前步骤」；兼容库内仍残留最后一笔活动名的历史数据 */
const displayCurrentStepLabel = computed(() => {
  const st = processInfo.value.status
  if (st === 'COMPLETED') return '-'
  return processInfo.value.currentNode || '-'
})

/** 流程图区块角标：终态显示状态文案，运行中显示当前节点或待处理 */
const workflowDiagramBadgeLabel = computed(() => {
  const st = processInfo.value.status || ''
  if (st === 'COMPLETED') return t('applicationDetail.completed')
  if (st === 'WITHDRAWN') return t('applicationDetail.withdrawn')
  if (st === 'REJECTED') return t('applicationDetail.rejected')
  return processInfo.value.currentNode || t('applicationDetail.pending')
})

// Process diagram data
const processNodes = ref<ProcessNode[]>([])
const processFlows = ref<ProcessFlow[]>([])
const currentNodeId = ref('')
const completedNodeIds = ref<string[]>([])
const bpmnXml = ref('')
const activeMiSubProcessScope = ref<MiSubProcessScopeConfig | null>(null)
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
/** Heavy BPMN node/flow parse is deferred so form + sub-tables can paint first. */
const diagramReady = ref(false)
let diagramParseScheduled = false

function scheduleParseApplicationBpmnDiagram(xml: string) {
  if (!xml) {
    diagramReady.value = true
    return
  }
  if (diagramParseScheduled) return
  diagramParseScheduled = true
  const run = () => {
    diagramParseScheduled = false
    parseBpmnXml(xml)
    diagramReady.value = true
  }
  if (typeof requestIdleCallback === 'function') {
    requestIdleCallback(run, { timeout: 1500 })
  } else {
    setTimeout(run, 0)
  }
}

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
  subMode?: string
  formFields?: FormField[]
  formOptions?: Record<string, any>
  // Per-binding portalViews loaded from form configJson.subTablePortalViews[bindingId].
  portalViews?: Record<string, any> | null
  /** From dw_field_definitions via admin assembleFunctionUnitContent; drives row merge / PK resolution. */
  primaryKeyFields?: string[]
}>>([])

/** Cached from loadFunctionUnitContent — shared attachment slice merge (parity with tasks/detail.vue). */
const lastBindingRelationTableMap = ref<Map<number, number | null>>(new Map())

function applySharedAttachmentHydrationToAllBindings(
  topLevelValues?: Record<string, unknown> | null,
  flattened?: Record<string, unknown> | null,
) {
  const tv = (topLevelValues ?? formData.value) as Record<string, unknown>
  const flat =
    flattened ??
    coerceSubTablesVariableToMap(formData.value.__subTables__)
  const opts = {
    flattened: flat,
    bindingTableById: lastBindingRelationTableMap.value,
  }
  applySharedAttachmentFinalizeAndMaterialize(subTableBindings.value, tv, opts)
  for (const pf of previousForms.value) {
    applySharedAttachmentFinalizeAndMaterialize(pf.subTableBindings, tv, opts)
  }
  for (const info of nodeFormMap.value.values()) {
    applySharedAttachmentFinalizeAndMaterialize(info.subTableBindings, tv, opts)
  }
}

const placedBindingIds = computed((): Set<number> => {
  return collectPlacedBindingIds(formFields.value, formTabs.value)
})

/** Binding ids from the active form's tableBindings (not merge-only link targets). */
const mainFormNativeSubTableBindingIds = ref<number[]>([])

/** Cached designer config for the active main form (subListViews, subTablePortalViews, rule). */
const mainFormConfig = ref<Record<string, any>>({})

/**
 * Portal-design-parity (see `portal-design-parity.mdc`): User Portal MUST mirror the DW Form
 * Designer Preview. DW Preview renders only sub-tables placed in `rule` (and transitive
 * link-form targets via `subListViews` columns) — see `FormDesigner.vue:buildPreviewItems`.
 * Unplaced bindings (orphans, stale designer state, RELATED lookup targets, link-form-only
 * bindings whose host column was deleted in a later designer save) are NEVER surfaced as
 * standalone bottom tables in the Designer Preview, so they MUST NOT be surfaced here either.
 *
 * The early-return gates below are retained for clarity (and to keep the call surface intact)
 * but the function intentionally returns `false` unconditionally — the bottom "unplaced
 * fallback" section is no longer rendered for any binding. Callers that legitimately need to
 * expose a sub-table should place it in the form's `rule` or reference it via a `linkForm`
 * column in `subListViews`.
 */
function shouldRenderBottomUnplacedSubTable(
  binding: { bindingId: number; bindingType?: string; subMode?: string; portalViews?: Record<string, unknown> | null },
  placed: Set<number>,
  bindings: Array<{ bindingId: number; bindingType?: string; subMode?: string; columns?: Array<{ type?: string; props?: Record<string, unknown> }>; portalViews?: Record<string, unknown> | null }>,
  nativeBindingIds: ReadonlySet<number>,
  formConfig?: Record<string, unknown> | null,
): boolean {
  // Argument references — retained so future legacy reactivation can re-enable specific gates.
  void binding
  void placed
  void bindings
  void nativeBindingIds
  void formConfig
  return false
}

const bottomSubTableBindings = computed(() => {
  const nativeIds = new Set(mainFormNativeSubTableBindingIds.value.map(Number))
  const placed = placedBindingIds.value
  return subTableBindings.value.filter(b =>
    shouldRenderBottomUnplacedSubTable(b, placed, subTableBindings.value, nativeIds, mainFormConfig.value),
  )
})

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

// Lookup config fallback map (from rt_lookup_configs)
const lookupDbConfigs = ref<Record<string, { tableId: number; searchFields: string[]; displayField: string; viewFields: any[] }>>({})

/** Function unit forms + relation-table field index — schema fallback parity with tasks/detail.vue */
let cachedContentForms: any[] = []
let cachedRelationTableFieldIndex = new Map<number, RelationFieldDef[]>()

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
    /** When FORM_ONLY and not in form rule, binding exists for Link Form only (no standalone block). */
    subMode?: string
    formFields?: FormField[]
    formOptions?: Record<string, any>
    portalViews?: Record<string, any> | null
    primaryKeyFields?: string[]
  }>
}
const previousForms = ref<PreviousFormEntry[]>([])

// Workflow diagram: click a BPMN node to preview its bound form (My Request)
interface ApplicationDiagramNodeFormInfo {
  formName: string
  /** Matches the process "current" BPMN userTask — reuse main form layout + variables */
  isCurrentStep: boolean
  fields: FormField[]
  tabs: FormTab[]
  values: Record<string, any>
  subTableBindings: PreviousFormEntry['subTableBindings']
  /** tableBindings on this BPMN form — excludes merge-only link targets. */
  nativeSubTableBindingIds: number[]
  /** Designer configJson for link-form suppression (subListViews). */
  formConfig: Record<string, any>
}

const selectedNodeId = ref<string | null>(null)
const nodeFormMap = ref<Map<string, ApplicationDiagramNodeFormInfo>>(new Map())

const selectedNodeForm = computed((): ApplicationDiagramNodeFormInfo | null => {
  if (!selectedNodeId.value) return null
  return nodeFormMap.value.get(selectedNodeId.value) ?? null
})

function handleDiagramNodeClick(node: ProcessNode) {
  ensureApplicationNodeFormMapBuilt()
  if (!node?.id) {
    selectedNodeId.value = null
    return
  }
  if (selectedNodeId.value === node.id) {
    selectedNodeId.value = null
  } else {
    selectedNodeId.value = node.id
  }
}

function clearDiagramNodeSelection() {
  selectedNodeId.value = null
}

/** Align with tasks/detail.vue: variables may key __subTables__ by table name or binding id. */
function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

function getSavedSubTableRowsFromVariables(
  savedSubTables: Record<string, any> | null | undefined,
  rawBinding: { bindingId: number; tableName?: string; tableDisplayName?: string },
  primaryKeyFields?: string[] | null
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
  const seenArrays = new Set<any>()
  const chunks: any[][] = []
  for (const key of keys) {
    if (key === '' || key == null) continue
    const v = savedSubTables[key as string]
    if (!Array.isArray(v) || v.length === 0 || seenArrays.has(v)) continue
    seenArrays.add(v)
    chunks.push(v)
  }
  if (chunks.length === 0) return undefined
  let merged: any[] = chunks.length === 1 ? [...chunks[0]!] : []
  if (chunks.length > 1) {
    for (const chunk of chunks) {
      merged = mergeSubTableRowsByRowId(merged, chunk, primaryKeyFields ?? null)
    }
  }
  return dropSubsumedSubTableRows(merged)
}

type SubTableBindingAlignable = {
  bindingId?: number
  tableId?: number | null
  tableName: string
  data: any[]
  primaryKeyFields?: string[]
  physicalTableName?: string
}

/**
 * Copied forms (e.g. subform_copy) get a new bindingId while runtime data still lives under the original key;
 * MI may only persist one row under the new id — merge all bindings that share tableId (or display name) for My Request.
 *
 * Previous logic bucketed only by `tid:*` OR `tn:*`. When one form binding had `tableId` and another did not
 * (same relation table, different form metadata), they landed in separate groups and `length < 2` skipped merge —
 * common when the process advances (e.g. assignment step) and the current form uses a new binding row while
 * `__subTables__` keys still match an earlier step. Union-find merges by equal numeric tableId OR equal normalized
 * display name, then a column-overlap pass fills bindings that still have no rows.
 */
/** Union-find merge of row snapshots across bindings that share tableId or display name. */
function applyUnionFindMergeToBindingList(all: SubTableBindingAlignable[]) {
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
      if (
        isSharedAttachmentFileBinding(
          a as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string; physicalTableName?: string; tableId?: number | null },
        ) ||
        isSharedAttachmentFileBinding(
          b as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string; physicalTableName?: string; tableId?: number | null },
        )
      ) {
        continue
      }
      const tnA = normalizeSubTableName(a.tableName)
      const tnB = normalizeSubTableName(b.tableName)
      const tidA = a.tableId != null && !Number.isNaN(Number(a.tableId)) ? Number(a.tableId) : null
      const tidB = b.tableId != null && !Number.isNaN(Number(b.tableId)) ? Number(b.tableId) : null
      if (tidA != null && tidB != null && tidA === tidB) {
        union(i, j)
      } else if (tnA.length > 0 && tnA === tnB) {
        union(i, j)
      }
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
      const pks = (b as SubTableBindingAlignable).primaryKeyFields
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

function backfillSubTableBindingsFromVariables(bindings: SubTableBindingAlignable[]) {
  const savedMap = coerceSubTablesVariableToMap(formData.value.__subTables__)
  if (!savedMap || bindings.length === 0) return
  formData.value = { ...formData.value, __subTables__: savedMap }
  const sliceArrays = collectSubTableSliceArraysDeep(savedMap)

  for (const b of bindings) {
    if (isSharedAttachmentFileBinding(b as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string; physicalTableName?: string; tableId?: number | null })) {
      continue
    }
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

/** Fast path: current form bindings only — enough for first paint on initiator My Request. */
/** Resolve list columns for a binding, including sibling-form / dataTables fallbacks (binding 104 empty subListViews). */
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

function resolveSubTableBindingColumnsForPortal(
  b: {
    bindingId?: number
    tableId?: number | null
    tableName?: string
    foreignKeyField?: string | null
  },
  formConfig: Record<string, any>,
  contentForms?: any[] | null,
): ReturnType<typeof deriveColumnsFromBinding> {
  let columns = deriveColumnsFromBinding(b, formConfig)
  const tableIdNum = b.tableId != null ? Number(b.tableId) : NaN
  const forms = contentForms ?? cachedContentForms
  if ((!Array.isArray(columns) || columns.length === 0) && Number.isFinite(tableIdNum) && forms.length > 0) {
    const alt = resolveSubTableSchemaByTableId(tableIdNum, forms, b.bindingId)
    if (alt) {
      columns = deriveColumnsFromBinding({ ...b, bindingId: alt.bindingId }, alt.formConfig)
    }
    if ((!columns || columns.length === 0) && cachedRelationTableFieldIndex.has(tableIdNum)) {
      columns = deriveColumnsFromRelationFieldDefinitions(cachedRelationTableFieldIndex.get(tableIdNum)!)
    }
  }
  if ((!columns || columns.length === 0) && isPortalSharedAttachmentTableBinding(b)) {
    columns = defaultAttachmentListColumns()
  }
  return Array.isArray(columns) ? columns : []
}

function alignMainSubTableBindingsOnly() {
  const main = subTableBindings.value as SubTableBindingAlignable[]
  if (main.length === 0) return
  applySharedAttachmentHydrationToAllBindings()
  applyUnionFindMergeToBindingList(main)
  enrichChildBindingRowsFromParentsNestedSubTables(subTableBindings.value)
  resyncMiDashboardFieldsFromVariablesOnBindings(main)
  backfillSubTableBindingsFromVariables(main)
  applySharedAttachmentHydrationToAllBindings()
}

function alignProcessSubTableBindingsBySharedTable() {
  refreshActiveMiSubProcessScopeFromBpmn()
  const nodeBindings: SubTableBindingAlignable[] = Array.from(nodeFormMap.value.values()).flatMap(
    info => info.subTableBindings as SubTableBindingAlignable[]
  )
  const all: SubTableBindingAlignable[] = [
    ...(subTableBindings.value as SubTableBindingAlignable[]),
    ...previousForms.value.flatMap(f => f.subTableBindings as SubTableBindingAlignable[]),
    ...nodeBindings
  ]
  if (all.length === 0) return

  applySharedAttachmentHydrationToAllBindings()
  applyUnionFindMergeToBindingList(all)

  backfillEmptySubTableBindingsFromVariables()
  enrichChildBindingRowsFromParentsNestedSubTables([
    ...subTableBindings.value,
    ...previousForms.value.flatMap(f => f.subTableBindings),
    ...Array.from(nodeFormMap.value.values()).flatMap(n => n.subTableBindings)
  ])
  resyncMiDashboardFieldsFromVariablesOnBindings(all)
  filterRunningMiBindingsByProcessDesignScope(subTableBindings.value)
  for (const prevForm of previousForms.value) {
    filterRunningMiBindingsByProcessDesignScope(prevForm.subTableBindings as typeof subTableBindings.value)
  }
  for (const nodeForm of nodeFormMap.value.values()) {
    filterRunningMiBindingsByProcessDesignScope(nodeForm.subTableBindings as typeof subTableBindings.value)
  }
  applySharedAttachmentHydrationToAllBindings()
}

/**
 * After hydrate/enrich passes, re-merge each binding from {@code __subTables__} so MI columns match
 * backend overlay (sub form2) — intermediate steps may have frozen stale task_current_node on row 0.
 */
function resyncMiDashboardFieldsFromVariablesOnBindings(all: SubTableBindingAlignable[]) {
  const savedSubTables = formData.value.__subTables__
  if (!savedSubTables || typeof savedSubTables !== 'object') return
  const savedMap = savedSubTables as Record<string, unknown>
  const useAllSlices = subTableVariablesIncludeMiRows(savedMap)
  const allSlicesMerged = useAllSlices
    ? mergeAllSubTableSlicesFromVariables(savedMap, undefined)
    : []
  for (const b of all) {
    const pk = b.primaryKeyFields ?? null
    const bindingSaved = getSavedSubTableRowsFromVariables(
      savedSubTables as Record<string, any>,
      {
        bindingId: Number(b.bindingId ?? 0),
        tableName: b.physicalTableName ?? b.tableName,
        tableDisplayName: b.tableName
      },
      pk
    )
    if (isSharedAttachmentFileBinding(b as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string; physicalTableName?: string; tableId?: number | null })) {
      continue
    }
    if (
      isMiParticipantScopedSubTableBinding(
        b as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string },
      )
    ) {
      const fromOwnSlice = bindingSaved ?? []
      if (fromOwnSlice.length === 0 && !(Array.isArray(b.data) && b.data.length > 0)) continue
      b.data = dropSubsumedSubTableRows(
        filterRowsForMiParticipantSubTableBinding(
          mergeSubTableRowsByRowId(fromOwnSlice, Array.isArray(b.data) ? b.data : [], pk),
          b as { columns?: Array<{ field?: string }>; tableName?: string },
        ),
      )
      continue
    }
    // HMDC Attachment: file-only columns — global MI slice merge injects transaction rows as empty file rows.
    if (isFileOnlySubTableBinding(b as { columns?: Array<{ field?: string }> | null })) {
      const fromOwnSlice = bindingSaved ?? []
      if (fromOwnSlice.length === 0 && !(Array.isArray(b.data) && b.data.length > 0)) continue
      const merged = mergeSubTableRowsByRowId(
        Array.isArray(b.data) ? b.data : [],
        fromOwnSlice,
        pk,
      )
      b.data = dropSubsumedSubTableRows(
        filterRowsForSharedProcessSubTableBinding(
          merged,
          b as {
            columns?: Array<{ field?: string }> | null
            foreignKeyField?: string | null
            tableName?: string
            physicalTableName?: string
            tableId?: number | null
          },
        ),
      )
      continue
    }
    const fromVariables = useAllSlices
      ? mergeSubTableRowsByRowId(allSlicesMerged, bindingSaved ?? [], pk)
      : (bindingSaved ?? [])
    if (fromVariables.length === 0 && !(Array.isArray(b.data) && b.data.length > 0)) continue
    // Variables (backend MI overlay) win over binding rows polluted by enrich.
    b.data = dropSubsumedSubTableRows(
      mergeSubTableRowsByRowId(Array.isArray(b.data) ? b.data : [], fromVariables, pk)
    )
  }
}

/**
 * When `__subTables__` keys do not match any bindingId/table label on the current form, merge-by-table may still
 * leave an empty `data` array. Pick a saved row list whose first row shares enough column names with the binding's
 * list-view columns (conservative threshold) so initiator My Request shows prior-step sub-table rows.
 */
/** Columns + inline-form field keys used to match variable slices and detect MI placeholder rows. */
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
      if (f.type === 'card') walkFormFields(f.children)
      else if (typeof f.key === 'string' && f.key.length > 0) fieldSet.add(f.key)
    }
  }
  walkFormFields(b.formFields)
  return fieldSet
}

const SUB_TABLE_MI_PLACEHOLDER_KEYS = new Set([
  'assignee_user_id',
  'assignee_id',
  'assignee_display_name',
  'task_status',
  'task_current_node',
  'sub_task_status',
  'sub_task_current_node',
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

/**
 * True when no row carries real values for designer list / sub-form fields (only MI assignment columns, etc.).
 * Otherwise backfill from variables is skipped and Link Form modal stays blank for My Request.
 */
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
    if (isSharedAttachmentFileBinding(b as { columns?: Array<{ field?: string }>; foreignKeyField?: string | null; tableName?: string; physicalTableName?: string; tableId?: number | null })) {
      continue
    }
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

// Link-form columns need access to other bindings as fallback data sources.
// Keep the contract aligned with `tasks/detail.vue` (linkableSubTableBindings).
const linkableSubTableBindings = computed<any[]>(() => [
  ...(subTableBindings.value as any[]),
  ...previousForms.value.flatMap(form => (form.subTableBindings as any[]))
])

/** Link-form fallback when previewing a diagram node's form */
const diagramSelectedLinkableBindings = computed<any[] | undefined>(() => {
  const sf = selectedNodeForm.value
  if (!sf) return undefined
  if (sf.isCurrentStep) return linkableSubTableBindings.value
  return [
    ...(sf.subTableBindings as any[]),
    ...previousForms.value.flatMap(form => form.subTableBindings as any[])
  ]
})

/** Unplaced sub-tables for the diagram-selected form (mirrors bottomSubTableBindings) */
const diagramSelectedBottomSubTables = computed(() => {
  const sf = selectedNodeForm.value
  if (!sf) return []
  const fields = sf.isCurrentStep ? formFields.value : sf.fields
  const tabs = sf.isCurrentStep ? formTabs.value : sf.tabs
  const bindings = sf.isCurrentStep ? subTableBindings.value : sf.subTableBindings
  const placed = collectPlacedBindingIds(fields, tabs)
  const nativeIds = new Set(
    (sf.isCurrentStep ? mainFormNativeSubTableBindingIds.value : sf.nativeSubTableBindingIds).map(Number),
  )
  return bindings.filter((b: { bindingId: number; subMode?: string }) =>
    shouldRenderBottomUnplacedSubTable(
      b,
      placed,
      bindings as any[],
      nativeIds,
      sf.isCurrentStep ? mainFormConfig.value : sf.formConfig,
    ),
  )
})

/** Same as tasks/detail.vue: link-form `.find()` must resolve prev-form bindings before current (empty MI slice). */
// Sub-task form detail dialog
const subTaskDetailVisible = ref(false)
const subTaskDetailTitle = ref('')
const subTaskDetailFields = ref<FormField[]>([])
const subTaskDetailData = ref<Record<string, any>>({})
const subTaskDetailSubTableBindings = ref<Array<{
  bindingId: number
  tableId?: number | null
  bindingType: string
  bindingMode: string
  foreignKeyField: string | null
  tableName: string
  physicalTableName?: string
  tableType: string
  tableDescription: string
  columns: Array<{ field: string; label: string; type?: string }>
  data: any[]
  subMode?: string
  formFields?: FormField[]
  formOptions?: Record<string, any>
  portalViews?: Record<string, any> | null
  primaryKeyFields?: string[]
}>>([])
const subTaskDetailLinkableBindings = computed(() => [
  ...subTaskDetailSubTableBindings.value,
  ...linkableSubTableBindings.value,
])
const subTaskFormSchema = ref<any>(null)
const subTaskFormId = ref<string | null>(null)
/** My Request always shows the form section; current node + portalViews drive MI summary vs full layout. */
const showCurrentFormSection = computed(() => true)

const hasSubTaskFormSchema = computed(() => !!subTaskFormSchema.value)

/**
 * Decide whether a bottom (unplaced) sub-table binding should expose a "Details" link
 * that opens the sub-task form modal. Per the designer-driven contract:
 *   - Explicit binding-level `initiatorRequest=summaryWithLinkFormModal` → always show
 *   - Explicit binding-level `tableOnly` or `mirrorTodo` → never show summary modal
 *   - No binding-level config → legacy heuristic preserved (hasSubTaskFormSchema && hasTaskStatusData)
 */
function shouldShowBindingDetailsModal(binding: { portalViews?: any; data?: any[] }): boolean {
  const mode = binding?.portalViews?.initiatorRequest
  // Match FormRenderer: designer list owns Actions/Detail when initiator uses summary+Link Form.
  if (mode === 'summaryWithLinkFormModal') return false
  if (mode === 'tableOnly' || mode === 'mirrorTodo') return false
  return hasSubTaskFormSchema.value && hasTaskStatusData(binding.data || [])
}

/** Task status column for unplaced bindings; aligns with placed sub-tables in FormRenderer (summary MI view). */
function shouldShowBindingTaskStatus(binding: { portalViews?: any; data?: any[] }): boolean {
  const mode = binding?.portalViews?.initiatorRequest
  if (mode === 'summaryWithLinkFormModal') return false
  if (mode === 'tableOnly' || mode === 'mirrorTodo') return false
  return hasSubTaskFormSchema.value && hasTaskStatusData(binding.data || [])
}

/**
 * Decide whether to render an inline form below an unplaced sub-table in My Request.
 * Mirrors the rendering rule used by FormRenderer for placed sub-tables: `formBelowTable`
 * (either explicitly via initiatorRequest, or via mirrorTodo → assigneeTodo). When the
 * binding has no portalViews configured, defaults to NO inline form (legacy behavior).
 */
function shouldShowBindingFormBelow(binding: { portalViews?: any; formFields?: any[] }): boolean {
  if (!Array.isArray(binding.formFields) || binding.formFields.length === 0) return false
  return resolveSubTableDisplayMode(binding.portalViews, 'initiatorRequest') === 'formBelowTable'
}

/** Aligns with FormRenderer: summary + Link/Details → compact cells (no inline lookup backfill block). */
function bindingCompactLookupCells(binding: { portalViews?: any }): boolean {
  return resolveSubTableDisplayMode(binding.portalViews, 'initiatorRequest') === 'summaryWithLinkFormModal'
}

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

/**
 * MI / assignee sub-table rows: process-level {@link formData} may contain another participant's link-form
 * fields (last writer). Never treat that as this row's Detail payload unless the row is terminal.
 */
function isMultiInstanceStyleSubTableRow(row: any): boolean {
  if (!row || typeof row !== 'object') return false
  if (row.task_status !== undefined && row.task_status !== null) return true
  if (row.sub_task_status !== undefined && row.sub_task_status !== null) return true
  if (row.task_id != null && String(row.task_id).trim() !== '') return true
  if (row.task_definition_key != null && String(row.task_definition_key).trim() !== '') return true
  if (row.assignee_user_id != null && String(row.assignee_user_id).trim() !== '') return true
  if (row.assignee_id != null && String(row.assignee_id).trim() !== '') return true
  return false
}

function rowAssigneeUserId(row: any, assigneeField: string): string | null {
  if (!row || typeof row !== 'object') return null
  const raw = row[assigneeField]
  if (raw == null || raw === '') return null
  if (typeof raw === 'string' || typeof raw === 'number') {
    const s = String(raw).trim()
    return s.length > 0 ? s : null
  }
  if (typeof raw === 'object') {
    const uid = (raw as { userId?: unknown; id?: unknown }).userId ?? (raw as { id?: unknown }).id
    if (uid == null || uid === '') return null
    const s = String(uid).trim()
    return s.length > 0 ? s : null
  }
  return null
}

function refreshActiveMiSubProcessScopeFromBpmn() {
  const xml = bpmnXml.value
  if (!xml) {
    activeMiSubProcessScope.value = null
    return
  }
  activeMiSubProcessScope.value = resolveMiSubProcessScopeFromBpmn(xml, {
    userTaskName: snapshotTaskName || processInfo.value.currentNode || null,
  })
}

/**
 * Running MI subprocess on My Request: scope to the viewer's participant row using
 * Process Design subTableName + designer primary key (not hard-coded columns).
 * Initiators see the full case (all MI transaction rows + case attachments), not one participant slice.
 */
function filterRunningMiBindingsByProcessDesignScope(bindings: typeof subTableBindings.value) {
  if (isInitiatorMyRequestView.value) return
  if (snapshotTaskName || processInfo.value.status !== 'RUNNING') return
  const scope = activeMiSubProcessScope.value
  if (!scope?.subTableName) return
  const viewerId = getPortalUserId()?.trim()
  if (!viewerId) return

  const collectionBinding = findBindingForMiSubTableName(bindings, scope.subTableName)
  if (!collectionBinding) return

  if (!hasConfiguredPrimaryKeyFields(collectionBinding.primaryKeyFields)) {
    warnMiMissingPrimaryKey(collectionBinding)
    return
  }

  const participantRowId = resolveViewerParticipantRowIdFromCollectionBinding(
    scope,
    collectionBinding,
    viewerId,
  )
  if (participantRowId == null) return

  filterBindingsToMiParticipantRow(bindings, scope, participantRowId)
}

/**
 * Rows considered for MI / variable-merge decisions. Prefer the emitting sub-table's `data`
 * so we do not mix unrelated bindings from {@link getMiRows}.
 */
function resolveSubTaskSiblingRows(siblingRowsOverride?: any[] | null): any[] {
  if (Array.isArray(siblingRowsOverride)) return siblingRowsOverride
  return getMiRows()
}

/**
 * Merge process variables only when safe: terminal MI rows (legacy payload on variables), or non-MI rows.
 * Open MI participants — including bare relation rows that only have `id` beside completed siblings — must not
 * inherit another row's submission from {@link formData.value}.
 */
function shouldMergeProcessVariablesIntoSubTaskDetailRow(row: any, siblingRowsOverride?: any[] | null): boolean {
  const ts = String(row.task_status ?? '').toUpperCase()
  if (ts === 'COMPLETED' || ts === 'REJECTED') return true

  const siblings = resolveSubTaskSiblingRows(siblingRowsOverride)
  const processUsesPerRowTaskStatus = siblings.some(
    (r: any) => r && r.task_status !== undefined && r.task_status !== null
  )
  if (processUsesPerRowTaskStatus) return false

  // Same sub-table has 2+ rows: instance-level variables often hold the last submitter only (API may omit task_status).
  if (siblings.length >= 2) return false

  if (isMultiInstanceStyleSubTableRow(row)) return false

  const hasMiSibling = siblings.some(
    (r: any) => r && r !== row && isMultiInstanceStyleSubTableRow(r)
  )
  if (hasMiSibling && row && typeof row === 'object') {
    const pk = row.id
    if (pk != null && pk !== '') return false
  }

  return true
}

/** Build sub-table bindings for a nested form (MI sub-task / Link Form target) — parity with main form load path. */
function buildSubTableBindingsForForm(
  formMeta: { tableBindings?: any[] },
  formConfig: Record<string, any>,
  parentRow?: Record<string, any> | null,
): typeof subTaskDetailSubTableBindings.value {
  const bindings: typeof subTaskDetailSubTableBindings.value = []
  const subFormsPayload = formConfig.subForms || {}
  const subTablePortalViewsPayload = formConfig.subTablePortalViews || {}
  for (const b of formMeta.tableBindings || []) {
    if (b.bindingType === 'PRIMARY') continue
    const columns = deriveColumnsFromBinding(b, formConfig)
    if (!Array.isArray(columns) || columns.length === 0) continue
    const subFormDesign = resolveSubFormDesign(b, subFormsPayload)
    const bindingPortalViews =
      subTablePortalViewsPayload[b.bindingId]
      ?? subTablePortalViewsPayload[String(b.bindingId)]
      ?? null
    bindings.push({
      bindingId: b.bindingId,
      tableId: b.tableId != null ? Number(b.tableId) : null,
      bindingType: b.bindingType,
      bindingMode: b.bindingMode,
      foreignKeyField: b.foreignKeyField,
      tableName: b.tableDisplayName || b.tableName,
      physicalTableName: b.tableName,
      tableType: b.tableType,
      tableDescription: b.tableDescription,
      columns,
      data: [] as any[],
      subMode: b.subMode,
      formFields: subFormDesign.formFields,
      formOptions: subFormDesign.formOptions,
      portalViews: bindingPortalViews,
      primaryKeyFields: resolveSubTablePrimaryKeyFields(
        b.primaryKeyFields,
        b.bindingId,
        formConfig,
      ),
    })
  }
  mergeLinkFormTargetBindingsInto(
    bindings,
    cachedContentForms,
    formConfig,
    subFormsPayload,
  )
  stripLinkOnlySubTableFieldsFromBindings(bindings, subFormsPayload, formConfig.rule, formConfig)
  const rtMap = buildBindingIdToRelationTableIdMap(cachedContentForms)
  if (parentRow && typeof parentRow === 'object') {
    for (const binding of bindings) {
      const nested = pullNestedRowsForBindingFromParentRows(
        {
          bindingId: binding.bindingId,
          tableName: binding.tableName,
          physicalTableName: binding.physicalTableName,
          tableId: binding.tableId ?? null,
        },
        [parentRow],
        rtMap.size > 0 ? rtMap : undefined,
      )
      if (nested.length > 0) {
        binding.data = cloneSubTableRows(nested)
      }
    }
    enrichChildBindingRowsFromParentsNestedSubTables(bindings)
  }
  for (const binding of bindings) {
    if (Array.isArray(binding.data) && binding.data.length > 0) continue
    const peer = linkableSubTableBindings.value.find(
      x => Number(x.bindingId) === Number(binding.bindingId),
    )
    if (peer?.data?.length) {
      binding.data = cloneSubTableRows(peer.data)
    }
  }
  return bindings
}

function openSubTaskDetailDialog(row: any, siblingRowsOverride?: any[] | null) {
  if (!subTaskFormSchema.value) return
  const schema = subTaskFormSchema.value
  const formRules =
    schema.rule && Array.isArray(schema.rule) ? schema.rule : (Array.isArray(schema) ? schema : [])

  const formMeta =
    (subTaskFormId.value
      ? cachedContentForms.find((f: any) => String(f.id) === subTaskFormId.value)
      : null)
    ?? (schema._formName
      ? cachedContentForms.find((f: any) => f.name === schema._formName)
      : null)
  subTaskDetailSubTableBindings.value = formMeta
    ? buildSubTableBindingsForForm(formMeta, schema, row)
    : []

  const rawFields = extractFieldsRecursive(formRules)
  subTaskDetailFields.value = filterLinkOnlyStandaloneSubTableFields(
    rawFields,
    subTaskDetailSubTableBindings.value,
    formRules,
    undefined,
    schema,
  )

  const mergedData: Record<string, any> = { ...row }
  // Fallback: for MI form fields absent from the row, use process-level variables.
  // Legacy saves only; never for open MI rows — variables often mirror another participant's submission.
  const allowVarFallback = shouldMergeProcessVariablesIntoSubTaskDetailRow(row, siblingRowsOverride)
  if (allowVarFallback) {
    for (const f of rawFields) {
      if (f.key && (mergedData[f.key] === undefined || mergedData[f.key] === null)) {
        if (formData.value[f.key] !== undefined) {
          mergedData[f.key] = formData.value[f.key]
        }
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
  return collectLeafFormFieldKeys(formFields.value, formTabs.value)
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
    const doc = getCachedBpmnDocument(bpmnXml.value)
    if (!doc) return []
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

/**
 * BPMN userTask / serviceTask / subProcess → bound form metadata for diagram clicks.
 * Aligns with tasks/detail.vue `nodeFormMap` (My Request is always read-only).
 */
let pendingNodeFormMapContent: any | null = null
let nodeFormMapBuildScheduled = false

/** Diagram node preview map is expensive; build after first paint or on first node click. */
function scheduleBuildApplicationNodeFormMap(content: any) {
  pendingNodeFormMapContent = content
  if (nodeFormMapBuildScheduled) return
  nodeFormMapBuildScheduled = true
  const run = () => {
    nodeFormMapBuildScheduled = false
    const payload = pendingNodeFormMapContent
    pendingNodeFormMapContent = null
    if (payload) buildApplicationNodeFormMap(payload)
  }
  if (typeof requestIdleCallback === 'function') {
    requestIdleCallback(run, { timeout: 2500 })
  } else {
    setTimeout(run, 0)
  }
}

function ensureApplicationNodeFormMapBuilt() {
  if (nodeFormMap.value.size > 0) return
  if (pendingNodeFormMapContent) {
    const payload = pendingNodeFormMapContent
    pendingNodeFormMapContent = null
    nodeFormMapBuildScheduled = false
    buildApplicationNodeFormMap(payload)
  }
}

type ApplicationDetailSecondaryCtx = {
  content: any
  useInitiatorFormOnly: boolean
  bpmnAllOrderedForms: Array<{ formId: string | null; formName: string | null; taskName: string | null }>
  bindingRelationTableMap: Map<number, number | null>
  selectedForm: any
  lookupSourceId: number | null
}

let pendingApplicationDetailSecondary: ApplicationDetailSecondaryCtx | null = null
let applicationDetailSecondaryScheduled = false

async function loadApplicationDetailLookupConfigs(selectedForm: any) {
  lookupDbConfigs.value = {}
  if (selectedForm?.sourceId == null) return
  try {
    const lcRes = await relationTableApi.getLookupConfigs(Number(selectedForm.sourceId))
    if (!lcRes?.data) return
    for (const lc of lcRes.data) {
      let sf: string[] = []
      try {
        sf =
          typeof lc.searchFields === 'string'
            ? JSON.parse(lc.searchFields || '[]')
            : lc.searchFields || []
      } catch {
        sf = []
      }
      lookupDbConfigs.value[lc.componentId] = {
        tableId: lc.tableId,
        searchFields: sf,
        displayField: lc.displayField || '',
        viewFields: lc.viewFields || []
      }
    }
  } catch (e: unknown) {
    console.warn('[app] Failed to load lookup configs:', e)
  }
}

async function runApplicationDetailSecondary(ctx: ApplicationDetailSecondaryCtx) {
  const { content, useInitiatorFormOnly, bindingRelationTableMap, selectedForm } = ctx
  let bpmnAllOrderedForms = ctx.bpmnAllOrderedForms
  let miSubTaskFormSourceId: string | null = null

  if (content.processes?.length > 0) {
    const xml = content.processes[0].data as string
    if (bpmnAllOrderedForms.length === 0) {
      bpmnAllOrderedForms = parseBpmnXmlAndGetAllFormIds(xml)
    }
    miSubTaskFormSourceId = findMiSubTaskFormIdFromBpmn(xml)

    subTaskFormSchema.value = null
    subTaskFormId.value = null
    if (content.forms?.length > 1) {
      let detected = false
      if (miSubTaskFormSourceId) {
        const taskForm = content.forms.find((f: any) => String(f.sourceId) === miSubTaskFormSourceId)
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
      if (!detected) {
        const taskForm = content.forms.find(
          (f: any) => f.id !== selectedForm.id && f.name !== selectedForm.name
        )
        if (taskForm) {
          try {
            const cfg = typeof taskForm.data === 'string' ? JSON.parse(taskForm.data) : (taskForm.data || {})
            cfg._formName = taskForm.name
            subTaskFormSchema.value = cfg
          } catch { /* ignore parse errors */ }
        }
      }
    }

    const normHistNameInit = (s: string | null | undefined) => (s || '').trim().replace(/\s+/g, ' ')
    let initiatorSliceIndex: number | null = null
    const prevFormIds = useInitiatorFormOnly
      ? (() => {
          const allOrdered =
            bpmnAllOrderedForms.length > 0 ? bpmnAllOrderedForms : parseBpmnXmlAndGetAllFormIds(xml)
          const curRaw = snapshotTaskName || processInfo.value.currentNode || ''
          const curN = normHistNameInit(curRaw)
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
          const reachedHistoryNames = new Set(
            historyRecords.value
              .filter(h => h.status === 'completed' || h.status === 'current')
              .map(h => normHistNameInit(h.nodeName))
              .filter(n => n.length > 0)
          )
          if (reachedHistoryNames.size > 0) {
            ordered = ordered.filter(info => {
              const prevFormGuess = content.forms.find(
                (f: any) =>
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
        if (info.formId === String(selectedForm.sourceId)) {
          skipReason = 'sourceIdEqSelected'
        } else prevForm = content.forms.find((f: any) => String(f.sourceId) === info.formId)
      }
      if (!skipReason && !prevForm && info.formName) {
        if (info.formName === selectedForm.name) {
          skipReason = 'formNameEqSelected'
        } else prevForm = content.forms.find((f: any) => f.name === info.formName)
      }
      if (!skipReason && !prevForm && (info as { taskName?: string }).taskName) {
        const tn = (info as { taskName?: string }).taskName
        if (tn === selectedForm.name) {
          skipReason = 'taskNameEqSelected'
        } else prevForm = content.forms.find((f: any) => f.name === tn)
      }
      if (!skipReason && (!prevForm || prevForm.id === selectedForm.id)) {
        skipReason = !prevForm ? 'noFormMatch' : 'idEqSelected'
      }
      if (!skipReason && collectedPrevForms.some(e => e.formId === String(prevForm.id))) {
        skipReason = 'duplicate'
      }
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
        buildPreviousFormEntry(
          prevForm,
          { isKnownMiSubTask: !!isKnownMiSubTaskForm },
          content.forms,
          bindingRelationTableMap
        )
      )
    }

    if (
      useInitiatorFormOnly &&
      initiatorSliceIndex != null &&
      processInfo.value.status === 'RUNNING' &&
      (subTaskFormId.value || subTaskFormSchema.value)
    ) {
      const orderedFull =
        bpmnAllOrderedForms.length > 0 ? bpmnAllOrderedForms : parseBpmnXmlAndGetAllFormIds(xml)
      const atCur = orderedFull[initiatorSliceIndex]
      if (atCur) {
        let curForm = content.forms.find(
          (f: any) =>
            (atCur.formId && String(f.sourceId) === atCur.formId) ||
            (atCur.formName && f.name === atCur.formName) ||
            (atCur.taskName && f.name === atCur.taskName)
        )
        if (!curForm && atCur.formId && miSubTaskFormSourceId && String(atCur.formId) === String(miSubTaskFormSourceId)) {
          if (subTaskFormId.value) {
            curForm = content.forms.find((f: any) => String(f.id) === subTaskFormId.value)
          }
          if (!curForm && subTaskFormSchema.value?._formName) {
            curForm = content.forms.find((f: any) => f.name === subTaskFormSchema.value._formName)
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
            buildPreviousFormEntry(
              curForm,
              { isKnownMiSubTask: true, isActiveMiSubTaskStep: true },
              content.forms,
              bindingRelationTableMap
            )
          )
        }
      }
    }

    previousForms.value = collectedPrevForms
  } else {
    previousForms.value = []
  }

  alignProcessSubTableBindingsBySharedTable()
  scheduleBuildApplicationNodeFormMap(content)
  if (bpmnXml.value) {
    scheduleParseApplicationBpmnDiagram(bpmnXml.value)
  } else {
    diagramReady.value = true
  }

  if (snapshotTaskName) {
    const viewerId = getPortalUserId()
    if (viewerId) {
      const filterByAssignee = (bindings: typeof subTableBindings.value) => {
        for (const binding of bindings) {
          if (!binding.data || binding.data.length === 0) continue
          const assigneeField = resolveAssigneeFieldForBinding(binding.columns, binding.tableName)
          if (!assigneeField || !hasAssignmentData(binding.data, assigneeField)) continue
          const filtered = binding.data.filter(
            (row: any) =>
              rowAssigneeUserId(row, assigneeField) === viewerId &&
              String(row.task_status ?? row.sub_task_status ?? '').toUpperCase() === 'COMPLETED',
          )
          if (filtered.length > 0) {
            binding.data = filtered
          } else {
            const byAssignee = binding.data.filter(
              (row: any) => rowAssigneeUserId(row, assigneeField) === viewerId,
            )
            if (byAssignee.length > 0) {
              binding.data = byAssignee
            }
          }
        }
      }
      filterByAssignee(subTableBindings.value)
      for (const prevForm of previousForms.value) {
        filterByAssignee(prevForm.subTableBindings)
      }
      for (const nodeForm of nodeFormMap.value.values()) {
        filterByAssignee(nodeForm.subTableBindings as typeof subTableBindings.value)
      }
    }
    hydrateCurrentFormDataFromCompletedSubTaskRows()
  }
}

function scheduleApplicationDetailSecondary(historyPromise: Promise<void>) {
  const ctx = pendingApplicationDetailSecondary
  if (!ctx) return
  pendingApplicationDetailSecondary = null
  if (applicationDetailSecondaryScheduled) return
  applicationDetailSecondaryScheduled = true
  const run = async () => {
    applicationDetailSecondaryScheduled = false
    try {
      await historyPromise
      if (ctx.lookupSourceId != null) {
        await loadApplicationDetailLookupConfigs(ctx.selectedForm)
      }
      await runApplicationDetailSecondary(ctx)
    } catch (e) {
      console.error('[ApplicationDetail] secondary load failed:', e)
    }
  }
  const kick = () => {
    void run()
  }
  if (typeof requestIdleCallback === 'function') {
    requestIdleCallback(kick, { timeout: 800 })
  } else {
    setTimeout(kick, 0)
  }
}

function buildApplicationNodeFormMap(content: any) {
  const newMap = new Map<string, ApplicationDiagramNodeFormInfo>()
  const bpmnData = content.processes?.[0]?.data as string | undefined
  const formsList = content.forms as any[] | undefined
  if (!bpmnData || !formsList?.length) {
    nodeFormMap.value = newMap
    return
  }

  const normLabel = (s: string | null | undefined) => (s || '').trim().replace(/\s+/g, ' ')
  const curRaw =
    (snapshotActivityId.value ||
      snapshotTaskDefinitionKey ||
      snapshotTaskName ||
      processInfo.value.currentNode ||
      '') + ''
  const curNorm = normLabel(curRaw)
  const savedSubTables = formData.value.__subTables__
  const bindingRelationTableMap = buildBindingIdToRelationTableIdMap(formsList)
  lastBindingRelationTableMap.value = bindingRelationTableMap

  try {
    const doc = getCachedBpmnDocument(bpmnData)
    if (!doc) {
      nodeFormMap.value = newMap
      return
    }
    const allElements = doc.getElementsByTagName('*')
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]!
      const localName = el.localName || el.nodeName.split(':').pop()
      if (localName !== 'userTask' && localName !== 'subProcess' && localName !== 'serviceTask') continue
      const nodeId = el.getAttribute('id') || ''
      if (!nodeId) continue

      let formId: string | null = null
      let formName: string | null = null
      const props = el.getElementsByTagName('*')
      for (let j = 0; j < props.length; j++) {
        const p = props[j]!
        const ln = p.localName || p.nodeName.split(':').pop()
        if (ln === 'property' || ln === 'values') {
          const n = p.getAttribute('name')
          const v = p.getAttribute('value')
          if (n === 'formId' && v) formId = v
          if (n === 'formName' && v) formName = v
        }
      }

      let matchedForm: any = null
      if (formId) {
        matchedForm = formsList.find((f: any) => String(f.sourceId) === formId)
      }
      if (!matchedForm && formName) {
        matchedForm = formsList.find((f: any) => f.name === formName)
      }
      if (!matchedForm) continue

      const nodeName = el.getAttribute('name') || nodeId
      const isCurrentStep =
        (!!snapshotTaskDefinitionKey && nodeId === String(snapshotTaskDefinitionKey).trim()) ||
        (!!snapshotActivityId.value && nodeId === String(snapshotActivityId.value).trim()) ||
        (!!curNorm.length && normLabel(nodeName) === curNorm) ||
        (!!curRaw.trim() && nodeId === curRaw.trim())

      const nodeFields: FormField[] = []
      const nodeTabs: FormTab[] = []
      const nodeBindings: PreviousFormEntry['subTableBindings'] = []
      const nativeSubTableBindingIds = (matchedForm.tableBindings || [])
        .filter((b: { bindingType?: string }) => b.bindingType !== 'PRIMARY')
        .map((b: { bindingId?: number }) => Number(b.bindingId))
        .filter((n: number) => Number.isFinite(n))
      let configForSubTables: Record<string, any> = {}
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
                nodeTabs.push({
                  name: tabPane.props.name || `tab_${nodeTabs.length}`,
                  label: tabPane.props.label || `Tab ${nodeTabs.length + 1}`,
                  fields: tabFields
                })
              }
            }
          } else {
            nodeFields.push(...extractFieldsRecursive(rules))
          }
        }
        let subForms: Record<string, any> = {}
        try {
          configForSubTables = cfg
          subForms = cfg.subForms || {}
        } catch {
          /* ignore */
        }
        const subTablePortalViewsPayload = cfg.subTablePortalViews || {}
        for (const b of matchedForm.tableBindings || []) {
          if (b.bindingType === 'PRIMARY') continue
          let cols = resolveSubTableBindingColumnsForPortal(b, configForSubTables, formsList)
          if ((!Array.isArray(cols) || cols.length === 0) && isPortalSharedAttachmentTableBinding(b)) {
            cols = defaultAttachmentListColumns()
          }
          if (!Array.isArray(cols) || cols.length === 0) continue
          const subFormDesign = resolveSubFormDesign(b, subForms)
          const bindingPortalViews =
            subTablePortalViewsPayload[b.bindingId]
            ?? subTablePortalViewsPayload[String(b.bindingId)]
            ?? null
          const binding = {
            bindingId: b.bindingId,
            tableId: b.tableId != null ? Number(b.tableId) : null,
            bindingType: b.bindingType,
            bindingMode: b.bindingMode,
            foreignKeyField: b.foreignKeyField,
            tableName: b.tableDisplayName || b.tableName,
            physicalTableName: b.tableName,
            tableType: b.tableType,
            tableDescription: b.tableDescription,
            columns: cols,
            data: [] as any[],
            subMode: b.subMode,
            formFields: subFormDesign.formFields,
            formOptions: subFormDesign.formOptions,
            portalViews: bindingPortalViews,
            primaryKeyFields: resolveSubTablePrimaryKeyFields(b.primaryKeyFields, b.bindingId, configForSubTables)
          }
          if (savedSubTables && typeof savedSubTables === 'object') {
            const saved = getSavedSubTableRowsFromVariables(
              savedSubTables,
              {
                bindingId: b.bindingId,
                tableName: b.tableName,
                tableDisplayName: b.tableDisplayName
              },
              binding.primaryKeyFields
            )
            if (saved) binding.data = saved
          }
          nodeBindings.push(binding)
        }
        mergeLinkFormTargetBindingsInto(nodeBindings, formsList, configForSubTables, subForms)
        stripLinkOnlySubTableFieldsFromBindings(nodeBindings, subForms, configForSubTables.rule, configForSubTables)
        if (savedSubTables && typeof savedSubTables === 'object') {
          for (const binding of nodeBindings) {
            const saved = getSavedSubTableRowsFromVariables(
              savedSubTables,
              {
                bindingId: binding.bindingId,
                tableName: (binding as { physicalTableName?: string }).physicalTableName,
                tableDisplayName: binding.tableName
              },
              binding.primaryKeyFields
            )
            if (saved) binding.data = saved
          }
        }
        hydrateChildSubTablesFromParentsNestedRows(
          nodeBindings,
          savedSubTables && typeof savedSubTables === 'object' ? (savedSubTables as Record<string, unknown>) : null,
          bindingRelationTableMap
        )
        if (savedSubTables && typeof savedSubTables === 'object') {
          hydrateBindingsRowsFromVariablesBySharedRelationTableId(
            nodeBindings,
            savedSubTables as Record<string, unknown>,
            bindingRelationTableMap
          )
        }
        enrichChildBindingRowsFromParentsNestedSubTables(nodeBindings)
        applySharedAttachmentFinalizeAndMaterialize(nodeBindings, formData.value as Record<string, unknown>, {
          flattened:
            savedSubTables && typeof savedSubTables === 'object'
              ? (savedSubTables as Record<string, unknown>)
              : null,
          bindingTableById: bindingRelationTableMap,
        })

        const formRulesForFilter =
          cfg.rule && Array.isArray(cfg.rule) ? cfg.rule : (Array.isArray(cfg) ? cfg : [])
        const nativeIdSet = new Set(nativeSubTableBindingIds)
        if (formRulesForFilter.length > 0 && nodeBindings.length > 0) {
          if (nodeFields.length > 0) {
            const filtered = filterLinkOnlyStandaloneSubTableFields(
              nodeFields,
              nodeBindings,
              formRulesForFilter,
              nativeIdSet,
              configForSubTables,
            )
            nodeFields.length = 0
            nodeFields.push(...filtered)
          }
          for (const tab of nodeTabs) {
            tab.fields = filterLinkOnlyStandaloneSubTableFields(
              tab.fields,
              nodeBindings,
              formRulesForFilter,
              nativeIdSet,
              configForSubTables,
            )
          }
        }
      } catch {
        /* ignore per-node parse errors */
      }

      newMap.set(nodeId, {
        formName: matchedForm.name || nodeName,
        isCurrentStep,
        fields: nodeFields,
        tabs: nodeTabs,
        values: { ...formData.value },
        subTableBindings: nodeBindings,
        nativeSubTableBindingIds,
        formConfig: configForSubTables,
      })
    }
  } catch (e) {
    console.warn('[ApplicationDetail] buildApplicationNodeFormMap failed:', e)
  }

  nodeFormMap.value = newMap
}

// Load process details
const loadProcessDetail = async () => {
  loading.value = true
  diagramReady.value = false
  diagramParseScheduled = false
  clearBpmnParseCache()
  pendingNodeFormMapContent = null
  nodeFormMapBuildScheduled = false
  pendingApplicationDetailSecondary = null
  applicationDetailSecondaryScheduled = false
  try {
    const res = await processApi.getProcessDetail(processId)
    const data = res.data || res
    if (data) {
      processInfo.value = data
      if (data.variables) formData.value = data.variables
      const stCoerced = coerceSubTablesVariableToMap(formData.value.__subTables__)
      if (stCoerced) {
        formData.value = { ...formData.value, __subTables__: stCoerced }
      }

      const processKey = data.processDefinitionKey
      const historyPromise = loadProcessHistory()
      const fuFetchPromise = processKey
        ? processApi.getFunctionUnitContent(processKey).then(r => r.data || r).catch(err => {
            console.error('Failed to fetch function unit content:', err)
            return null
          })
        : Promise.resolve(null)

      const prefetchedFu = await fuFetchPromise

      if (processKey && prefetchedFu) {
        try {
          await loadFunctionUnitContent(processKey, prefetchedFu)
        } catch (error) {
          console.error('Failed to load function unit content, but continuing:', error)
        }
        scheduleApplicationDetailSecondary(historyPromise)
      } else {
        await historyPromise
      }

      // Completed Tasks: do not advance the diagram to the next active node.
      if (snapshotTaskName) {
        currentNodeId.value = ''
      }
    }
  } catch (error: any) {
    console.error('Failed to load process detail:', error)
    ElMessage.error(t('applicationDetail.loadFailed'))
  } finally {
    loading.value = false
    if (!diagramReady.value && !diagramParseScheduled && !bpmnXml.value) {
      diagramReady.value = true
    }
  }
}

// Load function unit content (optional prefetched payload avoids duplicate HTTP when parallel with history)
const loadFunctionUnitContent = async (processKey: string, prefetchedContent?: any) => {
  try {
    const content =
      prefetchedContent ??
      (await processApi.getFunctionUnitContent(processKey).then(r => r.data || r))
    if (content.error) {
      console.error('Function unit content error:', content.error)
      return
    }

    selectedNodeId.value = null

    let currentFormInfo: { formId: string | null, formName: string | null } = { formId: null, formName: null }
    /**
     * Initiator My Request: still use dedicated BFS for `previousForms` (MI subprocess), but the
     * **main** form always follows the current BPMN userTask — including MI subtask (`subform_copy`)
     * and later approval steps — so it matches the designer’s per-node portalViews.
     */
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
      isInitiatorMyRequestView.value = useInitiatorFormOnly

      currentFormInfo = parseBpmnXmlAndGetFormId(xml)
      bpmnXml.value = xml
      refreshActiveMiSubProcessScopeFromBpmn()
    }
    
    if (content.forms?.length > 0) {      // Select the correct form based on the current node formId
      cachedContentForms = content.forms || []
      cachedRelationTableFieldIndex = buildRelationTableFieldIndexFromDataTables(content.dataTables)
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

      let selectedFormConfig: Record<string, any> = {}
      try {
        const cfg =
          typeof selectedForm.data === 'string'
            ? JSON.parse(selectedForm.data)
            : (selectedForm.data || {})
        selectedFormConfig = cfg
        relationViewConfigs.value = cfg.relationViews || {}
      } catch {
        selectedFormConfig = {}
        relationViewConfigs.value = {}
      }

      parseFormConfig(selectedForm.data)

      // Load sub-table bindings (SUB and RELATED, not PRIMARY).
      // FORM_ONLY bindings without a subTable node still join linkableSubTableBindings so Link Form can resolve;
      // bottomSubTableBindings / unplacedSubTableBindings omit them to avoid empty duplicate sections.
      const bindings: typeof subTableBindings.value = []
      const tableBindings: any[] = selectedForm.tableBindings || []
      mainFormNativeSubTableBindingIds.value = tableBindings
        .filter((b: { bindingType?: string }) => b.bindingType !== 'PRIMARY')
        .map((b: { bindingId?: number }) => Number(b.bindingId))
        .filter((n: number) => Number.isFinite(n))
      mainFormConfig.value = selectedFormConfig
      const subFormsPayload = selectedFormConfig.subForms || {}
      const subTablePortalViewsPayload = selectedFormConfig.subTablePortalViews || {}
      for (const b of tableBindings) {
        if (b.bindingType === 'PRIMARY') continue
        let columns = resolveSubTableBindingColumnsForPortal(b, selectedFormConfig, content.forms)
        if ((!Array.isArray(columns) || columns.length === 0) && isPortalSharedAttachmentTableBinding(b)) {
          columns = defaultAttachmentListColumns()
        }
        if (!Array.isArray(columns) || columns.length === 0) continue
        const subFormDesign = resolveSubFormDesign(b, subFormsPayload)
        const bindingPortalViews =
          subTablePortalViewsPayload[b.bindingId]
          ?? subTablePortalViewsPayload[String(b.bindingId)]
          ?? null
        bindings.push({
          bindingId: b.bindingId,
          tableId: b.tableId != null ? Number(b.tableId) : null,
          bindingType: b.bindingType,
          bindingMode: b.bindingMode,
          foreignKeyField: b.foreignKeyField,
          tableName: b.tableDisplayName || b.tableName,
          physicalTableName: b.tableName,
          tableType: b.tableType,
          tableDescription: b.tableDescription,
          columns,
          data: [],
          subMode: b.subMode,
          formFields: subFormDesign.formFields,
          formOptions: subFormDesign.formOptions,
          portalViews: bindingPortalViews,
          primaryKeyFields: resolveSubTablePrimaryKeyFields(
            b.primaryKeyFields,
            b.bindingId,
            selectedFormConfig
          )
        })
      }

      mergeLinkFormTargetBindingsInto(bindings, content.forms as any[], selectedFormConfig, subFormsPayload)
      stripLinkOnlySubTableFieldsFromBindings(bindings, subFormsPayload, selectedFormConfig.rule, selectedFormConfig)

      const bindingRelationTableMap = buildBindingIdToRelationTableIdMap(content.forms as any[])
      lastBindingRelationTableMap.value = bindingRelationTableMap

      // Restore sub-table data from variables (promote nested link-form rows so bindings resolve like To Do).
      const rawSubTables = coerceSubTablesVariableToMap(formData.value.__subTables__)
      if (rawSubTables) {
        flattenNestedSubTableRowsIntoPayload(rawSubTables as Record<string, unknown>)
        formData.value = { ...formData.value, __subTables__: rawSubTables }
      }
      const savedSubTables = formData.value.__subTables__
      if (savedSubTables && typeof savedSubTables === 'object') {
        for (const binding of bindings) {
          const raw = tableBindings.find((x: any) => Number(x.bindingId) === Number(binding.bindingId))
          const saved = getSavedSubTableRowsFromVariables(
            savedSubTables,
            {
              bindingId: binding.bindingId,
              tableName: raw?.tableName ?? (binding as { physicalTableName?: string }).physicalTableName,
              tableDisplayName: raw?.tableDisplayName ?? binding.tableName
            },
            binding.primaryKeyFields
          )
          if (saved) binding.data = saved
        }
        hydrateChildSubTablesFromParentsNestedRows(
          bindings,
          savedSubTables as Record<string, unknown>,
          bindingRelationTableMap
        )
        hydrateBindingsRowsFromVariablesBySharedRelationTableId(
          bindings,
          savedSubTables as Record<string, unknown>,
          bindingRelationTableMap
        )
        enrichChildBindingRowsFromParentsNestedSubTables(bindings)
      }
      subTableBindings.value = bindings
      applyLinkOnlySubTableFieldFilterToMainForm(selectedFormConfig)
      alignMainSubTableBindingsOnly()

      pendingApplicationDetailSecondary = {
        content,
        useInitiatorFormOnly,
        bpmnAllOrderedForms: [],
        bindingRelationTableMap,
        selectedForm,
        lookupSourceId:
          selectedForm.sourceId != null ? Number(selectedForm.sourceId) : null
      }
    } else {
      diagramReady.value = true
      previousForms.value = []
      subTableBindings.value = []
      nodeFormMap.value = new Map()
      isInitiatorMyRequestView.value = false
      selectedNodeId.value = null
    }
  } catch (error) {
    console.error('Failed to load function unit content:', error)
  }
}

// Parse BPMN XML and get the current node formId and formName
const parseBpmnXmlAndGetFormId = (xml: string): { formId: string | null, formName: string | null } => {
  if (!xml) return { formId: null, formName: null }
  
  try {
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return { formId: null, formName: null }
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

// Parse BPMN XML: return form info bound to all nodes before the current node, in topological order (deduplicated)
const parseBpmnXmlAndGetPreviousFormIds = (xml: string): Array<{ formId: string | null, formName: string | null, taskName: string | null }> => {
  if (!xml) return []
  try {
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return []
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
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return []
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
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return null
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
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return null
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
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return
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

    const ckDiag = (s: unknown) => String(s ?? '').trim()
    const normLabDiag = (s: unknown) => ckDiag(s).replace(/\s+/g, ' ')

    const flowEdgesDiag: Array<{ sourceRef: string; targetRef: string }> = []
    for (let fi = 0; fi < allElements.length; fi++) {
      const fln = allElements[fi].localName || allElements[fi].nodeName.split(':').pop()
      if (fln !== 'sequenceFlow') continue
      flowEdgesDiag.push({
        sourceRef: allElements[fi].getAttribute('sourceRef') || '',
        targetRef: allElements[fi].getAttribute('targetRef') || '',
      })
    }

    const findBpmnElementByIdAnyDiag = (nodeId: string): Element | null => {
      for (let fi = 0; fi < allElements.length; fi++) {
        if (ckDiag(allElements[fi].getAttribute('id')) === ckDiag(nodeId)) return allElements[fi]
      }
      return null
    }

    const isUnderGivenSubProcessDiag = (elementRef: Element | null, boundarySpId: string): boolean => {
      let node: Node | null = elementRef?.parentNode ?? null
      while (node && node.nodeType === 1) {
        const wrap = node as Element
        const wln = wrap.localName || wrap.nodeName.split(':').pop()
        if (wln === 'subProcess' && ckDiag(wrap.getAttribute('id')) === ckDiag(boundarySpId)) return true
        if (wln === 'process' || wln === 'definitions') break
        node = wrap.parentNode
      }
      return false
    }

    const nearestActiveMiSubProcessAncestorIdDiag = (from: Element): string | null => {
      let node: Node | null = from.parentNode
      while (node && node.nodeType === 1) {
        const wrap = node as Element
        const wln = wrap.localName || wrap.nodeName.split(':').pop()
        if (wln === 'subProcess') {
          const sid = ckDiag(wrap.getAttribute('id'))
          if (sid && activeMultiInstanceSubProcesses.has(sid)) return sid
        }
        if (wln === 'process' || wln === 'definitions') break
        node = wrap.parentNode
      }
      return null
    }

    const isDescendantOfActiveMiSubProcessDiag = (element: Element): boolean => {
      let node: Node | null = element.parentNode
      while (node && node.nodeType === 1) {
        const el = node as Element
        const lnn = el.localName || el.nodeName.split(':').pop()
        if (lnn === 'subProcess') {
          const sid = el.getAttribute('id') || ''
          if (sid && activeMultiInstanceSubProcesses.has(sid)) return true
        }
        if (lnn === 'process' || lnn === 'definitions') break
        node = el.parentNode
      }
      return false
    }

    const isDownstreamUserTaskInsideSameActiveMiDiag = (openTaskId: string, candidateTaskId: string, boundarySpId: string): boolean => {
      const openEl = findBpmnElementByIdAnyDiag(openTaskId)
      if (!openEl || !isUnderGivenSubProcessDiag(openEl, boundarySpId)) return false
      if (ckDiag(openTaskId) === ckDiag(candidateTaskId)) return false
      const queue: string[] = [openTaskId]
      const visited = new Set<string>()
      while (queue.length > 0) {
        const u = queue.shift()!
        if (visited.has(u)) continue
        visited.add(u)
        for (const f of flowEdgesDiag) {
          if (ckDiag(f.sourceRef) !== ckDiag(u)) continue
          const tar = ckDiag(f.targetRef)
          const tarEl = findBpmnElementByIdAnyDiag(tar)
          if (!tarEl || !isUnderGivenSubProcessDiag(tarEl, boundarySpId)) continue
          if ((tarEl.localName || tarEl.nodeName.split(':').pop()) === 'userTask' && tar === ckDiag(candidateTaskId)) return true
          queue.push(tar)
        }
      }
      return false
    }

    let currentOpenBpmnUserTaskIdDiag = ''
    if (processInfo.value.status === 'RUNNING' && !snapshotActive) {
      const ctk = (snapshotTaskDefinitionKey || '').trim()
      doc.querySelectorAll('userTask').forEach((ut: Element) => {
        const uid = ckDiag(ut.getAttribute('id'))
        if (!uid) return
        if (ctk && uid === ckDiag(ctk)) currentOpenBpmnUserTaskIdDiag = uid
      })
      if (!currentOpenBpmnUserTaskIdDiag) {
        doc.querySelectorAll('userTask').forEach((ut: Element) => {
          const uid = ckDiag(ut.getAttribute('id'))
          if (!uid) return
          const unm = normLabDiag(ut.getAttribute('name'))
          if (unm === normLabDiag(currentNodeName) || uid === ckDiag(currentNodeName)) currentOpenBpmnUserTaskIdDiag = uid
        })
      }
    }

    const shouldSuppressSiblingAggregationCompleteDiag = (userTaskEl: Element, userTaskBpmnId: string): boolean => {
      const boundary = nearestActiveMiSubProcessAncestorIdDiag(userTaskEl)
      if (!boundary || !currentOpenBpmnUserTaskIdDiag) return false
      if (ckDiag(userTaskBpmnId) === ckDiag(currentOpenBpmnUserTaskIdDiag)) return false
      return isDownstreamUserTaskInsideSameActiveMiDiag(currentOpenBpmnUserTaskIdDiag, userTaskBpmnId, boundary)
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
        // Align with todo (useBpmnParser): internal start is completed once MI instance is active
        startStatus = 'completed'
      }
      nodes.push({ id, name: event.getAttribute('name') || t('task.startNode'), type: 'start', status: startStatus, x: pos?.x, y: pos?.y, width: pos?.width, height: pos?.height })
      if (startStatus === 'completed') {
        completed.push(id)
      }
    })

    const completedHistoryIdsForMi = new Set<string>()
    const completedNodeNamesForMi = new Set<string>()
    historyRecords.value.forEach(record => {
      if (record.status === 'completed' && record.nodeId) completedHistoryIdsForMi.add(String(record.nodeId).trim())
      if (record.status === 'completed' && record.nodeName) completedNodeNamesForMi.add(record.nodeName)
    })

    // Parse user tasks
    doc.querySelectorAll('userTask').forEach((task, index) => {
      const id = task.getAttribute('id') || `task_${index}`
      const name = task.getAttribute('name') || t('task.taskFallbackName', { index: index + 1 })
      const pos = positionMap.get(id)

      let status: 'completed' | 'current' | 'pending' | 'rejected' = 'pending'
      const parentSpId = getParentSubProcessId(task)
      const inActiveMi = !!(parentSpId && activeMultiInstanceSubProcesses.has(parentSpId))

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
      } else if (
        processInfo.value.status === 'RUNNING'
        && !snapshotActive
        && inActiveMi
      ) {
        /** Same rules as todo task detail (`useBpmnParser`): current step + downstream suppression inside MI */
        const ctd = (snapshotTaskDefinitionKey || '').trim()
        const openTaskMatches =
          normLabDiag(name) === normLabDiag(currentNodeName)
          || ckDiag(id) === ckDiag(currentNodeName)
          || (ctd && (ckDiag(id) === ckDiag(ctd) || normLabDiag(name) === normLabDiag(ctd)))

        if (openTaskMatches) {
          status = 'current'
          currentNodeId.value = id
          foundCurrentNode = true
        } else if (completedHistoryIdsForMi.has(id) || completedNodeNamesForMi.has(name)) {
          if (
            isDescendantOfActiveMiSubProcessDiag(task)
            && (
              (ctd && (ckDiag(id) === ckDiag(ctd) || normLabDiag(name) === normLabDiag(ctd)))
              || normLabDiag(name) === normLabDiag(currentNodeName)
              || ckDiag(id) === ckDiag(currentNodeName)
            )
          ) {
            status = 'current'
            currentNodeId.value = id
            foundCurrentNode = true
          } else if (shouldSuppressSiblingAggregationCompleteDiag(task, id)) {
            status = 'pending'
          } else {
            status = 'completed'
            completed.push(id)
          }
        } else if (!foundCurrentNode) {
          const hm = historyRecords.value.find(h => normLabDiag(h.nodeName) === normLabDiag(name))
          const sameOpenMi =
            isDescendantOfActiveMiSubProcessDiag(task)
            && (
              (ctd && (ckDiag(id) === ckDiag(ctd) || normLabDiag(name) === normLabDiag(ctd)))
              || normLabDiag(name) === normLabDiag(currentNodeName)
              || ckDiag(id) === ckDiag(currentNodeName)
            )
          if (hm && hm.status === 'completed' && !sameOpenMi) {
            if (shouldSuppressSiblingAggregationCompleteDiag(task, id)) status = 'pending'
            else {
              status = 'completed'
              completed.push(id)
            }
          }
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
      const gwIncomingIds = earlyFlows.filter(f => f.targetRef === id).map(f => f.sourceRef)
      const gwHasCompletedPred = gwIncomingIds.some(srcId => completed.includes(srcId))

      if (parentSpId && activeMultiInstanceSubProcesses.has(parentSpId)) {
        // MI: match `useBpmnParser` — gateways are never "current" (orange); gray until an incoming node is completed, then green.
        status = gwHasCompletedPred ? 'completed' : 'pending'
      } else if (snapshotActive) {
        // Snapshot mode: check if the gateway incoming nodes are completed
        if (completedNodeNames.has(name)) {
          status = 'completed'
        } else if (gwHasCompletedPred) {
          status = 'completed'
        }
      } else if (completedNodeNames.has(name)) {
        status = 'completed'
      } else if (processInfo.value.status === 'COMPLETED') {
        // Process completed: only mark gateways on the actually executed path
        if (gwHasCompletedPred) {
          status = 'completed'
        }
      } else {
        // Check for completed incoming nodes (via sequenceFlow)
        if (gwHasCompletedPred) {
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

// form-create runtime-only nodes: do not emit as fields, but children must be traversed
// (sub-table row layouts use subForm/tableForm wrappers).
const FC_SKIP_TYPES = new Set(['subForm', 'tableForm', 'tableFormColumn'])

// Recursively extract fields.
// `skipSubTable`: when traversing subForm/tableForm wrappers on the main canvas, do not promote
// nested subTable widgets (e.g. link-form target subtable2) to the page-level field list.
const extractFieldsRecursive = (
  items: any[],
  ctx: { skipSubTable?: boolean } = {},
): FormField[] => {
  const fields: FormField[] = []
  for (const item of items) {
    const bindingId = item._bindingId ?? item.props?._bindingId
    if (item.type === 'subTable' && bindingId != null) {
      if (!ctx.skipSubTable) {
        const rawPv = item.props?.portalViews
        const hasWidgetPortalViews =
          rawPv != null && typeof rawPv === 'object' && Object.keys(rawPv).length > 0
        fields.push({
          key: `__subTable_${bindingId}`,
          label: '',
          type: 'subTable',
          _bindingId: Number(bindingId),
          ...(hasWidgetPortalViews ? { portalViews: normalizePortalViews(rawPv) } : {}),
          span: 24,
        })
      }
      continue
    } else if (isCardRule(item)) {
      fields.push({
        key: getLayoutKey(item, fields.length, 'card'),
        label: getLayoutLabel(item),
        type: 'card',
        span: item.col?.span || 24,
        children: item.children && Array.isArray(item.children)
          ? extractFieldsRecursive(item.children, ctx)
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
      const childCtx = FC_SKIP_TYPES.has(item.type) ? { skipSubTable: true } : ctx
      fields.push(...extractFieldsRecursive(item.children, childCtx))
    }
  }
  return fields
}

/** Link Form / sub-table row dialog: same contract as tasks/detail.vue — fields from designer subForm. */
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

function isSyntheticLookupField(fieldName?: string): boolean {
  return !fieldName || String(fieldName).startsWith('lookup:')
}

function isAssigneeLikeLabel(label?: string): boolean {
  const normalized = String(label || '').trim().toLowerCase()
  return /assignee|处理人|負責人|经办人|經辦人/.test(normalized)
}

/** Align with tasks/detail.vue: relation view + lookup config for sub-table list columns. */
function buildLookupColumnProps(rawLookupConfig: unknown): Record<string, any> {
  let lookupCfg: any = {}
  try {
    lookupCfg = typeof rawLookupConfig === 'string' ? JSON.parse(rawLookupConfig || '{}') : (rawLookupConfig || {})
  } catch {
    lookupCfg = {}
  }
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

// Derive display columns for a sub-table binding from the designer config.
// List-view column order comes from subListViews; control types/options come from subForm (same as process start / task detail).
const deriveColumnsFromBinding = (binding: any, formConfig?: Record<string, any>): Array<{ field: string; label: string; type?: string; required?: boolean; options?: Array<{ label: string; value: any }>; props?: Record<string, any> }> => {
  const subFormRule =
    binding.subFormConfig?.rule ||
    formConfig?.subForms?.[binding.bindingId]?.rule ||
    formConfig?.subForms?.[String(binding.bindingId)]?.rule

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
      } else if (r.type === 'lookup') {
        type = 'lookup'
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

      // lookup — same merge as tasks/detail.vue (rt_lookup_configs + relationViews)
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
      : []

  const listColumns = resolveSubListViewColumnsForBinding(
    formConfig,
    binding.bindingId,
    subFormColumns.map(c => c.field),
  )

  if (Array.isArray(listColumns) && listColumns.length > 0) {
    const ruleByField = new Map(
      (Array.isArray(subFormRule) ? subFormRule : []).map((ruleItem: any) => [ruleItem?.field, ruleItem])
    )
    const subFormColumnByField = new Map(subFormColumns.map(col => [col.field, col]))
    const assigneeField = resolveAssigneeFieldForBinding(
      subFormColumns as Array<{ field?: string }>,
      binding.tableDisplayName || binding.tableName
    )
    return listColumns
      .filter((col: any) => col && col.fieldName)
      .map((column: any) => {
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
          const field =
            isSyntheticLookupField(column.fieldName) && isAssigneeLikeLabel(label) && assigneeField
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
            props: buildLookupColumnProps(fieldRule?.props?.lookupConfig || baseColumn?.props?.lookupConfig || '{}')
          }
        }

        return mergeListViewFieldColumn(column, baseColumn, fieldRule)
      })
  }

  const tableId = binding.tableId != null ? Number(binding.tableId) : NaN
  if (Number.isFinite(tableId) && cachedContentForms.length > 0) {
    const alt = resolveSubTableSchemaByTableId(tableId, cachedContentForms, binding.bindingId)
    if (alt) {
      const fromAlt = deriveColumnsFromBinding(
        { ...binding, bindingId: alt.bindingId },
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
function findRawBindingInFormsForLinkMerge(
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

function linkTargetHasLocalSchemaForMerge(tid: number, formConfig: Record<string, any>, subForms: Record<string, any>): boolean {
  const sid = String(tid)
  const sf = subForms?.[tid] ?? subForms?.[sid]
  if (sf?.rule && Array.isArray(sf.rule) && sf.rule.length > 0) return true
  const lv = formConfig?.subListViews?.[tid] ?? formConfig?.subListViews?.[sid]
  if (lv?.columns && Array.isArray(lv.columns) && lv.columns.length > 0) return true
  return false
}

function resolveSubTableSchemaSourceForTargetMerge(
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
  if (linkTargetHasLocalSchemaForMerge(tid, preferFormConfig, preferSubForms)) {
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
    if (linkTargetHasLocalSchemaForMerge(tid, formConfig, sf)) {
      return {
        formConfig,
        subForms: sf,
        origin: 'crossForm',
        sourceFormName: f.name != null ? String(f.name) : undefined
      }
    }
  }
  return null
}

/**
 * Link Form targets (e.g. subtable2) keep {@code formFields} for modals but drop duplicate
 * {@code subTable} widgets when the designer did not place them on the sub-form canvas.
 */
function stripLinkOnlySubTableFieldsFromBindings(
  bindings: Array<{ bindingId: number; formFields?: FormField[] }>,
  subForms: Record<string, unknown>,
  mainFormRule?: unknown[],
  formConfig?: Record<string, unknown> | null,
) {
  for (const b of bindings) {
    if (!Array.isArray(b.formFields) || b.formFields.length === 0) continue
    const design = (subForms?.[b.bindingId] ?? subForms?.[String(b.bindingId)] ?? {}) as {
      rule?: unknown[]
    }
    const rule = Array.isArray(design.rule) && design.rule.length > 0
      ? design.rule
      : (Array.isArray(mainFormRule) ? mainFormRule : [])
    b.formFields = filterLinkOnlyStandaloneSubTableFields(b.formFields, bindings, rule, undefined, formConfig)
  }
}

/** Drop link-only sub-table widgets from the main form field tree once bindings are loaded. */
function applyLinkOnlySubTableFieldFilterToMainForm(formConfig: Record<string, any>) {
  const rules = Array.isArray(formConfig?.rule) ? formConfig.rule : []
  const bindings = subTableBindings.value
  const nativeIdSet = new Set(mainFormNativeSubTableBindingIds.value.map(Number))
  if (formFields.value.length > 0) {
    formFields.value = filterLinkOnlyStandaloneSubTableFields(
      formFields.value,
      bindings,
      rules,
      nativeIdSet,
      formConfig,
    )
  }
  if (formTabs.value.length > 0) {
    formTabs.value = formTabs.value.map(tab => ({
      ...tab,
      fields: filterLinkOnlyStandaloneSubTableFields(tab.fields, bindings, rules, nativeIdSet, formConfig),
    }))
  }
}

/**
 * Link Form targets may reference bindings omitted from the active form's tableBindings slice.
 * Same contract as tasks/detail.vue so Link Form modal / fallback rows resolve on My Request.
 */
function mergeLinkFormTargetBindingsInto(
  bindings: Array<{
    bindingId: number
    tableId?: number | null
    bindingType: string
    bindingMode: string
    foreignKeyField: string | null
    tableName: string
    physicalTableName?: string
    tableType: string
    tableDescription: string
    columns: Array<{ field: string; label: string; type?: string }>
    data: any[]
    subMode?: string
    formFields?: FormField[]
    formOptions?: Record<string, any>
    portalViews?: Record<string, any> | null
    primaryKeyFields?: string[]
  }>,
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
    for (const tid of collectLinkFormTargetBindingIdsFromSubListViews(localFormConfig)) {
      targetIds.add(tid)
    }
    for (const tid of targetIds) {
      if (known.has(tid)) continue
      const found = findRawBindingInFormsForLinkMerge(contentForms, tid)
      if (found) {
        const { raw, formConfig } = found
        if (raw.bindingType === 'PRIMARY') continue
        const sf = formConfig.subForms || {}
        const schemaSrc =
          resolveSubTableSchemaSourceForTargetMerge(tid, formConfig, sf, contentForms) ??
          ({ formConfig, subForms: sf, origin: 'local' as const })
        const effFormConfig = schemaSrc.formConfig
        const effSubForms = schemaSrc.subForms
        const columns = deriveColumnsFromBinding(raw, effFormConfig)
        const subFormDesign = resolveSubFormDesign(raw, effSubForms)
        const stpv = effFormConfig.subTablePortalViews || {}
        const bindingPortalViews = stpv[raw.bindingId] ?? stpv[String(raw.bindingId)] ?? null
        bindings.push({
          bindingId: raw.bindingId,
          tableId: raw.tableId != null ? Number(raw.tableId) : null,
          bindingType: raw.bindingType,
          bindingMode: raw.bindingMode,
          foreignKeyField: raw.foreignKeyField,
          tableName: raw.tableDisplayName || raw.tableName,
          physicalTableName: raw.tableName,
          tableType: raw.tableType,
          tableDescription: raw.tableDescription,
          columns,
          subMode: raw.subMode,
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
      const syntheticSchema = resolveSubTableSchemaSourceForTargetMerge(
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
      const columns = deriveColumnsFromBinding(synthetic, syntheticSchema.formConfig)
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

/** Build a read-only PreviousFormEntry from designer form metadata (shared by history + live MI step). */
function buildPreviousFormEntry(
  prevForm: any,
  options: { isKnownMiSubTask: boolean; isActiveMiSubTaskStep?: boolean },
  allContentForms?: any[],
  bindingRelationTableMap?: Map<number, number | null>,
): PreviousFormEntry {
  const savedSubTables = formData.value.__subTables__
  // My Request only consumes previousForms[*].subTableBindings (align / link-form); skip rule walks.
  const parsedFields: FormField[] = []
  const parsedTabs: FormTab[] = []

  let prevFormConfig: Record<string, any> = {}
  try {
    const cfg = typeof prevForm.data === 'string' ? JSON.parse(prevForm.data) : (prevForm.data || {})
    prevFormConfig = cfg || {}
  } catch { /* ignore */ }
  const prevBindings: PreviousFormEntry['subTableBindings'] = []
  for (const b of (prevForm.tableBindings || [])) {
    if (b.bindingType === 'PRIMARY') continue
    const cols = resolveSubTableBindingColumnsForPortal(b, prevFormConfig, allContentForms)
    if (!Array.isArray(cols) || cols.length === 0) continue
    const prevSubForms = prevFormConfig.subForms || {}
    const prevSubTablePortalViews = prevFormConfig.subTablePortalViews || {}
    const subFormDesign = resolveSubFormDesign(b, prevSubForms)
    const bindingPortalViews =
      prevSubTablePortalViews[b.bindingId]
      ?? prevSubTablePortalViews[String(b.bindingId)]
      ?? null
    const binding = {
      bindingId: b.bindingId,
      tableId: b.tableId != null ? Number(b.tableId) : null,
      bindingType: b.bindingType,
      bindingMode: b.bindingMode,
      foreignKeyField: b.foreignKeyField,
      tableName: b.tableDisplayName || b.tableName,
      physicalTableName: b.tableName,
      tableType: b.tableType,
      tableDescription: b.tableDescription,
      columns: cols,
      data: [] as any[],
      subMode: b.subMode,
      formFields: subFormDesign.formFields,
      formOptions: subFormDesign.formOptions,
      portalViews: bindingPortalViews,
      primaryKeyFields: resolveSubTablePrimaryKeyFields(
        b.primaryKeyFields,
        b.bindingId,
        prevFormConfig
      )
    }
    if (savedSubTables) {
      const saved = getSavedSubTableRowsFromVariables(
        savedSubTables,
        {
          bindingId: b.bindingId,
          tableName: b.tableName,
          tableDisplayName: b.tableDisplayName
        },
        binding.primaryKeyFields
      )
      if (Array.isArray(saved)) binding.data = saved
    }
    prevBindings.push(binding)
  }

  if (allContentForms?.length) {
    mergeLinkFormTargetBindingsInto(prevBindings, allContentForms, prevFormConfig, prevFormConfig.subForms || {})
    stripLinkOnlySubTableFieldsFromBindings(
      prevBindings,
      prevFormConfig.subForms || {},
      prevFormConfig.rule,
      prevFormConfig,
    )
  }
  if (savedSubTables && typeof savedSubTables === 'object') {
    for (const binding of prevBindings) {
      const raw = (prevForm.tableBindings || []).find((x: any) => Number(x.bindingId) === Number(binding.bindingId))
      const saved = getSavedSubTableRowsFromVariables(
        savedSubTables,
        {
          bindingId: binding.bindingId,
          tableName: raw?.tableName ?? (binding as { physicalTableName?: string }).physicalTableName,
          tableDisplayName: raw?.tableDisplayName ?? binding.tableName
        },
        binding.primaryKeyFields
      )
      if (saved) binding.data = saved
    }
    const rtMap =
      bindingRelationTableMap ?? buildBindingIdToRelationTableIdMap(allContentForms || [])
    hydrateChildSubTablesFromParentsNestedRows(prevBindings as any, savedSubTables as Record<string, unknown>, rtMap)
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(
      prevBindings as any,
      savedSubTables as Record<string, unknown>,
      rtMap
    )
    enrichChildBindingRowsFromParentsNestedSubTables(prevBindings as any)
    applySharedAttachmentFinalizeAndMaterialize(prevBindings, formData.value as Record<string, unknown>, {
      flattened: savedSubTables as Record<string, unknown>,
      bindingTableById: rtMap,
    })
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

const hasAssignmentData = (rows: any[], assigneeField?: string): boolean => {
  if (!Array.isArray(rows) || rows.length === 0) return false
  if (assigneeField) {
    return rows.some(r => r && rowAssigneeUserId(r, assigneeField) != null)
  }
  for (const field of ['assignee_user_id', 'assignee_id']) {
    if (rows.some(r => r && rowAssigneeUserId(r, field) != null)) return true
  }
  return rows.some(r => r && r.assignee_display_name)
}

function resolveBindingAssigneeField(binding: {
  columns?: Array<{ field?: string }>
  tableName?: string
  data?: any[]
}): string | undefined {
  const assigneeField = resolveAssigneeFieldForBinding(binding.columns, binding.tableName)
  if (assigneeField && hasAssignmentData(binding.data || [], assigneeField)) {
    return assigneeField
  }
  return undefined
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
