import { ref, unref, watch, type MaybeRef } from 'vue'
import { getCompletionOperationStatus, type TaskCompletionResult } from '@/api/task'
import { savedCompletionOperationId } from '@/utils/completionOperation'
import type { CompletionOperationStatus } from '@/api/assignmentCompletion'

export interface CompletionRecoveryState {
  operationId: string
  state: CompletionOperationStatus['state'] | 'DELIVERY_UNKNOWN'
  checking?: boolean
}
export function useCompletionRecovery(taskId: MaybeRef<string>) {
  const completionState = ref<CompletionRecoveryState | null>(null)
  watch(() => unref(taskId), task => {
    const operationId = task ? savedCompletionOperationId(task) : null
    completionState.value = operationId ? { operationId, state: 'DELIVERY_UNKNOWN' } : null
  }, { immediate: true })
  function accepted(result: TaskCompletionResult | null | undefined) {
    if (result?.operationId && result.state) completionState.value = { operationId: result.operationId, state: result.state }
  }
  function unknown(operationId: string) { completionState.value = { operationId, state: 'DELIVERY_UNKNOWN' } }
  async function refreshCompletion() {
    const current = completionState.value
    if (!current || current.checking) return
    const requestedTask = unref(taskId)
    completionState.value = { ...current, checking: true }
    try {
      const response = await getCompletionOperationStatus(current.operationId)
      if (completionState.value?.operationId !== current.operationId || unref(taskId) !== requestedTask) return
      if (!['READY', 'PENDING_SYNC', 'SYNC_CONFLICT', 'NOT_ACCEPTED'].includes(response.data.state)
        || response.data.operationId !== current.operationId
        || (response.data.state === 'NOT_ACCEPTED' ? response.data.taskId !== null : response.data.taskId !== unref(taskId))) {
        throw new Error('Mismatched completion operation status')
      }
      completionState.value = { operationId: current.operationId, state: response.data.state }
    } catch {
      if (completionState.value?.operationId !== current.operationId || unref(taskId) !== requestedTask) return
      // FALLBACK(external): A failed status read proves nothing about acceptance; preserve the operation and entered form.
      completionState.value = { operationId: current.operationId, state: 'DELIVERY_UNKNOWN' }
    }
  }
  return { completionState, accepted, unknown, refreshCompletion }
}
