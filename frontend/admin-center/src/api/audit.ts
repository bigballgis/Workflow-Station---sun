import request from './request'

// ==================== 类型定义 ====================

export interface SecurityPolicy {
  id: string
  policyType: string
  name: string
  description: string
  config: Record<string, any>
  enabled: boolean
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
}

export interface PasswordPolicyConfig {
  minLength: number
  maxLength: number
  requireUppercase: boolean
  requireLowercase: boolean
  requireDigit: boolean
  requireSpecialChar: boolean
  specialChars: string
  maxRepeatingChars: number
  passwordHistoryCount: number
  expirationDays: number
}

export interface LoginPolicyConfig {
  maxFailedAttempts: number
  lockoutDurationMinutes: number
  requireCaptchaAfterFailures: number
  allowRememberMe: boolean
  rememberMeDays: number
  allowMultipleSessions: boolean
  maxConcurrentSessions: number
}

export interface SessionPolicyConfig {
  sessionTimeoutMinutes: number
  idleTimeoutMinutes: number
  extendOnActivity: boolean
  forceLogoutOnPasswordChange: boolean
  forceLogoutOnRoleChange: boolean
}

export interface PasswordValidationResult {
  valid: boolean
  errors: string[]
}

export interface AuditLog {
  id: string
  userId: string
  username: string
  action: string
  resourceType: string
  resourceId: string
  resourceName: string
  description: string
  ipAddress: string
  userAgent: string
  requestMethod: string
  requestPath: string
  requestParams: Record<string, any>
  responseStatus: number
  result: 'SUCCESS' | 'FAILED'
  errorMessage?: string
  duration: number
  createdAt: string
}

export interface AuditQueryRequest {
  userId?: string
  username?: string
  action?: string
  resourceType?: string
  resourceId?: string
  result?: string
  ipAddress?: string
  startTime?: string
  endTime?: string
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface AnomalyDetectionResult {
  type: string
  severity: string
  description: string
  userId?: string
  username?: string
  count: number
  detectedAt: string
  details: Record<string, any>
}

export interface ComplianceReport {
  reportDate: string
  periodDays: number
  totalUsers: number
  activeUsers: number
  totalLogins: number
  failedLogins: number
  passwordChanges: number
  permissionChanges: number
  securityIncidents: number
  complianceScore: number
  recommendations: string[]
}

// ==================== 安全策略 API ====================

export const getAllPolicies = (): Promise<SecurityPolicy[]> =>
  request.get('/security/policies')

export const getPolicy = (policyType: string): Promise<SecurityPolicy> =>
  request.get(`/security/policies/${policyType}`)

export const updatePasswordPolicy = (config: PasswordPolicyConfig): Promise<SecurityPolicy> =>
  request.put('/security/policies/password', config)

export const updateLoginPolicy = (config: LoginPolicyConfig): Promise<SecurityPolicy> =>
  request.put('/security/policies/login', config)

export const updateSessionPolicy = (config: SessionPolicyConfig): Promise<SecurityPolicy> =>
  request.put('/security/policies/session', config)

// ==================== 密码验证 API ====================

export const validatePassword = (password: string): Promise<PasswordValidationResult> =>
  request.post('/security/validate-password', password)

// ==================== 审计日志 API ====================

export const queryAuditLogs = (
  query: AuditQueryRequest,
  page: number = 0,
  size: number = 20
): Promise<PageResult<AuditLog>> =>
  request.post(`/security/audit-logs/query?page=${page}&size=${size}`, query)

export const getAuditLogsByUser = (
  userId: string,
  page: number = 0,
  size: number = 20
): Promise<PageResult<AuditLog>> =>
  request.get(`/security/audit-logs/user/${userId}?page=${page}&size=${size}`)

export const getAuditLogsByResource = (
  resourceType: string,
  resourceId: string,
  page: number = 0,
  size: number = 20
): Promise<PageResult<AuditLog>> =>
  request.get(`/security/audit-logs/resource/${resourceType}/${resourceId}?page=${page}&size=${size}`)

// ==================== 异常检测 API ====================

export const detectAnomalies = (days: number = 7): Promise<AnomalyDetectionResult[]> =>
  request.get(`/security/anomalies?days=${days}`)

// ==================== 合规报告 API ====================

export const generateComplianceReport = (days: number = 30): Promise<ComplianceReport> =>
  request.get(`/security/compliance-report?days=${days}`)

// ==================== 导出 API ====================

export const exportAuditLogs = async (query: AuditQueryRequest): Promise<void> => {
  const response = await request.post('/security/audit-logs/export', query, {
    responseType: 'blob'
  })
  const blob = new Blob([response as unknown as BlobPart], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `audit-logs-${new Date().toISOString().slice(0, 10)}.xlsx`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
