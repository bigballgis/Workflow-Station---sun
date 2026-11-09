import { request } from './request'
import type { PortalListPage } from './task'
import type { ListColumnFilter } from '@platform-shared/list/columnMeta'

export interface DelegationRule {
  id: number
  delegatorId: string
  delegateId: string
  /** The delegate's display name (read with the list); absent when unknown. */
  delegateName?: string | null
  delegationType: string
  processTypes?: string[]
  priorityFilter?: string[]
  startTime?: string
  endTime?: string
  status: string
  reason?: string
  /** Set while the rule cannot take effect because a party lost Portal entry; `status` is unchanged. */
  inactiveReason?: DelegationInactiveReason | null
  createdAt: string
  updatedAt: string
}

export type DelegationInactiveReason = 'DELEGATOR_LOST_PORTAL_ENTRY' | 'DELEGATE_LOST_PORTAL_ENTRY'

export interface DelegationRuleRequest {
  delegateId: string
  delegationType: string
  processTypes?: string[]
  priorityFilter?: string[]
  startTime?: string
  endTime?: string
  reason?: string
}

export interface DelegationAudit {
  id: number
  delegatorId: string
  delegateId: string
  /** Display names of both parties (read with the list); absent when unknown or not a person (BU + Role). */
  delegatorName?: string | null
  delegateName?: string | null
  taskId?: string
  operationType: string
  operationResult?: string
  operationDetail?: string
  ipAddress?: string
  userAgent?: string
  createdAt: string
}

export interface DelegationListQueryRequest {
  page: number
  size: number
  filters?: Array<ListColumnFilter & { field: string }>
  sortField?: string
  sortDirection?: 'ASC' | 'DESC'
}

export function getDelegationRules() {
  return request.get<{ data: DelegationRule[] }>('/delegations')
}

export function queryDelegationRules(params: DelegationListQueryRequest) {
  return request.post<{ data: PortalListPage<DelegationRule> }>('/delegations/query', params)
}

export function getActiveDelegationRules() {
  return request.get<{ data: DelegationRule[] }>('/delegations/active')
}

export function createDelegationRule(data: DelegationRuleRequest) {
  return request.post<{ data: DelegationRule }>('/delegations', data)
}

export function updateDelegationRule(ruleId: number, data: DelegationRuleRequest) {
  return request.put<{ data: DelegationRule }>(`/delegations/${ruleId}`, data)
}

export function deleteDelegationRule(ruleId: number) {
  return request.delete(`/delegations/${ruleId}`)
}

export function suspendDelegationRule(ruleId: number) {
  return request.post<{ data: DelegationRule }>(`/delegations/${ruleId}/suspend`)
}

export function resumeDelegationRule(ruleId: number) {
  return request.post<{ data: DelegationRule }>(`/delegations/${ruleId}/resume`)
}

export function getProxyTasks() {
  return request.get<{ data: DelegationRule[] }>('/delegations/proxy-tasks')
}

export function getDelegationAuditRecords(page: number = 0, size: number = 20) {
  return request.get<{ data: { content: DelegationAudit[]; totalElements: number; totalPages: number } }>('/delegations/audit', { params: { page, size } })
}

export function queryDelegationAudit(params: DelegationListQueryRequest) {
  return request.post<{ data: PortalListPage<DelegationAudit> }>('/delegations/audit/query', params)
}
