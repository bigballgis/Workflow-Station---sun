/**
 * Live ATM chain: start → Assignment Correspondence save (with nested thin copy)
 * → status update → Confirmed all Assignment. Asserts Change History identity:
 * designer PK as row_identifier, no duplicate Email ADD, no phantom empty UPDATE.
 *
 * Users (password = password):
 *   123456 Index  — start
 *   e2e_lina Assign — claim Assignment
 */
const BASE = process.env.PORTAL_API ?? 'http://localhost:8082/api/portal'
const PROCESS_KEY = 'atm-20260623-gaevus'
const FU_ID = '24'
const TABLE_ID = 392
const BU = '2ca743c1-2af5-4c44-866b-ae8e1ba60acb'
const INDEX_ROLE = 'dbfc6328-3095-40f2-9e3a-efd4f55cba05'
const ASSIGN_ROLE = '5e40c0fd-7dba-4dd1-9933-eb6cf259a882'
const OPERATOR_ROLE = '25a60bcb-cd1b-4ef4-856d-93a646fe7998'
const APPROVER_ROLE = 'c5768dc1-94f8-4c39-9ab1-94fead7eee20'
const LIAM_ID = '9b9e94f5-7e69-4ed2-af2e-573d17a09943'

function unwrap(body) {
  if (body && typeof body === 'object' && 'data' in body && body.data !== undefined) {
    return body.data
  }
  return body
}

function cookieHeader(jar) {
  return [...jar.entries()].map(([k, v]) => `${k}=${v}`).join('; ')
}

function absorbCookies(res, jar) {
  const raw = typeof res.headers.getSetCookie === 'function'
    ? res.headers.getSetCookie()
    : (res.headers.get('set-cookie') ? [res.headers.get('set-cookie')] : [])
  for (const line of raw) {
    if (!line) continue
    const pair = line.split(';', 1)[0]
    const eq = pair.indexOf('=')
    if (eq > 0) jar.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim())
  }
}

async function login(username, roleId) {
  const jar = new Map()
  const headers = { 'Content-Type': 'application/json' }
  let res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ username, password: 'password' }),
  })
  absorbCookies(res, jar)
  let body = await res.json()
  if (body.loginErrorCode === 'WORKSPACE_CONTEXT_REQUIRED') {
    res = await fetch(`${BASE}/auth/login`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        username,
        password: 'password',
        workspaceBusinessUnitId: BU,
        workspaceRoleId: roleId,
      }),
    })
    absorbCookies(res, jar)
    body = await res.json()
  }
  const user = body.user || unwrap(body)?.user
  if (!res.ok || !user?.userId) {
    throw new Error(`login ${username} failed: ${res.status} ${JSON.stringify(body)}`)
  }
  console.log(`[login] ${username} ${user.userId}`)
  return {
    userId: user.userId,
    jar,
    async api(path, opts = {}) {
      const method = opts.method || 'GET'
      const res2 = await fetch(`${BASE}${path}`, {
        method,
        headers: {
          'Content-Type': 'application/json',
          'X-User-Id': user.userId,
          Cookie: cookieHeader(jar),
          ...(opts.headers || {}),
        },
        body: opts.body ? JSON.stringify(opts.body) : undefined,
      })
      absorbCookies(res2, jar)
      const text = await res2.text()
      let parsed
      try {
        parsed = text ? JSON.parse(text) : null
      } catch {
        parsed = { raw: text }
      }
      if (!res2.ok) {
        throw new Error(`${method} ${path} -> ${res2.status} ${text.slice(0, 800)}`)
      }
      return unwrap(parsed)
    },
  }
}

function transactionRow() {
  return {
    card_number: '1',
    merchant_name: 'ch-identity-e2e',
    billing_amount: 1,
    dispute_amount: 1,
    authorization_code: '1',
    post_entry_code: '1',
    transaction_date: '2026-09-10',
    case_received_date: '2026-09-10',
    case_type: { id: 'hmdc-dd-type-atm-cash', dropdown_name: 'ATM Cash Dispute' },
    dispute_reason: { id: 'hmdc-dd-rsn-no-cash', dropdown_name: 'Cash not dispensed' },
    incoming_channel: { id: 'hmdc-dd-ch-branch', dropdown_name: 'Branch' },
  }
}

function correspondenceRow(id, extras = {}) {
  return {
    row_id: extras.rowId || 'e2e-corr-identity-pk',
    correspondence_id: id,
    correspondence_channel: extras.channel === undefined ? 'Email' : extras.channel,
    correspondence_mode: extras.mode === undefined ? 'Outbound' : extras.mode,
    correspondence_type: extras.type === undefined ? 'Customer Notification' : extras.type,
    processed_date: extras.date === undefined ? '2026-09-10' : extras.date,
    ...('mdc_status' in extras ? { mdc_status: extras.mdc_status } : {}),
  }
}

function thinCopy(id, rowId) {
  return {
    row_id: rowId,
    correspondence_id: id,
    correspondence_channel: null,
    correspondence_mode: 'Outbound',
    processed_date: null,
  }
}

function fieldValuesFromForm(form) {
  const values = { ...(form.fieldValues || {}) }
  const existing = values.__subTables__ && typeof values.__subTables__ === 'object'
    ? { ...values.__subTables__ }
    : {}
  if (form.subTableBindings) {
    for (const b of form.subTableBindings) {
      const key = b.tableName
        ? `dw:${String(b.tableName).replace(/\s+/g, '_').toLowerCase()}`
        : String(b.bindingId)
      if (!Array.isArray(b.data) || b.data.length === 0) continue
      if (!Array.isArray(existing[key]) || existing[key].length === 0) {
        existing[key] = b.data
      }
    }
    values.__subTables__ = existing
  }
  return values
}

function summarizeSubTables(formData) {
  const tables = formData?.__subTables__ || {}
  const out = {}
  for (const [key, rows] of Object.entries(tables)) {
    out[key] = Array.isArray(rows)
      ? rows.map((r) => r && typeof r === 'object' ? r.row_id : r)
      : typeof rows
  }
  return out
}

function isTransactionRow(row) {
  return row && typeof row === 'object'
    && (row.card_number != null || row.assignee_id != null || row.case_row_id != null
      || row.merchant_name != null)
}

function nestCorrespondence(formData, thick, thin, topLevelAliases = true) {
  const tables = { ...(formData.__subTables__ || {}) }
  let patched = false
  for (const [key, rows] of Object.entries(tables)) {
    if (!Array.isArray(rows) || rows.length === 0) continue
    tables[key] = rows.map((row) => {
      if (!isTransactionRow(row)) return row
      patched = true
      const parent = { ...row }
      const nested = { ...(parent.__subTables__ || {}) }
      for (const nestedKey of Object.keys(nested)) {
        if (/corr/i.test(nestedKey)) delete nested[nestedKey]
      }
      nested['dw:atm_correspondence'] = [thick]
      if (thin) nested['dw:atm correspondence'] = [thin]
      parent.__subTables__ = nested
      return parent
    })
  }
  if (!patched) {
    const parent = { ...transactionRow(), __subTables__: {
      'dw:atm_correspondence': [thick],
      'dw:atm correspondence': [thin || thick],
    } }
    tables['dw:atm_transaction'] = [parent]
  }
  if (topLevelAliases) {
    tables['dw:atm_correspondence'] = [thin || thick]
    tables['dw:atm correspondence'] = [thick]
  } else {
    delete tables['dw:atm_correspondence']
    delete tables['dw:atm correspondence']
    delete tables['dw:ATM Correspondence']
  }
  formData.__subTables__ = tables
  return formData
}

function corrHistory(rows) {
  return (rows || []).filter((r) =>
    String(r.subTableName || '').toLowerCase().includes('corr')
    || String(r.changeType || '').startsWith('SUB_TABLE'))
    .filter((r) => String(r.subTableName || '').toLowerCase().includes('corr'))
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg)
}

function noPhantomEmptyUpdate(rows) {
  const bad = rows.filter((r) =>
    r.changeType === 'SUB_TABLE_ROW_UPDATE'
    && (r.oldValue == null || String(r.oldValue).trim() === '')
    && (r.newValue == null || String(r.newValue).trim() === ''))
  assert(bad.length === 0, `phantom empty UPDATE: ${JSON.stringify(bad)}`)
}

function emailAdds(rows) {
  return rows.filter((r) =>
    r.changeType === 'SUB_TABLE_ROW_ADD'
    && r.fieldName === 'correspondence_channel'
    && String(r.newValue || '').includes('Email'))
}

function stampTransactionAssignee(formData, userId) {
  const tables = formData.__subTables__ || {}
  const txKey = Object.keys(tables).find((k) => /atm.?transaction/i.test(k)) || 'dw:atm_transaction'
  const rows = Array.isArray(tables[txKey]) ? tables[txKey].map((r) => ({ ...r })) : []
  if (rows[0]) {
    rows[0] = { ...rows[0], assignee_id: userId }
    tables[txKey] = rows
    formData.__subTables__ = tables
  }
  return formData
}

async function claimIfNeeded(session, task) {
  if (!task.assignee || task.assignee !== session.userId) {
    await session.api(`/tasks/${task.taskId}/claim`, { method: 'POST' })
    console.log(`[claim] ${task.taskName} ${task.taskId}`)
  }
}

async function completeApprove(session, task, formData) {
  await session.api(`/tasks/${task.taskId}/complete`, {
    method: 'POST',
    body: { taskId: task.taskId, action: 'APPROVE', formData },
  })
  console.log(`[complete] ${task.taskName} ${task.taskId}`)
}

function assertCorrClean(hist, corrId, emailAddCount = 1) {
  noPhantomEmptyUpdate(hist)
  assert(emailAdds(hist).length === emailAddCount, `Email ADD count ${emailAdds(hist).length} expected ${emailAddCount}`)
  const drifted = hist.filter((r) => r.rowIdentifier && r.rowIdentifier !== corrId)
  assert(drifted.length === 0, `identifier drifted: ${JSON.stringify(drifted)}`)
  const cleared = hist.filter((r) =>
    r.changeType === 'SUB_TABLE_ROW_UPDATE'
    && ['correspondence_channel', 'processed_date', 'correspondence_mode', 'correspondence_type'].includes(r.fieldName)
    && r.oldValue && String(r.oldValue).trim()
    && (r.newValue == null || String(r.newValue).trim() === ''))
  assert(cleared.length === 0, `value cleared by UPDATE: ${JSON.stringify(cleared)}`)
}

async function waitForTask(session, processId, nameRe, timeoutMs = 90000, requestId) {
  const start = Date.now()
  let last = []
  while (Date.now() - start < timeoutMs) {
    const page = await session.api('/tasks/todo/query', {
      method: 'POST',
      body: { page: 0, size: 50 },
    })
    const content = page?.content || page?.records || []
    last = content.map((t) => `${t.taskName}/${t.requestId}/${t.processInstanceId}`)
    const hit = content.find((t) => {
      const name = `${t.taskName || ''} ${t.currentStepName || ''} ${t.taskDefinitionKey || ''}`
      if (!nameRe.test(name)) return false
      return t.processInstanceId === processId
        || (requestId && t.requestId === requestId)
    })
    if (hit) return hit
    await new Promise((r) => setTimeout(r, 1500))
  }
  throw new Error(`no todo matching ${nameRe} for ${processId} requestId=${requestId}; seen=${last.join(',')}`)
}

const index = await login('123456', INDEX_ROLE)
const started = await index.api(`/processes/${PROCESS_KEY}/start`, {
  method: 'POST',
  body: {
    processDefinitionKey: PROCESS_KEY,
    formData: {
      __subTables__: { 'dw:atm_transaction': [transactionRow()] },
    },
  },
})
const processId = started.processInstanceId || started.id
assert(processId, `start returned no process id: ${JSON.stringify(started)}`)
const startedDetail = await index.api(`/processes/${processId}`)
const requestId = startedDetail.requestId || started.requestId || started.businessKey
console.log(`[start] ${processId} requestId=${requestId} firstStep=${started.firstStepError || 'ok'}`)

if (started.firstStepError) {
  const cs = await waitForTask(index, processId, /case submission/i, 90000, requestId)
  console.log(`[case] ${cs.taskId} ${cs.taskName}`)
  const form = await index.api(`/tasks/${cs.taskId}/form-data`)
  await index.api(`/tasks/${cs.taskId}/complete`, {
    method: 'POST',
    body: { taskId: cs.taskId, action: 'APPROVE', formData: fieldValuesFromForm(form) },
  })
}

const assign = await login('e2e_lina', ASSIGN_ROLE)
const assignment = await waitForTask(assign, processId, /assignment/i, 90000, requestId)
console.log(`[assign] ${assignment.taskId} ${assignment.taskName} assignee=${assignment.assignee}`)
if (!assignment.assignee || assignment.assignee !== assign.userId) {
  await assign.api(`/tasks/${assignment.taskId}/claim`, { method: 'POST' })
  console.log('[assign] claimed')
}

const allocated = await index.api(
  `/processes/function-units/${FU_ID}/tables/primary-keys/allocate`,
  { method: 'POST', body: { tableId: TABLE_ID, fieldName: 'correspondence_id', count: 1 } },
)
const corrId = (allocated.values || allocated?.data?.values || [])[0]
assert(corrId && String(corrId).startsWith('Corr-'), `allocate failed: ${JSON.stringify(allocated)}`)
console.log(`[pk] ${corrId}`)

const form1 = await assign.api(`/tasks/${assignment.taskId}/form-data`)
const payload1 = nestCorrespondence(
  fieldValuesFromForm(form1),
  correspondenceRow(corrId),
  thinCopy(corrId, 'e2e-corr-identity-pk'),
)
await assign.api(`/tasks/${assignment.taskId}/submit`, {
  method: 'POST',
  body: { formData: payload1 },
})
const hist1 = corrHistory(await assign.api(`/processes/${processId}/change-history`))
console.log('[ch-1]', hist1.map((r) => `${r.changeType} ${r.fieldName}=${r.newValue} id=${r.rowIdentifier}`).join('\n'))
noPhantomEmptyUpdate(hist1)
const adds1 = emailAdds(hist1)
assert(adds1.length === 1, `expected 1 Email ADD after first save, got ${adds1.length}`)
assert(adds1[0].rowIdentifier === corrId, `identifier ${adds1[0].rowIdentifier} != ${corrId}`)
for (const row of hist1.filter((r) => r.changeType === 'SUB_TABLE_ROW_ADD' && r.fieldName?.startsWith('correspondence'))) {
  assert(row.rowIdentifier === corrId, `ADD ${row.fieldName} identifier ${row.rowIdentifier}`)
}

const form2 = await assign.api(`/tasks/${assignment.taskId}/form-data`)
const payload2 = nestCorrespondence(
  fieldValuesFromForm(form2),
  correspondenceRow(corrId, { mdc_status: 'Draft' }),
  thinCopy(corrId, 'e2e-corr-identity-pk'),
)
await assign.api(`/tasks/${assignment.taskId}/submit`, {
  method: 'POST',
  body: { formData: payload2 },
})
const hist2 = corrHistory(await assign.api(`/processes/${processId}/change-history`))
console.log('[ch-2]', hist2.map((r) => `${r.changeType} ${r.fieldName}=${r.oldValue}->${r.newValue} id=${r.rowIdentifier}`).join('\n'))
noPhantomEmptyUpdate(hist2)
assert(emailAdds(hist2).length === 1, `Email re-ADD after second save: ${emailAdds(hist2).length}`)
const statusRows = hist2.filter((r) => r.fieldName === 'mdc_status')
assert(statusRows.length >= 1, 'mdc_status not recorded')
assert(statusRows.every((r) => r.rowIdentifier === corrId), `mdc_status identifier drifted: ${JSON.stringify(statusRows)}`)

const form3 = await assign.api(`/tasks/${assignment.taskId}/form-data`)
const completePayload = stampTransactionAssignee(
  nestCorrespondence(
    fieldValuesFromForm(form3),
    correspondenceRow(corrId, { mdc_status: 'Draft' }),
    thinCopy(corrId, 'e2e-corr-identity-pk'),
  ),
  LIAM_ID,
)
await completeApprove(assign, assignment, completePayload)
const hist3 = corrHistory(await assign.api(`/processes/${processId}/change-history`))
console.log('[ch-3]', hist3.map((r) => `${r.changeType} ${r.fieldName}=${r.oldValue}->${r.newValue} id=${r.rowIdentifier}`).join('\n'))
assertCorrClean(hist3, corrId)

const operator = await login('123456', OPERATOR_ROLE)
const investigation = await waitForTask(operator, processId, /investigation/i, 90000, requestId)
console.log(`[invest] ${investigation.taskId} ${investigation.taskName} pid=${investigation.processInstanceId}`)
await claimIfNeeded(operator, investigation)
const formInv = await operator.api(`/tasks/${investigation.taskId}/form-data`)
const fromForm = fieldValuesFromForm(formInv)
console.log('[invest-tables]', JSON.stringify(summarizeSubTables(fromForm)))
console.log('[invest-fv-keys]', Object.keys(formInv.fieldValues || {}))
console.log('[invest-bindings]', (formInv.subTableBindings || []).map((b) => `${b.bindingId}:${b.tableName}:${(b.data || []).map((r) => r.row_id)}`))
const invPayload = nestCorrespondence(
  fromForm,
  correspondenceRow(corrId, { mdc_status: 'Draft', date: '2026-09-11' }),
  null,
  false,
)
console.log('[invest-submit-tables]', JSON.stringify(summarizeSubTables(invPayload)))
await operator.api(`/tasks/${investigation.taskId}/submit`, {
  method: 'POST',
  body: { formData: invPayload },
})
const histInv = corrHistory(await operator.api(`/processes/${processId}/change-history`))
console.log('[ch-inv]', histInv.map((r) => `${r.changeType} ${r.fieldName}=${r.oldValue}->${r.newValue} id=${r.rowIdentifier}`).join('\n'))
assertCorrClean(histInv, corrId)
const dateRows = histInv.filter((r) => r.fieldName === 'processed_date')
assert(dateRows.some((r) => String(r.newValue || '').includes('2026-09-11')), `processed_date not updated: ${JSON.stringify(dateRows)}`)
await completeApprove(operator, investigation, invPayload)

const approver = await login('12345678', APPROVER_ROLE)
const approval = await waitForTask(approver, processId, /approval/i, 90000, requestId)
console.log(`[approval] ${approval.taskId} ${approval.taskName}`)
await claimIfNeeded(approver, approval)
const formAppr = await approver.api(`/tasks/${approval.taskId}/form-data`)
await completeApprove(approver, approval, nestCorrespondence(fieldValuesFromForm(formAppr),
  correspondenceRow(corrId, { mdc_status: 'Draft', date: '2026-09-11' }),
  null,
  false))

const closer = await login('123456', OPERATOR_ROLE)
const mark = await waitForTask(closer, processId, /mark completed/i, 90000, requestId)
console.log(`[mark] ${mark.taskId} ${mark.taskName}`)
await claimIfNeeded(closer, mark)
const formMark = await closer.api(`/tasks/${mark.taskId}/form-data`)
await completeApprove(closer, mark, nestCorrespondence(fieldValuesFromForm(formMark),
  correspondenceRow(corrId, { mdc_status: 'Draft', date: '2026-09-11' }),
  null,
  false))

const histFinal = corrHistory(await operator.api(`/processes/${processId}/change-history`))
console.log('[ch-final]', histFinal.map((r) => `${r.changeType} ${r.fieldName}=${r.oldValue}->${r.newValue} id=${r.rowIdentifier}`).join('\n'))
assertCorrClean(histFinal, corrId)
const detail = await index.api(`/processes/${processId}`)
console.log(`[done] status=${detail.status} current=${detail.currentNode} requestId=${detail.requestId}`)
assert(String(detail.status).toUpperCase() === 'COMPLETED', `expected COMPLETED, got ${detail.status}`)

console.log(`PASS process=${processId} requestId=${requestId || detail.requestId} correspondence_id=${corrId}`)
