/** Local synthetic structural copy: source metadata is read only; all writes address tagged fixtures. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'
import { readBatchStartContext } from './batch-start-context.mjs'

const mode = process.argv[2]
assert.ok(['prepare', 'before', 'after', 'complete'].includes(mode))
const statePath = new URL('../../.codex-batch-lookup-fixture.json', import.meta.url)
const origin = 'http://localhost:3000'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
let pageErrors = 0
page.on('pageerror', () => pageErrors++)
async function api(method, path, data) {
  const response = await page.request.fetch(`${origin}${path}`, { method, data })
  const body = await response.json()
  assert.ok(response.ok() && body.success !== false, `${method} ${path}: ${body.error?.message || body.message || response.status()}`)
  return body.data ?? body
}
const dw = (method, path, data) => api(method, `/api/v1${path}`, data)
const portal = (method, path, data) => api(method, `/api/portal${path}`, data)
function walk(value, visitor) {
  if (Array.isArray(value)) value.forEach(child => walk(child, visitor))
  else if (value && typeof value === 'object') { visitor(value); Object.values(value).forEach(child => walk(child, visitor)) }
}
function db(processId) {
  assert.match(processId, /^[a-f0-9-]{36}$/)
  return JSON.parse(execFileSync('docker', ['exec', '-i', 'platform-postgres-dev', 'sh', '-c',
    'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'], {
    input: `select variables::text from up_process_instance where id='${processId}';`, encoding: 'utf8',
  }).trim())
}
async function prepare() {
  const context = await readSource()
  Object.assign(context, await createFixtureUnit(context.source))
  Object.assign(context, await copyTables(context))
  Object.assign(context, await createForms(context))
  Object.assign(context, syntheticSubForm(context))
  await configureForms(context)
  const deployed = await deployFixture(context)
  await startFixture(context, deployed)
}
async function readSource() {
  const sourceId = process.env.LOOKUP_SOURCE_FU_ID
  assert.match(sourceId || '', /^\d+$/)
  await loginViaDwPassword(page)
  const source = await dw('GET', `/function-units/${sourceId}`)
  assert.equal(source.name, process.env.LOOKUP_SOURCE_FU_NAME)
  const sourceForms = await dw('GET', `/function-units/${sourceId}/forms`)
  const sourceTables = await dw('GET', `/function-units/${sourceId}/tables`)
  const sourceHost = sourceForms.find(form => form.formName === 'Transaction Request Form - Transaction Assignment')
  assert.ok(sourceHost)
  const sourceBinding = sourceHost.tableBindings.find(item => item.bindingType === 'SUB' && item.bindingMode === 'EDITABLE')
  const sourceTable = sourceTables.find(item => item.id === sourceBinding.tableId)
  const sourcePrimary = sourceTables.find(item => item.tableType === 'MAIN')
  return { sourceId, source, sourceHost, sourceBinding, sourceTable, sourcePrimary }
}
async function createFixtureUnit(source) {
  const groups = await dw('GET', '/function-units/my-dev-groups')
  const group = groups.groups.find(item => item.selectable)
  assert.ok(group)
  const clone = process.env.LOOKUP_FIXTURE_UNIT_ID ? await dw('GET', `/function-units/${process.env.LOOKUP_FIXTURE_UNIT_ID}`)
    : await dw('POST', '/function-units', { name: `Batch verification Lookup ${Date.now()}`,
    tags: ['batch-verification-synthetic'], virtualGroupIds: [group.id],
    description: `Synthetic structural copy of ${source.name}; no source design or business values changed.` })
  const base = `/function-units/${clone.id}`
  assert.ok(clone.tags.includes('batch-verification-synthetic') && clone.name.startsWith('Batch verification Lookup '))
  console.log(JSON.stringify({ step: 'fixture-design', unitId: clone.id }))
  return { clone, base }
}
const copyFields = fields => fields.map(field => {
    const result = {}
    for (const name of ['fieldName', 'dataType', 'length', 'nullable', 'isPrimaryKey', 'isForeignKey', 'precision', 'scale', 'displayName', 'pkGeneration']) {
      if (field[name] != null) result[name] = field[name]
    }
    return result
  })

async function copyTables({ base, clone, sourcePrimary, sourceTable }) {
  const existingTables = await dw('GET', `${base}/tables`)
  const primary = existingTables.find(item => item.tableType === 'MAIN') || await dw('POST', `${base}/tables`, { tableName: `${sourcePrimary.tableName}_verify_${clone.id}`, tableType: 'MAIN',
    tableDisplayName: 'Synthetic case', fields: copyFields(sourcePrimary.fieldDefinitions) })
  const fields = copyFields(sourceTable.fieldDefinitions)
  for (const field of fields.filter(item => item.isForeignKey)) {
    field.refTableId = primary.id
    field.refPrimaryKeyFields = primary.fieldDefinitions.filter(item => item.isPrimaryKey).map(item => item.fieldName)
  }
  const table = existingTables.find(item => item.tableType === 'SUB') || await dw('POST', `${base}/tables`, { tableName: `${sourceTable.tableName}_verify_${clone.id}`, tableType: 'SUB',
    tableDisplayName: 'Synthetic transactions', fields })
  return { primary, table }
}
async function bindForm({ base, table, primary, sourceBinding }, form, mode) {
  const ownership = table.fieldDefinitions.filter(item => item.isForeignKey)
  assert.equal(ownership.length, 1)
  const attributes = { tableId: table.id, bindingType: 'SUB', bindingMode: 'EDITABLE',
    bindingLinkMode: sourceBinding.bindingLinkMode, foreignKeyField: ownership[0].fieldName, subMode: 'FULL', sortOrder: 1 }
  const existing = await dw('GET', `${base}/forms/${form.id}/bindings`)
  if (!existing.some(item => item.bindingType === 'PRIMARY')) await dw('POST', `${base}/forms/${form.id}/bindings`, { tableId: primary.id, bindingType: 'PRIMARY', bindingMode: mode, sortOrder: 0 })
  if (existing.some(item => item.bindingType === 'SUB')) {
    const current = existing.find(item => item.bindingType === 'SUB')
    await dw('PUT', `${base}/forms/${form.id}/bindings/${current.id}`, attributes)
    return current
  }
  return dw('POST', `${base}/forms/${form.id}/bindings`, attributes)
}
async function createForms(context) {
  const { base } = context
  const existingForms = await dw('GET', `${base}/forms`)
  const makeForm = async (name, type) => existingForms.find(item => item.formName === name)
    || dw('POST', `${base}/forms`, { formName: name, formType: type, scene: 'TASK', configJson: { rule: [], options: {} } })
  const startForm = await makeForm('Synthetic intake', 'PROCESS')
  const host = await makeForm('Synthetic Lookup assignment', 'TASK')
  const startBinding = await bindForm(context, startForm, 'EDITABLE')
  const binding = await bindForm(context, host, 'READONLY')
  const hostBindings = await dw('GET', `${base}/forms/${host.id}/bindings`)
  for (const related of hostBindings.filter(item => item.bindingType === 'RELATED')) await dw('DELETE', `${base}/forms/${host.id}/bindings/${related.id}`)
  return { startForm, host, startBinding, binding }
}
function syntheticSubForm({ sourceHost, sourceBinding }) {
  const sourceConfig = typeof sourceHost.configJson === 'string' ? JSON.parse(sourceHost.configJson) : sourceHost.configJson
  const subForm = structuredClone(sourceConfig.subForms[sourceBinding.id])
  const lookupFields = []
  let template
  walk(subForm.rule, rule => { if (rule.type === 'lookup') template = structuredClone(rule.props) })
  assert.ok(template)
  // Source business choices are Select. Only this test copy models the UAT Lookup contract.
  walk(subForm.rule, rule => {
    if (rule.type === 'select') {
      rule.type = 'lookup'; rule.readonly = true; rule.props = { ...structuredClone(template), readonly: true }
      lookupFields.push(rule.field)
    }
    if (rule.type !== 'lookup') return
    if (lookupFields.includes(rule.field)) rule.props = { readonly: true, lookupConfig: JSON.stringify({ selectedDisplayField: 'dropdown_name' }) }
    else { rule.type = 'input'; rule.props = {} }
  })
  assert.equal(lookupFields.length, 5)
  return { sourceConfig, subForm, lookupFields }
}
async function configureForms({ base, binding, sourceBinding, startBinding, subForm, sourceConfig, lookupFields, host, startForm }) {
  const grid = id => ({ type: 'subTable', field: `transactions_${id}`, title: 'Synthetic transactions',
    batchControlId: `lookup-assignment-${id}`, props: { _bindingId: id, allowEdit: true,
      allowAdd: true, allowDelete: false, allowBatchAssign: id === binding.id,
      allowBatchEdit: false, allowBatchAdd: false, allowBatchDelete: false } })
  const config = { rule: [grid(binding.id)], options: sourceConfig.options,
    subForms: { [binding.id]: subForm },
    subListViews: { [binding.id]: sourceConfig.subListViews?.[sourceBinding.id] },
    batchForms: { [binding.id]: { schemaVersion: 1, rule: [{ type: 'miAssignment' }], options: {} } } }
  await dw('PUT', `${base}/forms/${host.id}`, { formName: host.formName, formType: host.formType,
    scene: 'TASK', configJson: config,
    fieldPermissions: Object.fromEntries(lookupFields.map(name => [`${binding.id}:${name}`, 'READONLY'])) })
  await dw('PUT', `${base}/forms/${startForm.id}`, { formName: startForm.formName, formType: 'PROCESS', scene: 'TASK',
    configJson: { rule: [grid(startBinding.id)], options: {}, subForms: { [startBinding.id]: subForm } }, fieldPermissions: {} })
}
function syntheticBpmn({ clone, startForm, host, table }) {
  const property = (name, value) => `<custom:property name="${name}" value="${value}"/>`
  // Test-only process excludes the source FU's service tasks, messages and email actions.
  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" xmlns:custom="http://workflow.platform/schema/custom" targetNamespace="LookupAssignmentVerification">
<bpmn:process id="${clone.code}" isExecutable="true">
<bpmn:startEvent id="Start"><bpmn:extensionElements><custom:properties>${property('formId', startForm.id)}</custom:properties></bpmn:extensionElements></bpmn:startEvent>
<bpmn:sequenceFlow id="ToIntake" sourceRef="Start" targetRef="Intake"/>
<bpmn:userTask id="Intake" name="Synthetic intake" flowable:assignee="\${initiator}"><bpmn:extensionElements><custom:properties>${property('formId', startForm.id)}${property('assigneeType', 'INITIATOR')}</custom:properties></bpmn:extensionElements></bpmn:userTask>
<bpmn:sequenceFlow id="ToReview" sourceRef="Intake" targetRef="Review"/>
<bpmn:userTask id="Review" name="Synthetic Lookup assignment" flowable:assignee="\${initiator}"><bpmn:extensionElements><custom:properties>${property('formId', host.id)}${property('assigneeType', 'INITIATOR')}</custom:properties></bpmn:extensionElements></bpmn:userTask>
<bpmn:sequenceFlow id="ToRows" sourceRef="Review" targetRef="AssignedRows"/>
<bpmn:subProcess id="AssignedRows"><bpmn:multiInstanceLoopCharacteristics flowable:collection="rows" flowable:elementVariable="currentItem"/>
<bpmn:startEvent id="RowStart"/><bpmn:sequenceFlow id="ToWork" sourceRef="RowStart" targetRef="AssignedWork"/>
<bpmn:userTask id="AssignedWork" name="Synthetic assigned work" flowable:assignee="\${currentItem.assignee_id}"><bpmn:extensionElements><custom:properties>${property('assigneeType', 'ELEMENT_VARIABLE')}${property('subTableId', table.id)}${property('assigneeMode', 'user')}${property('subTableName', table.tableName)}${property('assigneeField', 'assignee_id')}${property('allowUser', 'true')}${property('allowRole', 'false')}${property('formId', host.id)}</custom:properties></bpmn:extensionElements></bpmn:userTask>
<bpmn:sequenceFlow id="ToRowEnd" sourceRef="AssignedWork" targetRef="RowEnd"/><bpmn:endEvent id="RowEnd"/></bpmn:subProcess>
<bpmn:sequenceFlow id="ToEnd" sourceRef="AssignedRows" targetRef="End"/><bpmn:endEvent id="End"/>
</bpmn:process></bpmn:definitions>`
  return xml
}
async function deployFixture(context) {
  const { base, clone } = context
  const xml = syntheticBpmn(context)
  await dw('POST', `${base}/process`, { bpmnXml: xml })
  const validation = await dw('GET', `${base}/validate`)
  assert.equal(validation.valid, true, JSON.stringify(validation))
  const deployment = await dw('POST', `${base}/deploy`, { environment: 'DEVELOPMENT', autoEnable: true,
    conflictStrategy: 'FAIL', changeLog: 'Isolated Lookup assignment verification clone' })
  await waitForFixtureDeployment(dw, deployment.deploymentId)
  await loginViaPortalPassword(page)
  const user = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')))
  await loginViaAdminPassword(page)
  const catalog = await api('GET', `/api/v1/admin/function-units/by-process-key/${encodeURIComponent(clone.code)}`)
  assert.equal(catalog.code, clone.code)
  const accessUrl = `/api/v1/admin/function-units/${catalog.id}/access`
  const access = await api('GET', accessUrl)
  if (!access.some(item => item.roleId === user.activeRoleId)) await api('POST', accessUrl, { roleId: user.activeRoleId })
  await loginViaPortalPassword(page)
  return { catalog, user }
}
async function startFixture(context, { catalog, user }) {
  const { sourceId, source, clone, table, primary, lookupFields } = context
  const store = `dw:${table.tableName.toLowerCase()}`
  const row = index => Object.fromEntries(table.fieldDefinitions.map(field => [field.fieldName,
    field.isPrimaryKey ? `SYNTHETIC-${index}` : field.isForeignKey ? 'SYNTHETIC-LOOKUP-CASE'
      : lookupFields.includes(field.fieldName) ? { row_id: `synthetic-choice-${index}`, dropdown_name: `Synthetic choice ${index}` }
        : field.fieldName === 'assignee_id' ? user.userId : null]))
  const mainPk = primary.fieldDefinitions.find(field => field.isPrimaryKey).fieldName
  const started = await portal('POST', `/processes/${encodeURIComponent(clone.code)}/start`, {
    batchStartContext: await readBatchStartContext(page, catalog.id),
    formData: { [mainPk]: 'SYNTHETIC-LOOKUP-CASE', legal_hold: false, __subTables__: { [store]: [row(1), row(2)] } },
    remark: 'Isolated synthetic Lookup assignment verification',
  })
  const processId = started.processInstanceId || started.id
  const response = await page.request.get(`http://localhost:8081/api/v1/tasks?processInstanceId=${processId}&page=0&size=100`)
  const tasks = (await response.json()).data.tasks
  assert.equal(tasks.length, 1)
  assert.equal(tasks[0].taskDefinitionKey, 'Review')
  const state = { sourceId, sourceName: source.name, fixtureUnitId: clone.id, catalogId: catalog.id,
    processKey: clone.code, processId, taskId: tasks[0].id ?? tasks[0].taskId, store, lookupFields }
  assert.ok(state.taskId)
  writeFileSync(statePath, JSON.stringify(state, null, 2))
  console.log(JSON.stringify({ step: 'prepared', ...state }))
}

async function verify() {
  const state = JSON.parse(readFileSync(statePath, 'utf8'))
  await loginViaPortalPassword(page)
  const task = await portal('GET', `/tasks/${state.taskId}`)
  assert.match(task.functionUnitName, /^Batch verification Lookup /)
  assert.equal(task.processInstanceId, state.processId)
  const form = await portal('GET', `/tasks/${state.taskId}/form-data`)
  const control = Object.values(form.subTableBatchCapabilities).find(item => item.capabilities.allowBatchAssign)
  assert.ok(control)
  if (mode === 'complete') { await completePending(state, control, form); return }
  const data = assignmentRequest(state, control, form)
  const before = db(state.processId)
  if (mode === 'after') await verifyRejections(state, data, before, control)
  const response = await submit(state, data)
  const body = await response.json()
  assertSaveResult({ response, body, before, state, control })
  await capturePage(state)
  console.log(JSON.stringify({ step: mode, success: body.success, error: body.error?.message ?? null,
    lookupFieldsPreserved: mode === 'after', otherRowPreserved: true, snapshotVersion: before.__subTableBatchRuntime__?.schemaVersion }))
  if (mode === 'after') {
    const savedForm = await portal('GET', `/tasks/${state.taskId}/form-data`)
    await completePending(state, control, savedForm)
  }
}
async function completePending(state, control, form) {
  const before = db(state.processId)
  const actor = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')).userId)
  const rows = form.fieldValues.__subTables__[state.store]
  assert.notEqual(rows[0][control.assignmentConfig.assigneeField], actor, 'Complete must carry an actual pending assignment')
  const targets = [actor, rows[0][control.assignmentConfig.assigneeField]]
  const key = row => Object.fromEntries(control.rowDesign.binding.primaryKeyFields.map(name => [name, row[name]]))
  const data = { formData: structuredClone(form.fieldValues), taskId: state.taskId, action: 'APPROVE',
    subTableBindingScopes: [{ bindingId: control.bindingId, storeKey: state.store, rowKeys: rows.map(key), emptied: false }],
    subTableBatchChanges: [{ batchControlId: control.batchControlId, bindingId: control.bindingId, operation: 'ASSIGN',
      rowPatches: rows.map((row, i) => ({ rowId: JSON.stringify(key(row)), rowKey: key(row), fields: [control.assignmentConfig.assigneeField],
        assignment: { mode: 'PERSON', assigneeId: targets[i] } })) }] }
  data.formData.__subTables__[state.store].forEach((row, i) => { row[control.assignmentConfig.assigneeField] = targets[i] })
  await portal('POST', `/tasks/${state.taskId}/complete`, data)
  const after = db(state.processId)
  for (let i = 0; i < rows.length; i++) {
    assert.equal(after.__subTables__[state.store][i][control.assignmentConfig.assigneeField], targets[i])
    for (const name of state.lookupFields) assert.deepEqual(after.__subTables__[state.store][i][name], before.__subTables__[state.store][i][name])
  }
  const response = await page.request.get(`http://localhost:8081/api/v1/tasks?processInstanceId=${state.processId}&page=0&size=100`)
  const tasks = (await response.json()).data.tasks
  assert.equal(tasks.length, 2)
  assert.ok(tasks.every(task => task.taskDefinitionKey === 'AssignedWork'))
  assert.deepEqual(tasks.map(task => task.currentAssignee).sort(), [...targets].sort())
  console.log(JSON.stringify({ step: 'complete', pendingAssignmentPersisted: true, lookupObjectsPreserved: true,
    generatedChildren: tasks.length, actualAssigneesMatch: true }))
  if (process.env.LOOKUP_SECOND_LOGIN_USER) await verifySecondActor(state, control, tasks, targets)
}
function assignmentRequest(state, control, form) {
  const rows = form.fieldValues.__subTables__[state.store]
  assert.equal(rows.length, 2)
  const binding = control.rowDesign.binding
  const rowKey = row => Object.fromEntries(binding.primaryKeyFields.map(name => [name, row[name]]))
  const selected = rows[0]
  assert.ok(process.env.LOOKUP_TARGET_USER_ID)
  assert.notEqual(process.env.LOOKUP_TARGET_USER_ID, selected[control.assignmentConfig.assigneeField])
  const data = { formData: structuredClone(form.fieldValues), subTableBindingScopes: [{ bindingId: control.bindingId,
    storeKey: state.store, rowKeys: [rowKey(selected)], emptied: false }], subTableBatchChanges: [{
    operation: 'ASSIGN', batchControlId: control.batchControlId, bindingId: control.bindingId,
    rowPatches: [{ rowId: JSON.stringify(rowKey(selected)), rowKey: rowKey(selected), fields: [control.assignmentConfig.assigneeField],
      assignment: { mode: 'PERSON', assigneeId: process.env.LOOKUP_TARGET_USER_ID } }],
  }] }
  data.formData.__subTables__[state.store][0][control.assignmentConfig.assigneeField] = process.env.LOOKUP_TARGET_USER_ID
  return data
}
const submit = (state, data) => page.request.post(`${origin}/api/portal/tasks/${state.taskId}/submit`, { data })
async function verifyRejections(state, data, before, control) {
  for (const kind of ['lookup-tamper', 'unknown-field', 'no-op-unknown-field', 'malformed-intent', 'invalid-target', 'stale-version']) {
    const candidate = structuredClone(data)
    const row = candidate.formData.__subTables__[state.store][0]
    if (kind === 'lookup-tamper') row[state.lookupFields[0]].dropdown_name = 'tampered'
    if (kind === 'unknown-field') row._arbitrary = 'must-not-write'
    if (kind === 'no-op-unknown-field') {
      row[control.assignmentConfig.assigneeField] = before.__subTables__[state.store][0][control.assignmentConfig.assigneeField]
      candidate.subTableBatchChanges[0].rowPatches[0].assignment.assigneeId = row[control.assignmentConfig.assigneeField]
      row._arbitrary = 'must-not-write'
    }
    // A legal ordinary edit needs no batch declaration; an explicit ASSIGN must include its intent.
    if (kind === 'malformed-intent') candidate.subTableBatchChanges[0].rowPatches[0].assignment = null
    if (kind === 'invalid-target') {
      row[control.assignmentConfig.assigneeField] = 'synthetic-nonexistent-target'
      candidate.subTableBatchChanges[0].rowPatches[0].assignment.assigneeId = 'synthetic-nonexistent-target'
    }
    if (kind === 'stale-version') row._wsRowVersion = -1
    const denied = await submit(state, candidate)
    const body = await denied.json()
    assert.equal(body.success, false, kind)
    assert.deepEqual(db(state.processId), before, `${kind} changed persisted state`)
    const complete = await page.request.post(`${origin}/api/portal/tasks/${state.taskId}/complete`, {
      data: { ...candidate, taskId: state.taskId, action: 'APPROVE' },
    })
    assert.equal((await complete.json()).success, false, `${kind} Complete`)
    assert.deepEqual(db(state.processId), before, `${kind} Complete changed persisted state`)
    console.log(JSON.stringify({ step: kind, saveRejected: true, completeRejected: true, databaseUnchanged: true }))
  }
}
function assertSaveResult({ response, body, before, state, control }) {
  if (mode === 'before') {
    assert.equal(body.success, false)
    assert.match(body.error?.message ?? body.message, /Batch field has invalid text type:/)
    assert.deepEqual(db(state.processId), before)
  } else {
    assert.ok(response.ok() && body.success, body.error?.message ?? body.message)
    const after = db(state.processId)
    const saved = after.__subTables__[state.store]
    assert.equal(saved[0][control.assignmentConfig.assigneeField], process.env.LOOKUP_TARGET_USER_ID)
    for (const name of state.lookupFields) assert.deepEqual(saved[0][name], before.__subTables__[state.store][0][name])
    assert.deepEqual(saved[1], before.__subTables__[state.store][1])
  }
}
async function capturePage(state) {
  await page.goto(`${origin}/portal/tasks/${state.taskId}`, { waitUntil: 'networkidle' })
  await page.locator('.sub-table-field').first().waitFor()
  await page.locator('.sub-table-field').first().scrollIntoViewIfNeeded()
  const folder = new URL('../user-portal/verification-screenshots/', import.meta.url)
  mkdirSync(folder, { recursive: true })
  // The cloned form and business values are synthetic; hide directory names and operator details.
  await page.addStyleTag({ content: '.header-right,.info-section .el-descriptions__content,.assignee-cell { visibility:hidden!important }' })
  await page.screenshot({ path: new URL(`2026-10-22-lookup-assignment-${mode}.png`, folder).pathname.replace(/^\/(.:)/, '$1'), fullPage: true })
  assert.equal(pageErrors, 0)
}
async function verifySecondActor(state, control, tasks, targets) {
  await loginViaPortalPassword(page, { user: process.env.LOOKUP_SECOND_LOGIN_USER, pass: process.env.LOOKUP_SECOND_LOGIN_PASS })
  const child = tasks.find(task => task.currentAssignee === targets[1])
  assert.ok(child)
  const childForm = await portal('GET', `/tasks/${child.taskId}/form-data`)
  const myKey = childForm.fieldValues._currentItem.rowKey
  const scoped = structuredClone(childForm.fieldValues)
  scoped.__subTables__[state.store] = scoped.__subTables__[state.store].filter(row =>
    Object.entries(myKey).every(([name, value]) => row[name] === value))
  assert.equal(scoped.__subTables__[state.store].length, 1)
  const prior = db(state.processId)
  await portal('POST', `/tasks/${child.taskId}/submit`, { formData: scoped,
    subTableBindingScopes: [{ bindingId: control.bindingId, storeKey: state.store, rowKeys: [myKey], emptied: false }] })
  const persisted = db(state.processId)
  assert.deepEqual(persisted.__subTables__[state.store][0], prior.__subTables__[state.store][0])
  for (const name of state.lookupFields) assert.deepEqual(persisted.__subTables__[state.store][1][name], prior.__subTables__[state.store][1][name])
  console.log(JSON.stringify({ step: 'second-actor-own-row-save', otherActorRowUnchanged: true, lookupObjectsPreserved: true }))
}
try { if (mode === 'prepare') await prepare(); else await verify() }
finally { await browser.close() }
