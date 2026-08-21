/**
 * Table Design list must order by table type: MAIN, then SUB, RELATION, ACTION.
 * The API returns creation order, which buries the main table among the rest.
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
const FU_ID = process.env.FU_ID ?? '50005'
const results = []
const rec = (n, ok, d = '') => { results.push({ n, ok, d }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`) }

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1700, height: 1000 } })).newPage()
try {
  await loginViaDwPassword(page)
  await page.goto(`http://localhost:3000/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(6000)
  await page.locator('text=Table Design').first().click()
  await page.waitForTimeout(5000)

  const rows = await page.locator('.el-table__row').allInnerTexts().catch(() => [])
  const clean = rows.map(r => r.replace(/\s+/g, ' ').trim()).filter(Boolean)
  console.log('   rows:', JSON.stringify(clean))
  rec('table list rendered', clean.length > 0, `${clean.length} rows`)

  // Map each row to its type tag, then assert the ranks are non-decreasing.
  // Longest label first: "Action Form Table" contains no other label, but ordering the probe
  // by length keeps this robust if labels are ever reworded to share a prefix.
  const ORDER = { 'Action Form Table': 3, 'Relation Table': 2, 'Main Table': 0, 'Sub Table': 1 }
  const labels = Object.keys(ORDER).sort((a, b) => b.length - a.length)
  const ranks = clean.map(r => {
    const hit = labels.find(label => r.includes(label))
    return hit === undefined ? 99 : ORDER[hit]
  })
  console.log('   ranks:', JSON.stringify(ranks))

  rec('Main table is first', ranks[0] === 0, clean[0]?.slice(0, 60))
  rec(
    'types are grouped in MAIN -> SUB -> RELATION -> ACTION order',
    ranks.every((r, i) => i === 0 || ranks[i - 1] <= r),
    ranks.join(' <= '),
  )

  await page.screenshot({ path: resolve(SHOTS, `${DATE}_table-design-order.png`) })
  console.log(`      shot in ${SHOTS}`)
} catch (e) {
  rec('run', false, String(e).slice(0, 200))
  await page.screenshot({ path: resolve(SHOTS, `${DATE}_table-design-order_ERROR.png`) }).catch(() => {})
} finally { await browser.close() }

const bad = results.filter(r => !r.ok)
console.log(`\n${results.length - bad.length}/${results.length} checks passed`)
process.exit(bad.length ? 1 : 0)
