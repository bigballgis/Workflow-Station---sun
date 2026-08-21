/**
 * Unified SSO login for edge-frontend (localhost:3000).
 * Used by verify-page-screenshot.mjs — not for production E2E suites.
 */

const APP_PRESETS = {
  portal: {
    basePath: '/portal',
    clientId: 'portal',
    redirectUri: 'http://localhost:3000/portal/sso/callback',
  },
  admin: {
    basePath: '/admin',
    clientId: 'admin',
    redirectUri: 'http://localhost:3000/admin/sso/callback',
  },
  dw: {
    basePath: '/dev',
    clientId: 'developer-workstation',
    redirectUri: 'http://localhost:3000/dev/sso/callback',
  },
}

/**
 * @param {import('playwright').Page} page
 * @param {'portal'|'admin'|'dw'} app
 * @param {{ user?: string; pass?: string; loginOrigin?: string }} opts
 */
export async function loginViaUnifiedSso(page, app, opts = {}) {
  const preset = APP_PRESETS[app]
  if (!preset) throw new Error(`Unknown app "${app}". Use: portal | admin | dw`)

  const user = opts.user ?? process.env.LOGIN_USER ?? 'developer'
  const pass = opts.pass ?? process.env.LOGIN_PASS ?? 'password'
  const origin = (opts.loginOrigin ?? 'http://localhost:3000').replace(/\/$/, '')
  const state = `verify-${Date.now()}`
  const loginUrl =
    `${origin}/login/?client_id=${encodeURIComponent(preset.clientId)}` +
    `&redirect_uri=${encodeURIComponent(preset.redirectUri)}` +
    `&state=${encodeURIComponent(state)}`

  await page.goto(loginUrl, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)

  const userInput = page.locator('input[autocomplete="username"]')
  if ((await userInput.count()) === 0) {
    throw new Error(`Login form not found at ${loginUrl}`)
  }

  await userInput.fill(user)
  await page.locator('input[autocomplete="current-password"]').fill(pass)
  await Promise.all([
    page.waitForURL(
      (url) => url.pathname.includes('/sso/callback') || url.pathname.startsWith(preset.basePath),
      { timeout: 30000 },
    ).catch(() => {}),
    page.locator('button[type="submit"]').click(),
  ])
  await page.waitForURL(
    (url) => !url.pathname.includes('/login'),
    { timeout: 15000 },
  ).catch(() => {})
  await page.waitForTimeout(1500)

  const url = page.url()
  if (url.includes('/login')) {
    const errText = await page.locator('.error-message, .el-message--error').first().textContent().catch(() => '')
    throw new Error(
      `SSO login failed — still on login page. Check LOGIN_USER / LOGIN_PASS and Kong/admin-center health.${errText ? ` (${errText.trim()})` : ''}`,
    )
  }
  return url
}

/**
 * Direct portal username/password login via POST /api/portal/auth/login.
 * Sets httpOnly session cookies + ws_up_user in localStorage (no unified SSO form).
 *
 * PREFER THIS over loginViaUnifiedSso for portal screenshots: the unified SSO form is
 * unreliable headless (it reaches /sso/callback and then bounces back to /login without
 * establishing a session).
 *
 * Default: developer / password (see deploy/init-scripts/01-admin/05-e2e-test-users-and-business-units.sql)
 *
 * Multi-UBR users must pick a workspace before a session is issued. By default the first
 * returned context is used; pass `buCode` / `roleCode` to target a specific one (e.g.
 * `{ buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' }`) so a screenshot lands in the
 * same workspace the reporter saw.
 */
export async function loginViaPortalPassword(page, opts = {}) {
  const user = opts.user ?? process.env.LOGIN_USER ?? 'developer'
  const pass = opts.pass ?? process.env.LOGIN_PASS ?? 'password'
  const origin = (opts.loginOrigin ?? 'http://localhost:3000').replace(/\/$/, '')
  const wantBu = opts.buCode ?? process.env.LOGIN_BU_CODE
  const wantRole = opts.roleCode ?? process.env.LOGIN_ROLE_CODE

  // Must land on the real origin before touching localStorage — on about:blank the
  // browser denies storage access, which surfaces later as a confusing SecurityError.
  // `commit` is enough (and far more reliable than domcontentloaded, which can hang on
  // the portal shell's long-lived connections); the origin is set as soon as it fires.
  await page.goto(`${origin}/portal/`, { waitUntil: 'commit' })
  if (!page.url().startsWith(origin)) {
    throw new Error(`Portal did not load at ${origin}/portal/ (got ${page.url()})`)
  }

  let res = await page.request.post(`${origin}/api/portal/auth/login`, {
    data: { username: user, password: pass },
  })
  let body = await res.json().catch(() => ({}))
  // FALLBACK(ux): multi-BU users must pick workspace before session is issued
  if (body.loginErrorCode === 'WORKSPACE_CONTEXT_REQUIRED' && body.workspaceContexts?.[0]) {
    const match = (wantBu || wantRole)
      ? body.workspaceContexts.find(c =>
          (!wantBu || c.businessUnitCode === wantBu || c.businessUnitName === wantBu)
          && (!wantRole || c.roleCode === wantRole || c.roleName === wantRole))
      : undefined
    if ((wantBu || wantRole) && !match) {
      const available = body.workspaceContexts
        .map(c => `${c.businessUnitCode ?? c.businessUnitName}/${c.roleCode ?? c.roleName}`)
        .join(', ')
      throw new Error(
        `No workspace matched buCode=${wantBu ?? '*'} roleCode=${wantRole ?? '*'}. Available: ${available}`,
      )
    }
    const c = match ?? body.workspaceContexts[0]
    res = await page.request.post(`${origin}/api/portal/auth/login`, {
      data: {
        username: user,
        password: pass,
        workspaceBusinessUnitId: c.businessUnitId,
        workspaceRoleId: c.roleId,
      },
    })
    body = await res.json().catch(() => ({}))
  }
  if (!res.ok() && body.loginErrorCode !== 'WORKSPACE_CONTEXT_REQUIRED') {
    throw new Error(`Portal password login failed: ${body.message || `HTTP ${res.status()}`} (user=${user})`)
  }
  const u = body.user || body.data?.user
  if (!u?.userId) {
    throw new Error('Portal password login failed: response missing user')
  }

  // /portal/ may bounce to /login/ after commit; wait so evaluate is not racing a navigation.
  await page.waitForLoadState('domcontentloaded').catch(() => {})
  await page.waitForTimeout(300)

  await page.evaluate((userInfo) => {
    localStorage.setItem('ws_up_user', JSON.stringify(userInfo))
    localStorage.setItem('ws_up_user_id', String(userInfo.userId))
  }, u)

  console.log(`[login] portal password ${u.username} (${u.userId}) mode=${u.portalAccessMode ?? '?'}`)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1500)
  return { userId: u.userId, username: u.username, portalAccessMode: u.portalAccessMode }
}

/**
 * Direct Developer Workstation username/password login via POST /api/v1/auth/login.
 *
 * PREFER THIS over loginViaUnifiedSso for DW screenshots — the unified SSO form is
 * unreliable headless (reaches /dev/sso/callback and bounces back to /login).
 * Tokens ride in httpOnly cookies; only the user record goes to localStorage.
 *
 * Default: developer / password.
 */
export async function loginViaDwPassword(page, opts = {}) {
  const user = opts.user ?? process.env.LOGIN_USER ?? 'developer'
  const pass = opts.pass ?? process.env.LOGIN_PASS ?? 'password'
  const origin = (opts.loginOrigin ?? 'http://localhost:3000').replace(/\/$/, '')

  // Land on the real origin first — localStorage is denied on about:blank.
  await page.goto(`${origin}/dev/`, { waitUntil: 'commit' })
  if (!page.url().startsWith(origin)) {
    throw new Error(`DW did not load at ${origin}/dev/ (got ${page.url()})`)
  }

  const res = await page.request.post(`${origin}/api/v1/auth/login`, {
    data: { username: user, password: pass },
  })
  const body = await res.json().catch(() => ({}))
  if (!res.ok()) {
    throw new Error(`DW password login failed: ${body.message || `HTTP ${res.status()}`} (user=${user})`)
  }
  const u = body.user ?? body.data?.user
  if (!u?.userId) throw new Error('DW password login failed: response missing user')

  // /dev/ may bounce to /login/ after commit; wait so evaluate is not racing a navigation.
  await page.waitForLoadState('domcontentloaded').catch(() => {})
  await page.waitForTimeout(300)

  await page.evaluate((userInfo) => {
    localStorage.setItem('ws_dw_user', JSON.stringify(userInfo))
    localStorage.setItem('ws_dw_user_id', String(userInfo.userId))
  }, u)

  console.log(`[login] dw password ${u.username} (${u.userId})`)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1500)
  return { userId: u.userId, username: u.username }
}

/**
 * Direct Admin Center username/password login via POST /api/v1/admin/auth/login.
 * Prefer this over loginViaUnifiedSso for admin screenshots (SSO form is unreliable headless).
 * Default: admin / admin123 (seed in 01-create-admin-only.sql; or LOGIN_USER / LOGIN_PASS).
 */
export async function loginViaAdminPassword(page, opts = {}) {
  const user = opts.user ?? process.env.LOGIN_USER ?? 'admin'
  const pass = opts.pass ?? process.env.LOGIN_PASS ?? 'admin123'
  const origin = (opts.loginOrigin ?? 'http://localhost:3000').replace(/\/$/, '')

  await page.goto(`${origin}/admin/`, { waitUntil: 'commit' })
  if (!page.url().startsWith(origin)) {
    throw new Error(`Admin did not load at ${origin}/admin/ (got ${page.url()})`)
  }

  const res = await page.request.post(`${origin}/api/v1/admin/auth/login`, {
    data: { username: user, password: pass },
  })
  const body = await res.json().catch(() => ({}))
  if (!res.ok()) {
    throw new Error(`Admin password login failed: ${body.error || body.message || `HTTP ${res.status()}`} (user=${user})`)
  }
  const u = body.user ?? body.data?.user
  if (!u?.userId) throw new Error('Admin password login failed: response missing user')

  await page.evaluate((userInfo) => {
    localStorage.setItem('ws_ac_user', JSON.stringify(userInfo))
    localStorage.setItem('ws_ac_user_id', String(userInfo.userId))
    if (userInfo.username) localStorage.setItem('ws_ac_username', String(userInfo.username))
  }, u)

  console.log(`[login] admin password ${u.username} (${u.userId})`)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1500)
  return { userId: u.userId, username: u.username }
}

export { APP_PRESETS }
