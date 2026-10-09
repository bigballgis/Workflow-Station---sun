#!/usr/bin/env node
/** Read-only red/green check for Process Compare's saved extension fields. */
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const phase = process.argv[2]
if (!['before', 'after'].includes(phase)) {
  throw new Error('Usage: node scripts/verify-process-extension-compare.mjs before|after')
}
const origin = 'http://localhost:3000'
const outputDir = resolve(dirname(fileURLToPath(import.meta.url)),
  '../developer-workstation/verification-screenshots')
mkdirSync(outputDir, { recursive: true })
const browser = await chromium.launch({ headless: true,
  ...(process.env.PLAYWRIGHT_EXECUTABLE_PATH
    ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH }
    : { channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' }) })

try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  const errors = []
  const checks = {}
  page.on('pageerror', error => errors.push(error.message))
  await loginViaDwPassword(page)
  await page.goto(`${origin}/dev/function-units/50037`, { waitUntil: 'domcontentloaded' })
  await page.locator('#tab-versions').click()
  await page.locator('.version-manager .el-table__row')
    .filter({ hasText: '1.0.2' }).locator('.action-buttons button').first().click()
  const dialog = page.locator('.el-dialog:has(.version-compare)')
  await dialog.waitFor()
  const pending = page.waitForResponse(response => response.request().method() === 'GET'
    && response.url().includes('/versions/compare-v2'))
  await dialog.locator('.version-compare__selectors button').click()
  const api = await pending
  assert.equal(api.status(), 200)
  const payload = await api.json()
  const process = payload.data.modules.find(item => item.key === 'PROCESS')
  assert.equal(process.semantic.status, 'COMPARED')
  const index = process.semantic.items.findIndex(item =>
    item.objectKey.endsWith('/Activity_1kwpdve'))
  assert.ok(index >= 0, 'QA User Task should be a compared Process item')
  const item = process.semantic.items[index]
  const readable = item.fields.filter(field => field.field.startsWith('config.'))
  await dialog.locator('.version-compare__summary').waitFor()
  await dialog.locator('#tab-PROCESS').click()
  await dialog.locator('#pane-PROCESS .compare-panel__object').nth(index).click()
  const detail = dialog.locator('#pane-PROCESS .compare-panel__detail')
  await detail.waitFor()
  const screenshot = join(outputDir, `2026-10-07_process-compare-extension-${phase}.png`)
  await dialog.screenshot({ path: screenshot, animations: 'disabled' })
  const visible = await detail.innerText()

  if (phase === 'before') {
    assert.equal(readable.length, 0)
    assert.match(visible, /hidden configuration/i)
  } else {
    assert.ok(readable.some(field => field.field === 'config.formId'),
      'To Do form should have before/after values')
    assert.match(visible, /扩展配置|Configuration/)
    const earlier = await page.request.get(
      `${origin}/api/v1/function-units/50037/versions/compare-v2`,
      { params: { versionId1: 32, versionId2: 33 } })
    assert.equal(earlier.status(), 200)
    const earlierPayload = await earlier.json()
    const earlierTask = earlierPayload.data.modules.find(module => module.key === 'PROCESS')
      .semantic.items.find(change => change.objectKey.endsWith('/Activity_1kwpdve'))
    for (const fieldName of ['config.assigneeType', 'config.formId', 'config.actionIds']) {
      const field = earlierTask.fields.find(change => change.field === fieldName)
      assert.ok(field, `B→C ${fieldName} should be visible`)
      assert.notEqual(field.oldValue, field.newValue)
      assert.ok(field.oldValue !== null || field.newValue !== null)
    }
    checks.earlierTaskFields = earlierTask.fields.filter(field => field.field.startsWith('config.'))
    for (const [locale, expected] of [
      ['zh-CN', '扩展配置'], ['zh-TW', '擴充設定']
    ]) {
      const changed = await page.evaluate(nextLocale => {
        const i18n = document.querySelector('#app')?.__vue_app__?.config.globalProperties.$i18n
        if (!i18n) return false
        if (typeof i18n.locale === 'string') i18n.locale = nextLocale
        else if (i18n.locale?.value !== undefined) i18n.locale.value = nextLocale
        return true
      }, locale)
      assert.equal(changed, true)
      await detail.getByText(expected, { exact: false }).first().waitFor()
      await dialog.screenshot({ path: join(outputDir,
        `2026-10-07_process-compare-extension-after-${locale}.png`), animations: 'disabled' })
    }
    // A second, existing FU checks compatibility without creating or rewriting any snapshots.
    const versionsResponse = await page.request.get(`${origin}/api/v1/function-units/50005/versions`)
    assert.equal(versionsResponse.status(), 200)
    const versions = (await versionsResponse.json()).data
    assert.ok(Array.isArray(versions) && versions.length > 0)
    const latest = versions[0]
    const oldest = versions[versions.length - 1]
    const existingResponse = await page.request.get(
      `${origin}/api/v1/function-units/50005/versions/compare-v2`,
      { params: { versionId1: oldest.id, versionId2: latest.id } })
    assert.equal(existingResponse.status(), 200)
    const existing = (await existingResponse.json()).data
    assert.equal(existing.modules.length, 12)
    const existingProcess = existing.modules.find(module => module.key === 'PROCESS')
    assert.equal(existingProcess.semantic.status, 'COMPARED')
    const selfResponse = await page.request.get(
      `${origin}/api/v1/function-units/50005/versions/compare-v2`,
      { params: { versionId1: latest.id, versionId2: latest.id } })
    assert.equal(selfResponse.status(), 400)
    assert.equal((await selfResponse.json()).error.code, 'BIZ_VERSION_COMPARE_SAME_VERSION')
    checks.existingFu = { functionUnitId: 50005, versionCount: versions.length,
      from: oldest.versionNumber, to: latest.versionNumber,
      counts: existingProcess.semantic.counts, sameVersionRejectedWith400: true,
      readableFieldCount: existingProcess.semantic.items.flatMap(item => item.fields)
        .filter(field => field.field.startsWith('config.')).length }

    for (const [position, version] of [[0, '1.0.1'], [1, '1.0.2']]) {
      const select = dialog.locator('.version-compare__selector .el-select').nth(position)
      await select.click()
      const option = page.locator('.el-select-dropdown__item:visible')
        .filter({ hasText: new RegExp(`^${version.replaceAll('.', '\\.')}\\s*$`) })
      await option.click()
      await select.getByText(version, { exact: true }).waitFor()
      await option.waitFor({ state: 'hidden' })
    }
    const earlierUiPending = page.waitForResponse(response =>
      response.request().method() === 'GET' && response.url().includes('/versions/compare-v2'))
    await dialog.locator('.version-compare__selectors button').click()
    const earlierUi = await (await earlierUiPending).json()
    assert.equal(earlierUi.data.baseVersion.versionNumber, '1.0.1')
    assert.equal(earlierUi.data.targetVersion.versionNumber, '1.0.2')
    await dialog.locator('#tab-PROCESS').click()
    await dialog.locator('#pane-PROCESS .compare-panel__object')
      .filter({ hasText: 'QA Approval B' }).click()
    await detail.getByText('MANUAL_ASSIGN', { exact: true }).waitFor()
    await detail.getByText('[50390]', { exact: true }).waitFor()
    await dialog.screenshot({ path: join(outputDir,
      '2026-10-07_process-compare-extension-assignee-actions.png'), animations: 'disabled' })
    checks.assigneeAndActionUiValuesVisible = true
  }
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ phase, baseVersion: payload.data.baseVersion.versionNumber,
    targetVersion: payload.data.targetVersion.versionNumber, objectKey: item.objectKey,
    readableFields: readable.map(field => field.field), screenshot, pageErrors: errors, checks }, null, 2))
} finally {
  await browser.close()
}
