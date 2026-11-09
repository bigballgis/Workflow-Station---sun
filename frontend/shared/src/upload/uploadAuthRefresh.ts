import { refreshSession } from '../auth/sessionRefresh'

/** Login refresh used when {@code /upload} returns 401 — the app-wide single refresh, see sessionRefresh.ts. */
export function refreshUploadAuth(): Promise<boolean> {
  return refreshSession()
}

export const UPLOAD_HTTP_UNAUTHORIZED = 'UPLOAD_HTTP_401'

export function isUploadUnauthorizedError(error: unknown): boolean {
  return error instanceof Error && error.message === UPLOAD_HTTP_UNAUTHORIZED
}
