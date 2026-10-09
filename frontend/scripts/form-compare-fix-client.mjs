import assert from 'node:assert/strict'
import fs from 'node:fs/promises'

export async function formCompareClient() {
  const dir = 'docs/test-evidence/form-compare-fixes-2026-10-08'
  await fs.mkdir(dir, { recursive: true })
  assert.ok(process.env.LOGIN_USER && process.env.LOGIN_PASS)
  const login = await fetch('http://localhost:3000/api/v1/auth/login', { method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: process.env.LOGIN_USER, password: process.env.LOGIN_PASS }) })
  assert.ok(login.ok)
  const auth = await login.json(); const user = auth.user ?? auth.data?.user
  const cookie = login.headers.getSetCookie().map(c => c.split(';')[0]).join('; ')
  const evidence = (name, value) => fs.writeFile(`${dir}/${name}.json`, JSON.stringify(value, null, 2))
  async function request(method, path, data, raw = false) {
    assert.ok((path === '' || path.startsWith('/')) && !path.includes('..'))
    const started = performance.now()
    const response = await fetch(`http://localhost:3000/api/v1/function-units${path}`, { method,
      headers: { Cookie: cookie, 'X-User-Id': String(user.userId), 'Content-Type': 'application/json' },
      ...(data === undefined ? {} : { body: JSON.stringify(data) }) })
    const result = { status: response.status, body: await response.json(), durationMs: Math.round(performance.now() - started) }
    if (raw) return result
    assert.equal(result.status, 200, `${method} ${path}: ${result.body.error?.code ?? result.body.message}`)
    assert.equal(result.body.success, true); return result.body.data
  }
  async function compare(fu, a, b, label) {
    const results = []
    for (const [from, to, suffix] of [[a, b, ''], [b, a, '-reverse']]) {
      const result = await request('GET', `/${fu}/versions/compare-v2?versionId1=${from}&versionId2=${to}`, undefined, true)
      await evidence(`${label}${suffix}`, result); assert.equal(result.status, 200); results.push(result.body.data)
    }
    verifyReverse(...results)
    return results[0]
  }
  return { dir, evidence, request, compare }
}

function verifyReverse(forward, reverse) {
  for (const f of forward.modules) {
    const r = reverse.modules.find(m => m.key === f.key)
    assert.deepEqual(r.semantic.counts, { added: f.semantic.counts.removed,
      modified: f.semantic.counts.modified, removed: f.semantic.counts.added })
    for (const item of f.semantic.items) {
      const back = r.semantic.items.find(i => i.objectKey === item.objectKey); assert.ok(back)
      for (const v of item.fields) {
        const swap = back.fields.find(x => x.field === v.field); assert.ok(swap)
        assert.equal(swap.oldValue, v.newValue); assert.equal(swap.newValue, v.oldValue)
      }
    }
  }
}
