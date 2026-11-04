import { afterEach, describe, expect, it, vi } from 'vitest'
import { FormEventOperation } from '@platform-shared/formEvents/operation'
import { createBatchRowEventRunner, type BatchEventSession } from '@platform-shared/batch/rowEvents'
import { parseFormCreateEventHandler } from '@/utils/formCreateEventRuntime'
import { workspace, rows } from './batchWorkspaceTestHelpers'
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() }, ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) } }))
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers() })
const deferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
type State = { setValue(field: string, value: unknown): boolean | Promise<boolean>; apply(): Promise<void>; close(): Promise<boolean>; eventPending: boolean; eventSessions: BatchEventSession[]; sharedEventContext: { event(rule: Record<string, unknown>, name: string, args: unknown[], kind: 'hook'): boolean | Promise<boolean> } }
function fixture(body: string, options: Record<string, unknown> = {}) {
  const wrapper = workspace({ options, runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler), rules: [
    { type: 'input', field: 'caption', on: { change: '$FNX:' + body } }, { type: 'inputNumber', field: 'amount' },
  ] })
  return { wrapper, state: wrapper.vm as unknown as State }
}
const query = 'return fetch("/api/test/" + value).then(r => r.json()).then(u => api.setValue("amount", u.amount));'

describe('atomic asynchronous workspaces', () => {
  it('honors an asynchronous false reset guard and keeps the dialog open', async () => {
    const { wrapper, state } = fixture('', { onReset: '$FNX:return Promise.resolve(false)' })
    expect(await state.close()).toBe(false)
    expect(wrapper.emitted('update:visible')).toBeUndefined()
  })
  it('queues asynchronous native hooks behind the pending change instead of cancelling either script', async () => {
    const reply = deferred<Response>()
    vi.stubGlobal('fetch', vi.fn(() => reply.promise))
    const { wrapper, state } = fixture(query)
    const changed = state.setValue('caption', 'selected')
    const watched = state.sharedEventContext.event({ field: 'caption', hook: { watch: '$FNX:return Promise.resolve().then(() => api.setValue("amount", api.getValue("amount") + 1));' } }, 'watch', [], 'hook')
    await state.apply(); expect(wrapper.emitted('apply')).toBeUndefined()
    reply.resolve(new Response('{"amount":42}'))
    expect(await changed).toBe(true)
    wrapper.findComponent({ name: 'BatchDesignedForm' }).vm.$emit('change', 'caption', 'selected')
    expect(await watched).toBe(true)
    expect(state.eventSessions.map(session => session.data.amount)).toEqual([43, 43])
  })
  it('waits before accepting or applying, deduplicates the two selected-row requests, and leaves unselected data intact', async () => {
    const reply = deferred<Response>()
    const fetch = vi.fn(() => reply.promise); vi.stubGlobal('fetch', fetch)
    const { wrapper, state } = fixture(query)
    const changed = state.setValue('caption', 'selected')
    expect(state.eventPending).toBe(true)
    await state.apply(); expect(wrapper.emitted('apply')).toBeUndefined()
    expect(fetch).toHaveBeenCalledTimes(1)
    reply.resolve(new Response('{"amount":42}'))
    expect(await changed).toBe(true)
    expect(state.eventPending).toBe(false)
    await state.apply()
    expect(wrapper.emitted('apply')![0]![0]).toMatchObject([{ caption: 'selected', amount: 42 }, { caption: 'selected', amount: 42 }, rows[2]!])
  })
  it('keeps only the latest action, even when the old transport ignores cancellation', async () => {
    const a = deferred<Response>(), b = deferred<Response>()
    vi.stubGlobal('fetch', vi.fn((url: string) => url.endsWith('/choice-A') ? a.promise : b.promise))
    const { state } = fixture(query)
    const first = state.setValue('caption', 'choice-A')
    const second = state.setValue('caption', 'choice-B')
    b.resolve(new Response('{"amount":22}')); expect(await second).toBe(true)
    a.resolve(new Response('{"amount":11}')); expect(await first).toBe(false)
    await Promise.resolve()
    expect(state.eventSessions.map(session => session.data.amount)).toEqual([22, 22])
  })
  it('restores every row if a single request fails and blocks subsequent Apply', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => url.endsWith('/two') ? new Response('', { status: 500 }) : new Response('{"amount":42}')))
    const { wrapper, state } = fixture(query.replace('+ value', '+ api.getValue("key")'))
    expect(await state.setValue('caption', 'failed')).toBe(false)
    expect(state.eventSessions.map(session => session.data)).toEqual(rows.slice(0, 2))
    await state.apply(); expect(wrapper.emitted('apply')).toBeUndefined()
  })
  it('cancels queries before closing and rejects late writes', async () => {
    const reply = deferred<Response>(); vi.stubGlobal('fetch', vi.fn(() => reply.promise))
    const { wrapper, state } = fixture(query)
    const changed = state.setValue('caption', 'pending')
    expect(await state.close()).toBe(true)
    reply.resolve(new Response('{"amount":42}')); expect(await changed).toBe(false)
    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(state.eventSessions.map(session => session.data)).toEqual(rows.slice(0, 2))
  })
})

describe('shared request operation', () => {
  it('restricts request origin, path, method, credentials and identity headers', () => {
    const operation = new FormEventOperation()
    for (const [url, options] of [
      ['https://example.test/api/users', {}], ['/other', {}], ['/api/users', { method: 'POST' }],
      ['/api/users', { credentials: 'include' }], ['/api/users', { headers: { Authorization: 'synthetic' } }],
    ] as Array<[string, RequestInit]>) expect(() => operation.fetch(url, options)).toThrow()
    operation.finish()
  })
  it('enforces a total deadline even for a script that never settles', async () => {
    vi.useFakeTimers()
    const operation = new FormEventOperation(25)
    const pending = operation.wait(new Promise(() => {}))
    const rejected = expect(pending).rejects.toMatchObject({ name: 'TimeoutError' })
    await vi.advanceTimersByTimeAsync(25); await rejected
    operation.finish()
  })
  it('bounds concurrent GETs and gives every deduplicated caller an independent response body', async () => {
    const replies: Array<ReturnType<typeof deferred<Response>>> = []
    const fetch = vi.fn(() => { const reply = deferred<Response>(); replies.push(reply); return reply.promise })
    vi.stubGlobal('fetch', fetch)
    const operation = new FormEventOperation()
    const promises = Array.from({ length: 6 }, (_, index) => operation.fetch('/api/users/' + index))
    const same = operation.fetch('/api/users/0')
    expect(fetch).toHaveBeenCalledTimes(4)
    replies[0]!.resolve(new Response('{"email":"same@example.test"}'))
    const first = await promises[0]!
    expect(await first.json()).toEqual({ email: 'same@example.test' })
    expect(await (await same).json()).toEqual({ email: 'same@example.test' })
    await Promise.resolve()
    expect(fetch).toHaveBeenCalledTimes(5)
    replies[1]!.resolve(new Response('{}')); await promises[1]!
    await Promise.resolve()
    expect(fetch).toHaveBeenCalledTimes(6)
    for (const reply of replies.slice(2)) reply.resolve(new Response('{}'))
    await Promise.all(promises)
    operation.finish()
  })
  it('invalidates all captured API references at the end of an action', () => {
    const operation = new FormEventOperation()
    const write = vi.fn()
    const api = operation.bindApi({ setValue: write })
    operation.finish()
    expect(() => api.setValue('target', 'late')).toThrow()
    expect(write).not.toHaveBeenCalled()
  })
})
