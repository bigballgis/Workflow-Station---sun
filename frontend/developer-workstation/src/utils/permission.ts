import { getStoredUser } from '@/api/auth'

/**
 * Permission utility functions for role-based access control
 * 
 * Role hierarchy:
 * - TECH_LEAD: Full permissions (CREATE, EDIT, DELETE, DEPLOY, PUBLISH)
 * - TEAM_LEAD: CREATE, EDIT, DEPLOY, PUBLISH (no DELETE)
 * - DEVELOPER: EDIT, DEPLOY, PUBLISH (no CREATE, no DELETE)
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
 * Permission checks for function unit operations
 */
export const permissions = {
  /**
   * Can create new function units
   * Allowed: TECH_LEAD, TEAM_LEAD
   */
  canCreate(): boolean {
    return hasAnyRole(['TECH_LEAD', 'TEAM_LEAD'])
  },

  /**
   * Can edit existing function units
   * Allowed: TECH_LEAD, TEAM_LEAD, DEVELOPER
   */
  canEdit(): boolean {
    return hasAnyRole(['TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER'])
  },

  /**
   * Can delete function units
   * Allowed: TECH_LEAD only
   */
  canDelete(): boolean {
    return hasRole('TECH_LEAD')
  },

  /**
   * Can publish function units
   * Allowed: TECH_LEAD, TEAM_LEAD, DEVELOPER
   */
  canPublish(): boolean {
    return hasAnyRole(['TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER'])
  },

  /**
   * Can deploy function units
   * Allowed: TECH_LEAD, TEAM_LEAD, DEVELOPER
   */
  canDeploy(): boolean {
    return hasAnyRole(['TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER'])
  },

  /**
   * Can clone function units (creates a new one)
   * Allowed: TECH_LEAD, TEAM_LEAD
   */
  canClone(): boolean {
    return hasAnyRole(['TECH_LEAD', 'TEAM_LEAD'])
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
