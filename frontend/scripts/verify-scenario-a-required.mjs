/**
 * Scenario A → Start date / Need-by date required (Portal start + DW Form Preview).
 * Usage: node scripts/verify-scenario-a-required.mjs
 */
import { mkdirSync } from 'fs'
import { dirname, join } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const origin = (process.env.PORTAL_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const date = new Date().toISOString().slice(0, 10)

function dateItem(page, label) {
  return page.locator('.el-form-item').filter({ hasText: label }).first()
}

async function selectScenarioA(root, page) {
  const item = root.locator('.el-form-item').filter({ hasText: 'Scenario' }).first()
  await item.locator('.el-select').click()
  await page.locator('body > .el-select-dropdown:visible, .el-popper:visible .el-select-dropdown').locator('.el-select-dropdown__item').filter({ hasText: /^A$/ }).first().click()
  await page.waitForTimeout(800)
}

function requiredHit(item) {
  return item.evaluate((el) => {
    const required = el.classList.contains('is-required')
    const star = !!el.querySelector('.el-form-item__label .el-form-item__label-wrap, .el-form-item__label')
      && (el.querySelector('.el-form-item__label')?.classList.contains('required')
        || getComputedStyle(el.querySelector('.el-form-item__label') || el, '::before').content.includes('*')
        || el.classList.contains('is-required'))
    return required || star
  })
}

async function assertDatesRequired(page, where) {
  const start = dateItem(page, 'Start date')
  const end = dateItem(page, 'Need-by date')
  await start.waitFor({ timeout: 15000 })
  const startReq = await requiredHit(start)
  const endReq = await requiredHit(end)
  console.log(`[${where}] startRequired=${startReq} endRequired=${endReq}`)
  if (!startReq || !endReq) {
    throw new Error(`${where}: Start date / Need-by date not required after Scenario A`)
  }
}

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } })
const page = await context.newPage()

try {
  await loginViaPortalPassword(page)
  const processKey = process.env.PROCESS_KEY || 'Process_HelpPurchaseRequest'
  await page.goto(`${origin}/portal/processes/start/${processKey}`, { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.waitForTimeout(4000)
  const bodyText = await page.locator('body').innerText().catch(() => '')
  if (/FUNCTION_UNIT_NOT_FOUND|Load Failed|404/i.test(bodyText) && !/Scenario/i.test(bodyText)) {
    throw new Error(`Portal start page failed for ${processKey}`)
  }
  await selectScenarioA(page, page)
  await assertDatesRequired(page, 'portal')
  const portalDir = join(__dirname, '../user-portal/verification-screenshots')
  mkdirSync(portalDir, { recursive: true })
  const portalShot = join(portalDir, `${date}_scenario-a-required-portal.png`)
  await page.screenshot({ path: portalShot, fullPage: true })
  console.log('[ok]', portalShot)

  await loginViaDwPassword(page)
  const fuId = process.env.HELP_GUIDE_FU_ID || '50012'
  await page.goto(`${origin}/dev/function-units/${fuId}`, { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.waitForTimeout(3000)
  const formTab = page.locator('.el-tabs__item').filter({ hasText: /Form Design|表单设计|表單設計/ })
  await formTab.first().click({ timeout: 15000 })
  const requestRow = page.locator('.el-table__body tr:visible').filter({ hasText: /Request Form/ })
  await requestRow.first().waitFor({ timeout: 30000 })
  const editForm = requestRow.locator('button').filter({ hasText: /Edit|编辑|編輯/ }).first()
  if ((await editForm.count()) > 0) {
    await editForm.click()
    await page.waitForTimeout(2500)
  }
  const previewBtn = page.locator('button').filter({ hasText: /^(Preview|预览|預覽)$/ }).first()
  const dwDir = join(__dirname, '../developer-workstation/verification-screenshots')
  mkdirSync(dwDir, { recursive: true })
  if ((await previewBtn.count()) === 0) {
    const debugShot = join(dwDir, `${date}_scenario-a-required-preview-debug.png`)
    await page.screenshot({ path: debugShot, fullPage: true })
    throw new Error(`DW Form Preview button not found. Debug: ${debugShot}`)
  }
  await previewBtn.click()
  await page.waitForTimeout(3500)
  const dlg = page.locator('.el-dialog:visible .el-dialog__body, .el-overlay:visible .el-dialog').last()
  const previewRoot = (await dlg.count()) > 0 ? dlg : page
  await selectScenarioA(previewRoot, page)
  await assertDatesRequired(previewRoot, 'dw-preview')
  const dwShot = join(dwDir, `${date}_scenario-a-required-preview.png`)
  await page.screenshot({ path: dwShot, fullPage: true })
  console.log('[ok]', dwShot)

  await page.keyboard.press('Escape')
  await page.waitForTimeout(500)
  const subNav = page.locator('.designer-nav-btn, .el-dropdown').filter({ hasText: /Sub Table|子表/ }).first()
  if (await subNav.count()) await subNav.click()
  const lineItem = page.locator('.el-dropdown-menu__item, .dropdown-item-label').filter({ hasText: /Line items|help_pr_line/ }).first()
  if (await lineItem.count()) {
    await lineItem.click()
    await page.waitForTimeout(1500)
  }
  const nativePreview = page.locator('.fc-designer-wrapper:visible button').filter({ has: page.locator('.icon-preview') }).first()
  await nativePreview.click({ timeout: 15000 })
  await page.waitForTimeout(2500)
  const nativeDlg = page.locator('.el-dialog:visible .el-dialog__body').last()
  await selectScenarioA(nativeDlg, page)
  await assertDatesRequired(nativeDlg, 'dw-native-preview')
  const nativeShot = join(dwDir, `${date}_scenario-a-required-native-preview.png`)
  await page.screenshot({ path: nativeShot, fullPage: true })
  console.log('[ok]', nativeShot)
} finally {
  await browser.close()
}

