/**
 * BI Dashboard Registry 业务逻辑 composable
 */
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { notifyConfirm, notifyError, notifySuccess } from '@/utils/notify'
import {
  biManagementApi,
  type DashboardRegistryResponse,
  type DashboardStatus,
} from '@/api/biManagement'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'

const ACTIONS_COL_WIDTH = 220

export function useBiDashboard() {
  const { t } = useI18n()

  const loading = ref(false)
  const syncing = ref(false)
  const editLoading = ref(false)
  const query = reactive({ title: '', tags: '', status: undefined as DashboardStatus | undefined })

  const grid = useAdminListGrid<DashboardRegistryResponse>({
    storageKey: 'admin-list-layout:bi-dashboards',
    extraWidth: ACTIONS_COL_WIDTH,
  })

  const editDialogVisible = ref(false)
  const editForm = reactive({ id: '', dashboardTitle: '', tags: '', isDefaultLanding: false })

  const loadDashboards = async () => {
    const seq = grid.beginQuery()
    loading.value = true
    try {
      const page = await biManagementApi.dashboard.query({
        ...grid.buildQuery(),
        title: query.title || undefined,
        tags: query.tags || undefined,
        status: query.status || undefined,
      })
      if (!grid.isCurrentQuery(seq)) return
      grid.applyPage(page, 'bi/dashboards/query response is missing its column declaration')
    } catch {
      if (!grid.isCurrentQuery(seq)) return
      notifyError(t(errorTranslator(AppErrorCode.BI_DASHBOARD_QUERY_FAILED)))
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
    }
  }

  const handleSearch = () => {
    void loadDashboards()
  }

  const handleReset = () => {
    Object.assign(query, { title: '', tags: '', status: undefined })
    void loadDashboards()
  }

  const handleSync = async () => {
    syncing.value = true
    try {
      const result = await biManagementApi.dashboard.sync()
      notifySuccess(t('bi.dashboard.syncSuccess', { created: result.created, updated: result.updated, autoInactivated: result.autoInactivated }))
      handleSearch()
    } catch {
      notifyError(t(errorTranslator(AppErrorCode.BI_DASHBOARD_SYNC_FAILED)))
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
      notifySuccess(t('bi.dashboard.updateSuccess'))
      editDialogVisible.value = false
      handleSearch()
    } catch {
      notifyError(t(errorTranslator(AppErrorCode.BI_DASHBOARD_UPDATE_FAILED)))
    } finally { editLoading.value = false }
  }

  const handleToggleStatus = async (row: DashboardRegistryResponse) => {
    const isActive = row.status === 'ACTIVE'
    const action = isActive ? 'disable' : 'enable'
    const newStatus: DashboardStatus = isActive ? 'MANUAL_INACTIVE' : 'ACTIVE'
    try {
      await notifyConfirm(
        isActive ? t('bi.dashboard.confirmDisableMsg', { title: row.dashboardTitle }) : t('bi.dashboard.confirmEnableMsg', { title: row.dashboardTitle }),
        isActive ? t('bi.dashboard.confirmDisable') : t('bi.dashboard.confirmEnable'), { type: 'warning' }
      )
      await biManagementApi.dashboard.updateStatus(row.id, { status: newStatus })
      notifySuccess(t('bi.dashboard.statusChangeSuccess', { action: action.charAt(0).toUpperCase() + action.slice(1) }))
      handleSearch()
    } catch (error) {
      if (error !== 'cancel') notifyError(t(errorTranslator(AppErrorCode.BI_DASHBOARD_STATUS_CHANGE_FAILED)))
    }
  }

  const handleDelete = async (row: DashboardRegistryResponse) => {
    try {
      await notifyConfirm(
        t('bi.dashboard.confirmDeleteMsg', { title: row.dashboardTitle }),
        t('bi.dashboard.confirmDelete'), { type: 'warning', confirmButtonText: t('bi.dashboard.delete'), confirmButtonClass: 'el-button--danger' }
      )
      await biManagementApi.dashboard.delete(row.id)
      notifySuccess(t('bi.dashboard.deleteSuccess'))
      handleSearch()
    } catch (error) {
      if (error !== 'cancel') notifyError(t(errorTranslator(AppErrorCode.BI_DASHBOARD_DELETE_FAILED)))
    }
  }

  return {
    loading, syncing, editLoading, query,
    editDialogVisible, editForm,
    handleSearch, handleReset, handleSync, loadDashboards,
    showEditDialog, handleEditSubmit, handleToggleStatus, handleDelete,
    ACTIONS_COL_WIDTH,
    ...grid,
  }
}
