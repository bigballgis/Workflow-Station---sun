import { beforeEach, describe, expect, it, vi } from 'vitest'
const transport = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock('./request', () => ({ request: transport }))
import { submitBatchTaskCompletion, BatchCompletionDeliveryUnknown } from './batchTaskCompletion'
import { completionOperationId, savedCompletionOperationId } from '@/utils/completionOperation'

describe('independent batch completion identities', () => {
  beforeEach(() => { vi.clearAllMocks(); sessionStorage.clear(); localStorage.clear() })
  it('reuses individual submit identities and preserves pending/ready protected UUIDs', async () => {
    const original = completionOperationId('pending')
    transport.post.mockImplementation(async (_url, body) => ({ data: {
      succeeded: ['ready', 'legacy'], failed: [], pending: [{ taskId: 'pending', operationId: body.operationIds.pending, state: 'PENDING_SYNC' }],
      completedOperationIds: { ready: body.operationIds.ready },
    } }))
    await submitBatchTaskCompletion(['pending', 'ready', 'legacy'], 'action', 'private comment')
    expect(transport.post.mock.calls[0][1].operationIds.pending).toBe(original)
    expect(savedCompletionOperationId('pending')).toBe(original)
    expect(savedCompletionOperationId('ready')).not.toBeNull()
    expect(savedCompletionOperationId('legacy')).toBeNull()
    expect(JSON.stringify(sessionStorage)).not.toContain('private comment')
  })
  it('treats a lost whole-batch response as unknown for all submitted task identities', async () => {
    transport.post.mockRejectedValue(new Error('lost response'))
    const failure = await submitBatchTaskCompletion(['a', 'b'], 'action').catch(error => error)
    expect(failure).toBeInstanceOf(BatchCompletionDeliveryUnknown)
    expect(failure.operations).toEqual({ a: savedCompletionOperationId('a'), b: savedCompletionOperationId('b') })
    await submitBatchTaskCompletion(['a', 'b'], 'action').catch(() => undefined)
    expect(transport.post.mock.calls[1][1].operationIds).toEqual(failure.operations)
  })
  it.each([
    { succeeded: ['a'], failed: [], pending: [] },
    { succeeded: ['a', 'a'], failed: [] },
    { succeeded: ['a'], failed: [], pending: [{ taskId: 'b', operationId: crypto.randomUUID(), state: 'PENDING_SYNC' }] },
    { succeeded: ['a', 'b'], failed: [], completedOperationIds: { a: crypto.randomUUID() } },
  ])('cannot confirm an incomplete, duplicate or mismatched server response %#', async data => {
    transport.post.mockResolvedValue({ data })
    await expect(submitBatchTaskCompletion(['a', 'b'], 'action')).rejects.toBeInstanceOf(BatchCompletionDeliveryUnknown)
    expect(savedCompletionOperationId('a')).not.toBeNull()
  })
  it('preserves definite validation rejection without labelling it accepted or unknown', async () => {
    const rejection = { response: { status: 400 } }
    transport.post.mockRejectedValue(rejection)
    await expect(submitBatchTaskCompletion(['a'], 'action')).rejects.toBe(rejection)
  })
})
