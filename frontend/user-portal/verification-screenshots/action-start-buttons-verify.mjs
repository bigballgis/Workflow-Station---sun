/**
 * Verify process start resolves custom BPMN actions (lookup + UI when entitled).
 * API check is authoritative when FU returns Access Denied in dev.
 */
import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  PORTAL_API,
  PORTAL_ORIGIN,
  setupPortalSession,
} from './portal-verify-session.mjs'
import {
  VERIFY_FU_KEY,
  installAllowedRolesStartFixtures,
} from './portal-allowed-roles-fixtures.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const OUT_DIR = __dirname
const DATE = process.env.VERIFY_DATE || '2026-10-14'
const FU_KEY = process.env.FU_START_KEY || VERIFY_FU_KEY
const USE_FIXTURE = process.env.FU_START_FIXTURE !== '0'

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1400, height: 900 } })
const page = await ctx.newPage()

if (USE_FIXTURE || FU_KEY === VERIFY_FU_KEY) {
  await installAllowedRolesStartFixtures(page, FU_KEY)
}

const user = await setupPortalSession(page, ctx)

const actionsRes = await ctx.request.get(`${PORTAL_API}/processes/actions`, {
  params: { ids: '50434,50435,50436,50437,50438,50439' },
})
const actionsJson = await actionsRes.json()
const actionCount = (actionsJson.data ?? actionsJson)?.length ?? 0
console.log('API actions resolved (50434-50439):', actionCount)

const startUrl = `${PORTAL_ORIGIN}/portal/processes/start/${FU_KEY}`
await page.goto(startUrl, { waitUntil: 'domcontentloaded', timeout: 120000 })
await page.waitForTimeout(3500)

const buttonLabels = await page
  .locator('.process-start-page .action-buttons .right-actions .el-button')
  .allTextContents()
const trimmed = buttonLabels.map((s) => s.trim()).filter(Boolean)
console.log('Start page buttons:', trimmed)

await mkdir(OUT_DIR, { recursive: true })
const shot = path.join(OUT_DIR, `${DATE}_process-start-custom-actions.png`)
await page.screenshot({ path: shot, fullPage: true })
console.log('Screenshot:', shot)

const denied = await page.getByText('Access Denied').count()
if (denied > 0 && !USE_FIXTURE && FU_KEY !== VERIFY_FU_KEY) {
  console.warn('SKIP UI buttons: Access Denied for FU (entitlement); API check is authoritative')
  await browser.close()
  process.exit(actionCount >= 1 ? 0 : 1)
}

if (USE_FIXTURE || FU_KEY === VERIFY_FU_KEY) {
  const hasFixtureActions = trimmed.some((l) =>
    /Unrestricted Action|Role Match Action|Hidden Role Action/.test(l),
  )
  if (!hasFixtureActions) {
    console.error('FAIL: fixture start page missing mocked custom actions', trimmed)
    await browser.close()
    process.exit(1)
  }
  console.log('PASS: fixture start page shows custom BPMN actions (sysAdmin=', user.systemAdministrator, ')')
  await browser.close()
  process.exit(0)
}

const hasCustom = trimmed.some((l) => /Save Draft|Continue|Send Email|Unrestricted/i.test(l))
const onlyDefaultSubmit = trimmed.length === 1 && /Submit/i.test(trimmed[0] || '')

if (actionCount < 1) {
  console.error('FAIL: API returned no actions')
  process.exit(1)
}
if (!hasCustom && (onlyDefaultSubmit || trimmed.length === 0)) {
  console.error('FAIL: start page missing custom BPMN actions', trimmed)
  process.exit(1)
}

console.log('PASS: custom start actions visible')
await browser.close()
