<template>
  <div class="page-container">
    <PageHeader :title="t('menu.slaPolicies')">
      <template #actions>
        <DesignerHelpLink
          path="/sla-policies"
          :aria-label="t('sla.guideLinkAria')"
          test-id="sla-policies-guide-link"
        />
        <el-button @click="exportGridCsv('sla-policies')">
          <el-icon><Download /></el-icon>{{ t('common.export') }}
        </el-button>
      </template>
    </PageHeader>

    <el-card
      v-loading="loading"
      class="table-card"
    >
      <p class="page-hint">
        {{ t('sla.pageHint') }}
      </p>
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
                <template v-if="col.field === 'leadTimeDays'">
                  <el-tag
                    v-if="row.leadTimeDays == null"
                    size="small"
                    type="warning"
                  >
                    {{ t('sla.notSet') }}
                  </el-tag>
                  <span v-else>{{ t('sla.days', { n: row.leadTimeDays }) }}</span>
                </template>
                <el-tag
                  v-else-if="col.field === 'latestJobStatus' && row.latestJobStatus"
                  size="small"
                  :type="jobStatusTagType(row.latestJobStatus)"
                >
                  {{ t(`sla.job.${row.latestJobStatus}`) }}
                </el-tag>
                <template v-else-if="col.field === 'updatedAt'">
                  {{ row.updatedAt ? formatDateTime(row.updatedAt) : '-' }}
                </template>
                <template v-else>
                  {{ row[col.field] ?? '-' }}
                </template>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('common.actions')"
              :width="SLA_ACTIONS_COL_WIDTH"
              fixed="right"
              align="center"
            >
              <template #default="{ row }">
                <el-button
                  v-if="canEdit"
                  link
                  type="primary"
                  size="small"
                  @click="showEditDialog(row)"
                >
                  {{ t('common.edit') }}
                </el-button>
                <el-button
                  v-if="canEdit && row.leadTimeDays != null"
                  link
                  type="warning"
                  size="small"
                  @click="recalculate(row)"
                >
                  {{ t('sla.recalculate') }}
                </el-button>
                <el-button
                  link
                  size="small"
                  @click="showDetail(row)"
                >
                  {{ t('sla.details') }}
                </el-button>
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
        @change="loadPolicies"
      />
    </el-card>

    <SlaPolicyEditDialog
      v-model="editDialogVisible"
      :form="editForm"
      :saving="saving"
      @submit="submitEdit"
    />

    <SlaPolicyDetailDrawer
      v-model="detailVisible"
      :loading="detailLoading"
      :row="detailRow"
      :history="history"
      :jobs="jobs"
      :selected-job-id="selectedJobId"
      :items="jobItems"
      :items-loading="itemsLoading"
      @refresh="refreshDetail"
      @select-job="selectJob"
    />

    <ListFilterDialog
      v-model:visible="filterDialog.visible"
      :column="activeFilterColumn"
      :filter="activeFilter"
      @apply="onFilterApply"
      @clear="onFilterClear"
    />
  </div>
</template>

<script setup lang="ts">
import { onActivated, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DesignerHelpLink from '@/components/relation-table/DesignerHelpLink.vue'
import ListColumnHeader from '@platform-shared/list/ListColumnHeader.vue'
import ListFilterDialog from '@platform-shared/list/ListFilterDialog.vue'
import ListPagination from '@platform-shared/list/ListPagination.vue'
import type { ListColumnFilter } from '@platform-shared/list/columnMeta'
import { SLA_ACTIONS_COL_WIDTH, useSlaPolicies } from '@/composables/modules/useSlaPolicies'
import SlaPolicyEditDialog from './components/SlaPolicyEditDialog.vue'
import SlaPolicyDetailDrawer from './components/SlaPolicyDetailDrawer.vue'
import { jobStatusTagType } from './components/slaStatus'
import { formatDateTime } from '@/utils/format'

const { t } = useI18n()

const {
  loading,
  canEdit,
  loadPolicies,
  editDialogVisible,
  saving,
  editForm,
  showEditDialog,
  submitEdit,
  recalculate,
  detailVisible,
  detailLoading,
  detailRow,
  history,
  jobs,
  selectedJobId,
  jobItems,
  itemsLoading,
  showDetail,
  refreshDetail,
  selectJob,
  displayColumns,
  displayRows,
  selectionColumnWidth,
  handleGridSelectionChange,
  exportGridCsv,
  columnFilters,
  sort,
  filterDialog,
  pagination,
  activeFilterColumn,
  activeFilter,
  gridScrollRef,
  gridFits,
  gridTableHeight,
  gridInnerStyle,
  widthOf,
  setWidth,
  persistWidths,
  moveColumn,
  openFilter,
  applyFilter,
  clearFilter,
  applySort,
  clearSort,
} = useSlaPolicies()

function onSort(field: string, direction: 'ASC' | 'DESC') {
  applySort(field, direction)
  void loadPolicies()
}

function onClearSort() {
  clearSort()
  void loadPolicies()
}

function onClearFilter(field: string) {
  clearFilter(field)
  void loadPolicies()
}

function onFilterApply(filter: ListColumnFilter) {
  applyFilter(filter)
  void loadPolicies()
}

function onFilterClear() {
  onClearFilter(filterDialog.field)
}

onMounted(() => { void loadPolicies() })
onActivated(() => { void loadPolicies() })
</script>

<style scoped>
.page-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
