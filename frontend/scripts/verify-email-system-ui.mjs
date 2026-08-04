#!/usr/bin/env node
/**
 * Screenshot verification for email SMTP/IMAP system config + DW Connections/Monitors tabs.
 *
 * Usage (from frontend/):
 *   node scripts/verify-email-system-ui.mjs
 *   FU_ID=48 node scripts/verify-email-system-ui.mjs
 */

import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaUnifiedSso } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = resolve(__dirname, '..')
const ORIGIN = (process.env.LOGIN_ORIGIN ?? 'http://localhost:3000').replace(/\/$/, '')
const FU_ID = process.env.FU_ID ?? '48'

const OUT = {
  admin: join(FRONTEND_ROOT, 'admin-center', 'verification-screenshots'),
  dw: join(FRONTEND_ROOT, 'developer-workstation', 'verification-screenshots'),
}

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function launchBrowser() {
  const launchOpts = { headless: true }
  if (process.env.PLAYWRIGHT_EXECUTABLE_PATH) {
    launchOpts.executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH
  } else if (process.env.PLAYWRIGHT_CHANNEL) {
    launchOpts.channel = process.env.PLAYWRIGHT_CHANNEL
  }
  const browser = await chromium.launch(launchOpts)
  const context = await browser.newContext({ viewport: { width: 1400, height: 1600 } })
  return { browser, page: await context.newPage() }
}

async function screenshotAdminConfig(prefix) {
  mkdirSync(OUT.admin, { recursive: true })
  const outPath = join(OUT.admin, `${prefix}_admin-email-system-config.png`)
  const { browser, page } = await launchBrowser()
  try {
    console.log('[admin] login')
    await loginViaUnifiedSso(page, 'admin')
    const url = `${ORIGIN}/admin/config`
    console.log('[admin] goto', url)
    await page.goto(url, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(4000)
    const imap = page.getByText('Email Monitor (IMAP)', { exact: false })
    if ((await imap.count()) === 0) {
      throw new Error('Admin config page missing IMAP section label')
    }
    await imap.first().scrollIntoViewIfNeeded()
    await page.waitForTimeout(500)
    await page.screenshot({ path: outPath, fullPage: true })
    console.log('[saved]', outPath)
    return outPath
  } finally {
    await browser.close()
  }
}

async function screenshotDwTab(prefix, tabLabel, slug) {
  mkdirSync(OUT.dw, { recursive: true })
  const outPath = join(OUT.dw, `${prefix}_dw-fu${FU_ID}-${slug}.png`)
  const { browser, page } = await launchBrowser()
  try {
    console.log('[dw] login')
    await loginViaDwPassword(page, { loginOrigin: ORIGIN })
    const url = `${ORIGIN}/dev/function-units/${FU_ID}`
    console.log('[dw] goto', url)
    await page.goto(url, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(3500)
    const tab = page.locator('#tab-connections, #tab-email-monitors').filter({ hasText: tabLabel })
    const tabByRole = page.getByRole('tab', { name: tabLabel })
    if ((await tabByRole.count()) > 0) {
      await tabByRole.first().click()
    } else {
      await page.getByText(tabLabel, { exact: true }).first().click()
    }
    await page.waitForTimeout(2500)
    await page.screenshot({ path: outPath, fullPage: false })
    console.log('[saved]', outPath)
    return outPath
  } finally {
    await browser.close()
  }
}

async function main() {
  const prefix = datePrefix()
  const paths = []
  paths.push(await screenshotAdminConfig(prefix))
  paths.push(await screenshotDwTab(prefix, 'Connections', 'connections'))
  paths.push(await screenshotDwTab(prefix, 'Email Monitors', 'email-monitors'))
  console.log('\n=== verification screenshots ===')
  for (const p of paths) console.log(p)
}

main().catch((err) => {
  console.error('[verify-email-system-ui] FAILED:', err.message)
  process.exit(1)
})
