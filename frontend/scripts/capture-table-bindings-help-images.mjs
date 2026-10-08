/**
 * Capture Manage Table Bindings help figures only.
 * Writes into frontend/help/public/guides/ with PII redaction.
 *
 * Uses HELP_GUIDE_FU_ID when set; otherwise looks up Purchase Request.
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword } from './playwright-login.mjs'
import { redactHelpGuidePii } from './redact-help-guide-pii.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT = resolve(__dirname, '../help/public/guides')
mkdirSync(OUT, { recursive: true })

const origin = 'http://localhost:3000'
const launchOpts = { headless: true }
if (process.env.PLAYWRIGHT_EXECUTABLE_PATH) {
  launchOpts.executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH
} else if (process.env.PLAYWRIGHT_CHANNEL) {
  launchOpts.channel = process.env.PLAYWRIGHT_CHANNEL
}

const browser = await chromium.launch(launchOpts)
const page = await (await browser.newContext({ viewport: { width: 1440, height: 1300 } })).newPage()

async function resolveHelpFuId() {
  const forced = process.env.HELP_GUIDE_FU_ID?.trim()
  if (forced) return forced
  const res = await page.request.get(`${origin}/api/v1/function-units?page=0&size=200`)
  const body = await res.json()
  const data = body?.data
  const records = data?.records || data?.content || (Array.isArray(data) ? data : [])
  const purchase = records.find((fu) =>
    /Purchase Request/i.test(String(fu.name || fu.functionUnitName || '')),
  )
  return purchase ? String(purchase.id) : '50007'
}

async function shot(name, locator) {
  await redactHelpGuidePii(page)
  const target = locator ?? page
  const path = resolve(OUT, name)
  await target.screenshot({ path })
  console.log(`wrote ${path}`)
}

function asRows(payload) {
  if (Array.isArray(payload)) return payload
  if (!payload || typeof payload !== 'object') return []
  for (const key of ['records', 'content', 'list', 'tables', 'forms', 'bindings', 'data']) {
    if (Array.isArray(payload[key])) return payload[key]
  }
  return []
}

async function dw(page, origin, userId, groupId, method, path, data) {
  const headers = {
    'X-User-Id': String(userId),
  }
  if (groupId) headers['X-Dev-Group-Id'] = String(groupId)
  const res = await page.request.fetch(`${origin}${path}`, {
    method,
    headers,
    ...(data !== undefined ? { data } : {}),
  })
  const json = await res.json().catch(() => ({}))
  if (!res.ok() || json.success === false) {
    const msg = json.error?.message || json.message || JSON.stringify(json)
    throw new Error(`${method} ${path} → HTTP ${res.status()} ${msg}`)
  }
  return json.data
}

async function ensurePurchaseRequestForm(page, origin, userId, fuId) {
  const groupsPayload = await dw(page, origin, userId, null, 'GET', '/api/v1/function-units/my-dev-groups')
  const groupId = groupsPayload?.groups?.[0]?.id || groupsPayload?.publicGroupId
  const call = (method, path, data) => dw(page, origin, userId, groupId, method, path, data)
  const tablePayload = await call('GET', `/api/v1/function-units/${fuId}/tables`)
  const tables = asRows(tablePayload)
  if (!tables.length) {
    throw new Error(`No tables on FU ${fuId}: ${JSON.stringify(tablePayload).slice(0, 400)}`)
  }
  const main = tables.find((t) => t.tableName === 'help_pr')
  const sub = tables.find((t) => t.tableName === 'help_pr_line')
  if (!main || !sub) {
    throw new Error('Purchase Request is missing help_pr / help_pr_line tables')
  }
  let forms = asRows(await call('GET', `/api/v1/function-units/${fuId}/forms`))
  let processForms = forms.filter((f) => f.formType === 'PROCESS')
  if (processForms.length === 0) {
    await call('POST', `/api/v1/function-units/${fuId}/forms`, {
      formName: 'Request Form',
      formType: 'PROCESS',
      scene: 'TASK',
      createBothScenes: true,
      boundTableId: main.id,
      configJson: { rule: [] },
      description: 'Start a purchase request',
    })
    forms = asRows(await call('GET', `/api/v1/function-units/${fuId}/forms`))
    processForms = forms.filter((f) => f.formType === 'PROCESS')
  }
  const form = processForms[0]
  const bindings = asRows(
    await call('GET', `/api/v1/function-units/${fuId}/forms/${form.id}/bindings`),
  )
  if (!bindings.some((b) => b.bindingType === 'PRIMARY')) {
    await call('POST', `/api/v1/function-units/${fuId}/forms/${form.id}/bindings`, {
      tableId: main.id,
      bindingType: 'PRIMARY',
      bindingMode: 'EDITABLE',
      sortOrder: 0,
    })
  }
  if (!bindings.some((b) => b.bindingType === 'SUB' && Number(b.tableId) === Number(sub.id))) {
    await call('POST', `/api/v1/function-units/${fuId}/forms/${form.id}/bindings`, {
      tableId: sub.id,
      bindingType: 'SUB',
      bindingMode: 'EDITABLE',
      foreignKeyField: 'main_id',
      bindingLinkMode: 'structuralFk',
      subMode: 'FULL',
      sortOrder: 1,
    })
  }
}

try {
  const session = await loginViaDwPassword(page)
  const fuId = await resolveHelpFuId()
  console.log(`HELP_GUIDE_FU_ID=${fuId}`)
  await ensurePurchaseRequestForm(page, origin, session.userId, fuId)
  await page.goto(`${origin}/dev/function-units/${fuId}`, { waitUntil: 'domcontentloaded' })
  await page.locator('.el-tabs').first().waitFor({ state: 'visible', timeout: 25000 })
  await page.waitForTimeout(800)
  await page.getByRole('tab', { name: 'Form Design', exact: true }).click()
  await page.locator('.form-list-sidebar').waitFor({ state: 'visible', timeout: 15000 })
  await page.waitForTimeout(800)
  const formEdit = page
    .locator('.form-list-sidebar .el-table__row')
    .filter({ hasText: /help_pr|Request Form|New Request/i })
    .getByRole('button', { name: 'Edit' })
    .first()
  if ((await formEdit.count()) === 0) {
    const rows = await page.locator('.form-list-sidebar .el-table__row').allTextContents()
    throw new Error(`No form Edit button. Rows: ${JSON.stringify(rows)}`)
  }
  await formEdit.waitFor({ state: 'visible', timeout: 15000 })
  await formEdit.click()
  await page.locator('.fc-designer-wrapper').first().waitFor({ state: 'visible', timeout: 15000 })
  await page.waitForTimeout(600)

  const manageBtn = page.getByRole('button', { name: 'Manage Table Bindings', exact: true })
  await manageBtn.waitFor({ state: 'visible', timeout: 15000 })
  await manageBtn.click()
  const manager = page.locator('.table-binding-manager')
  await manager.waitFor({ state: 'visible', timeout: 15000 })
  await page.waitForTimeout(600)
  const list = manager.locator('.binding-list').first()
  await shot('dw-table-bindings-list.png', (await list.count()) ? list : manager)

  const subRow = manager
    .locator('.el-table__row')
    .filter({ hasText: /help_pr_line|Line items|Sub Table/i })
    .first()
  if (await subRow.count()) {
    await subRow.getByRole('button', { name: 'Edit' }).click()
  } else {
    await manager.getByRole('button', { name: 'Add Binding', exact: true }).click()
  }
  const addDlg = page
    .locator('.el-dialog')
    .filter({ hasText: /Add Binding|Edit Table Binding/ })
    .last()
  await addDlg.waitFor({ state: 'visible', timeout: 8000 })
  await page.waitForTimeout(500)
  await shot('dw-table-bindings-add-dialog.png', addDlg)
} finally {
  await browser.close()
}

console.log('')
console.log('Next: cd frontend/help && pnpm run build')
console.log('Then: docker compose ... up -d --build platform-help-frontend')
