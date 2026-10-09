#!/usr/bin/env node
/** Read-only browser/API check for the deployed Function Unit semantic Compare workspace. */
import { mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const functionUnitId = process.argv[2]
if (!/^\d+$/.test(functionUnitId || '')) {
  throw new Error('Usage: node scripts/verify-version-semantic-compare.mjs <function-unit-id>')
}

const outputDir = resolve(scriptDir, '../developer-workstation/verification-screenshots')
mkdirSync(outputDir, { recursive: true })
const prefix = `${new Date().toISOString().slice(0, 10)}_semantic-compare-${functionUnitId}`
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
  await page.locator('.version-manager .action-buttons button:visible').first().click()
  const dialog = page.locator('.el-dialog:has(.version-compare)')
  await dialog.waitFor()
  const responsePromise = page.waitForResponse(response =>
    response.url().includes('/versions/compare-v2') && response.request().method() === 'GET')
  await dialog.locator('.version-compare__selectors button').click()
  const response = await responsePromise
  const body = await response.json()
  if (!response.ok()) throw new Error(`Compare HTTP ${response.status()}: ${body.message || ''}`)
  const modules = body.data?.modules
  if (!Array.isArray(modules) || modules.length !== 12) throw new Error('Expected 12 Compare areas')
  const totals = { added: 0, modified: 0, removed: 0 }
  for (const module of modules) {
    if (!module.semantic) throw new Error(`Missing semantic result for ${module.key}`)
    const counts = module.semantic.status === 'COMPARED' ? module.semantic.counts : module.counts
    for (const key of Object.keys(totals)) totals[key] += counts[key]
  }
  if (JSON.stringify(totals) !== JSON.stringify(body.data.displayTotals)) {
    throw new Error('Displayed totals do not equal the sum of displayed module counts')
  }
  const connectionHasDetails = modules.find(module => module.key === 'CONNECTIONS')?.semantic?.items?.length > 0
  const serialized = JSON.stringify(body.data)
  if (serialized.includes('QA_CMP_SECRET_NEVER_RETURN')) throw new Error('Connection secret leaked')
  await dialog.locator('.version-compare__summary').waitFor()
  const screenshots = []
  for (const key of ['CONNECTIONS', 'EMAIL_TEMPLATES', 'DECISIONS', 'DOCUMENTS', 'AUTOMATION']) {
    await dialog.locator(`#tab-${key}`).click()
    const path = join(outputDir, `${prefix}-${key.toLowerCase()}.png`)
    await dialog.screenshot({ path })
    screenshots.push(path)
  }
  await page.setViewportSize({ width: 768, height: 1000 })
  await dialog.locator('#tab-CONNECTIONS').click()
  const narrowPath = join(outputDir, `${prefix}-768px.png`)
  await dialog.screenshot({ path: narrowPath })
  screenshots.push(narrowPath)
  for (const [locale, label, field] of [
    ['zh-CN', '连接配置', '连接类型'], ['zh-TW', '連線設定', '連線類型']
  ]) {
    const switched = await page.evaluate(nextLocale => {
      const app = document.querySelector('#app')?.__vue_app__
      const i18n = app?.config.globalProperties.$i18n
      if (!i18n) return false
      if (typeof i18n.locale === 'string') i18n.locale = nextLocale
      else if (i18n.locale?.value !== undefined) i18n.locale.value = nextLocale
      return (typeof i18n.locale === 'string' ? i18n.locale : i18n.locale?.value) === nextLocale
    }, locale)
    if (!switched) throw new Error(`Cannot switch Compare locale to ${locale}`)
    await dialog.locator('#tab-CONNECTIONS').getByText(label).waitFor()
    if (connectionHasDetails) await dialog.locator('.compare-panel__field-name').getByText(field).waitFor()
    const localePath = join(outputDir, `${prefix}-${locale}.png`)
    await dialog.screenshot({ path: localePath })
    screenshots.push(localePath)
  }
  if (pageErrors.length) throw new Error(`Page errors: ${pageErrors.join('; ')}`)
  console.log(`[verified] FU ${functionUnitId}: ${modules.length} areas, display totals `
    + `${totals.added}/${totals.modified}/${totals.removed}; screenshots: ${screenshots.join(', ')}`)
} finally {
  await browser.close()
}
