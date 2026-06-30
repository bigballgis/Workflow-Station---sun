#!/usr/bin/env node
/*
 * Activepieces 共享账号初始化（幂等）。
 *
 * 新环境（SIT/prod 重置库）时,AP 是空库:共享账号不存在。本脚本把共享账号建好并完成 onboarding。
 *
 * **AP 0.84 CE 行为（实测）**:sign-up 只建 `user_identity`,**不自动建 platform/project**——账号停在
 * ONBOARDING 态、sign-in 返回 `projectId=null` 的 ONBOARDING token。登录桥需要 projectId,缺了 AP 会循环。
 * 故必须再调 `POST /api/v1/platforms` 完成 onboarding(AP 随之建 platform + 默认 project),之后 sign-in
 * 才返回 USER token 带非空 projectId。
 *
 * 流程:sign-in 探测 → 401 则 sign-up 建身份 → 若仍无 platform(ONBOARDING) 则 POST /platforms → 复核 projectId。
 * 幂等:已 onboard(sign-in 带 platformId)则直接成功退出。重复运行安全。
 * 跳过:未配置共享账号(prod runtime-only 不开桥、不需要共享账号)时直接成功退出。
 *
 * 用 node(AP 镜像自带,免装 curl/wget)。dev 与 k8s Job 共用这一份。
 *
 * 环境变量:
 *   AP_INTERNAL_URL          AP 服务端可达地址(默认 http://localhost:80;k8s 用 http://activepieces-service:80)
 *   ACTIVEPIECES_SHARED_EMAIL    共享账号邮箱
 *   ACTIVEPIECES_SHARED_PASSWORD 共享账号密码(须与配置/secret 一致)
 *   ACTIVEPIECES_PLATFORM_NAME   onboarding 时建的 platform 名(默认 "Hermes Automation")
 *   AP_BOOTSTRAP_TIMEOUT_MS  等待 AP 就绪的超时(默认 180000)
 */
'use strict';
const http = require('http');
const { URL } = require('url');

const BASE = (process.env.AP_INTERNAL_URL || 'http://localhost:80').replace(/\/+$/, '');
const EMAIL = process.env.ACTIVEPIECES_SHARED_EMAIL || '';
const PASSWORD = process.env.ACTIVEPIECES_SHARED_PASSWORD || '';
const PLATFORM_NAME = process.env.ACTIVEPIECES_PLATFORM_NAME || 'Hermes Automation';
const TIMEOUT_MS = parseInt(process.env.AP_BOOTSTRAP_TIMEOUT_MS || '180000', 10);

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
        let data = '';
        res.on('data', (c) => (data += c));
        res.on('end', () => resolve({ status: res.statusCode, body: data }));
      }
    );
    r.on('error', reject);
    if (payload) r.write(payload);
    r.end();
  });
}

function parseJson(s) { try { return JSON.parse(s); } catch (_) { return {}; } }

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
    console.log('[ap-bootstrap] shared account not configured (no email/password) -> skip.');
    return; // prod runtime-only: nothing to do
  }
  console.log('[ap-bootstrap] target=' + BASE + ' email=' + EMAIL);
  await waitReady();

  // 1) 确保身份存在：sign-in 探测，401 则 sign-up。
  //    注意 AP 0.84 CE：sign-up 只建 user_identity，**不建 platform/project**，账号停在 ONBOARDING 态、
  //    sign-in 返回 projectId=null 的 ONBOARDING token。故不能像旧版那样 "sign-in 200 即完成"。
  let signin = await req('POST', '/api/v1/authentication/sign-in', { email: EMAIL, password: PASSWORD });
  if (signin.status !== 200) {
    console.log('[ap-bootstrap] sign-in returned ' + signin.status + ' -> sign-up (create identity).');
    const signup = await req('POST', '/api/v1/authentication/sign-up', {
      email: EMAIL, password: PASSWORD, firstName: 'Hermes', lastName: 'Service',
      trackEvents: false, newsLetter: false,
    });
    if (!(signup.status >= 200 && signup.status < 300)) {
      throw new Error('[ap-bootstrap] sign-up failed: HTTP ' + signup.status + ' body=' + signup.body.slice(0, 300)
        + '  (若账号已存在,请确认密码与 ACTIVEPIECES_SHARED_PASSWORD 一致)');
    }
    signin = await req('POST', '/api/v1/authentication/sign-in', { email: EMAIL, password: PASSWORD });
    if (signin.status !== 200) {
      throw new Error('[ap-bootstrap] sign-in after sign-up failed: HTTP ' + signin.status);
    }
  }

  // 2) 已 onboard?有 platformId 就说明 platform/project 都在 -> 幂等成功。
  const session = parseJson(signin.body);
  if (session.platformId) {
    console.log('[ap-bootstrap] shared account already onboarded (platformId=' + session.platformId
      + ', projectId=' + session.projectId + ') -> nothing to do.');
    return;
  }

  // 3) ONBOARDING 态（无 platform）-> 用 ONBOARDING token 调 POST /platforms 完成 onboarding。
  //    AP 会建 platform + 默认 project；之后 sign-in 返回 USER token 带非空 projectId（登录桥需要它）。
  console.log('[ap-bootstrap] account is in ONBOARDING (no platform) -> creating platform to complete onboarding.');
  const created = await req('POST', '/api/v1/platforms', { name: PLATFORM_NAME }, session.token);
  if (!(created.status >= 200 && created.status < 300)) {
    throw new Error('[ap-bootstrap] platform creation failed: HTTP ' + created.status + ' body=' + created.body.slice(0, 300));
  }

  // 4) 复核：sign-in 现在应带非空 projectId。
  const verify = parseJson((await req('POST', '/api/v1/authentication/sign-in', { email: EMAIL, password: PASSWORD })).body);
  if (!verify.projectId) {
    throw new Error('[ap-bootstrap] platform created but sign-in still has no projectId -> manual check needed.');
  }
  console.log('[ap-bootstrap] onboarding complete (platformId=' + verify.platformId
    + ', projectId=' + verify.projectId + ').');
}

main().then(() => process.exit(0)).catch((e) => { console.error(String(e && e.message || e)); process.exit(1); });
