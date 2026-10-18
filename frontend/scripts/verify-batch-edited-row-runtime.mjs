/** Two own synthetic tasks only: reject an invalid second EDIT row before any business write. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const ids = (process.env.FIXTURE_TASK_IDS || '').split(',')
assert.equal(ids.length, 2, 'Pin two independently created synthetic tasks')
ids.forEach(id => assert.match(id, /^[a-f0-9-]{36}$/))
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  await loginViaPortalPassword(page)
  const get = async path => {
    const response = await page.request.get(`http://localhost:3000/api/portal${path}`)
    assert.ok(response.ok())
    const body = await response.json()
    assert.equal(body.success, true)
    return body.data
  }
  for (const id of ids) {
    const task = await get(`/tasks/${id}`)
    assert.match(task.functionUnitName, /^Batch verification /)
    assert.match(task.processInstanceId, /^[a-f0-9-]{36}$/)
    const db = () => execFileSync('docker', ['exec', 'platform-postgres-dev', 'psql', '-U', 'platform_dev', '-d',
      'workflow_platform_dev', '-Atc', `SELECT variables::text FROM up_process_instance WHERE id='${task.processInstanceId}'`],
    { encoding: 'utf8' }).trim()
    const before = await get(`/tasks/${id}/form-data`)
    const control = Object.values(before.subTableBatchCapabilities).find(item => item.capabilities.allowBatchEdit)
    assert.ok(control)
    const binding = control.rowDesign.binding
    const numeric = control.batchForm.rule.find(rule => rule.type === 'inputNumber' && control.rowDesign.fieldWriteModes[rule.field] === 'EDITABLE')
    assert.ok(numeric)
    const store = `dw:${binding.tableName}`
    const originalRows = before.fieldValues.__subTables__[store]
    assert.equal(originalRows.length, 2)
    const originalDb = db()
    const rowKey = row => Object.fromEntries(binding.primaryKeyFields.map(field => [field, row[field]]))
    const request = {
      formData: structuredClone(before.fieldValues),
      subTableBindingScopes: [{ bindingId: control.bindingId, storeKey: store, rowKeys: originalRows.map(rowKey), emptied: false }],
      subTableBatchChanges: [{ batchControlId: control.batchControlId, bindingId: control.bindingId, operation: 'EDIT',
        rowPatches: originalRows.map(row => ({ rowId: JSON.stringify(rowKey(row)), rowKey: rowKey(row), fields: [numeric.field] })) }],
    }
    request.formData.__subTables__[store][0][numeric.field] = originalRows[0][numeric.field] === 0 ? 1 : 0
    request.formData.__subTables__[store][1][numeric.field] = 'INVALID-NUMBER'
    for (const tagged of [true, false]) {
      const candidate = structuredClone(request)
      if (!tagged) delete candidate.subTableBatchChanges
      const response = await page.request.post(`http://localhost:3000/api/portal/tasks/${id}/submit`, { data: candidate })
      const body = await response.json()
      assert.equal(response.status(), 400, `Invalid numeric edit must be rejected even when tagged=${tagged}`)
      assert.equal(body.success, false)
      assert.equal(String(body.error.code), '4003')
      assert.equal(db(), originalDb, 'Invalid second row must not change first row, versions, audit or snapshot')
      assert.deepEqual((await get(`/tasks/${id}/form-data`)).fieldValues.__subTables__[store], originalRows)
      const deniedComplete = await page.request.post(`http://localhost:3000/api/portal/tasks/${id}/complete`, {
        data: { ...candidate, taskId: id, action: 'APPROVE' },
      })
      assert.equal(deniedComplete.status(), 400)
      const deniedBody = await deniedComplete.json()
      assert.equal(deniedBody.success, false)
      assert.equal(String(deniedBody.error.code), '4003')
      assert.equal(db(), originalDb, 'Rejected Complete must not persist any of the batch')
      assert.equal((await get(`/tasks/${id}`)).taskId, id, 'Rejected Complete must leave the real task active')
    }
    const valid = structuredClone(request)
    valid.formData.__subTables__[store][0][numeric.field] = 0
    valid.formData.__subTables__[store][1][numeric.field] = 2
    const saved = await page.request.post(`http://localhost:3000/api/portal/tasks/${id}/submit`, { data: valid })
    assert.equal(saved.status(), 200)
    assert.equal((await saved.json()).success, true)
    const after = (await get(`/tasks/${id}/form-data`)).fieldValues.__subTables__[store]
    assert.deepEqual(after.map(row => row[numeric.field]), [0, 2])
    assert.deepEqual(after.map(rowKey), originalRows.map(rowKey))
    console.log(JSON.stringify({ taskId: id, invalidSecondRowRejected: true, completeRejected: true, unmarkedRejected: true, databaseUnchanged: true }))
  }
} finally { await browser.close() }
