import { isEventPromise } from './async'

/** One user action owns requests and script API references, including after await. */
export class FormEventOperation {
  private readonly controller = new AbortController()
  private timer?: ReturnType<typeof setTimeout>
  private finished = false
  private requests = 0
  private running = 0
  private readonly queue: Array<{ start: () => void; reject: (error: unknown) => void }> = []
  private readonly responses = new Map<string, Promise<Response>>()
  private readonly effects: Array<() => void> = []

  constructor(private readonly timeoutMs = 15000, private readonly concurrency = 4) {}

  get signal() { return this.controller.signal }
  assertActive() {
    if (this.signal.aborted) throw this.signal.reason
    if (this.finished) throw new DOMException('Event operation has ended', 'AbortError')
  }
  cancel(reason: unknown = new DOMException('Event operation cancelled', 'AbortError')) {
    if (this.finished || this.signal.aborted) return
    this.controller.abort(reason)
    for (const queued of this.queue.splice(0)) queued.reject(reason)
    if (this.timer) clearTimeout(this.timer)
  }
  finish() {
    if (this.finished) return
    try { if (!this.signal.aborted) for (const effect of this.effects.splice(0)) effect() }
    finally {
      this.effects.length = 0
      this.responses.clear()
      this.finished = true
      if (this.timer) clearTimeout(this.timer)
      // Completed scripts cannot leave fire-and-forget requests running.
      this.controller.abort(new DOMException('Event operation has ended', 'AbortError'))
    }
  }
  private startDeadline() {
    this.assertActive()
    this.timer ??= setTimeout(() => this.cancel(new DOMException('Event operation timed out', 'TimeoutError')), this.timeoutMs)
  }
  wait<T>(value: PromiseLike<T>): Promise<T> {
    this.startDeadline()
    return new Promise<T>((resolve, reject) => {
      const abort = () => reject(this.signal.reason)
      this.signal.addEventListener('abort', abort, { once: true })
      Promise.resolve(value).then(result => { this.assertActive(); resolve(result) }, reject)
        .catch(reject).finally(() => this.signal.removeEventListener('abort', abort))
    })
  }
  bindApi<T extends object>(api: T): T {
    if (operationApis.get(api) === this) return api
    const scoped = new Proxy(api, { get: (target, key) => {
      this.assertActive()
      const value = Reflect.get(target, key)
      return typeof value === 'function' ? (...args: unknown[]) => {
        this.assertActive()
        if (key === 'setFormNotification' || key === 'clearFormNotification' || key === 'setFocus') {
          this.effects.push(() => Reflect.apply(value, target, args))
          return
        }
        return Reflect.apply(value, target, args)
      } : value
    } })
    operationApis.set(scoped, this)
    return scoped
  }
  invoke(action: () => unknown): unknown {
    this.assertActive()
    const before = this.requests
    const result = action()
    if (this.requests !== before && !isEventPromise(result)) {
      this.cancel(new Error('Event scripts must return the request Promise'))
      throw this.signal.reason
    }
    return isEventPromise(result) ? this.wait(result) : result
  }
  readonly fetch = (input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> => {
    this.startDeadline()
    this.requests++
    if (typeof input !== 'string' && !(input instanceof URL)) throw new Error('Event fetch accepts a URL string')
    const url = new URL(String(input), globalThis.location.origin)
    if (url.origin !== globalThis.location.origin || !url.pathname.startsWith('/api/') || url.username || url.password
      || (init.method ?? 'GET').toUpperCase() !== 'GET' || init.body != null
      || (init.credentials != null && init.credentials !== 'same-origin')
      || (init.redirect != null && init.redirect !== 'error')) throw new Error('Event fetch permits same-origin platform GET requests only')
    const headers = new Headers(init.headers)
    for (const name of headers.keys()) if (!['accept', 'accept-language'].includes(name)) throw new Error(`Unsupported event request header: ${name}`)
    const key = JSON.stringify([url.href, [...headers.entries()].sort()])
    let response = this.responses.get(key)
    if (!response) {
      response = new Promise<Response>((resolve, reject) => {
        const start = () => {
          this.running++
          globalThis.fetch(url.href, { method: 'GET', headers, credentials: 'same-origin', redirect: 'error', signal: this.signal })
            .then(async value => {
              if (!value.ok) throw new Error(`Event request failed: HTTP ${value.status}`)
              // Keep the concurrency slot until the body arrives; each consumer then
              // receives its own clone without opening another streaming request.
              await value.clone().arrayBuffer()
              resolve(value)
            }, reject).catch(reject).finally(() => {
              this.running--
              if (!this.signal.aborted) this.queue.shift()?.start()
            })
        }
        if (this.running < this.concurrency) start()
        else this.queue.push({ start, reject })
      })
      this.responses.set(key, response)
    }
    const result = this.wait(response).then(value => { this.assertActive(); return value.clone() })
    // Cancellation also covers scripts that illegally discard their returned request.
    void result.catch(() => {})
    // A caller's abort invalidates this atomic operation, including any shared GET.
    if (init.signal) {
      const signal = init.signal
      const abort = () => this.cancel(signal.reason)
      if (signal.aborted) abort()
      else {
        signal.addEventListener('abort', abort, { once: true })
        void result.finally(() => signal.removeEventListener('abort', abort)).catch(() => {})
      }
    }
    return result
  }
}

const operationApis = new WeakMap<object, FormEventOperation>()
export function operationForApi(api: object) { return operationApis.get(api) }
type ApiHost = (field: string, value: unknown, action: (operation: FormEventOperation) => unknown) => unknown
const apiHosts = new WeakMap<object, ApiHost>()
export function registerEventApiHost(api: object, host: ApiHost) { apiHosts.set(api, host) }
export function hostForApi(api: object) { return apiHosts.get(api) }

export function inFormEventOperation(action: (operation: FormEventOperation) => unknown): unknown {
  const operation = new FormEventOperation()
  try {
    const result = action(operation)
    if (isEventPromise(result)) return operation.wait(result).catch(error => {
      operation.cancel(error)
      throw error
    }).finally(() => operation.finish())
    operation.finish()
    return result
  } catch (error) {
    operation.cancel(error)
    operation.finish()
    throw error
  }
}
