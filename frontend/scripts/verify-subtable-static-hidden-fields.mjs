/**
 * Screenshot gate: designer-hidden sub-form fields must not render in the
 * sub-table Add/Edit dialog.
 *
 * Regression source (prod): two fields hidden on the ATM Transaction sub-form canvas
 * still showed on first open, because the dialog column mapping dropped the Hide flag
 * and nothing seeded visibility when the dialog opened.
 *
 * Dev fixture: FU 50029 "ATM" (process key atm-20260623-gaevus), form 50190, sub-form
 * binding 50533 → table ATM Transaction. Flip `hidden: true` on the rules named by
 * HIDDEN_FIELDS below in dw_form_definitions.config_json before running, and restore
 * afterwards — this script only reads.
 *
 * Usage:
 *   node scripts/verify-subtable-static-hidden-fields.mjs before
 *   node scripts/verify-subtable-static-hidden-fields.mjs after
 *
 * Env overrides: PORTAL_START_URL, SUBTABLE_HINT, LOGIN_USER, LOGIN_PASS
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const phase = (process.argv[2] || 'run').replace(/[^a-z0-9-]/gi, '')
const startUrl =
  process.env.PORTAL_START_URL
  || 'http://localhost:3000/portal/processes/start/atm-20260623-gaevus'
const subTableHint = (process.env.SUBTABLE_HINT || 'atm transaction').toLowerCase()

/** Labels the designer marked Hidden — must be absent from the dialog. */
const HIDDEN_FIELDS = ['Transaction Date', 'ARN']
/** Labels left visible — must still render, proving we did not over-hide. */
const VISIBLE_FIELDS = ['Card Number', 'Merchant Name', 'Billing Amount']

const outDir = join(__dirname, '../user-portal/verification-screenshots')
mkdirSync(outDir, { recursive: true })
const date = new Date().toISOString().slice(0, 10)
const slug = `subtable-static-hidden-fields-${phase}`

const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const page = await browser.newPage({ viewport: { width: 1400, height: 1000 } })
let failed = false

try {
  await loginViaPortalPassword(page)
  await page.goto(startUrl, { waitUntil: 'networkidle', timeout: 60000 })
  await page.waitForTimeout(2500)

  const blocks = page.locator('.sub-table-field')
  const count = await blocks.count()
  if (count === 0) throw new Error(`No .sub-table-field on ${startUrl}`)

  let target = null
  for (let i = 0; i < count; i++) {
    const block = blocks.nth(i)
    const text = ((await block.textContent().catch(() => '')) || '').toLowerCase()
    if (text.includes(subTableHint)) { target = block; break }
  }
  if (!target) throw new Error(`No sub-table matching "${subTableHint}" among ${count} blocks`)

  await target.locator('button').filter({ hasText: /add|新增|添加/i }).first().click()
  await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 15000 })
  await page.waitForTimeout(1200)

  const dialog = page.locator('.el-dialog:visible').last()
  const shotPath = join(outDir, `${date}_${slug}.png`)
  await dialog.screenshot({ path: shotPath })

  // Assert on rendered form-item labels, not raw HTML: a hidden field's label text can
  // still appear in an unrelated attribute, which would make a substring check lie.
  const labels = (await dialog.locator('.el-form-item__label').allTextContents())
    .map(s => s.replace(/\s+/g, ' ').trim())
    .filter(Boolean)

  console.log(`\n=== HIDDEN FIELDS REPORT (${phase}) ===`)
  console.log('screenshot :', shotPath)
  console.log('labels     :', JSON.stringify(labels))

  for (const name of HIDDEN_FIELDS) {
    const present = labels.some(l => l.replace(/[:：*]/g, '').trim() === name)
    console.log(`${present ? 'VISIBLE ✗' : 'hidden  ✓'}  ${name}  (expected: hidden)`)
    if (present) failed = true
  }
  for (const name of VISIBLE_FIELDS) {
    const present = labels.some(l => l.replace(/[:：*]/g, '').trim() === name)
    console.log(`${present ? 'visible ✓' : 'MISSING ✗'}  ${name}  (expected: visible)`)
    if (!present) failed = true
  }

  // `Transaction Date` is required AND hidden — the case that would strand the user with an
  // unreachable validation error. Save on the empty form: validation must fail only on
  // fields that are actually on screen. Nothing is persisted (required fields are blank).
  await dialog.locator('button').filter({ hasText: /^\s*(save|保存)\s*$/i }).first().click()
  await page.waitForTimeout(1000)
  const erroredLabels = (
    await dialog.locator('.el-form-item.is-error .el-form-item__label').allTextContents()
  ).map(s => s.replace(/[\s:：*]+/g, ' ').trim()).filter(Boolean)
  console.log('errored    :', JSON.stringify(erroredLabels))
  const strandedOn = HIDDEN_FIELDS.filter(n => erroredLabels.some(l => l === n))
  if (strandedOn.length > 0) {
    console.log(`BLOCKED ✗  save validation errors on hidden field(s): ${strandedOn.join(', ')}`)
    failed = true
  } else {
    console.log('save    ✓  no validation error attached to any hidden field')
  }
  const savePath = join(outDir, `${date}_${slug}-save-validation.png`)
  await dialog.screenshot({ path: savePath })
  console.log('screenshot :', savePath)

  console.log(`RESULT: ${failed ? 'FAIL' : 'PASS'}\n`)
} finally {
  await browser.close()
}

process.exit(failed ? 1 : 0)
