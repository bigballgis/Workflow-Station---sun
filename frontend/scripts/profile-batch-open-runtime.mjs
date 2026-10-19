/** Read-only CPU sampling on an explicitly selected synthetic task. Never Apply or Save. */
import assert from 'node:assert/strict'
import { performance } from 'node:perf_hooks'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId || '', /^[a-f0-9-]{36}$/)
const browser = await chromium.launch({ headless: true })
let stage = 'login'
try {
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  let errors = 0
  let writes = 0
  page.on('pageerror', () => errors++)
  await loginViaPortalPassword(page)
  const response = await page.request.get(`http://localhost:3000/api/portal/tasks/${taskId}`)
  assert.ok(response.ok())
  const task = (await response.json()).data
  assert.match(task.functionUnitName, /^Batch verification /)
  page.on('request', request => {
    if (request.method() === 'POST' && /\/tasks\/[^/]+\/(submit|complete)(?:\?|$)/.test(request.url())) writes++
  })
  await page.goto(`http://localhost:3101/portal/tasks/${taskId}`)
  stage = 'task-ready'
  const open = page.getByRole('button', { name: 'Batch Edit', exact: true })
  await open.waitFor()
  const session = await page.context().newCDPSession(page)
  await session.send('Profiler.enable')
  for (let sample = 0; sample < 3; sample++) {
    stage = `sample-${sample}-click`
    await page.evaluate(() => {
      window.__batchOpenProfile = { clicked: 0, painted: 0 }
      document.addEventListener('click', function listener(event) {
        if (event.target.closest('button')?.textContent.trim() !== 'Batch Edit') return
        document.removeEventListener('click', listener, true)
        window.__batchOpenProfile.clicked = performance.now()
        const check = () => {
          const input = document.querySelector('.el-dialog .el-table__body-wrapper input:not([type="checkbox"])')
          if (!input?.checkVisibility()) { requestAnimationFrame(check); return }
          requestAnimationFrame(() => requestAnimationFrame(() => {
            window.__batchOpenProfile.painted = performance.now()
          }))
        }
        requestAnimationFrame(check)
      }, true)
    })
    await session.send('Profiler.start')
    const started = performance.now()
    await open.click()
    stage = `sample-${sample}-inputs`
    const dialog = page.locator('.el-dialog:visible')
    await dialog.locator('.el-table__body-wrapper input:not([type="checkbox"])').first().waitFor()
    stage = `sample-${sample}-paint`
    await page.waitForFunction(() => window.__batchOpenProfile.painted > 0)
    const wallMs = Math.round(performance.now() - started)
    const { profile } = await session.send('Profiler.stop')
    const times = new Map()
    profile.samples?.forEach((id, index) => times.set(id, (times.get(id) || 0) + profile.timeDeltas[index]))
    const hottest = profile.nodes.map(node => ({
      function: node.callFrame.functionName || '(anonymous)',
      file: node.callFrame.url.split('/').pop(),
      line: node.callFrame.lineNumber + 1,
      column: node.callFrame.columnNumber + 1,
      selfMs: Math.round((times.get(node.id) || 0) / 1000),
    })).filter(node => node.selfMs > 0).sort((a, b) => b.selfMs - a.selfMs).slice(0, 18)
    const timing = await page.evaluate(() => window.__batchOpenProfile)
    console.log(JSON.stringify({ sample, wallMs, clickToPaintMs: Math.round(timing.painted - timing.clicked),
      optionNodes: await page.locator('.el-select-dropdown__item').count(), hottest }))
    await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
  }
  assert.equal(writes, 0)
  assert.equal(errors, 0)
  console.log(JSON.stringify({ writes, errors }))
} catch (error) {
  console.error(error.name === 'AssertionError' ? error.message : `Read-only profiling failed at ${stage} (${error.name})`)
  process.exitCode = 1
} finally {
  await browser.close()
}
