/**
 * Verify Send Task recipient fields: full-width input + { } field picker popover.
 *
 * Env:
 *   FU_ID — Function Unit with a Send Task on the diagram (default 50006).
 *
 * Screenshots use placeholder recipient values (no real mailboxes).
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const DW_SHOTS = resolve(__dirname, '../developer-workstation/verification-screenshots')
mkdirSync(DW_SHOTS, { recursive: true })
const DATE = new Date().toISOString().slice(0, 10)
const FU_ID = process.env.FU_ID ?? '50006'
const PLACEHOLDER_TO = '${to}; user@example.com'

const results = []
const rec = (n, ok, d = '') => {
  results.push({ n, ok, d })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`)
}

async function clickSendTaskNode(page) {
  const canvasSend = page.locator('.djs-element').filter({ hasText: /^send$/i }).first()
  if (await canvasSend.count()) {
    await canvasSend.click({ force: true })
    return
  }
  const sendNode = page.locator('[data-element-id]').filter({ hasText: /^send$/i }).first()
  if (await sendNode.count()) {
    await sendNode.click({ force: true })
    return
  }
  throw new Error(
    `No Send Task node found on FU ${FU_ID}. Add a send task to the process diagram or set FU_ID to a fixture that has one.`,
  )
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1100 } })).newPage()

try {
  await loginViaDwPassword(page)
  await page.goto(`http://localhost:3000/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.locator('.el-tabs').first().waitFor({ state: 'visible', timeout: 25000 })
  const processTab = page.locator('.el-tabs__item').filter({ hasText: /Process Design|流程设计|流程設計/ }).first()
  if (await processTab.count()) {
    await processTab.click()
  }
  await page.locator('.djs-container, .process-designer').first().waitFor({ timeout: 25000 })
  await page.waitForTimeout(2000)

  await clickSendTaskNode(page)
  await page.waitForTimeout(800)
  const panelTitle = page.locator('.panel-title').filter({ hasText: /Send Task Config|发送任务|傳送任務/i })
  if (await panelTitle.count()) {
    await panelTitle.first().waitFor({ state: 'visible', timeout: 5000 })
  }
  await page.locator('.send-task-properties, .send-task-email-section').first().waitFor({ state: 'visible', timeout: 20000 })

  const toWrap = page.locator('.email-to-field-wrap')
  await toWrap.waitFor({ state: 'visible', timeout: 20000 })

  const toInput = toWrap.locator('input').first()
  await toInput.fill(PLACEHOLDER_TO)
  await page.waitForTimeout(300)

  const insertBtn = toWrap.locator('.recipient-expression-field__insert-btn')
  rec('{ } insert button visible', await insertBtn.isVisible())
  rec('No side-by-side Insert Variable select', (await toWrap.locator('.expression-field-with-variable__select').count()) === 0)

  const closedShot = resolve(DW_SHOTS, `${DATE}_send-task-recipient-field-closed.png`)
  await toWrap.screenshot({ path: closedShot })
  console.log(`screenshot ${closedShot}`)

  await insertBtn.click()
  const picker = page.locator('.recipient-expression-field-picker').first()
  await picker.waitFor({ state: 'visible', timeout: 10000 })
  rec('Field picker popover opens', await picker.isVisible())

  const openShot = resolve(DW_SHOTS, `${DATE}_send-task-recipient-field-picker-open.png`)
  await page.locator('.send-task-email-section').first().screenshot({ path: openShot })
  console.log(`screenshot ${openShot}`)
} catch (err) {
  const debugShot = resolve(DW_SHOTS, `${DATE}_send-task-recipient-field-debug.png`)
  await page.screenshot({ path: debugShot, fullPage: true }).catch(() => {})
  console.error(`debug screenshot ${debugShot}`)
  throw err
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} passed`)
if (failed.length) process.exit(1)
