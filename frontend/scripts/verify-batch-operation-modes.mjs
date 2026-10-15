/** Explicitly pinned synthetic Add-only/Delete-only UI acceptance. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId || '', /^[a-f0-9-]{36}$/)
const origin = 'http://localhost:3101'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  let pageErrors = 0
  page.on('pageerror', () => pageErrors++)
  await loginViaPortalPassword(page, { loginOrigin: origin })
  const read = async path => {
    const response = await page.request.get(`${origin}/api/portal${path}`)
    const body = await response.json()
    assert.equal(response.ok() && body.success, true, 'Synthetic fixture read must succeed')
    return body.data
  }
  const task = await read(`/tasks/${taskId}`)
  assert.match(task.functionUnitName, /^Batch verification /)
  const form = await read(`/tasks/${taskId}/form-data`)
  assert.equal(form.formName, 'Synthetic TASK')
  const controls = Object.values(form.subTableBatchCapabilities)
  assert.equal(controls.length, 1)
  const control = controls[0]
  const flags = control.capabilities
  assert.equal(flags.allowBatchEdit, false)
  assert.equal(flags.allowBatchAssign, false)
  assert.equal(typeof flags.allowBatchAdd, 'boolean')
  assert.equal(typeof flags.allowBatchDelete, 'boolean')
  assert.notEqual(flags.allowBatchAdd, flags.allowBatchDelete, 'Only one membership operation must be enabled')
  const mode = flags.allowBatchAdd ? 'add' : 'delete'
  const binding = control.rowDesign.binding
  const store = `dw:${binding.tableName}`
  const rowsOf = data => data.fieldValues.__subTables__[store]
  const baseline = rowsOf(form)
  assert.equal(baseline.length, 2)
  const pk = binding.primaryKeyFields[0]
  const textField = control.batchForm.rule.find(rule => rule.type === 'input' && rule.field !== pk).field
  await page.goto(`${origin}/portal/tasks/${taskId}`)
  await page.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  const dialog = page.locator('.el-dialog:visible')
  const rows = dialog.locator('.el-table__body-wrapper tr')
  await rows.nth(1).waitFor()
  assert.equal(await rows.locator('input:not([type="checkbox"])').count(), 0, 'Existing business fields must be read-only')
  assert.equal(await dialog.getByRole('button', { name: 'Add', exact: true }).count(), mode === 'add' ? 1 : 0)
  assert.equal(await dialog.getByRole('button', { name: 'Delete', exact: true }).count(), mode === 'delete' ? 1 : 0)
  if (mode === 'add') {
    await dialog.getByRole('button', { name: 'Add', exact: true }).click()
    const inputs = rows.nth(2).locator('input:not([type="checkbox"])')
    assert.equal(await inputs.count(), 3, 'Synthetic manual PK, caption and quantity must accept initial values')
    await inputs.nth(0).fill('SYNTHETIC-THREE')
    await inputs.nth(1).fill('Third synthetic line')
    await inputs.nth(2).fill('3')
    await inputs.nth(2).blur()
  } else {
    await rows.first().locator('.el-checkbox').click()
    await dialog.getByRole('button', { name: 'Delete', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: /Confirm|OK/, exact: true }).click()
  }
  assert.deepEqual(rowsOf(await read(`/tasks/${taskId}/form-data`)), baseline, 'Membership draft must not persist before Apply')
  const pendingSave = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: /Apply/ }).click()
  const save = await pendingSave
  assert.equal(save.ok() && (await save.json()).success, true)
  const declarations = save.request().postDataJSON().subTableBatchChanges
  assert.deepEqual(declarations.map(change => change.operation), [mode.toUpperCase()])
  const saved = rowsOf(await read(`/tasks/${taskId}/form-data`))
  assert.equal(saved.length, mode === 'add' ? 3 : 1)
  const survivors = mode === 'add' ? baseline : baseline.slice(1)
  for (const original of survivors) {
    const actual = saved.find(row => row[pk] === original[pk])
    assert.deepEqual({ ...actual, _wsRowVersion: original._wsRowVersion }, original, 'Surviving business values must not change')
  }
  if (mode === 'add') {
    const added = saved.find(row => row[pk] === 'SYNTHETIC-THREE')
    assert.equal(added[textField], 'Third synthetic line')
    assert.equal(added[binding.foreignKeyField], 'SYNTHETIC-CASE')
  }
  await page.reload()
  await page.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  await rows.first().waitFor()
  await dialog.screenshot({ path: fileURLToPath(new URL(`../user-portal/verification-screenshots/${new Date().toISOString().slice(0, 10)}_batch-${mode}-only.png`, import.meta.url)) })
  assert.equal(pageErrors, 0)
  console.log(JSON.stringify({ result: 'PASS', mode, taskId, checks: 'single operation, readonly saved rows, draft, persistence, surviving values, declarations', pageErrors }))
} finally {
  await browser.close()
}
