/** Creates only synthetic, explicitly tagged FU designs through the real DW API. */
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'

const origin = 'http://localhost:3000'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  await loginViaDwPassword(page)
  const api = async (method, path, data) => {
    const response = await page.request.fetch(`${origin}/api/v1${path}`, { method, data })
    const body = await response.json()
    if (!response.ok() || body.success === false) {
      throw new Error(`${method} ${path}: HTTP ${response.status()} ${body.message || body.error?.message || ''}`)
    }
    return body.data
  }
  const suffix = process.env.FIXTURE_SUFFIX || Date.now().toString(36)
  if (!/^[a-z0-9]+$/.test(suffix)) throw new Error('Synthetic suffix must be an alphanumeric fixture identifier')
  if (process.env.FIXTURE_UNIT_ID && !/^\d+$/.test(process.env.FIXTURE_UNIT_ID)) throw new Error('Invalid synthetic FU ID')
  const automaticPk = process.env.FIXTURE_PK_STRATEGY === 'uuid'
  const readonlyNumber = process.env.FIXTURE_READONLY_NUMBER === '1'
  const readonlyDefault = process.env.FIXTURE_READONLY_DEFAULT ?? '0'
  const pkField = automaticPk ? 'entry_token' : 'line_key'
  const fkField = automaticPk ? 'owner_ref' : 'case_ref'
  const textField = automaticPk ? 'item_label' : 'caption'
  const numberField = automaticPk ? 'units' : 'amount'
  const teams = await api('GET', '/function-units/my-dev-groups')
  const teamId = teams.groups?.[0]?.id
  if (!teamId) throw new Error('Test account has no selectable development team')
  const unit = process.env.FIXTURE_UNIT_ID ? await api('GET', `/function-units/${process.env.FIXTURE_UNIT_ID}`) : await api('POST', '/function-units', {
    name: `Batch verification ${suffix}`, tags: ['batch-verification-synthetic'],
    virtualGroupIds: [teamId],
    description: 'Isolated synthetic acceptance fixture. No production business data.',
  })
  if (!unit.tags?.includes('batch-verification-synthetic')) throw new Error('Refusing non-synthetic FU')
  console.log(JSON.stringify({ step: 'unit-created', fixtureUnitId: unit.id }))
  const base = `/function-units/${unit.id}`
  const existingTables = await api('GET', `${base}/tables`)
  const main = existingTables.find(t => t.tableType === 'MAIN') || await api('POST', `${base}/tables`, {
    tableName: `batch_case_${suffix}`, tableType: 'MAIN', tableDisplayName: 'Synthetic case',
    fields: [{ fieldName: 'case_key', dataType: 'VARCHAR', length: 64, nullable: false,
      isPrimaryKey: true, pkGeneration: { strategy: 'manual' }, displayName: 'Case key' }],
  })
  const child = existingTables.find(t => t.tableType === 'SUB') || await api('POST', `${base}/tables`, {
    tableName: `batch_lines_${suffix}`, tableType: 'SUB', tableDisplayName: 'Synthetic lines',
    fields: [
      { fieldName: pkField, dataType: 'VARCHAR', length: 64, nullable: false,
        isPrimaryKey: true, pkGeneration: { strategy: automaticPk ? 'uuid' : 'manual' }, displayName: 'Line key' },
      { fieldName: fkField, dataType: 'VARCHAR', length: 64, nullable: false,
        isForeignKey: true, refTableId: main.id, refPrimaryKeyFields: ['case_key'], displayName: 'Case reference' },
      { fieldName: textField, dataType: 'VARCHAR', length: 100, nullable: false, displayName: 'Caption' },
      { fieldName: numberField, dataType: 'INTEGER', nullable: false, displayName: 'Amount',
        ...(readonlyNumber && readonlyDefault !== 'missing' ? { defaultValue: readonlyDefault } : {}) },
    ],
  })
  const forms = []
  const existingForms = await api('GET', `${base}/forms`)
  for (const formType of ['PROCESS', 'TASK']) {
    const form = existingForms.find(f => f.formType === formType) || await api('POST', `${base}/forms`, {
      formName: `Synthetic ${formType}`, formType, scene: 'TASK', configJson: { rule: [], options: {} },
    })
    const bindings = await api('GET', `${base}/forms/${form.id}/bindings`)
    const mainBinding = bindings.find(b => b.bindingType === 'PRIMARY') || await api('POST', `${base}/forms/${form.id}/bindings`, {
      tableId: main.id, bindingType: 'PRIMARY', bindingMode: 'EDITABLE', sortOrder: 0,
    })
    const binding = bindings.find(b => b.bindingType === 'SUB') || await api('POST', `${base}/forms/${form.id}/bindings`, {
      tableId: child.id, bindingType: 'SUB', bindingMode: 'EDITABLE', subMode: 'FULL',
      bindingLinkMode: 'structuralFk', foreignKeyField: fkField, sortOrder: 1,
    })
    const rowRule = [
      ...(!automaticPk ? [{ type: 'input', field: pkField, title: 'Line key', validate: [{ required: true }] }] : []),
      { type: 'input', field: textField, title: 'Caption', validate: [{ required: true }] },
      { type: 'inputNumber', field: numberField, title: 'Amount', validate: [{ required: true }] },
    ]
    const configJson = {
      rule: [
        { type: 'input', field: 'case_key', title: 'Case key', props: { _bindingId: mainBinding.id } },
        { type: 'subTable', field: `lines_${binding.id}`, title: 'Synthetic lines',
          batchControlId: `batch-${suffix}-${formType}`, props: {
            _bindingId: binding.id, allowAdd: true, allowEdit: true, allowDelete: true,
            allowBatchAdd: true, allowBatchEdit: true, allowBatchDelete: true, allowBatchAssign: false,
          } },
      ], options: { form: { labelPosition: 'top' } },
      subForms: { [binding.id]: { rule: rowRule, options: {} } },
      batchForms: { [binding.id]: { schemaVersion: 1, rule: rowRule, options: {} } },
    }
    await api('PUT', `${base}/forms/${form.id}`, {
      formName: form.formName, formType, scene: 'TASK', configJson,
      fieldPermissions: Object.fromEntries(['case_key', ...[pkField, textField, numberField].map(f => `${binding.id}:${f}`)]
        .map(f => [f, readonlyNumber && formType === 'TASK' && f === `${binding.id}:${numberField}` ? 'READONLY' : 'EDITABLE'])),
    })
    forms.push({ id: form.id, type: formType, bindingId: binding.id })
  }
  const property = (name, value) => `<custom:property name="${name}" value="${value}"/>`
  const bpmnXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" xmlns:custom="http://workflow.platform/schema/custom" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" targetNamespace="BatchVerification">
<bpmn:process id="batch_verify_${suffix}" name="Synthetic batch verification" isExecutable="true">
<bpmn:startEvent id="Start"><bpmn:extensionElements><custom:properties>${property('formId', forms[0].id)}</custom:properties></bpmn:extensionElements></bpmn:startEvent>
<bpmn:sequenceFlow id="StartToIntake" sourceRef="Start" targetRef="Intake"/>
<bpmn:userTask id="Intake" name="Synthetic intake" flowable:assignee="\${initiator}"><bpmn:extensionElements><custom:properties>${property('formId', forms[0].id)}${property('assigneeType', 'INITIATOR')}</custom:properties></bpmn:extensionElements></bpmn:userTask>
<bpmn:sequenceFlow id="IntakeToReview" sourceRef="Intake" targetRef="Review"/>
<bpmn:userTask id="Review" name="Synthetic batch review" flowable:assignee="\${initiator}"><bpmn:extensionElements><custom:properties>${property('formId', forms[1].id)}${property('assigneeType', 'INITIATOR')}</custom:properties></bpmn:extensionElements></bpmn:userTask>
<bpmn:sequenceFlow id="ReviewToEnd" sourceRef="Review" targetRef="End"/><bpmn:endEvent id="End"/>
</bpmn:process>
<bpmndi:BPMNDiagram id="FixtureDiagram"><bpmndi:BPMNPlane id="FixturePlane" bpmnElement="batch_verify_${suffix}">
<bpmndi:BPMNShape id="StartShape" bpmnElement="Start"><dc:Bounds x="100" y="112" width="36" height="36"/></bpmndi:BPMNShape>
<bpmndi:BPMNShape id="IntakeShape" bpmnElement="Intake"><dc:Bounds x="180" y="90" width="100" height="80"/></bpmndi:BPMNShape>
<bpmndi:BPMNShape id="ReviewShape" bpmnElement="Review"><dc:Bounds x="330" y="90" width="100" height="80"/></bpmndi:BPMNShape>
<bpmndi:BPMNShape id="EndShape" bpmnElement="End"><dc:Bounds x="480" y="112" width="36" height="36"/></bpmndi:BPMNShape>
<bpmndi:BPMNEdge id="StartEdge" bpmnElement="StartToIntake"><di:waypoint x="136" y="130"/><di:waypoint x="180" y="130"/></bpmndi:BPMNEdge>
<bpmndi:BPMNEdge id="IntakeEdge" bpmnElement="IntakeToReview"><di:waypoint x="280" y="130"/><di:waypoint x="330" y="130"/></bpmndi:BPMNEdge>
<bpmndi:BPMNEdge id="ReviewEdge" bpmnElement="ReviewToEnd"><di:waypoint x="430" y="130"/><di:waypoint x="480" y="130"/></bpmndi:BPMNEdge>
</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`
  await api('POST', `${base}/process`, { bpmnXml })
  const validation = await api('GET', `${base}/validate`)
  console.log(JSON.stringify({ step: 'design-created', fixtureUnitId: unit.id, forms, validation }))
  const deploy = await api('POST', `${base}/deploy`, { environment: 'DEVELOPMENT', autoEnable: true,
    conflictStrategy: 'FAIL', changeLog: 'Synthetic batch acceptance fixture' })
  console.log(JSON.stringify({ step: 'deploy-requested', fixtureUnitId: unit.id, deploy }))
  await waitForFixtureDeployment(api, deploy.deploymentId)
  console.log(JSON.stringify({ step: 'deployed', fixtureUnitId: unit.id }))
} finally {
  await browser.close()
}
