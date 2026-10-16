/** Fresh synthetic Review only. No directory writes, workspace switching, task repair or reassignment. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId || '', /^[a-f0-9-]{36}$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  await loginViaPortalPassword(page)
  const read = async path => {
    const response = await page.request.get(`http://localhost:3000/api/portal${path}`)
    const body = await response.json()
    assert.equal(response.ok() && body.success, true)
    return body.data
  }
  const task = await read(`/tasks/${taskId}`)
  assert.match(task.functionUnitName, /^Batch verification /)
  assert.match(task.processDefinitionKey, /^batch_verify_[a-z0-9]+$/)
  assert.equal(task.taskDefinitionKey, 'Review')
  const before = await read(`/tasks/${taskId}/form-data`)
  const [control] = Object.values(before.subTableBatchCapabilities)
  assert.equal(control.capabilities.allowBatchAssign && control.assignmentConfig.allowRole, true)
  assert.ok(control.assignmentConfig.buField, 'This fixture explicitly declares a BU field')
  const store = `dw:${control.rowDesign.binding.tableName}`
  const payload = structuredClone(before.fieldValues)
  const rows = payload.__subTables__[store]
  assert.equal(rows.length, 2)
  for (const row of rows) {
    assert.ok(!row[control.assignmentConfig.assigneeField] && !row[control.assignmentConfig.roleField], 'Fresh unassigned rows required')
    row[control.assignmentConfig.roleField] = 'SYNTHETIC-NONEXISTENT-ROLE'
    row[control.assignmentConfig.buField] = 'SYNTHETIC-NONEXISTENT-BU'
  }
  const scopes = [{ bindingId: String(control.bindingId), storeKey: store, emptied: false,
    rowKeys: rows.map(row => Object.fromEntries(control.rowDesign.binding.primaryKeyFields.map(key => [key, row[key]]))) }]
  for (const action of ['submit', 'complete']) {
    const response = await page.request.post(`http://localhost:3000/api/portal/tasks/${taskId}/${action}`, {
      data: { taskId, action: 'APPROVE', formData: payload, subTableBindingScopes: scopes },
    })
    const body = await response.json()
    assert.equal(body.success, false)
    assert.match(body.error?.message || body.message, action === 'submit'
      ? /role target is not currently eligible/ : /Instance MI collection source cannot be verified/)
    assert.deepEqual((await read(`/tasks/${taskId}/form-data`)).fieldValues, before.fieldValues)
    assert.equal((await read(`/tasks/${taskId}`)).taskDefinitionKey, 'Review')
  }
  console.log(JSON.stringify({ step: 'synthetic-role-rejected-at-save-and-complete', taskId, processInstanceId: task.processInstanceId }))
} finally { await browser.close() }
