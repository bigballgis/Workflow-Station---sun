/** Real synthetic DW/Portal input bounds. Draft only; never Apply, Save or task actions. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'

const unitId = process.env.FIXTURE_UNIT_ID
const taskId = process.env.FIXTURE_TASK_ID
assert.ok(Boolean(unitId) !== Boolean(taskId), 'Choose exactly one explicit synthetic surface')
if (unitId) assert.match(unitId, /^\d+$/)
if (taskId) assert.match(taskId, /^[a-f0-9-]{36}$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  let pageErrors = 0
  page.on('pageerror', () => pageErrors++)
  let readBack
  let baseline
  if (unitId) {
    const origin = 'http://localhost:3102'
    await loginViaDwPassword(page, { loginOrigin: origin })
    const unit = await (await page.request.get(`${origin}/api/v1/function-units/${unitId}`)).json()
    assert.ok(unit.data?.tags?.includes('batch-verification-synthetic'))
    readBack = async () => (await (await page.request.get(`${origin}/api/v1/function-units/${unitId}/forms`)).json()).data
    baseline = await readBack()
    await page.goto(`${origin}/dev/function-units/${unitId}`)
    await page.getByRole('tab', { name: /Form/ }).click()
    await page.getByRole('row').filter({ hasText: 'Synthetic TASK' }).getByRole('button', { name: 'Edit', exact: true }).click()
    await page.locator('.form-editor-view .fc-designer-wrapper').first().waitFor()
    await page.locator('.form-editor-view .header-actions').getByRole('button', { name: 'Preview', exact: true }).click()
    await page.locator('.form-preview-dialog').last().getByRole('button', { name: 'Batch Edit', exact: true }).click()
  } else {
    await loginViaPortalPassword(page)
    const origin = 'http://localhost:3000/api/portal'
    const task = await (await page.request.get(`${origin}/tasks/${taskId}`)).json()
    assert.match(task.data?.functionUnitName || '', /^Batch verification /)
    readBack = async () => (await (await page.request.get(`${origin}/tasks/${taskId}/form-data`)).json()).data.fieldValues
    baseline = await readBack()
    await page.goto(`http://localhost:3101/portal/tasks/${taskId}`)
    await page.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  }
  const dialog = page.locator('.batch-edit-dialog')
  await dialog.waitFor()
  const writes = []
  page.on('request', request => { if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method())) writes.push(new URL(request.url()).pathname) })
  const row = dialog.locator('.batch-edit-dialog__table .el-table__body-wrapper tbody tr').first()
  const text = row.getByRole('textbox')
  const number = row.getByRole('spinbutton')
  assert.equal(await text.getAttribute('maxlength'), '12')
  assert.equal(await number.getAttribute('aria-valuemin'), '0')
  assert.equal(await number.getAttribute('aria-valuemax'), '10')
  await text.fill('')
  await text.pressSequentially('SyntheticABCDEFG')
  assert.equal(await text.inputValue(), 'SyntheticABC')
  await number.fill('10')
  await text.click()
  assert.match(await row.locator('.el-input-number__increase').getAttribute('class'), /is-disabled/)
  await number.fill('0')
  await text.click()
  assert.match(await row.locator('.el-input-number__decrease').getAttribute('class'), /is-disabled/)
  for (const label of ['Caption', 'Amount']) {
    const uniform = dialog.locator('.batch-edit-dialog__uniform .el-form-item').filter({ hasText: label })
    const mode = uniform.getByRole('combobox')
    await uniform.locator('.el-select').click()
    const listId = await mode.getAttribute('aria-controls')
    assert.ok(listId)
    await page.locator(`[id="${listId}"]`).getByRole('option', { name: 'Set value', exact: true }).click()
    if (label === 'Caption') assert.equal(await uniform.getByRole('textbox').last().getAttribute('maxlength'), '12')
    else {
      assert.equal(await uniform.getByRole('spinbutton').getAttribute('aria-valuemin'), '0')
      assert.equal(await uniform.getByRole('spinbutton').getAttribute('aria-valuemax'), '10')
    }
  }
  const surface = unitId ? 'developer-workstation' : 'user-portal'
  const shot = fileURLToPath(new URL(`../${surface}/verification-screenshots/2026-10-17_batch-input-constraints.png`, import.meta.url))
  await dialog.screenshot({ path: shot })
  await page.setViewportSize({ width: 600, height: 800 })
  await dialog.screenshot({ path: shot.replace('.png', '-narrow.png') })
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: 'OK', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  assert.deepEqual(await readBack(), baseline)
  assert.deepEqual(writes, [])
  assert.equal(pageErrors, 0)
  console.log(JSON.stringify({ step: 'batch-input-constraints', surface, unitId, taskId, pageErrors, writes: writes.length, screenshot: shot }))
} finally { await browser.close() }
