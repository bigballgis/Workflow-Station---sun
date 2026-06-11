import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'
import { writeFileSync } from 'fs'

const TASK_ID = '091efdec-6308-11f1-a95b-92e64e1a5cf1'
const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext()).newPage()
await loginViaUnifiedSso(page, 'portal')
await page.goto(`http://localhost:3000/portal/tasks/${TASK_ID}`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(8000)

const data = await page.evaluate(async (taskId) => {
  const token = localStorage.getItem('ws_up_access_token') || localStorage.getItem('accessToken')
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  const task = await fetch(`/api/portal/tasks/${taskId}`, { headers }).then(r => r.json())
  const form = await fetch(`/api/portal/tasks/${taskId}/form`, { headers }).then(r => r.json()).catch(e => ({ error: String(e) }))
  return { task, form }
}, TASK_ID)

writeFileSync('scripts/_task-091e.json', JSON.stringify(data, null, 2))
const st = data.task?.data?.variables?.__subTables__
console.log('subTables keys:', st ? Object.keys(st) : 'none')
if (st) {
  for (const [k, v] of Object.entries(st)) {
    if (Array.isArray(v) && v.length) {
      console.log(`\n=== key ${k} row0 ===`, JSON.stringify(v[0], null, 2).slice(0, 1200))
    }
  }
}
await browser.close()
