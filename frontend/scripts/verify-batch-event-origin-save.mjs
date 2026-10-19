/** Test actual Batch Form hydration/Save on an isolated synthetic clone, not a user's design. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
  await loginViaDwPassword(page, { loginOrigin: 'http://localhost:3102' })
  const api = async (method, path, data) => {
    const response = await page.request.fetch(`http://localhost:3102/api/v1${path}`, { method, data })
    const body = await response.json()
    assert.ok(response.ok() && body.success !== false, `${path}: ${body.message}`)
    return body.data
  }
  const source = await api('GET', '/function-units/50022')
  assert.ok(source.tags.includes('batch-verification-synthetic'))
  const unit = process.env.FIXTURE_UNIT_ID ? await api('GET', `/function-units/${process.env.FIXTURE_UNIT_ID}`)
    : await api('POST', `/function-units/50022/clone?newName=${encodeURIComponent(`Batch event origin verification ${Date.now()}`)}`)
  assert.match(unit.name, /^Batch event origin verification /)
  console.log(JSON.stringify({ step: 'origin-clone', unitId: unit.id }))
  const base = `/function-units/${unit.id}`
  const form = (await api('GET', `${base}/forms`)).find(form => form.formType === 'PROCESS')
  const bindingId = Object.keys(form.configJson.batchForms).at(-1)
  for (const rule of form.configJson.batchForms[bindingId].rule) {
    rule.on = { change: '$FNX:', blur: '$FNX:', focus: '$FNX:' }
  }
  await api('PUT', `${base}/forms/${form.id}`, form)
  await page.goto(`http://localhost:3102/dev/function-units/${unit.id}`)
  await page.getByRole('tab', { name: /Form/ }).click()
  await page.getByRole('row').filter({ hasText: form.formName }).getByRole('button', { name: 'Edit', exact: true }).click()
  await page.locator('.designer-grouped-nav .el-dropdown').first().locator('.designer-nav-btn').click()
  await page.locator('.el-dropdown-menu:visible .el-dropdown-menu__item').last().click()
  await page.getByRole('button', { name: 'Batch Form', exact: true }).click()
  await page.locator('.sub-table-design-wrapper:visible ._fd-drag-mask:visible').first().click()
  const savedResponse = page.waitForResponse(response => response.url().endsWith(`/forms/${form.id}`) && response.request().method() === 'PUT')
  await page.locator('.form-editor-view .header-actions').getByRole('button', { name: 'Save', exact: true }).click()
  assert.equal((await (await savedResponse).json()).success, true)
  const saved = (await api('GET', `${base}/forms`)).find(item => item.id === form.id)
  for (const rule of saved.configJson.batchForms[bindingId].rule) {
    assert.equal(rule.on, undefined, 'Real Batch Form Save must remove empty event adapters')
    assert.equal(rule._on, undefined)
  }
  console.log(JSON.stringify({ step: 'origin-save-verified', unitId: unit.id, formId: form.id, bindingId,
    fields: saved.configJson.batchForms[bindingId].rule.map(rule => rule.field) }))
} finally { await browser.close() }
