/** 截取 People / Attachment 两张子表所在区域，作为修复后的可视证据。 */
import { chromium } from 'playwright'

const TASK_ID = process.env.TASK_ID || 'e3a13ae4-a6e2-11f1-95a1-b6918b5fa416'
const URL = `http://localhost:3000/portal/tasks/${TASK_ID}`
const SHOT = process.env.SHOT_PREFIX || 'mi-subtask-fixed'

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 } })
const page = await ctx.newPage()

const lb = await (await ctx.request.post('http://localhost:3000/api/v1/auth/login', {
  data: { username: 'developer', password: 'password' },
})).json()
await ctx.setExtraHTTPHeaders({ Authorization: `Bearer ${lb.accessToken}` })
await page.goto('http://localhost:3000/portal/', { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.evaluate(u => localStorage.setItem('portal_user', JSON.stringify(u)), lb.user)

await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.waitForFunction(
  () => [...document.querySelectorAll('.el-table thead th')].some(h => /SEX/i.test(h.innerText)),
  { timeout: 40000 },
)
await page.waitForTimeout(3500)

// 滚到 People 表并截该区域
const box = await page.evaluate(() => {
  const t = [...document.querySelectorAll('.el-table')].find(x =>
    [...x.querySelectorAll('thead th')].some(h => /SEX/i.test(h.innerText)))
  if (!t) return null
  t.scrollIntoView({ block: 'center' })
  return true
})
await page.waitForTimeout(1200)
await page.screenshot({ path: `${SHOT}-people-attachment.png` })

const rows = await page.evaluate(() => {
  const out = {}
  document.querySelectorAll('.el-table').forEach(t => {
    const head = [...t.querySelectorAll('thead th')].map(h => h.innerText.trim()).filter(Boolean)
    const body = [...t.querySelectorAll('tbody tr')].filter(
      r => !r.classList.contains('el-table__empty-row') && r.innerText.trim() !== '')
    const key = head.includes('FILE') ? 'attachment' : head.includes('SEX') ? 'people' : null
    if (key && out[key] === undefined) {
      out[key] = body.map(r => r.innerText.replace(/\s+/g, ' ').trim())
    }
  })
  return out
})
console.log('box:', box)
console.log(JSON.stringify(rows, null, 2))

await browser.close()
