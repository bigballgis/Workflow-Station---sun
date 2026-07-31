/**
 * Screenshot gate for SubTable Add Record Form-level onCreated/onMounted + select change hide.
 *
 * PLACEHOLDER — requires a real Portal task/process URL with a sub-form that:
 *   - Form onMounted (or onCreated) hides a field via api.hidden, and/or
 *   - select change hides lookup
 *
 * Usage (when URL is ready):
 *   PORTAL_TASK_URL="http://localhost:3000/portal/tasks/<taskId>" \
 *     node scripts/verify-subtable-dialog-form-lifecycle.mjs
 *
 * Optional:
 *   SUBTABLE_HINT=substring of sub-table title (default: empty = first sub-table)
 *   LOGIN_USER / LOGIN_PASS
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const taskUrl = process.env.PORTAL_TASK_URL || process.argv[2] || ''
const subTableHint = (process.env.SUBTABLE_HINT || process.argv[3] || '').toLowerCase()
const outDir = join(__dirname, '../user-portal/verification-screenshots')
mkdirSync(outDir, { recursive: true })
const date = new Date().toISOString().slice(0, 10)
const slug = 'subtable-dialog-form-lifecycle'

if (!taskUrl) {
  console.error(
    '[verify-subtable-dialog-form-lifecycle] SKIP: set PORTAL_TASK_URL (or argv[2]) to a Portal task/process URL, then re-run.',
  )
  console.error(
    '  Example: PORTAL_TASK_URL="http://localhost:3000/portal/tasks/<id>" node scripts/verify-subtable-dialog-form-lifecycle.mjs',
  )
  process.exit(2)
}

const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const page = await browser.newPage({ viewport: { width: 1400, height: 900 } })

try {
  await loginViaPortalPassword(page)
  await page.goto(taskUrl, { waitUntil: 'networkidle', timeout: 60000 })
  await page.waitForTimeout(2000)

  const blocks = page.locator('.sub-table-field')
  const count = await blocks.count()
  if (count === 0) throw new Error('No .sub-table-field on page')

  let target = blocks.first()
  if (subTableHint) {
    let found = null
    for (let i = 0; i < count; i++) {
      const block = blocks.nth(i)
      const title = ((await block.locator('.title').textContent().catch(() => '')) || '').toLowerCase()
      if (title.includes(subTableHint)) {
        found = block
        break
      }
    }
    if (!found) throw new Error(`No sub-table title matching "${subTableHint}"`)
    target = found
  }

  await target.locator('button').filter({ hasText: /add|新增|添加/i }).first().click()
  await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 10000 })
  await page.waitForTimeout(500)

  const dialog = page.locator('.el-dialog').last()
  const openPath = join(outDir, `${date}_${slug}-open.png`)
  await dialog.screenshot({ path: openPath })
  console.log('[ok] dialog open screenshot:', openPath)

  const select = dialog.locator('.el-select').filter({ has: page.locator('xpath=ancestor::*[contains(., "select") or contains(., "Select")]') }).first()
  if ((await select.count()) > 0) {
    await select.click()
    const opt = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first()
    if ((await opt.count()) > 0) {
      await opt.click()
      await page.waitForTimeout(300)
      const afterPath = join(outDir, `${date}_${slug}-after-select.png`)
      await dialog.screenshot({ path: afterPath })
      console.log('[ok] after select change screenshot:', afterPath)
    }
  }

  console.log('[ok] verify-subtable-dialog-form-lifecycle finished')
} finally {
  await browser.close()
}
