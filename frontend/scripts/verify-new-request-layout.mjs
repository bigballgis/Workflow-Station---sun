/** Verify the deployed catalog with real data, then isolated display/favourite fixtures. Never submits requests. */
import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { loginViaPortalPassword } from './playwright-login.mjs'

const origin = 'http://localhost:3000'
const output = new URL('../user-portal/verification-screenshots/', import.meta.url)
await mkdir(output, { recursive: true })
const prefix = '2026-11-03_new-request-tabs'
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1480, height: 960 } })
const page = await context.newPage()
const errors = []
page.on('pageerror', error => errors.push(error.message))
const evidence = { checks: [], errors }
const screenshot = name => page.screenshot({ path: new URL(`${prefix}-${name}.png`, output).pathname, fullPage: true, animations: 'disabled' })
const allTab = page.getByRole('tab', { name: /^All Requests/ })
const favoritesTab = page.getByRole('tab', { name: /^Favourites/ })
const search = page.locator('.process-search input')
const cards = page.locator('.process-card')
const star = key => page.locator(`.favorite-button[data-process-key="${key}"]`)
async function openCatalog() {
  await page.goto(`${origin}/portal/processes`, { waitUntil: 'networkidle' })
  await page.locator('.processes-page').waitFor()
  await page.locator('.process-list-loading').waitFor({ state: 'hidden' })
  assert.equal(await allTab.getAttribute('aria-selected'), 'true', 'Every visit defaults to All Requests')
  assert.equal(await page.getByRole('tab').first().getAttribute('id'), 'request-tab-all')
}
async function expectCount(count) {
  await page.waitForFunction(expected => document.querySelectorAll('.process-card').length === expected, count)
  assert.equal(await cards.count(), count)
}
async function searchFor(keyword) {
  const response = page.waitForResponse(r => r.url().includes('/processes/definitions')
    && (new URL(r.url()).searchParams.get('keyword') || '') === keyword)
  await search.fill(keyword)
  await response
}
async function toggle(key, saved) {
  const response = page.waitForResponse(r => r.url().endsWith(`/processes/${key}/favorite`))
  await star(key).click()
  await response
  await page.waitForFunction(({ key, saved }) => {
    const button = document.querySelector(`.favorite-button[data-process-key="${key}"]`)
    return !button || (button.getAttribute('aria-pressed') === String(saved) && !button.disabled)
  }, { key, saved })
}
async function layout(width, name) {
  await page.setViewportSize({ width, height: 960 })
  if (width <= 900) await page.locator('.nav-panel').waitFor({ state: 'hidden' })
  const metrics = await page.locator('.processes-page').evaluate(el => {
    const content = document.querySelector('.portal-content')
    const bounds = content.getBoundingClientRect()
    const grid = el.querySelector('.process-grid')
    const items = [...el.querySelectorAll('.process-card, [role="tab"]')]
    return {
      count: el.querySelectorAll('.process-card').length,
      overflows: content.scrollWidth > content.clientWidth + 1,
      itemsFit: items.every(item => {
        const box = item.getBoundingClientRect()
        return box.left >= bounds.left && box.right <= bounds.right + 1
      }),
      whiteCards: [...el.querySelectorAll('.process-card')].every(card => getComputedStyle(card).backgroundColor === 'rgb(255, 255, 255)'),
      columns: grid ? getComputedStyle(grid).gridTemplateColumns.split(' ').length : 0,
      active: el.querySelector('[role="tab"][aria-selected="true"]').id,
    }
  })
  assert(!metrics.overflows && metrics.itemsFit && metrics.whiteCards, JSON.stringify(metrics))
  assert.equal(await page.locator('.processes-page .portal-card').count(), 0, 'No outer white card')
  if (width <= 390 && metrics.count > 0) assert.equal(metrics.columns, 1)
  await screenshot(name)
  evidence.checks.push({ name, width, ...metrics })
}
async function checkKeyboardTabs() {
  await allTab.focus()
  await allTab.press('ArrowRight')
  assert.equal(await favoritesTab.getAttribute('aria-selected'), 'true')
  assert(await favoritesTab.evaluate(el => el === document.activeElement))
  await favoritesTab.press('Home')
  assert.equal(await allTab.getAttribute('aria-selected'), 'true')
  await allTab.press('End')
  await favoritesTab.press('ArrowRight')
  assert.equal(await allTab.getAttribute('aria-selected'), 'true', 'ArrowRight wraps to the first tab')
  await allTab.press('ArrowLeft')
  assert.equal(await favoritesTab.getAttribute('aria-selected'), 'true', 'ArrowLeft wraps to the last tab')
  await favoritesTab.press('ArrowLeft')
  assert.equal(await allTab.getAttribute('aria-selected'), 'true')
  evidence.checks.push({ name: 'keyboard-tabs', passed: true })
}
try {
  await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Approver_Role' })
  await openCatalog()
  assert(await cards.count() > 0, 'Real workspace must have accessible workflows')
  for (const width of [1480, 900, 600, 390, 320]) await layout(width, `real-all-${width}`)
  await favoritesTab.click()
  for (const width of [1480, 390]) await layout(width, `real-favourites-${width}`)
  await page.setViewportSize({ width: 1480, height: 960 })
  await checkKeyboardTabs()
  const link = page.locator('.process-link').first()
  const destination = await link.getAttribute('href')
  await link.focus()
  assert.equal(await link.evaluate(el => getComputedStyle(el.closest('.process-card')).outlineStyle), 'solid')
  await link.press('Enter')
  await page.waitForURL(url => url.pathname === destination)
  evidence.checks.push({ name: 'real-keyboard-navigation', passed: true })
  await openCatalog()
  const help = page.getByTestId('new-request-guide-link')
  assert.equal(await help.getAttribute('target'), '_blank')
  const helpPage = await context.newPage()
  await helpPage.goto(await help.getAttribute('href'), { waitUntil: 'networkidle' })
  await helpPage.getByTestId('new-requests-guide-page').waitFor()
  assert.match(await helpPage.locator('main').innerText(), /All Requests.*first tab/)
  await helpPage.screenshot({ path: new URL(`${prefix}-help-en.png`, output).pathname, fullPage: true, animations: 'disabled' })
  for (const [locale, text] of [['zh-CN', '全部流程位于第一个页签'], ['zh-TW', '全部流程位於第一個頁籤']]) {
    await helpPage.getByTestId('help-locale-select').selectOption(locale)
    await helpPage.getByText(text, { exact: false }).waitFor()
    await helpPage.screenshot({ path: new URL(`${prefix}-help-${locale}.png`, output).pathname, fullPage: true, animations: 'disabled' })
  }
  await helpPage.close()
  evidence.checks.push({ name: 'help-article', passed: true })

  // Synthetic workflows intentionally isolate text limits, missing optional fields and saved state.
  const fixtures = [
    { id: 'layout-fixture-1', key: 'layout-fixture-1', name: 'Purchase Request', description: 'Request approval for equipment, services and office supplies.', category: 'Procurement', version: '2.3.0', isFavorite: true, icon: '<svg viewBox="0 0 24 24"><path fill="#db0011" d="M3 3h18v18H3z"/></svg>' },
    { id: 'layout-fixture-2', key: 'layout-fixture-2', name: 'International travel and accommodation approval request with a long workflow name', description: 'A deliberately long description to verify consistent two-line truncation across desktop and narrow screens without pushing version labels out of alignment.', category: 'Travel', version: '1.0.0', isFavorite: false },
    { id: 'layout-fixture-3', key: 'layout-fixture-3', name: 'No description or custom icon', category: 'HR', version: '1.0.0', isFavorite: false },
    { id: 'layout-fixture-4', key: 'layout-fixture-4', name: 'VeryLongUnbrokenWorkflowNameForCheckingSmallScreensAndCardTextOverflow', category: 'Finance', version: '12.11.10', isFavorite: false },
  ]
  const queries = []
  let forceEmpty = false
  let rejectFavorite = false
  let holdFavorite = null
  let favoriteWrites = 0
  await page.route('**/api/portal/processes/definitions*', async route => {
    const keyword = new URL(route.request().url()).searchParams.get('keyword') || ''
    queries.push(keyword)
    const data = forceEmpty ? [] : fixtures.filter(item => item.name.toLowerCase().includes(keyword.toLowerCase()))
    await route.fulfill({ json: { success: true, data } })
  })
  await page.route('**/api/portal/processes/*/favorite', async route => {
    favoriteWrites++
    if (holdFavorite) await holdFavorite
    const item = fixtures.find(item => route.request().url().endsWith(`/processes/${item.key}/favorite`))
    assert(item, 'Favourite writes must only target isolated fixtures')
    if (!rejectFavorite) item.isFavorite = !item.isFavorite
    await route.fulfill({ json: { success: true, data: rejectFavorite ? 'invalid' : item.isFavorite } })
  })
  await openCatalog()
  await expectCount(4)
  assert.match(await favoritesTab.innerText(), /1/)
  const beforeSwitch = queries.length
  await favoritesTab.click()
  await expectCount(1)
  assert.equal(await page.locator('.process-icon-svg svg').count(), 1, 'Configured SVG survives tab switching')
  for (const width of [1480, 390]) await layout(width, `fixture-favourites-${width}`)
  await allTab.click()
  assert.equal(queries.length, beforeSwitch, 'Tab switching must not reload the catalogue')
  for (const width of [1480, 390, 320]) await layout(width, `fixture-all-${width}`)
  await page.setViewportSize({ width: 1480, height: 960 })

  let releaseFavorite
  holdFavorite = new Promise(resolve => { releaseFavorite = resolve })
  const beforeWrite = favoriteWrites
  await star('layout-fixture-2').click()
  await page.waitForFunction(() => document.querySelector('[data-process-key="layout-fixture-2"]').disabled)
  assert.equal(await star('layout-fixture-2').isDisabled(), true)
  await favoritesTab.click()
  await expectCount(1)
  releaseFavorite()
  await page.waitForResponse(r => r.url().endsWith('/layout-fixture-2/favorite'))
  holdFavorite = null
  await expectCount(2)
  assert.equal(favoriteWrites, beforeWrite + 1)
  assert.equal(await star('layout-fixture-2').getAttribute('aria-pressed'), 'true')
  await toggle('layout-fixture-1', false)
  await expectCount(1)
  assert(await star('layout-fixture-2').evaluate(el => el === document.activeElement), 'Focus moves to the remaining card')
  await toggle('layout-fixture-2', false)
  await expectCount(0)
  assert.equal(await favoritesTab.getAttribute('aria-selected'), 'true', 'Removing last favourite keeps the tab')
  assert(await page.getByText('No favourite requests yet', { exact: true }).isVisible())
  assert(await page.getByRole('button', { name: 'View all requests', exact: true }).evaluate(el => el === document.activeElement))
  await layout(1480, 'fixture-no-favourites')
  await layout(390, 'fixture-no-favourites-390')
  await page.getByRole('button', { name: 'View all requests', exact: true }).click()
  await expectCount(4)
  assert(await allTab.evaluate(el => el === document.activeElement))
  assert.equal(await star('layout-fixture-1').getAttribute('aria-pressed'), 'false')
  evidence.checks.push({ name: 'favourites-save-pending-tab-switch-remove-last-focus', passed: true })

  await toggle('layout-fixture-1', true)
  rejectFavorite = true
  await star('layout-fixture-1').click()
  await page.getByText('Could not update favourites. Refresh to check the saved state.', { exact: true }).waitFor()
  assert.equal(await star('layout-fixture-1').getAttribute('aria-pressed'), 'true', 'Failed save must preserve the confirmed state')
  rejectFavorite = false
  await openCatalog()
  await expectCount(4)
  assert.equal(await star('layout-fixture-1').getAttribute('aria-pressed'), 'true', 'Saved favourites survive a revisit')
  evidence.checks.push({ name: 'failed-save-and-revisit-default-all', passed: true })

  await searchFor('Purchase')
  await expectCount(1)
  await favoritesTab.click()
  await expectCount(1)
  assert.equal(await search.inputValue(), 'Purchase')
  assert.match(await allTab.innerText(), /1/)
  assert.match(await favoritesTab.innerText(), /1/)
  await searchFor('travel')
  await expectCount(0)
  assert(await page.getByText('No matching requests. Try another search.', { exact: true }).isVisible())
  assert.equal(await page.locator('.empty-state .el-text').count(), 0, 'Search miss must not ask user to request access')
  await layout(1480, 'fixture-favourites-search-empty')
  await page.getByRole('button', { name: 'View all requests', exact: true }).click()
  await expectCount(1)
  assert.equal(await search.inputValue(), 'travel', 'View all keeps the search keyword')
  await searchFor('no-matching-workflow')
  await expectCount(0)
  await screenshot('fixture-all-search-empty')
  await searchFor('')
  await expectCount(4)
  evidence.checks.push({ name: 'search-both-tabs-counts-empty-clear', queries, passed: true })
  forceEmpty = true
  await openCatalog()
  await page.locator('.empty-state .el-text').waitFor()
  await expectCount(0)
  assert.match(await allTab.innerText(), /0/)
  assert.match(await favoritesTab.innerText(), /0/)
  await screenshot('fixture-no-access')
  evidence.checks.push({ name: 'no-access', passed: true })
  assert.deepEqual(errors, [], 'No browser runtime errors')
  console.log(JSON.stringify(evidence, null, 2))
} finally {
  await writeFile(new URL(`${prefix}-verification.json`, output), JSON.stringify(evidence, null, 2))
  await browser.close()
}
