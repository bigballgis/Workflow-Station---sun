import axios, { type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  SESSION_REFRESH_LOCK,
  fetchWithSessionRefresh,
  installSessionRefresh,
  refreshSession,
  setSessionRefresher,
  wasRetriedAfterSessionRefresh,
} from '@platform-shared/auth/sessionRefresh'

/** Adapter double: answers each request with the next queued status for its URL. */
function adapterWith(statusesByUrl: Record<string, number[]>): { adapter: AxiosAdapter; calls: string[] } {
  const calls: string[] = []
  const adapter: AxiosAdapter = async (config) => {
    const url = `${config.baseURL ?? ''}${config.url ?? ''}`
    calls.push(url)
    const status = statusesByUrl[url]?.shift() ?? 200
    const response: AxiosResponse = {
      data: { ok: true, url },
      status,
      statusText: String(status),
      headers: {},
      config,
    }
    if (status >= 400) {
      throw new axios.AxiosError(`HTTP ${status}`, String(status), config, null, response)
    }
    return response
  }
  return { adapter, calls }
}

/** An instance shaped like the apps' ones: refresh first, then the instance's own success transform. */
function appInstance(adapter: AxiosAdapter, baseURL = '/api/v1') {
  const instance = axios.create({ baseURL, adapter })
  installSessionRefresh(instance, (config: InternalAxiosRequestConfig) => axios.request({ ...config, adapter }))
  const ownErrors: unknown[] = []
  instance.interceptors.response.use(
    (response) => response.data,
    (error) => {
      ownErrors.push(error)
      return Promise.reject(error)
    },
  )
  return { instance, ownErrors }
}

describe('installSessionRefresh', () => {
  afterEach(() => setSessionRefresher(null))

  it('renews once and replays a 401; the success transform runs exactly once', async () => {
    const { adapter, calls } = adapterWith({ '/api/v1/items': [401, 200] })
    const refresher = vi.fn(async () => undefined)
    setSessionRefresher(refresher)
    const { instance, ownErrors } = appInstance(adapter)

    await expect(instance.get('/items')).resolves.toEqual({ ok: true, url: '/api/v1/items' })
    expect(refresher).toHaveBeenCalledTimes(1)
    expect(calls).toEqual(['/api/v1/items', '/api/v1/items'])
    expect(ownErrors).toHaveLength(0)
  })

  it('shares one refresh between concurrent 401s (refresh tokens rotate)', async () => {
    const { adapter } = adapterWith({ '/api/v1/a': [401, 200], '/api/v1/b': [401, 200] })
    let release!: () => void
    const refresher = vi.fn(() => new Promise<void>((resolve) => { release = resolve }))
    setSessionRefresher(refresher)
    const { instance } = appInstance(adapter)

    const both = Promise.all([instance.get('/a'), instance.get('/b')])
    await vi.waitFor(() => expect(refresher).toHaveBeenCalled())
    release()
    await expect(both).resolves.toHaveLength(2)
    expect(refresher).toHaveBeenCalledTimes(1)
  })

  it('hands the original 401 to the instance handlers when the refresh fails', async () => {
    const { adapter, calls } = adapterWith({ '/api/v1/items': [401] })
    setSessionRefresher(async () => {
      throw new Error('refresh token expired')
    })
    const { instance, ownErrors } = appInstance(adapter)

    await expect(instance.get('/items')).rejects.toMatchObject({ response: { status: 401 } })
    expect(calls).toEqual(['/api/v1/items'])
    expect(wasRetriedAfterSessionRefresh((ownErrors[0] as { config: unknown }).config)).toBe(false)
  })

  it('marks a replay that is still 401 so the instance does not treat it as a lost session', async () => {
    const { adapter } = adapterWith({ '/api/v1/items': [401, 401] })
    const refresher = vi.fn(async () => undefined)
    setSessionRefresher(refresher)
    const { instance, ownErrors } = appInstance(adapter)

    await expect(instance.get('/items')).rejects.toMatchObject({ response: { status: 401 } })
    expect(refresher).toHaveBeenCalledTimes(1)
    expect(wasRetriedAfterSessionRefresh((ownErrors[0] as { config: unknown }).config)).toBe(true)
  })

  it('never refreshes for the auth endpoints themselves', async () => {
    const { adapter } = adapterWith({ '/api/v1/auth/refresh': [401], '/api/v1/auth/login': [401] })
    const refresher = vi.fn(async () => undefined)
    setSessionRefresher(refresher)
    const { instance } = appInstance(adapter, '/api/v1/auth')

    await expect(instance.post('/refresh')).rejects.toMatchObject({ response: { status: 401 } })
    await expect(instance.post('/login')).rejects.toMatchObject({ response: { status: 401 } })
    expect(refresher).not.toHaveBeenCalled()
  })

  it('leaves non-401 errors alone', async () => {
    const { adapter } = adapterWith({ '/api/v1/items': [403] })
    const refresher = vi.fn(async () => undefined)
    setSessionRefresher(refresher)
    const { instance } = appInstance(adapter)

    await expect(instance.get('/items')).rejects.toMatchObject({ response: { status: 403 } })
    expect(refresher).not.toHaveBeenCalled()
  })
})

describe('refreshSession across tabs', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    setSessionRefresher(null)
  })

  it('renews while holding the shared Web Lock, so another tab waits for the rotated cookie', async () => {
    let holding = false
    const request = vi.fn(async (_name: string, callback: () => Promise<unknown>) => {
      holding = true
      try {
        return await callback()
      } finally {
        holding = false
      }
    })
    vi.stubGlobal('navigator', { locks: { request } })
    const heldDuringRefresh: boolean[] = []
    setSessionRefresher(async () => {
      heldDuringRefresh.push(holding)
    })

    await expect(refreshSession()).resolves.toBe(true)

    expect(request).toHaveBeenCalledTimes(1)
    expect(request.mock.calls[0][0]).toBe(SESSION_REFRESH_LOCK)
    expect(heldDuringRefresh).toEqual([true])
  })

  it('reports a failed refresh made under the lock', async () => {
    vi.stubGlobal('navigator', {
      locks: { request: async (_name: string, callback: () => Promise<unknown>) => callback() },
    })
    setSessionRefresher(async () => {
      throw new Error('refresh token already rotated')
    })

    await expect(refreshSession()).resolves.toBe(false)
  })
})

describe('fetchWithSessionRefresh', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    setSessionRefresher(null)
  })

  function stubFetch(statuses: number[]) {
    const fetchMock = vi.fn(async () => new Response('{}', { status: statuses.shift() ?? 200 }))
    vi.stubGlobal('fetch', fetchMock)
    return fetchMock
  }

  it('renews the session once and sends the request again after a 401', async () => {
    const fetchMock = stubFetch([401, 200])
    const refresher = vi.fn(async () => undefined)
    setSessionRefresher(refresher)

    const response = await fetchWithSessionRefresh('/api/v1/files/a', { credentials: 'include' })

    expect(response.status).toBe(200)
    expect(refresher).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1]).toEqual(['/api/v1/files/a', { credentials: 'include' }])
  })

  it('returns the 401 as is when the refresh fails', async () => {
    const fetchMock = stubFetch([401])
    setSessionRefresher(async () => {
      throw new Error('refresh token expired')
    })

    const response = await fetchWithSessionRefresh('/api/v1/files/a')

    expect(response.status).toBe(401)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('does not refresh on other statuses', async () => {
    const fetchMock = stubFetch([404])
    const refresher = vi.fn(async () => undefined)
    setSessionRefresher(refresher)

    expect((await fetchWithSessionRefresh('/api/v1/files/a')).status).toBe(404)
    expect(refresher).not.toHaveBeenCalled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
