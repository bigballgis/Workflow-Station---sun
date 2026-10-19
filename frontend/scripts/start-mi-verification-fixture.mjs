/** Starts only an isolated MI clone and creates test-initiator-owned tasks. */
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaAdminPassword, loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'

const unitId = process.env.MI_FIXTURE_UNIT_ID
if (!/^\d+$/.test(unitId || '')) throw new Error('MI_FIXTURE_UNIT_ID required')
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  let pageErrors = 0
  page.on('pageerror', () => pageErrors++)
  const api = async (prefix, method, path, data) => {
    const response = await page.request.fetch(`http://localhost:3000${prefix}${path}`, { method, data })
    const body = await response.json()
    assert.equal(response.ok() && body.success !== false, true, `${method} ${path}: ${body.error?.message || response.status()}`)
    return body.data ?? body
  }
  await loginViaDwPassword(page)
  const unit = await api('/api/v1', 'GET', `/function-units/${unitId}`)
  assert.match(unit.name, /^MI verification [a-z0-9]+$/)
  const tables = await api('/api/v1', 'GET', `/function-units/${unitId}/tables`)
  const main = tables.find(t => t.tableType === 'MAIN')
  const participants = tables.find(t => t.fieldDefinitions.some(f => f.fieldName === 'assignee'))
  const people = tables.find(t => t.fieldDefinitions.some(f => f.fieldName === 'sex'))
  const attachment = tables.find(t => t.tableType === 'SUB' && t.fieldDefinitions.some(f => f.dataType === 'FILE'))
  assert.ok(main && participants && people, 'Expected synthetic MI regression schema')
  const pk = table => table.fieldDefinitions.filter(f => f.isPrimaryKey).map(f => f.fieldName)
  assert.equal(pk(main).length, 1)
  assert.equal(pk(participants).length, 1)
  assert.equal(pk(people).length, 1)
  const parentFk = participants.fieldDefinitions.find(f => f.isForeignKey && f.refTableId === main.id)
  const childFk = people.fieldDefinitions.find(f => f.isForeignKey && f.refTableId === participants.id)
  const attachmentFk = attachment?.fieldDefinitions.find(f => f.isForeignKey && f.refTableId === main.id)
  assert.ok(parentFk && childFk, 'Expected configured parent/participant FK relationship')
  const login = await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
  const roleId = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')).activeRoleId)
  await loginViaAdminPassword(page)
  const catalog = await api('/api/v1/admin', 'GET', `/function-units/by-process-key/${unit.code}`)
  assert.equal(catalog.name, unit.name)
  const accessPath = `/function-units/${catalog.id}/access`
  const access = await api('/api/v1/admin', 'GET', accessPath)
  if (!access.some(a => a.roleId === roleId)) await api('/api/v1/admin', 'POST', accessPath, { roleId })
  await loginViaPortalPassword(page, { loginOrigin: 'http://localhost:3101', buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
  assert.ok(attachment && attachmentFk, 'Shared attachment test schema is required')
  const uploadResponse = await page.request.post('http://localhost:3000/api/v1/upload', {
    multipart: { file: { name: 'synthetic-mi.txt', mimeType: 'text/plain', buffer: Buffer.from('Synthetic MI attachment verification.\n') } },
  })
  const uploadBody = await uploadResponse.json()
  assert.equal(uploadResponse.ok() && uploadBody.success, true, 'Synthetic attachment must upload through the real service')
  const fileUrl = uploadBody.data.url
  assert.match(fileUrl, /^\/api\/v1\/upload\/files\//)
  const parentKey = randomUUID()
  const participantRows = ['SYNTHETIC-MI-ONE', 'SYNTHETIC-MI-TWO'].map((key, index) => ({
    [pk(participants)[0]]: key, [parentFk.fieldName]: parentKey, assignee: login.userId,
    name: `Synthetic participant ${index + 1}`, role_code: '', bu_code: '', task_status: 'NOT_STARTED',
  }))
  const resumedId = process.env.MI_FIXTURE_PROCESS_ID
  if (resumedId && !/^[a-f0-9-]{36}$/.test(resumedId)) throw new Error('Invalid synthetic process ID')
  const started = resumedId ? await api('/api/portal', 'GET', `/processes/${resumedId}`) : await api('/api/portal', 'POST', `/processes/${unit.code}/start`, {
    formData: { [pk(main)[0]]: parentKey, __subTables__: {
      [`dw:${participants.tableName}`]: participantRows, [`dw:${people.tableName}`]: [],
      [`dw:${attachment.tableName}`]: [{ [pk(attachment)[0]]: randomUUID(), [attachmentFk.fieldName]: parentKey,
        [attachment.fieldDefinitions.find(f => f.dataType === 'FILE').fieldName]: fileUrl }],
    } }, remark: 'Synthetic MI isolation regression',
  })
  if (resumedId) assert.equal(started.functionUnitCode ?? started.processDefinitionKey, unit.code, 'Refusing a process outside the synthetic clone')
  const processId = started.id
  console.log(JSON.stringify({ step: 'isolated-mi-started', processId }))
  const tasks = async () => {
    const result = await api('/api/portal', 'POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] })
    return (result.content ?? result.records ?? result).filter(t => t.processInstanceId === processId)
  }
  const waitTasks = async (count, excludedId) => {
    const deadline = Date.now() + 60000
    while (Date.now() < deadline) {
      const result = await tasks()
      if (result.length === count && !result.some(t => t.taskId === excludedId)) return result
      await new Promise(resolve => setTimeout(resolve, 1000))
    }
    throw new Error(`Synthetic task index did not converge to ${count} tasks`)
  }
  let todo = await tasks()
  if (!resumedId) {
    assert.equal(todo.length, 1, 'Synthetic assignment host must be the sole initial task')
    await api('/api/portal', 'POST', `/tasks/${todo[0].taskId}/complete`, { taskId: todo[0].taskId, action: 'APPROVE', comment: 'Synthetic host completion' })
  }
  todo = await waitTasks(2)
  assert.equal(todo.length, 2, 'Exactly two synthetic MI participant tasks must be generated')
  const firstId = todo[0].taskId
  const form = await api('/api/portal', 'GET', `/tasks/${firstId}/form-data`)
  const current = form.fieldValues._currentItem
  assert.ok(current, 'MI form must expose its own authoritative current item')
  const ownerKey = current[pk(participants)[0]] ?? current.rowId
  assert.ok(participantRows.some(row => row[pk(participants)[0]] === ownerKey))
  const participantStore = `dw:${participants.tableName}`
  const parentLinks = values => new Map(values.__subTables__[participantStore].map(row =>
    [row[pk(participants)[0]], row[parentFk.fieldName]]))
  const originalParentLinks = parentLinks(form.fieldValues)
  let formData
  let expectedAge = '666'
  if (process.env.MI_FIXTURE_API_SEED === '1') {
    const controls = Object.values(form.subTableBatchCapabilities || {})
    const control = controls.find(value => value.rowDesign.binding.tableName === people.tableName)
    assert.ok(control, 'API seeding is only for the explicitly enhanced synthetic clone')
    const baseline = await api('/api/portal', 'GET', `/processes/${processId}`)
    const seeded = structuredClone(baseline.variables)
    seeded.__subTables__[`dw:${people.tableName}`] = [{ [pk(people)[0]]: randomUUID(), [childFk.fieldName]: ownerKey, age: '666' }]
    const scope = { bindingId: String(control.bindingId), storeKey: `dw:${people.tableName}`,
      rowKeys: seeded.__subTables__[`dw:${people.tableName}`].map(row => ({ [pk(people)[0]]: row[pk(people)[0]] })), emptied: false }
    await api('/api/portal', 'POST', `/tasks/${firstId}/submit`, { formData: seeded, subTableBindingScopes: [scope] })
    formData = (await api('/api/portal', 'GET', `/processes/${processId}`)).variables
    console.log(JSON.stringify({ step: 'synthetic-api-seed-only', taskId: firstId, uiVerified: false }))
  } else {
  // Use the real inline editor: it also prepares PK/FK and mirrors the participant's nested child slice.
  await page.goto(`http://localhost:3101/portal/tasks/${firstId}`)
  const age = page.locator('.sub-table-inline-form .el-form-item')
    .filter({ has: page.locator('.el-form-item__label', { hasText: /^Age$/i }) }).locator('input')
  await age.waitFor()
  const saveResponse = page.waitForResponse(r => r.url().endsWith(`/tasks/${firstId}/submit`) && r.request().method() === 'POST')
  await age.fill('666')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  const response = await saveResponse
  const saveBody = await response.json()
  if (!response.ok() || !saveBody.success) {
    const stored = await api('/api/portal', 'GET', `/processes/${processId}`)
    const submitted = response.request().postDataJSON().formData.__subTables__
    for (const [store, rows] of Object.entries(submitted)) {
      const before = stored.variables.__subTables__?.[store] || []
      if (JSON.stringify(before) === JSON.stringify(rows)) continue
      const fields = [...new Set([...before.flatMap(row => Object.keys(row)), ...rows.flatMap(row => Object.keys(row))])]
        .filter(field => JSON.stringify(before.map(row => row[field])) !== JSON.stringify(rows.map(row => row[field])))
      console.log(JSON.stringify({ step: 'synthetic-save-diff', store, beforeRows: before.length, submittedRows: rows.length, fields }))
    }
  }
  assert.equal(response.ok() && saveBody.success, true,
    `Synthetic MI inline Save must succeed: HTTP ${response.status()} ${saveBody.message || saveBody.error?.message || ''}`)
  if (Object.hasOwn(response.request().postDataJSON().formData.__subTables__, participantStore)) {
    assert.deepEqual(parentLinks(response.request().postDataJSON().formData), originalParentLinks,
      'Submitted MI child edits must preserve participant parent FKs')
  } else {
    assert.equal(form.subTableBatchRuntimeEnabled, true, 'Only enhanced partial submissions may omit the parent store')
  }
  const saved = await api('/api/portal', 'GET', `/tasks/${firstId}/form-data`)
  formData = saved.fieldValues
  assert.deepEqual(parentLinks(formData), originalParentLinks, 'Editing an MI child must not change any participant parent FK')
  assert.ok(formData.__subTables__?.[`dw:${people.tableName}`]?.some(row => row[childFk.fieldName] === ownerKey && row.age === '666'), 'Inline Save must persist only the owning participant child')
  }
  if (form.subTableBatchRuntimeEnabled === true && process.env.MI_FIXTURE_API_SEED !== '1') {
    for (const [suffix, width, height] of [['desktop', 1366, 768], ['narrow', 480, 800]]) {
      await page.setViewportSize({ width, height })
      await page.evaluate(async () => {
        await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
        await Promise.all(document.getAnimations().filter(animation => animation.effect?.getTiming().iterations !== Infinity)
          .map(animation => animation.finished))
      })
      const path = fileURLToPath(new URL(`../user-portal/verification-screenshots/${new Date().toISOString().slice(0, 10)}_batch-mi-save-${suffix}.png`, import.meta.url))
      await page.screenshot({ path, mask: [page.locator('.user-info')] })
      console.log(path)
    }
    await page.setViewportSize({ width: 1366, height: 768 })
    if (process.env.MI_FIXTURE_BATCH_COMPLETE === '1') {
      await page.getByRole('button', { name: 'Batch Edit', exact: true }).click()
      const batch = page.locator('.el-dialog:visible')
      const input = batch.locator('.el-table__body-wrapper tr').first().locator('input:not([type="checkbox"])')
      await input.fill('777')
      await input.blur()
      await batch.getByRole('button', { name: /Apply/ }).click()
      await batch.waitFor({ state: 'hidden' })
      const draftCheck = await api('/api/portal', 'GET', `/processes/${processId}`)
      assert.ok(draftCheck.variables.__subTables__[`dw:${people.tableName}`].some(row =>
        row[childFk.fieldName] === ownerKey && String(row.age) === '666'), 'MI Apply must remain a page draft before Complete')
      expectedAge = '777'
    }
    await page.getByRole('button', { name: 'Approve', exact: true }).click()
    const dialog = page.getByRole('dialog').filter({ has: page.locator('[data-testid="approve-comment-field"]') })
    await dialog.locator('textarea').fill('Synthetic UI MI completion')
    const completion = page.waitForResponse(r => r.url().endsWith(`/tasks/${firstId}/complete`) && r.request().method() === 'POST')
    await dialog.getByRole('button', { name: 'Confirm', exact: true }).click()
    const response = await completion
    const body = await response.json()
    assert.equal(response.ok() && body.success, true, `Synthetic UI Complete: ${body.error?.message || response.status()}`)
    assert.equal(pageErrors, 0, 'UI save and completion must have zero page errors')
  } else {
    await api('/api/portal', 'POST', `/tasks/${firstId}/complete`, { taskId: firstId, action: 'APPROVE', formData, comment: 'Synthetic first MI step' })
  }
  const next = await waitTasks(2, firstId)
  const stageTwo = []
  for (const task of next) {
    const data = await api('/api/portal', 'GET', `/tasks/${task.taskId}/form-data`)
    const item = data.fieldValues._currentItem
    if ((item?.[pk(participants)[0]] ?? item?.rowId) === ownerKey) {
      const persisted = data.fieldValues.__subTables__?.[`dw:${people.tableName}`] || []
      assert.ok(persisted.some(row => row[childFk.fieldName] === ownerKey && String(row.age) === expectedAge), 'Age must carry forward from the real saved/completed first MI step')
      stageTwo.push(task.taskId)
    }
  }
  assert.equal(stageTwo.length, 1, 'One next-stage task must belong to the filled participant')
  console.log(JSON.stringify({ step: 'isolated-mi-ready', processId, taskId: stageTwo[0] }))
} finally {
  await browser.close()
}
