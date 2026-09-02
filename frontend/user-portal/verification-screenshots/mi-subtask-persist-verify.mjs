/**
 * 现场验证：MI 子任务里给 Attachment / People 加行，保存后刷新，数据必须还在。
 *
 * 复现的 bug：attachment 的外键被改名成 main_idva 后，后端把这张「整个请求共享」的表
 * 误判成参与者子表，逐行拿子任务 PK 去匹配 —— 匹配不上就整片丢弃，保存还返回成功。
 *
 * 用真实 UI + 真实保存 + 真实刷新，并直接查库确认落地（不看 UI 的乐观渲染）。
 */
import { chromium } from 'playwright'

const TASK_ID = process.env.TASK_ID || 'e3a13ae4-a6e2-11f1-95a1-b6918b5fa416'
const URL = `http://localhost:3000/portal/tasks/${TASK_ID}`
const SHOT = process.env.SHOT_PREFIX || 'mi-subtask-persist'

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 } })
const page = await ctx.newPage()

const errors = []
page.on('console', m => {
  if (m.type() === 'error') errors.push(`[error] ${m.text()}`)
})
page.on('pageerror', e => errors.push(`[pageerror] ${e.message}`))

// 把失败的请求 URL 记下来：401 到底打在哪个 base 上
const failed = []
page.on('response', r => {
  if (r.status() >= 400) failed.push(`${r.status()} ${r.request().method()} ${r.url()}`)
})
page.on('requestfailed', r => failed.push(`FAILED ${r.method()} ${r.url()} ${r.failure()?.errorText}`))

// 保存请求的 payload / 响应，用来区分「前端没提交」和「后端丢弃」
const saveTraffic = []
page.on('request', r => {
  if (r.method() === 'POST' && /save|submit|form-data/i.test(r.url())) {
    saveTraffic.push({ kind: 'req', url: r.url(), body: r.postData()?.slice(0, 4000) })
  }
})
page.on('response', async r => {
  if (r.request().method() === 'POST' && /save|submit|form-data/i.test(r.url())) {
    saveTraffic.push({ kind: 'res', url: r.url(), status: r.status() })
  }
})

// 令牌是 httpOnly cookie（dw_access_token）。用 context.request 登录 —— 它和页面共用同一个
// cookie jar，比在页面里 fetch 可靠（页面里的 fetch 会被应用自身的拦截器/CSP 影响）。
const loginRes = await ctx.request.post('http://localhost:3000/api/v1/auth/login', {
  data: { username: 'developer', password: 'password' },
})
const loginBody = await loginRes.json().catch(() => ({}))
console.log('LOGIN:', loginRes.status(), 'user:', loginBody?.user?.username)
const ACCESS = loginBody?.accessToken
if (!ACCESS) throw new Error('no accessToken from login')

// /api/portal/** 只认 Authorization 头（cookie 会 401）。给所有 portal 请求补上。
await ctx.setExtraHTTPHeaders({ Authorization: `Bearer ${ACCESS}` })

if (loginBody?.user) {
  await page.goto('http://localhost:3000/portal/', { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.evaluate(u => localStorage.setItem('portal_user', JSON.stringify(u)), loginBody.user)
}

// 切到一个真实工作台（BU + Role），否则 /portal/tasks 会被守卫按「自助模式」重定向
const meRes = await ctx.request.get('http://localhost:3000/api/v1/auth/me')
const me = await meRes.json().catch(() => ({}))
const ctxs = me?.workspaceContexts ?? me?.data?.workspaceContexts ?? []
if (ctxs.length) {
  const c = ctxs[0]
  const sw = await ctx.request.post('http://localhost:3000/api/v1/auth/switch-workspace', {
    data: { businessUnitId: c.businessUnitId, roleId: c.roleId },
  })
  console.log('WORKSPACE:', sw.status(), c.businessUnitName, '/', c.roleName)
} else {
  console.log('WORKSPACE: none in /me — 继续（SYS_ADMIN 可能不需要）')
}

await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.waitForTimeout(9000)
await page.screenshot({ path: `${SHOT}-1-loaded.png`, fullPage: true })

// 列出页面上的子表区块，便于定位 Attachment / People
const tables = await page.evaluate(() => {
  const out = []
  document.querySelectorAll('.el-table').forEach((t, i) => {
    const head = [...t.querySelectorAll('thead th')].map(h => h.innerText.trim()).filter(Boolean)
    const rows = t.querySelectorAll('tbody tr:not(.el-table__empty-row)').length
    out.push({ i, head, rows })
  })
  return out
})
console.log('TABLES:', JSON.stringify(tables, null, 2))

const addButtons = await page.evaluate(() =>
  [...document.querySelectorAll('button')]
    .map((b, i) => ({ i, text: b.innerText.trim() }))
    .filter(b => /add|新增|添加/i.test(b.text)),
)
console.log('ADD BUTTONS:', JSON.stringify(addButtons))

console.log('\n=== FAILED REQUESTS (%d) ===', failed.length)
for (const f of failed.slice(0, 30)) console.log(f)

console.log('\n=== CONSOLE ERRORS (%d) ===', errors.length)
for (const e of errors.slice(0, 30)) console.log(e)

await browser.close()
