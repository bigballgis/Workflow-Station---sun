import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { preferenceApi, type UserPreference, type NotificationPreference } from '@/api/preference'

export const useSettingsStore = defineStore('settings', () => {
  const userPreference = ref<UserPreference>({
    theme: 'light',
    themeColor: '#409EFF',
    fontSize: 'medium',
    layoutDensity: 'comfortable',
    language: 'zh-CN',
    timezone: 'Asia/Shanghai',
    dateFormat: 'YYYY-MM-DD',
    pageSize: 20
  })

  const notificationPreference = ref<NotificationPreference>({
    notificationType: 'TASK',
    emailEnabled: true,
    browserEnabled: true,
    inAppEnabled: true
  })

  const loading = ref(false)

  const fetchPreferences = async () => {
    loading.value = true
    try {
      const [userRes, notifyRes] = await Promise.all([
        preferenceApi.getUserPreference(),
        preferenceApi.getNotificationPreferences()
      ])
      userPreference.value = userRes.data
      // 取第一个通知偏好设置，或使用默认值
      notificationPreference.value = Array.isArray(notifyRes.data) && notifyRes.data.length > 0 
        ? notifyRes.data[0] 
        : notificationPreference.value
    } finally {
      loading.value = false
    }
  }

  const updateUserPreference = async (data: Partial<UserPreference>) => {
    await preferenceApi.updateUserPreference(data)
    Object.assign(userPreference.value, data)
  }

  const updateNotificationPreference = async (data: Partial<NotificationPreference>) => {
    const fullData: NotificationPreference = {
      ...notificationPreference.value,
      ...data,
      notificationType: data.notificationType || notificationPreference.value.notificationType
    }
    await preferenceApi.updateNotificationPreference(fullData)
    Object.assign(notificationPreference.value, fullData)
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
