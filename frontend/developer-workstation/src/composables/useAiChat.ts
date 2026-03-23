import { ref } from 'vue'
import type { AiMessage, AiChatRequest, AiPhase } from '@/types/aiGeneration'
import { AI_CHAT_STREAM_URL } from '@/api/aiGeneration'
import { getUser, TOKEN_KEY } from '@/api/auth'

/**
 * Composable for managing AI chat SSE streaming.
 * Uses fetch API + ReadableStream to parse POST SSE event stream
 * (EventSource only supports GET).
 */
export function useAiChat() {
  const messages = ref<AiMessage[]>([])
  const isStreaming = ref(false)
  const streamingContent = ref('')
  const error = ref<string | null>(null)
  const canRetry = ref(false)

  let lastRequest: AiChatRequest | null = null
  let abortController: AbortController | null = null

  // Event callbacks
  let onDocumentCallback: ((type: string, content: string) => void) | null = null
  let onPhaseCompleteCallback: ((phase: AiPhase) => void) | null = null
  let onGeneratedDataCallback: ((data: any) => void) | null = null

  function getAuthHeaders(): Record<string, string> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json'
    }
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    const user = getUser()
    if (user?.userId) {
      headers['X-User-Id'] = user.userId
    }
    return headers
  }

  async function sendMessage(request: AiChatRequest): Promise<void> {
    // 防止重复发送（如果已经在 streaming 中，忽略新请求）
    if (isStreaming.value) {
      console.warn('sendMessage ignored: already streaming')
      return
    }

    lastRequest = request
    error.value = null
    canRetry.value = false

    const isAutoTrigger = request.message.startsWith('[AUTO_TRIGGER]')

    // Add user message to local list (skip for auto-trigger messages)
    if (!isAutoTrigger) {
      const userMessage: AiMessage = {
        id: Date.now(),
        sessionId: request.sessionId || '',
        role: 'USER',
        content: request.message,
        phase: request.phase,
        createdAt: new Date().toISOString()
      }
      messages.value.push(userMessage)
    }

    isStreaming.value = true
    streamingContent.value = ''

    abortController = new AbortController()

    try {
      const response = await fetch(AI_CHAT_STREAM_URL, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify(request),
        signal: abortController.signal
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('Response body is not readable')
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const events = buffer.split('\n\n')
        // Keep the last incomplete chunk in buffer
        buffer = events.pop() || ''

        for (const eventBlock of events) {
          if (!eventBlock.trim()) continue
          processSSEEvent(eventBlock)
        }
      }

      // Process any remaining buffer
      if (buffer.trim()) {
        processSSEEvent(buffer)
      }

      finalizeStream()
    } catch (err: any) {
      if (err.name === 'AbortError') return
      error.value = err.message || 'Connection error'
      canRetry.value = true
      isStreaming.value = false
    }
  }

  function processSSEEvent(eventBlock: string) {
    let eventType = ''
    const dataLines: string[] = []

    for (const line of eventBlock.split('\n')) {
      if (line.startsWith('event:')) {
        eventType = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5))
      }
    }

    const eventData = dataLines.join('\n').trim()

    if (!eventType) return

    switch (eventType) {
      case 'token':
        streamingContent.value += eventData
        break
      case 'document': {
        try {
          const parsed = JSON.parse(eventData)
          onDocumentCallback?.(parsed.documentType, parsed.content)
        } catch { /* ignore parse errors */ }
        break
      }
      case 'phase_complete': {
        try {
          // eventData may be a plain string like "REQUIREMENTS" or JSON like {"phase":"REQUIREMENTS"}
          let phase: string
          try {
            const parsed = JSON.parse(eventData)
            phase = typeof parsed === 'string' ? parsed : parsed.phase
          } catch {
            phase = eventData
          }
          if (phase) {
            onPhaseCompleteCallback?.(phase as AiPhase)
          }
        } catch { /* ignore */ }
        break
      }
      case 'generated_data': {
        try {
          const parsed = JSON.parse(eventData)
          onGeneratedDataCallback?.(parsed)
        } catch { /* ignore parse errors */ }
        break
      }
      case 'error': {
        error.value = eventData
        canRetry.value = true
        break
      }
      case 'done': {
        finalizeStream()
        break
      }
    }
  }

  function finalizeStream() {
    if (streamingContent.value) {
      const aiMessage: AiMessage = {
        id: Date.now(),
        sessionId: lastRequest?.sessionId || '',
        role: 'ASSISTANT',
        content: streamingContent.value,
        phase: lastRequest?.phase || 'REQUIREMENTS',
        createdAt: new Date().toISOString()
      }
      messages.value.push(aiMessage)
    }
    streamingContent.value = ''
    isStreaming.value = false
  }

  function retry() {
    if (lastRequest) {
      // Remove the last user message before resending
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg?.role === 'USER') {
        messages.value.pop()
      }
      sendMessage(lastRequest)
    }
  }

  function cancel() {
    abortController?.abort()
    isStreaming.value = false
    streamingContent.value = ''
  }

  function onDocument(cb: (type: string, content: string) => void) {
    onDocumentCallback = cb
  }

  function onPhaseComplete(cb: (phase: AiPhase) => void) {
    onPhaseCompleteCallback = cb
  }

  function onGeneratedData(cb: (data: any) => void) {
    onGeneratedDataCallback = cb
  }

  function setMessages(msgs: AiMessage[]) {
    messages.value = msgs
  }

  return {
    messages,
    isStreaming,
    streamingContent,
    error,
    canRetry,
    sendMessage,
    retry,
    cancel,
    onDocument,
    onPhaseComplete,
    onGeneratedData,
    setMessages
  }
}
