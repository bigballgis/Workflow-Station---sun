import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const taskId = process.argv[2] || '95233afa-6493-11f1-ab3a-ee47801f8369'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
await loginViaUnifiedSso(page, 'portal')
await page.goto(`http://localhost:3000/portal/tasks/${taskId}`, { waitUntil: 'networkidle' })
await page.waitForTimeout(5000)
const rows = await page.evaluate(() => {
  const tables = document.querySelectorAll('.sub-table-field table tbody tr')
  return Array.from(tables).map(tr => {
    const cells = tr.querySelectorAll('td')
    return Array.from(cells).slice(0, 3).map(c => c.innerText.trim().slice(0, 40))
  })
})
console.log('table rows', rows.length)
for (const r of rows) console.log(JSON.stringify(r))
await browser.close()
