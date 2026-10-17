/** Real synthetic draft → Start UI → actual tasks. No intercepted responses or task repairs. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const key = process.env.FIXTURE_PROCESS_KEY
const expected = process.env.FIXTURE_EXPECT_ASSIGNMENT_OUTCOME
assert.match(key || '', /^batch_verify_[a-z0-9]+$/)
assert.ok(['VERIFIED', 'FAILED', 'UNCONFIRMED'].includes(expected))
const tag = process.env.FIXTURE_SCREENSHOT_TAG || 'initial-assignment-outcome'
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
  const catalog = await request(page, 'GET', `/api/v1/admin/function-units/by-process-key/${key}`)
  assert.match(catalog.name, /^Batch verification /)
  const draftPath = `/api/portal/processes/${encodeURIComponent(catalog.code)}/draft`
  assert.ok(!await request(page, 'GET', draftPath), 'Refusing to overwrite an existing synthetic draft')
  const people = await request(page, 'GET', '/api/portal/tasks/users/search?keyword=developer')
  const person = people.find(value => value.username === 'developer')
  assert.ok(person?.id)
  const store = `dw:batch_lines_${key.slice('batch_verify_'.length)}`
  await request(page, 'POST', draftPath, { case_key: 'SYNTHETIC-OUTCOME', __subTables__: { [store]: [
    { line_key: 'SYNTHETIC-ONE', case_ref: 'SYNTHETIC-OUTCOME', caption: 'Synthetic first', amount: 1, handler_code: person.id },
    { line_key: 'SYNTHETIC-TWO', case_ref: 'SYNTHETIC-OUTCOME', caption: 'Synthetic second', amount: 2, handler_code: person.id },
  ] } })
  await page.goto(`http://localhost:3101/portal/processes/start/${encodeURIComponent(catalog.code)}?draft=true`)
  const submit = page.getByRole('button', { name: 'Submit Application', exact: true })
  await submit.waitFor()
  await page.locator('.el-message--success').waitFor({ state: 'hidden' })
  const startedResponse = page.waitForResponse(response => response.url().endsWith(`/processes/${catalog.code}/start`) && response.request().method() === 'POST')
  await submit.click()
  const body = await (await startedResponse).json()
  assert.equal(body.success, true, 'Instance creation must not be misreported as a retryable start failure')
  assert.ok(!body.data.firstStepError, 'A completed first step must not be reported as retryable')
  const instanceId = body.data.processInstanceId || body.data.id
  assert.ok(instanceId)
  console.log(JSON.stringify({ step: 'synthetic-initial-outcome-started', instanceId, observation: body.data.batchAssignmentVerification || null }))
  await page.waitForURL('**/my-applications')
  const message = page.locator('.el-message').last()
  await message.waitFor()
  const screenshot = fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-17_${tag}.png`, import.meta.url))
  await message.screenshot({ path: screenshot })
  assert.equal(body.data.batchAssignmentVerification?.status, expected)
  assert.equal(await message.locator('.el-message__content').innerText(), expected === 'VERIFIED' ? 'Process submitted successfully' : expected === 'FAILED'
    ? 'Request created, but some initial personal tasks have a missing or mismatched assignee. Do not submit again; ask an administrator to inspect the actual tasks.'
    : 'Request created, but the batch assignment results are not fully verified. Do not submit again; inspect the actual tasks.')
  assert.equal(await page.locator('.el-message--success').count(), expected === 'VERIFIED' ? 1 : 0)
  await page.setViewportSize({ width: 600, height: 800 })
  await message.screenshot({ path: screenshot.replace('.png', '-narrow.png') })
  const tasks = (await request(page, 'GET', `http://localhost:8081/api/v1/tasks?processInstanceId=${instanceId}&page=0&size=100`)).tasks
  assert.equal(tasks.length, 2)
  for (const task of tasks) assert.equal(task.currentAssignee === person.id, expected !== 'FAILED')
  assert.equal(pageErrors, 0)
  console.log(JSON.stringify({ step: 'synthetic-initial-outcome-ui', instanceId, taskIds: tasks.map(task => task.taskId), expected, pageErrors, screenshot }))
} finally { await browser.close() }
