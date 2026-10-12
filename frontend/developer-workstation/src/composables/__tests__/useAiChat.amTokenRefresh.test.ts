import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useAiChat } from '@/composables/useAiChat'
import { createMockSSEStream, mockRequest } from './useAiChat.testHelpers'
import { readAmToken, refreshAmToken } from '@/utils/amToken'

vi.mock('@/api/aiGeneration', () => ({
  aiGenerationApi: {},
  AI_CHAT_STREAM_URL: '/api/v1/ai-generation/chat/stream',
  AI_EVENT_STREAM_URL: vi.fn((id: number) => `/api/v1/ai-generation/events/${id}`)
}))

vi.mock('@/api/auth', () => ({
  getUser: vi.fn(() => ({ userId: 'test-user' })),
  TOKEN_KEY: 'auth_token'
}))

vi.mock('@/utils/amToken', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/amToken')>()),
  readAmToken: vi.fn(() => 'stale-token'),
  refreshAmToken: vi.fn()
}))

const REJECTED = 'event:error\ndata:{"errorCode":"AI_GATEWAY_UNAUTHORIZED","message":"rejected"}\n\n'
const ANSWER = ['event:token\ndata:Hi\n\n', 'event:done\ndata:{}\n\n']

function stream(events: string[]) {
  return { ok: true, body: createMockSSEStream(events) }
}

describe('useAiChat AMToken silent refresh', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('refreshes the token and replays the same message once when the gateway rejects it', async () => {
    vi.mocked(refreshAmToken).mockImplementation(async () => {
      vi.mocked(readAmToken).mockReturnValue('fresh-token')
      return true
    })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(stream([REJECTED, 'event:done\ndata:{}\n\n']))
      .mockResolvedValueOnce(stream(ANSWER))
    vi.stubGlobal('fetch', fetchMock)

    const { sendMessage, messages, error, errorCode, isStreaming } = useAiChat()
    await sendMessage(mockRequest)

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[0][1].headers['X-AM-Token']).toBe('stale-token')
    expect(fetchMock.mock.calls[1][1].headers['X-AM-Token']).toBe('fresh-token')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual(mockRequest)
    expect(error.value).toBeNull()
    expect(errorCode.value).toBeNull()
    expect(isStreaming.value).toBe(false)
    // the question appears once, followed by the answer of the replay
    expect(messages.value.map(m => [m.role, m.content])).toEqual([['USER', 'Hello AI'], ['ASSISTANT', 'Hi']])
  })

  it('surfaces the original error (sign in again) when no new token can be obtained', async () => {
    vi.mocked(refreshAmToken).mockResolvedValue(false)
    const fetchMock = vi.fn().mockResolvedValue(stream([REJECTED]))
    vi.stubGlobal('fetch', fetchMock)

    const { sendMessage, error, errorCode, isStreaming } = useAiChat()
    await sendMessage(mockRequest)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(errorCode.value).toBe('AI_GATEWAY_UNAUTHORIZED')
    expect(error.value).toBe('rejected')
    expect(isStreaming.value).toBe(false)
  })

  it('does not refresh a second time when the replay is rejected too', async () => {
    vi.mocked(refreshAmToken).mockResolvedValue(true)
    const fetchMock = vi.fn().mockResolvedValue(stream([REJECTED]))
    fetchMock.mockImplementation(() => Promise.resolve(stream([REJECTED])))
    vi.stubGlobal('fetch', fetchMock)

    const { sendMessage, errorCode } = useAiChat()
    await sendMessage(mockRequest)

    expect(refreshAmToken).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(errorCode.value).toBe('AI_GATEWAY_UNAUTHORIZED')
  })

  it('leaves other stream errors alone', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      stream(['event:error\ndata:{"errorCode":"AI_WEBHOOK_TIMEOUT","message":"slow"}\n\n']))
    vi.stubGlobal('fetch', fetchMock)

    const { sendMessage, errorCode } = useAiChat()
    await sendMessage(mockRequest)

    expect(refreshAmToken).not.toHaveBeenCalled()
    expect(errorCode.value).toBe('AI_WEBHOOK_TIMEOUT')
  })
})
