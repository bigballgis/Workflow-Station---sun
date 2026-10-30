import { request } from './request'
import type { AxiosRequestConfig } from 'axios'

export interface ActionAvailability {
  contractVersion: number
  resourceId: string
  allowed: boolean
  targets: Array<{ id: string; label: string }>
}
export interface ReopenResult {
  operationId: string
  processInstanceId: string
  processDefinitionId: string
  synchronizationState: 'READY' | 'PENDING_SYNC' | 'SYNC_CONFLICT'
}
const quiet: AxiosRequestConfig & { skipGlobalErrorHandler: boolean } = { skipGlobalErrorHandler: true }
export async function actionAvailability(resource: string, kind: 'handoff' | 'reopen'): Promise<ActionAvailability> {
  const prefix = kind === 'handoff' ? 'tasks' : 'processes'
  const response = await request.get<{ data: ActionAvailability }>(`/${prefix}/${encodeURIComponent(resource)}/assignment/${kind}-availability`, quiet)
  const value = response.data
  if (value?.contractVersion !== 1 || value.resourceId !== resource || typeof value.allowed !== 'boolean' || !Array.isArray(value.targets)) {
    throw new Error('Invalid action availability')
  }
  return value
}
export function handoffTask(task: string, target: string) {
  return request.post(`/tasks/${encodeURIComponent(task)}/assignment/handoff`, { target })
}
export async function reopenProcess(source: string, operationId: string): Promise<ReopenResult> {
  const response = await request.post<{ data: ReopenResult }>(`/processes/${encodeURIComponent(source)}/reopen`, { operationId }, quiet)
  const value = response.data
  if (value?.operationId !== operationId || !['READY', 'PENDING_SYNC', 'SYNC_CONFLICT'].includes(value.synchronizationState)
    || !value.processInstanceId || !value.processDefinitionId) throw new Error('Invalid reopen result')
  return value
}
