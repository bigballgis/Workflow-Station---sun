import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'

const phase = process.argv[2]
assert(['red', 'green'].includes(phase), 'Pass red or green')
const origin = process.env.VERIFY_ORIGIN || 'http://localhost:3000'
const output = fileURLToPath(new URL('../developer-workstation/verification-screenshots/', import.meta.url))
const statePath = '/tmp/action-compatibility-ui.json'
const date = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' })
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1480, height: 1050 } })
const errors = []
const consoleErrors = []
const failedRequests = []
let authenticated = false
let verified = false
page.on('pageerror', error => errors.push(error.message))
page.on('console', message => { if (message.text().includes('filter is not a function')) errors.push(message.text()) })
page.on('console', message => { if (authenticated && message.type() === 'error') consoleErrors.push(message.text()) })
page.on('response', response => { if (authenticated && response.status() >= 400 && response.url().startsWith(`${origin}/api/v1/`)) failedRequests.push(response.status()) })
const samples = await readFile(statePath, 'utf8').then(JSON.parse).catch(error => {
  if (error.code === 'ENOENT' && phase === 'red') return []
  throw error
})
async function api(method, path, data) {
  const response = await page.request.fetch(`${origin}/api/v1${path}`, { method, ...(data === undefined ? {} : { data }) })
  const body = await response.json()
  assert(response.ok() && body.success !== false, `${method} ${path}: ${response.status()}`)
  return body.data
}
function diagram(sample) {
  const prop = (name, value) => `<c:property name="${name}" value="${String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;')}"/>`
  const { actions: a, forms: f, legacy } = sample
  const globals = legacy ? `${a[2].id},${a[3].id}` : JSON.stringify([a[2].id, a[3].id])
  const nodeIds = legacy ? `${a[0].id},${a[1].id}` : JSON.stringify([a[0].id, a[1].id])
  const task = id => `<b:userTask id="${id}" name="${id}"><b:extensionElements><c:properties>${prop('formId', f[0].id)}${prop('formName', f[0].formName)}${prop('actionIds', nodeIds)}${prop('actionNames', JSON.stringify(a.slice(0, 2).map(x => x.actionName)))}</c:properties>${prop('formId', f[1].id)}</b:extensionElements></b:userTask>`
  return `<b:definitions xmlns:b="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:c="http://workflow.platform/schema/custom" xmlns:d="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" targetNamespace="compatibility"><b:process id="${sample.unit.code}" isExecutable="true"><b:extensionElements><c:properties>${prop('globalActionIds', globals)}${prop('globalActionNames', JSON.stringify(a.slice(2).map(x => x.actionName)))}</c:properties></b:extensionElements><b:startEvent id="start"/>${task('review_a')}${task('review_b')}<b:endEvent id="end"/><b:sequenceFlow id="s1" sourceRef="start" targetRef="review_a"/><b:sequenceFlow id="s2" sourceRef="review_a" targetRef="review_b"/><b:sequenceFlow id="s3" sourceRef="review_b" targetRef="end"/></b:process><d:BPMNDiagram id="diagram"><d:BPMNPlane id="plane" bpmnElement="${sample.unit.code}">${[['start',100,36,36],['review_a',230,120,80],['review_b',450,120,80],['end',680,36,36]].map(([id,x,w,h])=>`<d:BPMNShape id="shape_${id}" bpmnElement="${id}"><dc:Bounds x="${x}" y="200" width="${w}" height="${h}"/></d:BPMNShape>`).join('')}${[['s1',136,230],['s2',350,450],['s3',570,680]].map(([id,x,y])=>`<d:BPMNEdge id="edge_${id}" bpmnElement="${id}"><di:waypoint x="${x}" y="220"/><di:waypoint x="${y}" y="220"/></d:BPMNEdge>`).join('')}</d:BPMNPlane></d:BPMNDiagram></b:definitions>`
}
const node = id => page.locator(`.djs-element[data-element-id="${id}"] .djs-hit`).first()
const form = () => page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^To Do Form$/ }) }).locator('.el-select')
async function expectForm(name) {
  await page.waitForFunction(name => Array.from(document.querySelectorAll('.el-form-item')).find(item => item.querySelector('.el-form-item__label')?.textContent.trim() === 'To Do Form')?.querySelector('.el-select__selection')?.textContent.trim() === name, name)
}
async function save() {
  const result = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/process'))
  await page.locator('.process-designer > .designer-toolbar').getByRole('button', { name: 'Save', exact: true }).click()
  assert((await (await result).json()).success)
}
try {
  await mkdir(output, { recursive: true })
  await loginViaDwPassword(page, { loginOrigin: origin })
  authenticated = true
  if (phase === 'red' && samples.length === 0) {
    const groups = await api('GET', '/function-units/my-dev-groups')
    assert(groups.publicGroupId)
    for (const legacy of [false, true]) {
      const unit = await api('POST', '/function-units', { name: `Action Compatibility QA ${Date.now()}`, tags: ['action-compatibility-synthetic'], virtualGroupIds: [groups.publicGroupId] })
      const sample = { unit, legacy, actions: [], forms: [] }
      samples.push(sample)
      await writeFile(statePath, JSON.stringify(samples))
      for (const [name, type] of [['Save Draft','SAVE'],['Approve','APPROVE'],['Reject','REJECT'],['View Record','FORM_POPUP']]) sample.actions.push(await api('POST', `/function-units/${unit.id}/actions`, { actionName: name, actionType: type, configJson: {} }))
      for (const name of ['QA Current Form','QA Replacement Form']) sample.forms.push(await api('POST', `/function-units/${unit.id}/forms`, { formName: name, formType: 'TASK', scene: 'TASK', configJson: { rule: [], options: {} } }))
      await api('POST', `/function-units/${unit.id}/process`, { bpmnXml: diagram(sample) })
      await writeFile(statePath, JSON.stringify(samples))
    }
  }
  if (phase === 'green') for (const sample of samples) {
    if (sample.forms.length === 2) sample.forms.push(await api('POST', `/function-units/${sample.unit.id}/forms`, { formName: 'QA Edited Form', formType: 'TASK', scene: 'TASK', configJson: { rule: [], options: {} } }))
    await writeFile(statePath, JSON.stringify(samples))
  }
  for (const sample of samples) {
    await page.goto(`${origin}/dev/function-units/${sample.unit.id}`, { waitUntil: 'networkidle' })
    const ready = Promise.race([
      page.waitForEvent('pageerror', { predicate: error => error.message.includes('filter') }).catch(() => null),
      page.waitForEvent('console', { predicate: message => message.text().includes('filter is not a function') }).catch(() => null),
      page.getByTestId('node-action-order').waitFor().catch(() => null),
    ])
    await node('review_a').click()
    await ready
    if (phase === 'red') {
      if (sample.legacy) assert(errors.some(error => error.includes('filter')), 'Legacy global IDs must reproduce the panel error')
      else await expectForm(sample.forms[1].formName)
      await page.screenshot({ path: `${output}/${date}_action-compatibility-${sample.legacy ? 'legacy' : 'standard'}-before.png`, fullPage: true })
      console.log(JSON.stringify({ phase, legacy: sample.legacy, unitId: sample.unit.id, reproduced: true }))
      continue
    }
    await expectForm(sample.forms[0].formName)
    const order = page.getByTestId('node-action-order')
    const global = page.getByTestId('node-global-actions')
    await page.waitForFunction(() => document.querySelector('[data-testid="node-global-actions"]')?.textContent.includes('View Record'))
    assert.deepEqual(await global.locator('.global-action-name').allTextContents(), ['Reject','View Record'])
    assert.equal(await global.locator('button').count(), 0)
    await form().click()
    await page.getByRole('option').filter({ hasText: sample.forms[2].formName }).click()
    await order.locator('.action-order-name').last().dragTo(order.locator('.action-order-name').first())
    await page.waitForFunction(() => document.querySelector('.action-order-name')?.textContent.includes('Approve'))
    await save()
    await page.reload({ waitUntil: 'networkidle' })
    await node('review_a').click()
    await expectForm(sample.forms[2].formName)
    await page.waitForFunction(() => document.querySelector('.action-order-name')?.textContent.includes('Approve'))
    const stored = await api('GET', `/function-units/${sample.unit.id}/process`)
    const saved = await page.evaluate(xml => {
      const doc = new DOMParser().parseFromString(xml, 'text/xml')
      return Array.from(doc.getElementsByTagNameNS('*','userTask')).map(task => ({ id:task.id, properties:Array.from(task.getElementsByTagNameNS('*','property')).map(p=>({name:p.getAttribute('name'),value:p.getAttribute('value')})) }))
    }, stored.bpmnXml)
    assert.deepEqual(JSON.parse(saved[0].properties.find(p=>p.name==='actionIds').value), [sample.actions[1].id,sample.actions[0].id])
    assert.equal(saved[0].properties.find(p=>p.name==='formId').value, String(sample.forms[2].id))
    assert.equal(saved[1].properties.find(p=>p.name==='formId').value, String(sample.forms[0].id))
    await node('review_b').click()
    await expectForm(sample.forms[0].formName)
    await page.waitForFunction(() => document.querySelector('.action-order-name')?.textContent.includes('Save Draft'))
    await order.scrollIntoViewIfNeeded()
    await page.screenshot({ path: `${output}/${date}_action-compatibility-${sample.legacy ? 'legacy' : 'standard'}-after.png`, fullPage: true })
    console.log(JSON.stringify({ phase, legacy: sample.legacy, unitId: sample.unit.id, saved, result: 'PASS' }))
  }
  if (phase === 'green') {
    assert.deepEqual(errors, [])
    assert.deepEqual(consoleErrors, [])
    assert.deepEqual(failedRequests, [])
    verified = true
  }
} catch (error) {
  console.error(JSON.stringify({ phase, errors, forms: await page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^To Do Form$/ }) }).allTextContents() }))
  await page.screenshot({ path: `${output}/${date}_action-compatibility-${phase}-failure.png`, fullPage: true })
  throw error
} finally {
  if (phase === 'green' && verified) for (const sample of samples) {
    const current = await api('GET', `/function-units/${sample.unit.id}`)
    assert.equal(current.name, sample.unit.name)
    await api('DELETE', `/function-units/${sample.unit.id}`)
  }
  await browser.close()
}
