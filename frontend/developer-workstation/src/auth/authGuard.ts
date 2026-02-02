/**
 * Route-level auth guard with Role Gate.
 * Developer Workstation requires DEVELOPER or TECH_DIRECTOR.
 */

import type { RouteLocationNormalized } from 'vue-router'
import { authService } from './authService'
import { tokenStorage } from './tokenStorage'

const DEVELOPER_ROLES = ['DEVELOPER', 'TECH_DIRECTOR']

export function createAuthGuard(config: { requiredRoles?: string[] } = {}) {
  const requiredRoles = config.requiredRoles ?? DEVELOPER_ROLES

  return async (
    to: RouteLocationNormalized
  ): Promise<{ path: string; query?: Record<string, string> } | undefined> => {
    if (to.path === '/login') {
      const token = tokenStorage.getAccessToken()
      const user = tokenStorage.getUser()
      if (token && user && requiredRoles.some((r) => user.roles?.includes(r))) {
        return { path: '/' }
      }
      return undefined
    }

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
      return { path: '/no-permission' }
    }

    return undefined
  }
}
