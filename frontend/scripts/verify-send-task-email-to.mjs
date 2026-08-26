/**
 * Verify Send Task "To" placeholder/hint show ${assigneeEmail}, not $'assigneeEmail.
 *
 * Env: FU_ID (default 50006, Vincent Test).
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
  if (await sendNode.count()) {
    await sendNode.click({ force: true })
  } else {
    await page.locator('g[data-element-id="Activity_14r92ya"]').first().click({ force: true })
  }

  const toWrap = page.locator('.email-to-field-wrap')
  await toWrap.waitFor({ state: 'visible', timeout: 20000 })

  const placeholder = await toWrap.locator('input').getAttribute('placeholder')
  const hint = await toWrap.locator('.form-tip').innerText()
  rec('To placeholder contains ${assigneeEmail}', (placeholder || '').includes('${assigneeEmail}'), placeholder || '')
  rec("To placeholder is not $'assigneeEmail", !/\$'assigneeEmail/.test(placeholder || ''), placeholder || '')
  rec('To hint contains ${assigneeEmail}', (hint || '').includes('${assigneeEmail}'), hint || '')
  rec("To hint is not $'assigneeEmail", !/\$'assigneeEmail/.test(hint || ''), hint || '')

  const shot = resolve(DW_SHOTS, `${DATE}_send-task-email-to-placeholder.png`)
  await toWrap.screenshot({ path: shot })
  console.log(`screenshot ${shot}`)
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} passed`)
if (failed.length) process.exit(1)
