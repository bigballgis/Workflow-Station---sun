#!/usr/bin/env node
/**
 * Verify the phase gate: the model finishing a phase must NOT cascade into the next generation.
 *
 *   1. A phase_complete event offers a button instead of firing the next phase automatically.
 *      (Before this change, one user message ran requirements → design → generation back to
 *      back, so a wrong requirements doc was silently baked into tables and BPMN.)
 *   2. Nothing is sent to the AI while the offer is pending.
 *   3. Clicking the button runs exactly one next-phase call, for the next phase.
 *   4. That click is also what persists the phase (PUT /sessions/{id}/phase) — the backend no
 *      longer advances on its own, so the session never sits in a phase with no output.
 *
 * The chat stream is stubbed with a canned SSE body, so the run makes no AI gateway call and
 * writes no documents; only the phase PUT is observed (and it is aborted too).
 *
 * Usage (from frontend/):
 *   node scripts/verify-dw-ai-phase-gate.mjs [functionUnitId] [sessionId]
 *
 * Output: developer-workstation/verification-screenshots/{date}_ai-phase-gate-*.png
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

/** A finished REQUIREMENTS turn: the model produced its document and declared the phase done. */
function requirementsDoneStream() {
  const doc = JSON.stringify({
    documentType: 'REQUIREMENTS',
    content: '# Requirements Document\n\nStubbed by verify-dw-ai-phase-gate.',
    version: 7,
    generatedAt: new Date().toISOString()
  })
  return [
    `event:session\ndata:${JSON.stringify({ sessionId: SESSION_ID })}\n\n`,
    'event:token\ndata:Requirements captured.\n\n',
    `event:document\ndata:${doc}\n\n`,
    'event:phase_complete\ndata:REQUIREMENTS\n\n',
    'event:done\ndata:{}\n\n'
  ].join('')
}

/**
 * Take the "start a new session" branch so the panel really sits at REQUIREMENTS.
 *
 * Reusing the last session would land on whatever phase it ended at (GENERATION on the seeded
 * function unit), and the first→second phase gate is exactly what this script is about.
 * No session is created server-side: the chat stream never reaches the backend.
 */
async function startNewSession(page) {
  const newSession = page.getByRole('button', { name: /Start New Session|开始新会话/i })
  const shown = await newSession.waitFor({ state: 'visible', timeout: 10000 }).then(() => true, () => false)
  if (shown) {
    await newSession.click()
    await page.waitForTimeout(500)
  }
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

  // Every chat turn is answered from the stub; the requests are recorded so we can prove
  // how many model calls one user message actually costs.
  const chatCalls = []
  await page.route('**/ai-generation/chat/stream', async route => {
    try { chatCalls.push(JSON.parse(route.request().postData() || '{}')) } catch { chatCalls.push(null) }
    await route.fulfill({
      status: 200,
      headers: { 'content-type': 'text/event-stream', 'cache-control': 'no-cache' },
      body: requirementsDoneStream()
    })
  })

  const phasePuts = []
  await page.route('**/ai-generation/sessions/*/phase**', async route => {
    phasePuts.push(route.request().url())
    await route.abort()
  })

  try {
    await loginViaDwPassword(page)
    await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.bpmn-canvas', { timeout: 30000 })

    await page.getByRole('button', { name: /AI Generate/i }).click()
    await startNewSession(page)
    await page.waitForSelector('.ai-panel .chat-dialog', { timeout: 20000 })
    await page.waitForTimeout(2500)

    // ---- One user message -> exactly one model call, then a stop ----
    chatCalls.length = 0
    phasePuts.length = 0
    await page.locator('.ai-panel .chat-dialog__input-area textarea').fill('Track shipments per order.')
    await page.locator('.ai-panel .chat-dialog__input-area button', { hasText: /Send|发送/i }).click()

    await page.waitForSelector('.ai-panel .chat-dialog__phase-action', { timeout: 20000 })
    // Long enough that a cascade would have fired by now (it used to go out on nextTick).
    await page.waitForTimeout(3000)

    expect(chatCalls.length === 1,
      `1+2. one message costs exactly one model call, no cascade (got ${chatCalls.length})`)
    const gate = page.locator('.ai-panel .chat-dialog__phase-action')
    expect(await gate.count() > 0, '1. the next-phase button is offered')
    const gateLabel = (await gate.locator('button').innerText()).trim()
    expect(/Design|设计/i.test(gateLabel),
      `1. the button says what it will generate, not just "next phase" (got "${gateLabel}")`)
    expect(phasePuts.length === 0, '4. the phase is not persisted while the offer is pending')

    await page.locator('.ai-panel .chat-dialog__messages').screenshot({
      path: join(OUT_DIR, `${prefix}_ai-phase-gate-offer.png`)
    })

    // ---- Clicking runs the next phase, once, and persists it ----
    await gate.locator('button').click()
    await page.waitForTimeout(3500)

    expect(chatCalls.length === 2,
      `3. clicking runs exactly one more call (got ${chatCalls.length} total)`)
    expect(chatCalls[1]?.phase === 'DESIGN',
      `3. that call targets DESIGN (got ${chatCalls[1]?.phase})`)
    expect(phasePuts.some(url => /phase=DESIGN/.test(url)),
      `4. the click persists the advance to DESIGN (calls: ${JSON.stringify(phasePuts)})`)

    await page.screenshot({ path: join(OUT_DIR, `${prefix}_ai-phase-gate-after-click.png`), fullPage: false })

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
