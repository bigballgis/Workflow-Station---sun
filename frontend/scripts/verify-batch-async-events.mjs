/** Local-only synthetic MCY copy. Source FU48 and DEV business tasks are never written. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaPortalPassword, loginViaAdminPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'
import { readBatchStartContext } from './batch-start-context.mjs'
import { originalEmailScript, guardedEmailScript } from './fixtures/batch-email-scripts.mjs'

const origin = 'http://localhost:3000'
const statePath = new URL('../../.codex-async-events-fixture.json', import.meta.url)
const baseStatePath = new URL('../../.codex-async-events-base.json', import.meta.url)
const mode = process.argv[2]
if (mode === 'prepare') {
  execFileSync(process.execPath, ['scripts/verify-batch-lookup-assignment-runtime.mjs', 'prepare'], {
    env: { ...process.env, LOOKUP_SOURCE_FU_ID: '48', LOOKUP_SOURCE_FU_NAME: 'MCY Debit Card_20260525', LOOKUP_FIXTURE_STATE_PATH: fileURLToPath(baseStatePath) }, stdio: 'inherit',
  })
}
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1700, height: 1150 } })
page.setDefaultTimeout(20000)
page.setDefaultNavigationTimeout(30000)
const errors = []
page.on('response', response => { if (/\/admin\/users\/user-/.test(response.url())) console.log(JSON.stringify({ step: 'lookup-response', url: new URL(response.url()).pathname, status: response.status() })) })
page.on('pageerror', error => { errors.push(error.message); console.log(JSON.stringify({ step: 'pageerror', message: error.message })) })
const unwrap = async response => {
  const body = await response.json()
  assert.ok(response.ok() && body.success !== false, `${response.url()}: ${body.message || body.error?.message || response.status()}`)
  return body.data ?? body
}
const api = (method, path, data) => page.request.fetch(origin + '/api/v1' + path, { method, data }).then(unwrap)
const portal = (method, path, data) => page.request.fetch(origin + '/api/portal' + path, { method, data }).then(unwrap)
try {
  await loginViaDwPassword(page)
  const state = JSON.parse(readFileSync(mode === 'prepare' ? baseStatePath : statePath, 'utf8'))
  const base = `/function-units/${state.fixtureUnitId}`
  const unit = await api('GET', base)
  assert.ok(unit.tags.includes('batch-verification-synthetic') && unit.name.startsWith('Batch verification Lookup '))
  if (mode === 'prepare' || mode === 'guarded' || mode === 'original') {
    const tables = await api('GET', `${base}/tables`)
    const table = tables.find(item => item.tableType === 'SUB')
    const mainTable = tables.find(item => item.tableType === 'MAIN')
    const mainAdditions = ['assignee_id', 'assignee_email', 'scenario', 'probe', 'event_trace'].filter(fieldName => !mainTable.fieldDefinitions.some(field => field.fieldName === fieldName))
    if (mainAdditions.length) await api('PUT', `${base}/tables/${mainTable.id}`, { tableName: mainTable.tableName, tableType: mainTable.tableType, tableDisplayName: mainTable.tableDisplayName,
      fields: [...mainTable.fieldDefinitions.map(field => ({ ...field, pkGeneration: field.pkGenerationJson, computedField: field.computedFieldJson })), ...mainAdditions.map(fieldName => ({ fieldName, dataType: 'VARCHAR', length: fieldName === 'event_trace' ? 4000 : 255, nullable: true, displayName: fieldName }))] })
    const additions = ['assignee_email', 'scenario', 'probe', 'event_trace'].filter(name => !table.fieldDefinitions.some(field => field.fieldName === name))
    if (additions.length) await api('PUT', `${base}/tables/${table.id}`, { tableName: table.tableName, tableType: table.tableType, tableDisplayName: table.tableDisplayName,
      fields: [...table.fieldDefinitions.map(field => ({ ...field, pkGeneration: field.pkGenerationJson, computedField: field.computedFieldJson })),
        ...additions.map(fieldName => ({ fieldName, dataType: 'VARCHAR', length: fieldName === 'event_trace' ? 4000 : 255, nullable: true, displayName: fieldName }))] })
    const forms = await api('GET', `${base}/forms`)
    const script = mode === 'guarded' ? guardedEmailScript : originalEmailScript
    for (const form of forms) {
      const binding = form.tableBindings.find(item => item.bindingType === 'SUB')
      const person = { type: 'select', field: 'assignee_id', title: 'Assign To', props: { filterable: true },
        options: [{ label: 'Developer test', value: 'user-dev' }, { label: 'Administrator test', value: 'user-admin' }], on: { change: script } }
      const ordinary = { type: 'input', field: 'assignee_id', title: 'Assign To', on: { change: script } }
      const fields = [
        { type: 'input', field: 'assignee_email', title: 'Assignee Email' },
        { type: 'select', field: 'scenario', title: 'Scenario', options: ['normal', 'reject', 'field-error', 'async-submit', 'reset-reject', 'throw'].map(value => ({ label: value, value })) },
        { type: 'input', field: 'probe', title: 'Probe', on: { change: '$FNX:api.updateRule("probe", {info:"Pending probe"}); return Promise.resolve().then(() => { if(api.getValue("scenario") === "throw") throw new Error("Synthetic probe failure"); if(value === "mixed" && String(api.getValue("card_number")).endsWith("2")) throw new Error("Synthetic mixed-row failure"); api.setValue("event_trace", "probe:" + value); api.updateRule("probe", {info:""}); });' } },
        { type: 'input', field: 'event_trace', title: 'Event Trace' },
      ]
      const options = {
        onCreated: '$FNX:return Promise.resolve().then(() => api.updateRule("probe", {info:"Created"}));',
        onMounted: '$FNX:return Promise.resolve().then(() => api.updateRule("probe", {info:"Ready"}));',
        beforeSubmit: '$FNX:return Promise.resolve(api.getValue("scenario") !== "reject");',
        onSubmit: '$FNX:return Promise.resolve().then(() => { if(api.getValue("scenario") === "field-error") api.setFieldError("probe", "Synthetic validation failure"); if(api.getValue("scenario") === "async-submit") api.setValue("event_trace", "submit-finished"); });',
        onReset: '$FNX:return Promise.resolve().then(() => { if(api.getValue("scenario") === "reset-reject") throw new Error("Synthetic reset failure"); });',
      }
      form.configJson.subForms[binding.id] = { rule: [ordinary, ...structuredClone(fields)], options: structuredClone(options) }
      const mainBinding = form.tableBindings.find(item => item.bindingType === 'PRIMARY')
      if (mainBinding.bindingMode !== 'EDITABLE') await api('PUT', `${base}/forms/${form.id}/bindings/${mainBinding.id}`, { tableId: mainTable.id, bindingType: 'PRIMARY', bindingMode: 'EDITABLE', sortOrder: 0 })
      form.configJson.rule = form.configJson.rule.filter(rule => !['assignee_id', 'assignee_email', 'scenario', 'probe', 'event_trace'].includes(rule.field))
      form.configJson.rule.unshift(...[ordinary, ...structuredClone(fields)].map(rule => ({ ...rule, title: 'Main ' + rule.title })))
      form.configJson.options = structuredClone(options)
      for (const field of ['assignee_id', 'assignee_email', 'scenario', 'probe', 'event_trace']) form.fieldPermissions = { ...form.fieldPermissions, [field]: 'EDITABLE', [`${mainBinding.id}:${field}`]: 'EDITABLE' }
      if (form.formType === 'TASK') {
        form.configJson.rule.find(rule => rule.type === 'subTable').props.allowBatchEdit = true
        form.configJson.batchForms[binding.id] = { schemaVersion: 1, rule: [{ type: 'miAssignment', children: [person] }, ...fields], options }
        form.fieldPermissions = { ...form.fieldPermissions, [`${binding.id}:assignee_email`]: 'EDITABLE', [`${binding.id}:scenario`]: 'EDITABLE', [`${binding.id}:probe`]: 'EDITABLE', [`${binding.id}:event_trace`]: 'EDITABLE' }
        state.formId = form.id
      }
      await api('PUT', `${base}/forms/${form.id}`, form)
    }
    const deployment = await api('POST', `${base}/deploy`, { environment: 'DEVELOPMENT', autoEnable: true, conflictStrategy: 'FAIL', changeLog: `Synthetic async event verification: ${mode}` })
    await waitForFixtureDeployment(api, deployment.deploymentId)
    // Keep authentication rate limiting enabled while preparing independent fixtures.
    await new Promise(resolve => setTimeout(resolve, 60000))
    await loginViaAdminPassword(page)
    const catalog = await api('GET', `/admin/function-units/by-process-key/${encodeURIComponent(state.processKey)}`)
    state.catalogId = catalog.id
    await loginViaPortalPassword(page)
    const old = await portal('GET', `/tasks/${state.taskId}/form-data`)
    const primary = tables.find(item => item.tableType === 'MAIN').fieldDefinitions.find(field => field.isPrimaryKey).fieldName
    const caseId = `ASYNC-CASE-${Date.now()}`
    const rowKey = table.fieldDefinitions.filter(field => field.isPrimaryKey).map(field => field.fieldName)
    const rows = Array.from({ length: 3 }, (_, index) => {
      const row = { ...old.fieldValues.__subTables__[state.store][0], assignee_id: 'user-dev', assignee_email: '', scenario: 'normal', probe: '', event_trace: '', case_id: caseId }
      row.card_number = 'ASYNC-CARD-' + (index + 1)
      for (const key of rowKey) row[key] = `ASYNC-ROW-${index + 1}-${Date.now()}`
      for (const field of state.lookupFields) row[field] = null
      delete row._wsRowVersion; delete row.platformRowUuid
      return row
    })
    const started = await portal('POST', `/processes/${encodeURIComponent(state.processKey)}/start`, {
      batchStartContext: await readBatchStartContext(page, state.catalogId),
      formData: { [primary]: caseId, legal_hold: false, assignee_id: 'user-dev', assignee_email: '', scenario: 'normal', probe: '', event_trace: '', __subTables__: { [state.store]: rows } }, remark: 'Synthetic asynchronous event verification',
    })
    state.processId = started.processInstanceId || started.id
    const tasks = (await unwrap(await page.request.get(`http://localhost:8081/api/v1/tasks?processInstanceId=${state.processId}&page=0&size=100`))).tasks
    assert.equal(tasks.length, 1)
    state.taskId = tasks[0].id ?? tasks[0].taskId
    state.scriptKind = mode === 'guarded' ? 'guarded' : 'original'
    writeFileSync(statePath, JSON.stringify(state, null, 2))
    console.log(JSON.stringify({ step: 'async-fixture', unitId: state.fixtureUnitId, taskId: state.taskId, scriptKind: state.scriptKind }))
  } else if (mode === 'ui' || mode === 'red') {
    await verifyUi(state)
  } else if (mode === 'dw' || mode === 'dw-original') {
    await verifyDw(mode === 'dw-original' ? { ...state, scriptKind: 'original' } : state)
  } else throw new Error('Expected prepare, guarded or ui')
  assert.deepEqual(errors, [])
} catch (error) {
  const folder = new URL('../developer-workstation/verification-screenshots/', import.meta.url)
  mkdirSync(folder, { recursive: true })
  await page.screenshot({ path: fileURLToPath(new URL(`2026-11-04-async-${mode}-failure-${Date.now()}.png`, folder)), fullPage: true }).catch(() => {})
  console.log(JSON.stringify({ step: 'failure-dom', dialogs: await page.locator('.el-dialog:visible,.sub-table-nested-modal-panel:visible').allTextContents(), inputs: await page.locator('.sub-table-nested-modal-panel input').evaluateAll(inputs => inputs.map(input => ({label:input.closest('.el-form-item')?.textContent,value:input.value}))), errors }))
  throw error
} finally { await browser.close() }

async function verifyUi(state) {
  await loginViaPortalPassword(page)
  await page.goto(`${origin}/portal/tasks/${state.taskId}`, { waitUntil: 'networkidle' })
  if (mode !== 'red') {
    const main = title => page.locator('.form-renderer .el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp('^Main ' + title + '$') }) }).first()
    const admin = await api('GET', '/admin/users/user-admin')
    await page.route('**/api/v1/admin/users/user-admin', async route => { await new Promise(resolve => setTimeout(resolve, 600)); await route.continue() })
    await main('Assign To').locator('input').fill(''); await main('Assign To').locator('input').press('Tab')
    await main('Assign To').locator('input').fill('user-admin'); await main('Assign To').locator('input').press('Tab')
    await page.waitForFunction(email => [...document.querySelectorAll('.form-renderer input')].some(input => input.value === email), admin.email)
    assert.equal(await main('Assignee Email').locator('input').inputValue(), admin.email)
    await page.unroute('**/api/v1/admin/users/user-admin')
    const mainFolder = new URL('../user-portal/verification-screenshots/', import.meta.url)
    mkdirSync(mainFolder, { recursive: true })
    await page.locator('.form-renderer').first().screenshot({ path: fileURLToPath(new URL(`2026-11-04-async-${state.scriptKind}-portal-main.png`, mainFolder)) })
    console.log(JSON.stringify({ step: 'portal-main-form-email-success', scriptKind: state.scriptKind }))
  }
  const grid = page.locator('.sub-table-field').first()
  await grid.locator('.el-table__body-wrapper tbody tr').nth(2).waitFor()
  for (let i = 0; i < 2; i++) await grid.locator('.el-table__body-wrapper tbody tr').nth(i).locator('.el-checkbox').click()
  await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  const dialog = page.locator('.batch-edit-dialog')
  await dialog.waitFor()
  await dialog.getByText('Assign by person', { exact: true }).click()
  const folder = new URL('../user-portal/verification-screenshots/', import.meta.url)
  mkdirSync(folder, { recursive: true })
  if (mode === 'red') {
    await page.waitForTimeout(500)
    await page.screenshot({ path: fileURLToPath(new URL('2026-11-04-async-red-original.png', folder)), fullPage: true })
    assert.ok(errors.some(error => error.includes('updateRule is not a function')) || (await dialog.innerText()).includes('updateRule is not a function'))
    console.log(JSON.stringify({ step: 'red-confirmed', taskId: state.taskId, errors }))
    errors.length = 0
    return
  }
  const select = dialog.locator('.batch-assignment-editor .el-select').first()
  await select.click()
  await select.locator('input').fill('admin')
  await page.getByRole('option').filter({ hasText: /admin/i }).first().click()
  await page.waitForTimeout(1500)
  console.log(JSON.stringify({ step: 'batch-after-select', text: await dialog.innerText() }))
  await page.screenshot({ path: fileURLToPath(new URL(`2026-11-04-async-${mode}-${state.scriptKind}.png`, folder)), fullPage: true })
  if (mode === 'red') {
    assert.match(await dialog.innerText(), /updateRule is not a function/)
    console.log(JSON.stringify({ step: 'red-confirmed', taskId: state.taskId }))
    return
  }
  const email = dialog.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^Assignee Email$/ }) }).locator('input')
  const user = await api('GET', '/admin/users/user-admin')
  assert.ok(user.email)
  await email.waitFor()
  assert.equal(await email.inputValue(), user.email)
  console.log(JSON.stringify({ step: 'exact-script-browser-success', scriptKind: state.scriptKind }))
  const field = title => dialog.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp('^' + title + '$') }) })
  const choose = async search => {
    await select.click(); await select.locator('input').fill(search)
    await page.getByRole('option').filter({ hasText: new RegExp(search, 'i') }).first().click()
  }
  const chooseScenario = async value => {
    await field('Scenario').locator('.el-select').click()
    await page.getByRole('option', { name: value, exact: true }).click()
  }
  const before = (await portal('GET', `/tasks/${state.taskId}/form-data`)).fieldValues.__subTables__[state.store]
  let release, requestStarted
  let started = new Promise(resolve => { requestStarted = resolve })
  const userRoute = '**/api/v1/admin/users/*'
  await page.route(userRoute, async route => {
    requestStarted()
    await new Promise(resolve => { release = resolve })
    await route.continue().catch(() => {})
  })
  await choose('developer'); await started
  assert.equal(await dialog.getByRole('button', { name: /Apply/ }).isDisabled(), true)
  // A later choice invalidates the old operation, even if its response arrives afterwards.
  await page.unroute(userRoute)
  await choose('admin')
  release()
  await page.waitForFunction(({ email }) => [...document.querySelectorAll('.batch-edit-dialog .el-form-item')].some(el => el.innerText.includes('Assignee Email') && el.querySelector('input')?.value === email), { email: user.email })
  assert.equal(await email.inputValue(), user.email)
  assert.deepEqual((await portal('GET', `/tasks/${state.taskId}/form-data`)).fieldValues.__subTables__[state.store], before)
  console.log(JSON.stringify({ step: 'pending-race-no-save-passed' }))
  if (state.scriptKind === 'guarded') {
    await page.route(userRoute, route => route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }))
    await choose('developer')
    await dialog.getByText(/Event request failed: HTTP 500/).first().waitFor()
    await dialog.getByRole('button', { name: /Apply/ }).click()
    assert.equal(await dialog.isVisible(), true)
    await page.unroute(userRoute)
    await choose('admin')
    await page.waitForFunction(({ email }) => [...document.querySelectorAll('.batch-edit-dialog input')].some(el => el.value === email), { email: user.email })
    await page.route(userRoute, route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'user-dev', email: '' }) }))
    await choose('developer'); await dialog.getByText('Selected user has no valid email.', { exact: true }).first().waitFor()
    await dialog.getByRole('button', { name: /Apply/ }).click(); assert.equal(await dialog.isVisible(), true)
    await page.unroute(userRoute); await choose('admin')
    await page.waitForFunction(({ email }) => [...document.querySelectorAll('.batch-edit-dialog input')].some(el => el.value === email), { email: user.email })
    assert.ok(before[1].card_number.endsWith('2'))
    await field('Probe').locator('input').fill('mixed'); await field('Probe').locator('input').press('Tab')
    await dialog.getByText('Synthetic mixed-row failure', { exact: true }).waitFor()
    assert.equal(await field('Event Trace').locator('input').inputValue(), before[0].event_trace)
    await field('Probe').locator('input').fill('recovered'); await field('Probe').locator('input').press('Tab')
    await page.waitForFunction(() => [...document.querySelectorAll('.batch-edit-dialog input')].some(input => input.value === 'probe:recovered'))
    console.log(JSON.stringify({ step: 'missing-email-and-mixed-row-atomic-recovery-passed' }))
    console.log(JSON.stringify({ step: 'guarded-failure-recovery-passed' }))
  }
  await chooseScenario('reject')
  await dialog.getByRole('button', { name: /Apply/ }).click()
  assert.equal(await dialog.isVisible(), true)
  const saveMarker = `SAVE-${Date.now()}`
  await field('Probe').locator('input').fill(saveMarker); await field('Probe').locator('input').press('Tab')
  await chooseScenario('async-submit')
  await dialog.screenshot({ path: fileURLToPath(new URL(`2026-11-04-async-${state.scriptKind}-before-apply.png`, folder)) })
  const submitted = page.waitForResponse(response => response.url().endsWith(`/tasks/${state.taskId}/submit`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: /Apply/ }).click()
  await dialog.waitFor({ state: 'hidden' })
  const saved = await submitted
  await unwrap(saved)
  const after = (await portal('GET', `/tasks/${state.taskId}/form-data`)).fieldValues.__subTables__[state.store]
  for (const row of after.slice(0, 2)) { assert.equal(row.assignee_email, user.email); assert.equal(row.assignee_id, user.id); assert.equal(row.event_trace, 'submit-finished'); assert.equal(row.probe, saveMarker) }
  const business = row => Object.fromEntries(Object.entries(row).filter(([key]) => key !== '_wsRowVersion'))
  assert.deepEqual(business(after[2]), business(before[2]))
  console.log(JSON.stringify({ step: 'selected-two-save-reload-unselected-isolated', store: state.store, rowIds: after.map(row => row.platformRowUuid ?? row.id) }))
  // Ordinary row Edit uses the identical stored script and asynchronous guard contract.
  await grid.locator('.el-table__body-wrapper tbody tr').first().getByRole('button', { name: 'Edit', exact: true }).click()
  const ordinary = page.locator('.el-dialog:visible').last()
  const ordinaryField = title => ordinary.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp('^' + title + '$') }) })
  await ordinaryField('Assign To').locator('input').fill('user-dev')
  await ordinaryField('Assign To').locator('input').press('Tab')
  const developer = await api('GET', '/admin/users/user-dev')
  await page.waitForFunction(({ email }) => [...document.querySelectorAll('.el-dialog input')].some(el => el.value === email), { email: developer.email })
  assert.equal(await ordinaryField('Assignee Email').locator('input').inputValue(), developer.email)
  await ordinary.screenshot({ path: fileURLToPath(new URL(`2026-11-04-async-${state.scriptKind}-ordinary.png`, folder)) })
  await ordinary.getByRole('button', { name: 'Cancel', exact: true }).click()
  await ordinary.waitFor({ state: 'hidden' })
  console.log(JSON.stringify({ step: 'ordinary-exact-script-cancel-passed' }))
  await verifyCancellation(state, grid, user, developer, folder)
}

async function verifyCancellation(state, grid, admin, developer, folder) {
  const saved = (await portal('GET', `/tasks/${state.taskId}/form-data`)).fieldValues.__subTables__[state.store]
  const dialog = page.locator('.batch-edit-dialog:visible')
  const open = async () => {
    for (let i = 0; i < 2; i++) { const row = grid.locator('.el-table__body-wrapper tbody tr').nth(i); if (!await row.locator('input[type=checkbox]').isChecked()) await row.locator('.el-checkbox').click() }
    await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
    await dialog.getByText('Assign by person', { exact: true }).click()
  }
  let release, started
  const url = '**/api/v1/admin/users/user-dev'
  const delay = async () => {
    const ready = new Promise(resolve => { started = resolve })
    await page.route(url, async route => { started(); await new Promise(resolve => { release = resolve }); await route.continue().catch(() => {}) })
    const select = dialog.locator('.batch-assignment-editor .el-select').first()
    await select.click(); await select.locator('input').fill('developer')
    await page.getByRole('option').filter({ hasText: /developer/i }).first().click()
    await ready
  }
  const cancel = async () => {
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    const confirm = page.locator('.el-message-box:visible')
    if (await confirm.count()) await confirm.getByRole('button', { name: /OK|Confirm/ }).click()
    await dialog.waitFor({ state: 'hidden' })
  }
  await open(); await delay()
  await dialog.screenshot({ path: fileURLToPath(new URL(`2026-11-04-async-${state.scriptKind}-pending.png`, folder)) })
  await cancel(); release(); await page.unroute(url)
  await open()
  assert.ok(!(await dialog.locator('input').evaluateAll(inputs => inputs.map(input => input.value))).includes(developer.email))
  await delay()
  await dialog.getByText('Assign by person', { exact: true }).click()
  release(); await page.unroute(url)
  await page.waitForTimeout(300)
  assert.ok(!(await dialog.locator('input').evaluateAll(inputs => inputs.map(input => input.value))).includes(developer.email))
  await cancel()
  await open(); await delay()
  await dialog.getByText(/timed out/i).first().waitFor({ timeout: 20000 })
  await dialog.getByRole('button', { name: /Apply/ }).click(); assert.equal(await dialog.isVisible(), true)
  await dialog.screenshot({ path: fileURLToPath(new URL(`2026-11-04-async-${state.scriptKind}-timeout.png`, folder)) })
  release(); await page.unroute(url); await cancel()
  assert.deepEqual((await portal('GET', `/tasks/${state.taskId}/form-data`)).fieldValues.__subTables__[state.store], saved)
  console.log(JSON.stringify({ step: 'pending-cancel-reopen-mode-no-late-writes', scriptKind: state.scriptKind }))
}

async function verifyDw(state) {
  const forms = await api('GET', `/function-units/${state.fixtureUnitId}/forms`)
  const taskForm = forms.find(form => form.id === state.formId)
  const form = taskForm
  const binding = form.tableBindings.find(binding => binding.bindingType === 'SUB')
  const taskBinding = taskForm.tableBindings.find(binding => binding.bindingType === 'SUB')
  form.configJson.batchForms = { [binding.id]: structuredClone(taskForm.configJson.batchForms[taskBinding.id]) }
  const script = state.scriptKind === 'original' ? originalEmailScript : guardedEmailScript
  form.configJson.subForms[binding.id].rule.find(rule => rule.field === 'assignee_id').on.change = script
  form.configJson.rule.find(rule => rule.field === 'assignee_id').on.change = script
  const toggle = '$FNX:return Promise.resolve().then(() => api.disabled(value === "lock", "assignee_email"));'
  for (const rules of [form.configJson.rule, form.configJson.subForms[binding.id].rule]) {
    const probe = rules.find(rule => rule.field === 'probe')
    probe.on.change = [Array.isArray(probe.on.change) ? probe.on.change[0] : probe.on.change, toggle]
  }
  form.configJson.batchForms[binding.id].rule[0].children[0].on.change = script
  delete form.configJson.batchForms[binding.id].rule[0].children[0].props.allowCreate
  // The DW directory intentionally uses demo identities; exercise the unchanged
  // query from a declared ordinary Batch input with the row's real assignee ID.
  form.configJson.batchForms[binding.id].rule.find(rule => rule.field === 'probe').on.change = script
  form.configJson.rule.find(rule => rule.type === 'subTable').props.allowBatchEdit = true
  const tables = await api('GET', `/function-units/${state.fixtureUnitId}/tables`)
  const primary = tables.find(table => table.tableType === 'MAIN').fieldDefinitions.find(field => field.isPrimaryKey).fieldName
  if (!form.configJson.rule.some(rule => rule.field === primary)) {
    form.configJson.rule.unshift({ type: 'input', field: primary, title: 'Case key' })
  }
  form.configJson.rule.find(rule => rule.field === primary).value = 'ASYNC-PREVIEW-ONLY'
  await api('PUT', `/function-units/${state.fixtureUnitId}/forms/${form.id}`, form)
  const user = await api('GET', '/admin/users/user-dev')
  await page.goto(`${origin}/dev/function-units/${state.fixtureUnitId}`)
  console.log(JSON.stringify({ step: 'dw-open' }))
  await page.getByRole('tab', { name: /Form/ }).click()
  await page.getByRole('row').filter({ hasText: form.formName }).getByRole('button', { name: 'Edit', exact: true }).click()
  await page.locator('.designer-grouped-nav .el-dropdown').first().locator('.designer-nav-btn').click()
  await page.locator('.el-dropdown-menu:visible .el-dropdown-menu__item').last().click()
  await page.waitForFunction(() => [...document.querySelectorAll('.sub-table-design-wrapper')].some(el => el.textContent.includes('Assign To')))
  await page.locator('.designer-grouped-nav > .designer-nav-btn').first().click()
  await page.locator('.form-editor-view .header-actions').getByRole('button', { name: 'Preview', exact: true }).click()
  console.log(JSON.stringify({ step: 'dw-preview' }))
  const preview = page.locator('.form-preview-dialog').last()
  assert.equal(await preview.locator('.el-form-item').filter({ hasText: 'Case key' }).locator('input').inputValue(), 'ASYNC-PREVIEW-ONLY')
  await page.waitForLoadState('networkidle')
  const mainField = title => preview.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp('^Main ' + title + '$') }) })
  await mainField('Assign To').locator('input').fill('')
  await mainField('Assign To').locator('input').press('Tab')
  await mainField('Assign To').locator('input').fill('user-dev')
  await mainField('Assign To').locator('input').press('Tab')
  await page.waitForFunction(({ email }) => [...document.querySelectorAll('.form-preview-dialog > * input')].some(el => el.value === email), { email: user.email })
  assert.equal(await mainField('Assignee Email').locator('input').inputValue(), user.email)
  await mainField('Probe').locator('input').fill('lock'); await mainField('Probe').locator('input').press('Tab')
  await page.waitForFunction(() => [...document.querySelectorAll('.form-preview-dialog .el-form-item')].some(item => item.textContent.includes('Main Assignee Email') && item.querySelector('input')?.disabled))
  await mainField('Probe').locator('input').fill('unlock'); await mainField('Probe').locator('input').press('Tab')
  await page.waitForFunction(() => [...document.querySelectorAll('.form-preview-dialog .el-form-item')].some(item => item.textContent.includes('Main Assignee Email') && item.querySelector('input')?.disabled === false))
  const mainFolder = new URL('../developer-workstation/verification-screenshots/', import.meta.url)
  mkdirSync(mainFolder, { recursive: true })
  await preview.screenshot({ path: fileURLToPath(new URL(`2026-11-04-async-${state.scriptKind}-dw-main.png`, mainFolder)) })
  console.log(JSON.stringify({ step: 'dw-main-exact-script-passed' }))
  const grid = preview.locator('.sub-table-field').first()
  await grid.getByRole('button', { name: 'Add', exact: true }).click()
  await page.waitForTimeout(500)
  console.log(JSON.stringify({ step: 'dw-row-add' }))
  console.log(JSON.stringify({ step: 'dw-messages', messages: await page.locator('.el-message').allTextContents() }))
  const ordinary = page.locator('.sub-table-nested-modal-panel:visible').last()
  const field = title => ordinary.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp('^' + title + '$') }) })
  await field('Assign To').locator('input').fill('')
  await field('Assign To').locator('input').press('Tab')
  await field('Assign To').locator('input').fill('user-dev')
  await field('Assign To').locator('input').press('Tab')
  await page.waitForFunction(({ email }) => [...document.querySelectorAll('.sub-table-nested-modal-panel input')].some(el => el.value === email), { email: user.email })
  assert.equal(await field('Assignee Email').locator('input').inputValue(), user.email)
  await field('Probe').locator('input').fill('lock'); await field('Probe').locator('input').press('Tab')
  await page.waitForFunction(() => [...document.querySelectorAll('.sub-table-nested-modal-panel .el-form-item')].some(item => item.textContent.includes('Assignee Email') && item.querySelector('input')?.disabled))
  await field('Probe').locator('input').fill('unlock'); await field('Probe').locator('input').press('Tab')
  await page.waitForFunction(() => [...document.querySelectorAll('.sub-table-nested-modal-panel .el-form-item')].some(item => item.textContent.includes('Assignee Email') && item.querySelector('input')?.disabled === false))
  const folder = new URL('../developer-workstation/verification-screenshots/', import.meta.url)
  mkdirSync(folder, { recursive: true })
  await ordinary.screenshot({ path: fileURLToPath(new URL(`2026-11-04-async-${state.scriptKind}-dw-ordinary.png`, folder)) })
  await ordinary.getByRole('button', { name: 'Save', exact: true }).click()
  await ordinary.waitFor({ state: 'hidden' })
  await grid.locator('.el-table__body-wrapper tbody tr .el-checkbox').first().click()
  await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  const batch = page.locator('.batch-edit-dialog:visible')
  const batchField = title => batch.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp('^' + title + '$') }) })
  await batchField('Assignee Email').locator('input').fill('')
  await batchField('Assignee Email').locator('input').press('Tab')
  await batchField('Probe').locator('input').fill('user-dev')
  await batchField('Probe').locator('input').press('Tab')
  await page.waitForFunction(({ email }) => [...document.querySelectorAll('.batch-edit-dialog input')].some(el => el.value === email), { email: user.email })
  await batch.screenshot({ path: fileURLToPath(new URL(`2026-11-04-async-${state.scriptKind}-dw-batch.png`, folder)) })
  await batch.getByRole('button', { name: /Apply/ }).click()
  await batch.waitFor({ state: 'hidden' })
  await grid.locator('.el-table__body-wrapper tbody tr').first().getByRole('button', { name: 'Edit', exact: true }).click()
  await ordinary.waitFor()
  assert.equal(await field('Assignee Email').locator('input').inputValue(), user.email)
  await ordinary.getByRole('button', { name: 'Cancel', exact: true }).click()
  await ordinary.waitFor({ state: 'hidden' })
  const reloaded = await api('GET', `/function-units/${state.fixtureUnitId}/forms`)
  assert.deepEqual(reloaded.find(saved => saved.id === form.id).configJson, form.configJson)
  console.log(JSON.stringify({ step: 'dw-ordinary-batch-exact-success', scriptKind: state.scriptKind, unchangedDesign: true }))
}
