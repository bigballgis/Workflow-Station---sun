#!/usr/bin/env node
/** My Request: Details modal field mapping for unprocessed vs completed MI participants. */
import { chromium } from 'playwright'
import { mkdirSync } from 'fs'
import { join } from 'path'
import { loginViaPortalPassword } from './playwright-login.mjs'

const APP_ID = process.argv[2] || '3777ff51-63b4-11f1-9868-16c6d8eaa207'
const OUT_DIR = join(process.cwd(), 'user-portal', 'verification-screenshots')

async function readModalFields(page) {
  return page.evaluate(() => {
    const modal =
      document.querySelector('.link-form-modal-panel')
      || [...document.querySelectorAll('.el-overlay')].find(o => o.textContent?.match(/subtable|people/i))
    if (!modal) return { found: false, fields: [] }
    const readVal = (item) => {
      const input = item.querySelector('input')
      if (input) return input.value ?? ''
      const text = item.querySelector('.readonly-text, .el-form-item__content span')
      return text?.textContent?.trim() ?? ''
    }
    return {
      found: true,
      title: modal.querySelector('.link-form-modal-title, .el-dialog__title')?.textContent?.trim(),
      fields: [...modal.querySelectorAll('.el-form-item')].map(i => ({
        label: i.querySelector('.el-form-item__label')?.textContent?.trim(),
        val: readVal(i),
      })),
    }
  })
}

async function clickDetailsForParticipant(page, participantId) {
  return page.evaluate((pid) => {
    const rows = [...document.querySelectorAll('.el-table__body tr.el-table__row')]
    const targetRow = rows.find(tr => {
      const idCell = tr.querySelector('td')?.textContent?.trim() ?? ''
      return idCell === pid
    })
    if (!targetRow) return null
    const detail = [...targetRow.querySelectorAll('a, .el-link, button, span')].find(el =>
      /details|detail|详情/i.test(el.textContent || ''),
    )
    if (!detail) return null
    detail.click()
    return pid
  }, participantId)
}

async function closeModal(page) {
  await page.evaluate(() => {
    const close = document.querySelector('.link-form-modal-close, .el-dialog__headerbtn')
    close?.click()
  })
  await page.waitForTimeout(800)
}

function fieldVal(fields, labelRe) {
  return fields?.find(f => labelRe.test(f.label || ''))?.val ?? ''
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
await page.goto(`http://localhost:3000/portal/applications/${APP_ID}`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(12000)

mkdirSync(OUT_DIR, { recursive: true })
const date = new Date().toISOString().slice(0, 10)

// Unprocessed participant: sub_task_id = parent id, id empty
const click060 = await clickDetailsForParticipant(page, 'Test-000060')
console.log('[click060]', click060)
await page.waitForTimeout(1500)
let modal060 = await readModalFields(page)
console.log('[modal060]', JSON.stringify(modal060, null, 2))
const shot060 = join(OUT_DIR, `${date}_app-${APP_ID.slice(0, 8)}-details-060-unprocessed.png`)
await page.screenshot({ path: shot060, fullPage: false })

const id060 = fieldVal(modal060.fields, /^id$/i)
const sub060 = fieldVal(modal060.fields, /sub task/i)
if (!modal060.found) {
  console.error('FAIL: modal not found for Test-000060')
  process.exit(1)
}
if (id060 && id060 !== '-') {
  console.error(`FAIL: Test-000060 id should be empty, got "${id060}"`)
  process.exit(1)
}
if (sub060 !== 'Test-000060') {
  console.error(`FAIL: Test-000060 sub_task_id expected Test-000060, got "${sub060}"`)
  process.exit(1)
}
console.log('PASS: Test-000060 — id empty, sub_task_id=Test-000060')

await closeModal(page)

// Completed participant: id UUID, sub_task_id + age populated
const click061 = await clickDetailsForParticipant(page, 'Test-000061')
console.log('[click061]', click061)
await page.waitForTimeout(1500)
let modal061 = await readModalFields(page)
console.log('[modal061]', JSON.stringify(modal061, null, 2))
const shot061 = join(OUT_DIR, `${date}_app-${APP_ID.slice(0, 8)}-details-061-filled.png`)
await page.screenshot({ path: shot061, fullPage: false })

const id061 = fieldVal(modal061.fields, /^id$/i)
const sub061 = fieldVal(modal061.fields, /sub task/i)
const age061 = fieldVal(modal061.fields, /^age$/i)
if (!modal061.found) {
  console.error('FAIL: modal not found for Test-000061')
  process.exit(1)
}
if (!id061 || id061.length < 8) {
  console.error(`FAIL: Test-000061 id (UUID) missing, got "${id061}"`)
  process.exit(1)
}
if (sub061 !== 'Test-000061') {
  console.error(`FAIL: Test-000061 sub_task_id expected Test-000061, got "${sub061}"`)
  process.exit(1)
}
if (age061 !== '666') {
  console.error(`FAIL: Test-000061 age expected 666, got "${age061}"`)
  process.exit(1)
}
console.log('PASS: Test-000061 — id, sub_task_id, age populated')

const subTaskCount = await page.evaluate(() => {
  const t = [...document.querySelectorAll('.sub-table-field')].find(el =>
    el.querySelector('.title')?.textContent?.trim() === 'Sub Task',
  )
  if (!t) return -1
  return [...t.querySelectorAll('.el-table__body-wrapper tbody tr.el-table__row')].filter(tr =>
    /^Test-\d+/i.test(tr.querySelector('td')?.textContent?.trim() ?? ''),
  ).length
})
console.log('[subTaskRows]', subTaskCount)
if (subTaskCount !== 3) {
  console.error(`FAIL: Sub Task row count=${subTaskCount} (expected 3)`)
  process.exit(1)
}

await browser.close()
console.log('[saved]', shot060, shot061)
