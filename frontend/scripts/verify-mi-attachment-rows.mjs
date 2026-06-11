#!/usr/bin/env node
/** #1438 — Attachment table keeps all rows; Sub Task must not show id+file-only leaks. */
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'
import { countSubTableRows, screenshotPath } from './mi-regression-helpers.mjs'

const TASK_ID = process.argv[2] || '093962c4-6308-11f1-a95b-92e64e1a5cf1'

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaUnifiedSso(page, 'portal')
await page.goto(`http://localhost:3000/portal/tasks/${TASK_ID}`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(12000)

const attachment = await countSubTableRows(page, 'attachment')
console.log('[attachment]', attachment)

const subTask = await countSubTableRows(page, 'sub task')
console.log('[subTask]', subTask)

const attBlock = await page.evaluate(() => {
  const block = [...document.querySelectorAll('.sub-table-field')].find(el =>
    /attachment/i.test(el.querySelector('.title')?.textContent?.trim() ?? ''),
  )
  if (!block) return { found: false, fileRows: 0 }
  const rows = [...block.querySelectorAll('.el-table__body-wrapper tbody tr.el-table__row')]
  const withFile = rows.filter(tr => {
    const t = tr.textContent ?? ''
    return /\.pdf|upload|file/i.test(t)
  }).length
  return { found: true, fileRows: withFile, total: rows.length }
})
console.log('[attachmentDetail]', attBlock)

const subTaskPureFileLeaks = await page.evaluate(() => {
  const block = [...document.querySelectorAll('.sub-table-field')].find(el =>
    /sub task/i.test(el.querySelector('.title')?.textContent?.trim() ?? ''),
  )
  if (!block) return 0
  return [...block.querySelectorAll('.el-table__body-wrapper tbody tr.el-table__row')].filter(tr => {
    const cells = [...tr.querySelectorAll('td')].map(td => td.textContent?.trim() ?? '')
    const hasTestId = cells.some(c => /^Test-\d+/i.test(c))
    const hasFileOnly = cells.some(c => /\.pdf|upload/i.test(c)) && !hasTestId
    return hasFileOnly && cells.filter(Boolean).length <= 2
  }).length
})
console.log('[subTaskPureFileLeaks]', subTaskPureFileLeaks)

const shotAtt = screenshotPath('task-093962-attachment-table')
await page.locator('.sub-table-field').filter({ hasText: /attachment/i }).first().screenshot({ path: shotAtt }).catch(async () => {
  await page.screenshot({ path: shotAtt, fullPage: true })
})

const shotSub = screenshotPath('task-093962-subtask-grid')
await page.locator('.sub-table-field').filter({ hasText: /sub task/i }).first().screenshot({ path: shotSub }).catch(async () => {
  await page.screenshot({ path: shotSub, fullPage: false })
})

if (!attachment.found || attachment.count < 3) {
  console.error(`FAIL: Attachment rows=${attachment.count} (expected >= 3)`)
  process.exit(1)
}
if (subTaskPureFileLeaks > 0) {
  console.error(`FAIL: Sub Task has ${subTaskPureFileLeaks} pure id+file leak row(s)`)
  process.exit(1)
}

console.log('PASS: Attachment >= 3 rows, no Sub Task file-only leaks')
console.log('[saved]', shotAtt, shotSub)
await browser.close()
