import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs'
import { chromium } from 'playwright'
import { loginViaPortalPassword, loginViaDwPassword } from './playwright-login.mjs'

const origin = process.env.ORIGIN ?? 'http://localhost:3000'
const fuCode = process.env.FU_CODE ?? 'mcy-1232-ui-verification-20261031-904pwn'
const samplePath = '../.codex-1232-sample.json'
const shots = `${process.env.MODE === 'import' ? 'developer-workstation' : 'user-portal'}/verification-screenshots`
mkdirSync(shots, { recursive: true })
const browser = await chromium.launch({ headless: true })
const statePath = '../.codex-1232-session.json'
const cachedSession = process.env.SESSION === '1' ? JSON.parse(readFileSync(statePath, 'utf8')) : undefined
const context = await browser.newContext({ viewport: { width: 1600, height: 1100 }, storageState: cachedSession?.state })
const page = await context.newPage()
page.setDefaultTimeout(15000)
const errors = []
page.on('pageerror', error => errors.push(error.message))
const submits = []
page.on('response', async response => {
  if (response.url().endsWith('/submit') && response.request().method() === 'POST') {
    submits.push({ status: response.status(), body: await response.json() })
  }
})
async function screenshot(name) {
  const path = `${shots}/2026-10-31_1232-${name}.png`
  await page.screenshot({ path, fullPage: true })
  console.log('SCREENSHOT', path)
}
async function selectUser(scope) {
  const lookup = scope.locator('.lookup-field').last()
  if (await lookup.locator('.lookup-selected-close').count()) await lookup.locator('.lookup-selected-close').click()
  await lookup.locator('input').fill('developer')
  const row = page.locator('.lookup-dropdown:visible').getByRole('row').filter({ hasText: /^developer$/ }).first()
  await row.waitFor()
  await row.click()
}
async function readRows(taskId) {
  const response = await page.request.get(`${origin}/api/portal/tasks/${taskId}/form-data`)
  assert.equal(response.status(), 200)
  const body = await response.json()
  const data = body.data ?? body
  const rows = Object.values(data.fieldValues.__subTables__).find(rows =>
    Array.isArray(rows) && rows.some(row => String(row.card_number).startsWith('1232-')))
  assert(rows, 'MCY verification rows')
  return rows
}
async function waitSaved(taskId, predicate) {
  for (let i = 0; i < 30; i++) {
    const rows = await readRows(taskId)
    if (predicate(rows)) return rows
    await page.waitForTimeout(500)
  }
  throw new Error('Persisted rows did not match the expected UI change')
}
async function start() {
  await page.goto(`${origin}/portal/processes/start/${fuCode}`)
  const grid = page.locator('.sub-table-field').first()
  await grid.getByRole('button', { name: 'Add', exact: true }).waitFor()
  for (const letter of ['A', 'B', 'C']) {
    await grid.getByRole('button', { name: 'Add', exact: true }).click()
    const dialog = page.getByRole('dialog').last()
    await dialog.locator('.el-form-item').filter({ hasText: 'Card Number' }).locator('input').fill(`1232-${letter}`)
    await dialog.getByRole('button', { name: 'Save', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
  }
  await screenshot('after-batch-start')
  const responsePromise = page.waitForResponse(r => r.url().endsWith(`/${fuCode}/start`) && r.request().method() === 'POST')
  await page.getByRole('button', { name: 'Case Submit', exact: true }).click()
  const response = await responsePromise
  const body = await response.json()
  assert.equal(response.status(), 200, JSON.stringify(body))
  const result = body.data ?? body
  const pid = result.processInstanceId ?? result.id
  assert.match(pid, /^[0-9a-f-]{36}$/)
  const taskId = execFileSync('docker', ['exec', 'platform-postgres-dev', 'psql', '-U', 'platform_dev', '-d', 'workflow_platform_dev', '-Atc',
    `select id_ from act_ru_task where proc_inst_id_='${pid}' and name_='Transaction Assignment'`], { encoding: 'utf8' }).trim()
  assert.match(taskId, /^[0-9a-f-]{36}$/)
  writeFileSync(samplePath, JSON.stringify({ fuCode, pid, taskId }, null, 2))
  return { pid, taskId }
}
try {
  if (process.env.MODE === 'import') {
    await loginViaDwPassword(page)
    await page.goto(`${origin}/dev/function-units/${process.env.FU_ID ?? '50123'}`)
    await page.getByRole('button', { name: 'Function Unit Settings', exact: true }).waitFor()
    await page.waitForTimeout(500)
    await page.getByRole('tab', { name: 'Form Design', exact: true }).click()
    await page.getByRole('row').filter({ hasText: 'Transaction Request Form - Transaction Assignment' }).getByRole('button', { name: 'Edit', exact: true }).click()
    await page.locator('.nav-label').filter({ hasText: 'HMDC Transaction' }).click()
    await page.getByRole('menuitem').filter({ hasText: 'HMDC Transaction' }).click()
    await page.getByText('Batch Form', { exact: true }).click()
    await page.getByRole('button', { name: 'Import Table Fields', exact: true }).click()
    const row = page.getByRole('dialog').getByRole('row').filter({ hasText: 'transaction_date' })
    assert.equal(await row.locator('.already-imported-icon').count(), 0)
    await row.locator('.el-checkbox').click()
    await page.getByRole('dialog').getByRole('button', { name: 'Import (1)', exact: true }).click()
    const canvas = page.locator('.sub-table-design-wrapper:visible .fc-designer-wrapper:visible')
    await canvas.getByText('Transaction Date', { exact: true }).waitFor()
    await screenshot('after-batch-import')
    const saved = page.waitForResponse(response => response.request().method() === 'PUT' && /\/forms\/\d+$/.test(response.url()))
    await page.locator('.editor-header').getByRole('button', { name: 'Save', exact: true }).click()
    assert.equal((await saved).status(), 200)
    const forms = (await (await page.request.get(`${origin}/api/v1/function-units/${process.env.FU_ID ?? '50123'}/forms`)).json()).data
    const form = forms.find(item => item.formName.endsWith('Transaction Assignment'))
    const binding = form.tableBindings.find(item => item.bindingType === 'SUB')
    assert(JSON.stringify(form.configJson.batchForms[binding.id]).includes('transaction_date'))
    console.log('PASS independent Batch import')
  } else {
    const user = cachedSession?.user ?? await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
    if (!cachedSession) writeFileSync(statePath, JSON.stringify({ state: await context.storageState(), user }))
    const sample = process.env.REUSE === '1' ? JSON.parse(readFileSync(samplePath, 'utf8')) : await start()
    const { taskId } = sample
    assert.match(taskId, /^[0-9a-f-]{36}$/)
    assert.match(sample.pid, /^[0-9a-f-]{36}$/)
    assert.match(user.userId, /^[A-Za-z0-9-]+$/)
    await page.goto(`${origin}/portal/tasks/${taskId}`)
    const claim = page.getByRole('button', { name: 'Claim', exact: true })
    await page.getByText('Transaction Assignment', { exact: true }).first().waitFor()
    await page.waitForTimeout(1000)
    if (await claim.isVisible()) await claim.click()
    await page.getByRole('button', { name: 'Edit', exact: true }).first().waitFor()
    if (process.env.MODE !== 'batch-only') {
      await page.getByRole('button', { name: 'Edit', exact: true }).first().click()
      const dialog = page.getByRole('dialog').last()
      await selectUser(dialog)
      const email = dialog.locator('.el-form-item').filter({ hasText: 'Assignee email' }).locator('input')
      assert.match(await email.inputValue(), /@/)
      await screenshot('after-edit-email')
      await dialog.getByRole('button', { name: 'Save', exact: true }).click()
      await waitSaved(taskId, rows => rows[0]?.assignee_id?.id === user.userId && !!rows[0]?.merchant_name)
      await page.getByText('Details', { exact: true }).first().click()
      const link = page.locator('.link-form-modal-panel')
      const linkEmail = link.locator('.el-form-item').filter({ hasText: 'Assignee email' }).locator('input')
      await linkEmail.fill('')
      await selectUser(link)
      assert.match(await linkEmail.inputValue(), /@/, 'Details must fill email after clearing the previous Edit value')
      await link.getByRole('button', { name: 'Save', exact: true }).click()
      await waitSaved(taskId, rows => rows[0]?.assignee_id?.id === user.userId && !!rows[0]?.merchant_name)
    }
    const before = await readRows(taskId)
    const grid = page.locator('.sub-table-field').first()
    const gridRows = grid.locator('.el-table__body-wrapper tr.el-table__row')
    await page.waitForTimeout(2500)
    await gridRows.nth(0).locator('.el-checkbox__inner').click()
    await gridRows.nth(1).locator('.el-checkbox__inner').click()
    console.log('SELECTION', await gridRows.locator('input[type="checkbox"]').evaluateAll(inputs => inputs.map(input => input.checked)))
    await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
    const batch = page.getByRole('dialog').last()
    await batch.getByRole('radio').first().click()
    await selectUser(batch.locator('.mi-assignment-widget'))
    if (process.env.GENERIC_LOOKUP === '1') await selectUser(batch.locator('.el-form-item').filter({ hasText: 'Lookup reviewer' }))
    await screenshot('batch-before-apply')
    await batch.getByRole('button', { name: 'Apply to page', exact: true }).click()
    await batch.waitFor({ state: 'hidden' })
    const after = await waitSaved(taskId, rows => rows.slice(0, 2).every(row => row.assignee_id?.id === user.userId && !!row.merchant_name))
    if (process.env.GENERIC_LOOKUP === '1') {
      assert(after.slice(0, 2).every(row => row.authorization_code?.id === user.userId))
      assert(after.slice(0, 2).every(row => row.merchant_name.endsWith(`|${row.card_number}`)), 'each event must read its own row')
    }
    // The server advances its concurrency version on the submitted binding scope.
    const businessRow = ({ _wsRowVersion, ...row }) => row
    assert.deepEqual(businessRow(after[2]), businessRow(before[2]), 'unselected business data must remain unchanged')
    await page.reload()
    await page.getByRole('button', { name: 'Edit', exact: true }).first().waitFor()
    await screenshot('after-batch-persisted')
    assert.equal(submits.filter(item => item.status >= 400).length, 0, JSON.stringify(submits.map(item => ({ status: item.status, message: item.body.message }))))
    if (process.env.COMPLETE === '1') {
      await page.getByRole('button', { name: 'Edit', exact: true }).last().click()
      const dialog = page.getByRole('dialog').last()
      await selectUser(dialog)
      await dialog.getByRole('button', { name: 'Save', exact: true }).click()
      await waitSaved(taskId, rows => rows[2]?.assignee_id?.id === user.userId)
      await page.getByRole('button', { name: 'Confirmed all Assignment', exact: true }).click()
      const completion = page.waitForResponse(response => response.url().endsWith(`/${taskId}/complete`))
      await page.getByRole('dialog').last().getByRole('button', { name: 'Confirm', exact: true }).click()
      const response = await completion
      assert.equal(response.status(), 200, await response.text())
      const taskCount = execFileSync('docker', ['exec', 'platform-postgres-dev', 'psql', '-U', 'platform_dev', '-d', 'workflow_platform_dev', '-Atc',
        `select count(*) from act_ru_task where proc_inst_id_='${sample.pid}' and name_='Transaction Investigation' and assignee_='${user.userId}'`], { encoding: 'utf8' }).trim()
      assert.equal(taskCount, '3', 'all three downstream personal tasks must exist')
      await screenshot('after-complete')
      console.log('PASS Complete created three assigned investigation tasks')
    }
    console.log('PASS Edit, Link Form, Batch Assign with email persisted; unselected row unchanged', sample)
  }
  assert.deepEqual(errors, [], 'browser runtime errors')
} catch (error) {
  console.log('BROWSER ERRORS', errors)
  console.log('SUBMIT RESULTS', submits.map(item => ({ status: item.status, message: item.body.message ?? item.body.error?.message })))
  await screenshot('verification-failure')
  console.log((await page.locator('body').innerText()).slice(-6500))
  throw error
} finally { await browser.close() }
