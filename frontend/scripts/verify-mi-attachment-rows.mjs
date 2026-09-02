/**
 * #1438 — Attachment rows stay visible; collection table must not show id+file-only leaks.
 * Discovers a live To Do (prefers Multi-Instance Subtask Demo).
 */
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import {
  countSubTableRows,
  listMiCollectionTables,
  openFirstTodoMatching,
  screenshotPath,
} from './mi-regression-helpers.mjs'

function fail(message) {
  console.error(`FAIL: ${message}`)
  process.exit(1)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })

// 取「确实带附件行」的那个 To Do：To Do 列表随时会多出新建的空任务，
// 只取第一条会随机落到没有附件的任务上，把数据前置条件问题伪装成产品缺陷。
const taskId = await openFirstTodoMatching(
  page,
  async (p) => (await countSubTableRows(p, 'attachment')).count > 0,
  { prefer: /fu-20260422|subtask demo|attachment/i, limit: 6 },
)
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
  fail(`Attachment rows=${attachment.count} (expected at least 1 on this To Do)`)
}
if (collectionFileLeaks > 0) {
  fail(`collection table has ${collectionFileLeaks} pure id+file leak row(s)`)
}
console.log('PASS: Attachment visible, no file-only leaks in collection grid')
console.log('[saved]', shotAtt, shotGrid)
await browser.close()
