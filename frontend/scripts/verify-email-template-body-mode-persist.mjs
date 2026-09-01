/**
 * Email Template Visual/HTML tab is restored only after Save.
 * Env: FU_ID (default 50007).
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const DW_SHOTS = resolve(__dirname, '../developer-workstation/verification-screenshots')
mkdirSync(DW_SHOTS, { recursive: true })
const DATE = new Date().toISOString().slice(0, 10)
const FU_ID = process.env.FU_ID ?? '50007'
const NAME = `mode-persist-${Date.now()}`

const results = []
const rec = (n, ok, d = '') => {
  results.push({ n, ok, d })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`)
}

async function isModeActive(page, mode) {
  const btn = page.getByTestId(`email-body-mode-${mode}`)
  await btn.waitFor({ state: 'visible', timeout: 10000 })
  return btn.evaluate((el) => {
    const root = el.closest('.el-radio-button')
    return root?.classList.contains('is-active') === true
  })
}

async function openNewTemplate(page) {
  await page.getByRole('button', { name: 'New Template' }).click()
  await page.getByTestId('email-body-split').waitFor({ state: 'visible', timeout: 20000 })
}

async function closeDialog(page) {
  const dialog = page.locator('.email-template-form-dialog')
  await dialog.getByRole('button', { name: 'Cancel' }).click()
  await dialog.waitFor({ state: 'hidden', timeout: 10000 })
}

async function saveTemplate(page) {
  const dialog = page.locator('.email-template-form-dialog')
  await dialog.getByRole('button', { name: 'Save' }).click()
  await dialog.waitFor({ state: 'hidden', timeout: 20000 })
}

async function openEditByName(page, name) {
  const row = page.locator('.email-template-designer .el-table__row', { hasText: name })
  await row.waitFor({ state: 'visible', timeout: 15000 })
  await row.getByRole('button', { name: 'Edit' }).click()
  await page.getByTestId('email-body-split').waitFor({ state: 'visible', timeout: 20000 })
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1100 } })).newPage()

try {
  await loginViaDwPassword(page)
  await page.goto(`http://localhost:3000/dev/function-units/${FU_ID}`, {
    waitUntil: 'domcontentloaded',
  })
  await page.getByRole('tab', { name: 'Email Templates' }).click()
  await page.getByRole('button', { name: 'New Template' }).waitFor({ timeout: 20000 })

  await openNewTemplate(page)
  rec('New template defaults to Visual', await isModeActive(page, 'visual'))
  await page.getByTestId('email-body-mode-html').click()
  rec('Unsaved switch shows HTML', await isModeActive(page, 'html'))
  await closeDialog(page)

  await openNewTemplate(page)
  rec(
    'Cancel without Save reopens Visual',
    await isModeActive(page, 'visual'),
  )
  await page.locator('.email-template-form-dialog .el-form-item').first().locator('input').fill(NAME)
  await page.getByTestId('email-body-mode-html').click()
  rec('HTML selected before Save', await isModeActive(page, 'html'))
  await saveTemplate(page)

  await openEditByName(page, NAME)
  rec('Reopen after HTML Save stays HTML', await isModeActive(page, 'html'))
  const htmlShot = resolve(DW_SHOTS, `${DATE}_email-template-body-mode-html-after-save.png`)
  await page.locator('.email-template-form-dialog').screenshot({ path: htmlShot })
  console.log(`screenshot ${htmlShot}`)

  await page.getByTestId('email-body-mode-visual').click()
  const confirmBox = page.locator('.el-message-box')
  await confirmBox.waitFor({ state: 'visible', timeout: 10000 })
  await confirmBox.locator('.el-button--primary').click()
  await confirmBox.waitFor({ state: 'hidden', timeout: 10000 })
  rec('Switched to Visual in session', await isModeActive(page, 'visual'))
  await closeDialog(page)

  await openEditByName(page, NAME)
  rec(
    'Cancel without Save keeps last saved HTML',
    await isModeActive(page, 'html'),
  )
  await closeDialog(page)
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} passed`)
if (failed.length) process.exit(1)
