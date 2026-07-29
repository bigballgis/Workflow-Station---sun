#!/usr/bin/env node
/**
 * Verify: switching the "Sub-table ID" select in the Multi-Instance Sub-Task
 * Config panel updates the disabled "Sub-table name" field below it.
 *
 * Fix under test: handleSubTableChange now looks up the sub-table with a loose
 * String() comparison (useUserTaskMultiInstance.ts), so a number/string id
 * drift between the el-select v-model and the loaded option value no longer
 * makes the lookup miss and leave the name field stale.
 *
 * Usage (from frontend/):
 *   node scripts/verify-mi-subtable-name-sync.mjs
 *   FU_ID=123 node scripts/verify-mi-subtable-name-sync.mjs   # skip auto-discovery
 */

import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = resolve(__dirname, '..', 'developer-workstation', 'verification-screenshots')
const DEMO_NAME = process.env.FU_NAME || 'Multi-Instance Subtask Demo'
const ORIGIN = process.env.DW_ORIGIN || 'http://localhost:3000'
const LOGIN_USER = process.env.LOGIN_USER || 'developer'
const LOGIN_PASS = process.env.LOGIN_PASS || 'password'

/**
 * Local non-SSO login: POST /api/v1/auth/login sets the httpOnly session
 * cookies on the page context and returns the user profile, which the DW app
 * normally persists to localStorage['ws_dw_user']. We replicate that so the
 * SPA boots authenticated without going through the unified SSO flow.
 */
async function loginLocal(page) {
  await page.goto(`${ORIGIN}/dev/`, { waitUntil: 'domcontentloaded' }).catch(() => {})
  const result = await page.evaluate(
    async ({ user, pass }) => {
      const res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: user, password: pass }),
      })
      if (!res.ok) return { error: `login ${res.status}` }
      const body = await res.json()
      const u = body.user || body.data?.user || body.data || {}
      try {
        localStorage.setItem('ws_dw_user', JSON.stringify(u))
        if (u.userId) localStorage.setItem('ws_dw_user_id', String(u.userId))
      } catch {
        /* ignore */
      }
      return { userId: u.userId, username: u.username, roles: u.roles }
    },
    { user: LOGIN_USER, pass: LOGIN_PASS },
  )
  if (result?.error) throw new Error(`Local login failed: ${result.error}`)
  console.log(`[login] local ${result.username} (${result.userId})`)
}

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function loadPlaywright() {
  try {
    return await import('playwright')
  } catch {
    console.error('playwright missing. From frontend/: pnpm install && pnpm exec playwright install chromium')
    process.exit(1)
  }
}

/** Find the demo function unit id by calling the DW API from within the logged-in page. */
async function discoverFunctionUnitId(page) {
  if (process.env.FU_ID) return Number(process.env.FU_ID)
  const id = await page.evaluate(async (name) => {
    let userId = ''
    try {
      const u = JSON.parse(localStorage.getItem('ws_dw_user') || '{}')
      userId = u.userId || u.id || localStorage.getItem('ws_dw_user_id') || ''
    } catch {
      userId = localStorage.getItem('ws_dw_user_id') || ''
    }
    const res = await fetch('/api/v1/function-units?page=0&size=200', {
      credentials: 'include',
      headers: userId ? { 'X-User-Id': String(userId) } : {},
    })
    if (!res.ok) return { error: `list ${res.status}`, userId }
    const body = await res.json()
    const list = body?.data?.content ?? body?.content ?? body?.data ?? []
    const hit = list.find((fu) => (fu.name || '').includes(name))
    return hit ? { id: hit.id, name: hit.name } : { error: 'not-found', count: list.length }
  }, DEMO_NAME)
  if (id?.error) throw new Error(`FU discovery failed: ${JSON.stringify(id)}`)
  console.log(`[fu] ${id.name} -> #${id.id}`)
  return id.id
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const { chromium } = await loadPlaywright()
  const browser = await chromium.launch({ headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 1000 } })).newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))

  try {
    await loginLocal(page)

    const fuId = await discoverFunctionUnitId(page)
    const designerUrl = `${ORIGIN}/dev/function-units/${fuId}`
    console.log(`[goto] ${designerUrl}`)
    await page.goto(designerUrl, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(4000)

    // Ensure we are on the Process Design tab.
    const processTab = page.getByText('Process Design', { exact: false }).first()
    if (await processTab.count()) {
      await processTab.click().catch(() => {})
      await page.waitForTimeout(1500)
    }

    // Click the first multi-instance sub-task node ("sub form1") on the bpmn canvas.
    // bpmn-js renders the label text inside the diagram; click its djs-element shape.
    const subTaskLabel = page.locator('text=sub form1').first()
    await subTaskLabel.waitFor({ timeout: 15000 })
    await subTaskLabel.click({ force: true })
    await page.waitForTimeout(1500)

    // The Sub-Task Config panel should now be visible with the Sub-table ID select.
    // Element Plus: the select trigger shows the current label; the name input is disabled.
    const nameInput = page.locator('input[disabled]').filter({ hasText: '' })
    // More robust: find the form-item labelled "Sub-table name" then its input.
    const nameLocator = page
      .locator('.el-form-item', { hasText: 'Sub-table name' })
      .locator('input')
      .first()
    await nameLocator.waitFor({ timeout: 10000 })
    const before = await nameLocator.inputValue()
    console.log(`[name] before switch = "${before}"`)

    // Open the Sub-table ID select and pick a DIFFERENT option than the current one.
    const idSelect = page
      .locator('.el-form-item', { hasText: 'Sub-table ID' })
      .locator('.el-select')
      .first()
    await idSelect.click()
    await page.waitForTimeout(800)
    const options = page.locator('.el-select-dropdown__item:visible')
    const optCount = await options.count()
    console.log(`[options] ${optCount} sub-table option(s)`)

    let switched = false
    for (let i = 0; i < optCount; i++) {
      const opt = options.nth(i)
      const txt = (await opt.textContent())?.trim() ?? ''
      // Pick an option whose technical name differs from the current name value.
      if (!before || !txt.includes(`(${before})`)) {
        await opt.click()
        console.log(`[switch] selected option "${txt}"`)
        switched = true
        break
      }
    }
    if (!switched && optCount > 0) {
      // Only one option / all match — re-select first to still exercise the handler.
      await options.first().click()
      console.log('[switch] only one distinct option; re-selected it')
    }
    await page.waitForTimeout(1200)

    const selectText = (await idSelect.innerText().catch(() => '')).replace(/\s+/g, ' ').trim()
    console.log(`[select] displayed = "${selectText}"`)

    const after = await nameLocator.inputValue()
    console.log(`[name] after switch  = "${after}"`)

    const outPath = join(OUT_DIR, `${datePrefix()}_mi-subtable-name-sync.png`)
    await page.screenshot({ path: outPath })
    console.log(`[saved] ${outPath}`)

    // Assertion: after selecting a sub-table, the name must be non-empty and
    // reflect the selection (changed when a different table was picked).
    const ok = after.trim().length > 0 && (optCount <= 1 || after !== before)
    if (errors.length) console.warn('[page errors]', errors.join('\n'))
    if (!ok) {
      throw new Error(`Sub-table name did not sync. before="${before}" after="${after}" options=${optCount}`)
    }
    console.log('[PASS] Sub-table name field synced with the Sub-table ID selection.')
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error('[verify-mi-subtable-name-sync] FAILED:', err.message)
  process.exit(1)
})
