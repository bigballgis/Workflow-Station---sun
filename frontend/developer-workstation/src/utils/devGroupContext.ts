/**
 * Developer-workstation active "dev group (team)" context.
 *
 * The user's currently selected team is stored client-side and sent to the backend on
 * function-unit requests via the `X-Dev-Group-Id` header. It is ONLY a visibility filter:
 * the backend re-validates the user is actually a member before narrowing, so a spoofed
 * value cannot escalate access (non-member selections are ignored server-side).
 *
 * Special value {@link ALL_GROUPS} (`__ALL__`) — admins only — means "do not filter to a
 * single team" (see all function units). It is stored locally but never sent as a header.
 */

const ACTIVE_GROUP_KEY = 'ws_dw_active_group'

/** Admin-only sentinel meaning "all teams" (no single-team filter). */
export const ALL_GROUPS = '__ALL__'

/** Raw stored selection (group id, {@link ALL_GROUPS}, or null when unset). */
export function getActiveGroupRaw(): string | null {
  return localStorage.getItem(ACTIVE_GROUP_KEY)
}

/** Header value to send to the backend, or null when unset / all-groups. */
export function getActiveGroupHeaderValue(): string | null {
  const raw = getActiveGroupRaw()
  if (!raw || raw === ALL_GROUPS) {
    return null
  }
  return raw
}

/** Persist the active team selection (pass {@link ALL_GROUPS} for admin "all"). */
export function setActiveGroup(groupId: string): void {
  localStorage.setItem(ACTIVE_GROUP_KEY, groupId)
}

/** Clear the stored selection (e.g. on logout). */
export function clearActiveGroup(): void {
  localStorage.removeItem(ACTIVE_GROUP_KEY)
}
