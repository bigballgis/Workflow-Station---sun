/** Read-only reproduction on any selected task. Real writes are restricted to tagged synthetic workflows. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId ?? '', /^[a-f0-9-]{36}$/)
const stage = process.env.VERIFY_STAGE ?? 'after'
assert.match(stage, /^[a-z-]+$/)
const origin = 'http://localhost:3000'
const write = process.env.VERIFY_ASSIGN_WRITES === '1'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 1000 } })
  const errors = [], saves = [], screenshots = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  page.on('request', request => { if (/\/tasks\/[^/]+\/(submit|complete)$/.test(request.url())) saves.push(request) })
  await loginViaPortalPassword(page)
  const read = async () => {
    const body = await (await page.request.get(`${origin}/api/portal/tasks/${taskId}/form-data`)).json()
    assert.equal(body.success, true)
    return body.data
  }
  const baseline = await read()
  const cap = Object.values(baseline.subTableBatchCapabilities)[0]
  assert.ok(cap.assignmentConfig)
  const hasAssignmentChildren = cap.batchForm.rule.some(rule => rule.type === 'miAssignment' && rule.children?.length)
  const config = cap.assignmentConfig
  const storeKey = `dw:${cap.rowDesign.binding.tableName}`
  const rows = dto => dto.fieldValues.__subTables__[storeKey]
  const business = row => Object.fromEntries(Object.entries(row).filter(([key]) => key !== '_wsRowVersion'))
  const before = rows(baseline)
  if (write) {
    const task = await (await page.request.get(`${origin}/api/portal/tasks/${taskId}`)).json()
    assert.match(task.data.functionUnitName, /^Batch verification /)
    assert.equal(before.length, 2)
    assert.ok(!before[0][config.assigneeField] && !before[0][config.roleField], 'Start an unassigned synthetic instance')
  }
  const dialog = page.locator('.batch-edit-dialog:visible')
  async function open() {
    await page.goto(`${origin}/portal/tasks/${taskId}`)
    await page.reload()
    const grid = page.locator('.sub-table-field').filter({ has: page.getByRole('button', { name: 'Batch Edit', exact: true }) }).first()
    await grid.locator('.el-table__body-wrapper tbody tr').first().waitFor()
    await grid.locator('.el-table__body-wrapper tbody tr').first().locator('.el-checkbox').click()
    await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
    await dialog.waitFor()
    assert.doesNotMatch(await grid.locator('.sub-table-header').innerText(), /Selected rows:/)
    assert.doesNotMatch(await dialog.innerText(), /Selected rows:/)
  }
  async function shot(mode) {
    const path = fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-20_assignment-${stage}-${mode}.png`, import.meta.url))
    await dialog.screenshot({ path }); screenshots.push(path)
  }
  async function cancel() {
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    const confirm = page.locator('.el-message-box:visible')
    if (await confirm.count()) await confirm.getByRole('button', { name: /OK|Confirm/ }).click()
    await dialog.waitFor({ state: 'hidden' })
  }
  async function save() {
    const pending = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
    await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
    assert.equal((await (await pending).json()).success, true)
    await dialog.waitFor({ state: 'hidden' })
    return rows(await read())
  }
  await open()
  await dialog.getByRole('radio', { name: /Assign by person/ }).click()
  await shot('person')
  const person = dialog.locator('.mi-assignment-widget__fields .el-select')
  assert.equal(await person.count(), 1, 'Assign by person must contain a working person picker, including marker-only designs')
  if (write) {
    await person.click()
    await person.locator('input').fill('developer')
    await page.getByRole('option').filter({ hasText: /developer/i }).first().click()
    await page.locator('.el-select__popper:visible').waitFor({ state: 'hidden' })
    await shot('person-chosen')
    const assigned = await save()
    assert.ok(assigned[0][config.assigneeField])
    assert.deepEqual(business(assigned[1]), business(before[1]))
  } else await cancel()
  await open()
  await dialog.getByRole('radio', { name: /Assign by role/ }).click()
  await shot('role')
  const bu = dialog.locator('.mi-assignment-widget__fields .el-cascader')
  const role = dialog.locator('.mi-assignment-widget__fields .el-select')
  assert.equal(await bu.count(), 1)
  assert.equal(await role.count(), 1)
  assert.equal(await role.locator('input').isDisabled(), true, 'Role must wait for a BU')
  if (write) {
    await bu.click()
    await bu.locator('input').fill('hase-hmdc')
    await page.locator('.el-cascader__suggestion-item:visible').filter({ hasText: /hase-hmdc/i }).first().click()
    await role.locator('input').waitFor({ state: 'visible' })
    await role.click()
    await page.getByRole('option').filter({ hasText: /HMDC_Operator_Role/ }).first().click()
    await page.locator('.el-select__popper:visible').waitFor({ state: 'hidden' })
    await shot('role-chosen')
    const assigned = await save()
    assert.equal(assigned[0][config.buField], 'hase-hmdc')
    assert.equal(assigned[0][config.roleField], 'HMDC_Operator_Role')
    assert.ok(!assigned[0][config.assigneeField], 'Switching to role clears the old person assignment')
    assert.deepEqual(business(assigned[1]), business(before[1]))
    for (const field of ['item_label', 'units']) assert.equal(assigned[0][field], before[0][field])
  } else {
    await cancel()
    assert.equal(saves.length, 0)
    assert.deepEqual(rows(await read()), before)
  }
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ result: 'PASS', write, hasAssignmentChildren, screenshots }))
} finally { await browser.close() }
