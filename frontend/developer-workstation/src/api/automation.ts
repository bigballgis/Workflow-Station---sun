/**
 * Automation (AP) API client for the DW Automation page and the Service Task panel.
 *
 * Two halves:
 *  - the admin-center bridge (`/api/v1/admin/internal/ap/token`) that mints the
 *    per-user AP session (token + projectId);
 *  - the AP REST API behind the Kong `/api/ap` prefix, called with that token.
 *
 * AP runs as a single shared runtime per environment. Flows carry a stable business
 * key in `metadata.hermesFlowKey`; BPMN service tasks reference flows by that key
 * (`ap:flowKey`), never by the environment-local flow id.
 */
import axios from 'axios'

/* ------------------------------------------------------------------------- *
 * Bridge session
 * ------------------------------------------------------------------------- */

/** AP session the embedded builder / flows API needs: a per-user token plus its project. */
export interface ServiceTaskSession {
  token: string
  projectId: string
}

const adminAxios = axios.create({
  baseURL: '/api/v1/admin',
  timeout: 30000,
  withCredentials: true,
})

/**
 * Resolve the current user's Automation (AP) session from the admin-center bridge.
 *
 * Same-origin path of `/internal/ap/token`: the platform JWT cookie authenticates the
 * caller and the bridge mints a token for that user (per-user when
 * `service-task.managed.enabled`, otherwise the shared account).
 *
 * Throws on 401 (not signed in) / 404 (bridge disabled in this environment).
 */
export async function fetchServiceTaskSession(): Promise<ServiceTaskSession> {
  const res = await adminAxios.get('/internal/ap/token')
  const body = res.data as
    | { data?: Partial<ServiceTaskSession> } & Partial<ServiceTaskSession>
    | null
  const token = body?.data?.token || body?.token || ''
  const projectId = body?.data?.projectId || body?.projectId || ''
  if (!token || !projectId) {
    throw new Error('ServiceTask bridge returned an incomplete session')
  }
  return { token, projectId }
}

/* ------------------------------------------------------------------------- *
 * AP flows API (Kong /api/ap prefix, Bearer token from the bridge session)
 * ------------------------------------------------------------------------- */

const apAxios = axios.create({
  baseURL: `${window.location.origin}/api/ap/v1`,
  timeout: 30000,
})

function authHeaders(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` }
}

/** AP cursor-paged envelope (SeekPage). */
export interface ApSeekPage<T> {
  data: T[]
  next: string | null
  previous: string | null
}

export interface ApFlowVersionSummary {
  id: string
  displayName: string
  updated: string
  valid: boolean
  state: 'LOCKED' | 'DRAFT'
}

/** Populated flow, as returned by GET/POST /v1/flows. */
export interface ApFlow {
  id: string
  projectId: string
  status: 'ENABLED' | 'DISABLED'
  publishedVersionId: string | null
  metadata?: { hermesFlowKey?: string } & Record<string, unknown> | null
  updated: string
  version: ApFlowVersionSummary
}

/**
 * Flow operations the Automation page uses — the exact `{type, request}` union the
 * AP 0.88 flows API accepts (FlowOperationType in automation/packages/core/execution,
 * read-only reference).
 */
export type ApFlowOperation =
  | { type: 'CHANGE_NAME'; request: { displayName: string } }
  | { type: 'CHANGE_STATUS'; request: { status: 'ENABLED' | 'DISABLED' } }
  | { type: 'LOCK_AND_PUBLISH'; request: { status?: 'ENABLED' | 'DISABLED' } }
  | { type: 'UPDATE_METADATA'; request: { metadata: Record<string, unknown> | null } }

/** List the project's flows (cursor-paged). */
export async function listAutomationFlows(params: {
  token: string
  projectId: string
  cursor?: string
  limit?: number
  name?: string
}): Promise<ApSeekPage<ApFlow>> {
  const res = await apAxios.get('/flows', {
    headers: authHeaders(params.token),
    params: {
      projectId: params.projectId,
      cursor: params.cursor || undefined,
      limit: params.limit ?? 50,
      name: params.name || undefined,
    },
  })
  return res.data as ApSeekPage<ApFlow>
}

/**
 * Create a flow with its business key stamped in one call — CreateFlowRequest
 * accepts `metadata` directly, so create + key stamp is atomic (no second
 * UPDATE_METADATA round-trip that could leave a keyless flow behind on failure).
 */
export async function createAutomationFlow(params: {
  token: string
  projectId: string
  displayName: string
  flowKey: string
}): Promise<ApFlow> {
  const res = await apAxios.post(
    '/flows',
    {
      projectId: params.projectId,
      displayName: params.displayName,
      metadata: { hermesFlowKey: params.flowKey },
    },
    { headers: authHeaders(params.token) },
  )
  const flow = res.data as ApFlow | null
  if (!flow?.id) {
    throw new Error('Automation flow creation returned no id')
  }
  return flow
}

/** One populated flow (draft version). */
export async function getAutomationFlow(flowId: string, token: string): Promise<ApFlow> {
  const res = await apAxios.get(`/flows/${flowId}`, { headers: authHeaders(token) })
  return res.data as ApFlow
}

/** Apply a flow operation (rename / publish / enable / disable / metadata). */
export async function applyAutomationFlowOperation(
  flowId: string,
  token: string,
  operation: ApFlowOperation,
): Promise<ApFlow> {
  const res = await apAxios.post(`/flows/${flowId}`, operation, {
    headers: authHeaders(token),
  })
  return res.data as ApFlow
}

/** Delete a flow. Irreversible; run history goes with it. */
export async function deleteAutomationFlow(flowId: string, token: string): Promise<void> {
  await apAxios.delete(`/flows/${flowId}`, { headers: authHeaders(token) })
}
