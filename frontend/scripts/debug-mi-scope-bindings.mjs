import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const taskId = process.argv[2] || '95233afa-6493-11f1-ab3a-ee47801f8369'
const user = process.argv[3] || process.env.LOGIN_USER || 'e2e_lina'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
await loginViaUnifiedSso(page, 'portal', { user, pass: process.env.LOGIN_PASS || 'password' })

const info = await page.evaluate(async (id) => {
  const token = localStorage.getItem('ws_up_access_token') || localStorage.getItem('accessToken')
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  const taskRes = await fetch(`/api/portal/tasks/${id}`, { headers }).then(r => r.json())
  const task = taskRes?.data ?? taskRes
  const fu = await fetch(
    `/api/portal/processes/function-units/${task.processDefinitionKey}/content`,
    { headers },
  ).then(r => r.json())
  const content = fu?.data ?? fu
  const bpmn = content?.processes?.[0]?.data ?? ''
  const subTableNames = [...bpmn.matchAll(/subTableName" value="([^"]+)"/g)].map(m => m[1])
  const forms = (content?.forms ?? []).map(f => ({
    id: f.id,
    name: f.name,
    bindings: (f.tableBindings ?? [])
      .filter(b => b.bindingType !== 'PRIMARY')
      .map(b => ({
        bindingId: b.bindingId,
        tableName: b.tableName,
        displayName: b.tableDisplayName,
        pk: b.primaryKeyFields,
        cols: (b.fieldDefinitions ?? []).filter(fd => fd.isPrimaryKey).map(fd => fd.fieldName),
      })),
  }))
  return {
    taskName: task.taskName,
    taskDefinitionKey: task.taskDefinitionKey,
    currentItem: task.variables?._currentItem ?? task.variables?.currentItem,
    bpmnSubTableNames: subTableNames,
    forms,
  }
}, taskId)

console.log(JSON.stringify(info, null, 2))
await browser.close()
