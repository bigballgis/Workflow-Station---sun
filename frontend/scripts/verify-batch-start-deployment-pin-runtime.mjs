/** Synthetic catalog only. Redeploy its identical BPMN to reproduce a stale startup deployment pin. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { chromium } from 'playwright'
import { loginViaPortalPassword, loginViaAdminPassword } from './playwright-login.mjs'
import { readBatchStartContext } from './batch-start-context.mjs'

const processKey = process.env.FIXTURE_PROCESS_KEY
assert.match(processKey || '', /^batch_verify_[a-z0-9]+$/)
const businessKey = `SYNTHETIC-PIN-${Date.now()}`
const browser = await chromium.launch({ headless: true })
async function request(page, method, url, data) {
  const response = await page.request.fetch(url, { method, data })
  assert.equal(response.ok(), true, `Synthetic ${method} HTTP ${response.status()}`)
  const body = await response.json()
  if ('success' in body) assert.equal(body.success, true)
  return 'data' in body ? body.data : body
}
function rejectedInstanceCount() {
  assert.match(businessKey, /^SYNTHETIC-PIN-\d+$/)
  return Number(execFileSync('docker', ['exec', 'platform-postgres-dev', 'psql', '-U', 'platform_dev', '-d', 'workflow_platform_dev', '-Atc',
    `SELECT count(*) FROM act_hi_procinst WHERE business_key_='${businessKey}'`], { encoding: 'utf8' }).trim())
}
try {
  const page = await browser.newPage()
  await loginViaAdminPassword(page)
  const catalog = await request(page, 'GET', `http://localhost:3000/api/v1/admin/function-units/by-process-key/${processKey}`)
  assert.match(catalog.name, /^Batch verification /)
  await loginViaPortalPassword(page)
  const api = (method, path, data) => request(page, method, `http://localhost:3000/api/portal${path}`, data)
  const engine = (method, path, data) => request(page, method, `http://localhost:8081/api/v1/processes${path}`, data)
  const store = `dw:batch_lines_${processKey.slice('batch_verify_'.length)}`
  const payload = { batchStartContext: await readBatchStartContext(page, catalog.id), formData: { case_key: 'SYNTHETIC-PIN-CASE', __subTables__: { [store]: [
    { line_key: 'SYNTHETIC-ONE', case_ref: 'SYNTHETIC-PIN-CASE', caption: 'Synthetic pin first', amount: 1 },
    { line_key: 'SYNTHETIC-TWO', case_ref: 'SYNTHETIC-PIN-CASE', caption: 'Synthetic pin second', amount: 2 },
  ] } }, remark: 'Synthetic startup pin verification' }
  const startPath = `/processes/${encodeURIComponent(catalog.code)}/start`
  const warm = await api('POST', startPath, payload)
  assert.ok(!warm.firstStepError)
  const warmId = warm.processInstanceId || warm.id
  const warmSource = await engine('GET', `/definitions/${processKey}/bpmn?processInstanceId=${encodeURIComponent(warmId)}`)
  assert.equal(warmSource.processDefinitionKey, processKey)
  const deployed = await engine('POST', '/definitions/deploy', { key: processKey, name: catalog.name, bpmnXml: warmSource.bpmnXml })
  assert.equal(deployed.processDefinitionKey, processKey)
  assert.equal(typeof deployed.engineProcessDefinitionId, 'string')
  assert.ok(deployed.engineProcessDefinitionId)
  assert.notEqual(deployed.engineProcessDefinitionId, warmSource.processDefinitionId)
  assert.equal(rejectedInstanceCount(), 0)
  const stale = await page.request.post('http://localhost:8081/api/v1/processes/instances/pinned', { data: {
    processDefinitionKey: processKey, expectedProcessDefinitionId: warmSource.processDefinitionId, businessKey, variables: {},
  } })
  assert.equal(stale.status(), 400)
  assert.equal((await stale.json()).success, false)
  assert.equal(rejectedInstanceCount(), 0, 'Stale engine pin must not create a historic or live instance')
  const stalePortal = await page.request.post(`http://localhost:3000/api/portal${startPath}`, { data: { ...payload, businessKey } })
  assert.equal(stalePortal.ok(), false)
  assert.equal((await stalePortal.json()).success, false)
  assert.equal(rejectedInstanceCount(), 0, 'Portal must not silently retry latest or create a mismatched instance')
  const retry = await api('POST', startPath, payload)
  assert.ok(!retry.firstStepError)
  const instanceId = retry.processInstanceId || retry.id
  const source = await engine('GET', `/definitions/${processKey}/bpmn?processInstanceId=${encodeURIComponent(instanceId)}`)
  assert.notEqual(source.processDefinitionId, warmSource.processDefinitionId)
  const tasks = await request(page, 'GET', `http://localhost:8081/api/v1/tasks?processInstanceId=${encodeURIComponent(instanceId)}&page=0&size=100`)
  assert.equal(tasks.tasks.length, 1); assert.equal(tasks.tasks[0].taskDefinitionKey, 'Review')
  const form = await api('GET', `/tasks/${tasks.tasks[0].taskId}/form-data`)
  assert.ok(Object.values(form.subTableBatchCapabilities).some(control => control.capabilities.allowBatchAssign))
  const originalSource = await engine('GET', `/definitions/${processKey}/bpmn?processInstanceId=${encodeURIComponent(warmId)}`)
  assert.equal(originalSource.processDefinitionId, warmSource.processDefinitionId, 'Existing instance definition must not migrate')
  console.log(JSON.stringify({ step: 'synthetic-start-deployment-pin', warmInstanceId: warmId, instanceId,
    reviewTaskId: tasks.tasks[0].taskId, rejectedInstances: rejectedInstanceCount(), oldInstanceDefinitionUnchanged: true }))
} finally { await browser.close() }
