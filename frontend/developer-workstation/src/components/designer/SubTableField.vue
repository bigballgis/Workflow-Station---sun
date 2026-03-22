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
          <span>{{ scope.row[col.field] ?? '-' }}</span>
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

    <SubTableAddDialog
      :visible="dialogVisible"
      :columns="dialogColumns"
      :mode="dialogMode"
      :initialData="dialogInitialData"
      @update:visible="dialogVisible = $event"
      @save="handleDialogSave"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import SubTableAddDialog from './SubTableAddDialog.vue'
import type { DialogColumn } from './subTableAddDialogHelpers'

const { t } = useI18n()

// 列配置接口
interface ColumnConfig {
  field: string
  label: string
  type?: 'input' | 'number' | 'date' | 'switch' | 'text' | 'textarea' | 'select' | 'radio' | 'checkbox' | 'datetime' | 'upload' | 'user' | 'department'
  width?: number
  minWidth?: number
  required?: boolean
  placeholder?: string
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

// Dialog state
const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const editingRowIndex = ref<number | null>(null)
const dialogInitialData = ref<Record<string, any> | undefined>(undefined)

// 计算属性：是否可编辑
const editable = computed(() => props.editable !== false)

// 计算属性：显示的列
const displayColumns = computed(() => props.config.columns || [])

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
  dialogVisible.value = true
}

// 编辑行 — 打开 Dialog 并预填数据
function openEditDialog(index: number) {
  dialogMode.value = 'edit'
  editingRowIndex.value = index
  dialogInitialData.value = { ...tableData.value[index] }
  dialogVisible.value = true
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
}
</style>
