import { execFileSync } from 'node:child_process'
import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { writeFile } from 'node:fs/promises'
import { loginViaDwPassword } from './playwright-login.mjs'

const phase = process.argv[2]
assert(['red', 'green'].includes(phase))
const origin = 'http://localhost:3000'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
const evidence = { phase, units: [], checks: [] }
const state = `/tmp/action-mail-clone-${phase}.json`
const persist = () => writeFile(state, JSON.stringify(evidence, null, 2))
async function api(method, path, data) {
  const response = await page.request.fetch(`${origin}/api/v1${path}`, {
    method, ...(data === undefined ? {} : { data }),
  })
  const body = await response.json()
  assert(response.ok() && body.success !== false, `${method} ${path}: ${response.status()} ${body.message || body.error?.message || ''}`)
  return body.data
}
async function configs(id) {
  return Object.fromEntries((await api('GET', `/function-units/${id}/actions`))
    .map(action => [action.actionName, action.configJson]))
}
async function clone(source) {
  const unit = await api('POST', `/function-units/${source.id}/clone?newName=${encodeURIComponent(`Action mail clone QA ${phase} ${Date.now()}`)}`)
  evidence.units.push(unit); await persist()
  return unit
}
async function assertMapped(unit) {
  const [templates, connections, actions] = await Promise.all([
    api('GET', `/function-units/${unit.id}/email-templates`),
    api('GET', `/function-units/${unit.id}/connections`), configs(unit.id),
  ])
  assert.equal(templates.length, 1); assert.equal(connections.length, 1)
  for (const [name, config] of Object.entries(actions)) {
    const rule = config.postEmailRules?.[0] ?? config.postEmail
    assert.equal(String(rule.emailTemplateId), String(templates[0].id))
    assert.equal(rule.connectionId, connections[0].connectionUid)
    assert.equal(rule.enabled, false)
    evidence.checks.push({ step: 'mapped', unit: unit.id, name, templateId: rule.emailTemplateId, connectionUid: rule.connectionId })
  }
}
try {
  await loginViaDwPassword(page)
  const groups = await api('GET', '/function-units/my-dev-groups')
  const source = await api('POST', '/function-units', {
    name: `Action mail source QA ${phase} ${Date.now()}`, tags: ['action-mail-clone-synthetic'], virtualGroupIds: [groups.publicGroupId],
  })
  evidence.units.push(source); await persist()
  const base = `/function-units/${source.id}`
  await api('POST', `${base}/email-templates`, { name: 'QA clone template', subject: 'QA', bodyHtml: '<p>QA clone isolation</p>', enabled: false })
  // Seed disabled historical connection metadata through the official import path;
  // creating a new outbound connection requires global SMTP settings, which this QA must not change.
  const exported = await page.request.get(`${origin}/api/v1/export-import/function-units/${source.id}/export`)
  assert(exported.ok())
  const buffer = execFileSync('python3', ['-c', `
import io,json,sys,uuid,zipfile
source=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read()))
output=io.BytesIO()
path='connections/connection_qa.json'
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as target:
 for entry in source.infolist():
  data=source.read(entry.filename)
  if entry.filename=='manifest.json':
   manifest=json.loads(data)
   manifest['components']['connections']=[path]
   data=json.dumps(manifest).encode()
  target.writestr(entry,data)
 target.writestr(path,json.dumps({'connectionUid':str(uuid.uuid4()),'name':'qa-clone@example.invalid','connectionType':'SMTP','host':'localhost','port':2525,'useTls':False,'enabled':False,'direction':'OUTBOUND','fromEmail':'qa-clone@example.invalid'}))
sys.stdout.buffer.write(output.getvalue())
`], { input: await exported.body() })
  const imported = await page.request.post(`${origin}/api/v1/export-import/import?changeLog=Action-mail-clone-QA`, {
    multipart: { file: { name: 'action-mail-qa.zip', mimeType: 'application/zip', buffer } },
  })
  const importedBody = await imported.json()
  assert(imported.ok() && importedBody.success !== false, importedBody.error?.message || importedBody.message)
  assert.equal(importedBody.data.functionUnitId, source.id)
  const [template] = await api('GET', `${base}/email-templates`)
  const [connection] = await api('GET', `${base}/connections`)
  assert(template && connection)
  for (const format of ['postEmailRules', 'postEmail']) {
    const rule = { id: 'qa-rule', enabled: false, emailTemplateId: String(template.id), connectionId: connection.connectionUid, emailTo: 'qa@example.invalid' }
    await api('POST', `${base}/actions`, { actionName: format, actionType: 'APPROVE', configJson: { [format]: format === 'postEmailRules' ? [rule] : rule } })
  }
  const before = await configs(source.id)
  const first = await clone(source)
  const after = await configs(source.id)
  evidence.checks.push({ step: 'source-before-after', unit: source.id, before, after }); await persist()
  if (phase === 'red') {
    assert.notDeepEqual(after.postEmailRules, before.postEmailRules)
    assert.deepEqual(after.postEmail, before.postEmail)
  } else assert.deepEqual(after, before)
  await assertMapped(first)
  if (phase === 'green') {
    const firstBefore = await configs(first.id)
    const second = await clone(first)
    assert.deepEqual(await configs(first.id), firstBefore)
    assert.deepEqual(await configs(source.id), before)
    await assertMapped(second)
  }
  evidence.result = phase === 'red' ? 'REPRODUCED' : 'PASS'
  await persist(); console.log(JSON.stringify(evidence))
} finally {
  await browser.close()
}
