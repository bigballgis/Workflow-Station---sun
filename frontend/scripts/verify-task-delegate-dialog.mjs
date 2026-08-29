/**
 * Login via portal password, open a To Do task with an assignee, click Delegate,
 * screenshot the dialog (title + red ? help link).
 *
 * Usage (from frontend/):
 *   node scripts/verify-task-delegate-dialog.mjs
 *
 * Output: user-portal/verification-screenshots/{date}_task-delegate-dialog.png
 */
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const OUT = join(dirname(fileURLToPath(import.meta.url)), '../user-portal/verification-screenshots')
const DATE = new Date().toISOString().slice(0, 10)
const MAX_TASKS = 20

mkdirSync(OUT, { recursive: true })

function check(label, ok, detail) {
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) throw new Error(`${label}${detail ? `: ${detail}` : ''}`)
}

async function waitForTodoPage(page) {
  await page.locator('.tasks-page').waitFor({ timeout: 30000 })
  await page.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 20000 }).catch(() => {})
}

async function queryTodo(page) {
  const res = await page.request.post(`${ORIGIN}/api/portal/tasks/todo/query`, {
    data: { page: 0, size: 50 },
  })
  const body = await res.json().catch(() => ({}))
  const pageData = body.data ?? body
  const content = pageData.content ?? pageData.records ?? []
  if (!Array.isArray(content) || content.length === 0) {
    console.log(`[todo] body keys=${Object.keys(body).join(',')} dataKeys=${pageData && typeof pageData === 'object' ? Object.keys(pageData).join(',') : typeof pageData}`)
  }
  console.log(`[todo] HTTP ${res.status()} rows=${Array.isArray(content) ? content.length : '?'}`)
  return Array.isArray(content) ? content : []
}

function taskIdOf(row) {
  return String(row.taskId || row.id || '').trim()
}

function hasAssignee(row) {
  return String(row.assignee || '').trim().length > 0
}

async function debugShot(page, slug) {
  const path = join(OUT, `${DATE}_${slug}.png`)
  await page.screenshot({ path, fullPage: false })
  console.log(`[DEBUG] ${path} url=${page.url()}`)
  return path
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

try {
  await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
  await page.goto(`${ORIGIN}/portal/tasks`, { waitUntil: 'domcontentloaded' })
  await waitForTodoPage(page)

  let tasks = await queryTodo(page)
  if (tasks.length === 0) {
    console.log('[todo] hase-hmdc empty, retrying default workspace')
    await loginViaPortalPassword(page)
    await page.goto(`${ORIGIN}/portal/tasks`, { waitUntil: 'domcontentloaded' })
    await waitForTodoPage(page)
    tasks = await queryTodo(page)
  }
  if (tasks.length === 0) {
    await debugShot(page, 'task-delegate-todo-empty')
  }
  check('To Do list has rows', tasks.length > 0, `count=${tasks.length}`)

  const withAssignee = tasks.filter(hasAssignee)
  const candidates = withAssignee.length > 0 ? withAssignee : tasks
  console.log(`[todo] assignee tasks=${withAssignee.length} of ${candidates.length}`)

  let opened = false
  for (const row of candidates.slice(0, MAX_TASKS)) {
    const id = taskIdOf(row)
    if (!id) continue
    await page.goto(`${ORIGIN}/portal/tasks/${id}`, { waitUntil: 'domcontentloaded' })
    const bar = page.locator('.action-section .right-actions')
    await bar.waitFor({ timeout: 20000 }).catch(() => {})
    const btn = bar.getByRole('button', { name: /^(委托|Delegate|委託)$/ })
    try {
      await btn.first().waitFor({ timeout: 8000 })
      await btn.first().click()
      opened = true
      break
    } catch {
      const labels = (await bar.locator('button').allTextContents()).map((s) => s.replace(/\s+/g, ' ').trim())
      console.log(`[skip] task ${id} buttons=${JSON.stringify(labels)} (assignee=${row.assignee || ''})`)
    }
  }
  if (!opened) {
    await debugShot(page, 'task-delegate-no-button')
  }
  check('Opened Delegate dialog from a task with assignee', opened)

  const dialog = page.locator('.el-dialog').filter({ has: page.locator('.task-action-dialog-title') })
  await dialog.waitFor({ timeout: 10000 })
  const help = dialog.locator('[data-testid="task-delegate-guide-link"]')
  check('Help ? is in the Delegate dialog title', (await help.count()) > 0)
  check(
    'USER / BU+Role radios are visible',
    (await dialog.getByRole('radio').count()) >= 2,
  )

  const shot = join(OUT, `${DATE}_task-delegate-dialog.png`)
  await dialog.screenshot({ path: shot })
  console.log(`[SHOT] ${shot}`)

  await dialog.locator('.el-radio').filter({ hasText: /指定 BU 和 Role|Specified BU and Role/ }).click()
  await dialog.locator('.el-cascader').waitFor({ timeout: 8000 })
  const buShot = join(OUT, `${DATE}_task-delegate-dialog-bu-role.png`)
  await dialog.screenshot({ path: buShot })
  console.log(`[SHOT] ${buShot}`)
} finally {
  await browser.close()
}
