/**
 * 审计日志业务逻辑 composable
 *
 * 封装 audit/index.vue 页面的所有 API 调用、状态管理和业务逻辑。
 * 组件仅保留 template + 调用此 composable。
 */

import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { storeToRefs } from 'pinia'
import { notifyError, notifySuccess } from '@/utils/notify'
import type { TableInstance } from 'element-plus'
import DOMPurify from 'dompurify'
import { exportAuditLogs, type AuditLog } from '@/api/audit'
import { useAuditStore } from '@/stores/audit'

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
    const key = `audit.action${(action || '').toUpperCase()}` as string
    switch ((action || '').toUpperCase()) {
      case 'CREATE': return t('audit.actionCREATE')
      case 'UPDATE': return t('audit.actionUPDATE')
      case 'DELETE': return t('audit.actionDELETE')
      case 'QUERY':  return t('audit.actionQUERY')
      default:       return action || '-'
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
      case 'AUTH':               return t('common.auth', 'Auth')
      case 'BI_DASHBOARD':       return [t('menu.biManagement'), t('menu.biDashboardRegistry')].join(sep)
      case 'BI_ASSIGNMENT':      return [t('menu.biManagement'), t('menu.biDashboardAssignment')].join(sep)
      case 'BI_RBAC':            return [t('menu.biManagement'), t('menu.biRbacMapping')].join(sep)
      default:                   return rt || ''
    }
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
        await exportAuditLogs(store.buildQueryRequest())
        notifySuccess(t('common.success'))
        exportDialogVisible.value = false
      } catch (e) {
        notifyError(t(errorTranslator(AppErrorCode.AUDIT_EXPORT_FAILED)))
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

  // ==================== JSON Diff Rendering ====================

  const parseJson = (s: string | null | undefined): Record<string, unknown> | null => {
    if (!s) return null
    try { return JSON.parse(s) } catch { return { value: s } }
  }

  const getBeforeData = (log: AuditLog): Record<string, unknown> | null => {
    const cat = actionCategory(log.action)
    if (cat === 'create' || cat === 'query') return null
    if (cat === 'delete') return parseJson(log.oldValue)
    const old = parseJson(log.oldValue)
    const nw  = parseJson(log.newValue)
    if (!old) return null
    if (!nw)  return old
    const oldKeys    = Object.keys(old)
    const newKeys    = Object.keys(nw)
    const sharedKeys = oldKeys.filter(k => newKeys.includes(k)).length
    const maxKeys    = Math.max(oldKeys.length, newKeys.length)
    if (maxKeys > 2 && sharedKeys / maxKeys >= 0.7) {
      return getDiffJson(log.oldValue, log.newValue, 'before')
    }
    return old
  }

  const getAfterData = (log: AuditLog): Record<string, unknown> | null => {
    const cat = actionCategory(log.action)
    if (cat === 'delete' || cat === 'query') return null
    if (cat === 'create') return parseJson(log.newValue)
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
      const sharedKeys = oldKeys.filter(k => newKeys.includes(k)).length
      const maxKeys = Math.max(oldKeys.length, newKeys.length)
      if (maxKeys > 2 && sharedKeys / maxKeys < 0.3) {
        return side === 'before' ? oldObj : newObj
      }
      const diff: Record<string, unknown> = {}
      for (const key of allKeys) {
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
  const findArrayMatch = (item: unknown, arr: unknown[]): unknown => {
    const id = getIdentity(item)
    if (!id) return undefined
    return arr.find(c => {
      const cid = getIdentity(c)
      return cid !== null && cid.key === id.key && cid.value === id.value
    })
  }

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
          const matched = findArrayMatch(item, cmpArr)
          if (matched !== undefined) {
            cmpItem = matched
          } else if (!hasIdentityKey(item)) {
            cmpItem = cmpArr[idx]
          } else {
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
    query, resourceTypes,
    // Auto-refresh
    refreshCountdown, autoRefreshPaused, toggleAutoRefresh,
    // Export
    exportDialogVisible, exportSelectAll, exportIndeterminate,
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
