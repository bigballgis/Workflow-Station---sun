/**
 * Portal Editor create-time gate: saved / typed HTML must remain visible
 * after wangEditor mounts (not replaced by empty <p><br></p>).
 */
import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { PORTAL_ORIGIN, setupPortalSession } from './portal-verify-session.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const OUT_DIR = __dirname
const DATE = process.env.VERIFY_DATE || '2026-10-29'
const PROCESS_KEY = process.env.EDITOR_PROCESS_KEY || 'Process_MCY'
const MARKER = 'EditorGateKeep'

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1400, height: 900 } })
const page = await ctx.newPage()

await setupPortalSession(page, ctx)

const startUrl = `${PORTAL_ORIGIN}/portal/processes/start/${PROCESS_KEY}`
await page.goto(startUrl, { waitUntil: 'domcontentloaded', timeout: 120000 })

const editor = page.locator('.editor-wrapper .w-e-text-container').first()
await editor.waitFor({ state: 'visible', timeout: 30000 })
await page.waitForTimeout(1500)

const denied = await page.getByText('Access Denied').count()
if (denied > 0) {
  console.error('FAIL: Access Denied on', startUrl)
  await browser.close()
  process.exit(1)
}

await editor.click()
await page.keyboard.type(MARKER, { delay: 20 })
await page.waitForTimeout(1500)

const visibleText = (await editor.innerText()).replace(/\s+/g, ' ')
if (!visibleText.includes(MARKER)) {
  console.error('FAIL: typed marker missing after wait; editor text=', visibleText.slice(0, 200))
  await mkdir(OUT_DIR, { recursive: true })
  const failShot = path.join(OUT_DIR, `${DATE}_editor-create-gate-main-fail.png`)
  await page.locator('.editor-wrapper').screenshot({ path: failShot })
  console.error('Screenshot:', failShot)
  await browser.close()
  process.exit(1)
}

await mkdir(OUT_DIR, { recursive: true })
const mainShot = path.join(OUT_DIR, `${DATE}_editor-create-gate-main.png`)
await page.locator('.editor-wrapper').screenshot({ path: mainShot })
console.log('Screenshot:', mainShot)

const subEditor = page.locator('.sub-table-editor-wrapper .w-e-text-container')
const subCount = await subEditor.count()
if (subCount === 0) {
  console.log('N/A: no sub-table editor column on this start form')
} else {
  const subShot = path.join(OUT_DIR, `${DATE}_editor-create-gate-subtable.png`)
  await page.locator('.sub-table-editor-wrapper').first().screenshot({ path: subShot })
  console.log('Screenshot:', subShot)
}

console.log('PASS: main-form Editor kept typed HTML after create-time window')
await browser.close()
