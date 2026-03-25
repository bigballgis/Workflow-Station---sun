import { request } from './request'

export interface TaskOverview {
  pendingCount: number
  overdueCount: number
  completedTodayCount: number
  avgProcessingHours: number
  urgentCount: number
  highPriorityCount: number
  teamPendingCount: number
  teamOverdueCount: number
  teamCompletedTodayCount: number
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

export interface RecentTask {
  id: string
  name: string
  processName?: string
  assignee?: string
  status: string
  priority?: string
  createdAt: string
  dueDate?: string
}

export interface RecentProcess {
  id: string
  processDefinitionName: string
  businessKey?: string
  status: string
  startTime: string
  endTime?: string
  currentNode?: string
}

export interface DashboardOverview {
  taskOverview: TaskOverview
  processOverview: ProcessOverview
  performanceOverview: PerformanceOverview
  recentTasks: RecentTask[]
  recentProcesses: RecentProcess[]
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

export interface TaskTrendData {
  dates: string[]
  completed: number[]
  created: number[]
}

export interface ProcessStatisticsData {
  byStatus: Record<string, number>
  byType: Record<string, number>
  completionRate: number
}

// 获取任务趋势数据
export function getTaskTrendData(days: number = 30) {
  return request.get<{ data: TaskTrendData }>('/dashboard/task-trend', { params: { days } })
}

// 获取流程统计数据
export function getProcessStatisticsData() {
  return request.get<{ data: ProcessStatisticsData }>('/dashboard/process-statistics')
}
