#!/usr/bin/env node
/**
 * Screenshot the local P0 dual-binding Function Unit in Developer Workstation.
 *
 * Proves Form Design has two subTable widgets on the same physical table
 * (Case files / Party files) without opening BINDING_EXISTS in the designer.
 *
 * Usage (from frontend/):
 *   node scripts/verify-p0-dual-binding-fu.mjs
 */
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const FU_ID = process.env.FU_ID ?? '50008'
const FU_CODE = process.env.FU_CODE ?? 'p0-dual-binding-test'
const DATE = new Date().toISOString().slice(0, 10)
const OUT_DIR = join(__dirname, '..', 'developer-workstation', 'verification-screenshots')
mkdirSync(OUT_DIR, { recursive: true })

function fail(msg, path) {
  console.error(`FAIL  ${msg}${path ? ` — ${path}` : ''}`)
  process.exit(1)
}

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' })
  const page = await browser.newPage({ viewport: { width: 1600, height: 1200 } })
  try {
    await loginViaDwPassword(page, { loginOrigin: ORIGIN })

    const detailRes = await page.request.get(`${ORIGIN}/api/v1/function-units/${FU_ID}`)
    const detailBody = await detailRes.json().catch(() => ({}))
    const fu = detailBody.data ?? detailBody
    if (!fu?.id) {
      fail(`Function unit ${FU_CODE} id=${FU_ID} not readable via DW API (HTTP ${detailRes.status()})`)
    }
    if (fu.code && fu.code !== FU_CODE) {
      fail(`Expected code ${FU_CODE} at id=${FU_ID}, got ${fu.code}`)
    }

    const formsRes = await page.request.get(`${ORIGIN}/api/v1/function-units/${fu.id}/forms`)
    const formsBody = await formsRes.json().catch(() => ({}))
    const forms = formsBody.data ?? formsBody
    const form = (Array.isArray(forms) ? forms : []).find((f) => f.formName === 'P0 Dual Case Form') ?? (Array.isArray(forms) ? forms[0] : null)
    if (!form?.id) {
      fail('P0 Dual Case Form not returned by DW API')
    }
    const bindRes = await page.request.get(`${ORIGIN}/api/v1/function-units/${fu.id}/forms/${form.id}/bindings`)
    const bindBody = await bindRes.json().catch(() => ({}))
    const bindings = bindBody.data ?? bindBody
    const fileBinds = (Array.isArray(bindings) ? bindings : []).filter(
      (b) => b.bindingType === 'SUB' && (b.tableName === 'p0_dual_file' || b.table?.tableName === 'p0_dual_file'),
    )
    if (fileBinds.length !== 2) {
      fail(`Expected 2 SUB bindings on p0_dual_file, got ${fileBinds.length}`)
    }
    console.log(`API  two p0_dual_file SUB bindings: ${fileBinds.map((b) => b.id).join(', ')}`)

    await page.goto(`${ORIGIN}/dev/function-units/${fu.id}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(2500)

    const formsTab = page.locator('.el-tabs__item').filter({ hasText: /Form Design|表单设计|表單設計/ })
    await formsTab.first().click({ timeout: 15000 })
    await page.waitForTimeout(2000)

    const editForm = page.locator('.el-table__body tr:visible').filter({ hasText: /P0 Dual Case Form/ }).locator('button').filter({
      hasText: /Edit|编辑|編輯/,
    }).first()
    await editForm.click({ timeout: 15000 })
    await page.waitForTimeout(3500)

    const bodyText = await page.locator('body').innerText()
    const fileWidgetCount = (bodyText.match(/P0 Dual File/g) || []).length
    const canvasPath = join(OUT_DIR, `${DATE}_p0-dual-binding-form-design.png`)
    await page.screenshot({ path: canvasPath, fullPage: true })
    console.log(`shot: ${canvasPath}`)

    if (fileWidgetCount < 2) {
      fail(`Form Design expected two P0 Dual File widgets, found ${fileWidgetCount}`, canvasPath)
    }

    const bindingsBtn = page.locator('button').filter({ hasText: /Manage Table Bindings|管理表绑定|管理表綁定/ }).first()
    if ((await bindingsBtn.count()) > 0) {
      await bindingsBtn.click()
      await page.waitForTimeout(1500)
      const bindPath = join(OUT_DIR, `${DATE}_p0-dual-binding-table-bindings.png`)
      await page.screenshot({ path: bindPath, fullPage: true })
      console.log(`shot: ${bindPath}`)
      await page.keyboard.press('Escape')
      await page.waitForTimeout(400)
    }

    const previewBtn = page.locator('button').filter({ hasText: /^(Preview|预览|預覽)$/ }).first()
    if ((await previewBtn.count()) > 0) {
      await previewBtn.click()
      await page.waitForTimeout(2500)
      const previewPath = join(OUT_DIR, `${DATE}_p0-dual-binding-form-preview.png`)
      await page.screenshot({ path: previewPath, fullPage: true })
      console.log(`shot: ${previewPath}`)
    }

    console.log(`OK  ${FU_CODE} id=${fu.id} canvas has ${fileWidgetCount} P0 Dual File widgets`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
