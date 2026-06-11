#!/usr/bin/env node
/**
 * MI regression gate — unit tests + Playwright screenshots (both required).
 *
 * Usage (from frontend/):
 *   node scripts/run-mi-regression.mjs              # unit + screenshot e2e (default)
 *   node scripts/run-mi-regression.mjs --unit-only    # escape hatch when portal stack down
 */
import { spawnSync } from 'child_process'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const frontendRoot = join(__dirname, '..')
const userPortalRoot = join(frontendRoot, 'user-portal')
const unitOnly = process.argv.includes('--unit-only')

function run(cmd, args, cwd) {
  console.log(`\n> ${cmd} ${args.join(' ')}\n`)
  const r = spawnSync(cmd, args, { cwd, stdio: 'inherit', shell: process.platform === 'win32' })
  if (r.status !== 0) process.exit(r.status ?? 1)
}

console.log('\n[mi-regression] Phase 1/2 — unit tests\n')
run('npm', ['run', 'test:regression:mi'], userPortalRoot)

if (unitOnly) {
  console.warn('\n[mi-regression] WARN: --unit-only skips screenshot e2e — not a full regression pass\n')
  console.log('[mi-regression] OK (unit only)\n')
  process.exit(0)
}

console.log('\n[mi-regression] Phase 2/2 — Playwright + screenshots (requires localhost:3000)\n')
run('node', [join(__dirname, 'verify-mi-regression-all.mjs')], frontendRoot)

console.log('\n[mi-regression] OK — unit + screenshots\n')
