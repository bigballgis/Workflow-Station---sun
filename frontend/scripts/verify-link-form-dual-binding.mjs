/** Isolated synthetic FU only. Source FU is read; no business design is changed. */
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword, loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'
import { unwrapApiList } from './mi-regression-helpers.mjs'

const sourceId = process.env.LINK_FIXTURE_SOURCE_ID
assert.match(sourceId ?? '', /^\d+$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 1000 } })
  const errors = []
  let dwUserId
  page.on('pageerror', e => errors.push(e.message))
  const origin = 'http://localhost:3000'
  const api = async (prefix, method, path, data) => {
    const res = await page.request.fetch(`${origin}${prefix}${path}`, { method, data,
      ...(prefix === '/api/v1' && dwUserId ? { headers: { 'X-User-Id': dwUserId, 'Accept-Language': 'en' } } : {}) })
    const json = await res.json()
    assert.ok(res.ok() && json.success !== false, `${method} ${path}: ${res.status()} ${json.error?.message ?? ''}`)
    return json.data ?? json
  }
  const dw = (method, path, data) => api('/api/v1', method, path, data)
  dwUserId = (await loginViaDwPassword(page)).userId
  const sourceProcess = await dw('GET', `/function-units/${sourceId}/process`)
  assert.doesNotMatch(sourceProcess.bpmnXml, /<(?:\w+:)?(?:serviceTask|callActivity|sendTask)\b/)
  const resumeId = process.env.LINK_FIXTURE_UNIT_ID
  if (resumeId) assert.match(resumeId, /^\d+$/)
  const teams = await dw('GET', '/function-units/my-dev-groups')
  const team = teams.groups.find(group => group.selectable)
  assert.ok(team)
  const clone = resumeId ? await dw('GET', `/function-units/${resumeId}`)
    : await dw('POST', '/function-units', { name: `Link Form verification ${Date.now()}`, tags: ['link-form-verification-synthetic'], virtualGroupIds: [team.id] })
  assert.notEqual(String(clone.id), sourceId)
  assert.match(clone.name, /^Link Form verification /)
  const base = `/function-units/${clone.id}`
  console.log(JSON.stringify({ step: 'test-unit', unitId: clone.id }))
  let tables = await dw('GET', `${base}/tables`)
  if (!tables.length) {
    const suffix = Date.now().toString(36)
    const mainTable = await dw('POST', `${base}/tables`, { tableName: `lf_case_${suffix}`, tableType: 'MAIN', tableDisplayName: 'Link case', fields: [
      { fieldName: 'case_key', displayName: 'Case key', dataType: 'VARCHAR', length: 64, nullable: false, isPrimaryKey: true, pkGeneration: { strategy: 'manual' } },
      { fieldName: 'title', displayName: 'Title', dataType: 'VARCHAR', length: 100, nullable: true },
    ] })
    await dw('POST', `${base}/tables`, { tableName: `lf_lines_${suffix}`, tableType: 'SUB', tableDisplayName: 'Same lines', fields: [
      { fieldName: 'line_key', displayName: 'Line key', dataType: 'VARCHAR', length: 64, nullable: false, isPrimaryKey: true, pkGeneration: { strategy: 'manual' } },
      ...['case_ref', 'case_ref_2'].map(fieldName => ({ fieldName, displayName: fieldName, dataType: 'VARCHAR', length: 64, nullable: true, isForeignKey: true, refTableId: mainTable.id, refPrimaryKeyFields: ['case_key'] })),
      { fieldName: 'caption', displayName: 'Caption', dataType: 'VARCHAR', length: 100, nullable: true },
      { fieldName: 'amount', displayName: 'Amount', dataType: 'INTEGER', nullable: true },
    ] })
    tables = await dw('GET', `${base}/tables`)
    for (const formType of ['PROCESS', 'TASK']) await dw('POST', `${base}/forms`, { formName: `Synthetic ${formType}`, formType, scene: 'TASK', configJson: { rule: [], options: {} } })
  }
  const child = tables.find(t => t.tableType === 'SUB' && t.fieldDefinitions.some(f => f.fieldName === 'case_ref_2'))
  const main = tables.find(t => t.tableType === 'MAIN')
  assert.ok(child && main)
  const forms = await dw('GET', `${base}/forms`)
  const processForm = forms.find(f => f.formType === 'PROCESS')
  const task = forms.find(f => f.formType === 'TASK')
  assert.ok(processForm && task)
  let taskBindings, startBindings
  for (const form of [processForm, task]) {
    let binds = await dw('GET', `${base}/forms/${form.id}/bindings`)
    const primary = binds.find(b => b.bindingType === 'PRIMARY') || await dw('POST', `${base}/forms/${form.id}/bindings`, { tableId: main.id, bindingType: 'PRIMARY', bindingMode: 'EDITABLE', sortOrder: 0 })
    const a = binds.find(b => b.tableId === child.id && b.filterFkFieldId === child.fieldDefinitions.find(f => f.fieldName === 'case_ref').id)
      || await dw('POST', `${base}/forms/${form.id}/bindings`, { tableId: child.id, bindingType: 'SUB', bindingMode: 'EDITABLE', subMode: 'FULL', bindingLinkMode: 'structuralFk', foreignKeyField: 'case_ref', filterFkFieldId: child.fieldDefinitions.find(f => f.fieldName === 'case_ref').id, sortOrder: 1 })
    assert.ok(primary && a)
    let b = binds.find(b => b.tableId === child.id && b.filterFkFieldId === child.fieldDefinitions.find(f => f.fieldName === 'case_ref_2').id)
    if (!b) b = await dw('POST', `${base}/forms/${form.id}/bindings`, {
      tableId: child.id, bindingType: 'SUB', bindingMode: 'EDITABLE', subMode: 'FULL', bindingLinkMode: 'structuralFk',
      foreignKeyField: 'case_ref_2', filterFkFieldId: child.fieldDefinitions.find(f => f.fieldName === 'case_ref_2').id, sortOrder: 2,
    })
    const rowRule = [
      { type: 'input', field: 'line_key', title: 'Line key', props: { disabled: true } },
      { type: 'input', field: 'caption', title: 'Caption' }, { type: 'inputNumber', field: 'amount', title: 'Amount' },
    ]
    const configJson = { rule: [
      { type: 'input', field: 'case_key', title: 'Case key', props: { _bindingId: primary.id } },
      ...[a, b].map((binding, index) => ({ type: 'subTable', field: `lines_${binding.id}`, title: index ? 'Binding B' : 'Binding A',
        props: { _bindingId: binding.id, allowAdd: true, allowEdit: true, allowDelete: true, showLinkFormDialogFooter: true } })),
    ], options: { form: { labelPosition: 'top' } }, subForms: {
      [a.id]: { rule: rowRule, options: {} }, [b.id]: { rule: rowRule, options: {} },
    } }
    const columnsFor = binding => {
      const field = (fieldName, displayLabel) => ({ fieldName, displayName: displayLabel, displayLabel, columnType: 'field', visible: true })
      const link = (name, target) => ({ fieldName: `linkForm:${-target.id}`, dataType: 'LINK_FORM', nullable: true, isPrimaryKey: false,
        displayName: name, columnType: 'linkForm', componentId: -target.id, boundSubTableBindingId: target.id,
        boundSubTableName: child.tableDisplayName, columnLabel: name, linkText: name })
      return [field('line_key', 'Line key'), field('caption', 'Caption'), field('amount', 'Amount'), link('Self details', binding), link('Other details', binding.id === a.id ? b : a)]
    }
    configJson.subListViews = Object.fromEntries([a, b].map(binding => [binding.id, { columns: columnsFor(binding) }]))
    await dw('PUT', `${base}/forms/${form.id}`, { formName: form.formName, formType: form.formType, scene: 'TASK', configJson,
      fieldPermissions: { case_key: 'EDITABLE', ...Object.fromEntries([a, b].flatMap(binding => ['line_key', 'caption', 'amount'].map(field => [`${binding.id}:${field}`, 'EDITABLE']))) } })
    for (const binding of [a, b]) {
      await dw('PUT', `/api/forms/${form.id}/sub-table-views/${binding.id}`, columnsFor(binding)
        .filter(item => item.columnType === 'field').map((item, sortOrder) => ({ ...item, sortOrder })))
    }
    if (form.id === task.id) taskBindings = { a: a.id, b: b.id }
    else startBindings = { a: a.id, b: b.id }
  }
  const prop = (name, value) => `<custom:property name="${name}" value="${value}"/>`
  await dw('POST', `${base}/process`, { bpmnXml: `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" xmlns:custom="http://workflow.platform/schema/custom" targetNamespace="LinkVerification">
<bpmn:process id="${clone.code}" name="Link Form verification" isExecutable="true"><bpmn:startEvent id="Start"><bpmn:extensionElements><custom:properties>${prop('formId', processForm.id)}</custom:properties></bpmn:extensionElements></bpmn:startEvent><bpmn:sequenceFlow id="f1" sourceRef="Start" targetRef="Intake"/>
<bpmn:userTask id="Intake" name="Synthetic intake" flowable:assignee="\${initiator}"><bpmn:extensionElements><custom:properties>${prop('formId', processForm.id)}${prop('assigneeType', 'INITIATOR')}</custom:properties></bpmn:extensionElements></bpmn:userTask><bpmn:sequenceFlow id="f0" sourceRef="Intake" targetRef="Work"/>
<bpmn:userTask id="Work" name="Verify links" flowable:assignee="\${initiator}"><bpmn:extensionElements><custom:properties>${prop('formId', task.id)}${prop('assigneeType', 'INITIATOR')}</custom:properties></bpmn:extensionElements></bpmn:userTask><bpmn:sequenceFlow id="f2" sourceRef="Work" targetRef="End"/><bpmn:endEvent id="End"/></bpmn:process></bpmn:definitions>` })
  const deployment = await dw('POST', `${base}/deploy`, { environment: 'DEVELOPMENT', autoEnable: true, conflictStrategy: 'FAIL', changeLog: 'Isolated Link Form dual-key verification' })
  await waitForFixtureDeployment(dw, deployment.deploymentId)
  await loginViaPortalPassword(page, { user: process.env.LINK_FIXTURE_PORTAL_USER })
  const roleId = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')).activeRoleId)
  assert.ok(process.env.ADMIN_LOGIN_USER, 'Explicit test Admin login required')
  await loginViaAdminPassword(page, { user: process.env.ADMIN_LOGIN_USER })
  const catalog = await api('/api/v1/admin', 'GET', `/function-units/by-process-key/${clone.code}`)
  assert.equal(catalog.name, clone.name)
  const accessPath = `/function-units/${catalog.id}/access`
  const access = await api('/api/v1/admin', 'GET', accessPath)
  if (!access.some(item => item.roleId === roleId)) await api('/api/v1/admin', 'POST', accessPath, { roleId })
  await loginViaPortalPassword(page, { user: process.env.LINK_FIXTURE_PORTAL_USER })
  const portal = (method, path, data) => api('/api/portal', method, path, data)
  const key = `LF-${randomUUID().slice(0, 8)}`
  const storeKey = `dw:${child.tableName}`
  const rowA = { line_key: `A-${key}`, caption: 'A ONLY', amount: 1, case_ref: key, case_ref_2: null }
  const rowB = { line_key: `B-${key}`, caption: 'B ONLY', amount: 2, case_ref: null, case_ref_2: key }
  const start = async (caseKey, rows) => portal('POST', `/processes/${clone.code}/start`, {
    formData: { case_key: caseKey, title: 'Synthetic Link Form test', __subTables__: { [storeKey]: rows } },
    subTableBindingScopes: [['a', 'case_ref'], ['b', 'case_ref_2']].map(([side, fk]) => ({
      bindingId: String(startBindings[side]), storeKey, rowKeys: rows.filter(row => row[fk] === caseKey).map(row => ({ line_key: row.line_key })), emptied: false,
    })),
  })
  await start(`${key}-OTHER`, [])
  // The existing write guard correctly rejects a newly inserted row carrying both
  // bindings' FKs without explicit fill permission. Keep this reproduction to two
  // independently owned rows; do not weaken that guard to manufacture a fixture.
  const started = await start(key, [rowA, rowB])
  const instanceId = started.processInstanceId ?? started.id
  const todo = unwrapApiList(await portal('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] })).find(item => item.processInstanceId === instanceId)
  assert.ok(todo)
  const taskId = todo.taskId
  console.log(JSON.stringify({ step: 'fixture', unitId: clone.id, processKey: clone.code, instanceId, taskId, table: child.tableName, bindings: taskBindings, key }))
  if (process.env.LINK_FIXTURE_SETUP_ONLY !== '1') {
  await page.goto(`${origin}/portal/tasks/${taskId}`)
  const aGrid = page.locator('.sub-table-field').filter({ has: page.locator('.sub-table-header > .title').filter({ hasText: /^Binding A/ }) }).first()
  const bGrid = page.locator('.sub-table-field').filter({ has: page.locator('.sub-table-header > .title').filter({ hasText: /^Binding B/ }) }).first()
  await aGrid.locator('.el-table__body-wrapper tbody tr').first().waitFor()
  const grids = [await aGrid.innerText(), await bGrid.innerText()]
  console.log(JSON.stringify({ step: 'grids', grids }))
  const clicked = aGrid.locator('.el-table__body-wrapper tbody tr').filter({ hasText: 'A ONLY' }).first()
  await clicked.getByText('Other details', { exact: true }).click()
  const modal = page.locator('.link-form-modal-panel:visible')
  await page.getByText('No unique matching record was found in the selected binding. No changes have been applied.', { exact: true }).waitFor()
  assert.equal(await modal.count(), 0, 'A-only is not a record in the B binding')
  const screenshot = fileURLToPath(new URL('../user-portal/verification-screenshots/2026-10-20_link-form-dual-key-cross-fixed.png', import.meta.url))
  await page.screenshot({ path: screenshot, fullPage: true })
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ step: 'cross-open', result: 'PASS', screenshot, errors }))
  }
} finally { await browser.close() }
