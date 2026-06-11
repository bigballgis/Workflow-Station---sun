#!/usr/bin/env node
/** #1439 — sub form2 People inline inherits age/sex/name from sub form1. */
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'
import { fieldByLabel, readPeopleInlineFields, screenshotPath } from './mi-regression-helpers.mjs'

const TASK_ID = process.argv[2] || '75d662ec-5e8d-11f1-ac74-fe4105d84580'
const PARTICIPANT = process.argv[3] || 'Test-000059'

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaUnifiedSso(page, 'portal')
await page.goto(`http://localhost:3000/portal/tasks/${TASK_ID}`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(12000)

const rowClicked = await page.evaluate((pid) => {
  const cells = [...document.querySelectorAll('.el-table__body-wrapper td')]
  const cell = cells.find(td => td.textContent?.includes(pid))
  if (!cell) return false
  cell.click()
  return true
}, PARTICIPANT)
console.log('[select]', PARTICIPANT, 'clicked=', rowClicked)
await page.waitForTimeout(2500)

const fields = await readPeopleInlineFields(page)
console.log('[people]', JSON.stringify(fields, null, 2))

const shot = screenshotPath('task-75d662-subform2-people')
const peopleRoot =
  page.locator('.sub-table-inline-form').first()
  .or(page.locator('.sub-table-field').filter({ hasText: /people/i }).first())
await peopleRoot.screenshot({ path: shot }).catch(async () => {
  await page.screenshot({ path: shot, fullPage: true })
})

const age = fieldByLabel(fields, /^age$/i)
const subTaskId = fieldByLabel(fields, /sub task/i)
const id = fieldByLabel(fields, /^id$/i)

if (!fields?.length) {
  console.error('FAIL: People inline form not found')
  process.exit(1)
}
if (!age || age.trim() === '' || age === '-') {
  console.error(`FAIL: People age empty on sub form2 (sub_task_id=${subTaskId}, id=${id})`)
  process.exit(1)
}

console.log(`PASS: sub form2 People age="${age}" (participant ${PARTICIPANT})`)
console.log('[saved]', shot)
await browser.close()
