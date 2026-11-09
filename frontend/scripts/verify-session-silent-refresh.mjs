/**
 * AC-15 runtime check: with a short access token the three apps keep working after it expires,
 * and a revoked account is sent back to login without reloading the page.
 *
 * Start the backends with a short lifetime first, e.g.
 *   cd deploy/environments/dev && JWT_EXPIRATION=60000 docker compose -f docker-compose.dev.yml \
 *     --env-file .env up -d --no-deps workflow-engine admin-center user-portal developer-workstation
 * then: node frontend/scripts/verify-session-silent-refresh.mjs [waitSeconds=75] [mode=reload] [app=all]
 *
 * Modes, per app (log in, wait past the access-token lifetime, then):
 *   reload  — reload the page; require: not on /login, and a /auth/refresh answered 200.
 *   in-page — navigate inside the SPA without reloading to a page that always loads its data (the
 *             app's own API calls hit the expired token); require: not on /login, and a
 *             /auth/refresh answered 200.
 *   revoke  — mark the account INACTIVE during the wait, then navigate inside the SPA; require: the
 *             refresh was refused and the app is on /login. The account is set ACTIVE again at the end.
 */
import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaAdminPassword, loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const waitSeconds = Number(process.argv[2] ?? 75)
const mode = process.argv[3] ?? 'reload'
if (!['reload', 'in-page', 'revoke'].includes(mode)) throw new Error(`Unknown mode "${mode}"`)
const onlyApp = process.argv[4]
const user = process.env.LOGIN_USER ?? 'developer'
const pass = process.env.LOGIN_PASS ?? 'password'

const origin = 'http://localhost:3000'
const APPS = [
  { name: 'portal', dir: 'user-portal', path: '/portal/', inPage: ['/portal/tasks'],
    login: (p) => loginViaPortalPassword(p, { user, pass }) },
  { name: 'admin', dir: 'admin-center', path: '/admin/', inPage: ['/admin/organization'],
    login: (p) => loginViaAdminPassword(p, { user, pass }) },
  { name: 'dw', dir: 'developer-workstation', path: '/dev/', inPage: ['/dev/profile', '/dev/function-units'],
    login: (p) => loginViaDwPassword(p, { user, pass }) },
]

function setAccountStatus(status) {
  const sql = `UPDATE sys_users SET status = '${status}' WHERE username = '${user.replace(/'/g, "''")}'`
  execFileSync('docker', ['exec', '-i', 'platform-postgres-dev', 'psql', '-U', 'platform_dev',
    '-d', 'workflow_platform_dev', '-v', 'ON_ERROR_STOP=1', '-c', sql], { stdio: 'pipe' })
}

/** Client-side navigation: Vue Router picks up popstate, so route components load without a reload. */
async function navigateInPage(page, paths) {
  for (const path of paths) {
    // The app's own polling may already have hit the expired token and left for the login page;
    // pushState there would only rewrite the login page's URL.
    if (page.url().includes('/login')) return
    await page.evaluate((p) => {
      history.pushState({}, '', p)
      dispatchEvent(new PopStateEvent('popstate'))
    }, path)
    await page.waitForTimeout(4000)
  }
}

const browser = await chromium.launch()
let failed = 0
try {
  for (const app of APPS.filter((a) => !onlyApp || a.name === onlyApp)) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    const page = await context.newPage()
    const refreshStatuses = []
    page.on('response', (res) => {
      if (res.url().includes('/auth/refresh')) refreshStatuses.push(res.status())
    })
    await app.login(page)
    // The login helpers open the app before the session exists, so the app may already have
    // bounced to /login; enter it again now that cookies and the stored user are in place.
    await page.goto(`${origin}${app.path}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(4000)
    if (page.url().includes('/login')) {
      failed++
      console.log(`[${app.name}] SETUP FAIL: not signed in before the wait (url=${page.url()})`)
      await context.close()
      continue
    }
    refreshStatuses.length = 0
    if (mode === 'revoke') setAccountStatus('INACTIVE')
    try {
      console.log(`[${app.name}] ${mode}: in app at ${page.url()}, waiting ${waitSeconds}s for the access token to expire...`)
      await page.waitForTimeout(waitSeconds * 1000)
      if (mode === 'reload') {
        await page.reload({ waitUntil: 'domcontentloaded' })
        await page.waitForTimeout(6000)
      } else {
        await navigateInPage(page, app.inPage)
      }
      if (mode === 'revoke') {
        // The app leaves for the login page once its refresh is refused; give the redirect time to land.
        await page.waitForURL((u) => u.pathname.includes('/login'), { timeout: 15000 }).catch(() => {})
      }
    } finally {
      if (mode === 'revoke') setAccountStatus('ACTIVE')
    }
    const url = page.url()
    const outDir = resolve(here, '..', app.dir, 'verification-screenshots')
    mkdirSync(outDir, { recursive: true })
    const shot = resolve(outDir, `wp1-session-${mode}-${app.name}.png`)
    await page.screenshot({ path: shot, fullPage: false })
    const ok = mode === 'revoke'
      ? url.includes('/login') && refreshStatuses.length > 0 && !refreshStatuses.includes(200)
      : !url.includes('/login') && refreshStatuses.includes(200)
    if (!ok) failed++
    console.log(`[${app.name}] ${ok ? 'PASS' : 'FAIL'} url=${url} refreshStatuses=${JSON.stringify(refreshStatuses)} screenshot=${shot}`)
    await context.close()
  }
} finally {
  await browser.close()
}
process.exit(failed ? 1 : 0)
