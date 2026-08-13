/**
 * Verify the Assignment Mode block now follows the design as the only truth.
 *
 * Two fallbacks were removed:
 *  1. undesigned physical columns were appended to sub-table dialogs
 *  2. a synthetic Assignment Mode block was placed when the design had no marker
 *
 * FU 50005's Participants sub-form places neither an `assignee` field nor the
 * Assignment Mode component, so the Add Record dialog must show ONLY the designed
 * fields (Name / Id / main id / test) — matching DW Form Preview exactly.
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaPortalPassword, loginViaDwPassword } from './playwright-login.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const STAMP = new Date().toISOString().slice(0, 10)
const ORIGIN = 'http://localhost:3000'
// Defaults target the Multi-Instance Subtask Demo seed (FU 50005), whose Participants
// sub-form hides its Assignment Mode container — override for another FU/environment.
const FU_SLUG = process.env.VERIFY_FU_SLUG ?? 'fu-20260422-23tfag'
const FU_ID = Number(process.env.VERIFY_FU_ID ?? 50005)

function shotPath(app, name) {
  const dir = resolve(HERE, '..', app, 'verification-screenshots')
  mkdirSync(dir, { recursive: true })
  return resolve(dir, `${STAMP}_${name}.png`)
}

/** Report which fields the dialog actually rendered, so parity is checked on DOM not pixels. */
async function reportDialog(page, label) {
  const info = await page.evaluate(() => {
    // The row dialog stacks on top of DW's Form Preview but is not necessarily last
    // in DOM order — pick the visually topmost by its wrapper's z-index, or we'd
    // report the main form's fields instead of the sub-table row's.
    const zOf = (d) => {
      const w = d.closest('.el-overlay') || d.parentElement
      return Number(getComputedStyle(w || d).zIndex) || 0
    }
    const visible = [...document.querySelectorAll('.el-dialog')].filter(d => d.offsetParent !== null)
    const dlg = visible.sort((a, b) => zOf(a) - zOf(b))[visible.length - 1]
    if (!dlg) return null
    const labels = [...dlg.querySelectorAll('.el-form-item__label')]
      .map(el => el.textContent.trim().replace(/\s+/g, ' '))
      .filter(Boolean)
    return {
      title: dlg.querySelector('.el-dialog__title')?.textContent.trim() ?? '',
      labels,
      hasAssignmentBlock: !!dlg.querySelector('.mi-assignment-block__head'),
      assignmentFieldCount: dlg.querySelectorAll('.mi-assignment-block__field').length,
    }
  })
  console.log(`\n=== ${label} ===`)
  if (!info) { console.log('  !! no visible dialog'); return null }
  console.log(`  title: ${info.title}`)
  console.log(`  fields: ${JSON.stringify(info.labels)}`)
  console.log(`  assignment block: ${info.hasAssignmentBlock} (owned fields: ${info.assignmentFieldCount})`)
  return info
}

async function run() {
  const browser = await chromium.launch()
  const results = {}
  try {
    // ── user-portal: New Requests → Participants → Add ────────────────────────
    {
      const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 } })
      const page = await ctx.newPage()
      await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
      await page.goto(`${ORIGIN}/portal/processes/start/${FU_SLUG}`, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(4000)

      // The Participants sub-table's own Add button (first sub-table on the form).
      const addBtn = page.locator('button:has-text("Add")').first()
      await addBtn.waitFor({ state: 'visible', timeout: 20000 })
      await addBtn.click()
      await page.waitForTimeout(2000)

      results.portal = await reportDialog(page, 'user-portal · New Requests · Add Record')
      const p = shotPath('user-portal', 'mi-assignment-design-truth_portal-new-request')
      await page.screenshot({ path: p, fullPage: false })
      console.log(`  shot: ${p}`)
      await ctx.close()
    }

    // ── developer-workstation: Form Preview → Participants → Add ──────────────
    {
      const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 } })
      const page = await ctx.newPage()
      await loginViaDwPassword(page)
      await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(5000)

      // The FU editor opens on Process Design; Form Design lists the forms, and
      // Preview only exists inside a form's editor — so open the Main form first.
      await page.locator('[role="tab"]:has-text("Form Design"), .el-tabs__item:has-text("Form Design")')
        .first().click()
      await page.waitForTimeout(3500)
      await page.locator('button:has-text("Edit")').filter({ visible: true })
        .first().click({ timeout: 20000 })
      await page.waitForTimeout(6000)

      const previewBtn = page.locator('button:has-text("Preview")').first()
      await previewBtn.waitFor({ state: 'visible', timeout: 25000 })
      await previewBtn.click()
      await page.waitForTimeout(5000)

      // Preview renders the main form; the Participants sub-table sits inside it and
      // has its own Add. Scope to that sub-table so we compare the ROW dialog with
      // the portal's, not the main form.
      const subTable = page.locator('.el-dialog').filter({ visible: true })
        .locator('div:has(> .sub-table-header:has-text("Participants")), .sub-table-wrapper:has-text("Participants")')
        .first()
      const addBtn = (await subTable.count())
        ? subTable.locator('button:has-text("Add")').first()
        : page.locator('.el-dialog button:has-text("Add")').filter({ visible: true }).first()
      await addBtn.waitFor({ state: 'visible', timeout: 20000 })
      await addBtn.click()
      await page.waitForTimeout(3000)

      results.dw = await reportDialog(page, 'developer-workstation · Form Preview · Participants')
      const p = shotPath('developer-workstation', 'mi-assignment-design-truth_dw-form-preview')
      await page.screenshot({ path: p, fullPage: false })
      console.log(`  shot: ${p}`)
      await ctx.close()
    }

    // ── Parity verdict ────────────────────────────────────────────────────────
    console.log('\n=== PARITY ===')
    if (results.portal && results.dw) {
      const same = JSON.stringify(results.portal.labels) === JSON.stringify(results.dw.labels)
      console.log(`  portal fields === dw fields : ${same ? 'MATCH' : 'DIFFER'}`)
      if (!same) {
        console.log(`    portal: ${JSON.stringify(results.portal.labels)}`)
        console.log(`    dw    : ${JSON.stringify(results.dw.labels)}`)
      }
      const noAssignee = !results.portal.labels.some(l => /assignee/i.test(l))
      console.log(`  portal has no undesigned Assignee : ${noAssignee ? 'PASS' : 'FAIL'}`)
      console.log(`  portal assignment block hidden    : ${!results.portal.hasAssignmentBlock ? 'PASS' : 'FAIL'}`)
    } else {
      console.log('  incomplete — one side produced no dialog')
    }
  } finally {
    await browser.close()
  }
}

run().catch(err => { console.error(err); process.exit(1) })
