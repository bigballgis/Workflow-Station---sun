/** Real selected-row batch acceptance; writes only to an explicitly pinned synthetic task. */
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword, loginViaDwPassword } from './playwright-login.mjs'

const origin = 'http://localhost:3000'
const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId ?? '', /^[a-f0-9-]{36}$/)
const browser = await chromium.launch({ headless: true })
const screenshots = []
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await loginViaPortalPassword(page)
  const read = async () => {
    const response = await page.request.get(`${origin}/api/portal/tasks/${taskId}/form-data`)
    assert.equal(response.ok(), true)
    const body = await response.json()
    assert.equal(body.success, true)
    return body.data
  }
  const task = await (await page.request.get(`${origin}/api/portal/tasks/${taskId}`)).json()
  assert.match(task.data.functionUnitName, /^Batch verification /, 'Refusing a business workflow')
  const baseline = await read()
  const capability = Object.values(baseline.subTableBatchCapabilities)[0]
  assert.ok(capability)
  const tableName = capability.rowDesign.binding.tableName
  const storeKey = `dw:${tableName}`
  const persistedRows = dto => dto.fieldValues.__subTables__[storeKey]
  const businessRow = ({ _wsRowVersion, ...row }) => row
  const before = persistedRows(baseline)
  assert.equal(before.length, 2, 'Start with a fresh two-row synthetic fixture')
  const textField = capability.batchForm.rule.find(rule => rule.type === 'input').field
  const numberField = capability.batchForm.rule.find(rule => rule.type === 'inputNumber').field
  const pk = capability.rowDesign.binding.primaryKeyFields[0]
  const fk = capability.rowDesign.binding.filterFkFieldName ?? capability.rowDesign.binding.foreignKeyField
  await page.goto(`${origin}/portal/tasks/${taskId}`)
  const grid = page.locator('.sub-table-field').filter({ has: page.getByRole('button', { name: 'Batch Edit', exact: true }) }).first()
  await grid.waitFor()
  const edit = grid.getByRole('button', { name: 'Batch Edit', exact: true })
  await assert.equal(await edit.isDisabled(), true)
  const gridRows = () => grid.locator('.el-table__body-wrapper tbody tr')
  await gridRows().nth(0).locator('.el-checkbox').click()
  const shot = async (locator, name, app = 'user-portal') => {
    const path = fileURLToPath(new URL(`../${app}/verification-screenshots/2026-10-20_batch-outer-${name}.png`, import.meta.url))
    mkdirSync(fileURLToPath(new URL(`../${app}/verification-screenshots/`, import.meta.url)), { recursive: true })
    await locator.screenshot({ path }); screenshots.push(path)
  }
  await shot(grid, 'selection')
  await edit.click()
  const dialog = page.locator('.batch-edit-dialog:visible')
  await dialog.waitFor()
  assert.equal(await dialog.locator('table, input[type="checkbox"]').count(), 0)
  assert.equal(await dialog.locator('p[role="status"]').count(), 0)
  assert.equal(await grid.locator('.el-table__body-wrapper input[type="checkbox"]:checked').count(), 1)
  const caption = () => dialog.locator('.el-form-item').filter({ hasText: 'Caption' }).getByRole('textbox')
  const amount = () => dialog.getByRole('spinbutton', { name: /Amount/ })
  assert.equal(await caption().inputValue(), '')
  await caption().fill('Uniform selection')
  await amount().fill('0')
  await caption().click()
  await shot(dialog, 'form')
  const applyAndSave = async () => {
    const pending = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST', { timeout: 30000 })
    await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    const response = await pending
    const body = await response.json()
    assert.ok(response.ok() && body.success, 'Normal form save must succeed')
    return read()
  }
  const edited = persistedRows(await applyAndSave())
  assert.equal(edited[0][textField], 'Uniform selection')
  assert.equal(edited[0][numberField], 0)
  assert.deepEqual(businessRow(edited[1]), businessRow(before[1]), 'Unselected business values and identity must remain unchanged')
  await grid.getByRole('button', { name: 'Batch Add', exact: true }).click()
  await dialog.waitFor()
  await dialog.getByRole('spinbutton').first().fill('2')
  await caption().fill('Uniform added')
  await amount().fill('3')
  await caption().click()
  const added = persistedRows(await applyAndSave())
  assert.equal(added.length, 4)
  assert.equal(added.filter(row => row[textField] === 'Uniform added').length, 2)
  assert.equal(new Set(added.map(row => row[pk])).size, 4)
  assert.ok(added.every(row => row[fk] === before[0][fk]))
  // Clear the previous selection, then delete only one newly added row.
  for (const row of await gridRows().all()) if (await row.locator('input[type="checkbox"]').isChecked()) await row.locator('.el-checkbox').click()
  await gridRows().nth(3).locator('.el-checkbox').click()
  const deleteSave = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
  await grid.getByRole('button', { name: 'Batch Delete', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: /OK|Confirm/ }).click()
  assert.equal((await (await deleteSave).json()).success, true)
  const deleted = persistedRows(await read())
  assert.equal(deleted.length, 3)
  assert.deepEqual(businessRow(deleted.find(row => row[pk] === before[1][pk])), businessRow(before[1]))
  // A new selection after deletion must not resurrect the deleted identity.
  await gridRows().nth(1).locator('.el-checkbox').click()
  await edit.click()
  assert.doesNotMatch(await dialog.innerText(), /Selected rows:/)
  assert.doesNotMatch(await grid.locator('.sub-table-header').innerText(), /Selected rows:/)
  await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  assert.deepEqual(persistedRows(await read()), deleted, 'Unchanged Apply must not write values')
  await shot(grid, 'persisted')

  if (process.env.FIXTURE_UNIT_ID) {
    const dw = await browser.newPage({ viewport: { width: 1366, height: 1000 } })
    dw.on('pageerror', error => errors.push(error.message))
    await loginViaDwPassword(dw)
    await dw.goto(`${origin}/dev/function-units/${process.env.FIXTURE_UNIT_ID}`)
    await dw.getByRole('tab', { name: /Form/ }).click()
    await dw.getByRole('row').filter({ hasText: 'Synthetic TASK' }).getByRole('button', { name: 'Edit', exact: true }).click()
    await dw.locator('.form-editor-view .fc-designer-wrapper').first().waitFor()
    await dw.locator('.form-editor-view .header-actions').getByRole('button', { name: 'Preview', exact: true }).click()
    const preview = dw.locator('.form-preview-dialog').last()
    await preview.waitFor()
    const previewGrid = preview.locator('.sub-table-field').first()
    await previewGrid.getByRole('button', { name: 'Batch Add', exact: true }).click()
    const previewBatch = dw.locator('.batch-edit-dialog:visible')
    await previewBatch.waitFor()
    assert.equal(await previewBatch.locator('table, input[type="checkbox"]').count(), 0)
    await shot(previewBatch, 'dw-form', 'developer-workstation')
  }
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ result: 'PASS', selectedEdit: true, unchangedRows: true, generatedPkFk: true, batchDelete: true, screenshots }))
} finally { await browser.close() }
