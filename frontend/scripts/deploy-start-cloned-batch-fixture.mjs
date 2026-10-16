/** Only clones a tagged synthetic FU, deploys that new code and starts two synthetic rows. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'
import { unwrapApiList } from './mi-regression-helpers.mjs'

const sourceId = process.env.FIXTURE_UNIT_ID
assert.match(sourceId || '', /^\d+$/)
const browser = await chromium.launch({ headless: true })
let taskId, processKey, catalogId
async function json(page, method, url, data) {
  const response = await page.request.fetch(url, { method, data })
  assert.equal(response.ok(), true, `${method} ${url} HTTP ${response.status()}`)
  const body = await response.json()
  if (body && typeof body === 'object' && 'success' in body) assert.equal(body.success, true)
  return body && typeof body === 'object' && 'data' in body ? body.data : body
}
try {
  const page = await browser.newPage()
  await loginViaDwPassword(page)
  const dw = (method, path, data) => json(page, method, `http://localhost:3000/api/v1${path}`, data)
  const source = await dw('GET', `/function-units/${sourceId}`)
  assert.match(source.name, /^Batch verification /)
  assert.ok(source.tags.includes('batch-verification-synthetic'))
  const clone = await dw('POST', `/function-units/${sourceId}/clone?newName=${encodeURIComponent(`Batch verification runtime${Date.now()}`)}`)
  assert.notEqual(String(clone.id), sourceId)
  processKey = clone.code
  assert.match(processKey, /^[A-Za-z0-9_-]{1,100}$/)
  assert.notEqual(processKey, source.code, 'Clone must have its own code before deployment')
  console.log(JSON.stringify({ step: 'synthetic-runtime-clone', sourceId, clonedUnitId: clone.id, processKey }))
  const tables = await dw('GET', `/function-units/${clone.id}/tables`)
  const child = tables.find(table => table.tableType === 'SUB')
  assert.ok(child)
  assert.ok(['line_key', 'case_ref', 'caption', 'amount'].every(name => child.fieldDefinitions.some(field => field.fieldName === name)))
  assert.equal((await dw('GET', `/function-units/${clone.id}/validate`)).valid, true)
  const deployment = await dw('POST', `/function-units/${clone.id}/deploy`, {
    environment: 'DEVELOPMENT', autoEnable: true, conflictStrategy: 'FAIL', changeLog: 'Synthetic clone runtime verification',
  })
  await waitForFixtureDeployment(dw, deployment.deploymentId)
  await loginViaPortalPassword(page)
  const roleId = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')).activeRoleId)
  assert.ok(roleId)
  await loginViaAdminPassword(page)
  const catalog = await json(page, 'GET', `http://localhost:3000/api/v1/admin/function-units/by-process-key/${encodeURIComponent(processKey)}`)
  assert.equal(catalog.name, clone.name)
  assert.equal(catalog.code, processKey, 'Only the newly deployed clone catalog may receive fixture access/start')
  catalogId = catalog.id
  const accessUrl = `http://localhost:3000/api/v1/admin/function-units/${catalogId}/access`
  const access = await json(page, 'GET', accessUrl)
  if (!access.some(item => item.roleId === roleId)) await json(page, 'POST', accessUrl, { roleId })
  await loginViaPortalPassword(page)
  const portal = (method, path, data) => json(page, method, `http://localhost:3000/api/portal${path}`, data)
  const started = await portal('POST', `/processes/${encodeURIComponent(catalog.code)}/start`, {
    formData: { case_key: 'SYNTHETIC-CLONE-CASE', __subTables__: { [`dw:${child.tableName}`]: [
      { line_key: 'SYNTHETIC-ONE', case_ref: 'SYNTHETIC-CLONE-CASE', caption: 'First synthetic line', amount: 1 },
      { line_key: 'SYNTHETIC-TWO', case_ref: 'SYNTHETIC-CLONE-CASE', caption: 'Second synthetic line', amount: 2 },
    ] } }, remark: 'Synthetic clone runtime verification',
  })
  const instanceId = started.processInstanceId || started.id
  assert.ok(instanceId)
  const deadline = Date.now() + 30000
  do {
    const tasks = unwrapApiList(await portal('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] }))
      .filter(task => task.processInstanceId === instanceId)
    if (tasks.length === 1 && tasks[0].taskDefinitionKey === 'Review') { taskId = tasks[0].taskId; break }
    assert.ok(Date.now() < deadline, 'New cloned instance must reach the synthetic Review host')
    await new Promise(resolve => setTimeout(resolve, 500))
  } while (true)
  console.log(JSON.stringify({ step: 'synthetic-runtime-clone-started', clonedUnitId: clone.id, catalogId, instanceId, taskId }))
} finally { await browser.close() }

execFileSync(process.execPath, [fileURLToPath(new URL('./verify-batch-role-pool-runtime.mjs', import.meta.url))], {
  env: { ...process.env, FIXTURE_TASK_ID: taskId, FIXTURE_EXPECT_PROCESS_KEY: processKey, FIXTURE_EXPECT_CATALOG_ID: catalogId,
    FIXTURE_EXPECT_ROLE_ONLY: '1', FIXTURE_ASSIGN_VIA_UI: '1', FIXTURE_MIXED_TARGETS: '0', FIXTURE_SCREENSHOT_TAG: 'cloned-role-pool' },
  stdio: 'inherit', timeout: 180000,
})
