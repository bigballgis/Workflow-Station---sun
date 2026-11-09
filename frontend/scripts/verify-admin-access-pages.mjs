/**
 * WP2 runtime check (platform access model §8.1): with Admin Center authorizing every endpoint, the pages of
 * the three apps still load their data. Visits each page and fails on any /api/ response that is 401, 403
 * or 5xx. Since WP9 AUDITOR reads every Admin page (D-43) and a Technical Lead every page of the capability table.
 * Needs users developer (SYS_ADMIN), an AUDITOR (AUDITOR_USER, default wp2_auditor), a Technical Lead
 * (TECHLEAD_USER, default wp9_tl_a) and wp2_dev (DEVELOPER) with password "password":
 *   node frontend/scripts/verify-admin-access-pages.mjs [sysadmin|auditor|techlead|portal|developer]
 * SKIP = the Admin frontend's own route permissions refused the page before any API call.
 * Screenshots: frontend/<app>/verification-screenshots/wp2-access-<who>-<page>.png
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaAdminPassword, loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const origin = 'http://localhost:3000'
const pass = process.env.LOGIN_PASS ?? 'password'

const ADMIN_PAGES = ['dashboard', 'user/list', 'organization', 'virtual-group', 'role', 'function-unit', 'audit',
  'user-portal', 'config', 'bi-management/dashboard-registry', 'bi-management/audience-assignment',
  'bi-management/data-view-assignment', 'bi-management/rbac-mapping', 'relation-tables/structure',
  'environment-variables', 'sla-policies', 'relation-tables/data', 'automation-pieces', 'automation-flows',
  'automation-runs', 'audit/admin-center', 'audit/user-portal', 'audit/ldap-sync', 'relation-tables/structure/er-diagram']

const RUNS = [
  { who: 'sysadmin', dir: 'admin-center', base: '/admin/', user: 'developer', login: loginViaAdminPassword,
    pages: ADMIN_PAGES, allowed: () => false },
  { who: 'auditor', dir: 'admin-center', base: '/admin/', user: process.env.AUDITOR_USER ?? 'wp2_auditor',
    login: loginViaAdminPassword, pages: ADMIN_PAGES, allowed: () => false },
  { who: 'techlead', dir: 'admin-center', base: '/admin/', user: process.env.TECHLEAD_USER ?? 'wp9_tl_a',
    login: loginViaAdminPassword, pages: ADMIN_PAGES, allowed: () => false },
  { who: 'portal', dir: 'user-portal', base: '/portal/', user: 'developer', login: loginViaPortalPassword,
    pages: ['dashboard', 'tasks', 'tasks/to-claim', 'tasks/completed', 'processes', 'my-applications', 'delegations',
      'permissions', 'notifications', 'profile', 'bi-dashboard', 'relation-tables', 'views'], allowed: () => false },
  { who: 'developer', dir: 'developer-workstation', base: '/dev/', user: process.env.DW_DEVELOPER_USER ?? 'wp2_dev', login: loginViaDwPassword,
    pages: ['function-units', 'profile'], allowed: () => false },
]

const browser = await chromium.launch()
let failures = 0
try {
  for (const run of RUNS.filter((r) => !process.argv[2] || r.who === process.argv[2])) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    const page = await context.newPage()
    const bad = []
    page.on('response', (res) => {
      const url = res.url()
      const status = res.status()
      if (url.includes('/api/') && (status === 401 || status === 403 || status >= 500) && !run.allowed(url, status)) {
        bad.push(`${status} ${res.request().method()} ${url.replace(origin, '')}`)
      }
    })
    await run.login(page, { user: run.user, pass })
    await page.goto(`${origin}${run.base}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(3000)
    bad.length = 0 // the login helpers probe the app before the session exists
    const outDir = resolve(here, '..', run.dir, 'verification-screenshots')
    mkdirSync(outDir, { recursive: true })
    for (const path of run.pages) {
      const before = bad.length
      await page.goto(`${origin}${run.base}${path}`, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(3500)
      const onLogin = page.url().includes('/login')
      // The Admin frontend's own route permissions (unchanged by WP2) send e.g. AUDITOR to /403 before any API call.
      const blockedByRoute = new URL(page.url()).pathname.endsWith('/403')
      await page.screenshot({ path: resolve(outDir, `wp2-access-${run.who}-${path.replace(/\//g, '_')}.png`) })
      const ok = !onLogin && bad.length === before
      if (!ok) failures++
      const verdict = !ok ? 'FAIL' : blockedByRoute ? 'SKIP' : 'PASS'
      console.log(`${verdict} [${run.who}] ${path}${onLogin ? ' (sent to login)' : ''}`
        + (blockedByRoute ? ' (page refused by the frontend route permission; no API call made)' : '')
        + (bad.length > before ? `\n    ${bad.slice(before).join('\n    ')}` : ''))
    }
    await context.close()
  }
} finally {
  await browser.close()
}
process.exit(failures ? 1 : 0)
