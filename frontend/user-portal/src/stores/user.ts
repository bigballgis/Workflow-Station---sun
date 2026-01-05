import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface UserInfo {
  id: string
  username: string
  name: string
  email: string
  avatar?: string
  department?: string
  roles: string[]
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const userName = computed(() => userInfo.value?.name || '')
  const userRoles = computed(() => userInfo.value?.roles || [])

  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
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
      department: '技术部',
      roles: ['user']
    })
    return true
  }

  const logout = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
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
