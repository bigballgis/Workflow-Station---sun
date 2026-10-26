/** Exercise real favorites with restoration, then inject a failed response without a server write. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { mkdir, writeFile } from 'node:fs/promises'
import { loginViaPortalPassword } from './playwright-login.mjs'

const origin = 'http://localhost:3000'
const output = new URL('../user-portal/verification-screenshots/', import.meta.url)
const prefix = '2026-10-25_favorites'
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1480, height: 960 } })
const page = await context.newPage()
const errors = []
page.on('pageerror', error => errors.push(error.message))
const report = { checks: [], errors, restored: false }
let originals = []
const path = '/portal/processes'
const screenshot = name => page.screenshot({ path: new URL(`${prefix}-${name}.png`, output).pathname, fullPage: true })
async function definitions() {
  const response = await page.request.get(`${origin}/api/portal/processes/definitions`)
  assert(response.ok())
  const body = await response.json()
  assert(Array.isArray(body.data))
  return body.data
}
function cards(key) { return page.locator('.process-card').filter({ has: page.locator(`a[href="/portal/processes/start/${key}"]`) }) }
async function open() {
  await page.goto(`${origin}${path}`, { waitUntil: 'networkidle' })
  await page.locator('.process-link').first().waitFor()
}
async function waitState(key, state) {
  await page.waitForFunction(({ key, state }) => {
    const link = document.querySelector(`a[href="/portal/processes/start/${key}"]`)
    const star = link?.closest('.process-card').querySelector('button')
    return star && star.getAttribute('aria-pressed') === String(state) && !star.disabled
  }, { key, state })
  assert.equal(new URL(page.url()).pathname, path, 'Star must not navigate into the request form')
}
try {
  await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Approver_Role' })
  originals = (await definitions()).slice(0, 2).map(item => ({ key: item.key, favorite: !!item.isFavorite }))
  assert.equal(originals.length, 2, 'Two real workflows are required')
  await open()
  for (const [index, item] of originals.entries()) {
    const target = !item.favorite
    const button = cards(item.key).first().locator('.favorite-button')
    await button.focus()
    await button.press('Enter')
    await waitState(item.key, target)
    assert.equal((await definitions()).find(value => value.key === item.key).isFavorite, target)
    await open()
    await waitState(item.key, target)
    assert.equal(await page.locator('.favorites-section').locator(`a[href="/portal/processes/start/${item.key}"]`).count(), target ? 1 : 0)
    await screenshot(`real-${index + 1}-after-toggle`)
    await cards(item.key).first().locator('.favorite-button').click()
    await waitState(item.key, item.favorite)
    assert.equal((await definitions()).find(value => value.key === item.key).isFavorite, item.favorite)
    report.checks.push({ scenario: 'real-toggle-refresh-and-restore', key: item.key, passed: true })
  }

  const item = originals[0]
  const url = `**/api/portal/processes/${item.key}/favorite`
  if (!item.favorite) {
    await cards(item.key).first().locator('.favorite-button').click()
    await waitState(item.key, true)
  }
  assert.equal(await cards(item.key).count(), 2)
  let requests = 0
  let unblock
  const release = new Promise(resolve => { unblock = resolve })
  await page.route(url, async route => {
    requests++
    await release
    const response = await route.fetch()
    await route.fulfill({ response })
  })
  await cards(item.key).first().locator('.favorite-button').evaluate(el => {
    el.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    el.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  })
  await page.waitForFunction(key => [...document.querySelectorAll('.process-card')].filter(card => card.querySelector(`a[href="/portal/processes/start/${key}"]`)).every(card => card.querySelector('button').disabled), item.key)
  unblock()
  await waitState(item.key, false)
  assert.equal(requests, 1, 'Duplicate clicks must produce one toggle request')
  await page.unroute(url)
  if (item.favorite) {
    await cards(item.key).first().locator('.favorite-button').click()
    await waitState(item.key, true)
  }
  report.checks.push({ scenario: 'pending-lock-and-duplicate-click', passed: true })

  await page.route(url, route => route.fulfill({ status: 500, json: { success: false, message: 'Controlled verification failure' } }))
  await cards(item.key).first().locator('.favorite-button').click()
  await page.getByText('Could not update favorites. Refresh to check the saved state.').waitFor()
  await waitState(item.key, item.favorite)
  await screenshot('failure-retains-state')
  await page.unroute(url)
  report.checks.push({ scenario: 'failed-save-retains-state-and-reenables-button', passed: true })
  await cards(item.key).first().locator('.favorite-button').click()
  await waitState(item.key, !item.favorite)
  await page.setViewportSize({ width: 390, height: 960 })
  await screenshot('mobile')
  const content = page.locator('.portal-content')
  assert(await content.evaluate(el => el.scrollWidth <= el.clientWidth + 1))
  await cards(item.key).first().locator('.favorite-button').click()
  await waitState(item.key, item.favorite)
  await page.setViewportSize({ width: 1480, height: 960 })
  await screenshot('desktop-restored')
  const link = cards(item.key).first().locator('.process-link')
  const href = await link.getAttribute('href')
  await link.click()
  await page.waitForURL(url => url.pathname === href)
  report.checks.push({ scenario: 'mobile-layout-and-card-navigation', passed: true })
  assert.deepEqual(errors, [])
} finally {
  await page.unrouteAll({ behavior: 'wait' })
  for (const item of originals) {
    const current = (await definitions()).find(value => value.key === item.key)
    if (!!current.isFavorite !== item.favorite) {
      const response = await page.request.post(`${origin}/api/portal/processes/${item.key}/favorite`)
      assert(response.ok(), 'Restore favorite state')
    }
  }
  const final = await definitions()
  report.restored = originals.every(item => !!final.find(value => value.key === item.key).isFavorite === item.favorite)
  await writeFile(new URL(`${prefix}-report.json`, output), JSON.stringify(report, null, 2))
  await browser.close()
}
assert(report.restored)
console.log(JSON.stringify(report, null, 2))
