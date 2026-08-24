/**
 * 审计日志业务逻辑 composable
 *
 * 封装 audit/index.vue 页面的所有 API 调用、状态管理和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 */

import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { storeToRefs } from 'pinia'
import { notifyError, notifySuccess } from '@/utils/notify'
import type { TableInstance } from 'element-plus'
import { queryAuditLogList, type AuditLog } from '@/api/audit'
import { useAuditStore } from '@/stores/audit'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'
import * as XLSX from 'xlsx'
import {
  actionType,
  actionText as actionTextImpl,
  actionCategory,
  resourceTypeText as resourceTypeTextImpl,
  formatTime,
} from './audit/auditMappings'
import {
  getBeforeData,
  getAfterData,
  formatJsonHighlight,
} from './audit/auditJsonDiff'

export function useAudit() {
  const { t } = useI18n()
  const store = useAuditStore()

  // Bridge store state via storeToRefs (preserves reactivity)
  const { logs, total, loading: storeLoading, query, dateRange, resourceTypes } = storeToRefs(store)

  // ==================== Local State ====================

  const loading = storeLoading // alias for template compatibility
  const SELECTION_COL_WIDTH = 40
  const ACTIONS_COL_WIDTH = 80
  const AUDIT_COL_WIDTHS: Record<string, number> = {
    action: 110,
    resourceType: 220,
    username: 130,
    ipAddress: 150,
    result: 100,
    duration: 100,
    createdAt: 220,
  }
  const grid = useAdminListGrid<AuditLog>({
    storageKey: 'admin-list-layout:audit',
    extraWidth: SELECTION_COL_WIDTH + ACTIONS_COL_WIDTH,
    defaultWidthOf: (field) => AUDIT_COL_WIDTHS[field] ?? 120,
  })
  const page = computed({
    get: () => grid.pagination.page,
    set: (v) => { grid.pagination.page = v }
  })
  const size = computed({
    get: () => grid.pagination.size,
    set: (v) => { grid.pagination.size = v }
  })


  // ==================== Local UI State ====================

  const exporting = ref(false)
  const detailDialogVisible = ref(false)
  const currentLog = ref<AuditLog | null>(null)
  const tableRef = ref<TableInstance>()
  const selectedRows = ref<AuditLog[]>([])

  // ==================== Auto-refresh ====================

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

  const handleSearch = async () => {
    const seq = grid.beginQuery()
    loading.value = true
    startAutoRefresh()
    try {
      const pageResult = await queryAuditLogList({
        ...store.buildQueryRequest(),
        ...grid.buildQuery(),
      })
      if (!grid.isCurrentQuery(seq)) return
      grid.applyPage(pageResult, 'audit-logs/list-query response is missing its column declaration')
      logs.value = pageResult.content
      total.value = pageResult.totalElements
      store.sort.field = grid.sort.field || 'createdAt'
      store.sort.order = grid.sort.direction === 'ASC' ? 'ascending' : 'descending'
    } catch (error: unknown) {
      if (!grid.isCurrentQuery(seq)) return
      if (!(error as { response?: unknown })?.response) {
        notifyError(error instanceof Error ? error.message : t('audit.emptyText'))
      }
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
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

  // ==================== Export Dialog State ====================

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

  // ==================== Date Shortcuts ====================

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

  // ==================== Computed ====================

  const exportRecordCount = computed(() =>
    selectedRows.value.length > 0 ? selectedRows.value.length : total.value
  )

  const filterResourceTypes = computed(() => {
    const seen = new Set<string>()
    return resourceTypes.value.filter(rt => {
      if (rt === 'TASK') return false
      const label = resourceTypeText(rt)
      if (seen.has(label)) return false
      seen.add(label)
      return true
    })
  })

  const sortedLogs = computed(() => grid.displayRows.value)

  // ==================== Helper Functions ====================

  // ==================== Action/Type Mapping ====================
  // 纯函数实现见 ./audit/auditMappings.ts；此处绑定 i18n 的 t 后委托。

  const actionText = (action: string) => actionTextImpl(t, action)
  const resourceTypeText = (rt: string | null | undefined): string => resourceTypeTextImpl(t, rt)

  // ==================== Pagination ====================

  const handleReset = () => {
    store.resetQuery()
    grid.clearSort()
    grid.applyGroup('', false)
    for (const field of Object.keys(grid.columnFilters.value)) {
      grid.clearFilter(field)
    }
    handleSearch()
  }

  const handleSelectionChange = (rows: AuditLog[]) => {
    selectedRows.value = rows
  }

  const clearSelection = () => {
    tableRef.value?.clearSelection()
    selectedRows.value = []
  }

  // ==================== Export ====================

  const exportAsCsv = (data: AuditLog[], filename: string) => {
    const fieldLabels = ALL_EXPORT_FIELDS.value
    const { headers, rows } = buildExportRows(store.sortLogs(data), fieldLabels)
    downloadBlob(buildCsvBlob(headers, rows), filename)
  }

  const openExportDialog = () => {
    exportDialogVisible.value = true
  }

  const resolveOperatorUsername = (row: AuditLog): string => {
    const raw = row.username ?? (row as AuditLog & { userName?: string }).userName
    const name = typeof raw === 'string' ? raw.trim() : ''
    if (name && name.toLowerCase() !== 'unknown') return name
    return '-'
  }

  const getRowValue = (row: AuditLog, key: string): string => {
    switch (key) {
      case 'action':       return actionText(row.action)
      case 'resourceType': return resourceTypeText(row.resourceType) || ''
      case 'resourceId':   return row.resourceId || ''
      case 'username':
        return resolveOperatorUsername(row)
      case 'ipAddress':    return row.ipAddress || ''
      case 'result':       return row.result === 'SUCCESS' ? t('audit.success') : row.result === 'PENDING' ? t('audit.pending') : t('audit.failed')
      case 'duration':     return row.duration != null ? row.duration + 'ms' : '-'
      case 'createdAt':    return formatTime(row.createdAt)
      default:             return ''
    }
  }

  const downloadBlob = (blob: Blob, filename: string) => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  const buildExportRows = (data: AuditLog[], fieldKeys: { key: string; label: string }[]) => {
    const headers = fieldKeys.map(f => f.label)
    const rows = data.map(row => fieldKeys.map(f => getRowValue(row, f.key)))
    return { headers, rows }
  }

  const buildCsvBlob = (headers: string[], rows: string[][]) => {
    const csv = [headers, ...rows]
      .map(r => r.map(v => `"${String(v).replace(/"/g, '""')}"`).join(','))
      .join('\n')
    return new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
  }

  const downloadXlsx = (
    headers: string[],
    rows: string[][],
    filename: string,
    fieldKeys: { key: string; label: string }[]
  ) => {
    const ws = XLSX.utils.aoa_to_sheet([headers, ...rows])
    const textColumnKeys = new Set(['createdAt', 'duration'])
    const textColIndexes = fieldKeys
      .map((f, idx) => (textColumnKeys.has(f.key) ? idx : -1))
      .filter(idx => idx >= 0)

    for (let r = 1; r <= rows.length; r++) {
      for (const c of textColIndexes) {
        const ref = XLSX.utils.encode_cell({ r, c })
        const val = rows[r - 1]?.[c]
        if (val != null && val !== '') {
          ws[ref] = { t: 's', v: String(val), z: '@' }
        }
      }
    }

    const timeColIdx = fieldKeys.findIndex(f => f.key === 'createdAt')
    if (timeColIdx >= 0) {
      const cols = [...(ws['!cols'] ?? [])]
      cols[timeColIdx] = { wch: 26 }
      ws['!cols'] = cols
    }

    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, 'Audit Log')
    XLSX.writeFile(wb, filename)
  }

  const doExport = async (format: 'csv' | 'excel', fields?: string[]) => {
    const exportFields = fields?.length ? fields : selectedExportFields.value
    if (exportFields.length === 0) return
    const fieldLabels = ALL_EXPORT_FIELDS.value.filter(f => exportFields.includes(f.key))

    exporting.value = true
    try {
      let dataToExport: AuditLog[]
      if (selectedRows.value.length > 0) {
        dataToExport = store.sortLogs([...selectedRows.value])
      } else {
        dataToExport = await store.fetchAllLogsForExport()
      }

      const { headers, rows } = buildExportRows(dataToExport, fieldLabels)
      const dateSuffix = new Date().toISOString().slice(0, 10)

      if (format === 'csv') {
        downloadBlob(buildCsvBlob(headers, rows), `audit-logs-${dateSuffix}.csv`)
      } else {
        downloadXlsx(headers, rows, `audit-logs-${dateSuffix}.xlsx`, fieldLabels)
      }
      notifySuccess(t('common.success'))
      exportDialogVisible.value = false
    } catch (e) {
      notifyError(t(errorTranslator(AppErrorCode.AUDIT_EXPORT_FAILED)))
    } finally {
      exporting.value = false
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

  // ==================== JSON Diff Rendering ====================
  // 纯函数实现见 ./audit/auditJsonDiff.ts；此处仅按 currentLog 派生响应式 computed。

  const currentBeforeData = computed(() => currentLog.value ? getBeforeData(currentLog.value) : null)
  const currentAfterData  = computed(() => currentLog.value ? getAfterData(currentLog.value)  : null)
  const currentBeforeCompare = computed((): Record<string, unknown> | null => {
    if (!currentLog.value) return null
    return actionCategory(currentLog.value.action) === 'update' ? currentAfterData.value : null
  })
  const currentAfterCompare = computed((): Record<string, unknown> | null => {
    if (!currentLog.value) return null
    return actionCategory(currentLog.value.action) === 'update' ? currentBeforeData.value : null
  })

  // ==================== Detail Dialog ====================

  const showDetail = (log: AuditLog) => {
    currentLog.value = log
    detailDialogVisible.value = true
  }

  // ==================== Lifecycle ====================

  onMounted(async () => {
    // Initialize date range if not already set (from store)
    if (!store.dateRange || store.dateRange.length === 0) {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 6)
      const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
      store.dateRange = [fmt(start), fmt(end)]
    }
    if (store.resourceTypes.length === 0) {
      await store.fetchResourceTypes()
    }
    handleSearch()
  })

  onUnmounted(() => {
    stopAutoRefresh()
  })

  // ==================== Return ====================

  return {
    // State
    loading, exporting, logs, total, page, size,
    detailDialogVisible, currentLog, dateRange,
    tableRef, selectedRows,
    query, resourceTypes, filterResourceTypes,
    SELECTION_COL_WIDTH, ACTIONS_COL_WIDTH,
    // Auto-refresh
    refreshCountdown, autoRefreshPaused, toggleAutoRefresh,
    // Export
    exportDialogVisible, exportSelectAll, exportIndeterminate,
    exportRecordCount,
    ALL_EXPORT_FIELDS, selectedExportFields,
    openExportDialog, doExport, handleBatchExportCsv, exportAsCsv, getRowValue,
    // Date shortcuts
    dateShortcuts,
    // Computed
    sortedLogs,
    currentBeforeData, currentAfterData,
    currentBeforeCompare, currentAfterCompare,
    // Mapping functions
    actionType, actionText, actionCategory, resourceTypeText, formatTime,
    formatJsonHighlight,
    // Search/Filter
    handleSearch, handleReset,
    handleSelectionChange, clearSelection,
    // Detail
    showDetail, getPreviewContent,
    ...grid,
  }
}
