/** Saves only a pinned synthetic form; rejects accidental navigation to a real FU. */
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const unitId = process.env.FIXTURE_UNIT_ID
if (!/^\d+$/.test(unitId || '')) throw new Error('Explicit synthetic FIXTURE_UNIT_ID required')
const invalid = process.env.FIXTURE_EXPECT_INVALID === '1'
const origin = 'http://localhost:3102'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  const pageErrors = []
  page.on('pageerror', () => pageErrors.push('pageerror'))
  await loginViaDwPassword(page, { loginOrigin: origin })
  const response = await page.request.get(`${origin}/api/v1/function-units/${unitId}`)
  const body = await response.json()
  assert.equal(response.ok() && body.success !== false, true)
  assert.equal(body.data.tags?.includes('batch-verification-synthetic'), true, 'Refusing non-synthetic FU')
  assert.equal(body.data.canModify, true, 'The pinned synthetic fixture must be writable for the test account')
  const formsResponse = await page.request.get(`${origin}/api/v1/function-units/${unitId}/forms`)
  const forms = (await formsResponse.json()).data
  const form = forms.find(item => item.formName === 'Synthetic TASK' && item.formType === 'TASK')
  assert.ok(form, 'Pinned synthetic task form required')
  assert.ok(form.configJson?.rule?.length, 'Refusing a cleared fixture design')
  await page.goto(`${origin}/dev/function-units/${unitId}`)
  await page.getByRole('tab', { name: /Form/ }).click()
  await page.getByText('Synthetic TASK', { exact: true }).waitFor()
  let releaseLoad
  let loadingHeld
  if (process.env.FIXTURE_DELAY_LOAD === '1') {
    const hold = new Promise(resolve => { releaseLoad = resolve })
    let markHeld
    loadingHeld = new Promise(resolve => { markHeld = resolve })
    await page.route(`**/api/v1/function-units/${unitId}/forms/${form.id}/bindings`, async route => {
      markHeld()
      await hold
      await route.continue()
    })
  }
  await page.getByRole('row').filter({ hasText: 'Synthetic TASK' }).getByRole('button', { name: 'Edit', exact: true }).click()
  const editor = page.locator('.form-editor-view')
  await editor.waitFor()
  if (loadingHeld) {
    let deadline
    try {
      await Promise.race([loadingHeld, new Promise((_, reject) => {
        deadline = setTimeout(() => reject(new Error('Binding request was not observed')), 10000)
      })])
      assert.equal(await editor.locator('.header-actions').getByRole('button', { name: 'Save', exact: true }).isDisabled(), true,
        'Save must be locked before asynchronous form configuration reaches the canvas')
    } finally { clearTimeout(deadline); releaseLoad() }
  }
  await editor.locator('.fc-designer-zoom-stage > *').first().waitFor()
  await editor.locator('.fc-designer-wrapper').first().getByText('Case key', { exact: true }).first().waitFor()
  const writes = []
  page.on('response', result => {
    if (result.url().endsWith(`/forms/${form.id}`) && result.request().method() === 'PUT') writes.push(result)
  })
  await editor.locator('.header-actions').getByRole('button', { name: 'Save', exact: true }).click()
  await page.locator('.el-message').first().waitFor()
  if (invalid) {
    const error = page.locator('.el-message--error')
    await error.waitFor()
    assert.match(await error.innerText(), /Batch Add.*required|Batch.*business|required.*field/i)
    assert.equal(writes.length, 0, 'Invalid membership design must not call form persistence')
  } else {
    await page.locator('.el-message--success').waitFor()
    assert.equal(writes.length, 1, 'Valid batch design must issue exactly one form save')
    assert.equal((await writes[0].json()).success, true)
    const saved = writes[0].request().postDataJSON().configJson
    const controls = []
    const visit = rules => {
      for (const rule of rules || []) {
        if (rule.type === 'subTable') controls.push(rule)
        if (Array.isArray(rule.children)) visit(rule.children)
      }
    }
    visit(saved.rule)
    assert.equal(controls.length, 1, 'Save must preserve the loaded sub-table control')
    assert.equal(controls[0].props.allowBatchAdd, true)
    assert.ok(controls[0].batchControlId || controls[0].props.batchControlId)
    assert.equal(Object.keys(saved.batchForms || {}).length, 1)
    const reloadedResponse = await page.request.get(`${origin}/api/v1/function-units/${unitId}/forms`)
    const reloaded = (await reloadedResponse.json()).data.find(item => item.id === form.id)
    assert.deepEqual(reloaded.configJson.rule, saved.rule, 'Reload must retain the saved rules')
    assert.deepEqual(reloaded.configJson.batchForms, saved.batchForms, 'Reload must retain the independent batch form')
  }
  const output = new URL('../developer-workstation/verification-screenshots/', import.meta.url)
  mkdirSync(output, { recursive: true })
  await editor.screenshot({ path: fileURLToPath(new URL(`${new Date().toISOString().slice(0, 10)}_batch-designer-${invalid ? 'invalid-default' : 'valid-default'}-${unitId}.png`, output)) })
  if (invalid) await page.locator('.el-message--error').screenshot({ path: fileURLToPath(new URL(`${new Date().toISOString().slice(0, 10)}_batch-designer-invalid-default-message-${unitId}.png`, output)) })
  assert.deepEqual(pageErrors, [], 'Real designer interaction must not produce browser runtime errors')
  console.log(`PASS: synthetic designer ${invalid ? 'rejects missing required readonly default' : 'saves valid readonly default'}`)
} finally { await browser.close() }
