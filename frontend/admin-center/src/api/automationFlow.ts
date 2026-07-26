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

/** 导出包内的 connection 清单项(源环境信息,凭据不随包走) */
export interface FlowExportConnection {
  externalId: string
  pieceName?: string
  displayName?: string
}

/** connections-check 返回项:该 externalId 在本环境目标 project 是否已存在 */
export interface ConnectionCheckItem {
  externalId: string
  exists: boolean
  displayName: string | null
  pieceName: string | null
  status: string | null
}

export const automationFlowApi = {
  /** 全部 flow 概要(跨 project 管理面视角) */
  list: () => get<ApiEnvelope<AutomationFlowSummary[]>>('/automation/flows'),

  /** 导出可携带 JSON(优先已发布版本);uat 导出 → prod 导入 */
  exportFlow: (flowId: string) =>
    get<Blob>(`/automation/flows/${flowId}/export`, { responseType: 'blob' }),

  /** 导入前预检:导出包 connections 清单在本环境的存在性(仅提示,不阻塞导入) */
  connectionsCheck: (externalIds: string[]) =>
    post<ApiEnvelope<ConnectionCheckItem[]>>('/automation/flows/connections-check', { externalIds }),

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
