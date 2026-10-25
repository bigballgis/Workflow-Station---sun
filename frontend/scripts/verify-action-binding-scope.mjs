import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'

const phase = process.argv[2]
assert(['red', 'green'].includes(phase))
const origin = process.env.VERIFY_ORIGIN || 'http://localhost:3000'
const output = fileURLToPath(new URL('../developer-workstation/verification-screenshots/', import.meta.url))
const stateFile = '/tmp/action-binding-scope-fixture.json'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1480, height: 1050 } })
const errors = [], failures = []
let authenticated = false, verified = false
page.on('pageerror', error => errors.push(error.message))
page.on('response', response => { if (authenticated && response.status() >= 400 && response.url().startsWith(`${origin}/api/v1/`)) failures.push(response.status()) })
const samples = await readFile(stateFile, 'utf8').then(JSON.parse).catch(error => {
  if (error.code === 'ENOENT' && phase === 'red') return []
  throw error
})
async function api(method, path, data) {
  const response = await page.request.fetch(`${origin}/api/v1${path}`, { method, ...(data === undefined ? {} : { data }) })
  const body = await response.json()
  assert(response.ok() && body.success !== false, `${method} ${path}: HTTP ${response.status()}`)
  return body.data
}
function diagram(sample) {
  const p = (name, value, prefix = 'c') => `<${prefix}:property name="${name}" value="${String(typeof value === 'object' ? JSON.stringify(value) : value).replaceAll('"', '&quot;')}"/>`
  const ids = sample.actions.map(a => a.id)
  const bindings = order => p('actionIds', order) + p('actionNames', order.map(id => sample.actions.find(a => a.id === id).actionName))
  const id = sample.duplicate ? 'process' : 'review_a'
  const globals = sample.duplicate ? `<l:properties>${p('globalActionIds', [ids[1]], 'l')}</l:properties><c:properties>${p('globalActionIds', [ids[2]])}</c:properties>` : `<c:properties>${p('description', 'Preserve process properties')}</c:properties>`
  const initial = p('formId', sample.form.id) + p('formName', sample.form.formName) + p('assigneeType', 'INITIATOR') + p('assigneeLabel', 'Process Initiator')
  const actions = sample.duplicate ? `${bindings([ids[0],ids[1]])}</c:properties><c:properties>${bindings([ids[3],ids[0]])}` : ''
  return `<b:definitions xmlns:b="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:c="http://workflow.platform/schema/custom" xmlns:l="http://custom.bpmn.io/schema" xmlns:d="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" targetNamespace="scope"><b:process id="${sample.unit.code}" isExecutable="true"><b:extensionElements>${globals}</b:extensionElements><b:startEvent id="start"/><b:userTask id="${id}" name="Review process"><b:extensionElements><c:properties>${initial}${actions}</c:properties></b:extensionElements></b:userTask><b:userTask id="other" name="Other review"><b:extensionElements><c:properties>${initial}</c:properties></b:extensionElements></b:userTask><b:endEvent id="end"/><b:sequenceFlow id="s1" sourceRef="start" targetRef="${id}"/><b:sequenceFlow id="s2" sourceRef="${id}" targetRef="other"/><b:sequenceFlow id="s3" sourceRef="other" targetRef="end"/></b:process><d:BPMNDiagram id="diagram"><d:BPMNPlane id="plane" bpmnElement="${sample.unit.code}">${[['start',100,36,36],[id,230,120,80],['other',450,120,80],['end',680,36,36]].map(([id,x,w,h])=>`<d:BPMNShape id="shape_${id}" bpmnElement="${id}"><dc:Bounds x="${x}" y="200" width="${w}" height="${h}"/></d:BPMNShape>`).join('')}${[['s1',136,230],['s2',350,450],['s3',570,680]].map(([id,x,y])=>`<d:BPMNEdge id="edge_${id}" bpmnElement="${id}"><di:waypoint x="${x}" y="220"/><di:waypoint x="${y}" y="220"/></d:BPMNEdge>`).join('')}</d:BPMNPlane></d:BPMNDiagram></b:definitions>`
}
const tab = name => page.getByRole('tab', { name, exact: true }).click()
const selectAction = async name => {
  await tab('Action Design')
  if (await page.locator('.action-designer .action-editor').isVisible()) await page.locator('.action-designer').getByRole('button', { name: 'Back to List', exact: true }).click()
  await page.getByRole('row').filter({ hasText: name }).click()
}
async function saveBinding() {
  const result = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/process'))
  await page.getByRole('button', { name: 'Save Binding', exact: true }).click()
  assert((await (await result).json()).success)
}
async function inspectNode(id) {
  await tab('Process Design')
  await page.locator(`.djs-element[data-element-id="${id}"] .djs-hit`).first().click()
  await page.getByTestId('node-action-order').waitFor()
}
const nodeNames = () => page.getByTestId('node-action-order').locator('.action-order-name').allTextContents().then(names => names.map(s => s.replace(/^\s*\d+\.\s*/, '').trim()))
async function expectNames(names) {
  await page.waitForFunction(names => JSON.stringify(Array.from(document.querySelectorAll('.action-order-name'), row => row.textContent.replace(/^\s*\d+\.\s*/, '').trim())) === JSON.stringify(names), names)
}
async function saveDesign() {
  const result = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/process'))
  await page.locator('.process-designer > .designer-toolbar').getByRole('button', { name: 'Save', exact: true }).click()
  assert((await (await result).json()).success)
}
try {
  await mkdir(output, { recursive: true })
  await loginViaDwPassword(page, { loginOrigin: origin })
  authenticated = true
  if (phase === 'red' && !samples.length) {
    const groups = await api('GET', '/function-units/my-dev-groups')
    for (const duplicate of [false, true]) {
      const unit = await api('POST', '/function-units', { name: `Action Scope QA ${Date.now()}`, tags: ['action-binding-synthetic'], virtualGroupIds: [groups.publicGroupId] })
      const sample = { unit, duplicate, actions: [] }
      samples.push(sample)
      await writeFile(stateFile, JSON.stringify(samples))
      for (const [name,type] of [['Save Draft','SAVE'],['Approve','APPROVE'],['Reject','REJECT'],['View Record','FORM_POPUP']]) sample.actions.push(await api('POST', `/function-units/${unit.id}/actions`, { actionName: name, actionType: type, configJson: {} }))
      sample.form = await api('POST', `/function-units/${unit.id}/forms`, { formName: 'QA task form', formType: 'TASK', scene: 'TASK', configJson: { rule: [], options: {} } })
      sample.xml = diagram(sample)
      await writeFile(stateFile, JSON.stringify(samples))
    }
  }
  for (const sample of samples) {
    // Both phases repeat the identical starting configuration and user operation.
    await api('POST', `/function-units/${sample.unit.id}/process`, { bpmnXml: sample.xml })
    await page.goto(`${origin}/dev/function-units/${sample.unit.id}`, { waitUntil: 'networkidle' })
    if (!sample.duplicate) {
      await selectAction('Approve')
      await page.getByText('Review process', { exact: true }).last().click()
      await saveBinding()
      await inspectNode('review_a')
      if (phase === 'red') assert.deepEqual(await nodeNames(), [])
      else {
        await expectNames(['Approve'])
        await saveDesign()
        await page.reload({ waitUntil: 'networkidle' }); await inspectNode('review_a'); await expectNames(['Approve'])
        await selectAction('Reject')
        await page.locator('.action-designer .el-radio').filter({ hasText: /^\s*Process Global\s*$/ }).click()
        await saveBinding()
        await inspectNode('review_a')
        await page.waitForFunction(() => document.querySelector('.global-action-name')?.textContent === 'Reject')
        await saveDesign()
        await page.reload({ waitUntil: 'networkidle' }); await inspectNode('review_a'); await expectNames(['Approve'])
        await page.waitForFunction(() => document.querySelector('.global-action-name')?.textContent === 'Reject')
      }
    } else {
      await selectAction('View Record')
      assert.equal(await page.getByRole('checkbox', { name: 'Review process', exact: true }).isChecked(), phase === 'green')
      await selectAction('Save Draft')
      assert.equal(await page.getByRole('radio', { name: 'Bind to Node', exact: true }).isChecked(), phase === 'green')
      if (phase === 'green') {
        await saveBinding()
        await inspectNode('process'); await expectNames(['View Record','Save Draft'])
        const order = page.getByTestId('node-action-order')
        await order.locator('.action-order-name').last().dragTo(order.locator('.action-order-name').first())
        await expectNames(['Save Draft','View Record'])
        const toolbar = page.locator('.process-designer > .designer-toolbar .el-button-group').first()
        await toolbar.locator('button').nth(3).click(); await expectNames(['View Record','Save Draft'])
        await toolbar.locator('button').nth(4).click(); await expectNames(['Save Draft','View Record'])
        await saveDesign()
        await page.reload({ waitUntil: 'networkidle' }); await inspectNode('process'); await expectNames(['Save Draft','View Record'])
        assert.deepEqual(await page.locator('.global-action-name').allTextContents(), ['Reject'])
      }
    }
    await page.screenshot({ path: `${output}/2026-10-23_binding-${sample.duplicate ? 'duplicate-scope' : 'first-binding'}-${phase}.png`, fullPage: true })
    console.log(JSON.stringify({ phase, unitId: sample.unit.id, duplicate: sample.duplicate, result: phase === 'red' ? 'REPRODUCED' : 'PASS' }))
  }
  if (phase === 'green') { assert.deepEqual(errors, []); assert.deepEqual(failures, []); verified = true }
} catch (error) {
  await page.screenshot({ path: `${output}/2026-10-23_binding-${phase}-failure.png`, fullPage: true })
  console.error(JSON.stringify({ phase, errors, failures, message: error.message }))
  throw error
} finally {
  if (verified) for (const sample of samples) {
    const unit = await api('GET', `/function-units/${sample.unit.id}`)
    assert.equal(unit.name, sample.unit.name)
    await api('DELETE', `/function-units/${sample.unit.id}`)
  }
  await browser.close()
}
