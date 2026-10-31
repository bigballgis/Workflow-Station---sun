import type { AxiosRequestConfig } from 'axios'
import { completionOperationId, forgetLegacyCompletion, CompletionDeliveryUnknown, CompletionRejected } from '@/utils/completionOperation'
import { request } from './request'
import type { TaskCompleteRequest, TaskCompletionResult } from './task'

const quiet: AxiosRequestConfig & { skipGlobalErrorHandler: boolean } = { skipGlobalErrorHandler: true }

export async function submitTaskCompletion(task: string, data: TaskCompleteRequest) {
  const url = `/tasks/${task}/complete`
  if (data.action !== 'APPROVE' && data.action !== 'REJECT') {
    return request.post<{ data?: TaskCompletionResult | null }>(url, data)
  }
  const operationId = completionOperationId(task, data.operationId)
  try {
    const result = await request.post<{ data?: TaskCompletionResult | null }>(url, { ...data, operationId }, quiet)
    if (result?.data?.state && (!['READY', 'PENDING_SYNC', 'SYNC_CONFLICT'].includes(result.data.state) || result.data.operationId !== operationId)) {
      throw new CompletionDeliveryUnknown(operationId)
    }
    if (result?.data?.operationId && !result.data.state) throw new CompletionDeliveryUnknown(operationId)
    if (result && !result.data?.state) forgetLegacyCompletion(task) // A successful, explicitly legacy reply has no recoverable assignment receipt.
    return result
  } catch (error) {
    const response = (error as { response?: { status: number; data?: unknown } })?.response
    if (response && response.status >= 400 && response.status < 500) {
      throw new CompletionRejected(response)
    }
    // A transport/server failure cannot prove the workflow was rejected. Keep the original UUID for recovery.
    throw new CompletionDeliveryUnknown(operationId)
  }
}

export interface CompletionOperationStatus {
  operationId: string
  taskId: string | null
  state: 'READY' | 'PENDING_SYNC' | 'SYNC_CONFLICT' | 'NOT_ACCEPTED'
}
export function getCompletionOperationStatus(operationId: string) {
  return request.get<{ data: CompletionOperationStatus }>(`/tasks/assignment/operations/${operationId}`, quiet)
}
