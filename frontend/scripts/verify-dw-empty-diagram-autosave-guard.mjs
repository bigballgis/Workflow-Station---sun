#!/usr/bin/env node
/**
 * Verify the ProcessDesigner empty-diagram auto-save guard.
 *
 * Background: handleSave(true) runs 2s after every commandStack.changed and POSTed
 * whatever bpmn-js exported. On 2026-07-31 stray keyboard shortcuts wiped FU 50030's
 * canvas and the auto-save replaced its Start -> serviceTask -> End process with an
 * empty <bpmn:process/>; dw_process_definitions keeps only the current version, so the
 * overwrite was unrecoverable.
 *
 * Asserts (canvas is wiped with select-all + Delete):
 *   1. no POST to /function-units/{id}/process fires within the auto-save window;
 *   2. the designer warns and the toolbar shows the "auto-save paused" indicator;
 *   3. manual Save opens a confirmation dialog; cancelling it saves nothing;
 *   4. after a reload the stored diagram is still intact.
 *
 * Usage (from frontend/):
 *   node scripts/verify-dw-empty-diagram-autosave-guard.mjs [functionUnitId]
 *
 * Output: developer-workstation/verification-screenshots/{date}_empty-diagram-autosave-guard*.png
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
const FU_ID = process.argv[2] ?? '50030'
/** scheduleAutoSave debounce is 2s — wait well past it before concluding "nothing was sent". */
const AUTO_SAVE_WINDOW_MS = 5000

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const prefix = datePrefix()
  const browser = await chromium.launch()
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })

  const processPosts = []
  page.on('request', (req) => {
    if (req.method() === 'POST' && /\/function-units\/\d+\/process(\?|$)/.test(req.url())) {
      processPosts.push(req.url())
    }
  })

  try {
    await loginViaDwPassword(page)
    await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.bpmn-canvas .djs-element', { timeout: 30000 })
    const elementsBefore = await page.locator('.bpmn-canvas .djs-element').count()
    if (elementsBefore === 0) {
      throw new Error(`FU ${FU_ID} has an empty diagram already — pick a unit with a saved process`)
    }

    // Wipe the canvas exactly the way the incident did: canvas-scoped select-all + Delete.
    await page.locator('.bpmn-canvas').click({ position: { x: 40, y: 400 } })
    await page.keyboard.press('ControlOrMeta+a')
    await page.keyboard.press('Delete')
    await page.waitForTimeout(AUTO_SAVE_WINDOW_MS)

    const elementsAfterWipe = await page.locator('.bpmn-canvas .djs-element').count()
    if (elementsAfterWipe !== 0) {
      throw new Error(`canvas was not wiped (still ${elementsAfterWipe} elements) — nothing to verify`)
    }

    const blockedIndicator = await page.locator('.auto-save-blocked').count()
    const warningToast = await page.locator('.el-message--warning').first().textContent().catch(() => '')
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_empty-diagram-autosave-guard_blocked.png`) })

    if (processPosts.length > 0) {
      throw new Error(`FAIL: auto-save persisted the empty diagram (${processPosts.join(', ')})`)
    }
    if (blockedIndicator === 0) {
      throw new Error('FAIL: toolbar does not show the auto-save paused indicator')
    }
    if (!/auto-save|自动保存|自動儲存/i.test(warningToast || '')) {
      throw new Error(`FAIL: no auto-save warning shown (toast=${JSON.stringify(warningToast)})`)
    }

    // Manual Save must ask before committing the wipe; cancelling keeps the stored version.
    await page.getByRole('button', { name: /^\s*Save\s*$/ }).click()
    await page.waitForSelector('.el-message-box', { timeout: 10000 })
    await page.waitForTimeout(800) // let the dialog finish fading in before capturing
    const confirmText = (await page.locator('.el-message-box').innerText()) || ''
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_empty-diagram-autosave-guard_confirm.png`) })
    if (!/empty|空/i.test(confirmText)) {
      throw new Error(`FAIL: confirm dialog does not explain the wipe (text=${JSON.stringify(confirmText)})`)
    }
    await page.locator('.el-message-box__btns button').first().click()
    await page.waitForTimeout(1500)
    if (processPosts.length > 0) {
      throw new Error(`FAIL: cancelling the confirm dialog still saved (${processPosts.join(', ')})`)
    }

    // The stored process must survive the whole episode.
    await page.reload({ waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.bpmn-canvas .djs-element', { timeout: 30000 })
    const elementsAfterReload = await page.locator('.bpmn-canvas .djs-element').count()
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_empty-diagram-autosave-guard_after-reload.png`) })
    if (elementsAfterReload !== elementsBefore) {
      throw new Error(
        `FAIL: stored diagram changed (${elementsBefore} -> ${elementsAfterReload} elements after reload)`,
      )
    }

    console.log(
      `[verify] PASS wipe of FU ${FU_ID} was not auto-saved (0 POSTs), warning + paused indicator shown, ` +
        `manual Save asked for confirmation, diagram intact after reload (${elementsAfterReload} elements)`,
    )
    console.log(`[verify] screenshots -> ${OUT_DIR}`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
