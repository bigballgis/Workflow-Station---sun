import { request } from './request'

export interface TaskOverview {
  pendingCount: number
  overdueCount: number
  completedTodayCount: number
  avgProcessingHours: number
  urgentCount: number
  highPriorityCount: number
}

export interface ProcessOverview {
  initiatedCount: number
  inProgressCount: number
  completedThisMonthCount: number
  approvalRate: number
  typeDistribution: Record<string, number>
}

export interface PerformanceOverview {
  efficiencyScore: number
  qualityScore: number
  collaborationScore: number
  monthlyRank: number
  totalUsers: number
}

export interface DashboardOverview {
  taskOverview: TaskOverview
  processOverview: ProcessOverview
  performanceOverview: PerformanceOverview
  recentTasks: any[]
  recentProcesses: any[]
}

// 获取Dashboard概览
export function getDashboardOverview() {
  return request.get<{ data: DashboardOverview }>('/dashboard/overview')
}

// 获取任务概览
export function getTaskOverview() {
  return request.get<{ data: TaskOverview }>('/dashboard/task-overview')
}

// 获取流程概览
export function getProcessOverview() {
  return request.get<{ data: ProcessOverview }>('/dashboard/process-overview')
}

// 获取个人绩效
export function getPerformanceOverview() {
  return request.get<{ data: PerformanceOverview }>('/dashboard/performance')
}

// 获取任务趋势数据
export function getTaskTrendData(days: number = 30) {
  return request.get<{ data: any }>('/dashboard/task-trend', { params: { days } })
}

// 获取流程统计数据
export function getProcessStatisticsData() {
  return request.get<{ data: any }>('/dashboard/process-statistics')
}

// Dashboard Widget 类型
export interface DashboardWidget {
  id: string
  type: string
  title: string
  x: number
  y: number
  w: number
  h: number
  config?: any
}

// Dashboard API 对象
export const dashboardApi = {
  getOverview: getDashboardOverview,
  getTaskOverview,
  getProcessOverview,
  getPerformanceOverview,
  getTaskTrendData,
  getProcessStatisticsData,
  getWidgets: async () => {
    return request.get<{ data: DashboardWidget[] }>('/dashboard/widgets')
  },
  saveLayout: async (layout: DashboardWidget[]) => {
    return request.post('/dashboard/layout', layout)
  },
  resetLayout: async () => {
    return request.post('/dashboard/layout/reset')
  }
}
