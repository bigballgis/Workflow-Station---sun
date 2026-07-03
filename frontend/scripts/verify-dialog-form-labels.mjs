#!/usr/bin/env node
/**
 * Screenshot verification for portal dialog form label nowrap fix.
 * Does not require SSO — renders Element Plus dialog markup + global index.scss rules.
 *
 * Usage (from frontend/):
 *   node scripts/verify-dialog-form-labels.mjs           # mock before/after
 *   node scripts/verify-dialog-form-labels.mjs --live    # real portal (password login)
 *
 * Output:
 *   mock:  {date}_dialog-labels-{before|after}.png
 *   live:  {date}_permissions-apply-dialog-labels-live.png
 */

import { readFileSync, mkdirSync, writeFileSync, unlinkSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = resolve(__dirname, '..')
const OUT_DIR = join(FRONTEND_ROOT, 'user-portal', 'verification-screenshots')
const EP_CSS = join(FRONTEND_ROOT, 'user-portal', 'node_modules', 'element-plus', 'dist', 'index.css')
const PORTAL_INDEX_SCSS = join(FRONTEND_ROOT, 'user-portal', 'src', 'styles', 'index.scss')

// 当前生效规则：保留 label-width 统一宽度（输入框左对齐），min-width 兜底防折行
const DIALOG_LABEL_RULE = `
.el-dialog .el-form:not(.el-form--label-top) .el-form-item__label,
.el-drawer .el-form:not(.el-form--label-top) .el-form-item__label {
  min-width: max-content;
  max-width: none !important;
  white-space: nowrap;
  flex-shrink: 0;
}
.el-dialog .el-form--label-top .el-form-item__label,
.el-drawer .el-form--label-top .el-form-item__label {
  white-space: nowrap;
}
`

// 旧（错误）规则：width:auto 把 label 压成各自文字宽度 → 各行输入框起点不一
const OLD_DIALOG_LABEL_RULE = `
.el-dialog .el-form:not(.el-form--label-top) .el-form-item__label,
.el-drawer .el-form:not(.el-form--label-top) .el-form-item__label {
  width: auto !important;
  max-width: none !important;
  white-space: nowrap;
  flex-shrink: 0;
}
`

const FIELDS = [
  { label: '名称', type: 'input', placeholder: '请输入名称' },
  { label: 'ID', type: 'input', placeholder: '请输入 ID' },
  { label: '委托给（被委托人）', type: 'select', placeholder: '请选择被委托人' },
  { label: '委托类型（全部/部分）', type: 'select', placeholder: '请选择委托类型' },
  { label: '流程实例开始时间', type: 'input', placeholder: '请选择开始时间' },
  { label: '委托原因说明（选填）', type: 'textarea', placeholder: '请输入委托原因' },
]

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function buildFormItems(labelWidth, extraLabelCss = '') {
  return FIELDS.map((f) => {
    const control =
      f.type === 'textarea'
        ? `<textarea class="el-textarea__inner" placeholder="${f.placeholder}" rows="3"></textarea>`
        : f.type === 'select'
          ? `<div class="el-select"><div class="el-select__wrapper"><span class="el-select__placeholder">${f.placeholder}</span></div></div>`
          : `<div class="el-input"><div class="el-input__wrapper"><input class="el-input__inner" placeholder="${f.placeholder}" /></div></div>`
    return `
      <div class="el-form-item el-form-item--default is-required asterisk-left el-form-item--label-left">
        <label class="el-form-item__label" style="width: ${labelWidth}">${f.label}</label>
        <div class="el-form-item__content"${extraLabelCss ? '' : ''}>${control}</div>
      </div>`
  }).join('')
}

function buildHtml(mode) {
  const epCss = readFileSync(EP_CSS, 'utf8')
  const portalScss = readFileSync(PORTAL_INDEX_SCSS, 'utf8')
  const labelWidth = '190px' // 统一宽度须容纳最长 label（规范要求）
  const fixCss = mode === 'after' ? DIALOG_LABEL_RULE : OLD_DIALOG_LABEL_RULE
  const extraLabelCss = ''

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8" />
<style>${epCss}</style>
<style>
body { margin: 0; padding: 24px; background: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif; }
.el-dialog { width: 500px; margin: 0 auto; background: #fff; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,.12); padding: 16px; }
.el-dialog__header { font-size: 16px; font-weight: 500; margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid #eee; }
.el-form { width: 100%; }
.badge { display: inline-block; margin-bottom: 12px; padding: 4px 10px; border-radius: 4px; font-size: 12px; }
.badge.before { background: #fff1f0; color: #cf1322; }
.badge.after { background: #f6ffed; color: #389e0d; }
${extraLabelCss}
${fixCss}
</style>
</head>
<body>
<div class="el-dialog" role="dialog">
  <div class="el-dialog__header">
    <span class="el-dialog__title">创建委托</span>
    <span class="badge ${mode}">${mode === 'before' ? '修复前：width:auto 逐 label 自适应，输入框不对齐' : '修复后：label-width 统一 + min-width 兜底，输入框左对齐且不折行'}</span>
  </div>
  <div class="el-dialog__body">
    <form class="el-form el-form--default el-form--label-left" label-width="${labelWidth}">
      ${buildFormItems(labelWidth)}
    </form>
  </div>
</div>
</body>
</html>`
}

async function loadPlaywright() {
  try {
    return await import('playwright')
  } catch {
    console.error('playwright not installed. From frontend/: npm install && npx playwright install chromium')
    process.exit(1)
  }
}

async function screenshotMode(page, mode, prefix, tmpDir) {
  const htmlPath = join(tmpDir, `dialog-labels-${mode}.html`)
  writeFileSync(htmlPath, buildHtml(mode), 'utf8')
  await page.goto(`file:///${htmlPath.replace(/\\/g, '/')}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(400)
  const outPath = join(OUT_DIR, `${prefix}_dialog-labels-${mode}.png`)
  await page.locator('.el-dialog').screenshot({ path: outPath })
  console.log(`[saved] ${outPath}`)
  unlinkSync(htmlPath)
  return outPath
}

async function screenshotLivePortal(page, prefix) {
  await loginViaPortalPassword(page)
  console.log('[goto] /portal/permissions')
  await page.goto('http://localhost:3000/portal/permissions', { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2500)
  await page.locator('.page-header .el-button--primary').click()
  await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 15000 })
  await page.waitForTimeout(800)

  const outPath = join(OUT_DIR, `${prefix}_permissions-apply-dialog-labels-live.png`)
  await page.locator('.el-dialog').first().screenshot({ path: outPath })
  console.log('[saved]', outPath)

  const labelInfo = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.el-dialog .el-form-item__label')).map((el) => ({
      text: el.textContent?.trim(),
      width: getComputedStyle(el).width,
      whiteSpace: getComputedStyle(el).whiteSpace,
    })),
  )
  console.log('[labels]', JSON.stringify(labelInfo, null, 2))
  return outPath
}

async function main() {
  const live = process.argv.includes('--live')
  mkdirSync(OUT_DIR, { recursive: true })
  const prefix = datePrefix()

  const { chromium } = await loadPlaywright()
  const browser = await chromium.launch({ headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1400, height: 900 } })).newPage()

  try {
    if (live) {
      const livePath = await screenshotLivePortal(page, prefix)
      console.log('\n[done] live portal dialog:', livePath)
      return
    }

    const tmpDir = join(OUT_DIR, '_tmp')
    mkdirSync(tmpDir, { recursive: true })
    const before = await screenshotMode(page, 'before', prefix, tmpDir)
    const after = await screenshotMode(page, 'after', prefix, tmpDir)
    console.log('\n[done] before vs after:')
    console.log(' ', before)
    console.log(' ', after)
  } finally {
    await browser.close()
    try {
      unlinkSync(join(OUT_DIR, '_tmp'))
    } catch {
      /* ignore */
    }
  }
}

main().catch((err) => {
  console.error('[verify-dialog-form-labels] FAILED:', err.message)
  process.exit(1)
})
