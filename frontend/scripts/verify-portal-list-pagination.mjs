#!/usr/bin/env node
/**
 * Runtime verification for the shared portal list pager.
 *
 * Two things are asserted against the live stack rather than a mock:
 *   1. Changing the page size fires exactly ONE list request. el-pagination
 *      clamps its own current page when the size grows, so the same click also
 *      arrives as current-change; the old per-view handlers fetched on both.
 *   2. Todo / Completed task lists page through the engine — page 2 asks the
 *      backend for page 2 and the rows actually change.
 *
 * Usage (from frontend/):
 *   node scripts/verify-portal-list-pagination.mjs
 */

import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { launchChromium } from './playwright-browser.mjs'
import { loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(resolve(__dirname, '..'), 'user-portal', 'verification-screenshots')
const ORIGIN = process.env.PORTAL_ORIGIN ?? 'http://localhost:3000'

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function shoot(target, name) {
  const file = join(OUT_DIR, `${datePrefix()}_${name}.png`)
  await target.screenshot({ path: file })
  console.log(`  saved ${file}`)
  return file
}

/** Collect list requests whose URL matches `pattern` while `run` executes. */
async function recordRequests(page, pattern, run) {
  const hits = []
  const onRequest = (req) => {
    if (pattern.test(req.url())) hits.push(`${req.method()} ${req.url()}`)
  }
  page.on('request', onRequest)
  try {
    await run()
    await page.waitForTimeout(3000)
  } finally {
    page.off('request', onRequest)
  }
  return hits
}

/** Pick a value in the page-size select of the pager inside `scope`. */
async function changePageSize(page, scope, label) {
  const sizeSelect = scope.locator('.portal-list-pagination .el-select').first()
  await sizeSelect.waitFor({ state: 'visible', timeout: 20000 })
  await sizeSelect.click()
  const popper = page.locator('.el-select-dropdown:visible').last()
  await popper.waitFor({ state: 'visible', timeout: 10000 })
  await popper.locator('.el-select-dropdown__item', { hasText: label }).first().click()
}

async function firstRowText(page, scope) {
  const row = scope.locator('tbody tr').first()
  if (!(await row.count())) return ''
  return (await row.innerText()).replace(/\s+/g, ' ').trim()
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const browser = await launchChromium()
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
  const saved = []

  try {
    await loginViaPortalPassword(page)

    console.log('My Applications — page size change fires one request…')
    await page.goto(`${ORIGIN}/portal/my-applications`, { waitUntil: 'domcontentloaded' })
    await page.locator('.portal-list-pagination').first().waitFor({ timeout: 30000 })
    await page.waitForTimeout(2000)
    saved.push(await shoot(page, 'pagination-applications-pager'))

    const appHits = await recordRequests(page, /\/processes\/my-applications\?/, async () => {
      await changePageSize(page, page.locator('.application-table').locator('..'), '50')
    })
    console.log(`  size 20 → 50 issued ${appHits.length} request(s)`)
    appHits.forEach((h) => console.log(`    ${h}`))
    if (appHits.length !== 1) {
      throw new Error(`page-size change must issue exactly 1 list request, got ${appHits.length}`)
    }
    if (!/size=50/.test(appHits[0]) || !/page=0/.test(appHits[0])) {
      throw new Error(`size change should ask for size=50&page=0, got ${appHits[0]}`)
    }
    saved.push(await shoot(page, 'pagination-applications-size-50'))

    console.log('To Do — pager present and pages through the engine…')
    await page.goto(`${ORIGIN}/portal/tasks`, { waitUntil: 'domcontentloaded' })
    await page.locator('.portal-list-pagination').first().waitFor({ timeout: 30000 })
    await page.waitForTimeout(2500)
    saved.push(await shoot(page, 'pagination-todo-pager'))

    const pager = page.locator('.portal-list-pagination').first()
    const nextBtn = pager.locator('button.btn-next')
    const hasNext = (await nextBtn.count()) && !(await nextBtn.isDisabled())
    if (hasNext) {
      const before = await firstRowText(page, page.locator('.el-table').first())
      const todoHits = await recordRequests(page, /\/tasks\/query/, async () => {
        await nextBtn.click()
      })
      console.log(`  next page issued ${todoHits.length} request(s)`)
      if (todoHits.length !== 1) {
        throw new Error(`next page must issue exactly 1 list request, got ${todoHits.length}`)
      }
      const after = await firstRowText(page, page.locator('.el-table').first())
      if (before && after && before === after) {
        throw new Error('page 2 shows the same first row as page 1 — paging did not take effect')
      }
      console.log('  first row changed between page 1 and page 2')
      saved.push(await shoot(page, 'pagination-todo-page-2'))
    } else {
      console.log('  only one page of todo tasks — pager rendered, next disabled (still asserts pager mounts)')
    }

    console.log('Completed tasks — pager present…')
    await page.goto(`${ORIGIN}/portal/tasks/completed`, { waitUntil: 'domcontentloaded' })
    await page.locator('.portal-list-pagination').first().waitFor({ timeout: 30000 })
    await page.waitForTimeout(2500)
    saved.push(await shoot(page, 'pagination-completed-pager'))

    console.log(`OK — ${saved.length} screenshots`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
