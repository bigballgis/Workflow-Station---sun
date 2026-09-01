/**
 * Admin Audit list viewport: pagination sits under the last row (not a blank
 * gap mid-card). Run after rebuilding admin-center-frontend.
 *
 * Usage (from frontend/):
 *   node scripts/verify-admin-audit-list-viewport.mjs
 *
 * Output: admin-center/verification-screenshots/{date}_admin-audit-*-viewport.png
 */
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaAdminPassword } from './playwright-login.mjs'

const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const OUT = join(dirname(fileURLToPath(import.meta.url)), '../admin-center/verification-screenshots')
const DATE = new Date().toISOString().slice(0, 10)
const VIEWPORT = { width: 1400, height: 900 }
/** Last data row (or table bottom when empty) to ListPagination. */
const MAX_ROW_PAGER_GAP_PX = 48
const MIN_GRID_HEIGHT_PX = 220

mkdirSync(OUT, { recursive: true })

function check(label, ok, detail) {
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) throw new Error(`${label}${detail ? `: ${detail}` : ''}`)
}

async function waitUntilLayoutPinned(page, grid, pager) {
  const gridEl = await grid.elementHandle()
  const pagerEl = await pager.elementHandle()
  if (!gridEl || !pagerEl) {
    throw new Error('grid or pagination handle missing')
  }
  await page.waitForFunction(
    ([gridNode, pagerNode, maxGap, minGrid]) => {
      const loading = document.querySelector('.page-container .el-loading-mask')
      if (loading) {
        const style = window.getComputedStyle(loading)
        if (style.display !== 'none' && style.visibility !== 'hidden') return false
      }
      const gr = gridNode.getBoundingClientRect()
      const pr = pagerNode.getBoundingClientRect()
      const lastRow = gridNode.querySelector('.el-table__body-wrapper tr.el-table__row:last-child')
      const anchor = lastRow ?? gridNode.querySelector('.el-table')
      if (!anchor) return false
      const gap = pr.top - anchor.getBoundingClientRect().bottom
      return gr.height > minGrid && gap >= -20 && gap <= maxGap && pr.bottom <= window.innerHeight + 1
    },
    [gridEl, pagerEl, MAX_ROW_PAGER_GAP_PX, MIN_GRID_HEIGHT_PX],
    { timeout: 20000 },
  )
}

const PAGES = [
  { slug: 'admin-audit-admin-center-viewport', path: '/admin/audit/admin-center' },
  { slug: 'admin-audit-user-portal-viewport', path: '/admin/audit/user-portal' },
]

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await (await browser.newContext({ viewport: VIEWPORT })).newPage()

try {
  await loginViaAdminPassword(page, { loginOrigin: ORIGIN })

  for (const item of PAGES) {
    const url = `${ORIGIN}${item.path}`
    console.log(`[goto] ${url}`)
    await page.goto(url, { waitUntil: 'domcontentloaded' })
    const grid = page.locator('.page-container .list-data-grid-scroll').last()
    const pager = page.locator('.page-container .list-pagination').last()
    await grid.waitFor({ timeout: 20000 })
    await pager.waitFor({ timeout: 20000 })
    await grid.locator('.el-table__header').waitFor({ state: 'visible', timeout: 20000 })
    await waitUntilLayoutPinned(page, grid, pager)

    const shot = join(OUT, `${DATE}_${item.slug}.png`)
    await page.screenshot({ path: shot })
    console.log(`[SHOT] ${shot}`)

    const metrics = await page.evaluate(() => {
      const gridNode = document.querySelector('.page-container .list-data-grid-scroll')
      const pagerNode = document.querySelector('.page-container .list-pagination')
      const lastRow = gridNode?.querySelector('.el-table__body-wrapper tr.el-table__row:last-child')
      const anchor = lastRow ?? gridNode?.querySelector('.el-table')
      const gr = gridNode?.getBoundingClientRect()
      const pr = pagerNode?.getBoundingClientRect()
      const ar = anchor?.getBoundingClientRect()
      return {
        gridHeight: gr?.height ?? 0,
        gap: pr && ar ? pr.top - ar.bottom : null,
        hasTableCard: document.querySelectorAll('.page-container .table-card .list-data-grid-scroll').length > 0,
        headerVisible: !!gridNode?.querySelector('.el-table__header'),
      }
    })

    check(`${item.slug} uses table-card host`, metrics.hasTableCard)
    check(`${item.slug} table header visible`, metrics.headerVisible)
    check(
      `${item.slug} grid pane taller than a 2-row hug`,
      metrics.gridHeight > MIN_GRID_HEIGHT_PX,
      `gridHeight=${metrics.gridHeight.toFixed(1)}`,
    )
    check(
      `${item.slug} pagination under last row`,
      metrics.gap != null && metrics.gap >= -20 && metrics.gap <= MAX_ROW_PAGER_GAP_PX,
      `gap=${metrics.gap?.toFixed(1)}`,
    )
  }
} finally {
  await browser.close()
}

console.log('[OK] both Admin Audit menus keep pagination under the last log row')
