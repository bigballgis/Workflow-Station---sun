<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.audit') }}</span>
    </div>

    <!-- Filter Area -->
    <div class="filter-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item :label="t('audit.actionType')">
          <el-select v-model="query.action" clearable :placeholder="t('common.selectPlaceholder')" style="width: 120px">
            <el-option label="Create" value="CREATE" />
            <el-option label="Update" value="UPDATE" />
            <el-option label="Delete" value="DELETE" />
            <el-option label="Query"  value="QUERY"  />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('audit.resourceType')">
          <el-select v-model="query.resourceType" clearable :placeholder="t('common.selectPlaceholder')" style="width: 180px">
            <el-option v-for="rt in resourceTypes" :key="rt" :label="resourceTypeText(rt)" :value="rt" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('audit.operator')">
          <el-input v-model="query.username" clearable :placeholder="t('audit.usernamePlaceholder')" style="width: 120px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item :label="t('audit.result')">
          <el-select v-model="query.result" clearable :placeholder="t('common.selectPlaceholder')" style="width: 100px">
            <el-option :label="t('audit.success')" value="SUCCESS" />
            <el-option :label="t('audit.failed')" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('audit.ipAddress')">
          <el-input v-model="query.ipAddress" clearable placeholder="IP" style="width: 130px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item :label="t('audit.resourceId')">
          <el-input v-model="query.resourceId" clearable :placeholder="t('audit.resourceId')" style="width: 120px" @keyup.enter="handleSearch" />
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
          <el-button type="primary" @click="handleSearch" :loading="loading">
            <el-icon><Search /></el-icon>{{ t('common.search') }}
          </el-button>
          <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
          <el-button type="primary" :loading="exporting" style="margin-left: 8px" @click="openExportDialog">
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
              <el-icon v-else class="spin-icon"><RefreshRight /></el-icon>
              <span class="auto-refresh-countdown">
                {{ autoRefreshPaused ? t('audit.paused') : t('audit.autoRefreshIn', { n: refreshCountdown }) }}
              </span>
            </span>
          </el-tooltip>
        </el-form-item>
      </el-form>
    </div>

    <!-- Batch Actions Bar -->
    <div v-if="selectedRows.length > 0" class="batch-bar">
      <span class="batch-info">{{ t('audit.selectedCount', { n: selectedRows.length }) }}</span>
      <el-button size="small" type="primary" plain @click="handleBatchExportCsv">
        <el-icon><Download /></el-icon>{{ t('audit.batchExport') }}
      </el-button>
      <el-button size="small" @click="clearSelection">{{ t('common.cancel') }}</el-button>
    </div>

    <el-table
      ref="tableRef"
      :data="sortedLogs"
      v-loading="loading"
      stripe
      size="small"
      highlight-current-row
      @sort-change="handleSortChange"
      @selection-change="handleSelectionChange"
      style="width: 100%"
      :header-cell-style="{ background: '#f5f7fa', whiteSpace: 'nowrap' }"
    >
      <el-table-column type="selection" width="40" />
      <el-table-column prop="action" :label="t('audit.actionType')" min-width="110" sortable="custom">
        <template #default="{ row }">
          <el-tag size="small" class="action-tag" style="white-space: nowrap">{{ actionText(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resourceType" :label="t('audit.resourceType')" min-width="220" sortable="custom" class-name="resource-type-cell">
        <template #default="{ row }">
          <el-tooltip v-if="row.action === 'DATA_QUERIED' && (row.resourceType || row.resourceId)" placement="top" effect="light" :show-after="300" :enterable="false">
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
      <el-table-column prop="username" :label="t('audit.operator')" min-width="130" sortable="custom" show-overflow-tooltip />
      <el-table-column prop="ipAddress" :label="t('audit.ipAddress')" min-width="150" sortable="custom" show-overflow-tooltip />
      <el-table-column prop="result" :label="t('audit.result')" min-width="100" sortable="custom" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag
            :type="row.result === 'SUCCESS' ? 'success' : row.result === 'PENDING' ? 'warning' : 'danger'"
            size="small" style="white-space: nowrap">
            {{ row.result === 'SUCCESS' ? t('audit.success') : row.result === 'PENDING' ? t('audit.pending') : t('audit.failed') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="duration" :label="t('audit.duration')" min-width="100" sortable="custom" show-overflow-tooltip>
        <template #default="{ row }"><span style="white-space: nowrap">{{ row.duration }}ms</span></template>
      </el-table-column>
      <el-table-column prop="createdAt" :label="t('audit.time')" min-width="220" sortable="custom" show-overflow-tooltip>
        <template #default="{ row }"><span style="white-space: nowrap">{{ formatTime(row.createdAt) }}</span></template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="80" fixed="right">
        <template #default="{ row }">
          <el-tooltip :content="getPreviewContent(row)" placement="left" :show-after="300" effect="light" :enterable="false">
            <el-button link type="primary" @click="showDetail(row)">{{ t('common.view') }}</el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && logs.length === 0" class="empty-state">
      <el-empty :description="t('audit.emptyText')">
        <el-button type="primary" @click="handleReset">{{ t('audit.resetFilter') }}</el-button>
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

    <!-- Export Dialog -->
    <el-dialog v-model="exportDialogVisible" :title="t('common.export')" width="480px">
      <div class="export-dialog-body">
        <div class="export-stat">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ t('audit.exportEstimate', { n: total }) }}</span>
        </div>
        <div class="export-fields-label">{{ t('audit.exportSelectFields') }}</div>
        <div class="export-fields">
          <el-checkbox v-model="exportSelectAll" :indeterminate="exportIndeterminate" @change="handleExportSelectAll">
            {{ t('common.all') }}
          </el-checkbox>
          <el-divider style="margin: 8px 0" />
          <el-checkbox-group v-model="selectedExportFields" @change="handleExportFieldChange">
            <el-checkbox v-for="f in ALL_EXPORT_FIELDS" :key="f.key" :value="f.key">{{ f.label }}</el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
      <template #footer>
        <el-button @click="exportDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" plain :loading="exporting" @click="doExport('csv')" :disabled="selectedExportFields.length === 0">
          <el-icon><Download /></el-icon>CSV
        </el-button>
        <el-button type="primary" :loading="exporting" @click="doExport('excel')" :disabled="selectedExportFields.length === 0">
          <el-icon><Download /></el-icon>Excel
        </el-button>
      </template>
    </el-dialog>

    <!-- Detail Dialog -->
    <el-dialog v-model="detailDialogVisible" :title="t('audit.logDetail')" width="800px">
      <div class="log-detail" v-if="currentLog">
        <!-- Basic Info Section -->
        <div class="detail-section section-basic">
          <div class="section-title">{{ t('audit.basicInfo') }}</div>
          <div class="detail-grid">
            <div class="detail-row">
              <span class="detail-label">{{ t('audit.actionType') }}</span>
              <span class="detail-value"><el-tag :type="actionType(currentLog.action)" size="small">{{ actionText(currentLog.action) }}</el-tag></span>
            </div>
            <div class="detail-row">
              <span class="detail-label">{{ t('audit.operator') }}</span>
              <span class="detail-value">{{ currentLog.username || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">{{ t('audit.resourceType') }}</span>
              <span class="detail-value">{{ resourceTypeText(currentLog.resourceType) || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">{{ t('audit.resourceId') }}</span>
              <span class="detail-value">{{ currentLog.resourceId || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">{{ t('audit.ipAddress') }}</span>
              <span class="detail-value">{{ currentLog.ipAddress || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">{{ t('audit.result') }}</span>
              <span class="detail-value">
                <span :class="currentLog.result === 'SUCCESS' ? 'result-success' : currentLog.result === 'PENDING' ? 'result-pending' : 'result-danger'">
                  {{ currentLog.result === 'SUCCESS' ? t('audit.success') : currentLog.result === 'PENDING' ? t('audit.pending') : t('audit.failed') }}
                </span>
              </span>
            </div>
            <div class="detail-row">
              <span class="detail-label">{{ t('audit.requestMethod') }}</span>
              <span class="detail-value">{{ currentLog.requestMethod || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">{{ t('audit.requestPath') }}</span>
              <span class="detail-value path-value">{{ currentLog.requestPath || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">{{ t('audit.duration') }}</span>
              <span class="detail-value">{{ currentLog.duration != null ? currentLog.duration + 'ms' : '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">{{ t('audit.time') }}</span>
              <span class="detail-value">{{ formatTime(currentLog.createdAt) }}</span>
            </div>
            <div class="detail-row" v-if="currentLog.errorMessage">
              <span class="detail-label">{{ t('audit.errorMessage') }}</span>
              <span class="detail-value result-danger">{{ currentLog.errorMessage }}</span>
            </div>
          </div>
        </div>

        <!-- Before Change Section: hidden for Create & Query; full data for Delete; diff for Update -->
        <div class="detail-section section-before" v-if="currentBeforeData !== null">
          <div class="section-title">
            {{ t('audit.oldValue') }}
            <span class="section-badge" v-if="actionCategory(currentLog.action) === 'delete'">{{ t('audit.fullRecord') }}</span>
            <span class="section-badge badge-diff" v-if="actionCategory(currentLog.action) === 'update'">{{ t('audit.changedFieldsOnly') }}</span>
          </div>
          <div class="json-container">
            <pre class="json-content" :class="{ collapsed: !beforeExpanded }" v-html="formatJsonHighlight(currentBeforeData, currentBeforeCompare)"></pre>
            <el-button class="expand-btn" link type="primary" @click="beforeExpanded = !beforeExpanded">
              {{ beforeExpanded ? t('common.collapse') : t('common.expand') }}
            </el-button>
          </div>
        </div>

        <!-- After Change Section: hidden for Delete & Query; full data for Create; diff for Update -->
        <div class="detail-section section-after" v-if="currentAfterData !== null">
          <div class="section-title">
            {{ t('audit.newValue') }}
            <span class="section-badge" v-if="actionCategory(currentLog.action) === 'create'">{{ t('audit.fullRecord') }}</span>
            <span class="section-badge badge-diff" v-if="actionCategory(currentLog.action) === 'update'">{{ t('audit.changedFieldsOnly') }}</span>
          </div>
          <div class="json-container">
            <pre class="json-content" :class="{ collapsed: !afterExpanded }" v-html="formatJsonHighlight(currentAfterData, currentAfterCompare)"></pre>
            <el-button class="expand-btn" link type="primary" @click="afterExpanded = !afterExpanded">
              {{ afterExpanded ? t('common.collapse') : t('common.expand') }}
            </el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { Download, Search, InfoFilled, RefreshRight, VideoPause } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { queryAuditLogs, exportAuditLogs, getAuditResourceTypes, type AuditLog, type AuditQueryRequest } from '@/api/audit'

const { t } = useI18n()

const loading = ref(false)
const exporting = ref(false)
const logs = ref<AuditLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const detailDialogVisible = ref(false)
const currentLog = ref<AuditLog | null>(null)
const dateRange = ref<string[] | null>(null)
const beforeExpanded = ref(false)
const afterExpanded = ref(false)
const tableRef = ref<TableInstance>()
const selectedRows = ref<AuditLog[]>([])
const sortField = ref('createdAt')
const sortOrder = ref<'ascending' | 'descending'>('descending')

// Auto-refresh
const AUTO_REFRESH_SECONDS = 30
const refreshCountdown = ref(AUTO_REFRESH_SECONDS)
const autoRefreshPaused = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const stopAutoRefresh = () => {
  if (refreshTimer !== null) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  refreshCountdown.value = AUTO_REFRESH_SECONDS
  if (autoRefreshPaused.value) return
  refreshTimer = setInterval(() => {
    refreshCountdown.value -= 1
    if (refreshCountdown.value <= 0) {
      if (!detailDialogVisible.value) {
        handleSearch()
      } else {
        // Detail dialog is open — reset the countdown and wait
        refreshCountdown.value = AUTO_REFRESH_SECONDS
      }
    }
  }, 1000)
}

const toggleAutoRefresh = () => {
  autoRefreshPaused.value = !autoRefreshPaused.value
  if (autoRefreshPaused.value) {
    stopAutoRefresh()
  } else {
    startAutoRefresh()
  }
}

// Export dialog
const exportDialogVisible = ref(false)
const exportSelectAll = ref(true)
const exportIndeterminate = ref(false)
const ALL_EXPORT_FIELDS = computed(() => [
  { key: 'action',       label: t('audit.actionType') },
  { key: 'resourceType', label: t('audit.resourceType') },
  { key: 'resourceId',   label: t('audit.resourceId') },
  { key: 'username',     label: t('audit.operator') },
  { key: 'ipAddress',    label: t('audit.ipAddress') },
  { key: 'result',       label: t('audit.result') },
  { key: 'duration',     label: t('audit.duration') },
  { key: 'createdAt',    label: t('audit.time') },
])
const selectedExportFields = ref<string[]>([])

const resourceTypes = ref<string[]>([])

const query = reactive<AuditQueryRequest>({
  action: '',
  resourceType: '',
  username: '',
  result: '',
  ipAddress: '',
  resourceId: ''
})

const dateShortcuts = [
  {
    text: t('audit.today'),
    value: () => { const d = new Date(); return [d, d] }
  },
  {
    text: t('audit.last7Days'),
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 6)
      return [start, end]
    }
  },
  {
    text: t('audit.last30Days'),
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 29)
      return [start, end]
    }
  },
  {
    text: t('audit.thisMonth'),
    value: () => {
      const now = new Date()
      return [new Date(now.getFullYear(), now.getMonth(), 1), now]
    }
  }
]

const sortedLogs = computed(() => {
  if (!sortField.value) return logs.value
  return [...logs.value].sort((a, b) => {
    const av = a[sortField.value as keyof AuditLog]
    const bv = b[sortField.value as keyof AuditLog]
    const dir = sortOrder.value === 'ascending' ? 1 : -1
    if (av == null && bv == null) return 0
    if (av == null) return dir
    if (bv == null) return -dir
    if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * dir
    return String(av).localeCompare(String(bv)) * dir
  })
})

const actionType = (action: string): '' | 'success' | 'warning' | 'info' | 'primary' | 'danger' => {
  switch ((action || '').toUpperCase()) {
    case 'CREATE': return 'primary'
    case 'UPDATE': return 'warning'
    case 'DELETE': return 'danger'
    case 'QUERY':  return 'info'
    default:       return 'info'
  }
}

const actionText = (action: string) => {
  switch ((action || '').toUpperCase()) {
    case 'CREATE': return 'Create'
    case 'UPDATE': return 'Update'
    case 'DELETE': return 'Delete'
    case 'QUERY':  return 'Query'
    default:       return action || '-'
  }
}

const resourceTypeText = (rt: string | null | undefined): string => {
  const sep = ' - '
  const EM  = t('menu.entitlementManagement')
  const RT  = t('menu.relationTables')
  switch ((rt || '').toUpperCase()) {
    case 'USER':               return [t('menu.userManagement'), t('menu.userList')].join(sep)
    case 'ROLE':               return [EM, t('menu.roleManagement')].join(sep)
    case 'VIRTUAL_GROUP':      return [EM, t('menu.virtualGroup')].join(sep)
    case 'TASK':               return [EM, t('menu.virtualGroup')].join(sep)
    case 'BUSINESS_UNIT':      return [EM, t('menu.organization')].join(sep)
    case 'RELATION_TABLE':     return [RT, t('menu.tableStructure')].join(sep)
    case 'RELATION_TABLE_ROW': return [RT, t('menu.tableData')].join(sep)
    case 'AUTH':               return 'Auth'
    case 'BI_DASHBOARD':       return ['BI Management', 'Dashboard Registry'].join(sep)
    case 'BI_ASSIGNMENT':      return ['BI Management', 'Dashboard Assignment'].join(sep)
    case 'BI_RBAC':            return ['BI Management', 'RBAC Mapping'].join(sep)
    default:                   return rt || ''
  }
}

const buildQueryRequest = (): AuditQueryRequest => {
  const req: AuditQueryRequest = { ...query }
  if (dateRange.value && dateRange.value.length === 2) {
    req.startTime = dateRange.value[0] + 'T00:00:00+08:00'
    req.endTime = dateRange.value[1] + 'T23:59:59+08:00'
  }
  Object.keys(req).forEach(key => {
    if (!req[key as keyof AuditQueryRequest]) delete req[key as keyof AuditQueryRequest]
  })
  return req
}

// Map frontend display field names → actual JPA entity field names
const SORT_FIELD_MAP: Record<string, string> = {
  createdAt:    'timestamp',
  username:     'userName',
  result:       'success',
  duration:     'timestamp',   // duration doesn't exist in entity, fall back
}
const toEntityField = (field: string) => SORT_FIELD_MAP[field] ?? field

const handleSearch = async () => {
  loading.value = true
  // Reset the auto-refresh countdown so it doesn't fire immediately after a manual search
  startAutoRefresh()
  try {
    const sortDir = sortOrder.value === 'ascending' ? 'asc' : 'desc'
    const entityField = toEntityField(sortField.value)
    const result = await queryAuditLogs(buildQueryRequest(), page.value - 1, size.value, entityField, sortDir)
    logs.value = result.content
    total.value = result.totalElements
  } catch (e) {
    console.error('Failed to load audit logs:', e)
  } finally {
    loading.value = false
  }
}

const handleSizeChange = () => {
  page.value = 1
  handleSearch()
}

const handleReset = () => {
  Object.assign(query, { action: '', resourceType: '', username: '', result: '', ipAddress: '', resourceId: '' })
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 6)
  const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
  dateRange.value = [fmt(start), fmt(end)]
  page.value = 1
  sortField.value = 'createdAt'
  sortOrder.value = 'descending'
  handleSearch()
}

const handleSortChange = ({ prop, order }: { prop: string; order: string | null }) => {
  sortField.value = prop || 'createdAt'
  sortOrder.value = (order === 'ascending' ? 'ascending' : 'descending')
  page.value = 1
  handleSearch()
}

const handleSelectionChange = (rows: AuditLog[]) => {
  selectedRows.value = rows
}

const clearSelection = () => {
  tableRef.value?.clearSelection()
  selectedRows.value = []
}

const exportAsCsv = (data: AuditLog[], filename: string) => {
  const headers = [
    t('audit.actionType'), t('audit.resourceType'), t('audit.operator'),
    t('audit.ipAddress'), t('audit.result'), t('audit.duration'), t('audit.time')
  ]
  const rows = data.map(row => [
    actionText(row.action),
    row.resourceType || '',
    row.username || '',
    row.ipAddress || '',
    row.result,
    row.duration + 'ms',
    formatTime(row.createdAt)
  ])
  const csv = [headers, ...rows]
    .map(r => r.map(v => `"${String(v).replace(/"/g, '""')}"`).join(','))
    .join('\n')
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

const openExportDialog = () => {
  selectedExportFields.value = ALL_EXPORT_FIELDS.value.map(f => f.key)
  exportSelectAll.value = true
  exportIndeterminate.value = false
  exportDialogVisible.value = true
}

const handleExportSelectAll = (val: boolean) => {
  selectedExportFields.value = val ? ALL_EXPORT_FIELDS.value.map(f => f.key) : []
  exportIndeterminate.value = false
}

const handleExportFieldChange = (val: string[]) => {
  const total = ALL_EXPORT_FIELDS.value.length
  exportSelectAll.value = val.length === total
  exportIndeterminate.value = val.length > 0 && val.length < total
}

const getRowValue = (row: AuditLog, key: string): string => {
  switch (key) {
    case 'action':       return actionText(row.action)
    case 'resourceType': return row.resourceType || ''
    case 'resourceId':   return row.resourceId || ''
    case 'username':     return row.username || ''
    case 'ipAddress':    return row.ipAddress || ''
    case 'result':       return row.result === 'SUCCESS' ? t('audit.success') : row.result === 'PENDING' ? t('audit.pending') : t('audit.failed')
    case 'duration':     return row.duration + 'ms'
    case 'createdAt':    return formatTime(row.createdAt)
    default:             return ''
  }
}

const doExport = async (format: 'csv' | 'excel') => {
  const fields = selectedExportFields.value
  if (fields.length === 0) return
  const fieldLabels = ALL_EXPORT_FIELDS.value.filter(f => fields.includes(f.key))
  const dataToExport = selectedRows.value.length > 0 ? selectedRows.value : sortedLogs.value
  if (format === 'csv') {
    const headers = fieldLabels.map(f => f.label)
    const rows = dataToExport.map(row => fieldLabels.map(f => getRowValue(row, f.key)))
    const csv = [headers, ...rows]
      .map(r => r.map(v => `"${String(v).replace(/"/g, '""')}"`).join(','))
      .join('\n')
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `audit-logs-${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    exportDialogVisible.value = false
  } else {
    exporting.value = true
    try {
      await exportAuditLogs(buildQueryRequest())
      ElMessage.success(t('common.success'))
      exportDialogVisible.value = false
    } catch (e) {
      ElMessage.error(t('common.failed'))
    } finally {
      exporting.value = false
    }
  }
}

const handleBatchExportCsv = () => {
  openExportDialog()
}

const getPreviewContent = (row: AuditLog): string => {
  return [
    `${t('audit.actionType')}: ${actionText(row.action)}`,
    `${t('audit.operator')}: ${row.username || '-'}`,
    `${t('audit.result')}: ${row.result === 'SUCCESS' ? t('audit.success') : t('audit.failed')}`,
    `${t('audit.time')}: ${formatTime(row.createdAt)}`
  ].join('\n')
}

const formatTime = (isoStr: string | null | undefined): string => {
  if (!isoStr) return '-'
  try {
    const date = new Date(isoStr)
    if (isNaN(date.getTime())) return isoStr
    const yyyy = date.getFullYear()
    const MM = String(date.getMonth() + 1).padStart(2, '0')
    const dd = String(date.getDate()).padStart(2, '0')
    const HH = String(date.getHours()).padStart(2, '0')
    const mm = String(date.getMinutes()).padStart(2, '0')
    const ss = String(date.getSeconds()).padStart(2, '0')
    const SSS = String(date.getMilliseconds()).padStart(3, '0')
    return `${yyyy}-${MM}-${dd} ${HH}:${mm}:${ss}.${SSS}`
  } catch {
    return isoStr
  }
}

const actionCategory = (action: string): 'create' | 'update' | 'delete' | 'query' | 'other' => {
  switch ((action || '').toUpperCase()) {
    case 'CREATE': return 'create'
    case 'UPDATE': return 'update'
    case 'DELETE': return 'delete'
    case 'QUERY':  return 'query'
    default:       return 'other'
  }
}

const parseJson = (s: string | null | undefined): Record<string, unknown> | null => {
  if (!s) return null
  try { return JSON.parse(s) } catch { return { value: s } }
}

const getBeforeData = (log: AuditLog): Record<string, unknown> | null => {
  const cat = actionCategory(log.action)
  if (cat === 'create' || cat === 'query') return null
  if (cat === 'delete') return parseJson(log.oldValue)
  // UPDATE: backend pre-computes diff and stores only changed fields in oldValue/newValue.
  // For old records (full entity stored), fall back to client-side diff as best effort.
  const old = parseJson(log.oldValue)
  const nw  = parseJson(log.newValue)
  if (!old) return null
  if (!nw)  return old
  // Heuristic: if key overlap >= 70% they're both full entities → run client-side diff
  const oldKeys    = Object.keys(old)
  const newKeys    = Object.keys(nw)
  const sharedKeys = oldKeys.filter(k => newKeys.includes(k)).length
  const maxKeys    = Math.max(oldKeys.length, newKeys.length)
  if (maxKeys > 2 && sharedKeys / maxKeys >= 0.7) {
    return getDiffJson(log.oldValue, log.newValue, 'before')
  }
  // Pre-computed diff — return as-is
  return old
}

const getAfterData = (log: AuditLog): Record<string, unknown> | null => {
  const cat = actionCategory(log.action)
  if (cat === 'delete' || cat === 'query') return null
  if (cat === 'create') return parseJson(log.newValue)
  // UPDATE: same logic as getBeforeData
  const old = parseJson(log.oldValue)
  const nw  = parseJson(log.newValue)
  if (!nw)  return null
  if (!old) return nw
  const oldKeys    = Object.keys(old)
  const newKeys    = Object.keys(nw)
  const sharedKeys = oldKeys.filter(k => newKeys.includes(k)).length
  const maxKeys    = Math.max(oldKeys.length, newKeys.length)
  if (maxKeys > 2 && sharedKeys / maxKeys >= 0.7) {
    return getDiffJson(log.oldValue, log.newValue, 'after')
  }
  return nw
}

const currentBeforeData = computed(() => currentLog.value ? getBeforeData(currentLog.value) : null)
const currentAfterData  = computed(() => currentLog.value ? getAfterData(currentLog.value)  : null)

// For UPDATE, pass the "other side" so the renderer can deep-diff and
// highlight any key (at any nesting depth) whose value changed.
const currentBeforeCompare = computed((): Record<string, unknown> | null => {
  if (!currentLog.value) return null
  return actionCategory(currentLog.value.action) === 'update' ? currentAfterData.value : null
})
const currentAfterCompare = computed((): Record<string, unknown> | null => {
  if (!currentLog.value) return null
  return actionCategory(currentLog.value.action) === 'update' ? currentBeforeData.value : null
})

const getDiffJson = (
  oldStr: string | null | undefined,
  newStr: string | null | undefined,
  side: 'before' | 'after'
): Record<string, unknown> => {
  const parseOrWrap = (s: string) => {
    try { return JSON.parse(s) } catch { return { value: s } }
  }
  if (!oldStr && !newStr) return {}
  if (!oldStr) return parseOrWrap(newStr!)
  if (!newStr) return parseOrWrap(oldStr!)
  try {
    const oldObj = parseOrWrap(oldStr)
    const newObj = parseOrWrap(newStr)
    const oldKeys = Object.keys(oldObj)
    const newKeys = Object.keys(newObj)
    const allKeys = new Set([...oldKeys, ...newKeys])

    // Structural compatibility check: if the two objects share fewer than 30% of
    // their keys they were likely serialised from different types (entity vs DTO).
    // In that case return the raw object rather than a misleading diff.
    const sharedKeys = oldKeys.filter(k => newKeys.includes(k)).length
    const maxKeys = Math.max(oldKeys.length, newKeys.length)
    if (maxKeys > 2 && sharedKeys / maxKeys < 0.3) {
      return side === 'before' ? oldObj : newObj
    }

    const diff: Record<string, unknown> = {}
    for (const key of allKeys) {
      // Drop framework-managed audit fields — they change on every save and
      // would otherwise pollute the diff with noise.
      if (SYSTEM_AUDIT_FIELDS.has(key)) continue
      if (JSON.stringify(oldObj[key]) !== JSON.stringify(newObj[key])) {
        diff[key] = side === 'before' ? oldObj[key] : newObj[key]
      }
    }
    return Object.keys(diff).length > 0 ? diff : (side === 'before' ? oldObj : newObj)
  } catch {
    return parseOrWrap(side === 'before' ? oldStr : newStr)
  }
}

// Auto-managed audit fields (timestamps, modifier id, version, etc.). They
// change on every save without representing real user intent, so we always
// hide them from the UPDATE diff view and never highlight them.
const SYSTEM_AUDIT_FIELDS = new Set<string>([
  'updatedAt', 'createdAt', 'timestamp',
  'lastModifiedAt', 'lastModifiedDate', 'modifiedAt',
  'lastUpdatedAt', 'updateTime', 'createTime', 'createdDate',
  'updatedBy', 'createdBy',
  'lastModifiedBy', 'modifiedBy',
  'createBy', 'updateBy',
  'version',
])

const escapeHtml = (s: string): string =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

// Common identity keys used by JPA / DTO objects to uniquely identify a
// child record. Order matters — first hit wins.
const IDENTITY_KEYS = ['id', 'uuid', 'code', 'key', 'name', 'fieldName'] as const

const isPlainObject = (x: unknown): x is Record<string, unknown> =>
  x !== null && typeof x === 'object' && !Array.isArray(x)

const getIdentity = (x: unknown): { key: string; value: unknown } | null => {
  if (!isPlainObject(x)) return null
  for (const k of IDENTITY_KEYS) {
    const v = x[k]
    if (v !== undefined && v !== null && (typeof v === 'string' || typeof v === 'number')) {
      return { key: k, value: v }
    }
  }
  return null
}

const hasIdentityKey = (x: unknown): boolean => getIdentity(x) !== null

// Find the element in `arr` that shares the same identity key/value as
// `item`. Returns undefined when no match (caller treats as "new element").
const findArrayMatch = (item: unknown, arr: unknown[]): unknown => {
  const id = getIdentity(item)
  if (!id) return undefined
  return arr.find(c => {
    const cid = getIdentity(c)
    return cid !== null && cid.key === id.key && cid.value === id.value
  })
}

// Recursively render a JSON value with syntax highlighting, and if a
// `compare` value is provided, mark every key whose sub-value differs from
// `compare`'s corresponding sub-value with the `jk-changed` class. This works
// at any nesting depth (objects and arrays).
//
// `forceChanged` is set when we recurse into a subtree that is entirely new
// (or entirely removed) relative to the other side — e.g. a freshly added
// element in an array. In that case every key inside must be highlighted
// regardless of per-key comparison, so the user sees exactly which JSON
// object was added / deleted.
const renderJsonValue = (
  value: unknown,
  compare: unknown,
  depth: number,
  forceChanged = false
): string => {
  const pad = '  '.repeat(depth)
  const padInner = '  '.repeat(depth + 1)

  if (value === null) return '<span class="jnull">null</span>'
  if (typeof value === 'boolean') return `<span class="jb">${value}</span>`
  if (typeof value === 'number') return `<span class="jn">${value}</span>`
  if (typeof value === 'string') {
    return `<span class="js">${escapeHtml(JSON.stringify(value))}</span>`
  }
  if (Array.isArray(value)) {
    if (value.length === 0) return '[]'
    const cmpArr = !forceChanged && Array.isArray(compare) ? compare : null
    const items = value.map((item, idx) => {
      let cmpItem: unknown = undefined
      let itemForced = forceChanged
      if (cmpArr !== null) {
        // Prefer identity-based pairing for arrays of objects: an UPDATE that
        // simply inserts/removes one element must not flag every shifted
        // element as "changed". Try common identity keys before falling back
        // to index alignment.
        const matched = findArrayMatch(item, cmpArr)
        if (matched !== undefined) {
          cmpItem = matched
        } else if (!hasIdentityKey(item)) {
          // No identity key on the item → fall back to positional compare.
          cmpItem = cmpArr[idx]
        } else {
          // Item has an id but no match → it's a brand-new (or removed)
          // element. Force-highlight every key inside so the user sees the
          // full JSON object that was added / deleted.
          itemForced = true
        }
      }
      return `${padInner}${renderJsonValue(item, cmpItem, depth + 1, itemForced)}`
    })
    return `[\n${items.join(',\n')}\n${pad}]`
  }
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    const keys = Object.keys(record)
    if (keys.length === 0) return '{}'
    const cmpRecord =
      !forceChanged && compare && typeof compare === 'object' && !Array.isArray(compare)
        ? (compare as Record<string, unknown>)
        : undefined
    const entries = keys.map((k) => {
      const v = record[k]
      const cmpV = cmpRecord ? cmpRecord[k] : undefined
      const changed =
        !SYSTEM_AUDIT_FIELDS.has(k) &&
        (forceChanged ||
          (cmpRecord !== undefined &&
            JSON.stringify(v) !== JSON.stringify(cmpV)))
      const keyClass = changed ? 'jk jk-changed' : 'jk'
      const keyJson = escapeHtml(JSON.stringify(k))
      return `${padInner}<span class="${keyClass}">${keyJson}:</span> ${renderJsonValue(v, cmpV, depth + 1, forceChanged)}`
    })
    return `{\n${entries.join(',\n')}\n${pad}}`
  }
  return `<span class="jn">${escapeHtml(String(value))}</span>`
}

const formatJsonHighlight = (
  obj: Record<string, unknown> | null,
  compareAgainst?: Record<string, unknown> | null
): string => {
  if (!obj || Object.keys(obj).length === 0) return '{}'
  const html = renderJsonValue(obj, compareAgainst ?? undefined, 0)
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['span'],
    ALLOWED_ATTR: ['class'],
  })
}

const showDetail = (log: AuditLog) => {
  currentLog.value = log
  beforeExpanded.value = false
  afterExpanded.value = false
  detailDialogVisible.value = true
}

onMounted(async () => {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 6)
  const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
  dateRange.value = [fmt(start), fmt(end)]
  try {
    const types = await getAuditResourceTypes()
    resourceTypes.value = [...types].sort((a, b) => a.localeCompare(b))
  } catch {
    // fallback: leave empty; the dropdown will just be empty
  }
  handleSearch()
})

onUnmounted(() => {
  stopAutoRefresh()
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

.pagination-container {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
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

/* Log Detail Dialog */
.log-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-section {
  border: 1px solid #dee2e6;
  border-radius: 6px;
  overflow: hidden;
}

.section-title {
  font-weight: 600;
  font-size: 13px;
  padding: 8px 14px;
  border-bottom: 1px solid #dee2e6;
  background: #e9ecef;
  color: #343a40;
}

.section-basic { background: #f8f9fa; }

.section-before { background: #fff8f0; }
.section-before .section-title {
  background: #ffe8cc;
  border-bottom-color: #ffd6a5;
  color: #7d4800;
}

.section-after { background: #f0fff4; }
.section-after .section-title {
  background: #c3f0d4;
  border-bottom-color: #99e0b4;
  color: #1a5c35;
}

.detail-grid {
  display: grid;
  grid-template-columns: 130px 1fr;
  gap: 0;
}

.detail-row { display: contents; }

.detail-label {
  padding: 8px 14px;
  font-size: 13px;
  color: #6c757d;
  font-weight: 500;
  border-bottom: 1px solid #e9ecef;
  background: rgba(0,0,0,0.02);
  display: flex;
  align-items: center;
}

.detail-value {
  padding: 8px 14px;
  font-size: 13px;
  color: #212529;
  border-bottom: 1px solid #e9ecef;
  display: flex;
  align-items: center;
  word-break: break-all;
}

.detail-row:last-child .detail-label,
.detail-row:last-child .detail-value {
  border-bottom: none;
}

.path-value {
  font-family: monospace;
  font-size: 12px;
}

.result-success { color: #198754; font-weight: 600; }
.result-danger  { color: #dc3545; }
.result-pending { color: #e6a23c; font-weight: 600; }

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-badge {
  font-size: 11px;
  font-weight: 400;
  padding: 1px 7px;
  border-radius: 10px;
  background: #e9ecef;
  color: #6c757d;
  border: 1px solid #dee2e6;
}

.section-badge.badge-diff {
  background: #fff3cd;
  color: #856404;
  border-color: #ffc107;
}

.json-container {
  padding: 10px 14px;
  position: relative;
}

.json-content {
  background: #1e1e2e;
  color: #cdd6f4;
  border-radius: 4px;
  padding: 10px 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  line-height: 1.6;
  margin: 0;
  overflow: hidden;
  white-space: pre-wrap;
  word-break: break-all;
  transition: max-height 0.2s ease;
}

.json-content.collapsed {
  max-height: 4.8em;
  overflow: hidden;
  -webkit-mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
  mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
}

.expand-btn { margin-top: 6px; font-size: 12px; }

:deep(.jk)    { color: #89b4fa; }
:deep(.js)    { color: #a6e3a1; }
:deep(.jn)    { color: #fab387; }
:deep(.jb)    { color: #cba6f7; }
:deep(.jnull) { color: #6c7086; font-style: italic; }

/* Amber pill highlight for top-level keys whose value changed in an UPDATE. */
:deep(.jk-changed) {
  background: rgba(250, 204, 21, 0.22);
  color: #fde68a !important;
  padding: 0 4px;
  border-radius: 3px;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(250, 204, 21, 0.45);
}
.section-after :deep(.jk-changed) {
  background: rgba(34, 197, 94, 0.25);
  color: #bbf7d0 !important;
  box-shadow: inset 0 0 0 1px rgba(34, 197, 94, 0.55);
}
.section-before :deep(.jk-changed) {
  background: rgba(239, 68, 68, 0.22);
  color: #fecaca !important;
  box-shadow: inset 0 0 0 1px rgba(239, 68, 68, 0.5);
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

/* Export Dialog */
.export-dialog-body { display: flex; flex-direction: column; gap: 12px; }

.export-stat {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
  font-size: 13px;
  color: #409eff;
}

.export-fields-label {
  font-size: 13px;
  font-weight: 500;
  color: #606266;
}

.export-fields {
  background: #f8f9fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px 14px;
}

.export-fields :deep(.el-checkbox) {
  display: block;
  margin-bottom: 6px;
}
</style>
