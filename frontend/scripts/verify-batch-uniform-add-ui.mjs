/** Real UUID Add + uniform values, scoped to an explicitly pinned synthetic task only. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId || '', /^[a-f0-9-]{36}$/)
const legacy = process.env.FIXTURE_EXPECT_LEGACY === '1'
const origin = 'http://localhost:3101'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 1000 } })
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await loginViaPortalPassword(page, { loginOrigin: origin })
  const read = async path => {
    const response = await page.request.get(`${origin}/api/portal${path}`)
    const body = await response.json()
    assert.ok(response.ok() && body.success, `${path}: ${body.message}`)
    return body.data
  }
  const task = await read(`/tasks/${taskId}`)
  assert.equal(task.functionUnitName, 'Batch verification uniform1019')
  const baseline = await read(`/tasks/${taskId}/form-data`)
  const control = Object.values(baseline.subTableBatchCapabilities).find(item => item.capabilities.allowBatchAdd)
  const binding = control.rowDesign.binding
  const pk = binding.primaryKeyFields[0]
  const caption = control.batchForm.rule.find(rule => rule.type === 'input' && rule.field !== pk).field
  const amount = control.batchForm.rule.find(rule => rule.type === 'inputNumber').field
  const store = `dw:${binding.tableName}`
  const rowsOf = data => data.fieldValues.__subTables__[store]
  const initial = rowsOf(baseline)
  await page.goto(`${origin}/portal/tasks/${taskId}`)
  await page.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  const dialog = page.locator('.batch-edit-dialog:visible')
  const rows = dialog.locator('.el-table__body-wrapper tr')
  await rows.nth(initial.length - 1).waitFor()
  await dialog.locator('.batch-edit-dialog__add .el-input-number input').fill('3')
  await dialog.getByRole('button', { name: 'Add', exact: true }).click()
  assert.equal(await rows.count(), initial.length + 3)
  const uniform = dialog.locator('.batch-edit-dialog__uniform .el-form-item')
  for (const label of ['Caption', 'Amount']) {
    const item = uniform.filter({ hasText: label })
    await item.scrollIntoViewIfNeeded()
    await item.locator('.el-select').click()
    const menuId = await item.getByRole('combobox').getAttribute('aria-controls')
    assert.ok(menuId)
    const option = page.locator(`[id="${menuId}"]`).getByRole('option', { name: 'Set value', exact: true })
    await option.waitFor({ state: 'visible' })
    await option.click()
    await item.locator('.batch-edit-dialog__uniform-value input').fill('6')
    await item.locator('.batch-edit-dialog__uniform-value input').blur()
    console.log(JSON.stringify({ step: 'uniform-value-set', label }))
  }
  assert.equal(await rows.locator('input[type="checkbox"]:checked').count(), 0)
  const applies = []
  page.on('request', request => { if (request.url().endsWith(`/tasks/${taskId}/submit`)) applies.push(request) })
  await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
  const error = page.locator('.el-message--error').last()
  await error.waitFor()
  assert.match(await error.innerText(), legacy ? /Check Caption in row/ : /Select.*rows.*uniform/i)
  assert.equal(applies.length, 0)
  if (!legacy) {
    await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
    assert.equal(await page.locator('.el-message--error').count(), 1, 'Repeated Apply must group the selection warning')
  }
  const shot = suffix => fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-19_batch-uniform-${legacy ? 'before' : 'after'}-${suffix}.png`, import.meta.url))
  await dialog.screenshot({ path: shot('no-selection') })
  await error.screenshot({ path: shot('message') })
  for (let i = initial.length; i < initial.length + 3; i++) await rows.nth(i).locator('.el-checkbox').click()
  if (!legacy) {
    assert.match(await dialog.locator('.batch-edit-dialog__selection').innerText(), /Selected rows: 3/)
    await dialog.screenshot({ path: shot('selected') })
  }
  const savedResponse = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
  const response = await savedResponse
  assert.equal(response.ok() && (await response.json()).success, true)
  const saved = rowsOf(await read(`/tasks/${taskId}/form-data`))
  assert.equal(saved.length, initial.length + 3)
  assert.deepEqual(saved.slice(0, initial.length).map(row => [row[pk], row[caption], row[amount]]), initial.map(row => [row[pk], row[caption], row[amount]]))
  assert.ok(saved.slice(initial.length).every(row => row[caption] === '6' && row[amount] === 6 && /^[a-f0-9-]{36}$/.test(row[pk])))
  assert.equal(new Set(saved.map(row => row[pk])).size, saved.length)
  assert.deepEqual(pageErrors, [])
  console.log(JSON.stringify({ result: 'PASS', legacy, taskId, before: initial.length, after: saved.length,
    checks: 'unselected blocked, selected fresh rows receive text/number, UUIDs unique, existing rows unchanged, GET persisted', pageErrors }))
} finally { await browser.close() }
