<template>
  <div class="sub-table-field">
    <div class="sub-table-header">
      <span class="title">{{ title }}</span>
      <el-button v-if="editable" type="primary" size="small" @click="handleAdd">
        <el-icon><Plus /></el-icon> 添加
      </el-button>
    </div>

    <el-table :data="rows" size="small" border :max-height="300" v-loading="loading">
      <el-table-column
        v-for="col in columns"
        :key="col.field"
        :prop="col.field"
        :label="col.label"
        :min-width="col.minWidth || 100"
      >
        <template #default="scope">
          <template v-if="editable && editingRow === scope.$index">
            <el-input-number
              v-if="col.type === 'number'"
              v-model="scope.row[col.field]"
              size="small"
              :controls="false"
              style="width:100%"
            />
            <el-date-picker
              v-else-if="col.type === 'date'"
              v-model="scope.row[col.field]"
              type="date"
              size="small"
              value-format="YYYY-MM-DD"
              style="width:100%"
            />
            <el-input v-else v-model="scope.row[col.field]" size="small" />
          </template>
          <span v-else>{{ scope.row[col.field] ?? '-' }}</span>
        </template>
      </el-table-column>

      <el-table-column v-if="editable" label="操作" width="120">
        <template #default="scope">
          <template v-if="editingRow === scope.$index">
            <el-button link type="primary" size="small" @click="saveRow(scope.$index)">保存</el-button>
            <el-button link size="small" @click="cancelEdit(scope.$index)">取消</el-button>
          </template>
          <template v-else>
            <el-button link type="primary" size="small" @click="editRow(scope.$index)">编辑</el-button>
            <el-button link type="danger" size="small" @click="deleteRow(scope.$index)">删除</el-button>
          </template>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="暂无数据" :image-size="40" />
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

interface Column {
  field: string
  label: string
  type?: 'text' | 'number' | 'date'
  minWidth?: number
}

const props = defineProps<{
  title: string
  columns: Column[]
  modelValue?: any[]
  editable?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: any[]): void
}>()

const rows = ref<any[]>([])
const editingRow = ref<number | null>(null)
const backup = ref<any>(null)

watch(() => props.modelValue, (v) => { rows.value = v ? [...v] : [] }, { immediate: true, deep: true })

function handleAdd() {
  const newRow: any = {}
  props.columns.forEach(c => { newRow[c.field] = c.type === 'number' ? 0 : '' })
  rows.value.push(newRow)
  editingRow.value = rows.value.length - 1
  backup.value = { ...newRow }
}

function editRow(i: number) {
  editingRow.value = i
  backup.value = { ...rows.value[i] }
}

function saveRow(i: number) {
  editingRow.value = null
  backup.value = null
  emit('update:modelValue', [...rows.value])
}

function cancelEdit(i: number) {
  if (backup.value !== null) {
    const isNew = Object.values(backup.value).every(v => v === '' || v === 0)
    if (isNew) rows.value.splice(i, 1)
    else rows.value[i] = { ...backup.value }
  }
  editingRow.value = null
  backup.value = null
}

async function deleteRow(i: number) {
  await ElMessageBox.confirm('确定删除这条记录吗？', '提示', { type: 'warning' })
  rows.value.splice(i, 1)
  emit('update:modelValue', [...rows.value])
}
</script>

<style scoped lang="scss">
.sub-table-field {
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  padding: 12px;
  background: #fafafa;

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
}
</style>
