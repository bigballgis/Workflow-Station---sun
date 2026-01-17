<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.functionUnit') }}</span>
      <el-button type="primary" @click="showImportDialog = true">
        <el-icon><Upload /></el-icon>{{ t('common.import') }}
      </el-button>
    </div>
    
    <el-tabs v-model="activeTab">
      <el-tab-pane :label="t('functionUnit.list')" name="list">
        <el-table :data="functionUnits" stripe v-loading="loading">
          <el-table-column prop="name" :label="t('common.name')" />
          <el-table-column prop="code" :label="t('common.code')" />
          <el-table-column prop="version" :label="t('functionUnit.version')" />
          <el-table-column prop="status" :label="t('common.status')">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.enable')" width="80">
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                :loading="row._enabledLoading"
                @change="() => handleEnabledChange(row, row.enabled)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" :label="t('common.updateTime')" />
          <el-table-column :label="t('common.actions')" width="320">
            <template #default="{ row }">
              <el-button link type="primary" @click="showAccessDialog(row)">{{ t('functionUnit.access') }}</el-button>
              <el-button link type="primary" @click="showDeployDialog(row)">{{ t('functionUnit.deploy') }}</el-button>
              <el-button link type="primary" @click="showVersions(row)">{{ t('functionUnit.versions') }}</el-button>
              <el-button link type="danger" @click="handleRollback(row)">{{ t('functionUnit.rollback') }}</el-button>
              <el-button link type="danger" @click="handleDeleteClick(row)">{{ t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      
      <el-tab-pane :label="t('functionUnit.deploymentRecords')" name="deployments">
        <el-table :data="deployments" stripe>
          <el-table-column prop="functionUnitName" :label="t('menu.functionUnit')" />
          <el-table-column prop="version" :label="t('functionUnit.version')" />
          <el-table-column prop="environment" :label="t('functionUnit.environment')" />
          <el-table-column prop="strategy" :label="t('functionUnit.strategy')" />
          <el-table-column prop="status" :label="t('common.status')">
            <template #default="{ row }">
              <el-tag :type="deployStatusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deployedAt" :label="t('functionUnit.deployedAt')" />
          <el-table-column prop="deployedBy" :label="t('functionUnit.deployedBy')" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
    
    <el-dialog v-model="showImportDialog" :title="t('functionUnit.importPackage')" width="500px">
      <el-upload drag :auto-upload="false" accept=".zip" :limit="1">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">{{ t('functionUnit.dragPackageHere') }}<em>{{ t('functionUnit.clickToUpload') }}</em></div>
        <template #tip>
          <div class="el-upload__tip">{{ t('functionUnit.zipFormatTip') }}</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="showImportDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary">{{ t('functionUnit.startImport') }}</el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="showDeployDialogVisible" :title="t('functionUnit.deployFunctionUnit')" width="500px">
      <el-form label-width="100px">
        <el-form-item :label="t('functionUnit.targetEnvironment')">
          <el-select v-model="deployForm.environment">
            <el-option :label="t('functionUnit.envDev')" value="DEV" />
            <el-option :label="t('functionUnit.envTest')" value="TEST" />
            <el-option :label="t('functionUnit.envStaging')" value="STAGING" />
            <el-option :label="t('functionUnit.envProd')" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('functionUnit.deployStrategy')">
          <el-select v-model="deployForm.strategy">
            <el-option :label="t('functionUnit.strategyFull')" value="FULL" />
            <el-option :label="t('functionUnit.strategyIncremental')" value="INCREMENTAL" />
            <el-option :label="t('functionUnit.strategyCanary')" value="CANARY" />
            <el-option :label="t('functionUnit.strategyBlueGreen')" value="BLUE_GREEN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDeployDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleDeploy">{{ t('functionUnit.confirmDeploy') }}</el-button>
      </template>
    </el-dialog>
    
    <!-- Access Config Dialog -->
    <el-dialog v-model="showAccessDialogVisible" :title="t('functionUnit.accessConfig')" width="700px">
      <div class="access-config-header">
        <span>{{ t('menu.functionUnit') }}: {{ currentUnit?.name }}</span>
        <el-button type="primary" size="small" @click="showAddAccessDialog">
          <el-icon><Plus /></el-icon>{{ t('functionUnit.addBusinessRole') }}
        </el-button>
      </div>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        {{ t('functionUnit.accessConfigHint') }}
      </el-alert>
      <el-table :data="accessConfigs" stripe v-loading="accessLoading" :empty-text="t('functionUnit.noAccessConfig')">
        <el-table-column prop="roleName" :label="t('functionUnit.businessRole')" />
        <el-table-column prop="createdAt" :label="t('common.createTime')" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="80">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleRemoveAccess(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showAccessDialogVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>
    
    <!-- Add Business Role Dialog -->
    <el-dialog v-model="showAddAccessDialogVisible" :title="t('functionUnit.selectBusinessRole')" width="500px">
      <el-form :model="accessForm" label-width="100px">
        <el-form-item :label="t('functionUnit.businessRole')" required>
          <el-select v-model="accessForm.roleId" filterable :placeholder="t('functionUnit.selectBusinessRole')" @change="handleRoleChange">
            <el-option v-for="role in businessRoles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddAccessDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleAddAccess" :loading="addAccessLoading">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
    
    <!-- Delete Confirm Dialog -->
    <DeleteConfirmDialog
      v-model="showDeleteDialogVisible"
      :function-unit="deleteTargetUnit"
      :preview="deletePreview"
      @confirm="handleDeleteConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { functionUnitApi, type FunctionUnit, type Deployment, type FunctionUnitAccess, type DeletePreviewResponse } from '@/api/functionUnit'
import { roleApi, type Role } from '@/api/role'
import DeleteConfirmDialog from './components/DeleteConfirmDialog.vue'

const { t } = useI18n()

const activeTab = ref('list')
const showImportDialog = ref(false)
const showDeployDialogVisible = ref(false)
const showAccessDialogVisible = ref(false)
const showAddAccessDialogVisible = ref(false)
const showDeleteDialogVisible = ref(false)
const currentUnit = ref<FunctionUnit | null>(null)
const deleteTargetUnit = ref<FunctionUnit | null>(null)
const deletePreview = ref<DeletePreviewResponse | null>(null)
const deployForm = reactive({ environment: 'DEVELOPMENT' as const, strategy: 'FULL' as const })
const loading = ref(false)
const deploymentsLoading = ref(false)
const accessLoading = ref(false)
const addAccessLoading = ref(false)

const functionUnits = ref<FunctionUnit[]>([])
const deployments = ref<Deployment[]>([])
const accessConfigs = ref<FunctionUnitAccess[]>([])
const businessRoles = ref<Role[]>([])

const accessForm = reactive({
  roleId: '',
  roleName: ''
})

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'
const statusType = (status: string): TagType => ({ DEPLOYED: 'success', VALIDATED: 'primary', DRAFT: 'warning', DEPRECATED: 'info' }[status] as TagType || 'info')
const getStatusText = (status: string) => {
  const statusMap: Record<string, string> = {
    DEPLOYED: t('functionUnit.statusDeployed'),
    VALIDATED: t('functionUnit.statusValidated'),
    DRAFT: t('functionUnit.statusDraft'),
    DEPRECATED: t('functionUnit.statusDeprecated')
  }
  return statusMap[status] || status
}
const deployStatusType = (status: string): TagType => ({ COMPLETED: 'success', EXECUTING: 'warning', PENDING: 'info', APPROVED: 'primary', FAILED: 'danger', ROLLED_BACK: 'danger', CANCELLED: 'info' }[status] as TagType || 'info')

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

const fetchFunctionUnits = async () => {
  loading.value = true
  try {
    const result = await functionUnitApi.list()
    // Add _enabledLoading property to each function unit
    functionUnits.value = result.content.map(unit => ({
      ...unit,
      enabled: unit.enabled !== false, // Default to true
      _enabledLoading: false
    }))
  } catch (e) {
    console.error('Failed to load function units:', e)
    ElMessage.error(t('functionUnit.loadFailed'))
  } finally {
    loading.value = false
  }
}

const fetchDeployments = async () => {
  if (!currentUnit.value) return
  deploymentsLoading.value = true
  try {
    deployments.value = await functionUnitApi.getDeploymentHistory(currentUnit.value.id)
  } catch (e) {
    console.error('Failed to load deployments:', e)
  } finally {
    deploymentsLoading.value = false
  }
}

const fetchAccessConfigs = async () => {
  if (!currentUnit.value) return
  accessLoading.value = true
  try {
    accessConfigs.value = await functionUnitApi.getAccessConfigs(currentUnit.value.id)
  } catch (e) {
    console.error('Failed to load access configs:', e)
    ElMessage.error(t('functionUnit.loadAccessFailed'))
  } finally {
    accessLoading.value = false
  }
}

const showDeployDialog = (unit: FunctionUnit) => { currentUnit.value = unit; showDeployDialogVisible.value = true }
const showVersions = async (unit: FunctionUnit) => {
  try {
    const versions = await functionUnitApi.getVersionHistory(unit.code)
    ElMessage.info(t('functionUnit.versionCount', { name: unit.name, count: versions.length }))
  } catch (e) {
    console.error('Failed to load versions:', e)
  }
}

const showAccessDialog = async (unit: FunctionUnit) => {
  currentUnit.value = unit
  showAccessDialogVisible.value = true
  await fetchAccessConfigs()
}

const showAddAccessDialog = async () => {
  accessForm.roleId = ''
  accessForm.roleName = ''
  showAddAccessDialogVisible.value = true
  
  // Load business roles list
  try {
    businessRoles.value = await roleApi.getBusinessRoles()
  } catch (e) {
    console.error('Failed to load business roles:', e)
    ElMessage.error(t('functionUnit.loadRolesFailed'))
  }
}

const handleRoleChange = (roleId: string) => {
  const role = businessRoles.value.find(r => r.id === roleId)
  accessForm.roleName = role?.name || ''
}

const handleAddAccess = async () => {
  if (!accessForm.roleId) {
    ElMessage.warning(t('functionUnit.selectBusinessRole'))
    return
  }
  if (!currentUnit.value) return
  
  addAccessLoading.value = true
  try {
    await functionUnitApi.addAccessConfig(currentUnit.value.id, {
      roleId: accessForm.roleId,
      roleName: accessForm.roleName
    })
    ElMessage.success(t('common.success'))
    showAddAccessDialogVisible.value = false
    await fetchAccessConfigs()
  } catch (e: any) {
    console.error('Failed to add access config:', e)
    ElMessage.error(e.response?.data?.message || t('common.failed'))
  } finally {
    addAccessLoading.value = false
  }
}

const handleRemoveAccess = async (access: FunctionUnitAccess) => {
  if (!currentUnit.value) return
  
  await ElMessageBox.confirm(t('functionUnit.removeAccessConfirm', { role: access.roleName }), t('common.confirm'), { type: 'warning' })
  
  try {
    await functionUnitApi.removeAccessConfig(currentUnit.value.id, access.id)
    ElMessage.success(t('common.success'))
    await fetchAccessConfigs()
  } catch (e) {
    console.error('Failed to remove access config:', e)
    ElMessage.error(t('common.failed'))
  }
}

const handleDeploy = async () => {
  if (!currentUnit.value) return
  try {
    await functionUnitApi.createDeployment(currentUnit.value.id, deployForm.environment, deployForm.strategy)
    ElMessage.success(t('functionUnit.deploySubmitted'))
    showDeployDialogVisible.value = false
    fetchFunctionUnits()
  } catch (e) {
    console.error('Failed to create deployment:', e)
    ElMessage.error(t('functionUnit.deployFailed'))
  }
}

const handleRollback = async (unit: FunctionUnit) => {
  await ElMessageBox.confirm(t('functionUnit.rollbackConfirm', { name: unit.name }), t('common.confirm'), { type: 'warning' })
  try {
    const deploymentHistory = await functionUnitApi.getDeploymentHistory(unit.id)
    const lastDeployment = deploymentHistory.find(d => d.status === 'COMPLETED')
    if (lastDeployment) {
      await functionUnitApi.rollbackDeployment(lastDeployment.id, t('functionUnit.manualRollback'))
      ElMessage.success(t('functionUnit.rollbackSuccess'))
      fetchFunctionUnits()
    } else {
      ElMessage.warning(t('functionUnit.noRollbackRecord'))
    }
  } catch (e) {
    console.error('Failed to rollback:', e)
    ElMessage.error(t('functionUnit.rollbackFailed'))
  }
}

// ==================== Enable/Disable Feature ====================

const handleEnabledChange = async (unit: FunctionUnit & { _enabledLoading?: boolean }, enabled: boolean) => {
  // Confirm before disabling
  if (!enabled) {
    try {
      await ElMessageBox.confirm(
        t('functionUnit.disableConfirmMessage', { name: unit.name }),
        t('functionUnit.confirmDisable'),
        { type: 'warning' }
      )
    } catch {
      // User cancelled, restore switch state
      unit.enabled = true
      return
    }
  }
  
  unit._enabledLoading = true
  try {
    await functionUnitApi.setEnabled(unit.id, enabled)
    ElMessage.success(enabled ? t('functionUnit.enabledSuccess') : t('functionUnit.disabledSuccess'))
  } catch (e) {
    console.error('Failed to set enabled:', e)
    ElMessage.error(t('common.failed'))
    // Restore switch state
    unit.enabled = !enabled
  } finally {
    unit._enabledLoading = false
  }
}

// ==================== Delete Feature ====================

const handleDeleteClick = async (unit: FunctionUnit) => {
  deleteTargetUnit.value = unit
  
  // Get delete preview
  try {
    deletePreview.value = await functionUnitApi.getDeletePreview(unit.id)
    showDeleteDialogVisible.value = true
  } catch (e) {
    console.error('Failed to get delete preview:', e)
    ElMessage.error(t('functionUnit.getDeletePreviewFailed'))
  }
}

const handleDeleteConfirm = async () => {
  if (!deleteTargetUnit.value) return
  
  try {
    await functionUnitApi.delete(deleteTargetUnit.value.id)
    ElMessage.success(t('functionUnit.deleteSuccess'))
    showDeleteDialogVisible.value = false
    fetchFunctionUnits()
  } catch (e: any) {
    console.error('Failed to delete:', e)
    ElMessage.error(e.response?.data?.message || t('functionUnit.deleteFailed'))
  }
}

onMounted(() => {
  fetchFunctionUnits()
})
</script>

<style scoped>
.access-config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
</style>
