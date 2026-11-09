import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'

/**
 * One session refresh for the whole app.
 *
 * Access tokens live 15 minutes, so a request may hit a 401 at any time. Every axios instance
 * renews the session through {@link refreshSession}: refresh tokens rotate (each one is
 * blacklisted on use), so two instances refreshing on their own would race and the loser would
 * log the user out. Tabs of the same origin share the refresh cookie, so they are serialized too.
 */
type SessionRefresher = () => Promise<unknown>

let refresher: SessionRefresher | null = null
let inflight: Promise<boolean> | null = null

/** Web Lock held while a tab renews; a tab queued behind it then renews with the cookie the first one set. */
export const SESSION_REFRESH_LOCK = 'platform-session-refresh'

/** Registered once per app (main.ts) with that app's `/auth/refresh` call. */
export function setSessionRefresher(fn: SessionRefresher | null): void {
  refresher = fn
}

/** Renews the session cookie; concurrent callers share one refresh call. Resolves false if it failed. */
export function refreshSession(): Promise<boolean> {
  if (!refresher) return Promise.resolve(false)
  if (!inflight) {
    const run = refresher
    inflight = acrossTabs(run)
      .then(() => true, () => false)
      .finally(() => {
        inflight = null
      })
  }
  return inflight
}

function acrossTabs(run: SessionRefresher): Promise<unknown> {
  const locks = globalThis.navigator?.locks
  // FALLBACK(external): without the Web Locks API only this tab's refreshes are serialized.
  return locks ? locks.request(SESSION_REFRESH_LOCK, () => run()) : run()
}

const SESSION_RETRIED = '_sessionRetried'

/** Endpoints that must not trigger a refresh: the refresh call itself and the login / logout flows. */
const AUTH_ENDPOINT = /\/auth\/(refresh|login|logout|sso\/exchange)(\?|$)/

type RetryableConfig = InternalAxiosRequestConfig & { [SESSION_RETRIED]?: boolean }

/** True when the request already went through a refresh-and-resend and still failed. */
export function wasRetriedAfterSessionRefresh(config: unknown): boolean {
  return Boolean((config as RetryableConfig | undefined)?.[SESSION_RETRIED])
}

/**
 * `fetch` counterpart of {@link installSessionRefresh} for platform endpoints called without axios
 * (file download / preview, SSE streams): on a 401 renew the session once and send the request again.
 * Only for same-origin platform calls — not for third-party tokens (DSP, Activepieces).
 */
export async function fetchWithSessionRefresh(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const response = await fetch(input, init)
  if (response.status !== 401 || !(await refreshSession())) return response
  return fetch(input, init)
}

/**
 * Registers the first response interceptor of `instance`: on a 401 it renews the session once and
 * replays the request through `resend`, which must not run this instance's interceptors (pass
 * `config => axios.request(config)`), so the instance's own success transform applies exactly once.
 * When the refresh fails, or the replay is still 401, the error continues to the instance's own
 * handlers (which redirect to login or report it as before).
 *
 * Must be called right after `axios.create`, before the instance registers its own response interceptors.
 */
export function installSessionRefresh(
  instance: AxiosInstance,
  resend: (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>,
): void {
  instance.interceptors.response.use(undefined, async (error) => {
    const config = error?.config as RetryableConfig | undefined
    const url = `${config?.baseURL ?? ''}${config?.url ?? ''}`
    if (error?.response?.status !== 401 || !config || config[SESSION_RETRIED] || AUTH_ENDPOINT.test(url)) {
      throw error
    }
    if (!(await refreshSession())) throw error
    config[SESSION_RETRIED] = true
    return resend(config)
  })
}
