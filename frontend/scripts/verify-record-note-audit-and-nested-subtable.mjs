#!/usr/bin/env node
/**
 * User Portal 验证（三合一）:
 *  1. Record Note 的新增/编辑/删除写入 Change History（Audit history）。
 *  2. Record Note 组件 Allow Delete 默认 false —— 面板不出现 Delete 按钮。
 *  3. 子表套子表：父行弹窗里的嵌套子表操作列（Edit/Delete）可见可点。
 *
 * Usage: node scripts/verify-record-note-audit-and-nested-subtable.mjs
 *   PROCESS_KEY  发起用的流程 key（默认 dev 上的 FU 50018 AutoNumber Nested Repro）
 *   FU_NAME      To Do 列表里定位该任务用的名称正则
 */
import { mkdirSync } from 'fs'
import { join } from 'path'
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const ORIGIN = (process.env.VERIFY_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const OUT_DIR = join(process.cwd(), 'user-portal', 'verification-screenshots')
const FU_NAME = new RegExp(process.env.FU_NAME || 'AutoNumber Nested Repro', 'i')
const PROCESS_KEY = process.env.PROCESS_KEY || 'autonumber-nested-repro-20260727-nir6kn'
const TAG = `AUDIT-${Date.now().toString().slice(-6)}`
/** 置 1 表示被测表单已打开 Record Note 的 Allow Delete —— 此时还会验证删除写入审计。 */
const EXPECT_ALLOW_DELETE = process.env.EXPECT_ALLOW_DELETE === '1'

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

  // ── 发起一条含嵌套子表 + Record Note 的申请 ──────────────────────────────
  await page.goto(`${ORIGIN}/portal/processes/start/${PROCESS_KEY}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(7000)
  check(page.url().includes(PROCESS_KEY), `New Request form opened for ${PROCESS_KEY}`)

  const titleInput = page.locator('input[placeholder*="Order Title"]').first()
  if (await titleInput.count()) await titleInput.fill(`ORDER-${TAG}`)

  // 3) 子表套子表：父行弹窗中的嵌套子表操作列
  const parentTable = page.locator('.sub-table-field').first()
  await parentTable.locator('.sub-table-header button').filter({ hasText: /Add/i }).first().click({ force: true })
  await page.waitForTimeout(3000)
  const parentDlg = page.locator('.el-dialog').filter({ has: page.locator('.sub-table-field') }).first()
  const nested = parentDlg.locator('.sub-table-field').first()
  await nested.locator('.sub-table-header button').filter({ hasText: /Add/i }).first().click({ force: true })
  await page.waitForTimeout(2500)
  const nestedRowDlg = page.locator('.el-dialog:visible').last()
  await nestedRowDlg.locator('.el-form input:not([type=file])').first().fill(`PKG-${TAG}`)
  await nestedRowDlg.locator('button').filter({ hasText: /^\s*Save\s*$/i }).first().click({ force: true })
  await page.waitForTimeout(2500)
  await page.screenshot({ path: shot('portal-nested-subtable-row-in-dialog'), fullPage: true })

  const nestedEdit = nested.locator('.el-table__row').first().locator('button').filter({ hasText: /^\s*Edit\s*$/i }).first()
  const dlgBox = await parentDlg.boundingBox()
  const editBox = await nestedEdit.boundingBox().catch(() => null)
  check(await nestedEdit.count() > 0, 'nested sub-table row exposes an Edit button')
  check(
    !!(editBox && dlgBox && editBox.x >= dlgBox.x && editBox.x + editBox.width <= dlgBox.x + dlgBox.width),
    `nested Edit button sits inside the dialog (dialog x=${dlgBox?.x}..${(dlgBox?.x ?? 0) + (dlgBox?.width ?? 0)}, button x=${editBox?.x})`,
  )
  // 真点一次并改值，证明可编辑
  await nestedEdit.click({ force: true })
  await page.waitForTimeout(2500)
  const nestedEditDlg = page.locator('.el-dialog:visible').last()
  const nestedInput = nestedEditDlg.locator('.el-form input:not([type=file])').first()
  await nestedInput.fill(`PKG-${TAG}-EDITED`)
  await nestedEditDlg.locator('button').filter({ hasText: /^\s*Save\s*$/i }).first().click({ force: true })
  await page.waitForTimeout(2500)
  const nestedCells = await nested.locator('.el-table__row').first().locator('.cell').allTextContents()
  check(nestedCells.some((c) => c.includes(`PKG-${TAG}-EDITED`)), 'nested sub-table row edit is applied')
  await page.screenshot({ path: shot('portal-nested-subtable-edit-applied'), fullPage: true })
  await parentDlg.locator('.el-dialog__footer button').filter({ hasText: /^\s*Save\s*$/i }).first().click({ force: true })
  await page.waitForTimeout(3000)

  // 提交申请 → 拿到实例（Record Note 的 Change History 需要实例上下文）
  await page.locator('button').filter({ hasText: /Submit Application/i }).first().click({ force: true })
  await page.waitForTimeout(2000)
  const confirm = page.locator('.el-message-box button').filter({ hasText: /OK|Confirm|确定/i }).first()
  if (await confirm.count()) await confirm.click({ force: true })
  await page.waitForTimeout(9000)

  // ── 进入刚产生的 To Do 任务：写一条 Record Note，检查 Change History ────
  // 走 API 取任务 id（To Do 列表的行文案随视图配置变化，按名字点行不稳）。
  const listed = await page.request.post(`${ORIGIN}/api/portal/tasks/query`, {
    data: { page: 0, size: 20 },
  })
  const body = await listed.json().catch(() => ({}))
  const rows = body?.data?.content ?? body?.content ?? []
  const mine = rows
    .filter((r) => r.processDefinitionKey === PROCESS_KEY)
    .sort((a, b) => String(b.createTime).localeCompare(String(a.createTime)))[0]
  check(!!mine?.taskId, `To Do task for the submitted request is queryable (${rows.length} task(s) listed)`)
  if (!mine?.taskId) throw new Error('no task to open')
  await page.goto(`${ORIGIN}/portal/tasks/${mine.taskId}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(9000)
  console.log('task url:', page.url())

  // 主表单上的 TABLE 作用域面板（RECORD 作用域面板默认折叠，Add 藏在折叠区里）
  const notes = page.locator('.record-note-field:not(.is-compact)').first()
  check(await notes.count() > 0, 'Record Note panel is rendered on the task form')
  await notes.locator('.rn-header-actions button').filter({ hasText: /Add/i }).first().click({ force: true })
  await notes.locator('.rn-editor').waitFor({ state: 'visible', timeout: 15000 })
  await page.waitForTimeout(1500)
  await notes.locator('.rn-editor-body [contenteditable="true"]').first().click()
  await page.keyboard.type(`note ${TAG}`)
  await notes.locator('.rn-editor-actions button').filter({ hasText: /Post|Submit/i }).first().click({ force: true })
  await page.waitForTimeout(4000)
  await page.screenshot({ path: shot('portal-record-note-added'), fullPage: true })

  // 2) Allow Delete：默认 false → 无 Delete 按钮；EXPECT_ALLOW_DELETE=1 时（表单已打开该开关）应出现
  const noteItem = notes.locator('.rn-item').first()
  const noteButtons = await noteItem.locator('button').allTextContents()
  const hasDelete = noteButtons.some((b) => /^\s*Delete\s*$/i.test(b))
  if (EXPECT_ALLOW_DELETE) {
    check(hasDelete, `note entry shows Delete when the switch is on (buttons: ${JSON.stringify(noteButtons)})`)
  } else {
    check(!hasDelete, `note entry has no Delete button by default (buttons: ${JSON.stringify(noteButtons)})`)
  }
  check(noteButtons.some((b) => /^\s*Edit\s*$/i.test(b)),
    'note entry still offers Edit (allowEditOwn default stays on)')

  // 1) Change History 里应出现 Note Added，且值含备注正文
  const history = page.locator('.ch-root-collapse').first()
  await history.scrollIntoViewIfNeeded().catch(() => {})
  await page.waitForTimeout(2500)
  const historyText = ((await history.textContent()) || '').replace(/\s+/g, ' ')
  check(/Note Added/i.test(historyText), 'Change History shows a "Note Added" entry')
  check(historyText.includes(`note ${TAG}`), 'Change History entry carries the note text')
  await page.screenshot({ path: shot('portal-record-note-in-change-history'), fullPage: true })

  // 编辑该备注 → Change History 出现 Note Edited
  await noteItem.locator('button').filter({ hasText: /^\s*Edit\s*$/i }).first().click({ force: true })
  await notes.locator('.rn-editor').waitFor({ state: 'visible', timeout: 15000 })
  await page.waitForTimeout(1500)
  const editArea = notes.locator('.rn-editor-body [contenteditable="true"]').first()
  await editArea.click()
  await page.keyboard.press('End')
  await page.keyboard.type(' revised')
  await notes.locator('.rn-editor-actions button').filter({ hasText: /Post|Submit/i }).first().click({ force: true })
  await page.waitForTimeout(4500)
  const historyText2 = ((await (page.locator('.ch-root-collapse').first().textContent())) || '').replace(/\s+/g, ' ')
  check(/Note Edited/i.test(historyText2), 'Change History shows a "Note Edited" entry')
  await page.screenshot({ path: shot('portal-record-note-edit-in-change-history'), fullPage: true })

  // 删除该备注 → Change History 出现 Note Deleted（仅在表单开启 Allow Delete 时可测）
  if (EXPECT_ALLOW_DELETE) {
    await noteItem.locator('button').filter({ hasText: /^\s*Delete\s*$/i }).first().click({ force: true })
    await page.locator('.el-message-box').waitFor({ state: 'visible', timeout: 10000 })
    await page.locator('.el-message-box button').filter({ hasText: /OK|Confirm|确定/i }).first().click({ force: true })
    await page.waitForTimeout(4500)
    const historyText3 = ((await (page.locator('.ch-root-collapse').first().textContent())) || '').replace(/\s+/g, ' ')
    check(/Note Deleted/i.test(historyText3), 'Change History shows a "Note Deleted" entry')
    await page.screenshot({ path: shot('portal-record-note-delete-in-change-history'), fullPage: true })
  }

  console.log('screenshots:', OUT_DIR)
  await browser.close()
  if (fails.length) {
    console.error(`\n${fails.length} check(s) FAILED:\n` + fails.map((f) => ' - ' + f).join('\n'))
    process.exit(2)
  }
  console.log('\nALL CHECKS PASSED')
}

main().catch((e) => { console.error('ERROR:', e.message); process.exit(1) })
