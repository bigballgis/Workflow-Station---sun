<template>
  <div class="sub-table-field">
    <div class="sub-table-header">
      <span class="title">{{ title }}</span>
      <el-button v-if="editable" type="primary" size="small" @click="handleAdd">
        <el-icon><Plus /></el-icon> {{ t('subTable.add') }}
      </el-button>
    </div>

    <div class="sub-table-scroll-wrapper">
    <el-table :data="rows" size="small" border :max-height="400" v-loading="loading" style="width: 100%" :show-summary="hasSummary" :summary-method="getSummaryMethod">
      <el-table-column
        v-for="col in columns"
        :key="col.field"
        :prop="col.field"
        :label="col.label"
        :min-width="columnMinWidth(col)"
        :show-overflow-tooltip="false"
      >
        <template #default="scope">
          <!-- Read-only display -->
          <template v-if="col.type === 'upload'">
            <span
              v-if="scope.row[col.field]"
              class="file-download-link"
              :class="{ downloading: downloadingKeys[scope.$index + '_' + col.field] }"
              @click="downloadFile(scope.row[col.field], uploadNames[scope.$index + '_' + col.field], scope.$index, col.field)"
            >
              <el-icon v-if="downloadingKeys[scope.$index + '_' + col.field]" class="is-loading"><Loading /></el-icon>
              <el-icon v-else><Document /></el-icon>
              {{ getFilenameFromUrl(scope.row[col.field], uploadNames[scope.$index + '_' + col.field]) }}
            </span>
            <span v-else class="no-file">-</span>
          </template>
          <template v-else-if="col.type === 'colorPicker'">
            <span v-if="scope.row[col.field]" class="color-swatch" :style="{ backgroundColor: scope.row[col.field] }" :title="scope.row[col.field]" />
            <span v-else>-</span>
          </template>
          <template v-else-if="col.type === 'editor'">
            <span v-if="scope.row[col.field]" v-html="sanitizeHtml(scope.row[col.field])" class="editor-preview" />
            <span v-else>-</span>
          </template>
          <template v-else-if="col.type === 'signature'">
            <img v-if="scope.row[col.field]" :src="scope.row[col.field]" class="signature-preview" alt="Signature" />
            <span v-else>-</span>
          </template>
          <template v-else-if="col.type === 'slider'">
            <el-slider
              v-if="scope.row[col.field] != null"
              :model-value="Number(scope.row[col.field])"
              :min="col.props?.min ?? 0"
              :max="col.props?.max ?? 100"
              disabled
              style="width: 100%; padding: 0 10px;"
            />
            <span v-else>-</span>
          </template>
          <template v-else-if="col.type === 'password'">
            <span>••••••</span>
          </template>
          <template v-else-if="col.type === 'rate'">
            <el-rate
              v-if="scope.row[col.field] != null"
              :model-value="Number(scope.row[col.field])"
              :max="col.props?.max || 5"
              disabled
              style="display: inline-flex;"
            />
            <span v-else>-</span>
          </template>
          <template v-else-if="col.type === 'lookup'">
            <div class="lookup-preview-wrapper sub-table-lookup-preview">
              <div class="lookup-form-item">
                <label class="lookup-label-text">
                  <el-icon class="lookup-label-icon"><Search /></el-icon>
                </label>
                <div class="lookup-field readonly">
                  <div v-if="lookupSelectedRow(col, scope.row[col.field])" class="lookup-selected-wrapper">
                    <span class="lookup-selected-tag">
                      <span class="lookup-selected-text">{{ resolveDisplayValue(col, scope.row[col.field]) }}</span>
                    </span>
                  </div>
                  <span v-else class="lookup-readonly-empty">-</span>
                </div>
              </div>
              <div
                v-if="col.props?.showBackfillView !== false && lookupSelectedRow(col, scope.row[col.field]) && lookupDisplayViewFields(col).length > 0"
                class="lookup-view-display"
              >
                <el-descriptions :column="1" border size="small" direction="horizontal">
                  <el-descriptions-item
                    v-for="field in lookupDisplayViewFields(col)"
                    :key="field.fieldName"
                    :label="field.displayLabel || field.fieldName"
                    label-class-name="lookup-view-label"
                    class-name="lookup-view-value"
                  >
                    {{ lookupSelectedRow(col, scope.row[col.field])?.[field.fieldName] ?? '-' }}
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </div>
          </template>
          <template v-else-if="col.type === 'linkForm'">
            <el-link type="primary" :underline="false" @click="handleLinkFormClick(col, scope.row, scope.$index)">
              {{ col.props?.linkText || t('linkForm.defaultLinkText') }}
            </el-link>
          </template>
          <span v-else>{{ resolveDisplayValue(col, scope.row[col.field]) }}</span>
        </template>
      </el-table-column>

      <!-- Task status column (multi-instance subtask completion) -->
      <el-table-column v-if="showTaskStatus" :label="t('subTable.taskStatus')" width="120" align="center">
        <template #default="scope">
          <el-tag
            :type="scope.row.task_status === 'COMPLETED' ? 'success' : 'warning'"
            size="small"
          >
            {{ scope.row.task_status === 'COMPLETED' ? t('subTable.taskCompleted') : t('subTable.taskPending') }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column v-if="editable" :label="t('common.operation')" width="120">
        <template #default="scope">
          <el-button link type="primary" size="small" @click="openEditDialog(scope.$index)">{{ t('subTable.edit') }}</el-button>
          <el-button link type="danger" size="small" @click="deleteRow(scope.$index)">{{ t('subTable.delete') }}</el-button>
        </template>
      </el-table-column>

      <!-- View subtask detail button (read-only mode) -->
      <el-table-column v-if="showViewDetail" :label="t('subTable.actions')" width="100" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            size="small"
            :disabled="scope.row.task_status !== 'COMPLETED'"
            @click="emit('viewDetail', scope.row, scope.$index)"
          >
            {{ t('subTable.viewDetail') }}
          </el-button>
        </template>
      </el-table-column>

      <!-- Fill form button for multi-instance subtask (todo mode) -->
      <el-table-column v-if="showFillButton" :label="t('subTable.actions')" :min-width="fillButtonLabel ? 200 : 100" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            size="small"
            @click="emit('fillForm', scope.row, scope.$index)"
          >
            {{ fillButtonLabel || t('subTable.add') }}
          </el-button>
        </template>
      </el-table-column>

      <!-- Multi-instance assignment column -->
      <el-table-column v-if="showAssigneeColumn" :label="t('subTable.assignee')" width="180">
        <template #default="scope">
          <div class="assignee-cell">
            <span v-if="scope.row.assignee_display_name" class="assignee-name">
              {{ scope.row.assignee_display_name }}
            </span>
            <span v-else-if="assigneeField && scope.row[assigneeField]" class="assignee-name">
              {{ getUserDisplayName(scope.row[assigneeField]) }}
            </span>
            <span v-else class="text-muted">{{ t('subTable.unassigned') }}</span>
            <el-button 
              v-if="canAssign"
              link 
              type="primary" 
              size="small" 
              @click="openAssignDialog(scope.row, scope.$index)"
              class="assign-btn">
              {{ scope.row[assigneeField] ? t('subTable.reassign') : t('subTable.assign') }}
            </el-button>
          </div>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty :description="t('subTable.noData')" :image-size="40" />
      </template>
    </el-table>
    </div>

    <SubTableAddDialog
      :visible="dialogVisible"
      :columns="editableColumns"
      :mode="dialogMode"
      :initialData="dialogInitialData"
      :row-formulas="rowFormulas"
      :column-validation-rules="validationConfig?.columnRules"
      :upload-url="uploadUrl"
      @update:visible="dialogVisible = $event"
      @save="handleDialogSave"
    />

    <Teleport to="body">
      <div v-if="linkFormDialogVisible" class="link-form-modal-overlay">
        <div ref="linkFormModalPanelRef" class="link-form-modal-panel" role="dialog" aria-modal="true">
          <div class="link-form-modal-header">
            <span>{{ activeLinkColumn?.label || selectedLinkBinding?.tableName || t('linkForm.linkedForm') }}</span>
          </div>
          <div class="link-form-dialog-body">
            <el-alert
              v-if="selectedLinkBinding && linkedFormFields.length === 0"
              type="warning"
              :closable="false"
              show-icon
              :title="t('subTable.noData')"
              style="margin-bottom: 12px;"
            />
            <el-form
              v-if="selectedLinkBinding && linkedFormFields.length > 0"
              :model="linkedFormData"
              :label-width="linkedFormLabelWidth"
              label-position="right"
            >
              <el-row :gutter="20">
                <template v-for="field in linkedFormFields" :key="field.key">
                  <el-col v-if="field.type === 'card'" :span="field.span || 24">
                    <el-card shadow="never" class="linked-form-card">
                      <template v-if="field.label" #header>
                        <span>{{ field.label }}</span>
                      </template>
                      <el-row :gutter="20">
                        <el-col v-for="child in field.children || []" :key="child.key" :span="child.span || 24">
                          <el-form-item :label="child.label" :prop="child.key" :required="child.required">
                            <FieldRenderer
                              :field="child"
                              :model-value="linkedFormData[child.key]"
                              :readonly="!canEditSelectedLinkBinding"
                              @update:model-value="(val: any) => updateLinkedFormField(child.key, val)"
                            />
                          </el-form-item>
                        </el-col>
                      </el-row>
                    </el-card>
                  </el-col>
                  <el-col v-else :span="field.span || 24">
                    <el-form-item :label="field.label" :prop="field.key" :required="field.required">
                      <FieldRenderer
                        :field="field"
                        :model-value="linkedFormData[field.key]"
                        :readonly="!canEditSelectedLinkBinding"
                        @update:model-value="(val: any) => updateLinkedFormField(field.key, val)"
                      />
                    </el-form-item>
                  </el-col>
                </template>
              </el-row>
            </el-form>
            <SubTableField
              v-else-if="selectedLinkBinding"
              :title="selectedLinkBinding.tableName"
              :columns="selectedLinkBinding.columns"
              :model-value="linkedSubTableRows"
              :editable="canEditSelectedLinkBinding"
              :linked-sub-table-bindings="linkedSubTableBindings"
              @update:model-value="handleLinkedSubTableUpdate"
            />
            <el-empty v-else :description="t('subTable.noData')" :image-size="60" />
          </div>
          <div class="link-form-modal-footer">
            <el-button @click="linkFormDialogVisible = false">{{ t('common.cancel') }}</el-button>
            <el-button
              v-if="selectedLinkBinding && linkedFormFields.length > 0"
              type="primary"
              :disabled="!canEditSelectedLinkBinding"
              @click="saveLinkedFormData"
            >
              {{ t('common.save') }}
            </el-button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- User picker dialog for assignment -->
    <el-dialog 
      v-model="assignDialogVisible" 
      :title="t('subTable.selectAssignee')" 
      width="500px"
      @opened="onAssignDialogOpened">
      <el-form label-width="100px">
        <el-form-item :label="t('subTable.user')">
          <el-select 
            v-model="selectedAssigneeId" 
            :placeholder="t('subTable.searchUser')" 
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userSearchLoading"
            style="width: 100%;">
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.name} (${user.username})`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="confirmAssignment" :loading="assigning">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Document, Loading, Search } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import SubTableAddDialog from './SubTableAddDialog.vue'
import { resolveDisplayValue } from './subTableAddDialogHelpers'
import type { DialogColumn } from './subTableAddDialogHelpers'
import type { FormField, RowFormulaRule, SubTableValidationConfig } from './formRendererHelpers'
import { calculateSummary } from './businessLogicEngine'
import FieldRenderer from './FieldRenderer.vue'
import type { AssignSubTableRowResponse } from '@/api/task'
import { assignSubTableRow, assignSubTableRowByIdentity, getSubTableData, getTaskDetail } from '@/api/task'
import {
  pickHttpErrorBodyMessage,
  unwrapPortalApiPayload,
  resolveUserFacingHttpMessage
} from '@/utils/httpErrorMessage'
import { userApi } from '@/api/user'
import { onMounted, onBeforeUnmount } from 'vue'
import { useSubTableWebSocket, type SubTableUpdateMessage } from '@/composables/useSubTableWebSocket'

const { t } = useI18n()

/** Sanitize HTML content to prevent XSS */
function sanitizeHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'u', 's', 'ol', 'ul', 'li',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'a', 'img', 'table', 'tr', 'td', 'th', 'span', 'div'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'class', 'style', 'target', 'rel'],
  })
}

type Column = DialogColumn & {
  type?: DialogColumn['type'] | 'linkForm'
  props?: DialogColumn['props'] & {
    linkText?: string
    componentId?: number
    boundSubTableBindingId?: number
    boundSubTableName?: string
  }
}

interface SubTableBinding {
  bindingId: number
  bindingType: string
  bindingMode: string
  tableName: string
  tableType: string
  tableDescription: string
  columns: Column[]
  data: any[]
  formFields?: FormField[]
  formOptions?: Record<string, any>
}

/** Return a reasonable minimum column width based on field type */
function columnMinWidth(col: Column): number {
  if (col.minWidth) return col.minWidth
  switch (col.type) {
    case 'upload':       return 180
    case 'timerange':    return 200
    case 'datetime':     return 180
    case 'date':         return 130
    case 'tree':         return 180
    case 'checkbox':     return 160
    case 'treeselect':   return 160
    case 'colorPicker':  return 100
    case 'rate':         return 140
    case 'editor':       return 200
    case 'signature':    return 150
    case 'transfer':     return 180
    case 'cascader':     return 180
    case 'lookup':       return 260
    case 'slider':       return 160
    case 'password':     return 120
    default:             return 120
  }
}

function getLookupPrimaryDisplayField(col: Column): string {
  const displayFields = col.props?.displayFields
  if (Array.isArray(displayFields) && displayFields.length > 0) return String(displayFields[0])
  if (typeof col.props?.displayField === 'string' && col.props.displayField) return col.props.displayField
  const searchFields = col.props?.searchFields
  if (Array.isArray(searchFields) && searchFields.length > 0) return String(searchFields[0])
  return ''
}

function lookupSelectedRow(col: Column, rawValue: unknown): Record<string, any> | null {
  if (rawValue == null || rawValue === '') return null
  if (typeof rawValue === 'object' && !Array.isArray(rawValue)) return rawValue as Record<string, any>
  const displayField = getLookupPrimaryDisplayField(col)
  return displayField ? { [displayField]: rawValue } : { value: rawValue }
}

function lookupDisplayViewFields(col: Column): Array<{ fieldName: string; displayLabel?: string; sortOrder?: number; visible?: boolean }> {
  const fields = col.props?.viewFields
  if (!Array.isArray(fields)) return []
  return [...fields]
    .filter((field: any) => field?.visible !== false)
    .sort((a: any, b: any) => (a?.sortOrder ?? 0) - (b?.sortOrder ?? 0))
}

const props = defineProps<{
  title: string
  columns: Column[]
  modelValue?: any[]
  editable?: boolean
  loading?: boolean
  rowFormulas?: RowFormulaRule[]
  summaryColumns?: string[]
  summaryAggregations?: Record<string, 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'>
  validationConfig?: SubTableValidationConfig
  uploadUrl?: string
  // Multi-instance assignment props
  taskId?: string
  assigneeField?: string
  canAssign?: boolean
  showAssignButton?: boolean
  // Real-time sync props
  enablePolling?: boolean
  pollingInterval?: number
  enableWebSocket?: boolean
  // View detail props (application detail read-only mode)
  showViewDetail?: boolean
  showTaskStatus?: boolean
  // Fill form button (todo detail for MI subtask)
  showFillButton?: boolean
  fillButtonLabel?: string
  linkedSubTableBindings?: SubTableBinding[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: any[]): void
  (e: 'assignmentChanged'): void
  (e: 'dataRefreshed', rows: any[]): void
  (e: 'viewDetail', row: any, index: number): void
  (e: 'fillForm', row: any, index: number): void
  (e: 'update:linkedSubTableData', bindingId: number, rows: any[]): void
}>()

const rows = ref<any[]>([])
// key = "{rowIndex}_{field}" -> original filename (recorded during current session upload)
const uploadNames = ref<Record<string, string>>({})
// Set of keys currently being downloaded
const downloadingKeys = ref<Record<string, boolean>>({})

const agentDebugLog = (runId: string, hypothesisId: string, location: string, message: string, data: Record<string, any>) => {
  const payload = JSON.stringify({ sessionId: 'b88427', runId, hypothesisId, location, message, data, timestamp: Date.now() })
  fetch('http://127.0.0.1:7683/ingest/1fc88847-d32b-4694-9f56-a337ecc92dd3', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': 'b88427' }, body: payload }).catch(() => {
    try { navigator.sendBeacon('http://127.0.0.1:7683/ingest/1fc88847-d32b-4694-9f56-a337ecc92dd3', new Blob([payload], { type: 'application/json' })) } catch {}
  })
}

function assigneeLikeFields(): string[] {
  return props.columns
    .map(col => col.field)
    .filter(field => /assignee|处理人|負責人|经办人|經辦人/i.test(String(field)))
}

// Dialog state
const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const editingRowIndex = ref<number | null>(null)
const dialogInitialData = ref<Record<string, any> | undefined>(undefined)
const linkFormDialogVisible = ref(false)
const linkFormModalPanelRef = ref<HTMLElement | null>(null)
const activeLinkColumn = ref<Column | null>(null)
const activeLinkRowIndex = ref<number | null>(null)
const linkedSubTableRows = ref<any[]>([])
const linkedFormData = ref<Record<string, any>>({})

const selectedLinkBinding = computed(() => {
  const col = activeLinkColumn.value
  if (!col) return null
  const boundId = col.props?.boundSubTableBindingId
  const boundName = col.props?.boundSubTableName
  return props.linkedSubTableBindings?.find(binding =>
    (boundId != null && Number(binding.bindingId) === Number(boundId)) ||
    (!!boundName && binding.tableName === boundName)
  ) || null
})

const linkedFormFields = computed(() => selectedLinkBinding.value?.formFields || [])
const linkedFormLabelWidth = computed(() => {
  const width = selectedLinkBinding.value?.formOptions?.form?.labelWidth
  return typeof width === 'string' && width.trim() ? width : '125px'
})
const canEditSelectedLinkBinding = computed(() => !!(props.editable && selectedLinkBinding.value?.bindingMode === 'EDITABLE'))

watch(linkFormDialogVisible, async (visible) => {
  if (!visible) return
  await nextTick()
  requestAnimationFrame(() => {
    const panel = linkFormModalPanelRef.value
    if (!panel) return
    const overlay = panel.closest('.link-form-modal-overlay')
    const pr = panel.getBoundingClientRect()
    const vh = window.innerHeight
    const centerY = pr.top + pr.height / 2
    // #region agent log
    agentDebugLog('post-fix', 'H-vert,H-footer', 'SubTableField.vue:linkFormModalLayout', 'link form modal layout metrics', {
      panelTop: Math.round(pr.top),
      panelHeight: Math.round(pr.height),
      viewportH: vh,
      distanceFromViewportVerticalCenter: Math.round(Math.abs(centerY - vh / 2)),
      overlayAlignItems: overlay instanceof HTMLElement ? getComputedStyle(overlay).alignItems : 'n/a',
      canEditLinkBinding: canEditSelectedLinkBinding.value,
      linkedFormFieldCount: linkedFormFields.value.length
    })
    // #endregion
  })
})

// Assignee column: show when assign buttons are active, OR when data already has assignee values (read-only completed tasks)
const showAssigneeColumn = computed(() => {
  if (props.showAssignButton && props.assigneeField) return true
  if (!props.assigneeField) return false
  return rows.value.some(r =>
    r && (r.assignee_display_name || r[props.assigneeField!])
  )
})

const editableColumns = computed(() => props.columns.filter(col => col.type !== 'linkForm'))

// Summary row support
const hasSummary = computed(() => (props.summaryColumns?.length ?? 0) > 0)

function getSummaryMethod({ columns: tableCols }: { columns: any[] }) {
  const sums: string[] = []
  tableCols.forEach((col: any, index: number) => {
    if (index === 0) {
      sums[index] = t('subTable.summary')
      return
    }
    const prop = col.property
    if (!prop || !props.summaryColumns?.includes(prop) || !props.summaryAggregations?.[prop]) {
      sums[index] = ''
      return
    }
    const agg = props.summaryAggregations[prop]
    const val = calculateSummary(rows.value, prop, agg)
    sums[index] = `${val}`
  })
  return sums
}

watch(() => props.modelValue, (v) => { rows.value = v ? [...v] : [] }, { immediate: true, deep: true })

watch(
  () => [props.title, props.columns, props.modelValue] as const,
  () => {
    const linkColumns = props.columns.filter(col => col.type === 'linkForm')
    if (!linkColumns.length) return
    // #region agent log
    agentDebugLog('link-form-popup-pre-fix', 'R1,R3', 'SubTableField.vue:421', 'linkForm columns and row payload available', {
      title: props.title,
      rowCount: rows.value.length,
      linkColumns: linkColumns.map(col => ({
        field: col.field,
        label: col.label,
        props: {
          linkText: col.props?.linkText,
          componentId: col.props?.componentId,
          boundSubTableBindingId: col.props?.boundSubTableBindingId,
          boundSubTableName: col.props?.boundSubTableName
        }
      })),
      firstRowKeys: rows.value[0] && typeof rows.value[0] === 'object' ? Object.keys(rows.value[0]) : [],
      firstRowSubTableKeys: rows.value[0]?.__subTables__ && typeof rows.value[0].__subTables__ === 'object'
        ? Object.keys(rows.value[0].__subTables__)
        : []
    })
    // #endregion
  },
  { immediate: true, deep: true }
)

function handleLinkFormClick(col: Column, row: Record<string, any>, rowIndex: number) {
  // #region agent log
  agentDebugLog('link-form-popup-pre-fix', 'R1,R2,R3', 'SubTableField.vue:450', 'linkForm clicked', {
    title: props.title,
    rowIndex,
    column: {
      field: col.field,
      label: col.label,
      props: {
        linkText: col.props?.linkText,
        componentId: col.props?.componentId,
        boundSubTableBindingId: col.props?.boundSubTableBindingId,
        boundSubTableName: col.props?.boundSubTableName
      }
    },
    rowKeys: row && typeof row === 'object' ? Object.keys(row) : [],
    rowSubTableKeys: row?.__subTables__ && typeof row.__subTables__ === 'object' ? Object.keys(row.__subTables__) : []
  })
  // #endregion
  activeLinkColumn.value = col
  activeLinkRowIndex.value = rowIndex
  const binding = props.linkedSubTableBindings?.find(item =>
    (col.props?.boundSubTableBindingId != null && Number(item.bindingId) === Number(col.props.boundSubTableBindingId)) ||
    (!!col.props?.boundSubTableName && item.tableName === col.props.boundSubTableName)
  )
  const boundId = col.props?.boundSubTableBindingId
  const boundName = col.props?.boundSubTableName || binding?.tableName
  const rowSub = row?.__subTables__ && typeof row.__subTables__ === 'object' ? (row.__subTables__ as Record<string, any>) : {}
  const saved = (boundId != null ? (rowSub[boundId] ?? rowSub[String(boundId)]) : undefined) ?? (boundName ? (rowSub[boundName] ?? rowSub[String(boundName)]) : undefined)
  const savedRows = Array.isArray(saved) ? saved : []
  linkedSubTableRows.value = [...savedRows]
  linkedFormData.value = buildLinkedFormData({ ...(binding || ({} as any)), data: savedRows })
  linkFormDialogVisible.value = true
}

function buildLinkedFormData(binding?: SubTableBinding): Record<string, any> {
  const source = binding?.data?.[0] && typeof binding.data[0] === 'object' ? binding.data[0] : {}
  const next: Record<string, any> = { ...source }
  binding?.formFields?.forEach(field => {
    if (field.type === 'card') {
      field.children?.forEach(child => {
        if (next[child.key] === undefined) next[child.key] = child.defaultValue ?? null
      })
    } else if (next[field.key] === undefined) {
      next[field.key] = field.defaultValue ?? null
    }
  })
  return next
}

function updateLinkedFormField(key: string, value: any) {
  linkedFormData.value = { ...linkedFormData.value, [key]: value }
}

function saveLinkedFormData() {
  const linkRowIndex = activeLinkRowIndex.value
  const col = activeLinkColumn.value
  if (linkRowIndex == null || !col) {
    linkFormDialogVisible.value = false
    return
  }

  const boundId = col.props?.boundSubTableBindingId
  const binding = selectedLinkBinding.value
  const boundName = col.props?.boundSubTableName || binding?.tableName

  const currentRows = linkedSubTableRows.value.length > 0 ? [...linkedSubTableRows.value] : [{}]
  currentRows[0] = { ...currentRows[0], ...linkedFormData.value }
  linkedSubTableRows.value = [...currentRows]

  const nextMainRows = rows.value.map((r, idx) => {
    if (idx !== linkRowIndex) return r
    const base = (r && typeof r === 'object') ? { ...r } : {}
    const sub = { ...((base.__subTables__ && typeof base.__subTables__ === 'object') ? base.__subTables__ : {}) } as Record<string, any>
    if (boundId != null) {
      sub[boundId] = currentRows
      sub[String(boundId)] = currentRows
    }
    if (boundName) {
      sub[boundName] = currentRows
      sub[String(boundName)] = currentRows
    }
    base.__subTables__ = sub
    return base
  })

  // #region agent log
  agentDebugLog('post-fix', 'H-persist,H-isolation', 'SubTableField.vue:saveLinkedFormData', 'link form saved into main row __subTables__ and emitted update:modelValue', {
    title: props.title,
    linkRowIndex,
    boundId,
    boundName,
    savedRowSubTableKeys: nextMainRows[linkRowIndex]?.__subTables__ ? Object.keys(nextMainRows[linkRowIndex].__subTables__) : [],
    savedLinkedRowCount: Array.isArray(currentRows) ? currentRows.length : -1
  })
  // #endregion

  emit('update:modelValue', nextMainRows)
  linkFormDialogVisible.value = false
}

function handleLinkedSubTableUpdate(rows: any[]) {
  linkedSubTableRows.value = [...rows]
  const bindingId = selectedLinkBinding.value?.bindingId
  if (bindingId != null) {
    emit('update:linkedSubTableData', bindingId, rows)
  }
}



/** Extract filename from URL, preferring the original filename recorded in this session */
function getFilenameFromUrl(url: string, savedName?: string): string {
  if (savedName) return savedName
  if (!url) return 'unknown file'
  const last = url.split('/').pop()
  return last || 'unknown file'
}

/** Click filename to trigger download, using fetch+Blob to avoid new tab navigation */
async function downloadFile(url: string, savedName: string | undefined, rowIndex: number, field: string) {
  if (!url) return
  const key = `${rowIndex}_${field}`
  if (downloadingKeys.value[key]) return

  const filename = getFilenameFromUrl(url, savedName)
  downloadingKeys.value = { ...downloadingKeys.value, [key]: true }
  const msg = ElMessage({ message: t('common.downloading'), type: 'info', duration: 0 })

  try {
    const response = await fetch(url)
    if (!response.ok) {
      msg.close()
      ElMessage.error(response.status === 404 ? t('common.fileNotFound') : t('common.downloadFailed'))
      return
    }
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(blobUrl)
    msg.close()
  } catch {
    msg.close()
    ElMessage.error(t('common.downloadFailed'))
  } finally {
    const next = { ...downloadingKeys.value }
    delete next[key]
    downloadingKeys.value = next
  }
}

function handleAdd() {
  dialogMode.value = 'add'
  dialogInitialData.value = undefined
  editingRowIndex.value = null
  // #region agent log
  agentDebugLog('pre-fix', 'H1,H2', 'SubTableField.vue:444', 'open add record dialog', {
    title: props.title,
    rowCount: rows.value.length,
    columnCount: props.columns.length,
    assigneeFieldPropPresent: !!props.assigneeField,
    assigneeLikeFields: assigneeLikeFields(),
    previousRowHasAssigneeLikeValue: rows.value.some(row =>
      assigneeLikeFields().some(field => row?.[field] != null && row?.[field] !== '')
    )
  })
  // #endregion
  dialogVisible.value = true
}

function openEditDialog(i: number) {
  dialogMode.value = 'edit'
  editingRowIndex.value = i
  dialogInitialData.value = { ...rows.value[i] }
  const fields = assigneeLikeFields()
  // #region agent log
  agentDebugLog('pre-fix', 'H1,H2', 'SubTableField.vue:451', 'open edit record dialog', {
    title: props.title,
    rowIndex: i,
    rowCount: rows.value.length,
    columnCount: props.columns.length,
    assigneeFieldPropPresent: !!props.assigneeField,
    assigneeLikeFields: fields,
    initialDataKeys: Object.keys(dialogInitialData.value || {}),
    initialDataHasAssigneeLikeValue: fields.some(field =>
      dialogInitialData.value?.[field] != null && dialogInitialData.value?.[field] !== ''
    ),
    hasAssignmentDisplayName: !!dialogInitialData.value?.assignee_display_name
  })
  // #endregion
  dialogVisible.value = true
}

function handleDialogSave(rowData: Record<string, any>) {
  if (dialogMode.value === 'add') {
    rows.value.push(rowData)
  } else if (dialogMode.value === 'edit' && editingRowIndex.value !== null) {
    rows.value[editingRowIndex.value] = rowData
  }
  emit('update:modelValue', [...rows.value])
}

async function deleteRow(i: number) {
  await ElMessageBox.confirm(t('subTable.deleteConfirm'), t('common.confirm'), { type: 'warning' })
  rows.value.splice(i, 1)
  emit('update:modelValue', [...rows.value])
}

// Assignment functionality
const assignDialogVisible = ref(false)
const selectedAssigneeId = ref('')
const currentAssignRow = ref<any>(null)
const currentAssignRowIndex = ref<number | null>(null)
const assigning = ref(false)
const userOptions = ref<any[]>([])
const userSearchLoading = ref(false)
const userNameCache = ref<Record<string, string>>({})

function openAssignDialog(row: any, rowIndex: number) {
  currentAssignRow.value = row
  currentAssignRowIndex.value = rowIndex
  selectedAssigneeId.value = row[props.assigneeField || ''] || ''
  assignDialogVisible.value = true
}

function onAssignDialogOpened() {
  searchUsers('')
}

async function searchUsers(keyword: string) {
  userSearchLoading.value = true
  try {
    const result = await userApi.searchUsers(keyword || '')
    userOptions.value = [...result]
    // Cache user names
    result.forEach((user: any) => {
      userNameCache.value[user.id] = user.name
    })
  } catch (e) {
    console.error('Failed to search users:', e)
    userOptions.value = []
  } finally {
    userSearchLoading.value = false
  }
}

function getUserDisplayName(userId: string): string {
  if (userNameCache.value[userId]) return userNameCache.value[userId]
  return userId.startsWith('user-') ? userId.substring(5) : userId
}

/**
 * Sub-table row primary key: the engine assignment API requires the relation table's
 * numeric PK (e.g. participants.id). Also handles participant_id / case variants /
 * field names after form serialization.
 */
function resolveSubTableRowPk(row: Record<string, unknown> | null | undefined): string | number | null {
  if (!row) return null
  const r = row as Record<string, unknown>
  const candidates: unknown[] = [
    r.id,
    r.rowId,
    r.participant_id,
    r.participantId,
    (r as { ID?: unknown }).ID,
    (r as { RowId?: unknown }).RowId
  ]
  for (const v of candidates) {
    if (v != null && v !== '') return v as string | number
  }
  return null
}

function sameValue(a: unknown, b: unknown): boolean {
  const sa = a == null ? '' : String(a).trim().toLowerCase()
  const sb = b == null ? '' : String(b).trim().toLowerCase()
  return sa !== '' && sb !== '' && sa === sb
}

async function resolveMissingRowIdFromServer(
  taskId: string,
  localRow: Record<string, unknown>,
  rowIndex: number | null
): Promise<number | null> {
  try {
    const response = await getSubTableData(taskId)
    const payload = (response as Record<string, unknown>).data as Record<string, unknown> | undefined
    const rowsFromServer = Array.isArray(payload?.rows) ? (payload!.rows as Record<string, unknown>[]) : []
    if (!rowsFromServer.length) return null

    const byEmail = rowsFromServer.find(r => sameValue(r.email, localRow.email))
    const byNameAndDept = rowsFromServer.find(
      r => sameValue(r.name, localRow.name) && sameValue(r.department, localRow.department)
    )
    const byIndex =
      rowIndex != null && rowIndex >= 0 && rowIndex < rowsFromServer.length
        ? rowsFromServer[rowIndex]
        : null
    const match = byEmail || byNameAndDept || byIndex || null
    if (!match) return null

    const pk = resolveSubTableRowPk(match)
    const rowId = pk != null ? Number(pk) : NaN
    return Number.isNaN(rowId) ? null : rowId
  } catch (error: unknown) {
    return null
  }
}

async function resolveMissingRowIdFromTaskDetail(
  taskId: string,
  localRow: Record<string, unknown>,
  rowIndex: number | null
): Promise<{
  rowId: number | null
  effectiveTaskId?: string
  meetingHints?: { topic?: string; location?: string; organizerName?: string }
}> {
  try {
    const detailRes = await getTaskDetail(taskId)
    const detail = (detailRes as Record<string, unknown>).data as Record<string, unknown> | undefined
    const effectiveTaskId =
      detail && typeof detail.taskId === 'string' && detail.taskId.trim().length > 0 ? detail.taskId : taskId
    const vars = (detail?.variables as Record<string, unknown> | undefined) || {}
    const subTables = (vars.__subTables__ as Record<string, unknown> | undefined) || {}
    const allRows: Record<string, unknown>[] = []
    Object.values(subTables).forEach(v => {
      if (Array.isArray(v)) {
        v.forEach(r => {
          if (r && typeof r === 'object') allRows.push(r as Record<string, unknown>)
        })
      }
    })
    const meetingHints = {
      topic: typeof vars.topic === 'string' ? vars.topic : undefined,
      location: typeof vars.location === 'string' ? vars.location : undefined,
      organizerName: typeof vars.organizer_name === 'string' ? vars.organizer_name : undefined
    }
    if (!allRows.length) return { rowId: null, effectiveTaskId, meetingHints }
    const byEmail = allRows.find(r => sameValue(r.email, localRow.email))
    const byNameAndDept = allRows.find(
      r => sameValue(r.name, localRow.name) && sameValue(r.department, localRow.department)
    )
    const byIndex = rowIndex != null && rowIndex >= 0 && rowIndex < allRows.length ? allRows[rowIndex] : null
    const match = byEmail || byNameAndDept || byIndex || null
    if (!match) return { rowId: null, effectiveTaskId, meetingHints }
    const pk = resolveSubTableRowPk(match)
    const rowId = pk != null ? Number(pk) : NaN
    return { rowId: Number.isNaN(rowId) ? null : rowId, effectiveTaskId, meetingHints }
  } catch {
    return { rowId: null, effectiveTaskId: taskId }
  }
}

async function confirmAssignment() {
  if (!selectedAssigneeId.value) {
    ElMessage.warning(t('subTable.pleaseSelectUser'))
    return
  }

  const row = currentAssignRow.value as Record<string, unknown> | null | undefined
  const rowPk = resolveSubTableRowPk(row)
  let effectiveTaskId = props.taskId
  let meetingHints: { topic?: string; location?: string; organizerName?: string } | undefined
  let rowIdNum = rowPk != null ? Number(rowPk) : NaN
  if (props.taskId && (rowPk == null || Number.isNaN(rowIdNum)) && row) {
    let recovered = await resolveMissingRowIdFromServer(props.taskId, row, currentAssignRowIndex.value)
    if (recovered == null) {
      const fromDetail = await resolveMissingRowIdFromTaskDetail(props.taskId, row, currentAssignRowIndex.value)
      recovered = fromDetail.rowId
      meetingHints = fromDetail.meetingHints
      if (fromDetail.effectiveTaskId && fromDetail.effectiveTaskId.trim()) {
        effectiveTaskId = fromDetail.effectiveTaskId
      }
    }
    if (recovered != null) {
      rowIdNum = recovered
    }
  }
  if (!props.taskId) {
    ElMessage.error(t('subTable.assignmentFailed'))
    return
  }

  assigning.value = true
  try {
    let response: unknown
    if (!Number.isNaN(rowIdNum)) {
      response = await assignSubTableRow(
        props.taskId,
        rowIdNum,
        selectedAssigneeId.value
      )
    } else {
      const identityRow = row || {}
      response = await assignSubTableRowByIdentity(props.taskId, {
        // taskId may differ from route param in some task detail payloads
        assigneeId: selectedAssigneeId.value,
        email: typeof (identityRow as Record<string, unknown>).email === 'string'
          ? String((identityRow as Record<string, unknown>).email)
          : undefined,
        name: typeof (identityRow as Record<string, unknown>).name === 'string'
          ? String((identityRow as Record<string, unknown>).name)
          : undefined,
        department: typeof (identityRow as Record<string, unknown>).department === 'string'
          ? String((identityRow as Record<string, unknown>).department)
          : undefined,
        topic: meetingHints?.topic,
        location: meetingHints?.location,
        organizerName: meetingHints?.organizerName
      })
      // retry with effective task id from detail if route task id is stale
      if (effectiveTaskId !== props.taskId) {
        response = await assignSubTableRowByIdentity(effectiveTaskId, {
          assigneeId: selectedAssigneeId.value,
          email: typeof (identityRow as Record<string, unknown>).email === 'string'
            ? String((identityRow as Record<string, unknown>).email)
            : undefined,
          name: typeof (identityRow as Record<string, unknown>).name === 'string'
            ? String((identityRow as Record<string, unknown>).name)
            : undefined,
          department: typeof (identityRow as Record<string, unknown>).department === 'string'
            ? String((identityRow as Record<string, unknown>).department)
            : undefined,
          topic: meetingHints?.topic,
          location: meetingHints?.location,
          organizerName: meetingHints?.organizerName
        })
      }
    }

    const result = unwrapPortalApiPayload<AssignSubTableRowResponse>(response)
    const assigneePresent =
      result != null &&
      result.assigneeId != null &&
      String(result.assigneeId).trim().length > 0
    // When success is absent but assigneeId is returned, treat as success (serialization compat); success===false triggers error message
    const ok =
      result != null &&
      result.success !== false &&
      (result.success === true || assigneePresent)

    if (ok && result) {
      // Update the row data
      if (currentAssignRowIndex.value !== null && props.assigneeField) {
        const targetRow = rows.value[currentAssignRowIndex.value]
        targetRow[props.assigneeField] = result.assigneeId
        const displayName = result.assigneeName || result.assigneeId
        targetRow.assignee_display_name = displayName
        userNameCache.value[result.assigneeId] = displayName
        emit('update:modelValue', [...rows.value])
        emit('assignmentChanged')
      }

      ElMessage.success(t('subTable.assignmentSuccess'))
      assignDialogVisible.value = false
    } else {
      const r = result as Record<string, unknown> | null
      const hint =
        (r && typeof r.errorMessage === 'string' && r.errorMessage.trim()) ||
        (r && typeof r.message === 'string' && r.message.trim()) ||
        t('subTable.assignmentFailed')
      ElMessage.error(hint)
    }
  } catch (error: unknown) {
    console.error('Failed to assign sub-table row:', error)
    const ax = error as { response?: { status?: number; data?: unknown }; message?: string }
    try {
      const probe = await getTaskDetail(effectiveTaskId || props.taskId)
      const probeData = (probe as Record<string, unknown>).data as Record<string, unknown> | undefined
      void probeData
    } catch (probeError: unknown) {
      void probeError
    }
    const msg =
      pickHttpErrorBodyMessage(ax.response?.data) ||
      resolveUserFacingHttpMessage(error, t) ||
      (typeof ax.message === 'string' && ax.message.trim().length > 0 ? ax.message.trim() : undefined) ||
      t('subTable.assignmentFailed')
    ElMessage.error(msg)
  } finally {
    assigning.value = false
  }
}

// Real-time polling functionality
let pollingTimer: ReturnType<typeof setInterval> | null = null

// WebSocket functionality
const { connected: wsConnected, subscribe: wsSubscribe, unsubscribe: wsUnsubscribe } = useSubTableWebSocket()

async function refreshSubTableData() {
  if (!props.taskId) return
  
  try {
    const response = await getSubTableData(props.taskId)
    const result = response.data || response
    
    if (result.rows && Array.isArray(result.rows)) {
      // Merge the refreshed data with existing rows
      const updatedRows = rows.value.map(existingRow => {
        const refreshedRow = result.rows.find((r: any) => r.id === existingRow.id)
        if (refreshedRow) {
          // Update assignee and status fields while preserving other data
          return {
            ...existingRow,
            ...refreshedRow
          }
        }
        return existingRow
      })
      
      rows.value = updatedRows
      emit('update:modelValue', [...rows.value])
      emit('dataRefreshed', updatedRows)
    }
  } catch (error) {
    console.error('Failed to refresh sub-table data:', error)
    // Silently fail - don't show error message for background polling
  }
}

function startPolling() {
  if (!props.enablePolling || !props.taskId) return
  
  stopPolling()
  
  const interval = props.pollingInterval || 5000 // Default 5 seconds
  pollingTimer = setInterval(() => {
    refreshSubTableData()
  }, interval)
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// WebSocket subscription management
function startWebSocketSubscription() {
  if (!props.enableWebSocket || !props.taskId) return
  
  stopWebSocketSubscription()
  
  wsSubscribe(props.taskId, (message: SubTableUpdateMessage) => {
    console.log('[SubTableField] Received WebSocket update:', message)
    // Refresh data when receiving update notification
    refreshSubTableData()
  })
}

function stopWebSocketSubscription() {
  wsUnsubscribe()
}

// Lifecycle hooks for polling
onMounted(() => {
  if (props.enablePolling) {
    startPolling()
  }
  if (props.enableWebSocket) {
    startWebSocketSubscription()
  }
})

onBeforeUnmount(() => {
  stopPolling()
  stopWebSocketSubscription()
})

// Watch for enablePolling changes
watch(() => props.enablePolling, (enabled) => {
  if (enabled) {
    startPolling()
  } else {
    stopPolling()
  }
})

// Watch for enableWebSocket changes
watch(() => props.enableWebSocket, (enabled) => {
  if (enabled) {
    startWebSocketSubscription()
  } else {
    stopWebSocketSubscription()
  }
})

// Watch for taskId changes
watch(() => props.taskId, () => {
  if (props.enablePolling) {
    stopPolling()
    startPolling()
  }
  if (props.enableWebSocket) {
    stopWebSocketSubscription()
    startWebSocketSubscription()
  }
})
</script>

<style scoped lang="scss">
.sub-table-field {
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  padding: 12px;
  background: #fafafa;

  .sub-table-scroll-wrapper {
    width: 100%;
    overflow-x: auto;
  }

  :deep(.el-table .cell) {
    white-space: nowrap;
  }

  .sub-table-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;

    .title {
      font-weight: 500;
      font-size: 14px;
      color: #303133;
    }
  }

  .file-download-link {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    color: #165DFF;
    text-decoration: underline;
    font-size: 12px;
    cursor: pointer;
    transition: color 0.2s;

    &:hover { color: #0e44cc; }
    &.downloading { color: #909399; cursor: wait; }
  }

  .no-file {
    color: #909399;
    font-size: 12px;
  }

  .color-swatch {
    display: inline-block;
    width: 20px;
    height: 20px;
    border-radius: 3px;
    border: 1px solid #dcdfe6;
    vertical-align: middle;
  }

  .editor-preview {
    display: inline-block;
    max-width: 200px;
    max-height: 60px;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 12px;
    line-height: 1.4;
  }

  .signature-preview {
    max-width: 120px;
    max-height: 40px;
    object-fit: contain;
    vertical-align: middle;
  }

  .lookup-preview-wrapper {
    margin-bottom: 0;
  }

  .lookup-form-item {
    display: flex;
    align-items: flex-start;
  }

  .lookup-label-text {
    white-space: nowrap;
    width: auto;
    min-width: fit-content;
    max-width: 200px;
    height: auto;
    line-height: 1.5;
    padding-top: 6px;
    padding-right: 12px;
    font-size: 14px;
    color: #606266;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .lookup-label-icon {
    color: #409eff;
    font-size: 14px;
  }

  .lookup-field {
    flex: 1;
    min-width: 0;
    position: relative;

    &.readonly {
      cursor: default;
    }

    .lookup-selected-wrapper {
      display: flex;
      align-items: center;
      min-height: 32px;
      padding: 4px 8px;
      border: 1px solid #dcdfe6;
      border-radius: 4px;
      background: #fff;
    }

    .lookup-selected-tag {
      display: inline-flex;
      align-items: center;
      max-width: 100%;
      height: 24px;
      padding: 0 8px;
      border-radius: 4px;
      background: #f0f2f5;
      font-size: 13px;
      color: #909399;
      line-height: 24px;

      .lookup-selected-text {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }

  .lookup-readonly-empty {
    color: #909399;
    line-height: 32px;
  }

  .lookup-view-display {
    margin-top: 8px;

    :deep(.lookup-view-label) {
      width: 40%;
      font-weight: 500;
      color: #606266;
      background: #fafafa;
    }

    :deep(.lookup-view-value) {
      color: #303133;
    }
  }

  .assignee-cell {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;

    .assignee-name {
      font-size: 13px;
      color: #303133;
    }

    .text-muted {
      font-size: 13px;
      color: #909399;
    }

    .assign-btn {
      flex-shrink: 0;
    }
  }
}

.link-form-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 5000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: rgba(0, 0, 0, 0.45);
}

.link-form-modal-panel {
  width: min(700px, calc(100vw - 48px));
  max-height: 84vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 4px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
}

.link-form-modal-header,
.link-form-modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}

.link-form-modal-header {
  font-weight: 600;
  color: #303133;
}

.link-form-modal-footer {
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid #ebeef5;
  border-bottom: 0;
}

.link-form-dialog-body {
  min-height: 160px;
  padding: 16px;
  overflow: auto;
}

.linked-form-card {
  width: 100%;
  margin-bottom: 12px;
}
</style>
