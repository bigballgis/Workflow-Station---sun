import { beforeEach, describe, expect, it, vi } from 'vitest'
const transport = vi.hoisted(() => ({ post: vi.fn(), put: vi.fn() }))
vi.mock('./request', () => ({ request: transport, default: transport }))
import { completeTask } from './task'
import { submitActionFormPopup, submitProcessFormUpdate, submitTaskForm } from './processForm'
import { processApi } from './process'

const pinned = {
  functionUnitId: 'published-v1', functionUnitCatalogId: 'published-v1', functionUnitCode: 'sample',
  functionUnitVersionLabel: '1.0.1', functionUnitVersionId: 27,
  processDefinitionId: 'definition-v1', expectedProcessDefinitionId: 'definition-v1',
}
function form() {
  return { ...pinned, amount: 3, approved: true, version: 'business-version',
    __subTables__: { 'dw:lines': [{ line_key: 'row-a', functionUnitCode: 'nested-business-value' }] } }
}
function businessOnly() {
  return { amount: 3, approved: true, version: 'business-version',
    __subTables__: { 'dw:lines': [{ line_key: 'row-a', functionUnitCode: 'nested-business-value' }] } }
}

describe('Portal transport excludes server-owned runtime version context', () => {
  beforeEach(() => vi.clearAllMocks())
  it('approves loaded form values without posting their pin as a client reassignment', async () => {
    const values = form()
    const scopes = [{ bindingId: '9', storeKey: 'dw:lines', rowKeys: [{ line_key: 'row-a' }], emptied: false }]
    await completeTask('task-a', { taskId: 'task-a', action: 'APPROVE', variables: values,
      formData: values, subTableBindingScopes: scopes, comment: 'Review completed' })
    expect(transport.post).toHaveBeenCalledWith('/tasks/task-a/complete', {
      taskId: 'task-a', action: 'APPROVE', variables: businessOnly(), formData: businessOnly(),
      subTableBindingScopes: scopes, comment: 'Review completed', operationId: expect.stringMatching(/^[0-9a-f-]{36}$/),
    }, { skipGlobalErrorHandler: true })
    expect(values).toEqual(form())
  })
  it('does not add form or variable values to a comment-only completion', async () => {
    await completeTask('task-a', { taskId: 'task-a', action: 'APPROVE' })
    const sent = transport.post.mock.calls[0][1]
    expect(sent.formData).toBeUndefined()
    expect(sent.variables).toBeUndefined()
  })
  it('saves a loaded task while retaining batch transport declarations', async () => {
    const values = form()
    await submitTaskForm('task-a', { formData: values, emptiedSubTableKeys: ['dw:removed'] })
    expect(transport.post).toHaveBeenCalledWith('/tasks/task-a/submit', {
      formData: businessOnly(), emptiedSubTableKeys: ['dw:removed'],
    })
    expect(values).toEqual(form())
  })
  it('updates a returned request using business fields and existing transport metadata', async () => {
    await submitProcessFormUpdate('request-a', { ...form(), emptiedSubTableKeys: ['dw:removed'] })
    expect(transport.put).toHaveBeenCalledWith('/processes/request-a/form', {
      ...businessOnly(), emptiedSubTableKeys: ['dw:removed'],
    })
  })
  it('submits Action popup data without changing its request context', async () => {
    await submitActionFormPopup('task-a', 'action-a', form())
    expect(transport.post).toHaveBeenCalledWith('/tasks/task-a/actions/action-a/form-popup-submit', {
      formData: businessOnly(),
    })
  })
  it('starts a request without reusing context carried by a copied form', async () => {
    await processApi.startProcess('sample', { formData: form() })
    expect(transport.post).toHaveBeenCalledWith('/processes/sample/start', { formData: businessOnly() })
  })
  it('keeps a saved draft free of a previously loaded request pin', async () => {
    await processApi.saveDraft('sample', form())
    expect(transport.post).toHaveBeenCalledWith('/processes/sample/draft', businessOnly())
  })
})
