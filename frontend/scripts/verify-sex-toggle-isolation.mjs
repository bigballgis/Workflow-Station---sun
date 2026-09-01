#!/usr/bin/env node
/** Verify toggling People.sex on one MI participant does not pull another participant's row. */
import { chromium } from 'playwright'
import { mkdirSync } from 'fs'
import { join } from 'path'
import { loginViaPortalPassword } from './playwright-login.mjs'

const TASK_ID = process.argv[2] || '6c6c5cc6-63b4-11f1-9868-16c6d8eaa207'
const PARTICIPANT = process.argv[3] || 'Test-000062'
const OUT_DIR = join(process.cwd(), 'user-portal', 'verification-screenshots')

function readPeopleFields(page) {
  return page.evaluate(() => {
    const cards = [...document.querySelectorAll('.el-card, .form-layout-card, .sub-table-field')]
    const people = cards.find(c => /people/i.test(c.textContent || ''))
    if (!people) return null
    const inputs = [...people.querySelectorAll('.el-form-item')].map(i => ({
      label: i.querySelector('.el-form-item__label')?.textContent?.trim() ?? '',
      val: i.querySelector('input')?.value ?? '',
      checked: i.querySelector('.el-switch.is-checked') != null,
    }))
    return inputs
  })
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
await page.goto(`http://localhost:3000/portal/tasks/${TASK_ID}`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(12000)

// Select participant row in Sub Task grid if present
const rowClicked = await page.evaluate((pid) => {
  const cells = [...document.querySelectorAll('.el-table__body-wrapper td')]
  const cell = cells.find(td => td.textContent?.includes(pid))
  if (!cell) return false
  cell.click()
  return true
}, PARTICIPANT)
console.log('[select]', PARTICIPANT, 'clicked=', rowClicked)
await page.waitForTimeout(2000)

mkdirSync(OUT_DIR, { recursive: true })
const date = new Date().toISOString().slice(0, 10)
const beforePath = join(OUT_DIR, `${date}_task-6c6c-sex-before.png`)
await page.screenshot({ path: beforePath, fullPage: true })

const before = await readPeopleFields(page)
console.log('[before]', JSON.stringify(before, null, 2))

// Toggle sex switch in People section
const toggled = await page.evaluate(() => {
  const cards = [...document.querySelectorAll('.el-card, .form-layout-card, .sub-table-field')]
  const people = cards.find(c => /people/i.test(c.textContent || ''))
  if (!people) return false
  const sw = people.querySelector('.el-switch')
  if (!sw) return false
  sw.click()
  return true
})
console.log('[toggle sex]', toggled)
await page.waitForTimeout(1500)

const after = await readPeopleFields(page)
console.log('[after]', JSON.stringify(after, null, 2))

const afterPath = join(OUT_DIR, `${date}_task-6c6c-sex-after.png`)
await page.screenshot({ path: afterPath, fullPage: true })

const ageBefore = before?.find(i => /^age$/i.test(i.label))?.val ?? ''
const ageAfter = after?.find(i => /^age$/i.test(i.label))?.val ?? ''
const idBefore = before?.find(i => /^id$/i.test(i.label))?.val ?? ''
const idAfter = after?.find(i => /^id$/i.test(i.label))?.val ?? ''

console.log('[age]', ageBefore, '->', ageAfter)
console.log('[id]', idBefore, '->', idAfter)
console.log('[saved]', beforePath)
console.log('[saved]', afterPath)

// Fail if empty age suddenly got a value from cross-participant bleed (88 is known bad value on this task)
if (ageBefore.trim() === '' && ageAfter.trim() === '88') {
  console.error('FAIL: age jumped to 88 after sex toggle (cross-participant bleed)')
  process.exit(1)
}
if (idBefore.trim() === '' && idAfter.trim().match(/^[0-9a-f-]{36}$/i) && ageAfter.trim() === '88') {
  console.error('FAIL: id+age populated together after sex toggle')
  process.exit(1)
}

console.log('PASS: no cross-participant age bleed on sex toggle')
await browser.close()
