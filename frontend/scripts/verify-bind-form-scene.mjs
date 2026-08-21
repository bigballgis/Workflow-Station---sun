/**
 * Bind Form (Process Design > User Task Config) must make the To Do / My Requests split
 * unambiguous: only To Do designs are selectable, each tagged, plus hints stating that the
 * Actions configured below dispatch against that same To Do form.
 *
 * Note on technique: this panel keeps several el-select poppers mounted at once, so any
 * "first visible dropdown" query can latch onto a stale one (Assignee's). Both the click and
 * the option read are done inside the page, anchored on the "Bind Form" form-item label.
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const SHOTS = resolve(__dirname, '../developer-workstation/verification-screenshots')
mkdirSync(SHOTS, { recursive: true })
const DATE = new Date().toISOString().slice(0, 10)
const FU_ID = process.env.FU_ID ?? '50005'

const results = []
const rec = (n, ok, d = '') => {
  results.push({ n, ok, d })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1900, height: 1100 } })).newPage()

try {
  await loginViaDwPassword(page)
  await page.goto(`http://localhost:3000/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(6000)
  await page.locator('text=Process Design').first().click()
  await page.waitForTimeout(6000)

  await page.locator('.djs-element').filter({ hasText: 'submit' }).first().click({ force: true })
  await page.waitForTimeout(2500)

  const panel = page.locator('.user-task-properties, .task-properties').first()
  rec('User Task Config panel opened', (await panel.count()) > 0)

  const panelText = await panel.innerText().catch(() => '')
  rec('To Do Form field is labelled by scene', /To Do Form/.test(panelText))
  rec('My Requests Form field is shown too', /My Requests Form/.test(panelText))
  rec(
    'To Do field hint names the scene',
    /To Do designs only/i.test(panelText),
    (panelText.match(/To Do designs only[^\n]*/i) || [''])[0].slice(0, 90),
  )
  rec(
    'My Requests field points at where it is bound',
    /My Requests tab/i.test(panelText),
    (panelText.match(/Read-only here[^\n]*/i) || [''])[0].slice(0, 110),
  )
  rec(
    'Actions hint states the To Do pairing',
    /Action buttons render on the To Do page/i.test(panelText),
    (panelText.match(/Action buttons render[^\n]*/i) || [''])[0].slice(0, 90),
  )

  await page.screenshot({ path: resolve(SHOTS, `${DATE}_bind-form-scene_panel.png`), fullPage: true })

  const clicked = await page.evaluate(() => {
    const item = Array.from(document.querySelectorAll('.el-form-item')).find(
      (it) => (it.querySelector('.el-form-item__label')?.textContent || '').trim() === 'To Do Form',
    )
    if (!item) return false
    item.scrollIntoView({ block: 'center' })
    const trigger = item.querySelector('.el-select__wrapper') || item.querySelector('.el-select')
    if (!trigger) return false
    trigger.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    return true
  })
  rec('Bind Form dropdown opened', clicked)
  await page.waitForTimeout(2500)

  // Pick the popper by CONTENT (it is the one whose options carry a scene tag), not by order.
  const options = await page.evaluate(() => {
    const read = (d) =>
      Array.from(d.querySelectorAll('.el-select-dropdown__item'))
        .map((o) => (o.textContent || '').replace(/\s+/g, ' ').trim())
        .filter(Boolean)
    const visible = Array.from(document.querySelectorAll('.el-select-dropdown')).filter(
      (d) => d.offsetParent !== null,
    )
    return visible.map(read).find((items) => items.some((i) => /To Do|My Requests/i.test(i))) ?? []
  })
  console.log('   options:', JSON.stringify(options))

  rec(
    'every option is tagged To Do',
    options.length > 0 && options.every((o) => /To Do/i.test(o)),
    options.join(' / '),
  )
  rec(
    'no My Requests form is selectable',
    options.length > 0 && !options.some((o) => /My Requests/i.test(o)),
  )

  await page.screenshot({ path: resolve(SHOTS, `${DATE}_bind-form-scene_dropdown.png`) })
  console.log(`      shots in ${SHOTS}`)
} catch (e) {
  rec('run', false, String(e).slice(0, 200))
  await page
    .screenshot({ path: resolve(SHOTS, `${DATE}_bind-form-scene_ERROR.png`), fullPage: true })
    .catch(() => {})
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length ? 1 : 0)
