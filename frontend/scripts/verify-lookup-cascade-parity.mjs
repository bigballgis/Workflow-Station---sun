/**
 * Lookup cascade UI smoke: portal password login → open a To Do → screenshot.
 * Usage: node scripts/verify-lookup-cascade-parity.mjs [taskId]
 */
import { mkdirSync } from 'fs'
import { join, dirname } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'

const __dirname = dirname(fileURLToPath(import.meta.url))
const origin = (process.env.PORTAL_ORIGIN || 'http://localhost:3000').replace(/\/$/, '')
const shotDir = join(__dirname, '../user-portal/verification-screenshots')
mkdirSync(shotDir, { recursive: true })
const date = new Date().toISOString().slice(0, 10)

async function portalLogin(page) {
  const user = process.env.LOGIN_USER || 'developer'
  const pass = process.env.LOGIN_PASS || 'password'
  await page.goto(`${origin}/portal/`, { waitUntil: 'domcontentloaded' }).catch(() => {})
  let res = await page.request.post(`${origin}/api/portal/auth/login`, {
    data: { username: user, password: pass },
  })
  let body = await res.json().catch(() => ({}))
  if (body.loginErrorCode === 'WORKSPACE_CONTEXT_REQUIRED' && body.workspaceContexts?.[0]) {
    const c = body.workspaceContexts[0]
    res = await page.request.post(`${origin}/api/portal/auth/login`, {
      data: {
        username: user,
        password: pass,
        workspaceBusinessUnitId: c.businessUnitId,
        workspaceRoleId: c.roleId,
      },
    })
    body = await res.json().catch(() => ({}))
  }
  const u = body.user || body.data?.user
  if (!u?.userId) throw new Error(`login failed: ${body.message || res.status()}`)
  await page.evaluate((userInfo) => {
    localStorage.setItem('ws_up_user', JSON.stringify(userInfo))
    localStorage.setItem('ws_up_user_id', String(userInfo.userId))
  }, u)
  console.log('[login]', u.username, u.userId)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1440, height: 1100 } })).newPage()

try {
  await portalLogin(page)

  let taskId = process.argv[2]
  if (!taskId) {
    const q = await page.request.post(`${origin}/api/portal/tasks/query`, {
      data: { page: 0, size: 40 },
    })
    const todos = (await q.json()).data?.content || []
    const hit =
      todos.find((t) =>
        /lookup|stage|status|cascade|assignment/i.test(`${t.taskName || ''} ${t.processDefinitionName || ''}`),
      ) || todos[0]
    taskId = hit?.taskId
  }
  if (!taskId) throw new Error('no todo task available for lookup screenshot')

  await page.goto(`${origin}/portal/tasks/${taskId}`, {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  })
  await page.waitForTimeout(4000)

  const lookupCount = await page.locator('.lookup-form-item, .lookup-field, .fc-lookup-wrap').count()
  const out = join(shotDir, `${date}_lookup-cascade-task-${taskId}.png`)
  await page.screenshot({ path: out, fullPage: true })
  console.log('[ok]', out, 'lookupWidgets=', lookupCount, 'taskId=', taskId)
  if (lookupCount === 0) {
    console.warn('[warn] no lookup widgets on page — screenshot still saved for triage')
  }
} finally {
  await browser.close()
}
