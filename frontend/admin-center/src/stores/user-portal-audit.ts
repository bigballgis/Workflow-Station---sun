/**
 * User Portal 审计日志 Pinia store
 *
 * 管理 User Portal 审计日志列表数据、查询条件及功能单元代码缓存。
 * 查询状态在路由间导航时保持，参照 stores/audit.ts 的模式。
 */
import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import {
  queryAuditLogs,
  getFunctionUnitCodes,
  type UserPortalAuditRecord,
  type UserPortalAuditQueryRequest,
  type FunctionUnitOption,
} from '@/api/user-portal-audit'

interface SortState {
  field: string
  order: 'ascending' | 'descending'
}

export const useUserPortalAuditStore = defineStore('userPortalAudit', () => {
  // ==================== Data ====================
  const logs = ref<UserPortalAuditRecord[]>([])
  const total = ref(0)
  const loading = ref(false)

  // ==================== Query State (persisted across navigation) ====================
  const query = reactive<UserPortalAuditQueryRequest>({
    userId: '',
    username: '',
    functionUnitCode: '',
    changeType: '',
    processInstanceId: '',
  })
  const dateRange = ref<string[] | null>(null)
  const pagination = reactive({ page: 1, size: 20 })
  const sort = reactive<SortState>({ field: 'timestamp', order: 'descending' })

  // ==================== Cached Data ====================
  const functionUnitCodes = ref<FunctionUnitOption[]>([])

  // ==================== Actions ====================

  const SORT_FIELD_MAP: Record<string, string> = {
    timestamp: 'timestamp',
    userName: 'userId',
    changeType: 'changeType',
    functionUnitCode: 'functionUnitCode',
    processInstanceId: 'processInstanceId',
  }
  const toEntityField = (field: string) => SORT_FIELD_MAP[field] ?? field

  const getSortParams = () => ({
    field: toEntityField(sort.field),
    order: (sort.order === 'ascending' ? 'asc' : 'desc') as 'asc' | 'desc',
  })

  const buildQueryRequest = (): UserPortalAuditQueryRequest => {
    const req: UserPortalAuditQueryRequest = { ...query }
    if (dateRange.value && dateRange.value.length === 2) {
      req.startTime = dateRange.value[0] + 'T00:00:00+08:00'
      req.endTime = dateRange.value[1] + 'T23:59:59+08:00'
    }
    Object.keys(req).forEach((key) => {
      if (!req[key as keyof UserPortalAuditQueryRequest]) {
        delete req[key as keyof UserPortalAuditQueryRequest]
      }
    })
    return req
  }

  const fetchAllLogsForExport = async (): Promise<UserPortalAuditRecord[]> => {
    const { field: entityField, order: sortDir } = getSortParams()
    const pageSize = 500
    const all: UserPortalAuditRecord[] = []
    let page = 0
    let totalElements = Infinity

    while (all.length < totalElements) {
      const result = await queryAuditLogs(
        buildQueryRequest(), page, pageSize, entityField, sortDir
      )
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
      const { field: entityField, order: sortDir } = getSortParams()
      const result = await queryAuditLogs(
        buildQueryRequest(), pagination.page - 1, pagination.size, entityField, sortDir
      )
      logs.value = Array.isArray(result?.content) ? result.content : []
      total.value = result?.totalElements ?? 0
    } catch (e) {
      console.error('Failed to load user portal audit logs:', e)
    } finally {
      loading.value = false
    }
  }

  const fetchFunctionUnitCodes = async () => {
    try {
      const options = await getFunctionUnitCodes()
      functionUnitCodes.value = options.sort((a, b) =>
          (a.name || a.code).localeCompare(b.name || b.code)
        )
    } catch {
      /* leave empty */
    }
  }

  const resetQuery = () => {
    Object.assign(query, {
      userId: '',
      username: '',
      functionUnitCode: '',
      changeType: '',
      processInstanceId: '',
    })
    const end = new Date()
    const start = new Date()
    start.setDate(start.getDate() - 6)
    const fmt = (d: Date) =>
      `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    dateRange.value = [fmt(start), fmt(end)]
    pagination.page = 1
    sort.field = 'timestamp'
    sort.order = 'descending'
  }

  const setSort = (field: string, order: 'ascending' | 'descending') => {
    sort.field = field
    sort.order = order
    pagination.page = 1
  }

  return {
    logs,
    total,
    loading,
    query,
    dateRange,
    pagination,
    sort,
    functionUnitCodes,
    fetchLogs,
    fetchFunctionUnitCodes,
    fetchAllLogsForExport,
    resetQuery,
    setSort,
    buildQueryRequest,
    toEntityField,
    getSortParams,
  }
})
