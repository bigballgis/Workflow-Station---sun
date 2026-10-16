/** Direct synthetic Intake→MI only: reject invalid targets; rebuild a spoofed collection from valid canonical rows. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'

const processKey = process.env.FIXTURE_PROCESS_KEY
assert.match(processKey || '', /^batch_verify_[a-z0-9]+$/)
const browser = await chromium.launch({ headless: true })
async function request(page, method, url, data) {
  const response = await page.request.fetch(url, { method, data })
  assert.equal(response.ok(), true, `${method} ${url} HTTP ${response.status()}`)
  const body = await response.json()
  if (body && typeof body === 'object' && 'success' in body) assert.equal(body.success, true)
  return body && typeof body === 'object' && 'data' in body ? body.data : body
}
try {
  const page = await browser.newPage()
  await loginViaPortalPassword(page)
  const roleId = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')).activeRoleId)
  assert.ok(roleId)
  await loginViaAdminPassword(page)
  const catalog = await request(page, 'GET', `http://localhost:3000/api/v1/admin/function-units/by-process-key/${processKey}`)
  assert.match(catalog.name, /^Batch verification /)
  const accessUrl = `http://localhost:3000/api/v1/admin/function-units/${catalog.id}/access`
  const access = await request(page, 'GET', accessUrl)
  if (!access.some(item => item.roleId === roleId)) await request(page, 'POST', accessUrl, { roleId })
  await loginViaPortalPassword(page)
  const api = (method, path, data) => request(page, method, `http://localhost:3000/api/portal${path}`, data)
  const users = await api('GET', '/tasks/users/search?keyword=developer')
  const person = users.find(user => user.username === 'developer')
  assert.ok(person?.id)
  const store = `dw:batch_lines_${processKey.slice('batch_verify_'.length)}`
  const start = target => api('POST', `/processes/${encodeURIComponent(catalog.code)}/start`, { formData: {
    case_key: 'SYNTHETIC-FIRST-CASE', __subTables__: { [store]: [
      { line_key: 'SYNTHETIC-ONE', case_ref: 'SYNTHETIC-FIRST-CASE', caption: 'First synthetic line', amount: 1, handler_code: target },
      { line_key: 'SYNTHETIC-TWO', case_ref: 'SYNTHETIC-FIRST-CASE', caption: 'Second synthetic line', amount: 2, handler_code: target },
    ] }, syntheticAssignmentRows: [{ handler_code: 'SYNTHETIC-MUST-NOT-BE-CONSUMED' }],
  }, remark: 'Synthetic direct first-MI verification' })
  const tasksOf = id => request(page, 'GET', `http://localhost:8081/api/v1/tasks?processInstanceId=${encodeURIComponent(id)}&page=0&size=100`)
  const rejected = await start('SYNTHETIC-NONEXISTENT-PERSON')
  assert.equal(rejected.firstStepError, 'FIRST_STEP_NOT_COMPLETED')
  const rejectedId = rejected.processInstanceId || rejected.id
  const held = (await tasksOf(rejectedId)).tasks
  assert.equal(held.length, 1); assert.equal(held[0].taskDefinitionKey, 'Intake')
  const heldForm = await api('GET', `/tasks/${held[0].taskId}/form-data`)
  assert.equal(heldForm.fieldValues.__subTables__[store].length, 2)
  const started = await start(person.id)
  assert.ok(!started.firstStepError)
  const instanceId = started.processInstanceId || started.id
  const children = (await tasksOf(instanceId)).tasks
  assert.equal(children.length, 2, 'Canonical frozen rows must replace the single spoofed collection entry')
  const identities = new Set()
  for (const child of children) {
    assert.equal(child.taskDefinitionKey, 'AssignedWork')
    const detail = await request(page, 'GET', `http://localhost:8081/api/v1/tasks/${child.taskId}`)
    assert.equal(detail.currentAssignee === person.id, true, 'Actual task must use the active directory target')
    assert.ok(!detail.candidateUserIds?.length)
    assert.equal('__subTableBatchRuntime__' in detail.variables, false, 'Local capability snapshot must not enter Flowable')
    assert.ok(['SYNTHETIC-ONE', 'SYNTHETIC-TWO'].includes(detail.variables._currentItem.rowKey.line_key))
    identities.add(detail.variables._currentItem.rowKey.line_key)
  }
  assert.equal(identities.size, 2)
  console.log(JSON.stringify({ step: 'synthetic-direct-first-mi', rejectedInstanceId: rejectedId, heldFirstTaskId: held[0].taskId,
    instanceId, taskIds: children.map(child => child.taskId), identities: identities.size, snapshotInEngine: false }))
} finally { await browser.close() }
