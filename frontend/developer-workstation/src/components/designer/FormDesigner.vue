<template>
  <div class="form-designer">
    <div class="designer-toolbar">
      <el-button type="primary" @click="showCreateDialog = true">创建表单</el-button>
    </div>
    
    <div class="form-list" v-if="!selectedForm">
      <el-table :data="forms" v-loading="loading" stripe @row-click="handleSelectForm">
        <el-table-column prop="formName" :label="$t('form.formName')" />
        <el-table-column prop="formType" :label="$t('form.formType')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.formType === 'MAIN' ? 'primary' : 'info'">
              {{ formTypeLabel(row.formType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleSelectForm(row)">编辑</el-button>
            <el-button link type="danger" @click.stop="handleDeleteForm(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="form-editor" v-else>
      <div class="editor-header">
        <el-button @click="selectedForm = null">
          <el-icon><ArrowLeft /></el-icon> 返回列表
        </el-button>
        <span class="form-name">{{ selectedForm.formName }}</span>
        <el-button type="primary" @click="handlePreview">{{ $t('form.preview') }}</el-button>
      </div>
      
      <div class="designer-layout">
        <div class="component-panel">
          <h4>组件库</h4>
          <div class="component-list">
            <div class="component-item" v-for="comp in components" :key="comp.type"
                 draggable="true" @dragstart="handleDragStart(comp)">
              <el-icon><component :is="comp.icon" /></el-icon>
              <span>{{ comp.label }}</span>
            </div>
          </div>
        </div>
        <div class="form-canvas">
          <div class="canvas-placeholder" v-if="!selectedForm.configJson?.fields?.length">
            拖拽组件到此处
          </div>
          <div v-else class="form-preview">
            <el-form label-width="100px">
              <el-form-item v-for="field in selectedForm.configJson.fields" :key="field.name"
                            :label="field.label">
                <el-input v-if="field.type === 'input'" :placeholder="field.placeholder" />
                <el-select v-else-if="field.type === 'select'" :placeholder="field.placeholder">
                  <el-option v-for="opt in field.options" :key="opt.value" 
                             :label="opt.label" :value="opt.value" />
                </el-select>
                <el-date-picker v-else-if="field.type === 'date'" type="date" />
                <el-input v-else-if="field.type === 'textarea'" type="textarea" />
              </el-form-item>
            </el-form>
          </div>
        </div>
        <div class="property-panel">
          <h4>属性配置</h4>
          <div v-if="selectedField" class="property-form">
            <el-form label-position="top" size="small">
              <el-form-item label="字段名">
                <el-input v-model="selectedField.name" />
              </el-form-item>
              <el-form-item label="标签">
                <el-input v-model="selectedField.label" />
              </el-form-item>
              <el-form-item label="占位符">
                <el-input v-model="selectedField.placeholder" />
              </el-form-item>
              <el-form-item label="必填">
                <el-switch v-model="selectedField.required" />
              </el-form-item>
            </el-form>
          </div>
          <div v-else class="no-selection">请选择一个组件</div>
        </div>
      </div>
    </div>

    <!-- Create Form Dialog -->
    <el-dialog v-model="showCreateDialog" title="创建表单" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="表单名" required>
          <el-input v-model="createForm.formName" />
        </el-form-item>
        <el-form-item label="表单类型">
          <el-select v-model="createForm.formType">
            <el-option label="主表单" value="MAIN" />
            <el-option label="子表单" value="SUB" />
            <el-option label="弹出表单" value="POPUP" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ArrowLeft, Edit, Select, Calendar, Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'

const props = defineProps<{ functionUnitId: number }>()

const forms = ref<any[]>([])
const loading = ref(false)
const selectedForm = ref<any>(null)
const selectedField = ref<any>(null)
const showCreateDialog = ref(false)
const createForm = reactive({ formName: '', formType: 'MAIN', description: '' })

const components = [
  { type: 'input', label: '输入框', icon: Edit },
  { type: 'select', label: '下拉框', icon: Select },
  { type: 'date', label: '日期', icon: Calendar },
  { type: 'textarea', label: '文本域', icon: Document }
]

const formTypeLabel = (type: string) => {
  const map: Record<string, string> = { MAIN: '主表单', SUB: '子表单', POPUP: '弹出表单' }
  return map[type] || type
}

async function loadForms() {
  loading.value = true
  try {
    const res = await api.get(`/function-units/${props.functionUnitId}/forms`)
    forms.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleSelectForm(row: any) {
  selectedForm.value = { ...row, configJson: row.configJson || { fields: [] } }
}

async function handleCreateForm() {
  await api.post(`/function-units/${props.functionUnitId}/forms`, createForm)
  ElMessage.success('创建成功')
  showCreateDialog.value = false
  Object.assign(createForm, { formName: '', formType: 'MAIN', description: '' })
  loadForms()
}

async function handleDeleteForm(row: any) {
  await ElMessageBox.confirm('确定要删除该表单吗？', '提示', { type: 'warning' })
  await api.delete(`/function-units/${props.functionUnitId}/forms/${row.id}`)
  ElMessage.success('删除成功')
  loadForms()
}

function handleDragStart(comp: any) {
  // TODO: Implement drag
}

function handlePreview() {
  ElMessage.info('预览功能开发中')
}

onMounted(loadForms)
</script>

<style lang="scss" scoped>
.form-designer {
  min-height: 400px;
}

.designer-toolbar {
  margin-bottom: 16px;
}

.editor-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.form-name {
  flex: 1;
  font-size: 18px;
  font-weight: bold;
}

.designer-layout {
  display: flex;
  height: 500px;
  border: 1px solid #e6e6e6;
}

.component-panel {
  width: 200px;
  padding: 10px;
  border-right: 1px solid #e6e6e6;
}

.component-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.component-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  cursor: grab;
  
  &:hover {
    background-color: #f5f7fa;
  }
}

.form-canvas {
  flex: 1;
  padding: 20px;
  background-color: #fafafa;
}

.canvas-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  border: 2px dashed #e6e6e6;
  border-radius: 4px;
}

.property-panel {
  width: 280px;
  padding: 10px;
  border-left: 1px solid #e6e6e6;
}

.no-selection {
  color: #909399;
  text-align: center;
  padding: 20px;
}
</style>
