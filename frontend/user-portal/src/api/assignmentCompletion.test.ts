import { beforeEach, describe, expect, it, vi } from 'vitest'
const transport = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn() }))
vi.mock('./request', () => ({ request: transport }))
import { submitTaskCompletion } from './assignmentCompletion'
import { completionOperationId, savedCompletionOperationId, CompletionDeliveryUnknown, CompletionRejected } from '@/utils/completionOperation'
import { USER_ID_KEY } from './auth'

describe('stable completion operation identity', () => {
  beforeEach(() => {
    vi.clearAllMocks(); sessionStorage.clear(); localStorage.clear()
    transport.post.mockImplementation(async (_url, payload) => ({ data: { operationId: payload.operationId, state: 'PENDING_SYNC' } }))
  })
  it('keeps the original UUID across retries and edited values without storing form contents', async () => {
    transport.post.mockRejectedValueOnce(new Error('synthetic lost response'))
    await expect(submitTaskCompletion('task', { taskId: 'task', action: 'APPROVE', formData: { caption: 'private caption' } }))
      .rejects.toBeInstanceOf(CompletionDeliveryUnknown)
    const original = savedCompletionOperationId('task')
    await submitTaskCompletion('task', { taskId: 'task', action: 'APPROVE', formData: { caption: 'edited caption' } })
    expect(transport.post.mock.calls.map(call => call[1].operationId)).toEqual([original, original])
    expect(JSON.stringify(sessionStorage)).not.toContain('private caption')
    expect(JSON.stringify(sessionStorage)).not.toContain('edited caption')
  })
  it('partitions opaque operation IDs by user and task', () => {
    localStorage.setItem(USER_ID_KEY, 'actor-a')
    const first = completionOperationId('task-a')
    expect(completionOperationId('task-a')).toBe(first)
    expect(completionOperationId('task-b')).not.toBe(first)
    localStorage.setItem(USER_ID_KEY, 'actor-b')
    expect(completionOperationId('task-a')).not.toBe(first)
  })
  it('reports a definite rejection separately from an unknown completion outcome', async () => {
    transport.post.mockRejectedValueOnce({ response: { status: 403, data: { error: { message: 'Forbidden' } } } })
    await expect(submitTaskCompletion('task', { taskId: 'task', action: 'APPROVE' })).rejects.toBeInstanceOf(CompletionRejected)
  })
  it('clears an operation ID only after a successful explicit legacy response', async () => {
    transport.post.mockResolvedValue({ data: null })
    await submitTaskCompletion('task', { taskId: 'task', action: 'APPROVE' })
    expect(savedCompletionOperationId('task')).toBeNull()
  })
  it('cannot treat a response for another operation as completion success', async () => {
    transport.post.mockResolvedValue({ data: { state: 'READY', operationId: crypto.randomUUID() } })
    await expect(submitTaskCompletion('task', { taskId: 'task', action: 'APPROVE' })).rejects.toBeInstanceOf(CompletionDeliveryUnknown)
    expect(savedCompletionOperationId('task')).not.toBeNull()
  })
})
