<template>
  <div class="action-designer">
    <div class="designer-toolbar">
      <el-button type="primary" @click="showCreateDialog = true">创建动作</el-button>
    </div>
    
    <div class="action-list" v-if="!selectedAction">
      <el-table :data="actions" v-loading="loading" stripe @row-click="handleSelectAction">
        <el-table-column prop="actionName" :label="$t('action.actionName')" />
        <el-table-column prop="actionType" :label="$t('action.actionType')" width="120">
          <template #default="{ row }">
            <el-tag>{{ actionTypeLabel(row.actionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleSelectAction(row)">编辑</el-button>
            <el-button link type="success" @click.stop="handleTestAction(row)">测试</el-button>
            <el-button link type="danger" @click.stop="handleDeleteAction(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="action-editor" v-else>
      <div class="editor-header">
        <el-button @click="selectedAction = null">
          <el-icon><ArrowLeft /></el-icon> 返回列表
        </el-button>
        <span class="action-name">{{ selectedAction.actionName }}</span>
        <el-button type="success" @click="handleTestAction(selectedAction)">{{ $t('action.test') }}</el-button>
      </div>
      
      <el-form :model="selectedAction" label-width="100px" style="max-width: 600px;">
        <el-form-item :label="$t('action.actionName')">
          <el-input v-model="selectedAction.actionName" />
        </el-form-item>
        <el-form-item :label="$t('action.actionType')">
          <el-select v-model="selectedAction.actionType">
            <el-option label="API调用" value="API_CALL" />
            <el-option label="表单弹出" value="FORM_POPUP" />
            <el-option label="流程提交" value="PROCESS_SUBMIT" />
            <el-option label="流程驳回" value="PROCESS_REJECT" />
            <el-option label="自定义脚本" value="CUSTOM_SCRIPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="按钮颜色">
          <el-color-picker v-model="selectedAction.buttonColor" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="selectedAction.description" type="textarea" />
        </el-form-item>
        
        <!-- API Call Config -->
        <template v-if="selectedAction.actionType === 'API_CALL'">
          <el-divider>API配置</el-divider>
          <el-form-item label="请求URL">
            <el-input v-model="selectedAction.configJson.url" />
          </el-form-item>
          <el-form-item label="请求方法">
            <el-select v-model="selectedAction.configJson.method">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="DELETE" value="DELETE" />
            </el-select>
          </el-form-item>
        </template>
        
        <!-- Form Popup Config -->
        <template v-if="selectedAction.actionType === 'FORM_POPUP'">
          <el-divider>表单配置</el-divider>
          <el-form-item label="关联表单">
            <el-select v-model="selectedAction.configJson.formId">
              <el-option v-for="form in forms" :key="form.id" 
                         :label="form.formName" :value="form.id" />
            </el-select>
          </el-form-item>
        </template>
      </el-form>
    </div>

    <!-- Create Action Dialog -->
    <el-dialog v-model="showCreateDialog" title="创建动作" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="动作名" required>
          <el-input v-model="createForm.actionName" />
        </el-form-item>
        <el-form-item label="动作类型">
          <el-select v-model="createForm.actionType">
            <el-option label="API调用" value="API_CALL" />
            <el-option label="表单弹出" value="FORM_POPUP" />
            <el-option label="流程提交" value="PROCESS_SUBMIT" />
            <el-option label="流程驳回" value="PROCESS_REJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateAction">确定</el-button>
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

const actions = ref<any[]>([])
const forms = ref<any[]>([])
const loading = ref(false)
const selectedAction = ref<any>(null)
const showCreateDialog = ref(false)
const createForm = reactive({ actionName: '', actionType: 'API_CALL', description: '' })

const actionTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    API_CALL: 'API调用',
    FORM_POPUP: '表单弹出',
    PROCESS_SUBMIT: '流程提交',
    PROCESS_REJECT: '流程驳回',
    CUSTOM_SCRIPT: '自定义脚本'
  }
  return map[type] || type
}

async function loadActions() {
  loading.value = true
  try {
    const res = await api.get(`/function-units/${props.functionUnitId}/actions`)
    actions.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadForms() {
  const res = await api.get(`/function-units/${props.functionUnitId}/forms`)
  forms.value = res.data || []
}

function handleSelectAction(row: any) {
  selectedAction.value = { ...row, configJson: row.configJson || {} }
}

async function handleCreateAction() {
  await api.post(`/function-units/${props.functionUnitId}/actions`, createForm)
  ElMessage.success('创建成功')
  showCreateDialog.value = false
  Object.assign(createForm, { actionName: '', actionType: 'API_CALL', description: '' })
  loadActions()
}

async function handleDeleteAction(row: any) {
  await ElMessageBox.confirm('确定要删除该动作吗？', '提示', { type: 'warning' })
  await api.delete(`/function-units/${props.functionUnitId}/actions/${row.id}`)
  ElMessage.success('删除成功')
  loadActions()
}

async function handleTestAction(row: any) {
  ElMessage.info('动作测试功能开发中')
}

onMounted(() => {
  loadActions()
  loadForms()
})
</script>

<style lang="scss" scoped>
.action-designer {
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

.action-name {
  flex: 1;
  font-size: 18px;
  font-weight: bold;
}
</style>
