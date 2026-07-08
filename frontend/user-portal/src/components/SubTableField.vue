<template>
  <div class="sub-table-field">
    <div class="sub-table-header">
      <span class="title">{{ title }}</span>
      <div class="actions">
        <el-button
          v-if="editable"
          size="small"
          @click="handleExport"
        >
          <el-icon><Download /></el-icon> {{ t('subTable.exportWithData') }}
        </el-button>
        <el-button
          v-if="editable && !hasFileColumn"
          size="small"
          @click="triggerImport"
        >
          <el-icon><Upload /></el-icon> {{ t('subTable.import') }}
        </el-button>
        <el-button
          v-if="editable"
          type="primary"
          size="small"
          @click="handleAdd"
        >
          <el-icon><Plus /></el-icon> {{ t('subTable.add') }}
        </el-button>
      </div>
      <input
        ref="importInputRef"
        type="file"
        accept=".csv,.xlsx,.xls"
        style="display:none"
        @change="handleImportFile"
      >
    </div>

    <div class="sub-table-scroll-wrapper">
      <el-table
        v-loading="loading"
        :data="rows"
        size="small"
        border
        :max-height="400"
        style="width: 100%"
        :show-summary="hasSummary"
        :summary-method="getSummaryMethod"
      >
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
            <template v-if="col.field === 'task_status'">
              <el-tag
                :type="scope.row.task_status === 'COMPLETED' ? 'success' : 'warning'"
                size="small"
              >
                {{ formatTaskStatus(scope.row.task_status) }}
              </el-tag>
            </template>
            <template v-else-if="isUploadColumn(col, scope.row[col.field])">
              <span
                v-if="scope.row[col.field]"
                class="file-download-link"
                :class="{ downloading: downloadingKeys[scope.$index + '_' + col.field] }"
                @click="downloadFile(scope.row[col.field], uploadNames[scope.$index + '_' + col.field], scope.$index, col.field)"
              >
                <el-icon
                  v-if="downloadingKeys[scope.$index + '_' + col.field]"
                  class="is-loading"
                ><Loading /></el-icon>
                <el-icon v-else><Document /></el-icon>
                {{ getFilenameFromUrl(scope.row[col.field], uploadNames[scope.$index + '_' + col.field]) }}
              </span>
              <span
                v-else
                class="no-file"
              >-</span>
            </template>
            <template v-else-if="col.type === 'colorPicker'">
              <span
                v-if="scope.row[col.field]"
                class="color-swatch"
                :style="{ backgroundColor: scope.row[col.field] }"
                :title="scope.row[col.field]"
              />
              <span v-else>-</span>
            </template>
            <template v-else-if="col.type === 'editor'">
              <span
                v-if="scope.row[col.field]"
                class="editor-preview"
                v-html="sanitizeHtml(scope.row[col.field])"
              />
              <span v-else>-</span>
            </template>
            <template v-else-if="col.type === 'signature'">
              <img
                v-if="scope.row[col.field]"
                :src="scope.row[col.field]"
                class="signature-preview"
                alt="Signature"
              >
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
            <template v-else-if="col.type === 'switch'">
              <el-switch
                :model-value="scope.row[col.field] === true || scope.row[col.field] === 'true' || scope.row[col.field] === 1 || scope.row[col.field] === '1'"
                disabled
              />
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
                    <div
                      v-if="effectiveLookupRowForCell(col, scope.row[col.field])"
                      class="lookup-selected-wrapper"
                    >
                      <span class="lookup-selected-tag">
                        <span class="lookup-selected-text">{{ lookupTagDisplayText(col, scope.row[col.field]) }}</span>
                      </span>
                    </div>
                    <el-input
                      v-else
                      model-value=""
                      placeholder="-"
                      class="lookup-input"
                      disabled
                    />
                  </div>
                </div>
                <div
                  v-if="shouldShowLookupBackfill(col) && lookupSelectedRow(col, scope.row[col.field]) && effectiveLookupViewFields(col, scope.row[col.field]).length > 0"
                  class="lookup-view-display"
                >
                  <el-descriptions
                    :column="1"
                    border
                    size="small"
                    direction="horizontal"
                  >
                    <el-descriptions-item
                      v-for="field in effectiveLookupViewFields(col, scope.row[col.field])"
                      :key="field.fieldName"
                      :label="field.displayLabel || field.fieldName"
                      label-class-name="lookup-view-label"
                      class-name="lookup-view-value"
                    >
                      {{ formatUserSnapshotCellValue(effectiveLookupRowForCell(col, scope.row[col.field])?.[field.fieldName]) }}
                    </el-descriptions-item>
                  </el-descriptions>
                </div>
              </div>
            </template>
            <template v-else-if="isUserSnapshotLikeObject(scope.row[col.field])">
              <div class="lookup-preview-wrapper sub-table-lookup-preview">
                <div class="lookup-form-item">
                  <label class="lookup-label-text">
                    <el-icon class="lookup-label-icon"><Search /></el-icon>
                  </label>
                  <div class="lookup-field readonly">
                    <div class="lookup-selected-wrapper">
                      <span class="lookup-selected-tag">
                        <span class="lookup-selected-text">{{ userObjectTagDisplayString(scope.row[col.field]) }}</span>
                      </span>
                    </div>
                  </div>
                </div>
                <div
                  v-if="!compactLookupCells && userSnapshotViewFieldsFromRow(scope.row[col.field]).length > 0"
                  class="lookup-view-display"
                >
                  <el-descriptions
                    :column="1"
                    border
                    size="small"
                    direction="horizontal"
                  >
                    <el-descriptions-item
                      v-for="field in userSnapshotViewFieldsFromRow(scope.row[col.field])"
                      :key="field.key"
                      :label="field.label"
                      label-class-name="lookup-view-label"
                      class-name="lookup-view-value"
                    >
                      {{ formatUserSnapshotCellValue(getSnapshotField(scope.row[col.field], field.key)) }}
                    </el-descriptions-item>
                  </el-descriptions>
                </div>
              </div>
            </template>
            <template v-else-if="col.type === 'linkForm'">
              <el-link
                type="primary"
                :underline="false"
                @click.prevent="handleLinkFormClick(col, scope.row, scope.$index)"
              >
                {{ col.props?.linkText || t('linkForm.defaultLinkText') }}
              </el-link>
            </template>
            <span v-else>{{ resolveDisplayValue(col, scope.row[col.field]) }}</span>
          </template>
        </el-table-column>

        <!-- Task status column (multi-instance subtask completion) -->
        <el-table-column
          v-if="effectiveShowTaskStatus"
          :label="t('subTable.taskStatus')"
          width="120"
          align="center"
        >
          <template #default="scope">
            <el-tag
              :type="scope.row.task_status === 'COMPLETED' ? 'success' : 'warning'"
              size="small"
            >
              {{ formatTaskStatus(scope.row.task_status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column
          v-if="editable"
          :label="t('common.operation')"
          width="120"
        >
          <template #default="scope">
            <el-button
              link
              type="primary"
              size="small"
              @click="openEditDialog(scope.$index)"
            >
              {{ t('subTable.edit') }}
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              @click="deleteRow(scope.$index)"
            >
              {{ t('subTable.delete') }}
            </el-button>
          </template>
        </el-table-column>

        <!-- View subtask detail button (read-only mode) -->
        <el-table-column
          v-if="effectiveShowViewDetail"
          :label="t('subTable.actions')"
          width="100"
          align="center"
        >
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
        <el-table-column
          v-if="showFillButton"
          :label="t('subTable.actions')"
          :min-width="fillButtonLabel ? 200 : 100"
          align="center"
        >
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
        <el-table-column
          v-if="showAssigneeColumn"
          :label="t('subTable.assignee')"
          width="180"
        >
          <template #default="scope">
            <div class="assignee-cell">
              <span
                v-if="scope.row.assignee_display_name"
                class="assignee-name"
              >
                {{ formatAssigneeDisplayLabel(scope.row.assignee_display_name) }}
              </span>
              <span
                v-else-if="resolveRowAssigneeCell(scope.row)"
                class="assignee-name"
              >
                {{ getUserDisplayName(resolveRowAssigneeCell(scope.row)) }}
              </span>
              <span
                v-else
                class="text-muted"
              >{{ t('subTable.unassigned') }}</span>
              <el-button
                v-if="canAssign"
                link
                type="primary"
                size="small"
                class="assign-btn"
                @click="openAssignDialog(scope.row, scope.$index)"
              >
                {{ resolveRowAssigneeCell(scope.row) ? t('subTable.reassign') : t('subTable.assign') }}
              </el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty
            :description="t('subTable.noData')"
            :image-size="40"
          />
        </template>
      </el-table>
    </div>

    <SubTableAddDialog
      :visible="dialogVisible"
      :columns="subTableDialogColumns"
      :audit-columns="listViewColumnsForAudit"
      :mode="dialogMode"
      :initial-data="dialogInitialData"
      :row-formulas="rowFormulas"
      :column-validation-rules="validationConfig?.columnRules"
      :upload-url="uploadUrl"
      @update:visible="dialogVisible = $event"
      :save-row="handleDialogSave"
    />

    <Teleport to="body">
      <div
        v-if="linkFormDialogVisible"
        class="link-form-modal-overlay"
      >
        <div
          ref="linkFormModalPanelRef"
          class="link-form-modal-panel"
          role="dialog"
          aria-modal="true"
        >
          <div class="link-form-modal-header">
            <span class="link-form-modal-title">{{ linkFormModalTitle }}</span>
            <!-- Native button: el-form :disabled (readonly completed task) injects into el-button via component tree; Teleport does not break that inheritance. -->
            <button
              type="button"
              class="link-form-modal-close"
              :aria-label="t('common.close')"
              @click="closeLinkFormDetailDialog"
            >
              <el-icon :size="18">
                <Close />
              </el-icon>
            </button>
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
              label-position="left"
            >
              <el-row :gutter="20">
                <PortalFormFields
                  :fields="linkedFormFields"
                  :model="linkedFormData"
                  :readonly="!canEditSelectedLinkBinding"
                  :editable="canEditSelectedLinkBinding"
                  :sub-table-bindings="linkedSubTableBindings"
                  :linked-sub-table-bindings="linkedSubTableBindings"
                  :parent-row="linkedFormData"
                  :show-link-form-dialog-footer="showLinkFormDialogFooter"
                  @update:field="(k, v) => updateLinkedFormField(k, v)"
                />
              </el-row>
            </el-form>
            <SubTableField
              v-else-if="selectedLinkBinding"
              :title="selectedLinkBinding.tableName"
              :columns="selectedLinkBinding.columns"
              :model-value="linkedSubTableRows"
              :editable="canEditSelectedLinkBinding"
              :linked-sub-table-bindings="linkedSubTableBindings"
              :show-link-form-dialog-footer="showLinkFormDialogFooter"
              :primary-key-fields="selectedLinkBinding.primaryKeyFields"
              @update:model-value="handleLinkedSubTableUpdate"
            />
            <el-empty
              v-else
              :description="t('subTable.noData')"
              :image-size="60"
            />
          </div>
          <div
            v-if="showLinkFormDetailActionFooter"
            class="link-form-modal-footer"
          >
            <button
              type="button"
              class="link-form-modal-footer-btn link-form-modal-footer-btn--secondary"
              @click="closeLinkFormDetailDialog"
            >
              {{ t('common.cancel') }}
            </button>
            <button
              type="button"
              class="link-form-modal-footer-btn link-form-modal-footer-btn--primary"
              @click="saveLinkedFormData"
            >
              {{ t('common.save') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- User picker dialog for assignment -->
    <el-dialog
      v-model="assignDialogVisible"
      :title="t('subTable.selectAssignee')"
      width="500px"
      @opened="onAssignDialogOpened"
    >
      <el-form
        label-width="auto"
        label-position="left"
      >
        <el-form-item :label="t('subTable.user')">
          <el-select
            v-model="selectedAssigneeId"
            :placeholder="t('subTable.searchUser')"
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userSearchLoading"
            style="width: 100%;"
          >
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
        <el-button @click="assignDialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="assigning"
          @click="confirmAssignment"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed, withDefaults, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Document, Loading, Search, Close, Download, Upload } from '@element-plus/icons-vue'
import SubTableAddDialog from './SubTableAddDialog.vue'
import {
  resolveDisplayValue,
  isUserSnapshotLikeObject,
  userObjectTagDisplayString,
  userSnapshotViewFieldsFromRow,
  formatUserSnapshotCellValue,
  isUploadColumn
} from './subTableAddDialogHelpers'
import type { RowFormulaRule, SubTableValidationConfig } from './formRendererHelpers'
import { calculateSummary } from './businessLogicEngine'
import FieldRenderer from './FieldRenderer.vue'
import PortalFormFields from './PortalFormFields.vue'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'
import type { Column, SubTableBinding } from '@/composables/subTableField/subTableFieldTypes'
import { sanitizeHtml } from '@/composables/subTableField/subTableHtmlSanitize'
import {
  columnMinWidth,
  lookupSelectedRow,
  effectiveLookupViewFields,
  getSnapshotField,
  useSubTableLookupCells
} from '@/composables/subTableField/useSubTableLookupCells'
import { useSubTableStatusColumns } from '@/composables/subTableField/useSubTableStatusColumns'
import { useSubTableRowKeys } from '@/composables/subTableField/useSubTableRowKeys'
import { useSubTableLinkFormScope } from '@/composables/subTableField/useSubTableLinkFormScope'
import { useSubTableLinkFormDialog } from '@/composables/subTableField/useSubTableLinkFormDialog'
import { useSubTableLinkFormOpen } from '@/composables/subTableField/useSubTableLinkFormOpen'
import { getFilenameFromUrl, useSubTableFileDownload } from '@/composables/subTableField/useSubTableFileDownload'
import { formatAssigneeDisplayLabel, useSubTableAssignment } from '@/composables/subTableField/useSubTableAssignment'
import { useSubTableAssigneeHydration } from '@/composables/subTableField/useSubTableAssigneeHydration'
import { useSubTablePollingSync } from '@/composables/subTableField/useSubTablePollingSync'
import { useSubTableRowDialog } from '@/composables/subTableField/useSubTableRowDialog'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  title: string
  columns: Column[]
  /** Form-design canvas columns for Add/Edit row dialog (excludes list-view-only audit fields). */
  dialogColumns?: Column[]
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
  suppressLinkFormInitialData?: boolean
  /** Task To Do only: show Cancel/Save on Link Form detail (completed / My Request omit). */
  showLinkFormDialogFooter?: boolean
  /**
   * 办理人待办 + form below table + 表单来源为 Link 子表时：点击链接不打开弹层，由宿主滚动到表格下方内联表单。
   */
  linkFormClickScrollToInline?: boolean
  /**
   * My Request + 「汇总列表 + Link/Details」：表格内 lookup / 用户快照只显示摘要标签，不在单元格内展开 el-descriptions，
   * 避免与「详情走 Link 弹层」的设计冲突（否则看起来像待办的 inline 表单区）。
   */
  compactLookupCells?: boolean
  /**
   * 表设计器在 dw_field_definitions 中标记的主键列名（经 admin-center 随 tableBindings 下发）。
   * 仅单列时参与 assignment / 行定位；多列主键仍回退到既有 id/rowId 等待办路径。
   */
  primaryKeyFields?: string[]
  /** Field FK/PK metadata from tableBindings (PRD S5). */
  fieldDefinitions?: BindingFieldDefinition[]
  tableId?: number | null
  functionUnitId?: string
  primaryFormData?: Record<string, unknown>
  subTableBindingsForContext?: Array<{
    tableId?: number | null
    bindingType?: string
    tableName?: string
    tableDisplayName?: string
  }>
  parentRow?: Record<string, unknown> | null
  parentTableId?: number | null
  primaryTableDisplayName?: string
  primaryTableId?: number | null
  parentTablesById?: Record<number, { fieldDefinitions: BindingFieldDefinition[] }>
  /** PRD S6: structural FK vs MI participant row link. */
  bindingLinkMode?: 'structuralFk' | 'miParticipantRow' | string
  bindingForeignKeyField?: string | null
  /** Flowable MI element id — seeds attachment/link-child row_id on Add (To Do sub form2). */
  miParticipantRowId?: string | number | null
  miParentParticipantRow?: Record<string, unknown> | null
  miParentTableId?: number | null
}>(), {
  showLinkFormDialogFooter: false,
  linkFormClickScrollToInline: false,
  compactLookupCells: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', val: any[]): void
  (e: 'update:primaryFormData', val: Record<string, unknown>): void
  (e: 'assignmentChanged'): void
  (e: 'dataRefreshed', rows: any[]): void
  (e: 'viewDetail', row: any, index: number): void
  (e: 'fillForm', row: any, index: number): void
  (e: 'update:linkedSubTableData', bindingId: number, rows: any[]): void
  (e: 'linkFormScrollToInline'): void
}>()

// 判断列中是否存在 FILE 类型的字段（有 FILE 列时隐藏 Import 按钮）
const hasFileColumn = computed(() => {
  return props.columns.some(col => col.type === 'upload' || isUploadColumn(col))
})

// 隐藏的文件 input，用于触发 CSV 文件选择
const importInputRef = ref<HTMLInputElement | null>(null)

function triggerImport() {
  importInputRef.value?.click()
}

// 解析 CSV 文本为行数组
function parseCSV(text: string): string[][] {
  const rows: string[][] = []
  const lines = text.split(/\r?\n/).filter(line => line.trim())
  for (const line of lines) {
    const cols: string[] = []
    let current = ''
    let inQuotes = false
    for (let i = 0; i < line.length; i++) {
      const ch = line[i]
      if (inQuotes) {
        if (ch === '"') {
          if (i + 1 < line.length && line[i + 1] === '"') {
            current += '"'
            i++
          } else {
            inQuotes = false
          }
        } else {
          current += ch
        }
      } else {
        if (ch === '"') {
          inQuotes = true
        } else if (ch === ',') {
          cols.push(current.trim())
          current = ''
        } else {
          current += ch
        }
      }
    }
    cols.push(current.trim())
    rows.push(cols)
  }
  return rows
}

function handleImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (e) => {
    const text = e.target?.result as string
    if (!text) return

    const csvRows = parseCSV(text)
    if (csvRows.length < 1) return

    // 第一行是 header，映射到 props.columns 的 field
    const headers = csvRows[0]
    const colFieldSet = new Set(props.columns.map(c => c.field))
    const headerToField = new Map<number, string>()
    for (let i = 0; i < headers.length; i++) {
      const h = headers[i]
      if (colFieldSet.has(h)) {
        headerToField.set(i, h)
      }
    }

    // 从第二行开始解析数据
    const newRows: any[] = []
    for (let r = 1; r < csvRows.length; r++) {
      const row: Record<string, unknown> = {}
      for (const [colIdx, field] of headerToField.entries()) {
        const val = csvRows[r][colIdx] || ''
        const col = props.columns.find(c => c.field === field)
        // 根据列类型做基本的类型转换
        if (col?.type === 'number') {
          const num = Number(val)
          row[field] = isNaN(num) ? val : num
        } else if (col?.type === 'switch') {
          row[field] = val.toLowerCase() === 'true' || val === '1'
        } else {
          row[field] = val
        }
      }
      if (Object.keys(row).length > 0) {
        newRows.push(row)
      }
    }

    if (newRows.length > 0) {
      rows.value = [...rows.value, ...newRows]
      emit('update:modelValue', [...rows.value])
    }
  }
  reader.readAsText(file)
  // Reset input so the same file can be re-imported
  input.value = ''
}

// A lookup cell holds either the primary-key scalar directly or a full row snapshot.
// Export the PK scalar so re-import can rehydrate the cell via fetchLookupRowByPrimaryKey.
function lookupExportScalar(col: Column, raw: unknown): string {
  if (raw == null || raw === '') return ''
  if (typeof raw !== 'object') return String(raw)
  const snapshot = raw as Record<string, unknown>
  const pkField =
    (typeof col.props?.primaryKeyField === 'string' && col.props.primaryKeyField.trim())
    || (Array.isArray(col.props?.searchFields) && typeof col.props.searchFields[0] === 'string' && col.props.searchFields[0])
    || 'id'
  const v = snapshot[pkField] ?? snapshot.id
  return v == null ? '' : String(v)
}

function handleExport() {
  // linkForm columns hold no cell value (a runtime FK-resolved link) \u2014 still excluded.
  // lookup columns are exported as their primary-key scalar so they round-trip on import.
  const cols = props.columns.filter(c => c.type !== 'linkForm')
  const headers = cols.map(c => c.field)
  // BOM for Excel UTF-8 compatibility
  let csv = '\uFEFF' + headers.map(h => `"${h.replace(/"/g, '""')}"`).join(',') + '\n'
  // Append data rows
  for (const row of rows.value) {
    const values = cols.map(c => {
      const v = c.type === 'lookup' ? lookupExportScalar(c, row[c.field]) : row[c.field]
      if (v == null || v === '') return ''
      return `"${String(v).replace(/"/g, '""')}"`
    })
    csv += values.join(',') + '\n'
  }
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${props.title || 'subtable'}_export.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const rows = ref<any[]>([])

// Lookup cells (scalar PK hydration watch registers first — same effect order as before the split)
const { effectiveLookupRowForCell, shouldShowLookupBackfill, lookupTagDisplayText } =
  useSubTableLookupCells(props, rows)

const { formatTaskStatus, effectiveShowTaskStatus, effectiveShowViewDetail } =
  useSubTableStatusColumns(props, rows, t)

const rowKeys = useSubTableRowKeys(props)

const linkFormScope = useSubTableLinkFormScope(props, rowKeys)

const linkFormDialog = useSubTableLinkFormDialog(props, rows, emit, t, {
  resolveSubTableRowPk: rowKeys.resolveSubTableRowPk,
  filterRowsByMiLinkFormParent: linkFormScope.filterRowsByMiLinkFormParent
})
const {
  linkFormDialogVisible,
  linkFormModalPanelRef,
  selectedLinkBinding,
  linkFormModalTitle,
  linkedFormFields,
  linkedFormLabelWidth,
  canEditSelectedLinkBinding,
  showLinkFormDetailActionFooter,
  linkedSubTableRows,
  linkedFormData,
  updateLinkedFormField,
  closeLinkFormDetailDialog,
  saveLinkedFormData,
  handleLinkedSubTableUpdate
} = linkFormDialog

const { handleLinkFormClick } = useSubTableLinkFormOpen(props, emit, linkFormDialog, linkFormScope)

const { uploadNames, downloadingKeys, downloadFile } = useSubTableFileDownload(t)

const assignment = useSubTableAssignment(props, rows, emit, t, rowKeys)
const {
  assignDialogVisible,
  selectedAssigneeId,
  currentAssignRow,
  currentAssignRowIndex,
  assigning,
  userOptions,
  userSearchLoading,
  userNameCache,
  showAssigneeColumn,
  openAssignDialog,
  onAssignDialogOpened,
  searchUsers,
  getUserDisplayName,
  resolveRowAssigneeCell,
  confirmAssignment
} = assignment

/**
 * Test-facing bindings: SubTableField.assign / FormRenderer.subTable property tests assert these via
 * {@code wrapper.vm.*}; they are not referenced by this SFC's template, so mark them as read for noUnusedLocals.
 */
void currentAssignRow
void currentAssignRowIndex
void userNameCache

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

watch(() => props.modelValue, (v) => { rows.value = v ? [...v] : [] }, { immediate: true })

const {
  dialogVisible,
  dialogMode,
  editingRowIndex,
  dialogInitialData,
  subTableDialogColumns,
  listViewColumnsForAudit,
  handleAdd,
  openEditDialog,
  handleDialogSave,
  deleteRow
} = useSubTableRowDialog(props, rows, emit, t, assignment)
/** Test-facing binding (wrapper.vm.editingRowIndex) — see comment above. */
void editingRowIndex

const { clearAssigneeDisplayHydrateTimer } = useSubTableAssigneeHydration(props, rows, emit, assignment)

const { startPolling, stopPolling, startWebSocketSubscription, stopWebSocketSubscription } =
  useSubTablePollingSync(props, rows, emit, rowKeys)

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
  clearAssigneeDisplayHydrateTimer()
  stopPolling()
  stopWebSocketSubscription()
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
      cursor: not-allowed;

      .lookup-selected-wrapper {
        background: var(--el-disabled-bg-color, #f5f7fa);
        border-color: var(--el-disabled-border-color, #e4e7ed);
        cursor: not-allowed;
        pointer-events: none;
      }

      .lookup-input :deep(.el-input__wrapper) {
        background-color: var(--el-disabled-bg-color, #f5f7fa);
        box-shadow: 0 0 0 1px var(--el-disabled-border-color, #e4e7ed) inset;
        cursor: not-allowed;
        pointer-events: none;
      }
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

  .lookup-input {
    width: 100%;
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

.link-form-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
  color: #303133;
}

.link-form-modal-title {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  font-size: 16px;
  line-height: 1.4;
}

.link-form-modal-close {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #909399;
  cursor: pointer;

  &:hover {
    color: var(--el-color-primary);
    background: var(--el-fill-color-light);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }
}

.link-form-dialog-body {
  min-height: 160px;
  padding: 16px;
  overflow: auto;
  flex: 1;
  min-height: 0;
}

.link-form-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  flex-shrink: 0;
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}

.link-form-modal-footer-btn {
  min-width: 88px;
  padding: 8px 20px;
  font-size: 14px;
  line-height: 1.5;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid transparent;
}

.link-form-modal-footer-btn--secondary {
  border-color: #dcdfe6;
  color: #606266;
  background: #fff;

  &:hover {
    color: var(--el-color-primary);
    border-color: var(--el-color-primary-light-5);
  }
}

.link-form-modal-footer-btn--primary {
  color: #fff;
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);

  &:hover {
    background: var(--el-color-primary-light-3);
    border-color: var(--el-color-primary-light-3);
  }
}

.linked-form-card {
  width: 100%;
  margin-bottom: 12px;
}
</style>
