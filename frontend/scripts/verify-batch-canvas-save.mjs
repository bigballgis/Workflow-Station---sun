/** Synthetic normal DW Save compatibility only; no simulated canvas faults. */
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const unitId = process.env.FIXTURE_UNIT_ID
assert.match(unitId || '', /^\d+$/)
const origin = 'http://localhost:3102'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  let pageErrors = 0
  page.on('pageerror', () => pageErrors++)
  await loginViaDwPassword(page, { loginOrigin: origin })
  const unit = await (await page.request.get(`${origin}/api/v1/function-units/${unitId}`)).json()
  assert.ok(unit.data?.tags?.includes('batch-verification-synthetic'))
  const readForm = async () => (await (await page.request.get(`${origin}/api/v1/function-units/${unitId}/forms`)).json())
    .data.find(form => form.formType === 'TASK' && form.formName === 'Synthetic TASK')
  const original = await readForm()
  assert.ok(original?.configJson?.batchForms)
  const project = config => Object.entries(config.batchForms).map(([binding, form]) => ({ binding, schemaVersion: form.schemaVersion,
    fields: form.rule.filter(rule => rule.field).map(rule => ({ field: rule.field, type: rule.type,
      min: rule.props?.min, max: rule.props?.max, maxlength: rule.props?.maxlength })) }))
  const baseline = project(original.configJson)
  await page.goto(`${origin}/dev/function-units/${unitId}`)
  await page.getByRole('tab', { name: /Form/ }).click()
  await page.getByRole('row').filter({ hasText: 'Synthetic TASK' }).getByRole('button', { name: 'Edit', exact: true }).click()
  await page.locator('.form-editor-view .fc-designer-wrapper').first().waitFor()
  const save = async () => {
    const response = page.waitForResponse(result => result.url().endsWith(`/forms/${original.id}`) && result.request().method() === 'PUT')
    await page.locator('.form-editor-view .header-actions').getByRole('button', { name: 'Save', exact: true }).click()
    const result = await response
    assert.equal(result.ok(), true)
    assert.equal((await result.json()).success, true)
    assert.deepEqual(project((await readForm()).configJson), baseline)
  }
  await save() // Unvisited Batch Form preserves its saved definition.
  await page.locator('.designer-grouped-nav .el-dropdown').first().locator('.designer-nav-btn').click()
  const option = page.locator('.el-dropdown-menu:visible .el-dropdown-menu__item')
  await option.first().waitFor()
  assert.equal(await option.count(), 1)
  await option.click()
  await page.getByRole('button', { name: 'Batch Form', exact: true }).click()
  await page.locator('.sub-table-design-wrapper .fc-designer-wrapper:visible').waitFor()
  await save() // Mounted healthy Batch Form must collect its live rule/options.
  await page.locator('.form-editor-view .header-actions').getByRole('button', { name: 'Preview', exact: true }).click()
  await page.locator('.form-preview-dialog').last().getByRole('button', { name: 'Batch Edit', exact: true }).click()
  const dialog = page.locator('.batch-edit-dialog')
  await dialog.waitFor()
  assert.equal(await dialog.locator('.el-table__body-wrapper tbody tr').count(), 2)
  await page.waitForFunction(() => !document.querySelector('.dialog-fade-enter-active'))
  const screenshot = fileURLToPath(new URL('../developer-workstation/verification-screenshots/2026-10-17_batch-canvas-save.png', import.meta.url))
  await dialog.screenshot({ path: screenshot })
  assert.equal(pageErrors, 0)
  console.log(JSON.stringify({ step: 'batch-canvas-normal-save', unitId, formId: original.id, saves: 2, pageErrors,
    verified: 'unvisited and mounted healthy canvas; failure branches are unit-only', screenshot }))
} finally { await browser.close() }
