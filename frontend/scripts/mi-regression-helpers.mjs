/**
 * Shared helpers for MI regression Playwright scripts.
 */
import { mkdirSync } from 'fs'
import { join } from 'path'

export const OUT_DIR = join(process.cwd(), 'user-portal', 'verification-screenshots')

export function todayPrefix() {
  return new Date().toISOString().slice(0, 10)
}

export function screenshotPath(slug) {
  mkdirSync(OUT_DIR, { recursive: true })
  return join(OUT_DIR, `${todayPrefix()}_${slug}.png`)
}

/** @param {import('playwright').Page} page */
export async function countSubTableRows(page, titleMatch) {
  return page.evaluate((reSource) => {
    const re = new RegExp(reSource, 'i')
    const block = [...document.querySelectorAll('.sub-table-field')].find(el =>
      re.test(el.querySelector('.title, .sub-table-header')?.textContent?.trim() ?? ''),
    )
    if (!block) return { found: false, count: -1 }
    const rows = [...block.querySelectorAll('.el-table__body-wrapper tbody tr.el-table__row')].filter(tr =>
      (tr.querySelector('td')?.textContent?.trim() ?? '').length > 0,
    )
    return { found: true, count: rows.length }
  }, titleMatch)
}

/** @param {import('playwright').Page} page */
export async function readPeopleInlineFields(page) {
  return page.evaluate(() => {
    const root =
      document.querySelector('.sub-table-inline-form')
      || [...document.querySelectorAll('.el-card, .form-layout-card, .sub-table-field')].find(c =>
        /people/i.test(c.textContent || ''),
      )
    if (!root) return null
    return [...root.querySelectorAll('.el-form-item')].map(i => ({
      label: i.querySelector('.el-form-item__label')?.textContent?.trim() ?? '',
      val: i.querySelector('input')?.value ?? '',
      checked: i.querySelector('.el-switch.is-checked') != null,
    }))
  })
}

export function fieldByLabel(fields, labelRe) {
  return fields?.find(f => labelRe.test(f.label || ''))?.val ?? ''
}

export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function isBlankDisplayId(val) {
  const s = String(val ?? '').trim()
  return s === '' || s === '-'
}

export function unwrapApiList(body) {
  const page = body?.data ?? body
  if (Array.isArray(page?.content)) return page.content
  if (Array.isArray(page?.records)) return page.records
  if (Array.isArray(page)) return page
  return []
}

export const MI_PORTAL_ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'

const MI_NAME_RE = /multi[\s-]*instance|subtask demo|fu-20260422|atm-20260623|transaction/i

/** @param {import('playwright').Page} page */
export async function listPortalTodoTasks(page, origin = MI_PORTAL_ORIGIN, { size = 50 } = {}) {
  const res = await page.request.post(`${origin}/api/portal/tasks/todo/query`, {
    data: { page: 0, size, filters: [] },
  })
  if (!res.ok()) throw new Error(`todo/query HTTP ${res.status()}`)
  return unwrapApiList(await res.json())
}

export function preferMiTasks(rows, extraRe) {
  const re = extraRe || MI_NAME_RE
  const named = (rows || []).filter((row) =>
    re.test([row.processDefinitionName, row.functionUnitCode, row.taskName, row.currentStepName].join(' ')),
  )
  return named.length > 0 ? named : (rows || [])
}

/** @param {import('playwright').Page} page */
export async function openPortalTask(page, taskId, origin = MI_PORTAL_ORIGIN) {
  await page.goto(`${origin}/portal/tasks/${taskId}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(10000)
}

/**
 * Open TASK_ID / argv[2] when set; otherwise the first matching To Do.
 * @param {import('playwright').Page} page
 */
export async function resolveAndOpenTodo(page, { prefer, envKey = 'TASK_ID' } = {}) {
  const pinned = process.env[envKey] || process.argv[2] || ''
  if (pinned) {
    await openPortalTask(page, pinned)
    return pinned
  }
  const rows = preferMiTasks(await listPortalTodoTasks(page), prefer)
  const id = rows[0]?.taskId
  if (!id) throw new Error('no To Do task to open')
  await openPortalTask(page, id)
  return id
}

/**
 * Try several MI To Dos until `predicate` is true; fall back to the first.
 * @param {import('playwright').Page} page
 * @param {(page: import('playwright').Page) => Promise<boolean>} predicate
 */
export async function openFirstTodoMatching(page, predicate, { prefer, limit = 3 } = {}) {
  const pinned = process.env.TASK_ID || process.argv[2] || ''
  if (pinned) {
    await openPortalTask(page, pinned)
    return pinned
  }
  const rows = preferMiTasks(await listPortalTodoTasks(page), prefer).slice(0, limit)
  if (rows.length === 0) throw new Error('no To Do task to open')
  for (const row of rows) {
    await openPortalTask(page, row.taskId)
    if (await predicate(page)) return row.taskId
  }
  await openPortalTask(page, rows[0].taskId)
  return rows[0].taskId
}

/** @param {import('playwright').Page} page */
export async function listPortalApplications(page, origin, { status = 'RUNNING', size = 50 } = {}) {
  const res = await page.request.get(`${origin}/api/portal/processes/my-applications`, {
    params: { page: 0, size, status },
  })
  if (!res.ok()) {
    throw new Error(`my-applications HTTP ${res.status()}`)
  }
  return unwrapApiList(await res.json())
}

export function preferMiApplications(rows) {
  const named = rows.filter((row) =>
    /multi[\s-]*instance|subtask demo|mi\b/i.test(String(row.processDefinitionName || row.title || '')),
  )
  return named.length > 0 ? named : rows
}

/** @param {import('playwright').Page} page */
export async function listMiCollectionTables(page) {
  return page.evaluate(() => {
    return [...document.querySelectorAll('.sub-table-field')].map((el) => {
      const title = el.querySelector('.title, .sub-table-header')?.textContent?.trim() ?? ''
      const rows = [...el.querySelectorAll('.el-table__body-wrapper tbody tr.el-table__row')]
        .map((tr) => {
          const rowId = tr.querySelector('td')?.textContent?.trim() ?? ''
          const hasDetails = [...tr.querySelectorAll('a, .el-link, button, span')].some((a) =>
            /details|detail|详情/i.test(a.textContent || ''),
          )
          return { rowId, hasDetails }
        })
        .filter((row) => row.rowId)
      return { title, rows }
    }).filter((table) => table.rows.length > 0)
  })
}

export function pickMiCollectionTable(tables) {
  if (!Array.isArray(tables) || tables.length === 0) return null
  return (
    tables.find((table) => table.rows.some((row) => row.hasDetails))
    || tables.find((table) => /sub\s*task|participant/i.test(table.title || ''))
    || tables[0]
  )
}

/** @param {import('playwright').Page} page */
export async function clickSubTaskDetails(page, rowId) {
  return page.evaluate((pid) => {
    const rows = [...document.querySelectorAll('.sub-table-field .el-table__body tr.el-table__row')]
    const targetRow = rows.find((tr) => (tr.querySelector('td')?.textContent?.trim() ?? '') === pid)
    if (!targetRow) return null
    const detail = [...targetRow.querySelectorAll('a, .el-link, button, span')].find((el) =>
      /details|detail|详情/i.test(el.textContent || ''),
    )
    if (!detail) return null
    detail.click()
    return pid
  }, rowId)
}

/** @param {import('playwright').Page} page */
export async function readLinkFormModalFields(page) {
  return page.evaluate(() => {
    const modal =
      document.querySelector('.link-form-modal-panel')
      || [...document.querySelectorAll('.el-overlay')].find((o) => o.textContent?.match(/subtable|people|detail/i))
    if (!modal) return { found: false, fields: [] }
    const readVal = (item) => {
      const input = item.querySelector('input')
      if (input) return input.value ?? ''
      const text = item.querySelector('.readonly-text, .el-form-item__content span')
      return text?.textContent?.trim() ?? ''
    }
    return {
      found: true,
      title: modal.querySelector('.link-form-modal-title, .el-dialog__title')?.textContent?.trim(),
      fields: [...modal.querySelectorAll('.el-form-item')].map((i) => ({
        label: i.querySelector('.el-form-item__label')?.textContent?.trim(),
        val: readVal(i),
      })),
    }
  })
}

/** @param {import('playwright').Page} page */
export async function closeLinkFormModal(page) {
  await page.evaluate(() => {
    document.querySelector('.link-form-modal-close, .el-dialog__headerbtn')?.click()
  })
  await page.waitForTimeout(800)
}
