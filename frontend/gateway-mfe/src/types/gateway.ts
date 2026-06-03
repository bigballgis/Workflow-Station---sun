export interface ApiDefinition {
  id: number
  tenantId: string
  apiCode: string
  name: string
  domain: string
  basePath: string
  protocol: string
  status: string
  description: string
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

export interface ApiVersion {
  id: number
  tenantId: string
  apiDefinitionId: number
  version: string
  openapiDoc: string
  upstreamRef: string
  lifecycleStatus: string
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

export interface Application {
  id: number
  tenantId: string
  appCode: string
  name: string
  owner: string
  status: string
  description: string
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

export interface Credential {
  id: number
  tenantId: string
  applicationId: number
  credentialType: string
  displayName: string
  secretRef: string
  status: string
  expiresAt: string
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

export type ReleaseState = 'DRAFT' | 'TESTING' | 'PUBLISHED' | 'ROLLED_BACK'

export interface GatewayRelease {
  id: number
  tenantId: string
  environmentId: number
  releaseNo: string
  state: ReleaseState
  snapshotJson: any
  snapshotHash: string
  sourceReleaseId: number
  promotedFromEnvId: number
  description: string
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

export interface PublishHistory {
  id: number
  tenantId: string
  releaseId: number
  operation: string
  result: string
  runtimeRevision: string
  detailJson: any
  operator: string
  createdAt: string
}

// ==================== Phase 2: Drift ====================

export interface DriftReport {
  id: number
  tenantId: string
  environmentId: number
  syncMode: string
  status: string
  missingCount: number
  extraCount: number
  mismatchCount: number
  reportJson: any
  createdAt: string
}

// ==================== Phase 2: Approval ====================

export interface ReleaseApproval {
  id: number
  tenantId: string
  releaseId: number
  approverRole: string
  approverId: string
  status: 'PENDING' | 'APPROVED' | 'DENIED'
  comment: string
  decidedAt: string
  createdAt: string
}

// ==================== Phase 2: Monitoring ====================

export interface MetricsSnapshot {
  id: number
  tenantId: string
  apiDefinitionId: number
  environmentId: number
  periodStart: string
  periodEnd: string
  qps: number
  p50LatencyMs: number
  p95LatencyMs: number
  errorRate: number
  metricsJson: any
  createdAt: string
}

export interface MonitoringOverview {
  qps: number
  p50LatencyMs: number
  p95LatencyMs: number
  errorRate: number
  environmentCode: string
  period: string
  snapshotCount: number
}
