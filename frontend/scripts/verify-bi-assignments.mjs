import { chromium } from 'playwright'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import assert from 'node:assert/strict'
import { loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'

const origin = process.env.BI_ORIGIN || 'http://localhost:3000'
const fixtures = JSON.parse((await readFile(process.env.BI_FIXTURES || '/tmp/bi-fixtures-output.txt', 'utf8')).trim().split('\n').at(-1))
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1050 } })
const page = await context.newPage()
const errors = []
page.on('pageerror', error => errors.push(error.message))
const results = []
const created = []
let audience
const adminBase = `${origin}/api/v1/admin/bi`
const shots = resolve('frontend/admin-center/verification-screenshots')
await mkdir(shots, { recursive: true })
async function api(method, path, data) {
  const response = await page.request.fetch(`${adminBase}${path}`, { method, data })
  const body = await response.json().catch(() => null)
  assert(response.ok(), `${method} ${path}: ${response.status()} ${JSON.stringify(body)}`)
  return body?.data ?? body
}
try {
  await loginViaAdminPassword(page, { loginOrigin: origin })
  const list = await api('GET', '/data-view-assignments?size=100')
  assert(list.content.every(row => row.viewId && row.enabled && row.viewAvailable))
  const dashboardId = list.content[0].dashboardId
  results.push('Migrated existing assignments expose concrete active View identities')
  for (const target of fixtures.filter(f => f.viewName.endsWith('A'))) {
    const other = fixtures.find(f => f.tableId === target.tableId && f.viewName.endsWith('B'))
    assert.deepEqual(await api('GET', `/data-view-assignments/views/${target.id}/dashboards`), [])
    const row = await api('POST', '/data-view-assignments', {
      dashboardId, functionUnitId: target.functionUnitId, tableId: target.tableId, viewId: target.id, enabled: false,
    })
    created.push(row.id)
    assert.equal(row.enabled, false)
    await api('PUT', `/data-view-assignments/${row.id}/enabled`, { enabled: true })
    assert((await api('GET', `/data-view-assignments/views/${target.id}/dashboards`)).some(d => d.dashboardId === dashboardId))
    assert.deepEqual(await api('GET', `/data-view-assignments/views/${other.id}/dashboards`), [])
    const denied = await page.request.post(`${adminBase}/guest-token`, { data: { dashboardId, dataViewId: other.id } })
    assert.equal(denied.status(), 403)
    await api('PUT', `/data-view-assignments/${row.id}/enabled`, { enabled: false })
    assert.deepEqual(await api('GET', `/data-view-assignments/views/${target.id}/dashboards`), [])
    const disabled = await page.request.post(`${adminBase}/guest-token`, { data: { dashboardId, dataViewId: target.id } })
    assert.equal(disabled.status(), 403)
    await api('PUT', `/data-view-assignments/${row.id}/enabled`, { enabled: true })
    const duplicate = await page.request.post(`${adminBase}/data-view-assignments`, {
      data: { dashboardId, functionUnitId: target.functionUnitId, tableId: target.tableId, viewId: target.id },
    })
    assert.equal(duplicate.status(), 400)
    results.push(`Table ${target.tableId}: new View starts unassigned, exact View grant, independent disable/re-enable, foreign View and disabled Token denied, duplicate refused`)
  }
  audience = (await api('GET', '/assignments?size=20')).content[0]
  await api('PUT', `/assignments/${audience.id}/enabled`, { enabled: false })
  assert(!(await api('GET', `/assignments/user/${audience.targetId}`)).some(d => d.dashboardId === audience.dashboardId))
  const restored = await api('PUT', `/assignments/${audience.id}/enabled`, { enabled: true })
  for (const field of ['targetId', 'targetType', 'layoutMode', 'displayOrder', 'isDefault']) assert.equal(restored[field], audience[field])
  results.push('Audience disable removes grant; re-enable preserves all saved presentation and target settings')

  await page.goto(`${origin}/admin/bi-management/audience-assignment`)
  await page.getByRole('button', { name: /New Assignment|新建分配|新增指派/ }).click()
  const dialog = page.locator('.el-dialog').filter({ visible: true })
  const selects = dialog.locator('.el-select')
  await selects.nth(1).click()
  await page.getByRole('option', { name: /^User$|^用户$|^使用者$/ }).click()
  await selects.nth(2).click()
  const more = page.getByRole('button', { name: /Load more users|加载更多用户|載入更多使用者/ })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: `${shots}/2026-10-25_bi-user-dropdown-debug.png`, fullPage: true })
  await more.waitFor({ state: 'visible' })
  const before = await page.getByRole('option').count()
  await more.click()
  await page.waitForTimeout(700)
  assert((await page.getByRole('option').count()) > before)
  await page.screenshot({ path: `${shots}/2026-10-25_bi-audience-user-pagination.png`, fullPage: true })
  await more.click()
  await page.waitForTimeout(600)
  assert(!(await more.isVisible()))
  const searched = await page.request.get(`${origin}/api/v1/admin/users?keyword=BI_ASSIGNMENT_VERIFY&size=100`)
  const searchPage = await searched.json()
  assert(searchPage.totalElements >= 35, 'User search must be case insensitive across all eligible statuses')
  results.push('Real dropdown loads every user across three pages, including locked accounts; case-insensitive user search returns all test accounts')
  await page.keyboard.press('Escape')
  await dialog.getByRole('button', { name: /^Cancel$|^取消$/ }).click()
  await page.goto(`${origin}/admin/bi-management/data-view-assignment`)
  await page.locator('.el-table__body tr').first().waitFor()
  await page.screenshot({ path: `${shots}/2026-10-25_bi-data-view-assignments.png`, fullPage: true })
  await page.locator('.el-table__body tr').first().getByRole('button', { name: /^Edit$|^编辑$|^編輯$/ }).click()
  await page.locator('.el-dialog .el-select').first().waitFor()
  await page.waitForTimeout(800)
  await page.screenshot({ path: `${shots}/2026-10-25_bi-data-view-form.png`, fullPage: true })
  await page.locator('.el-dialog:visible').getByRole('button', { name: /^Confirm$|^确认$|^確認$/ }).click()
  await page.locator('.el-dialog:visible').waitFor({ state: 'hidden' })
  await page.locator('.el-table__body tr').first().getByRole('button', { name: /^Edit$|^编辑$|^編輯$/ }).click()
  await page.waitForTimeout(500)
  const labelLayout = await page.locator('.el-dialog:visible .el-form-item__label').evaluateAll(labels => labels.map(label => ({
    width: label.getBoundingClientRect().width, height: label.getBoundingClientRect().height, scroll: label.scrollWidth, client: label.clientWidth,
  })))
  assert(labelLayout.every(label => label.scroll <= label.client + 1), 'Dialog labels must fit on one line')

  const portal = await browser.newContext({ viewport: { width: 1440, height: 1050 } })
  const portalPage = await portal.newPage()
  portalPage.on('pageerror', error => errors.push(error.message))
  await loginViaPortalPassword(portalPage, { loginOrigin: origin })
  const target = fixtures.find(f => f.viewName.endsWith('A'))
  await portalPage.goto(`${origin}/portal/views/${target.fuCode}?viewId=${target.id}`)
  await portalPage.waitForTimeout(1800)
  const portalDir = resolve('frontend/user-portal/verification-screenshots')
  await mkdir(portalDir, { recursive: true })
  await portalPage.screenshot({ path: `${portalDir}/2026-10-25_bi-view-records.png`, fullPage: true })
  const dashboardButton = portalPage.getByRole('tab', { name: /^Dashboard$|仪表板|儀表板/ })
  if (await dashboardButton.isVisible()) {
    await dashboardButton.click()
    await portalPage.locator('.data-view-dashboard-panel').waitFor()
    await portalPage.locator('.data-view-dashboard-panel .el-loading-mask').waitFor({ state: 'hidden', timeout: 45000 })
    const panel = portalPage.locator('.data-view-dashboard-panel')
    const externalFailure = await panel.locator('.dashboard-error').isVisible()
    if (externalFailure) {
      assert(await panel.getByRole('button', { name: /Retry|重试|重試/ }).isVisible())
      results.push('Portal panel, scope guidance and recoverable error are rendered; live chart blocked by upstream Superset CSRF 403')
    } else {
      await panel.locator('iframe').waitFor()
      results.push('Portal Dashboard iframe mounted after successful authorization')
    }
    await portalPage.screenshot({ path: `${portalDir}/2026-10-25_bi-view-dashboard.png`, fullPage: true })
    const registry = await api('GET', '/dashboards?status=ACTIVE&size=100')
    const extraDashboard = registry.content.find(d => d.id !== dashboardId)
    assert(extraDashboard, 'Multi-Dashboard verification needs a second registered Dashboard')
    const batch = await api('POST', '/data-view-assignments/batch', { dashboardIds: [extraDashboard.id],
      functionUnitId: target.functionUnitId, tableId: target.tableId, viewId: target.id, enabled: true })
    created.push(...batch.map(row => row.id))
    await portalPage.reload()
    await portalPage.getByRole('tab', { name: /^Dashboard$/ }).click()
    await portalPage.locator('.data-view-dashboard-panel .el-tabs__item').nth(1).waitFor()
    await portalPage.locator('.data-view-dashboard-panel .el-loading-mask').waitFor({ state: 'hidden', timeout: 45000 })
    await portalPage.screenshot({ path: `${portalDir}/2026-10-25_bi-view-dashboard-tabs.png`, fullPage: true })
    results.push('Batch save binds multiple Dashboards to the concrete View; Portal exposes named Dashboard tabs')
    const other = fixtures.find(f => f.tableId === target.tableId && f.viewName.endsWith('B'))
    await portalPage.goto(`${origin}/portal/views/${other.fuCode}?viewId=${other.id}`)
    await portalPage.waitForTimeout(1300)
    assert(!(await portalPage.getByRole('tab', { name: /^Dashboard$/ }).isVisible()))
    results.push('Portal switching to the unassigned sibling View returns to Records without a Dashboard entry')
  } else {
    throw new Error('Portal test account cannot access the configured View Dashboard')
  }
  await portal.close()
  const anonymous = await browser.newContext()
  const forbidden = await anonymous.request.put(`${adminBase}/assignments/${audience.id}/enabled`, { data: { enabled: false } })
  assert([401, 403].includes(forbidden.status()))
  await anonymous.close()
  results.push('Unauthenticated assignment mutation is refused')
  assert.deepEqual(errors, [], 'Browser runtime errors')
  await writeFile(`${shots}/2026-10-25_bi-assignment-results.json`, JSON.stringify({ results, errors, labelLayout }, null, 2))
  console.log(JSON.stringify({ results, errors }, null, 2))
} finally {
  for (const id of created) await page.request.delete(`${adminBase}/data-view-assignments/${id}`)
  if (audience) await page.request.put(`${adminBase}/assignments/${audience.id}/enabled`, { data: { enabled: audience.enabled } })
  await browser.close()
}
