import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const phase = process.argv.includes('--before') ? 'before' : 'after'
const origin = process.env.DW_ORIGIN || 'http://localhost:3000'
const output = fileURLToPath(new URL('../developer-workstation/verification-screenshots/', import.meta.url))
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 1100 } })
page.setDefaultTimeout(15000)
const errors = [], checks = []
page.on('pageerror', error => errors.push(error.message))
let fixtureId, userId
const warning = () => page.locator('.el-message-box:visible')
async function api(method, path, data) {
  const res = await page.request.fetch(`${origin}/api/v1/function-units${path}`, { method, data, headers: { 'X-User-Id': userId } })
  const body = await res.json()
  assert(res.ok() && body.success, `${method} ${path}: ${JSON.stringify(body)}`)
  return body.data
}
async function screenshot(name) {
  await page.waitForTimeout(400)
  await page.screenshot({ path: `${output}2026-10-24_email-dirty-${phase}-${name}.png`, fullPage: true, animations: 'disabled' })
}
function check(name, pass) { checks.push({ name, pass }); console.log(`${pass ? 'PASS' : 'FAIL'} ${name}`) }
function queryDatabase(sql) {
  return execFileSync('docker', ['exec', 'platform-postgres-dev', 'sh', '-c', 'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "$1"', 'sh', sql], { encoding: 'utf8' }).trim()
}
async function choose(name) {
  await warning().getByRole('button', { name, exact: true }).click()
  await warning().waitFor({ state: 'hidden' })
}
async function openNew(tab, root) {
  await page.getByRole('tab', { name: tab, exact: true }).click()
  await page.locator(`${root} .designer-toolbar`).getByRole('button').first().click()
  return page.locator('.el-dialog:visible')
}
async function waitCloseResult(dialog) {
  await Promise.race([dialog.waitFor({ state: 'hidden' }), warning().waitFor()])
}
try {
  userId = (await loginViaDwPassword(page, { loginOrigin: origin })).userId
  fixtureId = (await api('POST', '', { name: `Email dirty verification ${Date.now()}`, virtualGroupIds: ['vg-dev-public'] })).id
  assert(Number.isSafeInteger(fixtureId))
  for (const [label, id] of [['new-fu', fixtureId], ['existing-fu', 2]]) {
    await page.goto(`${origin}/dev/function-units/${id}`)
    let dialog = await openNew('Email Templates', '.email-template-designer')
    await page.locator('.w-e-text-container').waitFor()
    // Wait for the editor creation/serialization turn, without typing into any field.
    await page.waitForTimeout(500)
    check(`${label}: untouched template has no dirty notice`, await dialog.locator('.designer-unsaved-notice').count() === 0)
    await screenshot(`${label}-template-pristine`)
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    await waitCloseResult(dialog)
    const asked = await warning().count() > 0
    check(`${label}: untouched template closes without confirmation`, !asked)
    if (asked) await choose('Discard')
    await dialog.waitFor({ state: 'hidden' })

    dialog = await openNew('Email Monitors', '.email-monitor-designer')
    await dialog.getByRole('tab', { name: 'Field Mapping', exact: true }).click()
    await dialog.getByRole('button', { name: 'Add Field', exact: true }).click()
    await dialog.getByPlaceholder('Case No: ').fill('Customer reference: ')
    check(`${label}: unfinished rule shows dirty notice`, await dialog.locator('.designer-unsaved-notice').count() === 1)
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    await waitCloseResult(dialog)
    const protectedDraft = await warning().count() > 0
    check(`${label}: unfinished rule requires confirmation before closing`, protectedDraft)
    await screenshot(`${label}-monitor-rule`)
    if (protectedDraft) {
      await warning().getByRole('button', { name: 'Close this dialog' }).click()
      check(`${label}: dismissing warning retains the unfinished rule`, await dialog.getByPlaceholder('Case No: ').inputValue() === 'Customer reference: ')
      await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
      await choose('Discard')
    }
    await dialog.waitFor({ state: 'hidden' })
  }

  if (phase === 'after') {
    const base = `/${fixtureId}`
    const table = await api('POST', `${base}/tables`, { tableName: `email_dirty_${fixtureId}`, tableDisplayName: 'Verification', tableType: 'MAIN', requestIdConfig: { fieldNames: ['request_code'], separator: '-' }, fields: [
      { fieldName: 'id', dataType: 'VARCHAR', length: 64, nullable: false, isPrimaryKey: true, pkGeneration: { strategy: 'uuid' } },
      { fieldName: 'request_code', displayName: 'Request Code', dataType: 'VARCHAR', length: 100, nullable: true },
    ] })
    await api('POST', `${base}/forms`, { formName: 'Verification Process Form', formType: 'PROCESS', configJson: { rule: [] }, boundTableId: table.id })
    // Isolated, undeployed design fixture: the local environment has no global IMAP.
    // No mailbox polling/deploy is performed; deleting the temporary FU cascades this row.
    const connection = { name: `verify-${fixtureId}@example.invalid` }
    queryDatabase(`insert into dw_email_connections (connection_uid, function_unit_id, name, direction, enabled, mailbox_address, password_env_key) values ('email-dirty-${fixtureId}', ${fixtureId}, '${connection.name}', 'INBOUND', true, '${connection.name}', 'EMAIL_DIRTY_TEST_UNUSED')`)
    const template = await api('POST', `${base}/email-templates`, { name: 'Existing verification template', bodyHtml: '<p>Original text</p>', enabled: true })
    await page.goto(`${origin}/dev/function-units/${fixtureId}`)
    await page.getByRole('tab', { name: 'Email Templates', exact: true }).click()
    await page.locator('.email-template-designer .el-table__body-wrapper tbody tr').filter({ hasText: template.name }).getByRole('button', { name: 'Edit', exact: true }).click()
    let dialog = page.locator('.el-dialog:visible')
    await page.locator('.w-e-text-container').waitFor()
    await page.waitForTimeout(500)
    check('existing template: loading saved HTML stays pristine', await dialog.locator('.designer-unsaved-notice').count() === 0)
    const editable = dialog.locator('[contenteditable="true"]').first()
    await editable.click(); await page.keyboard.press('Control+A'); await page.keyboard.insertText('Verified edited body')
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    await warning().waitFor(); await screenshot('template-edited-save'); await choose('Save')
    await dialog.waitFor({ state: 'hidden' })
    check('template: edited HTML persisted after Save on leave', (await api('GET', `${base}/email-templates/${template.id}`)).bodyHtml.includes('Verified edited body'))

    dialog = await openNew('Email Monitors', '.email-monitor-designer')
    await dialog.locator('.monitor-form > .form-grid .el-form-item').first().locator('input').fill('Verified monitor')
    await dialog.locator('.monitor-form > .form-grid .el-form-item').nth(1).locator('.el-select').click()
    await page.getByRole('option').filter({ hasText: connection.name }).click()
    await dialog.getByRole('tab', { name: 'Field Mapping', exact: true }).click()
    await dialog.getByRole('button', { name: 'Add Field', exact: true }).click()
    await dialog.getByPlaceholder('Case No: ').fill('Customer reference: ')
    let writes = 0
    const onRequest = request => { if (request.method() === 'POST' && request.url().endsWith(`${base}/email-monitors`)) writes++ }
    page.on('request', onRequest)
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click(); await warning().waitFor(); await choose('Save')
    check('monitor: incomplete rule prevents saving and retains dialog', await dialog.count() === 1 && writes === 0)
    check('monitor: failed save retains rule input', await dialog.getByPlaceholder('Case No: ').inputValue() === 'Customer reference: ')
    await screenshot('monitor-incomplete-save')
    await dialog.locator('.target-field-select').click()
    await page.getByRole('option', { name: 'Request Code (request_code)', exact: true }).click()
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click(); await warning().waitFor(); await choose('Save')
    await dialog.waitFor({ state: 'hidden' })
    const monitors = await api('GET', `${base}/email-monitors`)
    const saved = monitors.find(monitor => monitor.name === 'Verified monitor')
    check('monitor: completed rule persisted after Save on leave', saved?.extractionRules?.fields?.[0]?.label === 'Customer reference: ' && writes === 1)
    page.off('request', onRequest)
    const sql = `select json_build_object('templateHtml', (select body_html from dw_email_templates where id = ${template.id}), 'monitorRules', (select extraction_rules from dw_email_monitor_rules where function_unit_id = ${fixtureId} and name = 'Verified monitor'))`
    const database = JSON.parse(queryDatabase(sql))
    check('database: saved template and extraction rule match the edits', database.templateHtml.includes('Verified edited body') && database.monitorRules.fields[0].label === 'Customer reference: ')
    await writeFile(`${output}2026-10-24_email-dirty-database.json`, JSON.stringify(database, null, 2))
  }
  await writeFile(`${output}2026-10-24_email-dirty-${phase}.json`, JSON.stringify({ checks, browserErrors: errors }, null, 2))
  assert.deepEqual(errors, [])
  assert(checks.every(result => result.pass), `${checks.filter(result => !result.pass).length} email unsaved checks failed`)
  console.log(`PASS ${checks.length} email unsaved checks`)
} finally {
  if (fixtureId) {
    await api('DELETE', `/${fixtureId}`)
    // API deletion archives FUs; also remove only this script's isolated fixture and children.
    queryDatabase(`delete from dw_function_units where id = ${fixtureId} and name like 'Email dirty verification %'`)
  }
  await browser.close()
}
