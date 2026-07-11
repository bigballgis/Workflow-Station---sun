#!/usr/bin/env node
/**
 * Verify Table Design audit fields (created_at/by, updated_at/by) render read-only.
 *
 * Usage (from frontend/):
 *   node scripts/verify-table-audit-fields-readonly.mjs [functionUnitId]
 */

import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(__dirname, '..', 'developer-workstation', 'verification-screenshots')
const FUNCTION_UNIT_ID = Number(process.argv[2] || 50005)

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const browser = await chromium.launch({ headless: true, channel: 'chrome' })
  const page = await browser.newPage({ viewport: { width: 1600, height: 1200 } })
  const debugPath = join(OUT_DIR, `${datePrefix()}_table-audit-fields-debug.png`)

  try {
    await loginViaUnifiedSso(page, 'dw')
    await page.goto(`http://localhost:3000/dev/function-units/${FUNCTION_UNIT_ID}`, {
      waitUntil: 'networkidle',
    })
    await page.waitForTimeout(2500)

    const tablesTab = page.locator('#tab-tables, .el-tabs__item').filter({ hasText: /Table Design|表设计|表設計/ })
    if ((await tablesTab.count()) === 0) {
      await page.screenshot({ path: debugPath, fullPage: true })
      throw new Error(`Tables tab not found (FU ${FUNCTION_UNIT_ID}). Debug: ${debugPath}`)
    }
    await tablesTab.first().click({ timeout: 15000 })
    await page.waitForTimeout(2000)

    const tableRows = page.locator('.table-designer .table-list .el-table__body tr')
    if ((await tableRows.count()) === 0) {
      await page.screenshot({ path: debugPath, fullPage: true })
      throw new Error(`No tables in FU ${FUNCTION_UNIT_ID}. Debug: ${debugPath}`)
    }
    await tableRows.first().click({ timeout: 15000 })
    await page.waitForSelector('.table-fields-grid', { timeout: 15000 })
    await page.waitForTimeout(1500)

    const auditRows = page.locator('.table-fields-grid tr.audit-field-row')
    const count = await auditRows.count()
    if (count < 4) {
      throw new Error(`Expected at least 4 audit-field rows, found ${count}`)
    }

    const disabledInputs = page.locator('.table-fields-grid tr.audit-field-row input[disabled], .table-fields-grid tr.audit-field-row .is-disabled')
    const disabledCount = await disabledInputs.count()
    if (disabledCount < 4) {
      throw new Error(`Expected disabled controls on audit rows, found ${disabledCount}`)
    }

    const systemTags = page.locator('.table-fields-grid .audit-field-tag')
    const tagCount = await systemTags.count()
    if (tagCount < 4) {
      throw new Error(`Expected 4 system tags on audit rows, found ${tagCount}`)
    }

    const fieldsGrid = page.locator('.table-fields-wrap')
    const outPath = join(OUT_DIR, `${datePrefix()}_table-audit-fields-readonly.png`)
    await fieldsGrid.screenshot({ path: outPath })
    console.log(`OK: ${count} audit rows, ${tagCount} system tags, screenshot: ${outPath}`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
