/** Verifies completion only on a pinned, already emptied synthetic batch fixture. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const taskId = process.env.FIXTURE_TASK_ID
if (!/^[a-f0-9-]{36}$/.test(taskId || '')) throw new Error('Pinned FIXTURE_TASK_ID required')
const origin = 'http://localhost:3101'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  await loginViaPortalPassword(page, { loginOrigin: origin })
  const api = async (method, path, data) => {
    const response = await page.request.fetch(`${origin}/api/portal${path}`, { method, data })
    const body = await response.json()
    assert.equal(response.ok() && body.success, true, `${method} ${path} must succeed`)
    return body.data
  }
  const task = await api('GET', `/tasks/${taskId}`)
  assert.match(task.functionUnitName, /^Batch verification /)
  const form = await api('GET', `/tasks/${taskId}/form-data`)
  assert.equal(form.formName, 'Synthetic TASK')
  const controls = Object.values(form.subTableBatchCapabilities || {})
  assert.equal(controls.length, 1)
  const store = `dw:${controls[0].rowDesign.binding.tableName}`
  assert.deepEqual(form.fieldValues.__subTables__[store], [], 'Fixture must already have a validated saved delete-all')
  await api('POST', `/tasks/${taskId}/complete`, { taskId, action: 'APPROVE', formData: form.fieldValues,
    comment: 'Synthetic delete-all completion regression' })
  const completed = await api('GET', `/processes/${task.processInstanceId}`)
  assert.equal(completed.status, 'COMPLETED')
  assert.deepEqual(completed.variables.__subTables__[store], [], 'Completion must not resurrect deleted engine rows')
  for (const key of ['__subTableBatchRuntime__', '__subTableBatchDeletedRows__']) {
    assert.equal(Object.hasOwn(completed.variables, key), false, 'Internal metadata must not be serialized')
  }
  console.log('PASS: synthetic delete-all survives real Complete and response metadata remains private')
} finally {
  await browser.close()
}
