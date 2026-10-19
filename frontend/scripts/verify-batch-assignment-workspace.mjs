/** Real pre-subtask synthetic BPMN host: verifies draft/Save only, never advances the MI target. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import { unwrapApiList } from './mi-regression-helpers.mjs'

const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId || '', /^[a-f0-9-]{36}$/)
const screenshotTag = process.env.FIXTURE_SCREENSHOT_TAG
if (screenshotTag) assert.match(screenshotTag, /^[a-z0-9-]+$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  let pageErrors = 0
  page.on('pageerror', () => pageErrors++)
  await loginViaPortalPassword(page)
  const api = async (method, path, data) => {
    const response = await page.request.fetch(`http://localhost:3000/api/portal${path}`, { method, data })
    const body = await response.json()
    assert.equal(response.ok() && body.success, true, `${method} ${path}: ${body.error?.message || response.status()}`)
    return body.data
  }
  const task = await api('GET', `/tasks/${taskId}`)
  assert.match(task.functionUnitName, /^Batch verification /)
  let activeTaskId = taskId
  let form = await api('GET', `/tasks/${activeTaskId}/form-data`)
  if (form.formName === 'Synthetic PROCESS') {
    await api('POST', `/tasks/${activeTaskId}/complete`, { taskId: activeTaskId, action: 'APPROVE', formData: form.fieldValues, comment: 'Synthetic assignment host' })
    const tasks = unwrapApiList(await api('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] })).filter(row => row.processInstanceId === task.processInstanceId)
    assert.equal(tasks.length, 1); activeTaskId = tasks[0].taskId
    form = await api('GET', `/tasks/${activeTaskId}/form-data`)
  }
  const entries = Object.entries(form.subTableBatchCapabilities)
  assert.equal(entries.length, 1)
  const [, control] = entries[0]
  assert.equal(control.capabilities.allowBatchAssign, true)
  const store = `dw:${control.rowDesign.binding.tableName}`
  const rowsOf = data => data.fieldValues.__subTables__[store]
  const baseline = rowsOf(form)
  assert.equal(baseline.length, 2)
  const beforeTasks = unwrapApiList(await api('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] })).filter(row => row.processInstanceId === task.processInstanceId).map(row => row.taskId).sort()
  await page.goto(`http://localhost:3101/portal/tasks/${activeTaskId}`)
  await page.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  const dialog = page.locator('.el-dialog:visible')
  const editor = dialog.locator('.batch-assignment-editor')
  await editor.locator('.el-table__body-wrapper tr').nth(1).waitFor()
  assert.equal(await page.locator('.el-dialog:visible').count(), 1)
  assert.equal(await editor.locator('.el-alert--error').count(), 0, 'Directory must load successfully')
  const ordinary = dialog.locator('.batch-edit-dialog__table .el-table__body-wrapper tr').first().locator('input:not([type="checkbox"])').first()
  if (control.capabilities.allowBatchEdit) await ordinary.fill('Combined synthetic draft')
  else assert.equal(await dialog.locator('.batch-edit-dialog__table .el-table__body-wrapper input:not([type="checkbox"])').count(), 0)
  const mode = editor.locator('.el-table__body-wrapper tr').first().locator('.el-select').first()
  await mode.click()
  await page.locator('.el-select-dropdown:visible').getByText('Assign by role', { exact: true }).click()
  await dialog.getByRole('button', { name: /Apply/ }).click()
  await page.locator('.el-message--error').waitFor()
  assert.equal(await dialog.isVisible(), true, 'Invalid Assignment must retain the whole workspace')
  if (control.capabilities.allowBatchEdit) assert.equal(await ordinary.inputValue(), 'Combined synthetic draft')
  assert.deepEqual(rowsOf(await api('GET', `/tasks/${activeTaskId}/form-data`)), baseline)
  await mode.click()
  await page.locator('.el-select-dropdown:visible').getByText('Assign by person', { exact: true }).click()
  const person = editor.locator('.el-table__body-wrapper tr').first().locator('.el-select').nth(1)
  await person.locator('input').fill('developer')
  const search = page.waitForResponse(response => response.url().includes('/tasks/users/search'))
  const searchResponse = await search
  const searchBody = await searchResponse.json()
  assert.equal(searchResponse.ok(), true)
  const target = searchBody.data.find(user => user.username === 'developer')
  assert.ok(target?.id)
  const option = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: target.displayName || target.fullName || target.username }).first()
  await option.click()
  if (control.capabilities.allowBatchEdit) {
    await ordinary.fill('')
    await dialog.getByRole('button', { name: /Apply/ }).click()
    assert.equal(await dialog.isVisible(), true, 'Invalid ordinary field must retain Assignment')
    assert.deepEqual(rowsOf(await api('GET', `/tasks/${activeTaskId}/form-data`)), baseline)
    await ordinary.fill('Combined synthetic draft')
  }
  assert.deepEqual(rowsOf(await api('GET', `/tasks/${activeTaskId}/form-data`)), baseline, 'Neither draft may persist before Apply')
  const prefix = screenshotTag ? `2026-10-16_${screenshotTag}-workspace` : '2026-10-15_batch-assignment'
  const screenshot = fileURLToPath(new URL(`../user-portal/verification-screenshots/${prefix}-${control.capabilities.allowBatchEdit ? 'mixed' : 'only'}.png`, import.meta.url))
  const footer = dialog.getByRole('button', { name: /Apply/ })
  assert.equal(await footer.isVisible(), true)
  assert.ok((await footer.boundingBox()).y + (await footer.boundingBox()).height <= 768,
    `Desktop footer must fit the viewport: ${JSON.stringify(await dialog.evaluate(el => ({ className: el.className, attributes: [...el.attributes].map(attr => attr.name), display: getComputedStyle(el).display, maxHeight: getComputedStyle(el).maxHeight, bodyOverflow: getComputedStyle(el.querySelector('.el-dialog__body')).overflowY, footer: el.querySelector('.el-dialog__footer').getBoundingClientRect().toJSON() })))}`)
  await editor.scrollIntoViewIfNeeded()
  await dialog.screenshot({ path: screenshot, mask: [editor.locator('.el-select__selected-item')] })
  const save = page.waitForResponse(response => response.url().endsWith(`/tasks/${activeTaskId}/submit`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: /Apply/ }).click()
  const savedResponse = await save
  assert.equal((await savedResponse.json()).success, true)
  const changes = savedResponse.request().postDataJSON().subTableBatchChanges
  assert.deepEqual(changes.map(change => change.operation).sort(), control.capabilities.allowBatchEdit ? ['ASSIGN', 'EDIT'] : ['ASSIGN'])
  assert.equal(changes.find(change => change.operation === 'ASSIGN').rowPatches.length, 1)
  const saved = await api('GET', `/tasks/${activeTaskId}/form-data`)
  assert.equal(rowsOf(saved)[0][control.assignmentConfig.assigneeField], target.id)
  assert.equal(rowsOf(saved)[1][control.assignmentConfig.assigneeField] ?? null, baseline[1][control.assignmentConfig.assigneeField] ?? null)
  if (control.capabilities.allowBatchEdit) assert.equal(rowsOf(saved)[0].caption, 'Combined synthetic draft')
  const afterTasks = unwrapApiList(await api('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] })).filter(row => row.processInstanceId === task.processInstanceId).map(row => row.taskId).sort()
  assert.deepEqual(afterTasks, beforeTasks, 'Save must not generate tasks')
  await page.reload()
  await page.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  await editor.locator('.el-table__body-wrapper tr').nth(1).waitFor()
  await page.setViewportSize({ width: 600, height: 800 })
  await editor.scrollIntoViewIfNeeded()
  assert.ok((await footer.boundingBox()).y + (await footer.boundingBox()).height <= 800, 'Narrow footer must fit the viewport')
  await dialog.screenshot({ path: screenshot.replace('.png', '-narrow.png'), mask: [editor.locator('.el-select__selected-item')] })
  assert.equal(pageErrors, 0)
  console.log(JSON.stringify({ step: 'assignment-workspace-saved', taskId: activeTaskId, processInstanceId: task.processInstanceId, operations: changes.map(change => change.operation), pageErrors, screenshot, tasksUnchanged: true }))
} finally { await browser.close() }
