import { ref } from 'vue'
import { USER_ID_KEY } from '@/api/auth'
import { getCompletionOperationStatus } from '@/api/task'
import type { CompletionRecoveryState } from './useCompletionRecovery'

export interface BatchRecoveryRow extends CompletionRecoveryState { taskId: string }
/** Retain only opaque task/operation identities; comments, form data and request labels stay out of storage. */
export function useBatchCompletionRecovery() {
  const key = `ws_up_batch_completion:${encodeURIComponent(localStorage.getItem(USER_ID_KEY) ?? '')}`
  const rows = ref<BatchRecoveryRow[]>(load())
  function load(): BatchRecoveryRow[] {
    const stored = sessionStorage.getItem(key)
    if (!stored) return []
    const parsed: unknown = JSON.parse(stored)
    if (!Array.isArray(parsed) || parsed.some(row => !row || typeof row.taskId !== 'string'
      || typeof row.operationId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(row.operationId))) {
      throw new Error('Invalid saved batch completion identities')
    }
    return parsed.map(row => ({ taskId: row.taskId, operationId: row.operationId, state: 'DELIVERY_UNKNOWN' }))
  }
  function save() {
    sessionStorage.setItem(key, JSON.stringify(rows.value.map(({ taskId, operationId }) => ({ taskId, operationId }))))
  }
  function record(pending: BatchRecoveryRow[]) {
    const byTask = new Map(rows.value.map(row => [row.taskId, row]))
    for (const row of pending) byTask.set(row.taskId, row)
    rows.value = [...byTask.values()]
    save()
  }
  function completed(tasks: string[]) {
    rows.value = rows.value.filter(row => !tasks.includes(row.taskId))
    save()
  }
  async function refresh(task: string) {
    const row = rows.value.find(item => item.taskId === task)
    if (!row || row.checking) return
    row.checking = true
    try {
      const { data } = await getCompletionOperationStatus(row.operationId)
      if (data.operationId !== row.operationId || !['READY', 'PENDING_SYNC', 'SYNC_CONFLICT', 'NOT_ACCEPTED'].includes(data.state)
        || (data.state === 'NOT_ACCEPTED' ? data.taskId !== null : data.taskId !== task)) throw new Error('Mismatched batch status')
      row.state = data.state
    } catch {
      // FALLBACK(external): A failed read cannot prove that an independent task completion was rejected.
      row.state = 'DELIVERY_UNKNOWN'
    } finally { row.checking = false }
  }
  return { rows, record, completed, refresh }
}
