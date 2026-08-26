/**
 * Verify DesignerHelpLink on Connections opens /help/email-send in a new tab.
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'
import { redactHelpGuidePii } from './redact-help-guide-pii.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const DW_SHOTS = resolve(__dirname, '../developer-workstation/verification-screenshots')
mkdirSync(DW_SHOTS, { recursive: true })
const DATE = new Date().toISOString().slice(0, 10)
const FU_ID = process.env.HELP_GUIDE_FU_ID ?? '50007'
const origin = 'http://localhost:3000'

const results = []
const rec = (n, ok, d = '') => {
  results.push({ n, ok, d })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`)
}

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await context.newPage()

try {
  await loginViaDwPassword(page)
  await page.goto(`${origin}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.locator('.el-tabs').first().waitFor({ state: 'visible', timeout: 25000 })
  await page.getByRole('tab', { name: 'Connections', exact: true }).click()
  await page.waitForTimeout(1200)

  const helpLink = page.getByTestId('connection-guide-link')
  await helpLink.waitFor({ state: 'visible', timeout: 15000 })
  rec('Connections toolbar has the help ?', await helpLink.isVisible())

  await redactHelpGuidePii(page)
  const connShot = resolve(DW_SHOTS, `${DATE}_dw-connection-help-link.png`)
  await page.locator('.designer-workspace').screenshot({ path: connShot })
  console.log(`screenshot ${connShot}`)

  const popupPromise = context.waitForEvent('page')
  await helpLink.click()
  const popup = await popupPromise
  await popup.waitForURL(/\/help\/email-send/, { timeout: 15000 })
  rec(
    'Help ? opens /help/email-send',
    popup.url().includes('/help/email-send'),
    popup.url(),
  )
  const helpShot = resolve(DW_SHOTS, `${DATE}_dw-connection-help-target.png`)
  await popup.screenshot({ path: helpShot, fullPage: true })
  console.log(`screenshot ${helpShot}`)
  await popup.close()
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} passed`)
if (failed.length) process.exit(1)
