/**
 * To Do: column Filter by = High must show High cells, not Normal.
 * Flowable stores priority as "50"; the cell renderer must map that onto HIGH.
 */
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const OUT = join(dirname(fileURLToPath(import.meta.url)), '../user-portal/verification-screenshots')
const DATE = new Date().toISOString().slice(0, 10)

mkdirSync(OUT, { recursive: true })

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
let failures = 0

function check(label, ok, detail) {
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) failures++
}

async function waitForTodoRows() {
  await page.waitForSelector('.list-data-grid tbody tr, .el-table__body tbody tr', { timeout: 20000 })
  await page.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 20000 }).catch(() => {})
}

try {
  await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
  await page.goto(`${ORIGIN}/portal/tasks`, { waitUntil: 'domcontentloaded' })
  await waitForTodoRows()

  const priorityHeader = page.locator('.list-col-header', { hasText: /^Priority$/ }).locator('.list-col-trigger')
  await priorityHeader.click()
  await page.getByRole('menuitem', { name: 'Filter by' }).click()
  await page.waitForSelector('.el-dialog', { timeout: 8000 })
  await page.locator('.el-dialog .list-filter-value').click()
  await page.locator('.el-select-dropdown:visible').getByRole('option', { name: /^High$/ }).click()
  await page.locator('.el-dialog').getByRole('button', { name: 'Apply' }).click()
  await page.locator('.el-dialog').waitFor({ state: 'hidden', timeout: 8000 })
  await waitForTodoRows()

  const labels = (await page.locator('.priority').allTextContents())
    .map((s) => s.trim())
    .filter(Boolean)
  const unique = [...new Set(labels)]
  check(
    'High column filter shows High cells, not Normal',
    labels.length > 0 && unique.every((l) => l === 'High'),
    `cells=${JSON.stringify(unique)} count=${labels.length}`,
  )
  check(
    'Priority header shows the active filter funnel',
    (await page.locator('.list-col-header', { hasText: /^Priority$/ }).locator('.is-filter').count()) > 0,
  )

  const shot = join(OUT, `${DATE}_todo-priority-filter-high.png`)
  await page.screenshot({ path: shot, fullPage: false })
  console.log(`[SHOT] ${shot}`)
} finally {
  await browser.close()
}

if (failures > 0) {
  process.exit(1)
}
