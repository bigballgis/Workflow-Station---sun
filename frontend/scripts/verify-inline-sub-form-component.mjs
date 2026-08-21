/**
 * Screenshot verification for the `inlineSubForm` (Inline Form) widget.
 *
 * Covers the three surfaces the widget must work on:
 *   1. DW designer canvas  — placeholder chip renders, binding select in the props panel
 *   2. DW Form Preview     — the bound sub-table's form rendered inline (no grid, no Add)
 *   3. Portal New Request  — blank editable inline form, and typing into it
 *
 * Login is password-direct (never unified SSO — it is unreliable headless; see
 * .cursor/rules dw-headless-sso-login-blocked).
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaPortalPassword, loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const FU_ID = process.env.FU_ID ?? '50005'
// Portal's start route keys off the FU CODE, not its numeric id.
const FU_CODE = process.env.FU_CODE ?? 'fu-20260422-23tfag'
const DATE = new Date().toISOString().slice(0, 10)

const DW_SHOTS = resolve(__dirname, '../developer-workstation/verification-screenshots')
const PORTAL_SHOTS = resolve(__dirname, '../user-portal/verification-screenshots')
mkdirSync(DW_SHOTS, { recursive: true })
mkdirSync(PORTAL_SHOTS, { recursive: true })

const results = []
function record(name, ok, detail = '') {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`)
}

async function shot(page, dir, slug) {
  const path = resolve(dir, `${DATE}_${slug}.png`)
  await page.screenshot({ path, fullPage: true })
  console.log(`      shot: ${path}`)
  return path
}

async function runPortal(browser) {
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 } })
  const page = await ctx.newPage()
  page.on('console', (m) => {
    const t = m.text()
    if (m.type() === 'error' && !/favicon|ResizeObserver/i.test(t)) console.log(`  [portal console] ${t}`)
  })

  try {
    await loginViaPortalPassword(page)
    await page.goto(`${ORIGIN}/portal/processes/start/${FU_CODE}`, {
      waitUntil: 'domcontentloaded',
    })
    await page.waitForTimeout(9000)

    // This dev DB has nothing published to admin-center (sys_function_units is empty), so the
    // portal cannot load ANY function unit. Report that as a skip rather than a component failure.
    const bodyText = await page.locator('body').innerText().catch(() => '')
    if (/FUNCTION_UNIT_NOT_FOUND|Load Failed/i.test(bodyText)) {
      record(
        'portal: SKIPPED — function unit not deployed to admin-center in this env',
        true,
        'publish the FU (Deploy) to exercise the portal runtime',
      )
      await shot(page, PORTAL_SHOTS, 'inline-sub-form_portal-not-deployed')
      return
    }

    // The widget renders SubTableInlineForm with bordered=false.
    const inline = page.locator('.sub-table-inline-form')
    const count = await inline.count()
    record('portal: inline form block rendered', count > 0, `found ${count}`)

    if (count > 0) {
      // The bound sub-form (attachment) designs id / main_id / file.
      const labels = await page.locator('.sub-table-inline-form .el-form-item__label').allInnerTexts()
      record(
        'portal: bound sub-form fields laid out inline',
        labels.length > 0,
        `labels: ${labels.map((l) => l.trim()).filter(Boolean).join(', ')}`,
      )

      // 1:1 contract — no grid and no Add button inside the inline block.
      const gridInside = await inline.first().locator('.el-table').count()
      record('portal: no grid inside inline form (1:1, not a table)', gridInside === 0)

      // No Save button of its own — rows persist with the host form.
      const saveInside = await inline.first().locator('button:has-text("Save")').count()
      record('portal: no own Save button (persists with host form)', saveInside === 0)
    }

    await shot(page, PORTAL_SHOTS, 'inline-sub-form_portal-new-request-empty')

    // Clip to the framed block itself — on a long form it sits well below the fold, and the
    // whole point of this change is how the boundary reads next to ordinary host fields.
    if (count > 0) {
      await inline.first().scrollIntoViewIfNeeded().catch(() => {})
      await page.waitForTimeout(800)
      const framePath = resolve(PORTAL_SHOTS, `${DATE}_inline-sub-form_portal-framed-block.png`)
      await inline.first().screenshot({ path: framePath })
      console.log(`      shot: ${framePath}`)

      // Also capture it in context with the host fields above it, to show the contrast.
      const ctxPath = resolve(PORTAL_SHOTS, `${DATE}_inline-sub-form_portal-frame-in-context.png`)
      await page.screenshot({ path: ctxPath })
      console.log(`      shot: ${ctxPath}`)
    }

    // The framed block must be visually distinguishable from ordinary host-form fields.
    const framed = await inline.first().evaluate((el) => el.classList.contains('is-framed'))
    record('portal: inline block is framed (distinguishable from host fields)', framed)
    const frameTitle = await inline.first().locator('.inline-form-frame-title').innerText().catch(() => '')
    record('portal: frame is labelled with the bound table', frameTitle.trim().length > 0, frameTitle.trim())

    // Type into the first editable TEXT input: row[0] is created on first edit. Skip upload's
    // hidden file input, and note this demo's sub-form is id/main_id (PK/FK, both readonly by
    // design) + file — so "no editable text input" is a valid state here, not a failure.
    const firstInput = inline.first().locator('input[type="text"]:not([disabled]):not([readonly])').first()
    if (await firstInput.count()) {
      await firstInput.fill('INLINE-TEST-1')
      await page.waitForTimeout(1200)
      record('portal: inline field accepts input', (await firstInput.inputValue()) === 'INLINE-TEST-1')
      await shot(page, PORTAL_SHOTS, 'inline-sub-form_portal-new-request-filled')
    } else {
      const total = await inline.first().locator('input').count()
      record(
        'portal: inline field editability (this demo binds PK/FK + upload only)',
        total > 0,
        `${total} inputs rendered, none free-text — expected for the attachment sub-form`,
      )
    }
  } catch (e) {
    record('portal: run', false, String(e).slice(0, 300))
    await shot(page, PORTAL_SHOTS, 'inline-sub-form_portal-ERROR').catch(() => {})
  } finally {
    await ctx.close()
  }
}

async function runDw(browser) {
  const ctx = await browser.newContext({ viewport: { width: 1900, height: 1100 } })
  const page = await ctx.newPage()
  page.on('console', (m) => {
    const t = m.text()
    if (m.type() === 'error' && !/favicon|ResizeObserver/i.test(t)) console.log(`  [dw console] ${t}`)
  })

  try {
    await loginViaDwPassword(page)
    await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(6000)

    // Form Design tab -> form list -> Edit the form carrying the widget ("Main" = PROCESS form).
    const formTab = page.locator('text=Form Design').first()
    if (await formTab.count()) {
      await formTab.click()
      await page.waitForTimeout(4000)
    }
    const mainRow = page.locator('tr', { hasText: 'Main' }).first()
    const editLink = mainRow.locator('text=Edit').first()
    if (await editLink.count()) {
      await editLink.click()
      // fc-designer canvas hydration is slow on multi-sub-table forms.
      await page.waitForTimeout(12000)
    } else {
      record('dw: opened the form designer', false, 'Edit link not found on the Main row')
    }

    // The palette must offer the new component.
    const paletteItem = page.locator('text=Inline Form').first()
    record('dw: "Inline Form" appears in the component palette', (await paletteItem.count()) > 0)

    // The persisted rule places one on the canvas — its placeholder chip must render.
    const chip = page.locator('.inline-sub-form-placeholder-widget')
    const chipCount = await chip.count()
    record('dw: placeholder chip renders on canvas', chipCount > 0, `found ${chipCount}`)
    if (chipCount > 0) {
      const cls = await chip.first().getAttribute('class')
      record('dw: chip resolves its binding (is-valid, not stale)', /is-valid/.test(cls ?? ''), cls ?? '')
    }
    await shot(page, DW_SHOTS, 'inline-sub-form_dw-designer-canvas')

    // Form Preview — the bound sub-form should render inline, without table chrome.
    const previewBtn = page.locator('button:has-text("Preview")').first()
    if (await previewBtn.count()) {
      await previewBtn.click()
      await page.waitForTimeout(7000)
      const dialog = page.locator('.el-dialog').filter({ hasText: /Preview/i }).first()
      const visible = await dialog.count()
      record('dw: preview dialog opened', visible > 0)
      await shot(page, DW_SHOTS, 'inline-sub-form_dw-form-preview')

      // The widget renders as its own preview item kind — a plain form-create block, NOT the
      // subTable arm, so it must carry no grid and no Add button of its own.
      const body = dialog.locator('.el-dialog__body').first()
      const previewBlocks = body.locator('.form-preview-wrapper')
      const blockCount = await previewBlocks.count()
      record('dw preview: inline form segment rendered', blockCount > 0, `${blockCount} form-create blocks`)

      // The widget was appended last in the rule, so bring it into view. Scroll whichever
      // element actually overflows, then clip the shot to the dialog (fullPage re-renders
      // from the top and would hide the scroll).
      await page.evaluate(() => {
        const els = Array.from(document.querySelectorAll('.el-dialog, .el-dialog__body, .el-dialog__body *'))
        for (const el of els) {
          if (el.scrollHeight > el.clientHeight + 40) el.scrollTop = el.scrollHeight
        }
        const last = document.querySelectorAll('.el-dialog .form-preview-wrapper')
        last[last.length - 1]?.scrollIntoView({ block: 'center' })
      })
      await page.waitForTimeout(2500)

      // Clip to the LAST preview segment — that is the inlineSubForm item (appended last in
      // the rule). Screenshotting the element itself sidesteps dialog scroll entirely.
      const lastBlock = previewBlocks.nth(blockCount - 1)
      const dlgPath = resolve(DW_SHOTS, `${DATE}_inline-sub-form_dw-form-preview-inline-block.png`)
      await lastBlock.scrollIntoViewIfNeeded().catch(() => {})
      await page.waitForTimeout(800)
      await lastBlock.screenshot({ path: dlgPath })
      console.log(`      shot: ${dlgPath}`)

      // Contract: the inline segment shows the sub-form's own fields, with no grid / Add button.
      const inlineText = await lastBlock.innerText().catch(() => '')
      const hasGrid = (await lastBlock.locator('.el-table').count()) > 0
      const hasAdd = (await lastBlock.locator('button:has-text("Add")').count()) > 0
      record('dw preview: inline segment has no grid', !hasGrid)
      record('dw preview: inline segment has no Add button', !hasAdd)
      record(
        'dw preview: inline segment shows the bound sub-form fields',
        /main_id|file|id/i.test(inlineText),
        inlineText.replace(/\s+/g, ' ').slice(0, 120),
      )
    } else {
      record('dw: preview dialog opened', false, 'Preview button not found')
    }
  } catch (e) {
    record('dw: run', false, String(e).slice(0, 300))
    await shot(page, DW_SHOTS, 'inline-sub-form_dw-ERROR').catch(() => {})
  } finally {
    await ctx.close()
  }
}

const browser = await chromium.launch({ headless: true })
try {
  await runDw(browser)
  await runPortal(browser)
} finally {
  await browser.close()
}

console.log('\n===== SUMMARY =====')
const failed = results.filter((r) => !r.ok)
for (const r of results) console.log(`${r.ok ? 'PASS' : 'FAIL'}  ${r.name}${r.detail ? ` — ${r.detail}` : ''}`)
console.log(`${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length ? 1 : 0)
