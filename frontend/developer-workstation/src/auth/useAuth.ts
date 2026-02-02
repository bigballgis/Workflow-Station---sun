/**
 * Auth composable exposing state, user, and actions.
 */

import { computed, ref } from 'vue'
import { authService } from './authService'
import { tokenStorage, type UserInfo } from './tokenStorage'

export type AuthState = 'Unauthenticated' | 'Authenticated' | 'AuthenticatedButForbidden' | 'Expired'

const DEVELOPER_ROLES = ['DEVELOPER', 'TECH_DIRECTOR']

export function useAuth() {
  const user = ref<UserInfo | null>(tokenStorage.getUser())

  const state = computed<AuthState>(() => {
    const token = tokenStorage.getAccessToken()
    if (!token) return 'Unauthenticated'
    const u = tokenStorage.getUser()
    if (!u) return 'Expired'
    const hasAccess = DEVELOPER_ROLES.some((r) => u.roles?.includes(r))
    return hasAccess ? 'Authenticated' : 'AuthenticatedButForbidden'
  })

  const isAuthenticated = computed(() => !!tokenStorage.getAccessToken())
  const isForbidden = computed(() => state.value === 'AuthenticatedButForbidden')

  const checkAccess = () => authService.checkAccess(DEVELOPER_ROLES)

  const login = authService.login.bind(authService)
  const logout = authService.logout.bind(authService)
  const refreshToken = authService.refreshToken.bind(authService)

  const setUser = (u: UserInfo | null) => {
    user.value = u
    if (u) tokenStorage.setUser(u)
  }

  const syncUser = () => {
    user.value = tokenStorage.getUser()
  }

  return {
    state,
    user,
    isAuthenticated,
    isForbidden,
    checkAccess,
    login,
    logout,
    refreshToken,
    setUser,
    syncUser
  }
}
