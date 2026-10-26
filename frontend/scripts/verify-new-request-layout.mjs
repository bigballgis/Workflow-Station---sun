/** Verify the deployed catalog with real data, then explicit display fixtures. Never submits requests. */
import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { loginViaPortalPassword } from './playwright-login.mjs'

const origin = 'http://localhost:3000'
const output = new URL('../user-portal/verification-screenshots/', import.meta.url)
await mkdir(output, { recursive: true })
const prefix = '2026-10-23_new-request'
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1480, height: 960 } })
const page = await context.newPage()
const errors = []
page.on('pageerror', error => errors.push(error.message))
const evidence = { checks: [], errors }
const screenshot = name => page.screenshot({ path: new URL(`${prefix}-${name}.png`, output).pathname, fullPage: true })
async function openCatalog() {
  await page.goto(`${origin}/portal/processes`, { waitUntil: 'networkidle' })
  await page.locator('.processes-page').waitFor()
  await page.locator('.process-list-loading').waitFor({ state: 'hidden' })
}
async function layout(width, name) {
  await page.setViewportSize({ width, height: 960 })
  const metrics = await page.locator('.processes-page').evaluate(el => {
    const cards = [...el.querySelectorAll('.process-card')]
    const content = document.querySelector('.portal-content')
    const bounds = content.getBoundingClientRect()
    return {
      count: cards.length,
      overflows: content.scrollWidth > content.clientWidth + 1,
      cardsFit: cards.every(card => {
        const box = card.getBoundingClientRect()
        return box.left >= bounds.left && box.right <= bounds.right + 1
      }),
      whiteCards: cards.every(card => getComputedStyle(card).backgroundColor === 'rgb(255, 255, 255)'),
      columns: getComputedStyle(el.querySelector('.process-grid')).gridTemplateColumns.split(' ').length,
    }
  })
  assert(!metrics.overflows && metrics.cardsFit && metrics.whiteCards, JSON.stringify(metrics))
  assert.equal(await page.locator('.processes-page .portal-card').count(), 0, 'No outer white card')
  assert.equal(await page.getByText('My Workflows', { exact: true }).count(), 0)
  if (width === 390) assert.equal(metrics.columns, 1)
  await screenshot(name)
  evidence.checks.push({ name, width, ...metrics })
}
try {
  await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Approver_Role' })
  await openCatalog()
  assert(await page.locator('.process-card').count() > 0, 'Real workspace must have accessible workflows')
  for (const width of [1480, 900, 600, 390]) await layout(width, `real-${width}`)
  await page.setViewportSize({ width: 1480, height: 960 })
  const link = page.locator('.process-link').first()
  const destination = await link.getAttribute('href')
  await link.focus()
  assert.equal(await link.evaluate(el => getComputedStyle(el.closest('.process-card')).outlineStyle), 'solid')
  await screenshot('keyboard-focus')
  await link.press('Enter')
  await page.waitForURL(url => url.pathname === destination)
  evidence.checks.push({ name: 'real-keyboard-navigation', passed: true })
  await openCatalog()
  const help = page.getByTestId('new-request-guide-link')
  assert.equal(await help.getAttribute('target'), '_blank')
  const helpPage = await context.newPage()
  await helpPage.goto(await help.getAttribute('href'), { waitUntil: 'networkidle' })
  await helpPage.getByTestId('new-requests-guide-page').waitFor()
  await helpPage.close()
  evidence.checks.push({ name: 'help-article', passed: true })

  // Deliberate synthetic fixtures isolate long text, missing optional fields and favorites.
  const fixtures = [
    { id: 'layout-fixture-1', key: 'layout-fixture-1', name: 'Purchase Request', description: 'Request approval for equipment, services and office supplies.', category: 'Procurement', version: '2.3.0', isFavorite: true, icon: '<svg viewBox="0 0 24 24"><path fill="#db0011" d="M3 3h18v18H3z"/></svg>' },
    { id: 'layout-fixture-2', key: 'layout-fixture-2', name: 'International travel and accommodation approval request with a long workflow name', description: 'A deliberately long description to verify consistent two-line truncation across desktop and narrow screens without pushing version labels out of alignment.', category: 'Travel', version: '1.0.0' },
    { id: 'layout-fixture-3', key: 'layout-fixture-3', name: 'No description or custom icon', category: 'HR', version: '1.0.0' },
    { id: 'layout-fixture-4', key: 'layout-fixture-4', name: 'VeryLongUnbrokenWorkflowNameForCheckingSmallScreensAndCardTextOverflow', category: 'Finance', version: '12.11.10' },
  ]
  const queries = []
  let forceEmpty = false
  await page.route('**/api/portal/processes/definitions*', async route => {
    const keyword = new URL(route.request().url()).searchParams.get('keyword') || ''
    queries.push(keyword)
    const data = forceEmpty ? [] : fixtures.filter(item => item.name.toLowerCase().includes(keyword.toLowerCase()))
    await route.fulfill({ json: { success: true, data } })
  })
  await openCatalog()
  assert.equal(await page.locator('.favorites-section .process-card').count(), 1)
  assert.equal(await page.locator('.process-card').count(), 5)
  assert.equal(await page.locator('.favorites-section .process-icon-svg svg').count(), 1, 'Favorite preserves configured SVG')
  for (const width of [1480, 390]) await layout(width, `fixture-${width}`)
  const search = page.locator('.process-search input')
  await search.fill('Purchase')
  await page.waitForResponse(response => response.url().includes('/processes/definitions?keyword=Purchase'))
  await page.waitForFunction(() => document.querySelectorAll('.process-card').length === 2)
  assert.equal(await page.locator('.process-card').count(), 2)
  await search.fill('no-matching-workflow')
  await page.waitForResponse(response => response.url().includes('keyword=no-matching-workflow'))
  await page.locator('.el-empty').waitFor()
  assert.equal(await page.locator('.process-card').count(), 0)
  assert.equal(await page.locator('.empty-state .el-text').count(), 0, 'Search miss must not ask user to request access')
  await screenshot('fixture-search-empty')
  await page.locator('.process-search').hover()
  await page.locator('.process-search .el-input__clear').click()
  await page.locator('.process-card').first().waitFor()
  assert.equal(await search.inputValue(), '')
  assert.equal(await page.locator('.process-card').count(), 5)
  evidence.checks.push({ name: 'search-filter-empty-clear', queries, passed: true })
  forceEmpty = true
  await openCatalog()
  await page.locator('.empty-state .el-text').waitFor()
  assert.equal(await page.locator('.favorites-section').count(), 0)
  await screenshot('fixture-no-access')
  evidence.checks.push({ name: 'no-access', passed: true })
  assert.deepEqual(errors, [], 'No browser runtime errors')
  console.log(JSON.stringify(evidence, null, 2))
} finally {
  await writeFile(new URL(`${prefix}-verification.json`, output), JSON.stringify(evidence, null, 2))
  await browser.close()
}
