/**
 * Screenshot + behaviour verification for the Developer Workstation left sidebar
 * (nav + "Recent" function units), the shared three-app shell, and designer Back.
 *
 * Run from frontend/:  node scripts/verify-dw-sidebar-recent.mjs
 * Screenshots land in frontend/developer-workstation/verification-screenshots/ and MUST NOT be deleted.
 */
import { mkdirSync } from 'fs'
import { join } from 'path'
import { chromium } from 'playwright'
import { loginViaUnifiedSso } from './playwright-login.mjs'

const OUT = new URL('../developer-workstation/verification-screenshots/', import.meta.url).pathname
mkdirSync(OUT, { recursive: true })
const d = new Date()
const pad = (n) => String(n).padStart(2, '0')
const DATE = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const shot = async (page, name, opts = {}) => {
  const p = join(OUT, `${DATE}_${name}.png`)
  await page.screenshot({ path: p, ...opts })
  console.log('[shot]', p)
}

const LIST = 'http://localhost:3000/dev/function-units'
const failures = []
const check = (label, ok, detail = '') => {
  console.log(`[${ok ? 'pass' : 'FAIL'}] ${label}${detail ? ` — ${detail}` : ''}`)
  if (!ok) failures.push(label)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
const errors = []
page.on('pageerror', (e) => errors.push(e.message))

try {
  await loginViaUnifiedSso(page, 'dw')
  await page.goto(LIST, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(5000)

  // Start from a clean slate so the empty state is the real first-run view
  await page.evaluate(() => {
    Object.keys(localStorage)
      .filter((k) => k.startsWith('dw-fu-recent:') || k === 'sidebar-collapsed')
      .forEach((k) => localStorage.removeItem(k))
  })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(5000)

  // ---- Shell parity with admin-center / user-portal: full-height aside carrying the
  //      brand, red header spanning only the content column.
  const shell = await page.evaluate(() => {
    const box = (sel) => {
      const el = document.querySelector(sel)
      if (!el) return null
      const r = el.getBoundingClientRect()
      return { left: Math.round(r.left), top: Math.round(r.top), width: Math.round(r.width), height: Math.round(r.height) }
    }
    return { aside: box('.dw-aside'), brand: box('.dw-aside .brand'), header: box('.main-header'), crumb: box('.crumb-home') }
  })
  console.log('[shell]', JSON.stringify(shell))
  check('aside is full height and 248px wide', shell.aside?.top === 0 && shell.aside?.width === 248)
  check('brand sits inside the aside', Boolean(shell.brand) && shell.brand.top === 0)
  check('red header starts right of the aside', shell.header?.left === 248, `header.left=${shell.header?.left}`)
  check('header is 64px tall (parity)', shell.header?.height === 64)
  check('breadcrumb home crumb rendered', Boolean(shell.crumb))

  await shot(page, 'dw-sidebar-01-empty-recent')
  await shot(page, 'dw-sidebar-01-empty-recent-aside', { clip: { x: 0, y: 0, width: 300, height: 900 } })

  // ---- Open three function units so "Recent" has content
  const total = await page.locator('.launchpad-cell').count()
  console.log('[info] launchpad cells =', total)
  const visited = []
  for (let i = 0; i < Math.min(3, total); i++) {
    await page.goto(LIST, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(3500)
    await page.locator('.launchpad-cell').nth(i).click()
    await page.waitForTimeout(4000)
    visited.push((await page.locator('.card h3').first().textContent())?.trim())
    console.log('[open]', page.url(), visited.at(-1))
  }
  await shot(page, 'dw-sidebar-03-designer-active')

  // ---- Clicking a Recent entry really switches the designer to that function unit
  const beforeUrl = page.url()
  const beforeTitle = (await page.locator('.card h3').first().textContent())?.trim()
  await page.locator('.recent-item').nth(2).click()
  await page.waitForTimeout(4500)
  const afterUrl = page.url()
  const afterTitle = (await page.locator('.card h3').first().textContent())?.trim()
  console.log('[switch]', JSON.stringify({ beforeUrl, beforeTitle, afterUrl, afterTitle }))
  check('URL changed to the clicked function unit', beforeUrl !== afterUrl)
  check('designer reloaded onto the clicked function unit', Boolean(afterTitle) && afterTitle !== beforeTitle)
  check('clicked entry is the only one marked as current', (await page.locator('.recent-item.is-active').count()) === 1)
  await shot(page, 'dw-sidebar-05-after-recent-switch')

  // ---- Back always leaves the designer for the list, never walks the visited chain
  await page.locator('.recent-item').nth(1).click()
  await page.waitForTimeout(4000)
  await page.getByRole('button', { name: /Back/i }).first().click()
  await page.waitForTimeout(3500)
  console.log('[back]', page.url())
  check('Back returns to the function unit list', page.url().endsWith('/dev/function-units'))

  // The list is account-scoped and server-persisted, so earlier runs may leave entries
  // behind: assert the three just-visited function units are the ones on top, not that
  // the list is exactly three long.
  const names = (await page.locator('.recent-name').allTextContents()).map((n) => n.trim())
  const topThree = new Set(names.slice(0, 3))
  console.log('[recent]', JSON.stringify({ names, visited }))
  check(
    'the three visited function units are on top of the recent list',
    visited.every((n) => topThree.has(n)),
    `top=${names.slice(0, 3).join(' | ')}`
  )
  check('recent list stays within its cap', names.length > 0 && names.length <= 8, `count=${names.length}`)
  await shot(page, 'dw-sidebar-02-recent-expanded')
  await shot(page, 'dw-sidebar-02-recent-expanded-aside', { clip: { x: 0, y: 0, width: 300, height: 900 } })

  // ---- Collapsed dock + persisted preference
  await page.locator('.collapse-btn').click()
  await page.waitForTimeout(1200)
  await shot(page, 'dw-sidebar-04-collapsed-dock')
  await shot(page, 'dw-sidebar-04-collapsed-dock-aside', { clip: { x: 0, y: 0, width: 180, height: 900 } })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(4500)
  const width = await page.locator('.dw-aside').evaluate((el) => Math.round(el.getBoundingClientRect().width))
  check('collapse preference survives a reload', width === 64, `width=${width}`)

  check('no page errors', errors.length === 0, errors.join(' | '))
} finally {
  await browser.close()
}

console.log(failures.length ? `\nFAILED: ${failures.join(', ')}` : '\nALL CHECKS PASSED')
process.exit(failures.length ? 1 : 0)
