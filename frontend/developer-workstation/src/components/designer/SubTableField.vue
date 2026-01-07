<template>
  <div class="sub-table-field">
    <div class="sub-table-header">
      <span class="title">{{ config.title || '子表数据' }}</span>
      <div class="actions" v-if="editable">
        <el-button type="primary" size="small" @click="handleAdd">
          <el-icon><Plus /></el-icon> 添加
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
          <template v-if="editable && editingRow === scope.$index">
            <el-input 
              v-if="col.type === 'input'" 
              v-model="scope.row[col.field]" 
              size="small"
            />
            <el-input-number 
              v-else-if="col.type === 'number'" 
              v-model="scope.row[col.field]" 
              size="small"
              :controls="false"
            />
            <el-date-picker 
              v-else-if="col.type === 'date'" 
              v-model="scope.row[col.field]" 
              type="date"
              size="small"
              value-format="YYYY-MM-DD"
            />
            <el-switch 
              v-else-if="col.type === 'switch'" 
              v-model="scope.row[col.field]"
            />
            <el-input v-else v-model="scope.row[col.field]" size="small" />
          </template>
          <span v-else>{{ scope.row[col.field] }}</span>
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="120" v-if="editable">
        <template #default="scope">
          <template v-if="editingRow === scope.$index">
            <el-button link type="primary" size="small" @click="handleSave(scope.$index)">保存</el-button>
            <el-button link type="info" size="small" @click="handleCancel">取消</el-button>
          </template>
          <template v-else>
            <el-button link type="primary" size="small" @click="handleEdit(scope.$index)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(scope.$index)">删除</el-button>
          </template>
        </template>
      </el-table-column>
      
      <template #empty>
        <el-empty description="暂无数据" :image-size="40" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// 列配置接口
interface ColumnConfig {
  field: string
  label: string
  type?: 'input' | 'number' | 'date' | 'switch' | 'text'
  width?: number
  minWidth?: number
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
const editingRow = ref<number | null>(null)
const editingRowBackup = ref<any>(null)
const currentPage = ref(1)
const total = ref(0)

// 计算属性：是否可编辑
const editable = computed(() => props.editable !== false)

// 计算属性：显示的列
const displayColumns = computed(() => {
  return props.config.columns || []
})

// 监听 modelValue 变化
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    tableData.value = [...newVal]
    total.value = newVal.length
  }
}, { immediate: true, deep: true })

// 添加行
function handleAdd() {
  const newRow: any = {}
  displayColumns.value.forEach(col => {
    newRow[col.field] = col.type === 'number' ? 0 : col.type === 'switch' ? false : ''
  })
  tableData.value.push(newRow)
  editingRow.value = tableData.value.length - 1
  editingRowBackup.value = { ...newRow }
}

// 编辑行
function handleEdit(index: number) {
  editingRow.value = index
  editingRowBackup.value = { ...tableData.value[index] }
}

// 保存行
function handleSave(index: number) {
  editingRow.value = null
  editingRowBackup.value = null
  emit('update:modelValue', [...tableData.value])
  emit('edit', tableData.value[index], index)
}

// 取消编辑
function handleCancel() {
  if (editingRow.value !== null && editingRowBackup.value) {
    // 如果是新添加的行，删除它
    const isNewRow = Object.values(editingRowBackup.value).every(v => v === '' || v === 0 || v === false)
    if (isNewRow) {
      tableData.value.splice(editingRow.value, 1)
    } else {
      // 恢复原始数据
      tableData.value[editingRow.value] = { ...editingRowBackup.value }
    }
  }
  editingRow.value = null
  editingRowBackup.value = null
}

// 删除行
async function handleDelete(index: number) {
  await ElMessageBox.confirm('确定要删除这条记录吗？', '提示', { type: 'warning' })
  const deletedRow = tableData.value[index]
  tableData.value.splice(index, 1)
  emit('update:modelValue', [...tableData.value])
  emit('delete', deletedRow, index)
  ElMessage.success('删除成功')
}

// 分页变化
function handlePageChange(page: number) {
  currentPage.value = page
  // 如果需要从后端加载数据，在这里触发
}

// 暴露方法
defineExpose({
  getData: () => tableData.value,
  setData: (data: any[]) => {
    tableData.value = [...data]
    total.value = data.length
  },
  refresh: () => {
    // 刷新数据
  }
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
