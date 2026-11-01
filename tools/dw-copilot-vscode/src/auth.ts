// SSO sign-in and token lifecycle. No `vscode` import: the host passes its pieces in, so this runs under `node --test`.
import { randomBytes } from 'node:crypto';

export const CALLBACK_PATH = '/sso-callback';
const SIGN_IN_TIMEOUT_MS = 5 * 60 * 1000;
const REQUEST_TIMEOUT_MS = 30_000;

export interface SecretStore {
  get(key: string): PromiseLike<string | undefined>;
  store(key: string, value: string): PromiseLike<void>;
  delete(key: string): PromiseLike<void>;
}

export interface AuthHost {
  secrets: SecretStore;
  openExternal(url: string): Promise<boolean>;
  /** e.g. vscode://workflow-station.dw-copilot — must match the admin-center SSO redirect whitelist. */
  callbackBase: string;
  fetch: typeof fetch;
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  username: string;
  /** Admitted through the team-membership fallback only: DW opens read-only for this user. */
  readOnly?: boolean;
}

export class NotSignedInError extends Error {
  constructor(origin: string) {
    super(`Not signed in to DW at ${origin}. Run "DW: Sign in".`);
  }
}

export class NoDwAccessError extends Error {
  constructor(origin: string) {
    super(`This account cannot enter Developer Workstation at ${origin}, so it cannot use DW Copilot. ` +
      'Ask for a DW role or to join a development team.');
  }
}

/** Same roles as the DW web router's requiredRoles (frontend/developer-workstation/src/utils/permission.ts). */
const CAPABILITY_ROLES = ['SYS_ADMIN', 'TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER'];

class TokenRejected extends Error {}

interface Pending {
  origin: string;
  resolve(session: Session): void;
  reject(error: Error): void;
  timer: NodeJS.Timeout;
}

export function loginUrl(origin: string, redirectUri: string, state: string): string {
  const u = new URL('/login/', origin);
  u.searchParams.set('client_id', 'developer-workstation');
  u.searchParams.set('redirect_uri', redirectUri);
  u.searchParams.set('state', state);
  return u.toString();
}

export class DwAuth {
  private readonly pending = new Map<string, Pending>();
  /** One refresh per origin at a time: parallel 401s must not spend the same rotating refresh token twice. */
  private readonly refreshing = new Map<string, Promise<string>>();

  constructor(private readonly host: AuthHost) {}

  private key(origin: string): string {
    return `dwCopilot.session:${origin}`;
  }

  async session(origin: string): Promise<Session | undefined> {
    const raw = await this.host.secrets.get(this.key(origin));
    return raw ? (JSON.parse(raw) as Session) : undefined;
  }

  async accessToken(origin: string): Promise<string> {
    const s = await this.session(origin);
    if (!s) throw new NotSignedInError(origin);
    return s.accessToken;
  }

  /** Opens the unified login page; resolves when the browser hands the code back to {@link handleCallback}. */
  async signIn(origin: string): Promise<Session> {
    const state = randomBytes(16).toString('hex');
    const result = new Promise<Session>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(state);
        reject(new Error('DW sign-in timed out after 5 minutes.'));
      }, SIGN_IN_TIMEOUT_MS);
      this.pending.set(state, { origin, resolve, reject, timer });
    });
    const opened = await this.host.openExternal(loginUrl(origin, this.host.callbackBase + CALLBACK_PATH, state));
    if (!opened) {
      this.settle(state)?.reject(new Error('The browser was not opened for DW sign-in.'));
    }
    return result;
  }

  /** Callback URI query: code + state. A state we did not issue is rejected, never exchanged. */
  async handleCallback(query: string): Promise<void> {
    const params = new URLSearchParams(query);
    const state = params.get('state') ?? '';
    const code = params.get('code');
    const pending = this.settle(state);
    if (!pending) throw new Error('Ignored a DW sign-in callback that this window did not start.');
    if (!code) {
      pending.reject(new Error('DW sign-in callback had no code.'));
      return;
    }
    try {
      const res = await this.post(pending.origin, '/api/v1/auth/sso/exchange', { code, state });
      const body = (await res.json()) as { accessToken?: string; refreshToken?: string; user?: { username?: string } };
      if (!body.accessToken || !body.refreshToken) {
        throw new Error('DW sign-in exchange returned no tokens.');
      }
      const session: Session = {
        accessToken: body.accessToken,
        refreshToken: body.refreshToken,
        username: body.user?.username ?? '',
        readOnly: await this.admit(pending.origin, body.accessToken) === 'readOnly',
      };
      await this.host.secrets.store(this.key(pending.origin), JSON.stringify(session));
      pending.resolve(session);
    } catch (e) {
      pending.reject(e as Error);
    }
  }

  /** Refresh tokens rotate (the old one is blacklisted), so the new pair replaces the stored one as a whole. */
  refresh(origin: string): Promise<string> {
    let inFlight = this.refreshing.get(origin);
    if (!inFlight) {
      inFlight = this.doRefresh(origin).finally(() => this.refreshing.delete(origin));
      this.refreshing.set(origin, inFlight);
    }
    return inFlight;
  }

  private async doRefresh(origin: string): Promise<string> {
    const current = await this.session(origin);
    if (!current) throw new NotSignedInError(origin);
    const res = await this.request(origin, '/api/v1/auth/refresh', { refreshToken: current.refreshToken });
    // 400/401 = the refresh token is expired or already used: the session is gone. Anything else may be transient.
    if (res.status === 400 || res.status === 401) {
      await this.host.secrets.delete(this.key(origin));
      throw new NotSignedInError(origin);
    }
    if (!res.ok) throw new Error(`DW /api/v1/auth/refresh failed: HTTP ${res.status}`);
    const body = (await res.json()) as { accessToken?: string; refreshToken?: string };
    if (!body.accessToken || !body.refreshToken) throw new Error('DW refresh returned no tokens.');
    // The old refresh token is already spent: keep the new pair even if admission fails transiently below.
    const rotated: Session = { ...current, accessToken: body.accessToken, refreshToken: body.refreshToken };
    await this.host.secrets.store(this.key(origin), JSON.stringify(rotated));
    await this.storeAdmission(origin, rotated);
    return body.accessToken;
  }

  /**
   * Re-checks DW admission for the stored session (on startup), refreshing once if the access token has expired.
   * Roles can be revoked between sessions; a user who can no longer enter DW is signed out.
   */
  async verify(origin: string): Promise<Session | undefined> {
    const current = await this.session(origin);
    if (!current) return undefined;
    try {
      return await this.storeAdmission(origin, current);
    } catch (e) {
      if (!(e instanceof TokenRejected)) throw e;
    }
    await this.refresh(origin);
    return this.session(origin);
  }

  async signOut(origin: string): Promise<void> {
    await this.host.secrets.delete(this.key(origin));
  }

  private async storeAdmission(origin: string, session: Session): Promise<Session> {
    let level: 'full' | 'readOnly';
    try {
      level = await this.admit(origin, session.accessToken);
    } catch (e) {
      if (e instanceof NoDwAccessError) await this.host.secrets.delete(this.key(origin));
      throw e;
    }
    // A refresh may have rotated the pair while we were checking: never write the checked (older) pair back over it.
    const latest = await this.session(origin);
    if (!latest || latest.accessToken !== session.accessToken) return latest ?? session;
    const admitted = { ...latest, readOnly: level === 'readOnly' };
    await this.host.secrets.store(this.key(origin), JSON.stringify(admitted));
    return admitted;
  }

  /**
   * The DW web router's entry rule, in its order: a pure AUDITOR is refused; a capability role enters; anyone else
   * enters read-only only when the backend says their team owns function units (workspace-access, which on its own
   * would also admit AUDITOR — hence the first check).
   */
  private async admit(origin: string, accessToken: string): Promise<'full' | 'readOnly'> {
    const me = await this.getJson(origin, '/api/v1/auth/me', accessToken) as { roles?: string[] };
    const roles = me.roles ?? [];
    if (roles.some(r => CAPABILITY_ROLES.includes(r))) return 'full';
    if (roles.includes('AUDITOR')) throw new NoDwAccessError(origin);
    const access = await this.getJson(origin, '/api/v1/function-units/workspace-access', accessToken) as
      { data?: { canView?: boolean } };
    if (access.data?.canView === true) return 'readOnly';
    throw new NoDwAccessError(origin);
  }

  private async getJson(origin: string, path: string, accessToken: string): Promise<unknown> {
    const res = await this.host.fetch(new URL(path, origin), {
      headers: { Authorization: `Bearer ${accessToken}` },
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    if (res.status === 401) throw new TokenRejected(`DW ${path}: HTTP 401`);
    if (!res.ok) throw new Error(`DW ${path} failed: HTTP ${res.status}`);
    return res.json();
  }

  private settle(state: string): Pending | undefined {
    const p = this.pending.get(state);
    if (p) {
      clearTimeout(p.timer);
      this.pending.delete(state);
    }
    return p;
  }

  private request(origin: string, path: string, body: unknown): Promise<Response> {
    return this.host.fetch(new URL(path, origin), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  }

  private async post(origin: string, path: string, body: unknown): Promise<Response> {
    const res = await this.request(origin, path, body);
    if (!res.ok) throw new Error(`DW ${path} failed: HTTP ${res.status}`);
    return res;
  }
}
