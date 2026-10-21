/** Read-only DW target picker verification on an explicitly pinned synthetic FU. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'
const unitId = process.env.LINK_FIXTURE_UNIT_ID
assert.match(unitId ?? '', /^\d+$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 1000 } })
  const errors = []
  page.on('pageerror', e => errors.push(e.message))
  await loginViaDwPassword(page)
  const get = async path => (await (await page.request.get(`http://localhost:3000/api/v1${path}`)).json()).data
  const unit = await get(`/function-units/${unitId}`)
  assert.match(unit.name, /^Link Form verification /)
  const form = (await get(`/function-units/${unitId}/forms`)).find(f => f.formType === 'PROCESS')
  const binds = (await get(`/function-units/${unitId}/forms/${form.id}/bindings`)).filter(b => b.bindingType === 'SUB')
  const before = await get(`/api/forms/${form.id}/sub-table-views/${binds[0].id}`)
  await page.goto(`http://localhost:3000/dev/function-units/${unitId}`)
  await page.getByRole('tab', { name: /Form/ }).click()
  await page.getByRole('row').filter({ hasText: 'Synthetic PROCESS' }).getByRole('button', { name: 'Edit', exact: true }).click()
  await page.locator('.form-editor-view .fc-designer-wrapper').first().waitFor()
  await page.locator('.form-editor-view .designer-nav-btn').filter({ hasText: /Sub Table/ }).click()
  await page.getByRole('menuitem').filter({ hasText: 'Same lines' }).first().click()
  await page.getByRole('button', { name: 'List View', exact: true }).click()
  const view = page.locator('.sub-table-list-view:visible')
  await view.locator('.column-header').filter({ hasText: 'Other details' }).locator('.col-edit').click()
  const dialog = page.locator('.el-dialog:visible').filter({ hasText: 'Bound Sub Table' })
  await dialog.locator('.el-select').click()
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().waitFor()
  const optionTexts = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').allTextContents()
  assert.equal(optionTexts.length, 2)
  assert.ok(optionTexts.some(label => /case_ref · #/.test(label)))
  assert.ok(optionTexts.some(label => /case_ref_2 · #/.test(label)))
  assert.notEqual(optionTexts[0], optionTexts[1])
  const screenshot = fileURLToPath(new URL('../developer-workstation/verification-screenshots/2026-10-20_link-form-binding-labels.png', import.meta.url))
  await page.screenshot({ path: screenshot, fullPage: true })
  await page.keyboard.press('Escape')
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  assert.deepEqual(await get(`/api/forms/${form.id}/sub-table-views/${binds[0].id}`), before)
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ result: 'PASS', optionTexts, screenshot, designUnchanged: true }))
} finally { await browser.close() }
