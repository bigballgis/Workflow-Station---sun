import { ref } from 'vue'
import type { DevGroupOption } from '@/api/functionUnit'

/**
 * Developer-workstation active "dev group (team)" context.
 *
 * The user's currently selected team is stored client-side and sent to the backend on
 * function-unit requests via the `X-Dev-Group-Id` header. It is ONLY a visibility filter:
 * the backend re-validates the user is actually a member before narrowing, so a spoofed
 * value cannot escalate access (non-member selections are ignored server-side).
 *
 * Special value {@link ALL_GROUPS} (`__ALL__`) — global-view users (SYS_ADMIN / AUDITOR
 * overlay / canSeeAllGroups) — means "do not filter to a single team". It is stored
 * locally but never sent as a header. The Public group is a concrete group id and is
 * sent so the backend can return only public function units.
 */

const ACTIVE_GROUP_KEY = 'ws_dw_active_group'

/**
 * The teams from my-dev-groups with the user's lists and create rights per team, and whether the user writes
 * Public (System Administrators only). The backend decides both (platform access model §6.2).
 */
const myTeams = ref<DevGroupOption[]>([])
const publicWritable = ref(false)

/** Sentinel meaning "all teams" (no single-team filter). Used by global-view users. */
export const ALL_GROUPS = '__ALL__'

/** Built-in Public developer group. */
export const PUBLIC_GROUP_ID = 'vg-dev-public'

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

/** Persist the active team selection (pass {@link ALL_GROUPS} for global-view "all"). */
export function setActiveGroup(groupId: string): void {
  localStorage.setItem(ACTIVE_GROUP_KEY, groupId)
}

/** Clear the stored selection (e.g. on logout). */
export function clearActiveGroup(): void {
  localStorage.removeItem(ACTIVE_GROUP_KEY)
  myTeams.value = []
  publicWritable.value = false
}

/** Remember what my-dev-groups said about the user's teams and Public. */
export function setMyDevGroups(groups: readonly DevGroupOption[] | null | undefined, canWritePublic: boolean): void {
  myTeams.value = [...(groups ?? [])]
  publicWritable.value = canWritePublic
}

export function getMyTeams(): readonly DevGroupOption[] { return myTeams.value }

export function canWritePublic(): boolean { return publicWritable.value }
