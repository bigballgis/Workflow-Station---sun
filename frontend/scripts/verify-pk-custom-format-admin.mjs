#!/usr/bin/env node
/**
 * Admin Center Relation Table PK editor: custom format with sequence reset.
 *
 * Usage (from frontend/):
 *   node scripts/verify-pk-custom-format-admin.mjs [relationTableId]
 */

import { mkdirSync } from 'fs'
import { dirname, join } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaAdminPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(__dirname, '..', 'admin-center', 'verification-screenshots')
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

async function clickVisibleOption(page, pattern) {
  const wrap = page.locator('.el-select-dropdown:visible .el-scrollbar__wrap').last()
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

async function firstRelationTableId(page) {
  const given = process.argv[2] || process.env.AC_RT_ID
  if (given) return given
  const res = await page.request.get(`${ORIGIN}/api/v1/admin/relation-tables/structures`)
  const body = await res.json().catch(() => ({}))
  const rows = Array.isArray(body.data)
    ? body.data
    : (body.data?.content ?? body.data?.records ?? (Array.isArray(body) ? body : []))
  return rows[0]?.id ?? null
}

async function ensurePkEditor(page, debugPath) {
  await page.locator('.el-table').first().evaluate((el) => {
    const wrap = el.querySelector('.el-table__body-wrapper')
    if (wrap) wrap.scrollLeft = wrap.scrollWidth
  }).catch(() => {})
  if (await page.locator('.pk-generation-editor .pk-strategy-select').count()) {
    return page.locator('.pk-generation-editor .pk-strategy-select').first()
  }
  const addBtn = page.locator('button').filter({ hasText: /Add Field|添加字段|新增欄位/ }).first()
  if (await addBtn.count()) {
    await addBtn.click()
    await page.waitForTimeout(400)
  }
  const pkSwitch = page.locator('.pk-cell .el-switch:not(.is-disabled)').first()
  if (await pkSwitch.count()) {
    await pkSwitch.click()
    await page.waitForTimeout(500)
  }
  const pkSelect = page.locator('.pk-generation-editor .pk-strategy-select').first()
  if ((await pkSelect.count()) === 0) {
    await page.screenshot({ path: debugPath, fullPage: true })
    throw new Error(`PK generation editor not found. Debug: ${debugPath}`)
  }
  return pkSelect
}

async function main() {
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}),
  })
  const page = await (await browser.newContext({ viewport: { width: 1680, height: 1100 } })).newPage()
  const debugPath = shot('pk-custom-format-admin-debug')

  try {
    await loginViaAdminPassword(page, { loginOrigin: ORIGIN })
    const tableId = await firstRelationTableId(page)
    if (tableId) {
      await page.goto(`${ORIGIN}/admin/relation-tables/structure/${tableId}/edit`, {
        waitUntil: 'domcontentloaded',
      })
    } else {
      await page.goto(`${ORIGIN}/admin/relation-tables/structure/create`, {
        waitUntil: 'domcontentloaded',
      })
    }
    await page.waitForTimeout(2500)

    const pkSelect = await ensurePkEditor(page, debugPath)
    await pkSelect.click()
    await page.waitForTimeout(400)
    const dropdownText = (await page.locator('.el-select-dropdown:visible').last().innerText()) ?? ''
    if (/Date prefixed number|日期前缀编号|日期前綴編號/.test(dropdownText)) {
      throw new Error(`Date prefixed strategy still listed. Text: ${dropdownText.slice(0, 400)}`)
    }
    const optionsShot = shot('ac-table-pk-custom-format-options')
    await page.screenshot({ path: optionsShot })
    await clickVisibleOption(page, CUSTOM_OPTION)

    const gear = page.locator('.pk-generation-editor .pk-config-btn').first()
    if ((await gear.count()) === 0) {
      await page.screenshot({ path: debugPath, fullPage: true })
      throw new Error(`PK settings button not found. Debug: ${debugPath}`)
    }
    await gear.click()
    await page.waitForTimeout(500)
    const popover = page.locator('.el-popover, .el-popper').filter({ hasText: /Sequence reset|序号重置|序號重置/ }).last()
    await popover.waitFor({ state: 'visible', timeout: 8000 })
    const body = (await popover.innerText()) ?? ''
    if (!/\d{4}-\d{2}-\d{2}-\d+/.test(body) && !/Preview|示例|範例/.test(body)) {
      throw new Error(`Custom PK settings missing preview. Text: ${body.slice(0, 500)}`)
    }
    const editorShot = shot('ac-table-pk-custom-format-editor')
    await popover.screenshot({ path: editorShot })
    console.log(`OK: options=${optionsShot}`)
    console.log(`OK: custom=${editorShot}`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
