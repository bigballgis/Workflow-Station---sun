/**
 * Companion to verify-mi-assignment-design-truth.mjs, covering the three entries
 * that go through the OTHER two field extractors:
 *
 *   To Do / Completed Tasks -> useTaskDetailFieldExtraction
 *   My Requests             -> useApplicationDetailFormSchema
 *
 * FU 50005's Participants sub-form nests assignee / bu_code / role_code inside a
 * miAssignment container whose Hide toggle is ON, so every entry must show only
 * the designed fields (Name / Id / main id / test) — no Assignment Mode block and
 * no leaked Assignee row — matching DW Form Preview.
 *
 * Note the sub-table's PHYSICAL columns do include `assignee`; the grid showing it
 * is expected. What must not happen is that column surfacing as a dialog FIELD.
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaPortalPassword } from './playwright-login.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const STAMP = new Date().toISOString().slice(0, 10)
const ORIGIN = process.env.VERIFY_ORIGIN ?? 'http://localhost:3000'

// Defaults point at the Multi-Instance Subtask Demo seed; override per environment.
const TODO_TASK = process.env.VERIFY_TODO_TASK ?? '131d5538-914b-11f1-8977-2e7e5ff49f82'
const DONE_TASK = process.env.VERIFY_DONE_TASK ?? '132d81dc-914b-11f1-8977-2e7e5ff49f82'
const MY_REQUEST = process.env.VERIFY_MY_REQUEST ?? 'a11cea59-914a-11f1-8977-2e7e5ff49f82'

function shotPath(name) {
  const dir = resolve(HERE, '..', 'user-portal', 'verification-screenshots')
  mkdirSync(dir, { recursive: true })
  return resolve(dir, `${STAMP}_${name}.png`)
}

/** Read the topmost visible dialog (row dialogs stack over the page). */
async function readDialog(page) {
  return page.evaluate(() => {
    const zOf = (d) => {
      let n = d
      while (n && n !== document.body) {
        const z = Number(getComputedStyle(n).zIndex)
        if (!Number.isNaN(z) && z) return z
        n = n.parentElement
      }
      return 0
    }
    // The MI sub-task row form is NOT an .el-dialog. Two containers exist, both
    // rendering through SubTableAddDialog's field pipeline:
    //   To Do / Completed Tasks -> .sub-table-inline-form   (inline card)
    //   My Requests             -> .link-form-modal-panel   (custom modal)
    // NB: the modal is position:fixed, so offsetParent is null even when shown —
    // use layout box + computed display/visibility instead.
    const shown = (el) => {
      const s = getComputedStyle(el)
      if (s.display === 'none' || s.visibility === 'hidden') return false
      const r = el.getBoundingClientRect()
      return r.width > 0 && r.height > 0
    }
    const vis = [...document.querySelectorAll(
      '.link-form-modal-panel, .sub-table-inline-form, .el-dialog')]
      .filter(o => shown(o) && o.querySelector('.el-form-item__label'))
    if (!vis.length) return null
    const dlg = vis.slice().sort((a, b) => zOf(a) - zOf(b)).pop()
    return {
      title: (dlg.querySelector('.el-dialog__title, .el-drawer__title')
        ?? dlg.querySelector('[class*=title]'))?.textContent.trim() ?? '',
      labels: [...dlg.querySelectorAll('.el-form-item__label')]
        .map(el => el.textContent.trim().replace(/\s+/g, ' ')).filter(Boolean),
      hasAssignmentBlock: !!dlg.querySelector('.mi-assignment-block__head'),
      assignmentFieldCount: dlg.querySelectorAll('.mi-assignment-block__field').length,
    }
  })
}

/**
 * The Participants grid is the first .el-table on all three pages (Attachment is
 * second) — verified by probing each page's DOM rather than guessing a selector.
 * Editable entries open a blank row via Add; read-only ones open an existing row.
 */
async function openParticipantsDialog(page) {
  // Deliberately no page-level scroll: scrolling to the bottom re-orders which
  // grid `.el-table` first matches. scrollIntoViewIfNeeded on the target row is
  // enough and keeps the Participants grid as index 0.
  const grid = page.locator('.el-table').first()

  // Participants rows expose a "Details" link (an el-link, not a button) that opens
  // the MI sub-task row dialog. It is the one entry point present on read-only pages
  // too, so all three entries exercise the same SubTableAddDialog render path — the
  // path whose extractor changes are under test.
  const details = grid.locator('.el-table__row').first()
    .locator('a.el-link', { hasText: /details/i }).first()
  if (await details.count()) {
    await details.scrollIntoViewIfNeeded().catch(() => {})
    await details.click({ timeout: 15000 }).catch(() => {})
    await page.waitForTimeout(3500)
    const d = await readDialog(page)
    if (d) return { how: 'details-link', info: d }
  }
  return { how: null, info: null }
}

async function check(page, label, url, slug) {
  console.log(`\n=== ${label} ===`)
  await page.goto(url, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(8000)

  const { how, info } = await openParticipantsDialog(page)
  const p = shotPath(slug)
  await page.screenshot({ path: p })

  if (!info) {
    console.log('  !! no dialog opened')
    console.log(`  shot: ${p}`)
    return { label, ok: false, reason: 'no dialog' }
  }

  const leaked = info.labels.filter(l => /^(assignee|role code|business unit)$/i.test(l))
  const ok = !info.hasAssignmentBlock && leaked.length === 0
  console.log(`  opened via: ${how}`)
  console.log(`  title : ${info.title}`)
  console.log(`  fields: ${JSON.stringify(info.labels)}`)
  console.log(`  assignment block: ${info.hasAssignmentBlock} (owned: ${info.assignmentFieldCount})`)
  console.log(`  -> no leaked assignment field : ${leaked.length === 0 ? 'PASS' : `FAIL ${JSON.stringify(leaked)}`}`)
  console.log(`  -> assignment block hidden    : ${!info.hasAssignmentBlock ? 'PASS' : 'FAIL'}`)
  console.log(`  shot: ${p}`)
  return { label, ok, labels: info.labels }
}

async function run() {
  const browser = await chromium.launch()
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 } })
  const page = await ctx.newPage()
  const out = []
  try {
    await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
    out.push(await check(page, 'To Do · task detail',
      `${ORIGIN}/portal/tasks/${TODO_TASK}`, 'mi-assignment-other_todo'))
    out.push(await check(page, 'My Requests · application detail',
      `${ORIGIN}/portal/applications/${MY_REQUEST}`, 'mi-assignment-other_my-request'))
    out.push(await check(page, 'Completed Tasks · task detail',
      `${ORIGIN}/portal/tasks/${DONE_TASK}`, 'mi-assignment-other_completed'))

    console.log('\n=== SUMMARY ===')
    for (const r of out) console.log(`  ${r.ok ? 'PASS' : 'FAIL'}  ${r.label}${r.reason ? ` (${r.reason})` : ''}`)
    if (out.some(r => !r.ok)) process.exitCode = 1
  } finally {
    await browser.close()
  }
}

run().catch(err => { console.error(err); process.exit(1) })
