#!/usr/bin/env node
/**
 * Screenshot verification for Admin Environment variables + DW Connection VAULT select.
 *
 * Usage (from frontend/):
 *   node scripts/verify-environment-vault-ui.mjs
 *   FU_ID=50015 node scripts/verify-environment-vault-ui.mjs
 */

import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaAdminPassword, loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = resolve(__dirname, '..')
const ORIGIN = (process.env.LOGIN_ORIGIN ?? 'http://localhost:3000').replace(/\/$/, '')
const FU_ID = process.env.FU_ID ?? '50015'

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

async function screenshotAdminEnvTab(prefix) {
  mkdirSync(OUT.admin, { recursive: true })
  const outPath = join(OUT.admin, `${prefix}_admin-environment-variables.png`)
  const { browser, page } = await launchBrowser()
  try {
    await loginViaAdminPassword(page, { loginOrigin: ORIGIN })
    await page.goto(`${ORIGIN}/admin/environment-variables`, {
      waitUntil: 'domcontentloaded',
    })
    await page.waitForTimeout(2500)
    const heading = page.getByText('Environment variables').first()
    if ((await heading.count()) === 0) {
      throw new Error('Admin sidebar missing Environment variables page')
    }
    const help = page.locator('[data-testid="env-guide-link"]')
    if ((await help.count()) === 0) {
      throw new Error('Environment variables page missing help ? link')
    }
    const addBtn = page.getByRole('button', { name: /Add variable|新增变量|新增變數/ })
    if ((await addBtn.count()) === 0) {
      throw new Error('Environment variables page missing Add variable')
    }
    await addBtn.first().click()
    await page.waitForSelector('.el-dialog', { timeout: 8000 })
    const vaultRadio = page.locator('.el-dialog').getByText('VAULT', { exact: true })
    if ((await vaultRadio.count()) > 0) {
      await vaultRadio.first().click()
    }
    await page.waitForTimeout(400)
    const passwordLabel = page.getByText(/Vault password|Vault 密码|Vault 密碼/)
    if ((await passwordLabel.count()) === 0) {
      await page.screenshot({ path: outPath.replace('.png', '-no-password.png'), fullPage: true })
      throw new Error('VAULT create dialog missing password field')
    }
    await page.screenshot({ path: outPath, fullPage: true })
    console.log('[saved]', outPath)
    return outPath
  } finally {
    await browser.close()
  }
}

async function screenshotDwConnectionVault(prefix) {
  mkdirSync(OUT.dw, { recursive: true })
  const outPath = join(OUT.dw, `${prefix}_dw-connection-vault-select.png`)
  const { browser, page } = await launchBrowser()
  try {
    await loginViaDwPassword(page, { loginOrigin: ORIGIN })
    await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(3500)
    const tabByRole = page.getByRole('tab', { name: 'Connections' })
    if ((await tabByRole.count()) > 0) {
      await tabByRole.first().click()
    } else {
      await page.locator('#tab-connections').first().click()
    }
    await page.waitForTimeout(2000)
    const newBtn = page.getByRole('button', { name: 'New Connection' })
    if ((await newBtn.count()) === 0) {
      await page.screenshot({ path: outPath.replace('.png', '-no-tab.png'), fullPage: true })
      throw new Error('Connections tab New Connection button not found')
    }
    await newBtn.first().click()
    await page.waitForSelector('.connection-form-dialog, .el-dialog', { timeout: 8000 })
    await page.waitForTimeout(800)
    const vaultLabel = page.getByText('Password (VAULT)', { exact: false })
    const vaultSelect = page.locator('.connection-form .el-select, .el-dialog .el-select').last()
    if ((await vaultLabel.count()) === 0 && (await vaultSelect.count()) === 0) {
      await page.screenshot({ path: outPath.replace('.png', '-no-select.png'), fullPage: true })
      throw new Error('Connection dialog missing VAULT environment variable select')
    }
    if ((await vaultLabel.count()) > 0) {
      await vaultLabel.first().scrollIntoViewIfNeeded()
    } else {
      await vaultSelect.first().scrollIntoViewIfNeeded()
    }
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
  paths.push(await screenshotAdminEnvTab(prefix))
  paths.push(await screenshotDwConnectionVault(prefix))
  console.log('\n=== verification screenshots ===')
  for (const p of paths) console.log(p)
}

main().catch((err) => {
  console.error('[verify-environment-vault-ui] FAILED:', err.message)
  process.exit(1)
})
