import { ContentOrganizerConfig } from './config';

/**
 * iB2B service-account token + HASE Content Organizer calls.
 *
 * A fresh iB2B token is minted for every run — one ~0.4s call — and shared by that run's calls
 * only, so nothing is cached in the shared engine process and an expired token is never carried
 * over. The only expiry window left is inside a single run; a 401 from Content Organizer mints
 * once more and retries once.
 *
 * Plain `fetch`: in AP_NETWORK_MODE=STRICT the engine's dns.lookup / Socket.connect guards apply
 * to it, so the hosts must be on AP_SSRF_ALLOW_LIST. This piece never disables TLS verification
 * itself, but the pieces-common httpClient sets NODE_TLS_REJECT_UNAUTHORIZED=0 for the whole engine
 * process the first time any HTTP-piece step runs there — so whether certificates are verified
 * depends on what ran earlier in that process. Make the corporate CA trusted (NODE_EXTRA_CA_CERTS)
 * rather than relying on either state.
 */
export type FetchLike = typeof fetch;

const TRUST_TOKEN_HEADER = 'X-HSBC-E2E-Trust-Token';
const SUCCESS_CODE = '00000';

export class ContentOrganizerError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
    this.name = 'ContentOrganizerError';
  }
}

export async function mintIb2bToken(config: ContentOrganizerConfig, fetchImpl: FetchLike): Promise<string> {
  const res = await fetchImpl(config.ib2bTokenUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: '*/*' },
    body: JSON.stringify({
      input_token_state: { token_type: 'CREDENTIAL', username: config.ib2bUsername, password: config.ib2bSecret },
      output_token_state: { token_type: 'JWT' },
    }),
    signal: AbortSignal.timeout(30_000),
  });
  const text = await res.text();
  const token = res.ok ? (safeJson(text) as { issued_token?: unknown } | undefined)?.issued_token : undefined;
  if (typeof token !== 'string' || token.trim() === '') {
    // The request body carries the password; only the response is quoted.
    throw new ContentOrganizerError(`iB2B token request failed (HTTP ${res.status}): ${preview(text)}`, res.status);
  }
  return token;
}

export type ContentOrganizerSession = {
  config: ContentOrganizerConfig;
  fetchImpl: FetchLike;
  sessionId: string;
  /** Staff ID — required whenever the call carries an iB2B token. */
  userId: string;
  /** This run's iB2B token; minted on first use. */
  token?: string;
};

/** #9 File Upload: returns the file_id Content Organizer assigned. */
export async function uploadFile(
  session: ContentOrganizerSession,
  file: { name: string; contentType: string; data: Uint8Array },
): Promise<string> {
  const { config, sessionId, userId } = session;
  const url = `${config.baseUrl}/api/management-service/api/applications/${encodeURIComponent(config.applicationId)}`
    + `/sessions/${encodeURIComponent(sessionId)}/files`;
  const body = await callWithFreshToken(session, (token) => {
    const form = new FormData();
    form.append('files', new Blob([file.data], { type: file.contentType }), file.name);
    form.append('userId', userId);
    return session.fetchImpl(url, {
      method: 'POST',
      headers: { [TRUST_TOKEN_HEADER]: token },
      body: form,
      signal: AbortSignal.timeout(120_000),
    });
  }, 'File upload');
  const fileId = (body['data'] as Array<{ file_id?: unknown }> | undefined)?.[0]?.file_id;
  if (typeof fileId !== 'string' || fileId === '') {
    throw new ContentOrganizerError(`File upload returned no file_id: ${preview(JSON.stringify(body))}`);
  }
  return fileId;
}

/**
 * #1 Chat completion. The uploaded file is linked by the same sessionId + applicationId + userId
 * (confirmed by the Content Organizer team), not by passing its file_id. The instructions live in the
 * configured prompt setting; `variableValue` fills its one variable (the field list).
 */
export async function complete(session: ContentOrganizerSession, question: string, variableValue: string): Promise<string> {
  const { config, sessionId, userId } = session;
  const body = await callWithFreshToken(session, (token) => session.fetchImpl(
    `${config.baseUrl}/api/management-service/chat/completion`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', [TRUST_TOKEN_HEADER]: token },
      body: JSON.stringify({
        sessionId,
        userId,
        metadata: { apiVersion: config.apiVersion },
        parameter: {
          ...completionParameter(config, question),
          promptSetting: {
            id: config.promptSettingId,
            promptVars: [{ name: config.promptVariableId, value: [variableValue] }],
            settingType: 'PROMPT',
          },
        },
        workflow: config.workflow,
        version: config.workflowVersion,
        defaultOptions: { language: 'English', noOfOutput: 1 },
      }),
      signal: AbortSignal.timeout(240_000),
    },
  ), 'Chat completion');
  const content = (body['data'] as { message?: Array<{ content?: unknown }> } | undefined)?.message?.[0]?.content;
  if (typeof content !== 'string' || content.trim() === '') {
    throw new ContentOrganizerError(`Chat completion returned no answer: ${preview(JSON.stringify(body))}`);
  }
  return content;
}

/**
 * Agent run against an agent setting (a multi-agent use case such as AI Studio's "Function Unit"), per the
 * Content Organizer API Request Examples (#12 `agents/run-sse`) and "SSE API Design" pages. #1
 * chat/completion only resolves prompt settings (UAT answers "Prompt setting not found" for an agent setting
 * id, 2026-11-03). The body is the documented one: `parameter.{applicationId, message, agentSetting}` and the
 * staff id as `user_id` (iB2B calls only). The documented `message` is "the user's query only" because the
 * agent remembers its session; every call here opens a new session and carries the conversation inside the
 * message instead, so no memory leaks between requests. A multi-agent run can take minutes.
 */
export async function completeWithAgent(session: ContentOrganizerSession, message: string): Promise<string> {
  const { config, sessionId, userId } = session;
  const res = await sendWithFreshToken(session, (token) => session.fetchImpl(
    `${config.baseUrl}/api/management-service/agents/run-sse`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        'Cache-Control': 'no-cache',
        [TRUST_TOKEN_HEADER]: token,
      },
      body: JSON.stringify({
        sessionId,
        parameter: {
          applicationId: config.applicationId,
          message,
          agentSetting: { id: config.promptSettingId, agentVars: [] },
        },
        user_id: userId,
      }),
      signal: AbortSignal.timeout(300_000),
    },
  ));
  const text = await res.text();
  if (!res.ok) {
    const body = safeJson(text) as Record<string, unknown> | undefined;
    const detail = body?.['detail'] ?? body?.['msg'] ?? text;
    throw new ContentOrganizerError(
      `Agent run failed (HTTP ${res.status}): ${preview(typeof detail === 'string' ? detail : JSON.stringify(detail))}`,
      res.status,
    );
  }
  return agentAnswer(text);
}

/**
 * The answer of an agent run stream ("SSE API Design"): every line is `data: <JSON>`; the documented types are
 * handled one by one, as that page asks. `status` and `chunk` are progress only; each `full` is one complete
 * response segment and a run may send several (e.g. one per answering agent), so the answer is all of them in
 * order; `error` (code SYSTEM_ERROR / FORBIDDEN_CONTENT — the latter means "revoke all output") fails the run
 * and discards everything received; `done` closes the stream with status success / failed. An unknown type is
 * reported, not guessed at.
 */
export function agentAnswer(stream: string): string {
  const segments: string[] = [];
  let done: Record<string, unknown> | undefined;
  for (const line of stream.split(/\r?\n/)) {
    if (!line.startsWith('data:')) continue;
    const event = safeJson(line.slice(5).trim()) as Record<string, unknown> | undefined;
    if (!event) {
      throw new ContentOrganizerError(`Agent run sent an unreadable event: ${preview(line)}`);
    }
    switch (event['type']) {
      case 'status':
      case 'chunk':
        break;
      case 'full':
        if (typeof event['content'] === 'string') segments.push(event['content']);
        break;
      case 'error':
        throw new ContentOrganizerError(
          `Agent run failed (${String(event['code'] ?? 'error')}): ${preview(String(event['message'] ?? event['content'] ?? ''))}`,
        );
      case 'done':
        done = event;
        break;
      default:
        throw new ContentOrganizerError(`Agent run sent an unknown event type: ${preview(JSON.stringify(event))}`);
    }
  }
  if (!done) {
    throw new ContentOrganizerError(`Agent run ended without a done event: ${preview(stream)}`);
  }
  if (done['status'] !== 'success') {
    throw new ContentOrganizerError(`Agent run did not succeed: ${preview(JSON.stringify(done))}`);
  }
  const answer = segments.join('\n\n');
  if (answer.trim() === '') {
    throw new ContentOrganizerError(`Agent run returned no answer: ${preview(stream)}`);
  }
  return answer;
}

/** Same parameter block as the Content Organizer web UI sends; model and applicationName are mandatory (HTTP 422 without them, UAT 2026-10-07). */
function completionParameter(config: ContentOrganizerConfig, question: string): Record<string, unknown> {
  return {
    applicationId: config.applicationId,
    applicationName: config.applicationName,
    model: config.model,
    llmProvider: 'OpenAI',
    apiVersion: '2023-03-15-preview',
    promptEngineer: 'true',
    enableQuestionDetection: 'false',
    numberOfRelevantDocument: 3,
    searchingScore: 0.3,
    temperature: 1,
    referDocumentList: [],
    referDocumentbaseList: [],
    messages: [{ role: 'user', content: question }],
  };
}

/** Sends with this run's iB2B token; a 401 mints a fresh one and retries once. */
async function sendWithFreshToken(
  session: ContentOrganizerSession,
  call: (token: string) => Promise<Response>,
): Promise<Response> {
  session.token ??= await mintIb2bToken(session.config, session.fetchImpl);
  const res = await call(session.token);
  if (res.status !== 401) return res;
  session.token = await mintIb2bToken(session.config, session.fetchImpl);
  return call(session.token);
}

async function callWithFreshToken(
  session: ContentOrganizerSession,
  call: (token: string) => Promise<Response>,
  what: string,
): Promise<Record<string, unknown>> {
  const res = await sendWithFreshToken(session, call);
  const text = await res.text();
  const body = safeJson(text) as Record<string, unknown> | undefined;
  if (!res.ok || !body || (body['code'] !== undefined && body['code'] !== SUCCESS_CODE)) {
    const detail = body?.['detail'] ?? body?.['msg'] ?? text;
    // A 422 validation error carries detail as an array of {type, loc, msg} — keep it readable.
    const reason = typeof detail === 'string' ? detail : JSON.stringify(detail);
    throw new ContentOrganizerError(`${what} failed (HTTP ${res.status}): ${preview(reason)}`, res.status);
  }
  return body;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

function preview(text: string): string {
  return text.length > 300 ? `${text.slice(0, 297)}...` : text;
}
