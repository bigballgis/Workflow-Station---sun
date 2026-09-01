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
  const styledHtml =
    '<style type="text/css">.ws-table{border-collapse:collapse;width:100%;font-family:Arial,sans-serif}' +
    '.ws-table th{background:#1e3a5f;color:#ffffff;padding:10px 12px;border:1px solid #1e3a5f}' +
    '.ws-table td{padding:9px 12px;border:1px solid #dcdfe6}</style>' +
    '<p>this email ${to}</p>' +
    '<table class="ws-table"><tr><th>Item</th><th>Qty</th></tr>' +
    '<tr><td>Laptop</td><td>2</td></tr></table>'
  await page.locator('.ebs-html-input textarea').fill(styledHtml)
  rec('HTML source pane is visible', await page.locator('.ebs-html-input textarea').isVisible())

  const preview = page.getByTestId('email-body-preview-iframe')
  await preview.waitFor({ state: 'visible', timeout: 10000 })
  const srcdoc = await preview.getAttribute('srcdoc')
  rec('Preview srcdoc keeps <style>', Boolean(srcdoc && /<style\b/i.test(srcdoc)))
  rec('Preview srcdoc keeps .ws-table CSS', Boolean(srcdoc && srcdoc.includes('.ws-table th')))
  rec('Preview srcdoc keeps ${to}', Boolean(srcdoc && srcdoc.includes('${to}')))

  const htmlShot = resolve(DW_SHOTS, `${DATE}_email-template-body-html.png`)
  await page.locator('.email-template-form-dialog').screenshot({ path: htmlShot })
  console.log(`screenshot ${htmlShot}`)

  await page.getByTestId('email-body-mode-visual').click()
  const confirm = page.getByRole('button', { name: /^(OK|Confirm|确定|確定)$/ })
  if (await confirm.count()) {
    await confirm.last().click()
  }
  await page.locator('.w-e-text-container').waitFor({ state: 'visible', timeout: 15000 })
  await page.waitForFunction(() => {
    const iframe = document.querySelector('[data-testid="email-body-preview-iframe"]')
    const src = iframe?.getAttribute('srcdoc') || ''
    return src.includes('Laptop') && !/<style\b/i.test(src)
  }, { timeout: 10000 })
  const visualSrcdoc = await page.getByTestId('email-body-preview-iframe').getAttribute('srcdoc')
  rec(
    'After Visual switch, preview drops <style>',
    Boolean(visualSrcdoc && !/<style\b/i.test(visualSrcdoc)),
  )
  rec(
    'After Visual switch, preview still has table text',
    Boolean(visualSrcdoc && visualSrcdoc.includes('Laptop')),
  )
  const visualAfterHtmlShot = resolve(DW_SHOTS, `${DATE}_email-template-body-visual-after-html.png`)
  await page.locator('.email-template-form-dialog').screenshot({ path: visualAfterHtmlShot })
  console.log(`screenshot ${visualAfterHtmlShot}`)
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} passed`)
if (failed.length) process.exit(1)
