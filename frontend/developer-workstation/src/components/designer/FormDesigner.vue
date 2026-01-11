<template>
  <div class="form-designer">
    <!-- 表单列表视图 -->
    <div class="form-list-view" v-if="!selectedForm">
      <div class="designer-toolbar">
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon> 创建表单
        </el-button>
        <el-button @click="loadForms" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
        <el-button @click="handleImportFromTable" :disabled="store.tables.length === 0">
          <el-icon><Connection /></el-icon> 从表导入字段
        </el-button>
      </div>
      
      <el-table :data="store.forms" v-loading="loading" stripe @row-click="handleSelectForm">
        <el-table-column prop="formName" label="表单名称" />
        <el-table-column prop="formType" label="表单类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.formType === 'MAIN' ? 'primary' : 'info'">
              {{ formTypeLabel(row.formType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="boundTableId" label="绑定表" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.boundTableId" type="success" size="small">
              {{ getTableName(row.boundTableId) }}
            </el-tag>
            <span v-else class="text-muted">未绑定</span>
          </template>
        </el-table-column>
        <el-table-column prop="boundNodeId" label="绑定节点" min-width="180">
          <template #default="{ row }">
            <div class="bound-nodes">
              <template v-if="getFormBoundNodes(row.id).length > 0">
                <el-tag 
                  v-for="node in getFormBoundNodes(row.id)" 
                  :key="node.nodeId"
                  :type="node.readOnly ? 'info' : 'success'" 
                  size="small"
                  class="node-tag"
                >
                  {{ node.nodeName }}{{ node.readOnly ? '(只读)' : '' }}
                </el-tag>
              </template>
              <span v-else class="text-muted">未绑定</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button link type="primary" @click.stop="handleSelectForm(row)">编辑</el-button>
              <el-button link type="warning" @click.stop="handleManageBindings(row)">管理绑定</el-button>
              <el-button link type="success" @click.stop="handleBindNode(row)">绑定节点</el-button>
              <el-button link type="danger" @click.stop="handleDeleteForm(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 表单设计器视图 -->
    <div class="form-editor-view" v-else>
      <div class="editor-header">
        <el-button @click="handleBackToList">
          <el-icon><ArrowLeft /></el-icon> 返回列表
        </el-button>
        <span class="form-name">{{ selectedForm.formName }}</span>
        <el-tag v-if="selectedForm.boundTableId" type="success" size="small" class="bound-table-tag">
          绑定表: {{ getTableName(selectedForm.boundTableId) }}
        </el-tag>
        <div class="bound-nodes-header" v-if="getFormBoundNodes(selectedForm.id).length > 0">
          <el-tag 
            v-for="node in getFormBoundNodes(selectedForm.id)" 
            :key="node.nodeId"
            :type="node.readOnly ? 'info' : 'success'" 
            size="small"
          >
            {{ node.nodeName }}{{ node.readOnly ? '(只读)' : '' }}
          </el-tag>
        </div>
        <div class="header-actions">
          <el-button @click="handleImportFieldsToDesigner" :disabled="!selectedForm.boundTableId">
            <el-icon><Connection /></el-icon> 导入表字段
          </el-button>
          <el-button @click="handleManageBindings(selectedForm)">管理绑定</el-button>
          <el-button @click="handleBindNode(selectedForm)">绑定流程节点</el-button>
          <el-button @click="handlePreview">预览</el-button>
          <el-button type="primary" @click="handleSaveForm">保存</el-button>
        </div>
      </div>
      
      <div class="fc-designer-wrapper">
        <fc-designer ref="designerRef" :config="designerConfig" height="calc(100vh - 200px)" />
      </div>
    </div>

    <!-- 创建表单对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建表单" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="表单名" required>
          <el-input v-model="createForm.formName" placeholder="请输入表单名称" />
        </el-form-item>
        <el-form-item label="表单类型">
          <el-select v-model="createForm.formType" style="width: 100%">
            <el-option label="主表单" value="MAIN" />
            <el-option label="子表单" value="SUB" />
            <el-option label="弹出表单" value="POPUP" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定表">
          <el-select v-model="createForm.boundTableId" placeholder="选择要绑定的数据表" style="width: 100%" clearable>
            <el-option 
              v-for="table in store.tables" 
              :key="table.id" 
              :label="`${table.tableName} (${tableTypeLabel(table.tableType)})`" 
              :value="table.id" 
            />
          </el-select>
          <div class="form-item-tip">绑定表后，表单数据将与该表进行关联，支持数据的增删改查</div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 预览对话框 -->
    <el-dialog v-model="showPreviewDialog" title="表单预览" width="800px" destroy-on-close>
      <div class="preview-container">
        <form-create v-if="previewRule.length" v-model="previewData" :rule="previewRule" :option="previewOption" />
        <el-empty v-else description="暂无表单内容" />
      </div>
    </el-dialog>

    <!-- 绑定节点对话框 -->
    <el-dialog v-model="showBindDialog" title="绑定流程节点" width="650px">
      <div class="bind-dialog-content">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
          选择要绑定此表单的流程节点。可以选择多个节点，并设置是否为只读模式。
        </el-alert>
        <div v-if="processNodes.length" class="node-list">
          <div v-for="node in processNodes" :key="node.id" class="node-item">
            <el-checkbox 
              :model-value="isNodeSelected(node.id)"
              @change="toggleNodeSelection(node.id, node.name, $event as boolean)"
            />
            <div class="node-icon" :class="node.type"></div>
            <div class="node-info">
              <div class="node-name">{{ node.name }}</div>
              <div class="node-type">{{ nodeTypeLabel(node.type) }}</div>
            </div>
            <el-checkbox 
              v-if="isNodeSelected(node.id)"
              :model-value="isNodeReadOnly(node.id)"
              @change="setNodeReadOnly(node.id, $event as boolean)"
            >
              只读
            </el-checkbox>
          </div>
        </div>
        <el-empty v-else description="暂无可绑定的流程节点，请先设计流程" />
      </div>
      <template #footer>
        <el-button @click="showBindDialog = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmBind">确定</el-button>
      </template>
    </el-dialog>

    <!-- 从表导入字段对话框 -->
    <el-dialog v-model="showImportFieldsDialog" title="从表导入字段" width="800px">
      <div class="import-fields-dialog">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
          选择表和字段，将自动生成对应的表单控件。字段类型会自动映射为合适的表单组件。
          <span v-if="formBindings.length > 0" style="display: block; margin-top: 4px;">
            当前表单已绑定 {{ formBindings.length }} 个表，可从绑定表中快速选择字段。
          </span>
        </el-alert>
        
        <el-form label-width="80px" style="margin-bottom: 16px;">
          <el-form-item label="选择表">
            <el-select v-model="importTableId" placeholder="请选择表" style="width: 100%;" @change="handleTableChange">
              <el-option-group v-if="formBindings.length > 0" label="已绑定的表">
                <el-option 
                  v-for="binding in formBindings" 
                  :key="binding.tableId" 
                  :label="`${getTableName(binding.tableId)} (${bindingTypeLabel(binding.bindingType)})`" 
                  :value="binding.tableId"
                >
                  <div class="table-option-with-binding">
                    <span>{{ getTableName(binding.tableId) }}</span>
                    <el-tag size="small" :type="bindingTypeTag(binding.bindingType)">
                      {{ bindingTypeLabel(binding.bindingType) }}
                    </el-tag>
                  </div>
                </el-option>
              </el-option-group>
              <el-option-group label="所有表">
                <el-option 
                  v-for="table in store.tables" 
                  :key="table.id" 
                  :label="`${table.tableName} (${tableTypeLabel(table.tableType)})`" 
                  :value="table.id" 
                />
              </el-option-group>
            </el-select>
          </el-form-item>
        </el-form>
        
        <div v-if="importTableId" class="field-selection">
          <div class="field-header">
            <el-checkbox 
              :model-value="isAllFieldsSelected" 
              :indeterminate="isFieldsIndeterminate"
              @change="(val: any) => handleSelectAllFields(!!val)"
            >
              全选
            </el-checkbox>
            <span class="field-count">已选 {{ selectedImportFields.length }} / {{ availableFields.length }} 个字段</span>
            <el-tag v-if="getImportTableBinding()" size="small" :type="bindingTypeTag(getImportTableBinding()!.bindingType)" style="margin-left: 8px;">
              {{ bindingTypeLabel(getImportTableBinding()!.bindingType) }}
            </el-tag>
          </div>
          
          <el-table :data="availableFields" size="small" max-height="300">
            <el-table-column width="50">
              <template #default="{ row }">
                <el-checkbox 
                  :model-value="isFieldSelected(row.fieldName)"
                  @change="toggleFieldSelection(row)"
                />
              </template>
            </el-table-column>
            <el-table-column prop="fieldName" label="字段名" width="150" />
            <el-table-column prop="dataType" label="数据类型" width="100" />
            <el-table-column label="表单组件" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ getFormComponentType(row.dataType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="来源表" width="120" v-if="formBindings.length > 0">
              <template #default>
                <span class="source-table">{{ getTableName(importTableId!) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column prop="nullable" label="必填" width="60">
              <template #default="{ row }">
                <el-tag :type="row.nullable ? 'info' : 'danger'" size="small">
                  {{ row.nullable ? '否' : '是' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
        
        <el-empty v-else description="请先选择一个表" />
      </div>
      <template #footer>
        <el-button @click="showImportFieldsDialog = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmImportFields" :disabled="selectedImportFields.length === 0">
          导入 ({{ selectedImportFields.length }})
        </el-button>
      </template>
    </el-dialog>

    <!-- 管理表绑定对话框 -->
    <el-dialog v-model="showBindingManagerDialog" title="管理表绑定" width="700px" destroy-on-close>
      <TableBindingManager 
        v-if="bindingManagerForm"
        ref="bindingManagerRef"
        :function-unit-id="props.functionUnitId"
        :form-id="bindingManagerForm.id"
        :tables="store.tables"
        @update="handleBindingUpdate"
      />
      <template #footer>
        <el-button @click="showBindingManagerDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import { ArrowLeft, Plus, Refresh, Connection } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import type { FormDefinition, FieldDefinition, TableBinding, BindingType } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import TableBindingManager from './TableBindingManager.vue'

interface ProcessNode {
  id: string
  name: string
  type: string
}

const props = defineProps<{ functionUnitId: number }>()

const store = useFunctionUnitStore()
const loading = ref(false)
const selectedForm = ref<FormDefinition | null>(null)
const designerRef = ref<any>(null)
const showCreateDialog = ref(false)
const showPreviewDialog = ref(false)
const showBindDialog = ref(false)
const previewData = ref({})
const previewRule = ref<any[]>([])
const createForm = reactive({ formName: '', formType: 'MAIN', description: '', boundTableId: null as number | null })
const bindingForm = ref<FormDefinition | null>(null)

// 管理表绑定相关状态
const showBindingManagerDialog = ref(false)
const bindingManagerForm = ref<FormDefinition | null>(null)
const processNodes = ref<ProcessNode[]>([])

// 导入字段相关状态
const showImportFieldsDialog = ref(false)
const importTableId = ref<number | null>(null)
const selectedImportFields = ref<FieldDefinition[]>([])
const formBindings = ref<TableBinding[]>([])

// 计算属性：当前选中表的可用字段
const availableFields = computed(() => {
  if (!importTableId.value) return []
  const table = store.tables.find(t => t.id === importTableId.value)
  return table?.fieldDefinitions || []
})

// 计算属性：是否全选
const isAllFieldsSelected = computed(() => {
  return availableFields.value.length > 0 && 
         selectedImportFields.value.length === availableFields.value.length
})

// 计算属性：是否部分选中
const isFieldsIndeterminate = computed(() => {
  return selectedImportFields.value.length > 0 && 
         selectedImportFields.value.length < availableFields.value.length
})
// 存储从BPMN XML解析出的表单绑定信息（支持多节点）
const formNodeBindings = ref<Map<number, Array<{ nodeId: string; nodeName: string; readOnly: boolean }>>>(new Map())

// 绑定对话框中选中的节点
const selectedBindNodes = ref<Array<{ nodeId: string; nodeName: string; readOnly: boolean }>>([])

// form-create designer 配置
const designerConfig = {
  showDevice: true,
  showSave: false, // 使用自定义保存按钮
  fieldReadonly: false
}

// 预览配置
const previewOption = {
  submitBtn: false,
  resetBtn: false
}

const formTypeLabel = (type: string) => {
  const map: Record<string, string> = { MAIN: '主表单', SUB: '子表单', POPUP: '弹出表单' }
  return map[type] || type
}

const nodeTypeLabel = (type: string) => {
  const map: Record<string, string> = { 
    userTask: '用户任务', 
    serviceTask: '服务任务',
    startEvent: '开始事件',
    endEvent: '结束事件'
  }
  return map[type] || type
}

const tableTypeLabel = (type: string) => {
  const map: Record<string, string> = { MAIN: '主表', SUB: '子表', ACTION: '动作表', RELATION: '关联表' }
  return map[type] || type
}

// 绑定类型标签
const bindingTypeLabel = (type: BindingType): string => {
  const map: Record<BindingType, string> = { PRIMARY: '主表', SUB: '子表', RELATED: '关联表' }
  return map[type] || type
}

// 绑定类型标签颜色
const bindingTypeTag = (type: BindingType): 'primary' | 'success' | 'warning' | 'info' => {
  const map: Record<BindingType, 'primary' | 'success' | 'warning' | 'info'> = { PRIMARY: 'primary', SUB: 'success', RELATED: 'warning' }
  return map[type] || 'info'
}

// 获取当前导入表的绑定信息
function getImportTableBinding(): TableBinding | undefined {
  if (!importTableId.value) return undefined
  return formBindings.value.find(b => b.tableId === importTableId.value)
}

/**
 * 根据表ID获取表名
 */
function getTableName(tableId: number): string {
  const table = store.tables.find(t => t.id === tableId)
  return table?.tableName || '未知表'
}

/**
 * 打开管理表绑定对话框
 */
function handleManageBindings(form: FormDefinition) {
  bindingManagerForm.value = form
  showBindingManagerDialog.value = true
}

/**
 * 表绑定更新回调
 */
function handleBindingUpdate() {
  loadForms()
}

/**
 * 根据数据类型获取对应的表单组件类型
 */
function getFormComponentType(dataType: string): string {
  const typeMap: Record<string, string> = {
    'VARCHAR': '输入框',
    'TEXT': '文本域',
    'INTEGER': '数字输入',
    'BIGINT': '数字输入',
    'DECIMAL': '数字输入',
    'BOOLEAN': '开关',
    'DATE': '日期选择',
    'TIMESTAMP': '日期时间'
  }
  return typeMap[dataType] || '输入框'
}

/**
 * 检查字段是否被选中
 */
function isFieldSelected(fieldName: string): boolean {
  return selectedImportFields.value.some(f => f.fieldName === fieldName)
}

/**
 * 切换字段选中状态
 */
function toggleFieldSelection(field: FieldDefinition) {
  const index = selectedImportFields.value.findIndex(f => f.fieldName === field.fieldName)
  if (index >= 0) {
    selectedImportFields.value.splice(index, 1)
  } else {
    selectedImportFields.value.push({ ...field })
  }
}

/**
 * 全选/取消全选字段
 */
function handleSelectAllFields(checked: boolean) {
  if (checked) {
    selectedImportFields.value = availableFields.value.map(f => ({ ...f }))
  } else {
    selectedImportFields.value = []
  }
}

/**
 * 表切换时重置选中字段
 */
function handleTableChange() {
  selectedImportFields.value = []
}

/**
 * 打开导入字段对话框（列表页面）
 */
async function handleImportFromTable() {
  await store.fetchTables(props.functionUnitId)
  formBindings.value = []
  importTableId.value = null
  selectedImportFields.value = []
  showImportFieldsDialog.value = true
}

/**
 * 打开导入字段对话框（设计器页面）
 */
async function handleImportFieldsToDesigner() {
  await store.fetchTables(props.functionUnitId)
  
  // 加载表单绑定信息
  if (selectedForm.value) {
    try {
      const res = await functionUnitApi.getFormBindings(props.functionUnitId, selectedForm.value.id)
      formBindings.value = res.data || []
    } catch (e) {
      formBindings.value = []
    }
    
    // 如果有主表绑定，自动选中主表
    const primaryBinding = formBindings.value.find(b => b.bindingType === 'PRIMARY')
    if (primaryBinding) {
      importTableId.value = primaryBinding.tableId
    } else if (selectedForm.value.boundTableId) {
      importTableId.value = selectedForm.value.boundTableId
    } else {
      importTableId.value = null
    }
  } else {
    formBindings.value = []
    importTableId.value = null
  }
  
  selectedImportFields.value = []
  showImportFieldsDialog.value = true
}

/**
 * 将数据库字段类型转换为 form-create 规则
 */
function fieldToFormRule(field: FieldDefinition): any {
  const baseRule = {
    field: field.fieldName,
    title: field.description || field.fieldName,
    props: {},
    validate: [] as any[]
  }
  
  // 如果字段不可空，添加必填验证
  if (!field.nullable) {
    baseRule.validate.push({
      required: true,
      message: `${field.description || field.fieldName}不能为空`,
      trigger: 'blur'
    })
  }
  
  // 根据数据类型映射表单组件
  switch (field.dataType) {
    case 'VARCHAR':
      return {
        ...baseRule,
        type: 'input',
        props: {
          placeholder: `请输入${field.description || field.fieldName}`,
          maxlength: field.length || 255,
          showWordLimit: true
        }
      }
    case 'TEXT':
      return {
        ...baseRule,
        type: 'input',
        props: {
          type: 'textarea',
          placeholder: `请输入${field.description || field.fieldName}`,
          rows: 3
        }
      }
    case 'INTEGER':
    case 'BIGINT':
      return {
        ...baseRule,
        type: 'inputNumber',
        props: {
          placeholder: `请输入${field.description || field.fieldName}`,
          precision: 0
        }
      }
    case 'DECIMAL':
      return {
        ...baseRule,
        type: 'inputNumber',
        props: {
          placeholder: `请输入${field.description || field.fieldName}`,
          precision: field.scale || 2
        }
      }
    case 'BOOLEAN':
      return {
        ...baseRule,
        type: 'switch',
        props: {}
      }
    case 'DATE':
      return {
        ...baseRule,
        type: 'datePicker',
        props: {
          type: 'date',
          placeholder: `请选择${field.description || field.fieldName}`,
          valueFormat: 'YYYY-MM-DD'
        }
      }
    case 'TIMESTAMP':
      return {
        ...baseRule,
        type: 'datePicker',
        props: {
          type: 'datetime',
          placeholder: `请选择${field.description || field.fieldName}`,
          valueFormat: 'YYYY-MM-DD HH:mm:ss'
        }
      }
    default:
      return {
        ...baseRule,
        type: 'input',
        props: {
          placeholder: `请输入${field.description || field.fieldName}`
        }
      }
  }
}

/**
 * 确认导入字段到表单设计器
 */
function handleConfirmImportFields() {
  if (selectedImportFields.value.length === 0) {
    ElMessage.warning('请至少选择一个字段')
    return
  }
  
  // 如果在设计器视图中，直接添加到设计器
  if (selectedForm.value && designerRef.value) {
    const rules = selectedImportFields.value.map(fieldToFormRule)
    
    // 获取当前设计器中的规则
    const currentRules = designerRef.value.getRule() || []
    
    // 检查是否有重复字段
    const existingFields = new Set(currentRules.map((r: any) => r.field))
    const newRules = rules.filter(r => !existingFields.has(r.field))
    const duplicateCount = rules.length - newRules.length
    
    if (duplicateCount > 0) {
      ElMessage.warning(`跳过 ${duplicateCount} 个已存在的字段`)
    }
    
    if (newRules.length > 0) {
      // 合并规则
      const mergedRules = [...currentRules, ...newRules]
      designerRef.value.setRule(mergedRules)
      ElMessage.success(`成功导入 ${newRules.length} 个字段`)
    }
  } else {
    // 如果在列表视图，提示用户先选择或创建表单
    ElMessage.info('请先选择或创建一个表单，然后在设计器中导入字段')
  }
  
  showImportFieldsDialog.value = false
}

async function loadForms() {
  loading.value = true
  try {
    await store.fetchForms(props.functionUnitId)
    await store.fetchTables(props.functionUnitId)
    await store.fetchProcess(props.functionUnitId)
    // 解析BPMN XML获取表单绑定信息
    parseFormBindingsFromBpmn()
  } finally {
    loading.value = false
  }
}

/**
 * 从BPMN XML解析表单与节点的绑定关系（支持多节点）
 */
function parseFormBindingsFromBpmn() {
  const bindings = new Map<number, Array<{ nodeId: string; nodeName: string; readOnly: boolean }>>()
  
  if (!store.process?.bpmnXml) {
    formNodeBindings.value = bindings
    return
  }
  
  try {
    const parser = new DOMParser()
    const xmlDoc = parser.parseFromString(store.process.bpmnXml, 'text/xml')
    
    // 查找所有任务节点
    const tasks = xmlDoc.querySelectorAll('userTask, serviceTask')
    
    tasks.forEach(task => {
      const taskId = task.getAttribute('id') || ''
      const taskName = task.getAttribute('name') || taskId
      
      // 查找formId和formReadOnly属性
      const properties = task.querySelectorAll('property')
      let formId: number | null = null
      let readOnly = false
      
      properties.forEach(prop => {
        const name = prop.getAttribute('name')
        const value = prop.getAttribute('value')
        
        if (name === 'formId' && value) {
          formId = parseInt(value, 10)
        }
        if (name === 'formReadOnly' && value === 'true') {
          readOnly = true
        }
      })
      
      if (formId !== null && !isNaN(formId)) {
        if (!bindings.has(formId)) {
          bindings.set(formId, [])
        }
        bindings.get(formId)!.push({ nodeId: taskId, nodeName: taskName, readOnly })
      }
    })
  } catch (e) {
    console.error('Failed to parse BPMN XML:', e)
  }
  
  formNodeBindings.value = bindings
}

/**
 * 获取表单绑定的所有节点信息
 */
function getFormBoundNodes(formId: number): Array<{ nodeId: string; nodeName: string; readOnly: boolean }> {
  return formNodeBindings.value.get(formId) || []
}

/**
 * 检查节点是否被选中
 */
function isNodeSelected(nodeId: string): boolean {
  return selectedBindNodes.value.some(n => n.nodeId === nodeId)
}

/**
 * 检查节点是否为只读
 */
function isNodeReadOnly(nodeId: string): boolean {
  const node = selectedBindNodes.value.find(n => n.nodeId === nodeId)
  return node?.readOnly || false
}

/**
 * 切换节点选中状态
 */
function toggleNodeSelection(nodeId: string, nodeName: string, selected: boolean) {
  if (selected) {
    if (!isNodeSelected(nodeId)) {
      selectedBindNodes.value.push({ nodeId, nodeName, readOnly: false })
    }
  } else {
    selectedBindNodes.value = selectedBindNodes.value.filter(n => n.nodeId !== nodeId)
  }
}

/**
 * 设置节点只读状态
 */
function setNodeReadOnly(nodeId: string, readOnly: boolean) {
  const node = selectedBindNodes.value.find(n => n.nodeId === nodeId)
  if (node) {
    node.readOnly = readOnly
  }
}

async function loadProcessNodes() {
  try {
    await store.fetchProcess(props.functionUnitId)
    if (store.process?.bpmnXml) {
      const parser = new DOMParser()
      const doc = parser.parseFromString(store.process.bpmnXml, 'text/xml')
      const nodes: ProcessNode[] = []
      
      const userTasks = doc.querySelectorAll('userTask')
      userTasks.forEach(task => {
        nodes.push({
          id: task.getAttribute('id') || '',
          name: task.getAttribute('name') || task.getAttribute('id') || '',
          type: 'userTask'
        })
      })
      
      const serviceTasks = doc.querySelectorAll('serviceTask')
      serviceTasks.forEach(task => {
        nodes.push({
          id: task.getAttribute('id') || '',
          name: task.getAttribute('name') || task.getAttribute('id') || '',
          type: 'serviceTask'
        })
      })
      
      processNodes.value = nodes
    } else {
      processNodes.value = []
    }
  } catch {
    processNodes.value = []
  }
}

function handleSelectForm(row: FormDefinition) {
  selectedForm.value = { ...row }
  nextTick(() => {
    if (designerRef.value && row.configJson) {
      // 加载已保存的表单配置到设计器
      const config = row.configJson
      if (config.rule) {
        designerRef.value.setRule(config.rule)
      }
      if (config.options) {
        designerRef.value.setOption(config.options)
      }
    }
  })
}

function handleBackToList() {
  selectedForm.value = null
}

async function handleCreateForm() {
  if (!createForm.formName.trim()) {
    ElMessage.warning('请输入表单名称')
    return
  }
  try {
    await store.createForm(props.functionUnitId, {
      formName: createForm.formName,
      formType: createForm.formType,
      description: createForm.description,
      boundTableId: createForm.boundTableId || undefined,
      configJson: { rule: [], options: {} }
    })
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    Object.assign(createForm, { formName: '', formType: 'MAIN', description: '', boundTableId: null })
    loadForms()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  }
}

async function handleSaveForm() {
  if (!selectedForm.value || !designerRef.value) return
  
  try {
    // 从设计器获取表单配置
    const rule = designerRef.value.getRule()
    const options = designerRef.value.getOption()
    
    await store.updateForm(props.functionUnitId, selectedForm.value.id, {
      formName: selectedForm.value.formName,
      formType: selectedForm.value.formType,
      description: selectedForm.value.description,
      configJson: { rule, options }
    })
    ElMessage.success('保存成功')
    loadForms()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  }
}

async function handleDeleteForm(row: FormDefinition) {
  await ElMessageBox.confirm('确定要删除该表单吗？', '提示', { type: 'warning' })
  try {
    await store.deleteForm(props.functionUnitId, row.id)
    ElMessage.success('删除成功')
    loadForms()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

function handlePreview() {
  if (!designerRef.value) return
  previewRule.value = designerRef.value.getRule()
  previewData.value = {}
  showPreviewDialog.value = true
}

async function handleBindNode(form: FormDefinition) {
  bindingForm.value = form
  // 从BPMN中获取当前绑定信息
  const boundNodes = getFormBoundNodes(form.id)
  selectedBindNodes.value = boundNodes.map(n => ({ ...n }))
  await loadProcessNodes()
  showBindDialog.value = true
}

async function handleConfirmBind() {
  if (!bindingForm.value) return
  try {
    // 更新BPMN XML中的节点formId属性
    if (store.process?.bpmnXml) {
      await updateBpmnFormBindings(bindingForm.value.id, bindingForm.value.formName, selectedBindNodes.value)
    }
    
    ElMessage.success('绑定保存成功')
    showBindDialog.value = false
    
    // 重新加载
    await store.fetchProcess(props.functionUnitId)
    parseFormBindingsFromBpmn()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '绑定失败')
  }
}

/**
 * 更新BPMN XML中多个节点的表单绑定
 */
async function updateBpmnFormBindings(
  formId: number, 
  formName: string, 
  nodes: Array<{ nodeId: string; nodeName: string; readOnly: boolean }>
) {
  if (!store.process?.bpmnXml) return
  
  const parser = new DOMParser()
  const xmlDoc = parser.parseFromString(store.process.bpmnXml, 'text/xml')
  
  // 先从所有节点中移除此表单的绑定
  const allTasks = xmlDoc.querySelectorAll('userTask, serviceTask')
  allTasks.forEach(task => {
    const properties = task.querySelector('extensionElements > properties')
    if (!properties) return
    
    const formIdProp = Array.from(properties.querySelectorAll('property')).find(
      p => p.getAttribute('name') === 'formId'
    )
    if (formIdProp && formIdProp.getAttribute('value') === String(formId)) {
      // 移除formId, formName, formReadOnly
      const propsToRemove = Array.from(properties.querySelectorAll('property')).filter(
        p => ['formId', 'formName', 'formReadOnly'].includes(p.getAttribute('name') || '')
      )
      propsToRemove.forEach(p => p.remove())
    }
  })
  
  // 为选中的节点添加绑定
  for (const node of nodes) {
    const task = xmlDoc.querySelector(`userTask[id="${node.nodeId}"], serviceTask[id="${node.nodeId}"]`)
    if (!task) continue
    
    // 获取或创建extensionElements
    let extensionElements = task.querySelector(':scope > extensionElements')
    if (!extensionElements) {
      extensionElements = xmlDoc.createElementNS('http://www.omg.org/spec/BPMN/20100524/MODEL', 'bpmn:extensionElements')
      task.insertBefore(extensionElements, task.firstChild)
    }
    
    // 获取或创建properties
    let properties = extensionElements.querySelector('properties')
    if (!properties) {
      properties = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:properties')
      extensionElements.appendChild(properties)
    }
    
    // 添加formId
    const formIdProp = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:property')
    formIdProp.setAttribute('name', 'formId')
    formIdProp.setAttribute('value', String(formId))
    properties.appendChild(formIdProp)
    
    // 添加formName
    const formNameProp = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:property')
    formNameProp.setAttribute('name', 'formName')
    formNameProp.setAttribute('value', formName)
    properties.appendChild(formNameProp)
    
    // 如果是只读，添加formReadOnly
    if (node.readOnly) {
      const readOnlyProp = xmlDoc.createElementNS('http://custom.bpmn.io/schema', 'custom:property')
      readOnlyProp.setAttribute('name', 'formReadOnly')
      readOnlyProp.setAttribute('value', 'true')
      properties.appendChild(readOnlyProp)
    }
  }
  
  // 序列化并保存
  const serializer = new XMLSerializer()
  const newXml = serializer.serializeToString(xmlDoc)
  
  await store.saveProcess(props.functionUnitId, {
    ...store.process,
    bpmnXml: newXml
  })
}

onMounted(loadForms)
</script>


<style lang="scss" scoped>
.form-designer {
  height: 100%;
}

.form-list-view {
  padding: 0;
}

.designer-toolbar {
  margin-bottom: 16px;
}

.text-muted {
  color: #909399;
  font-size: 12px;
}

.bound-nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.node-tag {
  margin: 0;
}

.bound-nodes-header {
  display: flex;
  gap: 4px;
}

.form-editor-view {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.editor-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e6e6e6;
  margin-bottom: 16px;
  
  .form-name {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }
  
  .header-actions {
    margin-left: auto;
    display: flex;
    gap: 8px;
  }
}

.fc-designer-wrapper {
  flex: 1;
  overflow: hidden;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  
  :deep(.fc-designer) {
    height: 100% !important;
  }
}

.preview-container {
  min-height: 300px;
  padding: 20px;
}

.bind-dialog-content {
  .node-list {
    max-height: 350px;
    overflow-y: auto;
  }
  
  .node-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border: 1px solid #e6e6e6;
    border-radius: 4px;
    margin-bottom: 8px;
    cursor: pointer;
    transition: all 0.2s;
    
    &:hover {
      border-color: #DB0011;
      background-color: rgba(219, 0, 17, 0.02);
    }
    
    &.selected {
      border-color: #DB0011;
      background-color: rgba(219, 0, 17, 0.08);
    }
    
    .node-icon {
      width: 32px;
      height: 32px;
      border-radius: 4px;
      
      &.userTask { background-color: #409EFF; }
      &.serviceTask { background-color: #67C23A; }
      &.startEvent { background-color: #00A651; border-radius: 50%; }
      &.endEvent { background-color: #DB0011; border-radius: 50%; }
    }
    
    .node-info {
      flex: 1;
      
      .node-name {
        font-weight: 500;
        margin-bottom: 2px;
      }
      
      .node-type {
        font-size: 12px;
        color: #909399;
      }
    }
    
    .check-icon {
      color: #DB0011;
      font-size: 20px;
    }
  }
}

.import-fields-dialog {
  .field-selection {
    .field-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
      padding: 8px 12px;
      background: #f5f7fa;
      border-radius: 4px;
      
      .field-count {
        font-size: 13px;
        color: #909399;
      }
    }
  }
  
  .table-option-with-binding {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;
  }
  
  .source-table {
    font-size: 12px;
    color: #909399;
  }
}

.form-item-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.bound-table-tag {
  margin-left: 8px;
}

.action-buttons {
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  white-space: nowrap;
}

.bind-table-dialog {
  .table-option {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;
  }
  
  .table-fields-preview {
    max-height: 150px;
    overflow-y: auto;
    padding: 8px;
    background: #f5f7fa;
    border-radius: 4px;
  }
}
</style>
