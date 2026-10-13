/**
 * Playwright: process start page shows/hides BPMN actions per configJson.allowedRoles
 * (session role codes, systemAdministrator bypass off).
 *
 * Uses routed API fixtures when dev catalog has no entitled FU — real DOM + real filter util.
 */
import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  PORTAL_ORIGIN,
  installPortalMeStub,
  setupPortalSession,
} from './portal-verify-session.mjs'
import {
  VERIFY_FU_KEY,
  VERIFY_ACTIONS_ALL_RESTRICTED,
  installAllowedRolesStartFixtures,
} from './portal-allowed-roles-fixtures.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const OUT_DIR = __dirname
const DATE = process.env.VERIFY_DATE || '2026-10-14'

async function readActionButtonLabels(page) {
  const labels = await page
    .locator('.process-start-page .action-buttons .right-actions .el-button')
    .allTextContents()
  return labels.map((s) => s.trim()).filter(Boolean)
}

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1400, height: 920 } })
const page = await ctx.newPage()

await installAllowedRolesStartFixtures(page, VERIFY_FU_KEY)
const meStub = await installPortalMeStub(page)
await setupPortalSession(page, ctx)

await meStub.apply(page, {
  systemAdministrator: false,
  roles: ['ROLE_MATCH'],
})

const startUrl = `${PORTAL_ORIGIN}/portal/processes/start/${VERIFY_FU_KEY}`
await page.goto(startUrl, { waitUntil: 'domcontentloaded', timeout: 120000 })
await page.waitForTimeout(3500)

const visibleMatch = await readActionButtonLabels(page)
console.log('Buttons (ROLE_MATCH):', visibleMatch)

await mkdir(OUT_DIR, { recursive: true })
const shotMatch = path.join(OUT_DIR, `${DATE}_process-start-allowed-roles-match.png`)
await page.screenshot({ path: shotMatch, fullPage: true })
console.log('Screenshot:', shotMatch)

const expectMatch = ['Unrestricted Action', 'Role Match Action']
for (const label of expectMatch) {
  if (!visibleMatch.includes(label)) {
    console.error('FAIL: missing button', label, 'got', visibleMatch)
    await browser.close()
    process.exit(1)
  }
}
if (visibleMatch.includes('Hidden Role Action')) {
  console.error('FAIL: Hidden Role Action should not show for ROLE_MATCH')
  await browser.close()
  process.exit(1)
}

await meStub.apply(page, {
  systemAdministrator: false,
  roles: ['ROLE_NOPE'],
})
await page.reload({ waitUntil: 'domcontentloaded', timeout: 120000 })
await page.waitForTimeout(3500)

const visibleHidden = await readActionButtonLabels(page)
console.log('Buttons (ROLE_NOPE, mixed):', visibleHidden)

const shotHidden = path.join(OUT_DIR, `${DATE}_process-start-allowed-roles-hidden.png`)
await page.screenshot({ path: shotHidden, fullPage: true })
console.log('Screenshot:', shotHidden)

if (!visibleHidden.includes('Unrestricted Action')) {
  console.error('FAIL: unrestricted action should remain visible')
  await browser.close()
  process.exit(1)
}
if (visibleHidden.some((l) => /Role Match|Hidden Role/.test(l))) {
  console.error('FAIL: restricted actions should be hidden for ROLE_NOPE', visibleHidden)
  await browser.close()
  process.exit(1)
}

await page.unroute('**/api/portal/processes/actions**')
await page.unroute(`**/api/portal/processes/function-units/${VERIFY_FU_KEY}/content**`)
await installAllowedRolesStartFixtures(page, VERIFY_FU_KEY, {
  actions: VERIFY_ACTIONS_ALL_RESTRICTED,
})
await meStub.apply(page, {
  systemAdministrator: false,
  roles: ['ROLE_NOPE'],
})
await page.goto(startUrl, { waitUntil: 'domcontentloaded', timeout: 120000 })
await page.waitForTimeout(3500)

const visibleAllBlocked = await readActionButtonLabels(page)
console.log('Buttons (ROLE_NOPE, all restricted):', visibleAllBlocked)

const shotBlocked = path.join(OUT_DIR, `${DATE}_process-start-allowed-roles-no-submit.png`)
await page.screenshot({ path: shotBlocked, fullPage: true })
console.log('Screenshot:', shotBlocked)

if (visibleAllBlocked.some((l) => /Submit Application|Submit|提交/.test(l))) {
  console.error('FAIL: default submit must not show when all BPMN actions are role-blocked', visibleAllBlocked)
  await browser.close()
  process.exit(1)
}
const alertCount = await page.locator('.action-roles-blocked-alert').count()
if (alertCount < 1) {
  console.error('FAIL: expected actionsBlockedByAllowedRoles alert')
  await browser.close()
  process.exit(1)
}

console.log('PASS: allowed roles filter on process start')
await browser.close()
