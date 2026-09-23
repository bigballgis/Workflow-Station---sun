#!/usr/bin/env node
/**
 * Two forms, each binding p0_mf_file twice.
 * Start on the PROCESS form, then save on the TASK review form.
 *
 *   node scripts/verify-p0-dual-binding-multi-form.mjs
 */
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const FU_CODE = 'p0-dual-binding-multi-form'
const DATE = new Date().toISOString().slice(0, 10)
const SHOTS = join(__dirname, '..', 'user-portal', 'verification-screenshots')
mkdirSync(SHOTS, { recursive: true })

async function unwrap(res) {
  const body = await res.json().catch(() => ({}))
  if (!res.ok()) throw new Error(`HTTP ${res.status()} ${JSON.stringify(body).slice(0, 800)}`)
  return body.data ?? body
}

function subTableCard(page, title) {
  const escaped = title.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return page.locator('.sub-table-field').filter({
    hasText: new RegExp(`^${escaped}(?:\\s|\\(|$)`),
  }).first()
}

async function addNamedRow(page, tableTitle, fieldHint, value) {
  const card = subTableCard(page, tableTitle)
  await card.scrollIntoViewIfNeeded()
  await card.getByRole('button', { name: /^(Add|新增)$/ }).click()
  const dlg = page.getByRole('dialog').filter({ hasText: /Add Record|新增/ }).last()
  await dlg.waitFor({ state: 'visible', timeout: 15000 })
  const input = dlg.locator('.el-form-item').filter({ hasText: fieldHint }).locator('input, textarea').first()
  await input.fill(value)
  const save = dlg.locator('button').filter({ hasText: /^(Save|保存|儲存)$/ }).last()
  const ready = Date.now() + 8000
  while (Date.now() < ready && await save.isDisabled().catch(() => true)) {
    await page.waitForTimeout(200)
  }
  if (await save.count() === 0) {
    const titles = await page.locator('.el-dialog').allInnerTexts()
    throw new Error(`no Save in ${tableTitle} dialog: ${titles.map((t) => t.replace(/\s+/g, ' ').slice(0, 180)).join(' || ')}`)
  }
  const notes = []
  const onResp = (res) => {
    if (res.status() >= 400) notes.push(`HTTP ${res.status()} ${res.url()}`)
  }
  page.on('response', onResp)
  await save.click()
  await page.waitForTimeout(600)
  const toast = await page.locator('.el-message').allInnerTexts().catch(() => [])
  const fieldError = await dlg.locator('.el-form-item__error').allInnerTexts().catch(() => [])
  const closed = await dlg.waitFor({ state: 'hidden', timeout: 8000 }).then(() => true).catch(() => false)
  page.off('response', onResp)
  if (!closed) {
    const text = (await dlg.innerText()).replace(/\s+/g, ' ').slice(0, 400)
    const shot = join(SHOTS, `${DATE}_p0-multi-form-add-stuck.png`)
    await page.screenshot({ path: shot, fullPage: true })
    throw new Error(
      `dialog stayed open for ${tableTitle}: ${text} toast=${JSON.stringify(toast)} field=${JSON.stringify(fieldError)} http=${notes.join('; ')} shot=${shot}`,
    )
  }
}

async function editNamedRow(page, tableTitle, rowText, fieldHint, value) {
  const card = subTableCard(page, tableTitle)
  await card.scrollIntoViewIfNeeded()
  const row = card.locator('.el-table__body tr').filter({ hasText: rowText }).first()
  await row.getByRole('button', { name: /^(Edit|编辑|編輯)$/ }).click()
  const dlg = page.getByRole('dialog').filter({ hasText: /Edit Record|编辑记录|編輯/ }).last()
  await dlg.waitFor({ state: 'visible', timeout: 15000 })
  const input = dlg.locator('.el-form-item').filter({ hasText: fieldHint }).locator('input, textarea').first()
  await input.fill(value)
  const save = dlg.locator('button').filter({ hasText: /^(Save|保存|儲存)$/ }).last()
  await save.click()
  const closed = await dlg.waitFor({ state: 'hidden', timeout: 8000 }).then(() => true).catch(() => false)
  if (!closed) {
    const text = (await dlg.innerText()).replace(/\s+/g, ' ').slice(0, 400)
    throw new Error(`edit dialog stayed open for ${tableTitle}: ${text}`)
  }
}

async function deleteNamedRow(page, tableTitle, rowText) {
  const card = subTableCard(page, tableTitle)
  await card.scrollIntoViewIfNeeded()
  const row = card.locator('.el-table__body tr').filter({ hasText: rowText }).first()
  await row.getByRole('button', { name: /^(Delete|删除|刪除)$/ }).click()
  const box = page.locator('.el-message-box').last()
  await box.waitFor({ state: 'visible', timeout: 8000 })
  await box.locator('.el-button--primary').click()
  await box.waitFor({ state: 'hidden', timeout: 8000 })
}

async function saveTask(page, taskId) {
  const saveRespPromise = page.waitForResponse(
    (r) => r.request().method() === 'POST' && r.url().includes(`/tasks/${taskId}/submit`),
    { timeout: 30000 },
  )
  await page.getByRole('button', { name: /^(Save|保存|儲存)$/ }).first().click()
  const saveResp = await saveRespPromise
  const saveBody = await saveResp.json().catch(() => ({}))
  const saveReq = saveResp.request().postDataJSON() ?? {}
  const saved = saveReq.formData?.__subTables__?.['dw:p0_mf_file'] ?? []
  return { saveResp, saveBody, saved }
}

async function fileBindings(page, headers, fuId, formId) {
  const rows = await unwrap(
    await page.request.get(`${ORIGIN}/api/v1/function-units/${fuId}/forms/${formId}/bindings`, { headers }),
  )
  return (Array.isArray(rows) ? rows : []).filter(
    (b) => b.bindingType === 'SUB' && (b.tableName === 'p0_mf_file' || b.table?.tableName === 'p0_mf_file'),
  )
}

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' })
  const dwPage = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  try {
    const dwUser = await loginViaDwPassword(dwPage, { loginOrigin: ORIGIN })
    const headers = { 'X-User-Id': String(dwUser.userId), 'Accept-Language': 'en' }
    const listed = await unwrap(await dwPage.request.get(`${ORIGIN}/api/v1/function-units`, {
      headers,
      params: { name: 'P0 Dual Binding Multi Form', page: 0, size: 20 },
    }))
    const rows = listed.content ?? listed.records ?? (Array.isArray(listed) ? listed : [])
    const fu = (Array.isArray(rows) ? rows : []).find((row) => row.code === FU_CODE)
    if (!fu?.id) throw new Error(`DW list did not contain ${FU_CODE}`)
    const forms = await unwrap(await dwPage.request.get(`${ORIGIN}/api/v1/function-units/${fu.id}/forms`, { headers }))
    const formList = Array.isArray(forms) ? forms : []
    const fill = formList.find((f) => f.formName === 'P0 MF Fill Form')
    const review = formList.find((f) => f.formName === 'P0 MF Review Form')
    if (!fill?.id || fill.formType !== 'PROCESS') throw new Error(`fill form missing or not PROCESS: ${JSON.stringify(fill)}`)
    if (!review?.id || review.formType !== 'TASK') throw new Error(`review form missing or not TASK: ${JSON.stringify(review)}`)
    const fillFiles = await fileBindings(dwPage, headers, fu.id, fill.id)
    const reviewFiles = await fileBindings(dwPage, headers, fu.id, review.id)
    if (fillFiles.length !== 2 || reviewFiles.length !== 2) {
      throw new Error(`expected 2 file bindings per form, fill=${fillFiles.length} review=${reviewFiles.length}`)
    }
    const fillFilters = new Set(fillFiles.map((b) => String(b.filterFkFieldId)))
    const reviewFilters = new Set(reviewFiles.map((b) => String(b.filterFkFieldId)))
    if (fillFilters.size !== 2 || reviewFilters.size !== 2) {
      throw new Error(`each form must use two different filter FKs: fill=${[...fillFilters]} review=${[...reviewFilters]}`)
    }
    const fillIds = new Set(fillFiles.map((b) => String(b.id)))
    const reviewIds = reviewFiles.map((b) => String(b.id))
    if (reviewIds.some((id) => fillIds.has(id))) throw new Error('review bindings reused fill binding ids')
    console.log(`[design] fill bindings ${[...fillIds].join(',')} review bindings ${reviewIds.join(',')}`)

    const dup = await dwPage.request.post(`${ORIGIN}/api/v1/function-units/${fu.id}/forms/${review.id}/bindings`, {
      headers,
      data: {
        tableId: reviewFiles[0].tableId,
        bindingType: 'SUB',
        bindingMode: 'EDITABLE',
        foreignKeyField: reviewFiles[0].foreignKeyField,
        bindingLinkMode: 'structuralFk',
        subMode: 'FULL',
        filterFkFieldId: reviewFiles[0].filterFkFieldId,
      },
    })
    const dupBody = await dup.json().catch(() => ({}))
    const dupCode = dupBody.code ?? dupBody.error?.code ?? dupBody.errorCode
    if (dup.ok() || dupCode !== 'BINDING_EXISTS') {
      throw new Error(`duplicate filter on the review form expected BINDING_EXISTS, got HTTP ${dup.status()} ${JSON.stringify(dupBody).slice(0, 400)}`)
    }
    console.log('[design] second form still rejects a repeated filter')

    if (process.env.SKIP_DEPLOY === '1') {
      console.log('[deploy] skipped')
    } else {
    const started = await unwrap(await dwPage.request.post(`${ORIGIN}/api/v1/function-units/${fu.id}/deploy`, {
      headers,
      data: { autoEnable: true, changeLog: 'Multi-form dual-binding catalog publish' },
      timeout: 120000,
    }))
    console.log(`[deploy] started ${started.deploymentId} ${started.status}`)
    let deploy = started
    const deadline = Date.now() + 180000
    while (deploy.status !== 'SUCCESS' && deploy.status !== 'FAILED' && Date.now() < deadline) {
      await dwPage.waitForTimeout(2000)
      deploy = await unwrap(await dwPage.request.get(
        `${ORIGIN}/api/v1/function-units/deployments/${started.deploymentId}/status`,
        { headers },
      ))
      console.log(`[deploy] ${deploy.status} ${deploy.message ?? ''}`)
    }
    if (deploy.status !== 'SUCCESS') throw new Error(`deploy ${deploy.status}: ${deploy.message ?? ''}`)
    }

    const portal = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
    await loginViaPortalPassword(portal, { loginOrigin: ORIGIN })
    await portal.goto(`${ORIGIN}/portal/processes/start/${FU_CODE}`, { waitUntil: 'domcontentloaded' })
    await portal.waitForTimeout(8000)
    const startText = await portal.locator('body').innerText()
    if (!startText.includes('Case files') || startText.includes('Review case files')) {
      throw new Error('start page should show the fill form, not the review form')
    }
    await portal.locator('.el-form-item').filter({ hasText: /^Title/ }).locator('input').first().fill('multi form')
    await addNamedRow(portal, 'Case files', 'File name', 'case-doc')
    await addNamedRow(portal, 'Parties', 'Party name', 'Alice')
    await addNamedRow(portal, 'Party files', 'File name', 'party-doc')

    const startRespPromise = portal.waitForResponse(
      (r) => r.request().method() === 'POST' && /\/processes\/.*\/start/.test(r.url()),
      { timeout: 30000 },
    )
    await portal.getByRole('button', { name: /Submit Application|提交申请|提交申請/ }).click()
    const startResp = await startRespPromise
    const startReq = startResp.request().postDataJSON() ?? {}
    const files = startReq.formData?.__subTables__?.['dw:p0_mf_file'] ?? []
    const parties = startReq.formData?.__subTables__?.['dw:p0_mf_party'] ?? []
    const mainId = startReq.formData?.id
    const aliceId = parties[0]?.id
    console.log(`[start] HTTP ${startResp.status()} files=${files.map((r) => r.file_name).join(',')} party=${aliceId} case=${mainId}`)
    if (startResp.status() !== 200) throw new Error(`start failed ${startResp.status()}`)
    const caseRow = files.find((r) => r.file_name === 'case-doc')
    const partyRow = files.find((r) => r.file_name === 'party-doc')
    if (!caseRow || String(caseRow.case_id) !== String(mainId)) throw new Error(`case-doc.case_id=${caseRow?.case_id}`)
    if (!partyRow || String(partyRow.party_id) !== String(aliceId)) throw new Error(`party-doc.party_id=${partyRow?.party_id}`)
    if (String(caseRow.party_id ?? '').trim() || String(partyRow.case_id ?? '').trim()) {
      throw new Error(`filters crossed: ${JSON.stringify(files)}`)
    }
    const startBody = await startResp.json().catch(() => ({}))
    const instanceId = startBody.data?.id || startBody.data?.processInstanceId || startBody.id
    if (!instanceId) throw new Error('start returned no process instance')

    // Mine list is cached for about 15s, so the review task can be missing on the first poll.
    let task = null
    let mineBody = {}
    const mineUntil = Date.now() + 20000
    while (Date.now() < mineUntil) {
      const mineRes = await portal.request.post(`${ORIGIN}/api/portal/tasks/mine/by-process-instances`, {
        data: { processInstanceIds: [String(instanceId)] },
      })
      mineBody = await mineRes.json().catch(() => ({}))
      const mineMap = mineBody.data ?? mineBody
      const mineTasks = mineMap?.[String(instanceId)] ?? []
      task = Array.isArray(mineTasks) ? mineTasks[0] : null
      if (task?.taskId || task?.id) break
      await portal.waitForTimeout(2000)
    }
    const taskId = task?.taskId ?? task?.id
    if (!taskId) throw new Error(`no review task for ${instanceId}: ${JSON.stringify(mineBody).slice(0, 400)}`)
    console.log(`[task] ${taskId} ${task.taskName ?? task.name ?? ''}`)

    await portal.goto(`${ORIGIN}/portal/tasks/${taskId}`, { waitUntil: 'domcontentloaded' })
    await portal.waitForTimeout(8000)
    const reviewText = await portal.locator('body').innerText()
    if (!reviewText.includes('Review case files') || !reviewText.includes('Review party files')) {
      throw new Error('review task did not render the second form')
    }
    const caseCard = String(await subTableCard(portal, 'Review case files').innerText())
    const partyCard = String(await subTableCard(portal, 'Review party files').innerText())
    const partyList = String(await subTableCard(portal, 'Review parties').innerText())
    if (!caseCard.includes('case-doc') || caseCard.includes('party-doc')) {
      throw new Error(`review case files slice wrong: ${caseCard.replace(/\s+/g, ' ').slice(0, 300)}`)
    }
    if (!partyCard.includes('party-doc') || partyCard.includes('case-doc')) {
      throw new Error(`review party files slice wrong: ${partyCard.replace(/\s+/g, ' ').slice(0, 300)}`)
    }
    if (!partyList.includes('Alice')) throw new Error('review parties missing Alice')
    console.log('[review] second form shows each file on its own filter')

    await addNamedRow(portal, 'Review party files', 'File name', 'review-doc')
    const saveRespPromise = portal.waitForResponse(
      (r) => r.request().method() === 'POST' && r.url().includes(`/tasks/${taskId}/submit`),
      { timeout: 30000 },
    )
    await portal.getByRole('button', { name: /^(Save|保存|儲存)$/ }).first().click()
    const saveResp = await saveRespPromise
    const saveBody = await saveResp.json().catch(() => ({}))
    const saveReq = saveResp.request().postDataJSON() ?? {}
    const saved = saveReq.formData?.__subTables__?.['dw:p0_mf_file'] ?? []
    const scopeIds = new Set((saveReq.subTableBindingScopes ?? []).map((s) => String(s.bindingId)))
    console.log(`[review] save HTTP ${saveResp.status()} files=${saved.map((r) => `${r.file_name}:${r._wsRowVersion ?? '-'}`).join(',')} scopes=${[...scopeIds].join(',')}`)
    if (saveResp.status() !== 200) throw new Error(`review save ${saveResp.status()} ${JSON.stringify(saveBody).slice(0, 500)}`)
    if (saved.length !== 3) throw new Error(`review save expected 3 file rows, got ${JSON.stringify(saved).slice(0, 800)}`)
    if (!reviewIds.every((id) => scopeIds.has(id))) {
      throw new Error(`review save scopes ${[...scopeIds]} did not use review bindings ${reviewIds}`)
    }
    if ([...fillIds].some((id) => scopeIds.has(id))) {
      throw new Error('review save claimed a fill-form binding')
    }

    await portal.reload({ waitUntil: 'domcontentloaded' })
    await portal.waitForTimeout(8000)
    const after = await portal.locator('body').innerText()
    for (const name of ['case-doc', 'party-doc', 'review-doc', 'Alice']) {
      if (!after.includes(name)) throw new Error(`after reload missing ${name}`)
    }
    const shot = join(SHOTS, `${DATE}_p0-multi-form-review-after-reload.png`)
    await portal.screenshot({ path: shot, fullPage: true })
    console.log(`shot: ${shot}`)

    await editNamedRow(portal, 'Review party files', 'review-doc', 'File name', 'review-doc-edited')
    const edited = await saveTask(portal, taskId)
    const editedNames = edited.saved.map((r) => r.file_name)
    console.log(`[review] edit HTTP ${edited.saveResp.status()} files=${editedNames.join(',')}`)
    if (edited.saveResp.status() !== 200) {
      throw new Error(`review edit save ${edited.saveResp.status()} ${JSON.stringify(edited.saveBody).slice(0, 500)}`)
    }
    if (edited.saved.length !== 3 || !editedNames.includes('review-doc-edited') || editedNames.includes('review-doc')) {
      throw new Error(`edit did not rename the party file: ${JSON.stringify(edited.saved).slice(0, 800)}`)
    }
    if (!editedNames.includes('case-doc') || !editedNames.includes('party-doc')) {
      throw new Error(`edit dropped another file: ${editedNames.join(',')}`)
    }

    await portal.reload({ waitUntil: 'domcontentloaded' })
    await portal.waitForTimeout(8000)
    const partyAfterEdit = String(await subTableCard(portal, 'Review party files').innerText())
    const caseAfterEdit = String(await subTableCard(portal, 'Review case files').innerText())
    if (!partyAfterEdit.includes('review-doc-edited') || partyAfterEdit.includes('case-doc')) {
      throw new Error(`after edit, party files slice wrong: ${partyAfterEdit.replace(/\s+/g, ' ').slice(0, 300)}`)
    }
    if (!caseAfterEdit.includes('case-doc') || caseAfterEdit.includes('party-doc') || caseAfterEdit.includes('review-doc')) {
      throw new Error(`after edit, case files slice wrong: ${caseAfterEdit.replace(/\s+/g, ' ').slice(0, 300)}`)
    }

    await deleteNamedRow(portal, 'Review party files', 'review-doc-edited')
    const removed = await saveTask(portal, taskId)
    const removedNames = removed.saved.map((r) => r.file_name)
    console.log(`[review] delete HTTP ${removed.saveResp.status()} files=${removedNames.join(',')}`)
    if (removed.saveResp.status() !== 200) {
      throw new Error(`review delete save ${removed.saveResp.status()} ${JSON.stringify(removed.saveBody).slice(0, 500)}`)
    }
    if (removed.saved.length !== 2 || removedNames.some((name) => String(name).includes('review-doc'))) {
      throw new Error(`delete did not drop only the edited party file: ${JSON.stringify(removed.saved).slice(0, 800)}`)
    }
    if (!removedNames.includes('case-doc') || !removedNames.includes('party-doc')) {
      throw new Error(`delete dropped a file that should remain: ${removedNames.join(',')}`)
    }

    await portal.reload({ waitUntil: 'domcontentloaded' })
    await portal.waitForTimeout(8000)
    const taskAfterDelete = await portal.locator('body').innerText()
    if (taskAfterDelete.includes('review-doc')) throw new Error('deleted file still on the review task')
    if (!taskAfterDelete.includes('case-doc') || !taskAfterDelete.includes('party-doc') || !taskAfterDelete.includes('Alice')) {
      throw new Error('review task lost case-doc, party-doc, or Alice after delete')
    }

    await portal.goto(`${ORIGIN}/portal/applications/${instanceId}`, { waitUntil: 'domcontentloaded' })
    await portal.waitForTimeout(8000)
    const myRequest = await portal.locator('body').innerText()
    if (!myRequest.includes('Review case files') || !myRequest.includes('Review party files')) {
      throw new Error(`my request did not render the review form: ${myRequest.replace(/\s+/g, ' ').slice(0, 300)}`)
    }
    const myCase = String(await subTableCard(portal, 'Review case files').innerText())
    const myParty = String(await subTableCard(portal, 'Review party files').innerText())
    const myParties = String(await subTableCard(portal, 'Review parties').innerText())
    if (!myCase.includes('case-doc') || myCase.includes('party-doc') || myCase.includes('Party-')) {
      throw new Error(`my request case files slice wrong: ${myCase.replace(/\s+/g, ' ').slice(0, 400)}`)
    }
    if (!myParty.includes('party-doc') || myParty.includes('case-doc')) {
      throw new Error(`my request party files slice wrong: ${myParty.replace(/\s+/g, ' ').slice(0, 400)}`)
    }
    if (!myParties.includes('Alice')) throw new Error('my request parties missing Alice')
    const myShot = join(SHOTS, `${DATE}_p0-multi-form-my-request-slices.png`)
    await portal.screenshot({ path: myShot, fullPage: true })
    console.log(`shot: ${myShot}`)
    console.log('OK  create, review read, edit, delete, and my-request slices')
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
