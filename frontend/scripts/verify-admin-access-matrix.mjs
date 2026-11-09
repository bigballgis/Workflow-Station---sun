/**
 * WP2 runtime check (platform access model §8.1): Admin Center answers each kind of caller as declared.
 * Needs the dev stack on http://localhost:3000, the users developer (SYS_ADMIN), wp2_auditor (AUDITOR) and
 * wp2_dev (DEVELOPER, DW only; override with AUDITOR_USER / DW_DEVELOPER_USER, and DW_DEVELOPER_ID for its user
 * id) with password "password", and
 * SERVICE_INTERNAL_TOKEN from the dev .env:
 *   SERVICE_INTERNAL_TOKEN=... node frontend/scripts/verify-admin-access-matrix.mjs
 * Only read endpoints and requests that fail validation before writing anything are used for allowed writes.
 */
const origin = process.env.ORIGIN ?? 'http://localhost:3000'
const admin = `${origin}/api/v1/admin`
const serviceToken = process.env.SERVICE_INTERNAL_TOKEN
if (!serviceToken) throw new Error('SERVICE_INTERNAL_TOKEN is required')

async function login(path, username) {
  const res = await fetch(`${origin}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: process.env.LOGIN_PASS ?? 'password' }),
  })
  if (res.status !== 200) throw new Error(`login ${username} at ${path} -> ${res.status}`)
  return res.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ')
}

async function call(method, path, { cookie, headers = {}, body } = {}) {
  const res = await fetch(`${admin}${path}`, {
    method,
    headers: {
      ...(cookie ? { Cookie: cookie } : {}),
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  return res.status
}

const results = []
async function expect(label, method, path, opts, check) {
  const status = await call(method, path, opts)
  const ok = check(status)
  results.push(ok)
  console.log(`${ok ? 'PASS' : 'FAIL'} ${status} ${label}: ${method} ${path}`)
}
const is = (code) => (s) => s === code
const passedAuthz = (s) => s !== 401 && s !== 403

const sysAdmin = await login('/api/v1/admin/auth/login', 'developer')
const auditor = await login('/api/v1/admin/auth/login', process.env.AUDITOR_USER ?? 'wp2_auditor')
const dwDeveloper = await login('/api/v1/auth/login', process.env.DW_DEVELOPER_USER ?? 'wp2_dev')
const service = { 'X-Service-Token': serviceToken }

// Anonymous and forged service calls
await expect('anonymous read', 'GET', '/business-units', {}, is(401))
await expect('anonymous user list', 'GET', '/users', {}, is(401))
await expect('anonymous vault password', 'GET', '/internal/environment-variables/vault-password?varKey=x', {}, is(401))
await expect('forged service token', 'GET', '/users', { headers: { 'X-Service-Token': 'guess' } }, is(401))

// SYS_ADMIN
await expect('sysadmin read', 'GET', '/business-units', { cookie: sysAdmin }, is(200))
await expect('sysadmin query', 'POST', '/users/query', { cookie: sysAdmin, body: { page: 0, size: 5 } }, is(200))
await expect('sysadmin write reaches validation', 'POST', '/business-units', { cookie: sysAdmin, body: {} }, passedAuthz)
await expect('sysadmin vault password', 'GET', '/internal/environment-variables/vault-password?varKey=x',
  { cookie: sysAdmin }, is(403))

// AUDITOR: reads yes, writes no
await expect('auditor read', 'GET', '/business-units', { cookie: auditor }, is(200))
await expect('auditor POST query', 'POST', '/users/query', { cookie: auditor, body: { page: 0, size: 5 } }, is(200))
await expect('auditor SLA query', 'POST', '/sla-policies/query', { cookie: auditor, body: { page: 0, size: 5 } }, is(200))
await expect('auditor create BU', 'POST', '/business-units', { cookie: auditor, body: {} }, is(403))
await expect('auditor delete user', 'DELETE', '/users/no-such-user', { cookie: auditor }, is(403))
await expect('auditor BI assignment write', 'POST', '/bi/assignments', { cookie: auditor, body: {} }, is(403))
await expect('auditor config write', 'PUT', '/configs/no-such-key', { cookie: auditor, body: {} }, is(403))

// DEVELOPER via the DW session: workstation lookups only
await expect('developer BU tree', 'GET', '/business-units/tree', { cookie: dwDeveloper }, is(200))
await expect('developer role catalog', 'GET', '/task-assignment/roles/bu-bounded', { cookie: dwDeveloper }, is(200))
await expect('developer user search', 'GET', '/users?keyword=dev&size=5', { cookie: dwDeveloper }, is(200))
await expect('developer admin read', 'GET', '/configs', { cookie: dwDeveloper }, is(403))
await expect('developer admin write', 'POST', '/business-units', { cookie: dwDeveloper, body: {} }, is(403))

// First-party service calls
await expect('service internal list', 'GET', '/internal/environment-variables?kind=TEXT', { headers: service }, is(200))
await expect('service listed read', 'GET', '/function-units/deployed/latest', { headers: service }, is(200))
await expect('service listed write reaches validation', 'POST', '/force-unclaim/evaluate',
  { headers: service, body: {} }, passedAuthz)
await expect('service unlisted write', 'PUT', '/configs/no-such-key', { headers: service, body: {} }, is(403))
await expect('deploy as developer (db roles)', 'POST', '/function-units/no-such-id/validate',
  { headers: { ...service, 'X-User-Id': process.env.DW_DEVELOPER_ID ?? 'wp2-dev' } }, passedAuthz)
await expect('deploy as business user (db roles)', 'POST', '/function-units/no-such-id/validate',
  { headers: { ...service, 'X-User-Id': 'user-e2e-lina' } }, is(403))
await expect('deploy without user', 'POST', '/function-units/no-such-id/validate', { headers: service }, is(403))

const failed = results.filter((ok) => !ok).length
console.log(`${results.length - failed}/${results.length} passed`)
process.exit(failed ? 1 : 0)
