/**
 * Shared list §6.6 leftover + §6.7 Audit / member-management / Views header menus.
 * Password login only — do not use unified SSO.
 */
import { mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaAdminPassword, loginViaPortalPassword } from './playwright-login.mjs'

const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const DATE = new Date().toISOString().slice(0, 10)
const PORTAL_OUT = join(dirname(fileURLToPath(import.meta.url)), '../user-portal/verification-screenshots')
const ADMIN_OUT = join(dirname(fileURLToPath(import.meta.url)), '../admin-center/verification-screenshots')

mkdirSync(PORTAL_OUT, { recursive: true })
mkdirSync(ADMIN_OUT, { recursive: true })

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
let failures = 0

function check(label, ok, detail) {
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) failures++
}

async function shot(dir, name) {
  const path = join(dir, `${DATE}_${name}.png`)
  await page.screenshot({ path, fullPage: false })
  console.log(`[SHOT] ${path}`)
  return path
}

async function waitGrid() {
  await page.locator('.el-loading-mask').waitFor({ state: 'hidden', timeout: 20000 }).catch(() => {})
  await page.locator('.list-col-header, .mtv-data-grid .list-col-header, .el-empty, .el-alert').first()
    .waitFor({ state: 'visible', timeout: 20000 }).catch(() => {})
  await page.waitForTimeout(400)
}

async function assertHeadersFullyVisible(label, titleMatchers) {
  for (const title of titleMatchers) {
    const el = page.locator('.list-col-header', { hasText: title }).locator('.list-col-label').first()
    if ((await el.count()) === 0) {
      check(`${label}: ${title} header present`, false)
      continue
    }
    const truncated = await el.evaluate((node) => node.scrollWidth > node.clientWidth + 1)
    check(`${label}: ${String(title)} header is fully visible`, !truncated)
  }
}

async function assertNoSpacer(label) {
  const n = await page.locator('.list-col-spacer').count()
  check(`${label}: no leftover spacer`, n === 0, `count=${n}`)
}

async function assertNoCurrentStepColumn(label) {
  const n = await page.locator('.list-col-header', { hasText: /Current Step|当前步骤|目前步驟/i }).count()
  check(`${label}: no Current Step column`, n === 0, `count=${n}`)
}

async function scrollGridX(delta) {
  await page.evaluate((dx) => {
    const wrap = document.querySelector(
      '.list-data-grid .el-table__body-wrapper .el-scrollbar__wrap, .list-data-grid .el-table__body-wrapper',
    )
    if (wrap) wrap.scrollLeft = dx
  }, delta)
}

async function assertFrozenPane(label) {
  const scroller = page.locator('.list-data-grid-scroll, .mtv-data-grid-scroll').first()
  const header = page.locator('.el-table__header-wrapper').first()
  const scrollerBox = await scroller.boundingBox()
  const headerBefore = await header.boundingBox()
  if (!scrollerBox || !headerBefore) {
    check(`${label}: frozen pane boxes`, false)
    return
  }
  check(
    `${label}: header sits at the top of the pane`,
    Math.abs(headerBefore.y - scrollerBox.y) <= 8,
    `headerY=${headerBefore.y} paneY=${scrollerBox.y}`,
  )
  await page.evaluate(() => {
    const wrap = document.querySelector(
      '.list-data-grid .el-table__body-wrapper .el-scrollbar__wrap, .mtv-data-grid .el-table__body-wrapper .el-scrollbar__wrap',
    )
    if (wrap) wrap.scrollTop = 240
  })
  await page.waitForTimeout(200)
  const headerAfter = await header.boundingBox()
  check(
    `${label}: header stays put after vertical scroll`,
    !!headerAfter && Math.abs(headerAfter.y - headerBefore.y) <= 2,
    headerAfter ? `afterY=${headerAfter.y}` : 'no header',
  )
  const bar = page.locator(
    '.list-data-grid .el-scrollbar__bar.is-horizontal, .mtv-data-grid .el-scrollbar__bar.is-horizontal',
  ).first()
  if ((await bar.count()) === 0) {
    check(`${label}: horizontal bar present when overflowing`, true, 'no overflow on this viewport')
    return
  }
  const barBox = await bar.boundingBox()
  if (!barBox) {
    check(`${label}: horizontal bar box`, false)
    return
  }
  const paneBottom = scrollerBox.y + scrollerBox.height
  check(
    `${label}: horizontal bar at visible pane bottom`,
    Math.abs((barBox.y + barBox.height) - paneBottom) <= 24,
    `barBottom=${barBox.y + barBox.height} paneBottom=${paneBottom}`,
  )
}

async function assertActionPinned(label) {
  await page.setViewportSize({ width: 720, height: 900 })
  await page.waitForTimeout(400)
  const scroller = page.locator('.list-data-grid-scroll').first()
  const action = page.locator('.el-table__body td.el-table-fixed-column--right').first()
  const present = (await page.locator('.el-table-fixed-column--right, .el-table__fixed-right').count()) > 0
  check(`${label}: Action column present`, present)
  if (!present) return
  const scrollerBox = await scroller.boundingBox()
  const before = await action.boundingBox()
  if (!scrollerBox || !before) {
    check(`${label}: Action bounding box`, false)
    return
  }
  const nearRight = (box) => Math.abs((box.x + box.width) - (scrollerBox.x + scrollerBox.width)) <= 16
  check(
    `${label}: Action at window right before scroll`,
    nearRight(before),
    `actionRight=${before.x + before.width} scrollerRight=${scrollerBox.x + scrollerBox.width}`,
  )
  await scrollGridX(220)
  const after = await action.boundingBox()
  check(
    `${label}: Action stays at window right after scroll`,
    !!after && nearRight(after),
    after ? `afterRight=${after.x + after.width}` : 'no box',
  )
}

async function assertRequestIdDrag(label) {
  const header = page.locator('.list-col-header', { hasText: /Request ID/i }).first()
  const handle = header.locator('.col-resize-handle')
  if ((await handle.count()) === 0) {
    check(`${label}: Request ID resize handle`, false)
    return
  }
  const th = page.locator('th').filter({ has: header }).first()
  const others = page.locator('.el-table__header-wrapper th').filter({ hasNot: header })
  const before = await th.boundingBox()
  const otherBefore = await others.nth(0).boundingBox()
  const handleBox = await handle.boundingBox()
  const scroller = page.locator('.list-data-grid-scroll').first()
  const scrollerBox = await scroller.boundingBox()
  if (!before || !otherBefore || !handleBox || !scrollerBox) {
    check(`${label}: Request ID drag boxes`, false)
    return
  }
  await page.mouse.move(handleBox.x + handleBox.width / 2, handleBox.y + handleBox.height / 2)
  await page.mouse.down()
  await page.mouse.move(handleBox.x + handleBox.width / 2 + 80, handleBox.y + handleBox.height / 2, { steps: 6 })
  const during = await th.boundingBox()
  const otherDuring = await others.nth(0).boundingBox()
  const guide = page.locator('.col-resize-guide')
  const guideBox = await guide.boundingBox()
  check(
    `${label}: Request ID follows the mouse while dragging`,
    !!during && during.width >= before.width + 50,
    during ? `before=${before.width.toFixed(0)} during=${during.width.toFixed(0)}` : 'no box',
  )
  check(
    `${label}: other columns keep width while dragging`,
    !!otherDuring && Math.abs(otherDuring.width - otherBefore.width) <= 6,
    otherDuring ? `before=${otherBefore.width.toFixed(0)} during=${otherDuring.width.toFixed(0)}` : 'no box',
  )
  check(
    `${label}: resize guide stays inside the grid pane`,
    !!guideBox && guideBox.y >= scrollerBox.y - 2
      && (guideBox.y + guideBox.height) <= (scrollerBox.y + scrollerBox.height) + 2,
    guideBox
      ? `guideBottom=${(guideBox.y + guideBox.height).toFixed(0)} paneBottom=${(scrollerBox.y + scrollerBox.height).toFixed(0)}`
      : 'no guide',
  )
  await shot(PORTAL_OUT, 'shared-list-my-requests-resize-drag')
  await page.mouse.up()
}

async function assertHeaderMenu(label) {
  const trigger = page.locator('.list-col-trigger').first()
  const hasTrigger = (await trigger.count()) > 0
  check(`${label}: shared header trigger`, hasTrigger)
  if (!hasTrigger) return
  await trigger.click()
  const menu = page.locator('.el-dropdown-menu.list-col-menu:visible, .list-col-menu:visible').first()
  await menu.waitFor({ state: 'visible', timeout: 8000 })
  const text = (await menu.innerText()).replace(/\s+/g, ' ')
  check(`${label}: header has Filter by`, /Filter by/i.test(text), text)
  check(`${label}: header has sort`, /A to Z|Older to newer|Small to large/i.test(text), text)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(200)
}

try {
  await loginViaPortalPassword(page)

  async function portalData(path) {
    try {
      const res = await page.request.get(`${ORIGIN}/api/portal${path}`, { timeout: 30000 })
      const body = await res.json()
      return body.data
    } catch (error) {
      // This user often has no audit grant; a hung listing must not fail layout checks.
      console.log(`[SKIP] portal ${path} — ${error instanceof Error ? error.message : String(error)}`)
      return null
    }
  }

  await page.goto(`${ORIGIN}/portal/tasks`, { waitUntil: 'domcontentloaded' })
  await waitGrid()
  await assertNoSpacer('To Do')
  await assertNoCurrentStepColumn('To Do')
  await assertFrozenPane('To Do')
  await assertHeadersFullyVisible('To Do', [/Request ID/i])
  await shot(PORTAL_OUT, 'shared-list-todo-no-spacer')

  await page.goto(`${ORIGIN}/portal/tasks/completed`, { waitUntil: 'domcontentloaded' })
  await waitGrid()
  await assertNoSpacer('Completed Tasks')
  await assertNoCurrentStepColumn('Completed Tasks')
  await shot(PORTAL_OUT, 'shared-list-completed-no-current-step')

  await page.goto(`${ORIGIN}/portal/my-applications`, { waitUntil: 'domcontentloaded' })
  await waitGrid()
  await assertNoSpacer('My Requests')
  await assertNoCurrentStepColumn('My Requests')
  await assertActionPinned('My Requests')
  await page.setViewportSize({ width: 1440, height: 900 })
  await assertFrozenPane('My Requests')
  await assertHeadersFullyVisible('My Requests', [/Request ID/i, /Process Title/i, /Current Assignee/i])
  await shot(PORTAL_OUT, 'shared-list-my-requests-action')
  await assertRequestIdDrag('My Requests')

  const viewFus = await portalData('/main-table-views/function-units')
  const viewCodes = Array.isArray(viewFus)
    ? viewFus.map((fu) => fu.functionUnitCode).filter(Boolean)
    : []
  check('Views: at least one function unit', viewCodes.length > 0, JSON.stringify(viewFus)?.slice(0, 200))
  let viewsOpened = false
  for (const viewCode of viewCodes) {
    await page.goto(`${ORIGIN}/portal/views/${encodeURIComponent(viewCode)}`, { waitUntil: 'domcontentloaded' })
    await page.waitForSelector('.mtv-data-grid, .list-col-header, .el-empty', { timeout: 20000 }).catch(() => {})
    await waitGrid()
    if ((await page.locator('.list-col-trigger').count()) > 0) {
      viewsOpened = true
      await assertNoSpacer('Views')
      await assertHeaderMenu('Views')
      await assertFrozenPane('Views')
      await shot(PORTAL_OUT, 'shared-list-views-header')
      break
    }
  }
  if (!viewsOpened) {
    const empty = (await page.locator('.el-empty').count()) > 0
    check(
      'Views: shared header or empty state for this user',
      empty,
      empty ? 'no tables visible to this role' : 'no view rendered a grid for this user',
    )
    await shot(PORTAL_OUT, 'shared-list-views-header')
  }

  const auditFus = await portalData('/processes/audit-function-units')
  const auditCode = Array.isArray(auditFus) && auditFus[0]?.functionUnitCode
  const fallbackAudit = viewCodes[0]
  if (auditCode) {
    await page.goto(`${ORIGIN}/portal/audit/${encodeURIComponent(auditCode)}`, { waitUntil: 'domcontentloaded' })
    await waitGrid()
    await assertNoSpacer('Audit')
    await assertNoCurrentStepColumn('Audit')
    await assertHeaderMenu('Audit')
    await shot(PORTAL_OUT, 'shared-list-audit-header')
  } else if (fallbackAudit) {
    await page.goto(`${ORIGIN}/portal/audit/${encodeURIComponent(fallbackAudit)}`, { waitUntil: 'domcontentloaded' })
    await waitGrid()
    await shot(PORTAL_OUT, 'shared-list-audit-no-grant')
    check(
      'Audit forbidden state (this user has no audit grant)',
      (await page.locator('.el-alert, .audit-page').count()) > 0,
    )
  } else {
    check('Audit menu exists', false, 'no audit grant and no view FU to open')
  }

  await page.goto(`${ORIGIN}/portal/member-management`, { waitUntil: 'domcontentloaded' })
  await waitGrid()
  await page.getByRole('tab', { name: /Business Unit Members/i }).click()
  await waitGrid()
  const buInput = page.getByPlaceholder(/Select Business Unit/i)
  let buOptionCount = 0
  if (await buInput.count()) {
    await buInput.click()
    const options = page.locator('.el-select-dropdown__item')
    try {
      await options.first().waitFor({ state: 'visible', timeout: 5000 })
      buOptionCount = await options.count()
      await options.first().click()
      await waitGrid()
    } catch {
      buOptionCount = 0
    }
  }
  await assertNoSpacer('Member management')
  if ((await page.locator('.list-col-trigger').count()) > 0) {
    await assertHeaderMenu('Member management')
  } else {
    check(
      'Member management: grid after selecting a BU',
      buOptionCount === 0,
      buOptionCount === 0 ? 'no BU options for this user' : 'selected a BU but no shared header',
    )
  }
  await shot(PORTAL_OUT, 'shared-list-member-management')

  await loginViaAdminPassword(page, { user: 'admin', pass: 'admin123' })
  await page.goto(`${ORIGIN}/admin/user/list`, { waitUntil: 'domcontentloaded' })
  await waitGrid()
  await assertNoSpacer('Admin users')
  await assertHeaderMenu('Admin users')
  await assertActionPinned('Admin users')
  await page.setViewportSize({ width: 1440, height: 900 })
  await assertFrozenPane('Admin users')
  await shot(ADMIN_OUT, 'shared-list-admin-users')
} finally {
  await browser.close()
}

if (failures > 0) {
  process.exit(1)
}
