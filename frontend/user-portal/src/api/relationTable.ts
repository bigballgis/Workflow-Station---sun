import { request } from './request'

// ==================== 类型定义 ====================

export interface RelationTableDTO {
  id: number
  tableName: string
  displayName: string
  description?: string
  status: string
  enabled: boolean
  portalVisible: boolean
  currentVersion: number
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

// ==================== API ====================

export const relationTableApi = {
  /** 获取用户可见的表列表 */
  getVisibleTables: () =>
    request.get<{ data: RelationTableDTO[] }>('/relation-tables'),

  /** 分页查询表数据（只读） */
  queryTableData: (tableId: number, params: { page?: number; size?: number; search?: string }) =>
    request.get<{ data: PageResponse<Record<string, any>> }>(`/relation-tables/${tableId}`, { params }),

  /** 导出 CSV */
  exportCsv: (tableId: number, maxRows = 10000) =>
    request.get<Blob>(`/relation-tables/${tableId}/export`, {
      params: { maxRows },
      responseType: 'blob'
    }),

  /** Lookup 搜索 */
  searchForLookup: (tableId: number, params: {
    keyword: string
    searchFields: string[]
    displayField: string
    limit?: number
  }) =>
    request.get<{ data: Record<string, any>[] }>(`/relation-tables/${tableId}/search`, { params }),
}
