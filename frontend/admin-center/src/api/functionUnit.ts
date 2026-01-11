import { get, post, put, del } from './request'

// ==================== 类型定义 ====================

export interface FunctionUnit {
  id: string
  name: string
  code: string
  version: string
  description?: string
  status: 'DRAFT' | 'VALIDATED' | 'DEPLOYED' | 'DEPRECATED'
  enabled?: boolean
  packagePath?: string
  importedAt?: string
  importedBy?: string
  deployedAt?: string
  deployedBy?: string
  environment?: string
  createdAt: string
  updatedAt: string
}

export interface Deployment {
  id: string
  functionUnitId: string
  functionUnitName?: string
  functionUnitCode?: string
  functionUnitVersion?: string
  environment: 'DEVELOPMENT' | 'TESTING' | 'STAGING' | 'PRODUCTION'
  strategy: 'FULL' | 'INCREMENTAL' | 'CANARY' | 'BLUE_GREEN'
  status: 'PENDING' | 'APPROVED' | 'EXECUTING' | 'COMPLETED' | 'FAILED' | 'ROLLED_BACK' | 'CANCELLED'
  deployedBy: string
  deployedByName?: string
  deployedAt?: string
  completedAt?: string
  rollbackReason?: string
  cancelReason?: string
  createdAt: string
}

export interface DeploymentProgress {
  deploymentId: string
  status: string
  progress: number
  currentStep: string
  totalSteps: number
  logs: string[]
  startedAt: string
  estimatedCompletion?: string
  error?: string
}

export interface Approval {
  id: string
  deploymentId: string
  approverType: string
  approverId?: string
  approverName?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  comment?: string
  approvedAt?: string
  createdAt: string
}

export interface ImportRequest {
  fileName: string
  fileContent: string
  overwriteExisting?: boolean
}

export interface ImportResult {
  success: boolean
  functionUnitId?: string
  functionUnitCode?: string
  functionUnitVersion?: string
  message?: string
  errors?: string[]
  warnings?: string[]
}

export interface ValidationResult {
  valid: boolean
  errors: string[]
  warnings: string[]
}

export interface VersionHistory {
  version: string
  status: string
  createdAt: string
  createdBy: string
  deployedAt?: string
  deployedEnvironment?: string
}

export interface VersionUpgradeCheck {
  canUpgrade: boolean
  fromVersion: string
  toVersion: string
  breakingChanges: string[]
  migrationSteps: string[]
}

// 访问权限类型（简化后只支持角色）
export type FunctionUnitAccessType = 'ROLE'

// 访问权限配置
export interface FunctionUnitAccess {
  id: string
  functionUnitId: string
  functionUnitName: string
  roleId: string
  roleName: string
  createdAt: string
  createdBy: string
}

// 访问权限请求（简化后只需要角色ID）
export interface FunctionUnitAccessRequest {
  roleId: string
  roleName?: string
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// 删除预览响应
export interface DeletePreviewResponse {
  functionUnitId: string
  functionUnitName: string
  functionUnitCode: string
  formCount: number
  processCount: number
  dataTableCount: number
  accessConfigCount: number
  deploymentCount: number
  dependencyCount: number
  hasRunningInstances: boolean
  runningInstanceCount: number
}

// 启用状态响应
export interface EnabledResponse {
  id: string
  enabled: boolean
  updatedAt: string
}

// ==================== 功能单元 CRUD API ====================

export const functionUnitApi = {
  // 获取功能单元列表（分页）
  list: (status?: string, page = 0, size = 20) =>
    get<PageResult<FunctionUnit>>('/function-units', { params: { status, page, size } }),

  // 根据ID获取功能单元
  getById: (id: string) =>
    get<FunctionUnit>(`/function-units/${id}`),

  // 导入功能包
  import: (data: ImportRequest) =>
    post<ImportResult>('/function-units/import', data),

  // 验证功能包
  validate: (data: ImportRequest) =>
    post<ValidationResult>('/function-units/validate', data),

  // 删除功能单元
  delete: (id: string) =>
    del<void>(`/function-units/${id}`),

  // 获取删除预览
  getDeletePreview: (id: string) =>
    get<DeletePreviewResponse>(`/function-units/${id}/delete-preview`),

  // 切换启用状态
  setEnabled: (id: string, enabled: boolean) =>
    put<EnabledResponse>(`/function-units/${id}/enabled`, { enabled }),

  // 验证功能单元（标记为已验证）
  validateUnit: (id: string) =>
    post<FunctionUnit>(`/function-units/${id}/validate`),

  // 废弃功能单元
  deprecate: (id: string) =>
    post<FunctionUnit>(`/function-units/${id}/deprecate`),

  // ==================== 部署管理 API ====================

  // 创建部署
  createDeployment: (id: string, environment: string, strategy = 'FULL') =>
    post<Deployment>(`/function-units/${id}/deployments`, null, {
      params: { environment, strategy }
    }),

  // 获取部署历史
  getDeploymentHistory: (id: string) =>
    get<Deployment[]>(`/function-units/${id}/deployments`),

  // 获取部署详情
  getDeployment: (deploymentId: string) =>
    get<Deployment>(`/function-units/deployments/${deploymentId}`),

  // 执行部署
  executeDeployment: (deploymentId: string) =>
    post<Deployment>(`/function-units/deployments/${deploymentId}/execute`),

  // 回滚部署
  rollbackDeployment: (deploymentId: string, reason: string) =>
    post<Deployment>(`/function-units/deployments/${deploymentId}/rollback`, null, {
      params: { reason }
    }),

  // 取消部署
  cancelDeployment: (deploymentId: string, reason: string) =>
    post<Deployment>(`/function-units/deployments/${deploymentId}/cancel`, null, {
      params: { reason }
    }),

  // 获取部署进度
  getDeploymentProgress: (deploymentId: string) =>
    get<DeploymentProgress>(`/function-units/deployments/${deploymentId}/progress`),

  // ==================== 审批管理 API ====================

  // 获取部署审批记录
  getDeploymentApprovals: (deploymentId: string) =>
    get<Approval[]>(`/function-units/deployments/${deploymentId}/approvals`),

  // 审批通过
  approveDeployment: (approvalId: string, comment?: string) =>
    post<Approval>(`/function-units/approvals/${approvalId}/approve`, null, {
      params: { comment }
    }),

  // 审批拒绝
  rejectDeployment: (approvalId: string, comment: string) =>
    post<Approval>(`/function-units/approvals/${approvalId}/reject`, null, {
      params: { comment }
    }),

  // 获取待审批列表
  getPendingApprovals: () =>
    get<Approval[]>('/function-units/approvals/pending'),

  // ==================== 版本管理 API ====================

  // 获取所有版本
  getAllVersions: (code: string) =>
    get<FunctionUnit[]>(`/function-units/code/${code}/versions`),

  // 获取最新版本
  getLatestVersion: (code: string) =>
    get<FunctionUnit>(`/function-units/code/${code}/latest`),

  // 获取最新稳定版本
  getLatestStableVersion: (code: string) =>
    get<FunctionUnit>(`/function-units/code/${code}/latest-stable`),

  // 创建新版本
  createNewVersion: (id: string, newVersion: string) =>
    post<FunctionUnit>(`/function-units/${id}/new-version`, null, {
      params: { newVersion }
    }),

  // 获取版本历史
  getVersionHistory: (code: string) =>
    get<VersionHistory[]>(`/function-units/code/${code}/history`),

  // 检查版本升级
  checkVersionUpgrade: (code: string, fromVersion: string, toVersion: string) =>
    get<VersionUpgradeCheck>(`/function-units/code/${code}/upgrade-check`, {
      params: { fromVersion, toVersion }
    }),

  // ==================== 访问权限管理 API ====================

  // 获取访问权限配置列表
  getAccessConfigs: (id: string) =>
    get<FunctionUnitAccess[]>(`/function-units/${id}/access`),

  // 添加访问权限配置
  addAccessConfig: (id: string, data: FunctionUnitAccessRequest) =>
    post<FunctionUnitAccess>(`/function-units/${id}/access`, data),

  // 删除访问权限配置
  removeAccessConfig: (id: string, accessId: string) =>
    del<void>(`/function-units/${id}/access/${accessId}`),

  // 批量设置访问权限配置
  setAccessConfigs: (id: string, data: FunctionUnitAccessRequest[]) =>
    put<FunctionUnitAccess[]>(`/function-units/${id}/access`, data),

  // 检查用户访问权限
  checkUserAccess: (id: string, userId: string) =>
    get<boolean>(`/function-units/${id}/access/check`, { params: { userId } })
}
