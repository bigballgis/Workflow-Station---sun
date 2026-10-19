/** Read and open a published synthetic FU; never start a process or edit its design. */
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'

const processKey = process.env.FIXTURE_PROCESS_KEY || 'batch_verify_mv6pj9pn'
const before = process.env.FIXTURE_EXPECT_FAILURE === '1'
assert.match(processKey, /^batch[_-]/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await loginViaAdminPassword(page)
  const catalogResponse = await page.request.get(`http://localhost:3000/api/v1/admin/function-units/by-process-key/${encodeURIComponent(processKey)}`)
  assert.ok(catalogResponse.ok(), `Catalog lookup: ${catalogResponse.status()}`)
  const catalog = await catalogResponse.json()
  const code = catalog.code
  assert.match(catalog.name, /^Batch (verification|binding UI verification) /)
  await loginViaPortalPassword(page)
  const response = await page.request.get(`http://localhost:3000/api/portal/processes/function-units/${catalog.id}/content?forStart=true`)
  const body = await response.json()
  console.log(JSON.stringify({ catalogId: catalog.id, status: response.status(), success: body.success,
    message: body.message || body.error?.message, hasBatchContext: !!body.data?.batchStartContext }))
  const output = new URL('../developer-workstation/verification-screenshots/', import.meta.url)
  mkdirSync(output, { recursive: true })
  await page.goto(`http://localhost:3101/portal/processes/start/${encodeURIComponent(code)}`)
  if (before) {
    assert.match(JSON.stringify(body), /Batch field event scripts are unsupported/)
    await page.getByText(/Batch field event scripts are unsupported/).first().waitFor()
  } else {
    assert.ok(response.ok() && body.success === true, JSON.stringify(body.error || body.message))
    assert.ok(body.data.batchStartContext)
    await page.getByRole('button', { name: 'Submit Application', exact: true }).waitFor()
    await page.getByText('Synthetic PROCESS', { exact: true }).waitFor()
    assert.equal(await page.getByText('Load Failed', { exact: true }).count(), 0)
    assert.deepEqual(pageErrors, [])
  }
  const path = fileURLToPath(new URL(`2026-10-19_batch-designer-events-${before ? 'before' : 'after'}-${catalog.id}.png`, output))
  await page.screenshot({ path, fullPage: true, mask: [page.locator('.user-info'), page.locator('.header-right')] })
  console.log(JSON.stringify({ step: before ? 'reproduced' : 'portal-form-opened', screenshot: path, pageErrors }))
} finally { await browser.close() }
