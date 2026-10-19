/** Exercise real designer Save and publication using an isolated synthetic clone. */
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const origin = 'http://localhost:3102'
const sourceId = process.env.FIXTURE_UNIT_ID || '50022'
const legacy = process.env.FIXTURE_EXPECT_LEGACY === '1'
assert.match(sourceId, /^\d+$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await loginViaDwPassword(page, { loginOrigin: origin })
  const api = async (method, path, data) => {
    const response = await page.request.fetch(`${origin}/api/v1${path}`, { method, data })
    const body = await response.json()
    assert.ok(response.ok() && body.success !== false, `${method} ${path}: ${body.message}`)
    return body.data
  }
  const source = await api('GET', `/function-units/${sourceId}`)
  assert.ok(source.tags.includes('batch-verification-synthetic'), 'Only synthetic fixtures may be cloned')
  const original = await api('GET', `/function-units/${sourceId}/validate`)
  assert.equal(original.valid, !legacy, JSON.stringify(original.errors))
  if (process.env.FIXTURE_CLONE_ID) assert.match(process.env.FIXTURE_CLONE_ID, /^\d+$/)
  const clone = process.env.FIXTURE_CLONE_ID
    ? await api('GET', `/function-units/${process.env.FIXTURE_CLONE_ID}`)
    : await api('POST', `/function-units/${sourceId}/clone?newName=${encodeURIComponent(`Batch binding UI verification ${Date.now()}`)}`)
  assert.ok(clone.tags.includes('batch-verification-synthetic'))
  assert.match(clone.name, /^Batch binding UI verification /)
  console.log(JSON.stringify({ step: 'cloned', sourceId, unitId: clone.id }))
  const base = `/function-units/${clone.id}`
  const forms = await api('GET', `${base}/forms`)
  const form = forms.find(item => item.formType === 'PROCESS')
  assert.ok(form)
  const output = new URL('../developer-workstation/verification-screenshots/', import.meta.url)
  mkdirSync(output, { recursive: true })
  const shot = name => fileURLToPath(new URL(`2026-10-19_batch-binding-${clone.id}-${name}.png`, output))
  await page.goto(`${origin}/dev/function-units/${clone.id}`)
  await page.getByRole('tab', { name: /Form/ }).click()
  await page.getByRole('row').filter({ hasText: form.formName }).getByRole('button', { name: 'Edit', exact: true }).click()
  const editor = page.locator('.form-editor-view')
  await editor.locator('.fc-designer-wrapper:visible ._fd-drag-mask').last().click()
  const assign = editor.locator('.el-form-item').filter({ hasText: 'Allow Batch Assign' }).locator('.el-switch')
  await assign.click()
  const save = editor.locator('.header-actions').getByRole('button', { name: 'Save', exact: true })
  await save.click()
  const message = page.locator('.el-message--error').last()
  await message.waitFor()
  assert.match(await message.innerText(), legacy ? /version 1 Batch Form/ : /MI Assignment.*turn off Allow Batch Assign/i)
  await message.screenshot({ path: shot(legacy ? 'assignment-message-before' : 'assignment-message') })
  await editor.screenshot({ path: shot(legacy ? 'editor-before' : 'editor-after') })
  if (legacy) {
    console.log(JSON.stringify({ step: 'reproduced', unitId: clone.id, validation: original, message: await message.innerText() }))
  } else {
  await assign.click()
  const savedResponse = page.waitForResponse(response => response.url().endsWith(`/forms/${form.id}`)
    && response.request().method() === 'PUT')
  await save.click()
  assert.equal((await (await savedResponse).json()).success, true)
  const saved = (await api('GET', `${base}/forms`)).find(item => item.id === form.id)
  const controls = saved.configJson.rule.filter(rule => rule.type === 'subTable')
  assert.ok(controls.length >= 2)
  assert.ok(controls.every(rule => rule._bindingId && rule.props._bindingId === undefined), 'Real UI Save must use root binding IDs')
  assert.equal((await api('GET', `${base}/validate`)).valid, true)
  await editor.screenshot({ path: shot('saved') })
  // Invalid publication must retain form/control names and recovery instructions.
  const invalidConfig = structuredClone(saved.configJson)
  invalidConfig.rule.find(rule => rule.type === 'subTable').props.allowBatchAssign = true
  await api('PUT', `${base}/forms/${form.id}`, { ...saved, configJson: invalidConfig })
  const invalid = await api('GET', `${base}/validate`)
  assert.equal(invalid.valid, false)
  assert.match(invalid.errors[0].message, /Form.*Sub-Table.*MI Assignment/)
  const poll = async id => {
    const deadline = Date.now() + 90000
    while (Date.now() < deadline) {
      const status = await api('GET', `/function-units/deployments/${id}/status`)
      if (!['PENDING', 'DEPLOYING'].includes(status.status)) return status
      await new Promise(resolve => setTimeout(resolve, 500))
    }
    throw new Error('Timed out waiting for isolated deployment')
  }
  const failed = await poll((await api('POST', `${base}/deploy`, { autoEnable: false, changeLog: 'Synthetic error-message verification' })).deploymentId)
  assert.equal(failed.status, 'FAILED')
  assert.match(failed.message, /Form.*Sub-Table.*MI Assignment/)
  assert.doesNotMatch(failed.message, /Deploy failed: Deploy failed/)
  await api('PUT', `${base}/forms/${form.id}`, saved)
  const deployed = await poll((await api('POST', `${base}/deploy`, { autoEnable: false, changeLog: 'Synthetic UI-save root binding verification' })).deploymentId)
  assert.equal(deployed.status, 'SUCCESS', deployed.message)
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ step: 'verified', sourceId, unitId: clone.id, formId: form.id,
    sourceValid: original.valid, controls: controls.length, deployment: deployed, invalidMessage: failed.message,
    screenshots: [shot('assignment-message'), shot('saved')], pageErrors: errors.length }))
  }
} finally {
  await browser.close()
}
