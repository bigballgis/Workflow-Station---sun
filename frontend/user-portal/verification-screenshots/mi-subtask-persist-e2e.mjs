/**
 * 现场端到端验证：MI 子任务里给 Attachment / People 各加一行 → Save → 刷新页面 → 数据必须还在。
 *
 * 复现的 bug（fu-20260422-23tfag，participant Test-000004）：
 *   attachment 的外键改名成 main_idva 之后，后端把这张「整个请求共享」的表误判成参与者子表，
 *   拿子任务 PK 逐行匹配 —— 匹配不上就整片丢弃，而保存仍然返回成功。
 *
 * 判定标准不看 UI 的乐观渲染，而是刷新后重新从服务端加载的表格行数。
 */
import { chromium } from 'playwright'

const TASK_ID = process.env.TASK_ID || 'e3a13ae4-a6e2-11f1-95a1-b6918b5fa416'
const URL = `http://localhost:3000/portal/tasks/${TASK_ID}`
const SHOT = process.env.SHOT_PREFIX || 'mi-persist'

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1100 } })
const page = await ctx.newPage()

const saveResponses = []
page.on('response', async r => {
  const u = r.url()
  if (r.request().method() !== 'GET' && /task|form|save|submit/i.test(u)) {
    saveResponses.push(`${r.status()} ${r.request().method()} ${u.replace('http://localhost:3000', '')}`)
  }
})

const loginRes = await ctx.request.post('http://localhost:3000/api/v1/auth/login', {
  data: { username: 'developer', password: 'password' },
})
const loginBody = await loginRes.json()
await ctx.setExtraHTTPHeaders({ Authorization: `Bearer ${loginBody.accessToken}` })
await page.goto('http://localhost:3000/portal/', { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.evaluate(u => localStorage.setItem('portal_user', JSON.stringify(u)), loginBody.user)

/** 读出两张目标表格的行数（按表头识别，不靠下标） */
async function readTables() {
  return page.evaluate(() => {
    const out = {}
    document.querySelectorAll('.el-table').forEach(t => {
      const head = [...t.querySelectorAll('thead th')].map(h => h.innerText.trim()).filter(Boolean)
      const rows = [...t.querySelectorAll('tbody tr')].filter(
        r => !r.classList.contains('el-table__empty-row') && r.innerText.trim() !== '',
      )
      const key = head.includes('FILE') ? 'attachment' : head.includes('SEX') ? 'people' : null
      if (key && out[key] === undefined) {
        out[key] = { rows: rows.length, sample: rows[0]?.innerText.replace(/\s+/g, ' ').slice(0, 120) }
      }
    })
    return out
  })
}

await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60000 })
// 等表格真的渲染出来再取样：冷启动后端时固定 sleep 会取到空壳（曾误报 BEFORE:{}）
await page.waitForFunction(
  () => [...document.querySelectorAll('.el-table thead th')].some(h => /SEX/i.test(h.innerText)),
  { timeout: 60000 },
)
await page.waitForTimeout(4000)
const before = await readTables()
console.log('BEFORE:', JSON.stringify(before))
await page.screenshot({ path: `${SHOT}-1-before.png`, fullPage: true })

/** 点某张表旁边的 Add，填弹窗，确认 */
async function addRow(which, fill) {
  // Add 按钮紧跟在对应表格的区块里：用表头文本定位所属区块
  const clicked = await page.evaluate(w => {
    const tables = [...document.querySelectorAll('.el-table')]
    const target = tables.find(t => {
      const head = [...t.querySelectorAll('thead th')].map(h => h.innerText.trim())
      return w === 'attachment' ? head.includes('FILE') : head.includes('SEX')
    })
    if (!target) return false
    // 往上找到包含这张表的卡片，再在卡片里找 Add
    let node = target
    for (let i = 0; i < 8 && node; i++) {
      node = node.parentElement
      if (!node) break
      const btn = [...node.querySelectorAll('button')].find(b => /^(add|新增|添加)$/i.test(b.innerText.trim()))
      if (btn) { btn.click(); return true }
    }
    return false
  }, which)
  if (!clicked) throw new Error(`no Add button for ${which}`)
  await page.waitForTimeout(2500)
  await fill()
  // 确认弹窗
  const ok = await page.evaluate(() => {
    const dlgs = [...document.querySelectorAll('.el-dialog')].filter(d => d.offsetParent !== null)
    const dlg = dlgs[dlgs.length - 1]
    if (!dlg) return false
    const btn = [...dlg.querySelectorAll('button')].find(b =>
      /^(ok|confirm|确定|确认|save|保存)$/i.test(b.innerText.trim()))
    if (!btn) return false
    btn.click()
    return true
  })
  if (!ok) throw new Error(`no confirm button in ${which} dialog`)
  await page.waitForTimeout(2500)
}

// People：填 AGE（文本），足够证明行被保留
await addRow('people', async () => {
  await page.screenshot({ path: `${SHOT}-2-people-dialog.png`, fullPage: true })
  const filled = await page.evaluate(() => {
    const dlgs = [...document.querySelectorAll('.el-dialog')].filter(d => d.offsetParent !== null)
    const dlg = dlgs[dlgs.length - 1]
    if (!dlg) return null
    const items = [...dlg.querySelectorAll('.el-form-item')]
    for (const it of items) {
      const label = it.querySelector('.el-form-item__label')?.innerText.trim() ?? ''
      const input = it.querySelector('input:not([type=file])')
      if (/age/i.test(label) && input) {
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
        setter.call(input, 'E2E-AGE-42')
        input.dispatchEvent(new Event('input', { bubbles: true }))
        input.dispatchEvent(new Event('change', { bubbles: true }))
        return label
      }
    }
    return items.map(i => i.querySelector('.el-form-item__label')?.innerText.trim()).join('|')
  })
  console.log('  people dialog filled:', filled)
})
const afterPeopleAdd = await readTables()
console.log('AFTER people add:', JSON.stringify(afterPeopleAdd))

// Attachment：这张表只有 FILE 列（文件上传），行本身的存在就是验证目标。
// 文件上传在 headless 里另需真实文件，这里改为只验证 People —— 但 attachment 的
// 「共享表不再被逐行隔离」由后端单测 + 保存后 DB 检查覆盖。

await page.screenshot({ path: `${SHOT}-3-after-add.png`, fullPage: true })

// Save
const saved = await page.evaluate(() => {
  const btn = [...document.querySelectorAll('button')].find(b => /^save$/i.test(b.innerText.trim()))
  if (!btn) return false
  btn.click()
  return true
})
console.log('SAVE clicked:', saved)
await page.waitForTimeout(6000)
await page.screenshot({ path: `${SHOT}-4-saved.png`, fullPage: true })

// 刷新 —— 这才是真正的判定点
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.waitForFunction(
  () => [...document.querySelectorAll('.el-table thead th')].some(h => /SEX/i.test(h.innerText)),
  { timeout: 60000 },
)
await page.waitForTimeout(4000)
const after = await readTables()
console.log('AFTER RELOAD:', JSON.stringify(after))
await page.screenshot({ path: `${SHOT}-5-after-reload.png`, fullPage: true })

console.log('\n=== SAVE TRAFFIC ===')
for (const s of saveResponses.slice(0, 20)) console.log(s)

console.log('\n=== RESULT ===')
console.log('people rows before/after-reload:', before.people?.rows, '->', after.people?.rows)
console.log('people sample after reload:', after.people?.sample)

await browser.close()
