/** User-authorized local FU50125 configuration update; never rewrites frozen tasks. */
import assert from 'node:assert/strict'
import { writeFileSync } from 'node:fs'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'
import { assigneeEmailBody, assigneeEmailScript } from './fixtures/assignee-email-script.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'

const mode = process.argv[2]
assert.ok(['configure', 'deploy', 'context'].includes(mode))
const origin = 'http://localhost:3000', base = '/function-units/50125'
const browser = await chromium.launch(), page = await browser.newPage()
const api = async (method, path, data) => {
  const response = await page.request.fetch(origin + '/api/v1' + path, { method, data })
  const body = await response.json()
  assert.ok(response.ok() && body.success !== false, body.error?.message || body.message || String(response.status()))
  return body.data ?? body
}
try {
  if (mode === 'context') {
    await loginViaPortalPassword(page, { roleCode: 'MANAGER' })
    console.log('Manager workspace login succeeded')
  } else {
    await loginViaDwPassword(page)
    const unit = await api('GET', base)
    assert.equal(unit.code, 'mcy-dual-binding-1101-179349950603-20261101-5srx1c')
    if (mode === 'deploy') {
      const monitors = await api('GET', base + '/email-monitors')
      assert.ok(monitors.every(item => item.enabled === false), 'Refusing to deploy with an enabled email monitor')
      const deployment = await api('POST', base + '/deploy', { environment: 'DEVELOPMENT', autoEnable: true, conflictStrategy: 'FAIL', changeLog: 'Add dedicated assignee email and align Lookup/ID backfill scripts' })
      await waitForFixtureDeployment(api, deployment.deploymentId)
      console.log(JSON.stringify({ step: 'published-local', unitId: unit.id, code: unit.code, deploymentId: deployment.deploymentId }))
    } else {
      const tables = await api('GET', base + '/tables'), forms = await api('GET', base + '/forms')
      const table = tables.find(item => item.id === 50590)
      assert.equal(table.tableName, 'HMDC_Transaction_copy5')
      writeFileSync(new URL(`../../.codex-mcy-email-backup-${Date.now()}.json`, import.meta.url), JSON.stringify({ unit, tables, forms }, null, 2))
      if (!table.fieldDefinitions.some(field => field.fieldName === 'assignee_email')) {
        await api('PUT', base + '/tables/' + table.id, { tableName: table.tableName, tableType: table.tableType, tableDisplayName: table.tableDisplayName,
          fields: [...table.fieldDefinitions.map(field => ({ ...field, pkGeneration: field.pkGenerationJson, computedField: field.computedFieldJson })),
            { fieldName: 'assignee_email', displayName: 'Assignee Email', dataType: 'VARCHAR', length: 255, nullable: true }] })
      }
      const changed = []
      for (const form of forms) {
        const bindings = form.tableBindings.filter(binding => binding.tableId === table.id)
        let dirty = false
        for (const binding of bindings) {
          for (const bucket of ['subForms', 'batchForms']) {
            const design = form.configJson?.[bucket]?.[binding.id]
            if (!design?.rule) continue
            const editable = binding.bindingMode === 'EDITABLE'
            function visit(rules) {
              for (const rule of rules) if (rule.field === 'merchant_name' && /assignee\s*email/i.test(rule.title ?? '')) {
                rule.field = 'assignee_email'; rule.title = 'Assignee Email'
                rule.props = { ...rule.props, placeholder: 'Filled from the selected assignee' }
              }
              const assignee = rules.find(rule => rule.field === 'assignee_id')
              if (assignee) {
                if (editable) {
                  assignee.on = { ...assignee.on, change: assigneeEmailScript }
                  if (assignee._on) assignee._on.change = assigneeEmailScript
                }
                if (!rules.some(rule => rule.field === 'assignee_email')) rules.splice(rules.indexOf(assignee) + 1, 0,
                  { type: 'input', field: 'assignee_email', title: 'Assignee Email', props: { maxlength: 255, placeholder: 'Filled from the selected assignee', ...(editable ? {} : { readonly: true }) } })
                dirty = true
              }
              for (const rule of rules) if (Array.isArray(rule.children)) visit(rule.children)
            }
            visit(design.rule)
          }
          if (dirty) form.fieldPermissions = { ...form.fieldPermissions, assignee_email: binding.bindingMode === 'EDITABLE' ? 'EDITABLE' : 'READONLY',
            [`${binding.id}:assignee_email`]: binding.bindingMode === 'EDITABLE' ? 'EDITABLE' : 'READONLY' }
        }
        if (dirty) { await api('PUT', base + '/forms/' + form.id, form); changed.push(form.id) }
      }
      const savedTables = await api('GET', base + '/tables'), savedForms = await api('GET', base + '/forms')
      const saved = savedTables.find(item => item.id === table.id)
      assert.deepEqual(saved.fieldDefinitions.filter(field => field.isPrimaryKey).map(field => field.fieldName), ['row_id'])
      assert.ok(saved.fieldDefinitions.some(field => field.fieldName === 'merchant_name'))
      assert.ok(saved.fieldDefinitions.some(field => field.fieldName === 'assignee_email'))
      const host = savedForms.find(form => form.id === 50898)
      assert.equal(host.configJson.batchForms['51443'].rule[0].children.find(rule => rule.field === 'assignee_id').on.change, assigneeEmailScript)
      assert.equal(host.configJson.batchForms['51443'].rule[0].children.find(rule => rule.field === 'assignee_email').field, 'assignee_email')
      writeFileSync(new URL('../../.codex-mcy-email-after.json', import.meta.url), JSON.stringify({ tables: savedTables, forms: savedForms }, null, 2))
      writeFileSync('F:/hermes1.0/31.1299,script error/assignee-email.optimized.js', assigneeEmailBody + '\n')
      console.log(JSON.stringify({ step: 'configured', unitId: unit.id, tableId: table.id, changedForms: changed, independentField: 'assignee_email' }))
    }
  }
} finally { await browser.close() }
