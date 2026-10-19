#!/usr/bin/env node
/**
 * To Do「可批量操作」end-to-end on the FU-call demo (Purchase Request).
 *
 * Stage `dw`     — DW UI: switch "Allow batch processing" on for NODE_ON and off for NODE_OFF,
 *                  click Save, read the saved BPMN back, reload and re-read the switch, deploy.
 * Stage `portal` — start START_COUNT requests, approve each one's vendor review the normal way
 *                  (single task) so they reach NODE_ON, then in To Do > Batch Actions open the
 *                  group, select SELECT_COUNT rows and run the batch action through the real UI.
 *                  Screenshots at each step; picked / untouched task ids are printed for DB checks.
 *
 * Usage (from frontend/):
 *   node scripts/verify-todo-batch.mjs dw
 *   node scripts/verify-todo-batch.mjs portal
 *
 * Env: DW_FU_ID (50007), NODE_ON (PR_Task_Approve), NODE_OFF (PR_Task_Raise),
 *      PROCESS_KEY (fu-call-demo-purchase), REVIEW_NODE (VC_Task_Review),
 *      START_COUNT (3), SELECT_COUNT (2), BATCH_ACTION (index of the batch button, 0), VERIFY_ORIGIN.
 */
import { mkdirSync } from 'fs'
import { join } from 'path'
import { chromium } from 'playwright'
import { loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'

const STAGE = process.argv[2] || 'dw'
const ORIGIN = (process.env.VERIFY_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const FU_ID = process.env.DW_FU_ID || '50007'
const NODE_ON = process.env.NODE_ON || 'PR_Task_Approve'
const NODE_OFF = (process.env.NODE_OFF ?? 'PR_Task_Raise').split(',').filter(Boolean)
const PROCESS_KEY = process.env.PROCESS_KEY || 'fu-call-demo-purchase'
/** Steps before NODE_ON that each request passes one task at a time (vendor review, budget-line confirm). */
const ADVANCE_NODES = (process.env.ADVANCE_NODES ?? 'VC_Task_Review,PR_MI_Task_Confirm').split(',').filter(Boolean)
const START_COUNT = Number(process.env.START_COUNT || 3)
const SELECT_COUNT = Number(process.env.SELECT_COUNT || 2)
const BATCH_ACTION = Number(process.env.BATCH_ACTION || 0)
const DATE = new Date().toISOString().slice(0, 10)
const SWITCH_LABEL = /Allow batch processing|允许批量处理/
/** Appended to screenshot names so runs (e.g. before/after a fix) do not overwrite each other. */
const TAG = process.env.SHOT_TAG ? `-${process.env.SHOT_TAG}` : ''

function shot(app, slug) {
  const dir = join(process.cwd(), app, 'verification-screenshots')
  mkdirSync(dir, { recursive: true })
  return join(dir, `${DATE}_${slug}${TAG}.png`)
}

function unwrap(body) {
  return body && typeof body === 'object' && 'data' in body ? body.data : body
}

function apiFor(page, prefix) {
  return async (method, path, data) => {
    const res = await page.request.fetch(`${ORIGIN}${prefix}${path}`, { method, data, timeout: 120000 })
    const body = await res.json().catch(() => ({}))
    if (!res.ok()) throw new Error(`${method} ${path} -> ${res.status()} ${JSON.stringify(body).slice(0, 300)}`)
    return unwrap(body)
  }
}

async function openProcessDesigner(page) {
  await page.goto(`${ORIGIN}/dev/function-units/${FU_ID}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(3000)
  await page.locator('.el-tabs__item').filter({ hasText: /process|流程/i }).first().click()
  await page.waitForSelector('.bpmn-canvas .djs-container', { timeout: 45000 })
  await page.waitForTimeout(3000)
}

/** Selects the node and returns its batch switch. */
async function batchSwitchOf(page, nodeId) {
  await page.locator(`[data-element-id="${nodeId}"]`).first().click({ force: true })
  await page.waitForSelector('.node-properties-panel', { timeout: 15000 })
  await page.waitForTimeout(1500)
  const item = page.locator('.node-properties-panel .el-form-item').filter({ hasText: SWITCH_LABEL })
  if ((await item.count()) !== 1) throw new Error(`batch switch not found on ${nodeId}`)
  return item
}

async function isOn(item) {
  return ((await item.locator('.el-switch').getAttribute('class')) || '').includes('is-checked')
}

function nodeXml(bpmnXml, nodeId) {
  return (bpmnXml || '').match(new RegExp(`<bpmn:userTask id="${nodeId}"[\\s\\S]*?</bpmn:userTask>`))?.[0] || ''
}

async function dwStage(browser) {
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 1100 } })).newPage()
  page.on('response', (res) => {
    if (res.request().method() !== 'GET' && /\/process(\?|$)/.test(res.url())) {
      console.log(`[dw] ${res.request().method()} ${res.url().replace(ORIGIN, '')} -> ${res.status()}`)
    }
  })
  await loginViaDwPassword(page)
  const api = apiFor(page, '')
  await openProcessDesigner(page)

  for (const nodeId of NODE_OFF) {
    const item = await batchSwitchOf(page, nodeId)
    if (await isOn(item)) await item.locator('.el-switch').click()
  }
  const item = await batchSwitchOf(page, NODE_ON)
  if (!(await isOn(item))) await item.locator('.el-switch').click()
  await item.scrollIntoViewIfNeeded()
  await page.waitForTimeout(800)
  await page.locator('.properties-panel-container').screenshot({ path: shot('developer-workstation', 'dw-allow-batch-switch') })

  // The process designer only marks the diagram dirty; the user saves explicitly.
  await page.locator('.process-designer .designer-toolbar .el-button--primary').last().click()
  await page.waitForTimeout(4000)

  const proc = await api('GET', `/api/v1/function-units/${FU_ID}/process`)
  if (!/name="allowBatch" value="true"/.test(nodeXml(proc.bpmnXml, NODE_ON))) {
    throw new Error(`saved BPMN has no allowBatch=true on ${NODE_ON}`)
  }
  for (const nodeId of NODE_OFF) {
    if (/name="allowBatch" value="true"/.test(nodeXml(proc.bpmnXml, nodeId))) {
      throw new Error(`saved BPMN still has allowBatch=true on ${nodeId}`)
    }
  }
  console.log(`[dw] saved BPMN: allowBatch=true on ${NODE_ON}; off on ${NODE_OFF.join(', ') || '-'}`)

  await page.reload({ waitUntil: 'domcontentloaded' })
  await openProcessDesigner(page)
  if (!(await isOn(await batchSwitchOf(page, NODE_ON)))) throw new Error('switch not on after reload')
  console.log('[dw] switch reads back as on after reload')

  const started = await api('POST', `/api/v1/function-units/${FU_ID}/deploy`, {
    autoEnable: true,
    changeLog: `To Do batch processing on ${NODE_ON}`,
  })
  let st = started
  for (let i = 0; i < 90 && !['SUCCESS', 'FAILED', 'ROLLED_BACK'].includes(st?.status); i += 1) {
    await page.waitForTimeout(2000)
    st = await api('GET', `/api/v1/function-units/deployments/${started.deploymentId}/status`)
  }
  if (st?.status !== 'SUCCESS') throw new Error(`deploy ${st?.status}: ${st?.message ?? ''}`)
  console.log(`[dw] deployed version ${st.versionNumber ?? ''}`)
}

async function todoRows(api, extra = {}) {
  const page = await api('POST', '/tasks/todo/query', { page: 0, size: 200, ...extra })
  return page.content
}

async function portalStage(browser) {
  const page = await (await browser.newContext({ viewport: { width: 1600, height: 1000 } })).newPage()
  const me = await loginViaPortalPassword(page)
  const api = apiFor(page, '/api/portal')

  const startedIds = []
  const stamp = Date.now()
  for (let i = 0; i < START_COUNT; i += 1) {
    const r = await api('POST', `/processes/${encodeURIComponent(PROCESS_KEY)}/start`, {
      processDefinitionKey: PROCESS_KEY,
      formData: {
        title: `Batch e2e ${stamp}-${i + 1}`,
        department: 'IT',
        primary_vendor: `Vendor ${i + 1}`,
        total_amount: 1000 * (i + 1),
        __subTables__: {
          'dw:budget_lines': [
            { id: `bl-${stamp}-${i}`, line_item: `Line ${i + 1}`, amount: 1000 * (i + 1), owner_user_id: me.userId },
          ],
          'dw:extra_vendors': [],
        },
      },
      priority: 'NORMAL',
      remark: `batch e2e ${stamp}-${i}`,
    })
    startedIds.push(r.processInstanceId || r.id)
  }
  console.log(`[portal] started ${startedIds.length}: ${startedIds.join(', ')}`)
  await page.waitForTimeout(1500)

  // Walk each request to NODE_ON the normal single-task way (action ids come from the task
  // detail, exactly as the To Do page would). Older leftovers that cannot move are reported.
  for (let round = 0; round < 4; round += 1) {
    const steps = (await todoRows(api)).filter((t) => ADVANCE_NODES.includes(t.taskDefinitionKey))
    if (steps.length === 0) break
    let moved = 0
    for (const t of steps) {
      try {
        const detail = await api('GET', `/tasks/${t.taskId}`)
        const approve = (detail.actions || []).find((a) => a.actionType === 'APPROVE')
        await api('POST', `/tasks/${t.taskId}/complete`, { taskId: t.taskId, action: 'APPROVE', actionId: approve?.actionId })
        moved += 1
      } catch (err) {
        console.log(`[portal] could not complete ${t.taskDefinitionKey} ${t.taskId}: ${err.message.slice(0, 160)}`)
      }
    }
    console.log(`[portal] round ${round + 1}: completed ${moved}/${steps.length} step task(s)`)
    if (moved === 0) break
    // The To Do Mine scan is cached ~15s; wait it out so the next round sees the new tasks.
    await page.waitForTimeout(16000)
  }
  await page.waitForTimeout(16000)

  const groups = await api('GET', '/tasks/batch/groups')
  console.log('[portal] groups', JSON.stringify(groups.map((g) => ({
    group: g.groupKey, version: g.version, node: g.taskDefinitionKey, count: g.count, actions: g.actions.map((a) => a.actionName),
  }))))
  const group = groups.find((g) => g.taskDefinitionKey === NODE_ON)
  if (!group) throw new Error(`no batch group for ${NODE_ON}`)
  const sameNodeAll = (await todoRows(api)).filter((t) => t.taskDefinitionKey === NODE_ON)
  console.log(`[portal] To Do rows at ${NODE_ON}: ${sameNodeAll.length} (all versions); batch group count: ${group.count}`)

  await page.goto(`${ORIGIN}/portal/tasks`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('[data-test="todo-tabs"]', { timeout: 30000 })
  await page.waitForTimeout(2500)
  await page.screenshot({ path: shot('user-portal', 'todo-tab-all') })

  await page.locator('.el-tabs__item').filter({ hasText: /Batch Actions|可批量操作/ }).click()
  await page.waitForSelector('[data-test="batch-group-row"]', { timeout: 30000 })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: shot('user-portal', 'todo-batch-groups') })

  await page.locator('[data-test="batch-group-row"]').filter({ hasText: group.nodeName })
    .locator('[data-test="batch-group-open"]').first().click()
  await page.waitForSelector('[data-test="batch-group-head"]', { timeout: 30000 })
  await page.waitForTimeout(2500)

  const listed = await todoRows(api, {
    batchGroupKey: group.groupKey,
    batchTaskDefinitionKey: group.taskDefinitionKey,
  })
  const rows = page.locator('.list-data-grid .el-table__body-wrapper tbody tr')
  const shown = await rows.count()
  console.log(`[portal] drilled-in grid rows: ${shown}; API group rows: ${listed.length}`)
  if (shown < SELECT_COUNT) throw new Error(`group list shows ${shown} rows, need ${SELECT_COUNT}`)
  const pickedRequestIds = []
  for (let i = 0; i < SELECT_COUNT; i += 1) {
    const r = rows.nth(i)
    pickedRequestIds.push((await r.locator('td').nth(1).innerText()).trim())
    await r.locator('.el-checkbox').first().click()
  }
  await page.waitForSelector('[data-test="batch-complete-bar"]', { timeout: 10000 })
  await page.waitForTimeout(600)
  await page.screenshot({ path: shot('user-portal', 'todo-batch-selected') })

  const picked = listed.filter((t) => pickedRequestIds.includes(String(t.requestId || '-')))
  if (picked.length !== SELECT_COUNT) {
    throw new Error(`could not map picked rows ${pickedRequestIds.join(',')} to tasks`)
  }
  const untouched = listed.filter((t) => !picked.includes(t))

  const batchResponse = page.waitForResponse((res) => res.url().includes('/tasks/batch/complete'))
  await page.locator('[data-test="batch-complete-btn"]').nth(BATCH_ACTION).click()
  await page.waitForSelector('.el-message-box', { timeout: 10000 })
  await page.waitForTimeout(500)
  await page.screenshot({ path: shot('user-portal', 'todo-batch-confirm') })
  const box = page.locator('.el-message-box')
  if ((await box.locator('textarea').count()) > 0) {
    await box.locator('textarea').fill(process.env.BATCH_COMMENT || 'batch e2e shared comment')
  }
  await box.locator('.el-button--primary').click()
  const result = unwrap(await (await batchResponse).json())
  console.log('[portal] batch/complete ->', JSON.stringify(result))
  await page.waitForTimeout(3000)
  await page.screenshot({ path: shot('user-portal', 'todo-batch-after') })

  const out = {
    picked: picked.map((t) => ({ requestId: t.requestId, taskId: t.taskId, processInstanceId: t.processInstanceId })),
    untouched: untouched.map((t) => ({ requestId: t.requestId, taskId: t.taskId, processInstanceId: t.processInstanceId })),
  }
  console.log('[portal] picked', JSON.stringify(out.picked))
  console.log('[portal] untouched', JSON.stringify(out.untouched))
}

const browser = await chromium.launch({ headless: true })
try {
  if (STAGE === 'dw') await dwStage(browser)
  else await portalStage(browser)
  console.log(`PASS stage=${STAGE}`)
} catch (err) {
  console.error(`FAIL stage=${STAGE}:`, err.message)
  process.exitCode = 1
} finally {
  await browser.close()
}
