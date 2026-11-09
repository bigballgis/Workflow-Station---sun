<!--
  LDAP sync records (D-51): every LDAP / AD group sync run, read-only for SYS_ADMIN, Technical Leads and Auditors.
  Starting a sync stays a SYS_ADMIN operation outside this page; the members a sync adds or removes are in the
  Admin operation audit (operator "LDAP sync", D-32).
-->
<template>
  <div class="page-container">
    <PageHeader :title="t('ldapSync.title')">
      <template #actions>
        <DesignerHelpLink
          path="/ac-audit-ldap-sync"
          v-bind="{ ariaLabel: t('ldapSync.guideLinkAria') }"
          test-id="ldap-sync-guide-link"
        />
        <el-button @click="exportGridCsv('ldap-sync-records')">
          <el-icon><Download /></el-icon>{{ t('common.export') }}
        </el-button>
        <el-button @click="loadRecords">
          <el-icon><Refresh /></el-icon>{{ t('common.refresh') }}
        </el-button>
      </template>
    </PageHeader>

    <el-card
      v-loading="loading"
      class="table-card"
    >
      <div class="toolbar">
        <el-input
          v-model="keyword"
          :placeholder="t('ldapSync.searchPlaceholder')"
          clearable
          style="width: 280px"
          @keyup.enter="loadRecords"
          @clear="loadRecords"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button
          type="primary"
          @click="loadRecords"
        >
          {{ t('common.search') }}
        </el-button>
        <span class="record-count">{{ t('ldapSync.total', { count: pagination.total }) }}</span>
      </div>

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
              :show-overflow-tooltip="col.field === 'message' ? MESSAGE_TOOLTIP : true"
              :tooltip-formatter="col.field === 'message' ? messageCellTooltip : undefined"
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
                <el-tag
                  v-if="col.field === 'status'"
                  :type="statusTone(row.status)"
                  size="small"
                  disable-transitions
                >
                  {{ optionLabel('status', row.status) }}
                </el-tag>
                <span v-else-if="col.field === 'syncType'">{{ optionLabel('syncType', row.syncType) }}</span>
                <span v-else-if="col.field === 'startedAt'">{{ formatDate(row.startedAt) }}</span>
                <span v-else-if="col.field === 'durationMs'">{{ formatDuration(row.durationMs) }}</span>
                <template v-else>
                  {{ row[col.field] ?? '—' }}
                </template>
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
        @change="loadRecords"
      />
    </el-card>

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
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DesignerHelpLink from '@/components/relation-table/DesignerHelpLink.vue'
import { formatDate } from '@/utils/format'
import { useLdapSyncRecords } from '@/composables/modules/useLdapSyncRecords'
import ListColumnHeader from '@platform-shared/list/ListColumnHeader.vue'
import ListFilterDialog from '@platform-shared/list/ListFilterDialog.vue'
import ListPagination from '@platform-shared/list/ListPagination.vue'
import type { ListColumnFilter } from '@platform-shared/list/columnMeta'
import type { LdapSyncRecord } from '@/api/ldapSync'

const { t } = useI18n()

/** Message can run to 1,000 characters: its hover tooltip wraps at a readable width, one finding per line. */
const MESSAGE_TOOLTIP = { popperClass: 'ldap-sync-message-tooltip' }
const messageCellTooltip = ({ row }: { row: LdapSyncRecord }) => messageTooltip(row.message)

const {
  loading,
  keyword,
  statusTone,
  optionLabel,
  formatDuration,
  messageTooltip,
  loadRecords,
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
} = useLdapSyncRecords()

function onSort(field: string, direction: 'ASC' | 'DESC') {
  applySort(field, direction)
  void loadRecords()
}

function onClearSort() {
  clearSort()
  void loadRecords()
}

function onClearFilter(field: string) {
  clearFilter(field)
  void loadRecords()
}

function onFilterApply(filter: ListColumnFilter) {
  applyFilter(filter)
  void loadRecords()
}

function onFilterClear() {
  onClearFilter(filterDialog.field)
}

onMounted(() => { void loadRecords() })
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.record-count {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.table-card :deep(.el-scrollbar__bar.is-horizontal) {
  opacity: 1;
  height: 8px;
}

/* The table appends its cell tooltip inside itself, so the scoped selector reaches it. */
.table-card :deep(.ldap-sync-message-tooltip) {
  max-width: min(720px, calc(100vw - 32px));
  white-space: pre-line;
  overflow-wrap: anywhere;
}
</style>
