#!/usr/bin/env node
/**
 * Content Organizer (OCR) smoke test — run INSIDE an activepieces Pod / container to check, layer by
 * layer, everything the content-organizer piece needs: environment, DNS, network + TLS, iB2B token,
 * CO upload (#9) and CO completion (#1). Prints no secret and no token.
 *
 *   kubectl -n <ns> cp deploy/scripts/content-organizer-smoke.mjs <activepieces-pod>:/tmp/smoke.mjs
 *   kubectl -n <ns> cp <a-labelled.pdf> <activepieces-pod>:/tmp/sample.pdf          # optional
 *   kubectl -n <ns> exec <activepieces-pod> -- node /tmp/smoke.mjs [/tmp/sample.pdf] [staffId]
 *
 * Without a PDF it stops after the iB2B token. It runs as plain node, i.e. OUTSIDE the engine
 * sandbox: it proves network / TLS / credentials, not AP_SSRF_ALLOW_LIST or
 * AP_SANDBOX_PROPAGATED_ENV_VARS (the guide checks those separately).
 * Guide: docs/guides/content-organizer-ocr-guide.md §12.
 */
import { randomUUID } from 'node:crypto';
import { lookup } from 'node:dns/promises';
import { readFile } from 'node:fs/promises';
import { basename } from 'node:path';

const [pdfPath, staffIdArg] = process.argv.slice(2);
const env = (n) => (process.env[n] ?? '').trim();
let failed = false;

function step(name, ok, detail = '') {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
  if (!ok) failed = true;
  return ok;
}

function describeError(e) {
  const c = e?.cause;
  return [e?.name, e?.message, c?.code, c?.message].filter(Boolean).join(' | ');
}

async function post(url, init) {
  try {
    const res = await fetch(url, { method: 'POST', ...init, signal: AbortSignal.timeout(60_000) });
    return { res, text: await res.text() };
  } catch (e) {
    return { error: describeError(e) };
  }
}

const preview = (t) => (t.length > 300 ? `${t.slice(0, 297)}...` : t);

// 1. environment
const names = ['IB2B_TOKEN_URL', 'IB2B_USERNAME', 'IB2B_SECRET', 'CONTENT_ORGANIZER_BASE_URL', 'CONTENT_ORGANIZER_APPLICATION_ID'];
for (const n of names) {
  const v = env(n);
  step(`env ${n}`, v !== '', n === 'IB2B_SECRET' ? (v ? `set (${v.length} chars)` : 'missing') : v || 'missing');
}
const propagated = env('AP_SANDBOX_PROPAGATED_ENV_VARS').split(',').map((s) => s.trim());
const notPropagated = names.filter((n) => !propagated.includes(n));
step('AP_SANDBOX_PROPAGATED_ENV_VARS lists them', notPropagated.length === 0,
  notPropagated.length ? `missing: ${notPropagated.join(', ')}` : '');
console.log(`INFO  AP_SSRF_ALLOW_LIST=${env('AP_SSRF_ALLOW_LIST') || '(empty)'}`);
console.log(`INFO  NODE_EXTRA_CA_CERTS=${env('NODE_EXTRA_CA_CERTS') || '(not set)'}`);
if (failed) process.exit(1);

const base = env('CONTENT_ORGANIZER_BASE_URL').replace(/\/+$/, '');
const appId = env('CONTENT_ORGANIZER_APPLICATION_ID');

// 2. DNS
for (const url of [env('IB2B_TOKEN_URL'), base]) {
  const host = new URL(url).hostname;
  try {
    const addrs = await lookup(host, { all: true });
    step(`dns ${host}`, true, addrs.map((a) => a.address).join(', '));
  } catch (e) {
    step(`dns ${host}`, false, describeError(e));
  }
}

// 3. iB2B token (network + TLS + credentials)
const ib2b = await post(env('IB2B_TOKEN_URL'), {
  headers: { 'Content-Type': 'application/json', Accept: '*/*' },
  body: JSON.stringify({
    input_token_state: { token_type: 'CREDENTIAL', username: env('IB2B_USERNAME'), password: env('IB2B_SECRET') },
    output_token_state: { token_type: 'JWT' },
  }),
});
let token;
if (ib2b.error) {
  step('iB2B token', false, ib2b.error);
} else {
  try { token = JSON.parse(ib2b.text).issued_token; } catch { /* reported below */ }
  if (step('iB2B token', ib2b.res.ok && typeof token === 'string', `HTTP ${ib2b.res.status}${token ? '' : ` ${preview(ib2b.text)}`}`)) {
    try {
      const claims = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString());
      console.log(`INFO  token sub=${claims.sub ?? '?'} exp=${claims.exp ? new Date(claims.exp * 1000).toISOString() : '(none)'}`);
    } catch {
      console.log('INFO  token is not a decodable JWT');
    }
  }
}
if (!token || !pdfPath) {
  if (!pdfPath && token) console.log('INFO  no PDF given — stopping after the token. Pass a labelled PDF to test upload + completion.');
  process.exit(failed ? 1 : 0);
}

// 4. CO #9 upload
const staffId = staffIdArg || env('SMOKE_STAFF_ID');
if (!step('staff id given', Boolean(staffId), 'second argument or SMOKE_STAFF_ID')) process.exit(1);
const sessionId = `hermes-smoke-${randomUUID()}`;
const form = new FormData();
form.append('files', new Blob([await readFile(pdfPath)], { type: 'application/pdf' }), basename(pdfPath));
form.append('userId', staffId);
const up = await post(`${base}/api/management-service/api/applications/${encodeURIComponent(appId)}/sessions/${sessionId}/files`, {
  headers: { 'X-HSBC-E2E-Trust-Token': token },
  body: form,
});
if (up.error || !step('CO upload (#9)', up.res.ok, `HTTP ${up.res.status} ${preview(up.text)}`)) {
  if (up.error) step('CO upload (#9)', false, up.error);
  process.exit(1);
}

// 5. CO #1 completion (same sessionId + applicationId + userId)
const done = await post(`${base}/api/management-service/chat/completion`, {
  headers: { 'Content-Type': 'application/json', 'X-HSBC-E2E-Trust-Token': token },
  body: JSON.stringify({
    sessionId,
    userId: staffId,
    metadata: { apiVersion: env('CONTENT_ORGANIZER_API_VERSION') || '2024-10-01-preview' },
    parameter: {
      applicationId: appId,
      messages: [{ role: 'user', content: 'Return ONLY a JSON object {"title": <the document title>}.' }],
    },
  }),
});
if (done.error) {
  step('CO completion (#1)', false, done.error);
} else {
  let content;
  try { content = JSON.parse(done.text)?.data?.message?.[0]?.content; } catch { /* reported below */ }
  step('CO completion (#1)', done.res.ok && typeof content === 'string',
    `HTTP ${done.res.status} ${preview(typeof content === 'string' ? content : done.text)}`);
}
process.exit(failed ? 1 : 0);
