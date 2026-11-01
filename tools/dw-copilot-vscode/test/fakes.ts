import { AuthHost, DwAuth } from '../src/auth';

export interface Call { url: string; method: string; headers: Record<string, string>; body?: string }

/** fetch double: answers by "METHOD path" and records every call. */
export function fakeFetch(routes: Record<string, (call: Call) => Response | Promise<Response>>): { fetch: typeof fetch; calls: Call[] } {
  const calls: Call[] = [];
  const fn = (async (input: string | URL, init?: RequestInit) => {
    const url = new URL(input.toString());
    const call: Call = {
      url: url.toString(),
      method: init?.method ?? 'GET',
      headers: (init?.headers ?? {}) as Record<string, string>,
      body: init?.body as string | undefined,
    };
    calls.push(call);
    const handler = routes[`${call.method} ${url.pathname}`];
    if (!handler) throw new Error(`unexpected ${call.method} ${url.pathname}`);
    return handler(call);
  }) as typeof fetch;
  return { fetch: fn, calls };
}

export function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

export function memorySecrets(): AuthHost['secrets'] & { data: Map<string, string> } {
  const data = new Map<string, string>();
  return {
    data,
    get: async k => data.get(k),
    store: async (k, v) => { data.set(k, v); },
    delete: async k => { data.delete(k); },
  };
}

export const ORIGIN = 'https://dw.example.test';

/** Routes DW admission reads: /auth/me roles and the team-membership fallback. */
export function admission(roles: string[], canView = false): Record<string, (call: Call) => Response> {
  return {
    'GET /api/v1/auth/me': () => json(200, { username: 'dev1', roles }),
    'GET /api/v1/function-units/workspace-access': () => json(200, { success: true, data: { canView } }),
  };
}

export function signedInAuth(fetchFn: typeof fetch, opened: string[] = []): { auth: DwAuth; secrets: ReturnType<typeof memorySecrets> } {
  const secrets = memorySecrets();
  secrets.data.set(`dwCopilot.session:${ORIGIN}`,
    JSON.stringify({ accessToken: 'access-1', refreshToken: 'refresh-1', username: 'dev1' }));
  const auth = new DwAuth({
    secrets,
    openExternal: async url => { opened.push(url); return true; },
    callbackBase: 'vscode://workflow-station.dw-copilot',
    fetch: fetchFn,
  });
  return { auth, secrets };
}
