/**
 * Screenshot verification for the Email Templates feature (developer-workstation).
 * Captures: (1) Email Templates tab list, (2) New Template dialog with rich-text editor.
 */
import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = resolve(__dirname, '..', 'developer-workstation', 'verification-screenshots')
const BASE = process.env.DW_BASE || 'http://localhost:3000/dev'
const FU_ID = process.env.FU_ID || '50006'

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const browser = await chromium.launch({ headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1400, height: 1000 } })).newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errors.push('CONSOLE: ' + m.text()) })

  try {
    await loginViaUnifiedSso(page, 'dw', { loginOrigin: 'http://localhost:3000' })
    await page.goto(`${BASE}/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(4000)

    const tab = page.locator('.el-tabs__item', { hasText: /Email Templates|邮件模板|郵件範本/ }).first()
    await tab.waitFor({ timeout: 15000 })
    await tab.click()
    await page.waitForTimeout(2000)

    const listPath = join(OUT_DIR, `${datePrefix()}_email-templates-tab.png`)
    await page.screenshot({ path: listPath })
    console.log('[saved]', listPath)

    // Open New Template dialog
    const newBtn = page.locator('.email-template-designer .designer-toolbar .el-button', { hasText: /New Template|新建模板|新增範本/ }).first()
    await newBtn.click()
    await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 10000 })
    // wait for wangeditor toolbar to mount
    await page.waitForSelector('.w-e-toolbar', { timeout: 15000 }).catch(() => {})
    await page.waitForTimeout(2500)

    const dialogPath = join(OUT_DIR, `${datePrefix()}_email-template-editor.png`)
    await page.screenshot({ path: dialogPath })
    console.log('[saved]', dialogPath)

    const subjectRow = page.locator('.subject-field-row').first()
    await subjectRow.waitFor({ timeout: 10000 })
    const subjectPath = join(OUT_DIR, `${datePrefix()}_email-template-subject-field.png`)
    await subjectRow.screenshot({ path: subjectPath })
    console.log('[saved]', subjectPath)

    const placeholder = await page.locator('.subject-field-row input').first().getAttribute('placeholder')
    const hintText = await page.locator('.template-form .form-tip').first().textContent()
    console.log('SUBJECT_PLACEHOLDER:', placeholder)
    console.log('SUBJECT_HINT:', hintText?.trim())
    if (!placeholder?.includes('${name}')) {
      throw new Error(`Subject placeholder must show \${name}, got: ${placeholder}`)
    }
    if (!hintText?.includes('${name}')) {
      throw new Error(`Subject hint must mention \${name}, got: ${hintText}`)
    }

    const hasToolbar = await page.locator('.w-e-toolbar').count()
    const hasInsert = await page.locator('.erb-insert-select').count()
    console.log('RICH_TOOLBAR:', hasToolbar, 'INSERT_SELECT:', hasInsert)
    if (errors.length) console.warn('[page errors]', errors.join('\n'))
    if (!hasToolbar) {
      throw new Error('wangEditor toolbar not found in template dialog')
    }
  } finally {
    await browser.close()
  }
}

main().catch((e) => { console.error('[verify-email-template] FAILED:', e.message); process.exit(1) })
