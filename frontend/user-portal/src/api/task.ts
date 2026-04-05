import type { AxiosRequestConfig } from 'axios'
import { request } from './request'

export interface TaskQueryRequest {
  userId?: string
  assignmentTypes?: string[]
  priorities?: string[]
  processTypes?: string[]
  statuses?: string[]
  startTime?: string
  endTime?: string
  includeOverdue?: boolean
  keyword?: string
  sortBy?: string
  sortDirection?: string
  page?: number
  size?: number
}

export interface TaskActionInfo {
  actionId: string
  actionName: string
  actionType: string
  description?: string
  icon?: string
  buttonColor?: string
  configJson?: string
}

export interface TaskInfo {
  taskId: string
  taskName: string
  description?: string
  processInstanceId: string
  processDefinitionKey: string
  processDefinitionName: string
  assignmentType: string
  /** 引擎分配目标（如 CANDIDATE_USERS 时候选人 ID 列表拼接） */
  assignmentTarget?: string
  assignee: string
  assigneeName?: string
  delegatorId?: string
  delegatorName?: string
  initiatorId: string
  initiatorName?: string
  priority: string
  status: string
  createTime: string
  dueDate?: string
  isOverdue: boolean
  formKey?: string
  variables?: Record<string, any>
  claimed?: boolean
  originalAssignmentType?: string
  originalAssignee?: string
  candidateUsers?: string
  /** 引擎候选人用户 ID（与门户 TaskInfo.candidateUserIds 一致） */
  candidateUserIds?: string[]
  candidateGroupIds?: string[]
  // 已处理任务字段
  completedTime?: string
  durationInMillis?: number
  action?: string
  // 自定义操作按钮
  actions?: TaskActionInfo[]
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export interface TaskCompleteRequest {
  taskId: string
  action: string
  comment?: string
  formData?: Record<string, any>
  variables?: Record<string, any>
  targetUserId?: string
  returnActivityId?: string
}

export interface TaskHistoryInfo {
  id: string
  taskId: string
  taskName: string
  activityId: string
  activityName: string
  activityType: string
  operationType: string
  operatorId: string
  operatorName: string
  operationTime: string
  comment?: string
  duration?: number
}

export interface TaskStatistics {
  totalTasks: number
  directTasks: number
  groupTasks: number
  deptRoleTasks: number
  delegatedTasks: number
  overdueTasks: number
  urgentTasks: number
  highPriorityTasks: number
  todayNewTasks: number
  todayCompletedTasks: number
}

// 查询待办任务
export function queryTasks(params: TaskQueryRequest) {
  return request.post<{ data: PageResponse<TaskInfo> }>('/tasks/query', params)
}

// 获取任务详情
export function getTaskDetail(taskId: string) {
  return request.get<{ data: TaskInfo }>(`/tasks/${taskId}`)
}

// 获取任务流转历史
export function getTaskHistory(taskId: string) {
  return request.get<{ data: TaskHistoryInfo[] }>(`/tasks/${taskId}/history`)
}

// 获取任务统计
export function getTaskStatistics() {
  return request.get<{ data: TaskStatistics }>('/tasks/statistics')
}

// 认领任务
export function claimTask(taskId: string) {
  return request.post<{ data: TaskInfo }>(`/tasks/${taskId}/claim`)
}

// 取消认领
export function unclaimTask(taskId: string, originalAssignmentType: string, originalAssignee: string) {
  return request.post<{ data: TaskInfo }>(`/tasks/${taskId}/unclaim`, null, {
    params: { originalAssignmentType, originalAssignee }
  })
}

// 完成任务
export function completeTask(taskId: string, data: TaskCompleteRequest) {
  return request.post(`/tasks/${taskId}/complete`, data)
}

// 委托任务
export function delegateTask(taskId: string, delegateId: string, reason?: string) {
  return request.post(`/tasks/${taskId}/delegate`, null, {
    params: { delegateId, reason }
  })
}

// 转办任务
export function transferTask(taskId: string, toUserId: string, reason?: string) {
  return request.post(`/tasks/${taskId}/transfer`, null, {
    params: { toUserId, reason }
  })
}

// 催办任务
export function urgeTask(taskId: string, message?: string) {
  return request.post(`/tasks/${taskId}/urge`, null, {
    params: { message }
  })
}

// 批量催办任务
export function batchUrgeTasks(taskIds: string[], message?: string) {
  return request.post('/tasks/batch/urge', { taskIds, message })
}

// 查询已处理任务
export function queryCompletedTasks(params: TaskQueryRequest) {
  return request.post<{ data: PageResponse<TaskInfo> }>('/tasks/completed/query', params)
}

// 分配子表行处理人
export interface AssignSubTableRowRequest {
  assigneeId: string
}

export interface AssignSubTableRowResponse {
  success: boolean
  rowId: number
  assigneeId: string
  assigneeName: string
}

export function assignSubTableRow(taskId: string, rowId: number, assigneeId: string) {
  const config: AxiosRequestConfig & { skipGlobalErrorHandler?: boolean } = {
    skipGlobalErrorHandler: true
  }
  return request.post<AssignSubTableRowResponse | { data: AssignSubTableRowResponse }>(
    `/tasks/${taskId}/sub-table-rows/${rowId}/assign`,
    { assigneeId },
    config
  )
}

// 获取子任务表单数据（包含主任务信息和子表数据行）
export interface FormField {
  name: string
  label: string
  type: string
  required?: boolean
  readonly?: boolean
  span?: number
  options?: any[]
  [key: string]: any
}

export interface SubTaskFormData {
  taskId: string
  mainFormData: Record<string, any>
  mainFormFields: FormField[]
  subTableRowData: Record<string, any>
  subFormFields: FormField[]
  rowVersion: number
}

export function getSubTaskFormData(taskId: string) {
  return request.get<{ data: SubTaskFormData }>(`/tasks/${taskId}/sub-task-form-data`)
}

// 获取主任务子表数据（用于实时同步）
export interface SubTableRowStatus {
  id: number
  assignee?: string
  assigneeName?: string
  status?: string
  [key: string]: any
}

export interface SubTableDataResponse {
  taskId: string
  subTableName: string
  rows: SubTableRowStatus[]
}

export function getSubTableData(taskId: string) {
  return request.get<{ data: SubTableDataResponse }>(`/tasks/${taskId}/sub-table-data/all`)
}
