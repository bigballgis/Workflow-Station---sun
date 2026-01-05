import request from './request'

export interface ProcessDefinition {
  id: string
  key: string
  name: string
  description?: string
  category: string
  version: number
  icon?: string
  isFavorite?: boolean
}

export interface ProcessInstance {
  id: string
  processDefinitionId: string
  processDefinitionName: string
  businessKey?: string
  startTime: string
  endTime?: string
  status: string
  startUserId: string
  startUserName: string
  currentNode?: string
  currentAssignee?: string
}

export interface ProcessStartRequest {
  businessKey?: string
  variables?: Record<string, any>
  priority?: string
}

export const processApi = {
  // 获取可发起的流程定义列表
  getDefinitions(params?: { category?: string; keyword?: string }) {
    return request.get<ProcessDefinition[]>('/api/processes/definitions', { params })
  },

  // 发起流程
  startProcess(processKey: string, data: ProcessStartRequest) {
    return request.post<ProcessInstance>(`/api/processes/${processKey}/start`, data)
  },

  // 获取我的申请列表
  getMyApplications(params: { page?: number; size?: number; status?: string }) {
    return request.get('/api/processes/my-applications', { params })
  },

  // 获取流程详情
  getProcessDetail(processId: string) {
    return request.get<ProcessInstance>(`/api/processes/${processId}`)
  },

  // 撤回流程
  withdrawProcess(processId: string, reason: string) {
    return request.post(`/api/processes/${processId}/withdraw`, { reason })
  },

  // 催办流程
  urgeProcess(processId: string) {
    return request.post(`/api/processes/${processId}/urge`)
  },

  // 切换收藏状态
  toggleFavorite(processKey: string) {
    return request.post<boolean>(`/api/processes/${processKey}/favorite`)
  },

  // 保存草稿
  saveDraft(processKey: string, formData: Record<string, any>) {
    return request.post(`/api/processes/${processKey}/draft`, formData)
  },

  // 获取草稿
  getDraft(processKey: string) {
    return request.get(`/api/processes/${processKey}/draft`)
  },

  // 删除草稿
  deleteDraft(processKey: string) {
    return request.delete(`/api/processes/${processKey}/draft`)
  }
}
