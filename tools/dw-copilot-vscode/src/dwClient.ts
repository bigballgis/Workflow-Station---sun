// The only HTTP path to the DW API: bearer token, timeout, one refresh on 401, ApiResponse unwrapping.
import { DwAuth } from './auth';

const REQUEST_TIMEOUT_MS = 120_000;

export class DwApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    message: string,
    readonly details: unknown,
  ) {
    super(message);
  }

  /** What the model sees: status, error code and the server's details (validation errors carry fieldPath). */
  toToolText(): string {
    return `Error: HTTP ${this.status}${this.code ? ` ${this.code}` : ''}: ${this.message}` +
      (this.details !== undefined ? `\nDetails: ${JSON.stringify(this.details)}` : '');
  }
}

interface ApiEnvelope<T> {
  success?: boolean;
  data?: T;
  error?: { code?: string; message?: string; details?: unknown };
}

export class DwClient {
  constructor(private readonly auth: DwAuth, private readonly fetchFn: typeof fetch) {}

  async json<T>(origin: string, method: 'GET' | 'POST', path: string, body?: unknown): Promise<T> {
    const res = await this.send(origin, method, path, body);
    const text = await res.text();
    let envelope: ApiEnvelope<T>;
    try {
      envelope = JSON.parse(text) as ApiEnvelope<T>;
    } catch {
      throw new DwApiError(res.status, undefined, `Non-JSON response from ${path}: ${text.slice(0, 300)}`, undefined);
    }
    if (!res.ok || envelope.success === false) {
      throw new DwApiError(res.status, envelope.error?.code,
        envelope.error?.message ?? `Request to ${path} failed`, envelope.error?.details);
    }
    return envelope.data as T;
  }

  async bytes(origin: string, path: string): Promise<Uint8Array> {
    const res = await this.send(origin, 'GET', path);
    if (!res.ok) {
      throw new DwApiError(res.status, undefined, `Download from ${path} failed: ${(await res.text()).slice(0, 300)}`, undefined);
    }
    return new Uint8Array(await res.arrayBuffer());
  }

  private async send(origin: string, method: string, path: string, body?: unknown): Promise<Response> {
    const call = (token: string) => this.fetchFn(new URL('/api/v1' + path, origin), {
      method,
      headers: { Authorization: `Bearer ${token}`, ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    const res = await call(await this.auth.accessToken(origin));
    return res.status === 401 ? call(await this.auth.refresh(origin)) : res;
  }
}
