import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

// Creates an isolated local FU, checks persisted responses, then deletes that fixture.
const origin = process.env.DW_ORIGIN || 'http://localhost:3000'
const out = fileURLToPath(new URL('../developer-workstation/verification-screenshots/', import.meta.url))
await mkdir(out, { recursive: true })
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 1100 } })
page.setDefaultTimeout(15000)
const errors = []
page.on('pageerror', error => errors.push(error.message))
let fixtureId
const results = []
const layoutPhase = process.argv.includes('--layout-before') ? 'before' : process.argv.includes('--layout') ? 'after' : null
const layouts = []
async function checkLayout(name, rootSelector, anchorSelector) {
  if (!layoutPhase) return
  for (const width of [1600, 1100]) {
    await page.setViewportSize({ width, height: 1100 })
    const root = page.locator(rootSelector)
    const notice = root.locator('.el-tag, .designer-unsaved-notice').filter({ hasText: 'You have unsaved changes.' }).first()
    await notice.waitFor()
    await page.waitForTimeout(400)
    const metrics = await notice.evaluate((element, anchorSelector) => {
      const root = element.closest('.el-dialog, .table-designer, .action-designer, .main-table-view-designer, .decision-designer')
      const anchor = root.querySelector(anchorSelector)
      const box = element.getBoundingClientRect()
      const anchorBox = anchor.getBoundingClientRect()
      const style = getComputedStyle(element)
      const icon = element.querySelector('.el-icon')
      const overlappingButtons = [...anchor.querySelectorAll('button')].filter(button => {
        const b = button.getBoundingClientRect()
        return Math.min(box.right, b.right) - Math.max(box.left, b.left) > 1
          && Math.min(box.bottom, b.bottom) - Math.max(box.top, b.top) > 1
      }).length
      return {
        insideActions: anchor.contains(element), width: box.width, height: box.height,
        fontSize: parseFloat(style.fontSize), border: parseFloat(style.borderTopWidth),
        radius: parseFloat(style.borderTopLeftRadius), iconWidth: icon?.getBoundingClientRect().width || 0,
        insideViewport: box.left >= 0 && box.right <= innerWidth && box.top >= 0 && box.bottom <= innerHeight,
        insideAnchor: box.left >= anchorBox.left - 1 && box.right <= anchorBox.right + 1,
        overlappingButtons,
        centerOffset: Math.abs((box.left + box.right - anchorBox.left - anchorBox.right) / 2),
      }
    }, anchorSelector)
    const pass = metrics.insideActions && metrics.width <= 420 && metrics.height <= 50
      && metrics.fontSize >= 14 && metrics.border >= 1 && metrics.radius <= 6
      && metrics.iconWidth >= 18 && metrics.insideViewport && metrics.insideAnchor && metrics.overlappingButtons === 0
      && (name !== 'view' || metrics.centerOffset <= 2)
    layouts.push({ name, viewport: width, pass, ...metrics })
    await page.screenshot({ path: `${out}2026-10-24_unsaved-layout-${layoutPhase}-${name}-${width}.png`, fullPage: true, animations: 'disabled' })
    console.log(`${pass ? 'PASS' : 'FAIL'} layout ${name} ${width}`, JSON.stringify(metrics))
  }
  await page.setViewportSize({ width: 1600, height: 1100 })
}
const stamp = Date.now()
async function api(method, path, data) {
  const res = await page.request.fetch(`${origin}/api/v1/function-units${path}`, {
    method, data, headers: { 'X-User-Id': 'user-dev' },
  })
  const body = await res.json()
  assert(res.ok() && body.success, `API ${method} ${path}: ${JSON.stringify(body)}`)
  return body.data
}
async function tab(name) { await page.getByRole('tab', { name, exact: true }).click() }
const warning = () => page.locator('.el-message-box:visible')
async function shot(name) {
  await page.waitForTimeout(400)
  await page.screenshot({ path: `${out}2026-10-24_unsaved-${name}.png`, fullPage: true, animations: 'disabled' })
}
async function choose(name) {
  await warning().getByRole('button', { name, exact: true }).click()
  await warning().waitFor({ state: 'hidden' })
}
async function check(condition, label) { assert(await condition(), label); results.push(label); console.log('PASS', label) }
try {
  await loginViaDwPassword(page, { loginOrigin: origin })
  fixtureId = (await api('POST', '', { name: `Unsaved verification ${stamp}`, virtualGroupIds: ['vg-dev-public'] })).id
  const base = `/${fixtureId}`
  const table = await api('POST', `${base}/tables`, {
    tableName: `unsaved_verify_${stamp}`, tableDisplayName: 'Verification Table', tableType: 'MAIN',
    description: 'Original description', requestIdConfig: { fieldNames: ['request_code'], separator: '-' },
    fields: [
      { fieldName: 'id', displayName: 'ID', dataType: 'VARCHAR', length: 64, isPrimaryKey: true, nullable: false, pkGeneration: { strategy: 'uuid' } },
      { fieldName: 'request_code', displayName: 'Request code', dataType: 'VARCHAR', length: 100, nullable: true },
    ],
  })
  const action = await api('POST', `${base}/actions`, { actionName: 'Verification Action', actionType: 'APPROVE', configJson: { requireComment: false }, description: 'Original action' })
  await api('POST', `${base}/decisions`, { decisionKey: 'unsaved_check', decisionName: 'Verification Decision', hitPolicy: 'FIRST', dmnXml: '' })
  const secondView = await api('POST', `${base}/main-table-views`, { viewName: 'Second verification view', tableId: table.id })
  await page.goto(`${origin}/dev/function-units/${fixtureId}`)
  await tab('Table Design')
  await page.locator('.table-designer .el-table__body-wrapper tbody tr').first().click()
  const description = () => page.locator('.table-designer .meta-field--description textarea')
  await page.locator('.request-id-field-row').first().getByRole('button', { name: 'Configure', exact: true }).click()
  await page.locator('.request-id-config-dialog .el-checkbox').filter({ hasText: 'Request code' }).click()
  await page.locator('.request-id-config-dialog').getByRole('button', { name: 'Cancel', exact: true }).click()
  await warning().waitFor(); await shot('request-id-close'); await choose('Discard')
  await page.locator('.request-id-config-dialog').waitFor({ state: 'hidden' })
  await check(() => page.locator('.request-id-config-dialog:visible').count().then(n => n === 0), 'Request ID local draft is checked before closing')
  await page.locator('.request-id-field-row').nth(1).getByRole('button', { name: 'Configure', exact: true }).click()
  await page.locator('.sla-config-dialog .el-radio').filter({ hasText: 'Submission time' }).click()
  await page.locator('.sla-config-dialog').getByRole('button', { name: 'Cancel', exact: true }).click()
  await warning().waitFor(); await shot('sla-close'); await choose('Save')
  await check(() => page.locator('.sla-config-dialog:visible').count().then(n => n === 1), 'Incomplete SLA draft cannot close through Save')
  await page.locator('.sla-config-dialog').getByRole('button', { name: 'Cancel', exact: true }).click(); await choose('Discard')
  await description().fill('Changed description')
  await checkLayout('table', '.table-designer', '.designer-toolbar')
  await tab('Action Design')
  await warning().waitFor()
  await shot('table-leave')
  await warning().getByRole('button', { name: 'Close this dialog' }).click()
  await check(() => description().inputValue().then(v => v === 'Changed description'), 'Closing warning retains table draft')
  await description().fill('Original description')
  await tab('Action Design')
  await check(() => warning().count().then(n => n === 0), 'Reverting table edit leaves without prompt')
  await tab('Table Design')
  await page.locator('.table-designer .el-table__body-wrapper tbody tr').first().click()
  await description().fill('Persisted table description')
  await tab('Action Design')
  const failureUrl = `**/api/v1/function-units/${fixtureId}/tables/${table.id}`
  await page.route(failureUrl, route => route.request().method() === 'PUT'
    ? route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ success: false, message: 'Verification save failure' }) }) : route.continue())
  await choose('Save')
  await check(() => description().inputValue().then(v => v === 'Persisted table description'), 'Failed table save blocks navigation and retains draft')
  await page.unroute(failureUrl)
  await tab('Action Design'); await choose('Save')
  await check(async () => (await api('GET', `${base}/tables`)).find(t => t.id === table.id).description === 'Persisted table description', 'Table save before leaving persists exact content')
  await page.locator('.action-designer .el-table__body-wrapper tbody tr').first().click()
  await page.locator('.action-designer textarea').first().fill('Changed action')
  await checkLayout('action', '.action-designer', '.designer-toolbar')
  await page.locator('.action-designer').getByRole('button', { name: 'Back to List', exact: true }).click()
  await warning().waitFor(); await shot('action-back'); await choose('Discard')
  await page.locator('.action-designer .el-table__body-wrapper tbody tr').first().click()
  await check(() => page.locator('.action-designer textarea').first().inputValue().then(v => v === 'Original action'), 'Action discard restores list and nested editor draft')
  await page.locator('.action-designer textarea').first().fill('Saved action settings')
  await page.locator('.action-designer .el-radio').filter({ hasText: 'Process Global' }).click()
  await Promise.all([
    page.waitForResponse(res => res.url().endsWith(`/actions/${action.id}`) && res.request().method() === 'PUT'),
    page.locator('.action-designer').getByRole('button', { name: 'Save', exact: true }).click(),
  ])
  await page.waitForFunction(() => !document.querySelector('.action-designer')?.inert)
  await tab('View Design')
  await warning().waitFor(); await shot('action-partial-save'); await choose('Discard')
  await check(async () => (await api('GET', `${base}/actions`)).find(a => a.id === action.id).description === 'Saved action settings', 'Saving Action settings retains independent unsaved binding warning')
  await page.locator('.main-table-view-designer').waitFor()
  const viewName = () => page.locator('.main-table-view-designer .properties-panel input').first()
  await page.locator('.main-table-view-designer').getByRole('button', { name: 'Edit filters...', exact: true }).click()
  await page.locator('.mtv-filter-dialog .logic-select').click()
  await page.getByRole('option', { name: 'Or', exact: true }).click()
  await page.locator('.mtv-filter-dialog').getByRole('button', { name: 'Cancel', exact: true }).click()
  await warning().waitFor(); await shot('view-filter-close'); await choose('Discard')
  await page.locator('.mtv-filter-dialog').waitFor({ state: 'hidden' })
  await check(() => page.locator('.mtv-filter-dialog:visible').count().then(n => n === 0), 'View Filter local draft is checked before closing')
  await viewName().fill('Changed view')
  await checkLayout('view', '.main-table-view-designer', '.designer-toolbar')
  await page.locator('.view-design-tab .el-menu-item').filter({ hasText: 'Second verification view' }).click()
  await warning().waitFor(); await shot('view-switch')
  await warning().getByRole('button', { name: 'Close this dialog' }).click()
  await check(() => page.locator('.view-design-tab .el-menu-item.is-active').innerText().then(v => !v.includes('Second verification view')), 'Cancelled view switch retains current selection highlight')
  await page.locator('.view-design-tab .el-menu-item').filter({ hasText: 'Second verification view' }).click()
  await choose('Discard')
  await page.waitForFunction(() => document.querySelector('.main-table-view-designer .properties-panel input')?.value === 'Second verification view')
  await check(() => viewName().inputValue().then(v => v === 'Second verification view'), 'Switching view discards only after confirmation')
  await viewName().fill('Saved second view')
  await tab('Connections'); await choose('Save')
  await check(async () => (await api('GET', `${base}/main-table-views/${secondView.id}`)).viewName === 'Saved second view', 'View save before navigation uses server result as new baseline')
  await page.locator('.connection-designer .designer-toolbar').getByRole('button').first().click()
  let dialog = page.locator('.el-dialog:visible')
  await dialog.locator('.connection-sender-email-item input').fill('invalid-email')
  await checkLayout('connection', '.connection-form-dialog:visible', '.designer-help-dialog-title')
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  await warning().waitFor(); await shot('connection-close'); await choose('Save')
  await check(() => page.locator('.connection-form-dialog:visible').count().then(n => n === 1), 'Connection validation failure keeps edit dialog open')
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click(); await choose('Discard')
  await tab('Email Templates')
  await page.locator('.email-template-designer .designer-toolbar').getByRole('button').first().click()
  dialog = page.locator('.el-dialog:visible')
  await dialog.locator('input').first().fill('Saved verification template')
  await checkLayout('email-template', '.email-template-form-dialog:visible', '.designer-help-dialog-title')
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  await warning().waitFor(); await shot('email-template-close'); await choose('Save')
  await check(async () => (await api('GET', `${base}/email-templates`)).some(t => t.name === 'Saved verification template'), 'Email Template save on close creates persisted template')
  await tab('Email Monitors')
  await page.locator('.email-monitor-designer .designer-toolbar').getByRole('button').first().click()
  dialog = page.locator('.el-dialog:visible')
  await dialog.locator('input').first().fill('Unsaved monitor')
  await checkLayout('email-monitor', '.el-dialog:visible', '.designer-help-dialog-title')
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  await warning().waitFor(); await shot('email-monitor-close'); await choose('Save')
  await check(() => page.locator('.el-dialog:visible').count().then(n => n === 1), 'Email Monitor missing connection blocks save and close')
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click(); await choose('Discard')
  await tab('Decision Design')
  await page.locator('.decision-list .el-table__body-wrapper tbody tr').first().getByRole('button', { name: 'Edit', exact: true }).click()
  const shape = page.locator('.decision-designer .djs-shape[data-element-id="decision_1"]')
  await shape.waitFor(); const bounds = await shape.boundingBox()
  await page.mouse.move(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2)
  await page.mouse.down(); await page.mouse.move(bounds.x + bounds.width / 2 + 70, bounds.y + bounds.height / 2 + 30, { steps: 8 }); await page.mouse.up()
  await checkLayout('decision', '.decision-designer', '.designer-toolbar')
  await page.locator('.decision-editor-nav').getByRole('button').click()
  await warning().waitFor(); await shot('decision-back'); await choose('Discard')
  await check(() => page.locator('.decision-list .el-table').count().then(n => n > 0), 'DMN drag is detected before returning to list')
  // Existing FU: edit and revert/discard only; never persist seed data.
  await page.goto(`${origin}/dev/function-units/2`)
  await tab('Table Design')
  await page.locator('.table-designer .el-table__body-wrapper tbody tr').first().click()
  const original = await description().inputValue()
  await description().fill(`${original} verification`)
  await checkLayout('table-existing', '.table-designer', '.designer-toolbar')
  await tab('Action Design'); await warning().waitFor(); await choose('Discard')
  results.push('Existing Function Unit uses the same navigation guard')
  assert.deepEqual(errors, [], 'No browser runtime errors')
  await page.goto(`${origin}/help/fu-documents#unsaved`)
  await page.locator('#unsaved').waitFor(); await shot('help')
  await writeFile(`${out}2026-10-24_unsaved-results.json`, JSON.stringify({ results, browserErrors: errors }, null, 2))
  if (layoutPhase) {
    await writeFile(`${out}2026-10-24_unsaved-layout-${layoutPhase}.json`, JSON.stringify({ layouts, browserErrors: errors }, null, 2))
    assert(layouts.every(item => item.pass), `${layouts.filter(item => !item.pass).length} unsaved notice layout checks failed`)
  }
  console.log(`PASS ${results.length} scenarios; ${layouts.length} layout checks; screenshots ${out}`)
} finally {
  if (fixtureId) await api('DELETE', `/${fixtureId}`)
  await browser.close()
}
