import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const taskId = process.argv[2] || '95233afa-6493-11f1-ab3a-ee47801f8369'
const user = process.argv[3] || process.env.LOGIN_USER || 'e2e_lina'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
await loginViaUnifiedSso(page, 'portal', { user, pass: process.env.LOGIN_PASS || 'password' })
await page.goto(`http://localhost:3000/portal/tasks/${taskId}`, { waitUntil: 'networkidle' })
await page.waitForTimeout(6000)

const info = await page.evaluate(() => {
  const blocks = Array.from(document.querySelectorAll('.sub-table-field'))
  return blocks.map((el, i) => {
    const title = el.querySelector('.sub-table-title, .el-card__header, h3, h4')?.textContent?.trim() ?? ''
    const rows = Array.from(el.querySelectorAll('table tbody tr')).map(tr =>
      Array.from(tr.querySelectorAll('td')).slice(0, 4).map(td => td.innerText.trim().slice(0, 50)),
    )
    return { i, title, rowCount: rows.length, rows: rows.slice(0, 5) }
  })
})

const people = await page.evaluate(() => {
  const labels = Array.from(document.querySelectorAll('.el-form-item__label'))
  const subTaskId = labels.find(l => l.textContent?.includes('sub task id'))
  const input = subTaskId?.closest('.el-form-item')?.querySelector('input')
  return input?.value ?? null
})

console.log('people sub_task_id', people)
console.log(JSON.stringify(info, null, 2))
await browser.close()
