import { beforeEach, describe, expect, it, vi } from 'vitest'
import { canAddRecordNote, createRecordNote, type RecordNoteTargetParams } from '../recordNote'

const { post, get } = vi.hoisted(() => ({
  post: vi.fn<(url: string, data: unknown) => Promise<unknown>>(async () => ({ data: { id: 'note', noteType: 'COMMENT', createdBy: 'developer', createdAt: '2026-10-16' } })),
  get: vi.fn<(url: string, config: unknown) => Promise<unknown>>(async () => ({ data: true }))
}))
vi.mock('../request', () => ({ default: { post, get } }))
const target: RecordNoteTargetParams = { targetType: 'RECORD', targetId: 'TX-B', tableId: 'table',
  processInstanceId: 'request', formKey: 'form', controlId: 'control', clientSubmissionId: 'client' }

beforeEach(() => { vi.clearAllMocks() })
describe('Record Note upload context', () => {
  it('sends the Audit surface on formal submission without changing the permission query', async () => {
    const audit = { ...target, sourceView: 'AUDIT' as const }
    await canAddRecordNote(audit)
    expect(get.mock.calls[0][1]).toEqual({ params: expect.not.objectContaining({ sourceView: expect.anything() }) })
    await createRecordNote(audit, { files: [new File(['file'], 'receipt.pdf')] })
    const data = post.mock.calls[0][1] as FormData
    expect(data.get('sourceView')).toBe('AUDIT')
    expect(data.get('targetId')).toBe('TX-B')
    expect(data.get('taskName')).toBeNull()
    expect(data.get('verifiedUploadSource')).toBeNull()
  })
  it('identifies the request surface explicitly so old clients are not guessed', async () => {
    await createRecordNote({ ...target, sourceView: 'REQUEST' }, {})
    expect((post.mock.calls[0][1] as FormData).get('sourceView')).toBe('REQUEST')
  })
  it('sends only the task ID for the backend to resolve its name', async () => {
    await createRecordNote({ ...target, taskId: 'actual-task' }, { bodyHtml: '<p>file added</p>' })
    const data = post.mock.calls[0][1] as FormData
    expect(data.get('taskId')).toBe('actual-task')
    expect(data.get('sourceView')).toBeNull()
    expect(data.get('taskName')).toBeNull()
  })
})
