/**
 * My Requests Current Assignee filter: Condition lists
 * Equals / Not equals / Contains / Does not contain / Has value / No value.
 */
import { mkdirSync } from 'fs'
import { dirname, join } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const outDir = join(__dirname, '..', 'user-portal', 'verification-screenshots')
mkdirSync(outDir, { recursive: true })

function stamp(slug) {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return join(
    outDir,
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}_${slug}.png`,
  )
}

const EXPECTED = [
  /Equals|等于|等於/,
  /Not equals|不等于|不等於/,
  /Contains|包含/,
  /Does not contain|不包含/,
  /Has value|有值/,
  /No value|没值|沒值/,
]

const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const page = await (await browser.newContext({ viewport: { width: 1400, height: 900 } })).newPage()

try {
  await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
  await page.goto('http://localhost:3000/portal/my-applications', { waitUntil: 'domcontentloaded' })
  await page.locator('.list-data-grid').waitFor({ timeout: 60000 })

  const assigneeHeader = page.locator('.list-col-header', {
    hasText: /Current Assignee|当前处理人|目前處理人/,
  }).locator('.list-col-trigger')
  await assigneeHeader.first().scrollIntoViewIfNeeded()
  await assigneeHeader.first().click()
  await page.getByRole('menuitem', { name: /Filter by|筛选依据|篩選依據/ }).click()
  const dialog = page.locator('.el-dialog').filter({ hasText: /Current Assignee|当前处理人|目前處理人/ })
  await dialog.waitFor()

  await dialog.locator('.list-filter-operator').click()
  const dropdown = page.locator('.el-select-dropdown').filter({ has: page.locator('.el-select-dropdown__item') }).last()
  await dropdown.waitFor()

  const labels = await dropdown.locator('.el-select-dropdown__item').allTextContents()
  const joined = labels.map((s) => s.trim()).filter(Boolean)
  for (const pattern of EXPECTED) {
    if (!joined.some((label) => pattern.test(label))) {
      throw new Error(`missing operator ${pattern} in ${JSON.stringify(joined)}`)
    }
  }
  if (joined.length !== EXPECTED.length) {
    throw new Error(`expected ${EXPECTED.length} operators, got ${JSON.stringify(joined)}`)
  }

  const shot = stamp('my-requests-assignee-filter-operators')
  await page.screenshot({ path: shot, fullPage: false })
  console.log(`[verify] operators: ${joined.join(' | ')}`)
  console.log(`[verify] screenshot: ${shot}`)
} finally {
  await browser.close()
}
