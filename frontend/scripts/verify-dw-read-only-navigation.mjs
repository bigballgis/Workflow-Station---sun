import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import assert from 'node:assert/strict'

const origin = process.env.DW_ORIGIN || 'http://localhost:3102'
const phase = process.argv.includes('--before') ? 'before' : 'after'
const inspect = process.argv.includes('--inspect')
const browser = await chromium.launch({ headless: true })
async function verify() {
try {
  const context = await browser.newContext({ viewport: { width: 1600, height: 1100 } })
  const page = await context.newPage()
  const errors = []
  const writes = []
  page.on('pageerror', error => errors.push(error.message))
  await loginViaDwPassword(page, { loginOrigin: origin })
  const roles = await page.evaluate(() => JSON.parse(localStorage.getItem('ws_dw_user')).roles)
  console.log('Signed-in roles:', roles)
  if (process.argv.includes('--create-options')) {
    const response = await page.request.get(`${origin}/api/v1/function-units/my-dev-groups`)
    assert.equal(response.status(), 200)
    const groups = (await response.json()).data
    const expected = groups.groups
      .filter(group => group.status !== 'INACTIVE' && groups.memberGroupIds.includes(group.id)
        && roles.some(role => ['TECH_LEAD', 'TEAM_LEAD'].includes(role)))
      .map(group => group.name)
    if (roles.includes('SYS_ADMIN')) expected.push('Public')
    await page.evaluate(id => localStorage.setItem('ws_dw_active_group', id), groups.publicGroupId)
    await page.goto(`${origin}/dev/function-units`, { waitUntil: 'domcontentloaded' })
    const loaded = page.waitForResponse(res => res.url().endsWith('/function-units/my-dev-groups') && res.ok())
    await page.getByRole('button', { name: 'Create Function Unit', exact: true }).first().click()
    await loaded
    const dialog = page.locator('.el-dialog:visible')
    await dialog.locator('.el-form-item').filter({ hasText: 'Workspace' }).locator('.el-select').click()
    await page.locator('.el-select-dropdown__item:visible').first().waitFor()
    const actual = await page.locator('.el-select-dropdown__item:visible').allTextContents()
    assert.deepEqual(actual.map(value => value.trim()).sort(), expected.sort())
    const screenshots = resolve('frontend/developer-workstation/verification-screenshots')
    await mkdir(screenshots, { recursive: true })
    const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Hong_Kong' }).format(new Date())
    await page.screenshot({ path: resolve(screenshots, `${date}_workspace-create-options.png`), fullPage: true })
    console.log('PASS (creation options): only member workspaces with a lead role, plus Public for admin; no FU created')
    return
  }
  if (process.argv.includes('--workspace-api')) {
    const result = await page.request.get(`${origin}/api/v1/function-units?page=0&size=100`)
    assert.equal(result.status(), 200)
    const units = (await result.json()).data.content.filter(unit => !unit.canModify)
    for (const unit of units) {
      const response = await page.request.get(`${origin}/api/v1/function-units/${unit.id}/forms`)
      assert.equal(response.status(), 200)
      for (const form of (await response.json()).data) {
        for (const binding of form.tableBindings || []) {
          if (binding.bindingType !== 'SUB') continue
          const bindingId = binding.id
          assert.ok(Number.isInteger(bindingId), 'Persisted binding must have an id')
          const path = `${origin}/api/v1/api/forms/${form.id}/sub-table-views/${bindingId}`
          const read = await page.request.get(path)
          assert.equal(read.status(), 200)
          const config = (await read.json()).data
          assert.equal(config.bindingId, bindingId)
          if (config.id == null) continue
          // Use an existing config: even a regressed or-create cannot create a new row here.
          const denied = await page.request.get(`${path}/or-create`)
          assert.equal(denied.status(), 403)
          assert.equal((await denied.json()).error, 'WORKSPACE_FORBIDDEN')
          const wrong = `${origin}/api/v1/api/forms/999999999/sub-table-views/${bindingId}`
          const mismatch = await page.request.get(wrong)
          assert.equal(mismatch.status(), 404)
          assert.equal((await mismatch.json()).error, 'RES_NOT_FOUND')
          console.log(`PASS (real server authorization): FU ${unit.id}, form ${form.id}, binding ${bindingId}; read=200, or-create=403, spoofed parent=404`)
          return
        }
      }
    }
    throw new Error('Need a real read-only workspace binding for API denial verification')
  }
  const simulateReadOnly = process.argv.includes('--simulate-readonly')
  if (simulateReadOnly) {
    console.log('UI verification uses real Public FU data with DEVELOPER/read-only response flags; no user roles are changed on the server.')
    await page.evaluate(() => {
      const user = JSON.parse(localStorage.getItem('ws_dw_user'))
      user.roles = ['DEVELOPER']
      localStorage.setItem('ws_dw_user', JSON.stringify(user))
    })
    await context.route(/\/api\/v1\/function-units(?:\/\d+)?(?:\?.*)?$/, async route => {
      const url = new URL(route.request().url())
      if (route.request().method() !== 'GET' || !/^\/api\/v1\/function-units(?:\/\d+)?$/.test(url.pathname)) {
        await route.continue()
        return
      }
      const response = await route.fetch()
      const body = await response.json()
      if (Array.isArray(body.data?.content)) body.data.content.forEach(unit => { unit.canModify = false })
      else if (body.data) body.data.canModify = false
      await route.fulfill({ response, json: body })
    })
  }
  await context.route(/\/api\/v1\//, async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/or-create') || (!path.startsWith('/api/v1/user-preferences/') && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(route.request().method()))) {
      writes.push(`${route.request().method()} ${new URL(route.request().url()).pathname}`)
      await route.abort('blockedbyclient')
    } else {
      await route.fallback()
    }
  })
  await page.evaluate(() => localStorage.setItem('ws_dw_active_group', 'vg-dev-public'))
  const headers = { 'X-Dev-Group-Id': 'vg-dev-public' }
  async function get(path) {
    const response = await page.request.get(`${origin}/api/v1${path}`, { headers })
    assert.equal(response.status(), 200, path)
    return (await response.json()).data
  }
  const units = (await get('/function-units?page=0&size=100')).content
  const candidates = []
  for (const unit of units) {
    if (unit.canModify && !simulateReadOnly) continue
    const forms = await get(`/function-units/${unit.id}/forms`)
    const actions = await get(`/function-units/${unit.id}/actions`)
    const form = forms.find(item => item.tableBindings?.some(binding => binding.bindingType === 'SUB'))
    if (form && actions.length) candidates.push({ id: unit.id, formId: form.id, formName: form.formName, actions: actions.length })
  }
  console.log('Read-only Public candidates:', JSON.stringify(candidates))
  assert.ok(candidates.length, 'Need a read-only Public FU with a sub-table form and actions')
  const target = process.env.DW_FU_ID
    ? candidates.find(candidate => candidate.id === Number(process.env.DW_FU_ID))
    : candidates[0]
  assert.ok(target, 'Specified FU must have a sub-table form and actions')
  await page.goto(`${origin}/dev/function-units/${target.id}`, { waitUntil: 'domcontentloaded' })
  await page.locator('#tab-forms').click()
  await page.locator('.form-list-sidebar').waitFor({ state: 'visible' })
  if (inspect) {
    console.log((await page.locator('.form-designer').innerText()).slice(0, 8000))
    console.log('Form cards:', await page.locator('.form-designer').evaluate(el => [...el.querySelectorAll('[class*=card]')].map(item => ({ cls: item.className, text: item.textContent.trim().slice(0,150) }))))
    return
  }
  const card = page.locator('.form-designer').getByText(target.formName, { exact: true }).first()
  await card.click()
  await page.locator('.form-editor-view').waitFor()
  assert.ok(await page.locator('.form-editor-view').getByRole('button', { name: /^Save$|^保存$/, exact: true }).isDisabled(), 'FU must be in read-only mode')
  const screenshots = resolve('frontend/developer-workstation/verification-screenshots')
  await mkdir(screenshots, { recursive: true })
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Hong_Kong' }).format(new Date())
  const shot = async name => {
    const path = resolve(screenshots, `${date}_readonly-${target.id}-${name}-${phase}.png`)
    await page.screenshot({ path, fullPage: true })
    console.log('Screenshot:', path)
  }
  const subNav = page.locator('.designer-nav-btn').filter({ hasText: /Sub Tables|Sub Table|子表/ }).first()
  await subNav.click()
  const menu = page.locator('.el-dropdown-menu:visible')
  if (phase === 'before') {
    assert.equal(await menu.count(), 0, 'Expected blocked sub-table menu before fix')
    await page.locator('.form-editor-view').getByRole('button', { name: /Back to List|返回列表/, exact: true }).click()
    assert.equal(await page.locator('.form-editor-view').count(), 1, 'Expected blocked back button before fix')
    await shot('form')
  } else {
    await menu.waitFor()
    await menu.evaluate(async element => {
      const popper = element.closest('.el-popper') || element
      await Promise.allSettled(popper.getAnimations({ subtree: true }).map(animation => animation.finished))
    })
    await shot('subtable-menu')
    await menu.locator('.el-dropdown-menu__item').first().click()
    await page.locator('.sub-table-design-wrapper:visible').waitFor()
    assert.ok(await page.locator('.form-editor-view').getByRole('button', { name: /^Save$|^保存$/, exact: true }).isDisabled())
    await shot('subtable')
    const listViewTab = page.locator('.sub-inner-tab-btn:visible').filter({ hasText: /List View|列表视图|列表視圖/ })
    if (await listViewTab.count()) {
      await listViewTab.click()
      await page.locator('.sub-inner-tab-btn.is-active:visible').filter({ hasText: /List View|列表视图|列表視圖/ }).waitFor()
      await shot('subtable-list-view')
    }
    await page.locator('.form-editor-view').getByRole('button', { name: /Back to List|返回列表/, exact: true }).click()
    await page.locator('.form-editor-view').waitFor({ state: 'detached' })
    await shot('form-list')
  }
  await page.locator('#tab-actions').click()
  await page.locator('.action-list tbody tr, .action-list .el-table__row').first().click()
  await page.locator('.action-editor').waitFor()
  await page.locator('.action-editor').getByRole('button', { name: /Back to List|返回列表/, exact: true }).click()
  if (phase === 'before') {
    assert.equal(await page.locator('.action-editor').count(), 1, 'Expected blocked action back before fix')
  } else {
    await page.locator('.action-editor').waitFor({ state: 'detached' })
  }
  await shot('actions')
  assert.deepEqual(errors, [], 'Browser errors')
  assert.deepEqual(writes, [], 'Inspection must not issue any design write requests')
  console.log(`PASS (${phase}): form back, sub-table dropdown, action back; no page errors`)
} finally {
  await browser.close()
}
}
await verify()
