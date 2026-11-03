/** Local MCY template verification. Writes only explicitly tagged synthetic Function Units. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { loginViaDwPassword, loginViaPortalPassword, loginViaAdminPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'
import { readBatchStartContext } from './batch-start-context.mjs'
const mode = process.argv[2]
const origin = 'http://localhost:3000'
const browser = await chromium.launch({ headless: true })
try {
 const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
 await loginViaDwPassword(page)
 const api = async (method, path, data) => {
   const response = await page.request.fetch(`${origin}/api/v1${path}`, { method, data })
   const body = await response.json()
   assert.ok(response.ok() && body.success !== false, `${path}: ${body.message || body.error?.message}`)
   return body.data
 }
 if (mode === 'prepare') {
   const unit = await api('POST', '/function-units', { name: `Batch verification Lookup Events ${Date.now()}`,
     tags: ['batch-verification-synthetic'], virtualGroupIds: ['vg-dev-public'], description: 'MCY isolated batch event verification' })
   console.log(JSON.stringify({ step: 'created', unitId: unit.id }))
   execFileSync(process.execPath, [new URL('./verify-batch-lookup-assignment-runtime.mjs', import.meta.url).pathname.replace(/^\/(.:)/, '$1'), 'prepare'], {
     env: { ...process.env, LOOKUP_SOURCE_FU_ID: '48', LOOKUP_SOURCE_FU_NAME: 'MCY Debit Card_20260525', LOOKUP_FIXTURE_UNIT_ID: String(unit.id) }, stdio: 'inherit',
   })
   const state = JSON.parse(readFileSync(new URL('../../.codex-batch-lookup-fixture.json', import.meta.url), 'utf8'))
   writeFileSync(new URL('../../.codex-batch-events-fixture.json', import.meta.url), JSON.stringify(state, null, 2))
 } else {
   const state = JSON.parse(readFileSync(new URL('../../.codex-batch-events-fixture.json', import.meta.url), 'utf8'))
   const unit = await api('GET', `/function-units/${state.fixtureUnitId}`)
   assert.ok(unit.tags.includes('batch-verification-synthetic') && unit.name.startsWith('Batch verification Lookup Events '))
   const forms = await api('GET', `/function-units/${state.fixtureUnitId}/forms`)
   const source = await api('GET', '/function-units/48/forms')
   const host = source.find(form => form.formName === 'Transaction Request Form - Transaction Assignment')
   const fields = []
   const visit = value => { if (Array.isArray(value)) value.forEach(visit); else if (value && typeof value === 'object') { if (value.type === 'lookup') fields.push({ field: value.field, config: value.props?.lookupConfig }); Object.values(value).forEach(visit) } }
   visit(host.configJson.subForms)
   if (mode === 'verify-ui') {
     await verifyUi(page, state, forms, api)
   } else if (mode === 'inspect-content') {
     await loginViaPortalPassword(page)
     const response = await page.request.get(`${origin}/api/portal/processes/function-units/${state.catalogId}/content?forStart=true`)
     const body = await response.json()
     console.log(JSON.stringify({ status: response.status(), success: body.success, error: body.error || body.message }))
   } else if (mode === 'configure-before' || mode === 'configure') {
     const form = forms.find(item => item.formType === 'TASK')
     const binding = form.tableBindings.find(item => item.bindingType === 'SUB')
     const lookup = JSON.parse(fields.find(item => item.field === 'assignee_id').config)
     lookup.displayFields = ['username', 'email']
     form.configJson.rule[0].props.allowBatchEdit = true
     form.configJson.batchForms[binding.id] = { schemaVersion: 1, rule: [
       { type: 'miAssignment', children: [{ type: 'lookup', field: 'assignee_id', title: 'Synthetic user', props: { lookupConfig: JSON.stringify(lookup) } }] },
       { type: 'input', field: 'merchant_name', title: 'Email copy' },
     ], options: { onChange: '$FNX:if (field === "assignee_id" && value && typeof value === "object") { api.setValue("merchant_name", value.email); }' } }
     form.fieldPermissions = { ...form.fieldPermissions, [`${binding.id}:merchant_name`]: 'EDITABLE' }
     const response = await page.request.put(`${origin}/api/v1/function-units/${unit.id}/forms/${form.id}`, { data: form })
     const body = await response.json()
     if (mode === 'configure-before') {
       assert.ok(body.success)
       await page.goto(`${origin}/dev/function-units/${unit.id}`)
       await page.getByRole('tab', { name: /Form/ }).click()
       await page.getByRole('row').filter({ hasText: form.formName }).getByRole('button', { name: 'Edit', exact: true }).click()
       let saves = 0
       page.on('request', request => { if (request.method() === 'PUT' && request.url().endsWith(`/forms/${form.id}`)) saves++ })
       await page.locator('.form-editor-view .header-actions').getByRole('button', { name: 'Save', exact: true }).click()
       const error = page.locator('.el-message--error').last()
       await error.waitFor()
       assert.match(await error.innerText(), /unsupported Batch field or event onChange/)
       assert.equal(saves, 0)
       const folder = new URL('../developer-workstation/verification-screenshots/', import.meta.url)
       mkdirSync(folder, { recursive: true })
       await page.screenshot({ path: new URL('2026-11-03-batch-events-save-before.png', folder).pathname.replace(/^\/(.:)/, '$1'), fullPage: true, mask: [page.locator('.header-right')] })
       console.log(JSON.stringify({ step: 'before', saveRejected: true, requestsSent: saves, error: await error.innerText() }))
     } else {
       assert.ok(response.ok() && body.success, body.error?.message || body.message)
       const deployment = await api('POST', `/function-units/${unit.id}/deploy`, { environment: 'DEVELOPMENT', autoEnable: true,
         conflictStrategy: 'FAIL', changeLog: 'MCY Batch Assign email event verification' })
       await waitForFixtureDeployment(api, deployment.deploymentId)
       await loginViaPortalPassword(page)
       const user = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')))
       await loginViaAdminPassword(page)
       const admin = async (method, path, data) => {
         const response = await page.request.fetch(`${origin}/api/v1/admin${path}`, { method, data })
         assert.ok(response.ok()); return response.json()
       }
       const catalog = await admin('GET', `/function-units/by-process-key/${encodeURIComponent(state.processKey)}`)
       const access = await admin('GET', `/function-units/${catalog.id}/access`)
       if (!access.some(item => item.roleId === user.activeRoleId)) await admin('POST', `/function-units/${catalog.id}/access`, { roleId: user.activeRoleId })
       state.catalogId = catalog.id
       await loginViaPortalPassword(page)
       const portal = async (method, path, data) => {
         const response = await page.request.fetch(`${origin}/api/portal${path}`, { method, data })
         const body = await response.json(); assert.ok(response.ok() && body.success !== false, body.error?.message || body.message)
         return body.data
       }
       const old = await portal('GET', `/tasks/${state.taskId}/form-data`)
       const tables = await (async () => { await loginViaDwPassword(page); return api('GET', `/function-units/${unit.id}/tables`) })()
       await loginViaPortalPassword(page)
       const primary = tables.find(table => table.tableType === 'MAIN').fieldDefinitions.find(field => field.isPrimaryKey).fieldName
       const rows = Array.from({ length: 3 }, (_, index) => ({ ...old.fieldValues.__subTables__[state.store][0],
         assignee_id: user.userId, row_id: `SYNTHETIC-EVENT-${index + 1}`, case_id: 'SYNTHETIC-EVENT-CASE', merchant_name: `before-${index + 1}` }))
       rows.forEach(row => { delete row._wsRowVersion; delete row.platformRowUuid; for (const field of state.lookupFields) row[field] = null })
       const started = await portal('POST', `/processes/${encodeURIComponent(state.processKey)}/start`, {
         batchStartContext: await readBatchStartContext(page, state.catalogId),
         formData: { [primary]: 'SYNTHETIC-EVENT-CASE', legal_hold: false, __subTables__: { [state.store]: rows } }, remark: 'Synthetic Batch event verification',
       })
       const processId = started.processInstanceId || started.id
       const taskResponse = await page.request.get(`http://localhost:8081/api/v1/tasks?processInstanceId=${processId}&page=0&size=100`)
       const tasks = (await taskResponse.json()).data.tasks
       assert.equal(tasks.length, 1)
       assert.equal(tasks[0].taskDefinitionKey, 'Review')
       Object.assign(state, { taskId: tasks[0].id ?? tasks[0].taskId, processId })
       writeFileSync(new URL('../../.codex-batch-events-fixture.json', import.meta.url), JSON.stringify(state, null, 2))
       console.log(JSON.stringify({ step: 'configured', unitId: unit.id, taskId: state.taskId, formId: form.id }))
     }
   } else {
     console.log(JSON.stringify({ step: 'inspect', unitId: unit.id, forms: forms.map(form => ({ id: form.id, name: form.formName, type: form.formType })), lookups: fields }))
   }
 }
} finally { await browser.close() }

async function verifyUi(page, state, forms, api) {
 const pageErrors = []
 page.on('pageerror', error => { pageErrors.push(error.message); console.log(JSON.stringify({ step: 'pageerror', message: error.message })) })
 const folder = app => new URL(`../${app}/verification-screenshots/`, import.meta.url)
 const screenshot = async (app, name, locator = page) => {
   mkdirSync(folder(app), { recursive: true })
   const path = new URL(`2026-11-03-batch-events-${name}.png`, folder(app)).pathname.replace(/^\/(.:)/, '$1')
   await locator.screenshot({ path, mask: [page.locator('.batch-edit-dialog .lookup-field'), page.locator('.batch-edit-dialog input'), page.locator('.sub-table-field .assignee-cell'), page.locator('.header-right'), page.locator('.user-info'), page.locator('.info-section .el-descriptions__content')] })
   console.log(JSON.stringify({ step: 'screenshot', path }))
 }
 const form = forms.find(item => item.formType === 'TASK')
 await page.goto(`${origin}/dev/function-units/${state.fixtureUnitId}`)
 await page.getByRole('tab', { name: /Form/ }).click()
 await page.getByRole('row').filter({ hasText: form.formName }).getByRole('button', { name: 'Edit', exact: true }).click()
 await page.locator('.designer-grouped-nav .el-dropdown').first().locator('.designer-nav-btn').click()
 await page.locator('.el-dropdown-menu:visible .el-dropdown-menu__item').first().click()
 await page.getByRole('button', { name: 'Batch Form', exact: true }).click()
 await page.getByText('Email copy', { exact: true }).first().waitFor()
 const save = page.waitForResponse(response => response.url().endsWith(`/forms/${form.id}`) && response.request().method() === 'PUT')
 await page.locator('.form-editor-view .header-actions').getByRole('button', { name: 'Save', exact: true }).click()
 const saved = await (await save).json()
 assert.equal(saved.success, true, JSON.stringify(saved.error))
 const reloaded = await api('GET', `/function-units/${state.fixtureUnitId}/forms/${form.id}`)
 assert.ok(Object.values(reloaded.configJson.batchForms)[0].options.onChange.includes('merchant_name'))
 await screenshot('developer-workstation', 'save-after')
 console.log(JSON.stringify({ step: 'designer-save', success: true, eventPersisted: true }))
 await loginViaPortalPassword(page)
 const portal = async path => {
   const response = await page.request.get(`${origin}/api/portal${path}`)
   const body = await response.json(); assert.ok(response.ok() && body.success, body.error?.message || body.message); return body.data
 }
 const getRows = async () => (await portal(`/tasks/${state.taskId}/form-data`)).fieldValues.__subTables__[state.store]
 const liveForm = await portal(`/tasks/${state.taskId}/form-data`)
 const liveControl = Object.values(liveForm.subTableBatchCapabilities)[0]
 console.log(JSON.stringify({ step: 'runtime-event-config', hasFormChange: typeof liveControl.batchForm.options?.onChange === 'string', writeMode: liveControl.rowDesign?.fieldWriteModes?.merchant_name }))
 let baseline = await getRows()
 assert.equal(baseline.length, 3)
 await page.goto(`${origin}/portal/tasks/${state.taskId}`)
 const table = page.locator('.sub-table-field').first()
 await table.locator('.el-table__body-wrapper tbody tr').nth(2).waitFor()
 await page.waitForLoadState('networkidle')
 await page.waitForTimeout(1500)
 baseline = await getRows()
 for (const index of [0,1]) await table.locator('.el-table__body-wrapper tbody tr').nth(index).locator('.el-checkbox').first().click()
 const dialog = page.locator('.batch-edit-dialog:visible')
 const open = async () => { await table.getByRole('button', { name: 'Batch Edit', exact: true }).click(); await dialog.waitFor() }
 const selectUser = async () => {
   await dialog.locator('.batch-assignment-editor [role="radio"]').filter({ hasText: 'Assign by person' }).click()
   const picker = dialog.locator('.batch-assignment-editor .lookup-field').first()
   await picker.locator('input').fill('admin')
   const row = page.locator('.lookup-dropdown:visible .el-table__body-wrapper tbody tr').filter({ hasText: /admin/ }).first()
   await row.waitFor()
   const email = (await row.locator('td').nth(1).innerText()).trim()
   assert.match(email, /@/)
   await row.click()
   const copy = dialog.locator('.el-form-item').filter({ hasText: 'Email copy' }).locator('input')
   try {
     await page.waitForFunction(({ email }) => [...document.querySelectorAll('.batch-edit-dialog input')].some(input => input.value === email), { email }, { timeout: 5000 })
   } catch (error) {
     const diagnosis = await page.evaluate(email => ({
       alerts: [...document.querySelectorAll('.batch-edit-dialog .el-alert__title')].map(el => el.textContent),
       states: [...document.querySelectorAll('.batch-edit-dialog,.batch-edit-dialog .batch-designed-form,.batch-edit-dialog .batch-assignment-editor')].map(el => ({ className: el.className, ...el.dataset })),
       radioStates: [...document.querySelectorAll('.batch-edit-dialog [role="radio"]')].map(el => ({ checked: el.getAttribute('aria-checked'), text: el.innerText })),
       fieldMatches: [...document.querySelectorAll('.batch-edit-dialog input')].map(el => ({ disabled: el.disabled, matches: el.value === email })),
     }), email)
     console.log(JSON.stringify({ step: 'diagnosis', diagnosis }))
     throw error
   }
   assert.equal(await copy.inputValue(), email)
   return email
 }
 await open()
 const email = await selectUser()
 assert.deepEqual(await getRows(), baseline, 'Draft must not persist before Apply')
 await screenshot('user-portal', 'email-draft', dialog)
 await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
 await page.locator('.el-message-box').getByRole('button', { name: /OK|Confirm/ }).click()
 await dialog.waitFor({ state: 'hidden' })
 assert.deepEqual(await getRows(), baseline, 'Cancel must discard the entire draft')
 await open(); assert.equal(await selectUser(), email)
 const savedRequest = page.waitForResponse(response => response.url().endsWith(`/tasks/${state.taskId}/submit`) && response.request().method() === 'POST')
 await dialog.getByRole('button', { name: /Apply/ }).click()
 const response = await savedRequest
 const body = await response.json(); assert.ok(response.ok() && body.success, body.error?.message || body.message)
 const payload = response.request().postDataJSON()
 assert.deepEqual(payload.subTableBatchChanges.map(change => change.operation).sort(), ['ASSIGN', 'EDIT'])
 const after = await getRows()
 assert.equal(after[0].merchant_name, email); assert.equal(after[1].merchant_name, email)
 const withoutVersion = row => Object.fromEntries(Object.entries(row).filter(([key]) => key !== '_wsRowVersion'))
 assert.deepEqual(withoutVersion(after[2]), withoutVersion(baseline[2]))
 assert.equal(after[2]._wsRowVersion, baseline[2]._wsRowVersion + 1, 'The existing save scope versions all claimed rows')
 assert.match(state.processId, /^[a-f0-9-]{36}$/)
 const targetId = payload.subTableBatchChanges.find(change => change.operation === 'ASSIGN').rowPatches[0].assignment.assigneeId
 const personId = value => typeof value === 'object' && value !== null ? value.id : value
 assert.equal(personId(after[0].assignee_id), targetId); assert.equal(personId(after[1].assignee_id), targetId)
 const persisted = JSON.parse(execFileSync('docker', ['exec', '-i', 'platform-postgres-dev', 'sh', '-c', 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'], {
   input: `select variables::text from up_process_instance where id='${state.processId}';`, encoding: 'utf8',
 }).trim())
 assert.equal(persisted.__subTables__[state.store][0].merchant_name, email)
 assert.equal(persisted.__subTables__[state.store][1].merchant_name, email)
 assert.equal(personId(persisted.__subTables__[state.store][0].assignee_id), targetId)
 assert.equal(personId(persisted.__subTables__[state.store][1].assignee_id), targetId)
 assert.deepEqual(persisted.__subTables__[state.store][2].merchant_name, baseline[2].merchant_name)
 await page.reload(); await table.waitFor()
 await screenshot('user-portal', 'saved-after', table)
 await page.goto(`${origin}/help/form-events#batch`)
 await page.getByText('Batch Assign: fill another field', { exact: true }).waitFor()
 await screenshot('help', 'help')
 assert.deepEqual(pageErrors, [])
 console.log(JSON.stringify({ step: 'portal-verified', selectedRowsBackfilled: 2, unselectedBusinessRowUnchanged: true, existingScopeVersionIncrement: 1, cancelPreservedDatabase: true, databaseConfirmed: true, pageErrors: 0 }))
}
