import { describe, expect, it } from 'vitest'
import { createBatchRowEventRunner } from '@platform-shared/batch/rowEvents'
import { parseFormCreateEventHandler } from '@/utils/formCreateEventRuntime'

const runner = createBatchRowEventRunner(parseFormCreateEventHandler)
const rules = [{ type: 'miAssignment', children: [
  { type: 'lookup', field: 'recipient' }, { type: 'input', field: 'contact' },
] }, { type: 'input', field: 'suffix' }]
const options = { onChange: '$FNX:if (field === "recipient") api.setValue("contact", value.email + api.getValue("suffix"));' }

describe('ordinary form events on independent Batch drafts', () => {
  it('accepts form onChange, preserves Lookup values and fills each row immediately', () => {
    const a = runner.createSession({ suffix: '-A', contact: 'old' }, rules, () => false, options)
    const b = runner.createSession({ suffix: '-B', contact: 'old' }, rules, () => false, options)
    const person = { id: 'synthetic', email: 'person@example.invalid' }
    a.change('recipient', person); b.change('recipient', person)
    expect(a.data.contact).toBe('person@example.invalid-A')
    expect(b.data.contact).toBe('person@example.invalid-B')
    expect(a.data.recipient).toEqual(person)
  })
  it('runs initialization, blur, value hooks and submit guards with the ordinary API', () => {
    const session = runner.createSession({ suffix: '' }, [{ type: 'input', field: 'contact',
      on: { blur: '$FNX:api.required(true, "contact"); api.setFieldError("contact", "Check contact");' },
      hook: { value: '$FNX:api.setLabel("contact", "Updated");' },
    }, { type: 'input', field: 'suffix' }], () => false, {
      onCreated: '$FNX:api.setValue("suffix", "created");',
      beforeSubmit: '$FNX:return api.getValue("contact") === "allowed";',
    })
    session.bootstrap()
    expect(session.data.suffix).toBe('created')
    session.change('contact', 'blocked')
    session.event('contact', 'blur')
    expect(session.labels.get('contact')).toBe('Updated')
    expect(() => session.submit()).toThrow()
    session.api.clearFieldError('contact')
    session.change('contact', 'allowed')
    expect(() => session.submit()).not.toThrow()
  })
  it('passes native fetch context through handler arrays and preserves cancellation', () => {
    const session = runner.createSession({}, rules, () => false, { beforeFetch: [
      '$FNX:config.method = "POST"; data.filter = "synthetic";', '$FNX:return false;',
      '$FNX:config.method = "must-not-run";',
    ] })
    const config = { method: 'GET' }; const data = { filter: '' }
    expect(session.beforeFetch(config, data)).toBe(false)
    expect(config.method).toBe('POST')
    expect(data.filter).toBe('synthetic')
  })
  it('keeps the native fetch API context by reference without JSON cloning it', () => {
    const nativeApi: { top?: unknown } = {}; nativeApi.top = nativeApi
    const session = runner.createSession({}, rules, () => false, { beforeFetch: '$FNX:config.native = data.api.top === data.api;' })
    const config = { native: false }
    session.beforeFetch(config, { api: nativeApi })
    expect(config.native).toBe(true)
  })
  it('rejects readonly writes and leaves the source rows unchanged', () => {
    const source = { suffix: '-A', contact: 'keep' }
    const session = runner.createSession(source, rules, field => field === 'contact', options)
    expect(() => session.change('recipient', { email: 'x@example.invalid' })).toThrow('batchEventWriteDenied')
    expect(source.contact).toBe('keep')
  })
})
