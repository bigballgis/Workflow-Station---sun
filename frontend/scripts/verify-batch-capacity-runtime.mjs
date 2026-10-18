/** An explicitly pinned synthetic business task only; no Complete, deletion or directory writes. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { performance } from 'node:perf_hooks'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const id = process.env.FIXTURE_TASK_ID
assert.match(id || '', /^[a-f0-9-]{36}$/)
const tag = process.env.FIXTURE_SCREENSHOT_TAG || 'batch-capacity'
assert.match(tag, /^[a-z0-9-]+$/)
const shot = suffix => fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-18_${tag}-${suffix}.png`, import.meta.url))
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  let pageErrors = 0
  page.on('pageerror', () => pageErrors++)
  await loginViaPortalPassword(page)
  const api = async (method, path, data) => {
    // Playwright transport exceptions include request cookies; never forward their raw diagnostics.
    let response
    try { response = await page.request.fetch(`http://localhost:3000/api/portal${path}`, { method, data, timeout: 30000 }) }
    catch (error) { throw new Error(`Synthetic API transport failed (${error.name}); inspect server timing and persisted state before retrying`) }
    const body = await response.json()
    assert.ok(response.ok() && body.success, `Synthetic API failed HTTP ${response.status()}, code ${body.error?.code}`)
    return body.data
  }
  const task = await api('GET', `/tasks/${id}`)
  assert.match(task.functionUnitName, /^Batch verification /)
  assert.match(task.processInstanceId, /^[a-f0-9-]{36}$/)
  const read = () => api('GET', `/tasks/${id}/form-data`)
  let form = await read()
  const control = Object.values(form.subTableBatchCapabilities).find(item => item.capabilities.allowBatchAdd && item.capabilities.allowBatchEdit)
  assert.ok(control)
  const binding = control.rowDesign.binding
  assert.equal(binding.bindingLinkMode, 'structuralFk')
  assert.equal(binding.primaryKeyFields.length, 1)
  const pk = binding.primaryKeyFields[0]
  const numeric = control.batchForm.rule.find(rule => rule.type === 'inputNumber' && control.rowDesign.fieldWriteModes[rule.field] === 'EDITABLE')?.field
  const caption = control.batchForm.rule.find(rule => rule.type === 'input' && rule.field !== pk)?.field
  assert.ok(numeric && caption && binding.foreignKeyField)
  const extraText = control.batchForm.rule.filter(rule => rule.type === 'input' && rule.field !== pk && rule.field !== caption
    && control.rowDesign.fieldWriteModes[rule.field] === 'EDITABLE').map(rule => rule.field)
  const store = `dw:${binding.tableName}`
  const rowKey = row => ({ [pk]: row[pk] })
  const rowsOf = data => data.fieldValues.__subTables__[store]
  const requestFor = (data, operation, affected, fields) => ({
    formData: structuredClone(data.fieldValues),
    subTableBindingScopes: [{ bindingId: control.bindingId, storeKey: store, rowKeys: rowsOf(data).map(rowKey), emptied: false }],
    subTableBatchChanges: [{ batchControlId: control.batchControlId, bindingId: control.bindingId, operation,
      rowPatches: affected.map(row => ({ rowId: JSON.stringify(rowKey(row)), rowKey: rowKey(row), fields })) }],
  })
  const timings = {}
  if (rowsOf(form).length === 2) {
    const meta = control.rowDesign.fields.find(field => field.fieldName === pk)
    const generation = meta.pkGenerationJson ?? meta.pkGeneration
    const values = generation?.strategy === 'manual' ? Array.from({ length: 198 }, (_, index) => `SYNTHETIC-CAP-${index}`)
      : (await api('POST', `/processes/function-units/${encodeURIComponent(task.functionUnitCode)}/tables/primary-keys/allocate?taskId=${id}`,
          { tableId: binding.tableId, fieldName: pk, count: 198, batchControlId: control.batchControlId })).values
    assert.equal(values.length, 198)
    const added = values.map((value, index) => ({ [pk]: value, [binding.foreignKeyField]: rowsOf(form)[0][binding.foreignKeyField],
      [caption]: `Synthetic capacity ${index}`, [numeric]: index % 17,
      ...Object.fromEntries(extraText.map(field => [field, `Synthetic value ${index}`])) }))
    const request = requestFor(form, 'ADD', added, [pk, caption, numeric, ...extraText].filter(field => control.batchForm.rule.some(rule => rule.field === field)))
    request.formData.__subTables__[store].push(...added)
    request.subTableBindingScopes[0].rowKeys = request.formData.__subTables__[store].map(rowKey)
    const start = performance.now()
    await api('POST', `/tasks/${id}/submit`, request)
    timings.add198Ms = Math.round(performance.now() - start)
    form = await read()
  }
  assert.equal(rowsOf(form).length, 200, 'Never seed or truncate an unrelated data set')
  const editMs = []
  for (let sample = 0; sample < 3; sample++) {
    const request = requestFor(form, 'EDIT', rowsOf(form), [numeric, ...extraText])
    request.formData.__subTables__[store].forEach((row, index) => {
      row[numeric] = index + sample + 1
      extraText.forEach(field => { row[field] = `Synthetic sample ${sample} row ${index}` })
    })
    const start = performance.now()
    await api('POST', `/tasks/${id}/submit`, request)
    editMs.push(Math.round(performance.now() - start))
    const saved = await read()
    assert.equal(rowsOf(saved).length, 200)
    assert.deepEqual(rowsOf(saved).map(row => row[numeric]), Array.from({ length: 200 }, (_, index) => index + sample + 1))
    assert.ok(rowsOf(saved).every((row, index) => extraText.every(field => row[field] === `Synthetic sample ${sample} row ${index}`)))
    assert.deepEqual(rowsOf(saved).map(rowKey), rowsOf(form).map(rowKey))
    form = saved
  }
  const unchangedSaveMs = []
  for (let sample = 0; sample < 3; sample++) {
    const request = requestFor(form, 'EDIT', [], [])
    delete request.subTableBatchChanges
    const start = performance.now()
    await api('POST', `/tasks/${id}/submit`, request)
    unchangedSaveMs.push(Math.round(performance.now() - start))
    const saved = await read()
    assert.deepEqual(rowsOf(saved).map(rowKey), rowsOf(form).map(rowKey))
    assert.deepEqual(rowsOf(saved).map(row => [row[numeric], ...extraText.map(field => row[field])]),
      rowsOf(form).map(row => [row[numeric], ...extraText.map(field => row[field])]))
    form = saved
  }
  const db = () => execFileSync('docker', ['exec', 'platform-postgres-dev', 'psql', '-U', 'platform_dev', '-d',
    'workflow_platform_dev', '-Atc', `SELECT variables::text FROM up_process_instance WHERE id='${task.processInstanceId}'`], { encoding: 'utf8' }).trim()
  const before = db()
  const excess = requestFor(form, 'EDIT', [...rowsOf(form), { [pk]: 'SYNTHETIC-EXCESS' }], [numeric])
  excess.formData.__subTables__[store][0][numeric] = 0
  const denied = await page.request.post(`http://localhost:3000/api/portal/tasks/${id}/submit`, { data: excess })
  const deniedBody = await denied.json()
  assert.equal(denied.status(), 400, 'The explicit batch bound is a client validation error, not a permission denial')
  assert.equal(deniedBody.success, false)
  assert.equal(String(deniedBody.error.code), '4000')
  assert.match(deniedBody.error.message, /200/)
  assert.equal(db(), before, 'Over-limit must reject the whole request, not truncate to 200')
  let uiSaves = 0
  page.on('request', request => { if (request.method() === 'POST' && request.url().endsWith(`/tasks/${id}/submit`)) uiSaves++ })
  const loadStarted = performance.now()
  await page.goto(`http://localhost:3101/portal/tasks/${id}`)
  const open = page.getByRole('button', { name: 'Batch Edit', exact: true })
  await open.waitFor()
  timings.taskReadyMs = Math.round(performance.now() - loadStarted)
  const openStarted = performance.now()
  await open.click()
  const dialog = page.locator('.el-dialog:visible')
  const table = dialog.locator('.el-table__body-wrapper')
  assert.equal(await table.locator('tr').count(), 20)
  assert.equal(await table.locator('.el-input-number input').count(), 20)
  const editableColumns = control.batchForm.rule.filter(rule => rule.field !== pk && control.rowDesign.fieldWriteModes[rule.field] === 'EDITABLE').length
  const renderedControls = await table.locator('input:not([type="checkbox"])').count()
  assert.equal(renderedControls, editableColumns * 20)
  timings.open200Ms = Math.round(performance.now() - openStarted)
  await dialog.locator('.el-pagination .btn-next').click()
  await table.locator('tr').first().locator('td').nth(1).getByText('21', { exact: true }).waitFor()
  const captionColumn = control.batchForm.rule.filter(rule => rule.field && rule.type !== 'miAssignment').findIndex(rule => rule.field === caption) + 2
  assert.equal(await table.locator('tr').first().locator('td').nth(captionColumn).locator('input').inputValue(), String(rowsOf(form)[20][caption]))
  await table.scrollIntoViewIfNeeded()
  await dialog.screenshot({ path: shot('page-two') })
  await page.setViewportSize({ width: 390, height: 844 })
  const box = await dialog.boundingBox()
  assert.ok(box && box.x >= 0 && box.x + box.width <= 390 && box.y + box.height <= 844)
  await dialog.screenshot({ path: shot('narrow') })
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  assert.equal(uiSaves, 0)
  assert.equal(db(), before)
  assert.equal(pageErrors, 0)
  console.log(JSON.stringify({ taskId: id, rows: 200, columns: control.batchForm.rule.length, renderedRows: 20, renderedControls, samples: 3, editMs, unchangedSaveMs, ...timings,
    overLimitRejected: true, unchangedAfterCancel: true, pageErrors }))
} catch (error) {
  // Browser/request errors may embed authentication headers in their call logs.
  console.error(error.name === 'AssertionError' ? error.message : `Synthetic capacity verification failed (${error.name}); inspect sanitized server evidence`)
  process.exitCode = 1
} finally { await browser.close() }
