/** Read-only real catalog check, with a clearly identified display fixture when access excludes the icon sample. */
import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { loginViaPortalPassword } from './playwright-login.mjs'

const origin = 'http://localhost:3000'
const out = new URL('../user-portal/verification-screenshots/2026-10-23_custom-icon-', import.meta.url)
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1480, height: 960 } })
const page = await context.newPage()
const errors = []
page.on('pageerror', error => errors.push(error.message))
const report = { mode: '', source: '', checks: [], errors }
try {
  await loginViaPortalPassword(page)
  const response = await page.request.get(`${origin}/api/portal/processes/definitions`)
  assert(response.ok())
  const body = await response.json()
  assert(Array.isArray(body.data))
  let process = body.data.find(item => item.icon)
  if (process) {
    report.mode = 'real-catalog'
    report.source = process.name
  } else {
    const source = JSON.parse(await readFile('/private/tmp/new-request-real-icon.json', 'utf8'))
    assert(source.icon.includes('<svg'))
    report.mode = 'display-fixture-with-existing-function-unit-svg'
    report.source = source.name
    process = { id: 'custom-icon-preview', key: 'custom-icon-preview', name: source.name, description: 'Custom icon display test — existing Function Unit SVG', category: 'Business Process', version: '1.0.0', icon: source.icon, isFavorite: true }
    await page.route('**/api/portal/processes/definitions*', route => route.fulfill({ json: { success: true, data: [process] } }))
  }
  await page.goto(`${origin}/portal/processes`, { waitUntil: 'networkidle' })
  const card = page.locator('.process-card').filter({ has: page.locator('.process-name', { hasText: process.name }) }).first()
  await card.waitFor()
  const icon = card.locator('.process-icon-svg')
  await icon.locator('svg').waitFor()
  const metrics = await icon.evaluate(el => {
    const svg = el.querySelector('svg')
    const box = el.getBoundingClientRect(), area = el.closest('.card-icon').getBoundingClientRect()
    return { width: box.width, height: box.height, centered: Math.abs(box.x + box.width / 2 - area.x - area.width / 2) < 1, svgWidth: svg.getBoundingClientRect().width, svgHeight: svg.getBoundingClientRect().height, hasUnsafeNodes: !!el.querySelector('script, foreignObject, iframe'), shapeCount: svg.querySelectorAll('path, rect, circle, polygon, line, polyline, g').length }
  })
  assert.equal(metrics.width, 56)
  assert.equal(metrics.height, 56)
  assert(metrics.centered && !metrics.hasUnsafeNodes && metrics.shapeCount > 0)
  assert.equal(await card.locator('.card-icon > .el-icon').count(), 0, 'Custom SVG replaces category icon')
  await card.screenshot({ path: `${out.pathname}card.png` })
  await page.screenshot({ path: `${out.pathname}desktop.png`, fullPage: true })
  report.checks.push({ scenario: 'desktop', ...metrics })
  await page.setViewportSize({ width: 390, height: 960 })
  await page.screenshot({ path: `${out.pathname}mobile.png`, fullPage: true })
  const mobile = await icon.boundingBox()
  assert.equal(mobile.width, 56)
  assert.equal(mobile.height, 56)
  report.checks.push({ scenario: 'mobile', width: mobile.width, height: mobile.height })
  const favorite = page.locator('.favorites-section .process-icon-svg svg')
  if (await favorite.count()) {
    assert.equal(await favorite.first().evaluate(el => el.outerHTML), await icon.locator('svg').evaluate(el => el.outerHTML))
    report.checks.push({ scenario: 'favorite-matches-main-card', passed: true })
  }
  assert.deepEqual(errors, [])
  console.log(JSON.stringify(report, null, 2))
} finally {
  await writeFile(`${out.pathname}report.json`, JSON.stringify(report, null, 2))
  await browser.close()
}
