/**
 * Portal All Requests (audit): grant a test role if needed, then screenshot
 * the sidebar entry, the list toolbar search, and a keyword result.
 *
 * Usage (from frontend/):
 *   node scripts/verify-audit-list-search.mjs
 *
 * Output: user-portal/verification-screenshots/{date}_audit-*.png
 */
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'

const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const OUT = join(dirname(fileURLToPath(import.meta.url)), '../user-portal/verification-screenshots')
const DATE = new Date().toISOString().slice(0, 10)
const ROLE_ID = process.env.AUDIT_ROLE_ID ?? 'dbfc6328-3095-40f2-9e3a-efd4f55cba05'
const ROLE_CODE = process.env.AUDIT_ROLE_CODE ?? 'HMDC_Index_Role'
const BU_CODE = process.env.AUDIT_BU_CODE ?? 'hase-hmdc'

mkdirSync(OUT, { recursive: true })

function check(label, ok, detail) {
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) throw new Error(`${label}${detail ? `: ${detail}` : ''}`)
}

async function shot(page, slug, opts = {}) {
  const path = join(OUT, `${DATE}_${slug}.png`)
  if (opts.selector) {
    const el = page.locator(opts.selector).first()
    await el.waitFor({ timeout: 15000 })
    await el.screenshot({ path })
  } else {
    await page.screenshot({ path, fullPage: Boolean(opts.fullPage) })
  }
  console.log(`[SHOT] ${path}`)
  return path
}

async function unwrap(res) {
  const body = await res.json().catch(() => ({}))
  return body.data ?? body.content ?? body
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
let failures = 0

try {
  await loginViaPortalPassword(page, { buCode: BU_CODE, roleCode: ROLE_CODE })
  const portalFuRes = await page.request.get(`${ORIGIN}/api/portal/main-table-views/function-units`)
  const portalFuBody = await portalFuRes.json().catch(() => ({}))
  const portalFus = portalFuBody.data ?? portalFuBody
  console.log(`[portal-fu] HTTP ${portalFuRes.status()} count=${Array.isArray(portalFus) ? portalFus.length : '?'}`)
  const portalCodes = new Set(
    (Array.isArray(portalFus) ? portalFus : [])
      .map((u) => String(u.functionUnitCode || u.code || ''))
      .filter(Boolean),
  )
  console.log(`[portal-fu] codes=${[...portalCodes].join(',')}`)

  const adminPage = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  await loginViaAdminPassword(adminPage)
  const listRes = await adminPage.request.get(`${ORIGIN}/api/v1/admin/function-units?size=50`)
  check('admin function-unit list HTTP', listRes.ok(), `HTTP ${listRes.status()}`)
  const listed = await unwrap(listRes)
  const units = listed.content ?? listed.records ?? listed
  check('admin has at least one function unit', Array.isArray(units) && units.length > 0, `type=${typeof listed}`)
  const fu = (Array.isArray(units) ? units : []).find((u) => portalCodes.has(String(u.code || u.functionUnitCode)))
    ?? units[0]
  const fuId = String(fu.id)
  const fuCode = String(fu.code || fu.functionUnitCode)
  const fuName = String(fu.name || fu.functionUnitName || fuCode)
  console.log(`[fu] id=${fuId} code=${fuCode} name=${fuName} matchedPortal=${portalCodes.has(fuCode)}`)

  const versionRes = await adminPage.request.get(
    `${ORIGIN}/api/v1/admin/function-units/code/${encodeURIComponent(fuCode)}/versions`,
  )
  const versionPayload = versionRes.ok() ? await unwrap(versionRes) : []
  const versionList = Array.isArray(versionPayload)
    ? versionPayload
    : (versionPayload.content ?? versionPayload.records ?? [])
  const idsToGrant = new Set([fuId, ...versionList.map((v) => String(v.id)).filter(Boolean)])
  console.log(`[grant] candidates=${[...idsToGrant].join(',')}`)
  for (const id of idsToGrant) {
    const existingRes = await adminPage.request.get(`${ORIGIN}/api/v1/admin/function-units/${id}/audit-access`)
    if (!existingRes.ok()) {
      throw new Error(
        `GET audit-access HTTP ${existingRes.status()} id=${id} — apply 00-schema/68-form-scene-and-fu-audit-access.sql`,
      )
    }
    const grants = (await unwrap(existingRes)) || []
    const already = Array.isArray(grants) && grants.some((g) => g.targetId === ROLE_ID || g.roleId === ROLE_ID)
    if (already) {
      console.log(`[grant] already present on ${id}`)
      continue
    }
    const addRes = await adminPage.request.post(`${ORIGIN}/api/v1/admin/function-units/${id}/audit-access`, {
      data: { roleId: ROLE_ID, roleName: ROLE_CODE },
    })
    check(
      `add audit grant on ${id}`,
      addRes.ok() || addRes.status() === 201,
      `HTTP ${addRes.status()} ${await addRes.text()}`,
    )
  }
  await adminPage.close()

  const scopeRes = await page.request.get(`${ORIGIN}/api/portal/processes/audit-function-units`)
  const scopeBody = await scopeRes.json().catch(() => ({}))
  const auditable = scopeBody.data ?? scopeBody
  console.log(`[scope] HTTP ${scopeRes.status()} count=${Array.isArray(auditable) ? auditable.length : '?'} body=${JSON.stringify(scopeBody).slice(0, 500)}`)
  check('audit-function-units returns a grant', Array.isArray(auditable) && auditable.length > 0)

  await page.goto(`${ORIGIN}/portal/dashboard`, { waitUntil: 'domcontentloaded' })
  const auditMenu = page.locator('.portal-menu').getByText(/All Requests|全部申请|全部申請/)
  await auditMenu.first().waitFor({ timeout: 40000 })
  await auditMenu.first().click()
  await page.locator('.el-menu-item', { hasText: 'ATM' }).first().waitFor({ timeout: 10000 }).catch(() => {})
  await shot(page, 'audit-sidebar-menu')
  check('audit submenu is visible in the left menu', await auditMenu.count().then((n) => n > 0))

  const granted = auditable.find((u) => u.functionUnitCode === fuCode) ?? auditable[0]
  const openCode = granted?.functionUnitCode || fuCode
  await page.goto(`${ORIGIN}/portal/audit/${encodeURIComponent(openCode)}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.audit-page', { timeout: 20000 })
  await page.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 20000 }).catch(() => {})
  await auditMenu.first().waitFor({ timeout: 40000 }).catch(() => {})

  const search = page.locator('[data-test="audit-search"]')
  await search.waitFor({ timeout: 10000 })
  await shot(page, 'audit-list-search-idle')
  check('search box is above the grid', await search.isVisible())

  await search.fill('ATM-DC-PW-000013')
  await search.press('Enter')
  await page.waitForTimeout(1500)
  await page.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 20000 }).catch(() => {})
  await shot(page, 'audit-list-search-keyword')
  check('search input keeps the typed keyword', (await search.inputValue()) === 'ATM-DC-PW-000013')

  await search.fill('Developer Tester')
  await search.press('Enter')
  await page.waitForTimeout(1500)
  await page.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 20000 }).catch(() => {})
  await shot(page, 'audit-list-search-display-name')
  check('display-name search keeps the typed keyword', (await search.inputValue()) === 'Developer Tester')
  const nameHit = page.locator('.list-data-grid').getByText('Developer Tester')
  check('display-name search hits a painted cell', await nameHit.count().then((n) => n > 0))

  await search.fill('Running')
  await search.press('Enter')
  await page.waitForTimeout(1500)
  await page.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 20000 }).catch(() => {})
  await shot(page, 'audit-list-search-status-label')
  check('status-label search keeps the typed keyword', (await search.inputValue()) === 'Running')
  const runningHit = page.locator('.list-data-grid').getByText('Running')
  check('typed Running maps to stored RUNNING rows', await runningHit.count().then((n) => n > 0))
} catch (err) {
  failures++
  console.error(err)
  await shot(page, 'audit-list-search-error').catch(() => {})
} finally {
  await browser.close()
}

if (failures > 0) process.exit(1)
