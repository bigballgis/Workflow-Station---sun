/**
 * Admin Relation Table Formula dialog: red ? opens /help/computed-fields#relation.
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaAdminPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(__dirname, '..', 'admin-center', 'verification-screenshots')
mkdirSync(OUT_DIR, { recursive: true })
const ORIGIN = (process.env.VERIFY_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const DATE = new Date().toISOString().slice(0, 10)

const results = []
const rec = (n, ok, d = '') => {
  results.push({ n, ok, d })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`)
}

function rowsFromPage(body) {
  const data = body.data ?? body
  if (Array.isArray(data)) return data
  if (Array.isArray(data.records)) return data.records
  if (Array.isArray(data.content)) return data.content
  return []
}

async function firstRelationTableId(page) {
  const given = process.env.AC_RT_ID
  if (given) return given
  const query = await page.request.post(`${ORIGIN}/api/v1/admin/relation-tables/structures/query`, {
    data: { page: 0, size: 20 },
  })
  const queried = rowsFromPage(await query.json().catch(() => ({})))
  if (queried[0]?.id) return queried[0].id
  const listed = await page.request.get(`${ORIGIN}/api/v1/admin/relation-tables/structures`)
  const rows = rowsFromPage(await listed.json().catch(() => ({})))
  return rows[0]?.id ?? null
}

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1680, height: 1100 } })
const page = await context.newPage()

try {
  await loginViaAdminPassword(page, { loginOrigin: ORIGIN })
  const tableId = await firstRelationTableId(page)
  if (tableId) {
    await page.goto(`${ORIGIN}/admin/relation-tables/structure/${tableId}/edit`, {
      waitUntil: 'domcontentloaded',
    })
  } else {
    await page.goto(`${ORIGIN}/admin/relation-tables/structure/create`, {
      waitUntil: 'domcontentloaded',
    })
  }
  await page.locator('.el-table').first().waitFor({ state: 'visible', timeout: 25000 })
  const addBtn = page.getByRole('button', { name: 'Add Field' }).first()
  if (await addBtn.count()) {
    await addBtn.click()
    await page.waitForTimeout(400)
  }
  await page.locator('.el-table').first().evaluate((el) => {
    const wrap = el.querySelector('.el-table__body-wrapper')
    if (wrap) wrap.scrollLeft = wrap.scrollWidth
  }).catch(() => {})
  const editorWait = page.locator('.computed-field-editor').first()
  try {
    await editorWait.waitFor({ state: 'visible', timeout: 15000 })
  } catch (err) {
    const debugPath = join(OUT_DIR, `${DATE}_ac-formula-help-debug.png`)
    await page.screenshot({ path: debugPath, fullPage: true })
    console.log(`debug screenshot ${debugPath}`)
    throw err
  }

  const editor = page.locator('.computed-field-editor').first()
  const sw = editor.locator('.el-switch').first()
  const cls = (await sw.getAttribute('class')) || ''
  if (!cls.includes('is-checked')) {
    await sw.click()
    await page.waitForTimeout(500)
  }
  const helpLink = page.getByTestId('computed-field-guide-link')
  if (!(await helpLink.isVisible().catch(() => false))) {
    const gear = editor.locator('.computed-config-btn').first()
    if (await gear.count()) {
      await gear.click()
    }
  }
  await helpLink.waitFor({ state: 'visible', timeout: 15000 })
  rec('Admin Formula dialog has the help ?', await helpLink.isVisible())

  const dlgShot = join(OUT_DIR, `${DATE}_ac-formula-help-link.png`)
  const dialog = page.getByRole('dialog').filter({ has: helpLink }).last()
  await dialog.screenshot({ path: dlgShot })
  console.log(`screenshot ${dlgShot}`)

  const popupPromise = context.waitForEvent('page')
  await helpLink.click()
  const popup = await popupPromise
  await popup.waitForURL(/\/help\/computed-fields/, { timeout: 15000 })
  rec(
    'Help ? opens /help/computed-fields#relation',
    popup.url().includes('/help/computed-fields') && popup.url().includes('relation'),
    popup.url(),
  )
  const targetShot = join(OUT_DIR, `${DATE}_ac-formula-help-target.png`)
  await popup.screenshot({ path: targetShot, fullPage: true })
  console.log(`screenshot ${targetShot}`)
  await popup.close()
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} passed`)
if (failed.length) process.exit(1)
