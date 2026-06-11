import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const taskId = process.argv[2] || '95233afa-6493-11f1-ab3a-ee47801f8369'
const user = process.argv[3] || process.env.LOGIN_USER || 'e2e_lina'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
await loginViaUnifiedSso(page, 'portal', { user, pass: process.env.LOGIN_PASS || 'password' })
const res = await page.evaluate(async (id) => {
  const r = await fetch(`/api/portal/tasks/${id}`, { credentials: 'include' })
  return r.json()
}, taskId)
const data = res?.data ?? res
console.log('assignee', data?.assignee)
console.log('currentItem', JSON.stringify(data?.variables?._currentItem ?? data?.variables?.currentItem))
const st = data?.variables?.__subTables__ ?? {}
for (const [k, v] of Object.entries(st)) {
  if (!Array.isArray(v)) continue
  const ids = v.filter(r => r?.id_idw).map(r => ({
    id_idw: r.id_idw,
    assignee: typeof r.assignee === 'object' ? r.assignee?.id : r.assignee,
    name: r.name,
  }))
  if (ids.length) console.log('slice', k, JSON.stringify(ids))
}
await browser.close()
