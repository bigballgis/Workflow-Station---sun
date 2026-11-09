/**
 * 功能单元业务逻辑 composable
 *
 * 封装 function-unit 页面的所有 API 调用和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 */

import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { logger } from '@/utils/logger'
import { notifyConfirm, notifySuccess, notifyWarning } from '@/utils/notify'
import { notifyUnreportedError } from '@/utils/notifyUnreportedError'
import { functionUnitApi, type FunctionUnit, type DeletePreviewResponse, type FunctionUnitValidationResult } from '@/api/functionUnit'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { useFunctionUnitLists, type FunctionUnitRow } from '@/composables/modules/useFunctionUnitLists'
import { useFunctionUnitImport } from '@/composables/modules/useFunctionUnitImport'

export function useFunctionUnit() {
  const { t } = useI18n()
  const store = useFunctionUnitStore()
  const lists = useFunctionUnitLists()
  const {
    listLoading,
    archivedLoading,
    deploymentsLoading,
    searchKeyword,
    archiveSearchKeyword,
    deploymentSearchKeyword,
    selectedUnits,
    listGrid,
    archiveGrid,
    deployGrid,
    fetchFunctionUnits,
    fetchArchivedFunctionUnits,
    fetchDeployments,
    handleSelectionChange,
    LIST_ACTIONS_WIDTH,
    LIST_SELECTION_WIDTH,
    ARCHIVE_ACTIONS_WIDTH,
  } = lists

  // ==================== State ====================

  const activeTab = ref('list')
  const versionsLoading = ref(false)
  const deployLoadingId = ref<string | null>(null)
  const validateLoadingId = ref<string | null>(null)
  const restoreLoadingId = ref<string | null>(null)

  const versionList = ref<FunctionUnit[]>([])

  // Dialog visibility
  const showAccessDialogVisible = ref(false)
  const showDeleteDialogVisible = ref(false)
  const showVersionsDialogVisible = ref(false)
  const showCompareDialogVisible = ref(false)

  // Current selections
  const currentUnit = ref<FunctionUnit | null>(null)
  const deleteTargetUnit = ref<FunctionUnit | null>(null)
  const deletePreview = ref<DeletePreviewResponse | null>(null)
  const compareVersion = ref<FunctionUnit | null>(null)

  const importUploadRef = ref<any>(null)

  const showValidateResultDialog = ref(false)
  const validateResult = ref<FunctionUnitValidationResult | null>(null)

  watch(activeTab, (tab) => {
    if (tab === 'deployments') void fetchDeployments()
    if (tab === 'archive') void fetchArchivedFunctionUnits()
  })

  // ==================== Dialog Actions ====================

  const showAccessDialog = (unit: FunctionUnit) => {
    currentUnit.value = unit
    showAccessDialogVisible.value = true
  }

  const showVersions = async (unit: FunctionUnit) => {
    currentUnit.value = unit
    showVersionsDialogVisible.value = true
    versionsLoading.value = true
    try {
      versionList.value = await functionUnitApi.getAllVersions(unit.code)
    } catch (e) {
      logger.error('functionUnit', 'Failed to load versions:', e)
      notifyUnreportedError(e, t('functionUnit.loadFailed'))
    } finally {
      versionsLoading.value = false
    }
  }

  // ==================== Validate ====================

  const handleValidate = async (unit: FunctionUnit) => {
    validateLoadingId.value = unit.id
    try {
      const result = await functionUnitApi.validateUnit(unit.id)
      validateResult.value = result
      showValidateResultDialog.value = true
      if (result.valid) {
        notifySuccess(t('functionUnit.validateSuccess'))
        await fetchFunctionUnits()
      }
    } catch (e: unknown) {
      logger.error('functionUnit', 'Failed to validate:', e)
      notifyUnreportedError(e, t('functionUnit.validateFailed'))
    } finally {
      validateLoadingId.value = null
    }
  }

  // ==================== Deploy ====================

  const handleDeploy = async (unit: FunctionUnit) => {
    try {
      await notifyConfirm(
        t('functionUnit.deployConfirmMessage', { name: unit.name }),
        t('functionUnit.confirmDeploy'),
        { type: 'warning' }
      )
    } catch {
      return
    }

    deployLoadingId.value = unit.id
    try {
      await functionUnitApi.deploy(unit.id)
      notifySuccess(t('functionUnit.deploySuccess'))
      await fetchFunctionUnits()
      await fetchDeployments()
    } catch (e: unknown) {
      logger.error('functionUnit', 'Failed to deploy:', e)
      notifyUnreportedError(e, t('functionUnit.deployFailed'))
    } finally {
      deployLoadingId.value = null
    }
  }

  // ==================== Restore ====================

  const handleRestore = async (unit: FunctionUnit) => {
    try {
      await notifyConfirm(
        t('functionUnit.restoreConfirmMessage', { name: unit.name }),
        t('functionUnit.restore'),
        { type: 'warning' }
      )
    } catch {
      return
    }

    restoreLoadingId.value = unit.id
    try {
      await functionUnitApi.restore(unit.id)
      notifySuccess(t('functionUnit.restoreSuccess'))
      await fetchArchivedFunctionUnits()
      await fetchFunctionUnits()
    } catch (e: unknown) {
      logger.error('functionUnit', 'Failed to restore:', e)
      notifyUnreportedError(e, t('functionUnit.restoreFailed'))
    } finally {
      restoreLoadingId.value = null
    }
  }

  // ==================== Rollback ====================

  const handleRollback = async (unit: FunctionUnit) => {
    await notifyConfirm(
      t('functionUnit.rollbackConfirm', { name: unit.name }),
      t('common.confirm'),
      { type: 'warning' }
    )
    try {
      const deploymentHistory = await functionUnitApi.getDeploymentHistory(unit.id)
      const lastDeployment = deploymentHistory.find(d => d.status === 'COMPLETED')
      if (lastDeployment) {
        await functionUnitApi.rollbackDeployment(lastDeployment.id, t('functionUnit.manualRollback'))
        notifySuccess(t('functionUnit.rollbackSuccess'))
        fetchFunctionUnits()
      } else {
        notifyWarning(t('functionUnit.noRollbackRecord'))
      }
    } catch (e) {
      logger.error('functionUnit', 'Failed to rollback:', e)
      notifyUnreportedError(e, t('functionUnit.rollbackFailed'))
    }
  }

  // ==================== Enable/Disable ====================

  const handleEnabledChange = async (unit: FunctionUnitRow, enabled: boolean) => {
    if (!enabled) {
      try {
        await notifyConfirm(
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
      notifySuccess(enabled ? t('functionUnit.enabledSuccess') : t('functionUnit.disabledSuccess'))
    } catch (e: unknown) {
      notifyUnreportedError(e, t('common.failed'))
      unit.enabled = !enabled
    } finally {
      unit._enabledLoading = false
    }
  }

  // ==================== Delete (Archive) ====================

  const handleDeleteClick = async (unit: FunctionUnit) => {
    deleteTargetUnit.value = unit
    try {
      deletePreview.value = await functionUnitApi.getDeletePreview(unit.id)
      showDeleteDialogVisible.value = true
    } catch (e: unknown) {
      notifyUnreportedError(e, t('functionUnit.getDeletePreviewFailed'))
    }
  }

  const handleDeleteConfirm = async () => {
    if (!deleteTargetUnit.value) return
    try {
      await store.deleteFunctionUnit(deleteTargetUnit.value.id)
      notifySuccess(t('functionUnit.archiveSuccess'))
      showDeleteDialogVisible.value = false
      fetchFunctionUnits()
    } catch (e: unknown) {
      logger.error('functionUnit', 'Failed to archive:', e)
      notifyUnreportedError(e, t('functionUnit.deleteFailed'))
    }
  }

  // ==================== Batch Operations (current page only) ====================

  const handleBatchEnable = async () => {
    try {
      await store.batchSetEnabled(selectedUnits.value.map(u => u.id), true)
      notifySuccess(t('functionUnit.enabledSuccess'))
      selectedUnits.value = []
      fetchFunctionUnits()
    } catch (e: unknown) {
      notifyUnreportedError(e, t('common.failed'))
    }
  }

  const handleBatchDisable = async () => {
    try {
      await notifyConfirm(t('functionUnit.batchDisableConfirm'), t('common.confirm'), { type: 'warning' })
      await store.batchSetEnabled(selectedUnits.value.map(u => u.id), false)
      notifySuccess(t('functionUnit.disabledSuccess'))
      selectedUnits.value = []
      fetchFunctionUnits()
    } catch (e) {
      // A dismissed confirm rejects with 'cancel' ('close' for X / Esc if distinguishCancelAndClose is set): not a failure.
      if (e !== 'cancel' && e !== 'close') notifyUnreportedError(e, t('common.failed'))
    }
  }

  const handleBatchDelete = async () => {
    const ids = selectedUnits.value.map(u => u.id)
    try {
      await notifyConfirm(t('functionUnit.batchArchiveConfirm', { count: ids.length }), t('common.confirm'), { type: 'warning' })
      await store.batchDelete(ids)
      notifySuccess(t('functionUnit.archiveSuccess'))
      selectedUnits.value = []
      fetchFunctionUnits()
    } catch (e) {
      // A dismissed confirm rejects with 'cancel' ('close' for X / Esc if distinguishCancelAndClose is set): not a failure.
      if (e !== 'cancel' && e !== 'close') notifyUnreportedError(e, t('common.failed'))
    }
  }

  // ==================== Version Compare ====================

  const handleCompareVersion = (version: FunctionUnit) => {
    compareVersion.value = version
    showCompareDialogVisible.value = true
  }

  // ==================== Import ====================

  const importing = useFunctionUnitImport(() => { void fetchFunctionUnits() })

  return {
    activeTab,
    listLoading,
    archivedLoading,
    deploymentsLoading,
    versionsLoading,
    deployLoadingId,
    validateLoadingId,
    restoreLoadingId,
    versionList,
    searchKeyword,
    archiveSearchKeyword,
    deploymentSearchKeyword,
    selectedUnits,
    listGrid,
    archiveGrid,
    deployGrid,
    handleSelectionChange,
    LIST_ACTIONS_WIDTH,
    LIST_SELECTION_WIDTH,
    ARCHIVE_ACTIONS_WIDTH,
    showAccessDialogVisible,
    showDeleteDialogVisible,
    showVersionsDialogVisible,
    showCompareDialogVisible,
    currentUnit,
    deleteTargetUnit,
    deletePreview,
    compareVersion,
    importUploadRef,
    showValidateResultDialog,
    validateResult,
    fetchFunctionUnits,
    fetchArchivedFunctionUnits,
    fetchDeployments,
    showAccessDialog,
    showVersions,
    handleValidate,
    handleDeploy,
    handleRestore,
    handleRollback,
    handleEnabledChange,
    handleDeleteClick,
    handleDeleteConfirm,
    handleBatchEnable,
    handleBatchDisable,
    handleBatchDelete,
    handleCompareVersion,
    ...importing,
  }
}
