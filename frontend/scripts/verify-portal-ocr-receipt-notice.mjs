/**
 * Receipt Notice OCR (FU "Receipt Notice OCR"): New Request uploads a PDF → the BPMN service task
 * runs the Automation flow hermes-ocr-receipt-notice → DW /ocr/extract → the "Review OCR Result"
 * To Do opens with the extracted fields pre-filled.
 *
 * From frontend/: PROCESS_KEY=<fu code> OCR_SAMPLE_PDF=<path> node scripts/verify-portal-ocr-receipt-notice.mjs
 */
import { mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const UP_SHOTS = resolve(__dirname, '../user-portal/verification-screenshots')
mkdirSync(UP_SHOTS, { recursive: true })

const DATE = new Date().toISOString().slice(0, 10)
const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const PROCESS_KEY = process.env.PROCESS_KEY
const SAMPLE = process.env.OCR_SAMPLE_PDF
if (!PROCESS_KEY || !SAMPLE) {
  throw new Error('Set PROCESS_KEY (FU code) and OCR_SAMPLE_PDF (path to a text-layer PDF)')
}

/** What the sample "Receipt Notice.pdf" must come back with. */
const EXPECTED = {
  receipt_number: 'MCT2373613541',
  received_date: '2023-01-15',
  notice_date: '2023-01-16',
  applicant: 'ZHANG, YUXI',
  beneficiary_dob: '1994-05-17',
  ocr_status: 'SUCCESS',
}

const results = []
function rec(n, ok, d = '') {
  results.push({ n, ok, d })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`)
}

function unwrap(json) {
  return json && typeof json === 'object' && 'data' in json && json.data != null ? json.data : json
}

async function shot(page, slug) {
  const path = join(UP_SHOTS, `${DATE}_${slug}.png`)
  await page.screenshot({ path, fullPage: true })
  console.log(`screenshot ${path}`)
}

async function latestTask(page) {
  const res = await page.request.post(`${ORIGIN}/api/portal/tasks/query`, { data: { page: 0, size: 30 } })
  const body = unwrap(await res.json())
  const rows = body?.content ?? body?.records ?? []
  return rows
    .filter((r) => String(r.processDefinitionKey || '').startsWith(PROCESS_KEY) || r.processDefinitionName === 'Receipt Notice OCR')
    .sort((a, b) => String(b.createTime || '').localeCompare(String(a.createTime || '')))[0]
}

/** Value shown for a form field, read from the el-form-item whose label is `label`. */
async function fieldValue(page, label) {
  const item = page.locator('.el-form-item').filter({
    has: page.locator('.el-form-item__label', { hasText: new RegExp(`^\\s*${label}\\s*$`) }),
  }).first()
  if (!(await item.count())) return null
  const input = item.locator('input:not([type=file]), textarea').first()
  return (await input.count()) ? input.inputValue() : (await item.innerText()).trim()
}

const browser = await chromium.launch({ headless: true })
try {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1100 } })
  const page = await ctx.newPage()
  await loginViaPortalPassword(page, { roleCode: 'MANAGER' })

  await page.goto(`${ORIGIN}/portal/processes/start/${PROCESS_KEY}`, { waitUntil: 'domcontentloaded' })
  const fileInput = page.locator('input[type="file"]').first()
  await fileInput.waitFor({ state: 'attached', timeout: 30000 })
  const uploadDone = page.waitForResponse((r) => r.url().includes('/api/v1/upload') && r.request().method() === 'POST',
    { timeout: 30000 }).catch(() => null)
  await fileInput.setInputFiles(SAMPLE)
  const listed = await page.getByText('Receipt Notice.pdf').first()
    .waitFor({ state: 'visible', timeout: 20000 }).then(() => true).catch(() => false)
  rec('New Request accepted the PDF', listed)
  const uploadRes = await uploadDone
  rec('PDF upload request succeeded', uploadRes?.status() === 200, uploadRes ? `HTTP ${uploadRes.status()}` : 'no upload request seen')
  await page.waitForTimeout(1500)
  await shot(page, 'portal-ocr-new-request-uploaded')

  const startedAt = Date.now()
  const submit = page.getByRole('button', { name: /Submit Application|Submit|提交申请|提交/i }).first()
  await submit.click({ force: true })
  await page.waitForTimeout(1500)
  const confirm = page.locator('.el-message-box button').filter({ hasText: /OK|Confirm|确定/i }).first()
  if (await confirm.count()) await confirm.click({ force: true })
  await page.waitForURL(/my-applications|tasks/, { timeout: 120000 }).catch(() => {})
  if (!/my-applications|tasks/.test(page.url())) await shot(page, 'portal-ocr-submit-stuck')
  rec('New Request submitted (OCR ran inside the submit)', /my-applications|tasks/.test(page.url()),
    `${page.url()} after ${((Date.now() - startedAt) / 1000).toFixed(1)}s`)

  const todo = await latestTask(page)
  rec('Review OCR Result task is in To Do', todo?.taskName === 'Review OCR Result', todo ? `${todo.taskName} ${todo.taskId}` : 'none')
  if (todo?.taskId) {
    await page.goto(`${ORIGIN}/portal/tasks/${todo.taskId}`, { waitUntil: 'domcontentloaded' })
    await page.locator('.el-form-item').first().waitFor({ state: 'visible', timeout: 30000 })
    await page.waitForTimeout(3000)
    const labels = {
      receipt_number: 'Receipt Number', received_date: 'Received Date', notice_date: 'Notice Date',
      applicant: 'Applicant', beneficiary_dob: 'Beneficiary DOB', ocr_status: 'OCR Status',
    }
    for (const [key, expected] of Object.entries(EXPECTED)) {
      const actual = await fieldValue(page, labels[key])
      rec(`Review form pre-filled ${labels[key]}`, actual === expected, `got ${JSON.stringify(actual)}`)
    }
    await shot(page, 'portal-ocr-review-task-prefilled')

    const action = page.getByRole('button', { name: /^\s*Confirm\s*$/i }).first()
    if (await action.count()) {
      await action.click({ force: true })
      await page.waitForTimeout(800)
      const dialog = page.locator('.el-dialog:visible').last()
      if (await dialog.count()) {
        await dialog.getByRole('button', { name: /Confirm|OK|确定/i }).last().click({ force: true }).catch(() => {})
      }
      await page.waitForTimeout(5000)
      const after = await latestTask(page)
      rec('Confirm completed the review task', !after || after.taskId !== todo.taskId, after ? after.taskId : 'no open task')
    } else {
      rec('Review task shows the Confirm action', false)
    }
  }
  await ctx.close()
} catch (e) {
  rec('script completed without throw', false, e?.message || String(e))
  console.error(e)
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} passed`)
if (failed.length) process.exit(1)
