<template>
  <div class="table-designer">
    <div class="designer-toolbar">
      <el-button type="primary" @click="showCreateDialog = true">{{ $t('table.title') }}</el-button>
      <el-button @click="loadTables" :loading="loading">
        <el-icon><Refresh /></el-icon> {{ $t('common.refresh') }}
      </el-button>
      <el-button @click="handleGenerateDDL" :disabled="!selectedTable">{{ $t('table.generateDDL') }}</el-button>
      <el-button @click="handleValidate">{{ $t('functionUnit.validate') }}</el-button>
      <el-button @click="showRelationDialog = true" :disabled="store.tables.length < 2">{{ $t('table.relations') }}</el-button>
    </div>
    
    <div class="table-list" v-if="!selectedTable">
      <el-table :data="store.tables" v-loading="loading" stripe @row-click="handleSelectTable">
        <el-table-column prop="tableName" :label="$t('table.tableName')" />
        <el-table-column prop="tableType" :label="$t('table.tableType')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.tableType === 'MAIN' ? 'primary' : 'info'">
              {{ tableTypeLabel(row.tableType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="$t('table.description')" show-overflow-tooltip />
        <el-table-column :label="$t('table.fieldCount')" width="80">
          <template #default="{ row }">{{ row.fieldDefinitions?.length || 0 }}</template>
        </el-table-column>
        <el-table-column :label="$t('table.relations')" width="100">
          <template #default="{ row }">
            <el-tag v-if="getTableRelations(row.id).length" type="success" size="small">
              {{ getTableRelations(row.id).length }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleSelectTable(row)">{{ $t('common.edit') }}</el-button>
            <el-button link type="danger" @click.stop="handleDeleteTable(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="table-editor" v-else>
      <div class="editor-header">
        <el-button @click="handleBackToList">
          <el-icon><ArrowLeft /></el-icon> 返回列表
        </el-button>
        <span class="table-name">{{ selectedTable.tableName }}</span>
        <el-button type="primary" @click="handleSaveTable">保存</el-button>
      </div>
      
      <el-form :model="selectedTable" label-width="100px" style="max-width: 600px; margin-bottom: 20px;">
        <el-form-item :label="$t('table.tableName')">
          <el-input v-model="selectedTable.tableName" />
        </el-form-item>
        <el-form-item :label="$t('table.tableType')">
          <el-select v-model="selectedTable.tableType">
            <el-option label="主表" value="MAIN" />
            <el-option label="子表" value="SUB" />
            <el-option label="动作表" value="ACTION" />
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
        <el-table-column prop="description" label="描述" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.description" size="small" />
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
    <el-dialog v-model="showCreateDialog" :title="$t('table.title')" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item :label="$t('table.tableName')" required>
          <el-input v-model="createForm.tableName" />
        </el-form-item>
        <el-form-item :label="$t('table.tableType')">
          <el-select v-model="createForm.tableType">
            <el-option :label="$t('form.mainForm')" value="MAIN" />
            <el-option :label="$t('form.subForm')" value="SUB" />
            <el-option :label="$t('form.actionForm')" value="ACTION" />
            <el-option :label="$t('table.relations')" value="RELATION" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('table.description')">
          <el-input v-model="createForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreateTable">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- DDL Dialog -->
    <el-dialog v-model="showDDLDialog" title="DDL 预览" width="700px">
      <el-select v-model="ddlDialect" style="margin-bottom: 16px;">
        <el-option label="PostgreSQL" value="POSTGRESQL" />
        <el-option label="MySQL" value="MYSQL" />
        <el-option label="Oracle" value="ORACLE" />
      </el-select>
      <el-input v-model="ddlContent" type="textarea" :rows="15" readonly />
      <template #footer>
        <el-button @click="handleCopyDDL">复制</el-button>
        <el-button @click="showDDLDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Relation Config Dialog -->
    <el-dialog v-model="showRelationDialog" title="数据模型关联配置" width="800px">
      <div class="relation-config">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
          配置表之间的关联关系，支持一对一、一对多、多对多关联类型。
        </el-alert>
        
        <div class="relation-list">
          <div v-for="(rel, index) in relations" :key="index" class="relation-item">
            <el-select v-model="rel.sourceTableId" placeholder="源表" style="width: 150px;">
              <el-option v-for="t in store.tables" :key="t.id" :label="t.tableName" :value="t.id" />
            </el-select>
            <el-select v-model="rel.sourceFieldName" placeholder="源字段" style="width: 120px;" 
                       :disabled="!rel.sourceTableId">
              <el-option v-for="f in getTableFields(rel.sourceTableId)" :key="f.fieldName" 
                         :label="f.fieldName" :value="f.fieldName" />
            </el-select>
            <el-select v-model="rel.relationType" placeholder="关联类型" style="width: 120px;">
              <el-option label="一对一" value="ONE_TO_ONE" />
              <el-option label="一对多" value="ONE_TO_MANY" />
              <el-option label="多对多" value="MANY_TO_MANY" />
            </el-select>
            <el-select v-model="rel.targetTableId" placeholder="目标表" style="width: 150px;">
              <el-option v-for="t in store.tables" :key="t.id" :label="t.tableName" :value="t.id" />
            </el-select>
            <el-select v-model="rel.targetFieldName" placeholder="目标字段" style="width: 120px;"
                       :disabled="!rel.targetTableId">
              <el-option v-for="f in getTableFields(rel.targetTableId)" :key="f.fieldName" 
                         :label="f.fieldName" :value="f.fieldName" />
            </el-select>
            <el-button link type="danger" @click="removeRelation(index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        
        <el-button @click="addRelation" style="margin-top: 12px;">
          <el-icon><Plus /></el-icon> 添加关联
        </el-button>
      </div>
      <template #footer>
        <el-button @click="showRelationDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRelations">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Delete, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { functionUnitApi, type TableDefinition, type FieldDefinition, type ForeignKeyDTO } from '@/api/functionUnit'

const { t } = useI18n()

interface TableRelation {
  id?: number
  sourceTableId: number | null
  sourceFieldName: string
  relationType: string
  targetTableId: number | null
  targetFieldName: string
}

const props = defineProps<{ functionUnitId: number }>()

const store = useFunctionUnitStore()
const loading = ref(false)
const selectedTable = ref<TableDefinition | null>(null)
const showCreateDialog = ref(false)
const showDDLDialog = ref(false)
const showRelationDialog = ref(false)
const ddlDialect = ref('POSTGRESQL')
const ddlContent = ref('')
const createForm = reactive({ tableName: '', tableType: 'MAIN', description: '' })
const relations = ref<TableRelation[]>([])
const foreignKeys = ref<ForeignKeyDTO[]>([])

const tableTypeLabel = (type: string) => {
  const map: Record<string, string> = { MAIN: t('form.mainForm'), SUB: t('form.subForm'), ACTION: t('form.actionForm'), RELATION: t('table.relations') }
  return map[type] || type
}

function getTableFields(tableId: number | null): FieldDefinition[] {
  if (!tableId) return []
  const table = store.tables.find(t => t.id === tableId)
  return table?.fieldDefinitions || []
}

function getTableRelations(tableId: number): (TableRelation | ForeignKeyDTO)[] {
  // Combine local relations and database foreign keys
  const localRelations = relations.value.filter(r => r.sourceTableId === tableId || r.targetTableId === tableId)
  const dbRelations = foreignKeys.value.filter(fk => fk.sourceTableId === tableId || fk.targetTableId === tableId)
  return [...localRelations, ...dbRelations]
}

async function loadTables() {
  loading.value = true
  try {
    const tables = await store.fetchTables(props.functionUnitId)
    console.log('[TableDesigner] Loaded tables:', tables)
    tables.forEach(table => {
      console.log(`[TableDesigner] Table ${table.tableName} has ${table.fieldDefinitions?.length || 0} fields`)
    })
    await loadRelations()
    // 如果当前选中的表还在，更新选中表的数据
    if (selectedTable.value) {
      const updatedTable = tables.find(t => t.id === selectedTable.value!.id)
      if (updatedTable) {
        selectedTable.value = { 
          ...updatedTable, 
          fieldDefinitions: [...(updatedTable.fieldDefinitions || []).map(f => ({ ...f }))] 
        }
        console.log('[TableDesigner] Updated selected table with', selectedTable.value.fieldDefinitions?.length || 0, 'fields')
      }
    }
  } finally {
    loading.value = false
  }
}

async function loadRelations() {
  try {
    // Load foreign keys from database API
    const res = await functionUnitApi.getForeignKeys(props.functionUnitId)
    foreignKeys.value = res?.data || []
    
    // Also load local relations from localStorage (for backward compatibility)
    const stored = localStorage.getItem(`table_relations_${props.functionUnitId}`)
    if (stored) {
      relations.value = JSON.parse(stored)
    }
  } catch {
    foreignKeys.value = []
    relations.value = []
  }
}

function handleSelectTable(row: TableDefinition) {
  selectedTable.value = { 
    ...row, 
    fieldDefinitions: [...(row.fieldDefinitions || []).map(f => ({ ...f }))] 
  }
}

function handleBackToList() {
  selectedTable.value = null
}

async function handleCreateTable() {
  try {
    await store.createTable(props.functionUnitId, createForm)
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    Object.assign(createForm, { tableName: '', tableType: 'MAIN', description: '' })
    loadTables()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  }
}

async function handleSaveTable() {
  if (!selectedTable.value) return
  try {
    // 转换数据格式：将 fieldDefinitions 转换为 fields
    // 后端期望的是 TableDefinitionRequest，包含 fields 而不是 fieldDefinitions
    const fields = (selectedTable.value.fieldDefinitions || [])
      .filter(f => f.fieldName && f.fieldName.trim()) // 过滤空字段名
      .map((f: any, index: number) => ({
        fieldName: f.fieldName,
        dataType: f.dataType, // 确保 dataType 是有效的枚举值
        length: f.length,
        precision: f.precision,
        scale: f.scale,
        nullable: f.nullable !== undefined ? f.nullable : true,
        defaultValue: f.defaultValue,
        isPrimaryKey: f.isPrimaryKey || false,
        isUnique: (f as any).isUnique || false,
        description: f.description,
        sortOrder: (f as any).sortOrder !== undefined ? (f as any).sortOrder : index
      }))
    
    const requestData = {
      tableName: selectedTable.value.tableName,
      tableType: selectedTable.value.tableType,
      description: selectedTable.value.description,
      fields: fields
    }
    
    console.log('[TableDesigner] Saving table with fields:', {
      tableId: selectedTable.value.id,
      tableName: requestData.tableName,
      fieldCount: fields.length,
      fields: fields,
      requestData: JSON.stringify(requestData, null, 2)
    })
    
    const result = await store.updateTable(props.functionUnitId, selectedTable.value.id, requestData)
    console.log('[TableDesigner] Save result:', result)
    console.log('[TableDesigner] Result fieldDefinitions:', result?.fieldDefinitions?.length || 0)
    console.log('[TableDesigner] Result fieldDefinitions array:', result?.fieldDefinitions)
    
    // 更新当前选中的表，使用返回的数据
    // result 已经是 TableDefinition（store.updateTable 返回 res.data，而 res 是 ApiResponse）
    if (result) {
      selectedTable.value = { 
        ...result, 
        fieldDefinitions: [...(result.fieldDefinitions || []).map(f => ({ ...f }))] 
      }
      console.log('[TableDesigner] Updated selected table after save with', selectedTable.value.fieldDefinitions?.length || 0, 'fields')
    } else {
      console.warn('[TableDesigner] Save result is null or undefined')
    }
    
    ElMessage.success('保存成功')
    
    // 延迟加载列表，确保事务已提交
    setTimeout(() => {
    loadTables()
    }, 500)
  } catch (e: any) {
    console.error('[TableDesigner] Save failed:', e)
    ElMessage.error(e.response?.data?.message || '保存失败')
  }
}

async function handleDeleteTable(row: TableDefinition) {
  await ElMessageBox.confirm('确定要删除该表吗？', '提示', { type: 'warning' })
  try {
    await store.deleteTable(props.functionUnitId, row.id)
    ElMessage.success('删除成功')
    loadTables()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

function handleAddField() {
  if (!selectedTable.value) return
  selectedTable.value.fieldDefinitions.push({
    fieldName: '',
    dataType: 'VARCHAR',
    length: 255,
    nullable: true,
    isPrimaryKey: false,
    description: ''
  })
}

function handleRemoveField(index: number) {
  if (!selectedTable.value) return
  selectedTable.value.fieldDefinitions.splice(index, 1)
}

async function handleGenerateDDL() {
  if (!selectedTable.value) return
  try {
    const res = await functionUnitApi.generateDDL?.(props.functionUnitId, selectedTable.value.id, ddlDialect.value)
    ddlContent.value = res?.data || ''
    showDDLDialog.value = true
  } catch {
    ElMessage.info('DDL生成功能开发中')
  }
}

async function handleValidate() {
  try {
    const res = await functionUnitApi.validateTables?.(props.functionUnitId)
    if (res?.data?.valid) {
      ElMessage.success('表结构验证通过')
    } else {
      ElMessage.warning(`验证发现问题: ${res?.data?.errors?.join(', ') || '未知错误'}`)
    }
  } catch {
    ElMessage.info('验证功能开发中')
  }
}

function handleCopyDDL() {
  navigator.clipboard.writeText(ddlContent.value)
  ElMessage.success('已复制到剪贴板')
}

function addRelation() {
  relations.value.push({
    sourceTableId: null,
    sourceFieldName: '',
    relationType: 'ONE_TO_MANY',
    targetTableId: null,
    targetFieldName: ''
  })
}

function removeRelation(index: number) {
  relations.value.splice(index, 1)
}

function handleSaveRelations() {
  // Validate relations
  const validRelations = relations.value.filter(r => 
    r.sourceTableId && r.sourceFieldName && r.relationType && r.targetTableId && r.targetFieldName
  )
  
  // Save to localStorage (can be replaced with API call)
  localStorage.setItem(`table_relations_${props.functionUnitId}`, JSON.stringify(validRelations))
  relations.value = validRelations
  
  ElMessage.success('关联配置已保存')
  showRelationDialog.value = false
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
  flex: 1;
}

.text-muted {
  color: #909399;
  font-size: 12px;
}

.relation-config {
  .relation-list {
    max-height: 350px;
    overflow-y: auto;
  }
  
  .relation-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px;
    margin-bottom: 8px;
    background: #f5f7fa;
    border-radius: 4px;
  }
}
</style>
