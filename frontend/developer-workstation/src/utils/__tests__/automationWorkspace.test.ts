import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ALL_GROUPS, clearActiveGroup, setActiveGroup } from '../devGroupContext'
import {
  getAutomationWorkspaceId,
  isAutomationWorkspacePickable,
  setAutomationWorkspaceId,
} from '../automationWorkspace'

describe('automationWorkspace', () => {
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

  it('follows the team selected in the header', () => {
    setActiveGroup('vg-team-alpha')
    expect(getAutomationWorkspaceId()).toBe('vg-team-alpha')
    expect(isAutomationWorkspacePickable()).toBe(false)
  })

  /** Switching teams in the header must move Automation too — a stale in-page pick would
      leave the page on the previous team's flows. */
  it('ignores the in-page pick while a concrete team is selected', () => {
    setAutomationWorkspaceId('vg-team-beta')
    setActiveGroup('vg-team-alpha')
    expect(getAutomationWorkspaceId()).toBe('vg-team-alpha')
  })

  it('uses the in-page pick on all-teams, where no single project exists', () => {
    setActiveGroup(ALL_GROUPS)
    expect(isAutomationWorkspacePickable()).toBe(true)
    expect(getAutomationWorkspaceId()).toBeNull()
    setAutomationWorkspaceId('vg-team-beta')
    expect(getAutomationWorkspaceId()).toBe('vg-team-beta')
  })

  it('clearing the pick falls back to the Public workspace (no header sent)', () => {
    setActiveGroup(ALL_GROUPS)
    setAutomationWorkspaceId('vg-team-beta')
    setAutomationWorkspaceId(null)
    expect(getAutomationWorkspaceId()).toBeNull()
  })
})
