/** Read-only designer check; the existing synthetic FU is never saved. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'

const unitId = process.env.FIXTURE_UNIT_ID
assert.match(unitId ?? '', /^\d+$/)
const stage = process.env.VERIFY_STAGE ?? 'after'
const spacing = process.env.VERIFY_SPACING === '1'
assert.ok(['before', 'after'].includes(stage))
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 900 } })
  const errors = [], writes = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  await loginViaDwPassword(page)
  const unit = await (await page.request.get(`http://localhost:3000/api/v1/function-units/${unitId}`)).json()
  assert.ok(unit.data.tags.includes('batch-verification-synthetic'))
  page.on('request', request => {
    const path = new URL(request.url()).pathname
    // Opening a FU updates Recent preferences; that is not a design mutation.
    if (['PUT', 'PATCH', 'DELETE'].includes(request.method()) && !path.startsWith('/api/v1/user-preferences/')) writes.push(path)
  })
  await page.goto(`http://localhost:3000/dev/function-units/${unitId}`)
  await page.getByRole('tab', { name: /Form/ }).click()
  await page.getByRole('row').filter({ hasText: 'Synthetic TASK' }).getByRole('button', { name: 'Edit', exact: true }).click()
  await page.locator('.form-editor-view .fc-designer-wrapper').first().waitFor()
  await page.locator('.form-editor-view .designer-nav-btn').filter({ hasText: /Sub Table/ }).click()
  await page.getByRole('menuitem').filter({ hasText: 'Synthetic lines' }).click()
  await page.getByRole('button', { name: 'List View', exact: true }).click()
  const view = page.locator('.sub-table-list-view:visible')
  const panel = view.locator('.columns-panel')
  await panel.waitFor()
  const shot = async name => {
    const path = fileURLToPath(new URL(`../developer-workstation/verification-screenshots/2026-10-20_list-view-${stage}-${spacing ? 'spacing-' : ''}${name}.png`, import.meta.url))
    await panel.screenshot({ path }); return path
  }
  const screenshots = [await shot('expanded')]
  const gap = await panel.evaluate(el => {
    const fields = el.querySelectorAll('.panel-section:first-child .field-item')
    const last = fields[fields.length - 1]
    return el.querySelector('.extend-action-section').getBoundingClientRect().top - last.getBoundingClientRect().bottom
  })
  const tableToggle = panel.getByTestId('table-columns-toggle')
  if (stage === 'before') {
    if (spacing) assert.ok(gap > 12, `Expected space beyond the compact 12px limit, actual ${gap}px`)
    else assert.equal(await tableToggle.count(), 0)
    console.log(JSON.stringify({ stage, gap, reproduced: spacing ? 'Excess empty space' : 'No vertical collapse control', screenshots }))
  } else {
    if (spacing) assert.ok(gap >= 0 && gap <= 12, `Extend Action must follow fields closely, actual ${gap}px`)
    const search = panel.getByPlaceholder('Search')
    await search.fill('caption')
    const beforeY = (await panel.getByTestId('extend-action-toggle').boundingBox()).y
    await tableToggle.click()
    assert.equal(await panel.locator('.section-content').isVisible(), false)
    const extend = panel.getByTestId('extend-action-toggle')
    const afterY = (await extend.boundingBox()).y
    assert.ok(afterY < beforeY, 'Extend Action must move up when the field section is folded')
    assert.equal(await panel.locator('.link-form-item').isVisible(), true)
    screenshots.push(await shot('columns-collapsed'))
    await extend.click()
    assert.equal(await panel.locator('.extend-action-list').isVisible(), false)
    screenshots.push(await shot('both-collapsed'))
    await tableToggle.click()
    assert.equal(await search.inputValue(), 'caption')
    assert.equal(await panel.locator('.extend-action-list').isVisible(), false)
    await panel.locator('.columns-panel-close').click()
    assert.equal(await panel.count(), 0)
    await view.locator('.columns-toggle').click()
    assert.equal(await panel.getByPlaceholder('Search').inputValue(), 'caption')
    await panel.getByTestId('extend-action-toggle').click()
    assert.equal(await panel.locator('.lookup-action-item').isVisible(), true)
    if (spacing) {
      await panel.getByPlaceholder('Search').fill('')
      await page.setViewportSize({ width: 1366, height: 720 })
      const scroll = await panel.evaluate(el => {
        const list = el.querySelector('.section-content .columns-field-list')
        const action = el.querySelector('.extend-action-section').getBoundingClientRect()
        const bounds = el.getBoundingClientRect()
        return { client: list.clientHeight, content: list.scrollHeight, actionBottom: action.bottom, panelBottom: bounds.bottom }
      })
      assert.ok(scroll.content > scroll.client, 'Long field lists must scroll inside their section')
      assert.ok(scroll.actionBottom <= scroll.panelBottom + 1, 'Extend Action must stay inside the panel')
      screenshots.push(await shot('short-viewport'))
    }
    console.log(JSON.stringify({ stage, result: 'PASS', gap, beforeY, afterY, screenshots }))
  }
  assert.deepEqual(writes, [])
  assert.deepEqual(errors, [])
} finally { await browser.close() }
