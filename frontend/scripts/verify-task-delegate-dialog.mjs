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
const TODO_PAGES = 1
const TODO_SIZE = 20
const MAX_TASKS = 5

mkdirSync(OUT, { recursive: true })

function check(label, ok, detail) {
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) throw new Error(`${label}${detail ? `: ${detail}` : ''}`)
}

async function waitForTodoPage(page) {
  await page.locator('.tasks-page').waitFor({ timeout: 30000 })
  await page.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 20000 }).catch(() => {})
}

async function queryTodo(page, pageNo = 0) {
  const res = await page.request.post(`${ORIGIN}/api/portal/tasks/todo/query`, {
    data: { page: pageNo, size: TODO_SIZE },
  })
  const body = await res.json().catch(() => ({}))
  const pageData = body.data ?? body
  const content = pageData.content ?? pageData.records ?? []
  if (!Array.isArray(content) || content.length === 0) {
    console.log(`[todo] page=${pageNo} body keys=${Object.keys(body).join(',')} dataKeys=${pageData && typeof pageData === 'object' ? Object.keys(pageData).join(',') : typeof pageData}`)
  }
  console.log(`[todo] page=${pageNo} HTTP ${res.status()} rows=${Array.isArray(content) ? content.length : '?'}`)
  return Array.isArray(content) ? content : []
}

async function collectTodo(page) {
  const all = []
  for (let pageNo = 0; pageNo < TODO_PAGES; pageNo++) {
    const rows = await queryTodo(page, pageNo)
    all.push(...rows)
    if (rows.length < TODO_SIZE) break
  }
  return all
}

async function taskHasDelegateAction(page, taskId) {
  const res = await page.request.get(`${ORIGIN}/api/portal/tasks/${taskId}`)
  const body = await res.json().catch(() => ({}))
  const data = body.data ?? body
  const actions = data.actions ?? []
  return Array.isArray(actions) && actions.some((a) => String(a.actionType || '').toUpperCase() === 'DELEGATE')
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
page.on('response', async (res) => {
  const url = res.url()
  if (!url.includes('/relation-tables/') || (!url.includes('/search') && !url.includes('/view-fields'))) return
  let n = '?'
  try {
    const body = await res.json()
    const data = body?.data ?? body
    n = Array.isArray(data) ? String(data.length) : typeof data
  } catch {
    n = 'non-json'
  }
  console.log(`[lookup-api] ${res.status()} rows=${n} ${url}`)
})
page.on('console', (msg) => {
  if (msg.type() === 'error') console.log(`[page-error] ${msg.text()}`)
})

try {
  await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
  await page.goto(`${ORIGIN}/portal/tasks`, { waitUntil: 'domcontentloaded' })
  await waitForTodoPage(page)

  let tasks = await collectTodo(page)
  if (tasks.length === 0) {
    console.log('[todo] hase-hmdc empty, retrying default workspace')
    await loginViaPortalPassword(page)
    await page.goto(`${ORIGIN}/portal/tasks`, { waitUntil: 'domcontentloaded' })
    await waitForTodoPage(page)
    tasks = await collectTodo(page)
  }
  if (tasks.length === 0) {
    await debugShot(page, 'task-delegate-todo-empty')
  }
  check('To Do list has rows', tasks.length > 0, `count=${tasks.length}`)

  const withAssignee = tasks.filter(hasAssignee)
  const candidates = withAssignee.length > 0 ? withAssignee : tasks
  console.log(`[todo] assignee tasks=${withAssignee.length} of ${candidates.length}`)

  // This env's To Do rows are ATM tasks without a designed DELEGATE action.
  // Inject one on GET /tasks/{id} so the real ActionDialog + LookupField can be opened.
  await page.route('**/api/portal/tasks/*', async (route) => {
    if (route.request().method() !== 'GET') {
      await route.continue()
      return
    }
    const res = await route.fetch()
    const body = await res.json().catch(() => null)
    const data = body?.data
    if (data?.taskId && Array.isArray(data.actions)
      && !data.actions.some((a) => String(a.actionType || '').toUpperCase() === 'DELEGATE')) {
      data.actions.push({
        actionId: 'verify-delegate',
        actionName: 'Delegate',
        actionType: 'DELEGATE',
        description: '',
        icon: null,
        buttonColor: null,
        configJson: '{}',
      })
    }
    await route.fulfill({
      status: res.status(),
      headers: { ...res.headers(), 'content-type': 'application/json' },
      body: JSON.stringify(body),
    })
  })

  let opened = false
  for (const row of candidates.slice(0, MAX_TASKS)) {
    const id = taskIdOf(row)
    if (!id) continue
    const hasDelegate = await taskHasDelegateAction(page, id)
    if (!hasDelegate) {
      console.log(`[inject] task ${id} has no designed DELEGATE; page route will add one`)
    }
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

  const lookup = dialog.getByTestId('task-action-user-lookup')
  check('USER lookup field is visible', (await lookup.count()) > 0)
  await lookup.locator('input').click()
  const userList = page.locator('.lookup-dropdown--floating')
  await userList.waitFor({ timeout: 15000 })
  await userList.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 20000 }).catch(() => {})
  try {
    await userList.locator('.el-table__row').first().waitFor({ timeout: 20000 })
  } catch {
    const empty = await userList.locator('.el-table__empty-text, .el-empty__description').textContent().catch(() => '')
    console.log(`[lookup] no rows after wait emptyText=${JSON.stringify(empty)}`)
  }
  const userShot = join(OUT, `${DATE}_task-delegate-dialog-user-lookup.png`)
  await page.screenshot({ path: userShot, fullPage: false })
  console.log(`[SHOT] ${userShot}`)
  const rowCount = await userList.locator('.el-table__row').count()
  check('sys_users lookup list has rows', rowCount > 0, `rows=${rowCount}`)
  check('first open is one page, not the full user table', rowCount <= 200, `rows=${rowCount}`)
  const headerText = await userList.locator('.el-table__header').textContent().catch(() => '')
  check(
    'column headers use display names',
    /Username|用户名|使用者名稱/.test(headerText)
      && /Email|邮箱|電子郵件/.test(headerText)
      && !/display_name/.test(headerText),
    `header=${JSON.stringify(headerText)}`,
  )
  const overflowX = await userList.evaluate((el) => getComputedStyle(el).overflowX)
  const canScrollX = await userList.evaluate((el) => el.scrollWidth > el.clientWidth + 4)
  check('lookup dropdown allows horizontal scroll', overflowX === 'auto' || overflowX === 'scroll', overflowX)
  check('lookup table is wider than the panel (scrollbar can move)', canScrollX, `scroll=${await userList.evaluate((el) => `${el.scrollWidth}x${el.clientWidth}`)}`)

  const searchInput = lookup.locator('input')
  const searchRes = page.waitForResponse(
    (res) => res.url().includes('/relation-tables/-1000000001/search') && res.url().includes('keyword='),
    { timeout: 15000 },
  )
  await searchInput.fill('li')
  const keywordRes = await searchRes.catch(() => null)
  check('typed keyword hits the search API', !!keywordRes, keywordRes ? keywordRes.url() : 'no keyword request')
  await userList.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 15000 }).catch(() => {})
  const searchShot = join(OUT, `${DATE}_task-delegate-dialog-user-lookup-search.png`)
  await page.screenshot({ path: searchShot, fullPage: false })
  console.log(`[SHOT] ${searchShot}`)
  await page.keyboard.press('Escape')
  await userList.waitFor({ state: 'hidden', timeout: 3000 }).catch(() => {})

  try {
    await dialog.locator('.el-radio').filter({ hasText: /指定 BU 和 Role|Specified BU and Role/ }).click({ force: true, timeout: 5000 })
    await dialog.locator('.el-cascader').waitFor({ timeout: 8000 })
    const buShot = join(OUT, `${DATE}_task-delegate-dialog-bu-role.png`)
    await dialog.screenshot({ path: buShot })
    console.log(`[SHOT] ${buShot}`)
  } catch (err) {
    console.log(`[skip] BU+Role shot after user lookup — ${err.message}`)
  }
} finally {
  await browser.close()
}
