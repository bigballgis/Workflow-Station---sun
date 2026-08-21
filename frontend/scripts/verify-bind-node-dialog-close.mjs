/**
 * Bind Process Node dialog: Confirm is a commit, so it must close the dialog. It used to
 * stay open (only a toast signalled success), which read as "nothing happened" and got
 * users clicking Confirm repeatedly.
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const SHOTS = resolve(__dirname, '../developer-workstation/verification-screenshots')
mkdirSync(SHOTS, { recursive: true })
const DATE = new Date().toISOString().slice(0, 10)
const results = []
const rec = (n, ok, d = '') => { results.push({ n, ok, d }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`) }

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1900, height: 1100 } })).newPage()
try {
  await loginViaDwPassword(page)
  await page.goto('http://localhost:3000/dev/function-units/50005', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(6000)
  await page.locator('text=Form Design').first().click()
  await page.waitForTimeout(5000)

  // Exercise the My Requests scene specifically — that is the path under discussion.
  await page.locator('.el-tabs__item').filter({ hasText: 'My Requests' }).first().click()
  await page.waitForTimeout(3000)

  const row = page.locator('.el-table__row').first()
  await row.locator('text=More').first().click()
  await page.waitForTimeout(1200)
  await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: 'Bound Node' }).first().click()
  await page.waitForTimeout(3500)

  const dialog = page.locator('.el-dialog').filter({ hasText: 'Bind Process Node' }).first()
  const openedNow = await dialog.isVisible().catch(() => false)
  rec('Bound Node dialog opened', openedNow)
  await page.screenshot({ path: resolve(SHOTS, `${DATE}_bind-node-dialog_open.png`) })

  await dialog.locator('button').filter({ hasText: /Confirm|确定/ }).first().click()

  // Poll for the toast: el-message auto-dismisses after ~3s, so reading document text later
  // finds nothing and looks like a failed save.
  let toast = ''
  for (let i = 0; i < 15; i++) {
    await page.waitForTimeout(300)
    const seen = await page.locator('.el-message').allInnerTexts().catch(() => [])
    if (seen.length) { toast = seen.join(' | ').replace(/\s+/g, ' ').trim(); break }
  }
  rec('success toast shown', /success|成功/i.test(toast), toast.slice(0, 70))

  await page.waitForTimeout(3500)
  const stillVisible = await dialog.isVisible().catch(() => false)
  rec('dialog closes after Confirm', !stillVisible)

  await page.screenshot({ path: resolve(SHOTS, `${DATE}_bind-node-dialog_after-confirm.png`) })
  console.log(`      shots in ${SHOTS}`)
} catch (e) {
  rec('run', false, String(e).slice(0, 200))
  await page.screenshot({ path: resolve(SHOTS, `${DATE}_bind-node-dialog_ERROR.png`) }).catch(() => {})
} finally { await browser.close() }

const bad = results.filter(r => !r.ok)
console.log(`\n${results.length - bad.length}/${results.length} checks passed`)
process.exit(bad.length ? 1 : 0)
