#!/usr/bin/env node
/*
 * 把一条 flow 定义 IMPORT 到**指定 flowId**(而非按 displayName 对齐)。
 * 用于原地改写用户已建好的 flow(保留其 flowId 与内嵌的 aiProviderModel=deepseek 绑定)。
 *
 * 用法(dev,经 stdin 喂进 AP 容器):
 *   docker exec -e AP_INTERNAL_URL=http://localhost:80 -e ACTIVEPIECES_SHARED_EMAIL=... \
 *     -e ACTIVEPIECES_SHARED_PASSWORD="$PW" -e TARGET_FLOW_ID=<目标 flowId> \
 *     -e FLOW_JSON="$(cat deploy/ap-flows/<flow>.json)" -i platform-activepieces-dev \
 *     node - < deploy/scripts/ap-import-to-id.js
 *
 * (本脚本与具体 flow 无关。此前的例子用的是 AI Generate 的 flow,该产物已于 2026-07-29
 *  随 AI Generate 停用一并删除 —— 见 docs/ap-integration/VENDOR_TRIM_CHECKLIST.md 的 VT-15。)
 */
'use strict';
const http = require('http');
const { URL } = require('url');

const BASE = (process.env.AP_INTERNAL_URL || 'http://localhost:80').replace(/\/+$/, '');
const EMAIL = process.env.ACTIVEPIECES_SHARED_EMAIL || '';
const PASSWORD = process.env.ACTIVEPIECES_SHARED_PASSWORD || '';
const TARGET = process.env.TARGET_FLOW_ID || process.argv[2] || '';

function req(method, path, body, token) {
  return new Promise((resolve, reject) => {
    const u = new URL(BASE + path);
    const payload = body ? JSON.stringify(body) : null;
    const headers = Object.assign(
      { 'Content-Type': 'application/json' },
      payload ? { 'Content-Length': Buffer.byteLength(payload) } : {},
      token ? { Authorization: 'Bearer ' + token } : {}
    );
    const r = http.request(
      { method, hostname: u.hostname, port: u.port || 80, path: u.pathname + u.search, headers },
      (res) => { let d = ''; res.on('data', (c) => (d += c)); res.on('end', () => resolve({ status: res.statusCode, body: d })); }
    );
    r.on('error', reject);
    if (payload) r.write(payload);
    r.end();
  });
}
function ok(r, step) {
  if (r.status < 200 || r.status >= 300) throw new Error(step + ' failed: HTTP ' + r.status + ' ' + r.body.slice(0, 400));
  return r;
}
async function main() {
  if (!EMAIL || !PASSWORD) throw new Error('shared email/password required');
  if (!TARGET) throw new Error('TARGET_FLOW_ID required');
  const def = JSON.parse(process.env.FLOW_JSON || '');
  if (!def.displayName || !def.trigger) throw new Error('invalid flow def');

  const si = await req('POST', '/api/v1/authentication/sign-in', { email: EMAIL, password: PASSWORD });
  if (si.status !== 200) throw new Error('sign-in HTTP ' + si.status + ' ' + si.body.slice(0, 200));
  const token = JSON.parse(si.body).token;

  // 确认目标 flow 存在(按 id)
  const cur = await req('GET', '/api/v1/flows/' + TARGET, null, token);
  if (cur.status !== 200) throw new Error('target flow not found: HTTP ' + cur.status + ' ' + cur.body.slice(0, 200));

  ok(await req('POST', '/api/v1/flows/' + TARGET,
    { type: 'IMPORT_FLOW', request: { displayName: def.displayName, trigger: def.trigger, schemaVersion: def.schemaVersion } },
    token), 'IMPORT_FLOW');
  ok(await req('POST', '/api/v1/flows/' + TARGET, { type: 'LOCK_AND_PUBLISH', request: {} }, token), 'LOCK_AND_PUBLISH');
  ok(await req('POST', '/api/v1/flows/' + TARGET, { type: 'CHANGE_STATUS', request: { status: 'ENABLED' } }, token), 'CHANGE_STATUS');

  const chk = JSON.parse((await req('GET', '/api/v1/flows/' + TARGET, null, token)).body);
  // 打印每步 valid,便于核对
  function walk(n, d) { if (!n) return; console.error('  '.repeat(d) + (n.displayName||'?') + ' [' + (n.type||'?') + '] valid=' + n.valid); walk(n.nextAction, d + 1); }
  console.error('[import-to-id] status=' + chk.status + ' published=' + !!chk.publishedVersionId);
  walk(chk.version && chk.version.trigger, 0);
  process.stdout.write(TARGET + '\n');
}
main().catch((e) => { console.error('[import-to-id] ' + String(e && e.message || e)); process.exit(1); });
