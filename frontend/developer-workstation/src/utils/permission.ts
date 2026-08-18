import { getStoredUser } from '@/api/auth'
import { isPublicGroupSelected } from '@/utils/devGroupContext'

/**
 * Permission utility functions for role-based access control.
 *
 * 二维模型（团队 scope × 能力角色）下的能力角色：
 * - TECH_LEAD: 全局全权（CREATE, EDIT, DELETE, DEPLOY, PUBLISH, CLONE），不受团队隔离
 * - TEAM_LEAD: CREATE, EDIT, DELETE, DEPLOY, PUBLISH, CLONE（仅团队 scope 内，后端校验）
 * - DEVELOPER: EDIT, DEPLOY, PUBLISH（仅团队 scope 内；不能 CREATE / DELETE / CLONE）
 * - 无能力角色的团队成员：只读基线（后端 workspace-access / 团队 scope 校验）
 *
 * SYS_ADMIN is the only global write bypass. AUDITOR is a read overlay; a pure
 * Auditor is blocked at the DW router, but these helpers stay in place so an
 * overlay (AUDITOR + developer capability) or a later product change can reuse them.
 *
 * 说明：这里的按钮可见性仅为 UX；具体某个 FU 能否被编辑由后端工作区隔离
 * （FunctionUnitWorkspaceAccessService）按团队 scope 逐 FU 校验，并以资源级
 * `canModify` 为准（缺失时 fail-closed）。
 */

const DW_CAPABILITY_ROLES = ['SYS_ADMIN', 'TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER'] as const

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
 * Check if the current user is SYS_ADMIN. Mirrors backend
 * FunctionUnitWorkspaceAccessService: only SYS_ADMIN is global full write.
 */
function isAdmin(): boolean {
  return hasRole('SYS_ADMIN')
}

/**
 * Pure Auditor (no DW capability role) must not enter the workstation UI.
 * Overlay users (AUDITOR + TECH_LEAD/TEAM_LEAD/DEVELOPER) still enter.
 */
export function isAuditorBlockedFromWorkstation(): boolean {
  return hasRole('AUDITOR') && !hasAnyRole([...DW_CAPABILITY_ROLES])
}

/**
 * Whether the current team scope is writeable. Public function units are
 * read-only for everyone except SYS_ADMIN.
 */
export function isCurrentScopeReadOnly(): boolean {
  if (isAdmin()) return false
  return isPublicGroupSelected()
}

/**
 * Whether the current user may mutate function units in the active scope.
 */
export function canModifyFunctionUnits(): boolean {
  return permissions.canEdit() && !isCurrentScopeReadOnly()
}

/**
 * Resource-level write flag from the backend. Missing `canModify` is false.
 */
export function isFunctionUnitReadOnly(item: { canModify?: boolean } | null | undefined): boolean {
  return item?.canModify !== true
}

/**
 * Permission checks for function unit operations.
 * SYS_ADMIN bypasses all checks — mirrors backend workspace-access and
 * developer-permission rules (SYS_ADMIN → full access).
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
  },

  /**
   * Can freely (re)assign a function unit's team via an editable selector in create/settings.
   * Allowed: SYS_ADMIN (any team incl. Public) or TECH_LEAD (own teams + Public).
   * Regular creators (TEAM_LEAD) get a read-only team = their currently selected team.
   * Backend re-validates the chosen teams against the caller's scope.
   */
  canReassignTeam(): boolean {
    return isAdmin() || hasRole('TECH_LEAD')
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
    'SYS_ADMIN': 'System Administrator',
    'AUDITOR': 'Auditor',
    'MANAGER': 'Manager'
  }
  
  return roleNames[roleCode] || roleCode
}
