import request from './request'

export interface ProcessDefinition {
  id: string
  key: string
  name: string
  description?: string
  category: string
  version: string
  icon?: string
  isFavorite?: boolean
}

export interface ProcessInstance {
  id: string
  processDefinitionId: string
  processDefinitionKey?: string
  processDefinitionName: string
  businessKey?: string
  startTime: string
  endTime?: string
  status: string
  startUserId: string
  startUserName: string
  currentNode?: string
  currentAssignee?: string
  candidateUsers?: string
  variables?: Record<string, any>
}

export interface ProcessStartRequest {
  businessKey?: string
  formData?: Record<string, any>
  priority?: string
}

export interface FunctionUnitContent {
  id: string
  name: string
  code: string
  version: string
  description?: string
  status: string
  forms: Array<{
    id: string
    name: string
    data: string
    type: string
  }>
  processes: Array<{
    id: string
    name: string
    data: string
    type: string
  }>
  dataTables: Array<{
    id: string
    name: string
    data: string
    type: string
  }>
  error?: string
}

export const processApi = {
  // 获取可发起的流程定义列表
  getDefinitions(params?: { category?: string; keyword?: string }) {
    return request.get<ProcessDefinition[]>('/processes/definitions', { params })
  },

  // 发起流程
  startProcess(processKey: string, data: ProcessStartRequest) {
    return request.post<ProcessInstance>(`/processes/${processKey}/start`, data)
  },

  // 获取我的申请列表
  getMyApplications(params: { page?: number; size?: number; status?: string }) {
    return request.get('/processes/my-applications', { params })
  },

  // 获取流程详情
  getProcessDetail(processId: string) {
    return request.get<ProcessInstance>(`/processes/${processId}`)
  },

  // 撤回流程
  withdrawProcess(processId: string, reason: string) {
    return request.post(`/processes/${processId}/withdraw`, { reason })
  },

  // 催办流程
  urgeProcess(processId: string) {
    return request.post(`/processes/${processId}/urge`)
  },

  // 切换收藏状态
  toggleFavorite(processKey: string) {
    return request.post<boolean>(`/processes/${processKey}/favorite`)
  },

  // 保存草稿
  saveDraft(processKey: string, formData: Record<string, any>) {
    return request.post(`/processes/${processKey}/draft`, formData)
  },

  // 获取草稿
  getDraft(processKey: string) {
    return request.get(`/processes/${processKey}/draft`)
  },

  // 删除草稿
  deleteDraft(processKey: string) {
    return request.delete(`/processes/${processKey}/draft`)
  },
  
  // 获取草稿列表
  getDraftList() {
    return request.get<Array<{
      id: number
      processDefinitionKey: string
      processDefinitionName: string
      formData: Record<string, any>
      createdAt: string
      updatedAt: string
    }>>('/processes/drafts')
  },
  
  // 根据ID删除草稿
  deleteDraftById(draftId: number) {
    return request.delete(`/processes/drafts/${draftId}`)
  },
  
  // 获取功能单元完整内容（BPMN、表单等）
  getFunctionUnitContent(functionUnitId: string) {
    return request.get<FunctionUnitContent>(`/processes/function-units/${functionUnitId}/content`)
  },
  
  // 获取功能单元特定类型的内容
  getFunctionUnitContents(functionUnitId: string, contentType: string) {
    // 调用已经能正常工作的 /processes/function-units/{id}/content 端点
    // 然后在前端根据 contentType 过滤结果
    return this.getFunctionUnitContent(functionUnitId).then((response: any) => {
      // response 是 ApiResponse 格式: {success, code, data: {forms, processes, dataTables, ...}}
      const content = response.data || response
      const key = contentType.toUpperCase() === 'FORM' ? 'forms' :
                  contentType.toUpperCase() === 'PROCESS' ? 'processes' :
                  contentType.toUpperCase() === 'DATA_TABLE' ? 'dataTables' : null
      
      const items = key ? (content[key] || []) : []
      // 返回与原始 API 格式兼容的结构: { data: [...] }
      return {
        data: items.map((item: any) => ({
          id: item.id,
          contentType: contentType,
          contentName: item.name,
          contentData: item.data,
          sourceId: item.sourceId || item.id
        }))
      }
    })
  },
  
  // 获取流程历史记录
  getProcessHistory(processId: string) {
    return request.get(`/processes/${processId}/history`)
  }
}
