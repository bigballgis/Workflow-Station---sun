/**
 * Route-level auth guard with Role Gate.
 * Admin Center requires SYS_ADMIN or AUDITOR.
 * Returns redirect path/object or undefined to proceed.
 */

import type { RouteLocationNormalized } from 'vue-router'
import { authService } from './authService'
import { tokenStorage } from './tokenStorage'

const ADMIN_CENTER_ROLES = ['SYS_ADMIN', 'AUDITOR']

export function createAuthGuard(config: { requiredRoles?: string[] } = {}) {
  const requiredRoles = config.requiredRoles ?? ADMIN_CENTER_ROLES

  return async (
    to: RouteLocationNormalized
  ): Promise<{ path: string; query?: Record<string, string> } | undefined> => {
    const requiresAuth = to.matched.some((r) => r.meta.requiresAuth)
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

    const hasRole = requiredRoles.some((r) => user!.roles?.includes(r))
    if (!hasRole) {
      return { path: '/403' }
    }

    return undefined
  }
}
