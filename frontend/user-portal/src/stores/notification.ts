import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/api/request'

export interface Notification {
  id: string
  type: 'task' | 'process' | 'system' | 'reminder'
  title: string
  content: string
  isRead: boolean
  createdAt: string
  link?: string
}

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<Notification[]>([])
  const loading = ref(false)
  const total = ref(0)

  const unreadCount = computed(() => notifications.value.filter(n => !n.isRead).length)

  const fetchNotifications = async (params?: { page?: number; size?: number; type?: string; isRead?: boolean }) => {
    loading.value = true
    try {
      const res = await request.get('/api/notifications', { params })
      notifications.value = res.data.content
      total.value = res.data.totalElements
    } finally {
      loading.value = false
    }
  }

  const markAsRead = async (id: string) => {
    await request.put(`/api/notifications/${id}/read`)
    const notification = notifications.value.find(n => n.id === id)
    if (notification) {
      notification.isRead = true
    }
  }

  const markAllAsRead = async () => {
    await request.put('/api/notifications/read-all')
    notifications.value.forEach(n => n.isRead = true)
  }

  const deleteNotification = async (id: string) => {
    await request.delete(`/api/notifications/${id}`)
    notifications.value = notifications.value.filter(n => n.id !== id)
  }

  return {
    notifications,
    loading,
    total,
    unreadCount,
    fetchNotifications,
    markAsRead,
    markAllAsRead,
    deleteNotification
  }
})
