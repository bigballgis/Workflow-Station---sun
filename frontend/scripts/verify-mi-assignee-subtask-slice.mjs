#!/usr/bin/env node
/** miSubProcessScope — assignee todo shows participant-scoped Sub Task (visual baseline). */
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'
import { countSubTableRows, screenshotPath } from './mi-regression-helpers.mjs'

const TASK_ID = process.argv[2] || '6c6c5cc6-63b4-11f1-9868-16c6d8eaa207'

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaUnifiedSso(page, 'portal')
await page.goto(`http://localhost:3000/portal/tasks/${TASK_ID}`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(12000)

const subTask = await countSubTableRows(page, 'sub task')
console.log('[subTask]', subTask)

const participantRows = await page.evaluate(() => {
  const block = [...document.querySelectorAll('.sub-table-field')].find(el =>
    /sub task/i.test(el.querySelector('.title')?.textContent?.trim() ?? ''),
  )
  if (!block) return []
  return [...block.querySelectorAll('.el-table__body-wrapper tbody tr.el-table__row')]
    .map(tr => tr.querySelector('td')?.textContent?.trim() ?? '')
    .filter(id => /^Test-\d+/i.test(id))
})
console.log('[participantIds]', participantRows)

const shot = screenshotPath('task-6c6c-assignee-subtask-slice')
await page.locator('.sub-table-field').filter({ hasText: /sub task/i }).first().screenshot({ path: shot }).catch(async () => {
  await page.screenshot({ path: shot, fullPage: true })
})

if (!subTask.found) {
  console.error('FAIL: Sub Task table not found')
  process.exit(1)
}
if (participantRows.length < 1) {
  console.error('FAIL: no Test-xxxx participant rows in Sub Task grid')
  process.exit(1)
}

console.log(`PASS: assignee Sub Task visible (${participantRows.length} participant row(s))`)
console.log('[saved]', shot)
await browser.close()
