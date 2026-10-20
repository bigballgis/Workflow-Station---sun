/** Checks a tagged synthetic design end-to-end; no business FU designs are modified. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword, loginViaDwPassword } from './playwright-login.mjs'
const origin = 'http://localhost:3000'
const taskId = process.env.FIXTURE_TASK_ID
const unitId = process.env.FIXTURE_UNIT_ID
assert.match(taskId ?? '', /^[a-f0-9-]{36}$/)
assert.match(unitId ?? '', /^\d+$/)
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
    const body = await (await page.request.get(`${origin}/api/portal/tasks/${taskId}/form-data`)).json()
    assert.equal(body.success, true)
    return body.data
  }
  const baseline = await read()
  const cap = Object.values(baseline.subTableBatchCapabilities)[0]
  assert.equal(cap.batchForm.rule[0].props.header, 'Designed batch layout')
  const [textRule, numberRule] = cap.batchForm.rule[0].children
  const storeKey = `dw:${cap.rowDesign.binding.tableName}`
  const rows = dto => dto.fieldValues.__subTables__[storeKey]
  const business = row => Object.fromEntries(Object.entries(row).filter(([key]) => key !== '_wsRowVersion'))
  const before = rows(baseline)
  assert.notEqual(before[0][numberRule.field], 0, 'Start a fresh synthetic instance for this mutating verification')
  assert.ok(!before[0][cap.assignmentConfig.assigneeField], 'The selected synthetic row must start unassigned')
  await page.goto(`${origin}/portal/tasks/${taskId}`)
  const grid = page.locator('.sub-table-field').filter({ has: page.getByRole('button', { name: 'Batch Edit', exact: true }) }).first()
  const open = async () => {
    // Verify each draft against the persisted page, after any auto-save refresh.
    await page.reload()
    await grid.locator('.el-table__body-wrapper tbody tr').first().waitFor()
    await grid.locator('.el-table__body-wrapper tbody tr').first().locator('.el-checkbox').click()
    await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  }
  const dialog = page.locator('.batch-edit-dialog:visible')
  const screenshot = async (locator, name, app = 'user-portal') => {
    const path = fileURLToPath(new URL(`../${app}/verification-screenshots/2026-10-20_batch-designed-${name}.png`, import.meta.url))
    await locator.screenshot({ path })
    return path
  }
  const submitRequests = []
  page.on('request', request => { if (request.url().endsWith(`/tasks/${taskId}/submit`)) submitRequests.push(request) })
  await grid.locator('.el-table__body-wrapper tbody tr').first().waitFor()
  await open()
  await dialog.waitFor()
  assert.equal(await dialog.locator('.batch-edit-dialog__intent, p[role="status"], table').count(), 0)
  const caption = dialog.locator('.el-form-item').filter({ hasText: 'Caption' }).getByRole('textbox')
  const amount = dialog.getByRole('spinbutton', { name: /Amount/ })
  assert.equal(await caption.inputValue(), textRule.value)
  assert.equal(await amount.inputValue(), String(numberRule.value))
  const width = await dialog.locator('.el-input-number').evaluate(el => getComputedStyle(el).width)
  assert.equal(width, numberRule.style.width)
  const geometry = await dialog.locator('.el-dialog__body').evaluate(el => ({ client: el.clientWidth, scroll: el.scrollWidth }))
  const initialScreenshot = await screenshot(dialog, 'portal')
  assert.equal(geometry.client, geometry.scroll)
  assert.equal(await dialog.locator('.mi-assignment-widget__fields .el-form-item:visible').count(), 0)
  await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  assert.equal(submitRequests.length, 0, 'Designer defaults must not create a write')
  assert.deepEqual(rows(await read()), before)
  await open()
  await amount.fill('0')
  await caption.click()
  const saved = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
  assert.equal((await (await saved).json()).success, true)
  const after = rows(await read())
  assert.equal(after[0][numberRule.field], 0)
  assert.equal(after[0][textRule.field], before[0][textRule.field])
  assert.deepEqual(business(after[1]), business(before[1]))
  await open()
  await dialog.getByRole('radio', { name: /Assign by person/ }).click()
  const person = dialog.locator('.el-form-item').filter({ hasText: 'Designed person' }).locator('.el-select')
  await person.waitFor()
  assert.equal(await dialog.locator('.mi-assignment-widget__fields .el-form-item:visible').count(), 1)
  assert.equal(await person.evaluate(el => getComputedStyle(el).width), '287px')
  await person.click()
  await person.locator('input').fill('developer')
  await page.getByRole('option', { name: /developer/i }).first().waitFor()
  await page.getByRole('option', { name: /developer/i }).first().click()
  await page.locator('.el-select__popper:visible').waitFor({ state: 'hidden' })
  const assignmentScreenshot = await screenshot(dialog, 'assignment')
  const assignmentSave = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
  assert.equal((await (await assignmentSave).json()).success, true)
  const assigned = rows(await read())
  assert.ok(assigned[0][cap.assignmentConfig.assigneeField])
  assert.deepEqual(business(assigned[1]), business(before[1]))
  assert.equal(assigned[0][textRule.field], before[0][textRule.field])
  assert.equal(assigned[0][numberRule.field], 0)
  await loginViaDwPassword(page)
  const unit = await (await page.request.get(`${origin}/api/v1/function-units/${unitId}`)).json()
  assert.ok(unit.data.tags.includes('batch-verification-synthetic'))
  await page.goto(`${origin}/dev/function-units/${unitId}`)
  await page.getByRole('tab', { name: /Form/ }).click()
  await page.getByRole('row').filter({ hasText: 'Synthetic TASK' }).getByRole('button', { name: 'Edit', exact: true }).click()
  await page.locator('.form-editor-view .fc-designer-wrapper').first().waitFor()
  // The editor is only read: no drag, property change or save.
  await page.locator('.form-editor-view .designer-nav-btn').filter({ hasText: /Sub Table/ }).click()
  await page.getByRole('menuitem').filter({ hasText: 'Synthetic lines' }).click()
  await page.getByRole('button', { name: 'Batch Form', exact: true }).click()
  const canvas = page.locator('.form-editor-view .fc-designer-wrapper:visible').last()
  await canvas.getByText('Designed batch layout', { exact: true }).waitFor()
  const designerScreenshot = await screenshot(canvas, 'dw-canvas', 'developer-workstation')
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ result: 'PASS', geometry, configuredNumberWidth: width, defaultNoWrite: true,
    selectedOnlyEdit: true, selectedOnlyAssignment: true, screenshots: [initialScreenshot, assignmentScreenshot, designerScreenshot] }))
} finally { await browser.close() }
