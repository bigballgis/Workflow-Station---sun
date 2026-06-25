#!/usr/bin/env node
/*
 * Activepieces 共享账号初始化（幂等）。
 *
 * 新环境（SIT/prod 重置库）时,AP 是空库:共享账号不存在。AP 的逻辑是「第一个注册用户 =
 * platform owner(ADMIN)」并自动建 platform + project。本脚本就用 sign-up 把共享账号建成第一个用户。
 *
 * 幂等:先 sign-in 探测——已存在则直接成功退出;不存在才 sign-up。重复运行安全。
 * 跳过:未配置共享账号(prod runtime-only 不开桥、不需要共享账号)时直接成功退出。
 *
 * 用 node(AP 镜像自带,免装 curl/wget)。dev 与 k8s Job 共用这一份。
 *
 * 环境变量:
 *   AP_INTERNAL_URL          AP 服务端可达地址(默认 http://localhost:80;k8s 用 http://activepieces-service:80)
 *   ACTIVEPIECES_SHARED_EMAIL    共享账号邮箱
 *   ACTIVEPIECES_SHARED_PASSWORD 共享账号密码(须与配置/secret 一致)
 *   AP_BOOTSTRAP_TIMEOUT_MS  等待 AP 就绪的超时(默认 180000)
 */
'use strict';
const http = require('http');
const { URL } = require('url');

const BASE = (process.env.AP_INTERNAL_URL || 'http://localhost:80').replace(/\/+$/, '');
const EMAIL = process.env.ACTIVEPIECES_SHARED_EMAIL || '';
const PASSWORD = process.env.ACTIVEPIECES_SHARED_PASSWORD || '';
const TIMEOUT_MS = parseInt(process.env.AP_BOOTSTRAP_TIMEOUT_MS || '180000', 10);

function req(method, path, body) {
  return new Promise((resolve, reject) => {
    const u = new URL(BASE + path);
    const payload = body ? JSON.stringify(body) : null;
    const r = http.request(
      { method, hostname: u.hostname, port: u.port || 80, path: u.pathname + u.search,
        headers: payload ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload) } : {} },
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

  // 1) 已存在?能 sign-in 就说明账号在、密码对 -> 幂等成功
  const signin = await req('POST', '/api/v1/authentication/sign-in', { email: EMAIL, password: PASSWORD });
  if (signin.status === 200) {
    console.log('[ap-bootstrap] shared account already exists and signs in OK -> nothing to do.');
    return;
  }
  console.log('[ap-bootstrap] sign-in returned ' + signin.status + ' -> attempting sign-up (first user = owner).');

  // 2) 不存在 -> sign-up 建为第一个用户(owner/ADMIN,自动建 platform + project)
  const signup = await req('POST', '/api/v1/authentication/sign-up', {
    email: EMAIL, password: PASSWORD, firstName: 'Hermes', lastName: 'Service',
    trackEvents: false, newsLetter: false,
  });
  if (signup.status >= 200 && signup.status < 300) {
    console.log('[ap-bootstrap] shared account created successfully.');
    return;
  }
  // sign-up 失败:很可能账号已存在但密码与 secret 不一致(此时 sign-in 也失败) -> 明确报错
  throw new Error('[ap-bootstrap] sign-up failed: HTTP ' + signup.status + ' body=' + signup.body.slice(0, 300)
    + '  (若账号已存在,请确认密码与 ACTIVEPIECES_SHARED_PASSWORD 一致)');
}

main().then(() => process.exit(0)).catch((e) => { console.error(String(e && e.message || e)); process.exit(1); });
