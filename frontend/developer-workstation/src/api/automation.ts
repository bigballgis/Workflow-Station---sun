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
 *
 * Flows are scoped to the **workspace** (dev team): the bridge maps the selected team to its
 * own AP project and mints a session for that project only, so the list, the builder and every
 * write are confined to it. Business keys stay global — deployment resolves `ap:flowKey` across
 * workspaces, so a key must be unique platform-wide (see {@link checkAutomationFlowKey}).
 */
import axios from 'axios'
import { getAutomationWorkspaceId } from '@/utils/automationWorkspace'

/* ------------------------------------------------------------------------- *
 * Bridge session
 * ------------------------------------------------------------------------- */

/** The workspace (dev team) an AP session is scoped to. */
export interface ServiceTaskWorkspace {
  /** Virtual group id; the Public workspace uses the built-in public group id. */
  id: string
  name: string
  /** False when the session may only read (Public workspace for non-SYS_ADMIN). */
  canWrite: boolean
  /** Public = the shared workspace holding legacy / shared flows. */
  isPublic: boolean
}

/** AP session the embedded builder / flows API needs: a per-user token plus its project. */
export interface ServiceTaskSession {
  token: string
  projectId: string
  /** Absent on the cross-domain nonce path, which carries no workspace metadata. */
  workspace?: ServiceTaskWorkspace
}

const adminAxios = axios.create({
  baseURL: '/api/v1/admin',
  timeout: 30000,
  withCredentials: true,
})

// Which workspace this session is for. A hint only: the backend re-validates that the caller is
// a member of that team before scoping to it, so a forged value gets 403 rather than someone
// else's flows.
adminAxios.interceptors.request.use(config => {
  const workspaceId = getAutomationWorkspaceId()
  if (workspaceId) {
    config.headers['X-Dev-Group-Id'] = workspaceId
  }
  return config
})

/**
 * Resolve the current user's Automation (AP) session from the admin-center bridge.
 *
 * Same-origin path of `/internal/ap/token`: the platform JWT cookie authenticates the
 * caller and the bridge mints a token for that user (per-user when
 * `service-task.managed.enabled`, otherwise the shared account).
 *
 * Throws on 401 (not signed in) / 403 (not a member of the requested workspace) /
 * 404 (bridge disabled in this environment).
 */
export async function fetchServiceTaskSession(): Promise<ServiceTaskSession> {
  const res = await adminAxios.get('/internal/ap/token')
  const body = res.data as ({ data?: Record<string, string> } & Record<string, string>) | null
  const payload: Record<string, string> = body?.data ?? (body as Record<string, string>) ?? {}
  const token = payload.token || ''
  const projectId = payload.projectId || ''
  if (!token || !projectId) {
    throw new Error('ServiceTask bridge returned an incomplete session')
  }
  const session: ServiceTaskSession = { token, projectId }
  if (payload.workspaceId) {
    session.workspace = {
      id: payload.workspaceId,
      name: payload.workspaceName || payload.workspaceId,
      // A missing flag must not lock the UI down: only an explicit 'false' means read-only.
      canWrite: payload.workspaceCanWrite !== 'false',
      isPublic: payload.workspacePublic === 'true',
    }
  }
  return session
}

/**
 * Is this business key still free platform-wide?
 *
 * Deployment resolves `ap:flowKey` across all workspaces, so the same key used in two
 * workspaces would make BPMN references land on whichever flow was updated last. The create
 * dialog blocks that up front instead of letting it surface as the wrong flow at runtime.
 */
export async function checkAutomationFlowKey(key: string): Promise<AutomationFlowKeyStatus> {
  const res = await adminAxios.get('/internal/ap/flow-key-available', { params: { key } })
  const body = res.data as
    | { data?: { available?: boolean; flowId?: string; inCurrentWorkspace?: boolean } }
    | null
  return {
    available: body?.data?.available !== false,
    flowId: body?.data?.flowId,
    inCurrentWorkspace: body?.data?.inCurrentWorkspace === true,
  }
}

/** Where a business key currently lives, from the caller's workspace point of view. */
export interface AutomationFlowKeyStatus {
  /** True = nobody holds this key yet (so no flow answers it at deployment time). */
  available: boolean
  flowId?: string
  /** Taken by a flow in THIS workspace (vs another team's — still resolvable at deploy). */
  inCurrentWorkspace: boolean
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
