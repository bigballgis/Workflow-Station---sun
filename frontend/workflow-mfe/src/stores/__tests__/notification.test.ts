import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useNotificationStore } from '../notification'

// Mock the API module
vi.mock('@/api/notification', () => ({
  notificationApi: {
    getNotifications: vi.fn(),
    getUnreadCount: vi.fn(),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn(),
    deleteNotification: vi.fn()
  }
}))

// Mock the WebSocket composable
vi.mock('@/composables/useNotificationWebSocket', () => ({
  useNotificationWebSocket: vi.fn((onMessage: Function) => {
    // Store the callback for testing
    ;(globalThis as any).__wsOnMessage = onMessage
    return {
      connected: { value: false },
      connect: vi.fn(),
      disconnect: vi.fn()
    }
  })
}))

import { notificationApi } from '@/api/notification'

const mockNotification = (id: number, isRead = false) => ({
  id,
  type: 'TASK' as const,
  title: `通知 ${id}`,
  content: `内容 ${id}`,
  link: `/tasks/${id}`,
  isRead,
  createdAt: '2024-01-01T12:00:00'
})

describe('notification store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('fetchNotifications', () => {
    it('应调用 API 并更新 store 状态', async () => {
      const mockData = {
        content: [mockNotification(1), mockNotification(2)],
        totalElements: 2
      }
      vi.mocked(notificationApi.getNotifications).mockResolvedValue({ data: mockData } as any)

      const store = useNotificationStore()
      await store.fetchNotifications({ page: 0, size: 20 })

      expect(notificationApi.getNotifications).toHaveBeenCalledWith({ page: 0, size: 20 })
      expect(store.notifications).toHaveLength(2)
      expect(store.total).toBe(2)
      expect(store.loading).toBe(false)
    })

    it('API 失败时应清空列表', async () => {
      vi.mocked(notificationApi.getNotifications).mockRejectedValue(new Error('网络错误'))

      const store = useNotificationStore()
      await store.fetchNotifications()

      expect(store.notifications).toHaveLength(0)
      expect(store.total).toBe(0)
      expect(store.loading).toBe(false)
    })
  })

  describe('fetchUnreadCount', () => {
    it('应调用 API 并更新未读数', async () => {
      vi.mocked(notificationApi.getUnreadCount).mockResolvedValue({ data: 5 } as any)

      const store = useNotificationStore()
      await store.fetchUnreadCount()

      expect(notificationApi.getUnreadCount).toHaveBeenCalled()
      expect(store.unreadCount).toBe(5)
    })
  })

  describe('markAsRead', () => {
    it('应调用 API 并更新通知已读状态和未读数', async () => {
      vi.mocked(notificationApi.getNotifications).mockResolvedValue({
        data: { content: [mockNotification(1, false)], totalElements: 1 }
      } as any)
      vi.mocked(notificationApi.markAsRead).mockResolvedValue({} as any)

      const store = useNotificationStore()
      await store.fetchNotifications()
      store.unreadCount = 3

      await store.markAsRead(1)

      expect(notificationApi.markAsRead).toHaveBeenCalledWith(1)
      expect(store.notifications[0].isRead).toBe(true)
      expect(store.unreadCount).toBe(2)
    })

    it('已读通知再次标记不应减少未读数', async () => {
      vi.mocked(notificationApi.getNotifications).mockResolvedValue({
        data: { content: [mockNotification(1, true)], totalElements: 1 }
      } as any)
      vi.mocked(notificationApi.markAsRead).mockResolvedValue({} as any)

      const store = useNotificationStore()
      await store.fetchNotifications()
      store.unreadCount = 3

      await store.markAsRead(1)

      expect(store.unreadCount).toBe(3) // 不变
    })
  })

  describe('markAllAsRead', () => {
    it('应调用 API 并将所有通知标记为已读', async () => {
      vi.mocked(notificationApi.getNotifications).mockResolvedValue({
        data: { content: [mockNotification(1), mockNotification(2)], totalElements: 2 }
      } as any)
      vi.mocked(notificationApi.markAllAsRead).mockResolvedValue({} as any)

      const store = useNotificationStore()
      await store.fetchNotifications()
      store.unreadCount = 2

      await store.markAllAsRead()

      expect(notificationApi.markAllAsRead).toHaveBeenCalled()
      expect(store.notifications.every(n => n.isRead)).toBe(true)
      expect(store.unreadCount).toBe(0)
    })
  })

  describe('deleteNotification', () => {
    it('应调用 API 并从列表中移除通知', async () => {
      vi.mocked(notificationApi.getNotifications).mockResolvedValue({
        data: { content: [mockNotification(1), mockNotification(2)], totalElements: 2 }
      } as any)
      vi.mocked(notificationApi.deleteNotification).mockResolvedValue({} as any)

      const store = useNotificationStore()
      await store.fetchNotifications()

      await store.deleteNotification(1)

      expect(notificationApi.deleteNotification).toHaveBeenCalledWith(1)
      expect(store.notifications).toHaveLength(1)
      expect(store.notifications[0].id).toBe(2)
      expect(store.total).toBe(1)
    })

    it('删除未读通知应减少未读数', async () => {
      vi.mocked(notificationApi.getNotifications).mockResolvedValue({
        data: { content: [mockNotification(1, false)], totalElements: 1 }
      } as any)
      vi.mocked(notificationApi.deleteNotification).mockResolvedValue({} as any)

      const store = useNotificationStore()
      await store.fetchNotifications()
      store.unreadCount = 3

      await store.deleteNotification(1)

      expect(store.unreadCount).toBe(2)
    })

    it('删除已读通知不应减少未读数', async () => {
      vi.mocked(notificationApi.getNotifications).mockResolvedValue({
        data: { content: [mockNotification(1, true)], totalElements: 1 }
      } as any)
      vi.mocked(notificationApi.deleteNotification).mockResolvedValue({} as any)

      const store = useNotificationStore()
      await store.fetchNotifications()
      store.unreadCount = 3

      await store.deleteNotification(1)

      expect(store.unreadCount).toBe(3)
    })
  })

  describe('WebSocket 消息处理', () => {
    it('收到新通知应添加到列表头部并递增未读数', () => {
      const store = useNotificationStore()
      store.unreadCount = 2

      const wsCallback = (globalThis as any).__wsOnMessage
      const newNotification = mockNotification(99, false)
      wsCallback(newNotification)

      expect(store.notifications[0].id).toBe(99)
      expect(store.unreadCount).toBe(3)
    })
  })
})
