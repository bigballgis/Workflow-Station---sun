/** Same physical table, two FK bindings; synthetic clone only, never modifies the source FU. */
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'
import { readBatchStartContext } from './batch-start-context.mjs'
import { unwrapApiList } from './mi-regression-helpers.mjs'

const before = process.env.FIXTURE_EXPECT_FAILURE === '1'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1100 } })
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  const request = async (prefix, method, path, data) => {
    const response = await page.request.fetch(`http://localhost:3000${prefix}${path}`, { method, data })
    const body = await response.json()
    assert.ok(response.ok() && body.success !== false, `${method} ${path}: ${body.message || body.error?.message}`)
    return body.data ?? body
  }
  const dw = (method, path, data) => request('/api/v1', method, path, data)
  const admin = (method, path, data) => request('/api/v1/admin', method, path, data)
  const portal = (method, path, data) => request('/api/portal', method, path, data)
  let taskId = process.env.FIXTURE_TASK_ID
  if (!taskId) {
    await loginViaDwPassword(page)
    assert.ok((await dw('GET', '/function-units/50022')).tags.includes('batch-verification-synthetic'))
    const clone = process.env.FIXTURE_UNIT_ID ? await dw('GET', `/function-units/${process.env.FIXTURE_UNIT_ID}`)
      : await dw('POST', `/function-units/50022/clone?newName=${encodeURIComponent(`Batch verification alternatefk${Date.now()}`)}`)
    assert.match(clone.name, /^Batch verification alternatefk/)
    console.log(JSON.stringify({ step: 'cloned', unitId: clone.id, code: clone.code }))
    const base = `/function-units/${clone.id}`
    const table = (await dw('GET', `${base}/tables`)).find(table => table.tableType === 'SUB')
    assert.ok(table.fieldDefinitions.some(field => field.fieldName === 'case_ref' && field.nullable === false && field.isForeignKey))
    assert.ok(table.fieldDefinitions.some(field => field.fieldName === 'case_ref2' && field.isForeignKey))
    const processForm = (await dw('GET', `${base}/forms`)).find(form => form.formType === 'PROCESS')
    const bindings = await dw('GET', `${base}/forms/${processForm.id}/bindings`)
    const deploy = await dw('POST', `${base}/deploy`, { autoEnable: true, changeLog: 'Synthetic alternate-FK batch regression' })
    await waitForFixtureDeployment(dw, deploy.deploymentId)
    await loginViaPortalPassword(page)
    const roleId = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')).activeRoleId)
    await loginViaAdminPassword(page)
    const catalog = await admin('GET', `/function-units/by-process-key/${encodeURIComponent(clone.code)}`)
    const access = await admin('GET', `/function-units/${catalog.id}/access`)
    if (!access.some(entry => entry.roleId === roleId)) await admin('POST', `/function-units/${catalog.id}/access`, { roleId })
    await loginViaPortalPassword(page)
    const initialRows = [
      { line_key: randomUUID(), case_ref: 'SYNTHETIC-FK-CASE', caption: 'Keep primary one', amount: 1 },
      { line_key: randomUUID(), case_ref: 'SYNTHETIC-FK-CASE', caption: 'Keep primary two', amount: 2 },
    ]
    const started = await portal('POST', `/processes/${clone.code}/start`, {
      batchStartContext: await readBatchStartContext(page, catalog.id),
      subTableBindingScopes: bindings.filter(binding => binding.bindingType === 'SUB').map(binding => ({
        bindingId: String(binding.id), storeKey: `dw:${table.tableName}`, emptied: false,
        rowKeys: binding.foreignKeyField === 'case_ref' ? initialRows.map(row => ({ line_key: row.line_key })) : [],
      })),
      formData: { case_key: 'SYNTHETIC-FK-CASE', __subTables__: { [`dw:${table.tableName}`]: initialRows } },
      remark: 'Synthetic alternate FK regression',
    })
    const instance = started.processInstanceId || started.id
    const tasks = unwrapApiList(await portal('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] }))
      .filter(task => task.processInstanceId === instance)
    assert.equal(tasks.length, 1)
    taskId = tasks[0].taskId
    console.log(JSON.stringify({ step: 'task-created', unitId: clone.id, taskId }))
  }
  assert.match(taskId, /^[a-f0-9-]{36}$/)
  await loginViaPortalPassword(page, { loginOrigin: 'http://localhost:3101' })
  const task = await portal('GET', `/tasks/${taskId}`)
  assert.match(task.functionUnitName, /^Batch verification alternatefk/)
  const read = () => portal('GET', `/tasks/${taskId}/form-data`)
  const baseline = await read()
  const control = Object.values(baseline.subTableBatchCapabilities).find(control => control.rowDesign.binding.foreignKeyField === 'case_ref2')
  assert.ok(control)
  const store = `dw:${control.rowDesign.binding.tableName}`
  const rowsOf = data => data.fieldValues.__subTables__[store]
  const original = rowsOf(baseline)
  const primary = original.filter(row => row.case_ref)
  await page.goto(`http://localhost:3101/portal/tasks/${taskId}`)
  await page.getByRole('button', { name: 'Batch Edit', exact: true }).last().click()
  let dialog = page.locator('.batch-edit-dialog:visible')
  await dialog.getByRole('button', { name: 'Add', exact: true }).click()
  const added = dialog.locator('.el-table__body-wrapper tr').last()
  const cells = added.locator('input:not([type="checkbox"])')
  assert.equal(await cells.count(), 2)
  await cells.nth(0).fill('Alternate new')
  await cells.nth(1).fill('8')
  await cells.nth(1).blur()
  const saveResponse = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
  const save = await saveResponse
  const body = await save.json()
  const shot = tag => fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-19_batch-alternate-fk-${tag}.png`, import.meta.url))
  if (before) {
    assert.equal(save.ok() && body.success === true, false)
    assert.match(JSON.stringify(body), /Batch row requires field case_ref/)
    assert.deepEqual(rowsOf(await read()), original)
    await page.locator('.el-message--error').filter({ hasText: 'case_ref' }).first().screenshot({ path: shot('before-message') })
    console.log(JSON.stringify({ step: 'reproduced', taskId, status: save.status(), message: body.message || body.error?.message }))
  } else {
    assert.ok(save.ok() && body.success, JSON.stringify(body))
    const afterAdd = rowsOf(await read())
    const newRow = afterAdd.find(row => !original.some(old => old.line_key === row.line_key))
    assert.ok(newRow && !newRow.case_ref && newRow.case_ref2 === 'SYNTHETIC-FK-CASE')
    assert.equal(newRow.caption, 'Alternate new')
    await page.getByRole('button', { name: 'Batch Edit', exact: true }).last().click()
    dialog = page.locator('.batch-edit-dialog:visible')
    const editable = dialog.locator('.el-table__body-wrapper tr').last().locator('input:not([type="checkbox"])')
    await editable.nth(0).fill('Alternate edited')
    await editable.nth(1).fill('999')
    await editable.nth(1).blur()
    const edited = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
    await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
    assert.equal((await (await edited).json()).success, true)
    const saved = await read()
    assert.deepEqual(rowsOf(saved).filter(row => row.case_ref).map(({ _wsRowVersion, ...row }) => row), primary.map(({ _wsRowVersion, ...row }) => row))
    const alternate = rowsOf(saved).find(row => row.line_key === newRow.line_key)
    assert.equal(alternate.caption, 'Alternate edited')
    assert.equal(alternate.amount, 999)
    assert.ok(!alternate.case_ref && alternate.case_ref2 === 'SYNTHETIC-FK-CASE')
    await page.screenshot({ path: shot('after'), fullPage: true, mask: [page.locator('.header-right')] })
    assert.deepEqual(pageErrors, [])
    console.log(JSON.stringify({ step: 'verified-add-edit', taskId, rowId: newRow.line_key, primaryRowsUnchanged: primary.length, pageErrors }))
  }
} finally { await browser.close() }
