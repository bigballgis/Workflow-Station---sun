/**
 * Verify AI Studio one-click via Content Organizer: while the job runs, the Copilot shows "Step k of 5: <phase>".
 *
 * Needs a one-click job already running on DW_FU_ID and a DW access token (DW_TOKEN_FILE) for its initiator:
 * the step line is shown to the user who started the job (the page resumes polling that job on load).
 */
import { chromium } from 'playwright'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const origin = (process.env.DW_ORIGIN || 'http://localhost:3102').replace(/\/$/, '')
const FU_ID = process.env.DW_FU_ID
const token = (await readFile(process.env.DW_TOKEN_FILE, 'utf8')).trim()
const user = {
  userId: 'user-dev', username: 'developer', displayName: 'Developer', email: '',
  roles: ['SYS_ADMIN', 'TECH_LEAD'], permissions: [], language: 'en', hasAvatar: false
}

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'en-US' })
await ctx.addCookies([{ name: 'dw_access_token', value: token, url: origin, httpOnly: true }])
await ctx.addInitScript((u) => {
  localStorage.setItem('ws_dw_user', JSON.stringify(u))
  localStorage.setItem('ws_dw_user_id', u.userId)
}, user)
const page = await ctx.newPage()

await page.goto(`${origin}/dev/function-units/${FU_ID}/ai-studio`, { waitUntil: 'domcontentloaded', timeout: 120000 })
const step = page.locator('.copilot-msg__working', { hasText: /Step \d of 5/ })
await step.waitFor({ state: 'visible', timeout: 90000 })
console.log('Step line:', await step.innerText())

const shot = path.join(__dirname, '2026-11-03_ai-studio-one-click-co-progress.png')
await page.screenshot({ path: shot })
console.log('Screenshot:', shot)
await browser.close()
