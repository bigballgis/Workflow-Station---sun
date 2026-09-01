/**
 * Create a screenshot-friendly Purchase Request Function Unit on local DW.
 * Login: developer / password. Origin: http://localhost:3000
 *
 * Prints HELP_GUIDE_FU_ID. Then capture (PowerShell):
 *   $env:HELP_GUIDE_FU_ID='<id>'; node scripts/capture-help-guide-images.mjs
 *
 * Deploys to Admin/Portal afterwards (set HELP_GUIDE_DEPLOY=0 to skip).
 */
const ORIGIN = (process.env.HELP_GUIDE_ORIGIN ?? 'http://localhost:3000').replace(/\/$/, '')
const FU_NAME = 'Purchase Request'
const USER = process.env.LOGIN_USER ?? 'developer'
const PASS = process.env.LOGIN_PASS ?? 'password'

function formula(source, scope, ast, dependsOn) {
  return {
    version: 1,
    scope,
    source,
    ast,
    dependsOn,
    onError: 'fail',
  }
}

const LINE_TOTAL = formula(
  'quantity * unit_price',
  'row',
  {
    type: 'binary',
    op: '*',
    left: { type: 'field', name: 'quantity' },
    right: { type: 'field', name: 'unit_price' },
  },
  ['quantity', 'unit_price'],
)

const GRAND_TOTAL = formula(
  'SUM(help_pr_line.line_total)',
  'aggregate',
  { type: 'aggregate', fn: 'SUM', table: 'help_pr_line', column: 'line_total' },
  ['help_pr_line.line_total'],
)

const LINE_COUNT = formula(
  'COUNT(help_pr_line)',
  'aggregate',
  { type: 'aggregate', fn: 'COUNT', table: 'help_pr_line' },
  ['help_pr_line'],
)

const WINDOW_DAYS = formula(
  'end_date - start_date',
  'row',
  {
    type: 'binary',
    op: '-',
    left: { type: 'field', name: 'end_date' },
    right: { type: 'field', name: 'start_date' },
  },
  ['end_date', 'start_date'],
)

const OVER_BUDGET = formula(
  'IF(grand_total > 5000, "Y", "N")',
  'row',
  {
    type: 'call',
    fn: 'IF',
    args: [
      {
        type: 'binary',
        op: '>',
        left: { type: 'field', name: 'grand_total' },
        right: { type: 'number', text: '5000' },
      },
      { type: 'text', value: 'Y' },
      { type: 'text', value: 'N' },
    ],
  },
  ['grand_total'],
)

function pkField(sortOrder) {
  return {
    fieldName: 'id',
    displayName: 'ID',
    dataType: 'VARCHAR',
    length: 64,
    nullable: false,
    isPrimaryKey: true,
    pkGeneration: { strategy: 'uuid', scope: 'perTable' },
    sortOrder,
  }
}

function linePk(sortOrder) {
  return {
    fieldName: 'id',
    displayName: 'Line ID',
    dataType: 'VARCHAR',
    length: 64,
    nullable: false,
    isPrimaryKey: true,
    pkGeneration: {
      strategy: 'prefixedSequence',
      prefix: 'PR-L-',
      padWidth: 4,
      startValue: 1,
      scope: 'perTable',
    },
    sortOrder,
  }
}

function col(sortOrder, fieldName, displayName, dataType, extra = {}) {
  return {
    fieldName,
    displayName,
    dataType,
    nullable: extra.nullable ?? true,
    sortOrder,
    ...extra,
  }
}

const AUDIT_FIELDS = new Set(['created_at', 'created_by', 'updated_at', 'updated_by'])

function asRows(payload) {
  if (Array.isArray(payload)) return payload
  if (payload && Array.isArray(payload.records)) return payload.records
  if (payload && Array.isArray(payload.content)) return payload.content
  return []
}

/** Same include rules as DW `shouldIncludeFieldOnFormCanvas`. */
function onCanvas(field) {
  if (!field?.fieldName?.trim()) return false
  if (AUDIT_FIELDS.has(String(field.fieldName).toLowerCase())) return false
  if (field.isForeignKey && field.fkDisplayMode === 'hidden') return false
  return true
}

/** Same mapping as DW `fieldToFormRule` + `applyTableFieldMetaToFormRule`. */
function fieldToRule(field) {
  const title = field.displayName || field.fieldName
  const validate = []
  if (field.nullable === false) {
    validate.push({
      required: true,
      message: `${title} is required`,
      trigger: 'blur',
    })
  }
  if (field.fieldName === 'scenario') {
    return {
      field: 'scenario',
      title,
      type: 'select',
      options: [
        { label: 'A', value: 'A' },
        { label: 'B', value: 'B' },
        { label: 'C', value: 'C' },
      ],
      props: {
        placeholder: `Please select ${title}`,
        clearable: true,
        filterable: true,
      },
      validate,
    }
  }
  const base = { field: field.fieldName, title, props: {}, validate }
  let rule
  switch (field.dataType) {
    case 'TEXT':
      rule = {
        ...base,
        type: 'input',
        props: { type: 'textarea', placeholder: `Please input ${title}`, rows: 3 },
      }
      break
    case 'INTEGER':
    case 'BIGINT':
      rule = {
        ...base,
        type: 'inputNumber',
        props: { placeholder: `Please input ${title}`, precision: 0 },
      }
      break
    case 'DECIMAL':
      rule = {
        ...base,
        type: 'inputNumber',
        props: { placeholder: `Please input ${title}`, precision: field.scale || 2 },
      }
      break
    case 'DATE':
      rule = {
        ...base,
        type: 'datePicker',
        props: { type: 'date', placeholder: `Please input ${title}`, valueFormat: 'YYYY-MM-DD' },
      }
      break
    default:
      rule = {
        ...base,
        type: 'input',
        props: {
          placeholder: `Please input ${title}`,
          maxlength: field.length || 255,
          showWordLimit: true,
        },
      }
  }
  const autoPk = field.isPrimaryKey && field.pkGeneration && field.pkGeneration.strategy !== 'manual'
  const readonlyFk = field.isForeignKey && field.fkDisplayMode !== 'hidden'
  if (autoPk || field.isComputed || readonlyFk) {
    rule = { ...rule, readonly: true, props: { ...rule.props, readonly: true } }
  }
  return rule
}

function listColumns(fields) {
  return fields
    .filter(onCanvas)
    .filter((f) => !f.isForeignKey)
    .map((f) => ({
      fieldName: f.fieldName,
      dataType: f.dataType,
      nullable: f.nullable !== false,
      isPrimaryKey: !!f.isPrimaryKey,
      displayName: f.displayName || f.fieldName,
      columnType: 'field',
    }))
}

/** Scenario change: A → Start date / Need-by date required (main form + line Add/Edit). */
const SCENARIO_REQUIRED_EVENT =
  "$FNX:\nvar on = $inject.value === 'A'\n$inject.api.required(on, ['start_date', 'end_date'])"

function withScenarioRequiredEvent(rule) {
  if (rule.field !== 'scenario') return rule
  return {
    ...rule,
    on: { change: SCENARIO_REQUIRED_EVENT },
  }
}

function buildFormConfigJson(mainFields, lineFields, subBindingId) {
  const mainRules = mainFields.filter(onCanvas).map(fieldToRule).map(withScenarioRequiredEvent)
  const lineRules = lineFields.filter(onCanvas).map(fieldToRule).map(withScenarioRequiredEvent)
  const key = String(subBindingId)
  return {
    rule: [
      ...mainRules,
      {
        type: 'subTable',
        _bindingId: subBindingId,
        title: 'Line items',
        props: {},
      },
    ],
    options: { form: { labelPosition: 'left' } },
    subForms: {
      [key]: {
        rule: lineRules,
        options: { form: { labelPosition: 'left' } },
      },
    },
    subListViews: {
      [key]: { columns: listColumns(lineFields) },
    },
  }
}

async function ensureBinding(dw, fuId, formId, payload) {
  const bindings = asRows(await dw('GET', `/api/v1/function-units/${fuId}/forms/${formId}/bindings`))
  const hit = bindings.find(
    (b) => b.bindingType === payload.bindingType && Number(b.tableId) === Number(payload.tableId),
  )
  if (hit) return hit
  return dw('POST', `/api/v1/function-units/${fuId}/forms/${formId}/bindings`, payload)
}

async function configureProcessForm(dw, fuId, form, mainTable, subTable, mainFieldList, lineFieldList) {
  const formId = form.id
  await ensureBinding(dw, fuId, formId, {
    tableId: mainTable.id,
    bindingType: 'PRIMARY',
    bindingMode: 'EDITABLE',
    sortOrder: 0,
  })
  const subBinding = await ensureBinding(dw, fuId, formId, {
    tableId: subTable.id,
    bindingType: 'SUB',
    bindingMode: 'EDITABLE',
    foreignKeyField: 'main_id',
    bindingLinkMode: 'structuralFk',
    subMode: 'FULL',
    sortOrder: 1,
  })
  const configJson = buildFormConfigJson(mainFieldList, lineFieldList, subBinding.id)
  await dw('PUT', `/api/v1/function-units/${fuId}/forms/${formId}`, {
    formName: form.formName,
    formType: form.formType || 'PROCESS',
    scene: form.scene || 'TASK',
    boundTableId: mainTable.id,
    description: form.description || form.displayName || 'Start a purchase request',
    configJson,
  })
}

function xmlAttr(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
}

function bpmnXml({
  connectionUid,
  templateId,
  monitorRuleId,
  taskFormId,
  taskFormName,
  requestFormId,
  requestFormName,
  actionIds,
  actionNames,
}) {
  const monitorProps =
    monitorRuleId == null
      ? ''
      : `
            <custom:property name="emailMonitorRuleId" value="${xmlAttr(monitorRuleId)}"/>
            <custom:property name="emailMonitorEnabled" value="true"/>`
  const actionIdsXml = xmlAttr(JSON.stringify(actionIds ?? []))
  const actionNamesXml = xmlAttr(JSON.stringify(actionNames ?? []))
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom="http://workflow.platform/schema/custom" xmlns:custom_1="http://custom.bpmn.io/schema" id="Definitions_HelpPR" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_HelpPurchaseRequest" name="Purchase Request" isExecutable="true">
    <bpmn:startEvent id="StartEvent_Email" name="Start">
      <bpmn:extensionElements>
        <custom:properties>${monitorProps}
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:outgoing>Flow_StartToManager</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_ManagerReview" name="Manager Review">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="assigneeType" value="INITIATOR"/>
          <custom:property name="formId" value="${xmlAttr(taskFormId)}"/>
          <custom:property name="formName" value="${xmlAttr(taskFormName)}"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="requestFormId" value="${xmlAttr(requestFormId)}"/>
          <custom:property name="requestFormName" value="${xmlAttr(requestFormName)}"/>
          <custom:property name="actionIds" value="${actionIdsXml}"/>
          <custom:property name="actionNames" value="${actionNamesXml}"/>
        </custom:properties>
        <custom_1:properties>
          <custom_1:values name="formId" value="${xmlAttr(taskFormId)}"/>
          <custom_1:values name="formName" value="${xmlAttr(taskFormName)}"/>
        </custom_1:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_StartToManager</bpmn:incoming>
      <bpmn:outgoing>Flow_ManagerToSend</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:sendTask id="Activity_SendNotice" name="Send approval notice">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="sendMode" value="email"/>
          <custom:property name="connectionId" value="${xmlAttr(connectionUid)}"/>
          <custom:property name="emailTo" value="\${assigneeEmail}"/>
          <custom:property name="emailTemplateId" value="${xmlAttr(templateId)}"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_ManagerToSend</bpmn:incoming>
      <bpmn:outgoing>Flow_SendToEnd</bpmn:outgoing>
    </bpmn:sendTask>
    <bpmn:endEvent id="EndEvent_1" name="Done">
      <bpmn:incoming>Flow_SendToEnd</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_StartToManager" sourceRef="StartEvent_Email" targetRef="Task_ManagerReview"/>
    <bpmn:sequenceFlow id="Flow_ManagerToSend" sourceRef="Task_ManagerReview" targetRef="Activity_SendNotice"/>
    <bpmn:sequenceFlow id="Flow_SendToEnd" sourceRef="Activity_SendNotice" targetRef="EndEvent_1"/>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_HelpPurchaseRequest">
      <bpmndi:BPMNShape id="StartEvent_Email_di" bpmnElement="StartEvent_Email">
        <dc:Bounds x="152" y="182" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="157" y="225" width="25" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_ManagerReview_di" bpmnElement="Task_ManagerReview">
        <dc:Bounds x="280" y="160" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_SendNotice_di" bpmnElement="Activity_SendNotice">
        <dc:Bounds x="460" y="160" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="642" y="182" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="646" y="225" width="28" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_StartToManager_di" bpmnElement="Flow_StartToManager">
        <di:waypoint x="188" y="200"/><di:waypoint x="280" y="200"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_ManagerToSend_di" bpmnElement="Flow_ManagerToSend">
        <di:waypoint x="380" y="200"/><di:waypoint x="460" y="200"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_SendToEnd_di" bpmnElement="Flow_SendToEnd">
        <di:waypoint x="560" y="200"/><di:waypoint x="642" y="200"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
}

async function login() {
  const res = await fetch(`${ORIGIN}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASS }),
  })
  const json = await res.json().catch(() => ({}))
  if (!res.ok) {
    throw new Error(`Login failed: ${json.message || `HTTP ${res.status}`}`)
  }
  const user = json.user ?? json.data?.user
  if (!user?.userId) throw new Error('Login response missing user')
  const raw = typeof res.headers.getSetCookie === 'function'
    ? res.headers.getSetCookie()
    : [res.headers.get('set-cookie')].filter(Boolean)
  const cookie = raw.map((c) => String(c).split(';')[0]).join('; ')
  return { userId: user.userId, cookie }
}

async function api(session, method, path, body) {
  const res = await fetch(`${ORIGIN}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Cookie: session.cookie,
      'X-User-Id': String(session.userId),
      ...(session.groupId ? { 'X-Dev-Group-Id': session.groupId } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = await res.json().catch(() => ({}))
  if (!res.ok || json.success === false) {
    const msg = json.error?.message || json.message || JSON.stringify(json)
    throw new Error(`${method} ${path} → HTTP ${res.status} ${msg}`)
  }
  return json.data
}

const session = await login()
const dw = (method, path, body) => api(session, method, path, body)
try {
  const groupsPayload = await dw('GET', '/api/v1/function-units/my-dev-groups')
  const groupId = groupsPayload?.groups?.[0]?.id || groupsPayload?.publicGroupId
  if (!groupId) {
    throw new Error('No developer team (virtual group) for this user — pick a team in DW first')
  }
  session.groupId = groupId
  console.log(`Using team ${groupsPayload?.groups?.[0]?.name ?? groupId}`)

  const listed = await dw('GET', '/api/v1/function-units?page=0&size=100')
  const records = listed?.records ?? listed?.content ?? (Array.isArray(listed) ? listed : [])
  const existing = records.find((fu) => fu.name === FU_NAME)
  let fuId
  if (existing?.id) {
    fuId = existing.id
    console.log(`Reusing FU id=${fuId} code=${existing.code ?? ''}`)
  } else {
    const fu = await dw('POST', '/api/v1/function-units', {
      name: FU_NAME,
      description:
        'Demo: employee or vendor-quote email starts a purchase request. Line totals roll up; manager is notified by email.',
      tags: ['help-guide', 'demo'],
      virtualGroupIds: [groupId],
    })
    fuId = fu.id
    console.log(`Created FU id=${fuId} code=${fu.code ?? ''}`)
  }

  const tables = (await dw('GET', `/api/v1/function-units/${fuId}/tables`)) ?? []
  const tableList = Array.isArray(tables) ? tables : []
  let main = tableList.find((t) => t.tableName === 'help_pr')
  let sub = tableList.find((t) => t.tableName === 'help_pr_line')
  if (!main) {
    main = await dw('POST', `/api/v1/function-units/${fuId}/tables`, {
      tableName: 'help_pr',
      tableDisplayName: 'Purchase Request',
      tableType: 'MAIN',
      description: 'Header: title, dates, rolled-up total',
    })
  }
  if (!sub) {
    sub = await dw('POST', `/api/v1/function-units/${fuId}/tables`, {
      tableName: 'help_pr_line',
      tableDisplayName: 'Line items',
      tableType: 'SUB',
      description: 'Qty × unit price',
    })
  }

  const mainHeaderFields = [
    pkField(0),
    col(1, 'request_title', 'Title', 'VARCHAR', { length: 200, nullable: false }),
    col(2, 'requester', 'Requester', 'VARCHAR', { length: 120 }),
    col(3, 'cost_center', 'Cost center', 'VARCHAR', { length: 64 }),
    col(4, 'scenario', 'Scenario', 'VARCHAR', { length: 8 }),
    col(5, 'start_date', 'Start date', 'DATE'),
    col(6, 'end_date', 'Need-by date', 'DATE'),
    col(7, 'window_days', 'Window (days)', 'INTEGER', {
      isComputed: true,
      computedField: WINDOW_DAYS,
    }),
  ]
  const mainRollupFields = [
    col(8, 'line_count', 'Line count', 'INTEGER', {
      isComputed: true,
      computedField: LINE_COUNT,
    }),
    col(9, 'grand_total', 'Grand total', 'DECIMAL', {
      precision: 18,
      scale: 2,
      isComputed: true,
      computedField: GRAND_TOTAL,
    }),
    col(10, 'over_budget', 'Over 5000', 'VARCHAR', {
      length: 1,
      isComputed: true,
      computedField: OVER_BUDGET,
    }),
  ]
  const mainFields = [...mainHeaderFields, ...mainRollupFields]
  const lineFields = [
    linePk(0),
    col(1, 'main_id', 'Request ID', 'VARCHAR', {
      length: 64,
      nullable: false,
      isForeignKey: true,
      refTableId: main.id,
      refPrimaryKeyFields: ['id'],
      fkDisplayMode: 'readonly',
      relationCardinality: 'oneToMany',
    }),
    col(2, 'item_name', 'Item', 'VARCHAR', { length: 200, nullable: false }),
    col(3, 'quantity', 'Quantity', 'INTEGER', { nullable: false }),
    col(4, 'unit_price', 'Unit price', 'DECIMAL', { precision: 18, scale: 2, nullable: false }),
    col(5, 'line_total', 'Line total', 'DECIMAL', {
      precision: 18,
      scale: 2,
      isComputed: true,
      computedField: LINE_TOTAL,
    }),
    col(6, 'notes', 'Notes', 'VARCHAR', { length: 500 }),
    col(7, 'scenario', 'Scenario', 'VARCHAR', { length: 8 }),
    col(8, 'start_date', 'Start date', 'DATE'),
    col(9, 'end_date', 'Need-by date', 'DATE'),
  ]

  await dw('PUT', `/api/v1/function-units/${fuId}/tables/${main.id}`, {
    tableName: 'help_pr',
    tableDisplayName: 'Purchase Request',
    tableType: 'MAIN',
    fields: mainHeaderFields,
  })

  await dw('PUT', `/api/v1/function-units/${fuId}/tables/${sub.id}`, {
    tableName: 'help_pr_line',
    tableDisplayName: 'Line items',
    tableType: 'SUB',
    fields: lineFields,
  })

  await dw('PUT', `/api/v1/function-units/${fuId}/tables/${main.id}`, {
    tableName: 'help_pr',
    tableDisplayName: 'Purchase Request',
    tableType: 'MAIN',
    fields: mainFields,
  })

  async function findOrCreate(listPath, createPath, match, payload) {
    const rows = asRows(await dw('GET', listPath))
    const hit = rows.find(match)
    if (hit) return hit
    return dw('POST', createPath, payload)
  }

  const outbound = await findOrCreate(
    `/api/v1/function-units/${fuId}/connections`,
    `/api/v1/function-units/${fuId}/connections`,
    (c) => c.name === 'notify@example.com',
    {
      name: 'notify@example.com',
      connectionType: 'SMTP',
      username: 'notify@example.com',
      password: 'demo-not-a-real-password',
      direction: 'OUTBOUND',
      fromName: 'PR Notify SMTP',
      enabled: true,
    },
  )
  const inbound = await findOrCreate(
    `/api/v1/function-units/${fuId}/connections`,
    `/api/v1/function-units/${fuId}/connections`,
    (c) => c.name === 'quotes@example.com',
    {
      name: 'quotes@example.com',
      connectionType: 'GMAIL',
      username: 'quotes@example.com',
      password: 'demo-not-a-real-password',
      direction: 'INBOUND',
      mailboxAddress: 'quotes@example.com',
      enabled: true,
    },
  )

  const template = await findOrCreate(
    `/api/v1/function-units/${fuId}/email-templates`,
    `/api/v1/function-units/${fuId}/email-templates`,
    (t) => t.name === 'PR Approved Notice',
    {
      name: 'PR Approved Notice',
      subject: '${request_title} approved',
      bodyHtml:
        '<h2>Purchase request approved</h2><p>Title: ${request_title}</p><p>Grand total: ${grand_total}</p><p>This notice is for design-time screenshots.</p>',
      enabled: true,
    },
  )

  const monitor = await findOrCreate(
    `/api/v1/function-units/${fuId}/email-monitors`,
    `/api/v1/function-units/${fuId}/email-monitors`,
    (m) => m.name === 'Vendor quote to PR',
    {
      name: 'Vendor quote to PR',
      enabled: true,
      connectionUid: inbound.connectionUid,
      actionType: 'START_PROCESS',
      folderLabel: 'INBOX',
      pollIntervalSeconds: 60,
      reviewOnMissing: true,
      extractionRules: {
        fields: [
          {
            target: 'request_title',
            source: 'SUBJECT',
            type: 'REGEX',
            pattern: 'Quote for (.+)',
            group: 1,
            required: true,
          },
        ],
        sampleEmail: {
          subject: 'Quote for Laptops for AP team',
          from: 'vendor@example.com',
          text: 'Please raise a purchase request for two notebooks.',
        },
      },
    },
  )

  const forms = asRows(await dw('GET', `/api/v1/function-units/${fuId}/forms`))
  let processForms = forms.filter((f) => f.formType === 'PROCESS')
  if (processForms.length === 0) {
    await dw('POST', `/api/v1/function-units/${fuId}/forms`, {
      formName: 'Request Form',
      formType: 'PROCESS',
      scene: 'TASK',
      createBothScenes: true,
      boundTableId: main.id,
      configJson: { rule: [] },
      description: 'Start a purchase request',
    })
    processForms = asRows(await dw('GET', `/api/v1/function-units/${fuId}/forms`)).filter(
      (f) => f.formType === 'PROCESS',
    )
  }
  for (const form of processForms) {
    await configureProcessForm(dw, fuId, form, main, sub, mainFields, lineFields)
    console.log(`Configured form id=${form.id} scene=${form.scene ?? 'TASK'} name=${form.formName}`)
  }

  const approve = await findOrCreate(
    `/api/v1/function-units/${fuId}/actions`,
    `/api/v1/function-units/${fuId}/actions`,
    (a) => a.actionName === 'Approve',
    {
      actionName: 'Approve',
      actionType: 'APPROVE',
      configJson: {},
      description: 'Manager approves the request',
    },
  )
  const reject = await findOrCreate(
    `/api/v1/function-units/${fuId}/actions`,
    `/api/v1/function-units/${fuId}/actions`,
    (a) => a.actionName === 'Reject',
    {
      actionName: 'Reject',
      actionType: 'REJECT',
      configJson: {},
      description: 'Manager rejects the request',
    },
  )
  const withdraw = await findOrCreate(
    `/api/v1/function-units/${fuId}/actions`,
    `/api/v1/function-units/${fuId}/actions`,
    (a) => a.actionName === 'Withdraw',
    {
      actionName: 'Withdraw',
      actionType: 'WITHDRAW',
      configJson: {},
      description: 'Requester withdraws',
    },
  )

  const taskForm = processForms.find((f) => (f.scene || 'TASK') !== 'REQUEST') ?? processForms[0]
  const requestForm = processForms.find((f) => f.scene === 'REQUEST') ?? taskForm
  const boundActions = [approve, reject, withdraw].filter((a) => a?.id != null)

  await dw('POST', `/api/v1/function-units/${fuId}/process`, {
    bpmnXml: bpmnXml({
      connectionUid: outbound.connectionUid,
      templateId: template.id,
      monitorRuleId: monitor.id,
      taskFormId: taskForm.id,
      taskFormName: taskForm.formName,
      requestFormId: requestForm.id,
      requestFormName: requestForm.formName,
      actionIds: boundActions.map((a) => a.id),
      actionNames: boundActions.map((a) => a.actionName),
    }),
  })

  await dw('PUT', `/api/v1/function-units/${fuId}/email-monitors/start-event-bindings`, {
    templateRuleId: monitor.id,
    startEventId: 'StartEvent_Email',
    processDefinitionKey: 'Process_HelpPurchaseRequest',
    filterSubject: 'Quote',
    enabled: true,
  })

  console.log(`HELP_GUIDE_FU_ID=${fuId}`)
  console.log(`Open: ${ORIGIN}/dev/function-units/${fuId}`)
  console.log(`Tables: help_pr (MAIN) / help_pr_line (SUB)`)
  console.log(`Request Form + Line Add/Edit: Scenario A → start_date + end_date required (api.required event)`)
  console.log(`Forms: Request Form (TASK) + Request Form (My Request) bound to both tables`)
  console.log(`Connections: ${outbound.connectionUid} outbound, ${inbound.connectionUid} inbound`)

  if (process.env.HELP_GUIDE_DEPLOY === '0') {
    console.log('Skipped deploy (HELP_GUIDE_DEPLOY=0)')
  } else {
    const deploy = await dw('POST', `/api/v1/function-units/${fuId}/deploy`, {
      autoEnable: true,
      changeLog: 'Help demo: designer custom xmlns + single start; form bound for testing',
    })
    let status = deploy
    const deploymentId = deploy?.deploymentId
    if (deploymentId && (deploy.status === 'DEPLOYING' || deploy.status === 'PENDING')) {
      for (let i = 0; i < 60; i += 1) {
        await new Promise((r) => setTimeout(r, 2000))
        status = await dw('GET', `/api/v1/function-units/deployments/${deploymentId}/status`)
        console.log(`Deploy ${status.status} ${status.progress ?? ''}% ${status.message ?? ''}`)
        if (status.status === 'SUCCESS' || status.status === 'FAILED' || status.status === 'ROLLED_BACK') {
          break
        }
      }
    } else {
      console.log(`Deploy ${status?.status} ${status?.message ?? ''}`)
    }
    if (status?.status !== 'SUCCESS') {
      throw new Error(`Deploy did not succeed: ${status?.status} ${status?.message ?? ''}`)
    }
    console.log(`Deployed version ${status.versionNumber ?? ''} — ready to test in Portal`)
  }
} catch (err) {
  console.error(err)
  process.exit(1)
}
