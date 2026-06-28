import { ref, computed, watch, onMounted, reactive, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { Search, Download, Refresh, Upload } from '@element-plus/icons-vue'
import {
  mainTableViewApi,
  type FunctionUnitViewMenuItem, type MainTableViewSummary, type MainTableViewDataPage,
  type MainTableViewFieldColumn, type MainTableViewDataRow, type MainTableViewImportResult,
  type ImportProgressPhase,
} from '@/api/mainTableView'
import {
  applyGridRuntime, applyGroupBy, COLUMN_WIDTH_MIN, COLUMN_WIDTH_MAX, columnWidth, setColumnWidth,
  createDefaultGridRuntime, initColumnOrder, isGroupHeaderRow, loadGridRuntimeFromSession, moveColumn,
  orderedColumns, saveGridRuntimeToSession, type GridColumnFilter, type GridDisplayRow, type GridRuntimeState,
} from '@/utils/mainTableViewGridRuntime'
import { downloadMainTableViewRowsAsCsv, formatMainTableViewCell } from '@/utils/mainTableViewCsvExport'

export function useMainTableViewPage() {
  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()

const dataLoading = ref(false)
const functionUnits = ref<FunctionUnitViewMenuItem[]>([])
const views = ref<MainTableViewSummary[]>([])
const selectedViewId = ref<number | null>(null)
const searchKeyword = ref('')
const gridColumns = ref<MainTableViewFieldColumn[]>([])
const allRows = ref<MainTableViewDataRow[]>([])
const dataTotal = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const gridRuntime = reactive<GridRuntimeState>(createDefaultGridRuntime())

const filterDialogVisible = ref(false)
const filterDialogField = ref<MainTableViewFieldColumn | null>(null)
const filterDraft = ref<GridColumnFilter>({ operator: 'contains', value: '' })

const widthDialogVisible = ref(false)
const widthDialogField = ref<MainTableViewFieldColumn | null>(null)
const widthDraft = ref(120)

const tableRef = ref<TableInstance>()
const selectedTableRows = ref<MainTableViewDataRow[]>([])
const importing = ref(false)
const importInputRef = ref<HTMLInputElement | null>(null)
const importProgressVisible = ref(false)
const importProgressPercent = ref(0)
const importProgressPhase = ref<ImportProgressPhase>('upload')
const importProgressFileName = ref('')
let importProcessTimer: ReturnType<typeof setInterval> | null = null

const importResultVisible = ref(false)
const importResult = ref<MainTableViewImportResult | null>(null)

const importProgressLabel = computed(() =>
  importProgressPhase.value === 'upload'
    ? t('mainTableView.importProgressUpload')
    : t('mainTableView.importProgressProcess'),
)

const importResultStatus = computed(() =>
  importResult.value && importResult.value.errorCount > 0 ? 'warning' : 'success',
)

const importResultHeadline = computed(() => {
  if (!importResult.value) return ''
  return importResult.value.errorCount > 0
    ? t('mainTableView.importResultHasErrors')
    : t('mainTableView.importResultAllSuccess')
})

const selectedFuCode = computed(() => String(route.params.functionUnitCode || ''))

const selectedViewMeta = computed(() =>
  views.value.find(v => v.id === selectedViewId.value),
)

const showExportButton = computed(() => selectedViewMeta.value?.enableExport !== false)
const showImportButton = computed(() => selectedViewMeta.value?.enableImport !== false)

const selectedFu = computed(() =>
  functionUnits.value.find(f => f.functionUnitCode === selectedFuCode.value),
)

// Group views by their owning table for the selector (parity with DW View Design left nav).
const groupedViews = computed(() => {
  const groups: Array<{ tableId: number | null; label: string; views: MainTableViewSummary[] }> = []
  const byTable = new Map<string, { tableId: number | null; label: string; views: MainTableViewSummary[] }>()
  for (const v of views.value) {
    const key = String(v.tableId ?? v.tableLabel ?? '')
    let g = byTable.get(key)
    if (!g) {
      g = { tableId: v.tableId ?? null, label: v.tableLabel || v.viewName, views: [] }
      byTable.set(key, g)
      groups.push(g)
    }
    g.views.push(v)
  }
  return groups
})

const displayColumns = computed(() => orderedColumns(gridColumns.value, gridRuntime))

const MTV_SELECTION_COL_WIDTH = 48

const gridTotalColumnWidth = computed(() => {
  const colsWidth = displayColumns.value.reduce(
    (sum, col) => sum + columnWidth(col, gridRuntime),
    0,
  )
  return MTV_SELECTION_COL_WIDTH + colsWidth
})

const gridInnerStyle = computed(() => ({
  width: `${gridTotalColumnWidth.value}px`,
  minWidth: '100%',
}))

const processedRows = computed(() => applyGridRuntime(allRows.value, gridRuntime))

const groupedRows = computed<GridDisplayRow[]>(() =>
  applyGroupBy(processedRows.value, gridRuntime.groupBy),
)

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return groupedRows.value.slice(start, start + pageSize.value)
})

const displayTotal = computed(() => groupedRows.value.length)

function persistRuntime() {
  if (selectedViewId.value) {
    saveGridRuntimeToSession(selectedViewId.value, gridRuntime)
  }
}

function resetRuntimeForView(viewId: number) {
  const loaded = loadGridRuntimeFromSession(viewId)
  Object.assign(gridRuntime, loaded)
}

async function loadFunctionUnits() {
  try {
    const res = await mainTableViewApi.listFunctionUnits()
    functionUnits.value = res.data || []
    if (!selectedFuCode.value && functionUnits.value.length) {
      await router.replace(`/views/${functionUnits.value[0].functionUnitCode}`)
    }
  } catch (e: unknown) {
    ElMessage.error((e instanceof Error ? e.message : undefined) || t('mainTableView.loadFuFailed'))
  }
}

async function loadViews() {
  if (!selectedFuCode.value) {
    views.value = []
    selectedViewId.value = null
    return
  }
  try {
    const res = await mainTableViewApi.listViews(selectedFuCode.value)
    views.value = res.data || []
    // FK drill-down: a `viewId` query param targets a specific view in this FU.
    const targetViewId = route.query.viewId ? Number(route.query.viewId) : null
    if (targetViewId && views.value.some(v => v.id === targetViewId)) {
      selectedViewId.value = targetViewId
    } else if (!views.value.some(v => v.id === selectedViewId.value)) {
      selectedViewId.value = views.value[0]?.id ?? null
    }
  } catch {
    views.value = []
    selectedViewId.value = null
  }
}

async function loadData() {
  if (!selectedViewId.value) {
    gridColumns.value = []
    allRows.value = []
    dataTotal.value = 0
    return
  }
  dataLoading.value = true
  try {
    const res = await mainTableViewApi.queryData(selectedViewId.value, {
      page: 0,
      size: 5000,
      search: searchKeyword.value.trim() || undefined,
    })
    const page: MainTableViewDataPage = res.data
    gridColumns.value = page.columns || []
    allRows.value = page.rows || []
    dataTotal.value = page.total
    initColumnOrder(gridColumns.value, gridRuntime)
    currentPage.value = 1
    selectedTableRows.value = []
    tableRef.value?.clearSelection()
    await nextTick()
    tableRef.value?.doLayout?.()
  } catch (e: unknown) {
    ElMessage.error((e instanceof Error ? e.message : undefined) || t('mainTableView.loadDataFailed'))
    gridColumns.value = []
    allRows.value = []
    dataTotal.value = 0
  } finally {
    dataLoading.value = false
  }
}

watch(selectedFuCode, async (code) => {
  if (!code) return
  currentPage.value = 1
  // FK drill-down passes the target FK value via the `fk` query param as the initial search filter.
  searchKeyword.value = route.query.fk ? String(route.query.fk) : ''
  await loadViews()
  if (selectedViewId.value) {
    resetRuntimeForView(selectedViewId.value)
  }
  await loadData()
})

watch(selectedViewId, (id) => {
  currentPage.value = 1
  if (id) {
    resetRuntimeForView(id)
  }
  loadData()
})

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
}

function formatCell(value: unknown) {
  return formatMainTableViewCell(value)
}

function isRowSelectable(row: GridDisplayRow) {
  return !isGroupHeaderRow(row)
}

function getRowKey(row: GridDisplayRow) {
  if (isGroupHeaderRow(row)) {
    return `group-${row._groupLabel}`
  }
  return row.processInstanceId
}

function onSelectionChange(rows: GridDisplayRow[]) {
  selectedTableRows.value = rows.filter(row => !isGroupHeaderRow(row)) as MainTableViewDataRow[]
}

async function handleExport() {
  if (!selectedViewId.value) return
  const baseName = selectedFu.value?.functionUnitName || 'view'
  const selected = selectedTableRows.value

  if (selected.length) {
    downloadMainTableViewRowsAsCsv(selected, displayColumns.value, baseName)
    ElMessage.success(t('mainTableView.exportSelected', { count: selected.length }))
    return
  }

  const rows = processedRows.value as MainTableViewDataRow[]
  if (!rows.length) {
    ElMessage.warning(t('mainTableView.exportNoRows'))
    return
  }
  downloadMainTableViewRowsAsCsv(rows, displayColumns.value, baseName)
  ElMessage.success(t('mainTableView.exportAllHint', { count: rows.length }))
}

function openRow(row: GridDisplayRow) {
  if (isGroupHeaderRow(row)) return
  if (row.processInstanceId) {
    router.push(`/applications/${row.processInstanceId}`)
  }
}

// A cell renders as a FK drill-down link when the column is a resolvable FK and the cell has a value.
function isFkLinkCell(col: MainTableViewFieldColumn, row: GridDisplayRow): boolean {
  if (isGroupHeaderRow(row)) return false
  if (!col.isForeignKey || !col.refViewId || !col.refFunctionUnitCode) return false
  const value = row.values?.[col.fieldName]
  return value !== null && value !== undefined && String(value) !== ''
}

// Drill to the referenced table's published default view, pre-filtered by this cell's FK value.
function openFkTarget(col: MainTableViewFieldColumn, row: GridDisplayRow) {
  if (!isFkLinkCell(col, row)) return
  const raw = row.values?.[col.fieldName]
  // Lookup values are objects {id,name,...}: filter the target by the referenced record's id.
  const value = raw && typeof raw === 'object'
    ? String((raw as Record<string, unknown>).id ?? '')
    : String(raw)
  router.push({
    path: `/views/${encodeURIComponent(col.refFunctionUnitCode as string)}`,
    query: { viewId: String(col.refViewId), fk: value },
  })
}

function columnIndex(fieldName: string): number {
  return gridRuntime.columnOrder.indexOf(fieldName)
}

function handleColumnCommand(col: MainTableViewFieldColumn, action: string) {
  switch (action) {
    case 'sortAsc':
      gridRuntime.sort = { fieldName: col.fieldName, direction: 'ASC' }
      currentPage.value = 1
      persistRuntime()
      break
    case 'sortDesc':
      gridRuntime.sort = { fieldName: col.fieldName, direction: 'DESC' }
      currentPage.value = 1
      persistRuntime()
      break
    case 'groupBy':
      gridRuntime.groupBy = gridRuntime.groupBy === col.fieldName ? null : col.fieldName
      currentPage.value = 1
      persistRuntime()
      break
    case 'filterBy':
      filterDialogField.value = col
      filterDraft.value = {
        operator: gridRuntime.filters[col.fieldName]?.operator || 'contains',
        value: gridRuntime.filters[col.fieldName]?.value || '',
      }
      filterDialogVisible.value = true
      break
    case 'columnWidth':
      widthDialogField.value = col
      widthDraft.value = columnWidth(col, gridRuntime)
      widthDialogVisible.value = true
      break
    case 'moveLeft':
      moveColumn(gridRuntime, col.fieldName, 'left')
      persistRuntime()
      break
    case 'moveRight':
      moveColumn(gridRuntime, col.fieldName, 'right')
      persistRuntime()
      break
    default:
      break
  }
}

function applyColumnFilter() {
  if (!filterDialogField.value) return
  const field = filterDialogField.value.fieldName
  const needsValue = filterDraft.value.operator !== 'isNull' && filterDraft.value.operator !== 'isNotNull'
  if (needsValue && !filterDraft.value.value.trim()) {
    delete gridRuntime.filters[field]
  } else {
    gridRuntime.filters[field] = { ...filterDraft.value }
  }
  currentPage.value = 1
  persistRuntime()
  filterDialogVisible.value = false
}

function clearColumnFilter() {
  if (!filterDialogField.value) return
  delete gridRuntime.filters[filterDialogField.value.fieldName]
  currentPage.value = 1
  persistRuntime()
  filterDialogVisible.value = false
}

function applyColumnWidth() {
  if (!widthDialogField.value) return
  setColumnWidth(gridRuntime, widthDialogField.value.fieldName, widthDraft.value)
  persistRuntime()
  widthDialogVisible.value = false
}

function handleColumnResize(fieldName: string, width: number) {
  setColumnWidth(gridRuntime, fieldName, width)
  nextTick(() => tableRef.value?.doLayout?.())
}

function handleColumnResizeEnd() {
  persistRuntime()
  nextTick(() => tableRef.value?.doLayout?.())
}

function triggerImport() {
  importInputRef.value?.click()
}

function stopImportProcessProgress() {
  if (importProcessTimer != null) {
    clearInterval(importProcessTimer)
    importProcessTimer = null
  }
}

function startImportProcessProgress() {
  stopImportProcessProgress()
  importProgressPhase.value = 'process'
  importProgressPercent.value = Math.max(importProgressPercent.value, 52)
  importProcessTimer = setInterval(() => {
    if (importProgressPercent.value < 92) {
      importProgressPercent.value += 1
    }
  }, 180)
}

function handleImportProgress(percent: number, phase: ImportProgressPhase) {
  importProgressPercent.value = percent
  if (phase === 'process') {
    startImportProcessProgress()
  }
}

function openImportResult(result: MainTableViewImportResult) {
  importResult.value = result
  importResultVisible.value = true
}

async function handleImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !selectedViewId.value) return

  importing.value = true
  importProgressVisible.value = true
  importProgressPercent.value = 0
  importProgressPhase.value = 'upload'
  importProgressFileName.value = file.name

  const progressFallbackTimer = window.setTimeout(() => {
    if (importing.value && importProgressPhase.value === 'upload' && importProgressPercent.value < 52) {
      handleImportProgress(52, 'process')
    }
  }, 400)

  try {
    const res = await mainTableViewApi.importCsv(
      selectedViewId.value,
      file,
      handleImportProgress,
    )
    window.clearTimeout(progressFallbackTimer)
    stopImportProcessProgress()
    importProgressPercent.value = 100
    await new Promise(resolve => setTimeout(resolve, 350))
    importProgressVisible.value = false
    openImportResult(res.data)
    await loadData()
  } catch (e: unknown) {
    window.clearTimeout(progressFallbackTimer)
    stopImportProcessProgress()
    importProgressVisible.value = false
    ElMessage.error((e instanceof Error ? e.message : undefined) || t('mainTableView.importFailed'))
  } finally {
    importing.value = false
  }
}

function mtvHeaderCellClassName({
  column,
}: {
  column: { type?: string; property?: string }
}) {
  if (column.type === 'selection') return ''
  return 'mtv-resizable-col-header'
}

function rowClassName({ row }: { row: GridDisplayRow }) {
  return isGroupHeaderRow(row) ? 'group-header-row' : ''
}

function spanMethod({
  row,
  columnIndex,
}: {
  row: GridDisplayRow
  columnIndex: number
}) {
  if (isGroupHeaderRow(row)) {
    if (columnIndex === 0) {
      return { rowspan: 1, colspan: displayColumns.value.length + 1 }
    }
    return { rowspan: 0, colspan: 0 }
  }
  return { rowspan: 1, colspan: 1 }
}

onMounted(async () => {
  await loadFunctionUnits()
  if (selectedFuCode.value) {
    // Honor a FK drill-down landing directly on this route (viewId + fk query params).
    if (route.query.fk) {
      searchKeyword.value = String(route.query.fk)
    }
    await loadViews()
    if (selectedViewId.value) {
      resetRuntimeForView(selectedViewId.value)
    }
    await loadData()
  }
})
  return {
    t, Search, Download, Refresh, Upload, dataLoading, functionUnits, views, selectedViewId, searchKeyword,
    gridColumns, allRows, dataTotal, currentPage, pageSize, gridRuntime, filterDialogVisible, filterDialogField,
    filterDraft, widthDialogVisible, widthDialogField, widthDraft, tableRef, selectedTableRows, importing,
    importInputRef, importProgressVisible, importProgressPercent, importProgressPhase, importProgressFileName,
    importResultVisible, importResult, importProgressLabel, importResultStatus, importResultHeadline,
    selectedFuCode, selectedViewMeta, showExportButton, showImportButton, selectedFu, displayColumns, groupedViews,
    MTV_SELECTION_COL_WIDTH, gridTotalColumnWidth, gridInnerStyle, processedRows, groupedRows, pagedRows, displayTotal,
    handleSearch, handlePageChange, formatCell, isRowSelectable, getRowKey, onSelectionChange, openRow, columnIndex,
    isFkLinkCell, openFkTarget,
    handleColumnCommand, applyColumnFilter, clearColumnFilter, applyColumnWidth, handleColumnResize, handleColumnResizeEnd,
    handleExport, triggerImport, handleImportFile, mtvHeaderCellClassName, rowClassName, spanMethod,
    loadData, columnWidth, isGroupHeaderRow, COLUMN_WIDTH_MIN, COLUMN_WIDTH_MAX,
  }
}