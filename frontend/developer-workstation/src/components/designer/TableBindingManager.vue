<template>
  <div class="table-binding-manager">
    <!-- Binding list -->
    <div class="binding-list">
      <div class="binding-header">
        <span class="title">{{ t('tableBinding.title') }}</span>
        <el-button type="primary" size="small" @click="showAddDialog = true">
          <el-icon><Plus /></el-icon> {{ t('tableBinding.addBinding') }}
        </el-button>
      </div>
      
      <el-table :data="bindings" size="small" v-loading="loading">
        <el-table-column prop="tableName" :label="t('tableBinding.tableName')" min-width="120">
          <template #default="{ row }">
            <span>{{ row.tableName || getTableName(row.tableId) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="bindingType" :label="t('tableBinding.bindingType')" width="100">
          <template #default="{ row }">
            <el-tag :type="bindingTypeTag(row.bindingType)" size="small">
              {{ bindingTypeLabel(row.bindingType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bindingMode" :label="t('tableBinding.mode')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.bindingMode === 'EDITABLE' ? 'success' : 'info'" size="small">
              {{ row.bindingMode === 'EDITABLE' ? t('tableBinding.editable') : t('tableBinding.readOnly') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="foreignKeyField" :label="t('tableBinding.foreignKeyField')" width="120">
          <template #default="{ row }">
            <span v-if="row.foreignKeyField">{{ row.foreignKeyField }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('tableBinding.operations')" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)" :disabled="row.bindingType === 'PRIMARY'">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <el-empty v-if="bindings.length === 0 && !loading" :description="t('tableBinding.noBindings')" :image-size="60" />
    </div>

    <!-- Add/Edit binding dialog -->
    <el-dialog 
      v-model="showAddDialog" 
      :title="editingBinding ? t('tableBinding.editBinding') : t('tableBinding.addBinding')" 
      width="500px"
      @close="resetForm"
    >
      <el-form :model="bindingForm" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item :label="t('tableBinding.selectTable')" prop="tableId">
          <el-select 
            v-model="bindingForm.tableId" 
            :placeholder="t('tableBinding.selectTablePlaceholder')" 
            style="width: 100%"
            :disabled="!!editingBinding"
            @change="handleTableSelect"
          >
            <el-option 
              v-for="table in availableTables" 
              :key="table.id" 
              :label="`${table.tableName} (${tableTypeLabel(table.tableType)})`" 
              :value="table.id"
              :disabled="isTableBound(table.id)"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item :label="t('tableBinding.bindingType')" prop="bindingType">
          <el-select v-model="bindingForm.bindingType" style="width: 100%" :disabled="!!editingBinding && editingBinding.bindingType === 'PRIMARY'">
            <el-option :label="t('tableBinding.primaryTable')" value="PRIMARY" :disabled="hasPrimaryBinding && bindingForm.bindingType !== 'PRIMARY'" />
            <el-option :label="t('tableBinding.subTable')" value="SUB" />
            <el-option :label="t('tableBinding.relatedTable')" value="RELATED" />
          </el-select>
        </el-form-item>
        
        <el-form-item :label="t('tableBinding.bindingMode')" prop="bindingMode">
          <el-radio-group v-model="bindingForm.bindingMode">
            <el-radio value="EDITABLE">{{ t('tableBinding.editable') }}</el-radio>
            <el-radio value="READONLY">{{ t('tableBinding.readOnly') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item 
          :label="t('tableBinding.foreignKeyField')" 
          prop="foreignKeyField"
          v-if="bindingForm.bindingType !== 'PRIMARY'"
        >
          <el-select 
            v-model="bindingForm.foreignKeyField" 
            :placeholder="t('tableBinding.selectForeignKey')" 
            style="width: 100%"
            clearable
          >
            <el-option 
              v-for="field in selectedTableFields" 
              :key="field.fieldName" 
              :label="`${field.fieldName} (${field.dataType})`" 
              :value="field.fieldName" 
            />
          </el-select>
          <div class="form-item-tip">{{ t('tableBinding.foreignKeyTip') }}</div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showAddDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ editingBinding ? t('common.save') : t('tableBinding.add') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { functionUnitApi, type TableBinding, type TableBindingRequest, type TableDefinition, type BindingType } from '@/api/functionUnit'

const { t } = useI18n()

const props = defineProps<{
  functionUnitId: number
  formId: number
  tables: TableDefinition[]
}>()

const emit = defineEmits<{
  (e: 'update'): void
}>()

const loading = ref(false)
const submitting = ref(false)
const bindings = ref<TableBinding[]>([])
const showAddDialog = ref(false)
const editingBinding = ref<TableBinding | null>(null)
const formRef = ref<FormInstance>()

const bindingForm = ref<TableBindingRequest>({
  tableId: 0,
  bindingType: 'SUB',
  bindingMode: 'READONLY',
  foreignKeyField: undefined
})

const formRules = computed<FormRules>(() => ({
  tableId: [{ required: true, message: t('tableBinding.selectTableRequired'), trigger: 'change' }],
  bindingType: [{ required: true, message: t('tableBinding.selectBindingTypeRequired'), trigger: 'change' }],
  bindingMode: [{ required: true, message: t('tableBinding.selectBindingModeRequired'), trigger: 'change' }]
}))

// Whether a primary binding already exists
const hasPrimaryBinding = computed(() => {
  return bindings.value.some(b => b.bindingType === 'PRIMARY')
})

// Available tables
const availableTables = computed(() => {
  return props.tables
})

// Fields of the selected table
const selectedTableFields = computed(() => {
  if (!bindingForm.value.tableId) return []
  const table = props.tables.find(t => t.id === bindingForm.value.tableId)
  return table?.fieldDefinitions || []
})

// Check if table is already bound
function isTableBound(tableId: number): boolean {
  if (editingBinding.value?.tableId === tableId) return false
  return bindings.value.some(b => b.tableId === tableId)
}

// Get table name by ID
function getTableName(tableId: number): string {
  const table = props.tables.find(t => t.id === tableId)
  return table?.tableName || t('tableBinding.unknownTable')
}

// Binding type label
function bindingTypeLabel(type: BindingType): string {
  const map: Record<BindingType, string> = {
    PRIMARY: t('tableBinding.primaryTable'),
    SUB: t('tableBinding.subTable'),
    RELATED: t('tableBinding.relatedTable')
  }
  return map[type] || type
}

// Binding type tag color
function bindingTypeTag(type: BindingType): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<BindingType, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { PRIMARY: 'primary', SUB: 'success', RELATED: 'warning' }
  return map[type] || 'info'
}

// Table type label
function tableTypeLabel(type: string): string {
  const map: Record<string, string> = {
    MAIN: t('tableBinding.mainTableType'),
    SUB: t('tableBinding.subTableType'),
    ACTION: t('tableBinding.actionTableType'),
    RELATION: t('tableBinding.relationTableType')
  }
  return map[type] || type
}

// Load bindings
async function loadBindings() {
  loading.value = true
  try {
    const res = await functionUnitApi.getFormBindings(props.functionUnitId, props.formId)
    bindings.value = res.data || []
  } catch (e: any) {
    console.error('Failed to load bindings:', e)
    bindings.value = []
  } finally {
    loading.value = false
  }
}

// Handle table selection change
function handleTableSelect(tableId: number) {
  const table = props.tables.find(t => t.id === tableId)
  if (table) {
    if (table.tableType === 'MAIN' && !hasPrimaryBinding.value) {
      bindingForm.value.bindingType = 'PRIMARY'
      bindingForm.value.bindingMode = 'EDITABLE'
    } else if (table.tableType === 'SUB') {
      bindingForm.value.bindingType = 'SUB'
      bindingForm.value.bindingMode = 'READONLY'
    } else {
      bindingForm.value.bindingType = 'RELATED'
      bindingForm.value.bindingMode = 'READONLY'
    }
  }
}

// Edit binding
function handleEdit(binding: TableBinding) {
  editingBinding.value = binding
  bindingForm.value = {
    tableId: binding.tableId,
    bindingType: binding.bindingType,
    bindingMode: binding.bindingMode,
    foreignKeyField: binding.foreignKeyField,
    sortOrder: binding.sortOrder
  }
  showAddDialog.value = true
}

// Delete binding
async function handleDelete(binding: TableBinding) {
  if (binding.bindingType === 'PRIMARY') {
    ElMessage.warning(t('tableBinding.cannotDeletePrimary'))
    return
  }
  
  await ElMessageBox.confirm(t('tableBinding.deleteConfirm'), t('tableBinding.confirmTitle'), { type: 'warning' })
  
  try {
    await functionUnitApi.deleteFormBinding(props.functionUnitId, props.formId, binding.id!)
    ElMessage.success(t('tableBinding.deleteSuccess'))
    loadBindings()
    emit('update')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('tableBinding.deleteFailed'))
  }
}

// Submit form
async function handleSubmit() {
  if (!formRef.value) return
  
  await formRef.value.validate()
  
  submitting.value = true
  try {
    if (editingBinding.value) {
      await functionUnitApi.updateFormBinding(
        props.functionUnitId, 
        props.formId, 
        editingBinding.value.id!, 
        bindingForm.value
      )
      ElMessage.success(t('tableBinding.updateSuccess'))
    } else {
      await functionUnitApi.createFormBinding(props.functionUnitId, props.formId, bindingForm.value)
      ElMessage.success(t('tableBinding.addSuccess'))
    }
    showAddDialog.value = false
    loadBindings()
    emit('update')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('tableBinding.operationFailed'))
  } finally {
    submitting.value = false
  }
}

// Reset form
function resetForm() {
  editingBinding.value = null
  bindingForm.value = {
    tableId: 0,
    bindingType: 'SUB',
    bindingMode: 'READONLY',
    foreignKeyField: undefined
  }
  formRef.value?.resetFields()
}

// Reload when formId changes
watch(() => props.formId, () => {
  if (props.formId) {
    loadBindings()
  }
}, { immediate: true })

onMounted(() => {
  if (props.formId) {
    loadBindings()
  }
})

// Expose methods for parent component
defineExpose({
  loadBindings,
  bindings
})
</script>

<style lang="scss" scoped>
.table-binding-manager {
  .binding-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    
    .title {
      font-weight: 500;
      font-size: 14px;
    }
  }
  
  .text-muted {
    color: #909399;
  }
  
  .form-item-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }
}
</style>
