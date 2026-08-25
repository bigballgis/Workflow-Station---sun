/**
 * Capture Designer screens for /help/ article figures (DW login, no SSO).
 * Writes PNGs into frontend/help/public/guides/.
 * Requires HELP_GUIDE_FU_ID from create-help-demo-purchase-request.mjs.
 * After recapture, bump GUIDE_FIGURE_REV in frontend/help/src/components/GuideArticle.vue
 * so the help SPA does not keep a cached PNG at the same URL.
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'
import { redactHelpGuidePii } from './redact-help-guide-pii.mjs'

const FU_ID = process.env.HELP_GUIDE_FU_ID?.trim()
if (!FU_ID) {
  console.error(
    'HELP_GUIDE_FU_ID is required. Run: node scripts/create-help-demo-purchase-request.mjs',
  )
  process.exit(1)
}

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT = resolve(__dirname, '../help/public/guides')
mkdirSync(OUT, { recursive: true })

const origin = 'http://localhost:3000'

const launchOpts = { headless: true }
if (process.env.PLAYWRIGHT_EXECUTABLE_PATH) {
  launchOpts.executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH
} else if (process.env.PLAYWRIGHT_CHANNEL) {
  launchOpts.channel = process.env.PLAYWRIGHT_CHANNEL
}

const browser = await chromium.launch(launchOpts)
const page = await (await browser.newContext({ viewport: { width: 1440, height: 1300 } })).newPage()

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

async function clickBpmnNode(text) {
  const node = page.locator('.djs-element').filter({ hasText: text }).first()
  if (await node.count()) {
    await node.click({ force: true })
    await page.waitForTimeout(800)
  }
}

try {
  await loginViaDwPassword(page)
  await page.goto(`${origin}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.locator('.el-tabs').first().waitFor({ state: 'visible', timeout: 25000 })
  await page.waitForTimeout(1500)

  await clickTab('Table Design')
  const mainTableRow = page.locator('.el-table__row').filter({ hasText: 'help_pr' }).filter({ hasNotText: 'help_pr_line' }).first()
  await mainTableRow.waitFor({ state: 'visible', timeout: 10000 })
  await mainTableRow.click()
  await page.locator('.table-fields-grid').waitFor({ state: 'visible', timeout: 10000 })
  await page.waitForTimeout(800)
  await shot('dw-table-design.png', page.locator('.designer-workspace'))
  await page.getByRole('button', { name: 'Back to List' }).click()
  await page.waitForTimeout(400)

  await clickTab('Connections')
  await shot('dw-connections.png', page.locator('.designer-workspace'))
  const inboundRow = page.locator('.connection-designer .el-table__row').filter({ hasText: /Gmail|IMAP/i }).first()
  if (await inboundRow.count()) {
    await inboundRow.getByRole('button', { name: 'Edit' }).click()
  } else {
    await page.getByRole('button', { name: 'New Connection' }).click()
  }
  const connDlg = page.locator('.connection-form-dialog').last()
  await connDlg.waitFor({ state: 'visible', timeout: 8000 })
  await page.waitForTimeout(600)
  await shot('dw-connections-inbound.png', connDlg)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)

  await clickTab('Email Templates')
  await shot('dw-email-templates.png', page.locator('.designer-workspace'))
  const editTpl = page.locator('.designer-workspace .el-table').getByRole('button', { name: 'Edit' }).first()
  if (await editTpl.count()) {
    await editTpl.click({ timeout: 8000 })
    const tplDlg = page.locator('.email-template-form-dialog, .el-dialog').filter({ hasText: 'Body' }).last()
    await tplDlg.waitFor({ state: 'visible', timeout: 8000 })
    await page.locator('[data-testid="email-body-split"]').waitFor({ state: 'visible', timeout: 8000 })
    await page.waitForTimeout(800)
    await shot('dw-email-body.png', tplDlg)
    await page.keyboard.press('Escape')
    await page.waitForTimeout(400)
  }

  await clickTab('Email Monitors')
  await shot('dw-email-monitors.png', page.locator('.designer-workspace'))

  await clickTab('Process Design')
  await page.waitForTimeout(1500)
  await clickBpmnNode('Send approval notice')
  await shot('dw-send-task.png', page.locator('.designer-workspace'))

  await clickBpmnNode(/^Start$/)
  const inboundBody = page.locator('.start-email-monitor')
  if ((await inboundBody.count()) === 0 || !(await inboundBody.isVisible().catch(() => false))) {
    await page.locator('.el-collapse-item').filter({ hasText: 'Inbound Email Trigger' }).locator('.el-collapse-item__header').click({ force: true })
    await page.waitForTimeout(500)
  }
  if (await inboundBody.count()) {
    await inboundBody.scrollIntoViewIfNeeded()
    await page.waitForTimeout(600)
  }
  await shot('dw-start-event.png', page.locator('.designer-workspace'))
} finally {
  await browser.close()
}
