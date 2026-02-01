import { request } from './request'

export interface UserPreference {
  id?: number
  userId?: string
  theme: string
  themeColor: string
  fontSize: string
  layoutDensity: string
  language: string
  timezone: string
  dateFormat: string
  pageSize: number
}

export interface DashboardLayout {
  id?: number
  userId?: string
  componentId: string
  componentType: string
  gridX: number
  gridY: number
  gridW: number
  gridH: number
  isVisible: boolean
  config?: Record<string, any>
}

export interface NotificationPreference {
  id?: number
  userId?: string
  notificationType: string
  emailEnabled: boolean
  browserEnabled: boolean
  inAppEnabled: boolean
  quietStartTime?: string
  quietEndTime?: string
}

// 获取用户偏好设置
export function getUserPreference() {
  return request.get<{ data: UserPreference }>('/preferences')
}

// 更新用户偏好设置
export function updateUserPreference(data: Partial<UserPreference>) {
  return request.put<{ data: UserPreference }>('/preferences', data)
}

// 获取工作台布局
export function getDashboardLayout() {
  return request.get<{ data: DashboardLayout[] }>('/preferences/dashboard-layout')
}

// 保存工作台布局
export function saveDashboardLayout(layouts: DashboardLayout[]) {
  return request.put<{ data: DashboardLayout[] }>('/preferences/dashboard-layout', layouts)
}

// 获取通知偏好
export function getNotificationPreferences() {
  return request.get<{ data: NotificationPreference[] }>('/preferences/notifications')
}

// 更新通知偏好
export function updateNotificationPreference(data: NotificationPreference) {
  return request.put<{ data: NotificationPreference }>('/preferences/notifications', data)
}

// Preference API 对象
export const preferenceApi = {
  getUserPreference,
  updateUserPreference,
  getDashboardLayout,
  saveDashboardLayout,
  getNotificationPreferences,
  updateNotificationPreference
}
