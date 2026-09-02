/**
 * Verify Guidelines header language control is a dropdown (not three text links).
 *
 * Usage (from frontend/):
 *   node scripts/verify-help-locale-dropdown.mjs
 *
 * Output: help/verification-screenshots/{date}_help-locale-dropdown-*.png
 * Screenshots MUST NOT be deleted after verify.
 */
import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = resolve(__dirname, '..')
const OUT_DIR = join(FRONTEND_ROOT, 'help', 'verification-screenshots')
const ORIGIN = (process.env.LOGIN_ORIGIN ?? 'http://localhost:3000').replace(/\/$/, '')

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

function launchOpts() {
  const opts = { headless: true }
  if (process.env.PLAYWRIGHT_EXECUTABLE_PATH) {
    opts.executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH
  } else if (process.env.PLAYWRIGHT_CHANNEL) {
    opts.channel = process.env.PLAYWRIGHT_CHANNEL
  }
  return opts
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const prefix = datePrefix()
  const shot = async (page, slug, locator) => {
    const p = join(OUT_DIR, `${prefix}_${slug}.png`)
    if (locator) {
      await locator.screenshot({ path: p })
    } else {
      await page.screenshot({ path: p, fullPage: false })
    }
    console.log('[shot]', p)
    return p
  }

  const { chromium } = await loadPlaywright()
  const browser = await chromium.launch(launchOpts())
  const page = await (
    await browser.newContext({ viewport: { width: 1400, height: 900 } })
  ).newPage()
  const failures = []
  const check = (label, ok, detail = '') => {
    console.log(`[${ok ? 'pass' : 'FAIL'}] ${label}${detail ? ` — ${detail}` : ''}`)
    if (!ok) failures.push(label)
  }

  try {
    await page.goto(`${ORIGIN}/help/form-events`, { waitUntil: 'domcontentloaded' })
    const article = page.getByTestId('form-events-guide-page')
    await article.waitFor({ state: 'visible', timeout: 20000 })

    const select = page.getByTestId('help-locale-select')
    await select.waitFor({ state: 'visible', timeout: 10000 })
    check('Language control is a select', (await select.evaluate((el) => el.tagName)) === 'SELECT')
    check('Old three-link buttons are gone', (await page.locator('button.help-lang').count()) === 0)
    const optionValues = await select.locator('option').evaluateAll((opts) =>
      opts.map((o) => o.getAttribute('value')),
    )
    check(
      'Select lists en / zh-CN / zh-TW',
      optionValues.join(',') === 'en,zh-CN,zh-TW',
      optionValues.join(','),
    )

    const header = page.locator('.help-header')
    const crumb = page.locator('.crumb-current')

    await select.selectOption('zh-CN')
    await crumb.getByText('表单事件', { exact: true }).waitFor({ timeout: 8000 })
    check('Switching to Simplified Chinese updates the crumb', true)
    check('Select value is zh-CN', (await select.inputValue()) === 'zh-CN')
    await shot(page, 'help-locale-dropdown-zh-CN', header)

    await select.selectOption('en')
    await crumb.getByText('Form events', { exact: true }).waitFor({ timeout: 8000 })
    check(
      'Switching to English updates the brand',
      (await page.getByTestId('help-brand').textContent())?.includes('Guidelines') === true,
    )
    check('Select value is en', (await select.inputValue()) === 'en')
    await shot(page, 'help-locale-dropdown-en', header)

    await select.selectOption('zh-TW')
    await crumb.getByText('表單事件', { exact: true }).waitFor({ timeout: 8000 })
    check('Switching to Traditional Chinese updates the crumb', true)
    check('Select value is zh-TW', (await select.inputValue()) === 'zh-TW')
    await shot(page, 'help-locale-dropdown-zh-TW', header)

    await select.selectOption('zh-CN')
    await crumb.getByText('表单事件', { exact: true }).waitFor({ timeout: 8000 })
    await shot(page, 'help-locale-dropdown-page', null)
  } finally {
    await browser.close()
  }

  if (failures.length) {
    console.error(`\n${failures.length} check(s) failed`)
    process.exit(1)
  }
  console.log('\nhelp locale dropdown: all checks passed')
}

main().catch((err) => {
  console.error('[verify-help-locale-dropdown] FAILED:', err.message)
  process.exit(1)
})
