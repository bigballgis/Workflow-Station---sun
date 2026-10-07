import { get, post, put } from './request'
import type { AdminListPage } from '@/types/common'
import type { ListColumnFilter } from '@platform-shared/list/columnMeta'

interface ApiEnvelope<T> {
  success: boolean
  data: T
}

export type SlaJobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL' | 'FAILED' | 'SUPERSEDED'
export type SlaDispatchStatus = 'PENDING' | 'DISPATCHED' | 'DISPATCH_FAILED'

export interface SlaPolicyRow {
  functionUnitCode: string
  functionUnitName: string
  /** null = the Function Unit declares an SLA mapping but no lead time is set yet */
  leadTimeDays: number | null
  version: number | null
  updatedBy: string | null
  updatedAt: string | null
  latestJobStatus: SlaJobStatus | null
  latestJobId: string | null
}

export interface SlaPolicyListQuery {
  page: number
  size: number
  filters?: Array<ListColumnFilter & { field: string }>
  sortField?: string
  sortDirection?: 'ASC' | 'DESC'
}

export interface SlaPolicyUpdateRequest {
  leadTimeDays: number
  changeReason?: string
}

export interface SlaPolicyResponse {
  functionUnitCode: string
  leadTimeDays: number
  version: number
  dispatchStatus: SlaDispatchStatus
  recalcJobId: string | null
  dispatchError: string | null
}

export interface SlaPolicyHistory {
  id: string
  oldLeadTimeDays: number | null
  newLeadTimeDays: number
  oldVersion: number | null
  newVersion: number
  changeReason: string | null
  changedBy: string | null
  changedAt: string
  recalcJobId: string | null
  dispatchStatus: SlaDispatchStatus
  dispatchError: string | null
}

export interface SlaRecalcJob {
  id: string
  policyVersion: number | null
  leadTimeDays: number | null
  triggeredBy: string | null
  status: SlaJobStatus
  totalCount: number
  updatedCount: number
  unchangedCount: number
  skippedCount: number
  failedCount: number
  errorMessage: string | null
  submittedAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface SlaRecalcJobItem {
  processInstanceId: string
  outcome: 'UPDATED' | 'SKIPPED' | 'FAILED'
  oldDueDate: string | null
  newDueDate: string | null
  reason: string | null
  createdAt: string
}

const BASE = '/sla-policies'
const unwrap = <T>(body: ApiEnvelope<T>): T => body.data

export const slaPolicyApi = {
  query: (body: SlaPolicyListQuery) =>
    post<ApiEnvelope<AdminListPage<SlaPolicyRow>>>(`${BASE}/query`, body).then(unwrap),
  update: (functionUnitCode: string, data: SlaPolicyUpdateRequest) =>
    put<ApiEnvelope<SlaPolicyResponse>>(`${BASE}/${encodeURIComponent(functionUnitCode)}`, data).then(unwrap),
  recalculate: (functionUnitCode: string) =>
    post<ApiEnvelope<{ jobId: string }>>(`${BASE}/${encodeURIComponent(functionUnitCode)}/recalculate`).then(unwrap),
  history: (functionUnitCode: string) =>
    get<ApiEnvelope<SlaPolicyHistory[]>>(`${BASE}/${encodeURIComponent(functionUnitCode)}/history`).then(unwrap),
  jobs: (functionUnitCode: string) =>
    get<ApiEnvelope<SlaRecalcJob[]>>(`${BASE}/${encodeURIComponent(functionUnitCode)}/jobs`).then(unwrap),
  jobItems: (functionUnitCode: string, jobId: string) =>
    get<ApiEnvelope<SlaRecalcJobItem[]>>(
      `${BASE}/${encodeURIComponent(functionUnitCode)}/jobs/${encodeURIComponent(jobId)}/items`).then(unwrap),
}
