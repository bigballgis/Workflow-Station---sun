import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import api from '@/api/index'
import { readAmToken, refreshAmToken } from '@/utils/amToken'

vi.mock('@/utils/amToken', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/amToken')>()),
  readAmToken: vi.fn(() => 'stale-token'),
  refreshAmToken: vi.fn()
}))

vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), warning: vi.fn() } }))

/** Platform error body the DW backend returns for an AiGenerationException (HTTP 500). */
function gatewayError(config: InternalAxiosRequestConfig, code: string) {
  return Promise.reject(Object.assign(new Error('Request failed with status code 500'), {
    config,
    response: { status: 500, data: { success: false, error: { code, message: 'rejected' } }, config }
  }))
}

function ok(config: InternalAxiosRequestConfig) {
  return Promise.resolve({ status: 200, statusText: 'OK', headers: {}, config, data: { data: { reply: 'hi' } } })
}

describe('api AMToken retry', () => {
  const sentTokens: (string | undefined)[] = []
  let replies: ((config: InternalAxiosRequestConfig) => Promise<unknown>)[] = []
  const originalAdapter = api.defaults.adapter

  beforeEach(() => {
    sentTokens.length = 0
    api.defaults.adapter = ((config: InternalAxiosRequestConfig) => {
      sentTokens.push(config.headers['X-AM-Token'] as string | undefined)
      return replies.shift()!(config)
    }) as AxiosAdapter
  })

  afterEach(() => {
    api.defaults.adapter = originalAdapter
    vi.clearAllMocks()
  })

  it('replays the request once with the refreshed token after AI_GATEWAY_UNAUTHORIZED', async () => {
    vi.mocked(refreshAmToken).mockImplementation(async () => {
      vi.mocked(readAmToken).mockReturnValue('fresh-token')
      return true
    })
    replies = [c => gatewayError(c, 'AI_GATEWAY_UNAUTHORIZED'), ok]

    const res = await api.post('/ai-generation/studio-chat', {}, { headers: { 'X-AM-Token': 'stale-token' } })

    expect(res).toEqual({ data: { reply: 'hi' } })
    expect(sentTokens).toEqual(['stale-token', 'fresh-token'])
  })

  it('gives the caller the original error when no new token can be obtained', async () => {
    vi.mocked(refreshAmToken).mockResolvedValue(false)
    replies = [c => gatewayError(c, 'AI_GATEWAY_TOKEN_MISSING')]

    await expect(api.post('/ai-generation/hermes-master/chat', {}, { silentError: true }))
      .rejects.toMatchObject({ response: { data: { error: { code: 'AI_GATEWAY_TOKEN_MISSING' } } } })
    expect(sentTokens).toHaveLength(1)
  })

  it('refreshes at most once per request', async () => {
    vi.mocked(refreshAmToken).mockResolvedValue(true)
    replies = [c => gatewayError(c, 'AI_GATEWAY_UNAUTHORIZED'), c => gatewayError(c, 'AI_GATEWAY_UNAUTHORIZED')]

    await expect(api.post('/ai-generation/studio-chat', {}, { silentError: true })).rejects.toBeTruthy()
    expect(refreshAmToken).toHaveBeenCalledTimes(1)
    expect(sentTokens).toHaveLength(2)
  })

  it('ignores unrelated errors', async () => {
    replies = [c => gatewayError(c, 'AI_WEBHOOK_TIMEOUT')]

    await expect(api.post('/ai-generation/studio-chat', {}, { silentError: true })).rejects.toBeTruthy()
    expect(refreshAmToken).not.toHaveBeenCalled()
  })
})
