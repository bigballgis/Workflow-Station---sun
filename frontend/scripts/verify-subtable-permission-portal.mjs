#!/usr/bin/env node
/**
 * User Portal — 子表逐操作权限运行时验证。
 * 打开 To Do 列表，进入含 Attachment 子表的任务，截图 Attachment 表的操作列。
 * 期望：Allow Edit / Allow Delete 关闭时，行内 Edit / Delete 按钮不出现（Add 仍在）。
 *
 * Usage: node scripts/verify-subtable-permission-portal.mjs
 */
import { mkdirSync } from 'fs'
import { join } from 'path'
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const ORIGIN = (process.env.VERIFY_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const OUT_DIR = join(process.cwd(), 'user-portal', 'verification-screenshots')

function shot(slug) {
  const date = new Date().toISOString().slice(0, 10)
  mkdirSync(OUT_DIR, { recursive: true })
  return join(OUT_DIR, `${date}_${slug}.png`)
}

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1680, height: 1400 } })).newPage()

  await loginViaUnifiedSso(page, 'portal')
  await page.goto(`${ORIGIN}/portal/tasks`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(4000)
  await page.screenshot({ path: shot('portal-todo-list'), fullPage: true })

  // 进入第一条含 "Meeting" / Multi-Instance 的任务
  const row = page.locator('tr, .el-table__row, .task-card').filter({ hasText: /Meeting|Subtask|Multi-Instance/i }).first()
  const openBtn = row.locator('text=/Process|Handle|处理|Detail|详情|Open/i').first()
  if ((await openBtn.count()) > 0) await openBtn.click({ force: true }).catch(() => {})
  else await row.click({ force: true }).catch(() => {})
  await page.waitForTimeout(5000)

  // 滚到底部让 Attachment 子表进入视口
  await page.mouse.wheel(0, 4000)
  await page.waitForTimeout(1500)
  await page.screenshot({ path: shot('portal-todo-attachment-subtable'), fullPage: true })

  // 断言：Attachment 表存在，且其操作列没有 Edit/Delete（Allow Edit/Delete = off）
  const attach = page.locator('.sub-table-field').filter({ hasText: /Attachment/i }).first()
  const report = { attachmentFound: (await attach.count()) > 0 }
  if (report.attachmentFound) {
    report.hasAdd = (await attach.locator('button', { hasText: /Add/i }).count()) > 0
    report.hasEdit = (await attach.locator('button', { hasText: /^Edit$/i }).count()) > 0
    report.hasDelete = (await attach.locator('button', { hasText: /^Delete$/i }).count()) > 0
  }
  console.log('screenshots:', OUT_DIR)
  console.log('report:', JSON.stringify(report))
  await browser.close()
}

main().catch((e) => {
  console.error('ERROR:', e.message)
  process.exit(1)
})
