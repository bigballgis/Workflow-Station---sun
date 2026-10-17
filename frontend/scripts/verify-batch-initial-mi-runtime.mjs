/** Initial Start→MI synthetic fixture only. Invalid targets must fail before any engine instance exists. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { chromium } from 'playwright'
import { loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'

const processKey = process.env.FIXTURE_PROCESS_KEY
assert.match(processKey || '', /^batch_verify_[a-z0-9]+$/)
const businessKey = `SYNTHETIC-INITIAL-MI-${Date.now()}`
const browser = await chromium.launch({ headless: true })
async function request(page, method, url, data) {
  const response = await page.request.fetch(url, { method, data })
  assert.ok(response.ok(), `Synthetic ${method} HTTP ${response.status()}`)
  const body = await response.json()
  if ('success' in body) assert.equal(body.success, true)
  return 'data' in body ? body.data : body
}
function rejectedInstanceCount() {
  assert.match(businessKey, /^SYNTHETIC-INITIAL-MI-\d+$/)
  return Number(execFileSync('docker', ['exec', 'platform-postgres-dev', 'psql', '-U', 'platform_dev', '-d', 'workflow_platform_dev', '-Atc',
    `SELECT count(*) FROM act_hi_procinst WHERE business_key_='${businessKey}'`], { encoding: 'utf8' }).trim())
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
  const users = await request(page, 'GET', 'http://localhost:3000/api/portal/tasks/users/search?keyword=developer')
  const person = users.find(user => user.username === 'developer')
  assert.ok(person?.id)
  const store = `dw:batch_lines_${processKey.slice('batch_verify_'.length)}`
  const payload = target => ({ formData: { case_key: 'SYNTHETIC-INITIAL-CASE', __subTables__: { [store]: [
    { line_key: 'SYNTHETIC-ONE', case_ref: 'SYNTHETIC-INITIAL-CASE', caption: 'Synthetic initial first', amount: 1, handler_code: target },
    { line_key: 'SYNTHETIC-TWO', case_ref: 'SYNTHETIC-INITIAL-CASE', caption: 'Synthetic initial second', amount: 2, handler_code: target },
  ] }, syntheticAssignmentRows: [{ handler_code: 'SYNTHETIC-SPOOFED-NONPERSON' }] }, remark: 'Synthetic initial MI verification' })
  assert.equal(rejectedInstanceCount(), 0)
  const startUrl = `http://localhost:3000/api/portal/processes/${encodeURIComponent(catalog.code)}/start`
  const response = await page.request.post(startUrl, { data: { ...payload('SYNTHETIC-INACTIVE-NONPERSON'), businessKey } })
  const rejected = await response.json()
  if (rejected.success) console.log(JSON.stringify({ step: 'unexpected-initial-mi-start', instanceId: rejected.data.processInstanceId || rejected.data.id }))
  assert.equal(rejected.success, false, 'Invalid initial targets must reject before Flowable start')
  assert.equal(rejectedInstanceCount(), 0)
  const started = await request(page, 'POST', startUrl, payload(person.id))
  assert.ok(!started.firstStepError)
  const instanceId = started.processInstanceId || started.id
  const tasks = (await request(page, 'GET', `http://localhost:8081/api/v1/tasks?processInstanceId=${instanceId}&page=0&size=100`)).tasks
  assert.equal(tasks.length, 2, 'Canonical rows must replace the one spoofed initial collection item')
  const identities = new Set()
  for (const task of tasks) {
    assert.equal(task.taskDefinitionKey, 'AssignedWork')
    const detail = await request(page, 'GET', `http://localhost:8081/api/v1/tasks/${task.taskId}`)
    assert.equal(detail.currentAssignee === person.id, true, 'Actual initial task must use the validated directory target')
    assert.ok(!detail.candidateUserIds?.length)
    assert.equal('__subTableBatchRuntime__' in detail.variables, false)
    const identity = detail.variables._currentItem.rowKey.line_key
    assert.ok(['SYNTHETIC-ONE', 'SYNTHETIC-TWO'].includes(identity)); identities.add(identity)
  }
  assert.equal(identities.size, 2)
  console.log(JSON.stringify({ step: 'synthetic-initial-mi', instanceId, taskIds: tasks.map(task => task.taskId), identities: 2, rejectedInstances: 0 }))
} finally { await browser.close() }
