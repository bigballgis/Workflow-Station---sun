<template>
  <div class="sub-table-field">
    <div class="sub-table-header">
      <span class="title">{{ config.title || t('subTable.defaultTitle') }}</span>
      <div class="actions" v-if="editable">
        <el-button type="primary" size="small" @click="handleAdd">
          <el-icon><Plus /></el-icon> {{ t('common.add') }}
        </el-button>
      </div>
    </div>

    <el-table
      :data="tableData"
      size="small"
      border
      v-loading="loading"
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
          <!-- colorPicker -->
          <template v-if="col.type === 'colorPicker'">
            <span v-if="scope.row[col.field]" class="color-swatch" :style="{ backgroundColor: scope.row[col.field] }" :title="scope.row[col.field]" />
            <span v-else>-</span>
          </template>
          <!-- editor -->
          <template v-else-if="col.type === 'editor'">
            <span v-if="scope.row[col.field]" v-html="sanitizeHtml(scope.row[col.field])" class="editor-preview" />
            <span v-else>-</span>
          </template>
          <!-- signature -->
          <template v-else-if="col.type === 'signature'">
            <img v-if="scope.row[col.field]" :src="scope.row[col.field]" class="signature-preview" alt="Signature" />
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
            <el-rate v-if="scope.row[col.field] != null" :model-value="Number(scope.row[col.field])" :max="col.props?.max || 5" disabled style="display: inline-flex;" />
            <span v-else>-</span>
          </template>
          <!-- slider -->
          <template v-else-if="col.type === 'slider'">
            <el-slider v-if="scope.row[col.field] != null" :model-value="Number(scope.row[col.field])" :min="col.props?.min ?? 0" :max="col.props?.max ?? 100" disabled style="width: 100%; padding: 0 10px;" />
            <span v-else>-</span>
          </template>
          <!-- password -->
          <template v-else-if="col.type === 'password'">
            <span>******</span>
          </template>
          <!-- link form action -->
          <template v-else-if="col.type === 'linkForm'">
            <el-link type="primary" :underline="false" @click.stop="openLinkFormDialog(col, scope.row)">
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
              :show-backfill-view="col.props?.showBackfillView !== false"
              readonly
            />
          </template>
          <!-- default -->
          <span v-else>{{ scope.row[col.field] ?? '-' }}</span>
        </template>
      </el-table-column>

      <el-table-column :label="t('common.operation')" width="120" v-if="editable">
        <template #default="scope">
          <el-button link type="primary" size="small" @click="openEditDialog(scope.$index)">{{ t('common.edit') }}</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(scope.$index)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty :description="t('common.noData')" :image-size="40" />
      </template>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper" v-if="config.pagination && total > (config.pageSize || 10)">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="config.pageSize || 10"
        :total="total"
        layout="total, prev, pager, next"
        size="small"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 使用表单设计器的 form-create 规则渲染对话框 (优先使用) -->
    <SubTableFormDialog
      v-if="formDialogVisible"
      :visible="formDialogVisible"
      :title="config.title || t('subTable.defaultTitle')"
      :mode="dialogMode"
      :initialData="dialogInitialData"
      :rule="formRule"
      :option="formOption"
      @update:visible="formDialogVisible = $event"
      @save="handleDialogSave"
    />

    <!-- 备用的简单对话框（当没有 form-create 规则时） -->
    <SubTableAddDialog
      v-else-if="simpleDialogVisible"
      :visible="simpleDialogVisible"
      :columns="dialogColumns"
      :mode="dialogMode"
      :initialData="dialogInitialData"
      @update:visible="simpleDialogVisible = $event"
      @save="handleDialogSave"
    />

    <SubTableFormDialog
      v-if="linkFormDialogVisible"
      :visible="linkFormDialogVisible"
      :title="linkFormDialogTitle"
      mode="edit"
      :initialData="linkFormInitialData"
      :rule="linkFormRule"
      :option="linkFormOption"
      @update:visible="linkFormDialogVisible = $event"
      @save="handleLinkFormSave"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import SubTableAddDialog from './SubTableAddDialog.vue'
import SubTableFormDialog from './SubTableFormDialog.vue'
import LookupPreview from './LookupPreview.vue'
import type { DialogColumn } from './subTableAddDialogHelpers'

const { t } = useI18n()

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
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any[]): void
  (e: 'add', row: any): void
  (e: 'edit', row: any, index: number): void
  (e: 'delete', row: any, index: number): void
}>()

const loading = ref(false)
const tableData = ref<any[]>([])
const currentPage = ref(1)
const total = ref(0)

// Dialog state - 使用两个独立的 dialog 来避免状态冲突
const formDialogVisible = ref(false)
const simpleDialogVisible = ref(false)
const linkFormDialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const editingRowIndex = ref<number | null>(null)
const dialogInitialData = ref<Record<string, any> | undefined>(undefined)
const linkFormDialogTitle = ref('')
const linkFormInitialData = ref<Record<string, any> | undefined>(undefined)
const linkFormRule = ref<any[]>([])
const linkFormOption = ref<any>({})

// 计算属性：是否可编辑
const editable = computed(() => props.editable !== false)

// 计算属性：显示的列
const displayColumns = computed(() => props.config.columns || [])

// 是否使用 form-create 对话框（当有 formRule 时优先使用）
const hasFormRule = computed(() => props.formRule && props.formRule.length > 0)

// 将 ColumnConfig 转换为 DialogColumn（兼容 SubTableAddDialog 的类型）
const dialogColumns = computed<DialogColumn[]>(() => {
  return displayColumns.value.map(col => {
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
    }
  })
})

// 监听 modelValue 变化
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    tableData.value = [...newVal]
    total.value = newVal.length
  }
}, { immediate: true, deep: true })

// 添加行 — 打开 Dialog
function handleAdd() {
  dialogMode.value = 'add'
  dialogInitialData.value = undefined
  editingRowIndex.value = null
  if (hasFormRule.value) {
    simpleDialogVisible.value = false
    formDialogVisible.value = true
  } else {
    formDialogVisible.value = false
    simpleDialogVisible.value = true
  }
}

// 编辑行 — 打开 Dialog 并预填数据
function openEditDialog(index: number) {
  dialogMode.value = 'edit'
  editingRowIndex.value = index
  dialogInitialData.value = { ...tableData.value[index] }
  if (hasFormRule.value) {
    simpleDialogVisible.value = false
    formDialogVisible.value = true
  } else {
    formDialogVisible.value = false
    simpleDialogVisible.value = true
  }
}

function openLinkFormDialog(col: ColumnConfig, row: Record<string, any>) {
  linkFormDialogTitle.value = col.label || col.props?.linkText || t('linkForm.defaultLinkText')
  linkFormInitialData.value = { ...row }
  linkFormRule.value = col.props?.formRule || props.formRule || []
  linkFormOption.value = col.props?.formOption || props.formOption || {}
  linkFormDialogVisible.value = true
}

function handleLinkFormSave(rowData: Record<string, any>) {
  linkFormDialogVisible.value = false
  linkFormInitialData.value = rowData
}

// Dialog 保存回调
function handleDialogSave(rowData: Record<string, any>) {
  if (dialogMode.value === 'add') {
    tableData.value.push(rowData)
    emit('add', rowData)
  } else if (dialogMode.value === 'edit' && editingRowIndex.value !== null) {
    tableData.value[editingRowIndex.value] = rowData
    emit('edit', rowData, editingRowIndex.value)
  }
  total.value = tableData.value.length
  emit('update:modelValue', [...tableData.value])
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
}
</style>
