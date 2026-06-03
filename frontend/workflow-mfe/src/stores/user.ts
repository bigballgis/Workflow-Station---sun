import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface UserInfo {
  id: string
  username: string
  name: string
  email: string
  avatar?: string
  roles: string[]
}

export const useUserStore = defineStore('user', () => {
  // Token no longer stored in localStorage — httpOnly cookies handle auth
  const token = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!userInfo.value)
  const userName = computed(() => userInfo.value?.name || '')
  const userRoles = computed(() => userInfo.value?.roles || [])

  const setToken = (_newToken: string) => {
    // No-op: tokens are now httpOnly cookies, not managed by JS
    token.value = ''
  }

  const setUserInfo = (info: UserInfo) => {
    userInfo.value = info
  }

  const login = async (username: string, password: string) => {
    // 模拟登录
    const mockToken = `token_${Date.now()}`
    setToken(mockToken)
    setUserInfo({
      id: '1',
      username,
      name: username,
      email: `${username}@hsbc.com`,
      roles: ['user']
    })
    return true
  }

  const logout = () => {
    token.value = ''
    userInfo.value = null
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    userName,
    userRoles,
    setToken,
    setUserInfo,
    login,
    logout
  }
})
