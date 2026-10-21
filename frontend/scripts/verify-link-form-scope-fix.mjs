/** Pinned synthetic task only; checks wrong-row rejection before any write. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { loginViaPortalPassword } from './playwright-login.mjs'

const taskId = process.env.LINK_FIXTURE_TASK_ID
assert.match(taskId ?? '', /^[a-f0-9-]{36}$/)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await loginViaPortalPassword(page)
  const get = async path => {
    const response = await page.request.get(`http://localhost:3000/api/portal${path}`)
    const body = await response.json()
    assert.equal(response.ok() && body.success, true)
    return body.data
  }
  const task = await get(`/tasks/${taskId}`)
  assert.match(task.functionUnitName, /^Link Form verification /)
  const before = await get(`/tasks/${taskId}/form-data`)
  const [store, rows] = Object.entries(before.fieldValues.__subTables__).find(([, list]) =>
    Array.isArray(list) && list.some(row => row.line_key?.startsWith('A-LF-')))
  const a = rows.find(row => row.line_key.startsWith('A-LF-'))
  const b = rows.find(row => row.line_key.startsWith('B-LF-'))
  assert.ok(a && b)
  const grid = side => page.locator('.sub-table-field').filter({ has: page.locator('.sub-table-header > .title')
    .filter({ hasText: new RegExp(`^Binding ${side}`) }) }).first()
  const modal = page.locator('.link-form-modal-panel:visible')
  const shot = async tag => {
    const path = fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-20_link-form-scope-${taskId.slice(0, 8)}-${tag}.png`, import.meta.url))
    await page.screenshot({ path, fullPage: true })
    return path
  }
  const checkCross = async (side, tag) => {
    await grid(side).locator('.el-table__body-wrapper').getByText('Other details', { exact: true }).click()
    try {
      await page.getByText('No unique matching record was found in the selected binding. No changes have been applied.', { exact: true }).last().waitFor({ timeout: 5000 })
    } catch (error) {
      await shot(`${tag}-failed`)
      throw error
    }
    assert.equal(await modal.count(), 0, 'An unrelated row must not open')
    await page.locator('.el-message').last().screenshot({ path: fileURLToPath(new URL(
      `../user-portal/verification-screenshots/2026-10-20_link-form-scope-${taskId.slice(0, 8)}-${tag}-notice.png`, import.meta.url)) })
    return shot(tag)
  }
  await page.goto(`http://localhost:3000/portal/tasks/${taskId}`)
  const shots = [await checkCross('A', 'a-to-b'), await checkCross('B', 'b-to-a')]
  assert.deepEqual((await get(`/tasks/${taskId}/form-data`)).fieldValues.__subTables__[store], rows)
  await grid('A').locator('.el-table__body-wrapper').getByText('Self details', { exact: true }).click()
  await modal.waitFor()
  const caption = () => modal.locator('.el-form-item').filter({ hasText: 'Caption' }).locator('input')
  assert.equal(await caption().inputValue(), a.caption)
  const nextCaption = `SCOPE VERIFIED ${Date.now()}`
  await caption().fill(nextCaption)
  const submit = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
  await modal.getByRole('button', { name: 'Save', exact: true }).click()
  assert.equal((await (await submit).json()).success, true)
  const after = (await get(`/tasks/${taskId}/form-data`)).fieldValues.__subTables__[store]
  const business = row => Object.fromEntries(Object.entries(row).filter(([key]) => key !== '_wsRowVersion'))
  assert.equal(after.find(row => row.line_key === a.line_key).caption, nextCaption)
  assert.deepEqual(business(after.find(row => row.line_key === b.line_key)), business(b))
  await page.reload()
  await grid('A').locator('.el-table__body-wrapper').getByText('Self details', { exact: true }).click()
  await modal.waitFor()
  assert.equal(await caption().inputValue(), nextCaption)
  shots.push(await shot('self-reload'))
  await modal.getByRole('button', { name: 'Cancel', exact: true }).click()
  await page.goto(`http://localhost:3000/portal/applications/${task.processInstanceId}`)
  shots.push(await checkCross('A', 'readonly-a-to-b'), await checkCross('B', 'readonly-b-to-a'))
  await grid('A').locator('.el-table__body-wrapper').getByText('Self details', { exact: true }).click()
  await modal.waitFor()
  assert.equal(await caption().inputValue(), nextCaption)
  assert.equal(await modal.getByRole('button', { name: 'Save', exact: true }).count(), 0)
  assert.deepEqual((await get(`/tasks/${taskId}/form-data`)).fieldValues.__subTables__[store], after)
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ result: 'PASS', taskId, processId: task.processInstanceId, nextCaption,
    crossBothDirections: 'rejected without writes', selfSaveReload: 'PASS', untouchedB: 'PASS', readonly: 'PASS', errors, shots }))
} finally { await browser.close() }
