import { request } from './request'
import type { BatchCompleteResult } from './task'
import { completionOperationId, forgetLegacyCompletion } from '@/utils/completionOperation'

/** A failed batch response cannot identify which of its independent transactions committed. */
export class BatchCompletionDeliveryUnknown extends Error {
  constructor(readonly operations: Record<string, string>) { super('Batch completion delivery is not confirmed') }
}
export async function submitBatchTaskCompletion(taskIds: string[], actionId: string, comment?: string) {
  const operationIds = Object.fromEntries([...new Set(taskIds)].map(task => [task, completionOperationId(task)]))
  try {
    const response = await request.post<{ data: BatchCompleteResult }>('/tasks/batch/complete',
      { taskIds, actionId, comment, operationIds }, { skipGlobalErrorHandler: true } as import('axios').AxiosRequestConfig)
    validateResult(response.data, operationIds)
    for (const task of response.data.succeeded) {
      if (!response.data.completedOperationIds?.[task]) forgetLegacyCompletion(task)
    }
    return response
  } catch (error) {
    const status = (error as { response?: { status: number } })?.response?.status
    if (status !== undefined && status >= 400 && status < 500) throw error
    throw new BatchCompletionDeliveryUnknown(operationIds)
  }
}
function validateResult(result: BatchCompleteResult, operations: Record<string, string>): void {
  const rows = [...result.succeeded, ...result.failed.map(row => row.taskId), ...(result.pending ?? []).map(row => row.taskId)]
  if (new Set(rows).size !== rows.length || rows.length !== Object.keys(operations).length || rows.some(task => !operations[task])) {
    throw new Error('Incomplete batch completion response')
  }
  for (const row of result.pending ?? []) {
    if (row.operationId !== operations[row.taskId] || !['PENDING_SYNC', 'SYNC_CONFLICT', 'DELIVERY_UNKNOWN'].includes(row.state)) {
      throw new Error('Mismatched batch completion operation')
    }
  }
  for (const [task, operation] of Object.entries(result.completedOperationIds ?? {})) {
    if (!result.succeeded.includes(task) || operation !== operations[task]) throw new Error('Mismatched completed batch operation')
  }
}
