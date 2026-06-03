/**
 * MFE Runtime Configuration — fetches from admin-center runtime API.
 * The user-portal API proxy is at /api/portal, so we use a separate
 * base URL for admin-center endpoints.
 */

export interface MfeModuleConfig {
  moduleCode: string
  displayName: string
  routePath: string
  icon: string
  orderNo: number
  remoteEntryUrl: string
  exposedModule: string
  requiredPermissions: string[]
  tenantScope: string[]
  version: string
}

const ADMIN_BASE = '/api/v1/admin'

export async function fetchMfeRuntimeConfig(
  hostApp: string,
  env: string
): Promise<MfeModuleConfig[]> {
  const url = `${ADMIN_BASE}/frontend-modules/runtime?hostApp=${encodeURIComponent(hostApp)}&env=${encodeURIComponent(env)}`
  const res = await fetch(url, { credentials: 'include' })
  if (!res.ok) {
    console.warn(`[MFE] Runtime config fetch failed: ${res.status}`)
    return []
  }
  return res.json()
}
