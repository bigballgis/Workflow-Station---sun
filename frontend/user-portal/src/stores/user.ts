import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { tokenStorage } from '@/auth/tokenStorage'

export interface UserInfo {
  id: string
  userId?: string
  username: string
  name: string
  displayName?: string
  email: string
  avatar?: string
  roles: string[]
}

export const useUserStore = defineStore('user', () => {
  const userInfo = ref<UserInfo | null>(null)

  const token = computed(() => tokenStorage.getAccessToken() ?? '')
  const isLoggedIn = computed(() => !!tokenStorage.getAccessToken())
  const userName = computed(() => {
    const u = userInfo.value ?? tokenStorage.getUser()
    return (u as any)?.displayName ?? (u as any)?.name ?? (u as any)?.username ?? ''
  })
  const userRoles = computed(() => {
    const u = userInfo.value ?? tokenStorage.getUser()
    return (u as any)?.roles ?? []
  })

  const setToken = (_newToken: string) => {
    tokenStorage.setTokens(_newToken, tokenStorage.getRefreshToken() ?? '')
  }

  const setUserInfo = (info: UserInfo | null) => {
    userInfo.value = info
    if (info) {
      tokenStorage.setUser({
        userId: info.userId ?? info.id,
        username: info.username,
        displayName: info.displayName ?? info.name,
        email: info.email,
        roles: info.roles,
        permissions: [],
        language: 'zh_CN'
      })
    }
  }

  const logout = () => {
    userInfo.value = null
    tokenStorage.clear()
  }

  const syncFromStorage = () => {
    const u = tokenStorage.getUser()
    if (u) {
      userInfo.value = {
        id: u.userId,
        userId: u.userId,
        username: u.username,
        name: u.displayName ?? u.username,
        displayName: u.displayName,
        email: u.email,
        roles: u.roles ?? []
      }
    }
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    userName,
    userRoles,
    setToken,
    setUserInfo,
    logout,
    syncFromStorage
  }
})
