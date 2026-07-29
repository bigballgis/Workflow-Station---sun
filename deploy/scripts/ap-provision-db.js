#!/usr/bin/env node
/*
 * Activepieces 供给的「要写库」那两步（幂等）：project externalId stamp + piece_metadata seed。
 *
 * 为什么要单独一个脚本：`ap-bootstrap-shared-account.js` 走 AP 的 REST API 就能建出
 * platform/project，但另外两项 AP 没有对应的写接口——
 *   - project 的 `externalId` 只能改库（AP 不暴露"给已有 project 打 externalId"的端点）；
 *   - `piece_metadata` 在 `AP_PIECES_SYNC_MODE=NONE` 下没有任何人会去填，只能灌 seed SQL。
 *
 * 写库需要的是 **DB 凭据**，不是 Kubernetes RBAC——集群里 AP 的 Deployment 本来就从
 * `workflow-platform-config` / `workflow-platform-secrets` 拿这几个键，这里原样复用同一组，
 * 不新增任何权限。（signing-key 仍然只报不改：私钥要写进 Secret，那个才真的需要 RBAC，
 * 且重跑会轮换正在用的密钥——见 ap-verify-provisioning.js 头注释。）
 *
 * **灌完必须让 AP 的 registry 缓存失效**，但不用重启：直接往 Redis 频道
 * `piece-registry-invalidation` 发一条消息即可——这正是 AP 自己在装/删 piece 时做的事
 * （`pieces/metadata/piece-cache.ts` 的 `invalidate()`）。pub/sub 会打到所有副本。
 *
 * 这一步不能省，而且很容易误判成"不需要"：列表 `/v1/pieces` 是**直接查库**的，灌完立刻就对；
 * 单件查询 `/v1/pieces/<name>` 走 `findExactVersion` → `loadRegistry` → 进程内 `cachedRegistry`。
 * 那个缓存是**懒加载**的：只有真正调过单查/registry 的进程才会填上它。所以如果在灌 seed 之前
 * 没人碰过这类端点，缓存还是 null，灌完自然全对——**看起来像不需要失效**。一旦有人（worker、
 * 或先打开画布的用户）在空表期间调过一次，`cachedRegistry` 就被钉成 `[]`，此后列表有、单查
 * 恒 404 `piece_metadata_not_found`，直到重启或收到失效消息。2026-07-29 两种顺序都实测过。
 *
 * 幂等：stamp 只动 `externalId IS NULL` 的行；seed SQL 本身是逐件 DELETE+INSERT，可反复执行，
 * 因此每次部署都跑一遍——这样白名单变了（增删件、升版本）也会跟着同步，而不是只在空库时才生效。
 *
 * 环境变量：
 *   AP_POSTGRES_HOST / _PORT / _DATABASE / _USERNAME / _PASSWORD   与 AP Deployment 同源
 *   AP_REDIS_HOST / _PORT / _PASSWORD                               同上；用于发缓存失效消息
 *   ACTIVEPIECES_MANAGED_PROJECT_EXTERNAL_ID                        默认 hermes-main
 *   AP_PIECES_SEED_FILE                                             seed SQL 路径；可为 .sql 或 .sql.gz
 *   AP_PROVISION_DB_TIMEOUT_MS                                      连库重试窗口（默认 120000）
 *
 * 退出码：0 = 成功（含"无事可做"）；1 = 失败。
 */
'use strict';
const fs = require('fs');
const zlib = require('zlib');

// AP 镜像里 pg 装在 /usr/src/app/node_modules；脚本从 /scripts 挂载进来，
// 常规解析路径找不到它，故显式兜底一次。
let Client;
try {
  ({ Client } = require('pg'));
} catch (_) {
  ({ Client } = require('/usr/src/app/node_modules/pg'));
}

const PROJECT_EXTERNAL_ID = process.env.ACTIVEPIECES_MANAGED_PROJECT_EXTERNAL_ID || 'hermes-main';
const SEED_FILE = process.env.AP_PIECES_SEED_FILE || '';
const TIMEOUT_MS = parseInt(process.env.AP_PROVISION_DB_TIMEOUT_MS || '120000', 10);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function readSeedSql(path) {
  const buf = fs.readFileSync(path);
  // gzip magic — the ConfigMap ships the seed compressed so the manifest stays well under
  // the 1MiB object limit (558KB raw -> ~78KB gzipped).
  if (buf.length > 2 && buf[0] === 0x1f && buf[1] === 0x8b) {
    return zlib.gunzipSync(buf).toString('utf8');
  }
  return buf.toString('utf8');
}

async function connect() {
  const cfg = {
    host: process.env.AP_POSTGRES_HOST,
    port: parseInt(process.env.AP_POSTGRES_PORT || '5432', 10),
    database: process.env.AP_POSTGRES_DATABASE,
    user: process.env.AP_POSTGRES_USERNAME,
    password: process.env.AP_POSTGRES_PASSWORD,
  };
  if (!cfg.host || !cfg.database || !cfg.user) {
    throw new Error('AP_POSTGRES_HOST / _DATABASE / _USERNAME must be set');
  }
  // AP's own migrations create these tables on first boot; this Job can start before that
  // finishes, so retry rather than fail the deploy on a race.
  const deadline = Date.now() + TIMEOUT_MS;
  let lastErr;
  while (Date.now() < deadline) {
    const client = new Client(cfg);
    try {
      await client.connect();
      const t = await client.query(
        "select to_regclass('project') as project, to_regclass('piece_metadata') as pieces"
      );
      if (t.rows[0].project && t.rows[0].pieces) {
        console.log('[ap-provision-db] connected to ' + cfg.host + ':' + cfg.port + '/' + cfg.database);
        return client;
      }
      console.log('[ap-provision-db] AP tables not created yet, waiting...');
      await client.end();
    } catch (e) {
      lastErr = e;
      try { await client.end(); } catch (_) { /* already down */ }
      console.log('[ap-provision-db] not ready (' + e.message + '), retrying...');
    }
    await sleep(5000);
  }
  throw new Error('AP database not ready within ' + TIMEOUT_MS + 'ms' + (lastErr ? ': ' + lastErr.message : ''));
}

async function stampProjectExternalId(client) {
  const existing = await client.query('select id, "displayName" from project where "externalId" = $1', [
    PROJECT_EXTERNAL_ID,
  ]);
  if (existing.rowCount > 0) {
    console.log('[ap-provision-db] project externalId=' + PROJECT_EXTERNAL_ID + ' already set -> skip.');
    return;
  }
  // Only ever fills in a blank. Re-pointing an externalId that already names a different
  // project would move every managed user to another tenant, so that stays a human decision.
  const res = await client.query('update project set "externalId" = $1 where "externalId" is null', [
    PROJECT_EXTERNAL_ID,
  ]);
  if (res.rowCount === 0) {
    const all = await client.query('select "displayName", "externalId" from project');
    throw new Error(
      'no project with a null externalId to stamp, and none carries ' + PROJECT_EXTERNAL_ID + '. Found: ' +
        (all.rows.map((r) => r.displayName + '=' + (r.externalId || 'null')).join(', ') || 'no projects at all') +
        '. Fix by hand — re-pointing an existing externalId would move managed users to another project.'
    );
  }
  console.log('[ap-provision-db] stamped ' + res.rowCount + ' project(s) with externalId=' + PROJECT_EXTERNAL_ID + '.');
}

async function seedPieces(client) {
  if (!SEED_FILE) {
    console.log('[ap-provision-db] AP_PIECES_SEED_FILE not set -> skip piece seeding.');
    return;
  }
  if (!fs.existsSync(SEED_FILE)) {
    throw new Error('AP_PIECES_SEED_FILE=' + SEED_FILE + ' does not exist');
  }
  const before = (await client.query('select count(*)::int as n from piece_metadata')).rows[0].n;
  const sql = readSeedSql(SEED_FILE);
  // The seed carries its own BEGIN/COMMIT and is DELETE+INSERT per piece, so it is safe to
  // replay on every deploy; that also picks up allowlist changes, which a seed-only-when-empty
  // gate would silently miss.
  await client.query(sql);
  const after = (await client.query('select count(*)::int as n from piece_metadata')).rows[0].n;
  console.log('[ap-provision-db] piece_metadata: ' + before + ' -> ' + after + ' row(s).');
  if (after === 0) {
    throw new Error('piece seed ran but piece_metadata is still empty');
  }
}

/**
 * Tell every running AP instance to drop its in-process piece registry, the same way AP does
 * when a piece is installed through its own API. Cheaper and less privileged than restarting
 * the Deployment (which would need RBAC), and it reaches all replicas.
 */
async function invalidatePieceRegistry() {
  const host = process.env.AP_REDIS_HOST;
  if (!host) {
    console.warn(
      '[ap-provision-db] AP_REDIS_HOST not set -> CANNOT invalidate the piece registry cache. ' +
        'If anything queried a single piece while piece_metadata was empty, /v1/pieces/<name> will ' +
        'keep returning 404 until Activepieces is restarted.'
    );
    return;
  }
  let Redis;
  try {
    Redis = require('ioredis');
  } catch (_) {
    Redis = require('/usr/src/app/node_modules/ioredis');
  }
  const client = new Redis({
    host,
    port: parseInt(process.env.AP_REDIS_PORT || '6379', 10),
    password: process.env.AP_REDIS_PASSWORD || undefined,
    lazyConnect: true,
    maxRetriesPerRequest: 3,
  });
  try {
    await client.connect();
    // Channel + payload copied from piece-cache.ts (PIECE_REGISTRY_INVALIDATION_CHANNEL).
    const reached = await client.publish('piece-registry-invalidation', '1');
    console.log('[ap-provision-db] piece registry invalidated (' + reached + ' subscriber(s) reached).');
    if (reached === 0) {
      console.warn('[ap-provision-db] no subscriber received it — was Activepieces running?');
    }
  } finally {
    try { await client.quit(); } catch (_) { /* already closing */ }
  }
}

async function main() {
  const client = await connect();
  try {
    await stampProjectExternalId(client);
    await seedPieces(client);
    await invalidatePieceRegistry();
    console.log('[ap-provision-db] done.');
  } finally {
    await client.end();
  }
}

main().catch((e) => {
  console.error('[ap-provision-db] ' + String((e && e.message) || e));
  process.exit(1);
});
