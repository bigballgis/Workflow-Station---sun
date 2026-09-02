/**
 * Assignee To Do shows a participant-scoped collection (not an empty grid).
 * Discovers a live To Do. Slice logic is locked by miSubProcessScope.test.ts.
 */
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import {
  listMiCollectionTables,
  openFirstTodoMatching,
  pickMiCollectionTable,
  screenshotPath,
} from './mi-regression-helpers.mjs'

function fail(message) {
  console.error(`FAIL: ${message}`)
  process.exit(1)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })

// 取「确实渲染出 MI collection」的那个 To Do：只取列表第一条会随机落到刚新建、
// 还没有 collection 行的任务上，把数据前置条件问题伪装成产品缺陷。
const taskId = await openFirstTodoMatching(
  page,
  async (p) => {
    const t = pickMiCollectionTable(await listMiCollectionTables(p))
    return !!t && t.rows.length > 0
  },
  { prefer: /multi-instance: transaction|fu-20260422|subtask demo|atm-20260623/i, limit: 6 },
)
console.log('[task]', taskId)

const table = pickMiCollectionTable(await listMiCollectionTables(page))
const shot = screenshotPath(`task-${taskId.slice(0, 8)}-assignee-subtask-slice`)
await page.locator('.sub-table-field').first().screenshot({ path: shot }).catch(async () => {
  await page.screenshot({ path: shot, fullPage: true })
})

if (!table) fail('collection table not found on assignee To Do')
if (table.rows.length < 1) fail(`collection "${table.title}" has 0 rows`)
console.log(`PASS: assignee collection "${table.title}" visible (${table.rows.length} row(s))`)
console.log('[saved]', shot)
await browser.close()
