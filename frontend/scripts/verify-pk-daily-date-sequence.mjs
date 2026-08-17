#!/usr/bin/env node
/**
 * Table Design PK editor: "Date + daily sequence" option, pad width, Shanghai preview.
 *
 * Usage (from frontend/):
 *   node scripts/verify-pk-daily-date-sequence.mjs [functionUnitId]
 */

import { mkdirSync } from 'fs'
import { dirname, join } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(__dirname, '..', 'developer-workstation', 'verification-screenshots')
const ORIGIN = (process.env.VERIFY_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const FU_ID = process.argv[2] || process.env.DW_FU_ID || '50005'

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function shot(slug) {
  mkdirSync(OUT_DIR, { recursive: true })
  return join(OUT_DIR, `${datePrefix()}_${slug}.png`)
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}),
  })
  const page = await (await browser.newContext({ viewport: { width: 1680, height: 1100 } })).newPage()
  const debugPath = shot('pk-daily-date-sequence-debug')

  try {
    await loginViaDwPassword(page, { loginOrigin: ORIGIN })
    await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(2500)

    const tablesTab = page.locator('.el-tabs__item').filter({ hasText: /Table Design|表设计|表設計/ })
    if ((await tablesTab.count()) === 0) {
      await page.screenshot({ path: debugPath, fullPage: true })
      throw new Error(`Tables tab not found (FU ${FU_ID}). Debug: ${debugPath}`)
    }
    await tablesTab.first().click({ timeout: 15000 })
    await page.waitForTimeout(2000)

    const tableRows = page.locator('.table-designer .table-list .el-table__body tr')
    if ((await tableRows.count()) === 0) {
      await page.screenshot({ path: debugPath, fullPage: true })
      throw new Error(`No tables in FU ${FU_ID}. Debug: ${debugPath}`)
    }
    await tableRows.first().click({ timeout: 15000 })
    await page.waitForSelector('.table-fields-grid', { timeout: 15000 })
    await page.waitForTimeout(800)

    const pkBtn = page.locator('.table-fields-grid .constraint-config-btn').first()
    if ((await pkBtn.count()) === 0) {
      await page.screenshot({ path: debugPath, fullPage: true })
      throw new Error(`PK generation button not found. Debug: ${debugPath}`)
    }
    await pkBtn.click()
    await page.waitForSelector('.pk-generation-popover', { timeout: 8000 })

    const popover = page.locator('.pk-generation-popover').last()
    await popover.locator('.el-select').first().click()
    await page.waitForTimeout(400)

    const dailyOpt = page.locator('.el-select-dropdown__item, .pk-generation-popover .el-option').filter({
      hasText: /Date \+ daily sequence|日期\+每日序号|日期\+每日序號/,
    })
    if ((await dailyOpt.count()) === 0) {
      await page.screenshot({ path: debugPath, fullPage: true })
      throw new Error(`dailyDateSequence option missing. Debug: ${debugPath}`)
    }
    const monthlyInDropdown = page.locator('.el-select-dropdown__item, .pk-generation-popover .el-option').filter({
      hasText: /Date \+ monthly sequence|日期\+每月序号|日期\+每月序號/,
    })
    if ((await monthlyInDropdown.count()) === 0) {
      await page.screenshot({ path: debugPath, fullPage: true })
      throw new Error(`monthlyDateSequence option missing. Debug: ${debugPath}`)
    }
    const dropdownShot = shot('dw-table-pk-daily-date-option')
    await page.screenshot({ path: dropdownShot })
    await dailyOpt.first().click()
    await page.waitForTimeout(500)

    const body = (await popover.innerText()) ?? ''
    if (!/Date \+ daily sequence|日期\+每日序号|日期\+每日序號/.test(body)) {
      throw new Error(`Daily strategy not selected. Popover text: ${body.slice(0, 400)}`)
    }
    if (!/\d{8}\d+/.test(body)) {
      throw new Error(`Expected YYYYMMDD preview in popover. Text: ${body.slice(0, 400)}`)
    }

    const editorShot = shot('dw-table-pk-daily-date-editor')
    await popover.screenshot({ path: editorShot })

    await popover.locator('.el-select').first().click()
    await page.waitForTimeout(400)
    const monthlyOpt = page.locator('.el-select-dropdown__item, .pk-generation-popover .el-option').filter({
      hasText: /Date \+ monthly sequence|日期\+每月序号|日期\+每月序號/,
    })
    const monthlyDropdownShot = shot('dw-table-pk-monthly-date-option')
    await page.screenshot({ path: monthlyDropdownShot })
    await monthlyOpt.first().click()
    await page.waitForTimeout(500)

    const monthlyBody = (await popover.innerText()) ?? ''
    if (!/Date \+ monthly sequence|日期\+每月序号|日期\+每月序號/.test(monthlyBody)) {
      throw new Error(`Monthly strategy not selected. Popover text: ${monthlyBody.slice(0, 400)}`)
    }
    const monthlyPreview = monthlyBody.match(/\b(\d{10,12})\b/)
    if (!monthlyPreview || monthlyPreview[1].length !== 10) {
      throw new Error(`Expected YYYYMM+pad4 preview (10 digits). Text: ${monthlyBody.slice(0, 400)}`)
    }

    const monthlyEditorShot = shot('dw-table-pk-monthly-date-editor')
    await popover.screenshot({ path: monthlyEditorShot })
    console.log(`OK: option=${dropdownShot}`)
    console.log(`OK: editor=${editorShot}`)
    console.log(`OK: monthly-option=${monthlyDropdownShot}`)
    console.log(`OK: monthly-editor=${monthlyEditorShot}`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
