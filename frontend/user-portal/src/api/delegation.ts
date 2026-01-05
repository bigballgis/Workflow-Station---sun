import { request } from './request'

export interface DelegationRule {
  id: number
  delegatorId: string
  delegateId: string
  delegationType: string
  processTypes?: string[]
  priorityFilter?: string[]
  startTime?: string
  endTime?: string
  status: string
  reason?: string
  createdAt: string
  updatedAt: string
}

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
  taskId?: string
  operationType: string
  operationResult?: string
  operationDetail?: string
  ipAddress?: string
  userAgent?: string
  createdAt: string
}

// 获取委托规则列表
export function getDelegationRules() {
  return request.get<{ data: DelegationRule[] }>('/delegations')
}

// 获取有效委托规则
export function getActiveDelegationRules() {
  return request.get<{ data: DelegationRule[] }>('/delegations/active')
}

// 创建委托规则
export function createDelegationRule(data: DelegationRuleRequest) {
  return request.post<{ data: DelegationRule }>('/delegations', data)
}

// 更新委托规则
export function updateDelegationRule(ruleId: number, data: DelegationRuleRequest) {
  return request.put<{ data: DelegationRule }>(`/delegations/${ruleId}`, data)
}

// 删除委托规则
export function deleteDelegationRule(ruleId: number) {
  return request.delete(`/delegations/${ruleId}`)
}

// 暂停委托规则
export function suspendDelegationRule(ruleId: number) {
  return request.post<{ data: DelegationRule }>(`/delegations/${ruleId}/suspend`)
}

// 恢复委托规则
export function resumeDelegationRule(ruleId: number) {
  return request.post<{ data: DelegationRule }>(`/delegations/${ruleId}/resume`)
}

// 获取代理任务
export function getProxyTasks() {
  return request.get<{ data: DelegationRule[] }>('/delegations/proxy-tasks')
}

// 获取委托审计记录
export function getDelegationAuditRecords(page: number = 0, size: number = 20) {
  return request.get<{ data: any }>('/delegations/audit', { params: { page, size } })
}
