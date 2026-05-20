import { ref, type Ref } from 'vue'
import { AI_EVENT_STREAM_URL } from '@/api/aiGeneration'
import { getUser } from '@/api/auth'

/**
 * Composable for managing independent SSE long connection for async events.
 * Uses fetch API + ReadableStream (not EventSource) because EventSource
 * doesn't support custom headers (Authorization, X-User-Id).
 */
export function useAiEvents(functionUnitId: Ref<number>) {
  const connected = ref(false)
  const abortController = ref<AbortController | null>(null)

  // Event callbacks
  let onForceUnlockRequestCallback: ((data: any) => void) | null = null
  let onForceUnlockResponseCallback: ((data: any) => void) | null = null
  let onWriteSuccessCallback: ((data: any) => void) | null = null
  let onWriteErrorCallback: ((data: any) => void) | null = null

  function getAuthHeaders(): Record<string, string> {
    const headers: Record<string, string> = {}
    const user = getUser()
    if (user?.userId) {
      headers['X-User-Id'] = user.userId
    }
    return headers
  }

  async function connect() {
    disconnect()

    const controller = new AbortController()
    abortController.value = controller

    try {
      const url = AI_EVENT_STREAM_URL(functionUnitId.value)
      const response = await fetch(url, {
        method: 'GET',
        headers: getAuthHeaders(),
        signal: controller.signal
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      connected.value = true

      const reader = response.body?.getReader()
      if (!reader) return

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const events = buffer.split('\n\n')
        buffer = events.pop() || ''

        for (const eventBlock of events) {
          if (!eventBlock.trim()) continue
          processEvent(eventBlock)
        }
      }
    } catch (err: any) {
      if (err.name === 'AbortError') return
      console.warn('AI event stream disconnected:', err.message)
    } finally {
      connected.value = false
    }
  }

  function processEvent(eventBlock: string) {
    let eventType = ''
    let eventData = ''

    for (const line of eventBlock.split('\n')) {
      if (line.startsWith('event:')) {
        eventType = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        eventData = line.slice(5).trim()
      }
    }

    if (!eventType) return

    let parsed: any = eventData
    try {
      parsed = JSON.parse(eventData)
    } catch { /* use raw string */ }

    switch (eventType) {
      case 'force_unlock_request':
        onForceUnlockRequestCallback?.(parsed)
        break
      case 'force_unlock_response':
        onForceUnlockResponseCallback?.(parsed)
        break
      case 'write_success':
        onWriteSuccessCallback?.(parsed)
        break
      case 'write_error':
        onWriteErrorCallback?.(parsed)
        break
    }
  }

  function disconnect() {
    abortController.value?.abort()
    abortController.value = null
    connected.value = false
  }

  function onForceUnlockRequest(cb: (data: any) => void) {
    onForceUnlockRequestCallback = cb
  }

  function onForceUnlockResponse(cb: (data: any) => void) {
    onForceUnlockResponseCallback = cb
  }

  function onWriteSuccess(cb: (data: any) => void) {
    onWriteSuccessCallback = cb
  }

  function onWriteError(cb: (data: any) => void) {
    onWriteErrorCallback = cb
  }

  return {
    connected,
    connect,
    disconnect,
    onForceUnlockRequest,
    onForceUnlockResponse,
    onWriteSuccess,
    onWriteError
  }
}
