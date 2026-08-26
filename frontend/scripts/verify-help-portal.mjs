/**
 * Capture Guidelines portal home + guideline articles (no login).
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const DW_SHOTS = resolve(__dirname, '../developer-workstation/verification-screenshots')
mkdirSync(DW_SHOTS, { recursive: true })
const DATE = new Date().toISOString().slice(0, 10)

const results = []
const rec = (n, ok, d = '') => {
  results.push({ n, ok, d })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${d ? ` — ${d}` : ''}`)
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1400, height: 900 } })).newPage()

try {
  await page.goto('http://localhost:3000/help/', { waitUntil: 'domcontentloaded' })
  const home = page.getByTestId('help-home')
  await home.waitFor({ state: 'visible', timeout: 20000 })
  rec('Help catalog is visible without login', await home.isVisible())
  rec(
    'Home lists articles by job',
    await page.getByTestId('help-by-need').isVisible(),
  )

  const homeShot = resolve(DW_SHOTS, `${DATE}_help-portal-home.png`)
  await page.screenshot({ path: homeShot, fullPage: true })
  console.log(`screenshot ${homeShot}`)

  rec(
    'Sidebar follows Developer Workstation menus',
    await page.getByTestId('help-nav-dw-tables').isVisible(),
  )

  await page.getByRole('button', { name: 'Admin Center' }).click()
  await page.getByRole('button', { name: 'Relation Tables' }).click()
  rec(
    'Sidebar hangs computed-fields under Admin Relation Tables',
    await page.getByTestId('help-nav-ac-rt-struct').isVisible(),
  )

  await page.getByTestId('help-card-dw-tables').click()
  const article = page.getByTestId('computed-field-guide-page')
  await article.waitFor({ state: 'visible', timeout: 15000 })
  rec('Computed-fields guideline is visible', await article.isVisible())
  rec(
    'Computed-fields samples use help_pr_line',
    (await article.textContent())?.includes('SUM(help_pr_line.line_total)') === true,
  )
  rec(
    'Computed-fields has order-of-work steps',
    (await article.locator('.help-flow li').count()) >= 4,
  )
  rec(
    'URL is /help/computed-fields',
    page.url().includes('/help/computed-fields'),
    page.url(),
  )

  const articleShot = resolve(DW_SHOTS, `${DATE}_help-portal-computed-fields.png`)
  await page.screenshot({ path: articleShot, fullPage: true })
  console.log(`screenshot ${articleShot}`)

  await page.goto('http://localhost:3000/help/email-send', { waitUntil: 'domcontentloaded' })
  const sendArticle = page.getByTestId('email-send-guide-page')
  await sendArticle.waitFor({ state: 'visible', timeout: 15000 })
  rec('Send-email guideline is visible', await sendArticle.isVisible())
  rec(
    'Send-email related links include Email Monitor',
    await sendArticle.locator('.help-related a[href$="/email-monitor"]').count().then((n) => n > 0),
  )
  rec('URL is /help/email-send', page.url().includes('/help/email-send'), page.url())
  const sendShot = resolve(DW_SHOTS, `${DATE}_help-portal-email-send.png`)
  await page.screenshot({ path: sendShot, fullPage: true })
  console.log(`screenshot ${sendShot}`)

  await page.goto('http://localhost:3000/help/email-monitor', { waitUntil: 'domcontentloaded' })
  const monitorArticle = page.getByTestId('email-monitor-guide-page')
  await monitorArticle.waitFor({ state: 'visible', timeout: 15000 })
  rec('Email-monitor guideline is visible', await monitorArticle.isVisible())
  rec(
    'Email-monitor names Vendor quote to PR',
    (await monitorArticle.textContent())?.includes('Vendor quote to PR') === true,
  )
  rec(
    'URL is /help/email-monitor',
    page.url().includes('/help/email-monitor'),
    page.url(),
  )
  const monitorShot = resolve(DW_SHOTS, `${DATE}_help-portal-email-monitor.png`)
  await page.screenshot({ path: monitorShot, fullPage: true })
  console.log(`screenshot ${monitorShot}`)

  await page.getByRole('button', { name: 'User Portal' }).click()
  await page.getByRole('button', { name: 'Task' }).click()
  rec(
    'Sidebar hangs task-delegate under Portal To Do',
    await page.getByTestId('help-nav-up-todo').isVisible(),
  )

  await page.goto('http://localhost:3000/help/task-delegate', { waitUntil: 'domcontentloaded' })
  const delegateArticle = page.getByTestId('task-delegate-guide-page')
  await delegateArticle.waitFor({ state: 'visible', timeout: 15000 })
  rec('Task-delegate guideline is visible', await delegateArticle.isVisible())
  rec(
    'Task-delegate names Specified BU and Role',
    (await delegateArticle.textContent())?.includes('Specified BU and Role') === true,
  )
  rec(
    'URL is /help/task-delegate',
    page.url().includes('/help/task-delegate'),
    page.url(),
  )
  const delegateShot = resolve(DW_SHOTS, `${DATE}_help-portal-task-delegate.png`)
  await page.screenshot({ path: delegateShot, fullPage: true })
  console.log(`screenshot ${delegateShot}`)

  const llms = await page.goto('http://localhost:3000/help/llms.txt', { waitUntil: 'domcontentloaded' })
  const llmsText = llms ? await llms.text() : ''
  rec('llms.txt is served', llms?.ok() === true && llmsText.includes('/help/computed-fields'))
  rec('llms.txt lists task-delegate', llmsText.includes('/help/task-delegate'))

  await page.goto('http://localhost:3000/dev/help/computed-fields', {
    waitUntil: 'domcontentloaded',
  })
  await page.waitForURL('**/help/computed-fields', { timeout: 10000 })
  rec('Legacy /dev/help/computed-fields redirects to /help/computed-fields', true)
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} passed`)
if (failed.length) process.exit(1)
