import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const taskId = process.argv[2] || '95233afa-6493-11f1-ab3a-ee47801f8369'
const user = process.argv[3] || process.env.LOGIN_USER || 'e2e_lina'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
await loginViaUnifiedSso(page, 'portal', { user, pass: process.env.LOGIN_PASS || 'password' })

const data = await page.evaluate(async (id) => {
  const task = await fetch(`/api/portal/tasks/${id}`, { credentials: 'include' }).then(r => r.json()).then(r => r.data)
  const fu = await fetch(`/api/portal/processes/function-units/${task.processDefinitionKey}/content`, {
    credentials: 'include',
  }).then(r => r.json()).then(r => r.data)
  const bpmn = fu.processes[0].data
  const tid = task.taskDefinitionKey
  const re = new RegExp(`userTask[^>]*id="${tid}"[\\s\\S]*?formKey="([^"]+)"`)
  const m = bpmn.match(re) || bpmn.match(new RegExp(`id="${tid}"[\\s\\S]*?formKey="([^"]+)"`))
  const formKey = m?.[1]
  const form =
    fu.forms.find(f => f.id === formKey || String(f.sourceId) === formKey)
    ?? fu.forms.find(f => f.name?.toLowerCase().includes('sub task'))
  const cfg = typeof form?.data === 'string' ? JSON.parse(form.data) : form?.data
  const walk = (arr, out = []) => {
    if (!Array.isArray(arr)) return out
    for (const n of arr) {
      if (n.type === 'subTable') out.push({ type: n.type, name: n.name, bid: n._bindingId })
      if (n.type === 'inlineForm' || n.type === 'formBelowTable') {
        out.push({ type: n.type, bid: n._bindingId, props: n.props })
      }
      walk(n.children, out)
    }
    return out
  }
  return {
    taskName: task.name,
    taskDefKey: tid,
    formKey,
    formName: form?.name,
    widgets: walk(cfg?.rule || []),
    bindings: (form?.tableBindings || [])
      .filter(b => b.bindingType !== 'PRIMARY')
      .map(b => ({ id: b.bindingId, t: b.tableName, d: b.tableDisplayName, pk: b.primaryKeyFields })),
  }
}, taskId)

console.log(JSON.stringify(data, null, 2))
await browser.close()
