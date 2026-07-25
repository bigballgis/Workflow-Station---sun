/**
 * ServiceTask (AP) types for the BPMN service-task designer.
 *
 * AP runs as a single shared runtime per environment, so — unlike the old per-connection
 * model — the designer does not fetch connection configs or workflow lists. The service task
 * only stores the AP flow id; the engine builds the sync webhook URL from its own
 * per-environment configuration at runtime.
 */
import axios from 'axios'


/** Variable mapping entry */
export interface VariableMapping {
  source: string
  target: string
}

/** AP task config for BPMN serialization */
export interface ServiceTaskConfig {
  /** AP flow id (the webhook flow identifier) */
  flowId: string
  /** Optional full sync webhook URL override; blank = build from engine config + flowId */
  webhookUrl: string
  /** Execution timeout seconds */
  timeoutSeconds: number
  /** Retry count on transient failure */
  retryCount: number
  /** Process-variable → AP input mapping */
  inputMapping: VariableMapping[]
  /** AP output → process-variable mapping */
  outputMapping: VariableMapping[]
}

/* ------------------------------------------------------------------------- *
 * ServiceTask builder session
 * ------------------------------------------------------------------------- */

/** AP session the embedded builder needs: a per-user token plus its project. */
export interface ServiceTaskSession {
  token: string
  projectId: string
}

const serviceTaskAxios = axios.create({
  baseURL: '/api/v1/admin',
  timeout: 30000,
  withCredentials: true,
})

/**
 * Resolve the current user's ServiceTask (AP) session from the admin-center bridge.
 *
 * Same-origin path of `/internal/ap/token`: the platform JWT cookie authenticates the
 * caller and the bridge mints a token for that user (per-user when
 * `service-task.managed.enabled`, otherwise the shared account). No nonce is involved —
 * that is only needed for the cross-domain redirect flow.
 *
 * Throws on 401 (not signed in) / 404 (bridge disabled in this environment).
 */
export async function fetchServiceTaskSession(): Promise<ServiceTaskSession> {
  const res = await serviceTaskAxios.get('/internal/ap/token')
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

/**
 * Create a new (empty) automation flow in the shared runtime and return its id.
 *
 * The bridge session's token authorises the call; it goes through the Kong /api/ap
 * prefix like every other builder request. The caller then binds this id onto the
 * BPMN service task and mounts the builder on it.
 */
export async function createServiceTaskFlow(params: {
  projectId: string
  token: string
  displayName: string
}): Promise<string> {
  const res = await serviceTaskAxios.post(
    `${window.location.origin}/api/ap/v1/flows`,
    { projectId: params.projectId, displayName: params.displayName },
    { headers: { Authorization: `Bearer ${params.token}` } },
  )
  const flowId = (res.data as { id?: string } | null)?.id
  if (!flowId) {
    throw new Error('ServiceTask flow creation returned no id')
  }
  return flowId
}
