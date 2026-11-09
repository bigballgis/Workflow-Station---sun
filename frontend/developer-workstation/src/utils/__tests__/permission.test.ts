import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { USER_KEY } from '@/api/auth'
import type { UserInfo } from '@/api/auth'
import type { DevGroupOption } from '@/api/functionUnit'
import {
  canCloneFunctionUnits,
  canCreateInActiveScope,
  canCreateInWorkspace,
  canManageFunctionUnit,
  isCurrentScopeReadOnly,
  isFunctionUnitReadOnly,
} from '../permission'
import {
  PUBLIC_GROUP_ID,
  setActiveGroup,
  clearActiveGroup,
  setMyDevGroups,
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

function team(id: string, lists: { techLead?: boolean; developer?: boolean; canCreate?: boolean; status?: string }): DevGroupOption {
  return { id, name: id, status: lists.status ?? 'ACTIVE', ...lists }
}

/** Platform access model §6.2: what the buttons offer follows the backend's per-team and per-unit flags. */
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

  it('treats missing unit flags as no', () => {
    expect(isFunctionUnitReadOnly({})).toBe(true)
    expect(isFunctionUnitReadOnly({ canModify: false })).toBe(true)
    expect(isFunctionUnitReadOnly({ canModify: true })).toBe(false)
    expect(isFunctionUnitReadOnly(null)).toBe(true)
    expect(canManageFunctionUnit({})).toBe(false)
    expect(canManageFunctionUnit({ canManage: true })).toBe(true)
    expect(canManageFunctionUnit(undefined)).toBe(false)
  })

  // D-35: cloning needs a workspace to land in, not rights on the source unit.
  it('offers cloning to whoever has a workspace a copy may land in', () => {
    setMyDevGroups([team('vg-b', { developer: true })], false)
    expect(canCloneFunctionUnits()).toBe(false)
    setMyDevGroups([team('vg-a', { techLead: true, canCreate: true }), team('vg-b', { developer: true })], false)
    expect(canCloneFunctionUnits()).toBe(true)
    setMyDevGroups([], true)
    expect(canCloneFunctionUnits()).toBe(true)
  })

  it('creates only where the backend says the user may', () => {
    setRoles(['TECH_LEAD', 'DEVELOPER'])
    setMyDevGroups([team('vg-a', { techLead: true, canCreate: true }), team('vg-b', { developer: true })], false)

    expect(canCreateInWorkspace('vg-a')).toBe(true)
    expect(canCreateInWorkspace('vg-b')).toBe(false)
    expect(canCreateInWorkspace('vg-c')).toBe(false)
    expect(canCreateInWorkspace(PUBLIC_GROUP_ID)).toBe(false)
    expect(canCreateInWorkspace(ALL_GROUPS)).toBe(false)
    setActiveGroup('vg-a')
    expect(canCreateInActiveScope()).toBe(true)
  })

  it('lets only a System Administrator write Public', () => {
    setActiveGroup(PUBLIC_GROUP_ID)
    setRoles(['TECH_LEAD'])
    setMyDevGroups([team('vg-a', { techLead: true, canCreate: true })], false)
    expect(isCurrentScopeReadOnly()).toBe(true)
    expect(canCreateInActiveScope()).toBe(false)

    setRoles(['SYS_ADMIN'])
    setMyDevGroups([], true)
    expect(isCurrentScopeReadOnly()).toBe(false)
    expect(canCreateInActiveScope()).toBe(true)
  })

  it('makes a team writable for its lists only', () => {
    setRoles(['DEVELOPER'])
    setMyDevGroups([team('vg-b', { developer: true }), team('vg-off', { developer: true, status: 'INACTIVE' })], false)

    setActiveGroup('vg-b')
    expect(isCurrentScopeReadOnly()).toBe(false)
    setActiveGroup('vg-c')
    expect(isCurrentScopeReadOnly()).toBe(true)
    setActiveGroup('vg-off')
    expect(isCurrentScopeReadOnly()).toBe(true)
  })

  it('lets a System Administrator write every team without being on it', () => {
    setRoles(['SYS_ADMIN'])
    setMyDevGroups([team('vg-a', { canCreate: true })], true)
    setActiveGroup('vg-a')
    expect(isCurrentScopeReadOnly()).toBe(false)
    expect(canCreateInActiveScope()).toBe(true)
  })

  it('keeps an Auditor read-only everywhere', () => {
    setRoles(['AUDITOR'])
    setMyDevGroups([team('vg-a', {})], false)
    for (const scope of [ALL_GROUPS, PUBLIC_GROUP_ID, 'vg-a']) {
      setActiveGroup(scope)
      expect(isCurrentScopeReadOnly()).toBe(true)
      expect(canCreateInActiveScope()).toBe(false)
    }
  })

  it('does not treat the all-groups sentinel as the Public scope', () => {
    setRoles(['TECH_LEAD'])
    setActiveGroup(ALL_GROUPS)
    expect(isCurrentScopeReadOnly()).toBe(false)
  })
})
