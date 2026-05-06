/**
 * BI Dashboard Registry 业务逻辑 composable
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  biManagementApi,
  type DashboardRegistryResponse,
  type DashboardStatus,
  type DashboardListParams
} from '@/api/biManagement'

export function useBiDashboard() {
  const { t } = useI18n()

  const loading = ref(false)
  const syncing = ref(false)
  const editLoading = ref(false)
  const dashboards = ref<DashboardRegistryResponse[]>([])
  const total = ref(0)

  const query = reactive<DashboardListParams & { page: number; size: number }>({
    title: '', tags: '', status: undefined, page: 1, size: 20
  })

  const editDialogVisible = ref(false)
  const editForm = reactive({ id: '', dashboardTitle: '', tags: '', isDefaultLanding: false })

  const handleSearch = async () => {
    loading.value = true
    try {
      const params: DashboardListParams = {
        title: query.title || undefined, tags: query.tags || undefined,
        status: query.status || undefined, page: query.page - 1, size: query.size
      }
      const result = await biManagementApi.dashboard.list(params)
      dashboards.value = result.content
      total.value = result.totalElements
    } catch (error: any) {
      ElMessage.error(error.message || t('bi.dashboard.queryFailed'))
    } finally { loading.value = false }
  }

  const handleReset = () => {
    Object.assign(query, { title: '', tags: '', status: undefined, page: 1 })
    handleSearch()
  }

  const handleSync = async () => {
    syncing.value = true
    try {
      const result = await biManagementApi.dashboard.sync()
      ElMessage.success(t('bi.dashboard.syncSuccess', { created: result.created, updated: result.updated, autoInactivated: result.autoInactivated }))
      handleSearch()
    } catch (error: any) {
      ElMessage.error(error.message || t('bi.dashboard.syncFailed'))
    } finally { syncing.value = false }
  }

  const showEditDialog = (row: DashboardRegistryResponse) => {
    editForm.id = row.id; editForm.dashboardTitle = row.dashboardTitle
    editForm.tags = row.tags || ''; editForm.isDefaultLanding = row.isDefaultLanding
    editDialogVisible.value = true
  }

  const handleEditSubmit = async () => {
    editLoading.value = true
    try {
      await biManagementApi.dashboard.update(editForm.id, { tags: editForm.tags || undefined, isDefaultLanding: editForm.isDefaultLanding })
      ElMessage.success(t('bi.dashboard.updateSuccess'))
      editDialogVisible.value = false
      handleSearch()
    } catch (error: any) {
      ElMessage.error(error.message || t('bi.dashboard.updateFailed'))
    } finally { editLoading.value = false }
  }

  const handleToggleStatus = async (row: DashboardRegistryResponse) => {
    const isActive = row.status === 'ACTIVE'
    const action = isActive ? 'disable' : 'enable'
    const newStatus: DashboardStatus = isActive ? 'MANUAL_INACTIVE' : 'ACTIVE'
    try {
      await ElMessageBox.confirm(
        isActive ? t('bi.dashboard.confirmDisableMsg', { title: row.dashboardTitle }) : t('bi.dashboard.confirmEnableMsg', { title: row.dashboardTitle }),
        isActive ? t('bi.dashboard.confirmDisable') : t('bi.dashboard.confirmEnable'), { type: 'warning' }
      )
      await biManagementApi.dashboard.updateStatus(row.id, { status: newStatus })
      ElMessage.success(t('bi.dashboard.statusChangeSuccess', { action: action.charAt(0).toUpperCase() + action.slice(1) }))
      handleSearch()
    } catch (error: any) {
      if (error !== 'cancel') ElMessage.error(error.message || t('bi.dashboard.statusChangeFailed', { action }))
    }
  }

  const handleDelete = async (row: DashboardRegistryResponse) => {
    try {
      await ElMessageBox.confirm(
        t('bi.dashboard.confirmDeleteMsg', { title: row.dashboardTitle }),
        t('bi.dashboard.confirmDelete'), { type: 'warning', confirmButtonText: t('bi.dashboard.delete'), confirmButtonClass: 'el-button--danger' }
      )
      await biManagementApi.dashboard.delete(row.id)
      ElMessage.success(t('bi.dashboard.deleteSuccess'))
      handleSearch()
    } catch (error: any) {
      if (error !== 'cancel') ElMessage.error(error.message || t('bi.dashboard.deleteFailed'))
    }
  }

  return {
    loading, syncing, editLoading, dashboards, total, query,
    editDialogVisible, editForm,
    handleSearch, handleReset, handleSync,
    showEditDialog, handleEditSubmit, handleToggleStatus, handleDelete,
  }
}
