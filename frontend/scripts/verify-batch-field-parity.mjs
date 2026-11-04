/** Local synthetic fixtures only. Real native controls, ordinary/Batch drafts and persisted values. */
import assert from 'node:assert/strict'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'
import { readBatchStartContext } from './batch-start-context.mjs'
import { unwrapApiList } from './mi-regression-helpers.mjs'

const origin = 'http://localhost:3000', mode = process.argv[2]
assert.ok(['prepare-red', 'red', 'prepare', 'prepare-defaults', 'portal', 'dw', 'legacy'].includes(mode))
const statePath = new URL('../../.codex-batch-field-parity-state.json', import.meta.url)
const browser = await chromium.launch(), page = await browser.newPage({ viewport: { width: 1700, height: 1400 } })
page.setDefaultTimeout(20000)
const errors = []; page.on('pageerror', error => errors.push(error.message))
const call = async (prefix, method, path, data) => {
  const response = await page.request.fetch(origin + prefix + path, { method, data }), body = await response.json()
  assert.ok(response.ok() && body.success !== false, JSON.stringify({ path, status: response.status(), message: body.message || body.error?.message }))
  return body.data ?? body
}
const dw = (method, path, data) => call('/api/v1', method, path, data)
const portal = (method, path, data) => call('/api/portal', method, path, data)
function persistedRows(processId, store) {
  assert.match(processId, /^[a-f0-9-]{36}$/)
  const raw = execFileSync('docker', ['exec', '-i', 'platform-postgres-dev', 'sh', '-c', 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'], {
    input: `select variables::text from up_process_instance where id='${processId}';`, encoding: 'utf8',
  })
  return JSON.parse(raw.trim()).__subTables__[store]
}
let state
try {
  if (mode.startsWith('prepare')) {
    await loginViaDwPassword(page)
    const source = await dw('GET', '/function-units/50131')
    assert.ok(source.tags.includes('batch-verification-synthetic'))
    const unit = await dw('POST', '/function-units/50131/clone?newName=' + encodeURIComponent('Batch verification fieldparity' + Date.now()))
    const base = '/function-units/' + unit.id
    const tables = await dw('GET', base + '/tables'), main = tables.find(table => table.tableType === 'MAIN'), child = tables.find(table => table.tableType === 'SUB')
    const mainKey = main.fieldDefinitions.find(field => field.isPrimaryKey).fieldName
    const childKey = child.fieldDefinitions.find(field => field.isPrimaryKey).fieldName
    const foreignKey = child.fieldDefinitions.find(field => field.isForeignKey && String(field.refTableId) === String(main.id)).fieldName
    const additions = [
      ['switch_num', 'INTEGER'], ['switch_text', 'VARCHAR'], ['choices', 'JSON'], ['period', 'JSON'],
      ['occurred', 'TIMESTAMP'], ['contact', 'VARCHAR'], ['code', 'VARCHAR'], ['category', 'VARCHAR'], ['concealed', 'VARCHAR'],
    ].map(([fieldName, dataType]) => ({ fieldName, displayName: fieldName, dataType, ...(dataType === 'VARCHAR' ? { length: 255 } : {}), nullable: true }))
    if (mode === 'prepare-defaults') additions.push({ fieldName: 'fraction', displayName: 'Fraction', dataType: 'DECIMAL', precision: 18, scale: 2, nullable: true, isComputed: true,
      computedField: { version: 1, scope: 'row', source: '1 / 2', ast: { type: 'binary', op: '/', left: { type: 'number', text: '1' }, right: { type: 'number', text: '2' } }, dependsOn: [], onError: 'fail' } })
    await dw('PUT', base + '/tables/' + child.id, { tableName: child.tableName, tableType: child.tableType, tableDisplayName: child.tableDisplayName,
      fields: [...child.fieldDefinitions.map(field => ({ ...field, pkGeneration: field.pkGenerationJson, computedField: field.computedFieldJson })), ...additions] })
    const validation = mode !== 'prepare-red' ? {
      contact: [{ type: 'email', message: 'Enter a valid email' }], code: [{ pattern: '^A', message: 'Code must start with A' }], occurred: [{ type: 'date' }],
    } : {}
    const rule = [
      { type: 'input', field: 'item_label', title: 'Caption', on: { change: '$FNX:api.setOptions("category", [{label:"Dynamic",value:"dynamic"}]); api.setValue("category", "dynamic");' } },
      { type: 'inputNumber', field: 'units', title: 'Units' },
      { type: 'switch', field: 'switch_num', title: 'Numeric switch', props: { activeValue: 1, inactiveValue: 0 } },
      { type: 'switch', field: 'switch_text', title: 'Text switch', props: { activeValue: 'Y', inactiveValue: 'N' } },
      { type: 'select', field: 'choices', title: 'Choices', props: { multiple: true }, options: [{ label: 'Alpha', value: 'a' }, { label: 'Beta', value: 'b' }] },
      { type: 'datePicker', field: 'period', title: 'Period', props: { type: 'daterange', ...(mode === 'prepare-defaults' ? {} : { valueFormat: 'YYYY-MM-DD' }) } },
      { type: 'datePicker', field: 'occurred', title: 'Occurred', props: { type: 'datetime', ...(mode === 'prepare-defaults' ? {} : { valueFormat: 'YYYY-MM-DD HH:mm:ss' }) }, validate: validation.occurred ?? [] },
      { type: 'input', field: 'contact', title: 'Contact', validate: validation.contact ?? [] },
      { type: 'input', field: 'code', title: 'Code', validate: validation.code ?? [] },
      { type: 'select', field: 'category', title: 'Category', options: [{ label: 'Static', value: 'static' }] },
      { type: 'input', field: 'concealed', title: 'Concealed', hidden: true, required: true },
    ]
    const forms = await dw('GET', base + '/forms')
    for (const form of forms) {
      for (const binding of form.tableBindings.filter(binding => binding.tableId === child.id)) {
        form.configJson.batchForms[binding.id] = { schemaVersion: 1, rule, options: {} }
        form.configJson.subForms[binding.id] = { rule, options: {} }
        for (const field of rule) form.fieldPermissions[binding.id + ':' + field.field] = 'EDITABLE'
      }
      for (const key of Object.keys(form.configJson.options ?? {})) if (key.startsWith('on') || key.startsWith('before')) delete form.configJson.options[key]
      await dw('PUT', base + '/forms/' + form.id, form)
    }
    assert.ok((await dw('GET', base + '/email-monitors')).every(item => !item.enabled))
    const process = await dw('GET', base + '/process')
    assert.ok(!/<(?:\w+:)?(?:serviceTask|sendTask|scriptTask)\b/.test(process.bpmnXml))
    const deployed = await dw('POST', base + '/deploy', { environment: 'DEVELOPMENT', autoEnable: true, conflictStrategy: 'FAIL', changeLog: 'Synthetic ordinary/Batch type parity' })
    await waitForFixtureDeployment(dw, deployed.deploymentId)
    await loginViaPortalPassword(page)
    const roleId = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_up_user')).activeRoleId)
    await loginViaAdminPassword(page)
    const catalog = await dw('GET', '/admin/function-units/by-process-key/' + unit.code)
    const access = await dw('GET', '/admin/function-units/' + catalog.id + '/access')
    if (!access.some(item => item.roleId === roleId)) await dw('POST', '/admin/function-units/' + catalog.id + '/access', { roleId })
    await loginViaPortalPassword(page)
    const baseline = [1, 2].map(n => ({ [childKey]: crypto.randomUUID(), [foreignKey]: 'PARITY-CASE', item_label: 'Parity row ' + n, units: n,
      switch_num: 0, switch_text: 'N', choices: ['a'], period: ['2026-11-01', '2026-11-04'], occurred: '2026-11-04 12:13:14',
      contact: 'person@example.invalid', code: 'ABC', category: 'static' }))
    const started = await portal('POST', '/processes/' + unit.code + '/start', {
      batchStartContext: await readBatchStartContext(page, catalog.id), formData: { [mainKey]: 'PARITY-CASE', __subTables__: { ['dw:' + child.tableName.toLowerCase()]: baseline } }, remark: 'Synthetic parity verification',
    })
    const processId = started.processInstanceId ?? started.id
    const tasks = unwrapApiList(await portal('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] })).filter(task => task.processInstanceId === processId)
    assert.equal(tasks.length, 1)
    state = { unitId: unit.id, catalogId: catalog.id, code: unit.code, store: 'dw:' + child.tableName.toLowerCase(), childKey, foreignKey, mainKey, processId, taskId: tasks[0].taskId, baseline, hasFraction: mode === 'prepare-defaults' }
    writeFileSync(statePath, JSON.stringify(state, null, 2))
    console.log(JSON.stringify({ step: mode, unitId: state.unitId, taskId: state.taskId }))
  } else if (mode === 'legacy') {
    const taskId = process.argv[3]
    assert.match(taskId, /^[a-f0-9-]{36}$/)
    await loginViaPortalPassword(page)
    const task = await portal('GET', '/tasks/' + taskId)
    assert.match(task.functionUnitName, /^Batch verification fieldparity/)
    const before = (await portal('GET', '/tasks/' + taskId + '/form-data')).fieldValues.__subTables__
    const store = Object.keys(before).find(key => before[key]?.some(row => Object.hasOwn(row, 'switch_num')))
    assert.ok(store)
    await page.goto(origin + '/portal/tasks/' + taskId)
    const grid = page.locator('.sub-table-field').first()
    await grid.locator('tbody tr.el-table__row').nth(1).waitFor()
    await grid.locator('tbody tr.el-table__row').first().locator('.el-checkbox').click()
    await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
    const dialog = page.locator('.batch-edit-dialog:visible'), caption = 'Legacy parity ' + Date.now()
    await dialog.locator('.el-form-item').filter({ hasText: 'Caption' }).locator('input').fill(caption)
    const saved = page.waitForResponse(response => response.url().endsWith('/tasks/' + taskId + '/submit') && response.request().method() === 'POST')
    await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
    const response = await saved
    assert.ok(response.ok() && (await response.json()).success)
    const rows = persistedRows(task.processInstanceId, store)
    assert.equal(rows[0].item_label, caption)
    const strip = row => Object.fromEntries(Object.entries(row).filter(([key]) => key !== '_wsRowVersion'))
    assert.deepEqual(strip(rows[1]), strip(before[store][1]))
    await page.screenshot({ path: 'user-portal/verification-screenshots/2026-11-04-batch-field-parity-legacy.png', fullPage: true })
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ step: 'parity-legacy', taskId, databaseVerified: true, pageErrors: 0 }))
  } else {
    state = JSON.parse(readFileSync(statePath, 'utf8'))
    let grid, designBaseline
    if (mode === 'dw') {
      await loginViaDwPassword(page)
      designBaseline = await dw('GET', '/function-units/' + state.unitId + '/forms')
      const form = designBaseline.find(item => item.formType === 'TASK')
      await page.goto(origin + '/dev/function-units/' + state.unitId)
      await page.getByRole('tab', { name: /Form/ }).click()
      await page.getByRole('row').filter({ hasText: form.formName }).getByRole('button', { name: 'Edit', exact: true }).click()
      await page.locator('.form-editor-view .header-actions').getByRole('button', { name: 'Preview', exact: true }).click()
      const preview = page.locator('.form-preview-dialog').last()
      await preview.locator('.el-form-item').filter({ hasText: 'Case key' }).locator('input').first().fill('PARITY-CASE')
      grid = preview.locator('.sub-table-field').first()
      await grid.getByRole('button', { name: 'Batch Add', exact: true }).click()
    } else {
      await loginViaPortalPassword(page)
      await page.goto(origin + '/portal/tasks/' + state.taskId)
      grid = page.locator('.sub-table-field').first()
      await grid.locator('tbody tr.el-table__row').nth(1).waitFor()
      const claim = page.getByRole('button', { name: 'Claim', exact: true })
      if (await claim.count()) { await claim.click(); await page.waitForLoadState('networkidle') }
      await grid.locator('tbody tr.el-table__row').first().locator('.el-checkbox').click()
      await grid.getByRole('button', { name: 'Batch Edit', exact: true }).click()
    }
    const dialog = page.locator('.batch-edit-dialog:visible')
    const caption = 'Batch parity ' + Date.now()
    const field = label => dialog.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp('^' + label + '$') }) })
    if (mode !== 'red') {
      await field('Contact').locator('input').fill('invalid')
      await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
      await page.waitForTimeout(200)
      assert.match(await dialog.locator('.el-alert').first().innerText(), /Enter a valid email/)
      await dialog.screenshot({ path: 'user-portal/verification-screenshots/2026-11-04-batch-field-parity-email-error.png' })
      await field('Contact').locator('input').fill('person@example.invalid')
      await field('Code').locator('input').fill('BCD')
      await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
      await page.waitForTimeout(200)
      assert.match(await dialog.locator('.el-alert').first().innerText(), /Code must start with A/)
      await field('Code').locator('input').fill('ABC')
      for (const label of ['Numeric switch', 'Text switch']) {
        const control = field(label).locator('.el-switch')
        if (!(await control.getAttribute('class')).includes('is-checked')) await control.click()
      }
      await field('Choices').locator('.el-select').click()
      if (mode === 'dw') await page.getByRole('option', { name: 'Alpha', exact: true }).click()
      const beta = page.getByRole('option', { name: 'Beta', exact: true })
      if (await beta.getAttribute('aria-selected') !== 'true') await beta.click()
      await field('Caption').locator('input').click()
      const dates = field('Period').locator('input')
      await dates.nth(0).fill('2026-11-02'); await dates.nth(1).fill('2026-11-06'); await dates.nth(1).press('Enter')
      await field('Occurred').locator('input').fill('2026-11-05 13:14:15'); await field('Occurred').locator('input').press('Enter')
    }
    await field('Caption').locator('input').fill(caption)
    await field('Units').locator('input').fill('3')
    await field('Units').locator('input').press('Tab')
    if (mode !== 'red') await dialog.screenshot({ path: `${mode === 'dw' ? 'developer-workstation' : 'user-portal'}/verification-screenshots/2026-11-04-batch-field-parity-${mode}-controls.png` })
    const saved = mode === 'portal' ? page.waitForResponse(response => response.url().endsWith('/tasks/' + state.taskId + '/submit') && response.request().method() === 'POST') : undefined
    await dialog.getByRole('button', { name: 'Apply to page', exact: true }).click()
    if (mode === 'red') {
      await page.waitForTimeout(300)
      assert.ok(await dialog.isVisible())
      const error = await dialog.locator('.el-alert').first().innerText()
      assert.match(error, /validation|switch|Concealed/i)
      await dialog.screenshot({ path: 'user-portal/verification-screenshots/2026-11-04-batch-field-parity-red.png' })
      console.log(JSON.stringify({ step: 'parity-red', error }))
    } else if (mode === 'dw') {
      await dialog.waitFor({ state: 'hidden' })
      assert.match(await grid.innerText(), new RegExp(caption))
      assert.deepEqual(await dw('GET', '/function-units/' + state.unitId + '/forms'), designBaseline)
      await page.screenshot({ path: 'developer-workstation/verification-screenshots/2026-11-04-batch-field-parity.png', fullPage: true })
      assert.deepEqual(errors, [])
      console.log(JSON.stringify({ step: 'parity-dw', unitId: state.unitId, pageErrors: 0, designUnchanged: true }))
    } else {
      await dialog.waitFor({ state: 'hidden' })
      if (await page.getByRole('button', { name: 'Save', exact: true }).count()) await page.getByRole('button', { name: 'Save', exact: true }).click()
      const response = await saved, body = await response.json()
      assert.ok(response.ok() && body.success !== false, JSON.stringify(body))
      const actual = await portal('GET', '/tasks/' + state.taskId + '/form-data')
      const rows = actual.fieldValues.__subTables__[state.store]
      assert.equal(rows[0].item_label, caption); assert.equal(rows[0].category, 'dynamic')
      const expected = { switch_num: 1, switch_text: 'Y', choices: ['a', 'b'], period: ['2026-11-02', '2026-11-06'], occurred: '2026-11-05 13:14:15', contact: 'person@example.invalid', code: 'ABC' }
      for (const key of Object.keys(expected)) assert.deepEqual(rows[0][key], expected[key], key)
      const stored = persistedRows(state.processId, state.store)
      for (const key of Object.keys(expected)) assert.deepEqual(stored[0][key], expected[key], 'database ' + key)
      if (state.hasFraction) assert.ok(stored.every(row => row.fraction === 0.5), 'Declared DECIMAL calculation must survive Batch Save')
      for (const key of Object.keys(state.baseline[1])) assert.deepEqual(rows[1][key], state.baseline[1][key], 'unselected ' + key)
      await page.reload(); await grid.locator('tbody tr.el-table__row').nth(1).waitFor()
      await page.screenshot({ path: 'user-portal/verification-screenshots/2026-11-04-batch-field-parity-green.png', fullPage: true })
      await grid.locator('tbody tr.el-table__row').first().getByRole('button', { name: 'Edit', exact: true }).click()
      const ordinary = page.locator('.el-dialog:visible').last()
      const normal = label => ordinary.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp('^' + label + '$') }) })
      await normal('Numeric switch').locator('.el-switch.is-checked').waitFor()
      await normal('Text switch').locator('.el-switch.is-checked').waitFor()
      assert.match(await normal('Choices').innerText(), /Alpha/); assert.match(await normal('Choices').innerText(), /Beta/)
      assert.deepEqual(await normal('Period').locator('input').evaluateAll(items => items.map(item => item.value)), expected.period)
      await normal('Contact').locator('input').fill('invalid')
      await ordinary.getByRole('button', { name: 'Save', exact: true }).click()
      await ordinary.getByText('Enter a valid email', { exact: true }).waitFor()
      await normal('Contact').locator('input').fill('person@example.invalid')
      await normal('Code').locator('input').fill('BCD')
      await ordinary.getByRole('button', { name: 'Save', exact: true }).click()
      await ordinary.getByText('Code must start with A', { exact: true }).waitFor()
      await normal('Code').locator('input').fill('ABC')
      await normal('Code').locator('input').press('Tab')
      await normal('Code').locator('.el-form-item__error').waitFor({ state: 'hidden' })
      await ordinary.screenshot({ path: 'user-portal/verification-screenshots/2026-11-04-ordinary-field-parity.png' })
      await ordinary.getByRole('button', { name: 'Save', exact: true }).click()
      await ordinary.waitFor({ state: 'hidden' })
      assert.deepEqual(errors, [])
      console.log(JSON.stringify({ step: 'parity-green', taskId: state.taskId, selected: 1, unselectedUnchanged: true, pageErrors: 0 }))
    }
  }
} catch (error) {
  mkdirSync('user-portal/verification-screenshots', { recursive: true })
  await page.screenshot({ path: 'user-portal/verification-screenshots/2026-11-04-batch-field-parity-failure.png', fullPage: true })
  console.log(JSON.stringify({ step: 'failure', alerts: await page.locator('.el-alert').allTextContents(), buttons: await page.getByRole('button').allTextContents() }))
  throw error
} finally { await browser.close() }
