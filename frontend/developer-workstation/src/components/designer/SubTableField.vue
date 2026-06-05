<template>
  <div class="sub-table-field">
    <div class="sub-table-header">
      <span class="title">{{ config.title || t('subTable.defaultTitle') }}</span>
      <div
        v-if="editable"
        class="actions"
      >
        <el-button
          type="primary"
          native-type="button"
          size="small"
          @click.stop="handleAdd"
        >
          <el-icon><Plus /></el-icon> {{ t('common.add') }}
        </el-button>
      </div>
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
              :filter-conditions="col.props?.filterConditions || []"
              :view-fields="col.props?.viewFields || []"
              :field-defs="col.props?.fieldDefs || []"
              :show-backfill-view="previewLookupCompact ? false : (col.props?.showBackfillView !== false)"
              :readonly="!editable"
            />
          </template>
          <!-- default -->
          <span v-else>{{ scope.row[col.field] ?? '-' }}</span>
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
            {{ t('common.edit') }}
          </el-button>
          <el-button
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
import { ref, computed, watch, inject, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Loading, Document } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import SubTableAddDialog from './SubTableAddDialog.vue'
import SubTableFormDialog from './SubTableFormDialog.vue'
import LookupPreview from './LookupPreview.vue'
import { mergeFormRowWithSeed, type DialogColumn } from './subTableAddDialogHelpers'
import {
  alignUploadFieldsToColumns,
  getFilenameFromUrl,
  isUploadColumn,
  normalizeSubTableColumns,
  normalizeUploadFieldsInRow,
  resolveFileFetchUrl,
  resolveUploadCellUrl,
} from './uploadFieldUtils'
import { collectUploadRulesFromTree } from '@/utils/formDesigner'
import { PREVIEW_SUBTABLE_DIALOG_KEY, PREVIEW_MY_REQUESTS_ACTIVE_KEY } from './previewSubTableDialog'
import { functionUnitApi } from '@/api/functionUnit'
import {
  buildRowAddContext,
  prepareSubTableAddRow,
  applyFkPresentationToDialogColumns,
  toFieldFkMetas,
} from '@/utils/subTableRowRuntime'

const { t } = useI18n()
const previewDialogHost = inject(PREVIEW_SUBTABLE_DIALOG_KEY, null)
const previewMyRequestsActive = inject(PREVIEW_MY_REQUESTS_ACTIVE_KEY, undefined)
const hideInlineFormForRowDialog = computed(
  () => previewDialogHost?.rowDialogOpen.value === true,
)

function sanitizeHtml(html: string): string {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'u', 's', 'ol', 'ul', 'li',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'a', 'img', 'table', 'tr', 'td', 'th', 'span', 'div'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'class', 'style', 'target', 'rel'],
  })
}

// 列配置接口
interface ColumnConfig {
  field: string
  label: string
  type?: 'input' | 'number' | 'date' | 'switch' | 'text' | 'textarea' | 'select' | 'radio' | 'checkbox' | 'datetime' | 'upload' | 'user' | 'department' | 'password' | 'timerange' | 'treeselect' | 'colorPicker' | 'rate' | 'slider' | 'tree' | 'editor' | 'signature' | 'transfer' | 'cascader' | 'linkForm' | 'lookup'
  width?: number
  minWidth?: number
  required?: boolean
  placeholder?: string
  options?: Array<{ label: string; value: any }>
  props?: Record<string, any>
}

// 子表配置接口
interface SubTableConfig {
  title?: string
  bindingId?: number
  tableId?: number
  columns: ColumnConfig[]
  fieldDefinitions?: import('@/utils/subTableRowRuntime').BindingFieldDefinition[]
  bindingLinkMode?: 'structuralFk' | 'miParticipantRow' | string
  bindingForeignKeyField?: string | null
  pagination?: boolean
  pageSize?: number
  maxHeight?: number
}

const props = defineProps<{
  config: SubTableConfig
  modelValue?: any[]
  editable?: boolean
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
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any[]): void
  (e: 'update:primaryFormData', value: Record<string, unknown>): void
  (e: 'add', row: any): void
  (e: 'edit', row: any, index: number): void
  (e: 'delete', row: any, index: number): void
}>()

const loading = ref(false)
const tableData = ref<any[]>([])
const currentPage = ref(1)
const total = ref(0)
const uploadNames = ref<Record<string, string>>({})
const downloadingKeys = ref<Record<string, boolean>>({})

// Dialog state - 使用两个独立的 dialog 来避免状态冲突
const formDialogVisible = ref(false)
const simpleDialogVisible = ref(false)
const linkFormDialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const editingRowIndex = ref<number | null>(null)
const dialogInitialData = ref<Record<string, any> | undefined>(undefined)
const dialogAddColumns = ref<DialogColumn[] | null>(null)
const linkFormDialogTitle = ref('')
const linkFormInitialData = ref<Record<string, any> | undefined>(undefined)
const linkFormRule = ref<any[]>([])
const linkFormOption = ref<any>({})

const previewInlineFormData = ref<Record<string, unknown>>({})
const inlineFormBelowRef = ref<HTMLElement | null>(null)
const effectiveInlineFormRule = computed(
  () => (props.previewInlineFormRule?.length ? props.previewInlineFormRule : props.formRule) || [],
)
const effectiveInlineFormOptionSource = computed(
  () => props.previewInlineFormOption ?? props.formOption,
)
const previewInlineFormOption = computed(() => {
  const saved = { ...((effectiveInlineFormOptionSource.value || {}) as Record<string, unknown>) }
  delete saved.title
  return {
    showMsg: true,
    form: {
      labelPosition: 'left',
      labelWidth: '140px',
      disabled: true,
    },
    language: {
      en: {
        clickToUpload: t('form.clickToUpload'),
      },
    },
    ...saved,
    resetBtn: false,
    submitBtn: false,
  }
})

// 计算属性：是否可编辑（Form Preview My Requests 全局只读覆盖 props.editable）
const editable = computed(() => {
  if (previewMyRequestsActive?.value === true) return false
  return props.editable !== false
})

// 计算属性：显示的列（FILE / file 字段归一为 upload，便于文件名展示与下载）
const displayColumns = computed(() =>
  normalizeSubTableColumns(props.config.columns || [], tableData.value),
)

// 是否使用 form-create 对话框（当有 formRule 时优先使用）
const hasFormRule = computed(() => props.formRule && props.formRule.length > 0)

// 将 ColumnConfig 转换为 DialogColumn（兼容 SubTableAddDialog 的类型）
const dialogColumns = computed<DialogColumn[]>(() => {
  const source = dialogAddColumns.value ?? displayColumns.value
  return source.map(col => {
    // 将旧的 'input' type 映射到 'text'
    const type = col.type === 'input' ? 'text' : (col.type as DialogColumn['type'])
    return {
      field: col.field,
      label: col.label,
      type,
      required: col.required,
      placeholder: col.placeholder,
      minWidth: col.minWidth,
      options: col.options,
      props: col.props,
      readonly: (col as { readonly?: boolean }).readonly,
    }
  })
})

// 监听 modelValue 变化
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    tableData.value = [...newVal]
    total.value = newVal.length
    const nextNames: Record<string, string> = {}
    newVal.forEach((row: Record<string, unknown>, rowIndex: number) => {
      for (const col of displayColumns.value) {
        if (!isUploadColumn(col, row[col.field])) continue
        const url = resolveUploadCellUrl(row[col.field])
        if (!url) continue
        nextNames[`${rowIndex}_${col.field}`] = getFilenameFromUrl(String(url))
      }
    })
    uploadNames.value = nextNames
  }
}, { immediate: true, deep: true })

function resolveRowUploadUrl(row: Record<string, unknown>, col: ColumnConfig): string | null {
  return resolveUploadCellUrl(row[col.field])
}

function rememberUploadNamesForRow(rowIndex: number, rowData: Record<string, any>) {
  for (const col of displayColumns.value) {
    if (!isUploadColumn(col, rowData[col.field])) continue
    const url = resolveUploadCellUrl(rowData[col.field])
    if (!url) continue
    const target = col.props?.fileNameTargetField as string | undefined
    const saved = (target && rowData[target] != null ? String(rowData[target]) : undefined)
      || getFilenameFromUrl(String(url))
    uploadNames.value = { ...uploadNames.value, [`${rowIndex}_${col.field}`]: saved }
  }
}

async function downloadFile(
  url: string,
  savedName: string | undefined,
  rowIndex: number,
  field: string,
) {
  if (!url) return
  const key = `${rowIndex}_${field}`
  if (downloadingKeys.value[key]) return

  const filename = getFilenameFromUrl(url, savedName)
  const fetchUrl = resolveFileFetchUrl(url)
  downloadingKeys.value = { ...downloadingKeys.value, [key]: true }
  const msg = ElMessage({ message: t('common.downloading'), type: 'info', duration: 0 })

  try {
    const response = await fetch(fetchUrl, { credentials: 'include' })
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

// 添加/编辑行 — preview 走 FormDesigner 顶层弹层，避免嵌在 Preview Dialog 内被遮罩挡住
async function openRowDialog(mode: 'add' | 'edit', index?: number) {
  dialogMode.value = mode
  editingRowIndex.value = mode === 'edit' && index != null ? index : null
  dialogAddColumns.value = null

  if (mode === 'add') {
    const fkMetas = toFieldFkMetas(props.config.fieldDefinitions)
    const baseCols = fkMetas.length
      ? applyFkPresentationToDialogColumns(
          displayColumns.value.map(col => ({
            field: col.field,
            label: col.label,
            type: col.type === 'input' ? 'text' : (col.type as DialogColumn['type']),
            required: col.required,
            placeholder: col.placeholder,
            options: col.options,
            props: col.props,
          })),
          fkMetas,
          props.config.fieldDefinitions,
        ).visibleColumns
      : undefined

    const rowAddContext = buildRowAddContext(
      props.primaryFormData ?? {},
      props.previewTableBindings,
    )
    try {
      const result = await prepareSubTableAddRow({
        columns: baseCols ?? dialogColumns.value,
        fieldDefinitions: props.config.fieldDefinitions,
        rowAddContext,
        tableId: props.config.tableId,
        tableDisplayName: props.config.title,
        primaryTableDisplayName: props.primaryTableDisplayName,
        primaryTableId: props.primaryTableId,
        parentTablesById: props.parentTablesById,
        functionUnitId: props.functionUnitId != null ? String(props.functionUnitId) : undefined,
        autoEnsurePrimaryRecord: props.primaryFormData != null,
        bindingLinkMode: props.config.bindingLinkMode,
        bindingForeignKeyField: props.config.bindingForeignKeyField,
        allocatePrimaryKeys:
          props.functionUnitId != null && props.config.tableId != null
            ? async (payload) => {
                const res = await functionUnitApi.allocatePrimaryKeys(props.functionUnitId!, payload)
                return res?.data?.values ?? []
              }
            : undefined,
        t,
      })
      if (!result.ok) {
        ElMessage.warning(result.message)
        return
      }
      if (result.primaryFormDataPatch && Object.keys(result.primaryFormDataPatch).length > 0) {
        emit('update:primaryFormData', result.primaryFormDataPatch)
      }
      dialogAddColumns.value = result.dialogColumns
      dialogInitialData.value = result.initialRow as Record<string, any>
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('common.error')
      ElMessage.error(message || t('common.error'))
      return
    }
  } else {
    dialogInitialData.value =
      index != null ? { ...tableData.value[index] } : undefined
  }

  if (previewDialogHost) {
    linkFormDialogVisible.value = false
    previewDialogHost.openRowDialog({
      mode,
      title: props.config.title || t('subTable.defaultTitle'),
      initialData: dialogInitialData.value,
      formRule: props.formRule,
      formOption: props.formOption,
      columns: dialogColumns.value,
      onSave: (rowData) => handleDialogSave(rowData),
    })
    return
  }

  linkFormDialogVisible.value = false
  formDialogVisible.value = false
  simpleDialogVisible.value = false
  window.setTimeout(() => {
    if (hasFormRule.value) {
      formDialogVisible.value = true
    } else {
      simpleDialogVisible.value = true
    }
  }, 0)
}

function handleAdd() {
  void openRowDialog('add')
}

// 编辑行 — 打开 Dialog 并预填数据
function openEditDialog(index: number) {
  void openRowDialog('edit', index)
}

function linkFormTitleTableName(raw: string): string {
  return String(raw || '')
    .trim()
    .replace(/^ADD\s*\+\s*/i, '')
    .trim()
}

function openLinkFormDialog(col: ColumnConfig, row: Record<string, any>) {
  if (props.previewLinkFormScrollToInline) {
    previewInlineFormData.value = { ...row }
    nextTick(() => {
      inlineFormBelowRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
    return
  }

  const raw = col.props?.boundSubTableName || props.config.title || ''
  const tableName = linkFormTitleTableName(raw)
  linkFormDialogTitle.value = tableName
    ? t('linkForm.dialogTitleAddTable', { tableName })
    : t('linkForm.linkedForm')
  linkFormInitialData.value = { ...row }
  linkFormRule.value = col.props?.formRule || props.formRule || []
  const opt = { ...((col.props?.formOption || props.formOption || {}) as Record<string, unknown>) }
  delete opt.title
  if (!editable.value) {
    opt.form = {
      ...((opt.form as Record<string, unknown>) || {}),
      disabled: true,
    }
  }
  linkFormOption.value = opt
  linkFormDialogVisible.value = true
}

function handleLinkFormSave(rowData: Record<string, any>) {
  linkFormDialogVisible.value = false
  linkFormInitialData.value = rowData
}

// Dialog 保存回调
function handleDialogSave(rowData: Record<string, any>) {
  const savedRow = mergeFormRowWithSeed(dialogInitialData.value, rowData)
  if (hasFormRule.value && props.formRule?.length) {
    const uploadRuleFields = collectUploadRulesFromTree(props.formRule).map((r) => r.field)
    alignUploadFieldsToColumns(savedRow, displayColumns.value, uploadRuleFields)
  }
  normalizeUploadFieldsInRow(savedRow, displayColumns.value)
  if (dialogMode.value === 'add') {
    const rowIndex = tableData.value.length
    tableData.value.push(savedRow)
    rememberUploadNamesForRow(rowIndex, savedRow)
    emit('add', savedRow)
  } else if (dialogMode.value === 'edit' && editingRowIndex.value !== null) {
    tableData.value[editingRowIndex.value] = savedRow
    rememberUploadNamesForRow(editingRowIndex.value, savedRow)
    emit('edit', savedRow, editingRowIndex.value)
  }
  total.value = tableData.value.length
  emit('update:modelValue', [...tableData.value])
  formDialogVisible.value = false
  simpleDialogVisible.value = false
}

// 删除行
async function handleDelete(index: number) {
  await ElMessageBox.confirm(t('subTable.deleteConfirm'), t('common.confirmTitle'), { type: 'warning' })
  const deletedRow = tableData.value[index]
  tableData.value.splice(index, 1)
  total.value = tableData.value.length
  emit('update:modelValue', [...tableData.value])
  emit('delete', deletedRow, index)
  ElMessage.success(t('common.deleteSuccess'))
}

// 分页变化
function handlePageChange(page: number) {
  currentPage.value = page
}

// 暴露方法
defineExpose({
  getData: () => tableData.value,
  setData: (data: any[]) => {
    tableData.value = [...data]
    total.value = data.length
  },
  refresh: () => {}
})
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
  }
}
</style>
