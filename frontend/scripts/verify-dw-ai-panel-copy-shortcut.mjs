#!/usr/bin/env node
/**
 * Verify that Cmd/Ctrl+C is NOT swallowed inside the DW "AI Generate" panel.
 *
 * Background: the BPMN modeler used to bind diagram-js keyboard shortcuts to
 * `document` (useProcessModeler.ts), so its KeyboardBindings preventDefault()'ed
 * copy/cut/paste/select-all everywhere on the Function Unit edit page — the AI
 * panel included — and text there could not be copied with the keyboard.
 * Shortcuts are now scoped to the (focusable) canvas.
 *
 * Asserts:
 *   1. text selected inside the AI panel survives, and Ctrl/Cmd+C is not
 *      preventDefault()'ed (i.e. the browser's native copy can run);
 *   2. the BPMN diagram is untouched by those keystrokes (no stray command,
 *      hence no auto-save);
 *   3. with the canvas focused, Ctrl/Cmd+A is still handled by diagram-js.
 *
 * Usage (from frontend/):
 *   node scripts/verify-dw-ai-panel-copy-shortcut.mjs [functionUnitId]
 *
 * Output: developer-workstation/verification-screenshots/{date}_ai-panel-copy-shortcut*.png
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

  try {
    await loginViaDwPassword(page)
    await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.bpmn-canvas .djs-element', { timeout: 30000 })
    const elementsBefore = await page.locator('.bpmn-canvas .djs-element').count()

    await page.getByRole('button', { name: /AI Generate/i }).click()
    await page.waitForSelector('.ai-panel', { timeout: 15000 })

    // Record whether anything preventDefault()s the copy shortcut, then select
    // panel text and press the shortcut for real.
    // The panel focuses its message box shortly after mount, which would collapse a
    // selection made too early — settle first, then select.
    await page.waitForTimeout(1500)
    const selectedText = await page.evaluate(() => {
      window.__keys = []
      window.addEventListener('keydown', (e) => {
        if (e.metaKey || e.ctrlKey) window.__keys.push({ key: e.key, prevented: e.defaultPrevented })
      })
      const node = [...document.querySelectorAll('.ai-panel .chat-dialog *')].find(
        (el) => el.children.length === 0 && (el.textContent || '').trim().length > 4,
      )
      if (!node) throw new Error('no selectable text found in the AI panel')
      const range = document.createRange()
      range.selectNodeContents(node)
      const sel = getSelection()
      sel.removeAllRanges()
      sel.addRange(range)
      return String(sel)
    })
    if (!selectedText.trim()) throw new Error('could not select any text in the AI panel')
    await page.keyboard.press('ControlOrMeta+c')

    const panel = await page.evaluate(() => ({
      keys: window.__keys,
      selection: String(getSelection()),
    }))
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_ai-panel-copy-shortcut.png`) })

    const copyKey = panel.keys.find((k) => k.key === 'c' || k.key === 'C')
    if (!copyKey) throw new Error('copy shortcut never reached the page')
    if (copyKey.prevented) {
      throw new Error('FAIL: Cmd/Ctrl+C is still preventDefault()ed inside the AI panel')
    }
    if (!panel.selection.trim()) throw new Error('FAIL: selection inside the AI panel was cleared')

    const elementsAfterPanel = await page.locator('.bpmn-canvas .djs-element').count()
    if (elementsAfterPanel !== elementsBefore) {
      throw new Error(
        `FAIL: BPMN diagram changed while typing in the panel (${elementsBefore} -> ${elementsAfterPanel})`,
      )
    }

    // Canvas keeps its own shortcuts: focus it, select all, expect diagram-js to handle it.
    await page.locator('.ai-panel__header-actions button').last().click()
    await page.waitForSelector('.ai-panel', { state: 'detached', timeout: 10000 })
    await page.locator('.bpmn-canvas').click({ position: { x: 40, y: 400 } })
    await page.evaluate(() => {
      window.__keys = []
      window.addEventListener('keydown', (e) => {
        if (e.metaKey || e.ctrlKey) window.__keys.push({ key: e.key, prevented: e.defaultPrevented })
      })
    })
    await page.keyboard.press('ControlOrMeta+a')
    const canvasKeys = await page.evaluate(() => window.__keys)
    const selected = await page.locator('.bpmn-canvas .djs-element.selected').count()
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_ai-panel-copy-shortcut_canvas-select-all.png`) })

    if (!canvasKeys.some((k) => (k.key === 'a' || k.key === 'A') && k.prevented)) {
      throw new Error('FAIL: canvas no longer handles Cmd/Ctrl+A')
    }
    if (selected === 0) throw new Error('FAIL: Cmd/Ctrl+A selected nothing on the canvas')

    console.log(
      `[verify] PASS panel copy not blocked (selection=${JSON.stringify(panel.selection.slice(0, 40))}), ` +
        `diagram untouched (${elementsBefore} elements), canvas select-all selected ${selected}`,
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
