import { chromium } from 'playwright'
import { strict as assert } from 'node:assert'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'

const origin = process.env.VERIFY_ORIGIN || 'http://localhost:3000'
const root = fileURLToPath(new URL('../', import.meta.url))
const output = `${root}developer-workstation/verification-screenshots`
const date = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' })
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1480, height: 1050 } })
const created = []
const errors = []
const consoleErrors = []
page.on('console', message => { if (message.type() === 'error' || message.type() === 'warning') consoleErrors.push(message.text()) })
page.on('pageerror', error => errors.push(error.message))
async function api(method, path, data) {
  const response = await page.request[method](`${origin}/api/v1${path}`, data === undefined ? {} : { data })
  const body = await response.json()
  assert(response.ok() && body.success !== false, `${method} ${path}: ${JSON.stringify(body)}`)
  return body.data
}
function nodeOrder(xml, nodeId) {
  const task = xml.match(new RegExp(`<[^>]*userTask\\b[^>]*id="${nodeId}"[\\s\\S]*?<\\/[^>]*userTask>`))?.[0]
  assert(task, `Missing node ${nodeId}`)
  const match = task.match(/name="actionIds"\s+value="([^"]*)"/)
  assert(match, 'Missing actionIds')
  return JSON.parse(match[1].replace(/&quot;|&#0*34;|&#x0*22;/gi, '"'))
}
function diagram(ids) {
  const task = (id, order) => `<bpmn:userTask id="${id}" name="${id === 'review_a' ? 'Department review' : 'Finance review'}"><bpmn:extensionElements><custom:properties><custom:property name="assigneeType" value="INITIATOR"/><custom:property name="actionIds" value='${JSON.stringify(order)}'/><custom:property name="actionNames" value='${JSON.stringify(order.map(n => ['Save Draft','Approve','Reject'][ids.indexOf(n)]))}'/></custom:properties></bpmn:extensionElements></bpmn:userTask>`
  return `<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom="http://workflow.platform/schema/custom" id="Definitions_order" targetNamespace="http://workflow.platform/order"><bpmn:process id="Process_order_verify" isExecutable="true"><bpmn:startEvent id="start"/>${task('review_a', ids)}${task('review_b', [ids[2],ids[0],ids[1]])}<bpmn:endEvent id="end"/><bpmn:sequenceFlow id="flow_1" sourceRef="start" targetRef="review_a"/><bpmn:sequenceFlow id="flow_2" sourceRef="review_a" targetRef="review_b"/><bpmn:sequenceFlow id="flow_3" sourceRef="review_b" targetRef="end"/></bpmn:process><bpmndi:BPMNDiagram id="Diagram"><bpmndi:BPMNPlane id="Plane" bpmnElement="Process_order_verify"><bpmndi:BPMNShape id="s_start" bpmnElement="start"><dc:Bounds x="150" y="200" width="36" height="36"/></bpmndi:BPMNShape><bpmndi:BPMNShape id="s_a" bpmnElement="review_a"><dc:Bounds x="250" y="178" width="120" height="80"/></bpmndi:BPMNShape><bpmndi:BPMNShape id="s_b" bpmnElement="review_b"><dc:Bounds x="440" y="178" width="120" height="80"/></bpmndi:BPMNShape><bpmndi:BPMNShape id="s_end" bpmnElement="end"><dc:Bounds x="630" y="200" width="36" height="36"/></bpmndi:BPMNShape>${[['flow_1',186,250],['flow_2',370,440],['flow_3',560,630]].map(([id,x,y])=>`<bpmndi:BPMNEdge id="e_${id}" bpmnElement="${id}"><di:waypoint x="${x}" y="218"/><di:waypoint x="${y}" y="218"/></bpmndi:BPMNEdge>`).join('')}</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`
}
try {
  await mkdir(output, { recursive: true })
  await loginViaDwPassword(page, { loginOrigin: origin })
  const groups = await api('get', '/function-units/my-dev-groups')
  assert(groups.publicGroupId, 'Verification requires the configured Public development team')
  const fu = await api('post', '/function-units', { name: `QA Node Action Order ${Date.now()}`, description: 'Isolated action order verification sample', virtualGroupIds: [groups.publicGroupId] })
  created.push(fu.id)
  const actions = []
  for (const [name, type] of [['Save Draft','SAVE'], ['Approve','APPROVE'], ['Reject','REJECT']]) {
    actions.push(await api('post', `/function-units/${fu.id}/actions`, { actionName: name, actionType: type, configJson: {} }))
  }
  const ids = actions.map(action => action.id)
  await api('post', `/function-units/${fu.id}/process`, { bpmnXml: diagram(ids) })
  await page.goto(`${origin}/dev/function-units/${fu.id}`, { waitUntil: 'networkidle' })
  const node = id => page.locator(`.djs-element[data-element-id="${id}"] .djs-hit`).first()
  await node('review_a').click()
  const list = page.getByTestId('node-action-order')
  await list.waitFor()
  const expectNames = async expected => {
    await page.waitForFunction(expected => JSON.stringify(Array.from(document.querySelectorAll('[data-testid="node-action-order"] .action-order-name'), el => el.textContent.replace(/\s+/g, ' ').trim())) === JSON.stringify(expected), expected)
  }
  await expectNames(['1. Save Draft','2. Approve','3. Reject'])
  const toolbar = page.locator('.process-designer > .designer-toolbar .el-button-group').first()
  await list.locator('li').last().locator('.action-order-name').dragTo(list.locator('li').first().locator('.action-order-name'))
  await expectNames(['1. Reject','2. Save Draft','3. Approve'])
  await toolbar.locator('button').nth(3).click()
  await expectNames(['1. Save Draft','2. Approve','3. Reject'])
  await toolbar.locator('button').nth(4).click()
  await expectNames(['1. Reject','2. Save Draft','3. Approve'])
  const dragSaved = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith(`/function-units/${fu.id}/process`))
  await page.locator('.process-designer > .designer-toolbar').getByRole('button', { name: 'Save', exact: true }).click()
  assert((await dragSaved).ok())
  const draggedProcess = await api('get', `/function-units/${fu.id}/process`)
  assert.deepEqual(nodeOrder(draggedProcess.bpmnXml, 'review_a'), [ids[2],ids[0],ids[1]])
  await page.reload({ waitUntil: 'networkidle' })
  await node('review_a').click()
  await expectNames(['1. Reject','2. Save Draft','3. Approve'])
  await list.locator('li').first().locator('.action-order-name').dragTo(list.locator('li').last().locator('.action-order-name'))
  await expectNames(['1. Save Draft','2. Approve','3. Reject'])
  await list.getByRole('button', { name: 'Move Reject up', exact: true }).click()
  await expectNames(['1. Save Draft','2. Reject','3. Approve'])
  await toolbar.locator('button').nth(3).click()
  await expectNames(['1. Save Draft','2. Approve','3. Reject'])
  await toolbar.locator('button').nth(4).click()
  await expectNames(['1. Save Draft','2. Reject','3. Approve'])
  await node('review_b').click()
  await expectNames(['1. Reject','2. Save Draft','3. Approve'])
  await node('review_a').click()
  const saved = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith(`/function-units/${fu.id}/process`))
  await page.locator('.process-designer > .designer-toolbar').getByRole('button', { name: 'Save', exact: true }).click()
  assert((await saved).ok())
  const savedProcess = await api('get', `/function-units/${fu.id}/process`)
  assert.deepEqual(nodeOrder(savedProcess.bpmnXml, 'review_a'), [ids[0],ids[2],ids[1]])
  assert.deepEqual(nodeOrder(savedProcess.bpmnXml, 'review_b'), [ids[2],ids[0],ids[1]])
  await page.reload({ waitUntil: 'networkidle' })
  await node('review_a').click()
  await expectNames(['1. Save Draft','2. Reject','3. Approve'])
  await list.scrollIntoViewIfNeeded()
  await page.screenshot({ path: `${output}/${date}_node-action-order.png`, fullPage: true })
  await list.screenshot({ path: `${root}help/public/guides/dw-node-action-order.png` })
  await page.getByRole('tab', { name: 'Action Design', exact: true }).click()
  await page.getByRole('row').filter({ hasText: 'Save Draft' }).click()
  await page.getByRole('checkbox', { name: 'Department review', exact: true }).waitFor({ state: 'attached' })
  assert(await page.getByRole('checkbox', { name: 'Department review', exact: true }).isChecked())
  assert(await page.getByRole('checkbox', { name: 'Finance review', exact: true }).isChecked())
  const bindingSaved = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith(`/function-units/${fu.id}/process`))
  await page.getByRole('button', { name: 'Save Binding', exact: true }).click()
  assert((await bindingSaved).ok())
  const reboundProcess = await api('get', `/function-units/${fu.id}/process`)
  assert.deepEqual(nodeOrder(reboundProcess.bpmnXml, 'review_a'), [ids[0],ids[2],ids[1]])
  assert.deepEqual(nodeOrder(reboundProcess.bpmnXml, 'review_b'), [ids[2],ids[0],ids[1]])
  await page.getByRole('tab', { name: 'Process Design', exact: true }).click()

  // A legacy diagram can contain both scopes and mixed numeric/string IDs.
  const extraGlobal = await api('post', `/function-units/${fu.id}/actions`, { actionName: 'View Record', actionType: 'SAVE', configJson: {} })
  const legacyXml = await page.evaluate(({ xml, globals }) => {
    const doc = new DOMParser().parseFromString(xml, 'text/xml')
    const process = doc.getElementsByTagNameNS('*', 'process')[0]
    const ext = doc.createElementNS(process.namespaceURI, 'bpmn:extensionElements')
    const properties = doc.createElementNS('http://workflow.platform/schema/custom', 'custom:properties')
    const prop = doc.createElementNS(properties.namespaceURI, 'custom:property')
    prop.setAttribute('name', 'globalActionIds')
    prop.setAttribute('value', JSON.stringify(globals))
    properties.appendChild(prop)
    ext.appendChild(properties)
    process.insertBefore(ext, process.firstChild)
    const finance = Array.from(doc.getElementsByTagNameNS('*', 'userTask')).find(task => task.id === 'review_b')
    const idsProp = Array.from(finance.getElementsByTagNameNS('*', 'property')).find(prop => prop.getAttribute('name') === 'actionIds')
    idsProp.setAttribute('value', JSON.stringify(JSON.parse(idsProp.getAttribute('value')).map(String)))
    return new XMLSerializer().serializeToString(doc)
  }, { xml: savedProcess.bpmnXml, globals: [extraGlobal.id, ids[0]] })
  await api('post', `/function-units/${fu.id}/process`, { bpmnXml: legacyXml })
  await page.reload({ waitUntil: 'networkidle' })
  await node('review_a').click()
  await expectNames(['1. Reject', '2. Approve'])
  const globals = page.getByTestId('node-global-actions')
  const expectGlobals = async () => {
    await page.waitForFunction(() => JSON.stringify(Array.from(document.querySelectorAll('[data-testid="node-global-actions"] .global-action-name'), el => el.textContent.trim())) === JSON.stringify(['View Record', 'Save Draft']))
    assert.equal(await globals.locator('button, .action-drag-grip, .action-order-name').count(), 0)
    assert.equal(await globals.getByText('Global inherited', { exact: true }).count(), 2)
  }
  await expectGlobals()
  // Opening and saving the process preserves the stored legacy bindings.
  const saveProcessButton = page.locator('.process-designer > .designer-toolbar').getByRole('button', { name: 'Save', exact: true })
  const unchangedSaved = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith(`/function-units/${fu.id}/process`))
  await saveProcessButton.click()
  assert((await unchangedSaved).ok())
  const beforeBindings = await api('get', `/function-units/${fu.id}/process`)
  assert.deepEqual(nodeOrder(beforeBindings.bpmnXml, 'review_a'), [ids[0], ids[2], ids[1]])
  assert.deepEqual(nodeOrder(beforeBindings.bpmnXml, 'review_b'), [ids[2], ids[0], ids[1]].map(String))
  // A global action is visible but cannot be dragged into the node action list.
  await globals.locator('.global-action-name').first().dragTo(list.locator('.action-order-name').first())
  await expectNames(['1. Reject', '2. Approve'])
  await expectGlobals()
  // The node selector displays inherited entries as disabled, preventing duplicate binding.
  const actionSelector = page.locator('.el-form-item').filter({ has: page.locator('label').filter({ hasText: /^Available Actions$/ }) }).locator('.el-select')
  await actionSelector.click()
  for (const name of ['Save Draft', 'View Record']) {
    await page.getByRole('option').filter({ hasText: name }).waitFor()
    assert(await page.getByRole('option').filter({ hasText: name }).evaluate(el => el.classList.contains('is-disabled')))
  }
  await page.keyboard.press('Escape')
  await list.scrollIntoViewIfNeeded()
  await page.screenshot({ path: `${output}/${date}_node-global-actions.png`, fullPage: true })
  await list.screenshot({ path: `${root}help/public/guides/dw-node-action-order.png` })

  // Saving a global action leaves the legacy raw node arrays intact.
  await page.getByRole('tab', { name: 'Action Design', exact: true }).click()
  const globalSummary = page.getByRole('row').filter({ hasText: 'Save Draft' })
  await globalSummary.locator('.node-tag').first().waitFor()
  await page.screenshot({ path: `${output}/${date}_action-binding-summary-${process.env.VERIFY_SUMMARY_STAGE || 'after'}.png`, fullPage: true, animations: 'disabled' })
  assert.deepEqual(await globalSummary.locator('.node-tag').allTextContents(), ['Process Global'])
  const nodeSummary = page.getByRole('row').filter({ hasText: 'Approve' })
  assert.deepEqual(await nodeSummary.locator('.node-tag').allTextContents(), ['Department review', 'Finance review'])
  await page.getByRole('row').filter({ hasText: 'Save Draft' }).click()
  assert(await page.getByRole('radio', { name: 'Process Global', exact: true }).isChecked())
  const saveBindingButton = page.getByRole('button', { name: 'Save Binding', exact: true })
  let bindingResponse = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith(`/function-units/${fu.id}/process`))
  await saveBindingButton.click()
  assert((await bindingResponse).ok())
  let afterBindings = await api('get', `/function-units/${fu.id}/process`)
  for (const id of ['review_a', 'review_b']) assert.deepEqual(nodeOrder(afterBindings.bpmnXml, id), nodeOrder(beforeBindings.bpmnXml, id))

  await page.getByRole('button', { name: 'Back to List', exact: true }).click()
  await page.getByRole('row').filter({ hasText: 'Approve' }).click()
  for (const name of ['Department review', 'Finance review']) assert(await page.getByRole('checkbox', { name, exact: true }).isChecked())
  bindingResponse = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith(`/function-units/${fu.id}/process`))
  await saveBindingButton.click()
  assert((await bindingResponse).ok())
  afterBindings = await api('get', `/function-units/${fu.id}/process`)
  for (const id of ['review_a', 'review_b']) assert.deepEqual(nodeOrder(afterBindings.bpmnXml, id), nodeOrder(beforeBindings.bpmnXml, id))
  await page.getByRole('tab', { name: 'Process Design', exact: true }).click()
  await node('review_b').click()
  await expectNames(['1. Reject', '2. Approve'])
  await expectGlobals()
  // Dragging local actions changes only their order; inherited globals stay last.
  await list.locator('.action-order-name').first().dragTo(list.locator('.action-order-name').last())
  await expectNames(['1. Approve', '2. Reject'])
  await expectGlobals()
  await toolbar.locator('button').nth(3).click()
  await expectNames(['1. Reject', '2. Approve'])
  await toolbar.locator('button').nth(4).click()
  await expectNames(['1. Approve', '2. Reject'])
  await expectGlobals()
  bindingResponse = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith(`/function-units/${fu.id}/process`))
  await saveProcessButton.click()
  assert((await bindingResponse).ok())
  const localOnly = await api('get', `/function-units/${fu.id}/process`)
  assert.deepEqual(nodeOrder(localOnly.bpmnXml, 'review_b'), [ids[1], ids[2]])
  await page.reload({ waitUntil: 'networkidle' })
  await node('review_b').click()
  await expectNames(['1. Approve', '2. Reject'])
  await expectGlobals()
  await list.scrollIntoViewIfNeeded()
  await page.screenshot({ path: `${output}/${date}_node-global-actions-saved.png`, fullPage: true })
  await page.setViewportSize({ width: 1366, height: 768 })
  await globals.scrollIntoViewIfNeeded()
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
  await page.screenshot({ path: `${output}/${date}_node-global-actions-1366.png`, fullPage: true })
  await page.setViewportSize({ width: 1480, height: 1050 })
  // Opt in: some local deployments do not assign a team to clones, preventing cleanup.
  if (process.env.VERIFY_CLONE === '1') {
    // Cloning remaps action IDs; compare the resulting names in the same ordered UI.
    const clone = await api('post', `/function-units/${fu.id}/clone?newName=${encodeURIComponent(`QA Cloned Action Order ${Date.now()}`)}`)
    created.push(clone.id)
    await page.goto(`${origin}/dev/function-units/${clone.id}`, { waitUntil: 'networkidle' })
    await node('review_a').click()
    await expectNames(['1. Reject', '2. Approve'])
    await expectGlobals()
    await node('review_b').click()
    await expectNames(['1. Approve', '2. Reject'])
    await expectGlobals()
    await list.scrollIntoViewIfNeeded()
    await page.screenshot({ path: `${output}/${date}_node-action-order-clone.png`, fullPage: true })
  }
  if (process.env.VERIFY_READONLY_FU) {
    const readonlyId = process.env.VERIFY_READONLY_FU
    const readonlyFu = await api('get', `/function-units/${readonlyId}`)
    assert.equal(readonlyFu.canModify, false, 'Readonly fixture must be denied modify permission by the server')
    const before = await api('get', `/function-units/${readonlyId}/process`)
    await page.goto(`${origin}/dev/function-units/${readonlyId}`, { waitUntil: 'networkidle' })
    await node('review_a').click()
    await list.locator('.action-order-readonly').waitFor()
    const rowIds = () => list.locator('li').evaluateAll(rows => rows.map(row => row.dataset.actionId))
    const beforeIds = await rowIds()
    await list.locator('li').first().locator('.action-order-name').dragTo(list.locator('li').last().locator('.action-order-name'))
    assert.deepEqual(await rowIds(), beforeIds)
    const after = await api('get', `/function-units/${readonlyId}/process`)
    assert.equal(after.bpmnXml, before.bpmnXml)
    await page.screenshot({ path: `${output}/${date}_node-action-order-readonly.png`, fullPage: true })
  }
  assert.deepEqual(errors, [])
  assert.deepEqual(consoleErrors.filter(message => /TypeError|ReferenceError|SyntaxError|Unhandled error/.test(message)), [])
  console.log(JSON.stringify({ result: 'PASS', checks: ['drag multi-position','drag undo/redo','drag save/reload', ...(process.env.VERIFY_READONLY_FU ? ['readonly drag disabled'] : []),'move','undo','redo','node independence','save/load','Action Design unchanged binding preserves order', 'mixed ID bindings preserved', 'global group is read-only', 'global scope summary hides legacy nodes', 'node scope summary keeps node tags', 'global selection disabled', 'legacy global save preserves raw bindings', 'node reorder keeps globals last', ...(process.env.VERIFY_CLONE === '1' ? ['clone/remap'] : [])], screenshots: [`${output}/${date}_node-action-order.png`, `${output}/${date}_node-global-actions.png`, `${output}/${date}_node-global-actions-saved.png`, ...(process.env.VERIFY_CLONE === '1' ? [`${output}/${date}_node-action-order-clone.png`] : [])] }))
} catch (error) {
  await page.screenshot({ path: `${output}/${date}_node-global-actions-failure.png`, fullPage: true })
  console.error('UI verification failed', { errors, consoleErrors, url: page.url(), text: (await page.locator('body').innerText()).slice(-2500) })
  throw error
} finally {
  for (const id of created.reverse()) {
    try { await api('delete', `/function-units/${id}`) } catch (error) { console.error(`Verification sample cleanup failed (${id}): ${error.message}`) }
  }
  await browser.close()
}
