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
  TENANT_ADMIN: 'tenant:admin',

  // Gateway Governance
  GATEWAY_API_READ: 'gateway:api:read',
  GATEWAY_API_WRITE: 'gateway:api:write',
  GATEWAY_APP_READ: 'gateway:application:read',
  GATEWAY_APP_WRITE: 'gateway:application:write',
  GATEWAY_POLICY_READ: 'gateway:policy:read',
  GATEWAY_POLICY_WRITE: 'gateway:policy:write',
  GATEWAY_RELEASE_READ: 'gateway:release:read',
  GATEWAY_RELEASE_EXECUTE: 'gateway:release:execute',
  GATEWAY_ENV_READ: 'gateway:environment:read',
  GATEWAY_ENV_WRITE: 'gateway:environment:write',
  GATEWAY_AUDIT_READ: 'gateway:audit:read',
  // Phase 2
  GATEWAY_DRIFT_READ: 'gateway:drift:read',
  GATEWAY_DRIFT_SYNC: 'gateway:drift:sync',
  GATEWAY_MONITORING_READ: 'gateway:monitoring:read',
  GATEWAY_RELEASE_APPROVE: 'gateway:release:approve',
  GATEWAY_RELEASE_PROMOTE: 'gateway:release:promote',

  // MFE Governance
  MFE_MODULE_READ: 'frontend.module:read',
  MFE_MODULE_WRITE: 'frontend.module:write',
  MFE_MODULE_ENABLE: 'frontend.module:enable',
  MFE_MODULE_VERSION_SWITCH: 'frontend.module:version:switch',
  MFE_MODULE_VERSION_ROLLBACK: 'frontend.module:version:rollback',
  MFE_MODULE_RUNTIME_READ: 'frontend.module:runtime:read'
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
  '/profile': [], // Everyone can access their profile
  '/relation-tables/structure': [PERMISSIONS.SYSTEM_ADMIN],
  '/relation-tables/structure/create': [PERMISSIONS.SYSTEM_ADMIN],
  '/relation-tables/data': [PERMISSIONS.SYSTEM_ADMIN],

  // Gateway Governance (Phase 3 MFE — consolidated catch-all route)
  '/gateway': [PERMISSIONS.GATEWAY_API_READ, PERMISSIONS.GATEWAY_APP_READ, PERMISSIONS.GATEWAY_RELEASE_READ, PERMISSIONS.GATEWAY_AUDIT_READ, PERMISSIONS.GATEWAY_DRIFT_READ, PERMISSIONS.GATEWAY_MONITORING_READ],

  // MFE Governance
  '/mfe/modules': [PERMISSIONS.MFE_MODULE_READ]
}

// Hardcoded role -> permissions fallback for when sys_role_permissions is empty
const ROLE_PERMISSION_DEFAULTS: Record<string, string[]> = {
  SYS_ADMIN: ['user:read', 'user:write', 'user:delete', 'role:read', 'role:write', 'role:delete', 'system:admin', 'system:config', 'audit:read', 'log:read', 'basic:access', 'gateway:api:read', 'gateway:api:write', 'gateway:application:read', 'gateway:application:write', 'gateway:policy:read', 'gateway:policy:write', 'gateway:release:read', 'gateway:release:execute', 'gateway:environment:read', 'gateway:environment:write', 'gateway:audit:read', 'gateway:drift:read', 'gateway:drift:sync', 'gateway:monitoring:read', 'gateway:release:approve', 'gateway:release:promote', 'frontend.module:read', 'frontend.module:write', 'frontend.module:enable', 'frontend.module:version:switch', 'frontend.module:version:rollback', 'frontend.module:runtime:read'],
  SUPER_ADMIN: ['user:read', 'user:write', 'user:delete', 'role:read', 'role:write', 'role:delete', 'system:admin', 'system:config', 'audit:read', 'log:read', 'basic:access', 'gateway:api:read', 'gateway:api:write', 'gateway:application:read', 'gateway:application:write', 'gateway:policy:read', 'gateway:policy:write', 'gateway:release:read', 'gateway:release:execute', 'gateway:environment:read', 'gateway:environment:write', 'gateway:audit:read', 'gateway:drift:read', 'gateway:drift:sync', 'gateway:monitoring:read', 'gateway:release:approve', 'gateway:release:promote', 'frontend.module:read', 'frontend.module:write', 'frontend.module:enable', 'frontend.module:version:switch', 'frontend.module:version:rollback', 'frontend.module:runtime:read'],
  AUDITOR: ['audit:read', 'log:read', 'user:read', 'basic:access', 'gateway:drift:read', 'gateway:monitoring:read'],
  SYS_ADMIN_GATEWAY: ['gateway:api:read', 'gateway:api:write', 'gateway:application:read', 'gateway:application:write', 'gateway:policy:read', 'gateway:policy:write', 'gateway:release:read', 'gateway:release:execute', 'gateway:environment:read', 'gateway:environment:write', 'gateway:audit:read', 'gateway:drift:read', 'gateway:drift:sync', 'gateway:monitoring:read', 'gateway:release:approve', 'gateway:release:promote'],
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
