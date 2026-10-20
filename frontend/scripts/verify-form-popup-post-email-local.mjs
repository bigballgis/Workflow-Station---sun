#!/usr/bin/env node
/**
 * Local Docker: FORM_POPUP (Add Remark) submit → Action postEmail → workflow-engine ledger/logs.
 *
 * Prereqs: edge stack on http://localhost:3000, MI demo deployed (fu-20260422-23tfag),
 * Playwright browsers (`cd frontend && pnpm exec playwright install chromium`).
 *
 * Usage:
 *   node frontend/scripts/verify-form-popup-post-email-local.mjs
 *   TASK_ID=<uuid> node frontend/scripts/verify-form-popup-post-email-local.mjs
 *   SKIP_START=1 TASK_ID=<uuid>  # only popup submit on an existing assignment task
 */
import { execFileSync } from 'node:child_process'
import { mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import {
  PORTAL_API,
  PORTAL_ORIGIN,
  setupPortalSession,
} from '../user-portal/verification-screenshots/portal-verify-session.mjs'
import { listPortalTodoTasks, preferMiTasks } from './mi-regression-helpers.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = resolve(__dirname, '..', '..')
const FU_CODE = process.env.FU_CODE ?? 'fu-20260422-23tfag'
const ACTION_ID = process.env.ACTION_ID ?? '50384'
const POSTGRES_CONTAINER = process.env.POSTGRES_CONTAINER ?? 'platform-postgres-dev'
const PG_USER = process.env.POSTGRES_USER ?? 'platform_dev'
const PG_DB = process.env.POSTGRES_DB ?? 'workflow_platform_dev'
const OUT = join(__dirname, '..', 'user-portal', 'verification-screenshots')
const DATE = new Date().toISOString().slice(0, 10)

mkdirSync(OUT, { recursive: true })

function log(step, msg) {
  console.log(`[${step}] ${msg}`)
}

function ensurePostEmailRulesInDb() {
  const smtpHost = process.env.LOCAL_SMTP_HOST ?? 'host.docker.internal'
  const smtpPort = Number(process.env.LOCAL_SMTP_PORT ?? 1025)
  const connectionUid = process.env.LOCAL_SMTP_CONNECTION_UID ?? 'local-mailpit-50005'
  const sql = `
INSERT INTO dw_email_connections (connection_uid, function_unit_id, name, connection_type, host, port, username, from_email, use_tls, enabled, direction)
SELECT '${connectionUid}', 50005, 'Local Mailpit', 'SMTP', '${smtpHost}', ${smtpPort}, '', 'test@local.dev', false, true, 'OUTBOUND'
WHERE NOT EXISTS (SELECT 1 FROM dw_email_connections WHERE function_unit_id=50005 AND connection_uid='${connectionUid}');

INSERT INTO sys_email_connections (id, function_unit_id, name, connection_type, host, port, username, from_email, use_tls, enabled, direction)
SELECT '${connectionUid}', fu.id, 'Local Mailpit', 'SMTP', '${smtpHost}', ${smtpPort}, '', 'test@local.dev', false, true, 'OUTBOUND'
FROM sys_function_units fu
WHERE fu.code = '${FU_CODE}'
ON CONFLICT (id) DO UPDATE SET
  host = EXCLUDED.host,
  port = EXCLUDED.port,
  enabled = true,
  synced_at = CURRENT_TIMESTAMP;

UPDATE dw_action_definitions
SET config_json = COALESCE(config_json, '{}'::jsonb)
  || jsonb_build_object('postEmailRules', jsonb_build_array(jsonb_build_object(
      'id', 'local-verify-rule-1',
      'enabled', true,
      'name', 'After remark send',
      'connectionId', '${connectionUid}',
      'emailTo', 'developer@local.test',
      'emailTemplateId', '2',
      'condition', jsonb_build_object('logic','and','conditions', jsonb_build_array())
    )))
WHERE id = ${Number(ACTION_ID)};

UPDATE admin_system_configs SET config_value = '${smtpHost}' WHERE config_key = 'smtp.host';
UPDATE admin_system_configs SET config_value = '${smtpPort}'::text WHERE config_key = 'smtp.port';
UPDATE admin_system_configs SET config_value = 'false' WHERE config_key = 'smtp.useTls';
`
  execFileSync(
    'docker',
    ['exec', '-i', POSTGRES_CONTAINER, 'psql', '-U', PG_USER, '-d', PG_DB, '-v', 'ON_ERROR_STOP=1'],
    { input: sql, stdio: ['pipe', 'inherit', 'inherit'] },
  )
}

function printDockerHints() {
  log('logs', '--- user-portal (last 10m) ---')
  try {
    const up = execFileSync(
      'docker',
      ['logs', 'platform-user-portal-dev', '--since', '10m'],
      { encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 },
    )
    for (const line of up.split('\n')) {
      if (/ACTION-EMAIL|ActionFormPopupSubmit|form-popup-submit/i.test(line)) console.log(line)
    }
  } catch {
    log('logs', '(user-portal container log unavailable)')
  }
  log('logs', '--- workflow-engine (last 10m) ---')
  try {
    const we = execFileSync(
      'docker',
      ['logs', 'platform-workflow-engine-dev', '--since', '10m'],
      { encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 },
    )
    for (const line of we.split('\n')) {
      if (/\[ACTION-EMAIL\]/i.test(line)) console.log(line)
    }
  } catch {
    log('logs', '(workflow-engine container log unavailable)')
  }
  log('ledger', execFileSync(
    'docker',
    [
      'exec',
      POSTGRES_CONTAINER,
      'psql',
      '-U',
      PG_USER,
      '-d',
      PG_DB,
      '-c',
      'SELECT event_id, process_instance_id, action_id, status, left(coalesce(last_error,\'\'),120) AS err FROM we_action_email_deliveries ORDER BY created_at DESC LIMIT 5;',
    ],
    { encoding: 'utf8' },
  ))
}

/** @param {import('playwright').Page} page */
async function pageHasAddRemarkAction(page) {
  const btn = page.getByRole('button', { name: /Add Remark|Add Meeting Remark/i })
  return (await btn.count()) > 0
}

/**
 * Start MI demo via portal API (same as seed-owner-demo-matrix). UI-only start often
 * misses the POST /start call; developer session lands on assignment after auto-submit.
 *
 * @param {import('playwright').BrowserContext} ctx
 */
async function startMiAssignmentTaskViaApi(ctx) {
  if (process.env.SKIP_START === '1') return null
  const res = await ctx.request.post(`${PORTAL_API}/processes/${encodeURIComponent(FU_CODE)}/start`, {
    data: {
      processDefinitionKey: FU_CODE,
      formData: { tiuer: `post-email-verify-${Date.now()}` },
      priority: 'NORMAL',
    },
  })
  const body = await res.json().catch(() => ({}))
  if (!res.ok()) {
    throw new Error(`POST /processes/start failed HTTP ${res.status()}: ${JSON.stringify(body).slice(0, 500)}`)
  }
  const instanceId = body.data?.id ?? body.data?.processInstanceId
  if (!instanceId) {
    throw new Error(`start response missing instance id: ${JSON.stringify(body).slice(0, 500)}`)
  }
  log('start', `instance=${instanceId} node=${body.data?.currentNode ?? '?'}`)
  return instanceId
}

/** @param {import('playwright').Page} page @param {import('playwright').BrowserContext} ctx */
async function openTaskWithAddRemark(page, ctx) {
  const pinned = process.env.TASK_ID?.trim()
  if (pinned) {
    await page.goto(`${PORTAL_ORIGIN}/portal/tasks/${pinned}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(8000)
    if (await pageHasAddRemarkAction(page)) return pinned
    throw new Error(`TASK_ID=${pinned} has no Add Remark button (wrong node or action not bound)`)
  }

  const instanceId = await startMiAssignmentTaskViaApi(ctx)

  const deadline = Date.now() + Number(process.env.WAIT_TODO_MS ?? 60000)
  while (Date.now() < deadline) {
    const rows = preferMiTasks(await listPortalTodoTasks(page))
    const match = rows.find((row) =>
      (!instanceId || row.processInstanceId === instanceId)
      && /assignment/i.test(String(row.taskName ?? row.currentStepName ?? '')),
    ) ?? rows.find((row) => !instanceId || row.processInstanceId === instanceId)
    if (match) {
      const taskId = match.taskId ?? match.id
      await page.goto(`${PORTAL_ORIGIN}/portal/tasks/${taskId}`, { waitUntil: 'domcontentloaded' })
      await page.waitForTimeout(8000)
      if (await pageHasAddRemarkAction(page)) return taskId
    }
    log('wait', `assignment todo not ready; retry in 5s (${rows.length} todos)`)
    await page.waitForTimeout(5000)
  }
  throw new Error(
    'Timed out waiting for assignment todo with Add Remark. Set TASK_ID or check portal start permissions.',
  )
}

/** @param {import('playwright').Page} page */
async function runFormPopupSubmit(page, taskId) {
  const popupBtn = page.getByRole('button', { name: /Add Remark|Add Meeting Remark/i }).first()
  await popupBtn.scrollIntoViewIfNeeded()
  await popupBtn.click()
  const dlg = page.getByRole('dialog').last()
  await dlg.waitFor({ state: 'visible', timeout: 20000 })

  const typeField = dlg.locator('.el-form-item').filter({ hasText: /remark_type|Remark Type|类型/i })
    .locator('input, textarea').first()
  const contentField = dlg.locator('.el-form-item').filter({ hasText: /remark_content|Remark Content|内容/i })
    .locator('input, textarea').first()
  if (await typeField.count()) await typeField.fill('local-e2e')
  if (await contentField.count()) await contentField.fill(`post-email verify ${new Date().toISOString()}`)

  await page.screenshot({
    path: join(OUT, `${DATE}_form-popup-post-email-before-submit.png`),
    fullPage: true,
  })

  const submitResp = page.waitForResponse(
    (r) => r.request().method() === 'POST' && /form-popup-submit/.test(r.url()),
    { timeout: 60000 },
  )
  await dlg.getByRole('button', { name: /^(Submit|提交|確定|OK)$/i }).click()
  const res = await submitResp
  const body = await res.json().catch(() => ({}))
  log('api', `form-popup-submit HTTP ${res.status()} task=${taskId} action=${ACTION_ID}`)
  if (!res.ok()) {
    throw new Error(`form-popup-submit failed: ${JSON.stringify(body).slice(0, 500)}`)
  }
  await page.waitForTimeout(1500)
  await page.screenshot({
    path: join(OUT, `${DATE}_form-popup-post-email-after-submit.png`),
    fullPage: true,
  })
}

async function main() {
  log('db', 'ensure postEmailRules on action + SMTP row for FU 50005')
  ensurePostEmailRulesInDb()

  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const page = await ctx.newPage()
  try {
    await setupPortalSession(page, ctx)
    const taskId = await openTaskWithAddRemark(page, ctx)
    log('task', taskId)
    await runFormPopupSubmit(page, taskId)
    log('wait', 'scheduler (~5s) + engine dispatch')
    await page.waitForTimeout(12000)
  } finally {
    await browser.close()
  }

  printDockerHints()
  log('done', 'Check [ACTION-EMAIL] sent/FAILED/skip above and we_action_email_deliveries')
}

main().catch((err) => {
  console.error(err)
  printDockerHints()
  process.exit(1)
})
