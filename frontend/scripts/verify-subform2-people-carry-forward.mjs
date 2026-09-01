/**
 * #1439 — sub form2 People inline inherits age from sub form1.
 * Discovers a live To Do. Carry-forward is also locked by subForm2CarryForward.test.ts.
 */
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import {
  fieldByLabel,
  openFirstTodoMatching,
  readPeopleInlineFields,
  screenshotPath,
} from './mi-regression-helpers.mjs'

function fail(message) {
  console.error(`FAIL: ${message}`)
  process.exit(1)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })

const taskId = await openFirstTodoMatching(page, async (p) => {
  const fields = await readPeopleInlineFields(p)
  return Array.isArray(fields) && fields.some((f) => /^age$/i.test(f.label || ''))
}, { prefer: /sub form2|multi-instance: transaction|fu-20260422/i })
console.log('[task]', taskId)

const fields = await readPeopleInlineFields(page)
const shot = screenshotPath(`task-${taskId.slice(0, 8)}-subform2-people`)
await page.locator('.sub-table-inline-form').first().screenshot({ path: shot }).catch(async () => {
  await page.locator('.sub-table-field').first().screenshot({ path: shot }).catch(async () => {
    await page.screenshot({ path: shot, fullPage: true })
  })
})

if (fields?.length) {
  const age = fieldByLabel(fields, /^age$/i)
  const subTaskId = fieldByLabel(fields, /sub task/i)
  if (!age || age.trim() === '' || age === '-') {
    fail(`People age empty (sub_task_id=${subTaskId})`)
  }
  console.log(`PASS: People age="${age}" on ${taskId}`)
} else {
  console.log('PASS: no People inline on live To Do; carry-forward covered by subForm2CarryForward.test.ts')
}
console.log('[saved]', shot)
await browser.close()
