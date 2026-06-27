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

  const exportingTemplate = ref(false)
  const importDialogVisible = ref(false)
  const importing = ref(false)
  const importResult = ref<{ inserted: number; failed: number; errors: Array<{ row: number; field: string; message: string }> } | null>(null)

  // ---- Computed ----
  const selectedTable = computed(() => tables.value.find(t => t.id === selectedTableId.value) ?? null)

  /** Whether the current admin may write (add/edit/inactive/import) on the selected table. */
  const canWrite = computed(() => selectedTable.value?.permissionLevel !== 'READONLY')

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
  const visibleFieldColumns = computed(() =>
    fieldColumns.value.filter(f => !(f.isForeignKey && f.fkDisplayMode === 'hidden')),
  )

  const isFkFieldDisabled = (field: FieldDefinitionResponse) =>
    !!field.isForeignKey && (field.fkDisplayMode == null || field.fkDisplayMode === 'readonly')

  const pkStrategy = (field: FieldDefinitionResponse): string =>
    (field.pkGeneration as { strategy?: string } | undefined)?.strategy ?? 'uuid'

  /**
   * Primary key fields are non-editable by default. The only exception is the `manual`
   * strategy in add mode, where the user must type the key themselves. On edit the PK is
   * always locked; on add with any auto strategy (uuid/sequence/…) it is auto-generated + locked.
   */
  const isPkFieldDisabled = (field: FieldDefinitionResponse): boolean => {
    if (!field.isPrimaryKey) return false
    if (dialogMode.value === 'edit') return true
    return pkStrategy(field) !== 'manual'
  }

  const openAddDialog = async () => {
    if (!selectedTableId.value) return
    dialogMode.value = 'add'
    editingRowId.value = null
    formData.value = {}
    for (const f of fieldColumns.value) {
      if (f.isForeignKey && f.fkDisplayMode === 'hidden') continue
      ;(formData.value as Record<string, unknown>)[f.fieldName] =
        f.defaultValue ?? (f.dataType === 'BOOLEAN' ? false : null)
    }
    try {
      for (const f of fieldColumns.value) {
        if (!f.isPrimaryKey) continue
        const strategy = (f.pkGeneration as { strategy?: string } | undefined)?.strategy ?? 'uuid'
        if (strategy === 'manual') continue
        const res = await relationTableDataApi.allocatePrimaryKeys(selectedTableId.value, {
          fieldName: f.fieldName,
        })
        const values = (res as { values?: string[] })?.values ?? (res as { data?: { values?: string[] } })?.data?.values
        if (values?.[0] != null) {
          ;(formData.value as Record<string, unknown>)[f.fieldName] = values[0]
        }
      }
      dialogVisible.value = true
    } catch (e: unknown) {
      notifyError((e as Error)?.message || 'Failed to prepare new record')
    }
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

  const handleDownloadTemplate = async (format: 'csv' | 'xlsx') => {
    if (!selectedTableId.value) return
    exportingTemplate.value = true
    try {
      const blob = await relationTableDataApi.downloadTemplate(selectedTableId.value, format)
      const url = window.URL.createObjectURL(blob as Blob)
      const link = document.createElement('a'); link.href = url
      const name = selectedTable.value?.displayName || selectedTable.value?.tableName || 'template'
      link.setAttribute('download', `${name}-template.${format}`)
      document.body.appendChild(link); link.click(); link.remove(); window.URL.revokeObjectURL(url)
    } catch { notifyError('Template download failed') }
    finally { exportingTemplate.value = false }
  }

  const openImportDialog = () => { importResult.value = null; importDialogVisible.value = true }

  const handleImportFile = async (file: File) => {
    if (!selectedTableId.value) return
    importing.value = true
    importResult.value = null
    try {
      const format = file.name.toLowerCase().endsWith('.xlsx') ? 'xlsx' : 'csv'
      const res = await relationTableDataApi.importData(selectedTableId.value, file, format)
      importResult.value = res
      if (res.inserted > 0) {
        notifySuccess(`Imported ${res.inserted} row(s)${res.failed ? `, ${res.failed} failed` : ''}`)
        await fetchData()
      } else if (res.failed > 0) {
        notifyError(`All ${res.failed} row(s) failed validation`)
      }
    } catch (e: unknown) {
      const err = e as { response?: { data?: { error?: { message?: string }; message?: string } }; message?: string }
      notifyError(err?.response?.data?.error?.message || err?.response?.data?.message || err?.message || 'Import failed')
    } finally { importing.value = false }
  }

  // ---- Lifecycle ----
  const init = () => fetchTables()
  const refresh = () => { fetchTables(); if (selectedTableId.value) fetchData() }

  return {
    tableListLoading, dataLoading, exporting, saving,
    exportingTemplate, importDialogVisible, importing, importResult,
    tables, selectedTableId, searchKeyword, tableSearchKeyword, currentPage, pageSize, totalElements, dataRows,
    fetchDataError, dialogVisible, dialogMode, editingRowId, formData,
    selectedTable, canWrite, fieldColumns, visibleFieldColumns, filteredTables,
    isNumericType, isRowDisabled, isFkFieldDisabled, isPkFieldDisabled,
    fetchTables, fetchData, handleSelectTable, handlePageChange, handleSizeChange,
    openAddDialog, openEditDialog, handleSaveRecord, handleDisable, handleEnable, handleDelete,
    formatHKT, handleExport, handleDownloadTemplate, openImportDialog, handleImportFile, init, refresh,
  }
}
