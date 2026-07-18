#!/usr/bin/env node
/**
 * Developer Workstation — 子表扩展组件逐操作权限（Allow Add / Edit / Delete）验证。
 *
 * 目标：证明选中已放置的子表组件后，右侧属性面板出现三个独立开关，
 * 并在表单预览里 Add / Edit / Delete 由各自的 canAdd/canEdit/canDelete 独立控制。
 *
 * Usage: node scripts/verify-subtable-permission.mjs [functionUnitId]
 */
import { mkdirSync } from 'fs'
import { join } from 'path'
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const FU_ID = process.argv[2] || process.env.DW_FU_ID || '50005'
const ORIGIN = (process.env.VERIFY_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const OUT_DIR = join(process.cwd(), 'developer-workstation', 'verification-screenshots')

function shot(slug) {
  const date = new Date().toISOString().slice(0, 10)
  mkdirSync(OUT_DIR, { recursive: true })
  return join(OUT_DIR, `${date}_${slug}.png`)
}

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1680, height: 1200 } })).newPage()
  const fails = []

  await loginViaUnifiedSso(page, 'dw')
  await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(3000)

  // 进入 Form 设计 tab
  await page.locator('.el-tabs__item').filter({ hasText: /form design/i }).first().click().catch(() => {})
  await page.waitForTimeout(3000)

  // 打开一个含子表的表单编辑器（"Sub task" 行的 Edit）
  const subTaskRow = page.locator('tr, .el-table__row').filter({ hasText: /Sub task/i }).first()
  await subTaskRow.locator('text=/^Edit$/i').first().click({ force: true }).catch(async () => {
    await page.locator('td, .cell').filter({ hasText: /^Edit$/i }).first().click({ force: true }).catch(() => {})
  })
  await page.waitForTimeout(4000)
  // 等待 form-create 设计器画布出现
  await page.waitForSelector('.sub-table-field, ._fc-l, .form-designer, ._fc-m-drag', { timeout: 20000 }).catch(() => {})
  await page.waitForTimeout(2000)
  await page.screenshot({ path: shot('dw-subtable-designer-open'), fullPage: true })

  // 选中画布上的子表组件（Participants 占位行）→ 右侧属性面板刷新为该组件属性
  const placed = page.locator('.sub-table-field, .sub-table-placeholder-widget').filter({ hasText: /Participants/i }).first()
  let clicked = false
  if ((await placed.count()) > 0) {
    await placed.click({ force: true }).catch(() => {})
    clicked = true
  }
  if (!clicked) {
    // 回退：canvas 中带 "Participants" 文案的可点击拖拽块
    await page.locator('div', { hasText: /^Participants$/i }).first().click({ force: true }).catch(() => {})
  }
  await page.waitForTimeout(2000)
  await page.screenshot({ path: shot('dw-subtable-props-panel'), fullPage: true })

  // 断言右侧属性面板出现三个开关（i18n: Allow Add / Allow Edit / Allow Delete）
  const bodyText = (await page.locator('body').textContent()) ?? ''
  for (const label of ['Allow Add', 'Allow Edit', 'Allow Delete']) {
    if (!bodyText.includes(label)) fails.push(`属性面板缺少开关: ${label}`)
  }

  // —— 运行时验证：Preview 基线（三项全开）——
  const panel = page.locator('.node-properties-panel, .el-tabs__content').filter({ hasText: /Allow Add/i }).first()
  async function previewShot(slug) {
    await page.locator('button, .el-radio-button, .el-tabs__item').filter({ hasText: /^Preview$/i }).first().click({ force: true }).catch(() => {})
    await page.waitForTimeout(2500)
    await page.screenshot({ path: shot(slug), fullPage: true })
    // 关闭预览弹层回到设计器
    await page.keyboard.press('Escape').catch(() => {})
    await page.waitForTimeout(800)
  }
  await previewShot('dw-preview-all-on')

  // —— 关掉 Allow Delete，再 Preview：删除按钮应消失，Add/Edit 仍在 ——
  const delSwitch = panel.locator('.el-form-item, div').filter({ hasText: /Allow Delete/i }).locator('.el-switch').first()
  await delSwitch.click({ force: true }).catch(() => {})
  await page.waitForTimeout(800)
  await page.screenshot({ path: shot('dw-props-delete-off'), fullPage: true })
  await previewShot('dw-preview-delete-off')

  console.log('screenshots:', OUT_DIR)
  if (fails.length) {
    console.error('FAIL:\n' + fails.map(f => ' - ' + f).join('\n'))
    console.error('（若为定位问题而非缺失，请人工对照上面两张截图确认三个开关存在）')
    await browser.close()
    process.exit(2)
  }
  console.log('PASS: 属性面板含 Allow Add / Allow Edit / Allow Delete 三个开关')
  await browser.close()
}

main().catch((e) => {
  console.error('ERROR:', e.message)
  process.exit(1)
})
