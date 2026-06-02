<template>
  <div class="page-container">
    <PageHeader :title="t('menu.audit')" />

    <!-- Filter Area -->
    <div class="filter-card">
      <el-form
        :inline="true"
        :model="query"
        class="search-form"
      >
        <el-form-item :label="t('audit.actionType')">
          <el-select
            v-model="query.action"
            clearable
            :placeholder="t('common.selectPlaceholder')"
            style="width: 120px"
          >
            <el-option
              label="Create"
              value="CREATE"
            />
            <el-option
              label="Update"
              value="UPDATE"
            />
            <el-option
              label="Delete"
              value="DELETE"
            />
            <el-option
              label="Query"
              value="QUERY"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('audit.resourceType')">
          <el-select
            v-model="query.resourceType"
            clearable
            :placeholder="t('common.selectPlaceholder')"
            style="width: 180px"
          >
            <el-option
              v-for="rt in filterResourceTypes"
              :key="rt"
              :label="resourceTypeText(rt)"
              :value="rt"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('audit.operator')">
          <el-input
            v-model="query.username"
            clearable
            :placeholder="t('audit.usernamePlaceholder')"
            style="width: 120px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('audit.result')">
          <el-select
            v-model="query.result"
            clearable
            :placeholder="t('common.selectPlaceholder')"
            style="width: 100px"
          >
            <el-option
              :label="t('audit.success')"
              value="SUCCESS"
            />
            <el-option
              :label="t('audit.failed')"
              value="FAILED"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('audit.ipAddress')">
          <el-input
            v-model="query.ipAddress"
            clearable
            placeholder="IP"
            style="width: 130px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('audit.resourceId')">
          <el-input
            v-model="query.resourceId"
            clearable
            :placeholder="t('audit.resourceId')"
            style="width: 120px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('audit.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :start-placeholder="t('common.startDate')"
            :end-placeholder="t('common.endDate')"
            value-format="YYYY-MM-DD"
            :shortcuts="dateShortcuts"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            @click="handleSearch"
          >
            <el-icon><Search /></el-icon>{{ t('common.search') }}
          </el-button>
          <el-button @click="handleReset">
            {{ t('common.reset') }}
          </el-button>
          <el-button
            type="primary"
            :loading="exporting"
            style="margin-left: 8px"
            @click="openExportDialog"
          >
            <el-icon><Download /></el-icon>{{ t('common.export') }}
          </el-button>
          <el-tooltip
            :content="autoRefreshPaused ? t('audit.resumeAutoRefresh') : t('audit.pauseAutoRefresh')"
            placement="top"
            effect="light"
          >
            <span
              class="auto-refresh-chip"
              :class="{ 'is-paused': autoRefreshPaused }"
              @click="toggleAutoRefresh"
            >
              <el-icon v-if="autoRefreshPaused"><VideoPause /></el-icon>
              <el-icon
                v-else
                class="spin-icon"
              ><RefreshRight /></el-icon>
              <span class="auto-refresh-countdown">
                {{ autoRefreshPaused ? t('audit.paused') : t('audit.autoRefreshIn', { n: refreshCountdown }) }}
              </span>
            </span>
          </el-tooltip>
        </el-form-item>
      </el-form>
    </div>

    <!-- Batch Actions Bar -->
    <div
      v-if="selectedRows.length > 0"
      class="batch-bar"
    >
      <span class="batch-info">{{ t('audit.selectedCount', { n: selectedRows.length }) }}</span>
      <el-button
        size="small"
        type="primary"
        plain
        @click="handleBatchExportCsv"
      >
        <el-icon><Download /></el-icon>{{ t('audit.batchExport') }}
      </el-button>
      <el-button
        size="small"
        @click="clearSelection"
      >
        {{ t('common.cancel') }}
      </el-button>
    </div>

    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="sortedLogs"
      stripe
      size="small"
      highlight-current-row
      style="width: 100%"
      :header-cell-style="{ background: '#f5f7fa', whiteSpace: 'nowrap' }"
      @sort-change="handleSortChange"
      @selection-change="handleSelectionChange"
    >
      <el-table-column
        type="selection"
        width="40"
      />
      <el-table-column
        prop="action"
        :label="t('audit.actionType')"
        min-width="110"
        sortable="custom"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            class="action-tag"
            style="white-space: nowrap"
          >
            {{ actionText(row.action) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="resourceType"
        :label="t('audit.resourceType')"
        min-width="220"
        sortable="custom"
        class-name="resource-type-cell"
      >
        <template #default="{ row }">
          <el-tooltip
            v-if="row.action === 'DATA_QUERIED' && (row.resourceType || row.resourceId)"
            placement="top"
            effect="light"
            :show-after="300"
            :enterable="false"
          >
            <template #content>
              <div style="font-size:12px;max-width:220px">
                <div><b>{{ t('audit.resourceType') }}:</b> {{ resourceTypeText(row.resourceType) || '-' }}</div>
                <div><b>{{ t('audit.resourceId') }}:</b> {{ row.resourceId || '-' }}</div>
              </div>
            </template>
            <span style="cursor:default">{{ resourceTypeText(row.resourceType) || '-' }} <el-icon style="font-size:11px;color:#409eff"><InfoFilled /></el-icon></span>
          </el-tooltip>
          <span v-else>{{ resourceTypeText(row.resourceType) || '' }}</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="username"
        :label="t('audit.operator')"
        min-width="130"
        sortable="custom"
        show-overflow-tooltip
      />
      <el-table-column
        prop="ipAddress"
        :label="t('audit.ipAddress')"
        min-width="150"
        sortable="custom"
        show-overflow-tooltip
      />
      <el-table-column
        prop="result"
        :label="t('audit.result')"
        min-width="100"
        sortable="custom"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <el-tag
            :type="row.result === 'SUCCESS' ? 'success' : row.result === 'PENDING' ? 'warning' : 'danger'"
            size="small"
            style="white-space: nowrap"
          >
            {{ row.result === 'SUCCESS' ? t('audit.success') : row.result === 'PENDING' ? t('audit.pending') : t('audit.failed') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="duration"
        :label="t('audit.duration')"
        min-width="100"
        sortable="custom"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span style="white-space: nowrap">{{ row.duration }}ms</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        :label="t('audit.time')"
        min-width="220"
        sortable="custom"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span style="white-space: nowrap">{{ formatTime(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.actions')"
        width="80"
        fixed="right"
      >
        <template #default="{ row }">
          <el-tooltip
            :content="getPreviewContent(row)"
            placement="left"
            :show-after="300"
            effect="light"
            :enterable="false"
          >
            <el-button
              link
              type="primary"
              @click="showDetail(row)"
            >
              {{ t('common.view') }}
            </el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <div
      v-if="!loading && logs.length === 0"
      class="empty-state"
    >
      <el-empty :description="t('audit.emptyText')">
        <el-button
          type="primary"
          @click="handleReset"
        >
          {{ t('audit.resetFilter') }}
        </el-button>
      </el-empty>
    </div>

    <div class="pagination-container">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="handleSearch"
      />
    </div>

    <!-- Export Dialog (extracted to AuditExportDialog.vue) -->
    <AuditExportDialog
      v-model="exportDialogVisible"
      v-model:selected-fields="selectedExportFields"
      v-model:select-all="exportSelectAll"
      v-model:indeterminate="exportIndeterminate"
      :total="exportRecordCount"
      :exporting="exporting"
      :export-fields="ALL_EXPORT_FIELDS"
      @export="doExport"
    />

    <!-- Detail Dialog (extracted to AuditDetailDialog.vue) -->
    <AuditDetailDialog
      v-model="detailDialogVisible"
      :log="currentLog"
      :action-type="actionType"
      :action-text="actionText"
      :resource-type-text="resourceTypeText"
      :format-time="formatTime"
      :action-category="actionCategory"
      :format-json-highlight="formatJsonHighlight"
      :before-data="currentBeforeData"
      :after-data="currentAfterData"
      :before-compare="currentBeforeCompare"
      :after-compare="currentAfterCompare"
    />
  </div>
</template>

<script setup lang="ts">
import { onActivated } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download, Search, InfoFilled, RefreshRight, VideoPause } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import AuditExportDialog from './components/AuditExportDialog.vue'
import AuditDetailDialog from './components/AuditDetailDialog.vue'
import { useAudit } from '@/composables/modules/useAudit'

const { t } = useI18n()

const {
  loading, exporting, logs, total, page, size,
  detailDialogVisible, currentLog, dateRange,
  tableRef, selectedRows,
  query, resourceTypes, filterResourceTypes,
  refreshCountdown, autoRefreshPaused, toggleAutoRefresh,
  exportDialogVisible, exportSelectAll, exportIndeterminate,
  exportRecordCount,
  ALL_EXPORT_FIELDS, selectedExportFields,
  openExportDialog, doExport, handleBatchExportCsv,
  dateShortcuts,
  sortedLogs,
  currentBeforeData, currentAfterData,
  currentBeforeCompare, currentAfterCompare,
  actionType, actionText, actionCategory, resourceTypeText, formatTime,
  formatJsonHighlight,
  handleSearch, handleReset, handleSizeChange, handleSortChange,
  handleSelectionChange, clearSelection,
  showDetail, getPreviewContent,
} = useAudit()

// Re-fetch when navigating back (keep-alive reactivation)
onActivated(() => {
  handleSearch()
})
</script>

<style scoped>
.filter-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 16px 16px 0;
  margin-bottom: 12px;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
  padding: 8px 14px;
  margin-bottom: 8px;
}

.batch-info {
  font-size: 13px;
  color: #409eff;
  font-weight: 500;
}

.empty-state {
  padding: 20px 0;
}

/* Action Type tag: keep default Element Plus color scheme from type prop */
.action-tag { white-space: nowrap; }

/* Resource Type cell: allow full text to wrap rather than truncate */
:deep(.resource-type-cell .cell) {
  white-space: normal;
  word-break: break-word;
  line-height: 1.5;
}

/* Auto-refresh chip */
.auto-refresh-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 10px;
  padding: 3px 9px;
  border-radius: 12px;
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  background: #f0f9eb;
  border: 1px solid #b3e19d;
  color: #529b2e;
  transition: background 0.2s, border-color 0.2s;
}
.auto-refresh-chip:hover {
  background: #e1f3d8;
  border-color: #95d475;
}
.auto-refresh-chip.is-paused {
  background: #f5f5f5;
  border-color: #d9d9d9;
  color: #909399;
}
.auto-refresh-chip.is-paused:hover {
  background: #ebebeb;
}
.auto-refresh-countdown {
  white-space: nowrap;
  min-width: 46px;
  display: inline-block;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
.spin-icon {
  animation: spin 2s linear infinite;
  display: inline-flex;
}
</style>
