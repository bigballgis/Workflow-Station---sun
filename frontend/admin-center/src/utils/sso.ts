const STORAGE_KEY = 'sso_return_admin'

export function setSsoReturnPath(fullPath: string) {
  sessionStorage.setItem(STORAGE_KEY, fullPath)
}

export function consumeSsoReturnPath(fallback: string) {
  const v = sessionStorage.getItem(STORAGE_KEY)
  sessionStorage.removeItem(STORAGE_KEY)
  return v || fallback
}

/** 跳转独立 /login/ 应用（单域多路径时同样为 /login/） */
export function redirectToUnifiedLogin(clientId: 'admin') {
  const redirectUri = new URL(
    (import.meta.env.BASE_URL || '/') + 'sso/callback',
    window.location.origin
  ).href
  const u = new URL('/login/', window.location.origin)
  u.searchParams.set('client_id', clientId)
  u.searchParams.set('redirect_uri', redirectUri)
  u.searchParams.set('state', crypto.randomUUID())
  window.location.href = u.toString()
}
