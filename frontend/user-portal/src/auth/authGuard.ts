/**
 * Route-level auth guard.
 * User Portal has no Role Gate - any authenticated user can access.
 */

import type { RouteLocationNormalized } from 'vue-router'
import { authService } from './authService'
import { tokenStorage } from './tokenStorage'

export function createAuthGuard() {
  return async (
    to: RouteLocationNormalized
  ): Promise<{ path: string; query?: Record<string, string> } | undefined> => {
    const requiresAuth = to.meta.requiresAuth !== false && to.path !== '/login'
    if (!requiresAuth) return undefined

    const token = tokenStorage.getAccessToken()
    if (!token) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    let user = tokenStorage.getUser()
    if (!user) {
      try {
        user = await authService.getCurrentUser()
        tokenStorage.setUser(user)
      } catch {
        tokenStorage.clear()
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }

    return undefined
  }
}
