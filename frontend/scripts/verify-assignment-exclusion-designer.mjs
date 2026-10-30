/** Actual designer SFC + bpmn-js in an owned Docker preview. Catalog replies are synthetic; no business API is used. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync, existsSync, readFileSync } from 'node:fs'
import { dirname, resolve, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const app = resolve(root, 'frontend/developer-workstation')
const generated = resolve(app, 'tmp/assignment-exclusion-preview')
const dist = resolve(root, 'tmp/assignment-exclusion-designer-dist')
const screenshots = resolve(app, 'verification-screenshots')
const marker = resolve(generated, '.owned-preview')
const docker = args => execFileSync('docker', args, { encoding: 'utf8', windowsHide: true }).trim()
const image = 'dev-developer-workstation-frontend'

const xml = `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
 xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" targetNamespace="urn:synthetic">
 <bpmn:process id="verification_process" isExecutable="true"><bpmn:startEvent id="start"/><bpmn:userTask id="review" name="Verification review"/>
 <bpmn:userTask id="audit" name="Verification audit"/><bpmn:endEvent id="end"/>
 <bpmn:sequenceFlow id="a" sourceRef="start" targetRef="review"/><bpmn:sequenceFlow id="b" sourceRef="review" targetRef="audit"/>
 <bpmn:sequenceFlow id="c" sourceRef="audit" targetRef="end"/></bpmn:process>
 <bpmndi:BPMNDiagram id="diagram"><bpmndi:BPMNPlane id="plane" bpmnElement="verification_process">
 <bpmndi:BPMNShape id="s_start" bpmnElement="start"><dc:Bounds x="80" y="120" width="36" height="36"/></bpmndi:BPMNShape>
 <bpmndi:BPMNShape id="s_review" bpmnElement="review"><dc:Bounds x="180" y="100" width="130" height="80"/></bpmndi:BPMNShape>
 <bpmndi:BPMNShape id="s_audit" bpmnElement="audit"><dc:Bounds x="380" y="100" width="130" height="80"/></bpmndi:BPMNShape>
 <bpmndi:BPMNShape id="s_end" bpmnElement="end"><dc:Bounds x="580" y="120" width="36" height="36"/></bpmndi:BPMNShape>
 <bpmndi:BPMNEdge id="e_a" bpmnElement="a"><di:waypoint x="116" y="138"/><di:waypoint x="180" y="140"/></bpmndi:BPMNEdge>
 <bpmndi:BPMNEdge id="e_b" bpmnElement="b"><di:waypoint x="310" y="140"/><di:waypoint x="380" y="140"/></bpmndi:BPMNEdge>
 <bpmndi:BPMNEdge id="e_c" bpmnElement="c"><di:waypoint x="510" y="140"/><di:waypoint x="580" y="138"/></bpmndi:BPMNEdge>
 </bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`

const entry = `
import { createApp, h, shallowRef, ref, markRaw } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '@fontsource-variable/inter'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import '@/styles/index.scss'
import Modeler from 'bpmn-js/lib/Modeler'
import i18n from '@/i18n'
import Panel from '@/components/designer/properties/UserTaskAssignmentExclusion.vue'
import { workflowPlatformModdleDescriptor, bpmnIoCustomModdleDescriptor, flowableModdleDescriptor } from '@/utils/customModdle'
import { writeAssignmentExclusion, readAssignmentExclusion } from '@/utils/assignmentExclusionBpmn'
async function main() {
  const modeler = markRaw(new Modeler({ container: '#diagram', moddleExtensions: {
    custom: workflowPlatformModdleDescriptor, custom_1: bpmnIoCustomModdleDescriptor, flowable: flowableModdleDescriptor } }))
  await modeler.importXML(${JSON.stringify(xml)})
  modeler.get('canvas').zoom('fit-viewport')
  const element = shallowRef(modeler.get('elementRegistry').get('review')), disabled = ref(false)
  const base = () => ({ schemaVersion: 1, excludeInitiator: true, excludePreviousHandlers: true, excludeSelectedNodeHandlers: true,
    selectedNodeKeys: ['audit'], backup: { enabled: true, mode: 'BU_ROLE', buSource: 'FIXED_BU', businessUnitCode: 'VERIFY_BU', roleCodes: ['VERIFY_ROLE'], userRefs: [] } })
  function set(locale, scenario) {
    i18n.global.locale.value = locale; disabled.value = scenario === 'readonly'
    const draft = base()
    if (scenario === 'users') { draft.backup.mode = 'USERS'; draft.backup.userRefs = ['verify_login'] }
    writeAssignmentExclusion(modeler, element.value, draft)
    if (scenario === 'invalid') {
      const moddle = modeler.get('moddle')
      const properties = moddle.create('custom:Properties', { property: [moddle.create('custom:Property', { name: 'assignmentExclusion', value: '{synthetic-invalid' })] })
      modeler.get('modeling').updateProperties(element.value, { extensionElements: moddle.create('bpmn:ExtensionElements', { values: [properties] }) })
    }
  }
  const app = createApp({ setup() { return () => h(Panel, { modeler, element: element.value, disabled: disabled.value }) } })
  app.use(ElementPlus).use(i18n).mount('#panel')
  window.__assignmentDesignerPreview = { set, raw: () => readAssignmentExclusion(element.value),
    undo: () => modeler.get('commandStack').undo(), redo: () => modeler.get('commandStack').redo(),
    xml: async () => (await modeler.saveXML({ format: true })).xml }
  set('en', 'fixed')
}
main().catch(error => { window.__assignmentDesignerError = String(error); throw error })
`

if (existsSync(generated)) assert.equal(readFileSync(marker, 'utf8'), 'assignment-exclusion-designer')
mkdirSync(generated, { recursive: true }); mkdirSync(screenshots, { recursive: true })
writeFileSync(marker, 'assignment-exclusion-designer')
writeFileSync(resolve(generated, 'main.mjs'), entry)
writeFileSync(resolve(generated, 'index.html'), `<!doctype html><html><head><meta charset="UTF-8"><link rel="icon" href="data:,"><style>
body{margin:0;background:#f5f7fa}main{padding:24px;display:grid;grid-template-columns:minmax(0,1fr) 400px;gap:20px}
h1{grid-column:1/-1;font-size:20px;margin:0}#diagram{height:600px;background:white;border:1px solid #ddd}
#panel{padding:16px;background:white;border:1px solid #ddd;min-width:0}*{box-sizing:border-box}
</style></head><body><main><h1>Assignment exclusions — synthetic designer fixture</h1><div id="diagram"></div><div id="panel"></div></main><script type="module" src="./main.mjs"></script></body></html>`)
assert.ok(!relative(root, dist).startsWith('..'))
const vite = createRequire(resolve(app, 'package.json'))('vite')
await vite.build({ root: app, configFile: resolve(app, 'vite.config.ts'), base: '/dev/',
  build: { outDir: dist, emptyOutDir: false, rollupOptions: { input: resolve(generated, 'index.html') } } })

let container, browser
const paths = [], errors = [], requests = []
try {
  // The production entrypoint writes runtime-config.js under /dev; this immutable visual fixture needs only nginx.
  container = docker(['run', '-d', '--name', `assignment-designer-ui-${Date.now()}`, '-p', '127.0.0.1::80', '-e', 'KONG_PROXY_URL=http://127.0.0.1:9',
    '--entrypoint', '/bin/sh', '--mount', `type=bind,src=${dist},dst=/usr/share/nginx/html/dev,readonly`, image, '-c',
    "envsubst '${KONG_PROXY_URL} ${HSTS_STS_HEADER}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf && exec nginx -g 'daemon off;'"])
  assert.match(container, /^[a-f0-9]{64}$/)
  browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname; requests.push(path)
    let data
    if (path === '/api/v1/admin/business-units/tree') data = { data: [{ id: 'verify-bu-id', code: 'VERIFY_BU', name: 'Verification business unit' }] }
    else if (path === '/api/v1/admin/task-assignment/roles/bu-bounded' || path === '/api/v1/admin/business-units/verify-bu-id/roles') {
      data = { data: [{ id: 'verify-role-id', code: 'VERIFY_ROLE', name: 'Verification reviewer' }] }
    } else if (path === '/api/v1/admin/users') data = { content: [{ id: 'verify-user-id', username: 'verify_login', displayName: 'Verification user' }] }
    else { errors.push('Unexpected API: ' + path); return route.abort() }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) })
  })
  const port = docker(['port', container, '80/tcp']).split(':').at(-1)
  await page.goto(`http://127.0.0.1:${port}/dev/tmp/assignment-exclusion-preview/index.html`, { waitUntil: 'networkidle' })
  await page.waitForFunction(() => !!window.__assignmentDesignerPreview)
  await page.evaluate(() => document.fonts.ready)
  const panel = page.locator('[data-testid="assignment-exclusion-panel"]')
  for (const locale of ['en', 'zh-CN', 'zh-TW']) for (const scenario of ['fixed', 'users', 'readonly', 'invalid']) {
    await page.evaluate(({ locale, scenario }) => window.__assignmentDesignerPreview.set(locale, scenario), { locale, scenario })
    await page.waitForTimeout(400)
    if (scenario === 'users') { await panel.locator('.el-select').last().click(); await page.waitForTimeout(250); await page.locator('h1').click() }
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
    if (scenario === 'readonly') assert.equal(await panel.locator('input[type="checkbox"]:not(:disabled)').count(), 0)
    if (scenario === 'invalid') {
      assert.equal(await panel.locator('.el-alert--error').count(), 1)
      assert.equal(await page.evaluate(() => window.__assignmentDesignerPreview.raw()), '{synthetic-invalid')
    } else {
      assert.equal(await panel.locator('.el-alert--error').count(), 0,
        JSON.stringify({ locale, scenario, alerts: await panel.locator('.el-alert--error').allTextContents(), errors, requests }))
      const policy = JSON.parse(await page.evaluate(() => window.__assignmentDesignerPreview.raw()))
      assert.deepEqual(policy.selectedNodeKeys, ['audit'])
      assert.equal(policy.backup.enabled, true)
    }
    const path = resolve(screenshots, `2026-10-28_assignment-exclusion-${locale}-${scenario}.png`)
    await page.screenshot({ path, fullPage: true }); paths.push(path)
    if (scenario === 'invalid') {
      await panel.locator('button').click()
      assert.equal(JSON.parse(await page.evaluate(() => window.__assignmentDesignerPreview.raw())).backup.enabled, false)
    }
  }
  await page.evaluate(() => window.__assignmentDesignerPreview.set('en', 'fixed'))
  await page.waitForTimeout(200)
  await panel.locator('.el-checkbox').nth(1).click()
  assert.equal(JSON.parse(await page.evaluate(() => window.__assignmentDesignerPreview.raw())).excludePreviousHandlers, false)
  await page.evaluate(() => window.__assignmentDesignerPreview.undo())
  assert.equal(JSON.parse(await page.evaluate(() => window.__assignmentDesignerPreview.raw())).excludePreviousHandlers, true)
  await page.evaluate(() => window.__assignmentDesignerPreview.redo())
  assert.equal(JSON.parse(await page.evaluate(() => window.__assignmentDesignerPreview.raw())).excludePreviousHandlers, false)
  const exported = await page.evaluate(() => window.__assignmentDesignerPreview.xml())
  assert.ok(exported.includes('assignmentExclusion') && exported.includes('VERIFY_ROLE'))
  assert.ok(requests.some(path => path.endsWith('/verify-bu-id/roles')))
  assert.deepEqual(errors, [])
  writeFileSync(resolve(root, 'tmp/assignment-designer-ui-result.json'), JSON.stringify({ scope: 'actual designer SFC + bpmn-js; synthetic catalogs; no business API acceptance claim', image, paths }, null, 2))
  console.log(JSON.stringify({ passed: true, screenshots: paths.length, paths }, null, 2))
} finally {
  await browser?.close()
  if (container && /^[a-f0-9]{64}$/.test(container)) docker(['rm', '-f', container])
}
