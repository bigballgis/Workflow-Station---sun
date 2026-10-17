/** Real synthetic draft → Start UI → actual tasks. No intercepted responses or task repairs. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword, loginViaAdminPassword } from './playwright-login.mjs'
import { selectExistingBatchRolePool } from './batch-verification-pool.mjs'

const key = process.env.FIXTURE_PROCESS_KEY
const expected = process.env.FIXTURE_EXPECT_ASSIGNMENT_OUTCOME
const roleMode = process.env.FIXTURE_ASSIGNMENT_MODE === 'role'
assert.ok(!process.env.FIXTURE_ASSIGNMENT_MODE || ['user', 'role'].includes(process.env.FIXTURE_ASSIGNMENT_MODE))
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
  let admin = null
  if (roleMode) {
    admin = await browser.newPage()
    await loginViaAdminPassword(admin)
    const roleId = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')).activeRoleId)
    assert.ok(roleId)
    const path = `/api/v1/admin/function-units/${catalog.id}/access`
    const access = await request(admin, 'GET', path)
    if (!access.some(entry => entry.roleId === roleId)) await request(admin, 'POST', path, { roleId })
  }
  const draftPath = `/api/portal/processes/${encodeURIComponent(catalog.code)}/draft`
  assert.ok(!await request(page, 'GET', draftPath), 'Refusing to overwrite an existing synthetic draft')
  let person = null
  let pool = null
  if (roleMode) {
    pool = await selectExistingBatchRolePool(page, admin, process.env.FIXTURE_MULTI_MEMBER_POOL === '1')
  } else {
    const people = await request(page, 'GET', '/api/portal/tasks/users/search?keyword=developer')
    person = people.find(value => value.username === 'developer')
    assert.ok(person?.id)
  }
  const target = roleMode ? { pool_code: pool.role.code, unit_code: pool.bu.code } : { handler_code: person.id }
  const store = `dw:batch_lines_${key.slice('batch_verify_'.length)}`
  await request(page, 'POST', draftPath, { case_key: 'SYNTHETIC-OUTCOME', __subTables__: { [store]: [
    { line_key: 'SYNTHETIC-ONE', case_ref: 'SYNTHETIC-OUTCOME', caption: 'Synthetic first', amount: 1, ...target },
    { line_key: 'SYNTHETIC-TWO', case_ref: 'SYNTHETIC-OUTCOME', caption: 'Synthetic second', amount: 2, ...target },
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
    ? 'Request created, but some initial assignments have missing or mismatched targets. Do not submit again; ask an administrator to inspect the actual tasks.'
    : 'Request created, but the batch assignment results are not fully verified. Do not submit again; inspect the actual tasks.')
  assert.equal(await page.locator('.el-message--success').count(), expected === 'VERIFIED' ? 1 : 0)
  await page.setViewportSize({ width: 600, height: 800 })
  await message.screenshot({ path: screenshot.replace('.png', '-narrow.png') })
  const tasks = (await request(page, 'GET', `http://localhost:8081/api/v1/tasks?processInstanceId=${instanceId}&page=0&size=100`)).tasks
  assert.equal(tasks.length, 2)
  for (const task of tasks) {
    if (roleMode) {
      assert.ok(!task.currentAssignee)
      assert.equal(task.assignmentType, 'CANDIDATE_USERS')
      assert.equal(JSON.stringify(task.candidateUserIds.toSorted()) === JSON.stringify(pool.users.toSorted()), true, 'Actual role candidates must exactly match the directory pool')
      assert.equal(task.miAssigneeMode, 'role'); assert.equal(task.miRoleCode, pool.role.code); assert.equal(task.miBusinessUnitCode, pool.bu.code)
      if (process.env.FIXTURE_EXPECT_EXTRA_GROUP === '1') assert.deepEqual(task.candidateGroupIds, ['synthetic-extra-group'])
      else assert.ok(!task.candidateGroupIds?.length)
    } else assert.equal(task.currentAssignee === person.id, expected !== 'FAILED')
  }
  if (process.env.FIXTURE_EXPECT_EXTRA_GROUP === '1') {
    assert.ok(roleMode && expected === 'FAILED')
    assert.deepEqual(body.data.batchAssignmentVerification.issues.map(issue => issue.taskId).sort(), tasks.map(task => task.taskId).sort())
    assert.ok(body.data.batchAssignmentVerification.issues.every(issue => issue.reasonCode === 'ROLE_TASK_CANDIDATES_MISMATCH'))
  }
  if (process.env.FIXTURE_EXPECT_WRONG_PERSON === '1') {
    assert.equal(expected, 'FAILED')
    for (const task of tasks) assert.equal(task.currentAssignee, 'synthetic-wrong-person')
    assert.deepEqual(body.data.batchAssignmentVerification.issues.map(issue => issue.taskId).sort(), tasks.map(task => task.taskId).sort())
    assert.ok(body.data.batchAssignmentVerification.issues.every(issue => issue.reasonCode === 'PERSON_TASK_TARGET_MISMATCH'))
  }
  if (process.env.FIXTURE_VERIFY_HISTORY === '1') {
    await page.goto(`http://localhost:3101/portal/applications/${instanceId}`)
    await page.getByRole('tab', { name: 'Flow History', exact: true }).click()
    const history = page.locator('.history-section')
    await history.waitFor()
    await history.screenshot({ path: screenshot.replace('.png', '-history.png') })
    const records = await request(page, 'GET', `/api/portal/processes/${instanceId}/history`)
    const record = records.find(item => item.operationType === 'BATCH_ASSIGNMENT_VERIFICATION')
    assert.deepEqual(record?.batchAssignmentVerification, body.data.batchAssignmentVerification,
      'Committed verification must remain available after navigation and reload')
    await page.reload()
    await page.getByRole('tab', { name: 'Flow History', exact: true }).click()
    await page.locator('.batch-assignment-history').waitFor()
    await page.locator('.batch-assignment-history').screenshot({ path: screenshot.replace('.png', '-history-reloaded.png') })
  }
  assert.equal(pageErrors, 0)
  console.log(JSON.stringify({ step: 'synthetic-initial-outcome-ui', instanceId, taskIds: tasks.map(task => task.taskId), expected, pageErrors, screenshot }))
} finally { await browser.close() }
