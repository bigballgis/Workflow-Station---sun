/**
 * Add Record: Scenario A must show date asterisks without immediate "is required" errors.
 * Usage: node scripts/verify-subtable-required-no-immediate-error.mjs
 */
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const origin = (process.env.PORTAL_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const date = new Date().toISOString().slice(0, 10)
const alwaysRequired = ['Item', 'Quantity', 'Unit price']
const dateLabels = ['Start date', 'Need-by date']

function shotDir(app) {
  const dir = join(__dirname, `../${app}/verification-screenshots`)
  mkdirSync(dir, { recursive: true })
  return dir
}

async function addRecordRoot(page) {
  const nested = page.locator('.sub-table-nested-modal-panel:visible')
  if ((await nested.count()) > 0) return nested.first()
  return page.locator('.el-dialog:visible').last()
}

async function selectScenarioA(root, page) {
  const item = root.locator('.el-form-item').filter({ hasText: 'Scenario' }).first()
  await item.locator('.el-select').click()
  await page
    .locator('body > .el-select-dropdown:visible, .el-popper:visible .el-select-dropdown')
    .locator('.el-select-dropdown__item')
    .filter({ hasText: /^A$/ })
    .first()
    .click()
  await page.waitForTimeout(800)
}

async function inspectDialog(page) {
  const dialog = await addRecordRoot(page)
  return dialog.evaluate((dlg, labels) => {
    const itemFor = (label) =>
      [...dlg.querySelectorAll('.el-form-item')].find((el) => {
        const text = el.querySelector('.el-form-item__label')?.textContent?.trim() ?? ''
        return text.includes(label)
      })
    const required = {}
    const errors = {}
    for (const label of labels) {
      const item = itemFor(label)
      required[label] = !!item?.classList.contains('is-required')
      errors[label] = (item?.querySelector('.el-form-item__error')?.textContent ?? '').trim()
    }
    const anyError = [...dlg.querySelectorAll('.el-form-item__error')]
      .map((el) => el.textContent?.trim())
      .filter(Boolean)
    return { required, errors, anyError }
  }, [...alwaysRequired, ...dateLabels])
}

async function assertNoImmediateErrors(page, where) {
  const info = await inspectDialog(page)
  console.log(`[${where}]`, JSON.stringify(info))
  for (const label of dateLabels) {
    if (!info.required[label]) {
      throw new Error(`${where}: ${label} missing required asterisk after Scenario A`)
    }
  }
  const dirty = [...alwaysRequired, ...dateLabels].filter((label) => info.errors[label])
  if (dirty.length > 0 || info.anyError.length > 0) {
    throw new Error(`${where}: immediate errors after Scenario A: ${JSON.stringify(info)}`)
  }
}

async function openPortalAddRecord(page) {
  const processKey = process.env.PROCESS_KEY || 'Process_HelpPurchaseRequest'
  await page.goto(`${origin}/portal/processes/start/${processKey}`, {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  })
  await page.waitForTimeout(4000)
  const addBtn = page.locator('.sub-table-field').filter({ hasText: /Line items/i }).locator('button').filter({
    hasText: /Add|新增|添加/,
  }).first()
  await addBtn.waitFor({ state: 'visible', timeout: 20000 })
  await addBtn.click()
  await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 10000 })
  await page.waitForTimeout(500)
}

async function openDwPreviewAddRecord(page) {
  const fuId = process.env.HELP_GUIDE_FU_ID || '50012'
  await page.goto(`${origin}/dev/function-units/${fuId}`, { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.waitForTimeout(3000)
  await page.locator('.el-tabs__item').filter({ hasText: /Form Design|表单设计|表單設計/ }).first().click({ timeout: 15000 })
  await page.waitForTimeout(2500)
  const editForm = page.locator('.el-table__body tr:visible').filter({ hasText: /Request Form/ }).locator('button').filter({
    hasText: /Edit|编辑|編輯/,
  }).first()
  if ((await editForm.count()) > 0) {
    await editForm.click()
    await page.waitForTimeout(2500)
  }
  const previewBtn = page.locator('button').filter({ hasText: /^(Preview|预览|預覽)$/ }).first()
  if ((await previewBtn.count()) === 0) throw new Error('DW Form Preview button not found')
  await previewBtn.click()
  await page.waitForTimeout(3500)
  const addBtn = page.locator('.el-dialog:visible, .el-overlay:visible').locator('button').filter({
    hasText: /Add Record|Add|新增/,
  }).first()
  await addBtn.waitFor({ state: 'visible', timeout: 15000 })
  await addBtn.click()
  await page.locator('.sub-table-nested-modal-panel:visible').waitFor({ state: 'visible', timeout: 10000 })
  await page.waitForTimeout(500)
}

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } })
const page = await context.newPage()

try {
  await loginViaPortalPassword(page)
  await openPortalAddRecord(page)
  const portalDlg = await addRecordRoot(page)
  await selectScenarioA(portalDlg, page)
  await assertNoImmediateErrors(page, 'portal-add')
  const portalShot = join(shotDir('user-portal'), `${date}_subtable-required-no-immediate-error.png`)
  await portalDlg.screenshot({ path: portalShot })
  console.log('[ok]', portalShot)

  await loginViaDwPassword(page)
  await openDwPreviewAddRecord(page)
  const dwDlg = await addRecordRoot(page)
  await selectScenarioA(dwDlg, page)
  await assertNoImmediateErrors(page, 'dw-preview-add')
  const dwShot = join(shotDir('developer-workstation'), `${date}_subtable-required-no-immediate-error.png`)
  await dwDlg.screenshot({ path: dwShot })
  console.log('[ok]', dwShot)
} finally {
  await browser.close()
}
