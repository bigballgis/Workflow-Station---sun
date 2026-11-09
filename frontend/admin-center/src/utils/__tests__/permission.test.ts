import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { USER_KEY } from '@/api/auth'
import type { UserInfo } from '@/api/auth'
import type { RouteMeta } from 'vue-router'
import { accessLevel, canAccessRoute, canRead, canWrite, isTeamScoped } from '../permission'

function user(partial: Partial<UserInfo>): UserInfo {
  return {
    userId: 'u1',
    username: 'tester',
    displayName: 'Tester',
    email: 'tester@example.com',
    roles: [],
    permissions: [],
    language: 'en',
    hasAvatar: false,
    ...partial,
  }
}

describe('admin-center permission (capabilities from the backend)', () => {
  const storage = new Map<string, string>()

  beforeEach(() => {
    storage.clear()
    vi.stubGlobal('localStorage', {
      getItem: vi.fn((key: string) => storage.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => {
        storage.set(key, value)
      }),
      removeItem: vi.fn((key: string) => {
        storage.delete(key)
      }),
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function setUser(info: UserInfo): void {
    storage.set(USER_KEY, JSON.stringify(info))
  }

  it('reads only the capability table, never the roles or permission codes', () => {
    setUser(user({
      roles: ['SYS_ADMIN'],
      permissions: ['system:admin', 'user:write'],
      adminAccess: { users: 'READ' },
    }))
    expect(canRead('users')).toBe(true)
    expect(canWrite('users')).toBe(false)
    expect(canRead('functionUnits')).toBe(false)
  })

  it('treats TEAM as writable but team-scoped', () => {
    setUser(user({ adminAccess: { functionUnits: 'TEAM', organization: 'WRITE', systemConfig: 'READ' } }))
    expect(canWrite('functionUnits')).toBe(true)
    expect(isTeamScoped('functionUnits')).toBe(true)
    expect(canWrite('organization')).toBe(true)
    expect(isTeamScoped('organization')).toBe(false)
    expect(canWrite('systemConfig')).toBe(false)
    expect(canRead('systemConfig')).toBe(true)
  })

  it('denies everything for a profile stored before the capability table existed', () => {
    setUser(user({ roles: ['AUDITOR'] }))
    expect(accessLevel('users')).toBe('NONE')
    expect(canRead('auditAdminCenter')).toBe(false)
  })

  it('opens a route by its feature and a write route only with write access', () => {
    setUser(user({ adminAccess: { users: 'READ', relationTables: 'TEAM' } }))
    const route = (meta: RouteMeta) => ({ matched: [{ meta: { requiresAuth: true } as RouteMeta }, { meta }] })
    expect(canAccessRoute(route({}))).toBe(true)
    expect(canAccessRoute(route({ feature: 'users' }))).toBe(true)
    expect(canAccessRoute(route({ feature: 'users', write: true }))).toBe(false)
    expect(canAccessRoute(route({ feature: 'relationTables', write: true }))).toBe(true)
    expect(canAccessRoute(route({ feature: 'systemConfig' }))).toBe(false)
  })
})
