/**
 * Verify sub-table Add dialog shows form-design canvas fields only (not list-view audit columns).
 * Usage: node scripts/verify-subtable-add-dialog-canvas-fields.mjs [fuCode] [subTableTitleSubstring]
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const fuCode = process.argv[2] || 'fu-20260422-23tfag'
const subTableHint = (process.argv[3] || 'testauditinfo').toLowerCase()
const outDir = join(__dirname, '../user-portal/verification-screenshots')
mkdirSync(outDir, { recursive: true })
const date = new Date().toISOString().slice(0, 10)
const slug = `subtable-add-dialog-canvas-${subTableHint.replace(/\W+/g, '-')}`

const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const page = await browser.newPage({ viewport: { width: 1400, height: 900 } })

try {
  await loginViaPortalPassword(page)
  const url = `http://localhost:3000/portal/processes/start/${fuCode}`
  await page.goto(url, { waitUntil: 'networkidle', timeout: 60000 })
  await page.waitForTimeout(2000)

  const subTableBlocks = page.locator('.sub-table-field')
  const count = await subTableBlocks.count()
  let target = null
  for (let i = 0; i < count; i++) {
    const block = subTableBlocks.nth(i)
    const title = ((await block.locator('.title').textContent()) || '').toLowerCase()
    if (title.includes(subTableHint)) {
      target = block
      break
    }
  }
  if (!target) {
    const titles = []
    for (let i = 0; i < count; i++) {
      titles.push(await subTableBlocks.nth(i).locator('.title').textContent())
    }
    throw new Error(`Sub-table matching "${subTableHint}" not found. Found: ${titles.join(', ')}`)
  }

  await target.screenshot({ path: join(outDir, `${date}_${slug}-table.png`) })
  await target.locator('button').filter({ hasText: /add|新增|添加/i }).first().click()
  await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 10000 })
  await page.waitForTimeout(500)

  const dialog = page.locator('.el-dialog').last()
  const testinfoInput = dialog.locator('input').filter({ has: page.locator('xpath=ancestor::el-form-item[contains(., "testinfo") or contains(., "Testinfo")]') }).first()
  const anyTextInput = dialog.locator('.el-form-item input:not([disabled])').first()
  const fillTarget = (await testinfoInput.count()) > 0 ? testinfoInput : anyTextInput
  if ((await fillTarget.count()) > 0) {
    await fillTarget.fill(`verify-${Date.now()}`)
  }
  await dialog.locator('button').filter({ hasText: /save|保存/i }).last().click()
  await page.waitForSelector('.el-dialog', { state: 'hidden', timeout: 10000 }).catch(() => {})
  await page.waitForTimeout(800)

  await target.screenshot({ path: join(outDir, `${date}_${slug}-table-after-save.png`) })

  const tableText = ((await target.textContent()) || '').toLowerCase()
  const hasAuditValueInTable =
    /\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}/.test(tableText)
    || /developer|user-dev/.test(tableText)
  if (!hasAuditValueInTable) {
    console.warn('WARN: table row may not show audit timestamps after save — check screenshot')
  }

  await target.locator('button').filter({ hasText: /add|新增|添加/i }).first().click()
  await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 10000 })
  await page.waitForTimeout(500)

  const dialog2 = page.locator('.el-dialog').last()
  await dialog2.screenshot({ path: join(outDir, `${date}_${slug}-dialog.png`) })

  const dialogText = ((await dialog2.textContent()) || '').toLowerCase()
  const auditHits = ['created_at', 'updated_at', 'created by', 'updated by', 'created_at', 'updated_at']
    .filter(k => dialogText.includes(k.replace('_', ' ')) || dialogText.includes(k))

  const canvasHits = ['testinfo', 'main_id'].filter(k => dialogText.includes(k))
  console.log('Dialog fields check:', { canvasHits, auditHits, dialogSnippet: dialogText.slice(0, 400) })

  if (auditHits.length > 0) {
    throw new Error(`Add dialog still shows list-view audit fields: ${auditHits.join(', ')}`)
  }
  if (canvasHits.length === 0 && subTableHint.includes('audit')) {
    console.warn('WARN: expected canvas field "testinfo" not found in dialog text — verify sub-table title hint')
  }

  console.log('OK: Add dialog excludes list-view audit columns')
  console.log(`Screenshots: ${join(outDir, `${date}_${slug}-table.png`)}`)
  console.log(`Screenshots: ${join(outDir, `${date}_${slug}-table-after-save.png`)}`)
  console.log(`Screenshots: ${join(outDir, `${date}_${slug}-dialog.png`)}`)
} finally {
  await browser.close()
}
