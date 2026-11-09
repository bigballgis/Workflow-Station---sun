import { afterEach, describe, expect, it, vi } from 'vitest'
import { queuedUploadRequest, resolveQueuedUploadOptions } from '../queuedUploadRequest'
import { UPLOAD_HTTP_UNAUTHORIZED } from '../uploadAuthRefresh'
import { setSessionRefresher } from '../../auth/sessionRefresh'

/** Minimal XHR double: each send() answers with the next queued status. */
class FakeXhr {
  static statuses: number[] = []
  static sends = 0
  status = 0
  responseText = '{"ok":true}'
  withCredentials = false
  upload: { onprogress: ((evt: ProgressEvent) => void) | null } = { onprogress: null }
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  onabort: (() => void) | null = null
  open(): void {}
  setRequestHeader(): void {}
  abort(): void {
    this.onabort?.()
  }
  send(): void {
    FakeXhr.sends++
    this.status = FakeXhr.statuses.shift() ?? 200
    setTimeout(() => this.onload?.(), 0)
  }
}

function uploadWithStatuses(statuses: number[], refreshResult: boolean) {
  FakeXhr.statuses = [...statuses]
  FakeXhr.sends = 0
  vi.stubGlobal('XMLHttpRequest', FakeXhr)
  const refresher = vi.fn(async () => {
    if (!refreshResult) throw new Error('refresh failed')
  })
  setSessionRefresher(refresher)
  const onSuccess = vi.fn()
  const onError = vi.fn()
  queuedUploadRequest({
    action: '/api/v1/upload',
    file: new File(['a'], 'a.txt', { type: 'text/plain' }),
    onSuccess,
    onError,
  })
  return { refresher, onSuccess, onError }
}

describe('queuedUploadRequest 401 handling', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    setSessionRefresher(null)
  })

  it('renews the session once and resends the same file after a 401', async () => {
    const { refresher, onSuccess, onError } = uploadWithStatuses([401, 200], true)
    await vi.waitFor(() => expect(onSuccess).toHaveBeenCalledWith({ ok: true }))
    expect(refresher).toHaveBeenCalledTimes(1)
    expect(FakeXhr.sends).toBe(2)
    expect(onError).not.toHaveBeenCalled()
  })

  it('reports UPLOAD_HTTP_401 without resending when renewal fails', async () => {
    const { onSuccess, onError } = uploadWithStatuses([401], false)
    await vi.waitFor(() => expect(onError).toHaveBeenCalled())
    expect((onError.mock.calls[0][0] as Error).message).toBe(UPLOAD_HTTP_UNAUTHORIZED)
    expect(FakeXhr.sends).toBe(1)
    expect(onSuccess).not.toHaveBeenCalled()
  })

  it('reports UPLOAD_HTTP_401 when the resend is still unauthorized, renewing only once', async () => {
    const { refresher, onSuccess, onError } = uploadWithStatuses([401, 401], true)
    await vi.waitFor(() => expect(onError).toHaveBeenCalled())
    expect((onError.mock.calls[0][0] as Error).message).toBe(UPLOAD_HTTP_UNAUTHORIZED)
    expect(refresher).toHaveBeenCalledTimes(1)
    expect(FakeXhr.sends).toBe(2)
    expect(onSuccess).not.toHaveBeenCalled()
  })
})

describe('queuedUploadRequest abort', () => {
  it('abort before send never calls onSuccess', async () => {
    const onSuccess = vi.fn()
    const onError = vi.fn()
    const xhr = queuedUploadRequest({
      action: 'http://127.0.0.1:1/does-not-exist',
      file: new File(['a'], 'a.txt', { type: 'text/plain' }),
      onSuccess,
      onError,
    })
    expect(xhr).toBeInstanceOf(XMLHttpRequest)
    xhr.abort()
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(onSuccess).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalled()
  })
})

describe('resolveQueuedUploadOptions', () => {
  it('unwraps form-create inject args so the File is still posted', () => {
    const file = new File(['a'], 'a.txt', { type: 'text/plain' })
    const inner = {
      action: '/api/v1/upload',
      file,
      onSuccess() {},
      onError() {},
    }
    expect(resolveQueuedUploadOptions({ args: [inner] })?.file).toBe(file)
  })

  it('does not throw when form-create calls httpRequest without a File', () => {
    const onError = vi.fn()
    const xhr = queuedUploadRequest({ args: [{}], onError } as never)
    expect(xhr).toBeInstanceOf(XMLHttpRequest)
    expect(onError).toHaveBeenCalled()
  })
})

describe('queuedUploadRequest abort', () => {
  it('abort before send never calls onSuccess', async () => {
    const onSuccess = vi.fn()
    const onError = vi.fn()
    const xhr = queuedUploadRequest({
      action: 'http://127.0.0.1:1/does-not-exist',
      file: new File(['a'], 'a.txt', { type: 'text/plain' }),
      onSuccess,
      onError,
    })
    expect(xhr).toBeInstanceOf(XMLHttpRequest)
    xhr.abort()
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(onSuccess).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalled()
  })
})
