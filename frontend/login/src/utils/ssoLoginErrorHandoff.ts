/**
 * Same-origin handoff from user-portal SSO callback when login fails after Admin SSO
 * (e.g. PORTAL_ENTITLEMENT_DENIED). Key must stay in sync with
 * frontend/user-portal/src/utils/sso.ts (`SSO_LOGIN_ERROR_KEY`).
 */
const SSO_LOGIN_ERROR_KEY = 'ws_sso_login_error'

/** Read and clear a previously persisted login failure message (one-shot). */
export function consumeSsoLoginErrorMessage(): string | null {
  try {
    const v = sessionStorage.getItem(SSO_LOGIN_ERROR_KEY)
    sessionStorage.removeItem(SSO_LOGIN_ERROR_KEY)
    return v && v.trim() ? v.trim() : null
  } catch {
    return null
  }
}
