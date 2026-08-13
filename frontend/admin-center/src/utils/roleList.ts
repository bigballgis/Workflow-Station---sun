import type { Role, RoleType } from '@/api/role'

export type RoleListTab = 'SYSTEM' | 'CUSTOM'

/** System Role List tab: the five named platform roles + Department Manager. */
export const SYSTEM_ROLE_LIST_CODES = new Set([
  'SYS_ADMIN',
  'AUDITOR',
  'TECH_LEAD',
  'TEAM_LEAD',
  'DEVELOPER',
  'MANAGER',
])

export function isSystemRoleListEntry(role: Role): boolean {
  if (role.code === 'FU_VIEWER') return false
  return Boolean(role.isSystem) && SYSTEM_ROLE_LIST_CODES.has(role.code)
}

export function isCustomRoleListEntry(role: Role): boolean {
  return !role.isSystem && role.type === 'BU_BOUNDED'
}

export function filterRolesByTab(roles: Role[], tab: RoleListTab): Role[] {
  return roles.filter((r) =>
    tab === 'SYSTEM' ? isSystemRoleListEntry(r) : isCustomRoleListEntry(r)
  )
}

export function filterRolesByType(roles: Role[], type?: RoleType | ''): Role[] {
  if (!type) return roles
  return roles.filter((r) => r.type === type)
}

export function sortRolesByName(roles: Role[], locale?: string): Role[] {
  return [...roles].sort((a, b) => {
    const an = (a.name || '').trim()
    const bn = (b.name || '').trim()
    if (!an && !bn) return 0
    if (!an) return 1
    if (!bn) return -1
    return an.localeCompare(bn, locale, { sensitivity: 'base' })
  })
}

export function filterSortRoles(
  roles: Role[],
  tab: RoleListTab,
  type: RoleType | '',
  locale?: string
): Role[] {
  return sortRolesByName(filterRolesByType(filterRolesByTab(roles, tab), type), locale)
}

export function paginateRoles(roles: Role[], page: number, size: number): Role[] {
  const p = Math.max(1, page)
  const s = Math.max(1, size)
  const start = (p - 1) * s
  return roles.slice(start, start + s)
}
