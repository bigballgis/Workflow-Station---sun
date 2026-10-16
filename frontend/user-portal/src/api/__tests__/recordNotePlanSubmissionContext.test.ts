import { describe, expect, it, vi } from 'vitest'
import { createRecordNote } from '../recordNote'
const { post }=vi.hoisted(()=>({post:vi.fn<(url: string, data: unknown) => Promise<unknown>>(async()=>({data:{id:'qa-note'}}))}))
vi.mock('../request',()=>({default:{post}}))
describe('Record Note plan: a row submission has one hosting Request identity',()=>{
  it('does not append processInstanceId twice when both target and payload carry it',async()=>{
    await createRecordNote({targetType:'RECORD',targetId:'row',tableId:'table',processInstanceId:'qa-request',formKey:'form',controlId:'control',clientSubmissionId:'submission'},
      {processInstanceId:'qa-request',files:[new File(['QA'],'qa.txt')]})
    const data=post.mock.calls[0][1] as FormData
    expect(data.getAll('processInstanceId')).toEqual(['qa-request'])
  })
})
