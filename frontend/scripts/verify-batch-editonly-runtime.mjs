/** Pinned synthetic Edit-only acceptance; never selects a real business task. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import { unwrapApiList } from './mi-regression-helpers.mjs'

const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId || '', /^[a-f0-9-]{36}$/)
const apiOrigin = 'http://localhost:3000'
const uiOrigin = 'http://localhost:3101'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  let pageErrors = 0
  page.on('pageerror', () => pageErrors++)
  await loginViaPortalPassword(page)
  const api = async (method, path, data) => {
    const response = await page.request.fetch(`${apiOrigin}/api/portal${path}`, { method, data })
    const body = await response.json()
    assert.equal(response.ok() && body.success, true, `${method} ${path} must succeed`)
    return body.data
  }
  const task = await api('GET', `/tasks/${taskId}`)
  assert.match(task.functionUnitName, /^Batch verification /)
  let activeTaskId = taskId
  let form = await api('GET', `/tasks/${activeTaskId}/form-data`)
  if (form.formName === 'Synthetic PROCESS') {
    await api('POST', `/tasks/${activeTaskId}/complete`, { taskId: activeTaskId, action: 'APPROVE', formData: form.fieldValues,
      comment: 'Synthetic Edit-only intake' })
    const tasks = unwrapApiList(await api('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] }))
      .filter(row => row.processInstanceId === task.processInstanceId)
    assert.equal(tasks.length, 1)
    activeTaskId = tasks[0].taskId
    form = await api('GET', `/tasks/${activeTaskId}/form-data`)
  }
  assert.equal(form.formName, 'Synthetic TASK')
  const entries = Object.entries(form.subTableBatchCapabilities)
  assert.equal(entries.length, 1)
  const [controlId, control] = entries[0]
  assert.match(controlId, /^batch-[a-z0-9]+-TASK$/)
  assert.deepEqual(control.capabilities, { allowBatchAdd: false, allowBatchEdit: true, allowBatchDelete: false, allowBatchAssign: false })
  const binding = control.rowDesign.binding
  const textField = control.batchForm.rule.find(rule => rule.type === 'input' && !binding.primaryKeyFields.includes(rule.field)).field
  const numberField = control.batchForm.rule.find(rule => rule.type === 'inputNumber').field
  assert.equal(control.rowDesign.fieldWriteModes[numberField], 'READONLY')
  const store = `dw:${binding.tableName}`
  const rowsOf = data => data.fieldValues.__subTables__[store]
  assert.equal(rowsOf(form).length, 2)
  assert.equal(rowsOf(form).every(row => row._wsRowVersion === 1), true)
  await page.goto(`${uiOrigin}/portal/tasks/${activeTaskId}`)
  await page.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  const dialog = page.locator('.el-dialog:visible')
  await dialog.locator('.el-table__body-wrapper tr').nth(1).waitFor()
  assert.equal(await dialog.getByRole('button', { name: 'Add', exact: true }).count(), 0)
  assert.equal(await dialog.getByRole('button', { name: 'Delete', exact: true }).count(), 0)
  const firstRow = dialog.locator('.el-table__body-wrapper tr').first()
  const firstInput = firstRow.locator('input:not([type="checkbox"])')
  assert.equal(await firstInput.count(), 1, 'Only the ordinary writable field is editable')
  await firstInput.fill('Edited synthetic line')
  assert.deepEqual(rowsOf(await api('GET', `/tasks/${activeTaskId}/form-data`)), rowsOf(form), 'Draft must not persist')
  const save = page.waitForResponse(response => response.url().endsWith(`/tasks/${activeTaskId}/submit`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: /Apply/ }).click()
  const response = await save
  assert.equal((await response.json()).success, true)
  const saved = await api('GET', `/tasks/${activeTaskId}/form-data`)
  assert.equal(rowsOf(saved)[0][textField], 'Edited synthetic line')
  // Existing binding-scoped saves claim the displayed rows and advance each claimed version once.
  assert.deepEqual(rowsOf(saved)[1], { ...rowsOf(form)[1], _wsRowVersion: rowsOf(form)[1]._wsRowVersion + 1 },
    'Unselected sibling business values and identity remain identical; only its claimed version advances')
  assert.equal(rowsOf(saved)[0][numberField], rowsOf(form)[0][numberField])
  const forged = structuredClone(response.request().postDataJSON())
  delete forged.subTableBatchChanges
  delete forged.subTableBindingScopes
  delete forged.subTableData
  forged.formData = structuredClone(saved.fieldValues)
  forged.formData.__subTables__[store][0][numberField] = 99
  const denied = await page.request.post(`${apiOrigin}/api/portal/tasks/${activeTaskId}/submit`, { data: forged })
  const deniedBody = await denied.json()
  assert.equal(deniedBody.success, false)
  assert.equal(String(deniedBody.error?.code), '403', 'Omitting batch intent must not bypass current readonly authority')
  assert.deepEqual(rowsOf(await api('GET', `/tasks/${activeTaskId}/form-data`)), rowsOf(saved))
  await page.reload()
  await page.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  await dialog.locator('.el-table__body-wrapper tr').nth(1).waitFor()
  assert.equal(await dialog.locator('.el-table__body-wrapper tr').first().locator('input:not([type="checkbox"])').inputValue(), 'Edited synthetic line')
  for (const [suffix, width, height] of [['desktop', 1366, 768], ['narrow', 480, 800]]) {
    await page.setViewportSize({ width, height })
    await page.evaluate(async () => {
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
      await Promise.all(document.getAnimations().filter(animation => animation.effect?.getTiming().iterations !== Infinity)
        .map(animation => animation.finished))
    })
    const path = new URL(`../user-portal/verification-screenshots/${new Date().toISOString().slice(0, 10)}_batch-editonly-${suffix}.png`, import.meta.url)
    await page.screenshot({ path: fileURLToPath(path) })
    console.log(fileURLToPath(path))
  }
  assert.equal(pageErrors, 0)
  console.log(JSON.stringify({ result: 'PASS', activeTaskId, pageErrors, checks: 'Edit-only frozen fields, versions, draft, persistence, sibling isolation, readonly omission rejection' }))
} finally {
  await browser.close()
}
