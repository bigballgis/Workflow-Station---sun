import { getStoredUser } from '@/api/auth'

/**
 * Permission utility functions for role-based access control.
 *
 * 二维模型（团队 scope × 能力角色）下的能力角色：
 * - TECH_LEAD: 全局全权（CREATE, EDIT, DELETE, DEPLOY, PUBLISH, CLONE），不受团队隔离
 * - TEAM_LEAD: CREATE, EDIT, DELETE, DEPLOY, PUBLISH, CLONE（仅团队 scope 内，后端校验）
 * - DEVELOPER: EDIT, DEPLOY, PUBLISH（仅团队 scope 内；不能 CREATE / DELETE / CLONE）
 * - FU_VIEWER: 团队只读基线——不属于任何能力角色列表 → 前端不显示任何编辑入口
 *
 * 说明：这里的按钮可见性仅为 UX；具体某个 FU 能否被编辑由后端工作区隔离
 * （FunctionUnitWorkspaceAccessService）按团队 scope 逐 FU 校验。
 */

/**
 * Check if the current user has a specific role
 */
export function hasRole(roleCode: string): boolean {
  const user = getStoredUser()
  if (!user || !user.roles) {
    return false
  }
  return user.roles.includes(roleCode)
}

/**
 * Check if the current user has any of the specified roles
 */
export function hasAnyRole(roleCodes: string[]): boolean {
  const user = getStoredUser()
  if (!user || !user.roles) {
    return false
  }
  return roleCodes.some(roleCode => user.roles.includes(roleCode))
}

/**
 * Check if the current user has all of the specified roles
 */
export function hasAllRoles(roleCodes: string[]): boolean {
  const user = getStoredUser()
  if (!user || !user.roles) {
    return false
  }
  return roleCodes.every(roleCode => user.roles.includes(roleCode))
}

/**
 * Check if the current user has an ADMIN-type role (SYS_ADMIN).
 * Mirrors backend FunctionUnitWorkspaceAccessService.userHasActiveAdminTypeRole()
 * and DeveloperPermissionService.getUserPermissions() ADMIN bypass.
 */
function isAdmin(): boolean {
  return hasRole('SYS_ADMIN')
}

/**
 * Permission checks for function unit operations.
 * SYS_ADMIN bypasses all checks — mirrors backend workspace-access and
 * developer-permission rules (ADMIN type → full access).
 */
export const permissions = {
  /**
   * Can create new function units
   * Allowed: SYS_ADMIN, TECH_LEAD, TEAM_LEAD
   */
  canCreate(): boolean {
    return isAdmin() || hasAnyRole(['TECH_LEAD', 'TEAM_LEAD'])
  },

  /**
   * Can edit existing function units
   * Allowed: SYS_ADMIN, TECH_LEAD, TEAM_LEAD, DEVELOPER
   */
  canEdit(): boolean {
    return isAdmin() || hasAnyRole(['TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER'])
  },

  /**
   * Can delete function units.
   * Allowed: SYS_ADMIN, TECH_LEAD (global) or TEAM_LEAD (within team scope, backend-enforced).
   */
  canDelete(): boolean {
    return isAdmin() || hasAnyRole(['TECH_LEAD', 'TEAM_LEAD'])
  },

  /**
   * Can publish function units
   * Allowed: SYS_ADMIN, TECH_LEAD, TEAM_LEAD, DEVELOPER
   */
  canPublish(): boolean {
    return isAdmin() || hasAnyRole(['TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER'])
  },

  /**
   * Can deploy function units
   * Allowed: SYS_ADMIN, TECH_LEAD, TEAM_LEAD, DEVELOPER
   */
  canDeploy(): boolean {
    return isAdmin() || hasAnyRole(['TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER'])
  },

  /**
   * Can clone function units (creates a new one)
   * Allowed: SYS_ADMIN, TECH_LEAD, TEAM_LEAD
   */
  canClone(): boolean {
    return isAdmin() || hasAnyRole(['TECH_LEAD', 'TEAM_LEAD'])
  },

  /**
   * Can assign a function unit to teams (virtual dev groups).
   * Allowed: SYS_ADMIN, TECH_LEAD (global) or TEAM_LEAD (within team scope, backend-enforced).
   */
  canAssignDevGroups(): boolean {
    return isAdmin() || hasAnyRole(['TECH_LEAD', 'TEAM_LEAD'])
  }
}

/**
 * Get the current user's role display name
 */
export function getCurrentRoleName(): string {
  const user = getStoredUser()
  if (!user || !user.roles || user.roles.length === 0) {
    return 'No Role'
  }
  
  // Return the first role (users typically have one primary role)
  const roleCode = user.roles[0]
  const roleNames: Record<string, string> = {
    'TECH_LEAD': 'Technical Lead',
    'TEAM_LEAD': 'Team Lead',
    'DEVELOPER': 'Developer',
    'FU_VIEWER': 'Function Unit Viewer',
    'SYS_ADMIN': 'System Administrator',
    'AUDITOR': 'Auditor',
    'MANAGER': 'Manager'
  }
  
  return roleNames[roleCode] || roleCode
}
