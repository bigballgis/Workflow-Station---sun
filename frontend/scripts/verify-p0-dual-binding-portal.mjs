#!/usr/bin/env node
/**
 * Deploy local P0 dual-binding FU to Admin catalog, then open Portal start.
 * Deploy uses the DW one-click API.
 */
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const FU_CODE = process.env.FU_CODE ?? 'p0-dual-binding-test'
const DATE = new Date().toISOString().slice(0, 10)
const PORTAL_SHOTS = join(__dirname, '..', 'user-portal', 'verification-screenshots')
mkdirSync(PORTAL_SHOTS, { recursive: true })

async function unwrap(res) {
  const body = await res.json().catch(() => ({}))
  if (!res.ok()) {
    throw new Error(`HTTP ${res.status()} ${JSON.stringify(body).slice(0, 800)}`)
  }
  return body.data ?? body
}

async function resolveFuId(page, userId) {
  if (process.env.FU_ID) return process.env.FU_ID
  const headers = { 'X-User-Id': String(userId), 'Accept-Language': 'en' }
  const listed = await unwrap(
    await page.request.get(`${ORIGIN}/api/v1/function-units`, {
      headers,
      params: { name: 'P0 Dual Binding Test', page: 0, size: 20 },
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
  const headers = {
    'X-User-Id': String(userId),
    'Accept-Language': 'en',
  }
  const started = await unwrap(
    await page.request.post(`${ORIGIN}/api/v1/function-units/${fuId}/deploy`, {
      headers,
      data: { autoEnable: true, changeLog: 'P0 dual-binding local catalog publish' },
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
    const st = await unwrap(
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

async function addNamedRow(page, tableIndex, tableTitle, fieldHint, value) {
  const card = page.locator('.sub-table-field').filter({ hasText: tableTitle }).nth(tableIndex)
  await card.scrollIntoViewIfNeeded()
  await card.getByRole('button', { name: /^(Add|新增)$/ }).click()
  const dlg = page.getByRole('dialog').filter({ hasText: /Add Record|新增/ }).last()
  await dlg.waitFor({ state: 'visible', timeout: 15000 })
  const input = dlg.locator('.el-form-item').filter({ hasText: fieldHint }).locator('input, textarea').first()
  await input.fill(value)
  await dlg.getByRole('button', { name: /^(Save|保存|儲存)$/ }).click()
  await dlg.waitFor({ state: 'hidden', timeout: 15000 })
}

async function runPortalWrite(browser) {
  const portalPage = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
  await loginViaPortalPassword(portalPage, { loginOrigin: ORIGIN })
  await portalPage.goto(`${ORIGIN}/portal/processes/start/${FU_CODE}`, {
    waitUntil: 'domcontentloaded',
  })
  await portalPage.waitForTimeout(8000)
  const title = portalPage.locator('.el-form-item').filter({ hasText: /^Title/ }).locator('input').first()
  await title.fill('P0 dual write')

  await addNamedRow(portalPage, 0, 'P0 Dual File', 'File name', 'case-doc')
  // Create the case-only row before a Party exists; otherwise structural FK fill
  // correctly makes it an intersection row owned by both file bindings.
  await addNamedRow(portalPage, 0, 'P0 Dual Party', 'Party name', 'Alice')
  await addNamedRow(portalPage, 1, 'P0 Dual File', 'File name', 'party-doc')

  const filled = join(PORTAL_SHOTS, `${DATE}_p0-dual-binding-portal-filled.png`)
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
  const sub = payload.formData?.__subTables__ ?? payload.__subTables__ ?? {}
  const files = Array.isArray(sub['dw:p0_dual_file']) ? sub['dw:p0_dual_file'] : []
  const parties = Array.isArray(sub['dw:p0_dual_party']) ? sub['dw:p0_dual_party'] : []
  console.log(`[start] party rows=${parties.length} file rows=${files.length} names=${files.map((r) => r.file_name).join(',')}`)
  console.log(`[start] formData.id=${JSON.stringify(payload.formData?.id ?? payload.id ?? null)}`)
  console.log(`[start] files=${JSON.stringify(files).slice(0, 1500)}`)
  console.log(`[start] parties=${JSON.stringify(parties).slice(0, 800)}`)
  const mainId = payload.formData?.id ?? payload.id
  if (mainId == null || String(mainId).trim() === '') {
    throw new Error('start payload missing MAIN id after PK preserve')
  }
  const fileCaseIds = files.map((r) => String(r.case_id ?? ''))
  if (fileCaseIds.some((id) => id !== String(mainId))) {
    throw new Error(`start file case_id must match MAIN id ${mainId}, got ${fileCaseIds.join(',')}`)
  }
  const aliceId = parties[0]?.id
  const partyDoc = files.find((r) => String(r.file_name) === 'party-doc')
  if (aliceId && partyDoc && String(partyDoc.party_id ?? '') !== String(aliceId)) {
    throw new Error(`party-doc party_id expected ${aliceId}, got ${partyDoc.party_id}`)
  }
  if (files.length < 2) {
    throw new Error(`start payload dw:p0_dual_file expected 2 union-merged rows, got ${JSON.stringify(files).slice(0, 500)}`)
  }
  const names = files.map((r) => String(r.file_name ?? ''))
  if (!names.includes('case-doc') || !names.includes('party-doc')) {
    throw new Error(`start payload missing both file names: ${names.join(',')}`)
  }
  if (payload.subTableBindingScopes) {
    console.log(`[start] scopes=${JSON.stringify(payload.subTableBindingScopes)}`)
  } else {
    console.log('[start] no subTableBindingScopes (start path stamps union-merge only)')
  }

  const startResp = await startRespWait
  const startBody = await startResp.json().catch(() => ({}))
  const instanceId =
    startBody.data?.id ||
    startBody.data?.processInstanceId ||
    startBody.id ||
    startBody.processInstanceId
  console.log(`[start] HTTP ${startResp.status()} instance=${instanceId ?? '?'}`)

  await portalPage.waitForTimeout(4000)
  const after = join(PORTAL_SHOTS, `${DATE}_p0-dual-binding-portal-after-start.png`)
  await portalPage.screenshot({ path: after, fullPage: true })
  console.log(`shot: ${after}`)

  if (!instanceId) {
    console.log('OK  portal start union-merged file rows (no instance id; skip task save)')
    return
  }

  const mineRes = await portalPage.request.post(`${ORIGIN}/api/portal/tasks/mine/by-process-instances`, {
    data: { processInstanceIds: [String(instanceId)] },
  })
  const mineBody = await mineRes.json().catch(() => ({}))
  const mineMap = mineBody.data ?? mineBody
  const mineTasks = mineMap?.[String(instanceId)] ?? mineMap?.[instanceId] ?? []
  const task = Array.isArray(mineTasks) ? mineTasks[0] : null
  if (!task?.taskId && !task?.id) {
    console.log(`[task] no todo for instance ${instanceId}; mine HTTP ${mineRes.status()} body=${JSON.stringify(mineBody).slice(0, 400)}`)
    console.log('OK  portal start union-merged file rows (no assignee task yet)')
    return
  }
  const taskId = task.taskId ?? task.id
  console.log(`[task] opening ${taskId}`)
  portalPage.on('response', async (res) => {
    const url = res.url()
    if (!/\/api\//.test(url) || res.status() >= 400) return
    const ct = String(res.headers()['content-type'] ?? '')
    if (!ct.includes('json')) return
    const body = await res.json().catch(() => null)
    if (!body) return
    const text = JSON.stringify(body)
    if (!/p0_dual_file|__subTables__|filterFk/.test(text)) return
    const slice = text.includes('p0_dual_file')
      ? text.slice(Math.max(0, text.indexOf('p0_dual_file') - 80), text.indexOf('p0_dual_file') + 700)
      : text.slice(0, 400)
    console.log(`[api] ${res.status()} ${url} … ${slice}`)
  })
  await portalPage.goto(`${ORIGIN}/portal/tasks/${taskId}`, { waitUntil: 'domcontentloaded' })
  await portalPage.waitForTimeout(8000)
  const fileCards = portalPage.locator('.sub-table-field').filter({ hasText: /P0 Dual File/i })
  const fileCardCount = await fileCards.count()
  if (fileCardCount < 2) {
    throw new Error(`P2 hydrate: expected 2 file widgets, got ${fileCardCount}`)
  }
  const widgetTexts = []
  for (let i = 0; i < fileCardCount; i++) {
    const text = String(await fileCards.nth(i).innerText()).replace(/\s+/g, ' ').slice(0, 500)
    widgetTexts.push(text)
    console.log(`[task] file widget ${i}/${fileCardCount}: ${text}`)
  }
  const widget0 = widgetTexts[0] ?? ''
  const widget1 = widgetTexts[1] ?? ''
  if (!/\bcase-doc\b/.test(widget0) || !/\bparty-doc\b/.test(widget0)) {
    throw new Error(`P2 display: MAIN-filter widget must show intersection (case-doc + party-doc), got ${widget0}`)
  }
  if (!/\bparty-doc\b/.test(widget1) || /\bcase-doc\b/.test(widget1)) {
    throw new Error(`P2 display: party-filter widget must show only party-doc, got ${widget1}`)
  }
  const hydrateShot = join(PORTAL_SHOTS, `${DATE}_p0-dual-binding-portal-task-hydrate.png`)
  await portalPage.screenshot({ path: hydrateShot, fullPage: true })
  console.log(`shot: ${hydrateShot}`)
  const submitWait = portalPage.waitForRequest(
    (r) => r.method() === 'POST' && r.url().includes(`/tasks/${taskId}/submit`),
    { timeout: 30000 },
  )
  const submitRespWait = portalPage.waitForResponse(
    (r) => r.request().method() === 'POST' && r.url().includes(`/tasks/${taskId}/submit`),
    { timeout: 30000 },
  )
  const saveBtn = portalPage.getByRole('button', { name: /^(Save|保存|儲存)$/ }).first()
  if ((await saveBtn.count()) === 0) {
    throw new Error('task detail has no Save button')
  }
  await saveBtn.click()
  const submitReq = await submitWait
  const submitPayload = submitReq.postDataJSON() ?? {}
  const savedFiles = submitPayload.formData?.__subTables__?.['dw:p0_dual_file']
  if (!Array.isArray(savedFiles) || savedFiles.length < 2) {
    throw new Error(`task submit store must keep both file rows, got ${JSON.stringify(savedFiles).slice(0, 500)}`)
  }
  const scopes = submitPayload.subTableBindingScopes
  console.log(`[task] scopes=${JSON.stringify(scopes)}`)
  if (!Array.isArray(scopes) || scopes.length < 2) {
    throw new Error(`task submit expected >=2 binding scopes, got ${JSON.stringify(scopes)}`)
  }
  const storeKeys = new Set(scopes.map((s) => s.storeKey))
  if (!storeKeys.has('dw:p0_dual_file')) {
    throw new Error(`task scopes missing dw:p0_dual_file: ${JSON.stringify(scopes)}`)
  }
  const bindingIds = new Set(scopes.map((s) => String(s.bindingId)))
  if (bindingIds.size < 2) {
    throw new Error(`task scopes expected two binding ids, got ${JSON.stringify(scopes)}`)
  }
  const submitResp = await submitRespWait
  const submitBody = await submitResp.json().catch(() => ({}))
  console.log(`[task] submit HTTP ${submitResp.status()} ${JSON.stringify(submitBody).slice(0, 400)}`)
  if (submitResp.status() !== 200) {
    throw new Error(`task submit expected HTTP 200, got ${submitResp.status()} ${JSON.stringify(submitBody).slice(0, 800)}`)
  }
  const taskShot = join(PORTAL_SHOTS, `${DATE}_p0-dual-binding-portal-task-save.png`)
  await portalPage.screenshot({ path: taskShot, fullPage: true })
  console.log(`shot: ${taskShot}`)
  console.log(`OK  portal start union-merge + task submit scopes (${scopes.length}) HTTP 200`)
}

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' })
  try {
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
    await runPortalWrite(browser)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
