/** Explicit synthetic Review→MI diagnostic. Completes the host normally; never reassigns/cancels child tasks. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { loginViaPortalPassword } from './playwright-login.mjs'
import { unwrapApiList } from './mi-regression-helpers.mjs'

const taskId = process.env.FIXTURE_TASK_ID
const verifyGenerated = process.env.FIXTURE_VERIFY_GENERATED === '1'
const screenshotTag = process.env.FIXTURE_SCREENSHOT_TAG || 'assignment-stage'
assert.match(taskId || '', /^[a-f0-9-]{36}$/)
assert.match(screenshotTag, /^[a-z0-9-]+$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  let pageErrors = 0
  page.on('pageerror', () => pageErrors++)
  await loginViaPortalPassword(page)
  const api = async (method, path, data) => {
    const response = await page.request.fetch(`http://localhost:3000/api/portal${path}`, { method, data })
    const body = await response.json()
    assert.equal(response.ok() && body.success, true, `${method} ${path}: ${body.error?.message || response.status()}`)
    return body.data
  }
  const task = await api('GET', `/tasks/${taskId}`)
  assert.match(task.functionUnitName, /^Batch verification /)
  assert.equal(task.taskDefinitionKey, verifyGenerated ? 'AssignedWork' : 'Review')
  const form = await api('GET', `/tasks/${taskId}/form-data`)
  const [control] = Object.values(form.subTableBatchCapabilities)
  assert.equal(control.capabilities.allowBatchAssign, !verifyGenerated)
  const store = `dw:${control.rowDesign.binding.tableName}`
  const fields = control.assignmentConfig
  const rowKey = row => Object.fromEntries(control.rowDesign.binding.primaryKeyFields.map(field => [field, row[field]]))
  const payload = structuredClone(form.fieldValues)
  const rows = payload.__subTables__[store]
  assert.equal(rows.length, 2)
  const target = rows[0][fields.assigneeField]
  assert.ok(target, 'First row must already have a real directory-selected person')
  const scopes = [{ bindingId: String(control.bindingId), storeKey: store, rowKeys: rows.map(rowKey), emptied: false }]
  if (!verifyGenerated) {
    rows[1][fields.assigneeField] = target
    rows[1][fields.roleField] = null; rows[1][fields.buField] = null
    // Deliberately no subTableBatchChanges: the ordinary single-row save remains legal before generation.
    await api('POST', `/tasks/${taskId}/submit`, { formData: payload, subTableBindingScopes: scopes })
    const saved = await api('GET', `/tasks/${taskId}/form-data`)
    assert.equal(saved.fieldValues.__subTables__[store][1][fields.assigneeField], target)
    await api('POST', `/tasks/${taskId}/complete`, { taskId, action: 'APPROVE', formData: saved.fieldValues,
      subTableBindingScopes: scopes, comment: 'Synthetic initial assignment stage verification' })
  }
  const active = unwrapApiList(await api('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] }))
    .filter(row => row.processInstanceId === task.processInstanceId)
  console.log(JSON.stringify({ step: verifyGenerated ? 'synthetic-generated-recheck' : 'synthetic-host-completed', processInstanceId: task.processInstanceId, activeTasks: active.map(row => ({ taskId: row.taskId, node: row.taskDefinitionKey })) }))
  assert.equal(active.length, 2, 'Both real MI child tasks must exist')
  const identities = new Set()
  for (const child of active) {
    const detail = await api('GET', `/tasks/${child.taskId}`)
    assert.equal(detail.taskDefinitionKey, 'AssignedWork')
    assert.equal(detail.assignee, target)
    const childForm = await api('GET', `/tasks/${child.taskId}/form-data`)
    const [childControl] = Object.values(childForm.subTableBatchCapabilities)
    assert.equal(childControl.capabilities.allowBatchAssign, false, 'Generated target must not advertise initial assignment')
    const canonical = childForm.fieldValues.__subTables__[store]
    // Existing form-data returns canonical data plus the task-scoped identity; rendering and write scope use that identity.
    assert.deepEqual(childForm.fieldValues._currentItem.rowKey, detail.variables._currentItem.rowKey)
    const visible = canonical.filter(row => control.rowDesign.binding.primaryKeyFields
      .every(field => row[field] === childForm.fieldValues._currentItem.rowKey[field]))
    assert.equal(visible.length, 1, 'Task-scoped configured identity must resolve exactly one canonical row')
    identities.add(JSON.stringify(rowKey(visible[0])))
    const forged = structuredClone(childForm.fieldValues)
    forged.__subTables__[store] = [{ ...visible[0], [fields.assigneeField]: 'SYNTHETIC-MUST-NOT-ASSIGN' }]
    const response = await page.request.post(`http://localhost:3000/api/portal/tasks/${child.taskId}/submit`, { data: {
      formData: forged, subTableBindingScopes: [{ bindingId: String(control.bindingId), storeKey: store, rowKeys: visible.map(rowKey), emptied: false }],
    } })
    const body = await response.json()
    assert.equal(body.success, false)
    assert.match(body.error?.message || body.message, /already started/)
    const reloaded = await api('GET', `/tasks/${child.taskId}/form-data`)
    assert.deepEqual(reloaded.fieldValues.__subTables__[store], canonical)
    await page.goto(`http://localhost:3101/portal/tasks/${child.taskId}`)
    const table = page.locator('.sub-table-field').first()
    await table.locator('.el-table__body-wrapper tr').first().waitFor()
    assert.equal(await table.locator('.el-table__body-wrapper tr').count(), 1, 'Rendered child must show only its participant')
    const otherBatchOperation = ['allowBatchAdd', 'allowBatchEdit', 'allowBatchDelete']
      .some(flag => childControl.capabilities[flag])
    assert.equal(await table.getByRole('button', { name: 'Batch Edit', exact: true }).count(), otherBatchOperation ? 1 : 0)
    const headers = await table.locator('thead th').allTextContents()
    const assigneeColumn = headers.findIndex(header => header.trim().toLowerCase() === 'assignee')
    assert.ok(assigneeColumn >= 0, 'Assignment display must be masked before capturing evidence')
    const screenshot = fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-16_${screenshotTag}-child-${identities.size}.png`, import.meta.url))
    await table.screenshot({ path: screenshot, mask: [table.locator('.el-select'),
      table.locator(`.el-table__body-wrapper td:nth-child(${assigneeColumn + 1})`)] })
  }
  assert.equal(identities.size, 2, 'Real task row identities must match different participants')
  assert.equal(pageErrors, 0)
  console.log(JSON.stringify({ step: 'unmarked-stage-write-rejected', processInstanceId: task.processInstanceId, childTasks: active.length, identities: identities.size, pageErrors }))
} finally { await browser.close() }
