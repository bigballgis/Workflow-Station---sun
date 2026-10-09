#!/usr/bin/env node
/** Read-only browser regression for the two Phase 2 semantic Compare defects. */
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const origin = 'http://localhost:3000'
const outputDir = resolve(dirname(fileURLToPath(import.meta.url)),
  '../developer-workstation/verification-screenshots')
mkdirSync(outputDir, { recursive: true })
const screenshotSuffix = process.env.VERIFY_SCREENSHOT_SUFFIX || ''
assert.match(screenshotSuffix, /^[a-z0-9-]*$/, 'Invalid screenshot suffix')
const prefix = `${new Date().toISOString().slice(0, 10)}_semantic-bugfix` +
  (screenshotSuffix ? `-${screenshotSuffix}` : '')
const browser = await chromium.launch({ headless: true,
  ...(process.env.PLAYWRIGHT_EXECUTABLE_PATH
    ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH }
    : { channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' }) })

async function compareApi(page, fu, base, target) {
  const response = await page.request.get(
    `${origin}/api/v1/function-units/${fu}/versions/compare-v2`,
    { params: { versionId1: base, versionId2: target } })
  assert.equal(response.status(), 200, `FU ${fu} Compare HTTP ${response.status()}`)
  const payload = await response.json()
  assert.equal(payload.success, true)
  return payload.data
}

function moduleOf(data, key) {
  const module = data.modules.find(item => item.key === key)
  assert.ok(module, `Missing ${key} module`)
  return module
}

async function chooseVersion(page, dialog, side, version) {
  await dialog.locator('.version-compare__selector .el-select').nth(side).click()
  await page.getByRole('listbox').last().getByText(version, { exact: true }).click()
}

async function openCompare(page, fu, base, target) {
  await page.goto(`${origin}/dev/function-units/${fu}`, { waitUntil: 'domcontentloaded' })
  await page.locator('#tab-versions').click()
  await page.locator('.version-manager .action-buttons button:visible').first().click()
  const dialog = page.locator('.el-dialog:has(.version-compare)')
  await dialog.waitFor()
  await chooseVersion(page, dialog, 0, base)
  await chooseVersion(page, dialog, 1, target)
  const responsePromise = page.waitForResponse(response =>
    response.url().includes('/versions/compare-v2') && response.request().method() === 'GET')
  await dialog.locator('.version-compare__selectors button').click()
  assert.equal((await responsePromise).status(), 200)
  await dialog.locator('.version-compare__summary').waitFor()
  return dialog
}

try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await loginViaDwPassword(page)

  const automation = await compareApi(page, 50033, 17, 18)
  const automationModule = moduleOf(automation, 'AUTOMATION').semantic
  const processModule = moduleOf(automation, 'PROCESS').semantic
  assert.deepEqual(automationModule.counts, { added: 0, modified: 1, removed: 0 })
  assert.deepEqual(processModule.counts, { added: 0, modified: 0, removed: 0 })
  assert.deepEqual(automationModule.items[0].fields,
    [{ field: 'flowKey', oldValue: 'qa-compare-flow-a', newValue: 'qa-compare-flow-b' }])
  const reverse = moduleOf(await compareApi(page, 50033, 18, 17), 'AUTOMATION').semantic
  assert.deepEqual(reverse.items[0].fields,
    [{ field: 'flowKey', oldValue: 'qa-compare-flow-b', newValue: 'qa-compare-flow-a' }])

  let dialog = await openCompare(page, 50033, '1.0.0', '1.0.1')
  await dialog.locator('#tab-AUTOMATION').click()
  const automationText = await dialog.locator('.compare-panel__detail:visible').innerText()
  assert.ok(automationText.includes('qa-compare-flow-a'))
  assert.ok(automationText.includes('qa-compare-flow-b'))
  const automationShot = join(outputDir, `${prefix}-50033-automation.png`)
  await dialog.screenshot({ path: automationShot, animations: 'disabled' })

  const forms = moduleOf(await compareApi(page, 50029, 3, 11), 'FORMS').semantic
  const control = forms.items.find(item => item.objectType === 'FORM_CONTROL'
    && item.label === 'Incoming Channel' && item.type === 'MODIFIED')
  assert.ok(control, 'ATM nested sub-form control is missing')
  assert.deepEqual(control.fields.filter(field => field.field.includes('filterConditions')),
    [
      { field: 'props.lookupConfig.filterConditions[0].matchType', oldValue: null, newValue: 'eq' },
      { field: 'props.lookupConfig.filterConditions[1].matchType', oldValue: null, newValue: 'eq' }
    ])
  assert.ok(!forms.items.some(item => item.fields.some(field =>
    field.field.includes('config.subForms') && field.field.endsWith('.rule'))))

  dialog = await openCompare(page, 50029, '1.0.42', '1.0.45')
  await dialog.locator('#tab-FORMS').click()
  await dialog.locator('.compare-panel__object:visible')
    .filter({ hasText: 'Incoming Channel' }).click()
  const formText = await dialog.locator('.compare-panel__detail:visible').innerText()
  assert.ok(formText.includes('filter Conditions[0]'))
  assert.ok(formText.includes('eq'))
  assert.ok(!formText.includes('[16 items]'))
  const formShot = join(outputDir, `${prefix}-50029-subform-rule.png`)
  await dialog.screenshot({ path: formShot, animations: 'disabled' })

  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ passed: true,
    automationCounts: automationModule.counts, processCounts: processModule.counts,
    formControl: control.label, formFields: control.fields.length,
    screenshots: [automationShot, formShot] }, null, 2))
} finally {
  await browser.close()
}
