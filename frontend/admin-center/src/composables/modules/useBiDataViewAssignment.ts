import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'
import { biManagementApi, type DataViewAssignmentResponse, type DataViewFunctionUnitOption } from '@/api/biManagement'
import { notifyConfirm, notifyError, notifySuccess } from '@/utils/notify'

export function useBiDataViewAssignment() {
  const { t } = useI18n()
  const ACTIONS_COL_WIDTH = 220
  const grid = useAdminListGrid<DataViewAssignmentResponse>({
    storageKey: 'admin-list-layout:bi-data-view-assignments', extraWidth: ACTIONS_COL_WIDTH,
  })
  const loading = ref(false)
  const functionUnits = ref<DataViewFunctionUnitOption[]>([])
  const dialogVisible = ref(false)
  const dialogMode = ref<'create' | 'edit'>('create')
  const editingRow = ref<DataViewAssignmentResponse | null>(null)
  const query = reactive({ dashboardTitle: '', functionUnitId: undefined as number | undefined })

  async function load() {
    const seq = grid.beginQuery()
    loading.value = true
    try {
      const page = await biManagementApi.dataViewAssignment.query({
        ...grid.buildQuery(), dashboardTitle: query.dashboardTitle.trim() || undefined,
        functionUnitId: query.functionUnitId,
      })
      if (grid.isCurrentQuery(seq)) grid.applyPage(page, 'Data View Assignment query is missing its column declaration')
    } catch {
      if (grid.isCurrentQuery(seq)) notifyError(t('bi.dataViewAssignment.queryFailed'))
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
    }
  }

  function search() { grid.resetPage(); void load() }
  function reset() {
    query.dashboardTitle = ''
    query.functionUnitId = undefined
    search()
  }
  function openCreate() { dialogMode.value = 'create'; editingRow.value = null; dialogVisible.value = true }
  function openEdit(row: DataViewAssignmentResponse) {
    dialogMode.value = 'edit'; editingRow.value = row; dialogVisible.value = true
  }
  async function remove(row: DataViewAssignmentResponse) {
    try {
      await notifyConfirm(t('bi.dataViewAssignment.deleteConfirm', {
        dashboard: row.dashboardTitle, table: row.tableDisplayName || row.tableName,
      }), t('bi.dataViewAssignment.deleteConfirmTitle'), { type: 'warning' })
      await biManagementApi.dataViewAssignment.delete(row.id)
      notifySuccess(t('bi.dataViewAssignment.deleteSuccess'))
      if (grid.displayRows.value.length === 1 && grid.pagination.page > 1) grid.pagination.page -= 1
      await load()
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') notifyError(t('bi.dataViewAssignment.deleteFailed'))
    }
  }
  async function initialize() {
    try {
      functionUnits.value = await biManagementApi.dataViewAssignment.listFunctionUnits()
    } catch {
      notifyError(t('bi.dataViewAssignment.initializeFailed'))
    }
    await load()
  }
  return { ...grid, ACTIONS_COL_WIDTH, loading, functionUnits, dialogVisible, dialogMode, editingRow,
    query, load, search, reset, openCreate, openEdit, remove, initialize }
}
