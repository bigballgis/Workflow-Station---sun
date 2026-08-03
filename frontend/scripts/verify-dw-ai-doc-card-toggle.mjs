#!/usr/bin/env node
/**
 * Verify the Requirements / Design document cards toggle from the whole title row.
 *
 * Before: clicking anywhere on a collapsed card expanded it, but collapsing only worked on
 * the "Collapse Document" button — the same strip of pixels obeyed two different rules.
 * Now the title row is the switch in both directions and both text buttons are gone; a
 * chevron carries the state, and View mode / Regenerate keep their own click.
 *
 * The generation draft is seeded the same way verify-dw-ai-panel-applied-state.mjs does it,
 * so the run needs no live AI gateway call.
 *
 * Usage (from frontend/):
 *   node scripts/verify-dw-ai-doc-card-toggle.mjs [functionUnitId] [sessionId]
 *
 * Output: developer-workstation/verification-screenshots/{date}_ai-doc-card-*.png
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

async function keepLastSession(page) {
  const viewLast = page.getByRole('button', { name: /View Last Session|查看上次会话/i })
  const shown = await viewLast.waitFor({ state: 'visible', timeout: 10000 }).then(() => true, () => false)
  if (shown) {
    await viewLast.click()
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

  try {
    await loginViaDwPassword(page)
    await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.bpmn-canvas', { timeout: 30000 })

    await page.evaluate(({ fuId, sessionId, draft }) => {
      localStorage.setItem(`ai_generation_draft_${fuId}_${sessionId}`, JSON.stringify(draft))
    }, { fuId: FU_ID, sessionId: SESSION_ID, draft: APPLIED_DRAFT })

    await page.getByRole('button', { name: /AI Generate/i }).click()
    await keepLastSession(page)
    await page.waitForSelector('.ai-panel .chat-dialog', { timeout: 20000 })
    await page.waitForTimeout(2500)

    const card = page.locator('.ai-panel .inline-doc-viewer__card').first()
    const header = card.locator('.inline-doc-viewer__header')
    const isExpanded = () => card.evaluate(el => el.classList.contains('is-expanded'))

    expect(!(await isExpanded()), 'card starts collapsed')
    expect(await page.locator('.ai-panel .inline-doc-viewer button', { hasText: /Expand Document|Collapse Document|展开文档|折叠文档/i }).count() === 0,
      'the Expand / Collapse Document buttons are gone')
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_ai-doc-card-collapsed.png`), fullPage: false })

    // Click the title row on a spot that is not a control — the reported half of the bug.
    await header.click({ position: { x: 200, y: 12 } })
    await page.waitForTimeout(600)
    expect(await isExpanded(), 'clicking the title row expands the card')
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_ai-doc-card-expanded-by-row.png`), fullPage: false })

    // View mode + Regenerate live in the same row and must not collapse it.
    await card.locator('.view-mode-toggle__seg', { hasText: /Markdown/i }).first().click()
    await page.waitForTimeout(500)
    expect(await isExpanded(), 'switching view mode from the title row keeps the card open')

    // Same blank spot again — this is what previously did nothing.
    await header.click({ position: { x: 200, y: 12 } })
    await page.waitForTimeout(600)
    expect(!(await isExpanded()), 'clicking the same blank spot collapses the card')
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_ai-doc-card-collapsed-by-row.png`), fullPage: false })

    // Keyboard parity: the row is a real button for anyone not using a mouse.
    await header.focus()
    await page.keyboard.press('Enter')
    await page.waitForTimeout(600)
    expect(await isExpanded(), 'Enter on the focused title row expands the card')
    await page.keyboard.press(' ')
    await page.waitForTimeout(600)
    expect(!(await isExpanded()), 'Space on the focused title row collapses the card')

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
