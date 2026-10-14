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

if (process.env.MI_FIXTURE_ONLY === '1') {
  if (!process.env.TASK_ID || !process.env.APP_ID) throw new Error('Isolated MI gate requires both TASK_ID and APP_ID')
  const { chromium } = await import('playwright')
  const { loginViaPortalPassword } = await import('./playwright-login.mjs')
  const browser = await chromium.launch({ headless: true })
  try {
    const page = await browser.newPage()
    await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
    const origin = process.env.ORIGIN ?? 'http://localhost:3000'
    const response = await page.request.get(`${origin}/api/portal/tasks/${process.env.TASK_ID}`)
    const body = await response.json()
    if (!response.ok() || body.success !== true || body.data?.processInstanceId !== process.env.APP_ID
      || !/^MI verification [a-z0-9]+$/.test(body.data?.functionUnitName || '')) {
      throw new Error('Refusing MI gate outside its pinned synthetic process')
    }
  } finally { await browser.close() }
}

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
