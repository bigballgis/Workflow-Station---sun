<template>
  <div class="table-designer">
    <div class="designer-toolbar">
      <el-button type="primary" @click="showCreateDialog = true">创建表</el-button>
      <el-button @click="handleGenerateDDL">{{ $t('table.generateDDL') }}</el-button>
    </div>
    
    <div class="table-list" v-if="!selectedTable">
      <el-table :data="tables" v-loading="loading" stripe @row-click="handleSelectTable">
        <el-table-column prop="tableName" :label="$t('table.tableName')" />
        <el-table-column prop="tableType" :label="$t('table.tableType')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.tableType === 'MAIN' ? 'primary' : 'info'">
              {{ tableTypeLabel(row.tableType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="字段数" width="80">
          <template #default="{ row }">{{ row.fieldDefinitions?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleSelectTable(row)">编辑</el-button>
            <el-button link type="danger" @click.stop="handleDeleteTable(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="table-editor" v-else>
      <div class="editor-header">
        <el-button @click="selectedTable = null">
          <el-icon><ArrowLeft /></el-icon> 返回列表
        </el-button>
        <span class="table-name">{{ selectedTable.tableName }}</span>
      </div>
      
      <el-form :model="selectedTable" label-width="100px" style="max-width: 600px; margin-bottom: 20px;">
        <el-form-item :label="$t('table.tableName')">
          <el-input v-model="selectedTable.tableName" />
        </el-form-item>
        <el-form-item :label="$t('table.tableType')">
          <el-select v-model="selectedTable.tableType">
            <el-option label="主表" value="MAIN" />
            <el-option label="子表" value="SUB" />
            <el-option label="关联表" value="RELATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="selectedTable.description" type="textarea" />
        </el-form-item>
      </el-form>

      <h4>{{ $t('table.fields') }}</h4>
      <el-button size="small" @click="handleAddField" style="margin-bottom: 10px;">添加字段</el-button>
      <el-table :data="selectedTable.fieldDefinitions" size="small" border>
        <el-table-column prop="fieldName" label="字段名" width="150">
          <template #default="{ row }">
            <el-input v-model="row.fieldName" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="dataType" label="数据类型" width="120">
          <template #default="{ row }">
            <el-select v-model="row.dataType" size="small">
              <el-option label="VARCHAR" value="VARCHAR" />
              <el-option label="INTEGER" value="INTEGER" />
              <el-option label="BIGINT" value="BIGINT" />
              <el-option label="DECIMAL" value="DECIMAL" />
              <el-option label="BOOLEAN" value="BOOLEAN" />
              <el-option label="DATE" value="DATE" />
              <el-option label="TIMESTAMP" value="TIMESTAMP" />
              <el-option label="TEXT" value="TEXT" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="length" label="长度" width="80">
          <template #default="{ row }">
            <el-input-number v-model="row.length" size="small" :min="0" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column prop="nullable" label="可空" width="60">
          <template #default="{ row }">
            <el-checkbox v-model="row.nullable" />
          </template>
        </el-table-column>
        <el-table-column prop="isPrimaryKey" label="主键" width="60">
          <template #default="{ row }">
            <el-checkbox v-model="row.isPrimaryKey" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="handleRemoveField($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Create Table Dialog -->
    <el-dialog v-model="showCreateDialog" title="创建表" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="表名" required>
          <el-input v-model="createForm.tableName" />
        </el-form-item>
        <el-form-item label="表类型">
          <el-select v-model="createForm.tableType">
            <el-option label="主表" value="MAIN" />
            <el-option label="子表" value="SUB" />
            <el-option label="关联表" value="RELATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateTable">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'

const props = defineProps<{ functionUnitId: number }>()

const tables = ref<any[]>([])
const loading = ref(false)
const selectedTable = ref<any>(null)
const showCreateDialog = ref(false)
const createForm = reactive({ tableName: '', tableType: 'MAIN', description: '' })

const tableTypeLabel = (type: string) => {
  const map: Record<string, string> = { MAIN: '主表', SUB: '子表', RELATION: '关联表' }
  return map[type] || type
}

async function loadTables() {
  loading.value = true
  try {
    const res = await api.get(`/function-units/${props.functionUnitId}/tables`)
    tables.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleSelectTable(row: any) {
  selectedTable.value = { ...row, fieldDefinitions: [...(row.fieldDefinitions || [])] }
}

async function handleCreateTable() {
  await api.post(`/function-units/${props.functionUnitId}/tables`, createForm)
  ElMessage.success('创建成功')
  showCreateDialog.value = false
  Object.assign(createForm, { tableName: '', tableType: 'MAIN', description: '' })
  loadTables()
}

async function handleDeleteTable(row: any) {
  await ElMessageBox.confirm('确定要删除该表吗？', '提示', { type: 'warning' })
  await api.delete(`/function-units/${props.functionUnitId}/tables/${row.id}`)
  ElMessage.success('删除成功')
  loadTables()
}

function handleAddField() {
  selectedTable.value.fieldDefinitions.push({
    fieldName: '',
    dataType: 'VARCHAR',
    length: 255,
    nullable: true,
    isPrimaryKey: false
  })
}

function handleRemoveField(index: number) {
  selectedTable.value.fieldDefinitions.splice(index, 1)
}

function handleGenerateDDL() {
  ElMessage.info('DDL生成功能开发中')
}

onMounted(loadTables)
</script>

<style lang="scss" scoped>
.table-designer {
  min-height: 400px;
}

.designer-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.editor-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.table-name {
  font-size: 18px;
  font-weight: bold;
}
</style>
