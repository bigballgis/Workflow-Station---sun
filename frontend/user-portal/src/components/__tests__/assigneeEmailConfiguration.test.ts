import { afterEach, describe, expect, it, vi } from 'vitest'
import { createBatchRowEventRunner } from '@platform-shared/batch/rowEvents'
import { parseFormCreateEventHandler } from '@/utils/formCreateEventRuntime'
import { assigneeEmailScript } from '../../../../scripts/fixtures/assignee-email-script.mjs'
import { originalEmailScript } from '../../../../scripts/fixtures/batch-email-scripts.mjs'

const script = process.env.ASSIGNEE_EMAIL_BASELINE === '1' ? originalEmailScript : assigneeEmailScript
const runner = createBatchRowEventRunner(parseFormCreateEventHandler)
function session() {
  return runner.createSession({ assignee_id: null, assignee_email: 'old@example.test', merchant_name: 'Original merchant' },
    [{ field: 'assignee_id', type: 'lookup', on: { change: script } }, { field: 'assignee_email', type: 'input' }, { field: 'merchant_name', type: 'input' }], () => false)
}
afterEach(() => { vi.unstubAllGlobals() })
describe('configured assignee email backfill', () => {
  it('uses a single Lookup row without another request and preserves the merchant value', async () => {
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch)
    const state = session()
    await state.change('assignee_id', { id: 'chosen', email: ' selected@example.test ' })
    expect(state.data.assignee_email).toBe('selected@example.test')
    expect(state.data.merchant_name).toBe('Original merchant')
    expect(fetch).not.toHaveBeenCalled()
  })
  it.each(['chosen', { id: 'chosen' }])('looks up an ID or partial user object: %j', async selection => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ id: 'chosen', email: 'selected@example.test' }))))
    const state = session(); await state.change('assignee_id', selection)
    expect(state.data.assignee_email).toBe('selected@example.test')
    expect(state.errors.size).toBe(0)
  })
  it('blocks submission on missing email and clears the error on retry or clear', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ id: 'chosen', email: '' }))))
    const state = session(); await state.change('assignee_id', 'chosen')
    expect(state.errors.get('assignee_email')).toBe('Selected user has no valid email.')
    expect(() => state.submit()).toThrow('batchEventValidation')
    await state.change('assignee_id', null)
    expect(state.data.assignee_email).toBe(''); expect(state.errors.size).toBe(0)
  })
  it('records request failure as a field error instead of treating info as validation', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 500 })))
    const state = session(); await state.change('assignee_id', 'chosen')
    expect(state.errors.has('assignee_email')).toBe(true)
    expect(() => state.submit()).toThrow('batchEventValidation')
  })
})
