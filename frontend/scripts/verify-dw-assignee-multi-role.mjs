#!/usr/bin/env node
/**
 * Developer Workstation — UserTask assignee multi-role select (role names, not UUIDs).
 * Vincent Test FU: test1 node uses INITIATOR_BU_ROLE with roleIds test1 + test1001.
 *
 * Usage: node scripts/verify-dw-assignee-multi-role.mjs [functionUnitId]
 */
import { mkdirSync } from 'fs'
import { join } from 'path'
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const FU_ID = process.argv[2] || process.env.DW_FU_ID || '50006'
const ORIGIN = (process.env.VERIFY_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const OUT_DIR = join(process.cwd(), 'developer-workstation', 'verification-screenshots')
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function screenshotPath(slug) {
  const date = new Date().toISOString().slice(0, 10)
  mkdirSync(OUT_DIR, { recursive: true })
  return join(OUT_DIR, `${date}_${slug}.png`)
}

function panelFormItem(page, labelRe) {
  return page.locator('.node-properties-panel .el-form-item').filter({ hasText: labelRe })
}

async function clickUserTask(page, taskName) {
  const byId = page.locator('[data-element-id="Activity_01oxxxy"]')
  if ((await byId.count()) > 0) {
    await byId.click({ force: true })
    return
  }
  await page.locator('.djs-label').filter({ hasText: new RegExp(`^${taskName}$`, 'i') }).first().click({ force: true })
}

async function readMultiSelectLabels(page) {
  const panel = page.locator('.node-properties-panel')
  const tags = await panel.locator('.el-select__selected-item .el-tag, .el-select__tags-text').allTextContents()
  const collapsed = await panel.locator('.el-select__wrapper').first().textContent()
  return [...tags, collapsed ?? ''].map(s => s.trim()).filter(Boolean)
}

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 1200 } })).newPage()

  await loginViaUnifiedSso(page, 'dw')
  const url = `${ORIGIN}/dev/function-units/${FU_ID}`
  await page.goto(url, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(3000)

  await page.locator('#tab-process, .el-tabs__item').filter({ hasText: /process/i }).first().click().catch(() => {})
  await page.waitForSelector('.bpmn-canvas .djs-container', { timeout: 45000 })
  await page.waitForTimeout(4000)

  await clickUserTask(page, 'test1')
  await page.waitForSelector('.node-properties-panel', { timeout: 15000 })
  await page.waitForTimeout(2500)

  const rolesItem = panelFormItem(page, /Select Roles/i)
  if ((await rolesItem.count()) === 0) {
    console.error('FAIL: Select Roles multi-select not visible on test1 node')
    process.exit(1)
  }

  const assigneeTypeText = await panelFormItem(page, /Assignee Type/i).textContent()
  if (!/Initiator BU Role|发起人.*BU.*角色/i.test(assigneeTypeText ?? '')) {
    console.warn('[warn] assignee type label:', assigneeTypeText?.slice(0, 120))
  }

  const labelsBeforeOpen = await readMultiSelectLabels(page)
  const uuidOnlyTags = labelsBeforeOpen.filter(t => UUID_RE.test(t))
  if (uuidOnlyTags.length > 0) {
    console.error('FAIL: multi-select shows UUID tags:', uuidOnlyTags)
    process.exit(1)
  }

  const hasRoleName = labelsBeforeOpen.some(t => /test1|test1001/i.test(t))
    || (await rolesItem.locator('.el-select__wrapper').textContent() ?? '').match(/test1|test1001/i)
  if (!hasRoleName) {
    console.error('FAIL: expected role names test1/test1001 in multi-select, got:', labelsBeforeOpen)
    process.exit(1)
  }

  const panelShot = screenshotPath('dw-assignee-multi-role-panel')
  await page.locator('.properties-panel-container').screenshot({ path: panelShot })

  await rolesItem.locator('.el-select').click()
  await page.waitForSelector('.el-select-dropdown:visible', { timeout: 8000 })
  await page.waitForTimeout(800)

  const optionTexts = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').allTextContents()
  const uuidOptions = optionTexts.filter(t => UUID_RE.test(t.trim()))
  if (uuidOptions.length > 0) {
    console.error('FAIL: dropdown options show UUIDs:', uuidOptions)
    process.exit(1)
  }
  if (!optionTexts.some(t => /test1001/i.test(t))) {
    console.error('FAIL: dropdown missing test1001 option. Options:', optionTexts)
    process.exit(1)
  }

  const dropdownShot = screenshotPath('dw-assignee-multi-role-dropdown')
  await page.locator('.properties-panel-container').screenshot({ path: dropdownShot })

  console.log('PASS: multi-role select shows role names (not UUIDs)')
  console.log('[saved]', panelShot)
  console.log('[saved]', dropdownShot)
  await browser.close()
}

main().catch(err => {
  console.error('[verify-dw-assignee-multi-role] FAILED:', err.message)
  process.exit(1)
})
