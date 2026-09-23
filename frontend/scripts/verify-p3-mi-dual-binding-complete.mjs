#!/usr/bin/env node
/**
 * Dedicated MI dual-binding Complete e2e.
 * Seed: deploy/init-scripts/22-dual-binding-mi (code p3-mi-dual-binding-test).
 * Does not touch Demo attachment. Does not freeze FormTableBindingLoader.
 */
import { execFileSync } from 'node:child_process'
import { mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = resolve(__dirname, '..', '..')
const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const FU_CODE = process.env.FU_CODE ?? 'p3-mi-dual-binding-test'
const FILE_STORE = 'dw:p3_mi_file'
const PARTY_STORE = 'dw:p3_mi_party'
const DATE = new Date().toISOString().slice(0, 10)
const PORTAL_SHOTS = join(__dirname, '..', 'user-portal', 'verification-screenshots')
mkdirSync(PORTAL_SHOTS, { recursive: true })

function unwrap(res, body) {
  if (!res.ok()) {
    throw new Error(`HTTP ${res.status()} ${JSON.stringify(body).slice(0, 800)}`)
  }
  return body.data ?? body
}

async function unwrapRes(res) {
  const body = await res.json().catch(() => ({}))
  return unwrap(res, body)
}

function applySql() {
  const sqlPath = join(REPO_ROOT, 'deploy', 'init-scripts', '22-dual-binding-mi', '00-init.sql')
  const container = process.env.POSTGRES_CONTAINER ?? 'platform-postgres-dev'
  const remote = '/tmp/22-p3-mi-dual.sql'
  console.log(`[sql] docker cp ${sqlPath} ${container}:${remote}`)
  execFileSync('docker', ['cp', sqlPath, `${container}:${remote}`], { stdio: 'inherit' })
  execFileSync(
    'docker',
    [
      'exec',
      container,
      'psql',
      '-U',
      process.env.POSTGRES_USER ?? 'platform_dev',
      '-d',
      process.env.POSTGRES_DB ?? 'workflow_platform_dev',
      '-v',
      'ON_ERROR_STOP=1',
      '-f',
      remote,
    ],
    { stdio: 'inherit' },
  )
}

async function resolveFuId(page, userId) {
  if (process.env.FU_ID) return process.env.FU_ID
  const headers = { 'X-User-Id': String(userId), 'Accept-Language': 'en' }
  const listed = await unwrapRes(
    await page.request.get(`${ORIGIN}/api/v1/function-units`, {
      headers,
      params: { name: 'P3 MI Dual Binding Test', page: 0, size: 20 },
    }),
  )
  const rows = listed.content ?? listed.records ?? (Array.isArray(listed) ? listed : [])
  const hit = (Array.isArray(rows) ? rows : []).find((row) => row.code === FU_CODE)
  if (!hit?.id) {
    throw new Error(`DW list did not contain ${FU_CODE}`)
  }
  return String(hit.id)
}

async function deployFromDw(page, userId, fuId) {
  const headers = { 'X-User-Id': String(userId), 'Accept-Language': 'en' }
  const started = await unwrapRes(
    await page.request.post(`${ORIGIN}/api/v1/function-units/${fuId}/deploy`, {
      headers,
      data: { autoEnable: true, changeLog: 'P3 MI dual-binding Complete e2e' },
      timeout: 120000,
    }),
  )
  console.log(`[deploy] started id=${started.deploymentId} status=${started.status}`)
  if (started.status === 'SUCCESS') return started
  if (started.status === 'FAILED') {
    throw new Error(`deploy failed immediately: ${started.message}`)
  }
  const deadline = Date.now() + 180000
  while (Date.now() < deadline) {
    await page.waitForTimeout(2000)
    const st = await unwrapRes(
      await page.request.get(`${ORIGIN}/api/v1/function-units/deployments/${started.deploymentId}/status`, {
        headers,
      }),
    )
    console.log(`[deploy] ${st.status} progress=${st.progress ?? '?'} ${st.message ?? ''}`)
    if (st.status === 'SUCCESS') return st
    if (st.status === 'FAILED') {
      const steps = Array.isArray(st.steps) ? st.steps.map((s) => `${s.name}:${s.status}:${s.message}`).join(' | ') : ''
      throw new Error(`deploy failed: ${st.message || ''} ${steps}`.trim())
    }
  }
  throw new Error('deploy timed out after 180s')
}

async function addNamedRow(page, tableIndex, tableTitle, fills) {
  const card = page.locator('.sub-table-field').filter({ hasText: tableTitle }).nth(tableIndex)
  await card.scrollIntoViewIfNeeded()
  await card.getByRole('button', { name: /^(Add|新增)$/ }).click()
  const dlg = page.getByRole('dialog').filter({ hasText: /Add Record|新增/ }).last()
  await dlg.waitFor({ state: 'visible', timeout: 15000 })
  for (const [hint, value] of fills) {
    const input = dlg.locator('.el-form-item').filter({ hasText: hint }).locator('input, textarea').first()
    await input.fill(String(value))
  }
  await dlg.getByRole('button', { name: /^(Save|保存|儲存)$/ }).click()
  await dlg.waitFor({ state: 'hidden', timeout: 15000 })
}

function subTablesOf(payload) {
  return payload?.formData?.__subTables__ ?? payload?.__subTables__ ?? {}
}

function taskFieldValues(form) {
  if (form?.fieldValues && typeof form.fieldValues === 'object') return form.fieldValues
  if (form?.formData && typeof form.formData === 'object') return form.formData
  return form ?? {}
}

function unionSubTableRows(left, right) {
  const map = new Map()
  for (const row of [...(Array.isArray(left) ? left : []), ...(Array.isArray(right) ? right : [])]) {
    if (!row || typeof row !== 'object') continue
    const id = row.id ?? row.platformRowUuid
    map.set(id != null ? String(id) : JSON.stringify(row), row)
  }
  return [...map.values()]
}

function assembleSubmitFormData(form) {
  const fieldValues = { ...taskFieldValues(form) }
  const sub = { ...(fieldValues.__subTables__ && typeof fieldValues.__subTables__ === 'object' ? fieldValues.__subTables__ : {}) }
  for (const binding of form?.subTableBindings ?? []) {
    const name = String(binding.tableName ?? '')
    if (!name) continue
    const key = name.startsWith('dw:') ? name : `dw:${name}`
    sub[key] = unionSubTableRows(sub[key], binding.data)
  }
  fieldValues.__subTables__ = sub
  return fieldValues
}

function stampPartyFileFks(payload) {
  const sub = subTablesOf(payload)
  const parties = Array.isArray(sub[PARTY_STORE]) ? sub[PARTY_STORE] : []
  const files = Array.isArray(sub[FILE_STORE]) ? sub[FILE_STORE] : []
  const alice = parties.find((p) => String(p.party_name) === 'Alice')
  const bob = parties.find((p) => String(p.party_name) === 'Bob')
  for (const file of files) {
    if (String(file.file_name) === 'alice-doc' && alice?.id) file.party_id = alice.id
    if (String(file.file_name) === 'bob-doc' && bob?.id) file.party_id = bob.id
  }
}

async function pollMineTasks(page, instanceId, predicate, label) {
  const deadline = Date.now() + 60000
  let last = '[]'
  while (Date.now() < deadline) {
    const mineRes = await page.request.post(`${ORIGIN}/api/portal/tasks/mine/by-process-instances`, {
      data: { processInstanceIds: [String(instanceId)] },
    })
    const mineBody = await mineRes.json().catch(() => ({}))
    const mineMap = mineBody.data ?? mineBody
    const mineTasks = mineMap?.[String(instanceId)] ?? mineMap?.[instanceId] ?? []
    const list = Array.isArray(mineTasks) ? mineTasks : []
    last = JSON.stringify(list.map((t) => ({ id: t.taskId ?? t.id, name: t.taskName }))).slice(0, 800)
    const hit = list.filter(predicate)
    if (hit.length) return hit
    await page.waitForTimeout(2000)
  }
  throw new Error(`${label}: timed out. last mine=${last}`)
}

async function loadFormData(page, taskId) {
  const res = await page.request.get(`${ORIGIN}/api/portal/tasks/${taskId}/form-data`)
  return unwrapRes(res)
}

function partyNameOfForm(form) {
  const data = assembleSubmitFormData(form)
  const item = data._currentItem ?? data.currentItem ?? {}
  if (item.party_name) return String(item.party_name)
  const parties = data.__subTables__?.[PARTY_STORE]
  const rowId = item.rowId ?? item.id ?? item.rowKey?.id
  if (Array.isArray(parties) && rowId != null) {
    const row = parties.find((p) => String(p.id) === String(rowId) || String(p.rowId) === String(rowId))
    if (row?.party_name) return String(row.party_name)
  }
  if (Array.isArray(parties) && parties.length === 1 && parties[0]?.party_name) {
    return String(parties[0].party_name)
  }
  return ''
}

function fileNamesOf(form) {
  const data = assembleSubmitFormData(form)
  const files = data.__subTables__?.[FILE_STORE] ?? []
  return (Array.isArray(files) ? files : []).map((r) => String(r.file_name ?? ''))
}

async function clickApprove(page) {
  const approveBtn = page.getByRole('button', { name: /^(Approve|批准|核准)$/ }).first()
  await approveBtn.click()
  const dlg = page.getByRole('dialog').last()
  await dlg.waitFor({ state: 'visible', timeout: 15000 })
  const confirm = dlg.getByRole('button', { name: /^(Confirm|确定|確認|OK)$/ }).last()
  await confirm.click()
}

async function completeReleaseViaApi(page, taskId, startFormData) {
  const form = await loadFormData(page, taskId)
  const assembled = assembleSubmitFormData(form)
  const fromStart = startFormData && typeof startFormData === 'object' ? startFormData : {}
  const startSub = fromStart.__subTables__ && typeof fromStart.__subTables__ === 'object' ? fromStart.__subTables__ : {}
  const assembledSub = assembled.__subTables__ && typeof assembled.__subTables__ === 'object' ? assembled.__subTables__ : {}
  const mergedSub = { ...startSub }
  for (const [key, rows] of Object.entries(assembledSub)) {
    mergedSub[key] = unionSubTableRows(startSub[key], rows)
  }
  const formData = {
    ...fromStart,
    ...assembled,
    __subTables__: mergedSub,
  }
  const parties = formData?.__subTables__?.[PARTY_STORE]
  console.log(`[release] formName=${form?.formName} bindings=${(form?.subTableBindings ?? []).length} fieldKeys=${Object.keys(form?.fieldValues ?? {}).join(',')}`)
  if (!Array.isArray(parties) || parties.length < 2) {
    throw new Error(
      `Release complete payload expected 2 parties, got ${JSON.stringify({
        partyLen: Array.isArray(parties) ? parties.length : parties,
        storeKeys: Object.keys(formData?.__subTables__ ?? {}),
      }).slice(0, 1200)}`,
    )
  }
  const res = await page.request.post(`${ORIGIN}/api/portal/tasks/${taskId}/complete`, {
    data: {
      taskId,
      action: 'APPROVE',
      comment: 'P3 MI dual-binding release',
      formData,
      variables: {
        approval_result: 'approved',
        approved: true,
        ...formData,
      },
    },
  })
  const body = await res.json().catch(() => ({}))
  console.log(`[release] api complete HTTP ${res.status()} ${JSON.stringify(body).slice(0, 400)}`)
  if (!res.ok()) {
    throw new Error(`Release API complete expected 200, got ${res.status()} ${JSON.stringify(body).slice(0, 800)}`)
  }
}

async function runPortalMiComplete(browser, userId) {
  const portalPage = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
  await loginViaPortalPassword(portalPage, { loginOrigin: ORIGIN })

  await portalPage.route(/\/api\/portal\/processes\/.*\/start/, async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue()
      return
    }
    const payload = route.request().postDataJSON() ?? {}
    stampPartyFileFks(payload)
    await route.continue({
      postData: JSON.stringify(payload),
      headers: {
        ...route.request().headers(),
        'content-type': 'application/json',
      },
    })
  })

  await portalPage.goto(`${ORIGIN}/portal/processes/start/${FU_CODE}`, {
    waitUntil: 'domcontentloaded',
  })
  await portalPage.locator('.sub-table-field').first().waitFor({ timeout: 30000 })
  const title = portalPage.locator('.el-form-item').filter({ hasText: /^Title/ }).locator('input').first()
  await title.fill('P3 MI dual complete')

  await addNamedRow(portalPage, 0, 'P3 MI Party', [
    ['Party name', 'Alice'],
    ['Assignee', userId],
  ])
  await addNamedRow(portalPage, 0, 'P3 MI File', [['File name', 'case-doc']])
  await addNamedRow(portalPage, 1, 'P3 MI File', [['File name', 'alice-doc']])
  await addNamedRow(portalPage, 0, 'P3 MI Party', [
    ['Party name', 'Bob'],
    ['Assignee', userId],
  ])
  await addNamedRow(portalPage, 1, 'P3 MI File', [['File name', 'bob-doc']])

  const filled = join(PORTAL_SHOTS, `${DATE}_p3-mi-dual-binding-start-filled.png`)
  await portalPage.screenshot({ path: filled, fullPage: true })
  console.log(`shot: ${filled}`)

  const startWait = portalPage.waitForRequest(
    (r) => r.method() === 'POST' && /\/processes\/.*\/start/.test(r.url()),
    { timeout: 30000 },
  )
  const startRespWait = portalPage.waitForResponse(
    (r) => r.request().method() === 'POST' && /\/processes\/.*\/start/.test(r.url()),
    { timeout: 30000 },
  )
  await portalPage.getByRole('button', { name: /Submit Application|提交申请|提交申請/ }).click()
  const startReq = await startWait
  const payload = startReq.postDataJSON() ?? {}
  const sub = subTablesOf(payload)
  const files = Array.isArray(sub[FILE_STORE]) ? sub[FILE_STORE] : []
  const parties = Array.isArray(sub[PARTY_STORE]) ? sub[PARTY_STORE] : []
  console.log(`[start] parties=${JSON.stringify(parties).slice(0, 800)}`)
  console.log(`[start] files=${JSON.stringify(files).slice(0, 1500)}`)
  if (parties.length < 2) {
    throw new Error(`start expected 2 parties, got ${parties.length}`)
  }
  const names = files.map((r) => String(r.file_name ?? ''))
  if (!names.includes('case-doc') || !names.includes('alice-doc') || !names.includes('bob-doc')) {
    throw new Error(`start missing file names: ${names.join(',')}`)
  }
  const bob = parties.find((p) => String(p.party_name) === 'Bob')
  const bobDoc = files.find((r) => String(r.file_name) === 'bob-doc')
  if (bob?.id && bobDoc && String(bobDoc.party_id ?? '') !== String(bob.id)) {
    console.log(`[start] bob-doc party_id=${bobDoc.party_id} (expected ${bob.id}; route stamp may apply after capture)`)
  }

  const startResp = await startRespWait
  const startBody = await startResp.json().catch(() => ({}))
  const instanceId =
    startBody.data?.id ||
    startBody.data?.processInstanceId ||
    startBody.id ||
    startBody.processInstanceId
  console.log(`[start] HTTP ${startResp.status()} instance=${instanceId ?? '?'}`)
  if (!instanceId) {
    throw new Error(`start did not return processInstanceId: ${JSON.stringify(startBody).slice(0, 800)}`)
  }

  const releaseTasks = await pollMineTasks(
    portalPage,
    instanceId,
    (t) => /Release MI Dual Binding/i.test(String(t.taskName ?? '')),
    'Release task',
  )
  const releaseId = releaseTasks[0].taskId ?? releaseTasks[0].id
  console.log(`[release] opening ${releaseId}`)
  await portalPage.goto(`${ORIGIN}/portal/tasks/${releaseId}`, { waitUntil: 'domcontentloaded' })
  await portalPage.waitForTimeout(4000)
  const releaseShot = join(PORTAL_SHOTS, `${DATE}_p3-mi-dual-binding-release.png`)
  await portalPage.screenshot({ path: releaseShot, fullPage: true })
  console.log(`shot: ${releaseShot}`)
  await completeReleaseViaApi(portalPage, releaseId, payload.formData ?? payload)

  const miTasks = await pollMineTasks(
    portalPage,
    instanceId,
    (t) => /MI Dual Binding Subtask/i.test(String(t.taskName ?? '')),
    'MI subtasks',
  )
  if (miTasks.length < 2) {
    throw new Error(`expected 2 MI todos, got ${miTasks.length}: ${JSON.stringify(miTasks).slice(0, 500)}`)
  }
  const withForms = []
  for (const task of miTasks) {
    const id = task.taskId ?? task.id
    const form = await loadFormData(portalPage, id)
    withForms.push({ id, form, party: partyNameOfForm(form) })
    console.log(`[mi] task=${id} party=${partyNameOfForm(form)} files=${fileNamesOf(form).join(',')}`)
  }
  const alice = withForms.find((t) => t.party === 'Alice') ?? withForms[0]
  const bobTask = withForms.find((t) => t.id !== alice.id)
  if (!bobTask) {
    throw new Error('could not find sibling MI task for Bob')
  }

  await portalPage.goto(`${ORIGIN}/portal/tasks/${alice.id}`, { waitUntil: 'domcontentloaded' })
  await portalPage.getByText('alice-doc', { exact: true }).first().waitFor({ timeout: 30000 })
  const aliceHydrate = join(PORTAL_SHOTS, `${DATE}_p3-mi-dual-binding-alice-hydrate.png`)
  await portalPage.screenshot({ path: aliceHydrate, fullPage: true })
  console.log(`shot: ${aliceHydrate}`)

  const completeWait = portalPage.waitForRequest(
    (r) => r.method() === 'POST' && r.url().includes(`/tasks/${alice.id}/complete`),
    { timeout: 30000 },
  )
  const completeRespWait = portalPage.waitForResponse(
    (r) => r.request().method() === 'POST' && r.url().includes(`/tasks/${alice.id}/complete`),
    { timeout: 30000 },
  )
  await clickApprove(portalPage)
  const completeReq = await completeWait
  const completePayload = completeReq.postDataJSON() ?? {}
  const scopes = completePayload.subTableBindingScopes
  console.log(`[alice-complete] scopes=${JSON.stringify(scopes)}`)
  console.log(`[alice-complete] emptied=${JSON.stringify(completePayload.emptiedSubTableKeys)}`)
  if (!Array.isArray(scopes) || scopes.length < 2) {
    throw new Error(`Alice complete expected >=2 binding scopes, got ${JSON.stringify(scopes)}`)
  }
  const storeKeys = new Set(scopes.map((s) => s.storeKey))
  if (!storeKeys.has(FILE_STORE)) {
    throw new Error(`Alice complete scopes missing ${FILE_STORE}: ${JSON.stringify(scopes)}`)
  }
  const bindingIds = new Set(scopes.map((s) => String(s.bindingId)))
  if (bindingIds.size < 2) {
    throw new Error(`Alice complete expected two binding ids, got ${JSON.stringify(scopes)}`)
  }
  if (completePayload.formData?.subTableBindingScopes || completePayload.variables?.subTableBindingScopes) {
    throw new Error('scopes must stay transport metadata, not inside formData/variables')
  }
  const completeResp = await completeRespWait
  const completeBody = await completeResp.json().catch(() => ({}))
  console.log(`[alice-complete] HTTP ${completeResp.status()} ${JSON.stringify(completeBody).slice(0, 400)}`)
  if (completeResp.status() !== 200) {
    throw new Error(`Alice complete expected 200, got ${completeResp.status()} ${JSON.stringify(completeBody).slice(0, 800)}`)
  }

  await portalPage.waitForTimeout(2000)
  const bobForm = await loadFormData(portalPage, bobTask.id)
  const bobFiles = fileNamesOf(bobForm)
  const bobStore = assembleSubmitFormData(bobForm).__subTables__?.[FILE_STORE]
  console.log(`[bob-after] files=${bobFiles.join(',')} raw=${JSON.stringify(bobStore ?? []).slice(0, 1200)}`)
  if (!bobFiles.includes('bob-doc')) {
    throw new Error(`Alice complete wiped Bob's file; Bob form files=${bobFiles.join(',')}`)
  }
  const afterShot = join(PORTAL_SHOTS, `${DATE}_p3-mi-dual-binding-bob-after-alice-complete.png`)
  await portalPage.goto(`${ORIGIN}/portal/tasks/${bobTask.id}`, { waitUntil: 'domcontentloaded' })
  await portalPage.waitForTimeout(6000)
  await portalPage.screenshot({ path: afterShot, fullPage: true })
  console.log(`shot: ${afterShot}`)
  console.log('OK  P3 MI dual-binding Complete kept sibling party file + sent binding scopes')
}

async function main() {
  if (process.env.SKIP_SQL !== '1') {
    applySql()
  } else {
    console.log('[sql] skipped')
  }
  const browser = await chromium.launch({ headless: true, channel: 'chrome' })
  try {
    let portalUserId = process.env.PORTAL_USER_ID
    if (process.env.SKIP_DEPLOY !== '1') {
      const dwPage = await browser.newPage({ viewport: { width: 1440, height: 900 } })
      const dwUser = await loginViaDwPassword(dwPage, { loginOrigin: ORIGIN })
      const fuId = await resolveFuId(dwPage, dwUser.userId)
      console.log(`[deploy] fuId=${fuId}`)
      const deploy = await deployFromDw(dwPage, dwUser.userId, fuId)
      console.log(`[deploy] SUCCESS version=${deploy.versionNumber ?? '?'}`)
    } else {
      console.log('[deploy] skipped')
    }
    if (!portalUserId) {
      const probe = await browser.newPage()
      const login = await loginViaPortalPassword(probe, { loginOrigin: ORIGIN })
      portalUserId = login.userId
      await probe.close()
    }
    console.log(`[portal] assignee userId=${portalUserId}`)
    await runPortalMiComplete(browser, portalUserId)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
