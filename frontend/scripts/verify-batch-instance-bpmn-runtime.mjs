/** Synthetic-only redeployment proof; restores the original XML as another deployment, never cancels tasks. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { chromium, request } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId || '', /^[a-f0-9-]{36}$/)
const browser = await chromium.launch({ headless: true })
const anonymous = await request.newContext()
let restore
try {
  const page = await browser.newPage()
  await loginViaPortalPassword(page)
  const portalResponse = await page.request.get(`http://localhost:3000/api/portal/tasks/${taskId}`)
  const portalBody = await portalResponse.json()
  assert.equal(portalResponse.ok() && portalBody.success, true)
  const task = portalBody.data
  assert.match(task.functionUnitName, /^Batch verification /)
  assert.equal(task.taskDefinitionKey, 'Review')
  const key = task.processDefinitionKey
  assert.match(key, /^batch_verify_[a-z0-9]+$/)
  const path = `http://localhost:8081/api/v1/processes/definitions/${key}/bpmn`
  const boundPath = `${path}?processInstanceId=${encodeURIComponent(task.processInstanceId)}`
  // The browser session's httpOnly cookie is sent normally; never extract/store/print its token.
  const engine = async (method, url, data) => {
    const response = await page.request.fetch(url, { method, data })
    const body = await response.json()
    assert.equal(response.ok() && body.success, true, `Engine ${method} HTTP ${response.status()}`)
    return body.data
  }
  for (const method of ['GET', 'HEAD']) {
    const response = await anonymous.fetch(boundPath, { method })
    assert.equal(response.status(), 403, `Anonymous ${method} must not read instance deployment`)
  }
  assert.equal((await anonymous.get(path)).status(), 200, 'Legacy latest read remains compatible')
  const original = await engine('GET', boundPath)
  assert.equal(original.processInstanceId, task.processInstanceId)
  assert.ok(original.processDefinitionId)
  const source = 'flowable:collection="syntheticAssignmentRows"'
  assert.equal(original.bpmnXml.split(source).length, 2, 'Only the explicit synthetic MI collection may be changed')
  const driftedXml = original.bpmnXml.replace(source, 'flowable:collection="syntheticMustNotBeUsedRows"')
  const deploy = xml => engine('POST', 'http://localhost:8081/api/v1/processes/definitions/deploy', {
    key, name: 'Synthetic batch instance-source verification', bpmnXml: xml,
  })
  // Set recovery before deployment: even a failed HTTP response may follow a committed deployment.
  restore = async () => {
    await deploy(original.bpmnXml)
    assert.equal((await engine('GET', path)).bpmnXml, original.bpmnXml)
  }
  const changed = await deploy(driftedXml)
  assert.notEqual(changed.processDefinitionId, original.processDefinitionId)
  const latest = await engine('GET', path)
  assert.ok(latest.bpmnXml.includes('syntheticMustNotBeUsedRows'))
  const pinned = await engine('GET', boundPath)
  assert.equal(pinned.processDefinitionId, original.processDefinitionId)
  assert.equal(pinned.bpmnXml, original.bpmnXml)
  console.log(JSON.stringify({ step: 'instance-definition-pinned-across-redeploy', processInstanceId: task.processInstanceId,
    anonymousReadsRejected: 2, latestDiffers: true }))
  execFileSync(process.execPath, [fileURLToPath(new URL('./verify-batch-assignment-stage-runtime.mjs', import.meta.url))], {
    env: { ...process.env, FIXTURE_TASK_ID: taskId, FIXTURE_VERIFY_GENERATED: '0', FIXTURE_SCREENSHOT_TAG: 'instance-bpmn' },
    stdio: 'inherit', timeout: 120000,
  })
} finally {
  try {
    if (restore) {
      await restore()
      console.log(JSON.stringify({ step: 'synthetic-latest-definition-restored' }))
    }
  } finally {
    await anonymous.dispose()
    await browser.close()
  }
}
