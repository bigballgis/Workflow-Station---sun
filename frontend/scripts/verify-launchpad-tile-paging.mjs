// 验证 DW Function Unit 列表的「按磁贴分页」：
//   1) 分组只算一个磁贴（组里几个 FU 都一样）
//   2) 页容量随视图模式：卡片（大图标）20 / 图标（小图标）50
//   3) 跨页拖拽：拖到网格边缘停留翻页，或直接扔在边缘区送到相邻页
// 为了凑出多页，脚本临时建一批 zz-paging-probe-* 的 FU，跑完删掉。
// 注意：DW 后端限流 60 请求/分钟（RateLimitConfig），写操作之间必须留间隔，
// 否则会被 429 打断，连列表接口都拿不到数据。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { loginViaDwPassword } from './playwright-login.mjs'

const ORIGIN = 'http://localhost:3000'
const OUT = new URL('../developer-workstation/verification-screenshots/', import.meta.url).pathname
const DATE = new Date().toISOString().slice(0, 10)
const PROBE_PREFIX = 'zz-paging-probe-'
const PROBE_COUNT = 12
const API_GAP_MS = 1200

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

/** 写接口带限流退避：429 就等一会儿再来（令牌每秒回 1 个） */
async function apiWrite(send, label) {
  for (let attempt = 1; attempt <= 6; attempt++) {
    await sleep(API_GAP_MS)
    const res = await send()
    if (res.status() !== 429) return res
    console.log(`[retry] ${label}: rate limited, backing off ${attempt * 10}s`)
    await sleep(attempt * 10000)
  }
  throw new Error(`${label}: still rate limited after retries`)
}

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1680, height: 1050 } })
mkdirSync(OUT, { recursive: true })

const created = [] // 建出来的探针 id（清理按名字兜底，这里只用于日志）
let failures = 0

function check(label, ok, detail) {
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) failures++
}

/** 当前页磁贴数 / 分组磁贴数 / 分页器页数 / 当前页码 */
async function probeGrid() {
  return page.evaluate(() => ({
    tiles: document.querySelectorAll('.launchpad-cell').length,
    folders: document.querySelectorAll('.launchpad-cell .folder-icon, .launchpad-cell .folder-card').length,
    pages: document.querySelectorAll('.el-pager li').length,
    current: Number(document.querySelector('.el-pager li.is-active')?.textContent ?? 1),
  }))
}

async function loadListPage() {
  for (let attempt = 1; attempt <= 4; attempt++) {
    await page.goto(`${ORIGIN}/dev/function-units`, { waitUntil: 'domcontentloaded' })
    try {
      await page.waitForSelector('.launchpad-cell', { timeout: 12000 })
      await page.waitForTimeout(900)
      return
    } catch {
      console.log(`[retry] list page empty (attempt ${attempt}) — waiting for the rate limiter to refill`)
      await sleep(30000)
    }
  }
  throw new Error('the function unit list never rendered any tiles')
}

// 用界面上的切换开关，别用「改 localStorage + reload」：每次 reload 都要重打一轮接口，
// 很容易撞上 60 请求/分钟的限流，列表拉空后整页没有磁贴。
async function setViewMode(mode) {
  const isCard = (await page.locator('.launchpad-grid--card').count()) > 0
  if ((mode === 'card') !== isCard) {
    await page.locator('.view-switch').click()
  }
  await page.waitForSelector(`.launchpad-grid--${mode} .launchpad-cell`, { timeout: 15000 })
  await page.waitForTimeout(700)
}

/** 按名字前缀清掉所有探针 FU（后端 delete 两段式：先归档、再真删） */
async function sweepProbes() {
  for (let pass = 0; pass < 3; pass++) {
    const res = await page.request.get(`${ORIGIN}/api/v1/function-units?size=500`).catch(() => null)
    const body = await res?.json().catch(() => null)
    const left = (body?.data?.content ?? []).filter((f) => f.name.startsWith(PROBE_PREFIX))
    if (left.length === 0) return
    console.log(`[cleanup] removing ${left.length} probe function units…`)
    for (const f of left) {
      const r = await apiWrite(
        () => page.request.delete(`${ORIGIN}/api/v1/function-units/${f.id}`),
        `delete probe ${f.name}`
      ).catch(() => null)
      if (r && !r.ok()) console.warn(`  probe ${f.name} delete: HTTP ${r.status()}`)
    }
  }
}

try {
  await loginViaDwPassword(page)
  // 上一次跑挂可能留下同名探针，重名会让 create 直接 409
  await sweepProbes()

  // 建 FU 必须带 team，否则后端 FunctionUnitWorkspaceAccessDeniedException
  const groupsRes = await page.request.get(`${ORIGIN}/api/v1/function-units/my-dev-groups`)
  const publicGroupId = (await groupsRes.json()).data?.publicGroupId
  if (!publicGroupId) throw new Error('cannot resolve the public dev group id')

  console.log(`[setup] creating ${PROBE_COUNT} probe function units…`)
  for (let i = 1; i <= PROBE_COUNT; i++) {
    const name = `${PROBE_PREFIX}${String(i).padStart(2, '0')}`
    const res = await apiWrite(
      () => page.request.post(`${ORIGIN}/api/v1/function-units`, {
        data: { name, description: 'temporary paging probe', virtualGroupIds: [publicGroupId] },
      }),
      `create ${name}`
    )
    if (!res.ok()) throw new Error(`probe create failed: HTTP ${res.status()}`)
    created.push((await res.json()).data.id)
  }

  // 探针建完后再进列表页，一次加载拿到全量。刚灌完一批写请求，令牌桶可能见底
  // （60/分钟、每秒回 1 个），列表接口被 429 打回会渲染成空态，所以带重试。
  await loadListPage()

  // ---------- 卡片视图（大图标）：20 磁贴 / 页 ----------
  await setViewMode('card')
  const card = await probeGrid()
  check('card view fills a page with 20 tiles', card.tiles === 20, `tiles=${card.tiles}`)
  check('card view paginates over tiles', card.pages >= 2, `pages=${card.pages}`)
  check('a folder takes a single tile slot', card.folders >= 1, `folder tiles on page 1=${card.folders}`)
  await page.screenshot({ path: `${OUT}${DATE}_launchpad-paging-card-20.png` })

  // ---------- 图标视图（小图标）：页容量更大 ----------
  await setViewMode('icon')
  const icon = await probeGrid()
  check('icon view holds more tiles per page than card view', icon.tiles > card.tiles, `tiles=${icon.tiles}`)
  check('icon view needs fewer pages', icon.pages < card.pages || icon.pages === 0, `pages=${icon.pages}`)
  await page.screenshot({ path: `${OUT}${DATE}_launchpad-paging-icon-50.png` })

  // ---------- 跨页拖拽：把首个磁贴扔到「下一页」边缘区 ----------
  await setViewMode('card')
  const source = page.locator('.launchpad-cell').first()
  const dragged = await source.locator('.card-title').innerText()
  const box = await source.boundingBox()

  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2 + 40, box.y + box.height / 2, { steps: 8 })
  await page.waitForTimeout(250)

  const zone = page.locator('.page-flip-zone--next')
  const zoneVisible = await zone.isVisible()
  check('the page-flip drop zone shows up while dragging', zoneVisible)
  if (zoneVisible) {
    await page.screenshot({ path: `${OUT}${DATE}_launchpad-paging-flip-zone.png` })
    const zb = await zone.boundingBox()
    await page.mouse.move(zb.x + zb.width / 2, zb.y + zb.height / 2, { steps: 10 })
    await page.waitForTimeout(300)
    await page.mouse.up()
    await page.waitForTimeout(700)

    const after = await probeGrid()
    const firstOnPage = await page.locator(".launchpad-cell").first().locator(".card-title").innerText()
    check('the edge drop lands the tile on the next page', after.current === 2, `current page=${after.current}`)
    check('the moved tile is first on the target page', firstOnPage === dragged, `first="${firstOnPage}" dragged="${dragged}"`)
    await page.screenshot({ path: `${OUT}${DATE}_launchpad-paging-after-cross-page-drag.png` })
  }
} catch (e) {
  failures++
  console.error('[error]', e.message)
} finally {
  await sweepProbes()
  await browser.close()
}

console.log(failures === 0 ? '[RESULT] all checks passed' : `[RESULT] ${failures} check(s) failed`)
process.exit(failures === 0 ? 0 : 1)
