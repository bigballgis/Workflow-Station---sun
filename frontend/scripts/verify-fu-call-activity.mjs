/**
 * Screenshot verification for "Function Unit calls Function Unit" (P1–P3).
 *
 * Captures:
 *   1. The Function Unit settings dialog, where Startup Mode is chosen.
 *   2. The process designer with a callActivity selected, showing its properties panel.
 *
 * Run with the dev stack up:
 *   node frontend/scripts/verify-fu-call-activity.mjs
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { loginViaDwPassword } from './playwright-login.mjs'

// Absolute so the output lands in the same place regardless of where node was invoked from.
const OUT_DIR = new URL('../developer-workstation/verification-screenshots/', import.meta.url).pathname
  .replace(/^\/([A-Za-z]:)/, '$1')
const ORIGIN = process.env.DW_ORIGIN ?? 'http://localhost:3000'
const STAMP = new Date().toISOString().slice(0, 10)

async function shot(page, name) {
  const path = `${OUT_DIR}/${STAMP}_${name}.png`
  await page.screenshot({ path, fullPage: false })
  console.log(`  saved ${path}`)
  return path
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })

  const browser = await chromium.launch()
  const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } })
  const page = await context.newPage()

  page.on('console', (msg) => {
    if (msg.type() === 'error') console.log(`  [browser error] ${msg.text()}`)
  })

  try {
    console.log('Logging in to Developer Workstation...')
    await loginViaDwPassword(page, { loginOrigin: ORIGIN })

    // ---- 1. Startup Mode in the Function Unit settings dialog -------------
    console.log('Opening the Function Unit list...')
    await page.goto(`${ORIGIN}/dev/function-units`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(1500)
    await shot(page, 'fu-call_01-function-unit-list')

    // Settings live behind each tile's hover menu. Hover the first tile, then click through.
    const tile = page.locator('.launchpad-cell').first()
    await tile.hover()
    await page.waitForTimeout(600)

    // The menu trigger is an icon button that appears on hover.
    const menuTrigger = tile.locator('.el-dropdown-link, .tile-menu, [class*="more"]').first()
    if (await menuTrigger.count() > 0) {
      await menuTrigger.click({ force: true })
      await page.waitForTimeout(600)
    }

    const settingsItem = page.locator('.el-dropdown-menu__item', { hasText: /settings|设置|設定/i }).first()
    if (await settingsItem.count() > 0) {
      await settingsItem.click()
      await page.waitForTimeout(1200)
      await shot(page, 'fu-call_02-startup-mode-dialog')
    } else {
      console.log('  (settings menu item not reachable; see 01 for list state)')
      await shot(page, 'fu-call_02-settings-menu-attempt')
    }

    // ---- 2. Call activity properties panel in the process designer -------
    const fuId = process.env.FU_ID ?? '3'
    console.log(`Opening process designer of Function Unit ${fuId}...`)
    await page.goto(`${ORIGIN}/dev/function-units/${fuId}`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(2500)
    await shot(page, 'fu-call_03-process-designer')

    // ---- 3. Call activity properties panel -------------------------------
    // Morphing an existing task through the context pad is how a user creates one, and it is
    // far more stable to drive than dragging from the palette.
    //
    // NOTE: this mutates the opened Function Unit's diagram (the designer auto-saves). Point
    // FU_ID at a scratch unit, or restore the diagram afterwards, before using this on a demo.
    const taskId = process.env.TASK_ID ?? 'Task_CreateMeeting'
    console.log(`Morphing ${taskId} into a call activity...`)

    const task = page.locator(`.djs-element[data-element-id="${taskId}"]`)
    if (await task.count() === 0) {
      console.log(`  (no element ${taskId} in this diagram; skipping the panel capture)`)
      return
    }

    await task.click({ force: true })
    await page.waitForTimeout(800)

    const replaceEntry = page.locator('.djs-context-pad .entry[data-action="replace"]').first()
    if (await replaceEntry.count() > 0) {
      await replaceEntry.click()
      await page.waitForTimeout(800)
      const callEntry = page.locator('.djs-popup [data-id="replace-with-call-activity"]').first()
      if (await callEntry.count() > 0) {
        await callEntry.click()
        await page.waitForTimeout(2000)
      }
    }

    await shot(page, 'fu-call_04-call-activity-properties')

    // The dropdown must offer only units whose Startup Mode allows being called, and never
    // the unit being edited — a self-call would recurse without end.
    const select = page.locator('.call-activity-properties .el-select').first()
    if (await select.count() > 0) {
      await select.click()
      await page.waitForTimeout(1200)
      const options = await page.locator('.el-select-dropdown__item')
        .evaluateAll((els) => els.map((e) => e.textContent?.trim()))
      console.log(`  callable units offered: ${JSON.stringify(options)}`)
      await shot(page, 'fu-call_05-callable-dropdown')
    } else {
      console.log('  (call activity properties panel did not render)')
    }

    console.log('Done.')
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
