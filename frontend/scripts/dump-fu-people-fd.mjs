import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext()).newPage()
await loginViaUnifiedSso(page, 'portal')

const data = await page.evaluate(async () => {
  const token = localStorage.getItem('ws_up_access_token') || localStorage.getItem('accessToken')
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  const fu = await fetch('/api/portal/processes/function-units/Process_1_KK/content', { headers }).then(r => r.json())
  return fu?.data ?? fu
}, )

for (const form of data?.forms ?? []) {
  for (const tb of form?.tableBindings ?? []) {
    if (tb.bindingId === 30 || tb.tableId === 50021 || String(tb.tableName).toLowerCase() === 'people') {
      console.log('binding', tb.bindingId, 'form', form.formName, 'fieldDefs', (tb.fieldDefinitions ?? []).map(f => ({
        name: f.fieldName, pk: f.isPrimaryKey, pkGen: f.pkGeneration ?? f.pkGenerationJson,
      })), 'formFields id', (tb.formFields ?? []).find?.((f) => f.key === 'id' || f.field === 'id'))
    }
  }
}
await browser.close()
