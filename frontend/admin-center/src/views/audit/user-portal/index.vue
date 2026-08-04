<template>
  <div class="page-container">
    <PageHeader :title="t('upAudit.title')" />

    <!-- Filter Area -->
    <div class="filter-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item :label="t('audit.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :start-placeholder="t('common.startDate')"
            :end-placeholder="t('common.endDate')"
            value-format="YYYY-MM-DD"
            :shortcuts="dateShortcuts"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item :label="t('audit.operator')">
          <el-input
            v-model="query.username"
            clearable
            :placeholder="t('audit.usernamePlaceholder')"
            style="width: 130px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="t('upAudit.functionUnit')">
          <el-select
            v-model="query.functionUnitCode"
            clearable
            filterable
            :placeholder="t('common.selectPlaceholder')"
            style="width: 220px"
          >
            <el-option
              v-for="item in functionUnitCodes"
              :key="item.code"
              :label="item.name || item.code || '-'"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('upAudit.changeType')">
          <el-select
            v-model="query.changeType"
            clearable
            :placeholder="t('common.selectPlaceholder')"
            style="width: 170px"
          >
            <el-option
              :label="t('upAudit.actionFIELD_UPDATE')"
              value="FIELD_UPDATE"
            />
            <el-option
              :label="t('upAudit.actionSUB_TABLE_ROW_ADD')"
              value="SUB_TABLE_ROW_ADD"
            />
            <el-option
              :label="t('upAudit.actionSUB_TABLE_ROW_UPDATE')"
              value="SUB_TABLE_ROW_UPDATE"
            />
            <el-option
              :label="t('upAudit.actionSUB_TABLE_ROW_DELETE')"
              value="SUB_TABLE_ROW_DELETE"
            />
            <el-option
              :label="t('upAudit.actionPROCESS_INITIATION')"
              value="PROCESS_INITIATION"
            />
            <el-option
              :label="t('upAudit.actionRECORD_NOTE_ADD')"
              value="RECORD_NOTE_ADD"
            />
            <el-option
              :label="t('upAudit.actionRECORD_NOTE_UPDATE')"
              value="RECORD_NOTE_UPDATE"
            />
            <el-option
              :label="t('upAudit.actionRECORD_NOTE_DELETE')"
              value="RECORD_NOTE_DELETE"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('upAudit.processInstanceId')">
          <el-input
            v-model="query.processInstanceId"
            clearable
            :placeholder="t('upAudit.processInstanceId')"
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">
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
            <el-icon><Download /></el-icon>{{ t('audit.batchExport') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Table -->
    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="logs"
      stripe
      size="small"
      highlight-current-row
      style="width: 100%"
      :header-cell-style="{ background: '#f5f7fa', whiteSpace: 'nowrap' }"
      @sort-change="handleSortChange"
    >
      <el-table-column
        prop="changeType"
        :label="t('upAudit.changeType')"
        min-width="140"
        sortable="custom"
      >
        <template #default="{ row }">
          <el-tag :type="changeTypeTag(row.changeType)" size="small">
            {{ changeTypeText(t, row.changeType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="functionUnitCode"
        :label="t('upAudit.functionUnit')"
        min-width="150"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.functionUnitName || row.functionUnitCode || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('upAudit.processInstanceId')"
        min-width="170"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span :title="row.processInstanceId">
            {{ row.processTitle || row.processInstanceId || '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('upAudit.stage')"
        min-width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.stageName || row.stageId || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('upAudit.subTableName')"
        min-width="120"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.subTableDisplayName || row.subTableName || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('upAudit.fieldName')"
        min-width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.fieldLabel || row.fieldName || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('upAudit.oldValue')"
        min-width="150"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ truncateValue(row.oldValue, 50) }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('upAudit.newValue')"
        min-width="150"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ truncateValue(row.newValue, 50) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="userName"
        :label="t('audit.operator')"
        min-width="120"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.userName || row.userId || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="timestamp"
        :label="t('audit.time')"
        min-width="170"
        sortable="custom"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span style="white-space: nowrap">{{ formatTimestamp(row.timestamp) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.actions')"
        width="80"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="showDetail(row)"
          >
            {{ t('common.view') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div
      v-if="!loading && logs.length === 0"
      class="empty-state"
    >
      <el-empty :description="t('upAudit.emptyText')">
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

    <!-- Detail Dialog -->
    <UserPortalAuditDetailDialog
      v-model:visible="detailDialogVisible"
      :record="currentRecord"
    />

    <!-- Export Dialog -->
    <AuditExportDialog
      v-model="exportDialogVisible"
      :export-fields="ALL_EXPORT_FIELDS"
      :total="total"
      :exporting="exporting"
      @export="(format, fields) => doExport(format, fields)"
    />
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Search, Download } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import AuditExportDialog from '@/views/audit/components/AuditExportDialog.vue'
import UserPortalAuditDetailDialog from './components/UserPortalAuditDetailDialog.vue'
import { useUserPortalAudit } from '@/composables/modules/useUserPortalAudit'

const { t } = useI18n()

const {
  loading, exporting,
  logs, total, page, size,
  detailDialogVisible, currentRecord,
  dateRange, sortField, sortOrder,
  query, functionUnitCodes,
  exportDialogVisible,
  ALL_EXPORT_FIELDS,
  dateShortcuts,
  changeTypeTag, changeTypeText, formatTimestamp, truncateValue,
  handleSearch, handleReset, handleSizeChange, handleSortChange,
  showDetail,
  openExportDialog, doExport,
} = useUserPortalAudit()
</script>

<style scoped>
.filter-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 16px 16px 0;
  margin-bottom: 12px;
}

.empty-state {
  padding: 20px 0;
}
</style>
