<template>
  <div class="page-container">
    <PageHeader :title="t('bi.dataViewAssignment.pageTitle')">
      <template #actions>
        <DesignerHelpLink path="/bi-data-view-assignment" :ariaLabel="t('bi.assignment.guidance')" />
        <el-button @click="exportAssignments">
          <el-icon><Download /></el-icon>{{ t('common.export') }}
        </el-button>
        <el-button
          type="primary"
          @click="openCreate"
        >
          <el-icon><Plus /></el-icon>{{ t('bi.dataViewAssignment.newAssignment') }}
        </el-button>
      </template>
    </PageHeader>

    <el-alert :title="t('bi.dataViewAssignment.guidance')" type="info" :closable="false" show-icon style="margin-bottom: 16px" />
    <el-card class="search-card">
      <el-form
        :inline="true"
        :model="query"
        class="search-form"
      >
        <el-form-item :label="t('bi.dataViewAssignment.filterDashboard')">
          <el-input
            v-model="query.dashboardTitle"
            clearable
            :placeholder="t('bi.dataViewAssignment.placeholderDashboardTitle')"
            style="width: 220px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item :label="t('bi.dataViewAssignment.filterTarget')">
          <el-select
            v-model="query.functionUnitId"
            clearable
            filterable
            :placeholder="t('bi.dataViewAssignment.placeholderTarget')"
            style="width: 260px"
          >
            <el-option
              v-for="unit in functionUnits"
              :key="unit.id"
              :label="unit.name"
              :value="unit.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            @click="search"
          >
            <el-icon><Search /></el-icon>{{ t('common.search') }}
          </el-button>
          <el-button @click="reset">
            <el-icon><Refresh /></el-icon>{{ t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-loading="loading" class="table-card">
      <div
        ref="gridScrollRef"
        class="list-data-grid-scroll"
      >
        <div
          class="list-data-grid-inner"
          :style="gridInnerStyle"
        >
          <el-table
            :data="displayRows"
            stripe
            :fit="false"
            table-layout="fixed"
            style="width: 100%"
            class="list-data-grid"
            :class="{ 'list-data-grid--fit': gridFits }"
            scrollbar-always-on
            :height="gridTableHeight || '100%'"
            @selection-change="handleGridSelectionChange"
          >
            <el-table-column
              type="selection"
              :width="selectionColumnWidth"
              fixed="left"
            />
            <el-table-column
              v-for="(col, colIndex) in displayColumns"
              :key="col.field"
              :prop="col.field"
              :width="widthOf(col.field)"
              show-overflow-tooltip
            >
              <template #header>
                <ListColumnHeader
                  :column="col"
                  :sort="sort.field === col.field ? sort.direction : null"
                  :filtered="!!columnFilters[col.field]"
                  :width="widthOf(col.field)"
                  :show-move="displayColumns.length > 1"
                  :can-move-left="colIndex > 0"
                  :can-move-right="colIndex < displayColumns.length - 1"
                  @sort-change="(direction: 'ASC' | 'DESC') => onSort(col.field, direction)"
                  @clear-sort="onClearSort"
                  @filter-open="openFilter(col.field)"
                  @clear-filter="onClearFilter(col.field)"
                  @move="(direction: 'left' | 'right') => moveColumn(col.field, direction)"
                  @width-change="(width: number) => setWidth(col.field, width)"
                  @width-commit="persistWidths"
                />
              </template>
              <template #default="{ row }">
                <template v-if="col.field === 'functionUnitName'">
                  <div>{{ row.functionUnitName }}</div>
                  <small class="secondary-text">{{ row.functionUnitCode }}</small>
                </template>
                <template v-else-if="col.field === 'tableDisplayName'">
                  <div class="table-name-cell">
                    <span>{{ row.tableDisplayName || row.tableName || t('bi.dataViewAssignment.tableUnavailable', { id: row.tableId }) }}</span>
                    <el-tag v-if="row.tableType" size="small" type="info">{{ t(`bi.dataViewAssignment.tableType${row.tableType}`) }}</el-tag>
                  </div>
                  <small v-if="row.tableDisplayName !== row.tableName" class="secondary-text">{{ row.tableName }}</small>
                </template>
                <template v-else-if="col.field === 'viewName'">
                  <span>{{ row.viewName }}</span>
                  <el-tag v-if="!row.viewAvailable" type="warning" size="small" :title="t('bi.dataViewAssignment.viewUnavailable')">{{ t('bi.dataViewAssignment.viewUnavailableLabel') }}</el-tag>
                </template>
                <el-tag v-else-if="col.field === 'enabled'" :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ t(row.enabled ? 'bi.assignment.enabled' : 'bi.assignment.disabled') }}
                </el-tag>
                <template v-else>{{ row[col.field] ?? '-' }}</template>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('bi.dataViewAssignment.colActions')"
              :width="ACTIONS_COL_WIDTH"
              fixed="right"
              align="center"
            >
              <template #header>
                {{ t('bi.dataViewAssignment.colActions') }}
              </template>
              <template #default="{ row }">
                <div class="action-cell">
                  <el-button
                    link
                    type="primary"
                    size="small"
                    @click="openEdit(row)"
                  >
                    {{ t('common.edit') }}
                  </el-button>
                  <AssignmentStatusAction :id="row.id" :dashboard-title="row.dashboardTitle" :enabled="row.enabled" scope="dataViewAssignment" :available="row.viewAvailable" @changed="load" />
                  <el-button
                    link
                    type="danger"
                    size="small"
                    @click="remove(row)"
                  >
                    {{ t('common.delete') }}
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <ListPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        :loading="loading"
        @change="load"
      />
    </el-card>

    <DataViewAssignmentFormDialog v-model="dialogVisible" :mode="dialogMode" :initial-row="editingRow" @success="load" />
    <ListFilterDialog v-model:visible="filterDialog.visible" :column="activeFilterColumn" :filter="activeFilter" @apply="onFilterApply" @clear="onFilterClear" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download, Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DesignerHelpLink from '@/components/relation-table/DesignerHelpLink.vue'
import { useBiDataViewAssignment } from '@/composables/modules/useBiDataViewAssignment'
import DataViewAssignmentFormDialog from './components/DataViewAssignmentFormDialog.vue'
import AssignmentStatusAction from './components/AssignmentStatusAction.vue'
import ListColumnHeader from '@platform-shared/list/ListColumnHeader.vue'
import ListFilterDialog from '@platform-shared/list/ListFilterDialog.vue'
import ListPagination from '@platform-shared/list/ListPagination.vue'
import type { ListColumnFilter } from '@platform-shared/list/columnMeta'
import type { ListColumnMeta } from '@platform-shared/list/columnMeta'
import { exportTableCsv } from '@platform-shared/list/tableExport'

const { t } = useI18n()
const {
  loading, query, functionUnits, dialogVisible, dialogMode, editingRow,
  load, search, reset, openCreate, openEdit, remove, initialize, ACTIONS_COL_WIDTH,
  displayColumns, displayRows, selectionColumnWidth, handleGridSelectionChange, gridSelectedRows,
  columnFilters, sort, filterDialog, pagination, activeFilterColumn, activeFilter,
  gridScrollRef, gridFits, gridTableHeight, gridInnerStyle, widthOf, setWidth, persistWidths,
  moveColumn, openFilter, applyFilter, clearFilter, applySort, clearSort,
} = useBiDataViewAssignment()
// Keep the original export fields in order, independently of the visible grid columns.
const exportColumns = computed<ListColumnMeta[]>(() => [
  ['dashboardTitle', 'bi.dataViewAssignment.colDashboard'],
  ['functionUnitName', 'bi.dataViewAssignment.colTarget'],
  ['functionUnitCode', 'bi.dataViewAssignment.exportFunctionUnitCode'],
  ['tableDisplayName', 'bi.dataViewAssignment.colTable'],
  ['tableName', 'bi.dataViewAssignment.exportTableName'],
  ['viewName', 'bi.dataViewAssignment.formView'],
  ['enabled', 'bi.assignment.enabled'],
].map(([field, label]) => ({
  field, label: t(label), kind: 'TEXT', filterable: false, sortable: false, operators: [],
})))
function exportAssignments() {
  exportTableCsv({
    rows: gridSelectedRows.value.length > 0 ? gridSelectedRows.value : displayRows.value,
    columns: exportColumns.value,
    filename: 'bi-data-view-assignments',
  })
}
function onSort(field: string, direction: 'ASC' | 'DESC') { applySort(field, direction); void load() }
function onClearSort() { clearSort(); void load() }
function onClearFilter(field: string) { clearFilter(field); void load() }
function onFilterApply(filter: ListColumnFilter) { applyFilter(filter); void load() }
function onFilterClear() { onClearFilter(filterDialog.field) }
onMounted(initialize)
</script>

<style scoped lang="scss">
.secondary-text { color: var(--el-text-color-secondary); }
.table-name-cell { display: flex; align-items: center; gap: 8px; }
</style>
