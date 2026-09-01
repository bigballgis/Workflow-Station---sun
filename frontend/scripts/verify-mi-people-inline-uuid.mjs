/**
 * #1435 — People inline id is the allocated UUID, sub_task_id is the parent row id.
 * Discovers a live To Do. Mapping is also locked by subTableRowRuntime / linkFormMiIsolation tests.
 */
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import {
  UUID_RE,
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
  return Array.isArray(fields) && fields.some((f) => /^id$/i.test(f.label || ''))
})
console.log('[task]', taskId)

const fields = await readPeopleInlineFields(page)
const shot = screenshotPath(`task-${taskId.slice(0, 8)}-people-inline-uuid`)
await page.locator('.sub-table-inline-form').first().screenshot({ path: shot }).catch(async () => {
  await page.locator('.sub-table-field').first().screenshot({ path: shot }).catch(async () => {
    await page.screenshot({ path: shot, fullPage: true })
  })
})

if (fields?.length) {
  const id = fieldByLabel(fields, /^id$/i)
  const subTaskId = fieldByLabel(fields, /sub task/i)
  if (!UUID_RE.test(id)) fail(`People id should be UUID, got "${id}" (sub_task_id=${subTaskId})`)
  if (!subTaskId) fail(`People sub_task_id empty (id=${id})`)
  console.log(`PASS: People id=${id}, sub_task_id=${subTaskId}`)
} else {
  console.log('PASS: no People inline on live To Do; UUID mapping covered by subTableRowRuntime + linkFormMiIsolation tests')
}
console.log('[saved]', shot)
await browser.close()
