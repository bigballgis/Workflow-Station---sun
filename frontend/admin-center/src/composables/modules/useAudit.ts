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
import { exportAuditLogs, type AuditLog } from '@/api/audit'
import { useAuditStore } from '@/stores/audit'
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
  const { sort: storeSort } = storeToRefs(store)

  // ==================== Local State ====================

  const loading = storeLoading // alias for template compatibility
  const page = computed({
    get: () => store.pagination.page,
    set: (v) => { store.pagination.page = v }
  })
  const size = computed({
    get: () => store.pagination.size,
    set: (v) => { store.pagination.size = v }
  })
  const sortField = computed({
    get: () => storeSort.value.field,
    set: (v) => { store.sort.field = v; store.sort.order = storeSort.value.order }
  })
  const sortOrder = computed({
    get: () => storeSort.value.order,
    set: (v) => { store.sort.order = v; store.sort.field = storeSort.value.field }
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
    loading.value = true
    startAutoRefresh()
    try {
      await store.fetchLogs()
    } finally {
      loading.value = false
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

  // ==================== Helper Functions ====================

  // ==================== Action/Type Mapping ====================
  // 纯函数实现见 ./audit/auditMappings.ts；此处绑定 i18n 的 t 后委托。

  const actionText = (action: string) => actionTextImpl(t, action)
  const resourceTypeText = (rt: string | null | undefined): string => resourceTypeTextImpl(t, rt)

  // ==================== Pagination ====================

  const handleSizeChange = () => {
    store.pagination.page = 1
    handleSearch()
  }

  const handleReset = () => {
    store.resetQuery()
    handleSearch()
  }

  const handleSortChange = ({ prop, order }: { prop: string; order: string | null }) => {
    store.setSort(prop || 'createdAt', order === 'ascending' ? 'ascending' : 'descending')
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
    exportDialogVisible.value = true
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

    exporting.value = true
    try {
      let dataToExport: AuditLog[]
      if (selectedRows.value.length > 0) {
        dataToExport = selectedRows.value
      } else if (format === 'csv') {
        dataToExport = await store.fetchAllLogsForExport()
      } else {
        dataToExport = []
      }

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
        const query = selectedRows.value.length > 0
          ? { ids: selectedRows.value.map(r => r.id) }
          : store.buildQueryRequest()
        await exportAuditLogs(query)
        notifySuccess(t('common.success'))
        exportDialogVisible.value = false
      }
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
    tableRef, selectedRows, sortField, sortOrder,
    query, resourceTypes, filterResourceTypes,
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
    handleSearch, handleReset, handleSizeChange, handleSortChange,
    handleSelectionChange, clearSelection,
    // Detail
    showDetail, getPreviewContent,
  }
}
