#!/usr/bin/env node
/*
 * Activepieces 供给校验（只读 / fail-loud，绝不改任何东西）。
 *
 * 背景：AP 把全部状态放在 Postgres 里。新环境、重建卷、或手工 drop 掉 AP 那套表之后，AP 自己的
 * migration 会把 schema 建回来但**数据一条不剩**——没有 platform / project / signing_key /
 * piece_metadata。重打镜像不恢复任何一项。症状是 DW 的 Automation 页签报错，而各处日志都是绿的：
 * 桥拿着一个在 signing_key 里已不存在的 key 去签名，AP 侧只回 401。
 *
 * `ap-bootstrap-shared-account.js` 只补第一项（platform + project）。剩下三项在 k8s 上仍是手工步骤，
 * 本脚本负责**在部署时把缺口喊出来**，而不是等有人点开 Automation 页签才发现。
 *
 * 为什么只检不修（裁决，2026-07-29）：
 *   - signing-key 的私钥只在创建时返回一次，要落进 `workflow-platform-secrets`。让 Job 自动写
 *     Secret 需要给它一个能改 Secret 的 ServiceAccount + RBAC，在气隙/合规集群里未必批得下来，
 *     且一旦 Job 重跑就有轮换掉正在用的密钥的风险。故只报告，由人执行。
 *   - project externalId 与 piece_metadata 的写入都要直连 DB，同理不在此处做。
 *
 * 全部检查走 AP 的 REST API（共享账号 token），不需要 psql、不需要 RBAC、不需要 DB 凭据。
 *
 * 退出码：0 = 全部就绪；1 = 有缺口（stdout 打印逐条手工修复命令）；2 = 连不上 / 认证失败。
 *
 * 环境变量：
 *   AP_INTERNAL_URL                          AP 服务端地址（k8s: http://activepieces-service:80）
 *   ACTIVEPIECES_SHARED_EMAIL / _PASSWORD    共享账号（与 ap-bootstrap-shared-account.js 同源）
 *   ACTIVEPIECES_MANAGED_PROJECT_EXTERNAL_ID 期望的 project externalId（默认 hermes-main）
 *   ACTIVEPIECES_MANAGED_ENABLED             'true' 时才要求 signing-key（L7 per-user 供给）
 *   AP_VERIFY_TIMEOUT_MS                     等待 AP 就绪的超时（默认 180000）
 */
'use strict';
const http = require('http');
const { URL } = require('url');

const BASE = (process.env.AP_INTERNAL_URL || 'http://localhost:80').replace(/\/+$/, '');
const EMAIL = process.env.ACTIVEPIECES_SHARED_EMAIL || '';
const PASSWORD = process.env.ACTIVEPIECES_SHARED_PASSWORD || '';
const PROJECT_EXTERNAL_ID = process.env.ACTIVEPIECES_MANAGED_PROJECT_EXTERNAL_ID || 'hermes-main';
const MANAGED_ENABLED = String(process.env.ACTIVEPIECES_MANAGED_ENABLED || '').toLowerCase() === 'true';
const TIMEOUT_MS = parseInt(process.env.AP_VERIFY_TIMEOUT_MS || '180000', 10);

function req(method, path, body, token) {
  return new Promise((resolve, reject) => {
    const u = new URL(BASE + path);
    const payload = body ? JSON.stringify(body) : null;
    const headers = {};
    if (payload) { headers['Content-Type'] = 'application/json'; headers['Content-Length'] = Buffer.byteLength(payload); }
    if (token) { headers['Authorization'] = 'Bearer ' + token; }
    const r = http.request(
      { method, hostname: u.hostname, port: u.port || 80, path: u.pathname + u.search, headers },
      (res) => {
        let d = '';
        res.on('data', (c) => (d += c));
        res.on('end', () => resolve({ status: res.statusCode, body: d }));
      }
    );
    r.on('error', reject);
    if (payload) r.write(payload);
    r.end();
  });
}

function parseJson(s) { try { return JSON.parse(s); } catch (_) { return null; } }

/** AP list endpoints are inconsistent: some return a bare array, some {data:[...]}. */
function asList(body) {
  const j = parseJson(body);
  if (Array.isArray(j)) return j;
  if (j && Array.isArray(j.data)) return j.data;
  return null;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function waitReady() {
  const deadline = Date.now() + TIMEOUT_MS;
  while (Date.now() < deadline) {
    try {
      const r = await req('GET', '/api/v1/flags');
      if (r.status === 200) return;
    } catch (_) { /* not up yet */ }
    await sleep(3000);
  }
  throw new Error('Activepieces did not become ready within ' + TIMEOUT_MS + 'ms');
}

async function main() {
  if (!EMAIL || !PASSWORD) {
    console.log('[ap-verify] shared account not configured -> skip (prod runtime-only needs no bridge).');
    return 0;
  }
  console.log('[ap-verify] target=' + BASE + ' email=' + EMAIL);
  await waitReady();

  const signinRes = await req('POST', '/api/v1/authentication/sign-in', { email: EMAIL, password: PASSWORD });
  if (signinRes.status !== 200) {
    console.error('[ap-verify] sign-in failed: HTTP ' + signinRes.status + ' ' + signinRes.body.slice(0, 200));
    console.error('[ap-verify] run the bootstrap Job first (ap-bootstrap-shared-account.js).');
    return 2;
  }
  const session = parseJson(signinRes.body) || {};
  const token = session.token;
  const gaps = [];

  // --- 1. platform + project (what ap-bootstrap-shared-account.js provides) ---
  // A projectId of null means the account is stuck in ONBOARDING: sign-up ran but
  // POST /v1/platforms never did, so AP has an identity and nothing else.
  if (!session.platformId || !session.projectId) {
    gaps.push({
      what: 'platform / default project',
      detail: 'sign-in returned platformId=' + session.platformId + ' projectId=' + session.projectId
        + ' (ONBOARDING state — AP has the identity but no platform/project)',
      fix: [
        'kubectl -n <ns> delete job ap-bootstrap-shared-account --ignore-not-found',
        'kubectl -n <ns> apply -f ap-bootstrap-job.yaml   # re-run the bootstrap Job',
      ],
    });
    // Everything below needs a platform, so stop here rather than emit noise.
    report(gaps);
    return 1;
  }
  console.log('[ap-verify] platform=' + session.platformId + ' project=' + session.projectId + ' OK');

  // --- 2. project externalId ---
  // managed-authn resolves the shared project BY externalId; when it does not match it silently
  // CREATES A SECOND project, and the shared account and per-user accounts then cannot see each
  // other's flows. Must be stamped before the first managed exchange.
  const projects = asList((await req('GET', '/api/v1/projects', null, token)).body) || [];
  const stamped = projects.filter((p) => p && p.externalId === PROJECT_EXTERNAL_ID);
  if (stamped.length === 0) {
    gaps.push({
      what: 'project externalId=' + PROJECT_EXTERNAL_ID,
      detail: 'no project carries it (found: '
        + (projects.map((p) => p.displayName + '=' + (p.externalId || 'null')).join(', ') || 'none') + ')',
      fix: [
        'kubectl -n <ns> exec deploy/postgres -- psql -U <user> -d <db> -c \\',
        '  "update project set \\"externalId\\"=\'' + PROJECT_EXTERNAL_ID + '\' where \\"externalId\\" is null;"',
        '# must run BEFORE anyone opens the Automation tab, or a second project gets created',
      ],
    });
  } else {
    console.log('[ap-verify] project externalId=' + PROJECT_EXTERNAL_ID + ' OK');
  }

  // --- 3. signing key (only meaningful when L7 managed auth is on) ---
  if (MANAGED_ENABLED) {
    const keysRes = await req('GET', '/api/v1/signing-keys', null, token);
    const keys = asList(keysRes.body);
    if (keys === null) {
      gaps.push({
        what: 'signing key',
        detail: 'GET /v1/signing-keys returned HTTP ' + keysRes.status + ' ' + keysRes.body.slice(0, 160),
        fix: ['# the shared account must be a platform admin to list signing keys'],
      });
    } else if (keys.length === 0) {
      gaps.push({
        what: 'signing key (ACTIVEPIECES_MANAGED_ENABLED=true)',
        detail: 'signing_key is empty — every managed sign-in will 401 and the DW Automation tab '
          + 'will fail to mount, even though AP itself looks healthy',
        fix: [
          '# 1) mint a key (the private key is returned ONCE and never again):',
          'kubectl -n <ns> exec deploy/activepieces -- node -e "' +
            'const h=require(\'http\');const rq=(m,p,b,t)=>new Promise(r=>{const q=b?JSON.stringify(b):null;' +
            'const x=h.request({method:m,hostname:\'localhost\',port:80,path:p,headers:Object.assign(' +
            'q?{\'Content-Type\':\'application/json\',\'Content-Length\':Buffer.byteLength(q)}:{},' +
            't?{Authorization:\'Bearer \'+t}:{})},s=>{let d=\'\';s.on(\'data\',c=>d+=c);s.on(\'end\',()=>r(d))});' +
            'if(q)x.write(q);x.end()});(async()=>{const s=JSON.parse(await rq(\'POST\',' +
            '\'/api/v1/authentication/sign-in\',{email:process.env.E,password:process.env.P}));' +
            'const k=JSON.parse(await rq(\'POST\',\'/api/v1/signing-keys\',{displayName:\'hermes\'},s.token));' +
            'console.log(k.id);console.log(k.privateKey.replace(/\\r?\\n/g,\'\'))})()"',
          '# 2) put id + single-line private key into the secret, then restart admin-center:',
          'kubectl -n <ns> edit secret workflow-platform-secrets   # ACTIVEPIECES_MANAGED_PRIVATE_KEY (base64)',
          'kubectl -n <ns> edit configmap workflow-platform-config # ACTIVEPIECES_MANAGED_SIGNING_KEY_ID',
          'kubectl -n <ns> rollout restart deploy/admin-center',
          '# NOT automated on purpose: writing Secrets from a Job needs RBAC, and a re-run would',
          '# rotate a key that is currently in use. See this script header.',
        ],
      });
    } else {
      console.log('[ap-verify] signing keys: ' + keys.length + ' OK');
    }
  } else {
    console.log('[ap-verify] ACTIVEPIECES_MANAGED_ENABLED is not true -> signing key not required.');
  }

  // --- 4. piece catalog (designer half of the allowlist) ---
  // AP_PIECES_SYNC_MODE=NONE: nothing ever populates piece_metadata by itself.
  const piecesRes = await req('GET', '/api/v1/pieces', null, token);
  const pieces = asList(piecesRes.body);
  if (pieces === null) {
    gaps.push({
      what: 'piece catalog',
      detail: 'GET /v1/pieces returned HTTP ' + piecesRes.status + ' ' + piecesRes.body.slice(0, 160),
      fix: ['# unexpected — check AP logs'],
    });
  } else if (pieces.length === 0) {
    gaps.push({
      what: 'piece catalog (piece_metadata)',
      detail: 'zero pieces — the Automation canvas will offer no steps at all',
      fix: [
        'kubectl -n <ns> cp deploy/pieces/metadata/pieces-seed.sql <postgres-pod>:/tmp/pieces-seed.sql',
        'kubectl -n <ns> exec <postgres-pod> -- psql -U <user> -d <db> -v ON_ERROR_STOP=1 -f /tmp/pieces-seed.sql',
        'kubectl -n <ns> rollout restart deploy/activepieces   # registry is cached in-process',
      ],
    });
  } else {
    console.log('[ap-verify] pieces: ' + pieces.length + ' OK');
  }

  report(gaps);
  return gaps.length > 0 ? 1 : 0;
}

function report(gaps) {
  if (gaps.length === 0) {
    console.log('[ap-verify] all Activepieces provisioning checks passed.');
    return;
  }
  console.error('');
  console.error('==================================================================');
  console.error(' Activepieces provisioning INCOMPLETE — ' + gaps.length + ' gap(s)');
  console.error(' The deployment is up but the DW Automation tab will not work.');
  console.error('==================================================================');
  for (const g of gaps) {
    console.error('');
    console.error('MISSING: ' + g.what);
    console.error('  ' + g.detail);
    console.error('  fix:');
    for (const line of g.fix) console.error('    ' + line);
  }
  console.error('');
}

main().then((code) => process.exit(code)).catch((e) => {
  console.error('[ap-verify] ' + String((e && e.message) || e));
  process.exit(2);
});
