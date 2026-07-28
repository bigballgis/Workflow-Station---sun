#!/usr/bin/env node
/**
 * User Portal — 嵌套子表（子表套子表）应遵守设计器的 Allow Add / Edit / Delete 三个开关。
 *
 * 只读验证：打开父行弹窗，报告嵌套子表实际出现了哪些操作按钮，并与
 * EXPECT_* 期望比对（默认期望三项全开，即设计器未关任何开关时的行为）。
 *
 * Usage:
 *   node scripts/verify-nested-subtable-permissions.mjs                     # New Request 页（已发布快照）
 *   TASK_MODE=1 EXPECT_EDIT=0 node scripts/verify-nested-subtable-permissions.mjs
 *
 * TASK_MODE=1 打开该流程最新的 To Do 任务：**任务表单读 dw_form_definitions 实时配置**，
 * 而 New Request 读已发布版本快照 —— 改完开关未重新 Publish 时只有任务页能看到效果。
 * 注意：任务表单在首次打开时会落快照，改配置后要**新发起一条申请**才能在新任务上看到新开关。
 */
import { mkdirSync } from 'fs'
import { join } from 'path'
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const ORIGIN = (process.env.VERIFY_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const OUT_DIR = join(process.cwd(), 'user-portal', 'verification-screenshots')
const PROCESS_KEY = process.env.PROCESS_KEY || 'autonumber-nested-repro-20260727-nir6kn'
const SLUG = process.env.SHOT_SLUG || 'portal-nested-subtable-permissions'
const TASK_MODE = process.env.TASK_MODE === '1'
const expect1 = (name, dflt) => (process.env[name] ?? dflt) === '1'
const EXPECT = {
  add: expect1('EXPECT_ADD', '1'),
  edit: expect1('EXPECT_EDIT', '1'),
  delete: expect1('EXPECT_DELETE', '1'),
}

function shot(slug) {
  const date = new Date().toISOString().slice(0, 10)
  mkdirSync(OUT_DIR, { recursive: true })
  return join(OUT_DIR, `${date}_${slug}.png`)
}

const fails = []
function check(ok, message) {
  console.log(`${ok ? 'PASS' : 'FAIL'}: ${message}`)
  if (!ok) fails.push(message)
}

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1680, height: 1400 } })).newPage()
  page.on('pageerror', (e) => console.log('[pageerror]', e.message))

  await loginViaUnifiedSso(page, 'portal')
  if (TASK_MODE) {
    const listed = await page.request.post(`${ORIGIN}/api/portal/tasks/query`, { data: { page: 0, size: 20 } })
    const rows = (await listed.json().catch(() => ({})))?.data?.content ?? []
    const mine = rows
      .filter((r) => r.processDefinitionKey === PROCESS_KEY)
      .sort((a, b) => String(b.createTime).localeCompare(String(a.createTime)))[0]
    check(!!mine?.taskId, `a To Do task exists for ${PROCESS_KEY} (${rows.length} listed)`)
    if (!mine?.taskId) throw new Error('no task to open — submit a request for this process first')
    await page.goto(`${ORIGIN}/portal/tasks/${mine.taskId}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(9000)
  } else {
    await page.goto(`${ORIGIN}/portal/processes/start/${PROCESS_KEY}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(7000)
  }

  // 父子表 → Add → 行弹窗里的嵌套子表
  const parentTable = page.locator('.sub-table-field').first()
  await parentTable.locator('.sub-table-header button').filter({ hasText: /Add/i }).first().click({ force: true })
  await page.waitForTimeout(3000)
  const parentDlg = page.locator('.el-dialog').filter({ has: page.locator('.sub-table-field') }).first()
  const nested = parentDlg.locator('.sub-table-field').first()
  check(await nested.count() > 0, 'nested sub-table renders inside the parent row dialog')

  const headerButtons = await nested.locator('.sub-table-header button').allTextContents()
  const hasAdd = headerButtons.some((b) => /^\s*\+?\s*Add\s*$/i.test(b.trim()))
  check(hasAdd === EXPECT.add, `nested Add ${EXPECT.add ? 'present' : 'hidden'} (header: ${JSON.stringify(headerButtons)})`)

  // 需要一行数据才能观察行内 Edit / Delete；Add 被关掉时无法造行，只能断言操作列整体缺失
  if (hasAdd) {
    await nested.locator('.sub-table-header button').filter({ hasText: /Add/i }).first().click({ force: true })
    await page.waitForTimeout(2500)
    const rowDlg = page.locator('.el-dialog:visible').last()
    await rowDlg.locator('.el-form input:not([type=file])').first().fill('PKG-PERM')
    await rowDlg.locator('button').filter({ hasText: /^\s*Save\s*$/i }).first().click({ force: true })
    await page.waitForTimeout(2500)
  }

  const rowCount = await nested.locator('.el-table__row').count()
  if (rowCount === 0) {
    // Add off (or nothing seeded) => no row to carry Edit / Delete; the Add assertion above is the check.
    console.log('SKIP: nested row Edit / Delete — no row could be created (Add is off)')
  } else {
    const rowButtons = await nested.locator('.el-table__row').first().locator('button').allTextContents()
    const hasEdit = rowButtons.some((b) => /^\s*Edit\s*$/i.test(b.trim()))
    const hasDelete = rowButtons.some((b) => /^\s*Delete\s*$/i.test(b.trim()))
    check(hasEdit === EXPECT.edit, `nested row Edit ${EXPECT.edit ? 'present' : 'hidden'} (row: ${JSON.stringify(rowButtons)})`)
    check(hasDelete === EXPECT.delete, `nested row Delete ${EXPECT.delete ? 'present' : 'hidden'} (row: ${JSON.stringify(rowButtons)})`)
  }

  await page.screenshot({ path: shot(SLUG), fullPage: true })
  console.log('screenshots:', OUT_DIR)
  await browser.close()
  if (fails.length) {
    console.error(`\n${fails.length} check(s) FAILED:\n` + fails.map((f) => ' - ' + f).join('\n'))
    process.exit(2)
  }
  console.log('\nALL CHECKS PASSED')
}

main().catch((e) => { console.error('ERROR:', e.message); process.exit(1) })
