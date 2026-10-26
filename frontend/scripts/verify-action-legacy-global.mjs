import {execFileSync} from 'node:child_process'
/** Isolated DW deploy -> Portal instance -> real task button order acceptance. */
import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword, loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'
import { readBatchStartContext } from './batch-start-context.mjs'

const phase = process.argv[2]; assert(['red', 'green'].includes(phase))
const origin = process.env.VERIFY_ORIGIN || 'http://localhost:3000'
const root = fileURLToPath(new URL('../', import.meta.url))
const output = `${root}user-portal/verification-screenshots`
const date = process.env.VERIFY_ARTIFACT_PREFIX || new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' })
const browser = await chromium.launch({ headless: true })
const dw = await (await browser.newContext({ viewport: { width: 1480, height: 1050 } })).newPage()
const admin = await (await browser.newContext()).newPage()
const portal = await (await browser.newContext({ viewport: { width: 1480, height: 1000 } })).newPage()
const pageErrors = []
const failedRequests = []
let authenticated = false
portal.on('response', async response => {
  if (authenticated && response.status() >= 400 && response.url().startsWith(`${origin}/api/portal/`) && !response.url().includes('/auth/login')) {
    failedRequests.push({ method: response.request().method(), url: response.url().replace(origin, ''), status: response.status() })
  }
})
for (const page of [dw, admin, portal]) {
  page.setDefaultTimeout(30000)
  page.on('pageerror', error => pageErrors.push(error.message))
}
let unit
const instances = []
const catalogs = []
const evidence = { checks: [], catalogs, instances }
async function api(page, prefix, method, path, data) {
  const response = await page.request.fetch(`${origin}${prefix}${path}`, { method, ...(data === undefined ? {} : { data }) })
  if (response.status() === 204) return null
  const text = await response.text()
  assert(text, `${method} ${path}: empty HTTP ${response.status()} response`)
  const body = JSON.parse(text)
  assert(response.ok() && body.success !== false, `${method} ${path}: HTTP ${response.status()} ${body.error?.message || body.message || ''}`)
  return body && typeof body === 'object' && 'data' in body ? body.data : body
}
const design = (method, path, data) => api(dw, '/api/v1', method, path, data)
const manage = (method, path, data) => api(admin, '/api/v1/admin', method, path, data)
const runtime = (method, path, data) => api(portal, '/api/portal', method, path, data)
function property(name, value) {
  return `<custom:property name="${name}" value="${String(typeof value === 'object' ? JSON.stringify(value) : value).replaceAll('&', '&amp;').replaceAll('"', '&quot;')}"/>`
}
function diagram(actions, forms, processKey) {
  const props = (entries) => `<bpmn:extensionElements><custom:properties>${entries.map(([key, value]) => property(key, value)).join('')}</custom:properties></bpmn:extensionElements>`
  const taskBase = (id, name) => `<bpmn:userTask id="${id}" name="${name}" flowable:assignee="\${initiator}">${props([['assigneeType', 'INITIATOR'], ['formId', forms.task.id], ['formName', forms.task.formName], ['actionIds', [actions.approve.id, actions.save.id]], ['actionNames', ['Approve', 'Save Draft']]])}</bpmn:userTask>`
  const duplicate = process.env.VERIFY_ACTION_DUPLICATES === '1'
  const staleNode = duplicate ? `<custom:properties>${property('actionIds', [actions.save.id])}${property('actionNames', ['Save Draft'])}</custom:properties>` : ''
  const task = (id, name) => taskBase(id, name).replace('<bpmn:extensionElements>', `<bpmn:extensionElements>${staleNode}`)
  const xml = `<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" xmlns:custom="http://workflow.platform/schema/custom" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" targetNamespace="ActionOrderAcceptance"><bpmn:process id="${processKey}" name="Action order acceptance" isExecutable="true">${props([['globalActionIds', [actions.reject.id]], ['globalActionNames', ['Reject']]])}<bpmn:startEvent id="start">${props([['formId', forms.process.id], ['formName', forms.process.formName]])}</bpmn:startEvent>${task('review_a', 'Department review')}${task('review_b', 'Finance review')}<bpmn:endEvent id="end"/><bpmn:sequenceFlow id="flow_1" sourceRef="start" targetRef="intake"/><bpmn:userTask id="intake" name="Request submission" flowable:assignee="\${initiator}">${props([['assigneeType', 'INITIATOR'], ['formId', forms.process.id], ['formName', forms.process.formName]])}</bpmn:userTask><bpmn:sequenceFlow id="flow_intake" sourceRef="intake" targetRef="review_a"/><bpmn:sequenceFlow id="flow_2" sourceRef="review_a" targetRef="review_b"/><bpmn:sequenceFlow id="flow_3" sourceRef="review_b" targetRef="end"/></bpmn:process><bpmndi:BPMNDiagram id="Diagram"><bpmndi:BPMNPlane id="Plane" bpmnElement="${processKey}">${[['start',150,200,36,36],['intake',250,178,120,80],['review_a',440,178,120,80],['review_b',630,178,120,80],['end',820,200,36,36]].map(([id,x,y,width,height])=>`<bpmndi:BPMNShape id="s_${id}" bpmnElement="${id}"><dc:Bounds x="${x}" y="${y}" width="${width}" height="${height}"/></bpmndi:BPMNShape>`).join('')}${[['flow_1',186,250],['flow_intake',370,440],['flow_2',560,630],['flow_3',750,820]].map(([id,x,y])=>`<bpmndi:BPMNEdge id="e_${id}" bpmnElement="${id}"><di:waypoint x="${x}" y="218"/><di:waypoint x="${y}" y="218"/></bpmndi:BPMNEdge>`).join('')}</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`
  const staleGlobal = `<legacy:properties xmlns:legacy="http://custom.bpmn.io/schema">${property('globalActionIds', [actions.save.id]).replaceAll('custom:', 'legacy:')}${property('globalActionNames', ['Save Draft']).replaceAll('custom:', 'legacy:')}</legacy:properties>`
  return duplicate ? xml.replace('<bpmn:extensionElements>', `<bpmn:extensionElements>${staleGlobal}`) : xml

}
function legacyDirectory(catalog) {
  assert.equal(catalog.code, unit.code)
  assert.equal(catalog.name, unit.name)
  const compose = `${root}../deploy/environments/dev/docker-compose.dev.yml`
  const env = `${root}../deploy/environments/dev/.env`
  const sql = "SELECT count(*) FROM sys_action_definitions WHERE function_unit_id=:'catalogId' AND source_id IS NULL;\n"
  const output = execFileSync('docker', ['compose','-f',compose,'--env-file',env,'exec','-T','postgres','sh','-c',
    'psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v catalogId="$1"', '--',catalog.id], {input:sql,encoding:'utf8'})
  assert.equal(output.trim(),'3','The imported legacy package must produce three actions without source IDs')
  evidence.checks.push({step:'legacy-directory',catalogId:catalog.id,sourceIds:null})
}
async function waitTask(instanceId, nodeId) {
  const deadline = Date.now() + 30000
  do {
    const tasks = await runtime('POST', '/tasks/todo/query', { page: 0, size: 100, filters: [] })
    const task = tasks.content.find(item => item.processInstanceId === instanceId && item.taskDefinitionKey === nodeId)
    if (task) return task
    assert(Date.now() < deadline, `No task on ${nodeId} for QA instance ${instanceId}`)
    await new Promise(resolve => setTimeout(resolve, 500))
  } while (true)
}
async function showTask(task, expected, label) {
  await portal.goto(`${origin}/portal/tasks/${task.taskId}`, { waitUntil: 'networkidle' })
  const detail = await runtime('GET', `/tasks/${task.taskId}`)
  assert.deepEqual(detail.actions.map(action => action.actionName), expected)
  await portal.goto(`${origin}/portal/tasks/${task.taskId}`, { waitUntil: 'networkidle' })
  const buttonLabels = expected.map(name => name === 'Save Draft' ? 'Save' : name)
  await portal.waitForFunction(names => JSON.stringify(Array.from(document.querySelectorAll('.right-actions button'), button => button.textContent.trim())) === JSON.stringify(names), buttonLabels)
  assert.equal(await portal.locator('.right-actions button').count(), expected.length)
  assert.equal(await portal.locator('.el-message--error').count(), 0, 'Task page must not show an API error toast')
  const boxes = await portal.locator('.right-actions button').evaluateAll(buttons => buttons.map(button => ({ text: button.textContent.trim(), x: button.getBoundingClientRect().x, y: button.getBoundingClientRect().y })))
  assert(boxes.every((button, index) => index === 0 || button.y > boxes[index-1].y || button.x > boxes[index-1].x))
  await portal.screenshot({ path: `${output}/${date}_action-order-${label}.png`, fullPage: true, animations: 'disabled' })
  evidence.checks.push({ label, expected, actual: detail.actions.map(action => action.actionName), displayedButtons: boxes.map(button => button.text), taskId: task.taskId })
  console.log(JSON.stringify({ step: label, buttons: expected, taskId: task.taskId }))
}
async function deploy() {
  const validation = await design('GET', `/function-units/${unit.id}/validate`)
  assert.equal(validation.valid,true)
  await design('POST', `/function-units/${unit.id}/publish?changeLog=Legacy-action-QA`)
  const response=await dw.request.get(`${origin}/api/v1/export-import/function-units/${unit.id}/export`)
  assert(response.ok())
  const legacy=execFileSync('python3',['-c',`
import io,json,sys,zipfile
source=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read()))
output=io.BytesIO()
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as target:
 for entry in source.infolist():
  data=source.read(entry.filename)
  if entry.filename.startswith('actions/') and entry.filename.endswith('.json'):
   value=json.loads(data);value.pop('actionId',None);data=json.dumps(value).encode()
  target.writestr(entry.filename,data)
sys.stdout.buffer.write(output.getvalue())
`],{input:await response.body()})
  const imported=await manage('POST','/function-units/import',{fileName:'legacy-actions.zip',fileContent:legacy.toString('base64')})
  const id=imported.functionUnit.id
  if(!catalogs.includes(id))catalogs.push(id)
  const checked=await manage('POST',`/function-units/${id}/validate`)
  assert.equal(checked.valid,true,JSON.stringify(checked))
  await manage('POST',`/function-units-import/${id}/deploy`,{environment:'DEVELOPMENT',autoEnable:true,deployToFlowable:true})
  const catalog=await manage('GET',`/function-units/${id}`)
  const user=await portal.evaluate(()=>JSON.parse(localStorage.getItem('ws_up_user')))
  const grants=await manage('GET',`/function-units/${id}/access`)
  if(!grants.some(grant=>grant.roleId===user.activeRoleId))await manage('POST',`/function-units/${id}/access`,{roleId:user.activeRoleId})
  legacyDirectory(catalog)
  console.log(JSON.stringify({step:'legacy-FU-deployed',unitId:unit.id,catalogId:id,version:catalog.version}))
  return catalog
}
async function start(catalog, suffix) {
  const started = await runtime('POST', `/processes/${encodeURIComponent(unit.code)}/start`, { batchStartContext: await readBatchStartContext(portal, catalog.id), formData: { case_key: `QA-ORDER-${suffix}`, caption: 'Synthetic action order acceptance' }, remark: 'Isolated action order acceptance' })
  const id = started.processInstanceId || started.id
  assert(id)
  instances.push(id)
  console.log(JSON.stringify({ step: 'QA-instance-started', instanceId: id }))
  return id
}
try {
  await mkdir(output, { recursive: true })
  await loginViaDwPassword(dw)
  await loginViaAdminPassword(admin)
  await loginViaPortalPassword(portal)
  // The login helper opens the unauthenticated SPA first; its /auth/me 401 is expected.
  authenticated = true
  const suffix = Date.now().toString(36)
  const groups = await design('GET', '/function-units/my-dev-groups')
  assert(groups.publicGroupId)
  unit = await design('POST', '/function-units', { name: `Action Legacy QA ${suffix}`, tags: ['action-order-acceptance-synthetic'], virtualGroupIds: [groups.publicGroupId], description: 'Isolated end-to-end action order acceptance. No business data.' })
  console.log(JSON.stringify({ step: 'QA-FU-created', unitId: unit.id, code: unit.code }))
  const base = `/function-units/${unit.id}`
  const table = await design('POST', `${base}/tables`, { tableName: `qa_order_${suffix}`, tableType: 'MAIN', tableDisplayName: 'QA action order', fields: [
    { fieldName: 'case_key', dataType: 'VARCHAR', length: 64, nullable: false, isPrimaryKey: true, pkGeneration: { strategy: 'manual' }, displayName: 'Case key' },
    { fieldName: 'caption', dataType: 'VARCHAR', length: 100, nullable: true, displayName: 'Caption' },
  ] })
  const forms = {}
  for (const [key, type] of [['process','PROCESS'], ['task','TASK']]) {
    const form = await design('POST', `${base}/forms`, { formName: `QA ${type}`, formType: type, scene: 'TASK', configJson: { rule: [], options: {} } })
    const binding = await design('POST', `${base}/forms/${form.id}/bindings`, { tableId: table.id, bindingType: 'PRIMARY', bindingMode: 'EDITABLE', sortOrder: 0 })
    await design('PUT', `${base}/forms/${form.id}`, { formName: form.formName, formType: type, scene: 'TASK', configJson: { rule: [
      { type: 'input', field: 'case_key', title: 'Case key', props: { _bindingId: binding.id } },
      { type: 'input', field: 'caption', title: 'Caption', props: { _bindingId: binding.id } },
    ], options: { form: { labelPosition: 'top' } } } })
    forms[key] = form
  }
  const actions = {}
  for (const [key,name,type] of [['save','Save Draft','SAVE'],['approve','Approve','APPROVE'],['reject','Reject','REJECT']]) actions[key] = await design('POST', `${base}/actions`, { actionName: name, actionType: type, configJson: {} })
  await design('POST', `${base}/process`, { bpmnXml: diagram(actions, forms, unit.code) })
  await dw.goto(`${origin}/dev/function-units/${unit.id}`, { waitUntil: 'networkidle' })
  const node = id => dw.locator(`.djs-element[data-element-id="${id}"] .djs-hit`).first()
  const order = dw.getByTestId('node-action-order')
  const expectNode = async names => dw.waitForFunction(expected => JSON.stringify(Array.from(document.querySelectorAll('[data-testid="node-action-order"] .action-order-name'), row => row.textContent.replace(/\s+/g,' ').trim())) === JSON.stringify(expected), names)
  await node('review_a').click()
  await expectNode(['1. Approve','2. Save Draft'])
  await order.locator('.action-order-name').last().dragTo(order.locator('.action-order-name').first())
  await expectNode(['1. Save Draft','2. Approve'])
  const save = async () => {
    const response = dw.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith(`${base}/process`))
    await dw.locator('.process-designer > .designer-toolbar').getByRole('button',{name:'Save',exact:true}).click()
    assert((await response).ok())
  }
  await save()
  await dw.screenshot({ path: `${output}/${date}_action-order-dw-v1.png`, fullPage: true, animations: 'disabled' })
  const v1 = await deploy()
  const firstId = await start(v1, `${suffix}-v1`)
  const first = await waitTask(firstId, 'review_a')
  await showTask(first, ['Save Draft','Approve','Reject'], 'v1-node-a')
  // Reopen the designer after deployment, as a user returning to edit the next release.
  await dw.goto(`${origin}/dev/function-units/${unit.id}`, { waitUntil: 'networkidle' })
  await node('review_a').click()
  await expectNode(['1. Save Draft','2. Approve'])
  // Deploy a different sequence, then prove the old instance still uses its bound release.
  await order.locator('.action-order-name').first().dragTo(order.locator('.action-order-name').last())
  await expectNode(['1. Approve','2. Save Draft'])
  await save()
  const v2 = await deploy()
  assert.notEqual(v1.id, v2.id, 'Second FU deployment must produce a distinct published catalog')
  const secondId = await start(v2, `${suffix}-v2`)
  const second = await waitTask(secondId, 'review_a')
  await showTask(second, ['Approve','Save Draft','Reject'], 'v2-node-a')
  await showTask(first, ['Save Draft','Approve','Reject'], 'v1-still-pinned')
  await runtime('POST', `/tasks/${first.taskId}/complete`, { taskId: first.taskId, action: 'APPROVE', actionId: String(actions.approve.id), comment: 'QA transition to next node' })
  const next = await waitTask(firstId, 'review_b')
  await showTask(next, ['Approve','Save Draft','Reject'], 'v1-node-b')
  assert.deepEqual(pageErrors, [])
  assert.deepEqual(failedRequests, [])
  await writeFile(`${output}/${date}_action-order-deployment.json`, JSON.stringify({ result: 'PASS', unitId: unit.id, ...evidence }, null, 2))
  console.log(JSON.stringify({ result: 'PASS', ...evidence }))
} catch (error) {
  await dw.screenshot({ path: `${output}/${date}_action-order-dw-failure.png`, fullPage: true, animations: 'disabled' }).catch(() => {})
  await portal.screenshot({ path: `${output}/${date}_action-order-e2e-failure.png`, fullPage: true, animations: 'disabled' }).catch(() => {})
  console.error(JSON.stringify({ stage: 'E2E-failure', unitId: unit?.id, catalogs, instances, pageErrors, failedRequests, message: error.message }))
  if (phase === 'red') {
    assert(String(error.message).includes('409') || String(error.message).includes('VERSION_SNAPSHOT_INCOMPLETE'))
    await writeFile(`${output}/${date}_legacy-reproduction.json`, JSON.stringify({result:'REPRODUCED',unitId:unit.id,catalogs,instances,error:error.message},null,2))
    console.log(JSON.stringify({result:'REPRODUCED',issue:'R4',unitId:unit.id,catalogs,instances,error:error.message}))
  } else throw error
} finally {
  for (const id of instances) {
    try { await runtime('POST', `/processes/${id}/withdraw`, { reason: 'QA acceptance cleanup' }) }
    catch (error) { console.error(`QA instance cleanup failed (${id}): ${error.message}`) }
  }
  if (catalogs.length) {
    try {
      for (const id of catalogs) {
        const catalog = await manage('GET', `/function-units/${id}`)
        assert.equal(catalog.code, unit.code, 'Cleanup is restricted to this newly created QA unit')
        assert.equal(catalog.name, unit.name)
        await manage('POST', `/function-units/${id}/purge-runtime-data`)
      }
      await manage('DELETE', `/function-units/${catalogs.at(-1)}`)
      console.log('QA runtime purged and published catalogs archived')
    }
    catch (error) { console.error(`QA catalog archive failed: ${error.message}`) }
  }
  if (unit) {
    try { await design('DELETE', `/function-units/${unit.id}`) }
    catch (error) { console.error(`QA design archive failed: ${error.message}`) }
  }
  await browser.close()
}
