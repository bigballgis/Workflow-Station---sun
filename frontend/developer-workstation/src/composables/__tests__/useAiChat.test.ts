import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useAiChat } from '@/composables/useAiChat'
import type { AiChatRequest } from '@/types/aiGeneration'

vi.mock('@/api/aiGeneration', () => ({
  aiGenerationApi: {},
  AI_CHAT_STREAM_URL: '/api/v1/ai-generation/chat/stream',
  AI_EVENT_STREAM_URL: vi.fn((id: number) => `/api/v1/ai-generation/events/${id}`)
}))

vi.mock('@/api/auth', () => ({
  getUser: vi.fn(() => ({ userId: 'test-user' })),
  TOKEN_KEY: 'auth_token'
}))

function createMockSSEStream(events: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  return new ReadableStream({
    start(controller) {
      for (const event of events) {
        controller.enqueue(encoder.encode(event))
      }
      controller.close()
    }
  })
}

describe('useAiChat', () => {
  const mockRequest: AiChatRequest = {
    functionUnitId: 1,
    sessionId: 'session-1',
    message: 'Hello AI',
    phase: 'REQUIREMENTS',
    mode: 'NEW'
  }

  beforeEach(() => {
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => 'mock-token'),
      setItem: vi.fn(),
      removeItem: vi.fn()
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('should initialize with default state', () => {
    const { messages, isStreaming, streamingContent, error, canRetry } = useAiChat()

    expect(messages.value).toEqual([])
    expect(isStreaming.value).toBe(false)
    expect(streamingContent.value).toBe('')
    expect(error.value).toBeNull()
    expect(canRetry.value).toBe(false)
  })

  it('sendMessage should add user message to list', async () => {
    const sseEvents = [
      'event:token\ndata:Hi\n\n',
      'event:done\ndata:{}\n\n'
    ]
    const mockResponse = {
      ok: true,
      body: createMockSSEStream(sseEvents)
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse))

    const { messages, sendMessage } = useAiChat()
    await sendMessage(mockRequest)

    // First message is the user message, second is the AI response
    expect(messages.value.length).toBe(2)
    expect(messages.value[0].role).toBe('USER')
    expect(messages.value[0].content).toBe('Hello AI')
    expect(messages.value[0].phase).toBe('REQUIREMENTS')
  })

  it('sendMessage should handle streaming tokens and finalize', async () => {
    const sseEvents = [
      'event:token\ndata:Hello\n\n',
      'event:token\ndata:World\n\n',
      'event:done\ndata:{}\n\n'
    ]
    const mockResponse = {
      ok: true,
      body: createMockSSEStream(sseEvents)
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse))

    const { messages, sendMessage } = useAiChat()
    await sendMessage(mockRequest)

    // After stream completes, AI message should be added
    const aiMessage = messages.value.find(m => m.role === 'ASSISTANT')
    expect(aiMessage).toBeDefined()
    expect(aiMessage!.content).toBe('HelloWorld')
  })

  it('sendMessage should set isStreaming during request', async () => {
    let resolveStream: (() => void) | undefined
    const streamPromise = new Promise<void>(resolve => { resolveStream = resolve })

    const mockResponse = {
      ok: true,
      body: new ReadableStream({
        async start(controller) {
          const encoder = new TextEncoder()
          controller.enqueue(encoder.encode('event:token\ndata:test\n\n'))
          await streamPromise
          controller.enqueue(encoder.encode('event:done\ndata:{}\n\n'))
          controller.close()
        }
      })
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse))

    const { isStreaming, sendMessage } = useAiChat()
    const promise = sendMessage(mockRequest)

    // isStreaming should be true while streaming
    expect(isStreaming.value).toBe(true)

    resolveStream!()
    await promise

    expect(isStreaming.value).toBe(false)
  })

  it('sendMessage should handle HTTP errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error'
    }))

    const { error, canRetry, sendMessage } = useAiChat()
    await sendMessage(mockRequest)

    expect(error.value).toBe('HTTP 500: Internal Server Error')
    expect(canRetry.value).toBe(true)
  })

  it('sendMessage should handle SSE error events', async () => {
    const sseEvents = [
      'event:error\ndata:Something went wrong\n\n'
    ]
    const mockResponse = {
      ok: true,
      body: createMockSSEStream(sseEvents)
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse))

    const { error, canRetry, sendMessage } = useAiChat()
    await sendMessage(mockRequest)

    expect(error.value).toBe('Something went wrong')
    expect(canRetry.value).toBe(true)
  })

  it('retry should remove last user message and resend', async () => {
    // First call fails
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Server Error'
    }))

    const { messages, retry, sendMessage } = useAiChat()
    await sendMessage(mockRequest)

    expect(messages.value.length).toBe(1)
    expect(messages.value[0].role).toBe('USER')

    // Now retry with success
    const sseEvents = [
      'event:token\ndata:Retried\n\n',
      'event:done\ndata:{}\n\n'
    ]
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      body: createMockSSEStream(sseEvents)
    }))

    // retry() calls sendMessage internally but doesn't return the promise
    // We need to wait for the async operation to complete
    retry()
    // Wait for the fetch + stream processing to complete
    await new Promise(r => setTimeout(r, 50))

    // The old user message should be removed and a new one added, plus AI response
    expect(messages.value.length).toBe(2)
    expect(messages.value[0].role).toBe('USER')
    expect(messages.value[1].role).toBe('ASSISTANT')
    expect(messages.value[1].content).toBe('Retried')
  })

  it('cancel should abort the request', async () => {
    const abortSpy = vi.fn()
    const originalAbortController = globalThis.AbortController
    vi.stubGlobal('AbortController', class {
      signal = { aborted: false }
      abort = abortSpy
    })

    vi.stubGlobal('fetch', vi.fn().mockImplementation(() =>
      new Promise(() => {}) // Never resolves
    ))

    const { cancel, sendMessage, isStreaming } = useAiChat()
    sendMessage(mockRequest) // Don't await — it won't resolve

    // Give it a tick to start
    await new Promise(r => setTimeout(r, 0))

    cancel()

    expect(abortSpy).toHaveBeenCalled()
    expect(isStreaming.value).toBe(false)

    vi.stubGlobal('AbortController', originalAbortController)
  })

  it('setMessages should replace the message list', () => {
    const { messages, setMessages } = useAiChat()

    setMessages([
      { id: 1, sessionId: 's1', role: 'USER', content: 'Hi', phase: 'REQUIREMENTS', createdAt: '' },
      { id: 2, sessionId: 's1', role: 'ASSISTANT', content: 'Hello', phase: 'REQUIREMENTS', createdAt: '' }
    ])

    expect(messages.value.length).toBe(2)
    expect(messages.value[0].content).toBe('Hi')
    expect(messages.value[1].content).toBe('Hello')
  })

  it('should invoke onPhaseComplete callback on phase_complete event', async () => {
    const sseEvents = [
      'event:phase_complete\ndata:{"phase":"REQUIREMENTS"}\n\n',
      'event:done\ndata:{}\n\n'
    ]
    const mockResponse = {
      ok: true,
      body: createMockSSEStream(sseEvents)
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse))

    const { sendMessage, onPhaseComplete } = useAiChat()
    const callback = vi.fn()
    onPhaseComplete(callback)

    await sendMessage(mockRequest)

    expect(callback).toHaveBeenCalledWith('REQUIREMENTS')
  })

  it('should invoke onGeneratedData callback on generated_data event', async () => {
    const generatedData = { tableDefinitions: [], formDefinitions: [] }
    const sseEvents = [
      `event:generated_data\ndata:${JSON.stringify(generatedData)}\n\n`,
      'event:done\ndata:{}\n\n'
    ]
    const mockResponse = {
      ok: true,
      body: createMockSSEStream(sseEvents)
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse))

    const { sendMessage, onGeneratedData } = useAiChat()
    const callback = vi.fn()
    onGeneratedData(callback)

    await sendMessage(mockRequest)

    expect(callback).toHaveBeenCalledWith(generatedData)
  })

  it('should invoke onDocument callback on document event', async () => {
    const docData = { documentType: 'REQUIREMENTS', content: '# Requirements' }
    const sseEvents = [
      `event:document\ndata:${JSON.stringify(docData)}\n\n`,
      'event:done\ndata:{}\n\n'
    ]
    const mockResponse = {
      ok: true,
      body: createMockSSEStream(sseEvents)
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse))

    const { sendMessage, onDocument } = useAiChat()
    const callback = vi.fn()
    onDocument(callback)

    await sendMessage(mockRequest)

    expect(callback).toHaveBeenCalledWith('REQUIREMENTS', '# Requirements')
  })
})
