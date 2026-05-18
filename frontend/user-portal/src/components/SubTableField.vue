<template>
  <div class="sub-table-field">
    <div class="sub-table-header">
      <span class="title">{{ title }}</span>
      <el-button
        v-if="editable"
        type="primary"
        size="small"
        @click="handleAdd"
      >
        <el-icon><Plus /></el-icon> {{ t('subTable.add') }}
      </el-button>
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
            <template v-else-if="col.type === 'upload'">
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
                    <span
                      v-else
                      class="lookup-readonly-empty"
                    >-</span>
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
                v-else-if="assigneeField && scope.row[assigneeField]"
                class="assignee-name"
              >
                {{ getUserDisplayName(scope.row[assigneeField]) }}
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
                {{ scope.row[assigneeField] ? t('subTable.reassign') : t('subTable.assign') }}
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
      :columns="editableColumns"
      :mode="dialogMode"
      :initial-data="dialogInitialData"
      :row-formulas="rowFormulas"
      :column-validation-rules="validationConfig?.columnRules"
      :upload-url="uploadUrl"
      @update:visible="dialogVisible = $event"
      @save="handleDialogSave"
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
                <template
                  v-for="field in linkedFormFields"
                  :key="field.key"
                >
                  <el-col
                    v-if="field.type === 'card'"
                    :span="field.span || 24"
                  >
                    <el-card
                      shadow="never"
                      class="linked-form-card"
                    >
                      <template
                        v-if="field.label"
                        #header
                      >
                        <span>{{ field.label }}</span>
                      </template>
                      <el-row :gutter="20">
                        <el-col
                          v-for="child in field.children || []"
                          :key="child.key"
                          :span="child.span || 24"
                        >
                          <el-form-item
                            :label="child.label"
                            :prop="child.key"
                            :required="child.required"
                          >
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
                  <el-col
                    v-else
                    :span="field.span || 24"
                  >
                    <el-form-item
                      :label="field.label"
                      :prop="field.key"
                      :required="field.required"
                    >
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
      <el-form label-width="100px">
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
import { ref, watch, computed, nextTick, withDefaults } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Document, Loading, Search, Close } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import SubTableAddDialog from './SubTableAddDialog.vue'
import { resolveDisplayValue, unwrapUserLikeValueToDisplayString, extractUserIdFromCellValue, isUserSnapshotLikeObject, userObjectTagDisplayString, userSnapshotViewFieldsFromRow, formatUserSnapshotCellValue } from './subTableAddDialogHelpers'
import { fetchLookupRowByPrimaryKey } from './lookup/fetchLookupRowByPrimaryKey'
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
import {
  collectNestedChildRowsFromPeerBindings,
  mergeSubTableRowsByRowId,
  pullNestedRowsForBindingFromParentRows,
  stripLinkFormDesignerTableLabel
} from '@/composables/tasks/shared'

const { t } = useI18n()

function formatTaskStatus(status: unknown): string {
  if (status === 'COMPLETED') return t('subTable.taskCompleted')
  if (status === 'IN_PROGRESS' || status === 'ASSIGNED') return t('subTable.taskInProgress')
  return t('subTable.taskPending')
}

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
  tableId?: number | null
  bindingType: string
  bindingMode: string
  foreignKeyField?: string | null
  tableName: string
  physicalTableName?: string
  tableType: string
  tableDescription: string
  columns: Column[]
  /** Designer PK field names (from admin tableBindings); preferred over hardcoded id/rowId. */
  primaryKeyFields?: string[]
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

function effectiveLookupViewFields(col: Column, rawValue: unknown): Array<{ fieldName: string; displayLabel?: string; sortOrder?: number; visible?: boolean }> {
  const configured = lookupDisplayViewFields(col)
  if (configured.length > 0) return configured
  if (isUserSnapshotLikeObject(rawValue)) {
    return userSnapshotViewFieldsFromRow(rawValue).map(f => ({
      fieldName: f.key,
      displayLabel: f.label
    }))
  }
  return []
}

function getSnapshotField(rowData: unknown, key: string): unknown {
  if (rowData == null || typeof rowData !== 'object' || Array.isArray(rowData)) return undefined
  return (rowData as Record<string, unknown>)[key]
}

const props = withDefaults(defineProps<{
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
}>(), {
  showLinkFormDialogFooter: false,
  linkFormClickScrollToInline: false,
  compactLookupCells: false
})

/** 子表单元格：主键标量经 {@link fetchLookupRowByPrimaryKey} 解析后的行（缓存），供标签/回填使用。 */
const lookupHydratedScalar = ref<Record<string, Record<string, any>>>({})

function effectiveLookupRowForCell(col: Column, rawValue: unknown): Record<string, any> | null {
  const tid = col.props?.tableId
  if (tid != null && rawValue != null && (typeof rawValue === 'string' || typeof rawValue === 'number')) {
    const ck = `${Number(tid)}:${String(rawValue).trim()}`
    const hit = lookupHydratedScalar.value[ck]
    if (hit) return hit
  }
  return lookupSelectedRow(col, rawValue)
}

/**
 * 紧凑列表模式下默认不展开回填块；列上显式开启回填视图时仍渲染（与 FormRenderer 设计选项一致）。
 */
function shouldShowLookupBackfill(col: Column): boolean {
  if (col.props?.showBackfillView === false) return false
  if (col.props?.showBackfillView === true) return true
  return !props.compactLookupCells
}

function lookupTagDisplayText(col: Column, rawValue: unknown): string {
  if (rawValue != null && isUserSnapshotLikeObject(rawValue)) {
    return userObjectTagDisplayString(rawValue)
  }
  const eff = effectiveLookupRowForCell(col, rawValue)
  if (eff) {
    return resolveDisplayValue(col, eff)
  }
  return resolveDisplayValue(col, rawValue)
}

function normalizeColumnHeaderLabel(s: string): string {
  return String(s || '').trim().toLowerCase()
}

/**
 * List view already carries MI / task progress (often long i18n labels like "Multi-instance subtask status"
 * with a field name other than {@code task_status}) — suppress the runtime "Status" column.
 */
function columnRepresentsMiOrTaskStatusList(col: Column): boolean {
  const f = String(col.field || '').toLowerCase()
  if (f === 'task_status' || f.endsWith('_task_status')) return true
  if (/\btask[_-]?status\b/i.test(f) || f.includes('taskstatus')) return true
  const lab = normalizeColumnHeaderLabel(String(col.label || ''))
  if (!lab) return false
  if (lab.includes('task status') || lab.includes('subtask status')) return true
  if (lab.includes('sub-task') && lab.includes('status')) return true
  if ((lab.includes('multi-instance') || lab.includes('multi instance')) && lab.includes('status')) return true
  if (lab.includes('multiinstance') && lab.includes('status')) return true
  return false
}

/** English/legacy headers often use "Status" while i18n runtime column uses another locale — still one conceptual column. */
function columnHeaderIsGenericStatusLabel(col: Column): boolean {
  const lab = normalizeColumnHeaderLabel(String(col.label || ''))
  return lab === 'status' || lab === '状态' || lab === '狀態'
}

/**
 * Row carries Flowable MI task_status; list already has a column whose header reads like a task/MI status
 * (even when {@link columnRepresentsMiOrTaskStatusList} missed due to unusual wording).
 */
function listViewLikelyAlreadyShowsTaskStatus(rowsSample: unknown[]): boolean {
  const r0 = rowsSample?.[0]
  if (!r0 || typeof r0 !== 'object') return false
  if ((r0 as Record<string, unknown>).task_status === undefined) return false
  if ((props.columns || []).some(columnHeaderIsGenericStatusLabel)) return true
  return (props.columns || []).some(c => {
    const lab = normalizeColumnHeaderLabel(String(c.label || ''))
    if (!lab.includes('status')) return false
    return /task|subtask|sub-task|multi|instance|parallel|loop|progress|assignee|participant|办理|子任务|多实例|進度|狀態/.test(lab)
  })
}

/** Designer list may already include task_status / an "Actions" column; avoid duplicating MI summary extras. */
const effectiveShowTaskStatus = computed(() => {
  if (!props.showTaskStatus) return false
  if (props.columns.some(columnRepresentsMiOrTaskStatusList)) return false
  const statusHeader = normalizeColumnHeaderLabel(t('subTable.taskStatus'))
  if (statusHeader && props.columns.some(c => normalizeColumnHeaderLabel(c.label) === statusHeader)) {
    return false
  }
  if (listViewLikelyAlreadyShowsTaskStatus(rows.value)) return false
  return true
})

const effectiveShowViewDetail = computed(() => {
  if (!props.showViewDetail) return false
  if (props.columns.some(c => String(c.field).toLowerCase() === 'actions')) return false
  const actionsHeader = normalizeColumnHeaderLabel(t('subTable.actions'))
  if (actionsHeader && props.columns.some(c => normalizeColumnHeaderLabel(c.label) === actionsHeader)) {
    return false
  }
  /**
   * Read-only (e.g. My Request): Link Form already provides a row-level Details affordance; the extra
   * Actions/Detail column duplicates UX for common MI+linkForm list designs.
   */
  if (!props.editable && props.columns.some(c => c.type === 'linkForm')) {
    return false
  }
  /** Same locale/header mismatch pattern as Status: designer "Actions" vs i18n. */
  const r0 = rows.value?.[0]
  if (
    !props.editable &&
    r0 &&
    typeof r0 === 'object' &&
    (r0 as Record<string, unknown>).task_status !== undefined &&
    (props.columns || []).some(c => {
      const lab = normalizeColumnHeaderLabel(String(c.label || ''))
      return lab === 'actions' || lab === '操作'
    })
  ) {
    return false
  }
  return true
})

const emit = defineEmits<{
  (e: 'update:modelValue', val: any[]): void
  (e: 'assignmentChanged'): void
  (e: 'dataRefreshed', rows: any[]): void
  (e: 'viewDetail', row: any, index: number): void
  (e: 'fillForm', row: any, index: number): void
  (e: 'update:linkedSubTableData', bindingId: number, rows: any[]): void
  (e: 'linkFormScrollToInline'): void
}>()

const rows = ref<any[]>([])

async function hydrateLookupScalarsInTable() {
  const tableRows = rows.value || []
  for (const col of props.columns || []) {
    if (col.type !== 'lookup') continue
    const tableId = col.props?.tableId
    if (tableId == null || !Number.isFinite(Number(tableId))) continue
    const pk =
      (typeof (col.props as { primaryKeyField?: string }).primaryKeyField === 'string' &&
        (col.props as { primaryKeyField?: string }).primaryKeyField) ||
      (props.primaryKeyFields?.length === 1 ? props.primaryKeyFields[0] : undefined) ||
      'id'
    for (const row of tableRows) {
      const raw = row[col.field]
      if (raw == null || typeof raw === 'object') continue
      const ck = `${Number(tableId)}:${String(raw).trim()}`
      if (lookupHydratedScalar.value[ck]) continue
      const loaded = await fetchLookupRowByPrimaryKey(Number(tableId), raw, {
        searchFields: (col.props?.searchFields as string[]) || [],
        displayField: (col.props?.displayField as string) || '',
        filterConditions: (col.props?.filterConditions as { fieldName: string; value: string }[]) || [],
        primaryKeyField: pk
      })
      if (loaded) {
        lookupHydratedScalar.value = { ...lookupHydratedScalar.value, [ck]: loaded }
      }
    }
  }
}

watch(
  () => [rows.value, props.columns],
  () => {
    void hydrateLookupScalarsInTable()
  },
  { deep: true, immediate: true }
)
// key = "{rowIndex}_{field}" -> original filename (recorded during current session upload)
const uploadNames = ref<Record<string, string>>({})
// Set of keys currently being downloaded
const downloadingKeys = ref<Record<string, boolean>>({})

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
/** When footer is shown (To Do), restore on Cancel/X without persisting to parent row. */
const linkFormDialogSnapshot = ref<{ linkedFormData: Record<string, any>; linkedSubTableRows: any[] } | null>(null)

/** Designer list views may store "ADD + …"; runtime bindings use display names — align with {@link stripLinkFormDesignerTableLabel}. */
function linkFormTableMatchKey(name?: string): string {
  return normalizeSubTableName(stripLinkFormDesignerTableLabel(String(name || '')).replace(/\s+/g, ''))
}

/**
 * Multiple bindings can share the same bindingId (prev vs current). `.find` always picked the first;
 * for read-only / snapshot UI (`suppressLinkFormInitialData` false), prefer the first match that
 * already has row data so Details is not blank. MI todo (`suppress` true) keeps the first match
 * so isolated empty binding wins → blank Details for a new sub-task.
 */
function resolveLinkBindingForColumn(col: Column | null | undefined): SubTableBinding | undefined {
  if (!col) return undefined
  const list = props.linkedSubTableBindings ?? []
  const boundId = col.props?.boundSubTableBindingId
  const boundNameRaw = col.props?.boundSubTableName ? String(col.props.boundSubTableName).trim() : ''
  const boundNameStripped = stripLinkFormDesignerTableLabel(boundNameRaw)
  const boundKey = linkFormTableMatchKey(boundNameRaw)

  const matches = list.filter(item => {
    if (boundId != null && Number(item.bindingId) === Number(boundId)) return true
    if (boundNameRaw && item.tableName === boundNameRaw) return true
    if (boundKey && linkFormTableMatchKey(item.tableName) === boundKey) return true
    if (boundNameStripped || boundId != null) {
      const pid = boundId != null ? Number(boundId) : -2147483648
      return subTableBindingMatches(
        {
          bindingId: pid,
          tableName: boundNameStripped || boundNameRaw,
          tableId: null,
          physicalTableName: undefined
        },
        {
          bindingId: item.bindingId,
          tableName: item.tableName,
          tableId: item.tableId ?? null,
          physicalTableName: item.physicalTableName
        }
      )
    }
    return false
  })
  if (matches.length === 0) return undefined
  if (props.suppressLinkFormInitialData) return matches[0]
  const withData = matches.find(m => Array.isArray(m.data) && m.data.length > 0)
  return withData ?? matches[0]
}

const selectedLinkBinding = computed(() => {
  const col = activeLinkColumn.value
  return resolveLinkBindingForColumn(col) ?? null
})

/** Modal title: bound sub-table name + i18n (do not use list column label — avoids stale "ADD + …" text). */
const linkFormModalTitle = computed(() => {
  const col = activeLinkColumn.value
  const fromProp = col?.props?.boundSubTableName ? String(col.props.boundSubTableName).trim() : ''
  const fromBinding = selectedLinkBinding.value?.tableName ? String(selectedLinkBinding.value.tableName).trim() : ''
  const tableName = fromProp || fromBinding
  if (tableName) return t('linkForm.dialogTitleAddTable', { tableName })
  return t('linkForm.linkedForm')
})

const linkedFormFields = computed(() => selectedLinkBinding.value?.formFields || [])
const linkedFormLabelWidth = computed(() => {
  const width = selectedLinkBinding.value?.formOptions?.form?.labelWidth
  return typeof width === 'string' && width.trim() ? width : '125px'
})
const canEditSelectedLinkBinding = computed(() => !!(props.editable && selectedLinkBinding.value?.bindingMode === 'EDITABLE'))

/** Field-layout link form only; grid fallback has no footer. */
const showLinkFormDetailActionFooter = computed(
  () =>
    !!props.showLinkFormDialogFooter &&
    canEditSelectedLinkBinding.value &&
    !!selectedLinkBinding.value &&
    linkedFormFields.value.length > 0
)

function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

function subTableBindingMatches(
  target?: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null } | null,
  source?: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null } | null
): boolean {
  if (!target || !source) return false
  if (target.bindingId === source.bindingId) return true
  if (target.tableId != null && source.tableId != null && Number(target.tableId) === Number(source.tableId)) return true
  const targetPhysicalName = normalizeSubTableName(target.physicalTableName)
  const sourcePhysicalName = normalizeSubTableName(source.physicalTableName)
  if (targetPhysicalName && sourcePhysicalName && targetPhysicalName === sourcePhysicalName) return true
  const targetName = normalizeSubTableName(target.tableName)
  const sourceName = normalizeSubTableName(source.tableName)
  return !!targetName && targetName === sourceName
}

function resolveLinkedFallbackRows(binding?: SubTableBinding): any[] {
  if (!binding) return []
  if (Array.isArray(binding.data) && binding.data.length > 0) return binding.data
  const sameTableBinding = props.linkedSubTableBindings?.find(item =>
    item !== binding &&
    Array.isArray(item.data) &&
    item.data.length > 0 &&
    subTableBindingMatches(item, binding)
  )
  return Array.isArray(sameTableBinding?.data) ? sameTableBinding.data : []
}

/** When copied forms / Link merge use a different bindingId than variables, {@link subTableBindingMatches} may miss; score by form field keys vs row keys. */
function collectLinkTargetFormFieldKeys(binding?: SubTableBinding): Set<string> {
  const keys = new Set<string>()
  if (!binding?.formFields?.length) return keys
  for (const f of binding.formFields) {
    if (f.type === 'card') {
      f.children?.forEach(c => {
        if (typeof c.key === 'string' && c.key) keys.add(c.key)
      })
    } else if (typeof f.key === 'string' && f.key) {
      keys.add(f.key)
    }
  }
  return keys
}

/** Max overlap score over all rows (row0 is often a MI placeholder; real payload may be at index 1+). */
function maxFormFieldOverlapScore(rows: any[], fieldKeys: Set<string>): number {
  if (!Array.isArray(rows) || fieldKeys.size === 0) return -1
  let best = -1
  for (const r of rows) {
    if (!r || typeof r !== 'object') continue
    let score = 0
    for (const fk of fieldKeys) {
      const v = rowValueForLinkedFormField(r as Record<string, any>, fk)
      if (isPresentLinkedModalValue(v)) score++
    }
    if (score > best) best = score
  }
  return best
}

function peerSubTableDataByFormFieldOverlap(binding: SubTableBinding | undefined, peers: SubTableBinding[]): any[] {
  if (!binding || !peers.length) return []
  const fieldKeys = collectLinkTargetFormFieldKeys(binding)
  if (fieldKeys.size === 0) return []
  const threshold =
    fieldKeys.size <= 2 ? 1 : Math.min(fieldKeys.size, Math.max(2, Math.ceil(fieldKeys.size * 0.25)))

  let best: any[] = []
  let bestScore = -1
  for (const p of peers) {
    if (!Array.isArray(p.data) || p.data.length === 0) continue
    const score = maxFormFieldOverlapScore(p.data, fieldKeys)
    if (score >= threshold && score > bestScore) {
      bestScore = score
      best = p.data
    }
  }
  return best
}


/** When Details uses process-level fallback rows (no row.__subTables__), narrow to this parent participant if child rows carry FKs. */
function filterLinkedChildRowsForParentRow(
  parentRow: Record<string, any>,
  rows: any[],
  binding?: SubTableBinding
): any[] {
  if (!Array.isArray(rows) || rows.length === 0) return rows
  const parentPk = resolveSubTableRowPk(parentRow as Record<string, unknown>)
  const parentId = parentPk != null ? Number(parentPk) : NaN
  if (Number.isNaN(parentId)) return rows

  const fkList: string[] = []
  if (binding?.foreignKeyField && String(binding.foreignKeyField).trim()) {
    fkList.push(String(binding.foreignKeyField))
  }
  for (const k of ['participant_id', 'participantId', 'parent_id', 'parentId', 'meeting_participant_id']) {
    if (!fkList.includes(k)) fkList.push(k)
  }

  const hasAnyFk = rows.some(r => {
    if (!r || typeof r !== 'object') return false
    return fkList.some(k => (r as Record<string, unknown>)[k] != null && String((r as Record<string, unknown>)[k]).trim() !== '')
  })
  if (!hasAnyFk) return rows

  const filtered = rows.filter(r => {
    if (!r || typeof r !== 'object') return false
    return fkList.some(k => {
      const v = (r as Record<string, unknown>)[k]
      return v != null && v !== '' && Number(v) === parentId
    })
  })
  return filtered.length > 0 ? filtered : rows
}

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

/** Header close / Cancel: with To Do footer, discard edits; otherwise auto-save field link form when editable. */
function closeLinkFormDetailDialog() {
  if (showLinkFormDetailActionFooter.value) {
    const snap = linkFormDialogSnapshot.value
    if (snap) {
      linkedFormData.value = JSON.parse(JSON.stringify(snap.linkedFormData)) as Record<string, any>
      linkedSubTableRows.value = JSON.parse(JSON.stringify(snap.linkedSubTableRows)) as any[]
    }
    linkFormDialogVisible.value = false
    linkFormDialogSnapshot.value = null
    return
  }
  if (
    canEditSelectedLinkBinding.value &&
    selectedLinkBinding.value &&
    linkedFormFields.value.length > 0
  ) {
    saveLinkedFormData()
    return
  }
  linkFormDialogVisible.value = false
}

function handleLinkFormClick(col: Column, row: Record<string, any>, rowIndex: number) {
  if (props.linkFormClickScrollToInline) {
    emit('linkFormScrollToInline')
    return
  }
  activeLinkColumn.value = col
  activeLinkRowIndex.value = rowIndex
  const binding = resolveLinkBindingForColumn(col)
  const boundId = col.props?.boundSubTableBindingId
  const boundName = col.props?.boundSubTableName || binding?.tableName
  const rowSub = row?.__subTables__ && typeof row.__subTables__ === 'object' ? (row.__subTables__ as Record<string, any>) : {}
  let saved: unknown =
    boundId != null ? rowSub[boundId] ?? rowSub[String(boundId)] : undefined
  if (!Array.isArray(saved) && boundName) {
    const raw = String(boundName).trim()
    const stripped = stripLinkFormDesignerTableLabel(raw)
    const tryKeys = [raw, stripped].filter((k, i, a) => k && a.indexOf(k) === i)
    for (const k of tryKeys) {
      const v = rowSub[k] ?? rowSub[String(k)]
      if (Array.isArray(v)) {
        saved = v
        break
      }
    }
    if (!Array.isArray(saved)) {
      const want = linkFormTableMatchKey(raw)
      if (want) {
        for (const rk of Object.keys(rowSub)) {
          if (linkFormTableMatchKey(rk) !== want) continue
          const v = rowSub[rk]
          if (Array.isArray(v)) {
            saved = v
            break
          }
        }
      }
    }
  }
  const savedRows = Array.isArray(saved) ? saved : []
  const baseFallbackRows = resolveLinkedFallbackRows(binding)
  const fallbackRows =
    baseFallbackRows.length > 0
      ? baseFallbackRows
      : peerSubTableDataByFormFieldOverlap(binding, props.linkedSubTableBindings ?? [])
  /**
   * MI 待办 `suppressLinkFormInitialData=true` 时仍优先用行内嵌套 `__subTables__`；
   * 若为空则回退到绑定数据（已在上层做多实例行隔离），并按父行主键收窄子表行，避免空白/错误。
   * 非 MI 行为不变：无 suppress 时仍可用全量 fallback + 父行过滤。
   */
  let effectiveSavedRows: any[] = []
  if (props.suppressLinkFormInitialData) {
    if (savedRows.length > 0) {
      effectiveSavedRows = savedRows
    } else if (fallbackRows.length > 0) {
      effectiveSavedRows = row
        ? filterLinkedChildRowsForParentRow(row, [...fallbackRows], binding)
        : [...fallbackRows]
    } else {
      effectiveSavedRows = []
    }
  } else {
    effectiveSavedRows = savedRows.length > 0 ? savedRows : fallbackRows
    if (savedRows.length === 0 && effectiveSavedRows.length > 0 && row) {
      effectiveSavedRows = filterLinkedChildRowsForParentRow(row, effectiveSavedRows, binding)
    }
  }
  /**
   * Parent row.__subTables__[child] may be [{}] / assignee-only placeholders. That makes savedRows non-empty so we
   * never took binding.data fallback; merge in fallback so Link Form modal matches To Do / variables.
   */
  if (
    binding?.formFields?.length &&
    fallbackRows.length > 0 &&
    effectiveSavedRows.length > 0 &&
    linkFormRowsLackFormPayload(effectiveSavedRows, binding.formFields)
  ) {
    effectiveSavedRows = mergeSubTableRowsByRowId(
      [...effectiveSavedRows],
      [...fallbackRows],
      binding.primaryKeyFields ?? null
    )
  }
  if (
    effectiveSavedRows.length === 0 &&
    binding &&
    Array.isArray(props.linkedSubTableBindings) &&
    props.linkedSubTableBindings.length > 0
  ) {
    const nested = collectNestedChildRowsFromPeerBindings(
      binding,
      props.linkedSubTableBindings as SubTableBinding[],
      null
    )
    if (nested.length > 0) {
      effectiveSavedRows = row
        ? filterLinkedChildRowsForParentRow(row, [...nested], binding)
        : [...nested]
    }
  }

  /** My Request / read-only: child slice often lives only under this parent row's {@code __subTables__} (key variants), not in {@code binding.data}. */
  if (binding && row && Array.isArray(props.linkedSubTableBindings) && props.linkedSubTableBindings.length > 0) {
    const peerMap = new Map<number, number | null>()
    for (const b of props.linkedSubTableBindings) {
      const tid = b.tableId != null ? Number(b.tableId) : null
      if (tid != null && Number.isFinite(tid)) peerMap.set(Number(b.bindingId), tid)
    }
    const fromClickedParent = pullNestedRowsForBindingFromParentRows(
      {
        bindingId: Number(binding.bindingId),
        tableName: String(binding.tableName ?? ''),
        physicalTableName: (binding as { physicalTableName?: string }).physicalTableName,
        tableId: binding.tableId ?? null
      },
      [row],
      peerMap
    )
    if (fromClickedParent.length > 0) {
      effectiveSavedRows = mergeSubTableRowsByRowId(
        effectiveSavedRows.length > 0 ? [...effectiveSavedRows] : [],
        fromClickedParent,
        binding.primaryKeyFields ?? null
      )
    }
  }

  const rowDataKeyCount = (r: unknown) =>
    r && typeof r === 'object' ? Object.keys(r as object).filter(k => !k.startsWith('__')).length : 0
  if (
    binding &&
    effectiveSavedRows.length > 0 &&
    rowDataKeyCount(effectiveSavedRows[0]) <= 2 &&
    Array.isArray(props.linkedSubTableBindings) &&
    props.linkedSubTableBindings.length > 0
  ) {
    const nestedPeers = collectNestedChildRowsFromPeerBindings(
      binding,
      props.linkedSubTableBindings as SubTableBinding[],
      null
    )
    if (nestedPeers.length > 0) {
      effectiveSavedRows = mergeSubTableRowsByRowId(
        [...effectiveSavedRows],
        nestedPeers,
        binding.primaryKeyFields ?? null
      )
    }
  }

  if (row && binding && effectiveSavedRows.length > 0) {
    const narrowed = filterLinkedChildRowsForParentRow(row, [...effectiveSavedRows], binding)
    if (narrowed.length > 0) effectiveSavedRows = narrowed
  }

  if (binding?.formFields?.length && effectiveSavedRows.length > 1) {
    const pr = promoteBestRowForLinkFormModal(effectiveSavedRows, binding.formFields)
    effectiveSavedRows = pr.rows
  }

  linkedSubTableRows.value = [...effectiveSavedRows]
  linkedFormData.value = buildLinkedFormData({ ...(binding || ({} as any)), data: effectiveSavedRows })
  const bindingForFooter = resolveLinkBindingForColumn(col)
  const formFieldsLen = bindingForFooter?.formFields?.length ?? 0
  const useDetailFooter =
    !!props.showLinkFormDialogFooter &&
    props.editable &&
    bindingForFooter?.bindingMode === 'EDITABLE' &&
    formFieldsLen > 0
  if (useDetailFooter) {
    linkFormDialogSnapshot.value = {
      linkedFormData: JSON.parse(JSON.stringify(linkedFormData.value)) as Record<string, any>,
      linkedSubTableRows: JSON.parse(JSON.stringify(linkedSubTableRows.value)) as any[]
    }
  } else {
    linkFormDialogSnapshot.value = null
  }
  linkFormDialogVisible.value = true
}

function rowValueForLinkedFormField(row: Record<string, any>, key: string): unknown {
  if (!row || typeof row !== 'object') return undefined
  if (Object.prototype.hasOwnProperty.call(row, key)) return row[key]
  const want = key.toLowerCase()
  const wantNorm = want.replace(/_/g, '')
  for (const rk of Object.keys(row)) {
    if (rk.startsWith('__')) continue
    const rkl = rk.toLowerCase()
    if (rkl === want) return row[rk]
    if (wantNorm.length > 0 && rkl.replace(/_/g, '') === wantNorm) return row[rk]
  }
  return undefined
}

function isPresentLinkedModalValue(v: unknown): boolean {
  if (v === undefined || v === null) return false
  if (typeof v === 'boolean') return true
  if (typeof v === 'number') return !Number.isNaN(v)
  if (typeof v === 'string') return v.trim() !== ''
  return true
}

/** Count filled link-form fields on one row (used to pick {@code data[0]} vs a richer sibling row). */
function scoreRowForLinkedFormFields(row: unknown, formFields?: FormField[]): number {
  if (!row || typeof row !== 'object' || !formFields?.length) return 0
  const o = row as Record<string, any>
  let s = 0
  for (const field of formFields) {
    if (field.type === 'card') {
      for (const c of field.children || []) {
        const v = rowValueForLinkedFormField(o, c.key)
        if (isPresentLinkedModalValue(v)) s++
      }
    } else {
      const v = rowValueForLinkedFormField(o, field.key)
      if (isPresentLinkedModalValue(v)) s++
    }
  }
  return s
}

/**
 * Link detail modal reads binding.data[0] only — if variables merged MI placeholders first, row 0 is empty while
 * another index holds the real payload; move the best-scoring row to the front without dropping siblings.
 */
function promoteBestRowForLinkFormModal(rows: any[], formFields: FormField[] | undefined): { rows: any[]; movedFrom: number | null } {
  if (!Array.isArray(rows) || rows.length <= 1 || !formFields?.length) return { rows, movedFrom: null }
  let bestIdx = 0
  let bestScore = scoreRowForLinkedFormFields(rows[0], formFields)
  for (let i = 1; i < rows.length; i++) {
    const sc = scoreRowForLinkedFormFields(rows[i], formFields)
    if (sc > bestScore) {
      bestScore = sc
      bestIdx = i
    }
  }
  if (bestIdx === 0) return { rows, movedFrom: null }
  const next = [...rows]
  const [pick] = next.splice(bestIdx, 1)
  return { rows: [pick, ...next], movedFrom: bestIdx }
}

/** True when saved nested rows carry no real values for any designer link-form field (placeholders only). */
function linkFormRowsLackFormPayload(rows: any[], formFields: FormField[] | undefined): boolean {
  if (!formFields?.length || !Array.isArray(rows) || rows.length === 0) return false
  const row0 = rows[0]
  if (!row0 || typeof row0 !== 'object') return true
  const o = row0 as Record<string, any>
  const anyFieldFilled = formFields.some(field => {
    if (field.type === 'card') {
      return (field.children || []).some(child =>
        isPresentLinkedModalValue(rowValueForLinkedFormField(o, child.key))
      )
    }
    return isPresentLinkedModalValue(rowValueForLinkedFormField(o, field.key))
  })
  return !anyFieldFilled
}

function buildLinkedFormData(binding?: SubTableBinding): Record<string, any> {
  const raw =
    binding?.data?.[0] && typeof binding.data[0] === 'object'
      ? (binding.data[0] as Record<string, any>)
      : {}
  const next: Record<string, any> = {}
  if (binding?.formFields?.length) {
    binding.formFields.forEach(field => {
      if (field.type === 'card') {
        field.children?.forEach(child => {
          const v = rowValueForLinkedFormField(raw, child.key)
          next[child.key] = isPresentLinkedModalValue(v) ? v : (child.defaultValue ?? null)
        })
      } else {
        const v = rowValueForLinkedFormField(raw, field.key)
        next[field.key] = isPresentLinkedModalValue(v) ? v : (field.defaultValue ?? null)
      }
    })
    return next
  }
  return { ...raw }
}

function updateLinkedFormField(key: string, value: any) {
  linkedFormData.value = { ...linkedFormData.value, [key]: value }
}

function saveLinkedFormData() {
  const linkRowIndex = activeLinkRowIndex.value
  const col = activeLinkColumn.value
  if (linkRowIndex == null || !col) {
    linkFormDialogSnapshot.value = null
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

  emit('update:modelValue', nextMainRows)
  linkFormDialogSnapshot.value = null
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
  try {
    const parsed = new URL(url, window.location.origin)
    const fromQuery = parsed.searchParams.get('originalName')
      || parsed.searchParams.get('fileName')
      || parsed.searchParams.get('filename')
      || parsed.searchParams.get('name')
    if (fromQuery) return decodeURIComponent(fromQuery)
    const pathPart = parsed.pathname.split('/').pop()
    return pathPart || 'unknown file'
  } catch {
    const [pathPart] = String(url).split('?')
    const last = pathPart.split('/').pop()
    return last || 'unknown file'
  }
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
  dialogVisible.value = true
}

function openEditDialog(i: number) {
  dialogMode.value = 'edit'
  editingRowIndex.value = i
  dialogInitialData.value = { ...rows.value[i] }
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
  selectedAssigneeId.value = extractUserIdFromCellValue(row[props.assigneeField || ''] as unknown) || ''
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

function getUserDisplayName(userId: unknown): string {
  if (userId == null || userId === '') return ''
  if (typeof userId === 'object' && !Array.isArray(userId)) {
    return unwrapUserLikeValueToDisplayString(userId)
  }
  const sid = String(userId)
  if (userNameCache.value[sid]) return userNameCache.value[sid]
  return sid.startsWith('user-') ? sid.substring(5) : sid
}

function formatAssigneeDisplayLabel(raw: unknown): string {
  if (raw == null || raw === '') return ''
  if (typeof raw === 'string' || typeof raw === 'number') return String(raw)
  return unwrapUserLikeValueToDisplayString(raw)
}

/** Align sub-table assignee column with task detail: resolve display names when only IDs are stored (e.g. completed tasks). */
const assigneeDisplayHydrateGeneration = ref(0)
let assigneeDisplayHydrateTimer: ReturnType<typeof setTimeout> | null = null

function scheduleHydrateAssigneeDisplayNames() {
  const af = props.assigneeField
  if (!af) return
  if (assigneeDisplayHydrateTimer) clearTimeout(assigneeDisplayHydrateTimer)
  assigneeDisplayHydrateTimer = setTimeout(() => {
    assigneeDisplayHydrateTimer = null
    void hydrateAssigneeDisplayNamesFromUserDirectory()
  }, 200)
}

async function hydrateAssigneeDisplayNamesFromUserDirectory() {
  const af = props.assigneeField
  if (!af || !rows.value.length) return
  const gen = ++assigneeDisplayHydrateGeneration.value

  let changed = false
  let next = rows.value.map(r => {
    if (!r || typeof r !== 'object') return r
    const sid = extractUserIdFromCellValue((r as Record<string, unknown>)[af])
    if (!sid) return r
    const existing = r.assignee_display_name
    if (existing != null && String(existing).trim() !== '') return r
    const cached = userNameCache.value[sid]
    if (!cached) return r
    changed = true
    return { ...r, assignee_display_name: cached }
  })
  if (changed) {
    rows.value = next
    emit('update:modelValue', [...next])
  }

  const idsToFetch = [...new Set(
    rows.value
      .map(r => (r && typeof r === 'object' ? extractUserIdFromCellValue((r as Record<string, unknown>)[af]) : ''))
      .filter(s => s.length > 0)
  )].filter(sid => {
    const row = rows.value.find(
      r => r && extractUserIdFromCellValue((r as Record<string, unknown>)[af]) === sid
    )
    if (!row) return false
    const hasName = row.assignee_display_name != null && String(row.assignee_display_name).trim() !== ''
    if (hasName) return false
    return !userNameCache.value[sid]
  })

  if (idsToFetch.length === 0) return

  await Promise.all(
    idsToFetch.map(async sid => {
      try {
        const info = await userApi.getUserSummary(sid)
        if (info?.name) {
          userNameCache.value = { ...userNameCache.value, [sid]: info.name }
        }
      } catch {
        /* ignore */
      }
    })
  )

  if (gen !== assigneeDisplayHydrateGeneration.value) return

  let changed2 = false
  const merged = rows.value.map(r => {
    if (!r || typeof r !== 'object') return r
    const sid = extractUserIdFromCellValue((r as Record<string, unknown>)[af])
    if (!sid) return r
    const existing = r.assignee_display_name
    if (existing != null && String(existing).trim() !== '') return r
    const cached = userNameCache.value[sid]
    if (!cached) return r
    changed2 = true
    return { ...r, assignee_display_name: cached }
  })
  if (changed2) {
    rows.value = merged
    emit('update:modelValue', [...merged])
  }
}

watch(
  () => [props.assigneeField, props.modelValue],
  () => scheduleHydrateAssigneeDisplayNames(),
  { deep: true, immediate: true }
)

const ROW_KEY_MERGE_SEP = '\u001f'

/**
 * Stable key matching {@link com.platform.common.jdbc.SubTableRowKeySupport#canonicalRowKeyString}
 * for server-provided {@code rowKey} on sub-table sync payloads.
 */
function canonicalRowKeyFromPayload(r: Record<string, unknown>): string | null {
  const rk = r.rowKey
  if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
    const o = rk as Record<string, unknown>
    return Object.keys(o)
      .sort()
      .map(k => `${k}=${o[k]}`)
      .join(ROW_KEY_MERGE_SEP)
  }
  return null
}

function resolveSubTableRowMergeKey(row: Record<string, unknown> | null | undefined): string | number | null {
  const c = canonicalRowKeyFromPayload(row || {})
  if (c != null && c !== '') return c
  return resolveSubTableRowPk(row)
}

/**
 * Sub-table row primary key for assignment APIs and client-side row matching.
 * Prefers designer single-column PK when provided; otherwise legacy id / rowId / MI heuristics.
 */
function resolveSubTableRowPk(row: Record<string, unknown> | null | undefined): string | number | null {
  if (!row) return null
  const r = row as Record<string, unknown>
  const pks = props.primaryKeyFields
  if (Array.isArray(pks) && pks.length === 1) {
    const v = r[pks[0]!]
    if (v != null && v !== '') return v as string | number
  }
  const candidates: unknown[] = [
    r.id,
    r.rowId,
    r.id_idw,
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
  const rowKeyRaw =
    row && typeof row === 'object' ? (row as Record<string, unknown>).rowKey : undefined
  const rowKeyForAssign =
    rowKeyRaw && typeof rowKeyRaw === 'object' && !Array.isArray(rowKeyRaw)
      ? (rowKeyRaw as Record<string, unknown>)
      : undefined

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
    if (rowKeyForAssign != null && Object.keys(rowKeyForAssign).length > 0) {
      response = await assignSubTableRow(
        props.taskId,
        0,
        selectedAssigneeId.value,
        rowKeyForAssign
      )
      if (effectiveTaskId !== props.taskId) {
        response = await assignSubTableRow(
          effectiveTaskId,
          0,
          selectedAssigneeId.value,
          rowKeyForAssign
        )
      }
    } else if (!Number.isNaN(rowIdNum)) {
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
        const rawDisplay = result.assigneeName ?? result.assigneeId
        const displayName =
          typeof rawDisplay === 'string' || typeof rawDisplay === 'number'
            ? String(rawDisplay)
            : unwrapUserLikeValueToDisplayString(rawDisplay)
        targetRow.assignee_display_name = displayName
        userNameCache.value[String(result.assigneeId)] = displayName
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
      // Merge the refreshed data with existing rows.
      // IMPORTANT: do NOT match by `id` when missing/undefined, or the first refreshed row
      // can be merged into every row, causing cross-row field leakage (e.g. assignee).
      const refreshedRows = result.rows as Array<Record<string, any>>
      const refreshedByPk = new Map<string | number, Record<string, any>>()
      for (const r of refreshedRows) {
        const pk = resolveSubTableRowMergeKey(r)
        if (pk != null && pk !== '') refreshedByPk.set(pk, r)
      }

      const updatedRows = rows.value.map((existingRow, idx) => {
        const pk = resolveSubTableRowMergeKey(existingRow as Record<string, unknown>)
        const refreshedRow =
          (pk != null && refreshedByPk.get(pk)) ||
          null
        if (refreshedRow) {
          return { ...existingRow, ...refreshedRow }
        }
        // Fallback: if no PKs are available, only merge by index when both sides exist.
        // This is safer than "undefined id" matching.
        const byIndex = refreshedRows[idx]
        if (pk == null && byIndex && resolveSubTableRowMergeKey(byIndex) == null) {
          return { ...existingRow, ...byIndex }
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
  if (assigneeDisplayHydrateTimer) {
    clearTimeout(assigneeDisplayHydrateTimer)
    assigneeDisplayHydrateTimer = null
  }
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
