/**
 * Approver Approve Request dialog must show Member vs Leader (and the role).
 * Repro: 12345 applied HMDC Member; 123456 opens Approve and previously saw only BU + reason.
 */
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const OUT = join(dirname(fileURLToPath(import.meta.url)), '../user-portal/verification-screenshots')
const DATE = new Date().toISOString().slice(0, 10)

mkdirSync(OUT, { recursive: true })

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
let failures = 0

function check(label, ok, detail) {
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) failures++
}

try {
  await loginViaPortalPassword(page, {
    user: process.env.LOGIN_USER ?? '123456',
    pass: process.env.LOGIN_PASS ?? 'password',
    buCode: process.env.LOGIN_BU_CODE ?? 'hase-hmdc',
    roleCode: process.env.LOGIN_ROLE_CODE ?? 'HMDC_Index_Role',
  })
  await page.goto(`${ORIGIN}/portal/permissions`, { waitUntil: 'domcontentloaded' })
  await page.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 20000 }).catch(() => {})
  await page.waitForTimeout(1500)

  const workSelect = page.locator('.work-domain-select')
  if (await workSelect.count()) {
    await workSelect.click()
    const option = page.locator('.el-select-dropdown:visible').getByRole('option').filter({ hasText: /approvals|审批/i })
    if (await option.count()) {
      await option.first().click()
    }
  }
  await page.waitForTimeout(1000)

  const debugShot = join(OUT, `${DATE}_permission-approve-membership-debug.png`)
  await page.screenshot({ path: debugShot, fullPage: true })
  console.log(`[SHOT] ${debugShot}`)

  const approveBtn = page.locator('.row-actions').getByRole('button').filter({ hasText: /Approve|批准|permission\.approve/ }).first()
  if (await approveBtn.count() === 0) {
    const body = (await page.locator('.permissions-page').innerText().catch(() => page.locator('body').innerText())).slice(0, 2000)
    console.log('[DEBUG page]\n' + body)
  }
  await approveBtn.waitFor({ timeout: 15000 })
  await approveBtn.click()
  const dialog = page.locator('.el-dialog:visible')
  await dialog.waitFor({ timeout: 8000 })

  const membership = dialog.getByTestId('approval-dialog-membership-type')
  await membership.waitFor({ timeout: 8000 })
  const membershipText = (await membership.innerText()).replace(/\s+/g, ' ').trim()
  const roleText = (await dialog.getByTestId('approval-dialog-role').innerText().catch(() => '')).replace(/\s+/g, ' ').trim()

  check(
    'Approve dialog shows Member or Leader',
    /Member|成员|Leader/.test(membershipText),
    membershipText,
  )
  check(
    'Approve dialog shows requested role',
    roleText.length > 0 && !roleText.endsWith(':'),
    roleText,
  )

  const shot = join(OUT, `${DATE}_permission-approve-membership-type.png`)
  await dialog.screenshot({ path: shot })
  console.log(`[SHOT] ${shot}`)
} finally {
  await browser.close()
}

if (failures > 0) {
  process.exit(1)
}
