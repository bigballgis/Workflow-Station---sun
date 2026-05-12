import { TOKEN_KEY } from '@/api/auth'

/** Cursor debug session id (must match debug workflow). */
export const AGENT_DEBUG_SESSION = '6ee1a8'

const CURSOR_INGEST =
  'http://127.0.0.1:7683/ingest/1fc88847-d32b-4694-9f56-a337ecc92dd3'

/**
 * Sends one NDJSON-style payload to user-portal (see container logs: logger AGENT_NDJSON),
 * and optionally to the Cursor ingest server on localhost (host-side IDE only).
 */
export function agentDebugLog(
  payload: Record<string, unknown> & {
    hypothesisId?: string
    location?: string
    message?: string
    data?: unknown
  }
): void {
  const bodyObj = {
    sessionId: AGENT_DEBUG_SESSION,
    timestamp: Date.now(),
    ...payload
  }
  const body = JSON.stringify(bodyObj)

  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (typeof localStorage !== 'undefined') {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) headers.Authorization = `Bearer ${token}`
  }

  fetch('/api/portal/debug/agent-ingest', {
    method: 'POST',
    headers,
    credentials: 'include',
    body
  }).catch(() => {})

  fetch(CURSOR_INGEST, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': AGENT_DEBUG_SESSION },
    body
  }).catch(() => {})
}
