/**
 * Capture Email Template Visual / HTML split editor (iframe preview).
 * Env: FU_ID (default 50007).
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

const results = []
const rec = (n, ok, d = '') => {
  results.push({ n, ok, d })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1100 } })).newPage()

try {
  await loginViaDwPassword(page)
  await page.goto(`http://localhost:3000/dev/function-units/${FU_ID}`, {
    waitUntil: 'domcontentloaded',
  })
  await page.getByRole('tab', { name: 'Email Templates' }).click()
  await page.getByRole('button', { name: 'New Template' }).waitFor({ timeout: 20000 })
  await page.getByRole('button', { name: 'New Template' }).click()

  const split = page.getByTestId('email-body-split')
  await split.waitFor({ state: 'visible', timeout: 20000 })
  rec('Visual split editor is visible', await split.isVisible())
  rec(
    'Email preview iframe is visible',
    await page.getByTestId('email-body-preview-iframe').isVisible(),
  )
  rec(
    'Legacy Show preview link is gone',
    (await page.getByRole('button', { name: 'Show preview' }).count()) === 0,
  )

  const visualShot = resolve(DW_SHOTS, `${DATE}_email-template-body-visual.png`)
  await page.locator('.email-template-form-dialog').screenshot({ path: visualShot })
  console.log(`screenshot ${visualShot}`)

  await page.getByTestId('email-body-mode-html').click()
  await page.locator('.ebs-html-input textarea').waitFor({ state: 'visible', timeout: 10000 })
  await page.locator('.ebs-html-input textarea').fill(
    '<p>this email ${to}</p><table style="border-collapse:collapse;border:1px solid #dcdfe6"><tr><th style="border:1px solid #dcdfe6">Col</th></tr><tr><td style="border:1px solid #dcdfe6">1</td></tr></table>',
  )
  rec('HTML source pane is visible', await page.locator('.ebs-html-input textarea').isVisible())

  const htmlShot = resolve(DW_SHOTS, `${DATE}_email-template-body-html.png`)
  await page.locator('.email-template-form-dialog').screenshot({ path: htmlShot })
  console.log(`screenshot ${htmlShot}`)
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} passed`)
if (failed.length) process.exit(1)
