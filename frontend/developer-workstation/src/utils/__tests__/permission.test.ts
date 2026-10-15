import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { USER_KEY } from '@/api/auth'
import type { UserInfo } from '@/api/auth'
import {
  canCreateInActiveScope,
  canCreateInWorkspace,
  canModifyFunctionUnits,
  isAuditorBlockedFromWorkstation,
  isCurrentScopeReadOnly,
  isFunctionUnitReadOnly,
  permissions,
} from '../permission'
import {
  PUBLIC_GROUP_ID,
  setActiveGroup,
  clearActiveGroup,
  setMemberGroupIds,
  ALL_GROUPS,
} from '../devGroupContext'

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
    expect(canCreateInActiveScope()).toBe(false)

    setRoles(['SYS_ADMIN'])
    expect(isCurrentScopeReadOnly()).toBe(false)
    expect(canModifyFunctionUnits()).toBe(true)
    expect(canCreateInActiveScope()).toBe(true)
  })

  it('keeps other workspaces read-only for an admin without that workspace role', () => {
    setActiveGroup('vg-team-a')
    setMemberGroupIds([])
    setRoles(['SYS_ADMIN'])
    expect(isCurrentScopeReadOnly()).toBe(true)
    expect(canCreateInActiveScope()).toBe(false)
    expect(canModifyFunctionUnits()).toBe(false)
  })

  it('lets an admin write a workspace they belong to when they hold its role', () => {
    setActiveGroup('vg-team-a')
    setMemberGroupIds(['vg-team-a'])
    setRoles(['SYS_ADMIN', 'DEVELOPER'])
    expect(isCurrentScopeReadOnly()).toBe(false)
    expect(canModifyFunctionUnits()).toBe(true)
    expect(canCreateInActiveScope()).toBe(false)

    setRoles(['SYS_ADMIN', 'TEAM_LEAD'])
    expect(canCreateInActiveScope()).toBe(true)
  })

  it('never offers private creation destinations outside membership or Public to non-admins', () => {
    setMemberGroupIds(['vg-team-a'])
    setRoles(['SYS_ADMIN', 'TECH_LEAD'])
    expect(canCreateInWorkspace('vg-team-a')).toBe(true)
    expect(canCreateInWorkspace('vg-team-b')).toBe(false)
    expect(canCreateInWorkspace(PUBLIC_GROUP_ID)).toBe(true)
    setRoles(['TECH_LEAD'])
    expect(canCreateInWorkspace('vg-team-a')).toBe(true)
    expect(canCreateInWorkspace(PUBLIC_GROUP_ID)).toBe(false)
    setRoles(['SYS_ADMIN'])
    expect(canCreateInWorkspace('vg-team-a')).toBe(false)
    expect(canCreateInWorkspace(PUBLIC_GROUP_ID)).toBe(true)
  })

  it('does not treat the all-groups sentinel as the Public scope', () => {
    setRoles(['TECH_LEAD'])
    setActiveGroup(ALL_GROUPS)
    expect(isCurrentScopeReadOnly()).toBe(false)
  })
})
