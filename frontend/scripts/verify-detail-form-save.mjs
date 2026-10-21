/** Independent test forms only. Never opens or saves the user's unsaved business form. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'

const unitId = process.env.DETAIL_FIXTURE_UNIT_ID
assert.match(unitId ?? '', /^\d+$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const login = await loginViaDwPassword(page)
  const base = `/function-units/${unitId}`
  const api = async (method, path, data) => {
    const response = await page.request.fetch(`http://localhost:3000/api/v1${path}`, { method, data,
      headers: { 'X-User-Id': login.userId, 'Accept-Language': 'en' } })
    const body = await response.json()
    assert.ok(response.ok() && body.success, `${method} ${path}: ${JSON.stringify(body.error)}`)
    return body.data
  }
  const unit = await api('GET', base)
  assert.match(unit.name, /^(Link Form verification |Detail Form verification )/)
  const tables = await api('GET', `${base}/tables`)
  const target = tables.find(table => table.tableType === 'SUB')
  assert.ok(target)
  const businessBefore = await api('GET', '/function-units/50083/forms/50857')
  let form
  if (process.env.DETAIL_FIXTURE_FORM_ID) {
    assert.match(process.env.DETAIL_FIXTURE_FORM_ID, /^\d+$/)
    form = await api('GET', `${base}/forms/${process.env.DETAIL_FIXTURE_FORM_ID}`)
  } else {
    form = await api('POST', `${base}/forms`, { formName: `Detail save verification ${Date.now()}`,
      formType: 'DETAIL', boundTableId: target.id, configJson: { rule: [
        { type: 'input', field: 'canvas_new_one', title: 'New Input One' },
        { type: 'input', field: 'canvas_new_two', title: 'New Input Two' },
      ], options: {} } })
  }
  assert.match(form.formName, /^Detail save verification /)
  assert.equal(form.boundTableId, target.id)
  console.log(JSON.stringify({ step: 'fixture', unitId, formId: form.id, boundTableId: target.id }))
  const formPath = `${base}/forms/${form.id}`
  const beforeBindings = await api('GET', `${formPath}/bindings`)
  await page.goto(`http://localhost:3000/dev/function-units/${unitId}`)
  await page.getByRole('tab', { name: 'Form Design', exact: true }).click()
  await page.locator('.form-scene-tabs').getByRole('tab', { name: /Views/ }).click()
  await page.locator('.views-form-card').filter({ hasText: form.formName }).click()
  await page.locator('.form-editor-view .fc-designer-wrapper').first().waitFor()
  const repair = beforeBindings.some(binding => binding.bindingType === 'PRIMARY') ? null
    : page.waitForResponse(response => new URL(response.url()).pathname.endsWith(`${formPath}/repair-config`)
      && response.request().method() === 'POST' && response.request().postDataJSON()?.apply === true)
  const save = page.waitForResponse(response => new URL(response.url()).pathname.endsWith(formPath)
    && response.request().method() === 'PUT').catch(error => ({ error: error.message }))
  await page.locator('.form-editor-view .editor-header').getByRole('button', { name: 'Save', exact: true }).click()
  const stage = process.env.DETAIL_VERIFY_STAGE ?? 'after'
  const screenshot = fileURLToPath(new URL(`../developer-workstation/verification-screenshots/2026-10-21_detail-save-${stage}-${form.id}.png`, import.meta.url))
  if (repair) {
    const repaired = await repair
    const body = await repaired.json()
    if (!repaired.ok() || !body.success) {
      await page.screenshot({ path: screenshot, fullPage: true })
      console.log(JSON.stringify({ result: 'FAIL', status: repaired.status(), error: body.error, screenshot }))
    }
    assert.ok(repaired.ok() && body.success, 'Manual Save must repair the explicitly selected table without guessing field names')
  }
  const saved = await save
  assert.ok(!('error' in saved), JSON.stringify(saved))
  assert.equal((await saved.json()).success, true)
  const persisted = await api('GET', formPath)
  const bindings = await api('GET', `${formPath}/bindings`)
  assert.equal(persisted.boundTableId, target.id)
  assert.deepEqual(persisted.configJson.rule.map(rule => rule.field), ['canvas_new_one', 'canvas_new_two'])
  assert.equal(bindings.filter(binding => binding.bindingType === 'PRIMARY' && binding.tableId === target.id).length, 1)
  assert.equal((await api('GET', `${base}/tables`)).length, tables.length, 'No inferred/duplicate table may be created')
  await page.reload()
  await page.getByRole('tab', { name: 'Form Design', exact: true }).click()
  await page.locator('.form-scene-tabs').getByRole('tab', { name: /Views/ }).click()
  await page.locator('.views-form-card').filter({ hasText: form.formName }).click()
  await page.locator('.form-editor-view .fc-designer-wrapper').first().waitFor()
  await page.getByText('New Input One', { exact: true }).first().waitFor()
  await page.screenshot({ path: screenshot, fullPage: true })
  assert.deepEqual(await api('GET', '/function-units/50083/forms/50857'), businessBefore)
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ result: 'PASS', unitId, formId: form.id, targetTableId: target.id,
    primaryBindings: bindings.length, ruleFields: persisted.configJson.rule.map(rule => rule.field),
    reload: 'PASS', businessFormUnchanged: true, errors, screenshot }))
} finally { await browser.close() }
