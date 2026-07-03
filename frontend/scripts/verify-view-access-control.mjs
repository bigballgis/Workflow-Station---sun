#!/usr/bin/env node
/**
 * View access control — multi-scenario Portal API smoke + optional screenshots.
 * Usage: node scripts/verify-view-access-control.mjs
 *
 * Scenarios (see docs/view-access-control-test-guide.md):
 *   view_admin   → MCY menu + 3 views
 *   view_allowed → MCY menu + 2 views (no Attachment)
 *   view_wrong_bu → empty menu
 */

import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { loginViaUnifiedSso, loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = resolve(__dirname, '..')
const DW_OUT = join(FRONTEND_ROOT, 'developer-workstation', 'verification-screenshots')
const PORTAL_OUT = join(FRONTEND_ROOT, 'user-portal', 'verification-screenshots')

const MCY_CODE = 'fu-20260505-thwmut'
const MCY_FU_ID = '48'

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function loadPlaywright() {
  try {
    return await import('playwright')
  } catch {
    console.error('Install playwright from frontend/: npm install && npx playwright install chromium')
    process.exit(1)
  }
}

function findMcyEntry(menu) {
  return menu.find(
    (x) => String(x.functionUnitCode || x.code || '').toLowerCase() === MCY_CODE.toLowerCase(),
  )
}

async function portalMenuViaApi(page, username) {
  const { userId } = await loginViaPortalPassword(page, { user: username, password: 'password' })
  const res = await page.request.get('http://localhost:3000/api/portal/main-table-views/function-units', {
    headers: { 'X-User-Id': String(userId) },
  })
  if (!res.ok()) {
    const text = await res.text()
    throw new Error(`${username}: menu API HTTP ${res.status()} — ${text.slice(0, 200)}`)
  }
  const body = await res.json()
  const menu = body?.data ?? []
  if (!Array.isArray(menu)) {
    throw new Error(`${username}: unexpected menu payload ${JSON.stringify(body)}`)
  }
  return menu
}

async function main() {
  mkdirSync(DW_OUT, { recursive: true })
  mkdirSync(PORTAL_OUT, { recursive: true })

  const { chromium } = await loadPlaywright()
  const browser = await chromium.launch({ headless: true, channel: 'msedge' })
  const context = await browser.newContext({ viewport: { width: 1600, height: 1200 } })
  const page = await context.newPage()

  // DW access panel
  await loginViaUnifiedSso(page, 'dw')
  await page.goto(`http://localhost:3000/dev/function-units/${MCY_FU_ID}`, { waitUntil: 'networkidle' })
  await page.locator('#tab-view-design, [id="tab-view-design"]').click({ timeout: 15000 })
  await page.locator('.access-control-section').waitFor({ state: 'visible', timeout: 15000 })
  const dwPath = join(DW_OUT, `${datePrefix()}_view-design-access-control-mcy.png`)
  await page.locator('.access-control-section').screenshot({ path: dwPath })
  console.log('DW screenshot:', dwPath)

  const scenarios = [
    { user: 'view_admin', slug: 'admin', expectFu: true, minViews: 3 },
    { user: 'view_allowed', slug: 'allowed', expectFu: true, minViews: 2 },
    { user: 'view_wrong_bu', slug: 'wrong-bu', expectFu: false, minViews: 0 },
  ]

  for (const s of scenarios) {
    const menu = await portalMenuViaApi(page, s.user)
    const mcy = findMcyEntry(menu)
    if (s.expectFu) {
      if (!mcy || (mcy.viewCount ?? 0) < s.minViews) {
        const summary = menu.map((x) => ({ code: x.functionUnitCode, viewCount: x.viewCount }))
        throw new Error(
          `${s.user}: expected MCY viewCount>=${s.minViews}, got ${JSON.stringify(mcy ?? null)}; menu=${JSON.stringify(summary)}`,
        )
      }
    } else if (mcy) {
      throw new Error(`${s.user}: expected no MCY in menu, got ${JSON.stringify(mcy)}`)
    }
    console.log(`API OK ${s.user}:`, mcy ? `MCY viewCount=${mcy.viewCount}` : 'no MCY')

    await page.goto(`http://localhost:3000/portal/dashboard`, { waitUntil: 'networkidle' })
    const shot = join(PORTAL_OUT, `${datePrefix()}_portal-views-${s.slug}.png`)
    await page.locator('.portal-sidebar, .sidebar-container, .el-aside').first().screenshot({ path: shot })
      .catch(async () => page.screenshot({ path: shot }))
    console.log('Portal screenshot:', shot)
  }

  await browser.close()
  console.log('All view-access scenarios passed.')
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
