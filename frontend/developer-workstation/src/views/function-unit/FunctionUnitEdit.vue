<template>
  <div class="page-container">
    <div class="card">
      <div class="flex-between" style="margin-bottom: 16px;">
        <div class="flex" style="align-items: center; gap: 16px;">
          <el-button @click="router.back()">
            <el-icon><ArrowLeft /></el-icon>
            {{ t('common.back') }}
          </el-button>
          <el-tooltip :content="store.current?.description || '暂无描述'" placement="bottom">
            <IconPreview 
              :icon-id="store.current?.icon?.id" 
              size="large" 
            />
          </el-tooltip>
          <h3>{{ store.current?.name }}</h3>
          <el-tag :type="statusTagType(store.current?.status)">
            {{ statusLabel(store.current?.status) }}
          </el-tag>
          <span v-if="store.current?.currentVersion" class="version-badge">
            v{{ store.current.currentVersion }}
          </span>
        </div>
        <div>
          <el-button @click="openEditDialog">
            <el-icon><Setting /></el-icon>
            {{ t('functionUnit.settings') }}
          </el-button>
          <el-button @click="handleExport" :loading="exporting">
            <el-icon><Download /></el-icon>
            {{ t('common.export') }}
          </el-button>
          <el-button @click="handleValidate" :loading="validating">{{ t('functionUnit.validate') }}</el-button>
          <el-button type="warning" @click="showDeployDialog = true">
            <el-icon><Upload /></el-icon>
            {{ t('functionUnit.deploy') }}
          </el-button>
          <el-button type="primary" @click="handlePublish">{{ t('functionUnit.publish') }}</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane :label="t('functionUnit.process')" name="process">
          <ProcessDesigner v-if="activeTab === 'process'" :function-unit-id="functionUnitId" />
        </el-tab-pane>
        <el-tab-pane :label="t('functionUnit.tables')" name="tables">
          <TableDesigner v-if="activeTab === 'tables'" :function-unit-id="functionUnitId" />
        </el-tab-pane>
        <el-tab-pane :label="t('functionUnit.forms')" name="forms">
          <FormDesigner v-if="activeTab === 'forms'" :function-unit-id="functionUnitId" />
        </el-tab-pane>
        <el-tab-pane :label="t('functionUnit.actionDesign')" name="actions">
          <ActionDesigner v-if="activeTab === 'actions'" :function-unit-id="functionUnitId" />
        </el-tab-pane>
        <el-tab-pane :label="t('version.title')" name="versions">
          <VersionManager v-if="activeTab === 'versions'" :function-unit-id="functionUnitId" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- Edit Function Unit Dialog -->
    <el-dialog v-model="showEditDialog" :title="t('functionUnit.settings')" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item :label="t('functionUnit.icon')">
          <div class="icon-edit-row">
            <IconPreview :icon-id="editForm.iconId" size="large" />
            <el-button @click="showIconSelectorForEdit = true">{{ t('functionUnit.changeIcon') }}</el-button>
            <el-button v-if="editForm.iconId" link type="danger" @click="editForm.iconId = undefined">{{ t('icon.clear') }}</el-button>
          </div>
        </el-form-item>
        <el-form-item :label="t('functionUnit.name')" required>
          <el-input v-model="editForm.name" :placeholder="t('functionUnit.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('functionUnit.description')">
          <el-input v-model="editForm.description" type="textarea" :rows="3" :placeholder="t('functionUnit.descriptionPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSaveEdit" :loading="saving">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- Validation Result Dialog -->
    <el-dialog v-model="showValidationDialog" title="验证结果" width="500px">
      <div v-if="validationResult">
        <el-result v-if="validationResult.valid" icon="success" title="验证通过" sub-title="功能单元配置完整，可以发布" />
        <div v-else>
          <el-alert v-if="validationResult.errors?.length" type="error" :closable="false" style="margin-bottom: 12px;">
            <template #title>错误 ({{ validationResult.errors.length }})</template>
            <ul style="margin: 8px 0 0 0; padding-left: 20px;">
              <li v-for="(err, i) in validationResult.errors" :key="i">{{ err }}</li>
            </ul>
          </el-alert>
          <el-alert v-if="validationResult.warnings?.length" type="warning" :closable="false">
            <template #title>警告 ({{ validationResult.warnings.length }})</template>
            <ul style="margin: 8px 0 0 0; padding-left: 20px;">
              <li v-for="(warn, i) in validationResult.warnings" :key="i">{{ warn }}</li>
            </ul>
          </el-alert>
        </div>
      </div>
      <template #footer>
        <el-button @click="showValidationDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Icon Selector for edit dialog -->
    <IconSelector 
      :model-value="editForm.iconId" 
      :visible="showIconSelectorForEdit" 
      @update:visible="showIconSelectorForEdit = $event"
      @select="handleIconSelectForEdit"
    />

    <!-- Deploy Dialog -->
    <el-dialog v-model="showDeployDialog" :title="t('functionUnit.deploy')" width="500px">
      <el-form :model="deployForm" label-width="100px">
        <el-form-item :label="t('functionUnit.autoEnable')">
          <el-switch v-model="deployForm.autoEnable" />
          <span style="margin-left: 12px; color: #909399; font-size: 12px;">{{ t('functionUnit.autoEnableHint') }}</span>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
        将功能单元部署到管理中心，部署后可在用户门户中使用
      </el-alert>
      
      <!-- Deploy Status -->
      <div v-if="deployStatus" class="deploy-status">
        <el-divider>部署状态</el-divider>
        <div class="status-header">
          <el-tag :type="getDeployStatusType(deployStatus.status)">
            {{ getDeployStatusLabel(deployStatus.status) }}
          </el-tag>
          <span v-if="deployStatus.progress !== undefined">{{ deployStatus.progress }}%</span>
        </div>
        <el-progress 
          v-if="deployStatus.status === 'DEPLOYING'" 
          :percentage="deployStatus.progress || 0" 
        />
        <el-progress 
          v-else-if="deployStatus.status === 'SUCCESS'" 
          :percentage="100" 
          status="success"
        />
        <el-progress 
          v-else-if="deployStatus.status === 'FAILED'" 
          :percentage="deployStatus.progress || 0" 
          status="exception"
        />
        <div v-if="deployStatus.steps?.length" class="deploy-steps">
          <div v-for="step in deployStatus.steps" :key="step.name" class="step-item">
            <el-icon v-if="step.status === 'SUCCESS'" color="#67c23a"><CircleCheck /></el-icon>
            <el-icon v-else-if="step.status === 'FAILED'" color="#f56c6c"><CircleClose /></el-icon>
            <el-icon v-else-if="step.status === 'RUNNING'" color="#409eff"><Loading /></el-icon>
            <el-icon v-else color="#909399"><Clock /></el-icon>
            <span>{{ step.name }}</span>
            <span v-if="step.message" class="step-message">{{ step.message }}</span>
          </div>
        </div>
        <div v-if="deployStatus.message && deployStatus.status === 'FAILED'" class="error-message">
          {{ deployStatus.message }}
        </div>
      </div>
      
      <template #footer>
        <el-button @click="closeDeployDialog">关闭</el-button>
        <el-button 
          v-if="!deployStatus || (deployStatus.status !== 'SUCCESS' && deployStatus.status !== 'FAILED')"
          type="primary" 
          @click="handleDeploy" 
          :loading="deploying"
          :disabled="deployStatus?.status === 'DEPLOYING'"
        >
          {{ deploying ? '部署中...' : '开始部署' }}
        </el-button>
        <el-button 
          v-if="deployStatus?.status === 'FAILED'"
          type="primary" 
          @click="handleDeploy" 
          :loading="deploying"
        >
          重新部署
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Setting, Download, Upload, CircleCheck, CircleClose, Loading, Clock } from '@element-plus/icons-vue'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import type { ValidationResult, DeployRequest, DeployResponse } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import ProcessDesigner from '@/components/designer/ProcessDesigner.vue'
import TableDesigner from '@/components/designer/TableDesigner.vue'
import FormDesigner from '@/components/designer/FormDesigner.vue'
import ActionDesigner from '@/components/designer/ActionDesigner.vue'
import VersionManager from '@/components/version/VersionManager.vue'
import IconPreview from '@/components/icon/IconPreview.vue'
import IconSelector from '@/components/icon/IconSelector.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const store = useFunctionUnitStore()

const functionUnitId = computed(() => Number(route.params.id))
const activeTab = ref('process')
const validating = ref(false)
const saving = ref(false)
const exporting = ref(false)
const deploying = ref(false)
const showValidationDialog = ref(false)
const showEditDialog = ref(false)
const showDeployDialog = ref(false)
const validationResult = ref<ValidationResult | null>(null)
const showIconSelectorForEdit = ref(false)
const deployStatus = ref<DeployResponse | null>(null)
const deployPollingTimer = ref<number | null>(null)

const deployForm = reactive({
  autoEnable: true
})

const editForm = reactive({
  name: '',
  description: '',
  iconId: undefined as number | undefined
})

const statusTagType = (status?: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' => {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }
  return map[status || ''] || 'info'
}

const statusLabel = (status?: string) => {
  const map: Record<string, string> = { 
    DRAFT: t('functionUnit.draft'), 
    PUBLISHED: t('functionUnit.published'), 
    ARCHIVED: t('functionUnit.archived') 
  }
  return map[status || ''] || status
}

function openEditDialog() {
  editForm.name = store.current?.name || ''
  editForm.description = store.current?.description || ''
  editForm.iconId = store.current?.icon?.id ?? undefined
  showEditDialog.value = true
}

async function handleSaveEdit() {
  if (!editForm.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  saving.value = true
  try {
    await store.update(functionUnitId.value, {
      name: editForm.name,
      description: editForm.description,
      iconId: editForm.iconId
    })
    ElMessage.success('保存成功')
    showEditDialog.value = false
    store.fetchById(functionUnitId.value)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function handleIconSelectForEdit(icon: any) {
  editForm.iconId = icon?.id ?? undefined
}

async function handleValidate() {
  validating.value = true
  try {
    validationResult.value = await store.validate(functionUnitId.value)
    showValidationDialog.value = true
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '验证失败')
  } finally {
    validating.value = false
  }
}

async function handlePublish() {
  try {
    const { value } = await ElMessageBox.prompt('请输入变更日志', '发布功能单元', { 
      inputType: 'textarea',
      inputPlaceholder: '描述本次发布的变更内容...'
    })
    await store.publish(functionUnitId.value, value)
    ElMessage.success('发布成功')
    store.fetchById(functionUnitId.value)
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '发布失败')
    }
  }
}

async function handleExport() {
  exporting.value = true
  try {
    const response = await functionUnitApi.exportFunctionUnit(functionUnitId.value)
    // Create download link
    const blob = new Blob([response as any], { type: 'application/zip' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `function-unit-${store.current?.name || functionUnitId.value}.zip`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

async function handleDeploy() {
  deploying.value = true
  deployStatus.value = null
  try {
    const request: DeployRequest = {
      autoEnable: deployForm.autoEnable
    }
    const response = await functionUnitApi.deploy(functionUnitId.value, request)
    deployStatus.value = response.data
    
    // Start polling for status
    if (response.data.status === 'DEPLOYING') {
      startDeployPolling(response.data.deploymentId)
    } else if (response.data.status === 'SUCCESS') {
      ElMessage.success('部署成功')
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '部署失败')
    deployStatus.value = {
      deploymentId: '',
      status: 'FAILED',
      message: e.response?.data?.message || '部署失败'
    }
  } finally {
    deploying.value = false
  }
}

function startDeployPolling(deploymentId: string) {
  if (deployPollingTimer.value) {
    clearInterval(deployPollingTimer.value)
  }
  
  deployPollingTimer.value = window.setInterval(async () => {
    try {
      const response = await functionUnitApi.getDeploymentStatus(deploymentId)
      deployStatus.value = response.data
      
      if (response.data.status === 'SUCCESS') {
        ElMessage.success('部署成功')
        stopDeployPolling()
      } else if (response.data.status === 'FAILED') {
        ElMessage.error('部署失败: ' + response.data.message)
        stopDeployPolling()
      }
    } catch (e) {
      stopDeployPolling()
    }
  }, 2000)
}

function stopDeployPolling() {
  if (deployPollingTimer.value) {
    clearInterval(deployPollingTimer.value)
    deployPollingTimer.value = null
  }
}

function closeDeployDialog() {
  showDeployDialog.value = false
  // 重置部署状态，以便下次打开时可以重新部署
  deployStatus.value = null
  stopDeployPolling()
}

onMounted(() => {
  store.fetchById(functionUnitId.value)
})

onUnmounted(() => {
  stopDeployPolling()
})

function getDeployStatusType(status: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    PENDING: 'info',
    DEPLOYING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger',
    ROLLED_BACK: 'info'
  }
  return map[status] || 'info'
}

function getDeployStatusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '等待中',
    DEPLOYING: '部署中',
    SUCCESS: '部署成功',
    FAILED: '部署失败',
    ROLLED_BACK: '已回滚'
  }
  return map[status] || status
}
</script>

<style lang="scss" scoped>
.version-badge {
  background-color: #f0f0f0;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #666;
}

.icon-edit-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.deploy-status {
  margin-top: 16px;
  
  .status-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
  }
  
  .deploy-steps {
    margin-top: 12px;
    
    .step-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 0;
      border-bottom: 1px solid #eee;
      
      &:last-child {
        border-bottom: none;
      }
      
      .step-message {
        color: #909399;
        font-size: 12px;
        margin-left: auto;
      }
    }
  }
  
  .error-message {
    margin-top: 12px;
    padding: 12px;
    background-color: #fef0f0;
    border-radius: 4px;
    color: #f56c6c;
    font-size: 13px;
  }
}
</style>
