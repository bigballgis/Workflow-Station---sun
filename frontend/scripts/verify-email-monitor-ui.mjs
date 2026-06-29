#!/usr/bin/env node
/**
 * Screenshot verification for the inbound Email Monitors designer (DW).
 * Logs in, opens a Function Unit, activates the Email Monitors tab, captures the list,
 * then opens the create dialog to capture the no-code extraction wizard.
 *
 * Usage (from frontend/): node scripts/verify-email-monitor-ui.mjs [functionUnitId]
 */
import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = resolve(__dirname, '..', 'developer-workstation', 'verification-screenshots')
const FU_ID = process.argv[2] || '48'

function datePrefix() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const { chromium } = await import('playwright')
  const browser = await chromium.launch({ headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1400, height: 1600 } })).newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))

  try {
    await loginViaUnifiedSso(page, 'dw')
    await page.goto(`http://localhost:3000/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(4000)

    // Activate the Email Monitors tab (el-tabs renders header id #tab-email-monitors).
    const tab = page.locator('#tab-email-monitors')
    await tab.first().click({ timeout: 15000 })
    await page.waitForTimeout(2500)

    const listPath = join(OUT_DIR, `${datePrefix()}_email-monitor-list.png`)
    await page.screenshot({ path: listPath })
    console.log(`[saved] ${listPath}`)

    // Open the create dialog → wizard.
    const createBtn = page.getByRole('button', { name: /New Monitor|新建监听|新增監聽/ }).first()
    await createBtn.click({ timeout: 10000 })
    await page.waitForTimeout(2000)

    const dialogPath = join(OUT_DIR, `${datePrefix()}_email-monitor-wizard.png`)
    await page.screenshot({ path: dialogPath })
    console.log(`[saved] ${dialogPath}`)

    if (errors.length) console.warn('[page errors]', errors.join('\n'))
  } catch (e) {
    console.error('[verify-email-monitor-ui] FAILED:', e.message)
    if (errors.length) console.error('[page errors]', errors.join('\n'))
    process.exitCode = 1
  } finally {
    await browser.close()
  }
}

main()
