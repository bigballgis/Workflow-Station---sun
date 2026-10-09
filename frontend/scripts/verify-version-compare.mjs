#!/usr/bin/env node
/** Screenshot and smoke-test the deployed Developer Workstation version comparison. */
import { mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const functionUnitId = process.argv[2]
if (!/^\d+$/.test(functionUnitId || '')) {
  throw new Error('Usage: node scripts/verify-version-compare.mjs <function-unit-id>')
}

const outputDir = resolve(scriptDir, '../developer-workstation/verification-screenshots')
mkdirSync(outputDir, { recursive: true })
const outputPath = join(outputDir, `${new Date().toISOString().slice(0, 10)}_version-compare-${functionUnitId}.png`)
const documentsPath = join(outputDir, `${new Date().toISOString().slice(0, 10)}_version-compare-documents-${functionUnitId}.png`)
const automationPath = join(outputDir, `${new Date().toISOString().slice(0, 10)}_version-compare-automation-${functionUnitId}.png`)
const browser = await chromium.launch({ headless: true,
  ...(process.env.PLAYWRIGHT_EXECUTABLE_PATH
    ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH }
    : { channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' }) })
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await loginViaDwPassword(page)
  await page.goto(`http://localhost:3000/dev/function-units/${functionUnitId}`, {
    waitUntil: 'domcontentloaded'
  })
  await page.locator('#tab-versions').click()
  const compareAction = page.locator('.version-manager .action-buttons button:visible').first()
  await compareAction.waitFor()
  await compareAction.click()
  const dialog = page.locator('.el-dialog:has(.version-compare)')
  await dialog.waitFor()
  const compareButton = dialog.locator('.version-compare__selectors button')
  if (await compareButton.isDisabled()) throw new Error('Two distinct saved versions are required')
  const responsePromise = page.waitForResponse(response =>
    response.url().includes('/versions/compare-v2') && response.request().method() === 'GET'
  )
  await compareButton.click()
  const response = await responsePromise
  const body = await response.json()
  if (!response.ok()) {
    throw new Error(`Compare V2 returned HTTP ${response.status()}: ${body.error?.code || ''} ${body.error?.message || body.message || ''}`)
  }
  if (!Array.isArray(body.data?.modules) || body.data.modules.length !== 12) {
    throw new Error('Compare V2 did not return all design and independent areas')
  }
  const byKey = Object.fromEntries(body.data.modules.map(module => [module.key, module]))
  if (byKey.AUTOMATION?.status !== 'NOT_SNAPSHOTTED' || !byKey.DOCUMENTS) {
    throw new Error('Automation or independent Documents area has an unexpected status')
  }
  await dialog.locator('.version-compare__summary').waitFor()
  await dialog.screenshot({ path: outputPath })
  await dialog.locator('#tab-DOCUMENTS').click()
  await dialog.screenshot({ path: documentsPath })
  await dialog.locator('#tab-AUTOMATION').click()
  await dialog.screenshot({ path: automationPath })
  if (pageErrors.length) throw new Error(`Page errors: ${pageErrors.join('; ')}`)
  console.log(`[verified] ${body.data.modules.length} areas; screenshots: ${outputPath}, ${documentsPath}, ${automationPath}`)
} finally {
  await browser.close()
}
