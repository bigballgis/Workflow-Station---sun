// Automation Flow Migration「查看结构」弹窗验证:
//   1) live   — 真实已发布 flow 的线性链渲染
//   2) draft  — 无发布版本的 flow 显示草稿告警
//   3) router — 拦截 export 接口注入 ROUTER+LOOP+未知类型 fixture,验证嵌套渲染
//     (dev 库没有带分支的真实 flow,fixture 契约与 src/utils/flowStructure.ts 单测同源)
// 截图落 frontend/admin-center/verification-screenshots/,验证后保留。
//   cd frontend && node scripts/verify-flow-structure-dialog.mjs
import { chromium } from 'playwright'
import { loginViaAdminPassword } from './playwright-login.mjs'

const OUT_DIR = new URL('../admin-center/verification-screenshots/', import.meta.url).pathname
const DATE = new Date().toISOString().slice(0, 10)

// 名称含关键词的行都可;找不到时回退第一行(router 场景响应被拦截,行内容无关紧要)
const LIVE_FLOW = process.env.LIVE_FLOW ?? 'HERMES E2E envelope final'
const DRAFT_FLOW = process.env.DRAFT_FLOW ?? 'Untitled'

const routerFixture = {
  hermesFlowExport: 1,
  flowKey: 'fixture-router',
  displayName: 'Router fixture',
  schemaVersion: '23',
  fromPublished: true,
  trigger: {
    name: 'trigger',
    type: 'PIECE_TRIGGER',
    displayName: 'Catch Webhook',
    settings: { pieceName: '@activepieces/piece-webhook', triggerName: 'catch_webhook' },
    nextAction: {
      name: 'step_1',
      type: 'ROUTER',
      displayName: 'Route by amount',
      settings: { branches: [{ branchName: 'amount > 1000' }, { branchName: 'Otherwise' }] },
      children: [
        {
          name: 'step_2',
          type: 'LOOP_ON_ITEMS',
          displayName: 'Loop approvers',
          firstLoopAction: {
            name: 'step_3',
            type: 'CODE',
            displayName: 'Build payload',
            nextAction: {
              name: 'step_4',
              type: 'PIECE',
              displayName: 'Send mail',
              settings: { pieceName: '@activepieces/piece-smtp', actionName: 'send_email' }
            }
          }
        },
        null
      ],
      nextAction: { name: 'step_5', type: 'FUTURE_TYPE', displayName: 'Mystery step' }
    }
  },
  notes: [],
  connections: []
}

const openStructureDialog = async (page, rowText) => {
  await page.goto('http://localhost:3000/admin/automation-flows')
  await page.waitForSelector('.el-table__row', { timeout: 15000 })
  const rows = page.locator('.el-table__row', { hasText: rowText })
  const row = (await rows.count()) > 0 ? rows.first() : page.locator('.el-table__row').first()
  await row.locator('.el-dropdown button').click()
  await page.locator('.el-dropdown-menu__item:visible', { hasText: /查看结构|View structure|檢視結構/ })
    .first().click()
  await page.waitForSelector('.el-dialog .step-card, .el-dialog .structure-empty', { timeout: 15000 })
  await page.waitForTimeout(400)
}

const shoot = async (page, slug) => {
  const path = `${OUT_DIR}${DATE}_${slug}.png`
  await page.locator('.el-dialog').first().screenshot({ path })
  console.log('saved:', path)
}

const expectVisible = async (page, selector, label) => {
  if (await page.locator(selector).count() === 0) {
    throw new Error(`ASSERT FAIL [${label}]: selector not found: ${selector}`)
  }
  console.log(`assert ok [${label}]: ${selector}`)
}

const closeDialog = async (page) => {
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)
}

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
try {
  await loginViaAdminPassword(page)

  await openStructureDialog(page, LIVE_FLOW)
  await expectVisible(page, '.el-dialog .step-card', 'live: step cards')
  await shoot(page, 'flow-structure-live')
  await closeDialog(page)

  await openStructureDialog(page, DRAFT_FLOW)
  await expectVisible(page, '.el-dialog .el-alert--warning', 'draft: draft banner')
  await shoot(page, 'flow-structure-draft')
  await closeDialog(page)

  await page.route('**/automation/flows/*/export', route =>
    route.fulfill({ contentType: 'application/json', body: JSON.stringify(routerFixture) }))
  await openStructureDialog(page, LIVE_FLOW)
  await expectVisible(page, '.el-dialog .step-nested .step-nested .step-card', 'router: loop nested in branch')
  await expectVisible(page, '.el-dialog .step-nested__empty', 'router: empty branch placeholder')
  await expectVisible(page, '.el-dialog .el-tag--danger', 'router: unknown type tag')
  await shoot(page, 'flow-structure-router-fixture')

  console.log('flow-structure dialog verification PASS')
} finally {
  await browser.close()
}
