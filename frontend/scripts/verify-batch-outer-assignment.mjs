/** Assignment applies uniformly to the pinned synthetic task's outer selection. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId ?? '', /^[a-f0-9-]{36}$/)
const origin = 'http://localhost:3000'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  await loginViaPortalPassword(page)
  const task = await (await page.request.get(`${origin}/api/portal/tasks/${taskId}`)).json()
  assert.match(task.data.functionUnitName, /^Batch verification /)
  const read = async () => {
    const response = await page.request.get(`${origin}/api/portal/tasks/${taskId}/form-data`)
    const body = await response.json(); assert.ok(response.ok() && body.success); return body.data
  }
  const before = await read()
  const capability = Object.values(before.subTableBatchCapabilities)[0]
  assert.equal(capability.capabilities.allowBatchAssign, true)
  const config = capability.assignmentConfig
  const key = `dw:${capability.rowDesign.binding.tableName}`
  const baseline = before.fieldValues.__subTables__[key]
  assert.equal(baseline.length, 2)
  assert.ok(!baseline[0][config.assigneeField], 'Use a fresh synthetic task without an existing person assignment')
  await page.goto(`${origin}/portal/tasks/${taskId}`)
  const grid = page.locator('.sub-table-field').filter({ has: page.getByRole('button', { name: 'Batch Edit', exact: true }) }).first()
  await grid.locator('.el-table__body-wrapper tbody tr').first().locator('.el-checkbox').click()
  await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  const dialog = page.locator('.batch-edit-dialog:visible')
  await dialog.waitFor()
  assert.equal(await dialog.locator('table, input[type="checkbox"]').count(), 0)
  await dialog.getByRole('radio', { name: /Assign by person/ }).click()
  const person = dialog.locator('.batch-assignment-editor .el-select').first()
  await person.click()
  await person.locator('input').fill('developer')
  await page.getByRole('option').filter({ hasText: /developer|Developer/ }).first().click()
  const pending = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  const response = await pending
  assert.ok(response.ok() && (await response.json()).success)
  const submitted = response.request().postDataJSON()
  const change = submitted.subTableBatchChanges.find(item => item.operation === 'ASSIGN')
  assert.equal(change.rowPatches.length, 1)
  const after = (await read()).fieldValues.__subTables__[key]
  assert.ok(after[0][config.assigneeField])
  assert.equal(after[0][config.assigneeField], change.rowPatches[0].assignment.assigneeId)
  assert.equal(after[1][config.assigneeField], baseline[1][config.assigneeField])
  assert.equal(after[1][config.roleField], baseline[1][config.roleField])
  assert.equal(after[1][config.buField], baseline[1][config.buField])
  // Saving may refresh task permissions and reset the outer selection.
  if (await grid.getByRole('button', { name: 'Batch Edit', exact: true }).isDisabled()) {
    await grid.locator('.el-table__body-wrapper tbody tr').first().locator('.el-checkbox').click()
  }
  // Display the single assignment form again with its unchanged initial state.
  await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  await dialog.waitFor()
  const screenshot = fileURLToPath(new URL('../user-portal/verification-screenshots/2026-10-20_batch-outer-assignment.png', import.meta.url))
  await dialog.screenshot({ path: screenshot })
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ result: 'PASS', selectedAssignmentRows: 1, untouchedAssignmentRows: 1, screenshot }))
} finally { await browser.close() }
