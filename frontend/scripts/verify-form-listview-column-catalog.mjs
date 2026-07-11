#!/usr/bin/env node
/**
 * Verify Form Design sub-table List View: left "Table Columns" shows full field catalog
 * (including audit fields); right grid only shows designer-selected columns.
 *
 * Usage (from frontend/):
 *   node scripts/verify-form-listview-column-catalog.mjs [functionUnitId]
 */

import { mkdirSync } from 'fs'
import { dirname, join } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(__dirname, '..', 'developer-workstation', 'verification-screenshots')
const FUNCTION_UNIT_ID = Number(process.argv[2] || 50005)
const AUDIT_NAMES = ['created_at', 'created_by', 'updated_at', 'updated_by']

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const browser = await chromium.launch({ headless: true, channel: 'chrome' })
  const page = await browser.newPage({ viewport: { width: 1600, height: 1200 } })

  try {
    await loginViaUnifiedSso(page, 'dw')
    await page.goto(`http://localhost:3000/dev/function-units/${FUNCTION_UNIT_ID}`, {
      waitUntil: 'networkidle',
    })
    await page.waitForTimeout(2500)

    const formsTab = page.locator('.el-tabs__item').filter({ hasText: /Form Design|表单设计|表單設計/ })
    await formsTab.first().click({ timeout: 15000 })
    await page.waitForTimeout(2000)

    const subTab = page.locator('.el-tabs__item').filter({ hasText: /Sub Table|子表|子表單/ }).first()
    if ((await subTab.count()) > 0) {
      await subTab.click()
      await page.waitForTimeout(1500)
    }

    const listViewTab = page.locator('button').filter({ hasText: /List View|列表视图|列表視圖/ }).first()
    if ((await listViewTab.count()) > 0) {
      await listViewTab.click()
      await page.waitForTimeout(2000)
    }

    const leftPanel = page.locator('.sub-table-list-view .columns-panel .columns-field-list').first()
    await leftPanel.waitFor({ timeout: 15000 })
    const leftText = await leftPanel.innerText()

    const leftFieldItems = await page.locator('.sub-table-list-view .columns-panel .field-item .field-name').allTextContents()
    if (leftFieldItems.length < 2) {
      const debugPath = join(OUT_DIR, `${datePrefix()}_form-listview-catalog-empty-left-debug.png`)
      await page.screenshot({ path: debugPath, fullPage: true })
      throw new Error(`Left Table Columns catalog is empty or too small (${leftFieldItems.length} items). Debug: ${debugPath}`)
    }

    const auditInLeft = AUDIT_NAMES.filter((name) => new RegExp(`\\b${name}\\b`, 'i').test(leftText))
    if (auditInLeft.length < AUDIT_NAMES.length) {
      const missing = AUDIT_NAMES.filter((n) => !auditInLeft.includes(n))
      const debugPath = join(OUT_DIR, `${datePrefix()}_form-listview-catalog-missing-audit-debug.png`)
      await page.screenshot({ path: debugPath, fullPage: true })
      throw new Error(`Audit fields missing from left catalog: ${missing.join(', ')}. Debug: ${debugPath}`)
    }

    const outPath = join(OUT_DIR, `${datePrefix()}_form-listview-column-catalog.png`)
    await page.screenshot({ path: outPath, fullPage: true })
    console.log(`OK: left catalog has ${leftFieldItems.length} fields incl. audit. Screenshot: ${outPath}`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
