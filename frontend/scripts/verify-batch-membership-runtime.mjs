/** Real UI/persistence acceptance. Refuses anything except a pinned synthetic fixture. */
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const taskId = process.env.FIXTURE_TASK_ID
if (!/^[a-f0-9-]{36}$/.test(taskId || '')) throw new Error('Pinned FIXTURE_TASK_ID is required; no automatic task selection')
const origin = 'http://localhost:3101'
const out = new URL('../user-portal/verification-screenshots/', import.meta.url)
mkdirSync(out, { recursive: true })
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  const runtimeErrors = []
  page.on('pageerror', () => runtimeErrors.push('pageerror'))
  page.on('console', message => {
    if (message.type() === 'error') runtimeErrors.push({ type: 'console.error', location: message.location() })
  })
  await loginViaPortalPassword(page, { loginOrigin: origin })
  const taskResponse = await page.request.get(`${origin}/api/portal/tasks/${taskId}`)
  const taskBody = await taskResponse.json()
  assert.equal(taskResponse.ok() && taskBody.success, true)
  const task = taskBody.data
  assert.match(task.functionUnitName, /^Batch verification /, 'Refusing a non-synthetic workflow')
  console.log(JSON.stringify({ step: 'membership-review-task', taskId, processId: task.processInstanceId }))
  const read = async () => {
    const response = await page.request.get(`${origin}/api/portal/tasks/${taskId}/form-data`)
    const body = await response.json()
    assert.equal(response.ok() && body.success, true, 'Task form read must succeed')
    assert.equal(body.data.formName, 'Synthetic TASK', 'Refusing non-synthetic form')
    const entries = Object.entries(body.data.subTableBatchCapabilities || {})
    assert.equal(entries.length, 1, 'Synthetic fixture must have one frozen control')
    assert.match(entries[0][0], /^batch-[a-z0-9]+-TASK$/)
    return body.data
  }
  const baseline = await read()
  const capability = Object.values(baseline.subTableBatchCapabilities)[0]
  const pkField = capability.rowDesign.binding.primaryKeyFields[0]
  const fkField = capability.rowDesign.binding.foreignKeyField
  const textField = capability.batchForm.rule.find(rule => rule.type === 'input' && rule.field !== pkField)?.field
  const numberField = capability.batchForm.rule.find(rule => rule.type === 'inputNumber')?.field
  const manualPk = capability.batchForm.rule.some(rule => rule.field === pkField)
  const pkDefinition = capability.rowDesign.fields.find(field => field.fieldName === pkField)
  assert.ok(pkDefinition, 'Frozen primary key metadata must be present')
  const pkConfig = pkDefinition.pkGenerationJson ?? pkDefinition.pkGeneration
  const sequencePk = pkConfig?.strategy === 'prefixedSequence'
  const readonlyNumber = capability.rowDesign.fieldWriteModes?.[numberField] === 'READONLY'
  const variant = `${manualPk ? 'manual' : sequencePk ? 'sequence' : 'uuid'}${readonlyNumber ? '-readonly' : ''}`
  assert.ok(pkField && fkField && textField && numberField)
  const storeKey = `dw:${capability.rowDesign.binding.tableName}`
  const rowsOf = data => data.fieldValues.__subTables__[storeKey]
  assert.equal(rowsOf(baseline).length, 2)
  assert.equal(rowsOf(baseline).every(row => row._wsRowVersion === 1), true, 'Initial row versions survive first-node completion')
  await page.goto(`${origin}/portal/tasks/${taskId}`)
  const batchButton = page.getByRole('button', { name: 'Batch Edit', exact: true })
  await batchButton.waitFor()
  await batchButton.click()
  const dialog = page.locator('.el-dialog:visible')
  await dialog.locator('.el-table__body-wrapper tr').nth(1).waitFor()
  await dialog.screenshot({ path: fileURLToPath(new URL(`${new Date().toISOString().slice(0, 10)}_batch-${variant}-workspace-initial.png`, out)) })
  const draftWrites = []
  const trackDraftWrites = request => {
    const path = new URL(request.url()).pathname
    if (request.method() === 'POST' && (path.endsWith(`/tasks/${taskId}/submit`)
      || path.endsWith('/primary-keys/allocate'))) draftWrites.push(path)
  }
  page.on('request', trackDraftWrites)
  await dialog.getByRole('button', { name: 'Add', exact: true }).click()
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: /Confirm|OK/, exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  assert.deepEqual(draftWrites, [], 'Add then Cancel must not allocate PKs or persist')
  page.off('request', trackDraftWrites)
  assert.deepEqual(rowsOf(await read()), rowsOf(baseline), 'Cancel must leave all saved rows unchanged')
  await batchButton.click()
  assert.equal(await dialog.locator('.el-table__body-wrapper tr').count(), 2)
  const allocations = []
  page.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/primary-keys/allocate')) allocations.push(request.postDataJSON())
  })
  await dialog.getByRole('button', { name: 'Add', exact: true }).click()
  const added = dialog.locator('.el-table__body-wrapper tr').nth(2)
  const addedInputs = added.locator('input:not([type="checkbox"])')
  if (manualPk) await addedInputs.nth(0).fill('SYNTHETIC-THREE')
  await addedInputs.nth(manualPk ? 1 : 0).fill('Third synthetic line')
  const numberInput = addedInputs.nth(manualPk ? 2 : 1)
  if (readonlyNumber) {
    const numberCell = added.locator('td').nth(capability.batchForm.rule.findIndex(rule => rule.field === numberField) + 2)
    assert.equal(await numberCell.locator('input').count(), 0, 'Frozen readonly field must render without an editable input')
    assert.equal((await numberCell.innerText()).trim(), '0', 'Numeric metadata default must be typed correctly')
  } else {
    await numberInput.fill('3')
    await numberInput.press('Tab')
  }
  if (sequencePk) {
    await dialog.getByRole('button', { name: 'Add', exact: true }).click()
    page.on('request', trackDraftWrites)
    await dialog.getByRole('button', { name: /Apply/ }).click()
    await page.locator('.el-message--error').waitFor()
    assert.equal(await dialog.isVisible(), true, 'Invalid second new row must retain the entire draft')
    assert.deepEqual(draftWrites, [], 'Second-row validation failure must not allocate or persist the first row')
    page.off('request', trackDraftWrites)
    assert.deepEqual(rowsOf(await read()), rowsOf(baseline))
    const fourthInputs = dialog.locator('.el-table__body-wrapper tr').nth(3).locator('input:not([type="checkbox"])')
    await fourthInputs.nth(0).fill('Fourth synthetic line')
    await fourthInputs.nth(1).fill('4')
    await fourthInputs.nth(1).blur()
  }
  const beforeApply = await read()
  assert.deepEqual(rowsOf(beforeApply), rowsOf(baseline), 'Editing a draft must not persist')
  const saveResponse = page.waitForResponse(r => r.url().endsWith(`/tasks/${taskId}/submit`) && r.request().method() === 'POST')
  await dialog.getByRole('button', { name: /Apply/ }).click()
  await dialog.waitFor({ state: 'hidden' })
  assert.equal(await page.locator('.el-overlay-message-box:visible').count(), 0, 'Apply must not trigger discard confirmation')
  const savedResponse = await saveResponse
  const savedBody = await savedResponse.json()
  assert.equal(savedResponse.ok() && savedBody.success, true, `Save rejected: ${savedBody.error?.message || ''}`)
  const saved = await read()
  const expectedCount = sequencePk ? 4 : 3
  assert.equal(rowsOf(saved).length, expectedCount)
  const newRow = rowsOf(saved).find(row => row[textField] === 'Third synthetic line')
  assert.equal(newRow[textField], 'Third synthetic line')
  assert.equal(newRow[numberField], readonlyNumber ? 0 : 3)
  assert.equal(newRow[fkField], 'SYNTHETIC-CASE')
  if (manualPk) assert.equal(newRow[pkField], 'SYNTHETIC-THREE')
  else if (sequencePk) {
    assert.equal(typeof pkConfig.prefix, 'string')
    assert.ok(newRow[pkField].startsWith(pkConfig.prefix), 'Allocated key must use the frozen configured prefix')
    assert.match(newRow[pkField].slice(pkConfig.prefix.length), /^\d+$/)
    const fourth = rowsOf(saved).find(row => row[textField] === 'Fourth synthetic line')
    assert.equal(fourth[numberField], 4)
    assert.equal(fourth[fkField], 'SYNTHETIC-CASE')
    assert.notEqual(fourth[pkField], newRow[pkField])
    assert.ok(fourth[pkField].startsWith(pkConfig.prefix))
    assert.equal(allocations.length, 1, 'Two sequence keys must be reserved in a single request')
    assert.equal(allocations[0].count, 2)
  }
  else assert.match(newRow[pkField], /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i)
  assert.equal(typeof newRow._wsRowVersion, 'number')
  if (readonlyNumber) {
    const unmarked = structuredClone(savedResponse.request().postDataJSON())
    delete unmarked.subTableBatchChanges
    delete unmarked.subTableBindingScopes
    delete unmarked.subTableData // Controller would otherwise override formData with the unchanged transport copy.
    unmarked.formData = structuredClone(saved.fieldValues)
    unmarked.formData.__subTables__[storeKey][0][numberField] = 99
    const denied = await page.request.post(`${origin}/api/portal/tasks/${taskId}/submit`, { data: unmarked })
    const deniedBody = await denied.json()
    assert.equal(deniedBody.success, false, 'Omitting batch marker must not bypass current task readonly permission')
    assert.equal(String(deniedBody.error?.code), '403')
    assert.deepEqual(rowsOf(await read()), rowsOf(saved), 'Denied unmarked write must leave persisted rows unchanged')
  }
  const duplicateRequest = structuredClone(savedResponse.request().postDataJSON())
  duplicateRequest.formData.__subTables__[storeKey][0][textField] = 'MUST-NOT-PERSIST'
  const duplicateResponse = await page.request.post(`${origin}/api/portal/tasks/${taskId}/submit`, { data: duplicateRequest })
  const duplicateBody = await duplicateResponse.json()
  assert.equal(duplicateBody.success, false, 'Replayed Add identity must be rejected')
  assert.equal(String(duplicateBody.error?.code), '409', 'Duplicate identity must produce a conflict, not an unrelated server failure')
  assert.deepEqual(rowsOf(await read()), rowsOf(saved), 'Rejected batch must not persist the accompanying first-row edit')
  const forgedRequest = structuredClone(duplicateRequest)
  forgedRequest.subTableBatchChanges[0].batchControlId = 'unavailable-synthetic-control'
  const forgedResponse = await page.request.post(`${origin}/api/portal/tasks/${taskId}/submit`, { data: forgedRequest })
  const forgedBody = await forgedResponse.json()
  assert.equal(forgedBody.success, false, 'Forged control identity must be rejected')
  assert.equal(String(forgedBody.error?.code), '403')
  assert.deepEqual(rowsOf(await read()), rowsOf(saved), 'Unauthorized batch must not change any saved row')
  await page.reload()
  await batchButton.waitFor()
  await batchButton.click()
  assert.equal(await dialog.locator('.el-table__body-wrapper tr').count(), expectedCount)
  await dialog.screenshot({ path: fileURLToPath(new URL(`${new Date().toISOString().slice(0, 10)}_batch-${variant}-add-persisted.png`, out)) })
  await dialog.locator('.el-table__header-wrapper .el-checkbox').click()
  await dialog.getByRole('button', { name: 'Delete', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: /Confirm|OK/, exact: true }).click()
  assert.equal(await dialog.locator('.el-table__body-wrapper tr').count(), 0)
  assert.equal(rowsOf(await read()).length, expectedCount, 'Delete stays in local draft before Apply')
  const deleteSaveResponse = page.waitForResponse(r => r.url().endsWith(`/tasks/${taskId}/submit`) && r.request().method() === 'POST')
  await dialog.getByRole('button', { name: /Apply/ }).click()
  const deleteResponse = await deleteSaveResponse
  const deleteBody = await deleteResponse.json()
  assert.equal(deleteResponse.ok() && deleteBody.success, true, `Delete rejected: ${deleteBody.error?.message || ''}`)
  assert.deepEqual(rowsOf(await read()), [], 'Versioned explicit deletion must persist an empty collection')
  await page.reload()
  await batchButton.waitFor()
  await batchButton.click()
  assert.equal(await dialog.locator('.el-table__body-wrapper tr').count(), 0)
  await dialog.screenshot({ path: fileURLToPath(new URL(`${new Date().toISOString().slice(0, 10)}_batch-${variant}-delete-empty-persisted.png`, out)) })
  await page.setViewportSize({ width: 390, height: 844 })
  await dialog.screenshot({ path: fileURLToPath(new URL(`${new Date().toISOString().slice(0, 10)}_batch-${variant}-narrow.png`, out)) })
  assert.deepEqual(runtimeErrors, [], 'Browser runtime and console errors must be absent')
  console.log('PASS: isolated batch Add/Delete draft, Apply, autosave, reload, PK/FK and row-version checks')
} finally {
  await browser.close()
}
