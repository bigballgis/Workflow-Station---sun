/** Exercise deployed Portal assets with isolated form-content fixtures; no backend writes. */
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const origin = process.env.ORIGIN ?? 'http://localhost:3000'
const phase = process.argv.includes('--before') ? 'before' : 'after'
const date = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Hong_Kong' })
const output = resolve(import.meta.dirname, '../user-portal/verification-screenshots')
mkdirSync(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
const errors = []
page.on('pageerror', error => errors.push(error.message))
page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })

try {
  await loginViaPortalPassword(page)
  const response = await page.request.get(`${origin}/api/portal/processes/definitions`)
  assert.equal(response.ok(), true)
  const body = await response.json()
  const definitions = body.data ?? body
  const definition = definitions.find(item => item.description?.includes('Isolated synthetic acceptance fixture'))
  assert.ok(definition, 'An isolated startable process fixture is required')
  const options = [{ label: 'First', value: 1 }, { label: 'Second', value: 2 }]
  await page.route('**/api/portal/processes/function-units/*/content?*', async route => {
    const live = await route.fetch()
    assert.equal(live.ok(), true)
    const content = await live.json()
    for (const form of content.data.forms) {
      form.data = JSON.stringify({
        rule: [
          { type: 'select', field: 'verify_multi', title: 'Verification multiple Select', props: { multiple: true }, value: [1], options },
          { type: 'select', field: 'verify_single', title: 'Verification single Select', props: { multiple: false }, value: 1, options },
          { type: 'select', field: 'verify_empty', title: 'Verification empty multiple Select', props: { multiple: true }, options },
        ],
        options: { form: { labelPosition: 'top' } },
      })
      form.tableBindings = []
    }
    content.data.dataTables = []
    await route.fulfill({ response: live, json: content })
  })
  await page.goto(`${origin}/portal/processes/start/${definition.key}`)
  const field = label => page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: label }) }).first()
  const option = async (item, label) => {
    const listId = await item.getByRole('combobox').getAttribute('aria-controls')
    assert.ok(listId)
    return page.locator(`[id="${listId}"]`).getByRole('option', { name: label, exact: true })
  }
  const multi = field('Verification multiple Select')
  await multi.waitFor()
  await multi.locator('.el-select').click()
  await (await option(multi, 'Second')).click()
  const shot = resolve(output, `${date}_select-multiple-${phase}.png`)
  await page.screenshot({ path: shot, fullPage: true })
  console.log(`Screenshot: ${shot}`)
  assert.equal(await multi.locator('.el-tag').count(), 2, 'Multiple Select retains both choices')
  await page.keyboard.press('Escape')
  await multi.locator('.el-tag__close').first().click()
  assert.equal(await multi.locator('.el-tag').count(), 1)
  await multi.hover()
  await multi.locator('.el-select__clear').click()
  assert.equal(await multi.locator('.el-tag').count(), 0)

  const single = field('Verification single Select')
  await single.locator('.el-select').click()
  await (await option(single, 'Second')).click()
  assert.equal(await single.locator('.el-tag').count(), 0)
  assert.match(await single.innerText(), /Second/)

  const empty = field('Verification empty multiple Select')
  await empty.locator('.el-select').click()
  await (await option(empty, 'First')).click()
  await (await option(empty, 'Second')).click()
  assert.equal(await empty.locator('.el-tag').count(), 2)
  await page.keyboard.press('Escape')
  await page.reload()
  await multi.locator('.el-tag').first().waitFor()
  assert.equal(await multi.locator('.el-tag').count(), 1)
  assert.match(await multi.innerText(), /First/)
  assert.deepEqual(errors, [], 'No browser errors')
  console.log('PASS: multiple selection, remove, clear, empty initial value, single selection, array default after reload; no browser errors. Content response is a browser fixture; database persistence is not exercised.')
} finally {
  await browser.close()
}
