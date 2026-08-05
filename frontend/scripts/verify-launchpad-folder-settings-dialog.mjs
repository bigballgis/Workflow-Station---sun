// 验证：分组浮层（Launchpad folder）内点「⋯ → Settings」，Settings 弹窗要显示在浮层之上。
// 回归点：.folder-overlay 曾是 z-index 3000，压过 Element Plus 弹层基线（2000+），弹窗被盖在背后。
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { loginViaDwPassword } from './playwright-login.mjs'

const OUT = new URL('../developer-workstation/verification-screenshots/', import.meta.url).pathname
const DATE = new Date().toISOString().slice(0, 10)

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
mkdirSync(OUT, { recursive: true })

await loginViaDwPassword(page)
await page.goto('http://localhost:3000/dev/function-units', { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(2500)

// 打开第一个分组（folder tile）
const folder = page.locator('.launchpad-folder').first()
await folder.waitFor({ timeout: 15000 })
await folder.click()
await page.locator('.folder-overlay').waitFor({ timeout: 5000 })
await page.waitForTimeout(600)
await page.screenshot({ path: `${OUT}${DATE}_folder-overlay-open.png` })

// 成员卡 ⋯ → Settings
const card = page.locator('.folder-overlay .member-card').first()
await card.hover()
await card.locator('.member-menu-btn').click()
await page.waitForTimeout(400)
await page.screenshot({ path: `${OUT}${DATE}_folder-overlay-dropdown.png` })

await page.locator('.el-dropdown-menu__item:visible').first().click()
await page.waitForTimeout(800)

const dialog = page.locator('.el-dialog').first()
await dialog.waitFor({ state: 'visible', timeout: 5000 })
await page.screenshot({ path: `${OUT}${DATE}_folder-overlay-settings-dialog.png` })

// 断言：弹窗真的在浮层之上（层级 + 命中测试）
const probe = await page.evaluate(() => {
  const z = (el) => (el ? Number(getComputedStyle(el).zIndex) || 0 : null)
  const overlay = document.querySelector('.folder-overlay')
  const dlg = document.querySelector('.el-dialog')
  const wrapper = dlg?.closest('.el-overlay') ?? dlg?.parentElement
  const r = dlg.getBoundingClientRect()
  const hit = document.elementFromPoint(r.left + r.width / 2, r.top + 20)
  return {
    overlayZ: z(overlay),
    dialogWrapperZ: z(wrapper),
    topElementInsideDialog: !!dlg.contains(hit),
    hitTag: hit?.className?.toString?.().slice(0, 60),
  }
})
console.log('[probe]', probe)

const ok = probe.dialogWrapperZ > probe.overlayZ && probe.topElementInsideDialog
console.log(ok ? '[PASS] settings dialog renders above the folder overlay' : '[FAIL] dialog still behind overlay')

await browser.close()
process.exit(ok ? 0 : 1)
