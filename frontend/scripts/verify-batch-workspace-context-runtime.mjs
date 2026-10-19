/** Synthetic-only role-context authority check; never switches a workspace or changes directory membership. */
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
  const before = await read(`/tasks/${taskId}/form-data`)
  for (const key of ['activeBusinessUnitId', 'activeRoleId']) {
    for (const action of ['submit', 'complete']) {
      const response = await page.request.post(`http://localhost:3000/api/portal/tasks/${taskId}/${action}`, { data: {
        taskId, action: 'APPROVE', formData: { [key]: 'SYNTHETIC-MUST-NOT-REPLACE-CONTEXT' },
      } })
      const body = await response.json()
      assert.equal(body.success, false)
      assert.match(body.error?.message || body.message, /workspace context cannot be changed by form data/)
      assert.deepEqual((await read(`/tasks/${taskId}/form-data`)).fieldValues, before.fieldValues)
      assert.equal((await read(`/tasks/${taskId}`)).taskDefinitionKey, 'Review')
    }
  }
  console.log(JSON.stringify({ step: 'synthetic-workspace-forgery-rejected', processInstanceId: task.processInstanceId,
    taskId, rejectedRequests: 4 }))
} finally { await browser.close() }

execFileSync(process.execPath, [fileURLToPath(new URL('./verify-batch-person-eligibility-runtime.mjs', import.meta.url))], {
  env: { ...process.env, FIXTURE_TASK_ID: taskId }, stdio: 'inherit', timeout: 240000,
})
