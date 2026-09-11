#!/usr/bin/env node
/**
 * Screenshot Edit Monitor dialog: exactly one ? (header), none next to Add Field.
 *
 * Usage (from frontend/):
 *   node scripts/verify-dw-email-monitor-dialog-help.mjs
 *
 * Output: developer-workstation/verification-screenshots/{date}_email-monitor-dialog-single-help.png
 */
import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = resolve(__dirname, '..', 'developer-workstation', 'verification-screenshots')
const ORIGIN = (process.env.LOGIN_ORIGIN ?? 'http://localhost:3000').replace(/\/$/, '')

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function resolveFunctionUnitId(page) {
  if (process.env.FU_ID) {
    return process.env.FU_ID
  }
  const res = await page.request.get(`${ORIGIN}/api/v1/function-units`, {
    params: { page: 0, size: 50 },
  })
  const body = await res.json().catch(() => ({}))
  const rows = body.data?.content ?? body.content ?? body.data ?? []
  const list = Array.isArray(rows) ? rows : []
  const preferred = list.find((row) =>
    row.code === 'fu-20260910-emlrep' || row.name === 'Email Inbound Reply' || row.id === 50010)
  const id = preferred?.id ?? list[0]?.id
  if (!id) {
    throw new Error(`No Function Unit found (HTTP ${res.status()})`)
  }
  return id
}

async function clickTab(page, name) {
  const tab = page.getByRole('tab', { name, exact: true })
  if ((await tab.count()) === 0) {
    throw new Error(`Tab not found: ${name}`)
  }
  await tab.first().scrollIntoViewIfNeeded()
  await tab.first().click()
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const outPath = join(OUT_DIR, `${datePrefix()}_email-monitor-dialog-single-help.png`)

  const browser = await chromium.launch({ headless: true })
  const page = await (
    await browser.newContext({ viewport: { width: 1440, height: 1100 } })
  ).newPage()

  try {
    await loginViaDwPassword(page, { loginOrigin: ORIGIN })
    const fuId = await resolveFunctionUnitId(page)
    console.log(`[fu] ${fuId}`)
    await page.goto(`${ORIGIN}/dev/function-units/${fuId}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(2000)

    await clickTab(page, 'Email Monitors')
    await page.waitForSelector('.email-monitor-designer', { timeout: 15000 })
    await page.waitForTimeout(1200)

    const designer = page.locator('.email-monitor-designer')
    const editBtn = designer.getByRole('button', { name: /^Edit$/ })
    if ((await editBtn.count()) > 0) {
      await editBtn.first().click()
    } else {
      await designer.getByRole('button', { name: 'New Monitor' }).click()
    }
    const dialog = page.locator('.el-dialog').filter({ has: page.locator('[data-testid="email-monitor-dialog-guide-link"]') }).first()
    await dialog.waitFor({ state: 'visible', timeout: 15000 })
    await page.waitForTimeout(800)

    await clickTab(page, 'Field Mapping')
    await page.waitForSelector('.email-field-mapping-table', { timeout: 10000 })
    await page.waitForTimeout(400)

    const helpInDialog = dialog.locator('.designer-help-link')
    const helpCount = await helpInDialog.count()
    if (helpCount !== 1) {
      throw new Error(`Expected 1 help link in Edit Monitor dialog, found ${helpCount}`)
    }
    const headerHelp = dialog.locator('[data-testid="email-monitor-dialog-guide-link"]')
    if ((await headerHelp.count()) !== 1) {
      throw new Error('Header help link missing')
    }
    const mappingHelp = page.locator(
      '[data-testid="email-field-mapping-attachments-guide-link"], [data-testid="email-field-mapping-raw-eml-guide-link"]',
    )
    if ((await mappingHelp.count()) !== 0) {
      throw new Error('Field Mapping still has inline help links')
    }

    await dialog.screenshot({ path: outPath })
    console.log(`[saved] ${outPath}`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error('[verify-dw-email-monitor-dialog-help] FAILED:', err.message)
  process.exit(1)
})
