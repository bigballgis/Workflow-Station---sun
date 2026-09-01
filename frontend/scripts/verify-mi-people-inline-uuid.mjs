#!/usr/bin/env node
/** #1435 — People inline id shows allocated UUID after save/hydrate. */
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import { fieldByLabel, readPeopleInlineFields, screenshotPath, UUID_RE } from './mi-regression-helpers.mjs'

const TASK_ID = process.argv[2] || '09367c90-6308-11f1-a95b-92e64e1a5cf1'
const PARTICIPANT = process.argv[3] || 'Test-000058'

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
await page.goto(`http://localhost:3000/portal/tasks/${TASK_ID}`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(12000)

await page.evaluate((pid) => {
  const cells = [...document.querySelectorAll('.el-table__body-wrapper td')]
  cells.find(td => td.textContent?.includes(pid))?.click()
}, PARTICIPANT)
await page.waitForTimeout(2500)

const fields = await readPeopleInlineFields(page)
console.log('[people]', JSON.stringify(fields, null, 2))

const shot = screenshotPath('task-09367-people-inline-uuid')
await page.locator('.sub-table-inline-form').first().screenshot({ path: shot }).catch(async () => {
  await page.screenshot({ path: shot, fullPage: true })
})

const id = fieldByLabel(fields, /^id$/i)
const subTaskId = fieldByLabel(fields, /sub task/i)

if (!UUID_RE.test(id)) {
  console.error(`FAIL: People id should be UUID, got "${id}" (sub_task_id=${subTaskId})`)
  process.exit(1)
}
if (subTaskId !== PARTICIPANT) {
  console.error(`FAIL: sub_task_id expected ${PARTICIPANT}, got "${subTaskId}"`)
  process.exit(1)
}

console.log(`PASS: People id=${id}, sub_task_id=${subTaskId}`)
console.log('[saved]', shot)
await browser.close()
