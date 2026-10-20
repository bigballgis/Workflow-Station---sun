/** DW parity on a pinned synthetic FU; edits preview data only. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'
const unitId = process.env.FIXTURE_UNIT_ID
assert.match(unitId ?? '', /^\d+$/)
const origin = 'http://localhost:3000'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  await loginViaDwPassword(page)
  const unit = await (await page.request.get(`${origin}/api/v1/function-units/${unitId}`)).json()
  assert.ok(unit.data.tags.includes('batch-verification-synthetic'))
  await page.goto(`${origin}/dev/function-units/${unitId}`)
  await page.getByRole('tab', { name: /Form/ }).click()
  await page.getByRole('row').filter({ hasText: 'Synthetic TASK' }).getByRole('button', { name: 'Edit', exact: true }).click()
  await page.locator('.form-editor-view .fc-designer-wrapper').first().waitFor()
  await page.locator('.form-editor-view .header-actions').getByRole('button', { name: 'Preview', exact: true }).click()
  const preview = page.locator('.form-preview-dialog').last()
  await preview.waitFor()
  const writes = []
  page.on('request', request => { if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method())) writes.push(new URL(request.url()).pathname) })
  await preview.locator('.el-form-item').filter({ hasText: 'Case key' }).locator('input').first().fill('SYNTHETIC-PREVIEW')
  const grid = preview.locator('.sub-table-field').first()
  await grid.getByRole('button', { name: 'Batch Add', exact: true }).click()
  const dialog = page.locator('.batch-edit-dialog:visible')
  await dialog.waitFor()
  await dialog.getByRole('spinbutton', { name: 'Rows to add', exact: true }).fill('2')
  await dialog.locator('.el-form-item').filter({ hasText: 'Caption' }).getByRole('textbox').fill('Preview synthetic')
  await dialog.getByRole('spinbutton', { name: /Amount/ }).fill('3')
  await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  const rows = grid.locator('.el-table__body-wrapper tbody tr')
  assert.equal(await rows.count(), 2)
  await rows.first().locator('.el-checkbox').click()
  await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  await dialog.waitFor()
  assert.equal(await dialog.locator('table, input[type="checkbox"]').count(), 0)
  const caption = dialog.locator('.el-form-item').filter({ hasText: 'Caption' }).getByRole('textbox')
  assert.equal(await caption.inputValue(), '')
  await caption.fill('Preview uniform')
  const screenshot = fileURLToPath(new URL('../developer-workstation/verification-screenshots/2026-10-20_batch-outer-dw-native-edit.png', import.meta.url))
  await dialog.screenshot({ path: screenshot })
  await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  assert.match(await rows.first().innerText(), /Preview uniform/)
  assert.match(await rows.nth(1).innerText(), /Preview synthetic/)
  assert.deepEqual(writes, [], 'DW Preview must not save or reserve keys')
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ result: 'PASS', previewOnly: true, selectedRows: 1, screenshot }))
} finally { await browser.close() }
