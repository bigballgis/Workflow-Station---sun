/** Actual claim-banner SFC in an owned Docker preview, with synthetic data and no business API calls. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync, existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const app = resolve(root, 'frontend/user-portal')
const generated = resolve(app, 'tmp/assignment-claim-preview')
const dist = resolve(root, 'tmp/assignment-claim-preview-dist')
const screenshots = resolve(app, 'verification-screenshots')
const marker = resolve(generated, '.owned-preview')
const docker = args => execFileSync('docker', args, { encoding: 'utf8', windowsHide: true }).trim()
const image = 'dev-user-portal-frontend'
if (existsSync(generated)) assert.equal(readFileSync(marker, 'utf8'), 'assignment-claim-preview')
mkdirSync(generated, { recursive: true }); mkdirSync(screenshots, { recursive: true })
writeFileSync(marker, 'assignment-claim-preview')
writeFileSync(resolve(generated, 'main.mjs'), `
import { createApp, h, ref } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '@fontsource-variable/inter'
import '@/styles/index.scss'
import i18n from '@/i18n'
import TaskClaimBanner from '@/components/tasks/TaskClaimBanner.vue'
import TaskActionBar from '@/components/tasks/TaskActionBar.vue'
import { isAssignmentWriteBlocked } from '@/utils/assignmentWriteAccess'
const states = ['CURRENT', 'RECONCILING', 'SYNC_PENDING', 'BLOCKED_NO_ELIGIBLE_USER',
  'ACTOR_INACTIVE', 'NOT_ACTIVE', 'CONTEXT_UNAVAILABLE', 'DEPENDENCY_UNAVAILABLE', 'held', 'legacy']
const count = ref(0)
const mode = ref('claim')
const writeStates = ['ASSIGNMENT_EXCLUDED', 'ASSIGNMENT_STATE_CHANGED', 'ASSIGNMENT_SYNC_PENDING',
  'ASSIGNMENT_DEPENDENCY_UNAVAILABLE', 'CLAIM_REQUIRED', 'CURRENT', 'retained', 'checking', 'eligible']
const draft = ref('Unsent local draft — synthetic verification data')
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }] })
window.__claimPreview = { set(locale, next = 'claim') { i18n.global.locale.value = locale; mode.value = next }, count() { return count.value } }
createApp({ setup() { return () => h('main', { class: 'preview-page' }, [
  h('h1', 'Claim eligibility — synthetic visual fixture'),
  ...(mode.value === 'write' ? writeStates.map(reason => {
    const allowed = ['eligible', 'retained', 'checking'].includes(reason)
    const task = { claimPoolTask: false, assignee: 'actor', assignmentLocalDraftRetained: reason === 'retained',
      assignmentWriteCheckPending: reason === 'checking', assignmentWriteAccess: { canWrite: allowed,
      evaluation: { contractVersion: 1, tracked: true, exclusionAllowsWrite: allowed, reason: allowed ? 'CURRENT' : reason, stamp: null } } }
    return h('article', { 'data-write': reason }, [h('h2', reason),
      h(TaskClaimBanner, { task, submitting: false }),
      h('input', { value: draft.value, disabled: isAssignmentWriteBlocked(task), 'aria-label': 'Synthetic local draft' }),
      h(TaskActionBar, { isCompletedTask: false, claimLocked: isAssignmentWriteBlocked(task), canDelegate: true,
        actions: [{ actionId: 'save', actionType: 'SAVE', actionName: 'Save', configJson: '{}' }],
        getIconComponent: () => null, getActionLabel: () => 'Save' })])
  }) : states.map(state => h('article', { 'data-state': state }, [h('h2', state),
    h(TaskClaimBanner, { submitting: false, task: { claimPoolTask: true, claimable: state === 'legacy',
      claimedByCurrentUser: state === 'held', assignee: state === 'held' ? 'actor' : null,
      assignmentClaimAccess: { taskId: state, tracked: state !== 'legacy',
        state: state === 'held' ? 'DEPENDENCY_UNAVAILABLE' : state === 'legacy' ? 'LEGACY' : state,
        claim: { exclusionAllowsClaim: state === 'legacy', poolSource: 'RESOLVER', holderSource: null }, stamp: null } },
      onClaim: () => count.value++, onUnclaim: () => count.value++ })])) )]) } }).use(ElementPlus).use(i18n).use(router).mount('#app')
`)
writeFileSync(resolve(generated, 'index.html'), '<!doctype html><html><head><meta charset="UTF-8"><link rel="icon" href="data:,"><style>body{margin:0;background:#f5f7fa}.preview-page{padding:24px;max-width:1000px;margin:auto}h1{font-size:20px}h2{font-size:14px;color:#64748b}article{margin:24px 0}</style></head><body><div id="app"></div><script type="module" src="./main.mjs"></script></body></html>')
const vite = createRequire(resolve(app, 'package.json'))('vite')
await vite.build({ root: app, configFile: resolve(app, 'vite.config.ts'), base: '/portal/',
  build: { outDir: dist, emptyOutDir: false, rollupOptions: { input: resolve(generated, 'index.html') } } })
let container, browser
const paths = [], errors = []
try {
  container = docker(['run', '-d', '--name', `assignment-claim-ui-${Date.now()}`, '-p', '127.0.0.1::80',
    '-e', 'KONG_PROXY_URL=http://127.0.0.1:9', '--mount', `type=bind,src=${dist},dst=/usr/share/nginx/html/portal,readonly`, image])
  assert.match(container, /^[a-f0-9]{64}$/)
  const port = docker(['port', container, '80/tcp']).split(':').at(-1)
  browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1100, height: 900 } })
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/**', route => { errors.push('Unexpected business API'); return route.abort() })
  await page.goto(`http://127.0.0.1:${port}/portal/tmp/assignment-claim-preview/index.html`, { waitUntil: 'networkidle' })
  await page.evaluate(() => document.fonts.ready)
  for (const locale of ['en', 'zh-CN', 'zh-TW']) {
    await page.evaluate(locale => window.__claimPreview.set(locale), locale)
    await page.locator('[data-state="CURRENT"] .el-alert').waitFor()
    assert.equal(await page.locator('article:not([data-state="held"]):not([data-state="legacy"]) button').count(), 0)
    assert.equal(await page.locator('[data-state="held"] [data-test="task-unclaim-btn"]').count(), 1)
    assert.equal(await page.locator('[data-state="legacy"] [data-test="task-claim-btn"]').count(), 1)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
    assert.doesNotMatch(await page.locator('main').innerText(), /task\.claim[A-Z]/)
    const path = resolve(screenshots, `2026-10-29_assignment-claim-${locale}.png`)
    await page.screenshot({ path, fullPage: true }); paths.push(path)
    await page.evaluate(locale => window.__claimPreview.set(locale, 'write'), locale)
    const blocked = page.locator('article[data-write]:not([data-write="eligible"])')
    assert.equal(await blocked.count(), 8)
    assert.equal(await blocked.locator('.action-section').count(), 0)
    for (const input of await blocked.locator('input').all()) {
      assert.equal(await input.isDisabled(), true)
      assert.equal(await input.inputValue(), 'Unsent local draft — synthetic verification data')
    }
    assert.equal(await page.locator('[data-write="eligible"] input').isDisabled(), false)
    assert.equal(await page.locator('[data-write="eligible"] .action-section').count(), 1)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
    assert.doesNotMatch(await page.locator('main').innerText(), /task\.assignmentWrite[A-Z]/)
    const writePath = resolve(screenshots, `2026-10-29_assignment-write-${locale}.png`)
    await page.screenshot({ path: writePath, fullPage: true }); paths.push(writePath)
    const retained = page.locator('[data-write="retained"]')
    await retained.locator('[data-test="assignment-reload-server-form"]').click()
    const confirmation = page.locator('.el-message-box:visible')
    await confirmation.waitFor()
    await page.waitForFunction(() => {
      const dialog = document.querySelector('.el-message-box')
      const overlay = dialog?.closest('.el-overlay')
      return dialog && overlay && getComputedStyle(overlay).opacity === '1'
        && !overlay.classList.contains('msgbox-fade-enter-active')
    })
    assert.doesNotMatch(await confirmation.innerText(), /task\.assignment|common\./)
    assert.equal(await confirmation.evaluate(element => element.scrollWidth > element.clientWidth), false)
    const confirmationPath = resolve(screenshots, `2026-10-29_assignment-draft-confirm-${locale}.png`)
    await page.screenshot({ path: confirmationPath, animations: 'disabled' }); paths.push(confirmationPath)
    await confirmation.locator('.el-message-box__btns button').first().click()
    await confirmation.waitFor({ state: 'hidden' })
    assert.equal(await retained.locator('input').inputValue(), 'Unsent local draft — synthetic verification data')
  }
  assert.deepEqual(errors, [])
  writeFileSync(resolve(root, 'tmp/assignment-claim-ui-result.json'), JSON.stringify({ scope: 'actual SFC, isolated Docker synthetic fixture; not business end-to-end', image, paths }, null, 2))
  console.log(JSON.stringify({ passed: true, paths }, null, 2))
} finally {
  await browser?.close()
  if (container && /^[a-f0-9]{64}$/.test(container)) docker(['rm', '-f', container])
}
