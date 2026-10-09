#!/usr/bin/env node
/** Read-only browser/API acceptance of saved Function Unit semantic Compare versions. */
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
const prefix = `${new Date().toISOString().slice(0, 10)}_semantic-acceptance` +
  (screenshotSuffix ? `-${screenshotSuffix}` : '')
const browser = await chromium.launch({ headless: true,
  ...(process.env.PLAYWRIGHT_EXECUTABLE_PATH
    ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH }
    : { channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' }) })

function count(c) { return c.added + c.modified + c.removed }
function moduleOf(data, key) { return data.modules.find(module => module.key === key) }
function itemOf(data, key, label, type = 'MODIFIED') {
  const item = moduleOf(data, key)?.semantic?.items.find(
    entry => entry.label === label && entry.type === type)
  assert.ok(item, `${key}: missing ${type} object ${label}`)
  return item
}
function fieldOf(item, field) {
  const change = item.fields.find(entry => entry.field === field)
  assert.ok(change, `${item.label}: missing field ${field}`)
  return change
}

async function getData(page, fu, base, target) {
  const response = await page.request.get(`${origin}/api/v1/function-units/${fu}/versions/compare-v2`,
    { params: { versionId1: base, versionId2: target } })
  assert.equal(response.status(), 200, `FU ${fu} compare HTTP ${response.status()}`)
  const payload = await response.json()
  assert.equal(payload.success, true)
  return payload.data
}

async function chooseVersion(page, dialog, side, value) {
  await dialog.locator('.version-compare__selector .el-select').nth(side).click()
  await page.getByRole('listbox').last().getByText(value, { exact: true }).click()
}

async function openDialog(page, fu) {
  await page.goto(`${origin}/dev/function-units/${fu}`, { waitUntil: 'domcontentloaded' })
  await page.locator('#tab-versions').click()
  await page.locator('.version-manager .action-buttons button:visible').first().click()
  const dialog = page.locator('.el-dialog:has(.version-compare)')
  await dialog.waitFor()
  return dialog
}

async function compareInUi(page, dialog, base, target) {
  await chooseVersion(page, dialog, 0, base)
  await chooseVersion(page, dialog, 1, target)
  const responsePromise = page.waitForResponse(response =>
    response.url().includes('/versions/compare-v2') && response.request().method() === 'GET')
  await dialog.locator('.version-compare__selectors button').click()
  const response = await responsePromise
  assert.equal(response.status(), 200)
  await dialog.locator('.version-compare__summary').waitFor()
  return (await response.json()).data
}

async function screenshot(dialog, suffix) {
  const path = join(outputDir, `${prefix}-${suffix}.png`)
  await new Promise(resolve => setTimeout(resolve, 180))
  await dialog.screenshot({ path })
  return path
}

try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await loginViaDwPassword(page)
  const samplePairs = [
    { fu: 50030, base: 13, target: 14 },
    { fu: 50029, base: 3, target: 11 },
    { fu: 50005, base: 6, target: 7 },
    { fu: 2, base: 8, target: 10 }
  ]
  const states = new Map()
  for (const { fu } of samplePairs) {
    const [unitResponse, versionsResponse] = await Promise.all([
      page.request.get(`${origin}/api/v1/function-units/${fu}`),
      page.request.get(`${origin}/api/v1/function-units/${fu}/versions`)
    ])
    assert.equal(unitResponse.status(), 200)
    assert.equal(versionsResponse.status(), 200)
    const unit = (await unitResponse.json()).data
    const versions = (await versionsResponse.json()).data
    states.set(fu, { status: unit.status, currentVersion: unit.currentVersion,
      versionIds: versions.map(version => version.id).sort((a, b) => a - b) })
  }

  const results = new Map()
  for (const { fu, base, target } of samplePairs) {
    const forward = await getData(page, fu, base, target)
    const reverse = await getData(page, fu, target, base)
    assert.equal(forward.modules.length, 12)
    assert.equal(new Set(forward.modules.map(module => module.key)).size, 12)
    const display = { added: 0, modified: 0, removed: 0 }
    for (const module of forward.modules) {
      assert.ok(module.semantic, `${fu}/${module.key}: missing semantic response`)
      const counts = module.semantic.status === 'COMPARED' ? module.semantic.counts : module.counts
      for (const key of Object.keys(display)) display[key] += counts[key]
      if (module.semantic.status === 'COMPARED' && !module.semantic.truncated) {
        assert.equal(module.semantic.items.length, count(module.semantic.counts),
          `${fu}/${module.key}: count does not equal visible items`)
      }
      if (module.semantic.status === 'NOT_CAPTURED') {
        assert.equal(count(module.semantic.counts), 0)
        assert.equal(module.semantic.items.length, 0)
      }
      const backward = moduleOf(reverse, module.key)
      assert.ok(backward)
      const backwardCounts = backward.semantic.status === 'COMPARED'
        ? backward.semantic.counts : backward.counts
      assert.equal(counts.added, backwardCounts.removed, `${fu}/${module.key}: reverse added`)
      assert.equal(counts.modified, backwardCounts.modified, `${fu}/${module.key}: reverse modified`)
      assert.equal(counts.removed, backwardCounts.added, `${fu}/${module.key}: reverse removed`)
      for (const item of module.semantic.items) {
        for (const field of item.fields) {
          assert.ok((field.oldValue?.length ?? 0) <= 241)
          assert.ok((field.newValue?.length ?? 0) <= 241)
        }
      }
    }
    assert.deepEqual(forward.displayTotals, display)
    results.set(fu, forward)
  }
  let historicalPairs = 0
  for (const [fu, ids] of [
    [50030, [12, 13, 14, 15, 16]], [50029, [3, 4, 5, 11]],
    [50005, [6, 7]], [2, [8, 9, 10]]
  ]) {
    for (let left = 0; left < ids.length; left++) {
      for (let right = left + 1; right < ids.length; right++) {
        const data = await getData(page, fu, ids[left], ids[right])
        assert.equal(data.modules.length, 12)
        historicalPairs++
      }
    }
  }
  assert.equal(historicalPairs, 20)

  const negativeCases = [
    { name: 'same version', fu: 50030, query: 'versionId1=13&versionId2=13', status: 400 },
    { name: 'cross-FU version', fu: 50030, query: 'versionId1=13&versionId2=11', status: 400 },
    { name: 'unknown version', fu: 50030, query: 'versionId1=13&versionId2=999999999', status: 404 },
    { name: 'missing parameter', fu: 50030, query: 'versionId1=13', status: 400 },
    { name: 'invalid parameter', fu: 50030, query: 'versionId1=abc&versionId2=14', status: 400 }
  ]
  for (const test of negativeCases) {
    const response = await page.request.get(
      `${origin}/api/v1/function-units/${test.fu}/versions/compare-v2?${test.query}`)
    assert.equal(response.status(), test.status, test.name)
  }
  const anonymous = await browser.newContext()
  try {
    const response = await anonymous.request.get(
      `${origin}/api/v1/function-units/50030/versions/compare-v2?versionId1=13&versionId2=14`)
    assert.equal(response.status(), 401, 'anonymous Compare must be denied')
  } finally { await anonymous.close() }
  const legacy = await page.request.get(
    `${origin}/api/v1/function-units/50030/versions/compare?versionId1=13&versionId2=14`)
  assert.equal(legacy.status(), 200, 'old Compare endpoint regression')

  const qa = results.get(50030)
  for (const key of ['CONNECTIONS', 'EMAIL_TEMPLATES', 'EMAIL_MONITORS']) {
    assert.deepEqual(moduleOf(qa, key).semantic.counts, { added: 1, modified: 1, removed: 1 })
  }
  assert.deepEqual(moduleOf(qa, 'DOCUMENTS').semantic.counts,
    { added: 0, modified: 1, removed: 0 })
  assert.deepEqual(fieldOf(itemOf(qa, 'CONNECTIONS', 'qa-mod@example.invalid'), 'fromName'),
    { field: 'fromName', oldValue: 'QA Before', newValue: 'QA After' })
  assert.deepEqual(fieldOf(itemOf(qa, 'EMAIL_TEMPLATES', 'QA_CMP_MOD'), 'subject'),
    { field: 'subject', oldValue: 'QA mod before', newValue: 'QA mod after' })
  assert.deepEqual(fieldOf(itemOf(qa, 'EMAIL_MONITORS', 'QA_CMP_MOD/'), 'folderLabel'),
    { field: 'folderLabel', oldValue: 'INBOX', newValue: 'QA-CHANGED' })
  assert.deepEqual(fieldOf(itemOf(qa, 'EMAIL_MONITORS', 'QA_CMP_MOD/'), 'pollIntervalSeconds'),
    { field: 'pollIntervalSeconds', oldValue: '60', newValue: '120' })
  assert.deepEqual(fieldOf(itemOf(qa, 'DECISIONS', 'QA mod after'), 'name'),
    { field: 'name', oldValue: 'QA mod before', newValue: 'QA mod after' })
  assert.ok(fieldOf(itemOf(qa, 'DOCUMENTS', 'REQUIREMENTS'), 'content').newValue.endsWith('…'))
  assert.equal(moduleOf(qa, 'AUTOMATION').status, 'NOT_SNAPSHOTTED')
  assert.equal(moduleOf(qa, 'AUTOMATION').semantic.scope, 'REFERENCES_ONLY')
  assert.ok(!JSON.stringify(qa).includes('QA_CMP_SECRET_NEVER_RETURN'))

  const atm = results.get(50029)
  for (const key of ['PROCESS', 'TABLES', 'FORMS', 'VIEWS', 'ACTIONS']) {
    assert.ok(count(moduleOf(atm, key).semantic.counts) > 0, `ATM ${key} has no semantic change`)
  }
  assert.ok(moduleOf(atm, 'PROCESS').semantic.items.some(item => item.objectType === 'BPMN_NODE'))
  assert.ok(moduleOf(atm, 'TABLES').semantic.items.some(item => item.objectType === 'FIELD'))
  assert.ok(moduleOf(atm, 'VIEWS').semantic.items.some(item => item.objectType === 'VIEW_FIELD'))
  assert.ok(moduleOf(atm, 'ACTIONS').semantic.items.some(item => item.objectType === 'ACTION'))
  assert.ok(count(moduleOf(results.get(50005), 'FORMS').semantic.counts) > 0)
  assert.ok(moduleOf(results.get(2), 'BASIC').semantic.items.some(item => item.objectType === 'FUNCTION_UNIT'))
  assert.equal(moduleOf(results.get(2), 'DECISIONS').semantic.status, 'UNPARSEABLE')

  const screenshots = []
  let dialog = await openDialog(page, 50030)
  const uiQa = await compareInUi(page, dialog, '1.0.1', '1.0.2')
  assert.deepEqual(uiQa.displayTotals, qa.displayTotals)
  for (const key of ['CONNECTIONS', 'EMAIL_TEMPLATES', 'EMAIL_MONITORS',
    'DECISIONS', 'DOCUMENTS', 'AUTOMATION']) {
    await dialog.locator(`#tab-${key}`).click()
    screenshots.push(await screenshot(dialog, `50030-${key.toLowerCase()}`))
  }
  await dialog.locator('#tab-CONNECTIONS').click()
  await dialog.locator('.compare-panel__object:visible').filter({ hasText: 'qa-mod@example.invalid' }).click()
  assert.ok((await dialog.locator('.compare-panel__detail:visible').innerText()).includes('QA Before'))
  assert.ok((await dialog.locator('.compare-panel__detail:visible').innerText()).includes('QA After'))
  await dialog.locator('#tab-EMAIL_TEMPLATES').click()
  assert.equal(await dialog.locator('.compare-panel__detail:visible code p').count(), 0,
    'Email body HTML must remain text, not rendered nodes')

  await chooseVersion(page, dialog, 1, '1.0.1')
  assert.ok(await dialog.locator('.version-compare__selectors button').isDisabled())
  assert.equal(await dialog.locator('.version-compare__summary').count(), 0)
  await chooseVersion(page, dialog, 1, '1.0.0')
  let releaseRequest
  let signalIntercepted
  const gate = new Promise(resolve => { releaseRequest = resolve })
  const intercepted = new Promise(resolve => { signalIntercepted = resolve })
  const delayedRoute = async route => {
    signalIntercepted()
    await gate
    await route.continue()
  }
  const routePattern = '**/versions/compare-v2?*'
  await page.route(routePattern, delayedRoute)
  const staleResponse = page.waitForResponse(response =>
    response.url().includes('versionId2=12') && response.url().includes('/versions/compare-v2'))
  await dialog.locator('.version-compare__selectors button').click()
  await intercepted
  await chooseVersion(page, dialog, 1, '1.0.2')
  assert.equal(await dialog.locator('.version-compare__summary').count(), 0)
  releaseRequest()
  await staleResponse
  await page.unroute(routePattern, delayedRoute)
  await page.waitForTimeout(250)
  assert.equal(await dialog.locator('.version-compare__summary').count(), 0,
    'stale response must not restore the old Compare result')
  await dialog.locator('.version-compare__selectors button').click()
  await dialog.locator('.version-compare__summary').waitFor()
  assert.ok((await dialog.locator('.version-compare__summary').innerText()).includes('1.0.1 → 1.0.2'))

  await page.setViewportSize({ width: 768, height: 1000 })
  screenshots.push(await screenshot(dialog, '50030-768px'))
  await dialog.locator('#tab-CONNECTIONS').click()
  await dialog.locator('.compare-panel__object:visible').filter({ hasText: 'qa-mod@example.invalid' }).click()
  for (const [locale, moduleLabel, fieldLabel] of [
    ['zh-CN', '连接配置', '发件人名称'], ['zh-TW', '連線設定', '寄件者名稱']
  ]) {
    await dialog.locator('#tab-CONNECTIONS').click()
    const changed = await page.evaluate(nextLocale => {
      const i18n = document.querySelector('#app')?.__vue_app__?.config.globalProperties.$i18n
      if (!i18n) return false
      if (typeof i18n.locale === 'string') i18n.locale = nextLocale
      else if (i18n.locale?.value !== undefined) i18n.locale.value = nextLocale
      return (typeof i18n.locale === 'string' ? i18n.locale : i18n.locale?.value) === nextLocale
    }, locale)
    assert.ok(changed)
    await dialog.locator('#tab-CONNECTIONS').getByText(moduleLabel).waitFor()
    await dialog.locator('.compare-panel__field-name:visible').getByText(fieldLabel).waitFor()
    screenshots.push(await screenshot(dialog, `50030-${locale}`))
  }
  await dialog.locator('.el-dialog__headerbtn').click()
  await dialog.waitFor({ state: 'hidden' })
  await page.locator('.version-manager .action-buttons button:visible').first().click()
  dialog = page.locator('.el-dialog:has(.version-compare)')
  await dialog.waitFor()
  assert.equal(await dialog.locator('.version-compare__summary').count(), 0,
    'reopened Compare must not retain prior results')

  await page.setViewportSize({ width: 1440, height: 1100 })
  await page.goto(`${origin}/dev/function-units/50029`, { waitUntil: 'domcontentloaded' })
  dialog = await openDialog(page, 50029)
  const uiAtm = await compareInUi(page, dialog, '1.0.42', '1.0.45')
  assert.deepEqual(uiAtm.displayTotals, atm.displayTotals)
  for (const key of ['PROCESS', 'TABLES', 'FORMS', 'VIEWS', 'ACTIONS']) {
    await dialog.locator(`#tab-${key}`).click()
    assert.ok(await dialog.locator('.compare-panel__object:visible').count() > 0)
    screenshots.push(await screenshot(dialog, `50029-${key.toLowerCase()}`))
  }

  dialog = await openDialog(page, 2)
  await compareInUi(page, dialog, '1.0.0', '1.0.2')
  await dialog.locator('#tab-BASIC').click()
  screenshots.push(await screenshot(dialog, '2-basic'))
  await dialog.locator('#tab-DECISIONS').click()
  assert.ok((await dialog.innerText()).includes('Field-level comparison is shown instead.'))
  screenshots.push(await screenshot(dialog, '2-dmn-fallback'))

  for (const { fu } of samplePairs) {
    const [unitResponse, versionsResponse] = await Promise.all([
      page.request.get(`${origin}/api/v1/function-units/${fu}`),
      page.request.get(`${origin}/api/v1/function-units/${fu}/versions`)
    ])
    const unit = (await unitResponse.json()).data
    const versions = (await versionsResponse.json()).data
    assert.deepEqual({ status: unit.status, currentVersion: unit.currentVersion,
      versionIds: versions.map(version => version.id).sort((a, b) => a - b) }, states.get(fu),
    `FU ${fu} changed during read-only Compare verification`)
  }
  assert.deepEqual(pageErrors, [])
  console.log(JSON.stringify({ passed: true,
    historicalPairs, negativeCases: negativeCases.length + 1,
    pairs: samplePairs.map(({ fu, base, target }) => ({ fu, base, target,
      displayTotals: results.get(fu).displayTotals })),
    screenshots }, null, 2))
} finally {
  await browser.close()
}
