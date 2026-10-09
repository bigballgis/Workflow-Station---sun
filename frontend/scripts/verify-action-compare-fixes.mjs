#!/usr/bin/env node
/** Read-only acceptance on retained real snapshots; failed checks exit nonzero. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const results = []
const failures = []
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  await loginViaDwPassword(page)
  const compare = async (a, b) => {
    const r = await page.request.get(`http://localhost:3000/api/v1/function-units/50042/versions/compare-v2?versionId1=${a}&versionId2=${b}`)
    assert.equal(r.status(), 200)
    const json = await r.json()
    assert.equal(json.success, true)
    return json.data
  }
  const semantic = (r, key = 'ACTIONS') => r.modules.find(m => m.key === key).semantic
  const action = (r, name) => semantic(r).items.find(i => i.label === name)
  const field = (item, name) => item.fields.find(f => f.field === name)
  const check = async (issue, test) => {
    try { await test(); results.push({ issue, pass: true }) }
    catch (e) { failures.push({ issue, error: e.message }); results.push({ issue, pass: false }) }
  }
  await check('1671', async () => {
    const p = semantic(await compare(74, 75), 'PROCESS').items.find(i => i.objectType === 'BPMN_PROCESS')
    assert.equal(field(p, 'config.globalActionIds')?.newValue, '[50391]')
    assert.equal(field(p, 'config.globalActionNames')?.newValue, '["QA APPROVE"]')
    const reverse = semantic(await compare(75, 74), 'PROCESS').items.find(i => i.objectType === 'BPMN_PROCESS')
    assert.equal(field(reverse, 'config.globalActionNames')?.oldValue, '["QA APPROVE"]')
  })
  await check('1674', async () => {
    const renamed = semantic(await compare(87, 88))
    assert.deepEqual(renamed.counts, { added: 0, modified: 1, removed: 0 })
    assert.deepEqual(field(renamed.items[0], 'actionName'), { field: 'actionName', oldValue: 'QA URGE', newValue: 'QA URGE Renamed' })
    const reverse = semantic(await compare(88, 87))
    assert.deepEqual(reverse.counts, { added: 0, modified: 1, removed: 0 })
    assert.equal(field(reverse.items[0], 'actionName').newValue, 'QA URGE')
    assert.deepEqual(semantic(await compare(91, 92)).counts, { added: 0, modified: 0, removed: 0 })
  })
  await check('1677', async () => {
    const f = field(action(await compare(81, 82), 'QA COMPOSITE'), 'configJson.subActions[0]')
    assert.deepEqual(f, { field: 'configJson.subActions[0]', oldValue: 'QA APPROVE', newValue: 'QA REJECT' })
    assert.equal(field(action(await compare(82, 84), 'QA COMPOSITE'), 'configJson.subActions[1]').oldValue, 'QA APPROVE')
    assert.equal(field(action(await compare(89, 90), 'QA COMPOSITE'), 'configJson.subActions[0]').newValue, 'unresolved:50392')
  })
  await check('1678', async () => {
    for (const [a, b] of [[93, 94], [94, 93], [94, 95]]) {
      const r = await compare(a, b)
      assert.equal(JSON.stringify(r).includes('QA_SYNTHETIC_NOT_A_REAL_SECRET'), false)
      assert.ok(JSON.stringify(r).includes('[REDACTED]'))
    }
    const legacy = await page.request.get('http://localhost:3000/api/v1/function-units/50042/versions/compare?versionId1=93&versionId2=94')
    assert.equal(legacy.status(), 200)
    const body = await legacy.json()
    assert.equal(body.success, true)
    assert.equal(JSON.stringify(body).includes('QA_SYNTHETIC_NOT_A_REAL_SECRET'), false, 'Legacy Compare must also be scrubbed')
  })
  await check('1679', async () => {
    const removed = action(await compare(82, 84), 'QA APPROVE')
    for (const [key, value] of Object.entries({ field: 'business_key', operator: 'equals', value: 'beta', logic: 'OR' })) {
      assert.deepEqual(field(removed, `configJson.visibilityCondition[0].${key}`), {
        field: `configJson.visibilityCondition[0].${key}`, oldValue: value, newValue: null })
    }
    const added = action(await compare(84, 82), 'QA APPROVE')
    assert.equal(field(added, 'configJson.visibilityCondition[0].value').newValue, 'beta')
  })
  if (process.env.ACTION_COMPARE_SCREENSHOTS === '1' && failures.length === 0) {
    const versionsResponse = await page.request.get('http://localhost:3000/api/v1/function-units/50042/versions')
    assert.equal(versionsResponse.status(), 200)
    const versions = (await versionsResponse.json()).data
    await page.goto('http://localhost:3000/dev/function-units/50042', { waitUntil: 'domcontentloaded' })
    await page.locator('#tab-versions').click()
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    const cases = [
      [74, 75, 'PROCESS', 'QA Action Process', 'global-binding', 'QA APPROVE'],
      [87, 88, 'ACTIONS', 'QA URGE Renamed', 'rename', 'QA URGE'],
      [81, 82, 'ACTIONS', 'QA COMPOSITE', 'composite', 'QA REJECT'],
      [93, 94, 'ACTIONS', 'QA API_CALL', 'redaction', '[REDACTED]'],
      [82, 84, 'ACTIONS', 'QA APPROVE', 'cleared-condition', 'business_key'],
    ]
    for (const [a, b, key, label, suffix, expected] of cases) {
      await page.locator('.version-manager .action-buttons button:visible').first().click()
      const dialog = page.locator('.el-dialog:has(.version-compare)')
      await dialog.waitFor()
      for (const [index, id] of [[0, a], [1, b]]) {
        const select = dialog.locator('.version-compare__selector .el-select').nth(index)
        await select.locator('.el-select__wrapper').click()
        const listId = await select.locator('[role="combobox"]').getAttribute('aria-controls')
        await page.locator(`[id="${listId}"]`).getByRole('option', { name: versions.find(v => v.id === id).versionNumber, exact: true }).click()
      }
      await dialog.locator('.version-compare__submit').click()
      await dialog.locator('.version-compare__summary').waitFor()
      await dialog.locator(`#tab-${key}`).click()
      const objects = dialog.locator('.compare-panel__object:visible')
      if (label !== 'QA Action Process') {
        await objects.filter({ has: page.locator('.compare-panel__object-label', { hasText: new RegExp(`^${label}$`) }) }).click()
      } else {
        await objects.first().click()
      }
      const detail = dialog.locator('.compare-panel__detail:visible')
      await detail.getByText(expected, { exact: false }).first().waitFor()
      await detail.getByText(expected, { exact: false }).first().scrollIntoViewIfNeeded()
      if (key === 'PROCESS') await detail.getByText('Global Action names', { exact: false }).waitFor()
      assert.equal((await dialog.innerText()).includes('QA_SYNTHETIC_NOT_A_REAL_SECRET'), false)
      await dialog.evaluate(async element => {
        await Promise.all(element.getAnimations({ subtree: true }).map(a => a.finished.catch(() => {})))
      })
      await page.screenshot({ path: `frontend/developer-workstation/verification-screenshots/2026-10-08-action-compare-fixed-${suffix}.png` })
      await dialog.locator('.el-dialog__headerbtn').click()
    }
    assert.deepEqual(errors, [])
    results.push({ ui: 'five real version pairs', screenshots: 5, pageErrors: errors })
  }
  console.log(JSON.stringify({ results, failures }, null, 2))
  assert.deepEqual(failures, [])
} finally {
  await browser.close()
}
