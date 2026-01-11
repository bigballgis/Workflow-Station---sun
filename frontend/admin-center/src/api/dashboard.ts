import { get } from './request'

// ==================== 类型定义 ====================

export interface DashboardStats {
  totalUsers: number
  totalDepartments: number
  totalRoles: number
  onlineUsers: number
  activeProcesses: number
  pendingTasks: number
  todayLogins: number
  todayNewUsers: number
}

export interface RecentActivity {
  id: string
  action: string
  resourceType: string
  resourceId?: string
  resourceName?: string
  username: string
  userId?: string
  description?: string
  createdAt: string
}

export interface UserTrend {
  date: string
  activeUsers: number
  newUsers: number
  loginCount: number
}

export interface SystemHealth {
  status: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY'
  services: ServiceHealth[]
  lastChecked: string
}

export interface ServiceHealth {
  name: string
  status: 'UP' | 'DOWN' | 'UNKNOWN'
  responseTime?: number
  lastChecked: string
}

// ==================== Dashboard API ====================

// 获取统计数据
export const getStats = () =>
  get<DashboardStats>('/dashboard/stats')

// 获取最近活动
export const getRecentActivities = (limit = 10) =>
  get<RecentActivity[]>('/dashboard/activities', { params: { limit } })

// 获取用户趋势
export const getUserTrends = (days = 7) =>
  get<UserTrend[]>('/dashboard/user-trends', { params: { days } })

// 获取系统健康状态
export const getSystemHealth = () =>
  get<SystemHealth>('/dashboard/health')

export const dashboardApi = {
  getStats,
  getRecentActivities,
  getUserTrends,
  getSystemHealth
}
