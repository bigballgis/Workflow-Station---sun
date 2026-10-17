/** Synthetic host only: normal Save/Complete, then read actual engine candidate identity links. No claiming. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { isDeepStrictEqual } from 'node:util'
import { loginViaPortalPassword, loginViaAdminPassword } from './playwright-login.mjs'

const taskId = process.env.FIXTURE_TASK_ID
assert.match(taskId || '', /^[a-f0-9-]{36}$/)
const screenshotTag = process.env.FIXTURE_SCREENSHOT_TAG || 'role-pool'
assert.match(screenshotTag, /^[a-z0-9-]+$/)
const mixedTargets = process.env.FIXTURE_MIXED_TARGETS === '1'
const roundTrip = process.env.FIXTURE_ROLE_PERSON_ROLE === '1'
assert.ok(!roundTrip || mixedTargets, 'Role/person/role verification requires the mixed-target scenario')
const wrongPerson = process.env.FIXTURE_EXPECT_WRONG_PERSON === '1'
assert.ok(!wrongPerson || mixedTargets && !roundTrip, 'Wrong-person verification requires a retained mixed target')
const browser = await chromium.launch({ headless: true })
async function request(page, method, url, data) {
  const response = await page.request.fetch(url, { method, data })
  assert.equal(response.ok(), true, `${method} ${url}: HTTP ${response.status()}`)
  const body = await response.json()
  if (body && typeof body === 'object' && 'success' in body) {
    assert.equal(body.success, true, body.error?.message || body.message)
    return body.data
  }
  return body
}
function flatten(nodes) { return nodes.flatMap(node => [node, ...flatten(node.children || [])]) }
async function completeMixedFailureThroughUi(page) {
  await page.setViewportSize({ width: 1366, height: 768 })
  await page.goto(`http://localhost:3101/portal/tasks/${taskId}`)
  await page.getByRole('button', { name: 'Approve', exact: true }).click()
  const dialog = page.locator('.el-dialog:visible')
  await dialog.locator('textarea').fill('Synthetic mixed personal failure verification')
  const completed = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/complete`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: 'Confirm', exact: true }).click()
  const body = await (await completed).json()
  assert.equal(body.success, true)
  await page.waitForURL('**/portal/tasks')
  const message = page.locator('.el-message').last()
  await message.waitFor()
  const path = fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-17_${screenshotTag}-outcome.png`, import.meta.url))
  await message.screenshot({ path })
  await page.setViewportSize({ width: 600, height: 800 })
  await message.screenshot({ path: path.replace('.png', '-narrow.png') })
  console.log(JSON.stringify({ step: 'synthetic-mixed-outcome', observation: body.data.batchAssignmentVerification, screenshot: path }))
  assert.equal(body.data.batchAssignmentVerification.status, 'FAILED')
  assert.match(await message.innerText(), /missing or mismatched assignee/)
  assert.equal(await page.locator('.el-message--success').count(), 0)
  return body.data.batchAssignmentVerification
}
async function saveUniformRoleThroughWorkspace(page, pool, expectedPatches = 2) {
  await page.setViewportSize({ width: 1366, height: 768 })
  await page.goto(`http://localhost:3101/portal/tasks/${taskId}`)
  await page.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  const dialog = page.locator('.el-dialog:visible')
  const editor = dialog.locator('.batch-assignment-editor')
  await editor.locator('.el-table__body-wrapper tr').nth(1).waitFor()
  await editor.locator('thead .el-checkbox').click()
  const uniform = editor.locator('.batch-assignment-editor__uniform')
  await uniform.locator('.el-select').first().click()
  if (process.env.FIXTURE_EXPECT_ROLE_ONLY === '1') {
    assert.equal(await page.locator('.el-select-dropdown:visible').getByText('Assign by person', { exact: true }).count(), 0)
  }
  await page.locator('.el-select-dropdown:visible').getByText('Assign by role', { exact: true }).click()
  await uniform.locator('.el-cascader input').fill(pool.bu.name)
  await page.locator('.el-cascader__suggestion-item:visible').filter({ hasText: pool.bu.name }).first().click()
  await uniform.locator('.el-select').last().click()
  await page.locator('.el-select-dropdown:visible').getByText(pool.role.name, { exact: true }).click()
  const save = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: /Apply/ }).click()
  const response = await save
  assert.equal((await response.json()).success, true)
  const changes = response.request().postDataJSON().subTableBatchChanges
  assert.deepEqual(changes.map(change => change.operation), ['ASSIGN'])
  assert.equal(changes[0].rowPatches.length, expectedPatches)
}
async function replaceFirstRoleWithPersonThroughWorkspace(page) {
  await page.setViewportSize({ width: 1366, height: 768 })
  await page.goto(`http://localhost:3101/portal/tasks/${taskId}`)
  await page.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  const dialog = page.locator('.el-dialog:visible')
  const editor = dialog.locator('.batch-assignment-editor')
  await editor.locator('.el-table__body-wrapper tr').nth(1).waitFor()
  const first = editor.locator('.el-table__body-wrapper tr').first()
  await first.locator('.el-select').first().click()
  await page.locator('.el-select-dropdown:visible').getByText('Assign by person', { exact: true }).click()
  const search = page.waitForResponse(response => response.url().includes('/tasks/users/search'))
  await first.locator('.el-select').nth(1).locator('input').fill('developer')
  const users = (await (await search).json()).data
  const index = users.findIndex(user => user.username === 'developer')
  assert.ok(index >= 0)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').nth(index).click()
  const save = page.waitForResponse(response => response.url().endsWith(`/tasks/${taskId}/submit`) && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: /Apply/ }).click()
  const response = await save
  assert.equal((await response.json()).success, true)
  const changes = response.request().postDataJSON().subTableBatchChanges
  assert.deepEqual(changes.map(change => change.operation), ['ASSIGN'])
  assert.equal(changes[0].rowPatches.length, 1)
  return users[index].id
}
async function selectSingleMemberPool(portal, admin) {
  const units = flatten(await request(portal, 'GET', 'http://localhost:3000/api/portal/permissions/business-units/tree'))
  assert.ok(units.length > 0 && units.length <= 50, 'Bounded directory fixture expected')
  for (const bu of units) {
    assert.ok(bu.id && bu.code)
    const roles = await request(portal, 'GET', `http://localhost:3000/api/portal/permissions/business-units/${encodeURIComponent(bu.id)}/roles`)
    assert.ok(roles.length <= 50)
    for (const role of roles) {
      assert.ok(role.code)
      const path = `http://localhost:3000/api/v1/admin/task-assignment/business-units/${encodeURIComponent(bu.code)}/roles/${encodeURIComponent(role.code)}`
      const users = await request(admin, 'GET', `${path}/users`)
      assert.ok(Array.isArray(users))
      if (new Set(users).size === 1) {
        const admission = await request(admin, 'GET', `${path}/eligible`)
        assert.equal(typeof admission.eligible, 'boolean')
        if (admission.eligible === true) return { bu, role, users: [...new Set(users)] }
      }
    }
  }
  throw new Error('No existing single-member admitted role pool; no directory data was changed')
}
try {
  const portal = await browser.newPage()
  const admin = await browser.newPage()
  let pageErrors = 0
  portal.on('pageerror', () => pageErrors++)
  await loginViaPortalPassword(portal)
  await loginViaAdminPassword(admin)
  const api = (method, path, data) => request(portal, method, `http://localhost:3000/api/portal${path}`, data)
  const task = await api('GET', `/tasks/${taskId}`)
  assert.match(task.functionUnitName, /^Batch verification /)
  if (process.env.FIXTURE_EXPECT_PROCESS_KEY) {
    assert.match(process.env.FIXTURE_EXPECT_PROCESS_KEY, /^[A-Za-z0-9_-]{1,100}$/)
    assert.match(process.env.FIXTURE_EXPECT_CATALOG_ID || '', /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i)
    assert.equal(task.processDefinitionKey, process.env.FIXTURE_EXPECT_PROCESS_KEY)
    assert.equal(task.functionUnitCatalogId, process.env.FIXTURE_EXPECT_CATALOG_ID)
  } else assert.match(task.processDefinitionKey, /^batch_verify_[a-z0-9]+$/)
  assert.equal(task.taskDefinitionKey, 'Review')
  const form = await api('GET', `/tasks/${taskId}/form-data`)
  const [control] = Object.values(form.subTableBatchCapabilities)
  assert.equal(control.capabilities.allowBatchAssign && control.assignmentConfig.allowRole, true)
  if (process.env.FIXTURE_EXPECT_ROLE_ONLY === '1') assert.equal(control.assignmentConfig.allowUser, false)
  const config = control.assignmentConfig
  if (mixedTargets) assert.equal(config.allowUser, true)
  assert.ok(config.buField)
  const pool = await selectSingleMemberPool(portal, admin)
  const payload = structuredClone(form.fieldValues)
  const store = `dw:${control.rowDesign.binding.tableName}`
  const rows = payload.__subTables__[store]
  assert.equal(rows.length, 2)
  for (const row of rows) {
    assert.ok(!row[config.assigneeField] && !row[config.roleField], 'Fresh unassigned rows required')
    row[config.assigneeField] = null; row[config.roleField] = pool.role.code; row[config.buField] = pool.bu.code
  }
  const scopes = [{ bindingId: String(control.bindingId), storeKey: store, emptied: false,
    rowKeys: rows.map(row => Object.fromEntries(control.rowDesign.binding.primaryKeyFields.map(key => [key, row[key]]))) }]
  const engineTasks = () => request(portal, 'GET', `http://localhost:8081/api/v1/tasks?processInstanceId=${encodeURIComponent(task.processInstanceId)}&page=0&size=100`)
  const beforeTasks = (await engineTasks()).tasks.map(row => row.taskId).sort()
  if (process.env.FIXTURE_ASSIGN_VIA_UI === '1') await saveUniformRoleThroughWorkspace(portal, pool)
  else await api('POST', `/tasks/${taskId}/submit`, { formData: payload, subTableBindingScopes: scopes })
  assert.deepEqual((await engineTasks()).tasks.map(row => row.taskId).sort(), beforeTasks, 'Save must not create tasks')
  const initialSaved = await api('GET', `/tasks/${taskId}/form-data`)
  for (const row of initialSaved.fieldValues.__subTables__[store]) {
    assert.equal(row[config.roleField], pool.role.code); assert.equal(row[config.buField], pool.bu.code)
    assert.ok(!row[config.assigneeField])
  }
  const personId = mixedTargets ? await replaceFirstRoleWithPersonThroughWorkspace(portal) : null
  let saved = await api('GET', `/tasks/${taskId}/form-data`)
  let savedRows = saved.fieldValues.__subTables__[store]
  if (mixedTargets) {
    assert.equal(savedRows[0][config.assigneeField] === personId, true)
    assert.ok(!savedRows[0][config.roleField] && !savedRows[0][config.buField], 'Role to person replacement must clear inactive targets')
    const unselected = initialSaved.fieldValues.__subTables__[store][1]
    assert.equal(typeof unselected._wsRowVersion, 'number')
    // The existing scope guard bumps every claimed row in the ordinary whole-form Save, including this unchanged row.
    assert.equal(isDeepStrictEqual(savedRows[1], { ...unselected, _wsRowVersion: unselected._wsRowVersion + 1 }), true,
      'Unselected second role row must preserve all business/identity fields and receive only the normal claimed-row version bump')
  }
  if (roundTrip) {
    await saveUniformRoleThroughWorkspace(portal, pool, 1)
    saved = await api('GET', `/tasks/${taskId}/form-data`)
    savedRows = saved.fieldValues.__subTables__[store]
    const original = initialSaved.fieldValues.__subTables__[store]
    assert.equal(savedRows.length, original.length)
    for (let index = 0; index < savedRows.length; index++) {
      const expected = { ...original[index], _wsRowVersion: original[index]._wsRowVersion + 2 }
      const differingFields = [...new Set([...Object.keys(expected), ...Object.keys(savedRows[index])])]
        .filter(key => !isDeepStrictEqual(savedRows[index][key], expected[key]))
      assert.equal(isDeepStrictEqual(savedRows[index], expected), true,
        `Role/person/role must restore all original fields; differing synthetic field names: ${differingFields.join(',')}`)
    }
    assert.deepEqual((await engineTasks()).tasks.map(row => row.taskId).sort(), beforeTasks,
      'Neither direction of mode replacement may create tasks before Complete')
  }
  for (const row of mixedTargets && !roundTrip ? savedRows.slice(1) : savedRows) {
    assert.equal(row[config.roleField], pool.role.code); assert.equal(row[config.buField], pool.bu.code)
    assert.ok(!row[config.assigneeField])
  }
  await portal.setViewportSize({ width: 1366, height: 768 })
  await portal.goto(`http://localhost:3101/portal/tasks/${taskId}`)
  if (roundTrip) {
    await portal.getByRole('button', { name: 'Batch Edit', exact: true }).waitFor()
    const grid = portal.locator('.sub-table-field').first()
    await grid.locator('.assignee-role-pool').nth(1).waitFor()
    assert.equal(await grid.locator('.assignee-role-pool').count(), 2)
    assert.equal(await grid.locator('.assignee-name').count(), 0, 'Role rows must not display the previous personal cache')
    await grid.screenshot({ path: fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-16_${screenshotTag}-grid.png`, import.meta.url)),
      mask: [grid.locator('.assignee-cell')] })
  }
  await portal.getByRole('button', { name: 'Batch Edit', exact: true }).click()
  const dialog = portal.locator('.el-dialog:visible')
  const editor = dialog.locator('.batch-assignment-editor')
  await editor.locator('.el-table__body-wrapper tr').nth(1).waitFor()
  assert.equal(await editor.locator('.el-cascader').count(), mixedTargets && !roundTrip ? 1 : 2, 'Saved target modes must reopen correctly')
  const screenshot = fileURLToPath(new URL(`../user-portal/verification-screenshots/2026-10-16_${screenshotTag}-workspace.png`, import.meta.url))
  const mask = [editor.locator('.el-select__selected-item'), editor.locator('.el-cascader')]
  await dialog.screenshot({ path: screenshot, mask })
  await portal.setViewportSize({ width: 600, height: 800 })
  await dialog.screenshot({ path: screenshot.replace('.png', '-narrow.png'), mask })
  // No Apply, Save or task action is performed by opening/closing this read-only inspection.
  await portal.goto('http://localhost:3101/portal/tasks')
  assert.equal(isDeepStrictEqual((await api('GET', `/tasks/${taskId}/form-data`)).fieldValues, saved.fieldValues), true,
    'Opening and closing the workspace must preserve all saved data')
  assert.deepEqual((await engineTasks()).tasks.map(row => row.taskId).sort(), beforeTasks)
  const observation = wrongPerson ? await completeMixedFailureThroughUi(portal)
    : (await api('POST', `/tasks/${taskId}/complete`, { taskId, action: 'APPROVE', formData: saved.fieldValues,
      subTableBindingScopes: scopes, comment: 'Synthetic single-member role pool verification' })).batchAssignmentVerification
  if (mixedTargets && !wrongPerson) assert.equal(observation.status, 'UNCONFIRMED', 'Correct personal rows cannot verify the unobserved role pool')
  const children = (await engineTasks()).tasks
  assert.equal(children.length, 2)
  const identities = new Set()
  for (const child of children) {
    assert.equal(child.taskDefinitionKey, 'AssignedWork')
    const detail = await request(portal, 'GET', `http://localhost:8081/api/v1/tasks/${encodeURIComponent(child.taskId)}`)
    const identity = detail.variables._currentItem.rowKey
    const matching = savedRows.filter(row => control.rowDesign.binding.primaryKeyFields.every(key => row[key] === identity[key]))
    assert.equal(matching.length, 1)
    if (matching[0][config.assigneeField]) {
      assert.equal(detail.currentAssignee === (wrongPerson ? 'synthetic-wrong-person' : personId), true,
        'Actual task must have the explicitly expected personal target')
      if (wrongPerson) assert.deepEqual(observation.issues, [{ taskId: child.taskId, reasonCode: 'PERSON_TASK_TARGET_MISMATCH' }])
      assert.equal(detail.assignmentType, 'USER')
      assert.ok(!detail.candidateUserIds?.length, 'A personal task must not retain the old role candidates')
    } else {
      assert.ok(!detail.currentAssignee, 'Single-member role pool must remain unclaimed')
      assert.equal(detail.assignmentType, 'CANDIDATE_USERS')
      assert.equal(JSON.stringify([...detail.candidateUserIds].sort()) === JSON.stringify(pool.users.toSorted()), true,
        'Actual Flowable candidate links must match the role members')
      assert.equal(detail.miAssigneeMode, 'role')
      assert.equal(detail.miRoleCode, pool.role.code); assert.equal(detail.miBusinessUnitCode, pool.bu.code)
    }
    identities.add(JSON.stringify(identity))
  }
  assert.equal(identities.size, 2)
  assert.equal(pageErrors, 0)
  console.log(JSON.stringify({ step: 'synthetic-single-member-role-pool', processInstanceId: task.processInstanceId,
    taskIds: children.map(child => child.taskId), candidateCount: pool.users.length, rolePoolsUnclaimed: true, directoryWrites: 0,
    assignmentSource: roundTrip ? 'role-person-role-workspace' : mixedTargets ? 'mixed-row-workspace' : process.env.FIXTURE_ASSIGN_VIA_UI === '1' ? 'uniform-workspace' : 'ordinary-api',
    personalTasks: mixedTargets && !roundTrip ? 1 : 0, pageErrors }))
} finally { await browser.close() }
