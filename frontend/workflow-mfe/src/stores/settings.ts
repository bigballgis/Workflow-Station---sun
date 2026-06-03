import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { preferenceApi, type UserPreference, type NotificationPreference } from '@/api/preference'

export const useSettingsStore = defineStore('settings', () => {
  const userPreference = ref<UserPreference>({
    language: 'zh-CN',
    theme: 'light',
    fontSize: 'medium',
    sidebarCollapsed: false,
    defaultPage: '/dashboard',
    pageSize: 20
  })

  const notificationPreference = ref<NotificationPreference>({
    emailEnabled: true,
    smsEnabled: false,
    pushEnabled: true,
    taskReminder: true,
    processUpdate: true,
    systemNotice: true
  })

  const loading = ref(false)

  const fetchPreferences = async () => {
    loading.value = true
    try {
      const [userRes, notifyRes] = await Promise.all([
        preferenceApi.getUserPreference(),
        preferenceApi.getNotificationPreference()
      ])
      userPreference.value = userRes.data
      notificationPreference.value = notifyRes.data
    } finally {
      loading.value = false
    }
  }

  const updateUserPreference = async (data: Partial<UserPreference>) => {
    await preferenceApi.updateUserPreference(data)
    Object.assign(userPreference.value, data)
  }

  const updateNotificationPreference = async (data: Partial<NotificationPreference>) => {
    await preferenceApi.updateNotificationPreference(data)
    Object.assign(notificationPreference.value, data)
  }

  // 监听语言变化
  watch(() => userPreference.value.language, (newLang) => {
    localStorage.setItem('language', newLang)
  })

  return {
    userPreference,
    notificationPreference,
    loading,
    fetchPreferences,
    updateUserPreference,
    updateNotificationPreference
  }
})
