/** Controlled response ordering; all favorite writes are intercepted, never persisted. */
import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { loginViaPortalPassword } from './playwright-login.mjs'

const baseline = process.argv.includes('--baseline')
const out = new URL('../user-portal/verification-screenshots/', import.meta.url)
await mkdir(out, { recursive: true })
const prefix = `2026-10-25_favorite-fix-${baseline ? 'before' : 'after'}`
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1480, height: 960 } })
const page = await context.newPage()
const report = { baseline, checks: [], errors: [] }
page.on('pageerror', error => report.errors.push(error.message))
const items = ['a', 'b'].map(id => ({ id: `race-${id}`, key: `race-${id}`, name: `Review ${id.toUpperCase()}`, category: 'HR', version: '1.0.0' }))
const favorites = new Map(items.map(item => [item.key, false]))
let gate = null
const main = () => page.locator('.processes-page > section').last()
const button = key => main().locator('.process-card').filter({ has: page.locator(`a[href="/portal/processes/start/${key}"]`) }).locator('.favorite-button')
async function open() {
  await page.goto('http://localhost:3000/portal/processes', { waitUntil: 'networkidle' })
  await button(items[0].key).waitFor()
}
try {
  await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Approver_Role' })
  await page.route('**/api/portal/processes/definitions*', async route => {
    const data = items.map(item => ({ ...item, isFavorite: favorites.get(item.key) }))
    const pending = gate
    if (pending && new URL(route.request().url()).searchParams.has('keyword')) {
      pending.started()
      await pending.blocked
    }
    await route.fulfill({ json: { success: true, data } })
  })
  await page.route('**/api/portal/processes/race-*/favorite', async route => {
    const key = new URL(route.request().url()).pathname.split('/').at(-2)
    assert(favorites.has(key))
    favorites.set(key, !favorites.get(key))
    await route.fulfill({ json: { success: true, data: favorites.get(key) } })
  })
  for (const item of items) {
    for (const initial of [false, true]) {
      favorites.set(item.key, initial)
      await open()
      let started, release
      const startPromise = new Promise(resolve => { started = resolve })
      const blocked = new Promise(resolve => { release = resolve })
      gate = { started, blocked, release }
      await page.locator('.process-search input').fill('Review')
      await startPromise
      await button(item.key).click()
      await page.waitForFunction(({ key, expected }) => {
        const link = [...document.querySelectorAll('.process-link')].find(el => el.getAttribute('href') === `/portal/processes/start/${key}`)
        const star = link.closest('.process-card').querySelector('button')
        return star.getAttribute('aria-pressed') === String(expected) && !star.disabled
      }, { key: item.key, expected: !initial })
      const responsePromise = page.waitForResponse(response => response.url().includes('/processes/definitions?keyword='))
      release()
      await (await responsePromise).finished()
      await page.waitForLoadState('networkidle')
      gate = null
      const ui = await button(item.key).getAttribute('aria-pressed')
      const passed = ui === String(favorites.get(item.key))
      report.checks.push({ scenario: 'stale-search-after-favorite', key: item.key, initial, server: favorites.get(item.key), ui, passed })
      await page.screenshot({ path: new URL(`${prefix}-${item.key}-${initial ? 'remove' : 'add'}.png`, out).pathname, fullPage: true })
    }
  }
  favorites.set(items[0].key, true)
  favorites.set(items[1].key, false)
  await open()
  const favorite = page.locator('.favorites-section .favorite-button')
  await favorite.focus()
  await favorite.press('Enter')
  await page.locator('.favorites-section').waitFor({ state: 'detached' })
  await page.waitForLoadState('networkidle')
  const focused = await button(items[0].key).evaluate(el => document.activeElement === el)
  report.checks.push({ scenario: 'focus-after-removing-final-favorite', passed: focused })
  await button(items[1].key).focus()
  await button(items[1].key).press('Enter')
  await page.locator('.favorites-section').waitFor()
  await page.waitForLoadState('networkidle')
  report.checks.push({ scenario: 'focus-after-adding-from-main-grid', passed: await button(items[1].key).evaluate(el => document.activeElement === el) })
  await page.screenshot({ path: new URL(`${prefix}-keyboard.png`, out).pathname, fullPage: true })
  assert.deepEqual(report.errors, [])
  assert(report.checks.every(check => check.passed), JSON.stringify(report.checks))
} finally {
  gate?.release()
  await writeFile(new URL(`${prefix}.json`, out), JSON.stringify(report, null, 2))
  await browser.close()
  console.log(JSON.stringify(report, null, 2))
}
