/**
 * Admin Audit list viewport: pagination + table pane sit at the window bottom
 * (not under the last row). Run after rebuilding admin-center-frontend.
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
/** Pagination bottom must land in the last 80px of the window (page + main padding). */
const BOTTOM_SLACK_PX = 80
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
    ([gridNode, pagerNode, vh, slack, minGrid]) => {
      const loading = document.querySelector('.page-container .el-loading-mask')
      if (loading) {
        const style = window.getComputedStyle(loading)
        if (style.display !== 'none' && style.visibility !== 'hidden') return false
      }
      const gr = gridNode.getBoundingClientRect()
      const pr = pagerNode.getBoundingClientRect()
      const pagerBottom = pr.top + pr.height
      return (
        gr.height > minGrid &&
        pagerBottom > vh - slack &&
        pagerBottom <= vh + 1
      )
    },
    [gridEl, pagerEl, VIEWPORT.height, BOTTOM_SLACK_PX, MIN_GRID_HEIGHT_PX],
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

    const gridBox = await grid.boundingBox()
    const pagerBox = await pager.boundingBox()
    const headerVisible = await grid.locator('.el-table__header').isVisible()
    const cardCount = await page.locator('.page-container .table-card .list-data-grid-scroll').count()
    const metrics = {
      vh: VIEWPORT.height,
      gridHeight: gridBox?.height ?? 0,
      pagerBottom: (pagerBox?.y ?? 0) + (pagerBox?.height ?? 0),
      hasTableCard: cardCount > 0,
      headerVisible,
    }

    check(`${item.slug} uses table-card host`, metrics.hasTableCard)
    check(`${item.slug} table header visible`, metrics.headerVisible)
    check(
      `${item.slug} grid pane taller than a 2-row hug`,
      metrics.gridHeight > MIN_GRID_HEIGHT_PX,
      `gridHeight=${metrics.gridHeight.toFixed(1)}`,
    )
    check(
      `${item.slug} pagination near viewport bottom`,
      metrics.pagerBottom > metrics.vh - BOTTOM_SLACK_PX && metrics.pagerBottom <= metrics.vh + 1,
      `pagerBottom=${metrics.pagerBottom.toFixed(1)} vh=${metrics.vh}`,
    )
  }
} finally {
  await browser.close()
}

console.log('[OK] both Admin Audit menus pin pagination to the viewport bottom')
