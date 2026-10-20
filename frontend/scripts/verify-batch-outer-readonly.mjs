/** Read-only check of an existing task: select rows, open the batch form, cancel. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId ?? '', /^[a-f0-9-]{36}$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await loginViaPortalPassword(page)
  await page.goto(`http://localhost:3000/portal/tasks/${taskId}`)
  const grid = page.locator('.sub-table-field').filter({ has: page.getByRole('button', { name: 'Batch Edit', exact: true }) }).first()
  const rows = grid.locator('.el-table__body-wrapper tbody tr')
  await rows.first().waitFor()
  for (let index = 0; index < Math.min(2, await rows.count()); index++) await rows.nth(index).locator('.el-checkbox').click()
  await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  const dialog = page.locator('.batch-edit-dialog:visible')
  await dialog.waitFor()
  assert.equal(await dialog.locator('table, input[type="checkbox"]').count(), 0)
  const geometry = await dialog.locator('.el-dialog__body').evaluate(body => ({ scrollLeft: body.scrollLeft, clientWidth: body.clientWidth, scrollWidth: body.scrollWidth }))
  const name = process.env.SCREENSHOT_VARIANT ?? (process.env.VERIFY_ASSIGNMENT === '1' ? 'assignment' : 'existing-demo')
  const screenshot = fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-20_batch-outer-${name}.png`, import.meta.url))
  await dialog.screenshot({ path: screenshot })
  assert.equal(geometry.scrollWidth, geometry.clientWidth, 'Batch Form must not overflow horizontally')
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ result: 'PASS', readOnly: true, geometry, screenshot }))
} finally { await browser.close() }
