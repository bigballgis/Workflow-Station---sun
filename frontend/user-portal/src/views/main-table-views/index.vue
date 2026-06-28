<script setup lang="ts">
import MainTableViewColumnMenu from '@/components/mainTableView/MainTableViewColumnMenu.vue'
import MainTableViewColumnResizeHandle from '@/components/mainTableView/MainTableViewColumnResizeHandle.vue'
import { useMainTableViewPage } from '@/composables/mainTableView/useMainTableViewPage'

const {
  t, Search, Download, Refresh, dataLoading, functionUnits, selectedViewId, searchKeyword,
  gridColumns, allRows, dataTotal, currentPage, pageSize, gridRuntime, filterDialogVisible, filterDialogField,
  filterDraft, widthDialogVisible, widthDialogField, widthDraft, tableRef, selectedTableRows,
  importProgressVisible, importProgressPercent, importProgressPhase, importProgressFileName,
  importResultVisible, importResult, importProgressLabel, importResultStatus, importResultHeadline,
  selectedFuCode, selectedViewMeta, showExportButton, selectedFu, displayColumns, groupedViews,
  MTV_SELECTION_COL_WIDTH, gridTotalColumnWidth, gridInnerStyle, processedRows, groupedRows, pagedRows, displayTotal,
  handleSearch, handlePageChange, formatCell, isRowSelectable, getRowKey, onSelectionChange, openRow, columnIndex,
  isFkLinkCell, openFkTarget,
  handleColumnCommand, applyColumnFilter, clearColumnFilter, applyColumnWidth, handleColumnResize, handleColumnResizeEnd,
  handleExport, mtvHeaderCellClassName, rowClassName, spanMethod,
  loadData, columnWidth, isGroupHeaderRow, COLUMN_WIDTH_MIN, COLUMN_WIDTH_MAX,
} = useMainTableViewPage()
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
            style="width: 240px;"
            popper-class="mtv-view-select-popper"
          >
            <el-option-group
              v-for="group in groupedViews"
              :key="group.tableId ?? group.label"
              :label="group.label"
            >
              <el-option
                v-for="v in group.views"
                :key="v.id"
                :label="v.viewName"
                :value="v.id"
              >
                <span class="mtv-view-option">
                  <span class="mtv-view-option-name">{{ v.viewName }}</span>
                  <el-tag
                    v-if="v.isDefault"
                    size="small"
                    type="info"
                    effect="plain"
                  >
                    {{ t('mainTableView.defaultTag') }}
                  </el-tag>
                </span>
              </el-option>
            </el-option-group>
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
              <template v-else-if="isFkLinkCell(col, row)">
                <a
                  class="mtv-fk-link"
                  :title="t('mainTableView.openRelatedRecord')"
                  @click.stop="openFkTarget(col, row)"
                >{{ formatCell(row.values[col.fieldName]) }}</a>
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

.mtv-fk-link {
  color: var(--el-color-primary);
  cursor: pointer;

  &:hover { text-decoration: underline; }
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

<style lang="scss">
/* Teleported view-selector popper — styles the per-table group headers so the grouping reads clearly.
   Not scoped: the dropdown renders at <body> root via popper-class. */
.mtv-view-select-popper {
  .el-select-group__title {
    padding: 6px 12px 4px;
    margin-top: 2px;
    font-size: 12px;
    font-weight: 700;
    color: var(--el-color-primary);
    text-transform: uppercase;
    letter-spacing: 0.04em;
    background: var(--el-fill-color-light);
    border-top: 1px solid var(--el-border-color-lighter);
  }
  .el-select-group:first-child .el-select-group__title {
    border-top: none;
    margin-top: 0;
  }
  .el-select-group__wrap:not(:last-of-type)::after {
    display: none;
  }
  .el-select-dropdown__item {
    padding-left: 22px;
  }
  .mtv-view-option {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    width: 100%;
  }
}
</style>
