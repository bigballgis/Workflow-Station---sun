import { ref } from 'vue'
import type { AiMessage, AiChatRequest, AiPhase, AiGeneratedData, GenerationPreviewData } from '@/types/aiGeneration'
import { AI_CHAT_STREAM_URL } from '@/api/aiGeneration'
import { getUser } from '@/api/auth'
import { readAmToken } from '@/utils/amToken'

/** Draft data structure stored in localStorage */
export interface AiGenerationDraft {
  generatedData: AiGeneratedData
  previewData: GenerationPreviewData | null
  timestamp: number
  sessionId: string
  /**
   * 这份结果已经写入 function unit。
   *
   * <p>Apply 成功后草稿不再删除，而是打上这个标记：预览卡是这一轮生成的唯一留存（后端没有存过
   * generatedData），删掉就意味着关一次面板卡片永久消失。带标记的草稿重开面板时静默还原成
   * "Applied ✓" 只读态，不弹"是否恢复草稿"。</p>
   */
  applied?: boolean
}

const DRAFT_EXPIRY_MS = 24 * 60 * 60 * 1000 // 24 hours

/**
 * Build the localStorage key for a generation draft.
 */
export function buildDraftKey(functionUnitId: number, sessionId: string): string {
  return `ai_generation_draft_${functionUnitId}_${sessionId}`
}

/**
 * Save a generation draft to localStorage.
 */
export function saveDraft(functionUnitId: number, sessionId: string, draft: AiGenerationDraft): void {
  try {
    localStorage.setItem(buildDraftKey(functionUnitId, sessionId), JSON.stringify(draft))
  } catch { /* quota exceeded or other storage error — silently ignore */ }
}

/**
 * Load a generation draft from localStorage. Returns null if not found or expired.
 */
export function loadDraft(functionUnitId: number, sessionId: string): AiGenerationDraft | null {
  try {
    const raw = localStorage.getItem(buildDraftKey(functionUnitId, sessionId))
    if (!raw) return null
    const draft: AiGenerationDraft = JSON.parse(raw)
    if (Date.now() - draft.timestamp > DRAFT_EXPIRY_MS) {
      localStorage.removeItem(buildDraftKey(functionUnitId, sessionId))
      return null
    }
    return draft
  } catch {
    return null
  }
}

/**
 * Clear a generation draft from localStorage.
 */
export function clearDraft(functionUnitId: number, sessionId: string): void {
  localStorage.removeItem(buildDraftKey(functionUnitId, sessionId))
}

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
  const errorCode = ref<string | null>(null)
  const partialGeneratedData = ref<Partial<AiGeneratedData>>({})
  const isGenerationComplete = ref(false)
  const generationStep = ref(0) // 0=not started, 1-6=steps
  const degradationInfo = ref<{ lastSuccessTime?: string; degradationOptions?: string[] } | null>(null)

  let lastRequest: AiChatRequest | null = null
  let abortController: AbortController | null = null

  // Event callbacks
  let onDocumentCallback: ((type: string, content: string) => void) | null = null
  let onPhaseCompleteCallback: ((phase: AiPhase) => void) | null = null
  let onGeneratedDataCallback: ((data: any) => void) | null = null
  let onValidationWarningCallback: ((warnings: any[]) => void) | null = null
  let onSessionCallback: ((sessionId: string) => void) | null = null

  function getAuthHeaders(): Record<string, string> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json'
    }
    const user = getUser()
    if (user?.userId) {
      headers['X-User-Id'] = user.userId
    }
    // AI gateway 的 Bearer 凭证是每用户的 DSP AMToken：后端不持有共享密钥，只透传这个头。
    // 读不到就不带，后端会以 AI_GATEWAY_TOKEN_MISSING 显式失败（不做匿名调用）。
    const amToken = readAmToken()
    if (amToken) {
      headers['X-AM-Token'] = amToken
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
    errorCode.value = null
    partialGeneratedData.value = {}
    isGenerationComplete.value = false
    generationStep.value = 0
    degradationInfo.value = null

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
    // Clear residual streamingContent before new request
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
      case 'session': {
        try {
          const parsed = JSON.parse(eventData)
          if (parsed.sessionId) {
            onSessionCallback?.(parsed.sessionId)
          }
        } catch { /* ignore */ }
        break
      }
      case 'token':
        if (generationStep.value < 1) generationStep.value = 1
        streamingContent.value += eventData
        break
      case 'document': {
        if (generationStep.value < 2) generationStep.value = 2
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
        if (generationStep.value < 5) generationStep.value = 5
        try {
          const parsed = JSON.parse(eventData)
          // Incremental merge: new data overwrites corresponding fields
          partialGeneratedData.value = {
            ...partialGeneratedData.value,
            ...parsed
          }
          onGeneratedDataCallback?.(partialGeneratedData.value as AiGeneratedData)

          // Auto-save draft to localStorage
          if (lastRequest) {
            saveDraft(lastRequest.functionUnitId, lastRequest.sessionId || '', {
              generatedData: partialGeneratedData.value as AiGeneratedData,
              previewData: null, // ChatDialog will compute this
              timestamp: Date.now(),
              sessionId: lastRequest.sessionId || ''
            })
          }
        } catch { /* ignore parse errors */ }
        break
      }
      case 'validation_warning': {
        try {
          const parsed = JSON.parse(eventData)
          const warnings = Array.isArray(parsed) ? parsed : parsed.warnings || []
          onValidationWarningCallback?.(warnings)
        } catch { /* ignore parse errors */ }
        break
      }
      case 'error': {
        generationStep.value = 0
        try {
          const parsed = JSON.parse(eventData)
          if (parsed.errorCode) {
            error.value = parsed.message || eventData
            const retryableCodes = ['AI_WEBHOOK_TIMEOUT', 'AI_WEBHOOK_CALL_FAILED']
            canRetry.value = retryableCodes.includes(parsed.errorCode)
            // Store errorCode for i18n lookup
            errorCode.value = parsed.errorCode
            // Capture degradation info if present
            if (parsed.degradationOptions) {
              degradationInfo.value = {
                lastSuccessTime: parsed.lastSuccessTime,
                degradationOptions: parsed.degradationOptions
              }
            }
          } else {
            error.value = eventData
            canRetry.value = true
            errorCode.value = null
          }
        } catch {
          // Backward compatible: plain string fallback
          error.value = eventData
          canRetry.value = true
          errorCode.value = null
        }
        break
      }
      case 'done': {
        isGenerationComplete.value = true
        generationStep.value = 6
        finalizeStream()
        break
      }
    }
  }

  function finalizeStream() {
    if (streamingContent.value) {
      // Dedup check: skip if last ASSISTANT message has identical content
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg?.role === 'ASSISTANT' && lastMsg.content === streamingContent.value) {
        streamingContent.value = ''
        isStreaming.value = false
        return
      }
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

  function onValidationWarning(cb: (warnings: any[]) => void) {
    onValidationWarningCallback = cb
  }

  function onSession(cb: (sessionId: string) => void) {
    onSessionCallback = cb
  }

  function setMessages(msgs: AiMessage[]) {
    messages.value = msgs
  }

  return {
    messages,
    isStreaming,
    streamingContent,
    error,
    errorCode,
    canRetry,
    partialGeneratedData,
    isGenerationComplete,
    generationStep,
    degradationInfo,
    sendMessage,
    retry,
    cancel,
    onDocument,
    onPhaseComplete,
    onGeneratedData,
    onValidationWarning,
    onSession,
    setMessages
  }
}
