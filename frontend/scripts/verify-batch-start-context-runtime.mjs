/** Own synthetic clone only: real page → same-catalog overwrite → reject old echo → refresh and start. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'

const sourceId = process.env.FIXTURE_UNIT_ID
assert.match(sourceId || '', /^\d+$/)
const tag = process.env.FIXTURE_SCREENSHOT_TAG || 'batch-start-context'
assert.match(tag, /^[a-z0-9-]+$/)
const shot = suffix => fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-18_${tag}-${suffix}.png`, import.meta.url))
const browser = await chromium.launch({ headless: true })
async function json(page, method, url, data) {
  const response = await page.request.fetch(url, { method, data })
  assert.ok(response.ok(), `Synthetic ${method} HTTP ${response.status()}`)
  const body = await response.json()
  if ('success' in body) assert.equal(body.success, true)
  return 'data' in body ? body.data : body
}
function instances(code) {
  assert.match(code, /^[A-Za-z0-9_-]{1,100}$/)
  return Number(execFileSync('docker', ['exec', 'platform-postgres-dev', 'psql', '-U', 'platform_dev', '-d', 'workflow_platform_dev', '-Atc',
    `SELECT count(*) FROM act_hi_procinst i JOIN act_re_procdef d ON d.id_=i.proc_def_id_ WHERE d.key_='${code}'`], { encoding: 'utf8' }).trim())
}
async function overwriteClone(dwPage, admin, id) {
  const exported = await dwPage.request.get(`http://localhost:3000/api/v1/export-import/function-units/${id}/export`)
  assert.ok(exported.ok())
  return admin('POST', '/function-units/import', {
    fileName: 'synthetic-context.zip', fileContent: (await exported.body()).toString('base64'), enableOnImport: true,
  })
}
try {
  const dwPage = await browser.newPage()
  const adminPage = await browser.newPage()
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  let pageErrors = 0
  page.on('pageerror', () => pageErrors++)
  await loginViaDwPassword(dwPage)
  await loginViaAdminPassword(adminPage)
  await loginViaPortalPassword(page)
  const dw = (method, path, data) => json(dwPage, method, `http://localhost:3000/api/v1${path}`, data)
  const admin = (method, path, data) => json(adminPage, method, `http://localhost:3000/api/v1/admin${path}`, data)
  const portal = (method, path, data) => json(page, method, `http://localhost:3000/api/portal${path}`, data)
  const source = await dw('GET', `/function-units/${sourceId}`)
  assert.match(source.name, /^Batch verification /)
  assert.ok(source.tags.includes('batch-verification-synthetic'))
  const clone = await dw('POST', `/function-units/${sourceId}/clone?newName=${encodeURIComponent(`Batch verification startcontext${Date.now()}`)}`)
  assert.notEqual(clone.code, source.code)
  const deployed = await dw('POST', `/function-units/${clone.id}/deploy`, {
    environment: 'DEVELOPMENT', autoEnable: true, conflictStrategy: 'FAIL', changeLog: 'Synthetic start context verification',
  })
  await waitForFixtureDeployment(dw, deployed.deploymentId)
  const catalog = await admin('GET', `/function-units/by-process-key/${encodeURIComponent(clone.code)}`)
  assert.equal(catalog.code, clone.code)
  const roleId = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')).activeRoleId)
  assert.ok(roleId)
  const access = await admin('GET', `/function-units/${catalog.id}/access`)
  if (!access.some(item => item.roleId === roleId)) await admin('POST', `/function-units/${catalog.id}/access`, { roleId })
  const table = (await dw('GET', `/function-units/${clone.id}/tables`)).find(item => item.tableType === 'SUB')
  assert.ok(table && ['line_key', 'case_ref', 'caption', 'amount'].every(key => table.fieldDefinitions.some(field => field.fieldName === key)))
  const draftPath = `/processes/${clone.code}/draft`
  // The code belongs to the brand-new clone above, never an existing user's draft target.
  const formData = { case_key: 'SYNTHETIC-CONTEXT', __subTables__: { [`dw:${table.tableName}`]: [
    { line_key: 'SYNTHETIC-ONE', case_ref: 'SYNTHETIC-CONTEXT', caption: 'Synthetic first', amount: 1 },
    { line_key: 'SYNTHETIC-TWO', case_ref: 'SYNTHETIC-CONTEXT', caption: 'Synthetic second', amount: 2 },
  ] } }
  await portal('POST', draftPath, formData)
  console.log(JSON.stringify({ step: 'synthetic-context-clone', clonedUnitId: clone.id, catalogId: catalog.id, processKey: clone.code }))
  await page.goto(`http://localhost:3101/portal/processes/start/${encodeURIComponent(clone.code)}?draft=true`)
  const submit = page.getByRole('button', { name: 'Submit Application', exact: true })
  await submit.waitFor()
  await page.locator('.el-message--success').waitFor({ state: 'hidden' })
  const before = await portal('GET', `/processes/function-units/${catalog.id}/content?forStart=true`)
  await page.screenshot({ path: shot('opened'), fullPage: true, mask: [page.locator('.user-info')] })
  assert.ok(before.batchStartContext, 'An enhanced New Request must receive a server start context')
  const omitted = await page.request.post(`http://localhost:3000/api/portal/processes/${clone.code}/start`, { data: { formData } })
  assert.equal(omitted.status(), 409)
  assert.equal((await omitted.json()).success, false)
  assert.equal(instances(clone.code), 0)
  // Warm the separate legacy task-content cache before overwriting the exact same catalog.
  await portal('GET', `/processes/function-units/${catalog.id}/content`)
  const forms = await dw('GET', `/function-units/${clone.id}/forms`)
  const form = await dw('GET', `/function-units/${clone.id}/forms/${forms.find(item => item.formType === 'TASK').id}`)
  const config = structuredClone(form.configJson)
  const batch = Object.values(config.batchForms)[0]
  batch.rule.find(rule => rule.type === 'miAssignment').title = `SYNTHETIC-CONTEXT-${Date.now()}`
  await dw('PUT', `/function-units/${clone.id}/forms/${form.id}`, {
    formName: form.formName, formType: form.formType, scene: form.scene, configJson: config, fieldPermissions: form.fieldPermissions,
  })
  const imported = await overwriteClone(dwPage, admin, clone.id)
  assert.equal(imported.functionUnit.id, catalog.id, 'This must overwrite the same catalog, not merely create another version')
  const after = await portal('GET', `/processes/function-units/${catalog.id}/content?forStart=true`)
  assert.notDeepEqual(after.batchStartContext, before.batchStartContext)
  assert.equal(instances(clone.code), 0)
  const rejectedResponse = page.waitForResponse(response => response.url().endsWith(`/processes/${clone.code}/start`) && response.request().method() === 'POST')
  await submit.click()
  const rejectedHttp = await rejectedResponse
  assert.equal(rejectedHttp.status(), 409)
  const rejected = await rejectedHttp.json()
  assert.equal(rejected.success, false)
  assert.match(rejected.error.message, /Refresh the request form/)
  assert.equal(instances(clone.code), 0, 'Rejected stale page must not create an engine instance')
  await page.locator('.el-message--error').last().screenshot({ path: shot('stale-rejected') })
  await page.reload()
  await submit.waitFor()
  const startedResponse = page.waitForResponse(response => response.url().endsWith(`/processes/${clone.code}/start`) && response.request().method() === 'POST')
  await submit.click()
  const started = await (await startedResponse).json()
  assert.equal(started.success, true)
  assert.ok(!started.data.firstStepError)
  assert.equal(instances(clone.code), 1)
  const instanceId = started.data.id || started.data.processInstanceId
  const tasks = await json(page, 'GET', `http://localhost:8081/api/v1/tasks?processInstanceId=${instanceId}&page=0&size=100`)
  assert.equal(tasks.tasks.length, 1)
  assert.equal(tasks.tasks[0].taskDefinitionKey, 'Review')
  const actual = await json(page, 'GET', `http://localhost:8081/api/v1/tasks/${tasks.tasks[0].taskId}`)
  assert.ok(!('batchStartContext' in actual.variables))
  const frozen = (await portal('GET', `/tasks/${tasks.tasks[0].taskId}/form-data`)).subTableBatchCapabilities
  assert.ok(Object.keys(frozen).length > 0)
  await page.waitForURL('**/my-applications')
  const secondData = structuredClone(formData)
  secondData.case_key = 'SYNTHETIC-CONTEXT-TWO'
  for (const row of secondData.__subTables__[`dw:${table.tableName}`]) row.case_ref = secondData.case_key
  await portal('POST', draftPath, secondData)
  await page.goto(`http://localhost:3101/portal/processes/start/${encodeURIComponent(clone.code)}?draft=true`)
  await submit.waitFor()
  for (const meta of await dw('GET', `/function-units/${clone.id}/forms`)) {
    const detail = await dw('GET', `/function-units/${clone.id}/forms/${meta.id}`)
    const disabled = structuredClone(detail.configJson)
    for (const control of disabled.rule.filter(rule => rule.type === 'subTable')) {
      for (const flag of ['allowBatchAdd', 'allowBatchEdit', 'allowBatchDelete', 'allowBatchAssign']) control.props[flag] = false
    }
    await dw('PUT', `/function-units/${clone.id}/forms/${meta.id}`, {
      formName: detail.formName, formType: detail.formType, scene: detail.scene, configJson: disabled, fieldPermissions: detail.fieldPermissions,
    })
  }
  assert.equal((await overwriteClone(dwPage, admin, clone.id)).functionUnit.id, catalog.id)
  assert.equal((await portal('GET', `/processes/function-units/${catalog.id}/content?forStart=true`)).batchStartContext, undefined)
  assert.deepEqual((await portal('GET', `/tasks/${tasks.tasks[0].taskId}/form-data`)).subTableBatchCapabilities, frozen,
    'An existing enhanced instance must retain its own frozen capability after the same catalog disables batch')
  await page.setViewportSize({ width: 600, height: 800 })
  const disabledResponse = page.waitForResponse(response => response.url().endsWith(`/processes/${clone.code}/start`) && response.request().method() === 'POST')
  await submit.click()
  assert.equal((await disabledResponse).status(), 409)
  assert.equal(instances(clone.code), 1)
  const error = page.locator('.el-message--error').last()
  await error.screenshot({ path: shot('disabled-narrow') })
  const errorBox = await error.boundingBox()
  assert.ok(errorBox && errorBox.x >= 0 && errorBox.x + errorBox.width <= 600, 'Narrow error must remain inside viewport')
  await page.reload()
  await submit.waitFor()
  const legacyResponse = page.waitForResponse(response => response.url().endsWith(`/processes/${clone.code}/start`) && response.request().method() === 'POST')
  await submit.click()
  const legacy = await (await legacyResponse).json()
  assert.equal(legacy.success, true)
  assert.equal(instances(clone.code), 2)
  const legacyId = legacy.data.id || legacy.data.processInstanceId
  const legacyTasks = await json(page, 'GET', `http://localhost:8081/api/v1/tasks?processInstanceId=${legacyId}&page=0&size=100`)
  assert.equal(legacyTasks.tasks.length, 1)
  const legacyForm = await portal('GET', `/tasks/${legacyTasks.tasks[0].taskId}/form-data`)
  assert.ok(!legacyForm.subTableBatchRuntimeEnabled)
  assert.equal(Object.keys(legacyForm.subTableBatchCapabilities || {}).length, 0)
  assert.equal(pageErrors, 0)
  console.log(JSON.stringify({ step: 'synthetic-context-runtime', instanceId: started.data.id || started.data.processInstanceId,
    staleInstances: 0, validInstances: 2, frozenExistingInstancePreserved: true, catalogId: catalog.id, pageErrors }))
} finally { await browser.close() }
