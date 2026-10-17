/** Real synthetic Save → UI Complete → actual tasks, without replaying completed actions. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const taskId = process.env.FIXTURE_TASK_ID
const expected = process.env.FIXTURE_EXPECT_ASSIGNMENT_OUTCOME
assert.match(taskId || '', /^[a-f0-9-]{36}$/i)
assert.ok(['FAILED', 'UNCONFIRMED'].includes(expected))
const tag = process.env.FIXTURE_SCREENSHOT_TAG || 'complete-assignment-outcome'
assert.match(tag, /^[a-z0-9-]+$/)
const browser = await chromium.launch({ headless: true })
async function request(page, method, path, data) {
  const response = await page.request.fetch(new URL(path, 'http://localhost:3000').href, { method, data })
  assert.ok(response.ok(), `Synthetic HTTP ${response.status()}`)
  const body = await response.json()
  if ('success' in body) assert.equal(body.success, true)
  return 'success' in body ? body.data : body
}
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  let pageErrors = 0
  page.on('pageerror', () => pageErrors++)
  await loginViaPortalPassword(page)
  const task = await request(page, 'GET', `/api/portal/tasks/${taskId}`)
  assert.match(task.functionUnitName, /^Batch verification /)
  assert.match(task.processDefinitionKey, /^batch_verify_[a-z0-9]+$/)
  assert.equal(task.taskDefinitionKey, 'Review')
  const form = await request(page, 'GET', `/api/portal/tasks/${taskId}/form-data`)
  const [control] = Object.values(form.subTableBatchCapabilities)
  assert.equal(control.capabilities.allowBatchAssign && control.assignmentConfig.allowUser, true)
  const people = await request(page, 'GET', '/api/portal/tasks/users/search?keyword=developer')
  const person = people.find(value => value.username === 'developer')
  assert.ok(person?.id)
  const store = `dw:${control.rowDesign.binding.tableName}`
  const payload = structuredClone(form.fieldValues)
  const rows = payload.__subTables__[store]
  assert.equal(rows.length, 2)
  for (const row of rows) {
    row[control.assignmentConfig.assigneeField] = person.id
    for (const field of ['roleField', 'buField']) if (control.assignmentConfig[field]) row[control.assignmentConfig[field]] = null
  }
  const scopes = [{ bindingId: String(control.bindingId), storeKey: store, emptied: false,
    rowKeys: rows.map(row => Object.fromEntries(control.rowDesign.binding.primaryKeyFields.map(key => [key, row[key]]))) }]
  await request(page, 'POST', `/api/portal/tasks/${taskId}/submit`, { formData: payload, subTableBindingScopes: scopes })
  const enginePath = `http://localhost:8081/api/v1/tasks?processInstanceId=${task.processInstanceId}&page=0&size=100`
  assert.deepEqual((await request(page, 'GET', enginePath)).tasks.map(value => value.taskId), [taskId], 'Save must not create tasks')
  await page.goto(`http://localhost:3101/portal/tasks/${taskId}`)
  await page.getByRole('button', { name: 'Approve', exact: true }).click()
  const dialog = page.locator('.el-dialog:visible')
  await dialog.locator('textarea').fill('Synthetic completion outcome verification')
  const completedResponse = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/complete`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: 'Confirm', exact: true }).click()
  const body = await (await completedResponse).json()
  assert.equal(body.success, true)
  const tasks = (await request(page, 'GET', enginePath)).tasks
  assert.equal(tasks.length, 2)
  for (const created of tasks) assert.equal(created.currentAssignee === person.id, expected !== 'FAILED')
  console.log(JSON.stringify({ step: 'synthetic-complete-outcome-created', instanceId: task.processInstanceId,
    taskIds: tasks.map(created => created.taskId), observation: body.data?.batchAssignmentVerification || null }))
  await page.waitForURL('**/portal/tasks')
  const message = page.locator('.el-message').last()
  await message.waitFor()
  const screenshot = fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-17_${tag}.png`, import.meta.url))
  await message.screenshot({ path: screenshot })
  assert.equal(body.data?.batchAssignmentVerification?.status, expected)
  assert.equal(await message.locator('.el-message__content').innerText(), expected === 'FAILED'
    ? 'Task completed, but some initial personal tasks have no assignee. Do not complete it again; ask an administrator to inspect the actual tasks.'
    : 'Task completed, but the batch assignment results are not fully verified. Do not complete it again; inspect the actual tasks.')
  assert.equal(await page.locator('.el-message--success').count(), 0)
  await page.setViewportSize({ width: 600, height: 800 })
  await message.screenshot({ path: screenshot.replace('.png', '-narrow.png') })
  assert.equal(pageErrors, 0)
  console.log(JSON.stringify({ step: 'synthetic-complete-outcome-ui', instanceId: task.processInstanceId, expected, pageErrors, screenshot }))
} finally { await browser.close() }
