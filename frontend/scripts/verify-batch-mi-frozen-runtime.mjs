/** Synthetic Review only: missing targets must block Complete, then normal workspace/Complete must still work. */
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
  const api = async path => {
    const response = await page.request.get(`http://localhost:3000/api/portal${path}`)
    const body = await response.json()
    assert.equal(response.ok() && body.success, true, `GET ${path} HTTP ${response.status()}`)
    return body.data
  }
  const task = await api(`/tasks/${taskId}`)
  assert.match(task.functionUnitName, /^Batch verification /)
  assert.match(task.processDefinitionKey, /^batch_verify_[a-z0-9]+$/)
  assert.equal(task.taskDefinitionKey, 'Review')
  const form = await api(`/tasks/${taskId}/form-data`)
  const [control] = Object.values(form.subTableBatchCapabilities)
  assert.equal(control.capabilities.allowBatchAssign, true)
  const store = `dw:${control.rowDesign.binding.tableName}`
  const rows = form.fieldValues.__subTables__[store]
  const config = control.assignmentConfig
  assert.equal(rows.length, 2)
  assert.ok(rows.every(row => !row[config.assigneeField] && !row[config.roleField]), 'Use a fresh unassigned synthetic fixture')
  const response = await page.request.post(`http://localhost:3000/api/portal/tasks/${taskId}/complete`, { data: {
    taskId, action: 'APPROVE', formData: form.fieldValues,
    subTableBindingScopes: [{ bindingId: String(control.bindingId), storeKey: store, emptied: false,
      rowKeys: rows.map(row => Object.fromEntries(control.rowDesign.binding.primaryKeyFields.map(key => [key, row[key]]))) }],
    comment: 'Synthetic missing-target rejection verification',
  } })
  const body = await response.json()
  assert.equal(body.success, false)
  assert.match(body.error?.message || body.message, /Instance MI collection source cannot be verified/)
  assert.equal((await api(`/tasks/${taskId}`)).taskDefinitionKey, 'Review')
  assert.deepEqual((await api(`/tasks/${taskId}/form-data`)).fieldValues.__subTables__, form.fieldValues.__subTables__)
  console.log(JSON.stringify({ step: 'frozen-mi-missing-target-rejected', processInstanceId: task.processInstanceId, taskId }))
} finally { await browser.close() }

for (const script of ['verify-batch-assignment-workspace.mjs', 'verify-batch-assignment-stage-runtime.mjs']) {
  execFileSync(process.execPath, [fileURLToPath(new URL(`./${script}`, import.meta.url))], {
    env: { ...process.env, FIXTURE_TASK_ID: taskId, FIXTURE_VERIFY_GENERATED: '0', FIXTURE_SCREENSHOT_TAG: 'frozen-mi' },
    stdio: 'inherit', timeout: 120000,
  })
}
