/** Fresh synthetic host only: fabricated people must fail both ordinary Save and Complete without any batch marker. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
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
  const form = await read(`/tasks/${taskId}/form-data`)
  const [control] = Object.values(form.subTableBatchCapabilities)
  assert.equal(control.capabilities.allowBatchAssign, true)
  const store = `dw:${control.rowDesign.binding.tableName}`
  const payload = structuredClone(form.fieldValues)
  const rows = payload.__subTables__[store]
  assert.equal(rows.length, 2)
  for (const row of rows) {
    assert.ok(!row[control.assignmentConfig.assigneeField] && !row[control.assignmentConfig.roleField], 'Fresh unassigned synthetic rows required')
    row[control.assignmentConfig.assigneeField] = 'SYNTHETIC-NONEXISTENT-PERSON'
  }
  const scopes = [{ bindingId: String(control.bindingId), storeKey: store, emptied: false,
    rowKeys: rows.map(row => Object.fromEntries(control.rowDesign.binding.primaryKeyFields.map(key => [key, row[key]]))) }]
  for (const action of ['submit', 'complete']) {
    const response = await page.request.post(`http://localhost:3000/api/portal/tasks/${taskId}/${action}`, { data: {
      taskId, action: 'APPROVE', formData: payload, subTableBindingScopes: scopes,
    } })
    const body = await response.json()
    assert.equal(body.success, false, `Fabricated person must not pass ${action}`)
    assert.match(body.error?.message || body.message, action === 'submit'
      ? /person target is not currently active/ : /Instance MI collection source cannot be verified/)
    assert.deepEqual((await read(`/tasks/${taskId}/form-data`)).fieldValues.__subTables__, form.fieldValues.__subTables__)
    assert.equal((await read(`/tasks/${taskId}`)).taskDefinitionKey, 'Review')
  }
  console.log(JSON.stringify({ step: 'synthetic-person-rejected-at-save-and-complete', processInstanceId: task.processInstanceId, taskId }))
} finally { await browser.close() }

execFileSync(process.execPath, [fileURLToPath(new URL('./verify-batch-mi-frozen-runtime.mjs', import.meta.url))], {
  env: { ...process.env, FIXTURE_TASK_ID: taskId, FIXTURE_SCREENSHOT_TAG: 'person-eligibility' }, stdio: 'inherit', timeout: 180000,
})
