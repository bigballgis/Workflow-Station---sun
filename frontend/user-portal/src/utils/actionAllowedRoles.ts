/**
 * Whether a Designer Workstation action button is shown for the signed-in portal session.
 *
 * - No allowedRoles / empty array / unreadable legacy configJson → visible to every portal user.
 * - Non-empty allowedRoles → visible only when session role codes intersect (any one match).
 * - System Administrator → always visible.
 *
 * Display-only; the server does not reject the action (see help actionAllowedRolesGuide.failApi).
 */
export function isActionVisibleForSession(
  configJson: unknown,
  sessionRoleCodes: readonly string[],
  systemAdministrator: boolean,
): boolean {
  if (systemAdministrator) return true
  const allowed = readAllowedRoleCodes(configJson)
  if (allowed.length === 0) return true
  const held = new Set(sessionRoleCodes)
  return allowed.some((code) => held.has(code))
}

function readAllowedRoleCodes(configJson: unknown): string[] {
  if (configJson == null || configJson === '') {
    return []
  }
  let parsed: unknown = configJson
  if (typeof configJson === 'string') {
    try {
      parsed = JSON.parse(configJson)
    } catch {
      // FALLBACK(ux): actionAllowedRolesGuide.failEmpty — unreadable legacy configJson → no role restriction
      return []
    }
  }
  if (parsed == null || typeof parsed !== 'object') {
    return []
  }
  const raw = (parsed as { allowedRoles?: unknown }).allowedRoles
  if (!Array.isArray(raw)) {
    return []
  }
  return raw
    .filter((item): item is string => typeof item === 'string')
    .map((code) => code.trim())
    .filter((code) => code !== '')
}
