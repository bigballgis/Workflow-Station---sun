import { PageResult } from '@/types/common'
import { get, post, put, del } from './request'

// ==================== 类型定义 ====================

/** 表状态枚举 */
export type RelationTableStatus = 'DRAFT' | 'DEPLOYED' | 'ROLLBACK' | 'INIT' | 'UPDATED'

/** 字段数据类型枚举 */
export type RelationDataType =
  | 'VARCHAR'
  | 'INTEGER'
  | 'BIGINT'
  | 'DECIMAL'
  | 'BOOLEAN'
  | 'DATE'
  | 'TIMESTAMP'
  | 'TEXT'

/** 审计操作类型枚举 */
export type RelationAuditAction = 'ADD' | 'UPDATE' | 'DELETE' | 'STATUS_CHANGE'

/** 字段定义响应 */
export interface FieldDefinitionResponse {
  id: number
  fieldName: string
  dataType: RelationDataType
  length?: number
  precision?: number
  scale?: number
  nullable: boolean
  isPrimaryKey: boolean
  defaultValue?: string
  comment?: string
  sortOrder: number
}

/** 表定义响应 */
export interface RelationTableResponse {
  id: number
  tableName: string
  displayName: string
  description?: string
  status: RelationTableStatus
  enabled: boolean
  portalVisible: boolean
  currentVersion: number
  fieldDefinitions: FieldDefinitionResponse[]
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

/** 版本历史响应 */
export interface RelationTableVersionResponse {
  id: number
  versionNumber: number
  snapshotData: string
  deployedBy: string
  deployedAt: string
  changeLog?: string
}

/** 访问权限配置 */
export interface RelationTableAccess {
  id: string
  tableId: number
  targetType: string
  targetId: string
  createdAt: string
  createdBy: string
}

/** 表数据行 */
export interface RelationTableDataRow {
  rowId: string
  tableId: number
  data: Record<string, unknown>
}

/** 审计日志 */
export interface RelationTableAuditLog {
  id: string
  tableId: number
  tableName: string
  rowId?: string
  action: RelationAuditAction
  oldValue?: string
  newValue?: string
  operatorId: string
  operatorName?: string
  operatedAt: string
}

/** 分页结果 */
// ==================== 请求类型 ====================

/** 字段定义请求（创建） */
export interface CreateFieldDefinitionRequest {
  fieldName: string
  dataType: RelationDataType
  length?: number
  precision?: number
  scale?: number
  nullable?: boolean
  isPrimaryKey?: boolean
  defaultValue?: string
  comment?: string
  sortOrder?: number
}

/** 创建表请求 */
export interface CreateRelationTableRequest {
  tableName: string
  displayName?: string
  description?: string
  fieldDefinitions: CreateFieldDefinitionRequest[]
}

/** 字段定义请求（更新） */
export interface UpdateFieldDefinitionRequest {
  id?: number
  fieldName?: string
  dataType?: RelationDataType
  length?: number
  precision?: number
  scale?: number
  nullable?: boolean
  isPrimaryKey?: boolean
  defaultValue?: string
  comment?: string
  sortOrder?: number
}

/** 更新表请求 */
export interface UpdateRelationTableRequest {
  tableName?: string
  displayName?: string
  description?: string
  fieldDefinitions?: UpdateFieldDefinitionRequest[]
}

/** 回滚请求 */
export interface RollbackRequest {
  targetVersionId: number
}

// ==================== 表结构 API ====================

export const relationTableStructureApi = {
  /** 创建表定义 */
  create: (data: CreateRelationTableRequest) =>
    post<RelationTableResponse>('/relation-tables/structures', data),

  /** 获取表定义列表 */
  list: () =>
    get<RelationTableResponse[]>('/relation-tables/structures'),

  /** 获取表定义详情 */
  getById: (id: number) =>
    get<RelationTableResponse>(`/relation-tables/structures/${id}`),

  /** 更新表定义 */
  update: (id: number, data: UpdateRelationTableRequest) =>
    put<RelationTableResponse>(`/relation-tables/structures/${id}`, data),

  /** 删除表定义 */
  delete: (id: number) =>
    del<void>(`/relation-tables/structures/${id}`),

  /** 启用/禁用 */
  setEnabled: (id: number, enabled: boolean) =>
    put<RelationTableResponse>(`/relation-tables/structures/${id}/enabled`, { enabled }),

  /** 门户可见性开关 */
  setPortalVisibility: (id: number, portalVisible: boolean) =>
    put<RelationTableResponse>(`/relation-tables/structures/${id}/portal-visibility`, { portalVisible }),

  /** 部署表结构 */
  deploy: (id: number) =>
    post<RelationTableResponse>(`/relation-tables/structures/${id}/deploy`),

  /** 回滚到指定版本 */
  rollback: (id: number, data: RollbackRequest) =>
    post<RelationTableResponse>(`/relation-tables/structures/${id}/rollback`, data),

  /** 获取版本历史 */
  getVersionHistory: (id: number) =>
    get<RelationTableVersionResponse[]>(`/relation-tables/structures/${id}/versions`),

  /** 获取访问配置 */
  getAccessConfig: (id: number) =>
    get<RelationTableAccess[]>(`/relation-tables/structures/${id}/access`),

  /** 添加访问配置 */
  addAccess: (id: number, targetId: string, targetType = 'ROLE') =>
    post<RelationTableAccess>(`/relation-tables/structures/${id}/access`, { targetType, targetId }),

  /** 批量设置访问配置 */
  batchSetAccess: (id: number, targetIds: string[]) =>
    put<void>(`/relation-tables/structures/${id}/access`, { targetIds }),

  /** 删除访问配置 */
  removeAccess: (id: number, accessId: string) =>
    del<void>(`/relation-tables/structures/${id}/access/${accessId}`)
}

// ==================== 表数据 API ====================

export const relationTableDataApi = {
  /** 获取已部署的表列表 */
  getDeployedTables: () =>
    get<RelationTableResponse[]>('/relation-tables/data/tables'),

  /** 分页查询表数据 */
  queryData: (tableId: number, params?: { search?: string; page?: number; size?: number }) =>
    get<PageResult<RelationTableDataRow>>(`/relation-tables/data/${tableId}`, { params }),

  /** 新增数据 */
  addData: (tableId: number, data: Record<string, unknown>) =>
    post<RelationTableDataRow>(`/relation-tables/data/${tableId}`, data),

  /** 修改数据 */
  updateData: (tableId: number, rowId: string, data: Record<string, unknown>) =>
    put<RelationTableDataRow>(`/relation-tables/data/${tableId}/${rowId}`, data),

  /** 删除数据 */
  deleteData: (tableId: number, rowId: string) =>
    del<void>(`/relation-tables/data/${tableId}/${rowId}`),

  /** Active/Inactive 状态变更 */
  changeStatus: (tableId: number, rowId: string, status: string) =>
    put<RelationTableDataRow>(`/relation-tables/data/${tableId}/${rowId}/status`, { status }),

  /** 查询审计日志 */
  queryAuditLogs: (
    tableId: number,
    params?: { action?: string; operatorId?: string; startTime?: string; endTime?: string; page?: number; size?: number }
  ) =>
    get<PageResult<RelationTableAuditLog>>(`/relation-tables/data/${tableId}/audit-logs`, { params }),

  /** 导出 CSV */
  exportCsv: (tableId: number, maxRows = 10000) =>
    get<Blob>(`/relation-tables/data/${tableId}/export`, {
      params: { maxRows },
      responseType: 'blob'
    })
}
