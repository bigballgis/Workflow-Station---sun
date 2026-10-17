/** Real UI activation of a legacy, ID-less control on an owned synthetic form only. */
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
  const api = async (method, path, data) => {
    const response = await page.request.fetch(`${origin}/api/v1/function-units/${unitId}${path}`, { method, data })
    const body = await response.json()
    assert.ok(response.ok() && body.success !== false, `${method} ${path}: ${body.message}`)
    return body.data
  }
  assert.ok((await api('GET', '')).tags?.includes('batch-verification-synthetic'))
  const name = 'Synthetic legacy batch enable'
  let form = (await api('GET', '/forms')).find(item => item.formName === name)
  if (!form || form.configJson.rule.length === 0) {
    const tables = await api('GET', '/tables')
    const main = tables.find(table => table.tableType === 'MAIN')
    const sub = tables.find(table => table.tableType === 'SUB')
    assert.ok(main && sub)
    if (!form) form = await api('POST', '/forms', { formName: name, formType: 'TASK', scene: 'TASK', configJson: { rule: [] } })
    const bindings = await api('GET', `/forms/${form.id}/bindings`)
    if (!bindings.some(binding => binding.bindingType === 'PRIMARY')) {
      await api('POST', `/forms/${form.id}/bindings`, { tableId: main.id, bindingType: 'PRIMARY', bindingMode: 'EDITABLE' })
    }
    const foreignKey = sub.fieldDefinitions.find(field => field.isForeignKey && field.refTableId === main.id)
    const caption = sub.fieldDefinitions.find(field => field.displayName === 'Caption')
    assert.ok(foreignKey && caption)
    const binding = await api('POST', `/forms/${form.id}/bindings`, { tableId: sub.id, bindingType: 'SUB',
      bindingMode: 'EDITABLE', subMode: 'FULL', bindingLinkMode: 'structuralFk', foreignKeyField: foreignKey.fieldName })
    const rowRule = [{ type: 'input', field: caption.fieldName, title: 'Caption', props: {} }]
    await api('PUT', `/forms/${form.id}`, { formName: name, formType: 'TASK', scene: 'TASK', configJson: {
      rule: [{ type: 'subTable', _bindingId: binding.id, title: 'Synthetic legacy lines', props: {} }], options: {},
      subForms: { [binding.id]: { rule: rowRule, options: {} } },
      batchForms: { [binding.id]: { schemaVersion: 1, rule: rowRule, options: {} } },
    } })
    form = (await api('GET', '/forms')).find(item => item.id === form.id)
  }
  const before = form.configJson.rule.find(rule => rule.type === 'subTable')
  const firstOptIn = before.batchControlId === undefined
  if (firstOptIn) assert.equal(before.props?.batchControlId, undefined)
  else assert.equal(before.props?.allowBatchEdit, true, 'Only resume an already activated synthetic fixture')
  await page.goto(`${origin}/dev/function-units/${unitId}`)
  await page.getByRole('tab', { name: /Form/ }).click()
  await page.getByRole('row').filter({ hasText: name }).getByRole('button', { name: 'Edit', exact: true }).click()
  await page.locator('.fc-designer-wrapper ._fd-drag-mask').first().click()
  const setting = page.locator('.el-form-item').filter({ hasText: 'Allow Batch Edit' })
  const save = async () => {
    const result = Promise.race([
      page.waitForResponse(response => response.url().endsWith(`/forms/${form.id}`) && response.request().method() === 'PUT'),
      page.locator('.el-message--error').first().waitFor({ state: 'visible' }).then(() => null),
    ])
    await page.locator('.form-editor-view .header-actions').getByRole('button', { name: 'Save', exact: true }).click()
    const response = await result
    if (response) assert.ok(response.ok() && (await response.json()).success === true)
    await page.waitForFunction(() => [...document.querySelectorAll('.form-editor-view .header-actions button')]
      .some(button => button.textContent.trim() === 'Save' && !button.disabled && !button.classList.contains('is-loading')))
  }
  if (firstOptIn) {
    await save()
    const ordinary = (await api('GET', '/forms')).find(item => item.id === form.id)
    assert.equal(ordinary.configJson.rule.find(rule => rule.type === 'subTable').batchControlId, undefined,
      'Ordinary legacy save must not allocate an identity')
    await page.locator('.fc-designer-wrapper ._fd-drag-mask').first().click()
    await setting.locator('.el-switch').click()
  }
  await save()
  const errors = await page.locator('.el-message--error').allTextContents()
  const phase = process.env.FIXTURE_EXPECT_FAILURE === '1' ? 'before' : 'after'
  const screenshot = fileURLToPath(new URL(`../developer-workstation/verification-screenshots/2026-10-17_batch-legacy-enable-${unitId}-${phase}.png`, import.meta.url))
  await page.locator('.fc-designer-wrapper ._fd-drag-mask').first().click()
  await setting.locator('.el-switch').waitFor({ state: 'visible' })
  await page.locator('.form-editor-view').screenshot({ path: screenshot })
  const saved = (await api('GET', '/forms')).find(item => item.id === form.id)
  const control = saved.configJson.rule.find(rule => rule.type === 'subTable')
  console.log(JSON.stringify({ step: 'legacy-batch-enable', unitId, formId: form.id, errors,
    persistedId: control.batchControlId, firstOptIn, enabled: control.props?.allowBatchEdit, screenshot, pageErrors }))
  assert.deepEqual(errors, [])
  assert.match(control.batchControlId, /^stbc_[0-9a-f-]+$/i)
  assert.equal(control.props.allowBatchEdit, true)
  const batchProjection = forms => Object.fromEntries(Object.entries(forms).map(([id, definition]) => [id,
    { ...definition, rule: definition.rule.map(rule => ({ ...rule, props: rule.props ?? {} })) }]))
  assert.deepEqual(batchProjection(saved.configJson.batchForms), batchProjection(form.configJson.batchForms))
  if (!firstOptIn) assert.equal(control.batchControlId, before.batchControlId)
  await save()
  const again = (await api('GET', '/forms')).find(item => item.id === form.id)
  assert.equal(again.configJson.rule.find(rule => rule.type === 'subTable').batchControlId, control.batchControlId)
  await page.locator('.designer-grouped-nav .el-dropdown').first().locator('.designer-nav-btn').click()
  await page.locator('.el-dropdown-menu:visible .el-dropdown-menu__item').first().click()
  await page.getByRole('button', { name: 'Batch Form', exact: true }).click()
  await page.locator('.sub-table-design-wrapper .fc-designer-wrapper:visible').waitFor()
  await page.locator('.sub-table-design-wrapper').screenshot({ path: screenshot.replace('.png', '-designer.png') })
  assert.equal(pageErrors, 0)
} finally { await browser.close() }
