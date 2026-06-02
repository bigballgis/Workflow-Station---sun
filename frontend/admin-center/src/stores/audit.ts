/**
 * 审计日志 Pinia store
 *
 * 管理审计日志列表数据、查询条件和资源类型缓存。
 * 查询状态在路由间导航时保持，避免返回页面后丢失搜索上下文。
 */
import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { queryAuditLogs, getAuditResourceTypes, type AuditLog, type AuditQueryRequest } from '@/api/audit'

interface SortState {
  field: string
  order: 'ascending' | 'descending'
}

export const useAuditStore = defineStore('audit', () => {
  // ==================== Data ====================
  const logs = ref<AuditLog[]>([])
  const total = ref(0)
  const loading = ref(false)

  // ==================== Query State (persisted across navigation) ====================
  const query = reactive<AuditQueryRequest>({
    action: '', resourceType: '', username: '', result: '', ipAddress: '', resourceId: ''
  })
  const dateRange = ref<string[] | null>(null)
  const pagination = reactive({ page: 1, size: 20 })
  const sort = reactive<SortState>({ field: 'createdAt', order: 'descending' })

  // ==================== Cached Data ====================
  const resourceTypes = ref<string[]>([])

  // ==================== Actions ====================

  const SORT_FIELD_MAP: Record<string, string> = {
    createdAt: 'timestamp', username: 'userName', result: 'success', duration: 'timestamp',
  }
  const toEntityField = (field: string) => SORT_FIELD_MAP[field] ?? field

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

  const fetchAllLogsForExport = async (): Promise<AuditLog[]> => {
    const sortDir = sort.order === 'ascending' ? 'asc' : 'desc'
    const entityField = toEntityField(sort.field)
    const pageSize = 500
    const all: AuditLog[] = []
    let page = 0
    let totalElements = Infinity

    while (all.length < totalElements) {
      const result = await queryAuditLogs(buildQueryRequest(), page, pageSize, entityField, sortDir)
      all.push(...result.content)
      totalElements = result.totalElements
      if (result.content.length === 0) break
      page += 1
    }

    return all
  }

  const fetchLogs = async () => {
    loading.value = true
    try {
      const sortDir = sort.order === 'ascending' ? 'asc' : 'desc'
      const entityField = toEntityField(sort.field)
      const result = await queryAuditLogs(buildQueryRequest(), pagination.page - 1, pagination.size, entityField, sortDir)
      logs.value = result.content
      total.value = result.totalElements
    } catch (e) {
      console.error('Failed to load audit logs:', e)
    } finally {
      loading.value = false
    }
  }

  const fetchResourceTypes = async () => {
    try {
      const types = await getAuditResourceTypes()
      resourceTypes.value = [...types]
        .filter(rt => rt !== 'TASK')
        .sort((a, b) => a.localeCompare(b))
    } catch { /* leave empty */ }
  }

  const resetQuery = () => {
    Object.assign(query, { action: '', resourceType: '', username: '', result: '', ipAddress: '', resourceId: '' })
    const end = new Date()
    const start = new Date()
    start.setDate(start.getDate() - 6)
    const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
    dateRange.value = [fmt(start), fmt(end)]
    pagination.page = 1
    sort.field = 'createdAt'
    sort.order = 'descending'
  }

  const setSort = (field: string, order: 'ascending' | 'descending') => {
    sort.field = field
    sort.order = order
    pagination.page = 1
  }

  return {
    logs, total, loading,
    query, dateRange, pagination, sort,
    resourceTypes,
    fetchLogs, fetchResourceTypes, fetchAllLogsForExport, resetQuery, setSort,
    buildQueryRequest,
  }
})
