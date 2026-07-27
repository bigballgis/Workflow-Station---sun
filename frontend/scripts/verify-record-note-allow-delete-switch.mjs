#!/usr/bin/env node
/**
 * Developer Workstation — Record Note 组件属性面板应出现 "Allow Delete" 开关（默认关闭）。
 *
 * Usage: node scripts/verify-record-note-allow-delete-switch.mjs [functionUnitId]
 */
import { mkdirSync } from 'fs'
import { join } from 'path'
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const FU_ID = process.argv[2] || process.env.DW_FU_ID || '50018'
const ORIGIN = (process.env.VERIFY_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const OUT_DIR = join(process.cwd(), 'developer-workstation', 'verification-screenshots')

function shot(slug) {
  const date = new Date().toISOString().slice(0, 10)
  mkdirSync(OUT_DIR, { recursive: true })
  return join(OUT_DIR, `${date}_${slug}.png`)
}

const fails = []
function check(ok, message) {
  console.log(`${ok ? 'PASS' : 'FAIL'}: ${message}`)
  if (!ok) fails.push(message)
}

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1680, height: 1200 } })).newPage()
  page.on('pageerror', (e) => console.log('[pageerror]', e.message))

  await loginViaUnifiedSso(page, 'dw')
  await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(5000)
  await page.locator('.el-tabs__item').filter({ hasText: /form design/i }).first().click().catch(() => {})
  await page.waitForTimeout(4000)

  // 打开表单设计器
  const editBtn = page.locator('button, .el-link, td .cell').filter({ hasText: /^\s*Edit\s*$/i }).first()
  await editBtn.click({ force: true }).catch(() => {})
  await page.waitForSelector('._fc-l, .form-designer, ._fc-m-drag', { timeout: 25000 }).catch(() => {})
  await page.waitForTimeout(4000)
  await page.screenshot({ path: shot('dw-form-designer-open'), fullPage: true })

  // 选中画布上的 Record Note 占位组件 → 右侧属性面板
  const widget = page.locator('.record-note-placeholder-widget, [class*="record-note"]').first()
  if (await widget.count()) await widget.click({ force: true }).catch(() => {})
  else await page.locator('div').filter({ hasText: /^Record Note$/i }).first().click({ force: true }).catch(() => {})
  await page.waitForTimeout(2500)
  await page.screenshot({ path: shot('dw-record-note-props-panel'), fullPage: true })

  const bodyText = (await page.locator('body').textContent()) ?? ''
  check(bodyText.includes('Allow Delete'), 'property panel exposes the "Allow Delete" switch')
  check(bodyText.includes('Allow Edit Own Notes'), 'existing note switches are still present')

  // 默认关闭：该开关的 el-switch 不带 is-checked
  const deleteRow = page.locator('.el-form-item').filter({ hasText: /Allow Delete/i }).first()
  const switchClass = (await deleteRow.locator('.el-switch').first().getAttribute('class').catch(() => '')) ?? ''
  check(!!switchClass && !switchClass.includes('is-checked'),
    `Allow Delete defaults to OFF (switch class: "${switchClass}")`)

  console.log('screenshots:', OUT_DIR)
  await browser.close()
  if (fails.length) {
    console.error(`\n${fails.length} check(s) FAILED:\n` + fails.map((f) => ' - ' + f).join('\n'))
    process.exit(2)
  }
  console.log('\nALL CHECKS PASSED')
}

main().catch((e) => { console.error('ERROR:', e.message); process.exit(1) })
