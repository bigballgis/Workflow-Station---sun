const STORAGE_KEY = 'sso_return_dw'

export function setSsoReturnPath(fullPath: string) {
  sessionStorage.setItem(STORAGE_KEY, fullPath)
}

export function consumeSsoReturnPath(fallback: string) {
  const v = sessionStorage.getItem(STORAGE_KEY)
  sessionStorage.removeItem(STORAGE_KEY)
  return v || fallback
}

/** 仅 DEV：与设计器同域部署时 /login/ 为统一登录入口 */
export function redirectToUnifiedLogin(_clientId: 'developer-workstation') {
  const redirectUri = new URL(
    (import.meta.env.BASE_URL || '/') + 'sso/callback',
    window.location.origin
  ).href
  const u = new URL('/login/', window.location.origin)
  u.searchParams.set('client_id', 'developer-workstation')
  u.searchParams.set('redirect_uri', redirectUri)
  u.searchParams.set('state', crypto.randomUUID())
  window.location.href = u.toString()
}
