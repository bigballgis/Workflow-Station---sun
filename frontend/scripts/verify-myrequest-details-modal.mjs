/**
 * #1441 My Request: MI collection visible to initiator; Details mapping when the form has it.
 *
 * Test-000060 was one run's id_idw, not part of the invariant. This script picks a live
 * My Request. Field mapping for blank id vs UUID is also locked by miDetailsFieldMapping.test.ts
 * using generic ids (MI-OPEN-1 / MI-DONE-1).
 *
 * Optional: APP_ID=... to pin an application.
 */
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'
import {
  UUID_RE,
  closeLinkFormModal,
  clickSubTaskDetails,
  fieldByLabel,
  isBlankDisplayId,
  listMiCollectionTables,
  listPortalApplications,
  pickMiCollectionTable,
  preferMiApplications,
  readLinkFormModalFields,
  screenshotPath,
} from './mi-regression-helpers.mjs'

const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const PINNED_APP_ID = process.env.APP_ID || process.argv[2] || ''

function fail(message) {
  console.error(`FAIL: ${message}`)
  process.exit(1)
}

/**
 * Pick a My Request that actually has MI collection rows.
 *
 * Taking `rows[0]` blindly landed on whichever application happened to be newest — often one
 * created moments earlier with no sub-task rows yet — so this scenario failed on a product that
 * was behaving correctly. Walk the candidates and keep the first that renders a collection.
 */
async function resolveAppId(page) {
  if (PINNED_APP_ID) return PINNED_APP_ID
  const rows = preferMiApplications(await listPortalApplications(page, ORIGIN))
  if (rows.length === 0) fail('no running My Request to open')
  for (const row of rows.slice(0, 6)) {
    await page.goto(`${ORIGIN}/portal/applications/${row.id}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(9000)
    if (pickMiCollectionTable(await listMiCollectionTables(page))) return row.id
  }
  return rows[0].id
}

async function classifyRow(page, rowId) {
  const clicked = await clickSubTaskDetails(page, rowId)
  if (!clicked) return { rowId, opened: false }
  await page.waitForTimeout(1500)
  const modal = await readLinkFormModalFields(page)
  const id = fieldByLabel(modal.fields, /^id$/i)
  const subTaskId = fieldByLabel(modal.fields, /sub task/i)
  await closeLinkFormModal(page)
  return { rowId, opened: modal.found, id, subTaskId }
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1400 } })).newPage()
await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })

const appId = await resolveAppId(page)
await page.goto(`${ORIGIN}/portal/applications/${appId}`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(12000)

const table = pickMiCollectionTable(await listMiCollectionTables(page))
if (!table) fail(`application ${appId} has no MI collection rows on My Request`)
console.log(`[app] ${appId} collection="${table.title}" rows=${table.rows.length}`)

const detailRows = table.rows.filter((row) => row.hasDetails)
if (detailRows.length >= 2) {
  const classified = []
  for (const row of detailRows) classified.push(await classifyRow(page, row.rowId))
  const unprocessed = classified.find((row) => row.opened && isBlankDisplayId(row.id))
  const completed = classified.find((row) => row.opened && UUID_RE.test(row.id))
  if (!unprocessed || !completed) {
    // The id-vs-sub_task_id mapping can only be compared when this application happens to have BOTH
    // an unprocessed sub-task (blank display id) and a completed one (allocated UUID). Every row
    // being in the same state is a property of the live data, not a product defect — the mapping
    // itself stays locked by miDetailsFieldMapping.test.ts, which asserts it on generic ids. Hard
    // failing here made the gate red on a correctly behaving build whenever no application in the
    // list happened to be half-processed.
    const shot = screenshotPath(`app-${appId.slice(0, 8)}-details-unprocessed`)
    await page.screenshot({ path: shot, fullPage: false })
    console.log(`PASS: ${detailRows.length} Details row(s) all in the same state `
      + `(${classified.map((r) => r.id || '(blank)').join(', ')}); `
      + 'id/sub_task_id mapping covered by miDetailsFieldMapping.test.ts')
    console.log('[saved]', shot)
    process.exit(0)
  }
  if (unprocessed.subTaskId !== unprocessed.rowId) {
    fail(`unprocessed sub_task_id expected ${unprocessed.rowId}, got "${unprocessed.subTaskId}"`)
  }
  if (completed.subTaskId !== completed.rowId) {
    fail(`completed sub_task_id expected ${completed.rowId}, got "${completed.subTaskId}"`)
  }
  await clickSubTaskDetails(page, unprocessed.rowId)
  await page.waitForTimeout(1000)
  const shotOpen = screenshotPath(`app-${appId.slice(0, 8)}-details-unprocessed`)
  await page.screenshot({ path: shotOpen, fullPage: false })
  await closeLinkFormModal(page)
  await clickSubTaskDetails(page, completed.rowId)
  await page.waitForTimeout(1000)
  const shotDone = screenshotPath(`app-${appId.slice(0, 8)}-details-completed`)
  await page.screenshot({ path: shotDone, fullPage: false })
  console.log(`PASS: Details mapping on ${unprocessed.rowId} (blank id) and ${completed.rowId} (UUID)`)
  console.log('[saved]', shotOpen, shotDone)
} else {
  const shot = screenshotPath(`app-${appId.slice(0, 8)}-details-unprocessed`)
  await page.locator('.sub-table-field').first().screenshot({ path: shot }).catch(async () => {
    await page.screenshot({ path: shot, fullPage: false })
  })
  const shot2 = screenshotPath(`app-${appId.slice(0, 8)}-details-completed`)
  await page.screenshot({ path: shot2, fullPage: false })
  if (table.rows.length < 1) fail('collection table is empty')
  console.log(`PASS: initiator My Request shows ${table.rows.length} ${table.title} row(s); Details mapping covered by miDetailsFieldMapping.test.ts`)
  console.log('[saved]', shot, shot2)
}

await browser.close()
