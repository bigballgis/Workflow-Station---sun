import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { loginViaPortalPassword } from './playwright-login.mjs'

const stage = process.env.VERIFY_STAGE
assert.ok(['before', 'after'].includes(stage))
const processId = '16efcba4-cc50-11f1-89f2-0a31b3ca61b3'
const taskId = '3109514b-cc50-11f1-89f2-0a31b3ca61b3'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()) })
  await loginViaPortalPassword(page)
  const readHistory = async () => (await (await page.request.get(`http://localhost:3000/api/portal/processes/${processId}/history`)).json()).data
  const baseline = await readHistory()
  for (const [entry, route] of [
    ['application', `applications/${processId}`],
    ['task', `tasks/${taskId}?fromList=completed&snapshotTime=2026-10-20T14:37:22.075&snapshotTaskName=Handle+assigned+row&snapshotTaskId=${taskId}&snapshotTaskDefinitionKey=AssignedWork&processInstanceId=${processId}&processDefinitionKey=sub-table-batch-demo`],
  ]) {
    await page.goto(`http://localhost:3000/portal/${route}`)
    await page.getByRole('tab', { name: 'Flow History', exact: true }).click()
    const history = page.locator('.process-history')
    await history.locator('.node-name').first().waitFor()
    const toggle = history.locator('.toggle-history .el-link')
    if (await toggle.count()) await toggle.click()
    await history.getByText('Handle assigned row', { exact: true }).first().waitFor()
    const unknown = await history.locator('.node-name').filter({ hasText: 'Unknown Node' }).count()
    const taskCount = await history.locator('.node-name').filter({ hasText: 'Handle assigned row' }).count()
    const path = fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-20_flow-history-${stage}-${entry}.png`, import.meta.url))
    await history.screenshot({ path })
    if (stage === 'before') assert.equal(unknown, 8)
    else {
      assert.equal(unknown, 0)
      assert.equal(taskCount, 4)
      const endCard = history.locator('.history-card').filter({ has: page.locator('.node-name').getByText('End', { exact: true }) })
      assert.equal(await endCard.count(), 1)
      assert.equal(await endCard.locator('.action-text').count(), 0)
      await endCard.scrollIntoViewIfNeeded()
      await endCard.screenshot({ path: fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-20_flow-history-after-${entry}-end.png`, import.meta.url)) })
    }
    console.log(JSON.stringify({ entry, stage, unknown, taskCount, path }))
  }
  assert.deepEqual(await readHistory(), baseline, 'Presentation must not change stored history')
  assert.deepEqual(errors, [])
} finally { await browser.close() }
