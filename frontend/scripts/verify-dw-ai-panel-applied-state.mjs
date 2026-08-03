#!/usr/bin/env node
/**
 * Verify the four AI Generate panel fixes:
 *
 *   1. GENERATION ("Preview & Confirm") shows a check mark once the result is applied —
 *      it is the last phase, so the "everything before currentPhase is done" rule could
 *      never light it and the rail stayed on "03" forever.
 *   2. The Requirements / Design document cards in the chat carry a Regenerate button.
 *   3. (code-level, not covered here) Apply refreshes the designer behind the panel.
 *   4. The Generation Preview card survives closing and reopening the panel — the applied
 *      result is kept in the localStorage generation draft instead of being deleted.
 *
 * The generation draft is seeded directly so the run needs no live AI gateway call; that
 * is exactly the payload useAiChat writes during a real generation, plus applied: true.
 *
 * Usage (from frontend/):
 *   node scripts/verify-dw-ai-panel-applied-state.mjs [functionUnitId] [sessionId]
 *
 * Output: developer-workstation/verification-screenshots/{date}_ai-panel-applied-*.png
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

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Shape produced by useAiChat's generated_data handler, flagged as already written. */
const APPLIED_DRAFT = {
  generatedData: {
    tableDefinitions: [{ tableName: 'demo_main', fields: [{ fieldName: 'title', dataType: 'STRING' }] }],
    formDefinitions: [{ formName: 'Demo Form', formType: 'PROCESS', tableBindings: [{ tableName: 'demo_main' }] }],
    actionDefinitions: [{ actionName: 'Submit', actionType: 'PROCESS_SUBMIT' }],
    decisionDefinitions: [],
    tableRelations: []
  },
  previewData: {
    tableCount: 1,
    totalFieldCount: 1,
    formCount: 1,
    actionCount: 1,
    actionTypes: ['PROCESS_SUBMIT'],
    processNodeCount: 2,
    processGatewayCount: 0,
    decisionCount: 0,
    tableRelationCount: 0
  },
  timestamp: Date.now(),
  sessionId: SESSION_ID,
  applied: true
}

/** The panel offers "new session" vs "view last session" when the latest one is COMPLETED. */
async function keepLastSession(page) {
  // isVisible() resolves immediately, so the dialog must be waited for explicitly.
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

  try {
    await loginViaDwPassword(page)
    await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.bpmn-canvas', { timeout: 30000 })

    await page.evaluate(({ fuId, sessionId, draft }) => {
      localStorage.setItem(`ai_generation_draft_${fuId}_${sessionId}`, JSON.stringify(draft))
    }, { fuId: FU_ID, sessionId: SESSION_ID, draft: APPLIED_DRAFT })

    // ---- First open: applied draft restores the preview card + lights GENERATION ----
    await openPanel(page)

    const previewCard = page.locator('.ai-panel .generation-preview')
    expect(await previewCard.count() > 0, '4. Generation Preview card restored from the applied draft')

    const appliedBtn = page.locator('.ai-panel .generation-preview__actions button', { hasText: /Applied|已应用/i })
    expect(await appliedBtn.count() > 0, '4. restored card is in the read-only "Applied" state')

    const genSegment = page.locator('.ai-panel .phase-rail__segment').nth(2)
    const genDone = await genSegment.evaluate(el => el.classList.contains('is-done')).catch(() => false)
    expect(genDone, '1. GENERATION segment is marked done (check mark) after apply')
    expect(await genSegment.locator('.phase-rail__check').count() > 0, '1. GENERATION check mark is rendered')

    const docCards = page.locator('.ai-panel .inline-doc-viewer')
    const docCount = await docCards.count()
    expect(docCount >= 2, `2. Requirements + Design cards present (found ${docCount})`)
    const regenOnDocs = page.locator('.ai-panel .inline-doc-viewer button', { hasText: /Regenerate|重新生成/i })
    expect(await regenOnDocs.count() >= 2, `2. each document card has a Regenerate button (found ${await regenOnDocs.count()})`)

    await page.screenshot({ path: join(OUT_DIR, `${prefix}_ai-panel-applied-restored.png`), fullPage: false })
    await page.locator('.ai-panel .chat-dialog__messages').screenshot({
      path: join(OUT_DIR, `${prefix}_ai-panel-applied-chat-column.png`)
    })

    // ---- Close and reopen: the card must still be there (this is the reported bug) ----
    await page.locator('.ai-panel__header-actions button').last().click()
    await page.waitForSelector('.ai-panel', { state: 'detached', timeout: 15000 })
    await page.waitForTimeout(1000)
    await openPanel(page)

    expect(await page.locator('.ai-panel .generation-preview').count() > 0,
      '4. Generation Preview card is still there after close + reopen')
    expect(await page.locator('.ai-panel .phase-rail__segment').nth(2)
      .evaluate(el => el.classList.contains('is-done')).catch(() => false),
      '1. GENERATION stays checked after close + reopen')

    await page.screenshot({ path: join(OUT_DIR, `${prefix}_ai-panel-applied-after-reopen.png`), fullPage: false })

    // Expanded document card: the Regenerate button must be reachable there too
    await page.locator('.ai-panel .inline-doc-viewer__header').first().click()
    await page.waitForTimeout(800)
    expect(await page.locator('.ai-panel .inline-doc-viewer__card.is-expanded button', { hasText: /Regenerate|重新生成/i }).count() > 0,
      '2. expanded document card also exposes Regenerate')
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_ai-panel-doc-card-expanded.png`), fullPage: false })

    // ---- Regenerate must ask for THAT document's phase and opt out of phase advance ----
    // The request is aborted, so this costs no AI gateway call and changes no data.
    let sent = null
    await page.route('**/ai-generation/chat/stream', async route => {
      try { sent = JSON.parse(route.request().postData() || '{}') } catch { /* leave null */ }
      await route.abort()
    })
    await page.locator('.ai-panel .inline-doc-viewer', { hasText: /^\s*Design/ })
      .locator('button', { hasText: /Regenerate|重新生成/i }).first().click()
    // Regenerate now opens a correction box first; confirming it with an empty text area keeps
    // the original blind-regenerate behaviour this assertion was written against.
    // el-popover pre-renders every instance into the body, so filter to the visible popper.
    const correctionBox = page.locator('.regenerate-box').locator('visible=true')
    await correctionBox.waitFor({ state: 'visible', timeout: 8000 })
    await correctionBox.locator('.regenerate-box__actions button').last().click()
    await page.waitForTimeout(2500)
    expect(sent?.phase === 'DESIGN',
      `2. Design card regenerates with phase=DESIGN, not the session phase (got ${sent?.phase})`)
    expect(sent?.regenerateOnly === true,
      `2. Design card regenerate sets regenerateOnly (got ${sent?.regenerateOnly})`)

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
