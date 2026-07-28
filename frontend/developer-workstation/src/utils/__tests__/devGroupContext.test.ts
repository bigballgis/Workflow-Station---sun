import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ALL_GROUPS,
  clearActiveGroup,
  getActiveGroupHeaderValue,
  getActiveGroupRaw,
  setActiveGroup,
} from '../devGroupContext'
describe('devGroupContext', () => {
  const storage = new Map<string, string>()
  beforeEach(() => {
    storage.clear()
    vi.stubGlobal('localStorage', {
      getItem: vi.fn((key: string) => storage.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => storage.set(key, value)),
      removeItem: vi.fn((key: string) => storage.delete(key)),
    })
  })
  afterEach(() => {
    clearActiveGroup()
    vi.unstubAllGlobals()
  })
  it('does not send the all-groups sentinel as a scope header', () => {
    setActiveGroup(ALL_GROUPS)
    expect(getActiveGroupRaw()).toBe(ALL_GROUPS)
    expect(getActiveGroupHeaderValue()).toBeNull()
  })
  it('sends the Public group as a concrete scope header', () => {
    setActiveGroup('vg-dev-public')
    expect(getActiveGroupHeaderValue()).toBe('vg-dev-public')
  })
})