#!/usr/bin/env node
/**
 * Developer Workstation — Service Task (AP) panel envelope contract hint.
 * The input expression must read `{{trigger.output.body.variables.<name>}}`: the engine exposes
 * every step as `{ output, error }`, so `trigger.body…` silently resolves to "".
 *
 * Usage: node scripts/verify-dw-service-task-contract-hint.mjs [functionUnitId] [serviceTaskId]
 */
import { mkdirSync } from 'fs'
import { join } from 'path'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const FU_ID = process.argv[2] || '50109'
const TASK_ID = process.argv[3] || 'svc_bound'
const ORIGIN = (process.env.VERIFY_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const OUT_DIR = join(process.cwd(), 'developer-workstation', 'verification-screenshots')
const EXPECTED = '{{trigger.output.body.variables.<name>}}'

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 1000 } })).newPage()

  await loginViaDwPassword(page)
  await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(3000)
  await page.locator('#tab-process, .el-tabs__item').filter({ hasText: /process/i }).first().click().catch(() => {})
  await page.waitForSelector('.bpmn-canvas .djs-container', { timeout: 45000 })
  await page.waitForTimeout(3000)

  await page.locator(`[data-element-id="${TASK_ID}"]`).first().click({ force: true })
  const tip = page.locator('.contract-tip')
  await tip.waitFor({ timeout: 15000 })
  await tip.scrollIntoViewIfNeeded()

  const codes = await tip.locator('code').allTextContents()
  console.log('contract codes:', codes)

  mkdirSync(OUT_DIR, { recursive: true })
  const date = new Date().toISOString().slice(0, 10)
  const panelShot = join(OUT_DIR, `${date}_dw-service-task-contract-hint.png`)
  await page.locator('.node-properties-panel').screenshot({ path: panelShot })
  console.log('screenshot:', panelShot)

  await browser.close()
  if (codes[0] !== EXPECTED) {
    console.error(`FAIL: expected input expression ${EXPECTED}, got ${codes[0]}`)
    process.exit(1)
  }
  console.log('PASS')
}

main().catch((e) => { console.error(e); process.exit(1) })
