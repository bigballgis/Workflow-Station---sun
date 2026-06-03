export interface FrontendModule {
  id?: number
  tenantId?: string
  hostApp: string
  moduleCode: string
  displayName: string
  routePath: string
  icon: string
  orderNo: number
  remoteEntryUrl: string
  exposedModule: string
  enabled: boolean
  requiredPermissions: string[]
  tenantScope: string[]
  env: string
  version: string
  createdBy?: string
  createdAt?: string
  updatedBy?: string
  updatedAt?: string
}

export interface FrontendModuleFilter {
  hostApp: string
  env: string
  enabled?: boolean
  page?: number
  size?: number
}

export interface ModuleVersion {
  id: number
  version: string
  remoteEntryUrl: string
  isActive: boolean
  releaseNote?: string
  createdBy?: string
  createdAt?: string
}

export interface HealthCheckResult {
  id: number
  moduleRegistryId: number
  status: 'HEALTHY' | 'UNHEALTHY'
  detail?: string
  checkedAt?: string
}

// ==================== Import/Export Package ====================

export interface ImportPackageResult {
  success: boolean
  moduleCode?: string
  version?: string
  registryId?: number
  remoteEntryUrl?: string
  error?: string
}
