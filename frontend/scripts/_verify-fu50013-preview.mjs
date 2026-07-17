/**
 * FU 50013 Form Preview verification: sub-table columns must include fields
 * nested inside an elCard (Shipment Name / Carrier).
 * Usage: node verify-fu50013-preview.mjs <slug>
 */
import { mkdirSync } from 'fs'
import { join } from 'path'
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const OUT_DIR = '/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/frontend/developer-workstation/verification-screenshots'
const slug = process.argv[2] || 'fu50013-card-subtable'

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1680, height: 1200 } })).newPage()
mkdirSync(OUT_DIR, { recursive: true })

try {
  await loginViaUnifiedSso(page, 'dw')
  await page.goto('http://localhost:3000/dev/function-units/50013', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(4000)

  const formDesignTab = page.locator('.el-tabs__item, [role="tab"]', { hasText: 'Form Design' }).first()
  await formDesignTab.click()
  await page.waitForTimeout(3000)

  const editLink = page.locator('tr', { hasText: 'Nested Demo Form' }).locator('text=Edit').first()
  await editLink.click()
  await page.waitForTimeout(4000)

  const previewBtn = page.locator('button', { hasText: 'Preview' }).first()
  await previewBtn.waitFor({ state: 'visible', timeout: 15000 })
  await previewBtn.click()
  await page.waitForTimeout(2500)

  const dialog = page.locator('.el-dialog', { has: page.locator('text=Form Preview') }).first()
  await dialog.waitFor({ state: 'visible', timeout: 15000 })

  const headers = await dialog.locator('.el-table__header-wrapper th').allInnerTexts()
  console.log('[headers]', JSON.stringify(headers.map(h => h.trim())))

  const outPath = join(OUT_DIR, `${datePrefix()}_${slug}.png`)
  await dialog.screenshot({ path: outPath })
  console.log('[saved]', outPath)

  const hasShipment = headers.some(h => h.trim() === 'Shipment Name')
  const hasCarrier = headers.some(h => h.trim() === 'Carrier')
  const hasBlank = headers.some(h => h.trim() === '')
  console.log(`[assert] shipmentName=${hasShipment} carrier=${hasCarrier} blankHeader=${hasBlank}`)
} catch (err) {
  const dbgPath = join(OUT_DIR, `${datePrefix()}_${slug}-DEBUG.png`)
  await page.screenshot({ path: dbgPath, fullPage: true }).catch(() => {})
  console.error('[error]', err.message, '— debug screenshot:', dbgPath)
  const btns = await page.locator('button:visible').allInnerTexts().catch(() => [])
  console.error('[visible buttons]', JSON.stringify(btns.slice(0, 30)))
  process.exitCode = 1
} finally {
  await browser.close()
}
