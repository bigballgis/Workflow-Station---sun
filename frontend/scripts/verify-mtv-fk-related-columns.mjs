#!/usr/bin/env node
/**
 * Capture View Design Related columns panel for HMDC Attachment (FU 48).
 */
import { mkdirSync } from 'fs'
import { join, resolve, dirname } from 'path'
import { fileURLToPath } from 'url'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const OUT_DIR = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '..',
  'developer-workstation',
  'verification-screenshots',
)
const FU_ID = process.argv[2] || '48'
const BASE = process.env.VERIFY_ORIGIN || 'http://localhost:3000'

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const { chromium } = await import('playwright')
  // Prefer system Edge when Playwright's bundled Chromium is not installed.
  const browser = await chromium.launch({
    headless: true,
    channel: 'msedge',
  })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()

  await loginViaUnifiedSso(page, 'dw', { loginOrigin: BASE })
  await page.goto(`${BASE}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(3500)

  // Open View Design tab
  const viewTab = page.locator('#tab-view-design, .el-tabs__item').filter({ hasText: /View Design|视图设计|視圖設計/i })
  if ((await viewTab.count()) === 0) {
    throw new Error('View Design tab not found')
  }
  await viewTab.first().click()
  await page.waitForTimeout(2500)

  // Select Attachment view in the left nav
  const attachmentNav = page.locator('.view-nav-item, .view-group, .mtv-nav, .el-tree-node__content, button, .nav-view-name, span')
    .filter({ hasText: /Attachment/i })
  if ((await attachmentNav.count()) > 0) {
    await attachmentNav.first().click({ timeout: 8000 }).catch(() => {})
    await page.waitForTimeout(2000)
  }

  // Prefer an explicit "HMDC Attachment" group click if present
  const attGroup = page.getByText(/HMDC.?Attachment/i).first()
  if (await attGroup.count()) {
    await attGroup.click({ timeout: 5000 }).catch(() => {})
    await page.waitForTimeout(1500)
  }

  // Related columns section
  const relatedTitle = page.locator('.columns-panel-subtitle, .columns-panel-lookup').filter({
    hasText: /Related columns|关联列|關聯欄/i,
  })
  await page.waitForTimeout(1500)

  const shotPath = join(OUT_DIR, '2026-07-19_mtv-fk-related-columns-panel.png')
  const panel = page.locator('.columns-panel, .main-table-view-designer, .view-designer').first()
  if ((await relatedTitle.count()) > 0) {
    await relatedTitle.first().scrollIntoViewIfNeeded()
    await page.screenshot({ path: shotPath, fullPage: false })
  } else if ((await panel.count()) > 0) {
    await panel.screenshot({ path: shotPath })
  } else {
    await page.screenshot({ path: shotPath, fullPage: true })
  }

  const bodyText = await page.locator('body').innerText()
  const hasRelated = /Related columns|关联列|關聯欄/i.test(bodyText)
  const hasCaseField = /case_number|legal_hold|Legal Hold|Case Number/i.test(bodyText)

  console.log(JSON.stringify({
    shotPath,
    hasRelated,
    hasCaseField,
    snippet: bodyText.slice(0, 500),
  }, null, 2))

  if (!hasRelated) {
    throw new Error('Related columns section not visible — catalog may not have loaded FK groups')
  }

  await browser.close()
  console.log('OK', shotPath)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
