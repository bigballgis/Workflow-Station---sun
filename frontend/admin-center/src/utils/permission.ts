import { getUser } from '@/api/auth'
import i18n from '@/i18n'

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
  '/automation-pieces': [PERMISSIONS.SYSTEM_ADMIN],
  '/automation-flows': [PERMISSIONS.SYSTEM_ADMIN],
  '/automation-runs': [PERMISSIONS.SYSTEM_ADMIN],
  '/monitor': [PERMISSIONS.SYSTEM_ADMIN],
  '/audit': [PERMISSIONS.AUDIT_READ, PERMISSIONS.LOG_READ],
  '/audit/admin-center': [PERMISSIONS.AUDIT_READ, PERMISSIONS.LOG_READ],
  '/audit/user-portal': [PERMISSIONS.AUDIT_READ, PERMISSIONS.LOG_READ],
  '/config': [PERMISSIONS.SYSTEM_ADMIN, PERMISSIONS.SYSTEM_CONFIG],
  '/profile': [], // Everyone can access their profile
  '/relation-tables/structure': [PERMISSIONS.SYSTEM_ADMIN],
  '/relation-tables/structure/create': [PERMISSIONS.SYSTEM_ADMIN],
  '/relation-tables/data': [PERMISSIONS.SYSTEM_ADMIN]
}

// Hardcoded role -> permissions fallback for when sys_role_permissions is empty
const ROLE_PERMISSION_DEFAULTS: Record<string, string[]> = {
  SYS_ADMIN: ['user:read', 'user:write', 'user:delete', 'role:read', 'role:write', 'role:delete', 'system:admin', 'system:config', 'audit:read', 'log:read', 'basic:access'],
  SUPER_ADMIN: ['user:read', 'user:write', 'user:delete', 'role:read', 'role:write', 'role:delete', 'system:admin', 'system:config', 'audit:read', 'log:read', 'basic:access'],
  AUDITOR: ['audit:read', 'log:read', 'user:read', 'basic:access'],
}

const AUDITOR_DENIED_PERMISSIONS = new Set([
  'user:write',
  'user:delete',
  'role:write',
  'role:delete',
  'system:admin',
  'system:config',
  'tenant:admin',
])

/**
 * Check if user has a specific permission
 */
export function hasPermission(permission: string): boolean {
  const user = getUser()
  if (!user) return false
  
  // System admin has all permissions (also bypasses AUDITOR deny-list when roles are stacked)
  if (user.roles?.includes('SYS_ADMIN') || user.roles?.includes('SUPER_ADMIN')) {
    return true
  }

  const auditorOnly = user.roles?.includes('AUDITOR')
  if (auditorOnly && AUDITOR_DENIED_PERMISSIONS.has(permission)) {
    return false
  }
  
  // Check explicit permissions from JWT
  if (user.permissions?.includes(permission)) return true
  
  // Fallback: derive permissions from roles when sys_role_permissions table is empty
  for (const role of (user.roles ?? [])) {
    if (ROLE_PERMISSION_DEFAULTS[role]?.includes(permission)) return true
  }
  
  return false
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

function findRoutePermissions(path: string): string[] | undefined {
  if (ROUTE_PERMISSIONS[path]) return ROUTE_PERMISSIONS[path]
  const segments = path.split('/')
  while (segments.length > 1) {
    segments.pop()
    const prefix = segments.join('/')
    if (ROUTE_PERMISSIONS[prefix]) return ROUTE_PERMISSIONS[prefix]
  }
  return undefined
}

/**
 * Check if user can access a specific route
 */
export function canAccessRoute(path: string): boolean {
  const permissions = findRoutePermissions(path)
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
  const t = i18n.global.t
  const user = getUser()
  if (!user?.roles?.length) return t('role.unknown')
  
  const roleNameKeys: Record<string, string> = {
    'SYS_ADMIN': 'role.sysAdmin',
    'SUPER_ADMIN': 'role.superAdmin',
    'AUDITOR': 'role.auditor',
    'TENANT_ADMIN': 'role.tenantAdmin'
  }
  
  return user.roles.map(r => roleNameKeys[r] ? t(roleNameKeys[r]) : r).join(', ')
}
