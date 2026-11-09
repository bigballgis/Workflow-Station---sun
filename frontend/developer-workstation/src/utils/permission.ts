import { getStoredUser } from '@/api/auth'
import {
  ALL_GROUPS,
  canWritePublic,
  getActiveGroupRaw,
  getMyTeams,
  PUBLIC_GROUP_ID,
} from '@/utils/devGroupContext'

/**
 * Permission helpers for the Developer Workstation (platform access model §6).
 *
 * What a user may do depends on the list they are on in each team, not on a global role:
 * - Technical Lead of a team: everything in that team (create, edit, deploy, delete, restore, clone, move);
 * - Developer of a team: view, edit the design, publish, deploy and roll back in that team;
 * - SYS_ADMIN: everything in every workspace, Public included; AUDITOR: read-only everywhere.
 *
 * The backend decides: per function unit (`canModify`, `canManage`) and per team (`canCreate`, `canWritePublic`
 * from my-dev-groups). These helpers only read those flags, so a button never offers what the backend refuses.
 * A missing flag means no.
 */

/** Everyone who may enter the workstation (§6.1). */
export const DW_ENTRY_ROLES = ['SYS_ADMIN', 'AUDITOR', 'TECH_LEAD', 'DEVELOPER']

/** Roles that write somewhere in the workstation; an Auditor only reads. */
const DW_WRITE_ROLES = ['SYS_ADMIN', 'TECH_LEAD', 'DEVELOPER']

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
 * Whether the selected workspace is read-only for the user: always for an Auditor; Public for everyone but a
 * System Administrator; a team the user is on neither list of. "All" spans the user's own teams.
 */
export function isCurrentScopeReadOnly(): boolean {
  if (!hasAnyRole(DW_WRITE_ROLES)) return true
  if (hasRole('SYS_ADMIN')) return false
  const selected = getActiveGroupRaw()
  if (selected === PUBLIC_GROUP_ID) return true
  if (!selected || selected === ALL_GROUPS) return false
  const team = getMyTeams().find(t => t.id === selected)
  return !(team && team.status === 'ACTIVE' && (team.techLead || team.developer))
}

/** Whether the user may create in the selected workspace. */
export function canCreateInActiveScope(): boolean {
  return canCreateInWorkspace(getActiveGroupRaw())
}

/** Whether a new, imported, cloned or moved unit may land in this workspace for the user (§6.2, §6.3). */
export function canCreateInWorkspace(groupId: string | null): boolean {
  if (groupId === PUBLIC_GROUP_ID) return canWritePublic()
  if (!groupId || groupId === ALL_GROUPS) return false
  return getMyTeams().some(t => t.id === groupId && t.canCreate === true)
}

/**
 * Resource-level write flag from the backend. Missing `canModify` is false.
 */
export function isFunctionUnitReadOnly(item: { canModify?: boolean } | null | undefined): boolean {
  return item?.canModify !== true
}

/** Delete, restore, move or import a new version: the workspace's Technical Lead or a System Administrator. */
export function canManageFunctionUnit(item: { canManage?: boolean } | null | undefined): boolean {
  return item?.canManage === true
}

/**
 * Clone any unit the user can see (D-35): it only takes a workspace the copy may land in — a team they lead, or
 * Public for a System Administrator.
 */
export function canCloneFunctionUnits(): boolean {
  return canWritePublic() || getMyTeams().some(t => t.canCreate === true)
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
    'DEVELOPER': 'Developer',
    'SYS_ADMIN': 'System Administrator',
    'AUDITOR': 'Auditor',
    'MANAGER': 'Manager'
  }

  return roleNames[roleCode] || roleCode
}
