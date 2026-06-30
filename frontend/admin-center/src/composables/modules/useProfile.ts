/**
 * 用户 Profile 业务逻辑 composable
 */
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { logger } from '@/utils/logger'
import { getCurrentUser, getUser, saveUser, USER_KEY, USERNAME_KEY } from '@/api/auth'
import { languageLabelFor } from '@/utils/languageLabel'

interface UserInfo {
  userId?: string
  username?: string
  displayName?: string
  email?: string
  roles?: string[]
  permissions?: string[]
  language?: string
  avatar?: string
}

export function useProfile() {
  const { t, locale } = useI18n()

  const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

  const loading = ref(false)
  const userInfo = ref<UserInfo | null>(null)

  const languageLabel = computed(() => languageLabelFor(userInfo.value?.language, String(locale.value)))

  const loadUserInfo = async () => {
    loading.value = true
    try {
      try {
        const fresh = await getCurrentUser()
        saveUser(fresh)
        userInfo.value = fresh
      } catch (error) {
        logger.error('profile', 'Failed to load user info:', error)
        const storedUser = localStorage.getItem(USER_KEY)
        if (storedUser) {
          try { userInfo.value = JSON.parse(storedUser) }
          catch { userInfo.value = getUser() }
        } else {
          userInfo.value = getUser()
        }
        if (!userInfo.value) {
          const legacy = localStorage.getItem('userInfo')
          if (legacy) {
            try { userInfo.value = JSON.parse(legacy) }
            catch { userInfo.value = { username: localStorage.getItem(USERNAME_KEY) || 'User' } }
          } else {
            userInfo.value = { username: localStorage.getItem(USERNAME_KEY) || 'User' }
          }
        }
      }
    } finally {
      loading.value = false
    }
  }

  return {
    defaultAvatar, loading, userInfo,
    languageLabel, loadUserInfo,
  }
}
