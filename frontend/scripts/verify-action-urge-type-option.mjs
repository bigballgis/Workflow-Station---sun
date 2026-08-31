/**
 * Verifies the Developer Workstation Action Designer offers the URGE action type,
 * so Delegate / Transfer / Urge can be enabled per Function Unit instead of being
 * hardcoded in the User Portal task detail action bar.
 *
 * Run from frontend/:  node scripts/verify-action-urge-type-option.mjs
 * Screenshots land in frontend/developer-workstation/verification-screenshots/ and MUST NOT be deleted.
 */
import { mkdirSync } from 'fs'
import { join } from 'path'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const FU_ID = process.env.FU_ID || '50029'
const OUT = new URL('../developer-workstation/verification-screenshots/', import.meta.url).pathname
mkdirSync(OUT, { recursive: true })
const d = new Date()
const pad = (n) => String(n).padStart(2, '0')
const DATE = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const shot = async (page, name, opts = {}) => {
  const p = join(OUT, `${DATE}_${name}.png`)
  await page.screenshot({ path: p, ...opts })
  console.log('[shot]', p)
}

const failures = []
const check = (label, ok, detail = '') => {
  console.log(`[${ok ? 'pass' : 'FAIL'}] ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) failures.push(label)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage()

try {
  await loginViaDwPassword(page)
  await page.goto(`http://localhost:3000/dev/function-units/${FU_ID}?tab=actions`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(4000)

  // Land on the Actions tab, then open Create Action so the type dropdown is reachable.
  const actionsTab = page.locator('.el-tabs__item', { hasText: /Action Design/i }).first()
  if (await actionsTab.count()) {
    await actionsTab.click()
    await page.waitForTimeout(1500)
  }
  await shot(page, 'dw-action-designer-list')

  const createBtn = page.locator('button', { hasText: /Create Action/i }).first()
  await createBtn.click()
  await page.waitForTimeout(1200)

  await page.locator('.el-dialog .el-select').first().click()
  await page.waitForTimeout(1200)

  const urgeOption = page.locator('.el-select-dropdown__item', { hasText: /^\s*Urge\s*$/ }).first()
  check('Urge option present in Action Type dropdown', (await urgeOption.count()) > 0)
  await shot(page, 'dw-action-type-urge-option')

  const optionTexts = await page.locator('.el-select-dropdown__item').allInnerTexts()
  console.log('[options]', optionTexts.map((s) => s.trim()).join(' | '))
} finally {
  await browser.close()
}

if (failures.length) {
  console.error('\nFAILURES:', failures.join(', '))
  process.exit(1)
}
console.log('\nAll checks passed.')
