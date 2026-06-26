#!/usr/bin/env node
/*
 * Activepieces flow 导出(测试→生产 发布通道的"导出"半边,见 ACTIVEPIECES_INTEGRATION.md §7)。
 *
 * 从非生产 AP 用共享账号导出一条 flow 的**定义**(displayName + trigger 整棵树 + schemaVersion),
 * 输出为 JSON,提交进 git(deploy/ap-flows/<name>.json)作为单一事实来源。再由 ap-import.js 发到生产。
 *
 * 导出的 JSON **不含密钥**:flow 引用的 connection 是 per-环境凭据,只带名字不带值。
 * 生产须预先建好同名 connection(见 §7 / §11.5)。
 *
 * 用法(两种,任选其一):
 *   A. 能直连 AP 的环境(CI / 容器内,node 可达 AP_INTERNAL_URL):
 *        AP_INTERNAL_URL=http://localhost:8086 \
 *        ACTIVEPIECES_SHARED_EMAIL=... ACTIVEPIECES_SHARED_PASSWORD=... \
 *        node deploy/scripts/ap-export.js <flowId|flowName> [outFile]
 *   B. dev(AP 只在容器内可达,把脚本经 stdin 喂进 AP 容器,stdout 重定向到 git 文件):
 *        PW=$(docker exec platform-admin-center-dev sh -c 'printf %s "$ACTIVEPIECES_SHARED_PASSWORD"')
 *        docker exec -e AP_INTERNAL_URL=http://localhost:80 \
 *          -e ACTIVEPIECES_SHARED_EMAIL=hermes-svc@platform.local -e ACTIVEPIECES_SHARED_PASSWORD="$PW" \
 *          -e AP_FLOW=aptest -i platform-activepieces-dev node - \
 *          < deploy/scripts/ap-export.js > deploy/ap-flows/aptest.json
 *
 * 入参:第 1 个 arg 或 AP_FLOW 环境变量 = flow id 或 displayName;第 2 个 arg 或 OUT_FILE = 输出文件(缺省打 stdout)。
 */
'use strict';
const http = require('http');
const fs = require('fs');
const { URL } = require('url');

const BASE = (process.env.AP_INTERNAL_URL || 'http://localhost:80').replace(/\/+$/, '');
const EMAIL = process.env.ACTIVEPIECES_SHARED_EMAIL || '';
const PASSWORD = process.env.ACTIVEPIECES_SHARED_PASSWORD || '';
const FLOW = process.argv[2] || process.env.AP_FLOW || '';
const OUT_FILE = process.argv[3] || process.env.OUT_FILE || '';

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

async function signIn() {
  const r = await req('POST', '/api/v1/authentication/sign-in', { email: EMAIL, password: PASSWORD });
  if (r.status !== 200) throw new Error('sign-in failed: HTTP ' + r.status + ' ' + r.body.slice(0, 200));
  const a = JSON.parse(r.body);
  return { token: a.token, projectId: a.projectId };
}

async function resolveFlowId(token, projectId, flow) {
  // 直接按 id 取
  const byId = await req('GET', '/api/v1/flows/' + encodeURIComponent(flow), null, token);
  if (byId.status === 200) return flow;
  // 否则按 displayName 在 flow 列表里找
  const list = await req('GET', '/api/v1/flows?projectId=' + projectId + '&limit=100', null, token);
  const flows = (JSON.parse(list.body).data) || [];
  const hit = flows.find((f) => f.version && f.version.displayName === flow);
  if (!hit) throw new Error('flow not found by id or name: ' + flow);
  return hit.id;
}

async function main() {
  if (!EMAIL || !PASSWORD) throw new Error('ACTIVEPIECES_SHARED_EMAIL / ACTIVEPIECES_SHARED_PASSWORD required');
  if (!FLOW) throw new Error('flow id or name required (arg1 or AP_FLOW)');
  const { token, projectId } = await signIn();
  const flowId = await resolveFlowId(token, projectId, FLOW);
  const r = await req('GET', '/api/v1/flows/' + flowId, null, token);
  if (r.status !== 200) throw new Error('export GET failed: HTTP ' + r.status);
  const f = JSON.parse(r.body);
  const v = f.version || {};
  // 只保留跨环境可移植的定义,丢掉 per-环境 id（id/projectId/publishedVersionId/connectionIds 等）
  const out = {
    displayName: v.displayName,
    schemaVersion: v.schemaVersion,
    externalId: f.externalId || null, // 稳定标识(可选),便于跨环境对齐
    trigger: v.trigger,
    _exportedFrom: { flowId, projectId },
  };
  const json = JSON.stringify(out, null, 2);
  if (OUT_FILE) { fs.writeFileSync(OUT_FILE, json + '\n'); console.error('[ap-export] wrote ' + OUT_FILE + ' (flow "' + v.displayName + '")'); }
  else { process.stdout.write(json + '\n'); }
}

main().catch((e) => { console.error('[ap-export] ' + String(e && e.message || e)); process.exit(1); });
