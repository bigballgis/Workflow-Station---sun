/**
 * Verifies: clicking a row in a Views view that has NO detail form bound in Developer
 * Workstation now reports the missing binding instead of silently navigating to the
 * owning request page (/applications/{processInstanceId}, i.e. "My Request").
 *
 * Repro from the report: FU "Multi-Instance Subtask Demo" -> Attachment view -> click the row.
 * Before: landed on the request detail page. After: an info message, still on /portal/views.
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { loginViaPortalPassword } from './playwright-login.mjs'

const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const FU_CODE = process.env.FU_CODE ?? 'fu-20260422-23tfag'
const VIEW_LABEL = process.env.VIEW_LABEL ?? 'Attachment'
const OUT = 'user-portal/verification-screenshots'
const SHOT = process.env.SHOT ?? 'view-no-detail-form'

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1600, height: 900 } })
mkdirSync(OUT, { recursive: true })

try {
  await loginViaPortalPassword(page, {
    buCode: process.env.LOGIN_BU_CODE ?? 'hase-hmdc',
    roleCode: process.env.LOGIN_ROLE_CODE ?? 'HMDC_Index_Role',
  })

  await page.goto(`${ORIGIN}/portal/views/${FU_CODE}`, { waitUntil: 'domcontentloaded' })
  // The tables list is fetched after mount; clicking before it arrives finds "No tables available".
  await page.getByText(VIEW_LABEL, { exact: true }).first().waitFor({ timeout: 20000 })
  await page.waitForTimeout(500)

  // Select the view named in VIEW_LABEL from the left "Available tables" list.
  await page.getByText(VIEW_LABEL, { exact: true }).first().click()
  await page.waitForTimeout(2500)
  await page.screenshot({ path: `${OUT}/${SHOT}-01-list.png`, fullPage: false })

  const rows = page.locator('.el-table__body tr.el-table__row')
  const rowCount = await rows.count()
  console.log(`[verify] view="${VIEW_LABEL}" rows=${rowCount}`)
  if (!rowCount) throw new Error(`No rows in view "${VIEW_LABEL}" — cannot exercise the row click.`)

  const urlBefore = page.url()
  // Click a plain data cell, not the first cell (selection checkbox) and not a link cell.
  await rows.first().locator('td').nth(1).click()
  await page.waitForTimeout(2000)
  const urlAfter = page.url()

  const msg = (await page.locator('.el-message').first().textContent().catch(() => ''))?.trim() ?? ''
  await page.screenshot({ path: `${OUT}/${SHOT}-02-after-click.png`, fullPage: false })

  console.log(`[verify] urlBefore=${urlBefore}`)
  console.log(`[verify] urlAfter =${urlAfter}`)
  console.log(`[verify] message  ="${msg}"`)

  // EXPECT=refuse (default): the reported bug — an unbound view must report, not navigate.
  // EXPECT=detail: control case for a view that DOES have a form bound, proving the refusal
  // is scoped to the missing binding and did not break normal drill-down.
  const expect = process.env.EXPECT ?? 'refuse'

  if (/\/applications\//.test(urlAfter)) {
    throw new Error('FAIL: navigated to the request page (/applications/...) — the regression is back.')
  }
  if (expect === 'detail') {
    if (!/\/detail\?/.test(urlAfter)) {
      throw new Error(`FAIL: expected the bound detail form to open, got ${urlAfter}`)
    }
    console.log('[verify] PASS — a view with a bound detail form still opens it.')
  } else {
    if (urlAfter !== urlBefore) {
      throw new Error(`FAIL: expected to stay on the views page, navigated to ${urlAfter}`)
    }
    if (!/no detail form/i.test(msg) && !msg.includes('详情表单') && !msg.includes('詳情表單')) {
      throw new Error(`FAIL: expected a "no detail form" message, got "${msg}"`)
    }
    console.log('[verify] PASS — row click reported the missing detail form and stayed on the views page.')
  }
} finally {
  await browser.close()
}
