import request from './request'
import type { PageResult } from '@/types/common'
import { unwrapApiData } from '@/utils/apiResponse'

// ==================== 类型定义 ====================

export interface UserPortalAuditRecord {
  id: number
  processInstanceId: string
  taskInstanceId?: string
  stageId?: string
  stageName?: string
  userId: string
  userName: string
  timestamp: string
  fieldName: string
  fieldLabel?: string
  oldValue?: string
  newValue?: string
  changeType: string
  subTableName?: string
  rowIdentifier?: string
  functionUnitCode?: string
  functionUnitName?: string
  functionUnitVersionLabel?: string
  formName?: string
  tableName?: string
  processTitle?: string
  subTableDisplayName?: string
}

export interface UserPortalAuditQueryRequest {
  userId?: string
  username?: string
  functionUnitCode?: string
  changeType?: string
  startTime?: string
  endTime?: string
  processInstanceId?: string
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

export interface FunctionUnitOption {
  code: string
  name: string
}

// ==================== API 函数 ====================

export const getFunctionUnitCodes = async (): Promise<FunctionUnitOption[]> => {
  const body = await request.get('/security/user-portal-audit-logs/function-units')
  const data = unwrapApiData<FunctionUnitOption[]>(body)
  return Array.isArray(data) ? data : []
}

export const queryAuditLogs = async (
  query: UserPortalAuditQueryRequest,
  page: number = 0,
  size: number = 20,
  sortField: string = 'timestamp',
  sortOrder: string = 'desc'
): Promise<PageResult<UserPortalAuditRecord>> => {
  const body = await request.post(
    `/security/user-portal-audit-logs/query?page=${page}&size=${size}&sort=${encodeURIComponent(sortField)},${sortOrder}`,
    query
  )
  const result = unwrapApiData<PageResult<UserPortalAuditRecord>>(body)
  return {
    content: Array.isArray(result?.content) ? result.content : [],
    totalElements: result?.totalElements ?? 0,
    totalPages: result?.totalPages ?? 0,
    size: result?.size ?? size,
    number: result?.number ?? page,
  }
}
