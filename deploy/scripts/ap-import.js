#!/usr/bin/env node
/*
 * Activepieces flow 导入/发布(测试→生产 发布通道的"导入"半边,见 ACTIVEPIECES_INTEGRATION.md §7)。
 *
 * 读 git 里的 flow 定义 JSON(ap-export.js 产出),在目标 AP(用共享账号)幂等地:
 *   不存在 → POST /api/v1/flows 建 → IMPORT_FLOW 灌定义 → LOCK_AND_PUBLISH → CHANGE_STATUS=ENABLED
 *   已存在(按 displayName 对齐) → IMPORT_FLOW 覆盖新版本 → 重发布 → 启用
 * 重复运行安全(幂等)。各步均经实测(AP CE 0.84:create 201 / IMPORT_FLOW 200 / LOCK_AND_PUBLISH 200 / CHANGE_STATUS 200)。
 *
 * 重要(坑,见 §7 / §11.5):
 *   - connection 不跟着导:生产须**预先建好同名 connection**,否则 flow 跑不起来。
 *   - flowId 跨环境会变:目标环境新建的 flow 有**新的 id**。脚本最后会打印目标 flowId——
 *     BPMN service task 的 ap:flowId 需按目标环境填这个新 id(webhook base-url 由引擎按环境配,自动适配)。
 *
 * 用法:
 *   A. 能直连 AP(CI / 容器内):
 *        AP_INTERNAL_URL=https://生产AP ACTIVEPIECES_SHARED_EMAIL=... ACTIVEPIECES_SHARED_PASSWORD=... \
 *        node deploy/scripts/ap-import.js deploy/ap-flows/aptest.json
 *   B. dev(经 stdin 喂脚本进 AP 容器,flow JSON 用 FLOW_JSON 环境变量传):
 *        PW=$(docker exec platform-admin-center-dev sh -c 'printf %s "$ACTIVEPIECES_SHARED_PASSWORD"')
 *        docker exec -e AP_INTERNAL_URL=http://localhost:80 \
 *          -e ACTIVEPIECES_SHARED_EMAIL=hermes-svc@platform.local -e ACTIVEPIECES_SHARED_PASSWORD="$PW" \
 *          -e FLOW_JSON="$(cat deploy/ap-flows/aptest.json)" -i platform-activepieces-dev node - \
 *          < deploy/scripts/ap-import.js
 *
 * 入参:第 1 个 arg = flow JSON 文件路径;或 FLOW_JSON 环境变量 = JSON 内容。
 */
'use strict';
const http = require('http');
const fs = require('fs');
const { URL } = require('url');

const BASE = (process.env.AP_INTERNAL_URL || 'http://localhost:80').replace(/\/+$/, '');
const EMAIL = process.env.ACTIVEPIECES_SHARED_EMAIL || '';
const PASSWORD = process.env.ACTIVEPIECES_SHARED_PASSWORD || '';
const FILE = process.argv[2] || process.env.FLOW_FILE || '';

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

function loadDef() {
  let raw;
  if (FILE) raw = fs.readFileSync(FILE, 'utf8');
  else if (process.env.FLOW_JSON) raw = process.env.FLOW_JSON;
  else throw new Error('flow JSON required (arg1 file path or FLOW_JSON env)');
  const def = JSON.parse(raw);
  if (!def.displayName || !def.trigger) throw new Error('invalid flow def: need displayName + trigger');
  return def;
}

function ok(r, step) {
  if (r.status < 200 || r.status >= 300) throw new Error(step + ' failed: HTTP ' + r.status + ' ' + r.body.slice(0, 300));
  return r;
}

async function findByName(token, projectId, name) {
  const list = await req('GET', '/api/v1/flows?projectId=' + projectId + '&limit=100', null, token);
  const flows = (JSON.parse(list.body).data) || [];
  const hit = flows.find((f) => f.version && f.version.displayName === name);
  return hit ? hit.id : null;
}

async function main() {
  if (!EMAIL || !PASSWORD) throw new Error('ACTIVEPIECES_SHARED_EMAIL / ACTIVEPIECES_SHARED_PASSWORD required');
  const def = loadDef();
  const { token, projectId } = await signIn();

  // 1) 幂等对齐:按 displayName 找现有 flow,没有就建
  let flowId = await findByName(token, projectId, def.displayName);
  if (flowId) {
    console.error('[ap-import] existing flow "' + def.displayName + '" -> ' + flowId + ' (will overwrite version)');
  } else {
    const cr = ok(await req('POST', '/api/v1/flows', { projectId, displayName: def.displayName }, token), 'create');
    flowId = JSON.parse(cr.body).id;
    console.error('[ap-import] created flow "' + def.displayName + '" -> ' + flowId);
  }

  // 2) IMPORT_FLOW 灌入定义(displayName + trigger 整棵树)
  ok(await req('POST', '/api/v1/flows/' + flowId,
    { type: 'IMPORT_FLOW', request: { displayName: def.displayName, trigger: def.trigger, schemaVersion: def.schemaVersion } },
    token), 'IMPORT_FLOW');

  // 3) 锁定并发布
  ok(await req('POST', '/api/v1/flows/' + flowId, { type: 'LOCK_AND_PUBLISH', request: {} }, token), 'LOCK_AND_PUBLISH');

  // 4) 启用
  ok(await req('POST', '/api/v1/flows/' + flowId, { type: 'CHANGE_STATUS', request: { status: 'ENABLED' } }, token), 'CHANGE_STATUS');

  // 5) 校验 + 打印目标 flowId(BPMN 的 ap:flowId 按此填)
  const chk = JSON.parse((await req('GET', '/api/v1/flows/' + flowId, null, token)).body);
  console.error('[ap-import] DONE  flowId=' + flowId + '  status=' + chk.status + '  published=' + !!chk.publishedVersionId);
  console.error('[ap-import] => set BPMN service task ap:flowId=' + flowId + ' for this environment');
  process.stdout.write(flowId + '\n'); // stdout 只打 flowId,便于脚本/CI 捕获
}

main().catch((e) => { console.error('[ap-import] ' + String(e && e.message || e)); process.exit(1); });
