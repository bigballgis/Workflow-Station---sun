#!/usr/bin/env node
/** Read-only real-version and browser-only long-content layout verification. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const phase = process.env.VERIFY_PHASE || 'after'
assert.ok(['before', 'after'].includes(phase))
const shots = 'developer-workstation/verification-screenshots'
const runName = process.env.VERIFY_NAME || 'compare-layout'
assert.match(runName, /^[a-z0-9-]+$/)
const runDate = process.env.VERIFY_DATE || new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai'
}).format(new Date())
assert.match(runDate, /^\d{4}-\d{2}-\d{2}$/)
const screenshotPath = suffix => `${shots}/${runDate}-${runName}-${phase}-${suffix}.png`
const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const results = []
const failures = []
const checks = (condition, message) => { if (!condition) failures.push(message) }
const geometry = element => {
  const rect = element.getBoundingClientRect()
  const style = getComputedStyle(element)
  return { top: rect.top, bottom: rect.bottom, width: rect.width, height: rect.height,
    clientHeight: element.clientHeight, scrollHeight: element.scrollHeight,
    scrollTop: element.scrollTop, overflowY: style.overflowY }
}

try {
  const page = await browser.newPage({ viewport: runName === 'compare-scrollbar'
    ? { width: 1280, height: 720 } : { width: 1440, height: 900 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await loginViaDwPassword(page)
  for (const fuId of [50005, 50040]) {
    const response = await page.request.get(`http://localhost:3000/api/v1/function-units/${fuId}/versions`)
    assert.equal(response.status(), 200)
    const versions = (await response.json()).data
    assert.ok(versions.length >= 2, `FU ${fuId} requires saved version pair`)
    await page.goto(`http://localhost:3000/dev/function-units/${fuId}`, { waitUntil: 'domcontentloaded' })
    await page.locator('#tab-versions').click()
    await page.locator('.version-manager .action-buttons button:visible').first().waitFor()
    await page.screenshot({ path: screenshotPath(`list-${fuId}`), animations: 'disabled' })
    await page.locator('.version-manager .action-buttons button:visible').first().click()
    const dialog = page.locator('.el-dialog:has(.version-compare)')
    await dialog.waitFor()
    if (runName === 'compare-scrollbar' && phase === 'before') {
      // Reproduce the original full-row design in this browser only; no FU writes.
      await dialog.evaluate(element => {
        element.querySelectorAll('.version-compare__selector').forEach(selector => {
          selector.style.flex = '1'
        })
        element.querySelector('.el-dialog__body').style.paddingRight = '0px'
      })
    }
    if (runName === 'compare-scrollbar') {
      await page.screenshot({ path: screenshotPath(`open-${fuId}`), animations: 'disabled' })
    }
    const button = dialog.locator('.version-compare__selectors button')
    // Both controls must be measured in the same frame during dialog transitions.
    const { buttonSize, selectorSize, targetSize } = await dialog.locator('.version-compare__selectors').evaluate(element => {
      const read = control => {
        const rect = control.getBoundingClientRect()
        return { top: rect.top, bottom: rect.bottom, left: rect.left, right: rect.right,
          width: rect.width, height: rect.height }
      }
      return { buttonSize: read(element.querySelector('button')),
        selectorSize: read(element.querySelector('.version-compare__selector .el-select')),
        targetSize: read(element.querySelectorAll('.version-compare__selector .el-select')[1]) }
    })
    checks(Math.abs(buttonSize.height - selectorSize.height) <= 2, `FU ${fuId}: button/control heights differ`)
    checks(Math.abs(buttonSize.bottom - selectorSize.bottom) <= 2, `FU ${fuId}: Compare button is not aligned with selectors`)
    checks(buttonSize.width <= 140, `FU ${fuId}: Compare button stretched (${buttonSize.width})`)
    if (runName === 'compare-compact' && phase === 'after') {
      checks(Math.abs(selectorSize.width - 240) <= 1 && Math.abs(targetSize.width - 240) <= 1,
        `FU ${fuId}: version selectors are not compact 240px controls`)
      checks(Math.abs(buttonSize.left - targetSize.right - 16) <= 1,
        `FU ${fuId}: Compare button is not next to To version`)
      checks(Math.abs(buttonSize.bottom - targetSize.bottom) <= 2,
        `FU ${fuId}: Compare button is not aligned with To version`)
    }
    await button.click()
    await dialog.locator('.version-compare__summary').waitFor()
    if (runName === 'compare-scrollbar') {
      await page.screenshot({ path: screenshotPath(`fu-${fuId}`), animations: 'disabled' })
      const spacing = await dialog.evaluate(element => {
        const body = element.querySelector('.el-dialog__body')
        const row = element.querySelector('.version-compare__selectors')
        const buttonElement = row.querySelector('button')
        const button = buttonElement.getBoundingClientRect()
        const controls = [...row.querySelectorAll('.el-select')].map(control => control.getBoundingClientRect())
        const bodyRect = body.getBoundingClientRect()
        return { safeGap: bodyRect.left + body.clientWidth - button.right,
          paddingRight: getComputedStyle(body).paddingRight,
          hasScroll: body.scrollHeight > body.clientHeight,
          selectorWidths: controls.map(control => control.width),
          buttonRightEdgeClickable: document.elementFromPoint(button.right - 2,
            button.top + button.height / 2)?.closest('button') === buttonElement,
          rowFilled: Math.abs(row.getBoundingClientRect().right - button.right) <= 1,
          aligned: controls.every(control => Math.abs(control.bottom - button.bottom) <= 1) }
      })
      checks(spacing.safeGap >= 16, `FU ${fuId}: scrollbar safety gap too small (${spacing.safeGap}px)`)
      checks(spacing.rowFilled && spacing.aligned && spacing.selectorWidths.every(width => width > 400),
        `FU ${fuId}: controls do not fill the full row with equal-height alignment`)
      checks(spacing.hasScroll, `FU ${fuId}: short desktop viewport did not activate body scrolling`)
      checks(spacing.buttonRightEdgeClickable, `FU ${fuId}: button right edge is obscured`)
      results.push({ fuId, spacing })
    }
    if (runName === 'compare-padding' && phase === 'after') {
      const padding = await dialog.evaluate(element => {
        const style = getComputedStyle(element)
        return { top: style.paddingTop, right: style.paddingRight,
          bottom: style.paddingBottom, left: style.paddingLeft }
      })
      checks(Object.values(padding).every(value => value === '24px'),
        `FU ${fuId}: dialog padding is not 24px (${JSON.stringify(padding)})`)
      results.push({ fuId, padding })
    }
    await page.screenshot({ path: screenshotPath(`fu-${fuId}`), animations: 'disabled' })
    results.push({ fuId, buttonSize, selectorSize, targetSize, dialog: await dialog.evaluate(geometry) })
    await dialog.locator('.el-dialog__headerbtn').click()
  }

  // Stress data only exists in this browser response; saved versions are untouched.
  await page.route('**/versions/compare-v2?*', async route => {
    const response = await route.fetch()
    const json = await response.json()
    const module = json.data.modules.find(module => module.key === 'TABLES')
    const items = Array.from({ length: 60 }, (_, index) => ({
      objectType: 'TABLE', objectKey: `layout-fixture-${index}`, label: `QA long-content table ${index + 1}`,
      type: 'MODIFIED', scope: 'DESIGN', fields: Array.from({ length: 40 }, (_, field) => ({
        field: `layoutField${field + 1}`, oldValue: `Before ${field + 1}: ${'QA preview '.repeat(15)}`,
        newValue: `After ${field + 1}: ${'QA preview '.repeat(15)}`
      }))
    }))
    module.semantic = { status: 'COMPARED', scope: 'FULL', truncated: false,
      counts: { added: 0, modified: items.length, removed: 0 }, items }
    json.data.displayTotals = { added: 0, modified: items.length, removed: 0 }
    await route.fulfill({ response, json })
  })
  for (const viewport of [{ width: 1440, height: 900 }, { width: 1280, height: 720 }]) {
    await page.setViewportSize(viewport)
    await page.goto('http://localhost:3000/dev/function-units/50040', { waitUntil: 'domcontentloaded' })
    await page.locator('#tab-versions').click()
    await page.locator('.version-manager .action-buttons button:visible').first().click()
    const dialog = page.locator('.el-dialog:has(.version-compare)')
    await dialog.waitFor()
    await dialog.locator('.version-compare__selectors button').click()
    await dialog.locator('.version-compare__summary').waitFor()
    await dialog.locator('#tab-TABLES').click()
    const detail = dialog.locator('.compare-panel__detail:visible')
    const index = dialog.locator('.compare-panel__index:visible')
    const body = dialog.locator('.el-dialog__body')
    const initial = { dialog: await dialog.evaluate(geometry), body: await body.evaluate(geometry),
      detail: await detail.evaluate(geometry), index: await index.evaluate(geometry) }
    await page.screenshot({ path: screenshotPath(`long-${viewport.height}`), animations: 'disabled' })
    checks(initial.dialog.bottom <= viewport.height && initial.dialog.top >= 0,
      `${viewport.height}px: dialog exceeds viewport (bottom ${initial.dialog.bottom})`)
    await detail.locator('.compare-panel__field').first().hover()
    await page.mouse.wheel(0, 500)
    await page.waitForTimeout(300)
    const wheelScrollTop = await body.evaluate(element => element.scrollTop)
    checks(wheelScrollTop > 0, `${viewport.height}px: mouse wheel cannot scroll the detail page`)
    // Reach the last object and field, through whichever region owns scrolling.
    await index.evaluate(element => { element.scrollTop = element.scrollHeight })
    await index.locator('button').last().click()
    await detail.evaluate(element => { element.scrollTop = element.scrollHeight })
    await body.evaluate(element => { element.scrollTop = element.scrollHeight })
    const lastField = detail.locator('.compare-panel__field').last()
    const fieldRect = await lastField.boundingBox()
    checks(fieldRect.y + fieldRect.height <= viewport.height && fieldRect.y >= 0,
      `${viewport.height}px: last field remains outside viewport`)
    const lastObjectText = await detail.locator('h3').innerText()
    checks(lastObjectText.endsWith('60'), `${viewport.height}px: last object unreachable`)
    const pinnedIndex = await index.boundingBox()
    const pinnedModules = await dialog.locator('.el-tabs__header.is-left').boundingBox()
    if (phase === 'after') {
      checks(pinnedIndex.y >= 0 && pinnedIndex.y + pinnedIndex.height <= viewport.height,
        `${viewport.height}px: object navigation disappears while scrolling`)
      checks(pinnedModules.y >= 0 && pinnedModules.y + pinnedModules.height <= viewport.height,
        `${viewport.height}px: module navigation disappears while scrolling`)
    }
    await page.screenshot({ path: screenshotPath(`last-field-${viewport.height}`), animations: 'disabled' })
    results.push({ viewport, initial, wheelScrollTop, pinnedIndex, pinnedModules,
      lastField: fieldRect, lastObjectText })
    await dialog.locator('.el-dialog__headerbtn').click()
  }
  await page.unroute('**/versions/compare-v2?*')
  await page.route('**/versions/compare-v2?*', async route => {
    const response = await route.fetch()
    const json = await response.json()
    const module = json.data.modules.find(module => module.key === 'PROCESS')
    module.semantic = { status: 'UNPARSEABLE', scope: 'FULL',
      counts: { added: 0, modified: 0, removed: 0 }, truncated: false, items: [] }
    module.changes = Array.from({ length: 40 }, (_, index) => ({
      path: `PROCESS/QA generic change ${index + 1}`, type: 'MODIFIED',
      oldValue: 'Before QA preview '.repeat(12), newValue: 'After QA preview '.repeat(12)
    }))
    module.counts = { added: 0, modified: 40, removed: 0 }
    await route.fulfill({ response, json })
  })
  await page.locator('.version-manager .action-buttons button:visible').first().click()
  const genericDialog = page.locator('.el-dialog:has(.version-compare)')
  await genericDialog.locator('.version-compare__selectors button').click()
  await genericDialog.locator('.version-compare__summary').waitFor()
  await genericDialog.locator('#tab-PROCESS').click()
  await genericDialog.locator('.el-dialog__body').evaluate(element => { element.scrollTop = element.scrollHeight })
  const lastGeneric = await genericDialog.locator('.compare-panel__generic-item:visible').last().boundingBox()
  checks(lastGeneric.y >= 0 && lastGeneric.y + lastGeneric.height <= 720,
    'Generic historical XML fallback: last change unreachable')
  await page.screenshot({ path: screenshotPath('generic-last'), animations: 'disabled' })
  results.push({ genericLast: lastGeneric })
  await genericDialog.locator('.el-dialog__headerbtn').click()
  checks(errors.length === 0, `Page errors: ${errors.join(', ')}`)
  console.log(JSON.stringify({ phase, results, errors, failures }, null, 2))
  if (phase === 'after') assert.deepEqual(failures, [])
} finally {
  await browser.close()
}
