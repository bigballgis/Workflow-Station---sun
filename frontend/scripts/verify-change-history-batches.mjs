import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import { createHistoryFixtureClock } from './change-history-fixture-clock.mjs'

// Live legacy evidence first, then explicitly mocked display scenarios; never writes business data.
const taskUrl = process.env.CH_TASK_URL
assert.ok(taskUrl, 'Set CH_TASK_URL to a completed task URL containing processInstanceId')
const processId = new URL(taskUrl).searchParams.get('processInstanceId')
assert.ok(processId, 'The task URL must contain processInstanceId')
const output = resolve(process.env.CH_VERIFY_OUTPUT_DIR ?? 'user-portal/verification-screenshots')
mkdirSync(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
const evidence = { live: {}, fixture: {} }
try {
  const context = await browser.newContext({ viewport: { width: 1600, height: 1000 }, timezoneId: 'Asia/Hong_Kong' })
  const page = await context.newPage()
  const snapshotTime = new URL(taskUrl).searchParams.get('snapshotTime')
  assert.ok(snapshotTime, 'CH_TASK_URL must contain snapshotTime for snapshot verification')
  const clock = createHistoryFixtureClock(await page.evaluate(value => new Date(value).getTime(), snapshotTime))
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await loginViaPortalPassword(page)
  const api = `${new URL(taskUrl).origin}/api/portal/processes/${processId}/change-history`
  const response = await page.request.get(api)
  assert.equal(response.status(), 200)
  const body = await response.json()
  assert.ok(Array.isArray(body.data))
  const digest = createHash('sha256').update(JSON.stringify(body.data.map(({ batchId, batchTime, ...row }) => row))).digest('hex')
  if (process.env.CH_BASELINE) {
    const baseline = JSON.parse(readFileSync(process.env.CH_BASELINE, 'utf8'))
    assert.equal(body.data.length, baseline.count, 'Legacy record count changed')
    assert.equal(digest, baseline.digest, 'Legacy fields, values or ordering changed')
  }
  await page.goto(taskUrl, { waitUntil: 'domcontentloaded' })
  await page.getByRole('tab', { name: 'Change History', exact: true }).click()
  const headers = page.locator('.ch-batch-header')
  await headers.first().waitFor({ state: 'visible' })
  const liveCount = await headers.count()
  const liveRows = await page.locator('.change-history-table .el-table__body-wrapper tbody tr').count()
  assert.ok(liveRows > 0)
  await page.getByText('Workflow Diagram', { exact: true }).click()
  await page.locator('.el-message').last().waitFor({ state: 'hidden' })
  await headers.first().evaluate(element => { element.style.scrollMarginTop = '90px'; element.scrollIntoView({ block: 'start' }) })
  await page.screenshot({ path: `${output}/2026-10-26_change-history-live-expanded.png` })
  await headers.first().click()
  assert.equal(await headers.first().getAttribute('aria-expanded'), 'false')
  for (let index = 1; index < liveCount; index++) await headers.nth(index).click()
  await headers.first().evaluate(element => element.scrollIntoView({ block: 'start' }))
  await page.screenshot({ path: `${output}/2026-10-26_change-history-live-collapsed.png` })
  const refreshed = page.waitForResponse(r => r.url().includes(`/processes/${processId}/change-history`) && !r.url().includes('sensitive'))
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('ws:record-note-changed')))
  await refreshed
  await page.locator('.ch-batch-header').first().waitFor({ state: 'visible' })
  assert.equal(await headers.first().getAttribute('aria-expanded'), 'false', 'Collapse state lost on refresh')
  evidence.live = { records: body.data.length, displayedRows: liveRows, groups: liveCount, digest, refreshRetainsCollapse: true }

  function row(id, seconds, extra = {}) {
    const timestamp = clock.at(seconds)
    return { id, processInstanceId: processId, taskInstanceId: 'verification-task', stageId: 'verification-stage',
      stageName: 'Review', userId: 'synthetic-editor', userName: 'Editor', timestamp,
      batchId: `save-${id}`, batchTime: timestamp, fieldName: `field${id}`, fieldLabel: `Field ${id}`,
      oldValue: 'Before', newValue: 'After', changeType: 'FIELD_UPDATE', subTableName: null, rowIdentifier: null,
      concurrent: false, ...extra }
  }
  const fixture = [row(1, 0), row(2, 60), row(3, 70, { changeType: 'CLAIM', fieldName: 'claimed_by' }),
    row(4, 80), row(5, 90, { changeType: 'RECORD_NOTE_ADD', stageId: null, stageName: null, taskInstanceId: null }),
    row(6, 100), row(7, 401), row(8, 402, { userId: 'other-editor', userName: 'Other Editor' }),
    row(9, 0, { timestamp: clock.future, batchTime: clock.future })]
  await page.route(`**/processes/${processId}/change-history?*`, route => route.fulfill({ json: { code: 200, data: fixture } }))
  await page.route(`**/processes/${processId}/change-history`, route => route.fulfill({ json: { code: 200, data: fixture } }))
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.getByRole('tab', { name: 'Change History', exact: true }).click()
  await headers.first().waitFor({ state: 'visible' })
  assert.equal(await headers.count(), 7, 'Expected sessions split by events, gap and operator')
  assert.match(await headers.nth(3).innerText(), /Record Note/, 'Notes without a stage keep their own event heading')
  assert.equal(await page.locator('.change-history-table .el-table__body-wrapper tbody tr').count(), 8, 'Snapshot must exclude future fixture row')
  for (const entry of fixture.slice(0, 2)) {
    const timeLabel = await page.evaluate(timestamp => new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
    }).format(new Date(timestamp)), entry.timestamp)
    assert.ok((await headers.first().innerText()).includes(timeLabel))
  }
  const diagram = page.getByText('Workflow Diagram', { exact: true })
  if (await diagram.isVisible()) await diagram.click()
  await page.locator('.el-message').last().waitFor({ state: 'hidden' })
  await headers.first().evaluate(element => { element.style.scrollMarginTop = '90px'; element.scrollIntoView({ block: 'start' }) })
  await page.screenshot({ path: `${output}/2026-10-26_change-history-mixed-fixture.png` })
  await page.setViewportSize({ width: 1366, height: 768 })
  await headers.first().focus()
  await page.keyboard.press('Enter')
  assert.equal(await headers.first().getAttribute('aria-expanded'), 'false')
  await page.screenshot({ path: `${output}/2026-10-26_change-history-1366-fixture.png` })
  evidence.fixture = { visibleRows: 8, groups: 7, snapshotCutoff: true, keyboardCollapse: true }
  fixture.splice(0, fixture.length,
    row(101, 0, { taskInstanceId: null, batchId: 'return-save-1' }),
    row(102, 0, { taskInstanceId: null, batchId: 'return-save-1' }),
    row(103, 120, { taskInstanceId: null, batchId: 'return-save-2' }),
    row(104, 120, { taskInstanceId: null, batchId: 'return-save-2' }),
    row(105, 240, { taskInstanceId: null, batchId: null, batchTime: null }),
    row(106, 240, { taskInstanceId: null, batchId: null, batchTime: null }),
    row(107, 241, { taskInstanceId: null, batchId: null, batchTime: null }))
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.getByRole('tab', { name: 'Change History', exact: true }).click()
  await headers.first().waitFor({ state: 'visible' })
  assert.equal(await headers.count(), 4, 'Taskless saves must not infer a shared return round')
  assert.equal(await page.locator('.change-history-table .el-table__body-wrapper tbody tr').count(), 7)
  if (await diagram.isVisible()) await diagram.click()
  await page.locator('.el-message').last().waitFor({ state: 'hidden' })
  await headers.first().evaluate(element => element.scrollIntoView({ block: 'start' }))
  await page.screenshot({ path: `${output}/2026-10-26_change-history-taskless-boundary.png` })
  evidence.taskless = { visibleRows: 7, groups: 4, separateSaves: true, legacyExactTimestampOnly: true }
  const helpHref = await page.locator('.ch-history-tools a').getAttribute('href')
  assert.ok(helpHref?.endsWith('/help/up-tasks-to-claim#change-history'))
  const help = await page.context().newPage()
  await help.goto(helpHref, { waitUntil: 'domcontentloaded' })
  await help.locator('#change-history').waitFor({ state: 'visible' })
  assert.match(await help.locator('#change-history').innerText(), /five minutes/)
  await help.close()
  assert.deepEqual(errors, [], 'Browser runtime errors')
  writeFileSync(`${output}/2026-10-26_change-history-evidence.json`, JSON.stringify(evidence, null, 2))
  console.log(JSON.stringify(evidence))
} finally {
  await browser.close()
}
