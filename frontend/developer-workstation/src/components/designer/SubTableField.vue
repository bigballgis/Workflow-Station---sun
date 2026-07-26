<template>
  <div class="sub-table-field">
    <div class="sub-table-header">
      <span class="title">{{ config.title || t('subTable.defaultTitle') }}</span>
      <div
        v-if="editable"
        class="actions"
      >
        <el-button
          size="small"
          @click.stop="handleExport"
        >
          <el-icon><Download /></el-icon> {{ t('subTable.exportWithData') }}
        </el-button>
        <el-button
          v-if="canAdd && !hasFileColumn"
          size="small"
          @click.stop="triggerImport"
        >
          <el-icon><Upload /></el-icon> {{ t('subTable.import') }}
        </el-button>
        <el-button
          v-if="canAdd"
          type="primary"
          native-type="button"
          size="small"
          @click.stop="handleAdd"
        >
          <el-icon><Plus /></el-icon> {{ t('common.add') }}
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

    <div class="table-scroll-wrap">
    <el-table
      v-loading="loading"
      :data="tableData"
      size="small"
      border
      :max-height="config.maxHeight || 300"
    >
      <el-table-column
        v-for="col in displayColumns"
        :key="col.field"
        :prop="col.field"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth || 100"
      >
        <template #default="scope">
          <!-- upload / file (first: avoid falling through to plain text for stored URLs) -->
          <template v-if="isUploadColumn(col, scope.row[col.field])">
            <span
              v-if="resolveRowUploadUrl(scope.row, col)"
              class="file-download-link"
              :class="{ downloading: downloadingKeys[scope.$index + '_' + col.field] }"
              @click.stop="downloadFile(resolveRowUploadUrl(scope.row, col)!, uploadNames[scope.$index + '_' + col.field], scope.$index, col.field)"
            >
              <el-icon
                v-if="downloadingKeys[scope.$index + '_' + col.field]"
                class="is-loading"
              >
                <Loading />
              </el-icon>
              <el-icon v-else>
                <Document />
              </el-icon>
              {{ getFilenameFromUrl(resolveRowUploadUrl(scope.row, col) || '', uploadNames[scope.$index + '_' + col.field]) }}
            </span>
            <span
              v-else
              class="no-file"
            >-</span>
          </template>
          <!-- colorPicker -->
          <template v-else-if="col.type === 'colorPicker'">
            <span
              v-if="scope.row[col.field]"
              class="color-swatch"
              :style="{ backgroundColor: scope.row[col.field] }"
              :title="scope.row[col.field]"
            />
            <span v-else>-</span>
          </template>
          <!-- editor -->
          <template v-else-if="col.type === 'editor'">
            <span
              v-if="scope.row[col.field]"
              class="editor-preview"
              v-html="sanitizeHtml(scope.row[col.field])"
            />
            <span v-else>-</span>
          </template>
          <!-- signature -->
          <template v-else-if="col.type === 'signature'">
            <img
              v-if="scope.row[col.field]"
              :src="scope.row[col.field]"
              class="signature-preview"
              alt="Signature"
            >
            <span v-else>-</span>
          </template>
          <!-- transfer -->
          <template v-else-if="col.type === 'transfer'">
            <span>{{ Array.isArray(scope.row[col.field]) ? scope.row[col.field].join(', ') : (scope.row[col.field] ?? '-') }}</span>
          </template>
          <!-- cascader -->
          <template v-else-if="col.type === 'cascader'">
            <span>{{ Array.isArray(scope.row[col.field]) ? scope.row[col.field].join(' / ') : (scope.row[col.field] ?? '-') }}</span>
          </template>
          <!-- rate -->
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
          <!-- slider -->
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
          <!-- password -->
          <template v-else-if="col.type === 'password'">
            <span>******</span>
          </template>
          <!-- link form action -->
          <template v-else-if="col.type === 'linkForm'">
            <el-link
              type="primary"
              :underline="false"
              @click.stop="openLinkFormDialog(col, scope.row)"
            >
              {{ col.props?.linkText || t('linkForm.defaultLinkText') }}
            </el-link>
          </template>
          <!-- lookup action -->
          <template v-else-if="col.type === 'lookup'">
            <LookupPreview
              class="sub-table-lookup-preview"
              :model-value="scope.row[col.field]"
              :label="''"
              :placeholder="col.placeholder || 'Click to search'"
              :search-fields="col.props?.searchFields || []"
              :display-fields="col.props?.displayFields || []"
              :selected-display-field="col.props?.selectedDisplayField || ''"
              :filter-conditions="effectiveLookupFilterForCell(col, scope.row)"
              :view-fields="col.props?.viewFields || []"
              :field-defs="col.props?.fieldDefs || []"
              :ensure-mock-fields="ensureMockFieldsForColumn(col)"
              :show-backfill-view="previewLookupCompact ? false : (col.props?.showBackfillView !== false)"
              :readonly="!editable || (col.field === 'assignee' && assigneeCellLocked(scope.row))"
              :multiple="col.props?.multiple === true"
              @update:model-value="(val) => onLookupCellChange(col, scope.$index, val)"
            />
          </template>
          <!-- default -->
          <span v-else>{{ scope.row[col.field] ?? '-' }}</span>
        </template>
      </el-table-column>

      <el-table-column
        v-if="canEdit || canDelete"
        :label="t('common.operation')"
        width="120"
      >
        <template #default="scope">
          <el-button
            v-if="canEdit"
            link
            type="primary"
            size="small"
            @click="openEditDialog(scope.$index)"
          >
            {{ t('common.edit') }}
          </el-button>
          <el-button
            v-if="canDelete"
            link
            type="danger"
            size="small"
            @click="handleDelete(scope.$index)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty
          :description="t('common.noData')"
          :image-size="40"
        />
      </template>
    </el-table>
    </div>

    <div
      v-if="previewShowFormBelow"
      ref="inlineFormBelowRef"
      class="preview-inline-form-below"
    >
      <el-divider content-position="left">
        {{ t('subTableView.assigneeFormBelowDivider') }}
      </el-divider>
      <div class="preview-inline-form-body">
        <form-create
          v-if="effectiveInlineFormRule.length && !hideInlineFormForRowDialog"
          v-model="previewInlineFormData"
          locale="en"
          :rule="effectiveInlineFormRule"
          :option="previewInlineFormOption"
        />
        <el-empty
          v-else
          :description="t('subTable.noFormDesign')"
          :image-size="48"
        />
        <div
          v-if="effectiveInlineFormRule.length"
          class="inline-form-actions"
        >
          <el-button
            type="primary"
            :disabled="!editable"
            @click="handleInlineFormBelowSave"
          >
            {{ t('common.save') }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div
      v-if="config.pagination && total > (config.pageSize || 10)"
      class="pagination-wrapper"
    >
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="config.pageSize || 10"
        :total="total"
        layout="total, prev, pager, next"
        size="small"
        @current-change="handlePageChange"
      />
    </div>

    <Teleport
      to="body"
    >
      <SubTableFormDialog
        :visible="linkFormDialogVisible"
        :title="linkFormDialogTitle"
        mode="edit"
        :read-only="!editable"
        :hide-footer="!editable"
        :initial-data="linkFormInitialData"
        :rule="linkFormRule"
        :option="linkFormOption"
        @update:visible="linkFormDialogVisible = $event"
        @save="handleLinkFormSave"
      />
    </Teleport>

    <Teleport
      v-if="!previewDialogHost"
      to="body"
    >
      <SubTableFormDialog
        :visible="formDialogVisible"
        :title="config.title || t('subTable.defaultTitle')"
        :mode="dialogMode"
        :initial-data="dialogInitialData"
        :rule="formRule"
        :option="formOption"
        :columns="dialogColumns"
        @update:visible="formDialogVisible = $event"
        @save="handleDialogSave"
      />

      <SubTableAddDialog
        :visible="simpleDialogVisible"
        :columns="dialogColumns"
        :mode="dialogMode"
        :initial-data="dialogInitialData"
        @update:visible="simpleDialogVisible = $event"
        @save="handleDialogSave"
      />
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Loading, Document, Download, Upload } from '@element-plus/icons-vue'
import SubTableAddDialog from './SubTableAddDialog.vue'
import SubTableFormDialog from './SubTableFormDialog.vue'
import LookupPreview from './LookupPreview.vue'
import { getFilenameFromUrl, isUploadColumn } from './uploadFieldUtils'
import { PREVIEW_SUBTABLE_DIALOG_KEY, PREVIEW_MY_REQUESTS_ACTIVE_KEY } from './previewSubTableDialog'
import dayjs from 'dayjs'
import type { SubTableConfig, ColumnConfig } from '@/composables/designerSubTableField/types'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'
import { useSubTableData } from '@/composables/designerSubTableField/useSubTableData'
import { useSubTableUploadCells } from '@/composables/designerSubTableField/useSubTableUploadCells'
import { useSubTableInlineForm } from '@/composables/designerSubTableField/useSubTableInlineForm'
import { useSubTableLinkForm } from '@/composables/designerSubTableField/useSubTableLinkForm'
import { useSubTableRowDialog } from '@/composables/designerSubTableField/useSubTableRowDialog'
import {
  buildPreviewAutofillModelValue,
  effectiveLookupFilterConditionsForRow,
  normalizeLookupRow,
  type LookupCascadeConfig,
  type LookupDerivedFrom,
} from '@/utils/lookupCascade'
import { parseLookupConfig } from '@/utils/formPreview'

const { t } = useI18n()
const previewDialogHost = inject(PREVIEW_SUBTABLE_DIALOG_KEY, null)
const previewMyRequestsActive = inject(PREVIEW_MY_REQUESTS_ACTIVE_KEY, undefined)
const hideInlineFormForRowDialog = computed(
  () => previewDialogHost?.rowDialogOpen.value === true,
)

const props = withDefaults(defineProps<{
  config: SubTableConfig
  modelValue?: any[]
  editable?: boolean
  /**
   * 子表逐操作权限（设计器右侧属性面板配置，存于组件 rule.props）。
   * 缺省/undefined => 视为放开（回退 editable，历史表单三项全开）；显式 false => 隐藏该操作。editable 总开关仍优先。
   */
  allowAdd?: boolean
  allowEdit?: boolean
  allowDelete?: boolean
  foreignKeyValue?: string | number
  /** Form-create rule from the sub-table form designer */
  formRule?: any[]
  /** Form-create option from the sub-table form designer */
  formOption?: any
  /** Form Preview: compact lookup cells (My Requests — summary mode) */
  previewLookupCompact?: boolean
  /** Form Preview: show read-only form below table (assignee — form below table) */
  previewShowFormBelow?: boolean
  /** Form Preview (To Do): Link Form Details scrolls to inline form instead of opening modal */
  previewLinkFormScrollToInline?: boolean
  /** Form Preview: override schema for form-below strip (linkForm → target sub-table) */
  previewInlineFormRule?: any[]
  previewInlineFormOption?: any
  /** Form Preview: main form data for FK fill */
  primaryFormData?: Record<string, unknown>
  functionUnitId?: number
  primaryTableDisplayName?: string
  primaryTableId?: number | null
  parentTablesById?: Record<number, { fieldDefinitions: BindingFieldDefinition[] }>
  previewTableBindings?: Array<{ tableId?: number | null; bindingType?: string }>
}>(), {
  // Per-op switches default OPEN. Without an explicit default, Vue casts an *absent*
  // Boolean prop to false (not undefined), which would hide Add/Edit/Delete at every
  // call site that omits the prop — defeating the “缺省视为放开” contract above.
  allowAdd: true,
  allowEdit: true,
  allowDelete: true
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: any[]): void
  (e: 'update:primaryFormData', value: Record<string, unknown>): void
  (e: 'add', row: any): void
  (e: 'edit', row: any, index: number): void
  (e: 'delete', row: any, index: number): void
}>()

// 计算属性：是否可编辑（Form Preview My Requests 全局只读覆盖 props.editable）
const editable = computed(() => {
  if (previewMyRequestsActive?.value === true) return false
  return props.editable !== false
})

// 子表逐操作权限：editable 总开关优先，逐项标志缺省视为放开（历史数据三项全开）
const canAdd = computed(() => editable.value && props.allowAdd !== false)
const canEdit = computed(() => editable.value && props.allowEdit !== false)
const canDelete = computed(() => editable.value && props.allowDelete !== false)

// MI 分派行级互斥：该行选了角色（role_code / bu_code 有值）时，assignee 分派字段应只读，
// 反之亦然（一行只能一种分派方式）。约定字段名 assignee / role_code / bu_code。
function hasVal(v: unknown): boolean {
  if (v == null) return false
  if (Array.isArray(v)) return v.length > 0
  if (typeof v === 'object') return Object.keys(v as object).length > 0
  return String(v).trim() !== ''
}
function assigneeCellLocked(row: Record<string, any>): boolean {
  return hasVal(row?.role_code) || hasVal(row?.bu_code)
}

function lookupCascadeConfigForColumn(col: ColumnConfig): LookupCascadeConfig {
  const fromProps = col.props?.derivedFrom as LookupDerivedFrom | undefined
  if (fromProps) {
    return {
      filterConditions: (col.props?.filterConditions as LookupCascadeConfig['filterConditions']) || [],
      derivedFrom: fromProps,
    }
  }
  const cfg = parseLookupConfig(
    typeof col.props?.lookupConfig === 'string' ? col.props.lookupConfig : JSON.stringify(col.props?.lookupConfig || {}),
  )
  return {
    filterConditions: Array.isArray(cfg.filterConditions)
      ? cfg.filterConditions
      : ((col.props?.filterConditions as LookupCascadeConfig['filterConditions']) || []),
    derivedFrom: cfg.derivedFrom,
  }
}

function effectiveLookupFilterForCell(col: ColumnConfig, row: Record<string, unknown>) {
  const cfg = lookupCascadeConfigForColumn(col)
  const base = (col.props?.filterConditions as LookupCascadeConfig['filterConditions']) || cfg.filterConditions || []
  return effectiveLookupFilterConditionsForRow(base, cfg, row)
}

function ensureMockFieldsForColumn(col: ColumnConfig): string[] {
  const fields = new Set<string>()
  const cfg = lookupCascadeConfigForColumn(col)
  for (const j of cfg.derivedFrom?.joins || []) {
    if (j.toColumn) fields.add(j.toColumn)
  }
  for (const dep of displayColumns.value) {
    if (dep.type !== 'lookup' || dep.field === col.field) continue
    const depCfg = lookupCascadeConfigForColumn(dep)
    if (depCfg.derivedFrom?.parentField !== col.field) continue
    for (const j of depCfg.derivedFrom.joins || []) {
      if (j.fromColumn) fields.add(j.fromColumn)
    }
  }
  return Array.from(fields)
}

function onLookupCellChange(col: ColumnConfig, rowIndex: number, value: unknown) {
  if (!editable.value || rowIndex < 0 || rowIndex >= tableData.value.length) return
  const next = tableData.value.map((r, i) => (i === rowIndex ? { ...r } : r))
  const row = next[rowIndex]
  const isMulti = col.props?.multiple === true
  // Multi LOOKUP stores full row object(s); single stores row object (Portal parity).
  row[col.field] = isMulti ? (Array.isArray(value) ? value : []) : normalizeLookupRow(value)
  const parentRow = isMulti ? null : normalizeLookupRow(value)
  for (const dep of displayColumns.value) {
    if (dep.type !== 'lookup' || dep.field === col.field) continue
    const depCfg = lookupCascadeConfigForColumn(dep)
    if (depCfg.derivedFrom?.parentField !== col.field) continue
    if (depCfg.derivedFrom.derivedMode !== 'autofill') continue
    const depMulti = dep.props?.multiple === true
    row[dep.field] = parentRow
      ? buildPreviewAutofillModelValue(depCfg, parentRow, {
        searchFields: (dep.props?.searchFields as string[]) || [],
        selectedDisplayField: String(dep.props?.selectedDisplayField || ''),
        displayFields: (dep.props?.displayFields as string[]) || [],
        multiple: depMulti,
      })
      : (depMulti ? [] : null)
  }
  tableData.value = next
  emit('update:modelValue', next)
}

// 判断列中是否存在 FILE 类型的字段（有 FILE 列时隐藏 Import 按钮）
const hasFileColumn = computed(() => {
  return displayColumns.value.some(col => col.type === 'upload')
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

    const rows = parseCSV(text)
    if (rows.length < 1) return

    // 第一行是 header，映射到 displayColumns 的 field
    const headers = rows[0]
    const colFieldSet = new Set(displayColumns.value.map(c => c.field))
    const headerToField = new Map<number, string>()
    for (let i = 0; i < headers.length; i++) {
      const h = headers[i]
      if (colFieldSet.has(h)) {
        headerToField.set(i, h)
      }
    }

    // 从第二行开始解析数据
    const newRows: any[] = []
    for (let r = 1; r < rows.length; r++) {
      const row: Record<string, unknown> = {}
      for (const [colIdx, field] of headerToField.entries()) {
        const val = rows[r][colIdx] || ''
        const col = displayColumns.value.find(c => c.field === field)
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
      tableData.value = [...tableData.value, ...newRows]
      total.value = tableData.value.length
      emit('update:modelValue', [...tableData.value])
    }
  }
  reader.readAsText(file)
  // Reset input so the same file can be re-imported
  input.value = ''
}

// Normalize a date/datetime cell to ISO 8601 for export.
// Handles the app's stored formats (YYYY-MM-DD / YYYY-MM-DD HH:mm:ss), Java LocalDateTime
// arrays ([y,mo,d,h,mi,s]), epoch numbers, Date objects and locale strings (e.g. 7/7/2026).
// Unparseable values are passed through untouched rather than dropped.
function toIsoDateCell(raw: unknown, withTime: boolean): string {
  if (raw == null || raw === '') return ''
  const outFmt = withTime ? 'YYYY-MM-DDTHH:mm:ss' : 'YYYY-MM-DD'
  if (Array.isArray(raw)) {
    const [y, mo = 1, d = 1, h = 0, mi = 0, s = 0] = raw as number[]
    const dt = dayjs(new Date(y, mo - 1, d, h, mi, s))
    return dt.isValid() ? dt.format(outFmt) : String(raw)
  }
  const dt = dayjs(raw as string | number | Date)
  return dt.isValid() ? dt.format(outFmt) : String(raw)
}

// A lookup cell holds either the primary-key scalar directly or a full row snapshot.
// Export the PK scalar so re-import can rehydrate the cell via the lookup preview.
function lookupExportScalar(col: ColumnConfig, raw: unknown): string {
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
  const cols = displayColumns.value.filter(c => c.type !== 'linkForm')
  const headers = cols.map(c => c.field)
  // BOM for Excel UTF-8 compatibility
  let csv = '\uFEFF' + headers.map(h => `"${h.replace(/"/g, '""')}"`).join(',') + '\n'
  // Append data rows
  for (const row of tableData.value) {
    const values = cols.map(c => {
      let v: unknown
      if (c.type === 'lookup') v = lookupExportScalar(c, row[c.field])
      else if (c.type === 'date') v = toIsoDateCell(row[c.field], false)
      else if (c.type === 'datetime') v = toIsoDateCell(row[c.field], true)
      else v = row[c.field]
      if (v == null || v === '') return ''
      return `"${String(v).replace(/"/g, '""')}"`
    })
    csv += values.join(',') + '\n'
  }
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${props.config.title || 'subtable'}_export.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

// 表格数据模型：行数据、分页、上传文件名缓存、显示列
const {
  loading,
  tableData,
  currentPage,
  total,
  uploadNames,
  displayColumns,
  handlePageChange,
  exposed,
} = useSubTableData(props)

// 单元格富文本净化 + 上传文件展示/下载
const {
  downloadingKeys,
  sanitizeHtml,
  resolveRowUploadUrl,
  rememberUploadNamesForRow,
  downloadFile,
} = useSubTableUploadCells({ displayColumns, uploadNames, t })

// Form Preview：表格下方只读内联表单
const {
  previewInlineFormData,
  inlineFormBelowRef,
  effectiveInlineFormRule,
  previewInlineFormOption,
  handleInlineFormBelowSave,
} = useSubTableInlineForm({ props, editable, t })

// linkForm 关联表单弹层
const {
  linkFormDialogVisible,
  linkFormDialogTitle,
  linkFormInitialData,
  linkFormRule,
  linkFormOption,
  openLinkFormDialog,
  handleLinkFormSave,
} = useSubTableLinkForm({ props, editable, previewInlineFormData, inlineFormBelowRef, t })

// 行的添加/编辑弹层编排 + 增删改
const {
  formDialogVisible,
  simpleDialogVisible,
  dialogMode,
  dialogInitialData,
  dialogColumns,
  handleAdd,
  openEditDialog,
  handleDialogSave,
  handleDelete,
} = useSubTableRowDialog({
  props,
  emit,
  displayColumns,
  tableData,
  total,
  previewDialogHost,
  // 循环依赖破环：弹层编排器需要关闭 linkForm 弹层，但 linkForm 编排器无需感知行弹层
  setLinkFormDialogVisible: (value: boolean) => { linkFormDialogVisible.value = value },
  rememberUploadNamesForRow,
  t,
})

// 暴露方法
defineExpose(exposed)
</script>

<style lang="scss" scoped>
.sub-table-field {
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  padding: 12px;
  background: #fafafa;

  .sub-table-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    position: relative;
    z-index: 2;

    .title {
      font-weight: 500;
      font-size: 14px;
      color: #303133;
    }
  }

  .pagination-wrapper {
    margin-top: 12px;
    display: flex;
    justify-content: flex-end;
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

  .sub-table-lookup-preview {
    min-width: 220px;
    margin-bottom: 0;

    :deep(.lookup-label-text) {
      display: none;
    }
  }

  .file-download-link {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    color: var(--el-color-primary);
    cursor: pointer;
    font-size: 12px;
    max-width: 100%;
    word-break: break-all;

    &:hover {
      text-decoration: underline;
    }

    &.downloading {
      color: #909399;
      cursor: wait;
    }
  }

  .no-file {
    color: #909399;
  }

  .preview-inline-form-below {
    margin-top: 12px;
    border-top: 1px dashed var(--el-border-color, #dcdfe6);
    background: var(--el-fill-color-lighter, #fafafa);
    border-radius: 0 0 4px 4px;
    margin-left: -12px;
    margin-right: -12px;
    margin-bottom: -12px;
    padding: 0 12px 4px;
  }

  .preview-inline-form-body {
    max-height: 280px;
    overflow-y: auto;
    padding-bottom: 8px;

    :deep(.form-create) {
      width: 100%;
    }

    :deep(.el-card) {
      margin-bottom: 10px;
    }
  }

  .inline-form-actions {
    margin-top: 12px;
    display: flex;
    justify-content: flex-end;
    padding-bottom: 8px;
  }
}
</style>
