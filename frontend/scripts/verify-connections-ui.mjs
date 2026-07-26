#!/usr/bin/env node
/**
 * Screenshot verification for the Connections designer (DW).
 * Logs in, opens a Function Unit, activates the Connections tab, captures row actions.
 *
 * Usage (from frontend/): node scripts/verify-connections-ui.mjs [functionUnitId]
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
  const launchOpts = { headless: true }
  if (process.env.PLAYWRIGHT_EXECUTABLE_PATH) {
    launchOpts.executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH
  } else if (process.env.PLAYWRIGHT_CHANNEL) {
    launchOpts.channel = process.env.PLAYWRIGHT_CHANNEL
  }
  const browser = await chromium.launch(launchOpts)
  const page = await (await browser.newContext({ viewport: { width: 1400, height: 1600 } })).newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))

  try {
    await loginViaUnifiedSso(page, 'dw')
    await page.goto(`http://localhost:3000/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(4000)

    const tab = page.locator('#tab-connections')
    await tab.first().click({ timeout: 15000 })
    await page.waitForTimeout(2500)

    const rowActions = page.locator('.connection-designer .row-actions')
    const count = await rowActions.count()
    console.log(`[expect] .connection-designer .row-actions count=${count}`)
    if (count === 0) {
      throw new Error('Expected at least one .row-actions in Connections tab')
    }

    const listPath = join(OUT_DIR, `${datePrefix()}_connections-actions.png`)
    const designer = page.locator('.connection-designer').first()
    await designer.scrollIntoViewIfNeeded()
    await page.waitForTimeout(400)
    await designer.screenshot({ path: listPath })
    console.log(`[saved] ${listPath}`)

    const buttonLabels = await rowActions.first().evaluate((el) =>
      Array.from(el.querySelectorAll('button')).map((b) => b.textContent?.trim() ?? ''),
    )
    console.log('[actions]', buttonLabels.join(' | '))

    if (errors.length) console.warn('[page errors]', errors.join('\n'))
  } catch (e) {
    console.error('[verify-connections-ui] FAILED:', e.message)
    if (errors.length) console.error('[page errors]', errors.join('\n'))
    process.exitCode = 1
  } finally {
    await browser.close()
  }
}

main()
