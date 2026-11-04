/** Local synthetic fixture: preserve text identity, validate old numeric computed cells, and persist canonical output. */
import assert from 'node:assert/strict'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'
import { readBatchStartContext } from './batch-start-context.mjs'
import { unwrapApiList } from './mi-regression-helpers.mjs'
const mode = process.argv[2]
assert.ok(['prepare', 'prepare-red', 'red', 'green', 'fresh', 'dw', 'admin', 'enable-complete', 'complete'].includes(mode))
const statePath = new URL('../../.codex-computed-text-state.json', import.meta.url)
const origin = 'http://localhost:3000', text = '00123456789012345678901234'
const browser = await chromium.launch(), page = await browser.newPage({ viewport: { width: 1700, height: 1150 } })
page.setDefaultTimeout(20000)
const errors = []; page.on('pageerror', error => errors.push(error.message))
const request = async (prefix, method, path, data) => {
  const response = await page.request.fetch(origin + prefix + path, { method, data }), body = await response.json()
  assert.ok(response.ok() && body.success !== false, body.error?.message || body.message || String(response.status()))
  return body.data ?? body
}
const dw = (method, path, data) => request('/api/v1', method, path, data)
const portal = (method, path, data) => request('/api/portal', method, path, data)
async function catalogForUser(code) {
  await loginViaPortalPassword(page)
  const roleId = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')).activeRoleId)
  await loginViaAdminPassword(page)
  const catalog = await dw('GET', '/admin/function-units/by-process-key/' + code)
  const accessPath = '/admin/function-units/' + catalog.id + '/access'
  const access = await dw('GET', accessPath)
  if (!access.some(item => item.roleId === roleId)) await dw('POST', accessPath, { roleId })
  return catalog
}
let state
try {
  if (mode.startsWith('prepare')) {
    await loginViaDwPassword(page)
    const source = await dw('GET', '/function-units/50131')
    assert.ok(source.tags.includes('batch-verification-synthetic') && source.name.startsWith('Batch verification '))
    const unit = await dw('POST', '/function-units/50131/clone?newName=' + encodeURIComponent('Batch verification computedtext' + Date.now()))
    assert.notEqual(unit.code, source.code)
    const base = '/function-units/' + unit.id
    const tables = await dw('GET', base + '/tables'), main = tables.find(table => table.tableType === 'MAIN'), child = tables.find(table => table.tableType === 'SUB')
    assert.ok(child.fieldDefinitions.some(field => field.fieldName === 'item_label'))
    const mainKey = main.fieldDefinitions.find(field => field.isPrimaryKey).fieldName
    const childKey = child.fieldDefinitions.find(field => field.isPrimaryKey).fieldName
    const foreignKey = child.fieldDefinitions.find(field => field.isForeignKey && String(field.refTableId) === String(main.id)).fieldName
    const formula = (source, ast, dependsOn) => ({ version: 1, scope: 'row', source, ast, dependsOn, onError: 'fail' })
    const newFields = [
      { fieldName: 'reference_copy', displayName: 'Copied reference', dataType: 'VARCHAR', length: 255, nullable: true, isComputed: true,
        computedField: formula(main.tableName + '.' + mainKey, { type: 'field', table: main.tableName, name: mainKey }, [main.tableName + '.' + mainKey]) },
      { fieldName: 'doubled_units', displayName: 'Doubled units', dataType: 'DECIMAL', precision: 18, scale: 2, nullable: true, isComputed: true,
        computedField: formula('units * 2', { type: 'binary', op: '*', left: { type: 'field', name: 'units' }, right: { type: 'number', text: '2' } }, ['units']) },
    ]
    await dw('PUT', base + '/tables/' + child.id, { tableName: child.tableName, tableType: child.tableType, tableDisplayName: child.tableDisplayName,
      fields: [...child.fieldDefinitions.map(field => ({ ...field, pkGeneration: field.pkGenerationJson, computedField: field.computedFieldJson })), ...newFields] })
    const forms = await dw('GET', base + '/forms')
    for (const form of forms) {
      for (const binding of form.tableBindings.filter(binding => binding.tableId === child.id)) {
        const editable = [{ type: 'input', field: 'item_label', title: 'Caption' }, { type: 'inputNumber', field: 'units', title: 'Units' }]
        form.configJson.batchForms[binding.id] = { schemaVersion: 1, rule: editable, options: {} }
        form.configJson.subForms[binding.id] = { rule: [...editable, ...newFields.map(field => ({ type: 'input', field: field.fieldName, title: field.displayName, props: { disabled: true } }))], options: {} }
        for (const field of editable) form.fieldPermissions[binding.id + ':' + field.field] = 'EDITABLE'
        for (const field of newFields) form.fieldPermissions[binding.id + ':' + field.fieldName] = 'READONLY'
      }
      for (const key of Object.keys(form.configJson.options ?? {})) if (key.startsWith('on') || key.startsWith('before')) delete form.configJson.options[key]
      await dw('PUT', base + '/forms/' + form.id, form)
    }
    const monitors = await dw('GET', base + '/email-monitors')
    assert.ok(monitors.every(item => !item.enabled))
    const process = await dw('GET', base + '/process')
    assert.ok(typeof process.bpmnXml === 'string' && !/<(?:\w+:)?(?:serviceTask|sendTask|scriptTask)\b/.test(process.bpmnXml), 'Fixture must contain no external work')
    const deployment = await dw('POST', base + '/deploy', { environment: 'DEVELOPMENT', autoEnable: true, conflictStrategy: 'FAIL', changeLog: 'Synthetic computed text contract' })
    await waitForFixtureDeployment(dw, deployment.deploymentId)
    const catalog = await catalogForUser(unit.code)
    state = { unitId: unit.id, code: unit.code, catalogId: catalog.id, store: 'dw:' + child.tableName.toLowerCase(), mainKey, childKey, foreignKey }
  } else state = JSON.parse(readFileSync(statePath, 'utf8'))
  if (mode === 'admin') {
    await loginViaAdminPassword(page)
    await page.goto(origin + '/admin/relation-tables/structure/create')
    await page.getByRole('button', { name: 'Add Field', exact: true }).click()
    const rows = page.locator('.el-table__body-wrapper tbody tr.el-table__row')
    const source = rows.last()
    await source.locator('input').nth(0).fill('Synthetic text identifier')
    await source.locator('input').nth(1).fill('text_identifier')
    await page.getByRole('button', { name: 'Add Field', exact: true }).click()
    const target = rows.last()
    await target.locator('input').nth(0).fill('Synthetic copied identifier')
    await target.locator('input').nth(1).fill('copied_identifier')
    await target.locator('.computed-field-editor .el-switch').click()
    const dialog = page.locator('.computed-field-dialog:visible')
    if (!await dialog.count()) await target.locator('.computed-config-btn').click()
    await dialog.locator('.formula-input textarea').fill('text_identifier')
    await dialog.locator('.preview-dep-row input').fill(text)
    await dialog.locator('.preview-actions button').click()
    assert.ok((await dialog.locator('.preview-result').innerText()).includes(text))
    mkdirSync('admin-center/verification-screenshots', { recursive: true })
    await dialog.screenshot({ path: 'admin-center/verification-screenshots/2026-11-04-computed-text-preview.png' })
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ step: 'computed-admin-preview', saved: false, pageErrors: 0 }))
  }
  if (mode === 'dw') {
    await loginViaDwPassword(page)
    const tables = await dw('GET', '/function-units/' + state.unitId + '/tables')
    const child = tables.find(table => 'dw:' + table.tableName.toLowerCase() === state.store)
    assert.ok(child)
    await page.goto(origin + '/dev/function-units/' + state.unitId)
    await page.getByRole('tab', { name: 'Table Design', exact: true }).click()
    await page.getByRole('row').filter({ hasText: child.tableName }).getByRole('button', { name: 'Edit', exact: true }).click()
    const rows = page.locator('.table-fields-grid .el-table__body-wrapper tbody tr.el-table__row')
    await rows.last().waitFor()
    const index = await rows.evaluateAll(items => items.findIndex(row => [...row.querySelectorAll('input')].some(input => input.value === 'reference_copy')))
    assert.ok(index >= 0)
    await rows.nth(index).locator('.computed-config-btn').click()
    const dialog = page.locator('.computed-field-dialog:visible')
    await dialog.locator('.preview-dep-row input').fill(text)
    await dialog.locator('.preview-actions button').click()
    assert.ok((await dialog.locator('.preview-result').innerText()).includes(text))
    mkdirSync('developer-workstation/verification-screenshots', { recursive: true })
    await dialog.screenshot({ path: 'developer-workstation/verification-screenshots/2026-11-04-computed-text-preview.png' })
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ step: 'computed-dw-preview', unitId: state.unitId, pageErrors: 0 }))
  }
  if (mode === 'enable-complete' || mode === 'complete') {
    await loginViaDwPassword(page)
    const unit = await dw('GET', '/function-units/' + state.unitId)
    assert.ok(unit.tags.includes('batch-verification-synthetic') && unit.code === state.code)
    const process = await dw('GET', '/function-units/' + state.unitId + '/process')
    assert.ok(typeof process.bpmnXml === 'string' && !/<(?:\w+:)?(?:serviceTask|sendTask|scriptTask)\b/.test(process.bpmnXml))
    if (mode === 'enable-complete') {
      const actions = await dw('GET', '/function-units/' + state.unitId + '/actions')
      const action = actions.find(item => item.actionType === 'APPROVE' && item.actionName === 'Approve')
        ?? await dw('POST', '/function-units/' + state.unitId + '/actions', { actionName: 'Approve', actionType: 'APPROVE', configJson: { requireComment: true } })
      process.bpmnXml = await page.evaluate(({ xml, actionId }) => {
        const document = new DOMParser().parseFromString(xml, 'application/xml')
        const review = [...document.getElementsByTagNameNS('*', 'userTask')].find(task => task.id === 'Review')
        const properties = review?.getElementsByTagNameNS('*', 'properties')[0]
        if (!properties) throw new Error('Synthetic Review configuration missing')
        for (const [name, value] of [['actionIds', JSON.stringify([String(actionId)])], ['actionNames', JSON.stringify(['Approve'])]]) {
          let property = [...properties.children].find(child => child.getAttribute('name') === name)
          if (!property) { property = document.createElementNS(properties.namespaceURI, 'custom:property'); properties.appendChild(property) }
          property.setAttribute('name', name); property.setAttribute('value', value)
        }
        return new XMLSerializer().serializeToString(document)
      }, { xml: process.bpmnXml, actionId: action.id })
      await dw('POST', '/function-units/' + state.unitId + '/process', process)
      const deployment = await dw('POST', '/function-units/' + state.unitId + '/deploy', { environment: 'DEVELOPMENT', autoEnable: true, conflictStrategy: 'FAIL', changeLog: 'Synthetic completion path verification only' })
      await waitForFixtureDeployment(dw, deployment.deploymentId)
      state.catalogId = (await catalogForUser(state.code)).id
      writeFileSync(statePath, JSON.stringify(state, null, 2))
      console.log(JSON.stringify({ step: 'synthetic-complete-action-enabled', unitId: state.unitId }))
    }
    if (mode === 'complete') {
    await loginViaPortalPassword(page)
    await page.goto(origin + '/portal/tasks/' + state.taskId)
    await page.waitForLoadState('networkidle')
    await page.getByRole('button', { name: 'Approve', exact: true }).click()
    const dialog = page.locator('.el-dialog:visible').last()
    await dialog.locator('textarea').fill('Synthetic computed type completion verification')
    const completed = page.waitForResponse(response => response.url().endsWith('/tasks/' + state.taskId + '/complete') && response.request().method() === 'POST')
    await dialog.getByRole('button', { name: 'Confirm', exact: true }).click()
    const response = await completed, body = await response.json()
    assert.ok(response.ok() && body.success !== false, JSON.stringify(body))
    await page.waitForURL('**/portal/tasks')
    await page.screenshot({ path: 'user-portal/verification-screenshots/2026-11-04-computed-text-complete.png' })
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ step: 'computed-complete', taskId: state.taskId, processId: state.processId, pageErrors: 0 }))
    }
  }
  if (mode.startsWith('prepare') || mode === 'fresh') {
    await loginViaPortalPassword(page)
    const started = await portal('POST', '/processes/' + state.code + '/start', {
      batchStartContext: await readBatchStartContext(page, state.catalogId),
      formData: { [state.mainKey]: text, __subTables__: { [state.store]: [
        { [state.childKey]: crypto.randomUUID(), [state.foreignKey]: text, item_label: 'First computed test', units: 1 },
        { [state.childKey]: crypto.randomUUID(), [state.foreignKey]: text, item_label: 'Second computed test', units: 2 },
      ] } }, remark: 'Synthetic computed text verification only',
    })
    state.processId = started.processInstanceId ?? started.id
    const tasks = unwrapApiList(await portal('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] })).filter(task => task.processInstanceId === state.processId)
    assert.equal(tasks.length, 1); state.taskId = tasks[0].taskId
    writeFileSync(statePath, JSON.stringify(state, null, 2))
    const data = await portal('GET', '/tasks/' + state.taskId + '/form-data')
    const rows = data.fieldValues.__subTables__[state.store]
    assert.equal(rows.length, 2)
    if (mode === 'prepare-red') assert.equal(typeof rows[0].reference_copy, 'number', 'Red fixture must be started on the old runtime')
    else assert.ok(rows.every(row => row.reference_copy === text && row.doubled_units === row.units * 2))
    console.log(JSON.stringify({ step: 'computed-fixture-' + mode, ...state, referenceType: typeof rows[0].reference_copy }))
  }
  if (mode === 'red' || mode === 'green' || mode === 'fresh') {
    await loginViaPortalPassword(page)
    await page.goto(origin + '/portal/tasks/' + state.taskId)
    const grid = page.locator('.sub-table-field').first()
    await grid.locator('tbody tr.el-table__row').nth(1).waitFor()
    const claim = page.getByRole('button', { name: 'Claim', exact: true })
    if (await claim.count()) { await claim.click(); await page.waitForLoadState('networkidle') }
    await grid.locator('tbody tr.el-table__row').first().locator('.el-checkbox').click()
    await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
    const dialog = page.locator('.batch-edit-dialog:visible')
    const caption = 'Computed text saved ' + Date.now()
    await dialog.locator('.el-form-item').filter({ hasText: 'Caption' }).locator('input').fill(caption)
    const saved = page.waitForResponse(response => response.url().endsWith('/tasks/' + state.taskId + '/submit') && response.request().method() === 'POST')
    await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    const response = await saved, body = await response.json()
    mkdirSync('user-portal/verification-screenshots', { recursive: true })
    if (mode === 'red') {
      assert.equal(response.status(), 400)
      assert.match(JSON.stringify(body), /invalid text type/)
    } else {
      assert.ok(response.ok() && body.success !== false, JSON.stringify(body))
      await page.reload(); await grid.locator('tbody tr.el-table__row').nth(1).waitFor()
      const actual = await portal('GET', '/tasks/' + state.taskId + '/form-data')
      const rows = actual.fieldValues.__subTables__[state.store]
      assert.ok(rows.every(row => row.reference_copy === text && row.doubled_units === row.units * 2))
      assert.equal(rows[0].item_label, caption); assert.equal(rows[1].item_label, 'Second computed test')
      await grid.locator('tbody tr.el-table__row').first().getByRole('button', { name: 'Edit', exact: true }).click()
      const ordinary = page.locator('.el-dialog:visible').last()
      assert.equal(await ordinary.locator('.el-form-item').filter({ hasText: 'Copied reference' }).locator('input').inputValue(), text)
      await ordinary.getByRole('button', { name: 'Cancel', exact: true }).click()
    }
    await page.screenshot({ path: 'user-portal/verification-screenshots/2026-11-04-computed-text-' + mode + '.png', fullPage: true })
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ step: 'computed-browser-' + mode, status: response.status(), taskId: state.taskId, pageErrors: 0 }))
  }
} finally { await browser.close() }
