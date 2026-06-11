import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const TASK_ID = '09367c90-6308-11f1-a95b-92e64e1a5cf1'
const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext()).newPage()
await loginViaUnifiedSso(page, 'portal')
await page.goto(`http://localhost:3000/portal/tasks/${TASK_ID}`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(8000)

const data = await page.evaluate(async (taskId) => {
  const token = localStorage.getItem('ws_up_access_token') || localStorage.getItem('accessToken')
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  const form = await fetch(`/api/portal/tasks/${taskId}/form-data`, { headers }).then(r => r.json())
  return form?.data ?? form
}, TASK_ID)

const bindings = data?.subTableBindings ?? []
console.log('form keys:', Object.keys(data || {}))
console.log('bindings count:', bindings.length, bindings.map(b => `${b.bindingId}:${b.tableName}`).join(', '))
for (const b of bindings) {
  if (b.tableName === 'People' || b.bindingId === 30) {
    console.log('People binding:', JSON.stringify({
      bindingId: b.bindingId,
      tableName: b.tableName,
      tableId: b.tableId,
      foreignKeyField: b.foreignKeyField,
      bindingLinkMode: b.bindingLinkMode,
      primaryKeyFields: b.primaryKeyFields,
      fieldDefinitions: (b.fieldDefinitions ?? []).map(f => ({ name: f.fieldName, pk: f.isPrimaryKey, fk: f.isForeignKey, auto: f.autoGenerate })),
      dataSample: (b.data ?? []).slice(0, 3).map(r => ({ id: r.id, id_idw: r.id_idw, sub_task_id: r.sub_task_id, age: r.age })),
    }, null, 2))
  }
}
await browser.close()
