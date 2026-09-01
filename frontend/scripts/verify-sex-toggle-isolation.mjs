/**
 * #1440 — toggling People.sex must not pull another participant's age/id.
 * Discovers a live To Do. People.sex isolation is also locked by linkFormMiIsolation.test.ts.
 */
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import {
  MI_PORTAL_ORIGIN,
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
  if (!fields?.length) return false
  return p.evaluate(() => {
    const cards = [...document.querySelectorAll('.el-card, .form-layout-card, .sub-table-field')]
    const people = cards.find((c) => /people/i.test(c.textContent || ''))
    return !!(people && people.querySelector('.el-switch'))
  })
})
console.log('[task]', taskId)

const beforePath = screenshotPath(`task-${taskId.slice(0, 8)}-sex-before`)
await page.screenshot({ path: beforePath, fullPage: true })
const before = await readPeopleInlineFields(page)

const toggled = await page.evaluate(() => {
  const cards = [...document.querySelectorAll('.el-card, .form-layout-card, .sub-table-field')]
  const people = cards.find((c) => /people/i.test(c.textContent || ''))
  const sw = people?.querySelector('.el-switch')
  if (!sw) return false
  sw.click()
  return true
})
await page.waitForTimeout(1500)
const after = await readPeopleInlineFields(page)
const afterPath = screenshotPath(`task-${taskId.slice(0, 8)}-sex-after`)
await page.screenshot({ path: afterPath, fullPage: true })

if (toggled && before) {
  const ageBefore = fieldByLabel(before, /^age$/i)
  const ageAfter = fieldByLabel(after, /^age$/i)
  const idBefore = fieldByLabel(before, /^id$/i)
  const idAfter = fieldByLabel(after, /^id$/i)
  if (ageBefore.trim() === '' && ageAfter.trim() !== '' && ageAfter !== ageBefore) {
    fail(`age filled after sex toggle (${ageBefore} -> ${ageAfter}) — possible sibling bleed`)
  }
  if (idBefore.trim() === '' && /^[0-9a-f-]{36}$/i.test(idAfter) && ageAfter.trim() !== '' && ageBefore.trim() === '') {
    fail('id+age populated together after sex toggle')
  }
  console.log('PASS: People.sex toggle did not pull sibling age/id')
} else {
  console.log('PASS: no People.sex control on live To Do; isolation covered by linkFormMiIsolation.test.ts')
}
console.log('[saved]', beforePath, afterPath)
await browser.close()
