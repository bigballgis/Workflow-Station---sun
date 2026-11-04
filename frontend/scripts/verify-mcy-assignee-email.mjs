/** Verify the actual, user-authorized FU50125 configuration on a new test instance. */
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { chromium } from 'playwright'
import { loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'
import { readBatchStartContext } from './batch-start-context.mjs'
import { assigneeEmailScript } from './fixtures/assignee-email-script.mjs'
const mode = process.argv[2], origin = 'http://localhost:3000'
assert.ok(['prepare', 'ui'].includes(mode))
const statePath = new URL('../../.codex-mcy-email-verification-state.json', import.meta.url)
const code = 'mcy-dual-binding-1101-179349950603-20261101-5srx1c'
const browser = await chromium.launch(), page = await browser.newPage({ viewport: { width: 1750, height: 1200 } })
page.setDefaultTimeout(25000)
const errors = []; page.on('pageerror', error => errors.push(error.message))
page.on('response', response => { if (/\/tasks\/[^/]+\/submit$/.test(response.url())) console.log(JSON.stringify({ step: 'task-save-response', status: response.status() })) })
const request = async (prefix, method, path, data) => {
  const response = await page.request.fetch(origin + prefix + path, { method, data }), body = await response.json()
  assert.ok(response.ok() && body.success !== false, body.error?.message || body.message || String(response.status()))
  return body.data ?? body
}
const portal = (method, path, data) => request('/api/portal', method, path, data)
try {
  if (mode === 'prepare') {
    await loginViaAdminPassword(page)
    const catalog = await request('/api/v1', 'GET', '/admin/function-units/by-process-key/' + code)
    await loginViaPortalPassword(page, { roleCode: 'MANAGER' })
    const oldTask = '1163b550-d826-11f1-a1af-aeb2e07f8cc8'
    const baseline = await portal('GET', '/tasks/' + oldTask + '/form-data')
    const store = 'dw:' + Object.values(baseline.subTableBatchCapabilities)[0].rowDesign.binding.tableName.toLowerCase()
    assert.equal(store, 'dw:hmdc_transaction_copy5')
    const caseId = randomUUID()
    const rows = Array.from({ length: 3 }, (_, index) => ({ row_id: randomUUID(), case_id: caseId,
      card_number: 'EMAIL-QA-CARD-' + (index + 1), merchant_name: 'EMAIL-QA-MERCHANT-' + (index + 1) }))
    const started = await portal('POST', '/processes/' + code + '/start', {
      batchStartContext: await readBatchStartContext(page, catalog.id),
      formData: { case_number: caseId, legal_hold: false, __subTables__: { [store]: rows } },
      remark: 'Assignee email configuration verification only',
    })
    const processId = started.processInstanceId ?? started.id
    const tasks = async () => {
      const response = await page.request.get('http://localhost:8081/api/v1/tasks', { params: { processInstanceId: processId, page: 0, size: 100 } })
      const body = await response.json(); assert.ok(response.ok()); return (body.data ?? body).tasks
    }
    const first = (await tasks())[0]
    // Portal start can consume the initiator's Case Submission as the start action.
    if ((first.taskDefinitionKey ?? first.taskDefinitionId) === 'Activity_092hlui') {
      await portal('POST', '/tasks/' + (first.id ?? first.taskId) + '/complete', {
        taskId: first.id ?? first.taskId, action: 'APPROVE', actionId: '50524', comment: 'Test case submission for assignee email verification',
      })
    }
    const assignment = (await tasks()).find(task => (task.taskDefinitionKey ?? task.taskDefinitionId) === 'Activity_0ayvpi1')
    assert.ok(assignment)
    const state = { unitId: 50125, processId, taskId: assignment.id ?? assignment.taskId, store, caseId }
    const frozen = await portal('GET', '/tasks/' + state.taskId + '/form-data')
    const capability = Object.values(frozen.subTableBatchCapabilities)[0]
    assert.ok(capability.rowDesign.fields.some(field => field.fieldName === 'assignee_email'))
    assert.equal(capability.batchForm.rule[0].children.find(rule => rule.field === 'assignee_id').on.change, assigneeEmailScript)
    writeFileSync(statePath, JSON.stringify(state, null, 2))
    assert.deepEqual((await portal('GET', '/tasks/' + oldTask + '/form-data')).configJson, baseline.configJson)
    console.log(JSON.stringify({ step: 'new-source-fu-instance-ready', ...state, oldFrozenConfigurationUnchanged: true }))
  } else {
    await loginViaPortalPassword(page, { roleCode: 'MANAGER' })
    const state = JSON.parse(readFileSync(statePath, 'utf8'))
    const baseline = await portal('GET', '/tasks/' + state.taskId + '/form-data')
    await page.goto(origin + '/portal/tasks/' + state.taskId)
    const grid = page.locator('.sub-table-field').first()
    await grid.locator('.el-table__body-wrapper tbody tr.el-table__row').nth(2).waitFor()
    const claim = page.getByRole('button', { name: 'Claim', exact: true })
    if (await claim.count()) { await claim.click(); await page.waitForLoadState('networkidle') }
    for (let index = 0; index < 2; index++) await grid.locator('.el-table__body-wrapper tbody tr.el-table__row').nth(index).locator('.el-checkbox').click()
    await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
    const dialog = page.locator('.batch-edit-dialog:visible')
    await dialog.getByText('Assign by person', { exact: true }).click()
    const choose = async root => {
      await root.locator('.lookup-field input').fill('developer')
      const row = page.locator('.lookup-dropdown:visible .el-table__body-wrapper tbody tr.el-table__row').filter({ hasText: /developer/i }).first()
      await row.waitFor(); await row.click()
    }
    const email = root => root.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^Assignee Email$/ }) }).locator('input')
    await choose(dialog)
    const user = await request('/api/v1', 'GET', '/admin/users/user-dev')
    await page.waitForFunction(expected => [...document.querySelectorAll('.batch-edit-dialog input')].some(input => input.value === expected), user.email)
    assert.equal(await email(dialog).inputValue(), user.email)
    assert.equal(await dialog.locator('.el-alert--error').count(), 0)
    mkdirSync('user-portal/verification-screenshots', { recursive: true })
    await dialog.screenshot({ path: 'user-portal/verification-screenshots/2026-11-04-mcy-actual-email-batch.png' })
    const identity = value => value && typeof value === 'object' ? value.id : value
    const needsSave = baseline.fieldValues.__subTables__[state.store].slice(0, 2).some(row => identity(row.assignee_id) !== 'user-dev' || row.assignee_email !== user.email)
    const saved = needsSave ? page.waitForResponse(response => response.url().endsWith('/tasks/' + state.taskId + '/submit') && response.request().method() === 'POST') : null
    await dialog.getByRole('button', { name: /Apply/ }).click(); await dialog.waitFor({ state: 'hidden' })
    if (saved) assert.equal((await (await saved).json()).success, true)
    const actual = await portal('GET', '/tasks/' + state.taskId + '/form-data'), rows = actual.fieldValues.__subTables__[state.store]
    for (let index = 0; index < 2; index++) { assert.equal(rows[index].assignee_email, user.email); assert.equal(identity(rows[index].assignee_id), 'user-dev') }
    assert.ok(!rows[2].assignee_email); assert.ok(!rows[2].assignee_id)
    rows.forEach((row, index) => assert.equal(row.merchant_name, 'EMAIL-QA-MERCHANT-' + (index + 1)))
    console.log(JSON.stringify({ step: 'batch-email-save-refresh-passed', needsSave, selectedRows: 2, merchantValuesPreserved: true }))
    await page.reload(); await grid.locator('.el-table__body-wrapper tbody tr.el-table__row').nth(2).waitFor()
    await grid.locator('.el-table__body-wrapper tbody tr.el-table__row').first().getByRole('button', { name: 'Edit', exact: true }).click()
    const ordinary = page.locator('.el-dialog:visible').last()
    await ordinary.locator('.lookup-selected-close').first().click()
    assert.equal(await email(ordinary).inputValue(), '')
    await choose(ordinary)
    await page.waitForFunction(expected => [...document.querySelectorAll('.el-dialog input')].some(input => input.value === expected), user.email)
    assert.equal(await email(ordinary).inputValue(), user.email)
    await ordinary.screenshot({ path: 'user-portal/verification-screenshots/2026-11-04-mcy-actual-email-ordinary.png' })
    await ordinary.getByRole('button', { name: 'Cancel', exact: true }).click()
    const businessValues = value => JSON.parse(JSON.stringify(value, (key, item) =>
      ['_wsRowVersion', 'updated_at', 'updated_by'].includes(key) ? undefined : item))
    assert.deepEqual(businessValues((await portal('GET', '/tasks/' + state.taskId + '/form-data')).fieldValues), businessValues(actual.fieldValues))
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ step: 'source-fu-browser-passed', ...state, selectedRows: 2, unselectedRows: 1, merchantValuesPreserved: true, ordinaryClearAndBackfill: true, pageErrors: errors.length }))
  }
} catch (error) {
  mkdirSync('user-portal/verification-screenshots', { recursive: true })
  await page.screenshot({ path: `user-portal/verification-screenshots/2026-11-04-mcy-email-configuration-failure-${Date.now()}.png`, fullPage: true }).catch(() => {})
  console.log('ERROR-UI', await page.locator('.el-alert,.el-message').allTextContents())
  throw error
} finally { await browser.close() }
