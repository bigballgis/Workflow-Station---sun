/**
 * Assignee To Do shows a participant-scoped collection (not an empty grid).
 * Discovers a live To Do. Slice logic is locked by miSubProcessScope.test.ts.
 */
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import {
  listMiCollectionTables,
  pickMiCollectionTable,
  resolveAndOpenTodo,
  screenshotPath,
} from './mi-regression-helpers.mjs'

function fail(message) {
  console.error(`FAIL: ${message}`)
  process.exit(1)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })

const taskId = await resolveAndOpenTodo(page, {
  prefer: /multi-instance: transaction|fu-20260422|subtask demo|atm-20260623/i,
})
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
