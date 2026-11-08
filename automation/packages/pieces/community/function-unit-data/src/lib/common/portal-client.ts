export type PortalMethod = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';

export type PortalCall = {
  method: PortalMethod;
  path: string;
  body?: Record<string, unknown>;
  idempotencyKey?: string;
};

const ACTOR_KEYS = ['actor', 'triggeredBy', 'userId', 'startUserId'] as const;

export function portalConfigFromEnv(env: NodeJS.ProcessEnv = process.env): { baseUrl: string; token: string } {
  const baseUrl = env['USER_PORTAL_URL']?.replace(/\/$/, '');
  const token = env['PORTAL_INTERNAL_API_TOKEN'];
  if (!baseUrl || !token) {
    throw new Error('USER_PORTAL_URL and PORTAL_INTERNAL_API_TOKEN must be set on the automation engine');
  }
  return { baseUrl, token };
}

export function buildPortalRequest(params: {
  baseUrl: string;
  token: string;
  externalProjectId: string;
  flowId: string;
  call: PortalCall;
}): { url: string; init: RequestInit } {
  if (!params.externalProjectId || !params.flowId) {
    throw new Error('This step has no workspace or flow. It runs as the workspace identity and cannot choose a person.');
  }
  const body = params.call.body ? { ...params.call.body } : undefined;
  if (body) {
    for (const key of ACTOR_KEYS) {
      if (body[key] != null && String(body[key]).trim() !== '') {
        throw new Error('This step cannot choose an actor');
      }
      delete body[key];
    }
    body['externalProjectId'] = params.externalProjectId;
    body['flowId'] = params.flowId;
  }
  const url = new URL(`${params.baseUrl}/api/portal${params.call.path}`);
  if (!body) {
    url.searchParams.set('externalProjectId', params.externalProjectId);
    url.searchParams.set('flowId', params.flowId);
  }
  const headers: Record<string, string> = { 'X-Internal-Token': params.token, Accept: 'application/json' };
  if (params.call.idempotencyKey) {
    headers['Idempotency-Key'] = params.call.idempotencyKey;
  }
  const init: RequestInit = { method: params.call.method, headers, signal: AbortSignal.timeout(30_000) };
  if (body) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  return { url: url.toString(), init };
}

export async function callPortal(params: {
  baseUrl: string;
  token: string;
  externalProjectId: string;
  flowId: string;
  call: PortalCall;
  fetchImpl?: typeof fetch;
}): Promise<unknown> {
  const request = buildPortalRequest(params);
  const response = await (params.fetchImpl ?? fetch)(request.url, request.init);
  const text = await response.text();
  const payload = text ? JSON.parse(text) as { data?: unknown; error?: { message?: string } } : {};
  if (!response.ok) {
    throw new Error(payload.error?.message || `Portal refused the data call (HTTP ${response.status})`);
  }
  return payload.data ?? payload;
}
