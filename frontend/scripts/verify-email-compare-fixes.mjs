#!/usr/bin/env node
// Read-only replay of saved QA versions; never sends email or polls monitors.
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import { createHash } from 'node:crypto'

const phase = process.argv[2]
assert.ok(['before', 'after'].includes(phase))
assert.ok(process.env.LOGIN_USER && process.env.LOGIN_PASS, 'Inject local QA login through environment')
const dir = 'docs/test-evidence/email-compare-fixes-2026-10-08'
await fs.mkdir(dir, { recursive: true })
const original = JSON.parse(await fs.readFile('docs/test-evidence/email-design-2026-10-08/state.json', 'utf8'))
const login = await fetch('http://localhost:3000/api/v1/auth/login', { method: 'POST',
  headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: process.env.LOGIN_USER, password: process.env.LOGIN_PASS }) })
assert.ok(login.ok, 'Local QA authentication failed')
const auth = await login.json()
const user = auth.user ?? auth.data?.user
const cookie = login.headers.getSetCookie().map(c => c.split(';')[0]).join('; ')
async function get(path) {
  const res = await fetch(`http://localhost:3000/api/v1${path}`, {
    headers: { Cookie: cookie, 'X-User-Id': String(user.userId), 'Accept-Language': 'en' } })
  return { status: res.status, body: await res.json() }
}
const hashes = {}
for (const id of new Set(original.results.flatMap(r => [r.from, r.to]))) {
  const fu = original.versionFu[id] ?? original.fuId
  const result = await get(`/function-units/${fu}/versions/${id}/export`)
  assert.equal(result.status, 200)
  hashes[id] = createHash('sha256').update(JSON.stringify(result.body)).digest('hex')
}
await fs.writeFile(`${dir}/${phase}-snapshot-hashes.json`, JSON.stringify(hashes, null, 2))
if (phase === 'after') assert.deepEqual(hashes, JSON.parse(await fs.readFile(`${dir}/before-snapshot-hashes.json`, 'utf8')))
const results = []
for (const expected of original.results) {
  const fu = original.versionFu[expected.from] ?? original.fuId
  const pair = []
  for (const [a, b] of [[expected.from, expected.to], [expected.to, expected.from]]) {
    pair.push(await get(`/function-units/${fu}/versions/compare-v2?versionId1=${a}&versionId2=${b}`))
  }
  await fs.writeFile(`${dir}/${phase}-${expected.label}.json`, JSON.stringify(pair, null, 2))
  const result = { label: expected.label, from: expected.from, to: expected.to, pass: true, errors: [] }
  try {
    for (const response of pair) { assert.equal(response.status, 200); assert.equal(response.body.success, true) }
    const modules = pair[0].body.data.modules
    const selected = modules.find(m => m.key === expected.module).semantic
    assert.deepEqual(selected.counts, expected.expected)
    for (const m of modules) {
      const reverse = pair[1].body.data.modules.find(r => r.key === m.key).semantic
      assert.deepEqual(reverse.counts, { added: m.semantic.counts.removed, modified: m.semantic.counts.modified, removed: m.semantic.counts.added })
      if (m.key !== expected.module) assert.deepEqual(m.semantic.counts, { added: 0, modified: 0, removed: 0 }, m.key)
      for (const item of m.semantic.items) {
        const back = reverse.items.find(r => r.objectKey === item.objectKey); assert.ok(back, item.objectKey)
        assert.equal(back.type, item.type === 'ADDED' ? 'REMOVED' : item.type === 'REMOVED' ? 'ADDED' : 'MODIFIED')
        for (const leaf of item.fields) {
          const swapped = back.fields.find(f => f.field === leaf.field); assert.ok(swapped, leaf.field)
          assert.equal(swapped.oldValue, leaf.newValue); assert.equal(swapped.newValue, leaf.oldValue)
        }
      }
    }
    if (expected.label.includes('rename')) {
      assert.ok(selected.items.some(i => i.fields.some(f => f.field === 'name' && f.oldValue !== f.newValue)))
      assert.ok(selected.items.every(i => !i.label.includes('matched/')))
    }
    if (expected.label === 'api-monitor-fields-order') {
      assert.ok(selected.items.some(i => i.fields.some(f => f.field === 'extractionRules.fields[0].target' && f.oldValue !== f.newValue)))
    }
  } catch (e) { result.pass = false; result.errors.push(e.message) }
  results.push(result)
}
const summary = { phase, pairs: results.length, passed: results.filter(r => r.pass).length,
  failed: results.filter(r => !r.pass).length, snapshotsUnchanged: phase === 'after', results }
await fs.writeFile(`${dir}/${phase}-summary.json`, JSON.stringify(summary, null, 2))
console.log(JSON.stringify({ ...summary, results: summary.results.filter(r => !r.pass).map(r => r.label) }))
assert.equal(summary.failed, phase === 'before' ? 26 : 0)
