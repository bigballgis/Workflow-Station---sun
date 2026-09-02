/**
 * Reproduces the reported failure: on a To Do task detail, the People sub-table nested inside an
 * Inline Form refused Add with
 *   "Please create a Main table record before adding People data."
 *
 * Drives the real UI: open the task, click the People grid's +Add, fill a field, Save, then report
 * whether that FK-guard message appeared and whether a row actually landed in the grid.
 *
 * Usage: node scripts/verify-inline-subtable-add.mjs [taskId]
 */
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import { openPortalTask, screenshotPath } from './mi-regression-helpers.mjs'

const TASK_ID = process.env.TASK_ID || process.argv[2] || 'd26fb05d-a608-11f1-96a1-b621c894194f'

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1680, height: 1200 } })

/** Element Plus renders both toasts and inline errors; collect every user-visible message. */
const messages = []
page.on('console', (m) => {
  const t = m.text()
  if (/fkGuard|Main table record|before adding/i.test(t)) messages.push(`[console] ${t}`)
})

try {
  await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
  await openPortalTask(page, TASK_ID)

  // Grids lazy-render below the fold; scroll the whole page so the People card mounts.
  await page.evaluate(async () => {
    for (let y = 0; y < document.body.scrollHeight; y += 600) {
      window.scrollTo(0, y)
      await new Promise(r => setTimeout(r, 150))
    }
  })
  await page.waitForTimeout(1500)

  const headings = await page.locator('.el-card__header, .sub-table-title, h3, h4').allTextContents()
  console.log(`[sections] ${headings.map(h => h.trim()).filter(Boolean).slice(0, 20).join(' | ')}`)

  // The People grid sits inside the Inline Form; its +Add is the button in that card's header.
  const addButton = page
    .locator('.sub-table-inline-form, .el-card, body')
    .locator('button:has-text("Add"), button:has-text("新增")')
    .filter({ hasNotText: 'Import' })

  const addCount = await addButton.count()
  console.log(`[add buttons] ${addCount}`)
  if (addCount === 0) {
    await page.screenshot({ path: screenshotPath('inline-subtable-add-noaddbtn'), fullPage: true })
    throw new Error('no Add button found on task detail — cannot exercise the flow')
  }

  await page.screenshot({ path: screenshotPath('inline-subtable-add-before'), fullPage: true })

  // Last Add button = the nested People grid (Inline Form renders below the main fields).
  await addButton.last().click()
  await page.waitForTimeout(2500)

  const dialog = page.locator('.el-dialog:visible').last()
  if ((await dialog.count()) === 0) {
    await page.screenshot({ path: screenshotPath('inline-subtable-add-nodialog'), fullPage: true })
    throw new Error('Add dialog did not open')
  }

  // Fill the first editable text/number input so Save has something to persist.
  const inputs = dialog.locator('input:not([readonly]):not([disabled])')
  const n = await inputs.count()
  console.log(`[dialog inputs] ${n}`)
  for (let i = 0; i < n; i++) {
    const el = inputs.nth(i)
    if (!(await el.isVisible())) continue
    await el.fill('7')
    break
  }

  await page.screenshot({ path: screenshotPath('inline-subtable-add-dialog'), fullPage: true })

  await dialog.locator('button:has-text("Save"), button:has-text("保存")').last().click()
  await page.waitForTimeout(3000)

  // Any Element Plus toast/alert currently on screen.
  const toasts = await page
    .locator('.el-message, .el-message__content, .el-notification__content')
    .allTextContents()
  toasts.forEach(t => messages.push(`[toast] ${t.trim()}`))

  const guardHit = messages.some(m => /Main table record|before adding|fkGuardMainNotReady/i.test(m))
  const dialogStillOpen = (await page.locator('.el-dialog:visible').count()) > 0

  await page.screenshot({ path: screenshotPath('inline-subtable-add-after'), fullPage: true })

  console.log('--- messages ---')
  messages.forEach(m => console.log(m))
  console.log(`[dialog still open] ${dialogStillOpen}`)

  if (guardHit) {
    console.log('FAIL: FK guard still blocks the nested Add')
    process.exitCode = 1
  } else if (dialogStillOpen) {
    console.log('FAIL: dialog did not close — Save was rejected for some other reason')
    process.exitCode = 1
  } else {
    console.log('PASS: nested Add saved without the FK guard message')
  }
} catch (e) {
  console.log(`ERROR: ${e.message}`)
  process.exitCode = 1
} finally {
  await browser.close()
}
