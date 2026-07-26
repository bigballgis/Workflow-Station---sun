import { get, post } from './request'

/** 后端 ApiResponse 包装(与 automationPiece 同构) */
interface ApiEnvelope<T> {
  success: boolean
  data: T
}

export interface AutomationFlowSummary {
  id: string
  /** 迁移键(metadata.hermesFlowKey);本环境原生 flow 为 null */
  flowKey: string | null
  displayName: string
  projectId: string
  projectName: string
  status: 'ENABLED' | 'DISABLED'
  published: boolean
  valid: boolean
  ownerName: string | null
  updated: string
}

export interface FlowImportResult {
  flowId: string
  flowKey: string
  displayName: string
  created: boolean
  published: boolean
}

export const automationFlowApi = {
  /** 全部 flow 概要(跨 project 管理面视角) */
  list: () => get<ApiEnvelope<AutomationFlowSummary[]>>('/automation/flows'),

  /** 导出可携带 JSON(优先已发布版本);uat 导出 → prod 导入 */
  exportFlow: (flowId: string) =>
    get<Blob>(`/automation/flows/${flowId}/export`, { responseType: 'blob' }),

  /** 导入(按迁移键 upsert);publish=true 时随后发布并启用 */
  importFlow: (file: File, publish: boolean) => {
    const form = new FormData()
    form.append('file', file)
    return post<ApiEnvelope<FlowImportResult>>(
      '/automation/flows/import', form,
      {
        headers: { 'Content-Type': 'multipart/form-data' },
        params: { publish },
        timeout: 60000
      }
    )
  }
}
