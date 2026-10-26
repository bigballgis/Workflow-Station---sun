import assert from 'node:assert/strict'
import { mkdirSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import { createHistoryFixtureClock } from './change-history-fixture-clock.mjs'

const taskUrl = process.env.CH_TASK_URL
assert.ok(taskUrl, 'Set CH_TASK_URL to a completed task URL with processInstanceId')
const processId = new URL(taskUrl).searchParams.get('processInstanceId')
assert.ok(processId)
const output = process.env.CH_VERIFY_OUTPUT_DIR ?? 'user-portal/verification-screenshots'
mkdirSync(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 }, timezoneId: 'Asia/Hong_Kong' })
  const snapshotTime = new URL(taskUrl).searchParams.get('snapshotTime')
  assert.ok(snapshotTime, 'CH_TASK_URL must contain snapshotTime for snapshot verification')
  const clock = createHistoryFixtureClock(await page.evaluate(value => new Date(value).getTime(), snapshotTime))
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await loginViaPortalPassword(page)
  async function openHistory() {
    await page.goto(taskUrl, { waitUntil: 'domcontentloaded' })
    await page.getByRole('tab', { name: 'Change History', exact: true }).click()
    await page.locator('.ch-batch-header').first().waitFor()
    await page.getByText('Workflow Diagram', { exact: true }).click()
    await page.locator('.el-message').last().waitFor({ state: 'hidden' })
    await page.getByRole('tab', { name: 'Change History', exact: true }).evaluate(element => {
      element.style.scrollMarginTop = '100px'
      element.scrollIntoView({ block: 'start' })
    })
  }
  const headers = page.locator('.ch-batch-header')
  const search = page.getByRole('textbox', { name: 'Search change history', exact: true })
  const results = page.locator('.ch-history-results')
  async function order(label) {
    await page.locator('.ch-history-order').click()
    await page.getByRole('option', { name: label, exact: true }).click()
    await page.locator('.el-select-dropdown:visible').waitFor({ state: 'hidden' })
  }
  await openHistory()
  const originalTimes = await page.locator('.ch-batch-time').allTextContents()
  const originalCount = await headers.count()
  const originalSummary = await results.innerText()
  const sampleField = await page.locator('.change-history-table .el-table__body-wrapper tbody tr td:nth-child(2)').first().innerText()
  await page.screenshot({ path: `${output}/2026-10-26_change-history-toolbar-live.png` })
  await headers.first().click()
  await order('Newest first')
  assert.deepEqual(await page.locator('.ch-batch-time').allTextContents(), [...originalTimes].reverse())
  await order('Oldest first')
  assert.equal(await headers.first().getAttribute('aria-expanded'), 'false')
  await search.fill(sampleField)
  assert.ok(await headers.count() > 0)
  await search.fill('no matching change history 987xyz')
  await page.getByText('No matching change history', { exact: true }).waitFor()
  await search.fill('')
  await headers.first().waitFor()
  assert.equal(await headers.count(), originalCount)
  assert.equal(await headers.first().getAttribute('aria-expanded'), 'false')
  assert.equal(await results.innerText(), originalSummary)
  const metrics = await page.locator('.change-history-panel-inner').evaluate(element => {
    const toolbar = element.querySelector('.ch-history-tools').getBoundingClientRect()
    const first = element.querySelector('.ch-batch').getBoundingClientRect()
    return { paddingTop: getComputedStyle(element.parentElement).paddingTop,
      toolbarHeight: toolbar.height, toolbarToFirstGroup: first.top - toolbar.bottom }
  })
  assert.equal(metrics.paddingTop, '0px', 'Embedded history must not add a second content inset')
  assert.ok(metrics.toolbarToFirstGroup <= 12)

  function row(id, seconds, extra = {}) {
    const timestamp = clock.at(seconds)
    return { id, processInstanceId: processId, taskInstanceId: 'fixture-task', stageId: 'fixture-stage',
      stageName: 'Review', userId: 'fixture-editor', userName: 'Editor', timestamp,
      batchId: `save-${id}`, batchTime: timestamp, fieldName: 'amount', fieldLabel: 'Amount',
      oldValue: '100', newValue: '200', changeType: 'FIELD_UPDATE', subTableName: null,
      rowIdentifier: null, concurrent: false, ...extra }
  }
  const fixture = [row(1, 0), row(2, 60, { fieldName: 'description', fieldLabel: 'Description', newValue: 'Approval memo' }),
    row(3, 70, { changeType: 'CLAIM', fieldName: 'claimed_by', oldValue: null, newValue: 'Editor' }),
    row(4, 80, { oldValue: '200', newValue: '300' }),
    row(5, 90, { fieldName: 'card', fieldLabel: 'Card', oldValue: null, newValue: '6222021234567890' }),
    row(6, 100, { fieldName: 'currency', fieldLabel: 'Currency', newValue: JSON.stringify({ dropdown_name: 'Currency label', internal: 'internal-lookup-secret' }) }),
    row(7, 480, { stageId: 'audit', stageName: 'Audit', userName: 'Other Editor', userId: 'other', fieldName: 'auditNote', fieldLabel: 'Audit note', newValue: 'Checked' }),
    row(8, 0, { timestamp: clock.future, batchTime: clock.future })]
  await page.route(`**/processes/${processId}/change-history/sensitive-masks`, route => route.fulfill({ json: { data: {
    card: { enabled: true, preset: 'last4', keepPrefix: 0, keepSuffix: 4, maskChar: '*', revealPlainOnFocus: false },
  } } }))
  await page.route(`**/processes/${processId}/change-history`, route => route.fulfill({ json: { data: fixture } }))
  await page.route(`**/processes/${processId}/change-history?*`, route => route.fulfill({ json: { data: fixture } }))
  await openHistory()
  assert.equal(await headers.count(), 4)
  assert.match(await results.innerText(), /7 of 7/)
  await order('Newest first')
  assert.match(await headers.first().innerText(), /Audit/)
  await search.fill('amount')
  assert.equal(await headers.count(), 2, 'Search must not merge sessions across the hidden Claim')
  assert.match(await results.innerText(), /2 of 7/)
  await page.screenshot({ path: `${output}/2026-10-26_change-history-toolbar-search-fixture.png` })
  for (const hidden of ['622202', 'internal-lookup-secret']) {
    await search.fill(hidden)
    await page.getByText('No matching change history', { exact: true }).waitFor()
    assert.match(await results.innerText(), /0 of 7/)
  }
  await search.fill('7890')
  assert.match(await results.innerText(), /1 of 7/)
  assert.match(await page.locator('.change-history-table').innerText(), /\*+7890/)
  await search.fill('currency label')
  assert.match(await results.innerText(), /1 of 7/)
  await search.fill(' aPProval ')
  assert.match(await results.innerText(), /1 of 7/)
  await search.fill('')
  await order('Oldest first')
  await page.setViewportSize({ width: 1366, height: 768 })
  await page.getByRole('tab', { name: 'Change History', exact: true }).evaluate(element => element.scrollIntoView({ block: 'start' }))
  const inputBox = await search.boundingBox()
  const orderBox = await page.locator('.ch-history-order').boundingBox()
  assert.ok(inputBox.x + inputBox.width <= orderBox.x, 'Search input overlaps order control')
  await page.screenshot({ path: `${output}/2026-10-26_change-history-toolbar-1366.png` })
  const helpHref = await page.locator('.ch-history-tools a').getAttribute('href')
  await page.goto(helpHref, { waitUntil: 'domcontentloaded' })
  await page.locator('#change-history').waitFor()
  assert.match(await page.locator('#change-history').innerText(), /Oldest first/)
  assert.deepEqual(errors, [])
  const evidence = { liveGroups: originalCount, liveSummary: originalSummary, metrics,
    ascendingDescending: true, search: true, clearRestoresCollapse: true, hiddenValuesNotSearchable: true,
    fixtureVisibleRows: 7, fixtureGroups: 4, searchKeepsEventBoundary: true, helpUpdated: true, browserErrors: errors }
  writeFileSync(`${output}/2026-10-26_change-history-toolbar-evidence.json`, JSON.stringify(evidence, null, 2))
  console.log(JSON.stringify(evidence))
} finally { await browser.close() }
