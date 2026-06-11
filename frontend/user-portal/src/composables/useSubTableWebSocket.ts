import { ref, onUnmounted } from 'vue'

export interface SubTableUpdateMessage {
  taskId: string
  rowId: number
  assigneeId?: string
  status?: string
  timestamp: string
}

export function useSubTableWebSocket() {
  const connected = ref(false)
  let client: any = null
  let currentSubscription: any = null

  async function connect() {
    // Lazy-load WebSocket libraries — saves ~350ms during component setup
    const [{ Client }, { default: SockJS }] = await Promise.all([
      import('@stomp/stompjs'),
      import('sockjs-client')
    ])

    // Auth via httpOnly cookie — browser auto-sends with same-origin WebSocket
    client = new Client({
      webSocketFactory: () => new SockJS('/api/workflow/ws/sub-table-updates'),
      debug: (str) => {
        if (import.meta.env.DEV) {
          console.log('[SubTable WS]', str)
        }
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('[SubTable WS] Connected')
        connected.value = true
      },
      onDisconnect: () => {
        console.log('[SubTable WS] Disconnected')
        connected.value = false
      },
      onStompError: (frame) => {
        console.error('[SubTable WS] STOMP error:', frame.headers['message'])
        console.error('[SubTable WS] Error details:', frame.body)
        connected.value = false
      }
    })

    client.activate()
  }

  function subscribe(taskId: string, onMessage: (message: SubTableUpdateMessage) => void) {
    if (!client || !client.connected) {
      console.warn('[SubTable WS] Client not connected, attempting to connect...')
      connect()
      setTimeout(() => {
        if (client && client.connected) {
          performSubscription(taskId, onMessage)
        }
      }, 1000)
      return
    }

    performSubscription(taskId, onMessage)
  }

  function performSubscription(taskId: string, onMessage: (message: SubTableUpdateMessage) => void) {
    if (!client) return

    if (currentSubscription) {
      currentSubscription.unsubscribe()
      currentSubscription = null
    }

    const topic = `/topic/tasks/${taskId}/sub-table-updates`
    console.log('[SubTable WS] Subscribing to:', topic)

    currentSubscription = client.subscribe(topic, (message) => {
      try {
        const data = JSON.parse(message.body) as SubTableUpdateMessage
        console.log('[SubTable WS] Received update:', data)
        onMessage(data)
      } catch (error) {
        console.error('[SubTable WS] Failed to parse message:', error)
      }
    })
  }

  function unsubscribe() {
    if (currentSubscription) {
      currentSubscription.unsubscribe()
      currentSubscription = null
      console.log('[SubTable WS] Unsubscribed')
    }
  }

  function disconnect() {
    unsubscribe()
    if (client) {
      client.deactivate()
      client = null
    }
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    connected,
    connect,
    subscribe,
    unsubscribe,
    disconnect
  }
}
