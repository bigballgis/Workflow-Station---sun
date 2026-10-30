import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
const status = vi.hoisted(() => vi.fn())
vi.mock('@/api/task', () => ({ getCompletionOperationStatus: status }))
import { useCompletionRecovery } from '../useCompletionRecovery'
import { completionOperationId } from '@/utils/completionOperation'

beforeEach(() => { sessionStorage.clear(); localStorage.clear(); vi.clearAllMocks() })
it('restores the original operation after reload and reads its status without submitting again', async () => {
  const operationId = completionOperationId('task')
  const recovery = useCompletionRecovery('task')
  expect(recovery.completionState.value?.state).toBe('DELIVERY_UNKNOWN')
  status.mockResolvedValue({ data: { operationId, taskId: 'task', state: 'PENDING_SYNC' } })
  await recovery.refreshCompletion()
  expect(recovery.completionState.value).toEqual({ operationId, state: 'PENDING_SYNC' })
})
it('does not reinterpret an unavailable or mismatched receipt as an unaccepted submission', async () => {
  const operationId = completionOperationId('task')
  const recovery = useCompletionRecovery('task')
  status.mockResolvedValue({ data: { operationId, taskId: 'another-task', state: 'READY' } })
  await recovery.refreshCompletion()
  expect(recovery.completionState.value).toEqual({ operationId, state: 'DELIVERY_UNKNOWN' })
})
it('ignores a delayed status response after navigation to another task', async () => {
  const operationId = completionOperationId('task')
  const task = ref('task'); const recovery = useCompletionRecovery(task)
  let resolve!: (value: unknown) => void
  status.mockImplementation(() => new Promise(done => { resolve = done }))
  const pending = recovery.refreshCompletion()
  task.value = 'new-task'; await nextTick()
  resolve({ data: { operationId, taskId: 'task', state: 'READY' } }); await pending
  expect(recovery.completionState.value).toBeNull()
})
