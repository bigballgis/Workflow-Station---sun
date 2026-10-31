/** Visual verification of the actual approval SFC in an owned Docker preview; no business API or mail is called. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync, existsSync, readFileSync } from 'node:fs'
import { dirname, resolve, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const app = resolve(root, 'frontend/user-portal')
const generated = resolve(app, 'tmp/assignment-completion-preview')
const dist = resolve(root, 'tmp/assignment-completion-preview-dist')
const screenshots = resolve(app, 'verification-screenshots')
const marker = resolve(generated, '.owned-preview')
const docker = args => execFileSync('docker', args, { encoding: 'utf8', windowsHide: true }).trim()
const image = 'dev-user-portal-frontend'

const entry = `
import { createApp, h, ref, reactive, computed } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '@fontsource-variable/inter'
import '@/styles/index.scss'
import i18n from '@/i18n'
import ApproveDialog from '@/components/tasks/ApproveDialog.vue'
import BatchCompletionStatus from '@/components/tasks/BatchCompletionStatus.vue'
const state = ref('PENDING_SYNC'), visible = ref(true), checked = ref(0), confirmed = ref(0)
const showBatch = ref(false), batchStates = ref(['PENDING_SYNC', 'SYNC_CONFLICT', 'DELIVERY_UNKNOWN'])
const batchRows = computed(() => batchStates.value.map((state, index) => ({ taskId: 'synthetic-task-' + index,
  operationId: '00000000-0000-4000-8000-00000000000' + index, state })))
const form = reactive({ comment: 'Original entered comment — preserved across status checks.' })
const completion = computed(() => ({ operationId: '00000000-0000-4000-8000-000000000001', state: state.value }))
window.__completionPreview = {
  set(locale, next, open = true) { i18n.global.locale.value = locale; state.value = next; visible.value = open; showBatch.value = false },
  batch(locale) { i18n.global.locale.value = locale; visible.value = false; showBatch.value = true;
    batchStates.value = ['PENDING_SYNC', 'SYNC_CONFLICT', 'DELIVERY_UNKNOWN'] },
  inspect() { return { comment: form.comment, checked: checked.value, confirmed: confirmed.value } },
}
const app = createApp({ setup() { return () => h('main', { class: 'preview-page' }, [
  h('h1', 'Approval completion — synthetic visual fixture'),
  showBatch.value ? h(BatchCompletionStatus, { rows: batchRows.value,
    onRefresh: task => { const index = batchRows.value.findIndex(row => row.taskId === task); batchStates.value[index] = 'READY' },
  }) : h(ApproveDialog, { modelValue: visible.value, 'onUpdate:modelValue': value => { visible.value = value },
    title: ({ en: 'Approve', 'zh-CN': '审批', 'zh-TW': '審批' })[i18n.global.locale.value],
    formData: form, submitting: false, completion: completion.value,
    'onRefresh-completion': () => { checked.value++ }, onConfirm: () => { confirmed.value++ },
  }),
]) } })
app.use(ElementPlus, { zIndex: 3000 }).use(i18n)
  .use(createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }] })).mount('#app')
`

async function buildPreview() {
  if (existsSync(generated)) assert.equal(readFileSync(marker, 'utf8'), 'assignment-completion-preview')
  mkdirSync(generated, { recursive: true }); mkdirSync(screenshots, { recursive: true })
  writeFileSync(marker, 'assignment-completion-preview')
  writeFileSync(resolve(generated, 'main.mjs'), entry)
  writeFileSync(resolve(generated, 'index.html'), '<!doctype html><html><head><meta charset="UTF-8"><link rel="icon" href="data:,"><style>body{margin:0;background:#f5f7fa}.preview-page{padding:24px}h1{font-size:20px}</style></head><body><div id="app"></div><script type="module" src="./main.mjs"></script></body></html>')
  assert.ok(!relative(root, dist).startsWith('..'), 'Preview output must stay in the workspace')
  const vite = createRequire(resolve(app, 'package.json'))('vite')
  await vite.build({ root: app, configFile: resolve(app, 'vite.config.ts'), base: '/portal/',
    build: { outDir: dist, emptyOutDir: false, rollupOptions: { input: resolve(generated, 'index.html') } } })
}

async function checkDialog(page, readonly) {
  const dialog = page.locator('.el-dialog:visible')
  await dialog.waitFor()
  const confirm = dialog.locator('button.el-button--primary')
  assert.equal(await confirm.isDisabled(), readonly)
  assert.equal(await dialog.locator('textarea').isDisabled(), readonly)
  const original = await page.evaluate(() => window.__completionPreview.inspect())
  await dialog.locator('.completion-status button').click()
  const current = await page.evaluate(() => window.__completionPreview.inspect())
  assert.equal(current.checked, original.checked + 1)
  assert.equal(current.comment, original.comment)
  assert.equal(current.confirmed, 0)
  const layout = await dialog.evaluate(element => {
    const label = element.querySelector('.el-form-item__label'), input = element.querySelector('textarea')
    const l = label.getBoundingClientRect(), i = input.getBoundingClientRect()
    return { overlaps: l.right > i.left + 1, wraps: l.height > parseFloat(getComputedStyle(label).lineHeight) + 2,
      overflow: element.scrollWidth > element.clientWidth || document.documentElement.scrollWidth > innerWidth }
  })
  assert.deepEqual(layout, { overlaps: false, wraps: false, overflow: false })
}

await buildPreview()
let container, browser
const paths = [], errors = []
try {
  container = docker(['run', '-d', '--name', `assignment-completion-ui-${Date.now()}`, '-p', '127.0.0.1::80',
    '-e', 'KONG_PROXY_URL=http://127.0.0.1:9', '--mount', `type=bind,src=${dist},dst=/usr/share/nginx/html/portal,readonly`, image])
  assert.match(container, /^[a-f0-9]{64}$/)
  const port = docker(['port', container, '80/tcp']).split(':').at(-1)
  browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/**', route => { errors.push(`Unexpected business API: ${route.request().url()}`); return route.abort() })
  await page.goto(`http://127.0.0.1:${port}/portal/tmp/assignment-completion-preview/index.html`, { waitUntil: 'networkidle' })
  await page.evaluate(() => document.fonts.ready)
  for (const locale of ['en', 'zh-CN', 'zh-TW']) for (const state of ['PENDING_SYNC', 'SYNC_CONFLICT', 'DELIVERY_UNKNOWN']) {
    await page.evaluate(({ locale, state }) => window.__completionPreview.set(locale, state), { locale, state })
    await page.waitForTimeout(350)
    await checkDialog(page, true)
    const path = resolve(screenshots, `2026-10-28_assignment-completion-${locale}-${state.toLowerCase()}.png`)
    await page.screenshot({ path }); paths.push(path)
  }
  await page.evaluate(() => window.__completionPreview.set('en', 'NOT_ACCEPTED'))
  await page.waitForTimeout(350); await checkDialog(page, false)
  await page.evaluate(() => window.__completionPreview.set('zh-CN', 'PENDING_SYNC', false))
  await page.waitForTimeout(400)
  await page.locator('.completion-status:visible').waitFor()
  const closed = resolve(screenshots, '2026-10-28_assignment-completion-closed-dialog-status.png')
  await page.screenshot({ path: closed }); paths.push(closed)
  for (const locale of ['en', 'zh-CN', 'zh-TW']) {
    await page.evaluate(locale => window.__completionPreview.batch(locale), locale)
    await page.waitForTimeout(350)
    const cards = page.locator('.batch-completion-status article')
    assert.equal(await cards.count(), 3)
    assert.equal(await cards.locator('a').count(), 3)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
    const path = resolve(screenshots, '2026-10-28_assignment-batch-completion-' + locale + '.png')
    await page.screenshot({ path, fullPage: true }); paths.push(path)
    await cards.first().locator('button').click()
    await page.waitForTimeout(100)
    assert.equal(await cards.first().locator('.el-alert--success').count(), 1)
    assert.equal(await cards.nth(1).locator('.el-alert--warning').count(), 1)
  }
  assert.deepEqual(errors, [])
  writeFileSync(resolve(root, 'tmp/assignment-completion-ui-result.json'), JSON.stringify({ scope: 'actual SFC, isolated Docker visual fixture; no business end-to-end claim', image, paths }, null, 2))
  console.log(JSON.stringify({ passed: true, screenshots: paths.length, paths }, null, 2))
} finally {
  await browser?.close()
  if (container && /^[a-f0-9]{64}$/.test(container)) docker(['rm', '-f', container])
}
