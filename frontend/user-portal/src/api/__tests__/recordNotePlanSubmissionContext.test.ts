import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createRecordNote } from '../recordNote'
const { post }=vi.hoisted(()=>({post:vi.fn<(url: string, data: unknown) => Promise<unknown>>(async()=>({data:{id:'qa-note'}}))}))
vi.mock('../request',()=>({default:{post}}))
describe('Record Note plan: a row submission has one hosting Request identity',()=>{
  beforeEach(() => { post.mockClear() })
  it.each([
    ['qa-request', undefined, ['qa-request']],
    [undefined, 'qa-request', ['qa-request']],
    [undefined, undefined, []],
  ] as const)('preserves single-source context target=%s payload=%s', async (targetId, payloadId, expected) => {
    const file = new File(['QA'], 'qa.txt')
    await createRecordNote({ targetType: 'RECORD', targetId: 'row', tableId: 'table', processInstanceId: targetId },
      { processInstanceId: payloadId, files: [file], inlineImageIds: ['image-a', 'image-b'] })
    const form = post.mock.calls[0][1] as FormData
    expect(form.getAll('processInstanceId')).toEqual(expected)
    expect(form.getAll('files')).toEqual([file])
    expect(form.getAll('inlineImageIds')).toEqual(['image-a', 'image-b'])
  })
  it('rejects conflicting identities before sending a request', async () => {
    await expect(createRecordNote({ targetType: 'RECORD', targetId: 'row', tableId: 'table', processInstanceId: 'request-a' },
      { processInstanceId: 'request-b' })).rejects.toThrow()
    expect(post).not.toHaveBeenCalled()
  })
  it('does not append processInstanceId twice when both target and payload carry it',async()=>{
    await createRecordNote({targetType:'RECORD',targetId:'row',tableId:'table',processInstanceId:'qa-request',formKey:'form',controlId:'control',clientSubmissionId:'submission'},
      {processInstanceId:'qa-request',files:[new File(['QA'],'qa.txt')]})
    const data=post.mock.calls[0][1] as FormData
    expect(data.getAll('processInstanceId')).toEqual(['qa-request'])
  })
})
