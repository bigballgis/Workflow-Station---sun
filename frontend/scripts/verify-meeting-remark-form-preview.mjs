/**
 * Verify DW Form Preview for an ACTION form is not empty.
 *
 * ACTION forms author fields on the ACTION binding canvas (subForms), not top-level
 * rule. Preview must use that canvas (parity with Portal FORM_POPUP).
 *
 * Env: FU_ID (default 50005), FORM_NAME (default "Meeting Remark").
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
const FU_ID = process.env.FU_ID ?? '50005'
const FORM_NAME = process.env.FORM_NAME ?? 'Meeting Remark'

const results = []
const rec = (n, ok, d = '') => {
  results.push({ n, ok, d })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1900, height: 1200 } })).newPage()

try {
  await loginViaDwPassword(page)
  await page.goto(`http://localhost:3000/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.getByText('Form Design', { exact: true }).first().waitFor({ timeout: 20000 })
  await page.getByText('Form Design', { exact: true }).first().click()

  const sidebar = page.locator('.form-list-sidebar')
  await sidebar.waitFor({ timeout: 20000 })
  const formRow = sidebar.getByText(FORM_NAME, { exact: true }).first()
  await formRow.waitFor({ timeout: 20000 })
  await formRow.click()

  const previewBtn = page.getByRole('button', { name: 'Preview', exact: true })
  await previewBtn.waitFor({ state: 'visible', timeout: 20000 })
  await page.locator('.fc-designer-wrapper, .sub-table-design-wrapper').first().waitFor({ timeout: 20000 })

  await page.screenshot({
    path: resolve(DW_SHOTS, `${DATE}_meeting-remark-form-designer.png`),
    fullPage: true,
  })

  await previewBtn.click()
  const dialog = page.locator('.form-preview-dialog').last()
  await dialog.waitFor({ state: 'visible', timeout: 20000 })
  await dialog.locator('.el-form-item, .form-create, .fc-form-item').first().waitFor({ timeout: 20000 }).catch(() => {})

  const dialogText = await dialog.innerText().catch(() => '')
  rec('Preview dialog opened', dialogText.length > 0)
  rec('Preview is not the empty "No form content" state', !/No form content|暂无表单内容/i.test(dialogText), dialogText.slice(0, 240))
  rec('Remark Type visible in Preview', /Remark Type/i.test(dialogText))
  rec('Remark Content visible in Preview', /Remark Content/i.test(dialogText))

  await page.screenshot({
    path: resolve(DW_SHOTS, `${DATE}_meeting-remark-form-preview.png`),
    fullPage: true,
  })
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} passed`)
if (failed.length) process.exit(1)
