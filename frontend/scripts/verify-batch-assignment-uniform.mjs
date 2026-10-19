/** Continue only a synthetic pre-subtask fixture: cancel and uniform initial assignment, not task creation. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId || '', /^[a-f0-9-]{36}$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.name))
  await loginViaPortalPassword(page)
  const formData = async () => {
    const response = await page.request.get(`http://localhost:3000/api/portal/tasks/${taskId}/form-data`)
    const body = await response.json()
    assert.equal(response.ok() && body.success, true)
    return body.data
  }
  const taskResponse = await page.request.get(`http://localhost:3000/api/portal/tasks/${taskId}`)
  const task = (await taskResponse.json()).data
  assert.match(task.functionUnitName, /^Batch verification /)
  const form = await formData()
  const [control] = Object.values(form.subTableBatchCapabilities)
  assert.equal(control.capabilities.allowBatchAssign, true)
  const store = `dw:${control.rowDesign.binding.tableName}`
  const rowsOf = data => data.fieldValues.__subTables__[store]
  const baseline = rowsOf(form)
  assert.equal(baseline.length, 2)
  assert.equal(baseline[1][control.assignmentConfig.assigneeField] ?? null, null, 'Use a fixture with an unchanged second-row target')
  await page.goto(`http://localhost:3101/portal/tasks/${taskId}`)
  const open = page.getByRole('button', { name: 'Batch Edit', exact: true })
  await open.click()
  const dialog = page.locator('.el-dialog:visible')
  const editor = dialog.locator('.batch-assignment-editor')
  await editor.locator('.el-table__body-wrapper tr').nth(1).waitFor()
  const mode = editor.locator('.batch-assignment-editor__uniform .el-select').first()
  await mode.click()
  await page.locator('.el-select-dropdown:visible').getByText('Assign by role', { exact: true }).click()
  await dialog.getByRole('button', { name: /Apply/ }).click()
  const error = page.locator('.el-message--error').last()
  await error.waitFor()
  assert.match(await error.innerText(), /Select a valid person/)
  assert.doesNotMatch(await error.innerText(), /subTable\./)
  await editor.scrollIntoViewIfNeeded()
  const shot = fileURLToPath(new URL('../user-portal/verification-screenshots/2026-10-15_batch-assignment-error.png', import.meta.url))
  await error.screenshot({ path: shot.replace('.png', '-message.png') })
  await dialog.screenshot({ path: shot, mask: [editor.locator('.el-select__selected-item')] })
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: /OK|Confirm/ }).click()
  await dialog.waitFor({ state: 'hidden' })
  assert.deepEqual(rowsOf(await formData()), baseline, 'Cancel must discard the assignment draft')
  await open.click()
  await editor.locator('.el-table__body-wrapper tr').nth(1).waitFor()
  await editor.locator('.el-table__body-wrapper tr').nth(1).locator('.el-checkbox').click()
  await mode.click()
  await page.locator('.el-select-dropdown:visible').getByText('Assign by person', { exact: true }).click()
  const person = editor.locator('.batch-assignment-editor__uniform .el-select').nth(1)
  const search = page.waitForResponse(response => response.url().includes('/tasks/users/search'))
  await person.locator('input').fill('developer')
  const response = await search
  assert.equal(response.ok(), true)
  const target = (await response.json()).data.find(user => user.username === 'developer')
  assert.ok(target?.id)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: target.displayName || target.fullName || target.username }).first().click()
  const save = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: /Apply/ }).click()
  const savedResponse = await save
  assert.equal((await savedResponse.json()).success, true)
  const changes = savedResponse.request().postDataJSON().subTableBatchChanges
  assert.equal(changes.length, 1); assert.equal(changes[0].operation, 'ASSIGN')
  assert.equal(changes[0].rowPatches.length, 1)
  const key = control.rowDesign.binding.primaryKeyFields[0]
  assert.equal(changes[0].rowPatches[0].rowKey[key], baseline[1][key])
  const saved = rowsOf(await formData())
  assert.deepEqual(saved[0], { ...baseline[0], _wsRowVersion: baseline[0]._wsRowVersion + 1 })
  assert.equal(saved[1][control.assignmentConfig.assigneeField], target.id)
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ step: 'uniform-assignment-and-cancel-pass', taskId, processInstanceId: task.processInstanceId, screenshot: shot, pageErrors: errors.length }))
} finally { await browser.close() }
