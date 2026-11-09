#!/usr/bin/env node
// =============================================================================
// Builds the two k8s offline init files from deploy/init-scripts, in the order the Docker init
// (deploy/init-scripts/00-init-all.sh) runs the same scripts:
//   init-platform-schema/all-in-one-for-gui.sql  every 00-schema script of Steps 1-2
//   init-platform-seed/all-in-one-for-gui.sql    the 01-admin scripts of Step 3 and the 90-post-seed scripts of
//                                                Step 5, minus the explicit k8s exclusions below
//
//   node deploy/k8s/init-data/build-all-in-one.mjs           write both files
//   node deploy/k8s/init-data/build-all-in-one.mjs --check   exit 1 when a committed file is not what would be built
//   node deploy/k8s/init-data/build-all-in-one.mjs --list    print the Docker run order and what k8s does with each
//
// 00-init-all.sh is the single source of the order. It is read with the rules init-database.ps1 uses too:
// comment and echo lines are ignored; every /docker-entrypoint-initdb.d/<path> names a script, in order of
// appearance; a * in the file name is a glob over that directory, sorted by byte order (bash in the postgres
// alpine image sorts that way); a script named twice (the `if [ -f X ]` test, then `$PSQL -f X`) runs once.
//
// The output is plain SQL that GUI clients accept too (no psql meta-commands): \echo and \set lines are dropped,
// and CREATE INDEX CONCURRENTLY becomes CREATE INDEX - CONCURRENTLY fails inside a transaction block, which some
// clients wrap a script in, and on a new database there is nothing to lock. The sections keep their own
// BEGIN / COMMIT where the source scripts have them, so the files are not one transaction: the reference way to
// run them is psql -v ON_ERROR_STOP=1 -f (statement by statement, stop at the first error), as the Docker init
// does. Two seed scripts get a k8s variant (TRANSFORMS). Each rewrite must match exactly where expected, or the
// build stops.
//
// Equivalence with the Docker init is checked on throwaway databases, not assumed: see
// deploy/k8s/init-data/verify-init-parity.sh.
// =============================================================================
import { readFileSync, readdirSync, writeFileSync, existsSync } from 'node:fs'
import { dirname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, '..', '..', '..')
const INIT = join(REPO, 'deploy', 'init-scripts')
const SCHEMA_OUT = join(HERE, 'init-platform-schema', 'all-in-one-for-gui.sql')
const SEED_OUT = join(HERE, 'init-platform-seed', 'all-in-one-for-gui.sql')

/** Scripts the Docker init runs that k8s environments do not get, and why. Demo packages are never included. */
const K8S_SEED_EXCLUDED = {
  '01-admin/06-hase-organization-seed.sql':
    'HASE organization demo data (used by the ATM demo); a k8s environment has its own organization',
  '01-admin/11-rename-hmdc-operator-role.sql': 'only renames a role that 01-admin/06 creates',
  '01-admin/13-digital-lending-action-roles.sql':
    'roles of the Digital Lending demo function unit and their E2E account grants; k8s gets no demo function units',
  '90-post-seed/03-platform-access-model-dev-prepare.sql':
    'dev only: the developer account and Platform Showcase (PRD D-22, appendix A-33)',
}

const DEVELOPER_BLOCK_START = '-- 通用开发测试账号：developer / password'

/** k8s variants of a script: a function of its text that must change it exactly as described. */
const TRANSFORMS = {
  // k8s environments have no developer account (PRD D-22, A-33): drop the block that creates it, which runs to the
  // end of the file. Every statement in it must be about user-dev, so nothing else is dropped with it.
  '01-admin/05-e2e-test-users-and-business-units.sql': (sql) => {
    const lines = sql.split('\n')
    const at = lines.findIndex((l) => l === DEVELOPER_BLOCK_START)
    if (at < 1 || lines.indexOf(DEVELOPER_BLOCK_START, at + 1) !== -1 || !/^-- =+$/.test(lines[at - 1])) {
      throw new Error(`05: expected one "${DEVELOPER_BLOCK_START}" block under a "-- ====" line`)
    }
    const dropped = lines.slice(at - 1).join('\n')
    const statements = dropped.split(';').map((s) => s.replace(/^\s*--.*$/gm, '').trim()).filter(Boolean)
    if (statements.length === 0 || statements.some((s) => !s.includes("'user-dev'"))) {
      throw new Error('05: the developer block holds a statement that is not about user-dev; update the transform')
    }
    return `${lines.slice(0, at - 1).join('\n').replace(/\n+$/, '')}\n`
  },
  // The initial System Administrator of a k8s environment is admin (PRD D-22).
  '90-post-seed/04-platform-access-model-switch.sql': (sql) =>
    replaceOnce(sql,
      "-- Initial System Administrator of this environment (D-22): developer in dev.\n"
        + "SET platform_access_model.initial_sys_admin = 'developer';",
      "-- Initial System Administrator of this environment (D-22): admin in k8s environments.\n"
        + "SET platform_access_model.initial_sys_admin = 'admin';"),
}

const RULE = '-- ============================================================================='

const SCHEMA_HEADER = `${RULE}
-- ALL-IN-ONE platform DDL for a NEW k8s database (pure SQL: pgAdmin, DBeaver, psql -f).
-- Reference run: psql -v ON_ERROR_STOP=1 -f <this file>, as the platform's database account (the services' user):
-- the first section sets the time zone default of the account that runs it, in this database. It is not one
-- transaction: some sections commit on their own (BEGIN / COMMIT); a run that stops halfway leaves the sections
-- before it in place.
-- GENERATED by deploy/k8s/init-data/build-all-in-one.mjs: every deploy/init-scripts/00-schema script that
-- deploy/init-scripts/00-init-all.sh runs (Steps 1-2), in the same order. Do not edit: change the source script
-- (append-only) or 00-init-all.sh, then run the generator; "--check" tells whether this file is current.
--
-- Order on a new database (deploy/k8s/init-data/README.md):
--   1. init-flowable/create/flowable.postgres.all.create.sql   Flowable tables (7.0.0.0)
--   2. this file                                               needs the act_* tables: it indexes act_hi_taskinst
--                                                              and widens act_* columns (30-, 31-)
--   3. init-platform-seed/all-in-one-for-gui.sql
--   4. init-flowable/upgrade/MIGRATE-7.0.0-to-7.2.0-for-gui.sql  Flowable 7.0.0.0 -> 7.2.0.2; the engine runs with
--                                                              FLOWABLE_SCHEMA_UPDATE=false and refuses 7.0.0.0
-- An existing database never runs this whole file: apply the sections it lacks, one by one.
${RULE}

SET client_min_messages = WARNING;
SET timezone = 'Asia/Hong_Kong';
`

const SEED_HEADER = `${RULE}
-- ALL-IN-ONE platform seed for a NEW k8s database (pure SQL: pgAdmin, DBeaver, psql -f).
-- Reference run: psql -v ON_ERROR_STOP=1 -f <this file>, as the platform account.
-- GENERATED by deploy/k8s/init-data/build-all-in-one.mjs: the deploy/init-scripts/01-admin (Step 3) and
-- 90-post-seed (Step 5) scripts of deploy/init-scripts/00-init-all.sh, in the same order. No demo function units.
-- Not taken from the Docker init:
${Object.entries(K8S_SEED_EXCLUDED).map(([f, why]) => `--   ${f}: ${why}`).join('\n')}
-- k8s variants: 01-admin/05 without the developer account; 90-post-seed/04 with admin as the initial
-- System Administrator. Do not edit: change the source script (append-only), then run the generator.
--
-- Run AFTER init-platform-schema/all-in-one-for-gui.sql, then init-flowable/upgrade/MIGRATE-7.0.0-to-7.2.0-for-gui.sql.
-- A database that has been switched to the access model must NOT run this whole file again: the early seed
-- sections rebuild the old SYSTEM groups and TEAM_LEAD, and the switch (90-post-seed/04) is skipped once applied.
-- Only the 90-post-seed/04 and 05 sections may run again (04 then does nothing, 05 is idempotent).
-- client_min_messages = WARNING hides the switch's NOTICE list; run the 04 section with psql to read it.
${RULE}

SET client_min_messages = WARNING;
SET timezone = 'Asia/Hong_Kong';
`

function replaceOnce(text, from, to) {
  const at = text.indexOf(from)
  if (at === -1 || text.indexOf(from, at + 1) !== -1) {
    throw new Error(`expected exactly one occurrence of:\n${from}`)
  }
  return text.slice(0, at) + to + text.slice(at + from.length)
}

/** The scripts 00-init-all.sh runs, in order, as paths relative to deploy/init-scripts. */
export function dockerRunOrder(shText = readFileSync(join(INIT, '00-init-all.sh'), 'utf8')) {
  const order = []
  const seen = new Set()
  for (const line of shText.replace(/\r\n/g, '\n').split('\n')) {
    if (/^\s*#/.test(line) || /^\s*echo\b/.test(line)) continue
    for (const m of line.matchAll(/\/docker-entrypoint-initdb\.d\/([A-Za-z0-9._/*-]+)/g)) {
      for (const file of expand(m[1])) {
        if (!seen.has(file)) {
          seen.add(file)
          order.push(file)
        }
      }
    }
  }
  return order
}

function expand(path) {
  if (!path.includes('*')) {
    if (!existsSync(join(INIT, path))) throw new Error(`00-init-all.sh runs ${path}, which does not exist`)
    return [path]
  }
  const dir = path.slice(0, path.lastIndexOf('/'))
  const pattern = new RegExp(`^${path.slice(dir.length + 1).split('*').map(escapeRegExp).join('[^/]*')}$`)
  return readdirSync(join(INIT, dir)).filter((f) => pattern.test(f)).sort(byteOrder).map((f) => `${dir}/${f}`)
}

const escapeRegExp = (s) => s.replace(/[.+?^${}()|[\]\\]/g, '\\$&')
const byteOrder = (a, b) => (Buffer.compare(Buffer.from(a), Buffer.from(b)))

/** What k8s does with one script of the Docker order: 'schema', 'seed', or why it is left out. */
export function k8sDecision(file) {
  if (file.startsWith('00-schema/')) return 'schema'
  if (K8S_SEED_EXCLUDED[file]) return `left out: ${K8S_SEED_EXCLUDED[file]}`
  if (file.startsWith('01-admin/') || file.startsWith('90-post-seed/')) return 'seed'
  return 'left out: demo function unit package'
}

/** Every numbered 00-schema script must be run by the Docker init (00-init-all.sh once skipped 93-*). */
function assertEverySchemaScriptRuns(order) {
  const run = new Set(order)
  const missing = readdirSync(join(INIT, '00-schema'))
    .filter((f) => /^\d+-.*\.sql$/.test(f) && !f.startsWith('00-'))
    .map((f) => `00-schema/${f}`)
    .filter((f) => !run.has(f))
  if (missing.length) throw new Error(`00-init-all.sh does not run: ${missing.join(', ')}`)
}

function section(file) {
  let sql = readFileSync(join(INIT, file), 'utf8').replace(/^﻿/, '').replace(/\r\n/g, '\n')
  sql = sql.split('\n').filter((l) => !/^\s*\\(echo|set)\b/.test(l)).join('\n')
  if (/^\s*\\/m.test(sql)) throw new Error(`${file}: psql meta-command left after dropping \\echo / \\set`)
  sql = sql.replace(/CREATE INDEX CONCURRENTLY/g, 'CREATE INDEX')
  if (TRANSFORMS[file]) sql = TRANSFORMS[file](sql)
  sql = `${sql.replace(/\s+$/, '')}\n`
  // psql runs a file's last statement at end of file even without ";". Here the next section follows, so end it.
  const code = sql.replace(/--.*$/gm, '').trim()
  if (code && !code.endsWith(';')) sql += ';\n'
  return `\n${RULE}\n-- ${file}\n${RULE}\n${sql}`
}

export function build() {
  const order = dockerRunOrder()
  assertEverySchemaScriptRuns(order)
  for (const file of Object.keys({ ...K8S_SEED_EXCLUDED, ...TRANSFORMS })) {
    if (!order.includes(file)) throw new Error(`${file} is configured here but 00-init-all.sh does not run it`)
  }
  const schema = order.filter((f) => k8sDecision(f) === 'schema')
  const seed = order.filter((f) => k8sDecision(f) === 'seed')
  return {
    order,
    schema: SCHEMA_HEADER + schema.map(section).join(''),
    seed: SEED_HEADER + seed.map(section).join(''),
  }
}

function main() {
  const mode = process.argv[2] ?? '--write'
  const out = build()
  if (mode === '--list') {
    out.order.forEach((f, i) => console.log(`${String(i + 1).padStart(3)}  ${f.padEnd(62)} ${k8sDecision(f)}`))
    return
  }
  const targets = [[SCHEMA_OUT, out.schema], [SEED_OUT, out.seed]]
  if (mode === '--check') {
    let stale = 0
    for (const [path, text] of targets) {
      const current = existsSync(path) ? readFileSync(path, 'utf8').replace(/\r\n/g, '\n') : ''
      if (current === text) {
        console.log(`up to date: ${relative(REPO, path)}`)
        continue
      }
      stale++
      const a = current.split('\n')
      const b = text.split('\n')
      let i = 0
      while (i < a.length && i < b.length && a[i] === b[i]) i++
      console.log(`STALE: ${relative(REPO, path)} differs from line ${i + 1}:\n  file:  ${a[i] ?? '<end>'}\n  built: ${b[i] ?? '<end>'}`)
    }
    if (stale) {
      console.log('Regenerate: node deploy/k8s/init-data/build-all-in-one.mjs')
      process.exit(1)
    }
    return
  }
  if (mode !== '--write') throw new Error(`unknown option ${mode}`)
  for (const [path, text] of targets) {
    writeFileSync(path, text)
    console.log(`wrote ${relative(REPO, path)} (${text.split('\n').length} lines)`)
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  try {
    main()
  } catch (e) {
    console.error(`ERROR: ${e.message}`)
    process.exit(1)
  }
}
