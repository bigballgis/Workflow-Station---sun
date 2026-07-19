/**
 * One-off: open FU View Design and capture the columns panel (lookup section).
 * Usage: node scripts/verify-mtv-lookup-columns.mjs [functionUnitId]
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'fs'
import { join, dirname } from 'path'
import { fileURLToPath } from 'url'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const fuId = process.argv[2] || '48'
const outDir = join(__dirname, '../developer-workstation/verification-screenshots')
mkdirSync(outDir, { recursive: true })
const date = new Date().toISOString().slice(0, 10)
const outPath = join(outDir, `${date}_mtv-lookup-columns-panel.png`)

const launchOpts = { headless: true }
if (process.env.PLAYWRIGHT_EXECUTABLE_PATH) {
  launchOpts.executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH
} else if (process.env.PLAYWRIGHT_CHANNEL) {
  launchOpts.channel = process.env.PLAYWRIGHT_CHANNEL
} else {
  launchOpts.channel = 'chrome'
}
const browser = await chromium.launch(launchOpts)
const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
try {
  await loginViaUnifiedSso(page, 'dw')
  await page.goto(`http://localhost:3000/dev/function-units/${fuId}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2000)
  const tab = page.locator('.el-tabs__item').filter({ hasText: /View Design|视图设计|視圖設計/i }).first()
  await tab.click()
  await page.waitForTimeout(2000)
  // Prefer the MAIN "HMDC Case" view if present
  const caseView = page.locator('.view-list-item, .el-menu-item, .views-nav-item').filter({ hasText: /HMDC Case(?!\s*Form)/i }).first()
  if (await caseView.count()) {
    await caseView.click()
    await page.waitForTimeout(2000)
  }
  const panel = page.locator('.columns-panel').first()
  if (await panel.count()) {
    await panel.screenshot({ path: outPath })
  } else {
    await page.screenshot({ path: outPath, fullPage: true })
  }
  console.log('WROTE', outPath)
  const lookupText = await page.locator('.columns-panel-lookup').innerText().catch(() => '')
  console.log('LOOKUP_PANEL_TEXT:', lookupText.slice(0, 400))
} finally {
  await browser.close()
}
