import { get, post, put, del } from './request'

// ==================== 类型定义 ====================

export interface SystemConfig {
  id: string
  configKey: string
  configValue: string
  configType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON'
  category: string
  environment: string
  description?: string
  isEncrypted: boolean
  version: number
  updatedAt: string
  updatedBy?: string
}

export interface ConfigHistory {
  id: string
  configKey: string
  oldValue: string
  newValue: string
  changedBy: string
  changedAt: string
  changeReason?: string
  version: number
}

export interface ConfigCreateRequest {
  configKey: string
  configValue: string
  configType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON'
  category: string
  environment?: string
  description?: string
  isEncrypted?: boolean
}

export interface ConfigUpdateRequest {
  configValue: string
  changeReason?: string
}

export interface ImpactAssessment {
  affectedServices: string[]
  requiresRestart: boolean
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  recommendations: string[]
}

export interface ConfigDiff {
  configKey: string
  sourceValue: string
  targetValue: string
  status: 'ADDED' | 'REMOVED' | 'MODIFIED' | 'UNCHANGED'
}

export interface ConfigDiffResult {
  sourceEnvironment: string
  targetEnvironment: string
  diffs: ConfigDiff[]
  totalDiffs: number
}

export interface ConfigSyncResult {
  success: boolean
  syncedCount: number
  failedCount: number
  errors: string[]
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// ==================== 配置 CRUD API ====================

export const configApi = {
  // 获取所有配置
  getAll: () =>
    get<SystemConfig[]>('/configs'),

  // 根据 key 获取配置
  getByKey: (key: string) =>
    get<SystemConfig>(`/configs/${key}`),

  // 获取配置值
  getValue: (key: string) =>
    get<string>(`/configs/${key}/value`),

  // 按类别获取配置
  getByCategory: (category: string) =>
    get<SystemConfig[]>(`/configs/category/${category}`),

  // 按环境获取配置
  getByEnvironment: (environment: string) =>
    get<SystemConfig[]>(`/configs/environment/${environment}`),

  // 创建配置
  create: (data: ConfigCreateRequest) =>
    post<SystemConfig>('/configs', data),

  // 更新配置
  update: (key: string, data: ConfigUpdateRequest) =>
    put<SystemConfig>(`/configs/${key}`, data),

  // 删除配置
  delete: (key: string) =>
    del<void>(`/configs/${key}`),

  // ==================== 版本管理和回滚 API ====================

  // 获取配置历史
  getHistory: (key: string, page = 0, size = 20) =>
    get<PageResult<ConfigHistory>>(`/configs/${key}/history`, { params: { page, size } }),

  // 回滚配置
  rollback: (key: string, version: number) =>
    post<SystemConfig>(`/configs/${key}/rollback/${version}`),

  // ==================== 影响评估 API ====================

  // 评估配置变更影响
  assessImpact: (key: string, newValue: string) =>
    post<ImpactAssessment>(`/configs/${key}/assess-impact`, newValue, {
      headers: { 'Content-Type': 'text/plain' }
    }),

  // ==================== 多环境同步 API ====================

  // 比较环境配置差异
  compareEnvironments: (sourceEnv: string, targetEnv: string) =>
    get<ConfigDiffResult>(`/configs/compare/${sourceEnv}/${targetEnv}`),

  // 同步配置到目标环境
  syncConfigs: (sourceEnv: string, targetEnv: string, configKeys: string[]) =>
    post<ConfigSyncResult>(`/configs/sync/${sourceEnv}/${targetEnv}`, configKeys)
}
