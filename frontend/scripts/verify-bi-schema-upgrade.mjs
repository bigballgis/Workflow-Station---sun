import assert from 'node:assert/strict'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

// Read-only runtime verification. BI_VERIFY_PHASE=before records the missing-schema failure.
const phase = process.env.BI_VERIFY_PHASE ?? 'after'
assert.ok(['before', 'after'].includes(phase))
const origin = process.env.ORIGIN ?? 'http://localhost:3000'
const output = resolve('user-portal/verification-screenshots')
mkdirSync(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
  await loginViaPortalPassword(page)
  const checks = []
  for (const query of ['', `?activeBusinessUnitId=${encodeURIComponent(process.env.BI_VERIFY_BU)}`]) {
    assert.ok(process.env.BI_VERIFY_BU, 'Set BI_VERIFY_BU to the affected workspace business unit ID')
    const response = await page.request.get(`${origin}/api/portal/bi/dashboards${query}`)
    const body = await response.json()
    if (phase === 'before') {
      assert.equal(response.status(), 502)
      assert.equal(body.error?.code, 'BI_UPSTREAM_UNAVAILABLE')
    } else {
      assert.equal(response.status(), 200)
      assert.ok(Array.isArray(body), 'Expected the dashboard list, not an error envelope')
    }
    checks.push({ explicitWorkspace: !!query, status: response.status(),
      ...(Array.isArray(body) ? { dashboardCount: body.length } : { errorCode: body.error?.code }) })
  }
  const failedRequests = []
  const browserErrors = []
  page.on('response', response => {
    if (response.status() >= 500 && response.url().includes('/api/portal/')) {
      failedRequests.push({ path: new URL(response.url()).pathname, status: response.status() })
    }
  })
  page.on('pageerror', error => browserErrors.push(error.message))
  const loaded = page.waitForResponse(response => new URL(response.url()).pathname === '/api/portal/bi/dashboards')
  await page.goto(`${origin}/portal/dashboard`, { waitUntil: 'domcontentloaded' })
  const dashboardResponse = await loaded
  assert.equal(dashboardResponse.status(), phase === 'before' ? 502 : 200)
  await page.locator('.page-title').waitFor({ state: 'visible' })
  if (phase === 'before') {
    await page.getByText('Service temporarily unavailable, please try again later', { exact: true }).first().waitFor()
  } else {
    assert.ok(Array.isArray(await dashboardResponse.json()))
    assert.equal(await page.getByText('Service temporarily unavailable, please try again later', { exact: true }).count(), 0)
    assert.deepEqual(failedRequests, [])
    assert.deepEqual(browserErrors, [])
  }
  await page.screenshot({ path: `${output}/2026-10-26_bi-schema-${phase}.png` })
  const evidence = { phase, checks, failedRequests, browserErrors }
  writeFileSync(`${output}/2026-10-26_bi-schema-${phase}.json`, JSON.stringify(evidence, null, 2))
  console.log(JSON.stringify(evidence))
} finally {
  await browser.close()
}
