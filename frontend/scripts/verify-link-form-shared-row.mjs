/** Synthetic fixture only: relate an existing A row to B through the normal scoped submit API. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { loginViaPortalPassword } from './playwright-login.mjs'

const taskId = process.env.LINK_FIXTURE_TASK_ID
const bindingId = process.env.LINK_FIXTURE_BINDING_A
assert.match(taskId ?? '', /^[a-f0-9-]{36}$/)
assert.match(bindingId ?? '', /^\d+$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await loginViaPortalPassword(page)
  const api = async (method, path, data) => {
    const response = await page.request.fetch(`http://localhost:3000/api/portal${path}`, { method, data })
    const body = await response.json()
    assert.ok(response.ok() && body.success, `${method} ${path}: ${JSON.stringify(body.error)}`)
    return body.data
  }
  const task = await api('GET', `/tasks/${taskId}`)
  assert.match(task.functionUnitName, /^Link Form verification /)
  const baseline = await api('GET', `/tasks/${taskId}/form-data`)
  assert.ok(baseline.configJson.rule.some(rule => String(rule.props?._bindingId) === bindingId
    && rule.title === 'Binding A'), 'The claimed A binding must be declared on the task form')
  const [store, rows] = Object.entries(baseline.fieldValues.__subTables__).find(([, list]) =>
    Array.isArray(list) && list.some(row => row.line_key?.startsWith('A-LF-')))
  const a = rows.find(row => row.line_key.startsWith('A-LF-'))
  const b = rows.find(row => row.line_key.startsWith('B-LF-'))
  assert.ok(a && b)
  // This is an existing synthetic row, not a new row with another binding's FK.
  a.case_ref_2 = a.case_ref
  await api('POST', `/tasks/${taskId}/submit`, { formData: baseline.fieldValues,
    subTableBindingScopes: [{ bindingId, storeKey: store, rowKeys: [{ line_key: a.line_key }], emptied: false }] })
  const persisted = await api('GET', `/tasks/${taskId}/form-data`)
  assert.equal(persisted.fieldValues.__subTables__[store].find(row => row.line_key === a.line_key).case_ref_2, a.case_ref)
  await page.goto(`http://localhost:3000/portal/tasks/${taskId}`)
  const grid = side => page.locator('.sub-table-field').filter({ has: page.locator('.sub-table-header > .title')
    .filter({ hasText: new RegExp(`^Binding ${side}`) }) }).first()
  const modal = page.locator('.link-form-modal-panel:visible')
  const open = async side => {
    await grid(side).locator('.el-table__body-wrapper tbody tr').filter({ hasText: a.line_key })
      .getByText('Other details', { exact: true }).click()
    await modal.waitFor()
    return modal.locator('.el-form-item').filter({ hasText: 'Caption' }).locator('input')
  }
  const caption = await open('A')
  assert.equal(await caption.inputValue(), a.caption)
  const nextCaption = `SHARED VERIFIED ${Date.now()}`
  await caption.fill(nextCaption)
  const submit = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
  await modal.getByRole('button', { name: 'Save', exact: true }).click()
  assert.equal((await (await submit).json()).success, true)
  const after = (await api('GET', `/tasks/${taskId}/form-data`)).fieldValues.__subTables__[store]
  assert.equal(after.find(row => row.line_key === a.line_key).caption, nextCaption)
  const business = row => Object.fromEntries(Object.entries(row).filter(([key]) => key !== '_wsRowVersion'))
  assert.deepEqual(business(after.find(row => row.line_key === b.line_key)), business(b))
  assert.equal(after.length, rows.length, 'Shared update must not create a nested/duplicate row')
  await page.reload()
  assert.equal(await (await open('B')).inputValue(), nextCaption)
  const screenshot = fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-20_link-form-shared-${taskId.slice(0, 8)}.png`, import.meta.url))
  await modal.screenshot({ path: screenshot })
  await modal.getByRole('button', { name: 'Cancel', exact: true }).click()
  assert.deepEqual((await api('GET', `/tasks/${taskId}/form-data`)).fieldValues.__subTables__[store], after)
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ result: 'PASS', taskId, nextCaption, sharedSaveReload: 'PASS', unrelatedBUnchanged: 'PASS', errors, screenshot }))
} finally { await browser.close() }
