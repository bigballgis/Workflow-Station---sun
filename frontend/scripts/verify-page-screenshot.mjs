#!/usr/bin/env node
/**
 * Playwright screenshot verification for frontend UI / parity changes.
 *
 * Usage (from frontend/):
 *   pnpm run verify:screenshot -- --app portal --url http://localhost:3000/portal/tasks/xxx --name task-detail
 *   pnpm run verify:screenshot -- --app portal --url ... --selector ".form-layout-card" --name title-card
 *
 * Screenshots are written to frontend/<app>/verification-screenshots/ and MUST NOT be deleted after verify.
 */

import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import {
  loginViaAdminPassword,
  loginViaDwPassword,
  loginViaPortalPassword,
  loginViaUnifiedSso,
} from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = resolve(__dirname, '..')

const APP_OUT_DIRS = {
  portal: join(FRONTEND_ROOT, 'user-portal', 'verification-screenshots'),
  admin: join(FRONTEND_ROOT, 'admin-center', 'verification-screenshots'),
  dw: join(FRONTEND_ROOT, 'developer-workstation', 'verification-screenshots'),
}

function parseArgs(argv) {
  const out = {
    app: 'portal',
    url: '',
    name: 'page',
    selector: '',
    wait: 5000,
    fullPage: false,
    expectSelector: '',
    clickSelector: '',
    skipLogin: false,
    viewport: '1400x1600',
  }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    const next = () => argv[++i]
    if (a === '--app') out.app = next()
    else if (a === '--url') out.url = next()
    else if (a === '--name') out.name = next()
    else if (a === '--selector') out.selector = next()
    else if (a === '--wait') out.wait = Number(next()) || 5000
    else if (a === '--full-page') out.fullPage = true
    else if (a === '--expect-selector') out.expectSelector = next()
    else if (a === '--click-selector') out.clickSelector = next()
    else if (a === '--skip-login') out.skipLogin = true
    else if (a === '--viewport') out.viewport = next()
    else if (a === '--help' || a === '-h') out.help = true
  }
  return out
}

function printHelp() {
  console.log(`
verify-page-screenshot — save Playwright screenshots for UI / parity verification

Options:
  --app portal|admin|dw     Target app (default: portal)
  --url <url>               Page URL after deploy (required)
  --name <slug>             Filename slug (default: page)
  --selector <css>          Capture element instead of full viewport
  --full-page               Full scrollable page screenshot
  --wait <ms>               Wait after navigation (default: 5000)
  --expect-selector <css>   Fail if selector count is 0
  --click-selector <css>    Click an element after navigation (e.g. a tab)
  --skip-login              Do not run SSO login first
  --viewport WxH            Browser size (default: 1400x1600)

Env: LOGIN_USER, LOGIN_PASS

Output: frontend/<app>/verification-screenshots/{date}_{name}.png
`)
}

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function loadPlaywright() {
  try {
    return await import('playwright')
  } catch {
    console.error(
      'playwright is not installed. From frontend/ run:\n' +
        '  pnpm install\n' +
        '  pnpm exec playwright install chromium',
    )
    process.exit(1)
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  if (args.help || !args.url) {
    printHelp()
    if (!args.url && !args.help) process.exit(1)
    return
  }

  const outDir = APP_OUT_DIRS[args.app]
  if (!outDir) {
    console.error(`Unknown --app "${args.app}". Use portal | admin | dw`)
    process.exit(1)
  }
  mkdirSync(outDir, { recursive: true })

  const slug = args.name.replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/^-|-$/g, '') || 'page'
  const outPath = join(outDir, `${datePrefix()}_${slug}.png`)

  const [vw, vh] = args.viewport.split('x').map(Number)
  const { chromium } = await loadPlaywright()
  // Allow reusing a locally-installed Chrome/Chromium (PLAYWRIGHT_EXECUTABLE_PATH)
  // or a system channel (PLAYWRIGHT_CHANNEL, e.g. "chrome") when the managed
  // headless-shell download is unavailable/blocked.
  const launchOpts = { headless: true }
  if (process.env.PLAYWRIGHT_EXECUTABLE_PATH) {
    launchOpts.executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH
  } else if (process.env.PLAYWRIGHT_CHANNEL) {
    launchOpts.channel = process.env.PLAYWRIGHT_CHANNEL
  }
  const browser = await chromium.launch(launchOpts)
  const page = await (await browser.newContext({
    viewport: { width: vw || 1400, height: vh || 1600 },
  })).newPage()

  const errors = []
  page.on('pageerror', (err) => errors.push(err.message))

  try {
    if (!args.skipLogin) {
      console.log(`[login] app=${args.app}`)
      // Prefer password login (SSO form is unreliable headless).
      if (args.app === 'admin') await loginViaAdminPassword(page)
      else if (args.app === 'dw') await loginViaDwPassword(page)
      else if (args.app === 'portal') await loginViaPortalPassword(page)
      else await loginViaUnifiedSso(page, args.app)
    }

    console.log(`[goto] ${args.url}`)
    await page.goto(args.url, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(args.wait)

    if (args.clickSelector) {
      console.log(`[click] ${args.clickSelector}`)
      await page.locator(args.clickSelector).first().click()
      await page.waitForTimeout(800)
    }

    if (args.expectSelector) {
      const count = await page.locator(args.expectSelector).count()
      console.log(`[expect] "${args.expectSelector}" count=${count}`)
      if (count === 0) {
        throw new Error(`Expected selector not found: ${args.expectSelector}`)
      }
    }

    // Parity helper: report cards with nested tables (portal forms)
    if (args.app === 'portal') {
      const cards = await page.evaluate(() =>
        Array.from(document.querySelectorAll('.form-layout-card')).map((card) => ({
          cardTitle: card.querySelector('.form-layout-card-title')?.textContent?.trim() ?? '',
          elTables: card.querySelectorAll('.el-table').length,
        })),
      )
      if (cards.length) console.log('[cards]', JSON.stringify(cards, null, 2))
    }

    if (args.selector) {
      const loc = page.locator(args.selector).first()
      await loc.scrollIntoViewIfNeeded()
      await page.waitForTimeout(400)
      await loc.screenshot({ path: outPath })
    } else {
      await page.screenshot({ path: outPath, fullPage: args.fullPage })
    }

    console.log(`[saved] ${outPath}`)
    if (errors.length) {
      console.warn('[page errors]', errors.join('\n'))
    }
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error('[verify-page-screenshot] FAILED:', err.message)
  process.exit(1)
})
