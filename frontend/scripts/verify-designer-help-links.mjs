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

const launchOpts = { headless: true }
if (process.env.PLAYWRIGHT_EXECUTABLE_PATH) {
  launchOpts.executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH
} else if (process.env.PLAYWRIGHT_CHANNEL) {
  launchOpts.channel = process.env.PLAYWRIGHT_CHANNEL
}

const browser = await chromium.launch(launchOpts)
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

  await page.getByRole('tab', { name: 'Form Design', exact: true }).click()
  await page.waitForTimeout(1200)
  const formEdit = page.locator('.form-list-sidebar .el-table__row').filter({ hasText: 'help_pr' }).getByRole('button', { name: 'Edit' }).first()
  await formEdit.waitFor({ state: 'visible', timeout: 15000 })
  await formEdit.click()
  await page.locator('.fc-designer-wrapper').first().waitFor({ state: 'visible', timeout: 15000 })
  await page.waitForTimeout(800)
  const titleField = page.locator('.el-form-item').filter({ hasText: /^Title/ }).first()
  if (await titleField.count()) await titleField.click({ force: true })
  else await page.locator('.el-form-item').first().click({ force: true })
  await page.waitForTimeout(500)
  await page.locator('._fd-event .el-button').first().click()
  const eventDlg = page.locator('._fd-event-dialog').last()
  await eventDlg.waitFor({ state: 'visible', timeout: 8000 })
  const eventHelp = page.getByTestId('form-events-guide-link')
  await eventHelp.waitFor({ state: 'visible', timeout: 8000 })
  rec('Form event dialog has the help ?', await eventHelp.isVisible())
  await redactHelpGuidePii(page)
  const eventShot = resolve(DW_SHOTS, `${DATE}_dw-form-events-help-link.png`)
  await eventDlg.screenshot({ path: eventShot })
  console.log(`screenshot ${eventShot}`)
  const eventPopupPromise = context.waitForEvent('page')
  await eventHelp.click()
  const eventPopup = await eventPopupPromise
  await eventPopup.waitForURL(/\/help\/form-events/, { timeout: 15000 })
  rec(
    'Help ? opens /help/form-events',
    eventPopup.url().includes('/help/form-events'),
    eventPopup.url(),
  )
  const eventHelpShot = resolve(DW_SHOTS, `${DATE}_dw-form-events-help-target.png`)
  await eventPopup.screenshot({ path: eventHelpShot, fullPage: true })
  console.log(`screenshot ${eventHelpShot}`)
  await eventPopup.close()
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)
  await eventDlg.waitFor({ state: 'hidden', timeout: 8000 }).catch(() => {})

  const formConfigTab = page.locator('._fc-r-tab').filter({ hasText: /^Form$/ })
  await formConfigTab.first().waitFor({ state: 'visible', timeout: 8000 })
  await formConfigTab.first().click()
  await page.waitForTimeout(600)
  const fnBtn = page.locator('._fd-fn-list .el-button').first()
  await fnBtn.waitFor({ state: 'visible', timeout: 10000 })
  await fnBtn.click()
  const fnDlg = page.locator('._fd-fn-list-dialog').last()
  await fnDlg.waitFor({ state: 'visible', timeout: 8000 })
  const fnHelp = page.getByTestId('form-event-guide-link')
  await fnHelp.waitFor({ state: 'visible', timeout: 8000 })
  rec('Form event dialog has the help ?', await fnHelp.isVisible())
  const fnShot = resolve(DW_SHOTS, `${DATE}_dw-form-event-help-link.png`)
  await fnDlg.screenshot({ path: fnShot })
  console.log(`screenshot ${fnShot}`)
  const fnPopupPromise = context.waitForEvent('page')
  await fnHelp.click()
  const fnPopup = await fnPopupPromise
  await fnPopup.waitForURL(/\/help\/form-events/, { timeout: 15000 })
  rec(
    'Form event ? opens /help/form-events#form-level',
    fnPopup.url().includes('/help/form-events') && fnPopup.url().includes('form-level'),
    fnPopup.url(),
  )
  const fnHelpShot = resolve(DW_SHOTS, `${DATE}_dw-form-event-help-target.png`)
  await fnPopup.screenshot({ path: fnHelpShot, fullPage: true })
  console.log(`screenshot ${fnHelpShot}`)
  await fnPopup.close()
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} passed`)
if (failed.length) process.exit(1)
