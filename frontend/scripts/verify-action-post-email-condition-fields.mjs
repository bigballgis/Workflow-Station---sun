/**
 * Post-email send condition: field dropdown must include MAIN table fields (not only system fields).
 *
 * Run from frontend/:  node scripts/verify-action-post-email-condition-fields.mjs
 * Env: FU_ID (optional), LOGIN_USER, LOGIN_PASS
 */
import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FU_ID = process.env.FU_ID?.trim() || process.env.HELP_GUIDE_FU_ID?.trim() || '50029'
const OUT = resolve(__dirname, '../developer-workstation/verification-screenshots')
mkdirSync(OUT, { recursive: true })
const d = new Date()
const pad = (n) => String(n).padStart(2, '0')
const DATE = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

const SYSTEM_LABELS = /^(\s*(Status|Start Time|Initiator|Current Step)\s*)$/

async function shot(page, name, opts = {}) {
  const p = join(OUT, `${DATE}_${name}.png`)
  await page.screenshot({ path: p, ...opts })
  console.log('[shot]', p)
  return p
}

const failures = []
const check = (label, ok, detail = '') => {
  console.log(`[${ok ? 'pass' : 'FAIL'}] ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) failures.push(label)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage()
let shotPaths = []

try {
  await loginViaDwPassword(page)
  console.log('[fu] initial FU_ID', FU_ID)
  let fuId = FU_ID
  const actionsRes = await page.request.get(`http://localhost:3000/api/v1/function-units/${fuId}/actions`)
  const actionsBody = actionsRes.ok() ? await actionsRes.json() : null
  console.log('[fu] actions on', fuId, ':', actionsBody?.data?.length ?? 0)
  if (!actionsBody?.data?.length) {
    const listRes = await page.request.get('http://localhost:3000/api/v1/function-units?page=0&size=30')
    const listBody = await listRes.json()
    const raw = listBody?.data
    const units = Array.isArray(raw) ? raw : raw?.content ?? raw?.records ?? []
    let best = { id: fuId, count: 0, name: '' }
    for (const u of units) {
      const ar = await page.request.get(`http://localhost:3000/api/v1/function-units/${u.id}/actions`)
      const acts = (await ar.json())?.data ?? []
      const name = u.functionUnitName || u.name || ''
      if (acts.length > best.count) {
        best = { id: String(u.id), count: acts.length, name }
      }
    }
    if (best.count > 0) {
      fuId = best.id
      console.log('[fu] using', fuId, best.name, `actions=${best.count}`)
    }
  }

  await page.goto(`http://localhost:3000/dev/function-units/${fuId}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(5000)

  await page.locator('.el-tabs__item').filter({ hasText: /Action Design|动作设计/i }).first().click()
  await page.waitForTimeout(3000)

  if (!(await page.getByTestId('action-post-email-rules').isVisible().catch(() => false))) {
    const rows = page.locator('.action-list .el-table__row')
    await rows.first().waitFor({ state: 'visible', timeout: 25000 })
    const n = await rows.count()
    let opened = false
    for (let i = 0; i < n; i++) {
      const row = rows.nth(i)
      const text = await row.innerText()
      if (!/(APPROVE|REJECT|SAVE|FORM_POPUP|Approve|Reject|Save)/i.test(text)) continue
      const editInRow = row.locator('.table-row-actions').getByRole('button', { name: /Edit|编辑/i })
      if ((await editInRow.count()) > 0) {
        await editInRow.click()
      } else {
        await row.click()
      }
      opened = true
      console.log('[nav] opened action row', i, text.split('\n')[0])
      break
    }
    if (!opened) {
      const row = rows.first()
      await row.locator('.table-row-actions').getByRole('button', { name: /Edit|编辑/i }).first().click()
    }
    await page.waitForTimeout(1500)
  }

  const rules = page.getByTestId('action-post-email-rules')
  await rules.scrollIntoViewIfNeeded()
  await rules.waitFor({ state: 'visible', timeout: 15000 })

  const editCondition = page.getByTestId('action-post-email-edit-condition').or(
    page.getByRole('button', { name: /Edit send condition|编辑发送条件/i }),
  )
  if (!(await editCondition.isVisible().catch(() => false))) {
    const addRule = page.getByTestId('add-post-email-rule').or(
      page.getByRole('button', { name: /Add rule|添加规则|新增规则/i }),
    )
    await addRule.click()
    await page.waitForTimeout(600)
  }
  await editCondition.scrollIntoViewIfNeeded()
  await editCondition.click({ timeout: 15000 })
  await page.waitForTimeout(800)

  const dialog = page.locator('.el-dialog').filter({ hasText: /Send condition|发送条件|Edit send condition/i })
  await dialog.waitFor({ state: 'visible', timeout: 10000 })
  shotPaths.push(await shot(page, 'dw-action-post-email-condition-dialog', { fullPage: false }))

  if ((await dialog.locator('.filter-condition-row').count()) === 0) {
    await dialog.locator('.filter-add-bar .el-button').first().click()
    await page.locator('.el-dropdown-menu:visible .el-dropdown-menu__item').first().click()
    await page.waitForTimeout(500)
  }

  const fieldSelect = dialog.locator('.filter-condition-row .filter-field-select').first()
  await fieldSelect.click()
  await page.waitForTimeout(800)

  shotPaths.push(await shot(page, 'dw-action-post-email-condition-field-dropdown', { fullPage: false }))

  const options = page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
  await options.first().waitFor({ state: 'visible', timeout: 8000 })
  const texts = (await options.allInnerTexts()).map(t => t.trim()).filter(Boolean)
  console.log('[field options]', texts.join(' | '))

  const nonSystem = texts.filter(t => !SYSTEM_LABELS.test(t) && t !== 'And' && t !== 'Or')
  check('At least one non-system field in send-condition dropdown', nonSystem.length > 0, `non-system: ${nonSystem.join(', ') || '(none)'}`)
  check('More than four field options total', texts.length > 4, `count=${texts.length}`)

  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)
} catch (err) {
  try {
    shotPaths.push(await shot(page, 'dw-action-post-email-condition-debug-fail', { fullPage: true }))
  } catch { /* page may be closed */ }
  throw err
} finally {
  await browser.close()
}

if (failures.length) {
  console.error('\nFAILURES:', failures.join(', '))
  if (shotPaths.length) console.error('Screenshots (for debug):', shotPaths.join('\n'))
  process.exit(1)
}
console.log('\nAll checks passed.')
console.log('Screenshots:', shotPaths.join('\n'))
