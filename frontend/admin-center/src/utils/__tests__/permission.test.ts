import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { USER_KEY } from '@/api/auth'
import type { UserInfo } from '@/api/auth'
import { hasPermission, PERMISSIONS } from '../permission'

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

describe('admin-center permission', () => {
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

  it('grants auditor the default read permissions', () => {
    setUser(user({ roles: ['AUDITOR'] }))
    expect(hasPermission(PERMISSIONS.USER_READ)).toBe(true)
    expect(hasPermission(PERMISSIONS.AUDIT_READ)).toBe(true)
    expect(hasPermission(PERMISSIONS.DASHBOARD)).toBe(true)
  })

  it('denies auditor write permissions even if the token lists them', () => {
    setUser(user({
      roles: ['AUDITOR'],
      permissions: [PERMISSIONS.USER_WRITE, PERMISSIONS.ROLE_WRITE, PERMISSIONS.SYSTEM_ADMIN],
    }))
    expect(hasPermission(PERMISSIONS.USER_WRITE)).toBe(false)
    expect(hasPermission(PERMISSIONS.ROLE_WRITE)).toBe(false)
    expect(hasPermission(PERMISSIONS.SYSTEM_ADMIN)).toBe(false)
  })

  it('lets SYS_ADMIN bypass the auditor deny-list when roles are stacked', () => {
    setUser(user({ roles: ['SYS_ADMIN', 'AUDITOR'] }))
    expect(hasPermission(PERMISSIONS.USER_WRITE)).toBe(true)
    expect(hasPermission(PERMISSIONS.SYSTEM_ADMIN)).toBe(true)
  })
})
