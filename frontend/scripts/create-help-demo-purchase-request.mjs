/**
 * Create a screenshot-friendly Purchase Request Function Unit on local DW.
 * Login: developer / password. Origin: http://localhost:3000
 *
 * Prints HELP_GUIDE_FU_ID. Then capture (PowerShell):
 *   $env:HELP_GUIDE_FU_ID='<id>'; node scripts/capture-help-guide-images.mjs
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

function bpmnXml({ connectionUid, templateId, monitorRuleId }) {
  const monitorProps =
    monitorRuleId == null
      ? ''
      : `
            <custom:property name="emailMonitorRuleId" value="${monitorRuleId}"/>
            <custom:property name="emailMonitorEnabled" value="true"/>`
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom="http://workflow.platform/schema/bpmn" id="Definitions_HelpPR" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_HelpPurchaseRequest" name="Purchase Request" isExecutable="true">
    <bpmn:startEvent id="StartEvent_Form" name="Form Start">
      <bpmn:outgoing>Flow_FormToManager</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:startEvent id="StartEvent_Email" name="Start">
      <bpmn:extensionElements>
        <custom:properties>${monitorProps}
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:outgoing>Flow_EmailToManager</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_ManagerReview" name="Manager Review">
      <bpmn:incoming>Flow_FormToManager</bpmn:incoming>
      <bpmn:incoming>Flow_EmailToManager</bpmn:incoming>
      <bpmn:outgoing>Flow_ManagerToSend</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:sendTask id="Activity_SendNotice" name="Send approval notice">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="sendMode" value="email"/>
          <custom:property name="connectionId" value="${connectionUid}"/>
          <custom:property name="emailTo" value="\${assigneeEmail}"/>
          <custom:property name="emailTemplateId" value="${templateId}"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_ManagerToSend</bpmn:incoming>
      <bpmn:outgoing>Flow_SendToEnd</bpmn:outgoing>
    </bpmn:sendTask>
    <bpmn:endEvent id="EndEvent_1" name="Done">
      <bpmn:incoming>Flow_SendToEnd</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_FormToManager" sourceRef="StartEvent_Form" targetRef="Task_ManagerReview"/>
    <bpmn:sequenceFlow id="Flow_EmailToManager" sourceRef="StartEvent_Email" targetRef="Task_ManagerReview"/>
    <bpmn:sequenceFlow id="Flow_ManagerToSend" sourceRef="Task_ManagerReview" targetRef="Activity_SendNotice"/>
    <bpmn:sequenceFlow id="Flow_SendToEnd" sourceRef="Activity_SendNotice" targetRef="EndEvent_1"/>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_HelpPurchaseRequest">
      <bpmndi:BPMNShape id="StartEvent_Form_di" bpmnElement="StartEvent_Form">
        <dc:Bounds x="152" y="242" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="136" y="285" width="69" height="14"/></bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="StartEvent_Email_di" bpmnElement="StartEvent_Email">
        <dc:Bounds x="152" y="102" width="36" height="36"/>
        <bpmndi:BPMNLabel><dc:Bounds x="157" y="145" width="25" height="14"/></bpmndi:BPMNLabel>
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
      <bpmndi:BPMNEdge id="Flow_FormToManager_di" bpmnElement="Flow_FormToManager">
        <di:waypoint x="188" y="260"/><di:waypoint x="230" y="260"/><di:waypoint x="230" y="200"/><di:waypoint x="280" y="200"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_EmailToManager_di" bpmnElement="Flow_EmailToManager">
        <di:waypoint x="188" y="120"/><di:waypoint x="230" y="120"/><di:waypoint x="230" y="200"/><di:waypoint x="280" y="200"/>
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
    col(4, 'start_date', 'Start date', 'DATE'),
    col(5, 'end_date', 'Need-by date', 'DATE'),
    col(6, 'window_days', 'Window (days)', 'INTEGER', {
      isComputed: true,
      computedField: WINDOW_DAYS,
    }),
  ]
  const mainRollupFields = [
    col(7, 'line_count', 'Line count', 'INTEGER', {
      isComputed: true,
      computedField: LINE_COUNT,
    }),
    col(8, 'grand_total', 'Grand total', 'DECIMAL', {
      precision: 18,
      scale: 2,
      isComputed: true,
      computedField: GRAND_TOTAL,
    }),
    col(9, 'over_budget', 'Over 5000', 'VARCHAR', {
      length: 1,
      isComputed: true,
      computedField: OVER_BUDGET,
    }),
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
    fields: [
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
    ],
  })

  await dw('PUT', `/api/v1/function-units/${fuId}/tables/${main.id}`, {
    tableName: 'help_pr',
    tableDisplayName: 'Purchase Request',
    tableType: 'MAIN',
    fields: [...mainHeaderFields, ...mainRollupFields],
  })

  async function findOrCreate(listPath, createPath, match, payload) {
    const listed = (await dw('GET', listPath)) ?? []
    const rows = Array.isArray(listed) ? listed : []
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

  await dw('POST', `/api/v1/function-units/${fuId}/forms`, {
    formName: 'Request Form',
    formType: 'PROCESS',
    scene: 'TASK',
    createBothScenes: true,
    boundTableId: main.id,
    configJson: { rule: [] },
    description: 'Start a purchase request',
  })
  await dw('POST', `/api/v1/function-units/${fuId}/actions`, {
    actionName: 'Approve',
    actionType: 'APPROVE',
    configJson: {},
    description: 'Manager approves the request',
  })
  await dw('POST', `/api/v1/function-units/${fuId}/actions`, {
    actionName: 'Reject',
    actionType: 'REJECT',
    configJson: {},
    description: 'Manager rejects the request',
  })
  await dw('POST', `/api/v1/function-units/${fuId}/actions`, {
    actionName: 'Withdraw',
    actionType: 'WITHDRAW',
    configJson: {},
    description: 'Requester withdraws',
  })

  await dw('POST', `/api/v1/function-units/${fuId}/process`, {
    bpmnXml: bpmnXml({
      connectionUid: outbound.connectionUid,
      templateId: template.id,
      monitorRuleId: monitor.id,
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
  console.log(`Connections: ${outbound.connectionUid} outbound, ${inbound.connectionUid} inbound`)
} catch (err) {
  console.error(err)
  process.exit(1)
}
