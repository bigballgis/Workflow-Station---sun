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
        <div style="margin-bottom: 16px; display: flex; align-items: center; gap: 12px;">
          <el-input
            v-model="searchKeyword"
            :placeholder="t('functionUnit.searchPlaceholder')"
            clearable
            style="width: 300px;"
          />
          <template v-if="selectedUnits.length > 0">
            <span style="color: #909399; font-size: 13px;">{{ t('functionUnit.selected', { count: selectedUnits.length }) }}</span>
            <el-button type="success" size="small" @click="handleBatchEnable">{{ t('functionUnit.batchEnable') }}</el-button>
            <el-button type="warning" size="small" @click="handleBatchDisable">{{ t('functionUnit.batchDisable') }}</el-button>
            <el-button type="danger" size="small" @click="handleBatchDelete">{{ t('functionUnit.batchDelete') }}</el-button>
          </template>
        </div>
        <el-table :data="filteredFunctionUnits" stripe v-loading="loading" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="50" />
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
          <el-table-column :label="t('common.actions')" width="360" fixed="right">
            <template #default="{ row }">
              <div style="display: flex; align-items: center; flex-wrap: nowrap; white-space: nowrap;">
                <el-button link type="primary" @click="showAccessDialog(row)">{{ t('functionUnit.access') }}</el-button>
                <el-button link type="primary" @click="showDeployDialog(row)">{{ t('functionUnit.deploy') }}</el-button>
                <el-button link type="primary" @click="showVersions(row)">{{ t('functionUnit.versions') }}</el-button>
                <el-button link type="danger" @click="handleRollback(row)">{{ t('functionUnit.rollback') }}</el-button>
                <el-button link type="danger" @click="handleDeleteClick(row)">{{ t('common.delete') }}</el-button>
              </div>
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
          <el-table-column :label="t('common.actions')" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleViewLog(row)">{{ t('deployment.viewLog') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
    
    <el-dialog v-model="showImportDialog" :title="t('functionUnit.importPackage')" width="500px">
      <el-upload drag :auto-upload="false" accept=".zip" :limit="1" ref="importUploadRef" :on-change="handleImportFileChange">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">{{ t('functionUnit.dragPackageHere') }}<em>{{ t('functionUnit.clickToUpload') }}</em></div>
        <template #tip>
          <div class="el-upload__tip">{{ t('functionUnit.zipFormatTip') }}</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="showImportDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="importLoading" :disabled="!importFile" @click="handleStartImport">{{ t('functionUnit.startImport') }}</el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="showDeployDialogVisible" :title="t('functionUnit.deployFunctionUnit')" width="500px">
      <el-form label-width="160px" label-position="left">
        <el-form-item :label="t('functionUnit.targetEnvironment')">
          <el-select v-model="deployForm.environment" style="width: 100%">
            <el-option :label="t('functionUnit.envDev')" value="DEV" />
            <el-option :label="t('functionUnit.envTest')" value="TEST" />
            <el-option :label="t('functionUnit.envStaging')" value="STAGING" />
            <el-option :label="t('functionUnit.envProd')" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('functionUnit.deployStrategy')">
          <el-select v-model="deployForm.strategy" style="width: 100%">
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
      <el-form :model="accessForm" label-width="120px" label-position="left">
        <el-form-item :label="t('functionUnit.businessRole')" required>
          <el-select v-model="accessForm.roleId" filterable :placeholder="t('functionUnit.selectBusinessRole')" @change="handleRoleChange" style="width: 100%">
            <el-option v-for="role in businessRoles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddAccessDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleAddAccess" :loading="addAccessLoading">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
    
    <!-- Version History Dialog -->
    <el-dialog v-model="showVersionsDialogVisible" :title="t('functionUnit.versions') + ' - ' + (currentUnit?.name || '')" width="800px">
      <el-table :data="versionList" stripe v-loading="versionsLoading">
        <el-table-column prop="name" :label="t('common.name')" />
        <el-table-column prop="version" :label="t('functionUnit.version')" width="120" />
        <el-table-column prop="status" :label="t('common.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.enable')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? t('common.yes') : t('common.no') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="t('common.updateTime')" />
        <el-table-column :label="t('common.actions')" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleCompareVersion(row)">{{ t('version.compare') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showVersionsDialogVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>
    
    <!-- Delete Confirm Dialog -->
    <DeleteConfirmDialog
      v-model="showDeleteDialogVisible"
      :function-unit="deleteTargetUnit"
      :preview="deletePreview"
      @confirm="handleDeleteConfirm"
    />

    <!-- Deployment Log Dialog -->
    <el-dialog v-model="showLogDialogVisible" :title="t('deployment.viewLog')" width="600px">
      <div v-if="logDeployment">
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('menu.functionUnit')">{{ logDeployment.functionUnitName }}</el-descriptions-item>
          <el-descriptions-item :label="t('functionUnit.environment')">{{ logDeployment.environment }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.status')">
            <el-tag :type="deployStatusType(logDeployment.status)">{{ logDeployment.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('functionUnit.deployedAt')">{{ logDeployment.deployedAt || logDeployment.createdAt }}</el-descriptions-item>
          <el-descriptions-item :label="t('functionUnit.deployedBy')">{{ logDeployment.deployedBy }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="showLogDialogVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <!-- Version Compare Dialog -->
    <el-dialog v-model="showCompareDialogVisible" :title="t('version.compare')" width="700px">
      <div v-if="compareVersion">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('functionUnit.version')">{{ compareVersion.version }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.status')">
            <el-tag :type="statusType(compareVersion.status)">{{ getStatusText(compareVersion.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.updateTime')">{{ compareVersion.updatedAt }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.enable')">{{ compareVersion.enabled ? t('common.yes') : t('common.no') }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="showCompareDialogVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { functionUnitApi, type FunctionUnit, type Deployment, type FunctionUnitAccess, type DeletePreviewResponse } from '@/api/functionUnit'
import { roleApi, type Role } from '@/api/role'
import DeleteConfirmDialog from './components/DeleteConfirmDialog.vue'

const { t } = useI18n()

const activeTab = ref('list')

watch(activeTab, (tab) => {
  if (tab === 'deployments') {
    fetchDeployments()
  }
})
const showImportDialog = ref(false)
const showDeployDialogVisible = ref(false)
const showAccessDialogVisible = ref(false)
const showAddAccessDialogVisible = ref(false)
const showDeleteDialogVisible = ref(false)
const showVersionsDialogVisible = ref(false)
const currentUnit = ref<FunctionUnit | null>(null)
const deleteTargetUnit = ref<FunctionUnit | null>(null)
const deletePreview = ref<DeletePreviewResponse | null>(null)
const deployForm = reactive({ environment: 'DEVELOPMENT' as const, strategy: 'FULL' as const })
const loading = ref(false)
const deploymentsLoading = ref(false)
const accessLoading = ref(false)
const addAccessLoading = ref(false)
const versionsLoading = ref(false)
const importLoading = ref(false)
const importFile = ref<File | null>(null)
const importUploadRef = ref<any>(null)

// Batch operation state
const selectedUnits = ref<FunctionUnit[]>([])

// Deployment log dialog
const showLogDialogVisible = ref(false)
const logDeployment = ref<Deployment | null>(null)

// Version compare dialog
const showCompareDialogVisible = ref(false)
const compareVersion = ref<FunctionUnit | null>(null)

const functionUnits = ref<FunctionUnit[]>([])
const searchKeyword = ref('')
const filteredFunctionUnits = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return functionUnits.value
  return functionUnits.value.filter(unit =>
    (unit.name?.toLowerCase().includes(keyword)) ||
    (unit.code?.toLowerCase().includes(keyword)) ||
    (unit.description?.toLowerCase().includes(keyword))
  )
})
const deployments = ref<Deployment[]>([])
const accessConfigs = ref<FunctionUnitAccess[]>([])
const businessRoles = ref<Role[]>([])
const versionList = ref<FunctionUnit[]>([])

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

/**
 * 比较两个语义化版本号，返回正数表示 a > b
 */
const compareVersions = (a: string, b: string): number => {
  const pa = (a || '0.0.0').split('.').map(Number)
  const pb = (b || '0.0.0').split('.').map(Number)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const na = pa[i] || 0
    const nb = pb[i] || 0
    if (na !== nb) return na - nb
  }
  return 0
}

/**
 * 按 code 分组，每组只保留版本号最高的记录
 */
const deduplicateByCode = <T extends { code: string; version: string }>(units: T[]): T[] => {
  const map = new Map<string, T>()
  for (const unit of units) {
    const existing = map.get(unit.code)
    if (!existing || compareVersions(unit.version, existing.version) > 0) {
      map.set(unit.code, unit)
    }
  }
  return Array.from(map.values())
}

const fetchFunctionUnits = async () => {
  loading.value = true
  try {
    const result = await functionUnitApi.list()
    // Deduplicate by code, keeping only the latest version per code
    const deduplicated = deduplicateByCode(result.content)
    // Add _enabledLoading property to each function unit
    functionUnits.value = deduplicated.map(unit => ({
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
  deploymentsLoading.value = true
  try {
    const result = await functionUnitApi.getAllDeployments(0, 20)
    deployments.value = result.content
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
  currentUnit.value = unit
  showVersionsDialogVisible.value = true
  versionsLoading.value = true
  try {
    versionList.value = await functionUnitApi.getAllVersions(unit.code)
  } catch (e) {
    console.error('Failed to load versions:', e)
    ElMessage.error(t('functionUnit.loadFailed'))
  } finally {
    versionsLoading.value = false
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

// ==================== Batch Operations (Req 20) ====================

const handleSelectionChange = (selection: FunctionUnit[]) => {
  selectedUnits.value = selection
}

const handleBatchEnable = async () => {
  const ids = selectedUnits.value.map(u => u.id)
  try {
    await functionUnitApi.batchSetEnabled(ids, true)
    ElMessage.success(t('functionUnit.enabledSuccess'))
    fetchFunctionUnits()
  } catch {
    ElMessage.error(t('common.failed'))
  }
}

const handleBatchDisable = async () => {
  const ids = selectedUnits.value.map(u => u.id)
  try {
    await ElMessageBox.confirm(t('functionUnit.batchDisableConfirm'), t('common.confirm'), { type: 'warning' })
    await functionUnitApi.batchSetEnabled(ids, false)
    ElMessage.success(t('functionUnit.disabledSuccess'))
    fetchFunctionUnits()
  } catch (e) {
    if ((e as string) !== 'cancel') ElMessage.error(t('common.failed'))
  }
}

const handleBatchDelete = async () => {
  const ids = selectedUnits.value.map(u => u.id)
  try {
    await ElMessageBox.confirm(t('functionUnit.batchDeleteConfirm', { count: ids.length }), t('common.confirm'), { type: 'warning' })
    await functionUnitApi.batchDelete(ids)
    ElMessage.success(t('functionUnit.deleteSuccess'))
    fetchFunctionUnits()
  } catch (e) {
    if ((e as string) !== 'cancel') ElMessage.error(t('common.failed'))
  }
}

// ==================== Deployment Log (Req 22) ====================

const handleViewLog = async (deployment: Deployment) => {
  try {
    const detail = await functionUnitApi.getDeployment(deployment.id)
    logDeployment.value = detail
    showLogDialogVisible.value = true
  } catch {
    ElMessage.error(t('common.failed'))
  }
}

// ==================== Version Compare (Req 23) ====================

const handleCompareVersion = (version: FunctionUnit) => {
  compareVersion.value = version
  showCompareDialogVisible.value = true
}

// ==================== Import (Req 38) ====================

const handleImportFileChange = (file: any) => {
  importFile.value = file?.raw || null
}

const handleStartImport = async () => {
  if (!importFile.value) return
  importLoading.value = true
  try {
    const reader = new FileReader()
    reader.onload = async () => {
      const base64 = (reader.result as string).split(',')[1]
      const result = await functionUnitApi.import({
        fileName: importFile.value!.name,
        fileContent: base64
      })
      if (result.success) {
        ElMessage.success(t('functionUnit.importSuccess'))
        showImportDialog.value = false
        importFile.value = null
        fetchFunctionUnits()
      } else {
        ElMessage.error(result.message || t('functionUnit.importFailed'))
      }
      importLoading.value = false
    }
    reader.readAsDataURL(importFile.value)
  } catch {
    ElMessage.error(t('functionUnit.importFailed'))
    importLoading.value = false
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
