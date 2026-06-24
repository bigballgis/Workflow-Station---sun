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

export { APP_PRESETS }
