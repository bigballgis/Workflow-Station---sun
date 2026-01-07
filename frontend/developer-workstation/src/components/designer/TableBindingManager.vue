<template>
  <div class="table-binding-manager">
    <!-- 绑定列表 -->
    <div class="binding-list">
      <div class="binding-header">
        <span class="title">表绑定管理</span>
        <el-button type="primary" size="small" @click="showAddDialog = true">
          <el-icon><Plus /></el-icon> 添加绑定
        </el-button>
      </div>
      
      <el-table :data="bindings" size="small" v-loading="loading">
        <el-table-column prop="tableName" label="表名" min-width="120">
          <template #default="{ row }">
            <span>{{ row.tableName || getTableName(row.tableId) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="bindingType" label="绑定类型" width="100">
          <template #default="{ row }">
            <el-tag :type="bindingTypeTag(row.bindingType)" size="small">
              {{ bindingTypeLabel(row.bindingType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bindingMode" label="模式" width="80">
          <template #default="{ row }">
            <el-tag :type="row.bindingMode === 'EDITABLE' ? 'success' : 'info'" size="small">
              {{ row.bindingMode === 'EDITABLE' ? '可编辑' : '只读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="foreignKeyField" label="外键字段" width="120">
          <template #default="{ row }">
            <span v-if="row.foreignKeyField">{{ row.foreignKeyField }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)" :disabled="row.bindingType === 'PRIMARY'">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <el-empty v-if="bindings.length === 0 && !loading" description="暂无表绑定" :image-size="60" />
    </div>

    <!-- 添加/编辑绑定对话框 -->
    <el-dialog 
      v-model="showAddDialog" 
      :title="editingBinding ? '编辑表绑定' : '添加表绑定'" 
      width="500px"
      @close="resetForm"
    >
      <el-form :model="bindingForm" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="选择表" prop="tableId">
          <el-select 
            v-model="bindingForm.tableId" 
            placeholder="请选择要绑定的表" 
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
        
        <el-form-item label="绑定类型" prop="bindingType">
          <el-select v-model="bindingForm.bindingType" style="width: 100%" :disabled="!!editingBinding && editingBinding.bindingType === 'PRIMARY'">
            <el-option label="主表" value="PRIMARY" :disabled="hasPrimaryBinding && bindingForm.bindingType !== 'PRIMARY'" />
            <el-option label="子表" value="SUB" />
            <el-option label="关联表" value="RELATED" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="绑定模式" prop="bindingMode">
          <el-radio-group v-model="bindingForm.bindingMode">
            <el-radio value="EDITABLE">可编辑</el-radio>
            <el-radio value="READONLY">只读</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item 
          label="外键字段" 
          prop="foreignKeyField"
          v-if="bindingForm.bindingType !== 'PRIMARY'"
        >
          <el-select 
            v-model="bindingForm.foreignKeyField" 
            placeholder="选择关联主表的外键字段" 
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
          <div class="form-item-tip">子表和关联表需要指定关联主表的外键字段</div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ editingBinding ? '保存' : '添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { functionUnitApi, type TableBinding, type TableBindingRequest, type TableDefinition, type BindingType } from '@/api/functionUnit'

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

const formRules: FormRules = {
  tableId: [{ required: true, message: '请选择表', trigger: 'change' }],
  bindingType: [{ required: true, message: '请选择绑定类型', trigger: 'change' }],
  bindingMode: [{ required: true, message: '请选择绑定模式', trigger: 'change' }]
}

// 计算属性：是否已有主表绑定
const hasPrimaryBinding = computed(() => {
  return bindings.value.some(b => b.bindingType === 'PRIMARY')
})

// 计算属性：可用的表（排除已绑定的）
const availableTables = computed(() => {
  return props.tables
})

// 计算属性：选中表的字段列表
const selectedTableFields = computed(() => {
  if (!bindingForm.value.tableId) return []
  const table = props.tables.find(t => t.id === bindingForm.value.tableId)
  return table?.fieldDefinitions || []
})

// 检查表是否已绑定
function isTableBound(tableId: number): boolean {
  if (editingBinding.value?.tableId === tableId) return false
  return bindings.value.some(b => b.tableId === tableId)
}

// 获取表名
function getTableName(tableId: number): string {
  const table = props.tables.find(t => t.id === tableId)
  return table?.tableName || '未知表'
}

// 绑定类型标签
function bindingTypeLabel(type: BindingType): string {
  const map: Record<BindingType, string> = { PRIMARY: '主表', SUB: '子表', RELATED: '关联表' }
  return map[type] || type
}

// 绑定类型标签颜色
function bindingTypeTag(type: BindingType): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<BindingType, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { PRIMARY: 'primary', SUB: 'success', RELATED: 'warning' }
  return map[type] || 'info'
}

// 表类型标签
function tableTypeLabel(type: string): string {
  const map: Record<string, string> = { MAIN: '主表', SUB: '子表', ACTION: '动作表', RELATION: '关联表' }
  return map[type] || type
}

// 加载绑定列表
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

// 表选择变化
function handleTableSelect(tableId: number) {
  // 根据表类型自动设置绑定类型
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

// 编辑绑定
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

// 删除绑定
async function handleDelete(binding: TableBinding) {
  if (binding.bindingType === 'PRIMARY') {
    ElMessage.warning('主表绑定不能删除')
    return
  }
  
  await ElMessageBox.confirm('确定要删除该表绑定吗？', '提示', { type: 'warning' })
  
  try {
    await functionUnitApi.deleteFormBinding(props.functionUnitId, props.formId, binding.id!)
    ElMessage.success('删除成功')
    loadBindings()
    emit('update')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

// 提交表单
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
      ElMessage.success('更新成功')
    } else {
      await functionUnitApi.createFormBinding(props.functionUnitId, props.formId, bindingForm.value)
      ElMessage.success('添加成功')
    }
    showAddDialog.value = false
    loadBindings()
    emit('update')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// 重置表单
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

// 监听 formId 变化重新加载
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

// 暴露方法供父组件调用
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
