/** Starts only the explicitly named synthetic fixture, never an existing business FU. */
import { chromium } from 'playwright'
import { randomUUID } from 'node:crypto'
import { loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'
import { unwrapApiList } from './mi-regression-helpers.mjs'

const processKey = process.env.FIXTURE_PROCESS_KEY
if (!/^batch_verify_[a-z0-9]+$/.test(processKey || '')) throw new Error('Explicit synthetic FIXTURE_PROCESS_KEY required')
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  await loginViaPortalPassword(page)
  const roleId = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')).activeRoleId)
  if (!roleId) throw new Error('Test workspace has no active role')
  await loginViaAdminPassword(page)
  const unitResponse = await page.request.get(`http://localhost:3000/api/v1/admin/function-units/by-process-key/${processKey}`)
  if (!unitResponse.ok()) throw new Error(`Synthetic catalog lookup HTTP ${unitResponse.status()}`)
  const unit = await unitResponse.json()
  if (!unit.name?.startsWith('Batch verification ')) throw new Error('Refusing non-synthetic catalog')
  const accessUrl = `http://localhost:3000/api/v1/admin/function-units/${unit.id}/access`
  const accessResponse = await page.request.get(accessUrl)
  if (!accessResponse.ok()) throw new Error(`Synthetic access lookup HTTP ${accessResponse.status()}`)
  const access = await accessResponse.json()
  if (!access.some(a => a.roleId === roleId)) {
    const grant = await page.request.post(accessUrl, { data: { roleId } })
    if (!grant.ok()) throw new Error(`Synthetic access grant HTTP ${grant.status()}`)
  }
  await loginViaPortalPassword(page)
  const tableName = `batch_lines_${processKey.slice('batch_verify_'.length)}`
  const pkStrategy = process.env.FIXTURE_PK_STRATEGY ?? 'manual'
  if (!['manual', 'uuid', 'prefixedSequence'].includes(pkStrategy)) throw new Error('Unsupported synthetic PK strategy')
  const automaticPk = pkStrategy !== 'manual'
  const pkField = automaticPk ? 'entry_token' : 'line_key'
  const fkField = automaticPk ? 'owner_ref' : 'case_ref'
  const textField = automaticPk ? 'item_label' : 'caption'
  const numberField = automaticPk ? 'units' : 'amount'
  const response = await page.request.post(`http://localhost:3000/api/portal/processes/${unit.code}/start`, {
    data: { formData: { case_key: 'SYNTHETIC-CASE', __subTables__: { [`dw:${tableName}`]: [
      { [pkField]: automaticPk ? randomUUID() : 'SYNTHETIC-ONE', [fkField]: 'SYNTHETIC-CASE', [textField]: 'First synthetic line', [numberField]: 1 },
      { [pkField]: automaticPk ? randomUUID() : 'SYNTHETIC-TWO', [fkField]: 'SYNTHETIC-CASE', [textField]: 'Second synthetic line', [numberField]: 2 },
    ] } }, remark: 'Isolated synthetic batch acceptance' },
  })
  const body = await response.json()
  if (!response.ok() || !body.success) throw new Error(`Synthetic start HTTP ${response.status()}: ${body.error?.message || body.message || ''}`)
  const instanceId = body.data.processInstanceId || body.data.id
  console.log(JSON.stringify({ step: 'synthetic-started', instanceId, keys: Object.keys(body.data) }))
  const deadline = Date.now() + 30000
  let tasks = []
  do {
    const tasksResponse = await page.request.post('http://localhost:3000/api/portal/tasks/todo/query', {
      data: { page: 0, size: 100, filters: [] },
    })
    const taskBody = await tasksResponse.json()
    if (!tasksResponse.ok() || !taskBody.success) throw new Error('Synthetic task lookup failed')
    tasks = unwrapApiList(taskBody).filter(t => t.processInstanceId === instanceId)
    if (tasks.length) break
    await new Promise(resolve => setTimeout(resolve, 500))
  } while (Date.now() < deadline)
  if (tasks.length !== 1) throw new Error('Synthetic fixture must expose one review task')
  console.log(JSON.stringify({ step: 'synthetic-tasks', tasks: tasks.map(t => ({ taskId: t.taskId, processInstanceId: t.processInstanceId })) }))
} finally {
  await browser.close()
}
