#!/usr/bin/env node
/**
 * Table Design PK editor: custom format with sequence reset, on two Function Units.
 *
 * Usage (from frontend/):
 *   node scripts/verify-pk-custom-format.mjs [functionUnitId] [functionUnitId2]
 */

import { mkdirSync } from 'fs'
import { dirname, join } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(__dirname, '..', 'developer-workstation', 'verification-screenshots')
const ORIGIN = (process.env.VERIFY_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const CUSTOM_OPTION = /Custom format|自定义格式|自訂格式/

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function shot(slug) {
  mkdirSync(OUT_DIR, { recursive: true })
  return join(OUT_DIR, `${datePrefix()}_${slug}.png`)
}

async function listFunctionUnitIds(page) {
  const res = await page.request.get(`${ORIGIN}/api/v1/function-units`, {
    params: { page: 0, size: 50, sort: 'id,asc' },
  })
  const body = await res.json().catch(() => ({}))
  const rows = body.data?.content ?? body.content ?? []
  return rows.map((row) => row.id).filter((id) => id != null)
}

async function functionUnitHasTables(page, fuId) {
  const res = await page.request.get(`${ORIGIN}/api/v1/function-units/${fuId}/tables`)
  const body = await res.json().catch(() => ({}))
  const tables = body.data ?? body
  return Array.isArray(tables) && tables.length > 0
}

async function openPkPopover(page, fuId, debugPath) {
  await page.goto(`${ORIGIN}/dev/function-units/${fuId}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2500)

  const tablesTab = page.locator('.el-tabs__item').filter({ hasText: /Table Design|表设计|表設計/ })
  if ((await tablesTab.count()) === 0) {
    await page.screenshot({ path: debugPath, fullPage: true })
    throw new Error(`Tables tab not found (FU ${fuId}). Debug: ${debugPath}`)
  }
  await tablesTab.first().click({ timeout: 15000 })
  await page.waitForTimeout(2000)

  const tableRows = page.locator('.table-designer .table-list .el-table__body tr')
  if ((await tableRows.count()) === 0) {
    return null
  }
  await tableRows.first().click({ timeout: 15000 })
  await page.waitForSelector('.table-fields-grid', { timeout: 15000 })
  await page.waitForTimeout(800)

  const pkBtn = page.locator('.table-fields-grid .constraint-config-btn').first()
  if ((await pkBtn.count()) === 0) {
    await page.screenshot({ path: debugPath, fullPage: true })
    throw new Error(`PK generation button not found (FU ${fuId}). Debug: ${debugPath}`)
  }
  await pkBtn.click()
  await page.waitForSelector('.pk-generation-popover', { timeout: 8000 })
  return page.locator('.pk-generation-popover').last()
}

async function clickVisibleOption(page, pattern) {
  const wrap = page.locator('.pk-generation-popover .el-select-dropdown .el-scrollbar__wrap, .el-select-dropdown:visible .el-scrollbar__wrap').last()
  if (await wrap.count()) {
    await wrap.evaluate((el) => { el.scrollTop = el.scrollHeight })
  }
  await page.waitForTimeout(200)
  const opt = page.locator('.el-select-dropdown__item').filter({ hasText: pattern })
  if ((await opt.count()) === 0) {
    throw new Error(`Strategy option missing: ${pattern}`)
  }
  await opt.last().click({ force: true })
  await page.waitForTimeout(500)
}

async function captureCustomEditor(page, popover, slugSuffix) {
  await popover.locator('.el-select').first().click()
  await page.waitForTimeout(400)
  const dropdownText = (await page.locator('.el-select-dropdown:visible').last().innerText()) ?? ''
  if (/Date prefixed number|日期前缀编号|日期前綴編號/.test(dropdownText)) {
    throw new Error(`Date prefixed strategy still listed. Text: ${dropdownText.slice(0, 400)}`)
  }
  if (!CUSTOM_OPTION.test(dropdownText)) {
    throw new Error(`Custom format missing from strategy list. Text: ${dropdownText.slice(0, 400)}`)
  }
  const dropdownShot = shot(`dw-table-pk-custom-format-options-${slugSuffix}`)
  await page.screenshot({ path: dropdownShot })
  await clickVisibleOption(page, CUSTOM_OPTION)
  await page.waitForTimeout(500)

  const customBodyEl = page.locator('.pk-generation-popover:visible .pk-popover-body').filter({
    hasText: /Sequence reset|序号重置|序號重置/,
  }).last()
  await customBodyEl.waitFor({ state: 'visible', timeout: 5000 })
  const customBody = (await customBodyEl.innerText()) ?? ''
  if (!/\d{4}-\d{2}-\d{2}-\d+/.test(customBody)) {
    throw new Error(`Custom preview missing. Text: ${customBody.slice(0, 500)}`)
  }
  if (!/Append|追加/.test(customBody)) {
    throw new Error(`Custom append control missing. Text: ${customBody.slice(0, 400)}`)
  }
  const customShot = shot(`dw-table-pk-custom-format-editor-${slugSuffix}`)
  await customBodyEl.scrollIntoViewIfNeeded()
  await page.waitForTimeout(200)
  await page.screenshot({ path: customShot })
  return { dropdownShot, customShot }
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}),
  })
  const page = await (await browser.newContext({ viewport: { width: 1680, height: 1100 } })).newPage()
  const debugPath = shot('pk-custom-format-debug')

  try {
    await loginViaDwPassword(page, { loginOrigin: ORIGIN })
    const listed = await listFunctionUnitIds(page)
    const preferred = [process.argv[2], process.argv[3], process.env.DW_FU_ID, process.env.DW_FU_ID_2, '50005']
      .filter(Boolean)
      .map((id) => Number(id))
    const fuIds = [...new Set([...preferred, ...listed])].filter((id) => Number.isFinite(id))
    const captured = []
    for (const fuId of fuIds) {
      if (captured.length >= 2) break
      if (!(await functionUnitHasTables(page, fuId))) continue
      const popover = await openPkPopover(page, fuId, debugPath)
      if (!popover) continue
      const shots = await captureCustomEditor(page, popover, `fu${fuId}`)
      captured.push({ fuId, ...shots })
      console.log(`OK: FU ${fuId} options=${shots.dropdownShot}`)
      console.log(`OK: FU ${fuId} custom=${shots.customShot}`)
    }
    if (captured.length < 2) {
      throw new Error(`Need PK editor on two Function Units, got ${captured.length}. ids=${fuIds.slice(0, 8).join(',')}`)
    }
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
