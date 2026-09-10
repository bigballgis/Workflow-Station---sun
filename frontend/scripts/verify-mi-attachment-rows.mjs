/**
 * #1438 — Attachment rows stay visible; collection table must not show id+file-only leaks.
 * Discovers a live To Do (prefers Multi-Instance Subtask Demo).
 */
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import {
  countSubTableRows,
  listMiCollectionTables,
  listPortalApplications,
  listPortalTodoTasks,
  MI_PORTAL_ORIGIN,
  openPortalTask,
  preferMiApplications,
  preferMiTasks,
  screenshotPath,
} from './mi-regression-helpers.mjs'

function fail(message) {
  console.error(`FAIL: ${message}`)
  process.exit(1)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })

/**
 * Prefer a To Do that already has attachment rows. If every live To Do is an empty
 * later node (common when attachments were added on start / My Request and the
 * current assignee tasks have not reached that form), walk running My Requests.
 */
let taskId = null
const todos = preferMiTasks(
  await listPortalTodoTasks(page),
  /fu-20260422|subtask demo/i,
).slice(0, 8)
for (const row of todos) {
  await openPortalTask(page, row.taskId)
  if ((await countSubTableRows(page, 'attachment')).count > 0) {
    taskId = row.taskId
    break
  }
}
if (!taskId) {
  const apps = preferMiApplications(await listPortalApplications(page, MI_PORTAL_ORIGIN))
  for (const row of apps.slice(0, 8)) {
    await page.goto(`${MI_PORTAL_ORIGIN}/portal/applications/${row.id}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(9000)
    if ((await countSubTableRows(page, 'attachment')).count > 0) {
      taskId = row.id
      break
    }
  }
}
if (!taskId) fail('no To Do or My Request with attachment rows')
console.log('[task]', taskId)

const attachment = await countSubTableRows(page, 'attachment')
const collection = await countSubTableRows(page, 'participant|sub task|transaction')
console.log('[attachment]', attachment, '[collection]', collection)

const collectionFileLeaks = await page.evaluate(() => {
  const block = [...document.querySelectorAll('.sub-table-field')].find((el) =>
    /participant|sub task|transaction/i.test(el.querySelector('.title, .sub-table-header')?.textContent || ''),
  )
  if (!block) return 0
  return [...block.querySelectorAll('.el-table__body-wrapper tbody tr.el-table__row')].filter((tr) => {
    const cells = [...tr.querySelectorAll('td')].map((td) => td.textContent?.trim() ?? '')
    const hasParticipantId = cells.some((c) => /^(Test-|ATM-)/i.test(c))
    const hasFileOnly = cells.some((c) => /\.pdf|upload/i.test(c)) && !hasParticipantId
    return hasFileOnly && cells.filter(Boolean).length <= 2
  }).length
})
console.log('[collectionFileLeaks]', collectionFileLeaks)

const shotAtt = screenshotPath(`task-${taskId.slice(0, 8)}-attachment-table`)
await page.locator('.sub-table-field').filter({ hasText: /attachment/i }).first().screenshot({ path: shotAtt }).catch(async () => {
  await page.screenshot({ path: shotAtt, fullPage: true })
})
const shotGrid = screenshotPath(`task-${taskId.slice(0, 8)}-subtask-grid`)
const tables = await listMiCollectionTables(page)
const gridTitle = tables.find((t) => /participant|sub task|transaction/i.test(t.title))?.title
await page.locator('.sub-table-field').filter({ hasText: gridTitle || /participant|sub task/i }).first().screenshot({ path: shotGrid }).catch(async () => {
  await page.screenshot({ path: shotGrid, fullPage: false })
})

if (!attachment.found || attachment.count < 1) {
  fail(`Attachment rows=${attachment.count} (expected at least 1)`)
}
if (collectionFileLeaks > 0) {
  fail(`collection table has ${collectionFileLeaks} pure id+file leak row(s)`)
}
console.log('PASS: Attachment visible, no file-only leaks in collection grid')
console.log('[saved]', shotAtt, shotGrid)
await browser.close()
