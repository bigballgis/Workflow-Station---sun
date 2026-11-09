import type { RouteMeta } from 'vue-router'
import { getUser, type AdminAccessLevel, type AdminFeature } from '@/api/auth'

/**
 * What the signed-in user may do on each Admin Center page. The backend computes it from the user's platform roles
 * (`AdminCapabilities`) and sends it with login, refresh and `/auth/me`; the frontend keeps no role table of its own.
 *
 * - `READ`: the page is visible, every write entry is hidden.
 * - `TEAM`: writes limited to the teams the user leads; each row says whether it is one of them (`canManage`, ...).
 * - `WRITE`: everything.
 */
export function accessLevel(feature: AdminFeature): AdminAccessLevel {
  return getUser()?.adminAccess?.[feature] ?? 'NONE'
}

export function canRead(feature: AdminFeature): boolean {
  return accessLevel(feature) !== 'NONE'
}

/** True at `TEAM` too: the page shows write entries, rows decide where they apply. */
export function canWrite(feature: AdminFeature): boolean {
  const level = accessLevel(feature)
  return level === 'WRITE' || level === 'TEAM'
}

export function isTeamScoped(feature: AdminFeature): boolean {
  return accessLevel(feature) === 'TEAM'
}

/** A route's `meta.feature` must be readable (and writable when `meta.write`); routes without a feature are open. */
export function canAccessRoute(route: { matched: { meta: RouteMeta }[] }): boolean {
  return route.matched.every(({ meta }) => {
    if (!meta.feature) return true
    return meta.write ? canWrite(meta.feature) : canRead(meta.feature)
  })
}
