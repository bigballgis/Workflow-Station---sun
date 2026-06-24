const STORAGE_KEY = 'sso_return_portal'
/**
 * OAuth `state`（类 UUID）。`crypto.randomUUID()` 仅在安全上下文可用（HTTPS 或 localhost）；
 * 纯 HTTP 域名下会缺失，需降级。
 */
function newSsoState(): string {
  const c = globalThis.crypto
  if (typeof c?.randomUUID === 'function') {
    try {
      return c.randomUUID()
    } catch {
      /* fall through */
    }
  }
  if (typeof c?.getRandomValues === 'function') {
    try {
      const bytes = new Uint8Array(16)
      c.getRandomValues(bytes)
      bytes[6] = (bytes[6]! & 0x0f) | 0x40
      bytes[8] = (bytes[8]! & 0x3f) | 0x80
      const hex = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('')
      return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
    } catch {
      /* fall through */
    }
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 15)}`
}
export function setSsoReturnPath(fullPath: string) {
  sessionStorage.setItem(STORAGE_KEY, fullPath)
}
export function consumeSsoReturnPath(fallback: string) {
  const v = sessionStorage.getItem(STORAGE_KEY)
  sessionStorage.removeItem(STORAGE_KEY)
  return v || fallback
}
interface UnifiedLoginOptions {
  autoSso?: boolean
}
export function redirectToUnifiedLogin(_clientId: 'portal', options: UnifiedLoginOptions = {}) {
  const redirectUri = new URL(
    (import.meta.env.BASE_URL || '/') + 'sso/callback',
    window.location.origin
  ).href
  const state = newSsoState()
  const externalLoginOrigin = (import.meta.env.VITE_SSO_LOGIN_ORIGIN as string | undefined)
    ?.trim()
    .replace(/\/$/, '')
  // Production (or explicit override): separate static login app at origin /login/
  if (import.meta.env.PROD || externalLoginOrigin) {
    const origin = externalLoginOrigin || window.location.origin
    const u = new URL('/login/', origin)
    u.searchParams.set('client_id', 'portal')
    u.searchParams.set('redirect_uri', redirectUri)
    u.searchParams.set('state', state)
    if (options.autoSso) u.searchParams.set('auto_sso', '1')
    window.location.href = u.toString()
    return
  }
  // Dev: same-origin /portal/login?… so Vite serves the portal bundle and UnifiedLogin.vue (no second dev server).
  const u = new URL((import.meta.env.BASE_URL || '/') + 'login', window.location.origin)
  u.searchParams.set('client_id', 'portal')
  u.searchParams.set('redirect_uri', redirectUri)
  u.searchParams.set('state', state)
  if (options.autoSso) u.searchParams.set('auto_sso', '1')
  window.location.href = u.toString()
}