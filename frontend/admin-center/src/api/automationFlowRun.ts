import { get, post } from './request'
import type { AdminListPage } from '@/types/common'
import type { ListColumnFilter } from '@platform-shared/list/columnMeta'

/** 后端 ApiResponse 包装(与 automationFlow 同构) */
interface ApiEnvelope<T> {
  success: boolean
  data: T
}

/** AP FlowRunStatus,逐值对齐后端 AutomationFlowRunColumnSpec 的选项 */
export type AutomationRunStatus =
  | 'SUCCEEDED'
  | 'RUNNING'
  | 'QUEUED'
  | 'PAUSED'
  | 'FAILED'
  | 'TIMEOUT'
  | 'CANCELED'
  | 'INTERNAL_ERROR'
  | 'QUOTA_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'LOG_SIZE_EXCEEDED'

export interface AutomationFlowRunSummary {
  id: string
  flowId: string
  /** 迁移键(flow.metadata.hermesFlowKey);本环境原生 flow 为 null */
  flowKey: string | null
  /** 执行时那个版本的名字(flow 改名后历史仍显示当时的名字) */
  flowDisplayName: string
  projectId: string
  projectName: string
  status: AutomationRunStatus
  startTime: string | null
  finishTime: string | null
  /** 执行耗时(毫秒);未结束的运行为 null */
  durationMs: number | null
  failedStepName: string | null
  failedStepMessage: string | null
}

export interface AutomationFlowRunListQuery {
  page: number
  size: number
  keyword?: string
  filters?: Array<ListColumnFilter & { field: string }>
  sortField?: string
  sortDirection?: 'ASC' | 'DESC'
}

export const automationFlowRunApi = {
  query: (body: AutomationFlowRunListQuery) =>
    post<ApiEnvelope<AdminListPage<AutomationFlowRunSummary>>>('/automation/flow-runs/query', body),

  /** 单次运行的完整 JSON(含逐步骤输出);当前会话看不到该运行时后端 404 */
  getRun: (runId: string) =>
    get<ApiEnvelope<Record<string, unknown>>>(`/automation/flow-runs/${runId}`)
}
