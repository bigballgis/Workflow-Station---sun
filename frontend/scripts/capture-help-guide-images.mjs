/**
 * Capture Designer screens for /help/ article figures (DW login, no SSO).
 * Writes PNGs into frontend/help/public/guides/.
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'
import { redactHelpGuidePii } from './redact-help-guide-pii.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT = resolve(__dirname, '../help/public/guides')
mkdirSync(OUT, { recursive: true })

const FU_ID = process.env.HELP_GUIDE_FU_ID ?? '50007'
const origin = 'http://localhost:3000'

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()

async function shot(name, locator) {
  await redactHelpGuidePii(page)
  const target = locator ?? page
  const path = resolve(OUT, name)
  await target.screenshot({ path })
  console.log(`wrote ${path}`)
}

async function clickTab(label) {
  const tab = page.getByRole('tab', { name: label, exact: true })
  await tab.waitFor({ state: 'visible', timeout: 15000 })
  await tab.click()
  await page.waitForTimeout(1200)
}

try {
  await loginViaDwPassword(page)
  await page.goto(`${origin}/dev/function-units`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1500)
  await shot('dw-sidebar.png', page.locator('.dw-aside'))

  await page.goto(`${origin}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.locator('.el-tabs').first().waitFor({ state: 'visible', timeout: 25000 })
  await page.waitForTimeout(1500)

  await clickTab('Table Design')
  await shot('dw-table-design.png', page.locator('.designer-workspace'))

  await clickTab('Connections')
  await shot('dw-connections.png', page.locator('.designer-workspace'))
  await page.getByRole('button', { name: 'New Connection' }).click()
  const connDlg = page.locator('.connection-form-dialog, .el-dialog').filter({ hasText: 'Direction' }).last()
  await connDlg.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {})
  await page.waitForTimeout(600)
  await shot('dw-connections-inbound.png', page.locator('.el-overlay:visible, .el-dialog:visible').last())
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)

  await clickTab('Email Templates')
  await shot('dw-email-templates.png', page.locator('.designer-workspace'))
  const editTpl = page.locator('.designer-workspace .el-table').getByRole('button', { name: 'Edit' }).first()
  if (await editTpl.count()) {
    await editTpl.click({ timeout: 8000 })
    await page.waitForTimeout(1000)
    await shot('dw-email-body.png', page.locator('.email-template-form-dialog, .el-dialog').last())
    await page.keyboard.press('Escape')
    await page.waitForTimeout(400)
  }

  await clickTab('Email Monitors')
  await shot('dw-email-monitors.png', page.locator('.designer-workspace'))

  await clickTab('Process Design')
  await page.waitForTimeout(1500)
  const sendNode = page.locator('.djs-element').filter({ hasText: 'send' }).first()
  if (await sendNode.count()) {
    await sendNode.click()
    await page.waitForTimeout(800)
  }
  await shot('dw-send-task.png', page.locator('.designer-workspace'))

  const startNode = page.locator('.djs-element').filter({ hasText: 'Start' }).first()
  if (await startNode.count()) {
    await startNode.click()
    await page.waitForTimeout(800)
  }
  const inboundTitle = page.getByText('Inbound Email Trigger', { exact: true })
  if (await inboundTitle.count()) {
    await inboundTitle.click()
    await page.waitForTimeout(600)
  }
  await shot('dw-start-event.png', page.locator('.designer-workspace'))
} finally {
  await browser.close()
}
