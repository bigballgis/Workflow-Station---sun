#!/usr/bin/env node
/**
 * Verify the Regenerate correction box + version stamp.
 *
 *   1. Clicking Regenerate opens a text box instead of firing a blind re-roll.
 *   2. Leaving it empty keeps the original "rewrite from scratch" prompt.
 *   3. Filling it in sends a keep-what-is-correct instruction with the user's text LAST,
 *      and still carries regenerateOnly + the document's own phase (no cascade to Preview).
 *   4. The document card shows the backend version stamp (vN · time), so the user can tell
 *      whether what they are looking at has actually been regenerated.
 *
 * Every regenerate request is intercepted and aborted, so the run costs no AI gateway call
 * and writes no data. The generation draft is seeded directly, same as the applied-state script.
 *
 * Usage (from frontend/):
 *   node scripts/verify-dw-ai-regenerate-instruction.mjs [functionUnitId] [sessionId]
 *
 * Output: developer-workstation/verification-screenshots/{date}_ai-regenerate-*.png
 */
import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const DW_ROOT = resolve(__dirname, '..', 'developer-workstation')
const OUT_DIR = join(DW_ROOT, 'verification-screenshots')
const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const FU_ID = process.argv[2] ?? '50036'
const SESSION_ID = process.argv[3] ?? '3ee1225e-f48b-476c-a52f-5a9994908832'

const INSTRUCTION = 'shipment table is missing carrier and tracking_no'

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const APPLIED_DRAFT = {
  generatedData: {
    tableDefinitions: [{ tableName: 'demo_main', fields: [{ fieldName: 'title', dataType: 'STRING' }] }],
    formDefinitions: [{ formName: 'Demo Form', formType: 'PROCESS', tableBindings: [{ tableName: 'demo_main' }] }],
    actionDefinitions: [{ actionName: 'Submit', actionType: 'PROCESS_SUBMIT' }],
    decisionDefinitions: [],
    tableRelations: []
  },
  previewData: {
    tableCount: 1, totalFieldCount: 1, formCount: 1, actionCount: 1,
    actionTypes: ['PROCESS_SUBMIT'], processNodeCount: 2, processGatewayCount: 0,
    decisionCount: 0, tableRelationCount: 0
  },
  timestamp: Date.now(),
  sessionId: SESSION_ID,
  applied: true
}

async function keepLastSession(page) {
  const viewLast = page.getByRole('button', { name: /View Last Session|查看上次会话/i })
  const shown = await viewLast.waitFor({ state: 'visible', timeout: 10000 }).then(() => true, () => false)
  if (shown) {
    await viewLast.click()
    await page.waitForTimeout(500)
  }
}

async function openPanel(page) {
  await page.getByRole('button', { name: /AI Generate/i }).click()
  await keepLastSession(page)
  await page.waitForSelector('.ai-panel .chat-dialog', { timeout: 20000 })
  await page.waitForTimeout(2500)
}

/** The Design document card, whichever of collapsed/expanded it is currently in. */
function designCard(page) {
  return page.locator('.ai-panel .inline-doc-viewer').filter({ hasText: /Design|设计/ }).first()
}

/**
 * The open correction box.
 *
 * el-popover renders every instance's content into the body up front and only toggles
 * visibility, so a bare `.regenerate-box` matches all three Regenerate buttons at once —
 * filter to the visible one or the run picks a hidden popper and times out.
 */
function openBox(page) {
  return page.locator('.regenerate-box').locator('visible=true')
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const prefix = datePrefix()
  const browser = await chromium.launch()
  const page = await browser.newPage({ viewport: { width: 1700, height: 1050 } })
  const failures = []
  const expect = (ok, label) => {
    console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}`)
    if (!ok) failures.push(label)
  }

  // Capture + abort every regenerate request: no gateway call, no data written.
  let sent = null
  await page.route('**/ai-generation/chat/stream', async route => {
    try { sent = JSON.parse(route.request().postData() || '{}') } catch { sent = null }
    await route.abort()
  })

  try {
    await loginViaDwPassword(page)
    await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.bpmn-canvas', { timeout: 30000 })
    await page.evaluate(({ fuId, sessionId, draft }) => {
      localStorage.setItem(`ai_generation_draft_${fuId}_${sessionId}`, JSON.stringify(draft))
    }, { fuId: FU_ID, sessionId: SESSION_ID, draft: APPLIED_DRAFT })

    await openPanel(page)

    // ---- 4. Version stamp on the document cards ----
    const badges = page.locator('.ai-panel .inline-doc-viewer .doc-version-badge')
    const badgeCount = await badges.count()
    expect(badgeCount >= 1, `4. document cards carry a version stamp (found ${badgeCount})`)
    const badgeText = badgeCount ? (await badges.first().innerText()).trim() : ''
    expect(/^v\d+/.test(badgeText), `4. stamp reads as a version number (got "${badgeText}")`)

    await page.locator('.ai-panel .chat-dialog__messages').screenshot({
      path: join(OUT_DIR, `${prefix}_ai-regenerate-version-stamp.png`)
    })

    // ---- 1. Regenerate opens the correction box instead of firing immediately ----
    sent = null
    await designCard(page).locator('button', { hasText: /Regenerate|重新生成/i }).first().click()
    await openBox(page).waitFor({ state: 'visible', timeout: 8000 })
    await page.waitForTimeout(600)
    expect(sent === null, '1. opening the box sends nothing on its own')
    expect(await openBox(page).locator('textarea').count() > 0, '1. the box exposes a text area')
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_ai-regenerate-box-open.png`), fullPage: false })

    // ---- 2. Empty box keeps the original full-rewrite behaviour ----
    await openBox(page).locator('.regenerate-box__actions button').last().click()
    await page.waitForTimeout(2500)
    expect(typeof sent?.message === 'string' && sent.message.includes('Rewrite it from scratch'),
      '2. empty box still sends the original full-rewrite prompt')
    expect(!sent?.message?.includes('USER CORRECTION REQUEST'),
      '2. empty box adds no correction banner')

    // ---- 3. Filled-in box sends a targeted, anti-drift instruction ----
    sent = null
    await designCard(page).locator('button', { hasText: /Regenerate|重新生成/i }).first().click()
    await openBox(page).locator('textarea').waitFor({ state: 'visible', timeout: 8000 })
    await openBox(page).locator('textarea').fill(INSTRUCTION)
    await page.waitForTimeout(400)
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_ai-regenerate-box-filled.png`), fullPage: false })

    await openBox(page).locator('.regenerate-box__actions button').last().click()
    await page.waitForTimeout(2500)

    const message = sent?.message ?? ''
    expect(message.includes(INSTRUCTION), '3. the user text reaches the request')
    expect(message.includes('Keep everything in the current version that is already correct'),
      '3. the anti-drift contract replaces "rewrite from scratch"')
    expect(!message.includes('Rewrite it from scratch'),
      '3. the full-rewrite instruction is gone when a correction is given')
    expect(message.indexOf(INSTRUCTION) > message.indexOf('USER CORRECTION REQUEST'),
      '3. the user text sits at the end of the prompt, after the banner')
    expect(sent?.regenerateOnly === true,
      `3. correction keeps regenerateOnly, so it cannot cascade into Preview (got ${sent?.regenerateOnly})`)
    expect(sent?.phase === 'DESIGN',
      `3. correction targets the document's own phase (got ${sent?.phase})`)

    console.log(`\nScreenshots: ${OUT_DIR}`)
    if (failures.length) {
      console.error(`\n${failures.length} assertion(s) failed:\n - ${failures.join('\n - ')}`)
      process.exitCode = 1
    }
  } finally {
    await browser.close()
  }
}

main().catch(err => { console.error(err); process.exit(1) })
