import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { isAmTokenReauthCode, readAmToken, refreshAmToken, resetAmTokenForTest } from '@/utils/amToken'

const DSP_URL = 'https://dsp.example/dsp/dspAuthenticate.jsp?realm=staff&service=wdssoservice'

function dspResponds(body: unknown, init: { ok?: boolean; status?: number } = {}) {
  return vi.fn().mockResolvedValue({
    ok: init.ok ?? true,
    status: init.status ?? 200,
    json: () => Promise.resolve(body)
  })
}

describe('amToken silent refresh', () => {
  beforeEach(() => {
    resetAmTokenForTest()
    document.cookie = 'AMToken=stale-token'
    window.__DW_RUNTIME_CONFIG__ = {
      VITE_DSP_AUTHENTICATE_URL: DSP_URL,
      VITE_DSP_CLIENT_ID: 'HERMES',
      VITE_DSP_CLIENT_SECRET: 'client-secret',
      VITE_DSP_ACCEPT_API_VERSION: 'protocol=1.0,resource=1.0'
    }
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(() => {
    document.cookie = 'AMToken=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    delete window.__DW_RUNTIME_CONFIG__
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('recognises exactly the two gateway credential codes', () => {
    expect(isAmTokenReauthCode('AI_GATEWAY_UNAUTHORIZED')).toBe(true)
    expect(isAmTokenReauthCode('AI_GATEWAY_TOKEN_MISSING')).toBe(true)
    expect(isAmTokenReauthCode('AI_WEBHOOK_TIMEOUT')).toBe(false)
    expect(isAmTokenReauthCode(null)).toBe(false)
  })

  it('posts to the configured DSP endpoint like the login page and keeps the new token in memory', async () => {
    const fetchMock = dspResponds({ tokenId: 'fresh-token' })
    vi.stubGlobal('fetch', fetchMock)
    expect(readAmToken()).toBe('stale-token')

    await expect(refreshAmToken()).resolves.toBe(true)

    expect(readAmToken()).toBe('fresh-token')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(DSP_URL)
    expect(init).toMatchObject({ method: 'POST', credentials: 'include' })
    expect(init.headers).toEqual({
      'X-Requested-With': 'XMLHttpRequest',
      'Accept-API-Version': 'protocol=1.0,resource=1.0',
      'X-Client-Id': 'HERMES',
      'X-Client-Secret': 'client-secret'
    })
  })

  it('does not refresh when the environment has no DSP endpoint configured', async () => {
    window.__DW_RUNTIME_CONFIG__ = {}
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(refreshAmToken()).resolves.toBe(false)
    expect(fetchMock).not.toHaveBeenCalled()
    expect(readAmToken()).toBe('stale-token')
  })

  it('reports failure when DSP hands back the very token that was rejected', async () => {
    vi.stubGlobal('fetch', dspResponds({ tokenId: 'stale-token' }))
    await expect(refreshAmToken()).resolves.toBe(false)
  })

  it('reports failure on a non-2xx answer, a network/CORS error, or a body without a token', async () => {
    vi.stubGlobal('fetch', dspResponds({}, { ok: false, status: 401 }))
    await expect(refreshAmToken()).resolves.toBe(false)

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(refreshAmToken()).resolves.toBe(false)

    document.cookie = 'AMToken=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    vi.stubGlobal('fetch', dspResponds({}))
    await expect(refreshAmToken()).resolves.toBe(false)
    expect(readAmToken()).toBe('')
  })

  it('reuses a token another request already renewed instead of asking DSP again', async () => {
    const fetchMock = dspResponds({ tokenId: 'fresh-token' })
    vi.stubGlobal('fetch', fetchMock)
    await refreshAmToken('stale-token')

    // a request that left with the stale token before the renewal finishes later and is rejected
    await expect(refreshAmToken('stale-token')).resolves.toBe(true)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(readAmToken()).toBe('fresh-token')
  })

  it('collapses concurrent refreshes into one DSP call', async () => {
    const fetchMock = dspResponds({ tokenId: 'fresh-token' })
    vi.stubGlobal('fetch', fetchMock)

    const results = await Promise.all([refreshAmToken(), refreshAmToken(), refreshAmToken()])

    expect(results).toEqual([true, true, true])
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
