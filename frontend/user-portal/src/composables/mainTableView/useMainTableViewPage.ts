import { ref, computed, watch, onMounted, onBeforeUnmount, reactive, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { Search, Download, Refresh, Upload } from '@element-plus/icons-vue'
import {
  mainTableViewApi,
  type FunctionUnitViewMenuItem, type MainTableViewSummary, type MainTableViewDataPage,
  type MainTableViewFieldColumn, type MainTableViewDataRow, type MainTableViewGroup,
  type MainTableViewImportResult, type ImportProgressPhase,
} from '@/api/mainTableView'
import type { ListColumnFilterRequest } from '@platform-shared/list/columnMeta'
import {
  COLUMN_WIDTH_MIN, COLUMN_WIDTH_MAX, columnWidth, setColumnWidth,
  createDefaultGridRuntime, initColumnOrder, insertGroupHeaders, isGroupHeaderRow,
  loadGridRuntimeFromSession, moveColumn, orderedColumns, pruneRuntimeToColumns,
  saveGridRuntimeToSession, toListColumnMeta,
  type GridColumnFilter, type GridDisplayRow, type GridRuntimeState, type GridSortDirection,
} from '@/utils/mainTableViewGridRuntime'
import { leftoverColumnWidth } from '@platform-shared/list/columnResizeCursor'
import {
  downloadMainTableViewRowsAsCsv, formatMainTableViewCell, extractFileLinks, type FileLink,
} from '@/utils/mainTableViewCsvExport'
import { useMainTableViewLookupHydration } from '@/composables/mainTableView/useMainTableViewLookupHydration'
import { useMainTableViewFkHydration } from '@/composables/mainTableView/useMainTableViewFkHydration'
import {
  filterTableGroups,
  groupViewsByTable,
  pickDefaultView,
  sortViewsByName,
  tableGroupKey,
} from '@/composables/mainTableView/mainTableViewNav'

export function useMainTableViewPage() {
  const { t, locale } = useI18n()
  const route = useRoute()
  const router = useRouter()

const dataLoading = ref(false)
const functionUnits = ref<FunctionUnitViewMenuItem[]>([])
const views = ref<MainTableViewSummary[]>([])
const selectedViewId = ref<number | null>(null)
const searchKeyword = ref('')
const gridColumns = ref<MainTableViewFieldColumn[]>([])
const allRows = ref<MainTableViewDataRow[]>([])
const dataGroups = ref<MainTableViewGroup[]>([])
const dataTotal = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
// Every state change reloads, so a slow response for an abandoned view must not overwrite a newer
// one. Only the reply to the most recently issued request is allowed to land.
let latestQuery = 0

const {
  hydrateLookupCells,
  formatHydratedCell,
} = useMainTableViewLookupHydration(gridColumns, allRows)

const {
  hydrateFkCells,
  formatFkDisplayCell,
} = useMainTableViewFkHydration(gridColumns, allRows)

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

// Left-panel (parity with Relation Tables): collapsible "Available Views" list + search.
const viewListCollapsed = ref(false)
const viewSearchKeyword = ref('')

// Group views by owning table for the left nav; tables with no visible views are dropped.
const groupedViews = computed(() => groupViewsByTable(views.value))

const filteredGroupedViews = computed(() =>
  filterTableGroups(groupedViews.value, viewSearchKeyword.value),
)

const selectedTableKey = computed(() => {
  const meta = selectedViewMeta.value
  if (!meta) return ''
  return tableGroupKey({
    tableId: meta.tableId ?? null,
    label: meta.tableLabel || meta.viewName || '',
  })
})

const currentTableViewsSorted = computed(() => {
  const group = groupedViews.value.find(g => tableGroupKey(g) === selectedTableKey.value)
  return sortViewsByName(group?.views ?? [], locale.value)
})

function handleSelectTable(index: string) {
  const group = groupedViews.value.find(g => tableGroupKey(g) === index)
  if (!group) return
  if (selectedViewId.value && group.views.some(v => v.id === selectedViewId.value)) {
    return
  }
  const next = pickDefaultView(group.views, locale.value)
  if (next && next.id !== selectedViewId.value) {
    selectedViewId.value = next.id
  }
}

const displayColumns = computed(() => orderedColumns(gridColumns.value, gridRuntime))

const MTV_SELECTION_COL_WIDTH = 48

const gridTotalColumnWidth = computed(() => {
  const colsWidth = displayColumns.value.reduce(
    (sum, col) => sum + columnWidth(col, gridRuntime),
    0,
  )
  return MTV_SELECTION_COL_WIDTH + colsWidth
})

// Width of the scroll viewport — tracked so we know whether the columns underflow the panel.
const gridScrollRef = ref<HTMLElement | null>(null)
const gridViewportWidth = ref(0)
let gridResizeObserver: ResizeObserver | null = null

const gridFits = computed(() =>
  gridViewportWidth.value > 0 && gridTotalColumnWidth.value <= gridViewportWidth.value,
)

const leftoverWidth = computed(() =>
  leftoverColumnWidth(gridViewportWidth.value, gridTotalColumnWidth.value),
)

const gridInnerStyle = computed(() => (
  gridFits.value
    ? { width: '100%' }
    : { width: `${gridTotalColumnWidth.value}px`, minWidth: '100%' }
))

// Forces el-table to fully rebuild (no stale row/column DOM reuse) whenever the selected view, its
// columns, or the group-by changes — otherwise switching a grouped view to another view can briefly
// render the old rows under the new headers.
const gridTableKey = computed(() =>
  `${selectedViewId.value ?? 'none'}|${gridRuntime.groupBy ?? ''}|${gridColumns.value.map(c => c.fieldName).join(',')}`,
)

// The database already applied the filters, the sort and the paging, so the rows in hand ARE the
// page. All that is left is to slot in the group headers the same query counted.
const pagedRows = computed<GridDisplayRow[]>(() =>
  insertGroupHeaders(allRows.value, gridRuntime.groupBy, dataGroups.value),
)

const displayTotal = computed(() => dataTotal.value)

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
      const firstGroup = groupViewsByTable(views.value)[0]
      selectedViewId.value = pickDefaultView(firstGroup?.views ?? [], locale.value)?.id ?? null
    }
  } catch {
    views.value = []
    selectedViewId.value = null
  }
}

/** The filters the user has completed, in the shape the backend list contract expects. */
function activeFilters(): ListColumnFilterRequest[] {
  return Object.entries(gridRuntime.filters).map(([field, filter]) => ({ field, ...filter }))
}

async function loadData() {
  if (!selectedViewId.value) {
    gridColumns.value = []
    allRows.value = []
    dataGroups.value = []
    dataTotal.value = 0
    return
  }
  const viewId = selectedViewId.value
  const queryId = ++latestQuery
  dataLoading.value = true
  try {
    const res = await mainTableViewApi.queryData(viewId, {
      page: currentPage.value - 1,
      size: pageSize.value,
      search: searchKeyword.value.trim() || undefined,
      filters: activeFilters(),
      sortField: gridRuntime.sort?.fieldName ?? undefined,
      sortDirection: gridRuntime.sort?.direction ?? undefined,
      groupBy: gridRuntime.groupBy ?? undefined,
    })
    if (queryId !== latestQuery) return
    const page: MainTableViewDataPage = res.data
    if (!page || !Array.isArray(page.columns) || !Array.isArray(page.rows) || !Array.isArray(page.groups)) {
      throw new Error('main table view data page is missing columns, rows, or groups')
    }
    if (typeof page.total !== 'number') {
      throw new Error('main table view data page is missing total')
    }
    gridColumns.value = page.columns
    allRows.value = page.rows
    dataGroups.value = page.groups
    dataTotal.value = page.total
    initColumnOrder(gridColumns.value, gridRuntime)
    // Drop any groupBy / sort / filter that references a column the new view doesn't have, so runtime
    // state from a previously-selected view can never mis-render against this view's data.
    pruneRuntimeToColumns(gridRuntime, gridColumns.value)
    selectedTableRows.value = []
    tableRef.value?.clearSelection()
    await hydrateLookupCells()
    await hydrateFkCells()
    await nextTick()
    tableRef.value?.doLayout?.()
  } catch (e: unknown) {
    if (queryId !== latestQuery) return
    ElMessage.error((e instanceof Error ? e.message : undefined) || t('mainTableView.loadDataFailed'))
    gridColumns.value = []
    allRows.value = []
    dataGroups.value = []
    dataTotal.value = 0
  } finally {
    if (queryId === latestQuery) {
      dataLoading.value = false
    }
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

// FK drill-down to a view in the SAME function unit only changes the query (viewId + fk), not the path,
// so the FU watcher never fires. Sync the selected view + search filter from the query here; switching
// selectedViewId triggers the watcher above to reload. Guarded so it only acts on a real viewId change.
watch(
  () => [route.query.viewId, route.query.fk] as const,
  ([rawViewId, rawFk]) => {
    if (!selectedFuCode.value) return
    const targetViewId = rawViewId ? Number(rawViewId) : null
    const nextSearch = rawFk ? String(rawFk) : ''
    if (targetViewId && views.value.some(v => v.id === targetViewId) && targetViewId !== selectedViewId.value) {
      searchKeyword.value = nextSearch
      selectedViewId.value = targetViewId
    } else if (targetViewId === selectedViewId.value && nextSearch !== searchKeyword.value) {
      // Same view, new FK filter — re-filter in place.
      searchKeyword.value = nextSearch
      currentPage.value = 1
      loadData()
    }
  },
)

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number, size: number) {
  const sizeChanged = size !== pageSize.value
  pageSize.value = size
  currentPage.value = sizeChanged ? 1 : page
  loadData()
}

function formatCell(colOrValue: MainTableViewFieldColumn | unknown, row?: GridDisplayRow | MainTableViewDataRow) {
  // Backward-compatible: formatCell(value) still works for non-lookup callers.
  if (row && colOrValue && typeof colOrValue === 'object' && 'fieldName' in (colOrValue as object)) {
    const col = colOrValue as MainTableViewFieldColumn
    if (isGroupHeaderRow(row as GridDisplayRow)) return ''
    if (col.columnType === 'fk_display') {
      return formatFkDisplayCell(col, row as MainTableViewDataRow)
    }
    return formatHydratedCell(col, row as MainTableViewDataRow)
  }
  return formatMainTableViewCell(colOrValue)
}

function isRowSelectable(row: GridDisplayRow) {
  return !isGroupHeaderRow(row)
}

function getRowKey(row: GridDisplayRow) {
  if (isGroupHeaderRow(row)) {
    return `group-${row._groupLabel}`
  }
  return row.rowKey
}

function onSelectionChange(rows: GridDisplayRow[]) {
  selectedTableRows.value = rows.filter(row => !isGroupHeaderRow(row)) as MainTableViewDataRow[]
}

async function handleExport() {
  if (!selectedViewId.value) return
  const baseName = selectedFu.value?.functionUnitName || 'view'
  const selected = selectedTableRows.value
  const cols = displayColumns.value

  const projectHydrated = (rows: MainTableViewDataRow[]): MainTableViewDataRow[] =>
    rows.map(row => ({
      ...row,
      values: Object.fromEntries(
        cols.map(col => [
          col.fieldName,
          col.columnType === 'fk_display'
            ? formatFkDisplayCell(col, row)
            : formatHydratedCell(col, row),
        ]),
      ),
    }))

  if (selected.length) {
    downloadMainTableViewRowsAsCsv(projectHydrated(selected), cols, baseName)
    ElMessage.success(t('mainTableView.exportSelected', { count: selected.length }))
    return
  }

  if (!dataTotal.value) {
    ElMessage.warning(t('mainTableView.exportNoRows'))
    return
  }
  // Only one page is in memory now, so exporting everything the current query matches is the
  // server's job — it re-runs that same query without the paging.
  const blob = await mainTableViewApi.exportCsv(selectedViewId.value, {
    page: 0,
    size: pageSize.value,
    search: searchKeyword.value.trim() || undefined,
    filters: activeFilters(),
    sortField: gridRuntime.sort?.fieldName ?? undefined,
    sortDirection: gridRuntime.sort?.direction ?? undefined,
    groupBy: gridRuntime.groupBy ?? undefined,
  })
  const url = window.URL.createObjectURL(new Blob([blob as unknown as BlobPart]))
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `${baseName}.csv`)
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
  ElMessage.success(t('mainTableView.exportAllHint', { count: dataTotal.value }))
}

/**
 * Row click. A view with its own detail form opens that; otherwise a row that
 * belongs to a request falls back to the request page. Rows that can do neither
 * say so rather than appearing inert.
 */
function openRow(row: GridDisplayRow) {
  if (isGroupHeaderRow(row)) return

  const detailFormId = selectedViewMeta.value?.detailFormId
  if (detailFormId && selectedViewId.value) {
    const rowKey = resolveRowKey(row)
    if (!rowKey) {
      ElMessage.info(t('mainTableView.rowNotAddressable'))
      return
    }
    router.push({
      path: `/views/${selectedFuCode.value}/detail`,
      query: { viewId: String(selectedViewId.value), rowKey },
    })
    return
  }

  if (row.processInstanceId) {
    router.push(`/applications/${row.processInstanceId}`)
    return
  }

  ElMessage.info(t('mainTableView.noDetailPage'))
}

/**
 * Stable identifier for a row. Prefers the declared primary key, then the common
 * synthetic id columns, so sub-table views (which carry no process instance) are
 * addressable too.
 */
function resolveRowKey(row: GridDisplayRow): string | null {
  const values = row.values || {}
  for (const candidate of ['id', 'id_idw', 'row_id']) {
    const v = values[candidate]
    if (v != null && String(v).trim() !== '') return String(v)
  }
  return row.processInstanceId ? String(row.processInstanceId) : null
}

// A cell renders as downloadable file link(s) when its value is one (or many) upload URLs.
function fileLinksOf(col: MainTableViewFieldColumn, row: GridDisplayRow): FileLink[] {
  if (isGroupHeaderRow(row)) return []
  return extractFileLinks(row.values?.[col.fieldName])
}

function isFileLinkCell(col: MainTableViewFieldColumn, row: GridDisplayRow): boolean {
  return fileLinksOf(col, row).length > 0
}

// A cell renders as a FK drill-down link when the column is a resolvable FK and the cell has a value.
function isFkLinkCell(col: MainTableViewFieldColumn, row: GridDisplayRow): boolean {
  if (isFileLinkCell(col, row)) return false
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

// A cell renders as a lookup link when the column references a Relation Table and the cell has a value.
function isLookupLinkCell(col: MainTableViewFieldColumn, row: GridDisplayRow): boolean {
  if (isFileLinkCell(col, row)) return false
  if (isGroupHeaderRow(row)) return false
  if (!col.isLookup || col.lookupTableId == null) return false
  const value = row.values?.[col.fieldName]
  return value !== null && value !== undefined && String(value) !== ''
}

// Drill to the referenced Relation Table's data, pre-filtered by this cell's lookup value.
function openLookupTarget(col: MainTableViewFieldColumn, row: GridDisplayRow) {
  if (!isLookupLinkCell(col, row)) return
  const raw = row.values?.[col.fieldName]
  // Lookup values are objects {id,name,...}: filter the target by the referenced row's id (the exact
  // PK match), falling back to the display name when no id is present.
  const search = raw && typeof raw === 'object'
    ? String((raw as Record<string, unknown>).id
        ?? (raw as Record<string, unknown>).name ?? '')
    : String(raw)
  router.push({
    path: '/relation-tables',
    query: { tableId: String(col.lookupTableId), search },
  })
}

// Download an upload file via fetch+Blob (mirrors the sub-table file download) so the browser saves
// it with the original name instead of navigating away. Cookies auto-send for the same-origin request.
const downloadingFiles = ref<Record<string, boolean>>({})

async function downloadFile(link: FileLink) {
  if (!link.url || downloadingFiles.value[link.url]) return
  downloadingFiles.value = { ...downloadingFiles.value, [link.url]: true }
  const msg = ElMessage({ message: t('common.downloading'), type: 'info', duration: 0 })
  try {
    const response = await fetch(link.url)
    if (!response.ok) {
      msg.close()
      ElMessage.error(response.status === 404 ? t('common.fileNotFound') : t('common.downloadFailed'))
      return
    }
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = link.name
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(blobUrl)
    msg.close()
  } catch {
    msg.close()
    ElMessage.error(t('common.downloadFailed'))
  } finally {
    const next = { ...downloadingFiles.value }
    delete next[link.url]
    downloadingFiles.value = next
  }
}

function columnIndex(fieldName: string): number {
  return gridRuntime.columnOrder.indexOf(fieldName)
}

/** Sorting, grouping and filtering are all questions for the database, so each one re-queries. */
function handleSortChange(col: MainTableViewFieldColumn, direction: GridSortDirection) {
  gridRuntime.sort = { fieldName: col.fieldName, direction }
  currentPage.value = 1
  loadData()
}

function handleClearSort() {
  gridRuntime.sort = null
  currentPage.value = 1
  loadData()
}

function handleGroupChange(col: MainTableViewFieldColumn, grouped: boolean) {
  gridRuntime.groupBy = grouped ? col.fieldName : null
  currentPage.value = 1
  loadData()
}

function openFilterDialog(col: MainTableViewFieldColumn) {
  filterDialogField.value = col
  filterDraft.value = gridRuntime.filters[col.fieldName]
    ? { ...gridRuntime.filters[col.fieldName] }
    : { operator: col.operators[0] ?? '', value: '' }
  filterDialogVisible.value = true
}

function openWidthDialog(col: MainTableViewFieldColumn) {
  widthDialogField.value = col
  widthDraft.value = columnWidth(col, gridRuntime)
  widthDialogVisible.value = true
}

function handleMoveColumn(col: MainTableViewFieldColumn, direction: 'left' | 'right') {
  moveColumn(gridRuntime, col.fieldName, direction)
  persistRuntime()
}

function applyColumnFilter(filter: GridColumnFilter) {
  if (!filterDialogField.value) return
  gridRuntime.filters[filterDialogField.value.fieldName] = { ...filter }
  currentPage.value = 1
  filterDialogVisible.value = false
  loadData()
}

function clearColumnFilter(col: MainTableViewFieldColumn) {
  delete gridRuntime.filters[col.fieldName]
  currentPage.value = 1
  filterDialogVisible.value = false
  loadData()
}

/** The dialog's clear button knows the column only through the dialog's own state. */
function clearFilterFromDialog() {
  if (!filterDialogField.value) return
  clearColumnFilter(filterDialogField.value)
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
    // Column 0 is the selection checkbox column (it ignores our #default slot), so hide it and let the
    // first DATA column (index 1) span the rest — that cell renders the group label via #default.
    if (columnIndex === 0) {
      return { rowspan: 0, colspan: 0 }
    }
    if (columnIndex === 1) {
      return {
        rowspan: 1,
        colspan: displayColumns.value.length + (leftoverWidth.value > 0 ? 1 : 0),
      }
    }
    return { rowspan: 0, colspan: 0 }
  }
  return { rowspan: 1, colspan: 1 }
}

// Re-run the table's internal layout when the fit mode flips so columns stretch/shrink immediately.
watch(gridFits, () => {
  nextTick(() => tableRef.value?.doLayout?.())
})

// Observe the scroll viewport (behind v-if) — (dis)connect as it mounts/unmounts.
watch(gridScrollRef, (el, prev) => {
  if (prev) gridResizeObserver?.unobserve(prev)
  if (el) {
    gridViewportWidth.value = el.clientWidth
    gridResizeObserver?.observe(el)
  } else {
    gridViewportWidth.value = 0
  }
})

onBeforeUnmount(() => {
  gridResizeObserver?.disconnect()
  gridResizeObserver = null
})

onMounted(async () => {
  if (typeof ResizeObserver !== 'undefined') {
    gridResizeObserver = new ResizeObserver(entries => {
      const w = entries[0]?.contentRect.width ?? 0
      if (w > 0) gridViewportWidth.value = w
    })
  }
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
    selectedFuCode, selectedViewMeta, showExportButton, showImportButton, selectedFu, displayColumns,
    viewListCollapsed, viewSearchKeyword, filteredGroupedViews, selectedTableKey, currentTableViewsSorted, handleSelectTable,
    MTV_SELECTION_COL_WIDTH, gridTotalColumnWidth, gridInnerStyle, gridScrollRef, gridFits, leftoverWidth, gridTableKey,
    pagedRows, displayTotal, toListColumnMeta,
    handleSearch, handlePageChange, formatCell, isRowSelectable, getRowKey, onSelectionChange, openRow, columnIndex,
    isFkLinkCell, openFkTarget, isLookupLinkCell, openLookupTarget, isFileLinkCell, fileLinksOf, downloadFile,
    handleSortChange, handleClearSort, handleGroupChange, openFilterDialog, openWidthDialog, handleMoveColumn,
    applyColumnFilter, clearColumnFilter, clearFilterFromDialog, applyColumnWidth,
    handleColumnResize, handleColumnResizeEnd,
    handleExport, triggerImport, handleImportFile, mtvHeaderCellClassName, rowClassName, spanMethod,
    loadData, columnWidth, isGroupHeaderRow, COLUMN_WIDTH_MIN, COLUMN_WIDTH_MAX,
  }
}