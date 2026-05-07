/**
 * 功能单元业务逻辑 composable
 * 
 * 封装 function-unit 页面的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 */

import { ref, reactive, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storeToRefs } from 'pinia'
import { type FunctionUnit, type Deployment, type DeletePreviewResponse } from '@/api/functionUnit'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { pickHttpErrorBodyMessage } from '@/utils/httpErrorMessage'

export function useFunctionUnit() {
  const { t } = useI18n()
  const store = useFunctionUnitStore()
  const { functionUnits, deployments, loading } = storeToRefs(store)

  // ==================== State ====================

  const activeTab = ref('list')
  const deploymentsLoading = ref(false)
  const versionsLoading = ref(false)
  const importLoading = ref(false)

  const versionList = ref<FunctionUnit[]>([])
  const searchKeyword = ref('')
  const selectedUnits = ref<FunctionUnit[]>([])

  // Dialog visibility
  const showImportDialog = ref(false)
  const showDeployDialogVisible = ref(false)
  const showAccessDialogVisible = ref(false)
  const showDeleteDialogVisible = ref(false)
  const showVersionsDialogVisible = ref(false)
  const showLogDialogVisible = ref(false)
  const showCompareDialogVisible = ref(false)

  // Current selections
  const currentUnit = ref<FunctionUnit | null>(null)
  const deleteTargetUnit = ref<FunctionUnit | null>(null)
  const deletePreview = ref<DeletePreviewResponse | null>(null)
  const logDeployment = ref<Deployment | null>(null)
  const compareVersion = ref<FunctionUnit | null>(null)

  // Forms
  const deployForm = reactive({ environment: 'DEVELOPMENT' as const, strategy: 'FULL' as const })
  const importFile = ref<File | null>(null)
  const importUploadRef = ref<any>(null)

  // ==================== Computed ====================

  const filteredFunctionUnits = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase()
    if (!keyword) return functionUnits.value
    return functionUnits.value.filter(unit =>
      (unit.name?.toLowerCase().includes(keyword)) ||
      (unit.code?.toLowerCase().includes(keyword)) ||
      (unit.description?.toLowerCase().includes(keyword))
    )
  })

  // ==================== Data Fetching ====================

  const fetchFunctionUnits = async () => {
    try {
      await store.fetchFunctionUnits()
      functionUnits.value = functionUnits.value.map(unit => ({
        ...unit,
        enabled: unit.enabled !== false,
        _enabledLoading: false
      }))
    } catch (e) {
      console.error('Failed to load function units:', e)
      ElMessage.error(t('functionUnit.loadFailed'))
    }
  }

  const fetchDeployments = async () => {
    deploymentsLoading.value = true
    try {
      await store.fetchDeployments()
    } catch (e) {
      console.error('Failed to load deployments:', e)
    } finally {
      deploymentsLoading.value = false
    }
  }

  // Watch tab for deployments lazy load
  watch(activeTab, (tab) => {
    if (tab === 'deployments') fetchDeployments()
  })

  // ==================== Dialog Actions (UI helpers) ====================

  const showDeployDialog = (unit: FunctionUnit) => {
    currentUnit.value = unit
    showDeployDialogVisible.value = true
  }

  const showAccessDialog = (unit: FunctionUnit) => {
    currentUnit.value = unit
    showAccessDialogVisible.value = true
  }

  const showVersions = async (unit: FunctionUnit) => {
    currentUnit.value = unit
    showVersionsDialogVisible.value = true
    versionsLoading.value = true
    try {
      versionList.value = await store.getAllVersions(unit.code)
    } catch (e) {
      console.error('Failed to load versions:', e)
      ElMessage.error(t('functionUnit.loadFailed'))
    } finally {
      versionsLoading.value = false
    }
  }

  // ==================== Deploy ====================

  const handleDeploy = async () => {
    if (!currentUnit.value) return
    try {
      await store.createDeployment(currentUnit.value.id, deployForm.environment, deployForm.strategy)
      ElMessage.success(t('functionUnit.deploySubmitted'))
      showDeployDialogVisible.value = false
      fetchFunctionUnits()
    } catch (e) {
      console.error('Failed to create deployment:', e)
      ElMessage.error(t('functionUnit.deployFailed'))
    }
  }

  // ==================== Rollback ====================

  const handleRollback = async (unit: FunctionUnit) => {
    await ElMessageBox.confirm(
      t('functionUnit.rollbackConfirm', { name: unit.name }),
      t('common.confirm'),
      { type: 'warning' }
    )
    try {
      const deploymentHistory = await store.getDeploymentHistory(unit.id)
      const lastDeployment = deploymentHistory.find(d => d.status === 'COMPLETED')
      if (lastDeployment) {
        await store.rollbackDeployment(lastDeployment.id, t('functionUnit.manualRollback'))
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

  // ==================== Enable/Disable ====================

  const handleEnabledChange = async (unit: FunctionUnit & { _enabledLoading?: boolean }, enabled: boolean) => {
    if (!enabled) {
      try {
        await ElMessageBox.confirm(
          t('functionUnit.disableConfirmMessage', { name: unit.name }),
          t('functionUnit.confirmDisable'),
          { type: 'warning' }
        )
      } catch {
        unit.enabled = true
        return
      }
    }
    unit._enabledLoading = true
    try {
      await store.setEnabled(unit.id, enabled)
      ElMessage.success(enabled ? t('functionUnit.enabledSuccess') : t('functionUnit.disabledSuccess'))
    } catch (e: unknown) {
      const msg = pickHttpErrorBodyMessage((e as { response?: { data?: unknown } })?.response?.data)
      ElMessage.error(msg || t('common.failed'))
      unit.enabled = !enabled
    } finally {
      unit._enabledLoading = false
    }
  }

  // ==================== Delete ====================

  const handleDeleteClick = async (unit: FunctionUnit) => {
    deleteTargetUnit.value = unit
    try {
      deletePreview.value = await store.getDeletePreview(unit.id)
      showDeleteDialogVisible.value = true
    } catch {
      ElMessage.error(t('functionUnit.getDeletePreviewFailed'))
    }
  }

  const handleDeleteConfirm = async () => {
    if (!deleteTargetUnit.value) return
    try {
      await store.deleteFunctionUnit(deleteTargetUnit.value.id)
      ElMessage.success(t('functionUnit.deleteSuccess'))
      showDeleteDialogVisible.value = false
      fetchFunctionUnits()
    } catch (e: any) {
      console.error('Failed to delete:', e)
      ElMessage.error(e.response?.data?.message || t('functionUnit.deleteFailed'))
    }
  }

  // ==================== Batch Operations ====================

  const handleSelectionChange = (selection: FunctionUnit[]) => {
    selectedUnits.value = selection
  }

  const handleBatchEnable = async () => {
    try {
      await store.batchSetEnabled(selectedUnits.value.map(u => u.id), true)
      ElMessage.success(t('functionUnit.enabledSuccess'))
      fetchFunctionUnits()
    } catch (e: unknown) {
      const msg = pickHttpErrorBodyMessage((e as { response?: { data?: unknown } })?.response?.data)
      ElMessage.error(msg || t('common.failed'))
    }
  }

  const handleBatchDisable = async () => {
    try {
      await ElMessageBox.confirm(t('functionUnit.batchDisableConfirm'), t('common.confirm'), { type: 'warning' })
      await store.batchSetEnabled(selectedUnits.value.map(u => u.id), false)
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
      await store.batchDelete(ids)
      ElMessage.success(t('functionUnit.deleteSuccess'))
      fetchFunctionUnits()
    } catch (e) {
      if ((e as string) !== 'cancel') ElMessage.error(t('common.failed'))
    }
  }

  // ==================== Deployment Log ====================

  const handleViewLog = async (deployment: Deployment) => {
    try {
      logDeployment.value = await store.getDeployment(deployment.id)
      showLogDialogVisible.value = true
    } catch { ElMessage.error(t('common.failed')) }
  }

  // ==================== Version Compare ====================

  const handleCompareVersion = (version: FunctionUnit) => {
    compareVersion.value = version
    showCompareDialogVisible.value = true
  }

  // ==================== Import ====================

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
        const result = await store.importPackage(importFile.value!.name, base64)
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

  // ==================== Return ====================

  return {
    // State
    activeTab,
    loading,
    deploymentsLoading,
    versionsLoading,
    importLoading,
    functionUnits,
    deployments,
    versionList,
    searchKeyword,
    filteredFunctionUnits,
    selectedUnits,
    // Dialog visibility
    showImportDialog,
    showDeployDialogVisible,
    showAccessDialogVisible,
    showDeleteDialogVisible,
    showVersionsDialogVisible,
    showLogDialogVisible,
    showCompareDialogVisible,
    // Current selections
    currentUnit,
    deleteTargetUnit,
    deletePreview,
    logDeployment,
    compareVersion,
    // Forms
    deployForm,
    importFile,
    importUploadRef,
    // Methods
    fetchFunctionUnits,
    fetchDeployments,
    showDeployDialog,
    showAccessDialog,
    showVersions,
    handleDeploy,
    handleRollback,
    handleEnabledChange,
    handleDeleteClick,
    handleDeleteConfirm,
    handleSelectionChange,
    handleBatchEnable,
    handleBatchDisable,
    handleBatchDelete,
    handleViewLog,
    handleCompareVersion,
    handleImportFileChange,
    handleStartImport,
  }
}
