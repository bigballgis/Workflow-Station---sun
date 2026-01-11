import { getUser } from '@/api/auth'

/**
 * Permission utility functions for Admin Center
 */

// Permission definitions for each route/feature
export const PERMISSIONS = {
  // Dashboard - everyone can see
  DASHBOARD: 'basic:access',
  
  // User management
  USER_READ: 'user:read',
  USER_WRITE: 'user:write',
  USER_DELETE: 'user:delete',
  
  // Role management
  ROLE_READ: 'role:read',
  ROLE_WRITE: 'role:write',
  ROLE_DELETE: 'role:delete',
  
  // System admin
  SYSTEM_ADMIN: 'system:admin',
  SYSTEM_CONFIG: 'system:config',
  
  // Audit
  AUDIT_READ: 'audit:read',
  LOG_READ: 'log:read',
  
  // Tenant admin
  TENANT_ADMIN: 'tenant:admin'
} as const

// Route permission mapping
export const ROUTE_PERMISSIONS: Record<string, string[]> = {
  '/dashboard': [], // Everyone can access dashboard
  '/user': [PERMISSIONS.USER_READ],
  '/user/list': [PERMISSIONS.USER_READ],
  '/user/import': [PERMISSIONS.USER_WRITE],
  '/organization': [PERMISSIONS.USER_READ],
  '/virtual-group': [PERMISSIONS.USER_READ],
  '/role': [PERMISSIONS.ROLE_READ],
  '/function-unit': [PERMISSIONS.SYSTEM_ADMIN],
  '/dictionary': [PERMISSIONS.SYSTEM_ADMIN],
  '/monitor': [PERMISSIONS.SYSTEM_ADMIN],
  '/audit': [PERMISSIONS.AUDIT_READ, PERMISSIONS.LOG_READ],
  '/config': [PERMISSIONS.SYSTEM_ADMIN, PERMISSIONS.SYSTEM_CONFIG],
  '/profile': [] // Everyone can access their profile
}

/**
 * Check if user has a specific permission
 */
export function hasPermission(permission: string): boolean {
  const user = getUser()
  if (!user) return false
  
  // System admin has all permissions
  if (user.roles?.includes('SYS_ADMIN') || user.roles?.includes('SUPER_ADMIN')) {
    return true
  }
  
  return user.permissions?.includes(permission) ?? false
}

/**
 * Check if user has any of the specified permissions
 */
export function hasAnyPermission(permissions: string[]): boolean {
  if (!permissions || permissions.length === 0) return true
  return permissions.some(p => hasPermission(p))
}

/**
 * Check if user has all of the specified permissions
 */
export function hasAllPermissions(permissions: string[]): boolean {
  if (!permissions || permissions.length === 0) return true
  return permissions.every(p => hasPermission(p))
}

/**
 * Check if user can access a specific route
 */
export function canAccessRoute(path: string): boolean {
  const permissions = ROUTE_PERMISSIONS[path]
  if (!permissions || permissions.length === 0) return true
  return hasAnyPermission(permissions)
}

/**
 * Check if user has write permission for a feature
 */
export function canWrite(feature: 'user' | 'role' | 'system'): boolean {
  switch (feature) {
    case 'user':
      return hasPermission(PERMISSIONS.USER_WRITE)
    case 'role':
      return hasPermission(PERMISSIONS.ROLE_WRITE)
    case 'system':
      return hasPermission(PERMISSIONS.SYSTEM_ADMIN)
    default:
      return false
  }
}

/**
 * Check if user has delete permission for a feature
 */
export function canDelete(feature: 'user' | 'role'): boolean {
  switch (feature) {
    case 'user':
      return hasPermission(PERMISSIONS.USER_DELETE)
    case 'role':
      return hasPermission(PERMISSIONS.ROLE_DELETE)
    default:
      return false
  }
}

/**
 * Get user's role display name
 */
export function getUserRoleDisplay(): string {
  const user = getUser()
  if (!user?.roles?.length) return '未知角色'
  
  const roleNames: Record<string, string> = {
    'SYS_ADMIN': '系统管理员',
    'SUPER_ADMIN': '超级管理员',
    'AUDITOR': '审计员',
    'TENANT_ADMIN': '租户管理员'
  }
  
  return user.roles.map(r => roleNames[r] || r).join(', ')
}
