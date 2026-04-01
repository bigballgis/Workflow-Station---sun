<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('menu.audit') }}</span>
    </div>

    <!-- Filter Area -->
    <div class="filter-card">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item :label="t('audit.actionType')">
          <el-select v-model="query.action" clearable :placeholder="t('common.selectPlaceholder')" style="width: 150px">
            <el-option :label="t('audit.login')" value="USER_LOGIN" />
            <el-option :label="t('audit.loginFailed')" value="USER_LOGIN_FAILED" />
            <el-option :label="t('audit.logout')" value="USER_LOGOUT" />
            <el-option :label="t('audit.userCreate')" value="USER_CREATED" />
            <el-option :label="t('audit.userUpdate')" value="USER_UPDATED" />
            <el-option :label="t('audit.userDelete')" value="USER_DELETED" />
            <el-option :label="t('audit.roleCreate')" value="ROLE_CREATED" />
            <el-option :label="t('audit.roleUpdate')" value="ROLE_UPDATED" />
            <el-option :label="t('audit.roleDelete')" value="ROLE_DELETED" />
            <el-option :label="t('audit.permissionChange')" value="PERMISSION_GRANTED" />
            <el-option :label="t('audit.dataQuery')" value="DATA_QUERIED" />
            <el-option :label="t('audit.passwordChange')" value="PASSWORD_CHANGED" />
            <el-option :label="t('audit.passwordReset')" value="PASSWORD_RESET" />
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
      <el-table-column prop="action" :label="t('audit.actionType')" min-width="150" sortable="custom" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag size="small" class="action-tag" style="white-space: nowrap">{{ actionText(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resourceType" :label="t('audit.resourceType')" min-width="150" sortable="custom" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tooltip v-if="row.action === 'DATA_QUERIED' && (row.resourceType || row.resourceId)" placement="top" effect="light" :show-after="300" :enterable="false">
            <template #content>
              <div style="font-size:12px;max-width:220px">
                <div><b>{{ t('audit.resourceType') }}:</b> {{ row.resourceType || '-' }}</div>
                <div><b>{{ t('audit.resourceId') }}:</b> {{ row.resourceId || '-' }}</div>
              </div>
            </template>
            <span style="cursor:default">{{ row.resourceType || '-' }} <el-icon style="font-size:11px;color:#409eff"><InfoFilled /></el-icon></span>
          </el-tooltip>
          <span v-else>{{ row.resourceType || '' }}</span>
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
              <span class="detail-value">{{ currentLog.resourceType || '-' }}</span>
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
            <pre class="json-content" :class="{ collapsed: !beforeExpanded }" v-html="formatJsonHighlight(currentBeforeData)"></pre>
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
            <pre class="json-content" :class="{ collapsed: !afterExpanded }" v-html="formatJsonHighlight(currentAfterData)"></pre>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { Download, Search, InfoFilled } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { queryAuditLogs, exportAuditLogs, type AuditLog, type AuditQueryRequest } from '@/api/audit'

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

const query = reactive<AuditQueryRequest>({
  action: '',
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
  const map: Record<string, '' | 'success' | 'warning' | 'info' | 'primary' | 'danger'> = {
    USER_LOGIN: 'success', USER_LOGOUT: 'info', USER_LOGIN_FAILED: 'danger',
    USER_CREATED: 'primary', USER_UPDATED: 'warning', USER_DELETED: 'danger',
    USER_LOCKED: 'danger', USER_UNLOCKED: 'success',
    PASSWORD_CHANGED: 'warning', PASSWORD_RESET: 'warning',
    ROLE_CREATED: 'primary', ROLE_UPDATED: 'warning', ROLE_DELETED: 'danger',
    PERMISSION_GRANTED: 'warning', PERMISSION_REVOKED: 'warning',
    ROLE_ASSIGNED: 'primary', ROLE_UNASSIGNED: 'info',
    DATA_CREATED: 'primary', DATA_UPDATED: 'warning', DATA_DELETED: 'danger',
    DATA_QUERIED: 'info', DATA_EXPORTED: 'info', DATA_IMPORTED: 'primary',
    CONFIG_CREATED: 'primary', CONFIG_UPDATED: 'warning', CONFIG_DELETED: 'danger',
    SYSTEM_STARTUP: 'success', SYSTEM_SHUTDOWN: 'info',
  }
  return map[action] || 'info'
}

const actionText = (action: string) => {
  const map: Record<string, string> = {
    USER_LOGIN: t('audit.login'), USER_LOGOUT: t('audit.logout'), USER_LOGIN_FAILED: t('audit.loginFailed'),
    USER_CREATED: t('audit.userCreate'), USER_UPDATED: t('audit.userUpdate'), USER_DELETED: t('audit.userDelete'),
    USER_LOCKED: t('audit.userLocked'), USER_UNLOCKED: t('audit.userUnlocked'),
    PASSWORD_CHANGED: t('audit.passwordChange'), PASSWORD_RESET: t('audit.passwordReset'),
    ROLE_CREATED: t('audit.roleCreate'), ROLE_UPDATED: t('audit.roleUpdate'), ROLE_DELETED: t('audit.roleDelete'),
    PERMISSION_GRANTED: t('audit.permissionChange'), PERMISSION_REVOKED: t('audit.permissionRevoked'),
    ROLE_ASSIGNED: t('audit.roleAssigned'), ROLE_UNASSIGNED: t('audit.roleUnassigned'),
    DATA_CREATED: t('audit.create'), DATA_UPDATED: t('audit.update'), DATA_DELETED: t('audit.delete'),
    DATA_QUERIED: t('audit.dataQuery'), DATA_EXPORTED: t('audit.dataExport'), DATA_IMPORTED: t('audit.dataImport'),
    CONFIG_CREATED: t('audit.configCreate'), CONFIG_UPDATED: t('audit.configUpdate'), CONFIG_DELETED: t('audit.configDelete'),
    SYSTEM_STARTUP: t('audit.systemStartup'), SYSTEM_SHUTDOWN: t('audit.systemShutdown'),
  }
  return map[action] || action
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
  Object.assign(query, { action: '', username: '', result: '', ipAddress: '', resourceId: '' })
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
  const a = (action || '').toUpperCase()
  if (a.includes('CREAT')) return 'create'
  if (a.includes('UPDAT')) return 'update'
  if (a.includes('DELET')) return 'delete'
  if (a.includes('QUER') || a.includes('EXPORT') || a.includes('IMPORT')) return 'query'
  return 'other'
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
      if (JSON.stringify(oldObj[key]) !== JSON.stringify(newObj[key])) {
        diff[key] = side === 'before' ? oldObj[key] : newObj[key]
      }
    }
    return Object.keys(diff).length > 0 ? diff : (side === 'before' ? oldObj : newObj)
  } catch {
    return parseOrWrap(side === 'before' ? oldStr : newStr)
  }
}

const formatJsonHighlight = (obj: Record<string, unknown>): string => {
  if (!obj || Object.keys(obj).length === 0) return '{}'
  const json = JSON.stringify(obj, null, 2)
  const highlighted = json
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(
      /("(?:\\u[a-fA-F0-9]{4}|\\[^u]|[^\\"])*"(?:\s*:)?|\b(?:true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
      (match) => {
        let cls = 'jn'
        if (/^"/.test(match)) {
          cls = /:$/.test(match) ? 'jk' : 'js'
        } else if (/true|false/.test(match)) {
          cls = 'jb'
        } else if (/null/.test(match)) {
          cls = 'jnull'
        }
        return `<span class="${cls}">${match}</span>`
      }
    )
  return DOMPurify.sanitize(highlighted, {
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

onMounted(() => {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 6)
  const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
  dateRange.value = [fmt(start), fmt(end)]
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

.pagination-container {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.empty-state {
  padding: 20px 0;
}

/* Action Type tag: unified gray style */
.action-tag {
  background-color: #f4f4f5 !important;
  border-color: #e9e9eb !important;
  color: #606266 !important;
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
