import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { notificationApi, type NotificationData, type NotificationParams } from '@/api/notification'
import { useNotificationWebSocket } from '@/composables/useNotificationWebSocket'

export type { NotificationData as Notification }

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<NotificationData[]>([])
  const loading = ref(false)
  const total = ref(0)
  const unreadCount = ref(0)
  const wsConnected = ref(false)

  let pollTimer: ReturnType<typeof setInterval> | null = null

  const fetchNotifications = async (params?: NotificationParams) => {
    loading.value = true
    try {
      const res = await notificationApi.getNotifications(params)
      notifications.value = res?.data?.content ?? []
      total.value = res?.data?.totalElements ?? 0
    } catch {
      notifications.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  const fetchUnreadCount = async () => {
    try {
      const res = await notificationApi.getUnreadCount()
      unreadCount.value = res?.data ?? 0
    } catch {
      // Silent fail — state is already initialized
    }
  }

  const markAsRead = async (id: number) => {
    await notificationApi.markAsRead(id)
    const notification = notifications.value.find(n => n.id === id)
    if (notification && !notification.isRead) {
      notification.isRead = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
  }

  const markAllAsRead = async () => {
    await notificationApi.markAllAsRead()
    notifications.value.forEach(n => n.isRead = true)
    unreadCount.value = 0
  }

  const deleteNotification = async (id: number) => {
    await notificationApi.deleteNotification(id)
    const idx = notifications.value.findIndex(n => n.id === id)
    if (idx > -1) {
      const wasUnread = !notifications.value[idx].isRead
      notifications.value.splice(idx, 1)
      total.value = Math.max(0, total.value - 1)
      if (wasUnread) {
        unreadCount.value = Math.max(0, unreadCount.value - 1)
      }
    }
  }

  // WebSocket integration
  const { connected, connect, disconnect } = useNotificationWebSocket((notification: NotificationData) => {
    notifications.value.unshift(notification)
    unreadCount.value++
  })

  const startPolling = () => {
    stopPolling()
    pollTimer = setInterval(fetchUnreadCount, 30000)
  }

  const stopPolling = () => {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  const initWebSocket = () => {
    connect()

    watch(connected, (isConnected) => {
      wsConnected.value = isConnected
      if (isConnected) {
        stopPolling()
      } else {
        startPolling()
      }
    }, { immediate: true })

    const cleanup = () => {
      stopPolling()
      disconnect()
    }

    return cleanup
  }

  return {
    notifications,
    loading,
    total,
    unreadCount,
    wsConnected,
    fetchNotifications,
    fetchUnreadCount,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    initWebSocket,
    disconnect
  }
})
