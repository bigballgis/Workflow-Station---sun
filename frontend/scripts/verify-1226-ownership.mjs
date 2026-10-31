/** Local #1226 regression. Creates only a dedicated p3-mi-dual-binding-test request.
 * Run from frontend: node scripts/verify-1226-ownership.mjs
 * MODE=before expects 403 when run against the original frontend.
 * Vehicle: editable MI participant, nested Party files, top-level Case files.
 */
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import { writeFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import assert from 'node:assert/strict'

const origin = 'http://localhost:3000'
const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
const mode = process.env.MODE ?? 'after'
const runDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Hong_Kong' }).format(new Date())
assert.ok(['before', 'after'].includes(mode))
const errors = []
page.on('pageerror', error => errors.push(error.message))
try {
  await loginViaPortalPassword(page)
  const caseId = randomUUID(), partyA = randomUUID(), partyB = randomUUID()
  const data = { id: caseId, title: '1226 controlled ownership regression', __subTables__: {
    'dw:p3_mi_party': [
      { id: partyA, case_id: caseId, party_name: 'Alice', assignee_user_id: 'user-dev' },
      { id: partyB, case_id: caseId, party_name: 'Bob', assignee_user_id: 'user-dev' },
    ],
    'dw:p3_mi_file': [{ id: randomUUID(), case_id: caseId, file_name: 'case-only-1226' }],
  } }
  const contentRes = await page.request.get(`${origin}/api/portal/processes/function-units/p3-mi-dual-binding-test/content?forStart=true`)
  const content = (await contentRes.json()).data
  function scopes(bindings) {
    return bindings.filter(b => b.tableName !== 'p3_mi_case').map(b => {
      const storeKey = `dw:${b.designerTableName ?? b.tableName}`
      const rows = data.__subTables__[storeKey].filter(r => b.foreignKeyField === 'case_id' ? r.case_id === caseId : r.party_id != null)
      return { bindingId: String(b.bindingId), storeKey, rowKeys: rows.map(r => ({ id: r.id })), emptied: false }
    })
  }
  const startRes = await page.request.post(`${origin}/api/portal/processes/p3-mi-dual-binding-test/start`, { data: { formData: data, subTableBindingScopes: scopes(content.forms[0].tableBindings) } })
  const start = await startRes.json()
  if (!startRes.ok()) throw new Error(JSON.stringify(start))
  const instance = start.data?.processInstanceId ?? start.data?.id
  console.log('START', startRes.status(), instance)
  function databaseFiles() {
    assert.match(instance, /^[0-9a-f-]{36}$/)
    return JSON.parse(execFileSync('docker', ['exec', 'platform-postgres-dev', 'psql', '-U', 'platform_dev', '-d', 'workflow_platform_dev', '-Atc',
      `select variables->'__subTables__'->'dw:p3_mi_file' from up_process_instance where process_instance_id='${instance}'`], { encoding: 'utf8' }))
  }
  function businessRows(rows) {
    return rows.map(({ _wsRowVersion, ...row }) => row)
  }
  async function tasks() {
    if (!/^[0-9a-f-]{36}$/.test(instance)) throw new Error('Invalid synthetic process identity')
    const result = execFileSync('docker', ['exec', 'platform-postgres-dev', 'psql', '-U', 'platform_dev', '-d', 'workflow_platform_dev', '-Atc',
      `select coalesce(json_agg(json_build_object('taskId',id_,'taskName',name_)), '[]') from act_ru_task where proc_inst_id_='${instance}' and assignee_='user-dev'`], { encoding: 'utf8' })
    return JSON.parse(result)
  }
  let mine = []
  for (let attempt = 0; attempt < 20 && mine.length === 0; attempt++) {
    await page.waitForTimeout(1000)
    mine = await tasks()
  }
  console.log('TASKS', JSON.stringify(mine.map(t => ({ id: t.taskId, name: t.taskName }))))
  const release = mine.find(t => /Release/.test(t.taskName))
  if (release) {
    const response = await page.request.post(`${origin}/api/portal/tasks/${release.taskId}/complete`, { data: {
      taskId: release.taskId, action: 'APPROVE', formData: data, variables: { ...data, approved: true, approval_result: 'approved' },
      subTableBindingScopes: scopes(content.forms[0].tableBindings),
    } })
    if (!response.ok()) throw new Error(await response.text())
    await page.waitForTimeout(1500)
    mine = await tasks()
    for (let attempt = 0; attempt < 35 && !mine.some(t => /Subtask/.test(t.taskName)); attempt++) {
      await page.waitForTimeout(1000)
      mine = await tasks()
    }
  }
  writeFileSync('../.codex-1226-sample.json', JSON.stringify({ instance, data, tasks: mine.map(t => ({ id: t.taskId ?? t.id, name: t.taskName })) }, null, 2))
  const target = mine.find(t => /Subtask/.test(t.taskName))
  if (!target) throw new Error('MI subtask missing')
  const baselineFiles = databaseFiles()
  await page.goto(`${origin}/portal/tasks/${target.taskId ?? target.id}`)
  await page.waitForTimeout(7000)
  console.log('PAGE', (await page.locator('body').innerText()).slice(-10000))
  const caseFiles = page.locator('.sub-table-field').filter({ hasText: 'Case files' }).first()
  const partyFiles = page.locator('.sub-table-field').filter({ hasText: 'Party files' }).first()
  await caseFiles.scrollIntoViewIfNeeded()
  if (mode === 'after') {
    assert.match(await caseFiles.innerText(), /case-only-1226/)
    assert.doesNotMatch(await partyFiles.innerText(), /case-only-1226/)
  }
  await page.screenshot({ path: `user-portal/verification-screenshots/${runDate}_1226-${mode}-rows.png`, fullPage: true })
  if (mode === 'after') {
    const saved = page.waitForResponse(r => r.url().endsWith(`/tasks/${target.taskId}/submit`) && r.request().method() === 'POST')
    await page.getByRole('button', { name: 'Save', exact: true }).click()
    const result = await saved
    const saveBody = await result.json()
    assert.equal(result.status(), 200, JSON.stringify(saveBody))
    assert.equal(saveBody.success, true, JSON.stringify(saveBody))
    assert.deepEqual(businessRows(databaseFiles()), businessRows(baselineFiles))
    console.log('SAVE: database ownership unchanged')
  }
  const wait = page.waitForResponse(r => r.url().includes('/complete') && r.request().method() === 'POST')
  await page.getByRole('button', { name: /^(Approve|批准|核准)$/ }).first().click()
  await page.getByRole('dialog').last().getByRole('button', { name: /^(Confirm|确定|確認)$/ }).click()
  const response = await wait
  const payload = response.request().postDataJSON()
  const completeBody = await response.json()
  console.log('COMPLETE', response.status(), JSON.stringify(completeBody))
  writeFileSync(`../.codex-1226-${mode}-payload.json`, JSON.stringify(payload, null, 2))
  await page.waitForTimeout(1000)
  await page.screenshot({ path: `user-portal/verification-screenshots/${runDate}_1226-${mode}-submit.png`, fullPage: true })
  assert.equal(response.status(), mode === 'before' ? 403 : 200)
  assert.equal(completeBody.success, mode !== 'before')
  assert.deepEqual(errors, [])
  assert.deepEqual(businessRows(databaseFiles()), businessRows(baselineFiles))
  const remaining = await tasks()
  assert.equal(remaining.some(t => t.taskId === target.taskId), mode === 'before')
  assert.equal(remaining.filter(t => t.taskId !== target.taskId).length, 1)
  console.log('PASS: task transition and sibling preservation verified in database')
} finally { await browser.close() }
