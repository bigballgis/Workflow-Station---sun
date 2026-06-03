import { ref } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { NotificationData } from '@/api/notification'

export function useNotificationWebSocket(onMessage: (notification: NotificationData) => void) {
  const connected = ref(false)
  let client: Client | null = null

  const connect = () => {
    client = new Client({
      webSocketFactory: () => new SockJS('/api/portal/ws/notifications'),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        connected.value = true
        client?.subscribe('/user/queue/notifications', (message) => {
          try {
            onMessage(JSON.parse(message.body))
          } catch (e) {
            console.error('Failed to parse notification:', e)
          }
        })
      },
      onDisconnect: () => { connected.value = false },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'])
        connected.value = false
      }
    })
    client.activate()
  }

  const disconnect = () => {
    if (client?.active) client.deactivate()
    connected.value = false
  }

  return { connected, connect, disconnect }
}
