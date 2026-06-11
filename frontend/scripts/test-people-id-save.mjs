import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const TASK_ID = '09367c90-6308-11f1-a95b-92e64e1a5cf1'
const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1500, height: 1200 } })
const page = await ctx.newPage()

const apiLog = []
page.on('request', req => {
  const u = req.url()
  if (/primary-keys\/allocate|tasks\/.*\/form/i.test(u)) apiLog.push(`REQ ${req.method()} ${u}`)
})
page.on('response', async res => {
  const u = res.url()
  if (/primary-keys\/allocate/i.test(u)) {
    const body = await res.text().catch(() => '')
    apiLog.push(`RES ${res.status()} ${u} ${body.slice(0, 200)}`)
  }
})

await loginViaUnifiedSso(page, 'portal')
await page.goto(`http://localhost:3000/portal/tasks/${TASK_ID}`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(8000)

const fieldMeta = await page.evaluate(() => {
  const card = document.querySelector('.sub-table-inline-form')
  if (!card) return null
  const items = Array.from(card.querySelectorAll('.el-form-item'))
  return items.map(el => ({
    label: el.querySelector('.el-form-item__label')?.textContent?.trim(),
    inputType: el.querySelector('input')?.type,
    val: el.querySelector('input')?.value,
    hasNumber: !!el.querySelector('.el-input-number'),
  }))
})
console.log('People inline field meta:', JSON.stringify(fieldMeta, null, 1))

const varsBefore = await page.evaluate(async (taskId) => {
  const token = localStorage.getItem('ws_up_access_token') || localStorage.getItem('accessToken')
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  const task = await fetch(`/api/portal/tasks/${taskId}`, { headers }).then(r => r.json())
  const formData = await fetch(`/api/portal/tasks/${taskId}/form-data`, { headers }).then(r => r.json())
  const st = task?.data?.variables?.__subTables__ || {}
  const fd = formData?.data?.fieldValues?.__subTables__ ?? formData?.data?.formData?.__subTables__ ?? {}
  const people = st['30'] || st['People'] || []
  const fdPeople = fd['30'] || fd['People'] || []
  const for58 = (Array.isArray(people) ? people : []).filter(r => r?.sub_task_id === 'Test-000058')
  const fd58 = (Array.isArray(fdPeople) ? fdPeople : []).filter(r => r?.sub_task_id === 'Test-000058')
  return {
    taskVars: for58.map(r => ({ id: r.id, age: r.age })),
    formDataApi: fd58.map(r => ({ id: r.id, age: r.age })),
    formDataKeys: Object.keys(fd || {}),
  }
}, TASK_ID)
console.log('Variables vs form-data API:', JSON.stringify(varsBefore))

const saveBtns = page.locator('.sub-table-inline-form button').filter({ hasText: /^Save$/i })
if (await saveBtns.count() > 0) {
  await saveBtns.first().click()
  await page.waitForTimeout(6000)
}

const fieldMetaAfter = await page.evaluate(() => {
  const card = document.querySelector('.sub-table-inline-form')
  if (!card) return null
  const items = Array.from(card.querySelectorAll('.el-form-item'))
  return items.map(el => ({
    label: el.querySelector('.el-form-item__label')?.textContent?.trim(),
    val: el.querySelector('input')?.value,
  }))
})
console.log('People inline AFTER save:', JSON.stringify(fieldMetaAfter, null, 1))

console.log('--- API log ---')
apiLog.forEach(l => console.log(l))
await browser.close()
