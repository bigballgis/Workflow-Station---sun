/**
 * Relation Table Data 业务逻辑 composable
 *
 * 封装 relation-table/data/index.vue 所有状态、API、CRUD。
 * 组件仅保留 template + 调用此 composable。
 */

import { ref, computed } from 'vue'
import { notifySuccess, notifyError, notifyConfirm } from '@/utils/notify'
import { relationTableDataApi, type RelationTableResponse, type RelationTableDataRow, type FieldDefinitionResponse } from '@/api/relationTable'

export function useRelationTableData() {
  const tableListLoading = ref(false)
  const dataLoading = ref(false)
  const exporting = ref(false)
  const saving = ref(false)
  const tables = ref<RelationTableResponse[]>([])
  const selectedTableId = ref<number | null>(null)

  const searchKeyword = ref('')
  const tableSearchKeyword = ref('')
  const currentPage = ref(1)
  const pageSize = ref(20)
  const totalElements = ref(0)
  const dataRows = ref<RelationTableDataRow[]>([])
  const fetchDataError = ref<string | null>(null)

  const dialogVisible = ref(false)
  const dialogMode = ref<'add' | 'edit'>('add')
  const editingRowId = ref<string | null>(null)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- dynamic column keys from relation table schema
  const formData = ref<Record<string, any>>({})

  const localStatusMap = ref<Record<string, string>>({})

  const SYSTEM_COLUMNS = new Set(['created_at', 'created_by', 'updated_at', 'updated_by', 'status'])

  // ---- Computed ----
  const selectedTable = computed(() => tables.value.find(t => t.id === selectedTableId.value) ?? null)

  const fieldColumns = computed<FieldDefinitionResponse[]>(() => {
    if (!selectedTable.value?.fieldDefinitions) return []
    return selectedTable.value.fieldDefinitions
      .filter(f => !SYSTEM_COLUMNS.has(f.fieldName))
      .sort((a, b) => {
        if (a.isPrimaryKey && !b.isPrimaryKey) return -1
        if (!a.isPrimaryKey && b.isPrimaryKey) return 1
        return a.sortOrder - b.sortOrder
      })
  })

  const filteredTables = computed(() => {
    const kw = tableSearchKeyword.value.trim().toLowerCase()
    if (!kw) return tables.value
    return tables.value.filter(t => (t.displayName || '').toLowerCase().includes(kw) || (t.tableName || '').toLowerCase().includes(kw))
  })

  const isNumericType = (dt: string) => ['INTEGER', 'BIGINT', 'DECIMAL'].includes(dt)

  const isRowDisabled = (row: RelationTableDataRow): boolean => {
    const local = localStatusMap.value[row.rowId]
    if (local) return local === 'INACTIVE'
    const status = (row.data as unknown as Record<string, unknown>)?.status ?? (row as unknown as Record<string, unknown>).status ?? ''
    return String(status).toUpperCase() === 'DISABLED' || String(status).toUpperCase() === 'INACTIVE'
  }

  // ---- Fetch ----
  const fetchTables = async () => {
    tableListLoading.value = true
    try {
      tables.value = await relationTableDataApi.getDeployedTables()
      if (!selectedTableId.value && tables.value.length > 0) {
        selectedTableId.value = tables.value[0].id
        await fetchData()
      }
    } catch { tables.value = [] }
    finally { tableListLoading.value = false }
  }

  const fetchData = async () => {
    if (!selectedTableId.value) return
    dataLoading.value = true
    fetchDataError.value = null
    try {
      const params: Record<string, unknown> = { page: currentPage.value - 1, size: pageSize.value }
      if (searchKeyword.value) (params as Record<string, unknown>).search = searchKeyword.value
      const pageData = await relationTableDataApi.queryData(selectedTableId.value, params)
      dataRows.value = pageData?.content || []
      totalElements.value = pageData?.totalElements || 0
    } catch (e: unknown) {
      dataRows.value = []; totalElements.value = 0
      const err = e as { response?: { data?: { error?: { message?: string; traceId?: string }; message?: string; traceId?: string }; status?: number }; message?: string }
      const apiMsg = err?.response?.data?.error?.message || err?.response?.data?.message
      const traceId = err?.response?.data?.error?.traceId || err?.response?.data?.traceId
      fetchDataError.value = traceId ? `Data load failed: ${apiMsg || err?.message} (traceId: ${traceId})` : `Data load failed: ${apiMsg || err?.message || 'Failed to load data'}`
    } finally { dataLoading.value = false }
  }

  // ---- Navigation ----
  const handleSelectTable = (index: string) => {
    selectedTableId.value = Number(index); searchKeyword.value = ''; currentPage.value = 1
    localStatusMap.value = {}; fetchDataError.value = null; fetchData()
  }
  const handlePageChange = (page: number) => { currentPage.value = page; fetchData() }
  const handleSizeChange = (size: number) => { pageSize.value = size; currentPage.value = 1; fetchData() }

  // ---- CRUD ----
  const openAddDialog = () => {
    dialogMode.value = 'add'; editingRowId.value = null; formData.value = {}
    fieldColumns.value.forEach(f => { (formData.value as Record<string, unknown>)[f.fieldName] = f.defaultValue ?? (f.dataType === 'BOOLEAN' ? false : null) })
    dialogVisible.value = true
  }

  const openEditDialog = (row: RelationTableDataRow) => {
    dialogMode.value = 'edit'; editingRowId.value = row.rowId; formData.value = {}
    fieldColumns.value.forEach(f => { (formData.value as Record<string, unknown>)[f.fieldName] = row.data?.[f.fieldName] ?? null })
    dialogVisible.value = true
  }

  const handleSaveRecord = async () => {
    if (!selectedTableId.value) return
    saving.value = true
    try {
      const cleanData: Record<string, unknown> = {}
      for (const [key, val] of Object.entries(formData.value)) {
        if (val !== null && val !== undefined && val !== '') cleanData[key] = val
      }
      if (dialogMode.value === 'add') {
        await relationTableDataApi.addData(selectedTableId.value, cleanData)
        notifySuccess('Record added')
      } else if (editingRowId.value) {
        await relationTableDataApi.updateData(selectedTableId.value, editingRowId.value, cleanData)
        notifySuccess('Record updated')
      }
      dialogVisible.value = false; await fetchData()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { error?: { message?: string }; message?: string } }; message?: string }
      notifyError(err?.response?.data?.error?.message || err?.response?.data?.message || err?.message || 'Save failed')
    } finally { saving.value = false }
  }

  const handleDisable = async (row: RelationTableDataRow) => {
    if (!selectedTableId.value) return
    try {
      await relationTableDataApi.changeStatus(selectedTableId.value, row.rowId, 'INACTIVE')
      localStatusMap.value = { ...localStatusMap.value, [row.rowId]: 'INACTIVE' }
      notifySuccess('Record set to inactive'); await fetchData()
    } catch (e: unknown) { notifyError((e as Error)?.message || 'Failed') }
  }

  const handleEnable = async (row: RelationTableDataRow) => {
    if (!selectedTableId.value) return
    try {
      await relationTableDataApi.changeStatus(selectedTableId.value, row.rowId, 'ACTIVE')
      localStatusMap.value = { ...localStatusMap.value, [row.rowId]: 'ACTIVE' }
      notifySuccess('Record set to active'); await fetchData()
    } catch (e: unknown) { notifyError((e as Error)?.message || 'Failed') }
  }

  const handleDelete = async (row: RelationTableDataRow) => {
    if (!selectedTableId.value) return
    try { await notifyConfirm('Are you sure to delete this record?', 'Confirm', { type: 'warning' }) }
    catch { return }
    try { await relationTableDataApi.deleteData(selectedTableId.value, row.rowId); notifySuccess('Record deleted'); await fetchData() }
    catch (e: unknown) { notifyError((e as Error)?.message || 'Delete failed') }
  }

  const formatHKT = (value: unknown): string => {
    if (value == null || value === '') return ''
    try { const d = new Date(value as string); return isNaN(d.getTime()) ? String(value) : d.toLocaleString('en-HK', { timeZone: 'Asia/Hong_Kong', hour12: false }) }
    catch { return String(value) }
  }

  const handleExport = async () => {
    if (!selectedTableId.value) return
    exporting.value = true
    try {
      const blob = await relationTableDataApi.exportCsv(selectedTableId.value)
      const url = window.URL.createObjectURL(blob as Blob)
      const link = document.createElement('a'); link.href = url
      link.setAttribute('download', `${selectedTable.value?.displayName || selectedTable.value?.tableName || 'data'}.csv`)
      document.body.appendChild(link); link.click(); link.remove(); window.URL.revokeObjectURL(url)
      notifySuccess('Export completed')
    } catch { notifyError('Export failed') }
    finally { exporting.value = false }
  }

  // ---- Lifecycle ----
  const init = () => fetchTables()
  const refresh = () => { fetchTables(); if (selectedTableId.value) fetchData() }

  return {
    tableListLoading, dataLoading, exporting, saving,
    tables, selectedTableId, searchKeyword, tableSearchKeyword, currentPage, pageSize, totalElements, dataRows,
    fetchDataError, dialogVisible, dialogMode, editingRowId, formData,
    selectedTable, fieldColumns, filteredTables,
    isNumericType, isRowDisabled,
    fetchTables, fetchData, handleSelectTable, handlePageChange, handleSizeChange,
    openAddDialog, openEditDialog, handleSaveRecord, handleDisable, handleEnable, handleDelete,
    formatHKT, handleExport, init, refresh,
  }
}
