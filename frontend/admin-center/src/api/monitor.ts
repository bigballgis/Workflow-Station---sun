import request from './request'

// ==================== 类型定义 ====================

export interface SystemMetrics {
  cpuUsage: number
  memoryUsage: number
  memoryTotal: number
  memoryUsed: number
  diskUsage: number
  diskTotal: number
  diskUsed: number
  networkIn: number
  networkOut: number
  timestamp: string
}

export interface BusinessMetrics {
  onlineUsers: number
  activeProcesses: number
  pendingTasks: number
  completedTasksToday: number
  avgProcessingTime: number
  processStartsToday: number
  timestamp: string
}

export interface ApplicationMetrics {
  requestsPerSecond: number
  avgResponseTime: number
  errorRate: number
  activeConnections: number
  threadPoolUsage: number
  cacheHitRate: number
  timestamp: string
}

export interface AllMetrics {
  system: SystemMetrics
  business: BusinessMetrics
  application: ApplicationMetrics
}

export interface AlertRule {
  id: string
  name: string
  metricType: string
  metricName: string
  operator: string
  threshold: number
  severity: string
  enabled: boolean
  notificationChannels: string[]
  createdAt: string
}

export interface AlertRuleRequest {
  name: string
  metricType: string
  metricName: string
  operator: string
  threshold: number
  severity: string
  notificationChannels: string[]
}

export interface Alert {
  id: string
  ruleId: string
  ruleName: string
  metricType: string
  metricName: string
  currentValue: number
  threshold: number
  severity: string
  status: 'PENDING' | 'CONFIRMED' | 'RESOLVED'
  message: string
  acknowledgedBy?: string
  acknowledgedAt?: string
  resolvedBy?: string
  resolvedAt?: string
  createdAt: string
}

// ==================== 指标查询 API ====================

export const getSystemMetrics = (): Promise<SystemMetrics> =>
  request.get('/monitor/metrics/system')

export const getBusinessMetrics = (): Promise<BusinessMetrics> =>
  request.get('/monitor/metrics/business')

export const getApplicationMetrics = (): Promise<ApplicationMetrics> =>
  request.get('/monitor/metrics/application')

export const getAllMetrics = (): Promise<AllMetrics> =>
  request.get('/monitor/metrics/all')

// ==================== 告警规则 API ====================

export const createAlertRule = (data: AlertRuleRequest): Promise<AlertRule> =>
  request.post('/monitor/alert-rules', data)

export const getEnabledRules = (): Promise<AlertRule[]> =>
  request.get('/monitor/alert-rules')

// ==================== 告警管理 API ====================

export const getActiveAlerts = (): Promise<Alert[]> =>
  request.get('/monitor/alerts/active')

export const getActiveAlertCount = (): Promise<number> =>
  request.get('/monitor/alerts/active/count')

export const acknowledgeAlert = (alertId: string): Promise<Alert> =>
  request.post(`/monitor/alerts/${alertId}/acknowledge`)

export const resolveAlert = (alertId: string): Promise<Alert> =>
  request.post(`/monitor/alerts/${alertId}/resolve`)
