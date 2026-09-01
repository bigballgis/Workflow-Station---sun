/**
 * Current signed-in user for Form Design event scripts (`user` / `$inject.user`).
 * Keep in sync with developer-workstation/src/utils/formCreateEventUser.ts
 */

import { getUser } from '@/api/auth'

export type FormEventUser = {
  userId: string
  username: string
  displayName: string
  email: string
  roles: string[]
  language: string
  activeBusinessUnitId?: string
  activeBusinessUnitName?: string
  activeRoleId?: string
  activeRoleName?: string
}

function asString(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function optionalString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined
}

/** Fresh snapshot each event. No tokens. Returns null when nobody is signed in. */
export function readFormEventCurrentUser(): FormEventUser | null {
  const raw = getUser()
  if (!raw || typeof raw !== 'object') return null
  const u = raw as Record<string, unknown>
  const userId = asString(u.userId)
  const username = asString(u.username)
  if (!userId && !username) return null
  const roles = Array.isArray(u.roles)
    ? u.roles.filter((item): item is string => typeof item === 'string')
    : []
  const user: FormEventUser = {
    userId,
    username,
    displayName: asString(u.displayName),
    email: asString(u.email),
    roles,
    language: asString(u.language),
  }
  const buId = optionalString(u.activeBusinessUnitId)
  const buName = optionalString(u.activeBusinessUnitName)
  const roleId = optionalString(u.activeRoleId)
  const roleName = optionalString(u.activeRoleName)
  if (buId) user.activeBusinessUnitId = buId
  if (buName) user.activeBusinessUnitName = buName
  if (roleId) user.activeRoleId = roleId
  if (roleName) user.activeRoleName = roleName
  return user
}
