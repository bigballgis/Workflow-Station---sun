import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { chromium } from 'playwright'

const session = JSON.parse(readFileSync('../.codex-1232-session.json', 'utf8'))
const sample = JSON.parse(readFileSync('../.codex-1232-sample.json', 'utf8'))
const phase = process.env.PHASE ?? 'after'
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ storageState: session.state, viewport: { width: 1600, height: 1500 } })
const page = await context.newPage()
const errors = []
page.on('pageerror', error => errors.push(error.message))
const shots = 'user-portal/verification-screenshots/2026-10-31_1232-parity'
try {
  await page.goto(`http://localhost:3000/portal/tasks/${sample.taskId}`)
  await page.getByRole('button', { name: 'Edit', exact: true }).first().click()
  const dialog = page.getByRole('dialog').last()
  const email = dialog.locator('.el-form-item').filter({ has: page.locator('label', { hasText: /^Assignee email$/ }) })
  const arn = dialog.locator('.el-form-item').filter({ has: page.locator('label', { hasText: /^ARN$/ }) })
  await email.waitFor()
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${shots}-${phase}-edit.png`, fullPage: true, animations: 'disabled' })
  const actual = {
    emailInsideAssignment: await email.evaluate(node => node.classList.contains('mi-assignment-block__field')),
    emailClosesAssignment: await email.evaluate(node => node.classList.contains('mi-assignment-block__field--last')),
    arnPlaceholder: await arn.locator('input').getAttribute('placeholder'),
    wordCount: await arn.locator('.el-input__count').count(),
    caseTypePlaceholder: await dialog.locator('.el-form-item').filter({ has: page.locator('label', { hasText: /^Case Type$/ }) }).locator('.el-select__placeholder').innerText(),
    amountPlaceholder: await dialog.locator('.el-form-item').filter({ has: page.locator('label', { hasText: /^Billing Amount$/ }) }).locator('input').getAttribute('placeholder'),
  }
  console.log('EDIT PRESENTATION', actual)
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  await page.getByText('Details', { exact: true }).first().click()
  const details = page.getByRole('dialog').last()
  await details.getByText('Assignee email', { exact: true }).waitFor()
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${shots}-${phase}-details.png`, fullPage: true, animations: 'disabled' })
  assert.deepEqual(errors, [])
  assert.deepEqual(actual, {
    emailInsideAssignment: true, emailClosesAssignment: true,
    arnPlaceholder: 'Please input ARN', wordCount: 1,
    caseTypePlaceholder: 'Select', amountPlaceholder: '',
  })
  console.log('PASS Edit respects designed assignment children, placeholder and word count', sample.taskId)
} finally { await browser.close() }
