/**
 * Action Form read-only readback on the My Request (initiator) page — parallel to the
 * To Do side verified by verify-action-form-readonly-readback.mjs. Confirms the ACTION
 * binding seeded on form 50602 ("Sub task (My Request)") renders the same read-only table.
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const PORTAL_SHOTS = resolve(__dirname, '../user-portal/verification-screenshots')
mkdirSync(PORTAL_SHOTS, { recursive: true })
const DATE = new Date().toISOString().slice(0, 10)

const PROCESS_INSTANCE_ID = process.env.PROCESS_INSTANCE_ID ?? 'ac65b170-9c92-11f1-89bb-9acc5bab8bda'

const results = []
const rec = (n, ok, d = '') => {
  results.push({ n, ok, d })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1900, height: 1200 } })).newPage()

try {
  await loginViaPortalPassword(page)
  await page.goto(`http://localhost:3000/portal/applications/${PROCESS_INSTANCE_ID}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(6000)

  const bodyText = await page.locator('body').innerText().catch(() => '')
  console.log(`[info] "Meeting Remark" text found before scroll: ${await page.locator('text=Meeting Remark').count()} occurrence(s)`)

  const heading = page.locator('text=Meeting Remark').last()
  if ((await heading.count()) > 0) {
    await heading.scrollIntoViewIfNeeded()
    await page.waitForTimeout(1000)
  }
  await page.screenshot({ path: resolve(PORTAL_SHOTS, `${DATE}_action-form-myrequest-full.png`), fullPage: true })
  await page.screenshot({ path: resolve(PORTAL_SHOTS, `${DATE}_action-form-myrequest-viewport.png`) })

  const scrolledBodyText = await page.locator('body').innerText().catch(() => '')
  const combinedText = bodyText + '\n' + scrolledBodyText

  rec('Meeting Remark section visible on My Request page', /Meeting Remark/i.test(combinedText))
  rec('Seeded remark row (id f8b5a606... / remark_type 333) visible', /f8b5a606/.test(combinedText) && /333/.test(combinedText))
  rec('No Add/Edit/Delete controls inside the Meeting Remark table (read-only)', !/Meeting Remark[\s\S]{0,400}(Edit|Delete|\+\s*Add)/i.test(combinedText))
} catch (e) {
  rec('My Request verification completed without throwing', false, String(e))
} finally {
  await page.close()
}

await browser.close()

const failed = results.filter(r => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
if (failed.length > 0) {
  console.log('FAILED:', failed.map(f => f.n).join('; '))
  process.exit(1)
}
