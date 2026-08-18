import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { USER_KEY } from '@/api/auth'
import type { UserInfo } from '@/api/auth'
import {
  canModifyFunctionUnits,
  isAuditorBlockedFromWorkstation,
  isCurrentScopeReadOnly,
  isFunctionUnitReadOnly,
  permissions,
} from '../permission'
import { PUBLIC_GROUP_ID, setActiveGroup, clearActiveGroup, ALL_GROUPS } from '../devGroupContext'

function user(roles: string[]): UserInfo {
  return {
    userId: 'u1',
    username: 'tester',
    displayName: 'Tester',
    email: 'tester@example.com',
    roles,
    permissions: [],
    language: 'en',
    hasAvatar: false,
  }
}

describe('developer-workstation permission', () => {
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
    clearActiveGroup()
    vi.unstubAllGlobals()
  })

  function setRoles(roles: string[]): void {
    storage.set(USER_KEY, JSON.stringify(user(roles)))
  }

  it('blocks a pure auditor from the workstation', () => {
    setRoles(['AUDITOR'])
    expect(isAuditorBlockedFromWorkstation()).toBe(true)
  })

  it('lets auditor overlay with a capability role enter', () => {
    setRoles(['AUDITOR', 'DEVELOPER'])
    expect(isAuditorBlockedFromWorkstation()).toBe(false)
    expect(permissions.canEdit()).toBe(true)
  })

  it('treats missing canModify as read-only', () => {
    expect(isFunctionUnitReadOnly({})).toBe(true)
    expect(isFunctionUnitReadOnly({ canModify: false })).toBe(true)
    expect(isFunctionUnitReadOnly({ canModify: true })).toBe(false)
    expect(isFunctionUnitReadOnly(null)).toBe(true)
  })

  it('makes the Public scope read-only except for SYS_ADMIN', () => {
    setActiveGroup(PUBLIC_GROUP_ID)
    setRoles(['DEVELOPER'])
    expect(isCurrentScopeReadOnly()).toBe(true)
    expect(canModifyFunctionUnits()).toBe(false)

    setRoles(['SYS_ADMIN'])
    expect(isCurrentScopeReadOnly()).toBe(false)
    expect(canModifyFunctionUnits()).toBe(true)
  })

  it('does not treat the all-groups sentinel as the Public scope', () => {
    setRoles(['TECH_LEAD'])
    setActiveGroup(ALL_GROUPS)
    expect(isCurrentScopeReadOnly()).toBe(false)
  })
})
