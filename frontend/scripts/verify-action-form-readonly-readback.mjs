/**
 * Action Form (FORM_POPUP ACTION table binding) read-only readback verification.
 *
 * DW side: form 50192 ("Sub task") has a Sub-Table widget bound to the new ACTION-scoped
 * binding (id 50635, table meeting_remark) seeded directly in dw_form_table_bindings /
 * dw_form_definitions.config_json — mirrors what dragging the Sub-Table component onto the
 * canvas and picking "Meeting Remark" from the (now ACTION-inclusive) binding selector would
 * produce. This script verifies the RESULT of that configuration: the properties panel must
 * NOT show a List View tab or allowAdd/allowEdit/allowDelete toggles for this binding.
 *
 * Portal side: Task Detail for a task whose request already has a meeting_remark row must
 * render a read-only table showing that historical row.
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const DW_SHOTS = resolve(__dirname, '../developer-workstation/verification-screenshots')
const PORTAL_SHOTS = resolve(__dirname, '../user-portal/verification-screenshots')
mkdirSync(DW_SHOTS, { recursive: true })
mkdirSync(PORTAL_SHOTS, { recursive: true })
const DATE = new Date().toISOString().slice(0, 10)

const FU_ID = process.env.FU_ID ?? '50005'
const TASK_ID = process.env.TASK_ID ?? 'adce6995-9c92-11f1-89bb-9acc5bab8bda'

const results = []
const rec = (n, ok, d = '') => {
  results.push({ n, ok, d })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`)
}

const browser = await chromium.launch({ headless: true })

// ── DW: confirm the pre-seeded ACTION binding renders with a restricted properties panel ──
{
  const page = await (await browser.newContext({ viewport: { width: 1900, height: 1200 } })).newPage()
  try {
    await loginViaDwPassword(page)
    await page.goto(`http://localhost:3000/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(4000)

    await page.locator('text=Form Design').first().click()
    await page.waitForTimeout(3000)
    await page.locator('text=Sub task').first().click()
    await page.waitForTimeout(3000)

    await page.screenshot({ path: resolve(DW_SHOTS, `${DATE}_action-form-designer-loaded.png`), fullPage: true })

    // The canvas resolves the widget's displayed title from its bound table's display name
    // once a binding is selected (matches Attachment/Participants) — so it renders as
    // "Meeting Remark", not literally "Sub-Table". Several matches exist (top nav tab, a
    // hidden dropdown-menu echo, the visible canvas field row) — only the visible one is clickable.
    const allMeetingRemarkText = page.locator('text=Meeting Remark')
    const fieldCount = await allMeetingRemarkText.count()
    rec('"Meeting Remark" appears on page (nav tab + canvas widget)', fieldCount >= 2, `found ${fieldCount} occurrence(s)`)

    const visibleMeetingRemark = allMeetingRemarkText.filter({ visible: true })
    const visibleCount = await visibleMeetingRemark.count()
    if (visibleCount > 0) {
      // fc-designer overlays a drag-mask on canvas fields; a real drag interaction would
      // pass through it, so force-click here is representative of the actual UX, not a hack
      // around a broken locator.
      await visibleMeetingRemark.last().click({ force: true })
      await page.waitForTimeout(1000)
    }

    await page.screenshot({ path: resolve(DW_SHOTS, `${DATE}_action-form-binding-selected.png`), fullPage: true })

    const bindingSelectText = await page.locator('.sub-table-binding-select').last().innerText().catch(() => '')
    rec('Selected binding shows "Meeting Remark"', /Meeting Remark/i.test(bindingSelectText), bindingSelectText.slice(0, 200))

    const propsPanelText = await page.locator('.fc-config-form, .form-create-props, .fc-designer-config').last().innerText().catch(() => '')
    rec('List View tab NOT present in properties panel for ACTION binding', !/List View/i.test(propsPanelText), propsPanelText.slice(0, 400))
    rec('allowAdd/allowEdit/allowDelete toggles NOT present for ACTION binding',
      !/Allow Add|Allow Edit|Allow Delete/i.test(propsPanelText), propsPanelText.slice(0, 400))

    await page.screenshot({ path: resolve(DW_SHOTS, `${DATE}_action-form-properties-panel.png`), fullPage: true })
  } catch (e) {
    rec('DW verification completed without throwing', false, String(e))
  } finally {
    await page.close()
  }
}

// ── Portal: confirm the read-only table now renders with historical data ──
{
  const page = await (await browser.newContext({ viewport: { width: 1900, height: 1200 } })).newPage()
  try {
    await loginViaPortalPassword(page)
    await page.goto(`http://localhost:3000/portal/tasks/${TASK_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(5000)

    // The Task Detail layout scrolls its own inner content container, not document.body —
    // fullPage screenshots only capture the initial viewport. Find the Meeting Remark heading
    // and scroll it into view for a targeted shot, plus grab the whole scrollable text.
    const bodyText = await page.locator('body').innerText().catch(() => '')
    const heading = page.locator('text=Meeting Remark').last()
    if ((await heading.count()) > 0) {
      await heading.scrollIntoViewIfNeeded().catch(() => {})
      await page.waitForTimeout(800)
    }
    await page.screenshot({ path: resolve(PORTAL_SHOTS, `${DATE}_action-form-task-detail-full.png`), fullPage: true })

    const scrolledBodyText = await page.locator('body').innerText().catch(() => '')
    const combinedText = bodyText + '\n' + scrolledBodyText
    rec('Meeting Remark section visible on Task Detail', /Meeting Remark/i.test(combinedText))
    rec('Seeded remark row (id f8b5a606... / remark_type 333) visible', /f8b5a606/.test(combinedText) && /333/.test(combinedText))
    rec('No Add/Edit/Delete controls inside the Meeting Remark table (read-only)', !/Meeting Remark[\s\S]{0,400}(Edit|Delete|\+\s*Add)/i.test(combinedText))
  } catch (e) {
    rec('Portal verification completed without throwing', false, String(e))
  } finally {
    await page.close()
  }
}

await browser.close()

const failed = results.filter(r => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
if (failed.length > 0) {
  console.log('FAILED:', failed.map(f => f.n).join('; '))
  process.exit(1)
}
