<script setup lang="ts">
import { ref, computed, watch, onMounted, reactive, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { Search, Download, Refresh, Upload } from '@element-plus/icons-vue'
import MainTableViewColumnMenu from '@/components/mainTableView/MainTableViewColumnMenu.vue'
import MainTableViewColumnResizeHandle from '@/components/mainTableView/MainTableViewColumnResizeHandle.vue'
import {
  mainTableViewApi,
  type FunctionUnitViewMenuItem,
  type MainTableViewSummary,
  type MainTableViewDataPage,
  type MainTableViewFieldColumn,
  type MainTableViewDataRow,
  type MainTableViewImportResult,
  type ImportProgressPhase,
} from '@/api/mainTableView'
import {
  applyGridRuntime,
  applyGroupBy,
  COLUMN_WIDTH_MIN,
  COLUMN_WIDTH_MAX,
  columnWidth,
  setColumnWidth,
  createDefaultGridRuntime,
  initColumnOrder,
  isGroupHeaderRow,
  loadGridRuntimeFromSession,
  moveColumn,
  orderedColumns,
  saveGridRuntimeToSession,
  type GridColumnFilter,
  type GridDisplayRow,
  type GridRuntimeState,
} from '@/utils/mainTableViewGridRuntime'

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
  } catch (e: any) {
    ElMessage.error(e?.message || t('mainTableView.loadFuFailed'))
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
    if (!views.value.some(v => v.id === selectedViewId.value)) {
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
  } catch (e: any) {
    ElMessage.error(e?.message || t('mainTableView.loadDataFailed'))
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
  searchKeyword.value = ''
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
  if (value == null) return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
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

function csvEscape(value: string): string {
  if (/[",\n\r]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`
  }
  return value
}

function downloadRowsAsCsv(rows: MainTableViewDataRow[], baseName: string) {
  const cols = displayColumns.value
  const header = [
    csvEscape('processInstanceId'),
    ...cols.map(col => csvEscape(col.displayLabel)),
  ].join(',')
  const lines = rows.map(row =>
    [
      csvEscape(row.processInstanceId),
      ...cols.map(col => csvEscape(formatCell(row.values[col.fieldName]))),
    ].join(','),
  )
  const content = `\uFEFF${[header, ...lines].join('\n')}`
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${baseName}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

function openRow(row: GridDisplayRow) {
  if (isGroupHeaderRow(row)) return
  if (row.processInstanceId) {
    router.push(`/applications/${row.processInstanceId}`)
  }
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

async function handleExport() {
  if (!selectedViewId.value) return
  const baseName = selectedFu.value?.functionUnitName || 'view'
  const selected = selectedTableRows.value

  if (selected.length) {
    downloadRowsAsCsv(selected, baseName)
    ElMessage.success(t('mainTableView.exportSelected', { count: selected.length }))
    return
  }

  const rows = processedRows.value as MainTableViewDataRow[]
  if (!rows.length) {
    ElMessage.warning(t('mainTableView.exportNoRows'))
    return
  }
  downloadRowsAsCsv(rows, baseName)
  ElMessage.success(t('mainTableView.exportAllHint', { count: rows.length }))
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
  } catch (e: any) {
    window.clearTimeout(progressFallbackTimer)
    stopImportProcessProgress()
    importProgressVisible.value = false
    ElMessage.error(e?.message || t('mainTableView.importFailed'))
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
    await loadViews()
    if (selectedViewId.value) {
      resetRuntimeForView(selectedViewId.value)
    }
    await loadData()
  }
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">
        {{ selectedFu?.functionUnitName || t('menu.views') }}
      </span>
    </div>

    <div
      v-loading="dataLoading"
      class="data-grid-panel"
    >
      <template v-if="selectedFuCode && selectedViewId">
        <div class="grid-toolbar">
          <el-select
            v-model="selectedViewId"
            size="default"
            style="width: 220px;"
          >
            <el-option
              v-for="v in views"
              :key="v.id"
              :label="v.viewName"
              :value="v.id"
            />
          </el-select>
          <el-input
            v-model="searchKeyword"
            :placeholder="t('common.search')"
            clearable
            style="width: 240px;"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button
            :icon="Refresh"
            @click="loadData"
          >
            {{ t('common.refresh') }}
          </el-button>
          <el-button
            v-if="showExportButton"
            type="primary"
            :icon="Download"
            @click="handleExport"
          >
            {{ t('common.export') }}
          </el-button>
          <el-button
            v-if="showImportButton"
            :icon="Upload"
            :loading="importing"
            @click="triggerImport"
          >
            {{ t('common.import') }}
          </el-button>
          <input
            ref="importInputRef"
            type="file"
            accept=".csv,text/csv"
            class="hidden-import-input"
            @change="handleImportFile"
          >
          <span
            v-if="selectedTableRows.length"
            class="grid-hint"
          >
            {{ t('mainTableView.selectedRows', { count: selectedTableRows.length }) }}
          </span>
          <span
            v-if="dataTotal > allRows.length"
            class="grid-hint"
          >
            {{ t('mainTableView.rowsTruncated', { shown: allRows.length, total: dataTotal }) }}
          </span>
        </div>

        <div
          v-if="displayColumns.length"
          class="mtv-data-grid-scroll"
        >
          <div
            class="mtv-data-grid-inner"
            :style="gridInnerStyle"
          >
            <el-table
              ref="tableRef"
              :data="pagedRows"
              :row-key="getRowKey"
              stripe
              :fit="false"
              table-layout="fixed"
              style="width: 100%;"
              class="mtv-data-grid"
              :header-cell-class-name="mtvHeaderCellClassName"
              :span-method="spanMethod"
              :row-class-name="rowClassName"
              @row-click="(row: GridDisplayRow) => openRow(row)"
              @selection-change="onSelectionChange"
            >
          <el-table-column
            type="selection"
            width="48"
            :selectable="isRowSelectable"
            reserve-selection
          />
          <el-table-column
            v-for="col in displayColumns"
            :key="col.fieldName"
            :prop="col.fieldName"
            :width="columnWidth(col, gridRuntime)"
            show-overflow-tooltip
          >
            <template #header>
              <div class="col-header-cell">
                <MainTableViewColumnMenu
                  :column="col"
                  :can-move-left="columnIndex(col.fieldName) > 0"
                  :can-move-right="columnIndex(col.fieldName) < gridRuntime.columnOrder.length - 1"
                  :is-grouped="gridRuntime.groupBy === col.fieldName"
                  :has-filter="!!gridRuntime.filters[col.fieldName]"
                  @command="(action) => handleColumnCommand(col, action)"
                />
                <MainTableViewColumnResizeHandle
                  :initial-width="columnWidth(col, gridRuntime)"
                  @resize="(width) => handleColumnResize(col.fieldName, width)"
                  @resize-end="handleColumnResizeEnd"
                />
              </div>
            </template>
            <template #default="{ row }">
              <template v-if="isGroupHeaderRow(row)">
                <div class="group-header-cell">
                  <strong>{{ row._groupLabel }}</strong>
                  <span class="group-count">({{ row._groupCount }})</span>
                </div>
              </template>
              <template v-else>
                {{ formatCell(row.values[col.fieldName]) }}
              </template>
            </template>
          </el-table-column>
            </el-table>
          </div>
        </div>

        <div
          v-if="displayTotal > pageSize"
          class="pagination-wrap"
        >
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="displayTotal"
            layout="total, prev, pager, next"
            @current-change="handlePageChange"
          />
        </div>
      </template>

      <el-empty
        v-else
        :description="t('mainTableView.selectFuAndView')"
      />
    </div>

    <el-dialog
      v-model="filterDialogVisible"
      :title="filterDialogField ? `${t('mainTableView.colFilterBy')}: ${filterDialogField.displayLabel}` : ''"
      width="420px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item :label="t('mainTableView.filterOperator')">
          <el-select
            v-model="filterDraft.operator"
            style="width: 100%;"
          >
            <el-option
              :label="t('mainTableView.filterOpContains')"
              value="contains"
            />
            <el-option
              :label="t('mainTableView.filterOpEquals')"
              value="eq"
            />
            <el-option
              :label="t('mainTableView.filterOpNotEquals')"
              value="ne"
            />
            <el-option
              :label="t('mainTableView.filterOpStartsWith')"
              value="startsWith"
            />
            <el-option
              :label="t('mainTableView.filterOpEndsWith')"
              value="endsWith"
            />
            <el-option
              :label="t('mainTableView.filterOpNotContains')"
              value="notContains"
            />
            <el-option
              :label="t('mainTableView.filterOpHasData')"
              value="isNotNull"
            />
            <el-option
              :label="t('mainTableView.filterOpNoData')"
              value="isNull"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="filterDraft.operator !== 'isNull' && filterDraft.operator !== 'isNotNull'"
          :label="t('mainTableView.filterValue')"
        >
          <el-input v-model="filterDraft.value" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clearColumnFilter">
          {{ t('common.clear') }}
        </el-button>
        <el-button
          type="primary"
          @click="applyColumnFilter"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="widthDialogVisible"
      :title="widthDialogField ? `${t('mainTableView.colColumnWidth')}: ${widthDialogField.displayLabel}` : ''"
      width="360px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item :label="t('mainTableView.columnWidthPx')">
          <el-slider
            v-model="widthDraft"
            :min="COLUMN_WIDTH_MIN"
            :max="COLUMN_WIDTH_MAX"
            show-input
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="widthDialogVisible = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          @click="applyColumnWidth"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importProgressVisible"
      :title="t('mainTableView.importProgressTitle')"
      width="440px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      align-center
    >
      <p
        v-if="importProgressFileName"
        class="import-progress-file"
      >
        {{ importProgressFileName }}
      </p>
      <p class="import-progress-label">
        {{ importProgressLabel }}
      </p>
      <el-progress
        :percentage="importProgressPercent"
        :stroke-width="14"
        striped
        striped-flow
      />
    </el-dialog>

    <el-dialog
      v-model="importResultVisible"
      :title="t('mainTableView.importResultTitle')"
      width="540px"
      destroy-on-close
      align-center
    >
      <el-result
        :icon="importResultStatus"
        :title="importResultHeadline"
        :sub-title="importProgressFileName"
      />
      <div
        v-if="importResult"
        class="import-result-stats"
      >
        <div class="import-stat import-stat--created">
          <span class="import-stat-value">{{ importResult.createdCount }}</span>
          <span class="import-stat-label">{{ t('mainTableView.importResultCreated') }}</span>
        </div>
        <div class="import-stat import-stat--updated">
          <span class="import-stat-value">{{ importResult.updatedCount }}</span>
          <span class="import-stat-label">{{ t('mainTableView.importResultUpdated') }}</span>
        </div>
        <div class="import-stat import-stat--skipped">
          <span class="import-stat-value">{{ importResult.skippedCount }}</span>
          <span class="import-stat-label">{{ t('mainTableView.importResultSkipped') }}</span>
        </div>
        <div
          class="import-stat import-stat--errors"
          :class="{ 'import-stat--has-errors': importResult.errorCount > 0 }"
        >
          <span class="import-stat-value">{{ importResult.errorCount }}</span>
          <span class="import-stat-label">{{ t('mainTableView.importResultErrors') }}</span>
        </div>
      </div>
      <div
        v-if="importResult?.errors?.length"
        class="import-error-section"
      >
        <div class="import-error-title">
          {{ t('mainTableView.importErrorList') }}
        </div>
        <el-scrollbar max-height="220px">
          <ul class="import-error-list">
            <li
              v-for="(err, idx) in importResult.errors"
              :key="idx"
            >
              {{ err }}
            </li>
          </ul>
        </el-scrollbar>
      </div>
      <template #footer>
        <el-button
          type="primary"
          @click="importResultVisible = false"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-container {
  padding: 16px 20px;
  height: 100%;
  min-width: 0;
  max-width: 100%;
}
.page-header {
  margin-bottom: 16px;
  .page-title {
    font-size: 18px;
    font-weight: 600;
  }
}
.data-grid-panel {
  min-height: calc(100vh - 160px);
  min-width: 0;
  max-width: 100%;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  padding: 16px;
  background: var(--el-bg-color);
}
.grid-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}
.grid-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.hidden-import-input {
  display: none;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.mtv-data-grid-scroll {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
  overflow-y: visible;
}
.mtv-data-grid-inner {
  display: block;
}
:deep(.mtv-data-grid .el-table__body-wrapper),
:deep(.mtv-data-grid .el-table__header-wrapper) {
  overflow-x: visible !important;
}
:deep(.mtv-data-grid .el-table__inner-wrapper) {
  width: 100% !important;
}
.col-header-cell {
  position: static;
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  min-height: 23px;
  box-sizing: border-box;
  padding-right: 12px;
}
:deep(.mtv-data-grid .el-table__header-wrapper th.mtv-resizable-col-header) {
  position: relative;
  overflow: visible !important;
}
:deep(.mtv-data-grid .el-table__header-wrapper th.mtv-resizable-col-header .cell) {
  position: static !important;
  display: flex !important;
  align-items: center;
  width: 100% !important;
  max-width: none !important;
  overflow: visible !important;
  text-overflow: clip;
}
:deep(.mtv-data-grid .el-table__header-wrapper th.mtv-resizable-col-header:has(.col-resize-handle:hover)),
:deep(.mtv-data-grid .el-table__header-wrapper th.mtv-resizable-col-header:has(.col-resize-handle.is-active)) {
  z-index: 12;
}
:deep(.el-table__row:not(.group-header-row)) {
  cursor: pointer;
}
:deep(.group-header-row) {
  background: var(--el-fill-color-light) !important;
  cursor: default;
  font-weight: 600;
}
.group-header-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.group-count {
  color: var(--el-text-color-secondary);
  font-weight: normal;
  font-size: 12px;
}

.import-progress-file {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.import-progress-label {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.import-result-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-top: 4px;
}

.import-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 8px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.import-stat-value {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}

.import-stat-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.import-stat--created .import-stat-value {
  color: var(--el-color-success);
}

.import-stat--updated .import-stat-value {
  color: var(--el-color-primary);
}

.import-stat--skipped .import-stat-value {
  color: var(--el-text-color-regular);
}

.import-stat--errors .import-stat-value {
  color: var(--el-text-color-placeholder);
}

.import-stat--has-errors .import-stat-value {
  color: var(--el-color-danger);
}

.import-error-section {
  margin-top: 16px;
}

.import-error-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-danger);
}

.import-error-list {
  margin: 0;
  padding: 0 0 0 18px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

:deep(.el-result) {
  padding: 12px 0 8px;
}
</style>
