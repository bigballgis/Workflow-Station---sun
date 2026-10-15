/** Isolated MI regression design; the source is read-only and all tasks target the test initiator. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'

const sourceId = process.env.MI_FIXTURE_SOURCE_ID
if (!/^\d+$/.test(sourceId || '')) throw new Error('Explicit MI_FIXTURE_SOURCE_ID required')
if (process.env.MI_FIXTURE_UNIT_ID && (!/^\d+$/.test(process.env.MI_FIXTURE_UNIT_ID)
  || process.env.MI_FIXTURE_UNIT_ID === sourceId)) throw new Error('Fixture must be a distinct isolated clone')
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  await loginViaDwPassword(page)
  const api = async (method, path, data) => {
    const response = await page.request.fetch(`http://localhost:3000/api/v1${path}`, { method, data })
    const body = await response.json()
    assert.equal(response.ok() && body.success, true, `${method} ${path}: ${body.error?.message || response.status()}`)
    return body.data
  }
  const sourceProcess = await api('GET', `/function-units/${sourceId}/process`)
  assert.doesNotMatch(sourceProcess.bpmnXml, /<(?:\w+:)?(?:serviceTask|callActivity|sendTask)\b/, 'No external-action process may be used as an MI fixture')
  for (const resource of ['connections', 'email-templates', 'email-monitors']) {
    const integrations = await api('GET', `/function-units/${sourceId}/${resource}`)
    assert.equal(Array.isArray(integrations) && integrations.length === 0, true, 'Fixture source must have no mail integrations')
  }
  const name = `MI verification ${Date.now().toString(36)}`
  const clone = process.env.MI_FIXTURE_UNIT_ID ? await api('GET', `/function-units/${process.env.MI_FIXTURE_UNIT_ID}`)
    : await api('POST', `/function-units/${sourceId}/clone?newName=${encodeURIComponent(name)}`)
  assert.match(clone.name, /^MI verification [a-z0-9]+$/)
  const base = `/function-units/${clone.id}`
  console.log(JSON.stringify({ step: 'isolated-mi-cloned', fixtureUnitId: clone.id }))
  const tables = await api('GET', `${base}/tables`)
  for (const table of tables) {
    const changed = table.fieldDefinitions.some(f => f.isPrimaryKey && (table.tableName.includes('people') || table.fieldDefinitions.some(c => c.fieldName === 'assignee')))
    if (!changed) continue
    const fields = table.fieldDefinitions.map(f => ({
      id: f.id, fieldName: f.fieldName, dataType: f.isPrimaryKey ? 'VARCHAR' : f.dataType,
      length: f.isPrimaryKey ? 64 : f.length, precision: f.precision, scale: f.scale,
      nullable: f.nullable, defaultValue: f.defaultValue, isPrimaryKey: f.isPrimaryKey,
      isUnique: f.isUnique, displayName: f.displayName, sortOrder: f.sortOrder,
      isForeignKey: f.isForeignKey, refTableId: f.refTableId, refPrimaryKeyFields: f.refPrimaryKeyFields,
      pkGeneration: f.isPrimaryKey && table.tableName.includes('people') ? { strategy: 'uuid' } : f.pkGenerationJson,
      isComputed: f.isComputed, computedField: f.computedFieldJson, fkDisplayMode: f.fkDisplayMode,
      relationCardinality: f.relationCardinality,
    }))
    await api('PUT', `${base}/tables/${table.id}`, { tableName: table.tableName, tableType: table.tableType,
      tableDisplayName: table.tableDisplayName, description: table.description, fields })
  }
  // Explicitly author the fixture's participant→People Details links. Do not rely on source-specific legacy references.
  const participant = tables.find(t => t.fieldDefinitions.some(f => f.fieldName === 'assignee'))
  const people = tables.find(t => t.fieldDefinitions.some(f => f.fieldName === 'sex'))
  const attachment = tables.find(t => t.tableType === 'SUB' && t.fieldDefinitions.some(f => f.dataType === 'FILE'))
  const forms = await api('GET', `${base}/forms`)
  const peopleTaskBindings = forms.filter(f => f.formType === 'TASK' && f.scene === 'TASK')
    .flatMap(f => f.tableBindings.filter(b => (b.tableId ?? b.table?.id) === people.id))
  assert.equal(peopleTaskBindings.length, 1, 'Fixture needs one explicit task People binding for cross-form Details')
  const peopleSchemaSource = forms.find(f => f.tableBindings.some(b => b.id === peopleTaskBindings[0].id))
    .configJson.subForms[String(peopleTaskBindings[0].id)]
  assert.ok(peopleSchemaSource?.rule, 'Fixture needs an explicit People schema')
  const peopleSchema = structuredClone(peopleSchemaSource)
  const peoplePkNames = people.fieldDefinitions.filter(f => f.isPrimaryKey).map(f => f.fieldName)
  for (const rule of peopleSchema.rule) {
    if (!peoplePkNames.includes(rule.field)) continue
    rule.type = 'input'
    rule._fc_drag_tag = 'input'
    delete rule.props.precision
  }
  for (const form of forms) {
    const bindingTableId = binding => binding.tableId ?? binding.table?.id
    const parentBinding = form.tableBindings.find(b => bindingTableId(b) === participant.id)
    const childBinding = form.tableBindings.find(b => bindingTableId(b) === people.id)
    const detailsBinding = childBinding ?? peopleTaskBindings[0]
    const attachmentBinding = form.tableBindings.find(b => bindingTableId(b) === attachment.id)
    if (parentBinding?.bindingType === 'SUB') {
      await api('PUT', `${base}/forms/${form.id}/bindings/${parentBinding.id}`, {
        tableId: participant.id, bindingType: parentBinding.bindingType, bindingMode: parentBinding.bindingMode,
        foreignKeyField: parentBinding.foreignKeyField, bindingLinkMode: 'miParticipantRow',
        sortOrder: parentBinding.sortOrder, subMode: parentBinding.subMode,
      })
    }
    // This synthetic fixture uses UUID keys; its linked form must not retain source numeric PK controls.
    form.configJson.subForms = { ...form.configJson.subForms, [detailsBinding.id]: structuredClone(peopleSchema) }
    let changed = true
    let attachmentPlaced = false
    const visit = value => {
      if (Array.isArray(value)) {
        if (form.formType === 'TASK' && form.scene === 'TASK') {
          // The task fixture deliberately shows People inline next to its participant grid;
          // My Requests retains the Details links, avoiding a second hidden link-only editor.
          for (let index = value.length - 1; index >= 0; index--) {
            if (value[index]?.columnType === 'linkForm') value.splice(index, 1)
          }
        }
        return value.forEach(visit)
      }
      if (!value || typeof value !== 'object') return
      if (value.type === 'subTable' || value.type === 'inlineSubForm') {
        const id = Number(value._bindingId ?? value.props?._bindingId)
        if (id === parentBinding?.id) { value.type = 'subTable'; value.title = 'Sub Task'; changed = true }
        if (id === attachmentBinding?.id) { value.title = 'Attachment'; attachmentPlaced = true; changed = true }
        if (id === childBinding?.id && form.formType === 'TASK' && form.scene === 'TASK') {
          value.type = 'inlineSubForm'; value.title = 'People'; changed = true
        }
      }
      if (value.columnType === 'linkForm' && parentBinding) {
        value.componentId = -parentBinding.id
        value.fieldName = `linkForm:${-parentBinding.id}`
        value.boundSubTableBindingId = detailsBinding.id
        value.boundSubTableName = people.tableDisplayName || people.tableName
        changed = true
      }
      Object.values(value).forEach(visit)
    }
    visit(form.configJson)
    if (childBinding && form.formType === 'TASK' && form.scene === 'TASK') {
      const placed = value => {
        if (Array.isArray(value)) return value.some(placed)
        if (!value || typeof value !== 'object') return false
        if (value.type === 'inlineSubForm' && Number(value._bindingId ?? value.props?._bindingId) === childBinding.id) return true
        return Object.values(value).some(placed)
      }
      if (!placed(form.configJson.rule)) {
        form.configJson.rule.push({ type: 'inlineSubForm', field: `fixture_people_${childBinding.id}`,
          _bindingId: childBinding.id, title: 'People', props: { _bindingId: childBinding.id } })
      }
    }
    if (attachmentBinding && !attachmentPlaced && Array.isArray(form.configJson.rule)) {
      form.configJson.rule.push({ type: 'subTable', _bindingId: attachmentBinding.id, title: 'Attachment',
        props: { allowAdd: false, allowEdit: false, allowDelete: false } })
      changed = true
    }
    if (process.env.MI_FIXTURE_BATCH_EDIT === '1' && childBinding && form.formType === 'TASK' && form.scene === 'TASK') {
      assert.equal(people.fieldDefinitions.some(field => field.fieldName === 'age'), true)
      const controlId = `mi-verification-${form.id}-${childBinding.id}`
      assert.equal(form.configJson.rule.some(rule => rule.batchControlId === controlId), false,
        'Use a fresh synthetic clone for the batch-authority fixture')
      form.configJson.rule.push({ type: 'subTable', field: `synthetic_batch_${childBinding.id}`, title: 'Synthetic batch People', batchControlId: controlId,
        props: { _bindingId: childBinding.id, allowAdd: false, allowEdit: true, allowDelete: false,
          allowBatchAdd: false, allowBatchEdit: true, allowBatchDelete: false, allowBatchAssign: false } })
      form.configJson.batchForms = { ...form.configJson.batchForms,
        [childBinding.id]: { schemaVersion: 1, rule: [{ type: 'inputNumber', field: 'age', title: 'Synthetic age' }], options: {} } }
      changed = true
    }
    if (changed) await api('PUT', `${base}/forms/${form.id}`, { formName: form.formName, formType: form.formType,
      scene: form.scene, configJson: form.configJson, fieldPermissions: form.fieldPermissions })
  }
  const workflowDesign = await api('GET', `${base}/process`)
  const bpmnXml = await page.evaluate(xml => {
    const doc = new DOMParser().parseFromString(xml, 'application/xml')
    const namespace = 'http://workflow.platform/schema/custom'
    const property = (node, name, value) => {
      let properties = [...node.getElementsByTagNameNS('*', 'properties')][0]
      if (!properties) throw new Error('Fixture node has no designer properties')
      let entry = [...properties.children].find(p => p.getAttribute('name') === name)
      if (!entry) { entry = doc.createElementNS(namespace, 'custom:property'); properties.appendChild(entry) }
      entry.setAttribute('name', name); entry.setAttribute('value', value)
    }
    const nodes = [...doc.getElementsByTagNameNS('*', 'userTask')]
    for (const node of [...doc.getElementsByTagNameNS('*', 'subProcess')]) {
      if ([...node.children].some(c => c.localName === 'multiInstanceLoopCharacteristics')) {
        property(node, 'miTaskStatusField', 'task_status')
        property(node, 'miTaskCurrentNodeField', 'task_current_node')
      }
    }
    const mi = nodes.find(node => [...node.getElementsByTagNameNS('*', 'property')].some(p => p.getAttribute('name') === 'assigneeType' && p.getAttribute('value') === 'ELEMENT_VARIABLE'))
    if (!mi) throw new Error('Fixture source has no configured MI user task')
    const assignment = [...mi.getElementsByTagNameNS('*', 'property')].filter(p => ['subTableId', 'assigneeField', 'rowIdVariable'].includes(p.getAttribute('name')))
    for (const node of nodes) {
      let parent = node.parentElement
      let inMi = false
      while (parent) {
        if (parent.localName === 'subProcess' && [...parent.children].some(c => c.localName === 'multiInstanceLoopCharacteristics')) inMi = true
        parent = parent.parentElement
      }
      property(node, 'assigneeType', inMi ? 'ELEMENT_VARIABLE' : 'INITIATOR')
      if (inMi) assignment.forEach(p => property(node, p.getAttribute('name'), p.getAttribute('value')))
      node.removeAttributeNS('http://flowable.org/bpmn', 'candidateUsers')
      node.removeAttributeNS('http://flowable.org/bpmn', 'candidateGroups')
    }
    return new XMLSerializer().serializeToString(doc)
  }, workflowDesign.bpmnXml)
  await api('POST', `${base}/process`, { bpmnXml })
  const deploy = await api('POST', `${base}/deploy`, { environment: 'DEVELOPMENT', autoEnable: true,
    conflictStrategy: 'FAIL', changeLog: 'Isolated synthetic MI regression fixture' })
  console.log(JSON.stringify({ step: 'mi-deploy-requested', fixtureUnitId: clone.id, deploymentId: deploy.deploymentId }))
  await waitForFixtureDeployment(api, deploy.deploymentId)
  console.log(JSON.stringify({ step: 'mi-deployed', fixtureUnitId: clone.id }))
} finally {
  await browser.close()
}
