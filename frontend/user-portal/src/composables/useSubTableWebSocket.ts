import { ref, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { TOKEN_KEY } from '@/api/auth'

export interface SubTableUpdateMessage {
  taskId: string
  rowId: number
  assigneeId?: string
  status?: string
  timestamp: string
}

export function useSubTableWebSocket() {
  const connected = ref(false)
  let client: Client | null = null
  let currentSubscription: any = null

  function connect() {
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) {
      console.warn('No auth token found, cannot connect WebSocket')
      return
    }

    client = new Client({
      webSocketFactory: () => new SockJS('/api/workflow/ws/sub-table-updates'),
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      debug: (str) => {
        // Only log errors in production
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
      // Wait for connection and retry subscription
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

    // Unsubscribe from previous topic if exists
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

  // Auto cleanup on component unmount
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
