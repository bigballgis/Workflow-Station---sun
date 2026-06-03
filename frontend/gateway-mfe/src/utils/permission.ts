// Gateway Governance permissions — local copy for gateway-mfe
// Host enforces route-level guards; MFE uses these for button-level visibility

export const PERMISSIONS = {
  GATEWAY_API_READ: 'gateway:api:read',
  GATEWAY_API_WRITE: 'gateway:api:write',
  GATEWAY_APP_READ: 'gateway:application:read',
  GATEWAY_APP_WRITE: 'gateway:application:write',
  GATEWAY_POLICY_READ: 'gateway:policy:read',
  GATEWAY_POLICY_WRITE: 'gateway:policy:write',
  GATEWAY_RELEASE_READ: 'gateway:release:read',
  GATEWAY_RELEASE_EXECUTE: 'gateway:release:execute',
  GATEWAY_ENV_READ: 'gateway:environment:read',
  GATEWAY_ENV_WRITE: 'gateway:environment:write',
  GATEWAY_AUDIT_READ: 'gateway:audit:read',
  GATEWAY_DRIFT_READ: 'gateway:drift:read',
  GATEWAY_DRIFT_SYNC: 'gateway:drift:sync',
  GATEWAY_MONITORING_READ: 'gateway:monitoring:read',
  GATEWAY_RELEASE_APPROVE: 'gateway:release:approve',
  GATEWAY_RELEASE_PROMOTE: 'gateway:release:promote',
}

// Check if user has a specific permission (reads from localStorage set by host)
const userPerms: Set<string> = new Set()
try {
  const stored = localStorage.getItem('permissions')
  if (stored) {
    JSON.parse(stored).forEach((p: string) => userPerms.add(p))
  }
} catch { /* ignore */ }

export function hasPermission(perm: string): boolean {
  return userPerms.has(perm)
}
