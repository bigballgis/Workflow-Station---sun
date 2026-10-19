/**
 * Verify Action Design → Allowed Roles field (catalog multi-select + help link).
 */
import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from '../../scripts/playwright-login.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const OUT_DIR = __dirname
const origin = (process.env.DW_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const FU_ID = process.env.HELP_GUIDE_FU_ID || process.env.DW_FU_ID || '50015'

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()

await loginViaDwPassword(page, { loginOrigin: origin })

await page.goto(`${origin}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded', timeout: 120000 })
await page.getByRole('tab', { name: 'Action Design', exact: true }).click()
await page.waitForTimeout(800)

const editBtn = page.getByRole('button', { name: 'Edit' }).first()
await editBtn.waitFor({ state: 'visible', timeout: 15000 })
await editBtn.click()

const guideLink = page.getByTestId('action-allowed-roles-guide-link')
await guideLink.waitFor({ state: 'visible', timeout: 15000 })
const allowedField = page.locator('.allowed-roles-label').first()
await allowedField.waitFor({ state: 'visible', timeout: 10000 })

await mkdir(OUT_DIR, { recursive: true })
const shot = path.join(OUT_DIR, '2026-10-14_action-allowed-roles-field.png')
const panel = page.locator('.designer-workspace, .action-designer').first()
await panel.waitFor({ state: 'visible', timeout: 15000 })
await panel.screenshot({ path: shot })
console.log('Screenshot:', shot)

await browser.close()
