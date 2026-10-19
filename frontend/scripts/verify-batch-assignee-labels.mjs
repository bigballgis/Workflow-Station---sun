/** Read-only verification: open/cancel Batch Edit; never Apply, Save or complete tasks. */
import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'

const ids = (process.env.FIXTURE_TASK_IDS || '').split(',').filter(Boolean)
assert.ok(ids.length)
ids.forEach(id => assert.match(id, /^[a-f0-9-]{36}$/))
const phase = process.env.VERIFY_PHASE || 'after'
assert.ok(['before', 'after'].includes(phase))
const origin = 'http://localhost:3000'
const output = new URL('../user-portal/verification-screenshots/', import.meta.url)
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
let failure
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await loginViaAdminPassword(page)
  await loginViaPortalPassword(page)
  for (const [sample, id] of ids.entries()) {
    const response = await page.request.get(`${origin}/api/portal/tasks/${id}/form-data`)
    const body = await response.json()
    assert.equal(body.success, true)
    const form = body.data
    const control = Object.values(form.subTableBatchCapabilities).find(value => value.capabilities.allowBatchAssign)
    assert.ok(control)
    const rows = form.fieldValues.__subTables__[`dw:${control.rowDesign.binding.tableName}`]
    const names = new Map()
    for (const row of rows) {
      const userId = row[control.assignmentConfig.assigneeField]
      if (!userId || names.has(userId)) continue
      const userResponse = await page.request.get(`${origin}/api/v1/admin/users/${encodeURIComponent(userId)}`)
      assert.equal(userResponse.ok(), true)
      const raw = await userResponse.json()
      const user = raw.data || raw
      names.set(userId, user.displayName || user.fullName || user.username)
    }
    assert.ok(names.size, 'Sample must have an existing saved person assignment')
    await page.goto(`${origin}/portal/tasks/${id}`)
    if (process.env.VERIFY_GENERAL === '1') {
      const table = page.locator('.sub-table-field').first()
      let generalFailure
      try {
        await table.locator('.el-table__body-wrapper tr').first().waitFor()
        for (const [userId, name] of names) {
          await table.getByText(name, { exact: true }).first().waitFor({ state: 'visible', timeout: 8000 })
          assert.equal(await table.getByText(userId, { exact: true }).count(), 0, 'General must not retain a raw assignee ID cell')
        }
      } catch (error) { generalFailure = error }
      await table.screenshot({ path: fileURLToPath(new URL(`2026-10-19_general-assignee-labels-${phase}-${sample + 1}.png`, output)) })
      if (generalFailure) throw generalFailure
    }
    for (let cycle = 0; cycle < 2; cycle++) {
      await page.getByRole('button', { name: 'Batch Edit', exact: true }).first().click()
      const dialog = page.locator('.el-dialog:visible')
      const editor = dialog.locator('.batch-assignment-editor')
      const personRows = editor.locator('.el-table__body-wrapper tr')
      await personRows.first().waitFor()
      try {
        for (const [index, row] of rows.slice(0, 20).entries()) {
          const userId = row[control.assignmentConfig.assigneeField]
          if (!userId) continue
          await personRows.nth(index).getByText(names.get(userId), { exact: true }).waitFor({ state: 'visible', timeout: 8000 })
        }
      } catch (error) { failure = error }
      await dialog.screenshot({ path: fileURLToPath(new URL(`2026-10-19_batch-assignee-labels-${phase}-${sample + 1}-${cycle + 1}.png`, output)) })
      await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
      if (failure) throw failure
    }
    const reread = await (await page.request.get(`${origin}/api/portal/tasks/${id}/form-data`)).json()
    assert.deepEqual(reread.data.fieldValues, form.fieldValues, 'Label hydration/cancel must not change saved rows or user IDs')
  }
  assert.deepEqual(errors, [])
  console.log(`PASS: ${ids.length} samples, reopen twice, display names and unchanged saved IDs; pageerror=0`)
} finally { await browser.close() }
