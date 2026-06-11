#!/usr/bin/env node
/**
 * Run all MI regression Playwright scenarios (each MUST save screenshots).
 * Usage: node scripts/verify-mi-regression-all.mjs
 */
import { spawnSync } from 'child_process'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'
import { MI_REGRESSION_SCENARIOS, MI_REGRESSION_SCRIPT_ORDER } from './mi-regression-scenarios.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const frontendRoot = join(__dirname, '..')

console.log('\n[mi-regression:e2e] Playwright + screenshots (portal at http://localhost:3000)\n')
console.log('Scenarios:')
for (const s of MI_REGRESSION_SCENARIOS) {
  console.log(`  - ${s.id} (${s.issue}) → ${s.script}`)
  console.log(`    unit: ${s.unitTests.join(', ')}`)
  console.log(`    shots: ${s.screenshots.join(', ')}`)
}
console.log('')

for (const script of MI_REGRESSION_SCRIPT_ORDER) {
  const r = spawnSync('node', [join(__dirname, script)], {
    cwd: frontendRoot,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  })
  if (r.status !== 0) {
    console.error(`\n[mi-regression:e2e] FAIL: ${script}\n`)
    process.exit(r.status ?? 1)
  }
}

console.log('\n[mi-regression:e2e] All scenarios PASS — screenshots in user-portal/verification-screenshots/\n')
