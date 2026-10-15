/** Synthetic enhanced MI only: prove task execution authority, not client currentItem, owns writes. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import { unwrapApiList } from './mi-regression-helpers.mjs'

const processId = process.env.MI_FIXTURE_PROCESS_ID
assert.match(processId || '', /^[a-f0-9-]{36}$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
  const call = async (method, path, data) => {
    const response = await page.request.fetch(`http://localhost:3000/api/portal${path}`, { method, data })
    const body = await response.json()
    return { ok: response.ok() && body.success === true, status: response.status(), body }
  }
  const api = async (method, path, data) => {
    const result = await call(method, path, data)
    assert.equal(result.ok, true, `${method} ${path}: ${result.body.message || result.body.error?.message || result.status}`)
    return result.body.data
  }
  await api('GET', `/processes/${processId}`)
  const tasks = unwrapApiList(await api('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] }))
    .filter(task => task.processInstanceId === processId)
  assert.equal(tasks.length, 2, 'Start from the synthetic two-execution MI fixture')
  for (const task of tasks) assert.match(task.functionUnitName, /^MI verification [a-z0-9]+$/)
  const contexts = []
  for (const task of tasks) {
    const response = await page.request.get(`http://localhost:8081/api/v1/tasks/${task.taskId}?includeWriteContext=true`)
    const body = await response.json()
    assert.equal(response.ok(), true)
    const context = body.data.writeContext
    assert.equal(context.multiInstance, true)
    assert.equal(context.taskId, task.taskId)
    assert.equal(context.processInstanceId, processId)
    assert.equal(Object.keys(context.currentItem.rowKey).length, 1)
    contexts.push({ task, context, form: await api('GET', `/tasks/${task.taskId}/form-data`) })
  }
  const active = contexts.find(entry => Object.values(entry.form.subTableBatchCapabilities || {}).some(control => {
    const rows = entry.form.fieldValues.__subTables__?.[`dw:${control.rowDesign.binding.tableName}`]
    return rows?.some(row => String(row.age) === '666')
  }))
  assert.ok(active, 'One enhanced task must carry the synthetic saved child')
  const other = contexts.find(entry => entry !== active)
  assert.notDeepEqual(active.context.currentItem.rowKey, other.context.currentItem.rowKey)
  const control = Object.values(active.form.subTableBatchCapabilities)[0]
  const binding = control.rowDesign.binding
  const store = `dw:${binding.tableName}`
  const pk = binding.primaryKeyFields
  assert.equal(pk.length, 1)
  const canonical = (await api('GET', `/processes/${processId}`)).variables
  const original = canonical.__subTables__[store]
  const owned = active.form.fieldValues.__subTables__[store].find(row => String(row.age) === '666')
  const rowKey = { [pk[0]]: owned[pk[0]] }
  const scope = { bindingId: String(control.bindingId), storeKey: store, rowKeys: [rowKey], emptied: false }
  const payload = { formData: structuredClone(canonical), subTableBindingScopes: [scope] }
  payload.formData.__subTables__[store] = [{ ...owned, age: '777' }]
  payload.formData._currentItem = other.context.currentItem
  payload.formData.currentItem = other.context.currentItem
  await api('POST', `/tasks/${active.task.taskId}/submit`, payload)
  const savedProcess = await api('GET', `/processes/${processId}`)
  const saved = savedProcess.variables.__subTables__[store]
  const updated = saved.find(row => row[pk[0]] === owned[pk[0]])
  assert.equal(String(updated.age), '777')
  assert.equal(updated._wsRowVersion, owned._wsRowVersion + 1)
  assert.deepEqual(saved.filter(row => row[pk[0]] !== owned[pk[0]]), original.filter(row => row[pk[0]] !== owned[pk[0]]))
  assert.equal(Object.hasOwn(savedProcess.variables, '_currentItem'), false)
  assert.equal(Object.hasOwn(savedProcess.variables, 'currentItem'), false)
  const otherControl = Object.values(other.form.subTableBatchCapabilities || {})
    .find(candidate => candidate.rowDesign.binding.tableName === binding.tableName)
  assert.ok(otherControl, 'Both synthetic execution forms need the frozen child binding')
  const forged = { formData: structuredClone(savedProcess.variables), subTableBindingScopes: [{ ...scope, bindingId: String(otherControl.bindingId) }] }
  forged.formData.__subTables__[store] = [{ ...updated, age: '999' }]
  forged.formData._currentItem = active.context.currentItem
  forged.formData.currentItem = active.context.currentItem
  for (const suffix of ['submit', 'complete']) {
    const result = await call('POST', `/tasks/${other.task.taskId}/${suffix}`,
      { ...forged, taskId: other.task.taskId, action: 'APPROVE' })
    assert.equal(result.ok, false, `Forged sibling ${suffix} must fail`)
    assert.equal(result.status, 403)
    assert.deepEqual((await api('GET', `/processes/${processId}`)).variables.__subTables__[store], saved)
  }
  const currentValues = (await api('GET', `/processes/${processId}`)).variables
  await api('POST', `/tasks/${active.task.taskId}/complete`, { taskId: active.task.taskId, action: 'APPROVE',
    formData: { ...currentValues, _currentItem: other.context.currentItem }, subTableBindingScopes: [scope],
    comment: 'Synthetic execution-authority acceptance' })
  const finalProcess = await api('GET', `/processes/${processId}`)
  assert.equal(Object.hasOwn(finalProcess.variables, '_currentItem'), false)
  assert.equal(Object.hasOwn(finalProcess.variables, 'currentItem'), false)
  assert.equal(String(finalProcess.variables.__subTables__[store].find(row => row[pk[0]] === owned[pk[0]]).age), '777')
  const completedContext = await page.request.get(`http://localhost:8081/api/v1/tasks/${active.task.taskId}?includeWriteContext=true`)
  assert.equal(completedContext.ok(), false, 'Completed task must no longer have an active execution write scope')
  let remaining = []
  const deadline = Date.now() + 30000
  do {
    remaining = unwrapApiList(await api('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] }))
      .filter(task => task.processInstanceId === processId)
    if (!remaining.some(task => task.taskId === active.task.taskId)) break
    await new Promise(resolve => setTimeout(resolve, 1000))
  } while (Date.now() < deadline)
  assert.equal(remaining.some(task => task.taskId === active.task.taskId), false)
  assert.equal(remaining.some(task => task.taskId === other.task.taskId), true)
  console.log(JSON.stringify({ step: 'batch-mi-authority-PASS', processId, completedTaskId: active.task.taskId,
    preservedSiblingTaskId: other.task.taskId, operators: 1, executionScopes: 2 }))
} finally {
  await browser.close()
}
