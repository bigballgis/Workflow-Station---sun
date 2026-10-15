/**
 * Shared portal session for Playwright verification scripts (Kong edge localhost:3000).
 */
import { loginViaPortalPassword } from '../../scripts/playwright-login.mjs'

export const PORTAL_ORIGIN = (process.env.PORTAL_BASE || 'http://localhost:3000').replace(/\/$/, '')
export const PORTAL_API = `${PORTAL_ORIGIN}/api/portal`

/**
 * @param {import('playwright').Page} page
 * @param {import('playwright').BrowserContext} [ctx]
 */
export async function setupPortalSession(page, ctx, opts = {}) {
  await loginViaPortalPassword(page, {
    loginOrigin: PORTAL_ORIGIN,
    user: opts.user ?? 'developer',
    pass: opts.pass ?? 'password',
    buCode: opts.buCode ?? process.env.LOGIN_BU_CODE,
    roleCode: opts.roleCode ?? process.env.LOGIN_ROLE_CODE ?? 'HMDC_Index_Role',
  })

  const userJson = await page.evaluate(() => localStorage.getItem('ws_up_user'))
  const user = userJson ? JSON.parse(userJson) : null
  if (!user?.userId) {
    throw new Error('portal session missing ws_up_user after login')
  }

  if (ctx) {
    const workspaceBusinessUnitId =
      user.activeBusinessUnitId
      ?? process.env.LOGIN_WORKSPACE_BU_ID
      ?? '2ca743c1-2af5-4c44-866b-ae8e1ba60acb'
    const workspaceRoleId =
      user.activeRoleId
      ?? process.env.LOGIN_WORKSPACE_ROLE_ID
      ?? 'dbfc6328-3095-40f2-9e3a-efd4f55cba05'
    const loginRes = await ctx.request.post(`${PORTAL_API}/auth/login`, {
      data: {
        username: opts.user ?? 'developer',
        password: opts.pass ?? 'password',
        workspaceBusinessUnitId,
        workspaceRoleId,
      },
    })
    const body = await loginRes.json().catch(() => ({}))
    const token = body.accessToken ?? body.data?.accessToken
    if (token) {
      await ctx.setExtraHTTPHeaders({ Authorization: `Bearer ${token}` })
    }
  }

  return user
}

/**
 * @param {import('playwright').Page} page
 * @param {Record<string, unknown>} patch
 */
export async function patchStoredPortalUser(page, patch) {
  await page.evaluate((p) => {
    const raw = localStorage.getItem('ws_up_user')
    if (!raw) throw new Error('ws_up_user missing')
    const u = JSON.parse(raw)
    localStorage.setItem('ws_up_user', JSON.stringify({ ...u, ...p }))
  }, patch)
}

/**
 * Keep /auth/me aligned with local role-filter tests (developer seed is SYS_ADMIN).
 * @param {import('playwright').Page} page
 */
export async function installPortalMeStub(page) {
  /** @type {(u: Record<string, unknown>) => Record<string, unknown>} */
  let transform = (u) => u

  await page.route('**/api/portal/auth/me**', async (route) => {
    const upstream = await route.fetch()
    const user = await upstream.json().catch(() => ({}))
    const patched = transform(user && typeof user === 'object' ? user : {})
    await route.fulfill({
      status: upstream.status(),
      contentType: 'application/json',
      body: JSON.stringify(patched),
    })
  })

  return {
    /** @param {(u: Record<string, unknown>) => Record<string, unknown>} fn */
    setTransform(fn) {
      transform = fn
    },
    async apply(pageRef, patch) {
      transform = (u) => ({ ...u, ...patch })
      await patchStoredPortalUser(pageRef, patch)
    },
  }
}
