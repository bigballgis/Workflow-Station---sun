#!/usr/bin/env node
/**
 * View access control — Portal API checks per account + screenshots (platform access model §7.2, D-05).
 * Usage: node scripts/verify-view-access-control.mjs
 *
 * Preconditions (docs/view-access-control-test-guide.md):
 *   - Multi-Instance Subtask Demo (fu-20260422-23tfag) deployed and enabled in Admin Center;
 *   - deploy/init-scripts/91-view-access-test/02-view-access-test-access-model.sql applied after that deployment.
 *
 * Expected (rules: Meeting / People = 共享财务中心 + Department Manager, Participants unpaired, Attachment empty):
 *   developer        SYS_ADMIN, works as E2E_FINANCE / MANAGER -> exactly Meeting, People (no bypass, AC-25)
 *   view_allowed     E2E_FINANCE / MANAGER                     -> exactly Meeting, People
 *   view_wrong_bu    E2E_IT / MANAGER                          -> the unit is not in the Views menu
 *   view_wrong_role  E2E_FINANCE / VIEW_TEST_OTHER             -> the unit is not in the Views menu
 *   view_nofu        E2E_FINANCE / VIEW_TEST_NOFU (no access)  -> the unit is not in the Views menu
 */

import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = resolve(__dirname, '..')
const DW_OUT = join(FRONTEND_ROOT, 'developer-workstation', 'verification-screenshots')
const PORTAL_OUT = join(FRONTEND_ROOT, 'user-portal', 'verification-screenshots')
const ORIGIN = (process.env.ORIGIN ?? 'http://localhost:3000').replace(/\/$/, '')

const FU_CODE = 'fu-20260422-23tfag'
const FU_DW_ID = '50005'
const OPEN_VIEWS = ['Meeting', 'People']

const SCENARIOS = [
  // The login's workspace list names the business unit (no code), so E2E_FINANCE is picked by its name.
  { user: 'developer', buCode: '共享财务中心', roleCode: 'MANAGER', slug: 'sysadmin', views: OPEN_VIEWS },
  { user: 'view_allowed', slug: 'allowed', views: OPEN_VIEWS },
  { user: 'view_wrong_bu', slug: 'wrong-bu', views: null },
  { user: 'view_wrong_role', slug: 'wrong-role', views: null },
  { user: 'view_nofu', slug: 'nofu', views: null },
]

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function loadPlaywright() {
  try {
    return await import('playwright')
  } catch {
    console.error('Install playwright from frontend/: pnpm install && pnpm exec playwright install chromium')
    process.exit(1)
  }
}

async function portalGet(page, path, user) {
  const res = await page.request.get(`${ORIGIN}/api/portal${path}`)
  const text = await res.text()
  if (!res.ok()) {
    throw new Error(`${user}: GET ${path} HTTP ${res.status()} — ${text.slice(0, 200)}`)
  }
  const data = JSON.parse(text)?.data
  if (!Array.isArray(data)) {
    throw new Error(`${user}: GET ${path} unexpected payload ${text.slice(0, 200)}`)
  }
  return data
}

async function checkScenario(browser, s) {
  // One context per account: the session lives in cookies, nothing may carry over.
  const context = await browser.newContext({ viewport: { width: 1600, height: 1200 } })
  const page = await context.newPage()
  try {
    await loginViaPortalPassword(page, { user: s.user, pass: 'password', buCode: s.buCode, roleCode: s.roleCode })
    const menu = await portalGet(page, '/main-table-views/function-units', s.user)
    const entry = menu.find((x) => String(x.functionUnitCode ?? '').toLowerCase() === FU_CODE)
    if (s.views === null) {
      if (entry) {
        throw new Error(`${s.user}: expected ${FU_CODE} absent from the Views menu, got ${JSON.stringify(entry)}`)
      }
      console.log(`API OK ${s.user}: ${FU_CODE} not in the Views menu`)
    } else {
      if (!entry) {
        const codes = menu.map((x) => x.functionUnitCode)
        throw new Error(`${s.user}: expected ${FU_CODE} in the Views menu; menu=${JSON.stringify(codes)}`)
      }
      const views = await portalGet(page, `/main-table-views/function-units/${FU_CODE}/views`, s.user)
      const names = views.map((v) => v.viewName).sort()
      if (JSON.stringify(names) !== JSON.stringify([...s.views].sort()) || entry.viewCount !== s.views.length) {
        throw new Error(`${s.user}: expected views ${JSON.stringify(s.views)}, got ${JSON.stringify(names)} `
          + `(menu viewCount=${entry.viewCount})`)
      }
      console.log(`API OK ${s.user}: views ${names.join(', ')}`)
    }

    // Views page: the unit's views when it is open to the account, otherwise the menu without it.
    await page.goto(`${ORIGIN}/portal/views${s.views === null ? '' : `/${FU_CODE}`}`, { waitUntil: 'domcontentloaded' })
    await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {})
    await page.waitForTimeout(1500)
    const shot = join(PORTAL_OUT, `${datePrefix()}_portal-views-${s.slug}.png`)
    await page.screenshot({ path: shot })
    console.log('Portal screenshot:', shot)
  } finally {
    await context.close()
  }
}

/** The ids the Meeting view's access rules store (BU and Role ids). */
async function meetingAccessTargetIds(page) {
  const res = await page.request.get(`${ORIGIN}/api/v1/function-units/${FU_DW_ID}/main-table-views`)
  if (!res.ok()) throw new Error(`DW: GET main-table-views HTTP ${res.status()}`)
  const views = (await res.json())?.data ?? []
  const meeting = views.find((v) => v.viewName === 'Meeting')
  const ids = (meeting?.accessRules ?? []).map((r) => String(r.targetId))
  if (!ids.length) throw new Error('DW: the Meeting view has no access rules; apply the view access test seed first')
  return ids
}

/**
 * The BU / Role tags of the access panel show the raw ids until the option lists have loaded, then the names.
 * Wait for that instead of a fixed delay: both selects show tags, and none of them is one of the stored ids.
 */
export async function waitForAccessNames(page, targetIds, timeout = 15000) {
  await page.waitForFunction((ids) => {
    const selects = [...document.querySelectorAll('.access-control-section .access-select')]
    return selects.length === 2 && selects.every((select) => {
      const tags = [...select.querySelectorAll('.el-tag')]
        .map((tag) => tag.textContent.trim())
        .filter((text) => text && !/^\+\s*\d+$/.test(text))
      return tags.length > 0 && tags.every((text) => !ids.includes(text))
    })
  }, targetIds, { timeout })
}

async function main() {
  mkdirSync(DW_OUT, { recursive: true })
  mkdirSync(PORTAL_OUT, { recursive: true })

  const { chromium } = await loadPlaywright()
  const browser = await chromium.launch({ headless: true, channel: 'msedge' })

  // DW access panel of the demo unit's Meeting view (BU + Role, involved users only)
  const dwContext = await browser.newContext({ viewport: { width: 1600, height: 1200 } })
  const dwPage = await dwContext.newPage()
  await loginViaDwPassword(dwPage)
  await dwPage.goto(`${ORIGIN}/dev/function-units/${FU_DW_ID}`, { waitUntil: 'networkidle' })
  await dwPage.locator('#tab-view-design, [id="tab-view-design"]').click({ timeout: 15000 })
  await dwPage.locator('.view-groups .view-name-text', { hasText: /^Meeting$/ }).click({ timeout: 15000 })
  await dwPage.locator('.access-control-section').waitFor({ state: 'visible', timeout: 15000 })
  await waitForAccessNames(dwPage, await meetingAccessTargetIds(dwPage))
  const dwPath = join(DW_OUT, `${datePrefix()}_view-design-access-control-mi-demo.png`)
  await dwPage.locator('.access-control-section').screenshot({ path: dwPath })
  console.log('DW screenshot:', dwPath)
  await dwContext.close()

  for (const s of SCENARIOS) {
    await checkScenario(browser, s)
  }

  await browser.close()
  console.log('All view-access scenarios passed.')
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  main().catch((err) => {
    console.error(err)
    process.exit(1)
  })
}
