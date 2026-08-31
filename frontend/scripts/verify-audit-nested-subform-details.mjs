/**
 * Audit → application → Link Form Details: nested subTable stays in the modal.
 *
 * Usage (from frontend/):
 *   node scripts/verify-audit-nested-subform-details.mjs
 *
 * Optional: PROCESS_INSTANCE_ID, TABLE_TITLE (default /ACQ Transaction/i),
 *           NESTED_TITLE (default /Credit Card Correspondence/i)
 *
 * Output: user-portal/verification-screenshots/{date}_audit-nested-subform-*.png
 */
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const OUT = join(dirname(fileURLToPath(import.meta.url)), '../user-portal/verification-screenshots')
const DATE = new Date().toISOString().slice(0, 10)
const TABLE_TITLE = new RegExp(process.env.TABLE_TITLE || 'ACQ Transaction', 'i')
const NESTED_TITLE = new RegExp(process.env.NESTED_TITLE || 'Credit Card Correspondence', 'i')

mkdirSync(OUT, { recursive: true })

function check(label, ok, detail) {
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) throw new Error(`${label}${detail ? `: ${detail}` : ''}`)
}

async function shot(page, slug) {
  const path = join(OUT, `${DATE}_${slug}.png`)
  await page.screenshot({ path, fullPage: false })
  console.log(`[SHOT] ${path}`)
  return path
}

function unwrapList(body) {
  const data = body?.data ?? body
  if (Array.isArray(data)) return data
  return data?.content ?? data?.records ?? data?.items ?? []
}

function rowLooksLikeAcq(row) {
  const blob = [
    row.processDefinitionName, row.processDefinitionKey, row.processName,
    row.functionUnitName, row.functionUnitCode, row.businessKey, row.title,
  ].map((v) => String(v || '')).join(' ')
  return /acq/i.test(blob)
}

function rowId(row) {
  return String(row.processInstanceId || row.id || '').trim()
}

async function postList(page, url, data) {
  try {
    const res = await page.request.post(url, { data, timeout: 15000 })
    return { status: res.status(), rows: unwrapList(await res.json().catch(() => ({}))) }
  } catch (e) {
    console.log(`[list] ${url} failed: ${e.message}`)
    return { status: 0, rows: [] }
  }
}

async function findApplication(page) {
  const envId = String(process.env.PROCESS_INSTANCE_ID || '').trim()
  if (envId) return { id: envId, acq: true }

  const mine = await postList(page, `${ORIGIN}/api/portal/processes/my-applications/query`, { page: 0, size: 50 })
  const mineHit = mine.rows.find(rowLooksLikeAcq)
  console.log(`[my-apps] HTTP ${mine.status} rows=${mine.rows.length}`)
  if (mineHit) return { id: rowId(mineHit), acq: true }

  const todo = await postList(page, `${ORIGIN}/api/portal/tasks/todo/query`, { page: 0, size: 50 })
  const taskHit = todo.rows.find((t) => /acq/i.test(`${t.processDefinitionName || ''} ${t.processDefinitionKey || ''} ${t.processName || ''}`))
  console.log(`[todo] HTTP ${todo.status} rows=${todo.rows.length}`)
  if (taskHit?.processInstanceId) return { id: String(taskHit.processInstanceId), acq: true }

  const fusRes = await page.request.get(`${ORIGIN}/api/portal/processes/audit-function-units`)
  const fus = unwrapList(await fusRes.json().catch(() => ({})))
  console.log(`[audit-fu] HTTP ${fusRes.status()} names=${fus.map((f) => f.functionUnitName || f.functionUnitCode).join(',')}`)
  for (const fu of fus) {
    const code = String(fu.functionUnitCode || fu.code || '')
    if (!code) continue
    const { status, rows } = await postList(
      page,
      `${ORIGIN}/api/portal/processes/fu-applications/query?functionUnitCode=${encodeURIComponent(code)}`,
      { page: 0, size: 20 },
    )
    const hit = rows.find(rowLooksLikeAcq) || rows[0]
    console.log(`[apps] fu=${code} HTTP ${status} rows=${rows.length}`)
    if (hit) return { id: rowId(hit), acq: rowLooksLikeAcq(hit) }
  }
  return { id: '', acq: false }
}

async function clickDetails(page, requireAcqTable) {
  return page.evaluate(({ tableReSource, tableReFlags, requireAcqTable: requireMatch }) => {
    const tableRe = new RegExp(tableReSource, tableReFlags)
    const tables = [...document.querySelectorAll('.sub-table-field')]
    const match = tables.find((el) => tableRe.test(el.textContent || ''))
    const target = requireMatch ? match : (match || tables[0])
    if (!target) {
      return {
        clicked: false,
        reason: 'no-sub-table',
        titles: tables.map((el) => (el.querySelector('.sub-table-header, .sub-table-title')?.textContent || '').trim()),
      }
    }
    const details = [...target.querySelectorAll('a, .el-link, button, span')].find((el) =>
      /details|detail|详情/i.test(el.textContent || ''),
    )
    if (!details) return { clicked: false, reason: 'no-details' }
    details.click()
    return { clicked: true, title: (target.querySelector('.sub-table-header, .sub-table-title')?.textContent || '').trim() }
  }, { tableReSource: TABLE_TITLE.source, tableReFlags: TABLE_TITLE.flags, requireAcqTable })
}

const browser = await chromium.launch({ channel: 'chrome', headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 1100 } })

try {
  try {
    await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
  } catch (e) {
    console.log(`[login] hase-hmdc failed (${e.message}); retrying default workspace`)
    await loginViaPortalPassword(page)
  }

  let found = await findApplication(page)
  if (!found.id) {
    await loginViaPortalPassword(page)
    found = await findApplication(page)
  }
  check('found an application to open', Boolean(found.id))

  await page.goto(`${ORIGIN}/portal/applications/${found.id}?from=audit`, { waitUntil: 'domcontentloaded' })
  await page.locator('.application-detail, .el-form, .form-renderer').first().waitFor({ timeout: 40000 }).catch(() => {})
  await page.waitForTimeout(4000)
  const acqTable = page.locator('.sub-table-field').filter({ hasText: TABLE_TITLE }).first()
  const pageHasAcq = (await acqTable.count()) > 0
  if (pageHasAcq) {
    await acqTable.scrollIntoViewIfNeeded()
    await page.waitForTimeout(400)
  } else {
    const firstTable = page.locator('.sub-table-field').first()
    if (await firstTable.count()) await firstTable.scrollIntoViewIfNeeded()
  }
  await shot(page, 'audit-nested-subform-application')

  const click = await clickDetails(page, pageHasAcq)
  console.log('[click]', JSON.stringify(click))
  check('clicked Details on a sub-table row', Boolean(click.clicked), click.reason)

  const dialog = page.locator('.el-dialog:visible, .link-form-modal-panel:visible').last()
  await dialog.waitFor({ timeout: 15000 })
  await page.waitForTimeout(1500)
  const nested = dialog.locator('.sub-table-field').first()
  if (await nested.count()) await nested.scrollIntoViewIfNeeded()
  await shot(page, 'audit-nested-subform-details-modal')

  const dialogText = (await dialog.innerText()) || ''
  const nestedBlocks = await dialog.locator('.sub-table-field').count()
  const hasNestedTitle = NESTED_TITLE.test(dialogText)
  if (pageHasAcq || found.acq) {
    check(
      'Details modal shows nested Credit Card Correspondence',
      nestedBlocks > 0 || hasNestedTitle,
      `nestedBlocks=${nestedBlocks} titleMatch=${hasNestedTitle}`,
    )
  } else {
    console.log(
      `[SKIP] this env has no ACQ request; ATM Details modal nestedBlocks=${nestedBlocks} (ATM subForms have no nested subTable by design)`,
    )
  }
} finally {
  await browser.close()
}
