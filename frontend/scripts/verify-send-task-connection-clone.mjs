/**
 * Verify Send Task Connection shows this Function Unit's connection name, not a source GUID.
 *
 * Env: FU_ID (default 50007, VincentTest2), CONNECTION_NAME (default 1527598351@qq.com).
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
const FU_ID = process.env.FU_ID ?? '50007'
const CONNECTION_NAME = process.env.CONNECTION_NAME ?? '1527598351@qq.com'

const results = []
const rec = (n, ok, d = '') => {
  results.push({ n, ok, d })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1100 } })).newPage()

try {
  await loginViaDwPassword(page)
  await page.goto(`http://localhost:3000/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.locator('.djs-container, .process-designer').first().waitFor({ timeout: 25000 })
  await page.waitForTimeout(2000)

  const sendNode = page.locator('[data-element-id]').filter({ hasText: /^send$/i }).first()
  await sendNode.click({ force: true })

  const connWrap = page.locator('.send-task-properties .el-select').first()
  await connWrap.waitFor({ state: 'visible', timeout: 20000 })
  await connWrap.getByText(CONNECTION_NAME, { exact: true }).waitFor({ timeout: 15000 })
  const shown = (await connWrap.innerText()).replace(/\s+/g, ' ').trim()
  rec('Connection shows this FU connection name', shown.includes(CONNECTION_NAME), shown)
  rec('Connection does not show a raw GUID', !/[0-9a-f]{8}-[0-9a-f]{4}-/i.test(shown), shown)

  const shot = resolve(DW_SHOTS, `${DATE}_send-task-connection-clone.png`)
  await page.locator('.send-task-email-section').first().screenshot({ path: shot })
  console.log(`screenshot ${shot}`)
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} passed`)
if (failed.length) process.exit(1)
