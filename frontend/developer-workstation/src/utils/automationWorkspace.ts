/**
 * Automation workspace context.
 *
 * Automation flows live in the workspace (dev team) that owns them — the backend maps the
 * selected team to its own Activepieces project and mints the session for that project only.
 * The workspace therefore follows the SAME selection as the rest of the Developer Workstation
 * ({@link getActiveGroupRaw}); this module only adds the one case that selection cannot express:
 *
 * "All teams" ({@link ALL_GROUPS}, global-view users) has no single project to talk to — an AP
 * session is always scoped to exactly one project. Those users pick a workspace on the Automation
 * page itself, and that choice is stored here. It is deliberately ignored while a concrete team is
 * selected in the header, so switching teams up there never leaves Automation on the old one.
 */
import { ALL_GROUPS, getActiveGroupRaw } from './devGroupContext'

const AUTOMATION_WORKSPACE_KEY = 'ws_dw_automation_workspace'

/**
 * Workspace to request from the backend, or null for "not chosen" (backend resolves that to the
 * Public workspace, which carries the shared / legacy flows).
 */
export function getAutomationWorkspaceId(): string | null {
  const active = getActiveGroupRaw()
  if (active && active !== ALL_GROUPS) {
    return active
  }
  return localStorage.getItem(AUTOMATION_WORKSPACE_KEY)
}

/** Whether the in-page picker applies (global-view users on "All teams"). */
export function isAutomationWorkspacePickable(): boolean {
  const active = getActiveGroupRaw()
  return !active || active === ALL_GROUPS
}

/** Persist the in-page choice; pass null to fall back to the Public workspace. */
export function setAutomationWorkspaceId(groupId: string | null): void {
  if (groupId) {
    localStorage.setItem(AUTOMATION_WORKSPACE_KEY, groupId)
  } else {
    localStorage.removeItem(AUTOMATION_WORKSPACE_KEY)
  }
}
