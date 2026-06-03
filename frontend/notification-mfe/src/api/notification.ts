import request from './request'

export interface NotificationParams {
  page?: number; size?: number; type?: string; isRead?: boolean
}

export interface NotificationData {
  id: number
  type: 'TASK' | 'PROCESS' | 'SYSTEM' | 'REMINDER' | 'PERMISSION' | 'APPROVAL'
  title: string; content: string; link?: string; isRead: boolean; createdAt: string
}

export const notificationApi = {
  getNotifications(params?: NotificationParams) {
    return request.get<{ data: any }>('/notifications', { params })
  },
  getUnreadCount() { return request.get<{ data: number }>('/notifications/unread-count') },
  markAsRead(id: number) { return request.put(`/notifications/${id}/read`) },
  markAllAsRead() { return request.put('/notifications/read-all') },
  deleteNotification(id: number) { return request.delete(`/notifications/${id}`) }
}
