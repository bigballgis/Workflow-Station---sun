#!/usr/bin/env node
/**
 * Screenshot Email Monitors connection picker: INBOUND only (legacy BOTH excluded).
 *
 * Known dev fixture (2026-08-19): Function Unit 48 "MCY Debit Card_20260525" has
 * connection "Demo QQ Mailbox" with direction BOTH. After the INBOUND-only filter,
 * Email Monitors must show the no-inbound warning and the create-dialog dropdown
 * must not list that mailbox.
 *
 * Usage (from frontend/):
 *   node scripts/verify-dw-email-monitor-inbound-dropdown.mjs
 *
 * Output: developer-workstation/verification-screenshots/{date}_email-monitor-inbound-*.png
 * Screenshots MUST NOT be deleted after verify.
 */
import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = resolve(__dirname, '..')
const OUT_DIR = join(FRONTEND_ROOT, 'developer-workstation', 'verification-screenshots')
const ORIGIN = (process.env.LOGIN_ORIGIN ?? 'http://localhost:3000').replace(/\/$/, '')
const FU_ID = Number(process.env.FU_ID || 48)
const BOTH_MAILBOX = process.env.BOTH_MAILBOX || 'Demo QQ Mailbox'

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function loadPlaywright() {
  try {
    return await import('playwright')
  } catch {
    console.error(
      'playwright is not installed. From frontend/ run:\n' +
        '  pnpm install\n' +
        '  pnpm exec playwright install chromium',
    )
    process.exit(1)
  }
}

function launchOpts() {
  const opts = { headless: true }
  if (process.env.PLAYWRIGHT_EXECUTABLE_PATH) {
    opts.executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH
  } else if (process.env.PLAYWRIGHT_CHANNEL) {
    opts.channel = process.env.PLAYWRIGHT_CHANNEL
  }
  return opts
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const prefix = datePrefix()
  const shot = async (page, slug, opts = {}) => {
    const p = join(OUT_DIR, `${prefix}_${slug}.png`)
    await page.screenshot({ path: p, fullPage: false, ...opts })
    console.log('[shot]', p)
    return p
  }

  const { chromium } = await loadPlaywright()
  const browser = await chromium.launch(launchOpts())
  const page = await (
    await browser.newContext({ viewport: { width: 1440, height: 1100 } })
  ).newPage()
  const failures = []
  const check = (label, ok, detail = '') => {
    console.log(`[${ok ? 'pass' : 'FAIL'}] ${label}${detail ? ` — ${detail}` : ''}`)
    if (!ok) failures.push(label)
  }

  try {
    await loginViaDwPassword(page)

    const api = await page.evaluate(async (fuId) => {
      const res = await fetch(`/api/v1/function-units/${fuId}/connections`, {
        credentials: 'include',
      })
      const body = await res.json().catch(() => ({}))
      return { status: res.status, body }
    }, FU_ID)
    const connections = api.body?.data ?? api.body ?? []
    const list = Array.isArray(connections) ? connections : []
    console.log(
      '[connections]',
      JSON.stringify(
        list.map((c) => ({ name: c.name, direction: c.direction, enabled: c.enabled })),
      ),
    )
    check(
      'connections API reachable',
      api.status === 200,
      `HTTP ${api.status}`,
    )
    const bothRows = list.filter((c) => String(c.direction || '').toUpperCase() === 'BOTH')
    check(
      `legacy BOTH mailbox present (${BOTH_MAILBOX})`,
      bothRows.some((c) => c.name === BOTH_MAILBOX),
      bothRows.map((c) => c.name).join(', ') || 'none',
    )
    const inboundRows = list.filter((c) => String(c.direction || '').toUpperCase() === 'INBOUND')
    check('no INBOUND connections on this FU', inboundRows.length === 0, String(inboundRows.length))

    await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(4000)
    await page.locator('.el-tabs__item').first().waitFor({ timeout: 20000 })

    const connectionsTab = page.locator('.el-tabs__item').filter({ hasText: /^Connections$/ })
    await connectionsTab.click()
    await page.waitForTimeout(2500)
    await shot(page, 'email-monitor-inbound-01-connections-list')

    const mailboxCell = page.getByText(BOTH_MAILBOX, { exact: true }).first()
    check('Connections list shows BOTH mailbox name', (await mailboxCell.count()) > 0)
    try {
      const row = page.locator('.connection-list-table .el-table__body .el-table__row').first()
      await row.hover()
      await page.waitForTimeout(300)
      await row.getByRole('button', { name: /^Edit$/ }).click({ force: true, timeout: 8000 })
      await page.waitForTimeout(1200)
      const bothHint = page.getByText(/previously "Both"/i)
      check('connection edit shows legacy Both hint', (await bothHint.count()) > 0)
      await shot(page, 'email-monitor-inbound-02-connection-edit-both-hint')
      await page.keyboard.press('Escape')
      await page.waitForTimeout(600)
    } catch (err) {
      console.warn('[skip] connection edit dialog:', err.message.split('\n')[0])
    }

    const monitorsTab = page.locator('.el-tabs__item').filter({ hasText: /^Email Monitors$/ })
    await monitorsTab.click()
    await page.waitForTimeout(2500)
    const warning = page.getByText(/No inbound connection yet/i)
    check('Email Monitors shows no-inbound warning', (await warning.count()) > 0)
    const warningText = ((await warning.first().textContent()) ?? '')
    check('warning does not mention Both', !/both/i.test(warningText), warningText.trim())
    await shot(page, 'email-monitor-inbound-03-no-inbound-warning')

    await page.getByRole('button', { name: /New Monitor/i }).click()
    await page.waitForTimeout(1200)
    const dialog = page.locator('.el-dialog').filter({ hasText: /New Monitor|Create/i }).first()
    await dialog.waitFor({ timeout: 10000 })
    await shot(page, 'email-monitor-inbound-04-create-dialog')

    const mailboxSelect = dialog.locator('.el-select').first()
    await mailboxSelect.click()
    await page.waitForTimeout(800)
    const optionTexts = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').allTextContents()
    const cleaned = optionTexts.map((t) => t.trim()).filter(Boolean)
    console.log('[dropdown options]', JSON.stringify(cleaned))
    check(
      'dropdown does not list BOTH mailbox',
      !cleaned.some((t) => t.includes(BOTH_MAILBOX)),
      cleaned.join(' | ') || '(empty)',
    )
    check(
      'dropdown has no selectable connections when only BOTH exists',
      cleaned.length === 0,
      `count=${cleaned.length}`,
    )
    await shot(page, 'email-monitor-inbound-05-connection-dropdown')

    if (failures.length) {
      throw new Error(`Assertions failed: ${failures.join('; ')}`)
    }
    console.log('[ok] Email Monitor INBOUND-only dropdown verified')
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error('[verify-dw-email-monitor-inbound-dropdown] FAILED:', err.message)
  process.exit(1)
})
