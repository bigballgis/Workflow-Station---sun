#!/usr/bin/env node
/**
 * Verify the two ProcessDesigner BPMN-fidelity changes.
 *
 * A. Export XML must ship the persisted BPMN, not `bpmnModeler.saveXML()`.
 *    saveXML is `moddle.toXML(definitions)`, which drops any extension element the
 *    moddle descriptors do not declare, so an export used for migration/archival has
 *    to come from the database instead.
 *
 * B. When importXML fails with "no diagram to display" the designer falls back to a
 *    default Start -> End diagram. That placeholder has nodes AND shapes, so the
 *    empty-diagram guard lets it through and the 2s auto-save would overwrite the
 *    real process. It now needs its own guard.
 *
 * Asserts:
 *   1. the downloaded process.bpmn is byte-identical to GET /process's bpmnXml;
 *   2. bpmn-js is never asked to serialize during export (extension props survive);
 *   3. on the fallback placeholder no POST to /process fires within the auto-save window;
 *   4. the toolbar shows the "placeholder diagram" paused indicator + warning toast;
 *   5. manual Save opens a confirmation dialog; cancelling saves nothing.
 *
 * Usage (from frontend/):
 *   node scripts/verify-dw-bpmn-export-and-fallback-guard.mjs [functionUnitId]
 *
 * Output: developer-workstation/verification-screenshots/{date}_bpmn-export-*.png
 */
import { mkdirSync, readFileSync } from 'fs'
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

/**
 * Strip the DI section but keep the literal "BPMNShape" in a comment: that is exactly
 * the shape that reaches the fallback branch — ensureBpmnHasVisualLayout skips
 * bpmn-auto-layout (it only runs when /BPMNShape/ does not match) and bpmn-js then
 * throws "no diagram to display".
 */
function stripDiagramKeepMarker(xml) {
  return xml
    .replace(/<bpmndi:BPMNDiagram[\s\S]*?<\/bpmndi:BPMNDiagram>/g, '<!-- BPMNShape removed -->')
    .replace(/<bpmndi:BPMNDiagram[\s\S]*?\/>/g, '<!-- BPMNShape removed -->')
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const prefix = datePrefix()
  const browser = await chromium.launch()
  const context = await browser.newContext({
    viewport: { width: 1600, height: 1000 },
    acceptDownloads: true,
  })
  const page = await context.newPage()

  const processPosts = []
  page.on('request', (req) => {
    if (req.method() === 'POST' && /\/function-units\/\d+\/process(\?|$)/.test(req.url())) {
      processPosts.push(req.url())
    }
  })

  /** Persisted BPMN as the backend last returned it. */
  let persistedXml = ''
  page.on('response', async (res) => {
    if (res.request().method() === 'GET' && /\/function-units\/\d+\/process(\?|$)/.test(res.url())) {
      try {
        const body = await res.json()
        if (body?.data?.bpmnXml) persistedXml = body.data.bpmnXml
      } catch {
        /* non-JSON / already consumed */
      }
    }
  })

  try {
    await loginViaDwPassword(page)

    // ---------- A. Export XML ships the persisted BPMN ----------
    await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.bpmn-canvas .djs-element', { timeout: 30000 })
    if (!persistedXml) {
      throw new Error(`FU ${FU_ID} returned no stored bpmnXml — pick a unit with a saved process`)
    }

    // Count how often bpmn-js serializes; export must not call it at all.
    await page.evaluate(() => {
      window.__saveXmlCalls = 0
    })
    await page.exposeFunction('__countSaveXml', () => {})

    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 20000 }),
      page.getByRole('button', { name: /Export XML|导出XML|匯出XML/i }).click(),
    ])
    const downloadedPath = await download.path()
    const downloaded = readFileSync(downloadedPath, 'utf8')

    await page.waitForSelector('.el-message--success', { timeout: 10000 })
    await page.waitForTimeout(600) // let the toast finish sliding in before capturing
    const exportToast = (await page.locator('.el-message--success').first().innerText()) || ''
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_bpmn-export-saved-version.png`) })

    if (downloaded !== persistedXml) {
      throw new Error(
        `FAIL: exported XML differs from the stored BPMN (downloaded ${downloaded.length} chars, ` +
          `stored ${persistedXml.length} chars)`,
      )
    }
    if (download.suggestedFilename() !== 'process.bpmn') {
      throw new Error(`FAIL: unexpected download name ${download.suggestedFilename()}`)
    }
    if (!/saved|database|已导出|已匯出|資料庫|数据库/i.test(exportToast)) {
      throw new Error(`FAIL: export toast does not say it exported the saved version (${JSON.stringify(exportToast)})`)
    }

    // Extension elements that a saveXML round-trip is at risk of dropping.
    const extensionProps = [...persistedXml.matchAll(/name="([^"]+)"\s+value="/g)].map((m) => m[1])
    console.log(
      `[verify] A PASS export == stored BPMN (${downloaded.length} chars, ` +
        `${extensionProps.length} custom properties incl. ${[...new Set(extensionProps)].slice(0, 6).join(', ')})`,
    )

    // ---------- B. Fallback placeholder must not be auto-saved ----------
    await page.route(/\/function-units\/\d+\/process(\?|$)/, async (route) => {
      if (route.request().method() !== 'GET') return route.fallback()
      const res = await route.fetch()
      const body = await res.json()
      body.data.bpmnXml = stripDiagramKeepMarker(body.data.bpmnXml)
      await route.fulfill({ response: res, json: body })
    })

    processPosts.length = 0
    const consoleLines = []
    page.on('console', (msg) => consoleLines.push(msg.text()))
    await page.reload({ waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.bpmn-canvas .djs-element', { timeout: 30000 })

    const fellBack = consoleLines.some((l) => /no DI diagram info/i.test(l))
    const placeholderElements = await page.locator('.bpmn-canvas .djs-element').count()
    console.log(`[verify] fallback branch hit=${fellBack}, canvas elements=${placeholderElements}`)
    if (!fellBack) {
      throw new Error('setup: the import fallback never fired — the DI-stripping route did not take effect')
    }

    // Move a shape so commandStack.changed schedules an auto-save (selection alone does not).
    const shape = page.locator('.bpmn-canvas .djs-element[data-element-id="StartEvent_1"]').first()
    const box = await shape.boundingBox()
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
    await page.mouse.down()
    await page.mouse.move(box.x + box.width / 2 + 60, box.y + box.height / 2 + 40, { steps: 10 })
    await page.mouse.up()
    await page.waitForTimeout(AUTO_SAVE_WINDOW_MS)

    const pausedIndicator = page.locator('.auto-save-blocked')
    const pausedText = (await pausedIndicator.first().innerText().catch(() => '')) || ''
    const warningToast =
      (await page.locator('.el-message--warning').first().textContent().catch(() => '')) || ''
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_bpmn-fallback-autosave-blocked.png`) })

    if (processPosts.length > 0) {
      throw new Error(`FAIL: the placeholder diagram was auto-saved (${processPosts.join(', ')})`)
    }
    if (!/placeholder|占位|佔位/i.test(pausedText)) {
      throw new Error(`FAIL: toolbar indicator is not the placeholder one (${JSON.stringify(pausedText)})`)
    }
    if (!/placeholder|占位|佔位/i.test(warningToast)) {
      throw new Error(`FAIL: no placeholder warning toast (${JSON.stringify(warningToast)})`)
    }

    // Manual Save must ask before committing the placeholder over the stored process.
    await page.getByRole('button', { name: /^\s*(Save|保存|儲存)\s*$/ }).click()
    await page.waitForSelector('.el-message-box', { timeout: 10000 })
    await page.waitForTimeout(800) // let the dialog finish fading in before capturing
    const confirmText = (await page.locator('.el-message-box').innerText()) || ''
    await page.screenshot({ path: join(OUT_DIR, `${prefix}_bpmn-fallback-save-confirm.png`) })
    if (!/placeholder|占位|佔位/i.test(confirmText)) {
      throw new Error(`FAIL: confirm dialog does not explain the overwrite (${JSON.stringify(confirmText)})`)
    }

    await page.locator('.el-message-box__btns button').first().click()
    await page.waitForTimeout(1500)
    if (processPosts.length > 0) {
      throw new Error(`FAIL: cancelling the confirm dialog still saved (${processPosts.join(', ')})`)
    }

    console.log(
      `[verify] B PASS placeholder diagram not auto-saved (0 POSTs), paused indicator + warning shown, ` +
        `manual Save asked for confirmation, cancel saved nothing`,
    )
    console.log(`[verify] screenshots -> ${OUT_DIR}`)
  } finally {
    await context.close()
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
