/** Synthetic design only: Clone, DW ZIP export, then both Admin import HTTP surfaces. No task/data writes. */
import assert from 'node:assert/strict'
import { isDeepStrictEqual } from 'node:util'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaAdminPassword } from './playwright-login.mjs'

const sourceId = process.env.FIXTURE_UNIT_ID
assert.match(sourceId || '', /^\d+$/)
const browser = await chromium.launch({ headless: true })
async function json(page, method, url, data) {
  const response = await page.request.fetch(url, { method, data })
  assert.equal(response.ok(), true, `${method} ${url} HTTP ${response.status()}`)
  const body = await response.json()
  if (body && typeof body === 'object' && 'success' in body) assert.equal(body.success, true)
  return body && typeof body === 'object' && 'data' in body ? body.data : body
}
async function forms(api, id) {
  const result = []
  for (const form of await api('GET', `/function-units/${id}/forms`)) {
    const detail = await api('GET', `/function-units/${id}/forms/${form.id}`)
    const bindings = await api('GET', `/function-units/${id}/forms/${form.id}/bindings`)
    result.push({ ...detail, bindings })
  }
  assert.equal(result.length, 2, 'This fixture has exactly PROCESS and TASK forms')
  return result
}
function enabledControl(config) {
  const controls = config.rule.filter(rule => rule.type === 'subTable')
  assert.equal(controls.length, 1)
  const [control] = controls
  assert.ok(typeof control.batchControlId === 'string' && control.batchControlId.length > 0)
  assert.equal(control.props.allowBatchAssign, true)
  assert.equal(control.props.allowBatchAdd, false); assert.equal(control.props.allowBatchDelete, false)
  assert.ok(config.batchForms[String(control.props._bindingId)])
  assert.ok(config.subForms[String(control.props._bindingId)])
  return control
}
function compareClone(originals, copies, requireNewIds = true, requireNewTables = requireNewIds) {
  for (const source of originals) {
    const copy = copies.find(form => form.formType === source.formType)
    assert.ok(copy)
    if (requireNewIds) assert.notEqual(copy.id, source.id)
    const before = enabledControl(source.configJson)
    const after = enabledControl(copy.configJson)
    const oldBinding = source.bindings.find(binding => binding.id === before.props._bindingId)
    const newBinding = copy.bindings.find(binding => binding.id === after.props._bindingId)
    assert.ok(oldBinding && newBinding)
    if (requireNewIds) assert.notEqual(newBinding.id, oldBinding.id)
    if (requireNewTables) assert.notEqual(newBinding.tableId, oldBinding.tableId)
    else if (requireNewIds) assert.equal(newBinding.tableId, oldBinding.tableId, 'Form copy reuses its existing FU tables')
    assert.deepEqual(Object.keys(copy.configJson.batchForms), [String(newBinding.id)])
    assert.deepEqual(Object.keys(copy.configJson.subForms), [String(newBinding.id)])
    assert.equal(isDeepStrictEqual(copy.configJson.batchForms[String(newBinding.id)], source.configJson.batchForms[String(oldBinding.id)]), true,
      'Clone must preserve the complete independent batch form')
    assert.equal(isDeepStrictEqual(copy.configJson.subForms[String(newBinding.id)], source.configJson.subForms[String(oldBinding.id)]), true,
      'Clone must preserve the original single-row form independently')
    assert.deepEqual({ ...after.props, _bindingId: before.props._bindingId }, before.props)
    const permissions = Object.fromEntries(Object.entries(source.fieldPermissions).map(([field, value]) => {
      const binding = source.bindings.find(item => field.startsWith(`${item.id}:`))
      if (!binding) return [field, value]
      const target = copy.bindings.find(item => item.bindingType === binding.bindingType)
      assert.ok(target)
      return [`${target.id}:${field.slice(String(binding.id).length + 1)}`, value]
    }))
    assert.equal(isDeepStrictEqual(copy.fieldPermissions, permissions), true, 'Scoped field permissions must follow rebuilt binding IDs')
  }
}
async function importAndRollback(page, api, clone, expected, zip) {
  const response = await page.request.post('http://localhost:3000/api/v1/export-import/import', {
    multipart: { file: { name: 'synthetic-batch.zip', mimeType: 'application/zip', buffer: zip }, changeLog: 'Synthetic batch pre-import snapshot' },
  })
  assert.equal(response.ok(), true)
  const imported = (await response.json()).data
  assert.equal(imported.status, 'SUCCESS'); assert.equal(imported.functionUnitId, clone.id)
  assert.equal(imported.versioned, true)
  const rebuilt = await forms(api, clone.id)
  compareClone(expected, rebuilt, false)
  const versions = await api('GET', `/function-units/${clone.id}/versions`)
  const target = versions.find(version => version.changeLog === 'Synthetic batch pre-import snapshot')
  assert.ok(target)
  for (const form of rebuilt) {
    const config = structuredClone(form.configJson)
    for (const rule of config.rule.filter(rule => rule.type === 'subTable')) {
      for (const key of ['allowBatchAdd', 'allowBatchEdit', 'allowBatchDelete', 'allowBatchAssign']) rule.props[key] = false
    }
    delete config.batchForms
    await api('PUT', `/function-units/${clone.id}/forms/${form.id}`, {
      formName: form.formName, formType: form.formType, scene: form.scene, configJson: config, fieldPermissions: form.fieldPermissions,
    })
  }
  const disabled = await forms(api, clone.id)
  assert.ok(disabled.every(form => !('batchForms' in form.configJson) && form.configJson.rule
    .filter(rule => rule.type === 'subTable').every(rule => rule.props.allowBatchAssign === false)))
  await api('POST', `/function-units/${clone.id}/publish?changeLog=Synthetic%20disabled%20batch%20version`)
  await api('POST', `/function-units/${clone.id}/versions/${target.id}/rollback`)
  compareClone(expected, await forms(api, clone.id), false)
  assert.equal((await api('GET', `/function-units/${clone.id}/validate`)).valid, true)
  return target.id
}
async function verifyFormCopies(api, cloneId) {
  const restored = await forms(api, cloneId)
  const copiedIds = []
  for (const type of ['TASK', 'PROCESS']) {
    const source = restored.find(form => form.formType === type)
    assert.ok(source)
    const path = `/function-units/${cloneId}/forms/${source.id}`
    const created = await api('POST', `${path}/${type === 'TASK' ? 'copy' : 'copy-to-task'}`)
    const detail = await api('GET', `/function-units/${cloneId}/forms/${created.id}`)
    const bindings = await api('GET', `/function-units/${cloneId}/forms/${created.id}/bindings`)
    compareClone([{ ...source, formType: 'TASK' }], [{ ...detail, bindings }], true, false)
    assert.notEqual(enabledControl(detail.configJson).batchControlId, enabledControl(source.configJson).batchControlId)
    copiedIds.push(created.id)
  }
  return copiedIds
}
function compareCatalog(content, expected) {
  assert.equal(content.forms.length, expected.length)
  for (const form of expected) {
    const published = content.forms.find(item => item.sourceId === String(form.id))
    assert.ok(published)
    assert.equal(published.formType, form.formType)
    const config = JSON.parse(published.data)
    enabledControl(config)
    assert.equal(isDeepStrictEqual(config, form.configJson), true, 'Admin must preserve the complete configuration, not merely the four flags')
    assert.equal(isDeepStrictEqual(published.publishedFieldPermissions, form.fieldPermissions), true,
      'Admin must retain published permissions rather than rely on live DW permissions')
  }
}
try {
  const dwPage = await browser.newPage()
  const adminPage = await browser.newPage()
  await loginViaDwPassword(dwPage)
  await loginViaAdminPassword(adminPage)
  const dw = (method, path, data) => json(dwPage, method, `http://localhost:3000/api/v1${path}`, data)
  const admin = (method, path, data) => json(adminPage, method, `http://localhost:3000/api/v1/admin${path}`, data)
  const source = await dw('GET', `/function-units/${sourceId}`)
  assert.match(source.name, /^Batch verification /)
  assert.ok(source.tags.includes('batch-verification-synthetic'))
  const originals = await forms(dw, sourceId)
  const clone = await dw('POST', `/function-units/${sourceId}/clone?newName=${encodeURIComponent(`Batch verification portability${Date.now()}`)}`)
  assert.notEqual(String(clone.id), sourceId)
  const copies = await forms(dw, clone.id)
  compareClone(originals, copies)
  assert.equal((await dw('GET', `/function-units/${clone.id}/validate`)).valid, true)
  const exported = await dwPage.request.get(`http://localhost:3000/api/v1/export-import/function-units/${clone.id}/export`)
  assert.equal(exported.ok(), true)
  const zip = await exported.body()
  assert.equal(zip.subarray(0, 2).toString(), 'PK')
  const imported = await admin('POST', '/function-units/import', {
    fileName: 'synthetic-batch.zip', fileContent: zip.toString('base64'), enableOnImport: false,
  })
  assert.equal(imported.success, true)
  const catalogId = imported.functionUnit.id
  compareCatalog(await admin('GET', `/function-units/${catalogId}/content`), copies)
  const secondResponse = await adminPage.request.post('http://localhost:3000/api/v1/admin/function-units-import/import', {
    multipart: { file: { name: 'synthetic-batch.zip', mimeType: 'application/zip', buffer: zip } },
  })
  assert.equal(secondResponse.ok(), true)
  const second = await secondResponse.json()
  assert.equal(second.status, 'SUCCESS')
  compareCatalog(await admin('GET', `/function-units/${second.functionUnitId}/content`), copies)
  const rollbackVersionId = await importAndRollback(dwPage, dw, clone, copies, zip)
  compareCatalog(await admin('GET', `/function-units/${catalogId}/content`), copies)
  const copiedFormIds = await verifyFormCopies(dw, clone.id)
  assert.equal(isDeepStrictEqual(await forms(dw, sourceId), originals), true, 'Source FU must remain untouched')
  console.log(JSON.stringify({ step: 'synthetic-batch-config-lifecycle', sourceUnitId: sourceId, clonedUnitId: clone.id,
    catalogIds: [catalogId, second.functionUnitId], rollbackVersionId, copiedFormIds,
    verified: ['clone-binding-remap', 'separate-single-and-batch-forms', 'dw-zip-export', 'admin-json-import', 'admin-multipart-import',
      'dw-reimport', 'enabled-disabled-enabled-rollback', 'rollback-does-not-deploy', 'task-form-copy', 'process-to-task-copy'], taskWrites: 0 }))
} finally { await browser.close() }
