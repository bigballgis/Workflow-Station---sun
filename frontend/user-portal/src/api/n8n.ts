import { request } from './request'

export interface N8nActionExecuteRequest {
  actionDefinitionId: number
  taskId: string
  processInstanceId: string
  inputData: Record<string, any>
}

export interface N8nActionExecuteResponse {
  success: boolean
  data?: Record<string, any>
  errorMessage?: string
  status: string // SUCCESS, FAILED, TIMEOUT
}

export function executeN8nAction(data: N8nActionExecuteRequest) {
  return request.post<N8nActionExecuteResponse>('/n8n/action/execute', data)
}
