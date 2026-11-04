import { afterEach, describe, expect, it, vi } from 'vitest'
import { createBatchRowEventRunner } from '@platform-shared/batch/rowEvents'
import { parseFormCreateEventHandler } from '@/utils/formCreateEventRuntime'
import { originalEmailScript } from '../../../../scripts/fixtures/batch-email-scripts.mjs'
import { runNativeFormEvent } from '@platform-shared/formEvents/native'

const runner = createBatchRowEventRunner(parseFormCreateEventHandler)
const rules = (body: string): Array<Record<string, unknown>> => [{ type: 'input', field: 'person', on: { change: '$FNX:' + body } }, { type: 'input', field: 'email', info: 'Design help' }]
afterEach(() => { vi.unstubAllGlobals() })

describe('asynchronous form event contract', () => {
  it('discards deferred notifications when a standalone async handler rejects', async () => {
    const session = runner.createSession({}, rules(''), () => false)
    const handler = parseFormCreateEventHandler('$FNX:api.setFormNotification("Saved", "INFO", "saved"); return Promise.reject(new Error("failed"));')!
    await expect(handler({ api: session.api, rule: {}, field: 'person', value: '' })).rejects.toThrow('failed')
    expect(session.notifications.size).toBe(0)
  })
  it('runs the exact supplied email script with injected change arguments in Batch and native Preview', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ id: 'selected', email: 'selected@example.test' }))))
    const design = [{ type: 'input', field: 'assignee_id', on: { change: originalEmailScript } }, { type: 'input', field: 'assignee_email' }]
    const session = runner.createSession({ assignee_id: '', assignee_email: '' }, design, () => false)
    await session.change('assignee_id', 'selected')
    expect(session.data.assignee_email).toBe('selected@example.test')
    const model: Record<string, unknown> = { assignee_id: 'selected', assignee_email: '' }
    const native = { fields: () => Object.keys(model), getRule: (field: string) => design.find(rule => rule.field === field)!,
      getValue: (field: string) => model[field], setValue: (patch: Record<string, unknown>) => Object.assign(model, patch), mergeRule: vi.fn() }
    await runNativeFormEvent(native as never, originalEmailScript, { field: 'assignee_id', value: 'selected', rule: design[0]!, api: native as never, args: ['selected'] }, parseFormCreateEventHandler)
    expect(model.assignee_email).toBe('selected@example.test')
  })
  it('supports text info, explicit clearing, and snapshot restoration without changing design rules', () => {
    const design = rules('')
    const session = runner.createSession({ person: '', email: '' }, design, () => false)
    const saved = session.snapshot()
    session.api.updateRule('email', { info: '<b>Loading</b>' })
    expect(session.infos.get('email')).toBe('<b>Loading</b>')
    session.api.updateRule('email', { info: '' })
    expect(session.infos.has('email')).toBe(true)
    expect(session.infos.get('email')).toBe('')
    expect(() => session.api.updateRule('missing', { info: '' })).toThrow()
    expect(() => session.api.updateRule('email', { type: 'input' } as never)).toThrow()
    session.restore(saved)
    expect(session.infos.size).toBe(0)
    expect(design[1]!.info).toBe('Design help')
  })
  it('awaits returned requests before dispatching value hooks and form onChange', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ email: 'selected@example.test' }), { status: 200 })))
    const design = rules('api.updateRule("email", { info: "Loading" }); return fetch("/api/v1/admin/users/test").then(r => r.json()).then(u => { api.setValue("email", u.email); api.updateRule("email", { info: "" }); });')
    design[0]!.hook = { value: '$FNX:api.setValue("email", api.getValue("email") + "|value")' }
    const session = runner.createSession({ person: '', email: '' }, design, () => false, {
      onChange: '$FNX:if (field === "person") api.setValue("email", api.getValue("email") + "|form")',
    })
    await session.change('person', 'selected')
    expect(session.data.email).toBe('selected@example.test|value|form')
    expect(session.infos.get('email')).toBe('')
  })
  it('awaits asynchronous guards and retains false semantics across event arrays', async () => {
    const session = runner.createSession({ email: 'before' }, rules(''), () => false, {
      beforeSubmit: ['$FNX:return Promise.resolve(false)', '$FNX:api.setValue("email", "bad")'],
    })
    await expect(session.submit()).rejects.toThrow('batchEventSubmitRejected')
    expect(session.data.email).toBe('before')
  })
  it('preserves raw function returns and propagates failures', async () => {
    const session = runner.createSession({}, rules(''), () => false)
    const handler = parseFormCreateEventHandler(() => Promise.resolve(42))!
    await expect(handler({ api: session.api, rule: {}, field: 'person', value: '' })).resolves.toBe(42)
    const failure = parseFormCreateEventHandler(() => { throw new Error('raw failure') })!
    expect(() => failure({ api: session.api, rule: {}, field: 'person', value: '' })).toThrow('raw failure')
  })
  it('rebinds native hook API arguments to the guarded row context', async () => {
    const session = runner.createSession({ person: '', email: '' }, rules(''), field => field === 'person')
    const native = { getValue: () => '', setValue: vi.fn() }
    const hook = { field: 'email', hook: { titleClick: '$FNX:return Promise.resolve().then(() => $inject.args[0].api.setValue("person", "forbidden"));' } }
    await expect(session.ruleEvent(hook, 'titleClick', [{ api: native, rule: { native: true } }], 'hook')).rejects.toThrow('batchEventWriteDenied')
    expect(native.setValue).not.toHaveBeenCalled()
  })
})
