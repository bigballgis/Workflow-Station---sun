import { beforeEach, describe, expect, it, vi } from 'vitest'
const api = vi.hoisted(() => ({ status: vi.fn() }))
vi.mock('@/api/task', () => ({ getCompletionOperationStatus: api.status }))
import { useBatchCompletionRecovery } from '../useBatchCompletionRecovery'
import { USER_ID_KEY } from '@/api/auth'

describe('batch operation recovery after tasks leave the To Do page', () => {
  const operationId = '10000000-0000-4000-8000-000000000001'
  beforeEach(() => { sessionStorage.clear(); localStorage.clear(); vi.resetAllMocks() })
  it('restores opaque operation/task IDs after reload and queries their status without live task data', async () => {
    const first = useBatchCompletionRecovery()
    first.record([{ taskId: 'closed', operationId, state: 'PENDING_SYNC' }])
    const reloaded = useBatchCompletionRecovery()
    expect(reloaded.rows.value).toEqual([{ taskId: 'closed', operationId, state: 'DELIVERY_UNKNOWN' }])
    api.status.mockResolvedValue({ data: { taskId: 'closed', operationId, state: 'READY' } })
    await reloaded.refresh('closed')
    expect(reloaded.rows.value[0]?.state).toBe('READY')
    expect(api.status).toHaveBeenCalledWith(operationId)
  })
  it.each([
    { taskId: 'other', operationId, state: 'READY' },
    { taskId: null, operationId: crypto.randomUUID(), state: 'NOT_ACCEPTED' },
    { taskId: 'closed', operationId, state: 'NOT_ACCEPTED' },
  ])('does not convert a mismatched response into success or permission to retry %#', async data => {
    const recovery = useBatchCompletionRecovery()
    recovery.record([{ taskId: 'closed', operationId, state: 'PENDING_SYNC' }])
    api.status.mockResolvedValue({ data })
    await recovery.refresh('closed')
    expect(recovery.rows.value[0]?.state).toBe('DELIVERY_UNKNOWN')
  })
  it('retains the original operation during an outage, and isolates another signed-in user', async () => {
    localStorage.setItem(USER_ID_KEY, 'first')
    const recovery = useBatchCompletionRecovery()
    recovery.record([{ taskId: 'closed', operationId, state: 'SYNC_CONFLICT' }])
    api.status.mockRejectedValue(new Error('unavailable'))
    await recovery.refresh('closed')
    expect(recovery.rows.value[0]).toMatchObject({ operationId, state: 'DELIVERY_UNKNOWN', checking: false })
    localStorage.setItem(USER_ID_KEY, 'other')
    expect(useBatchCompletionRecovery().rows.value).toEqual([])
  })
  it('removes only successfully acknowledged task rows', () => {
    const recovery = useBatchCompletionRecovery()
    recovery.record([{ taskId: 'pending', operationId, state: 'PENDING_SYNC' }])
    recovery.completed(['other'])
    expect(recovery.rows.value).toHaveLength(1)
    recovery.completed(['pending'])
    expect(useBatchCompletionRecovery().rows.value).toEqual([])
  })
})
