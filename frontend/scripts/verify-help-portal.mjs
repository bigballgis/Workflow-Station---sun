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

const launchOpts = { headless: true }
if (process.env.PLAYWRIGHT_EXECUTABLE_PATH) {
  launchOpts.executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH
} else if (process.env.PLAYWRIGHT_CHANNEL) {
  launchOpts.channel = process.env.PLAYWRIGHT_CHANNEL
}

const browser = await chromium.launch(launchOpts)
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
  rec(
    'Send-email HTML mode keeps style tags',
    (await sendArticle.textContent())?.includes('<style>') === true,
  )
  rec(
    'Send-email field catalog names Sender Email',
    (await sendArticle.textContent())?.includes('Sender Email (From address)') === true,
  )
  rec(
    'Send-email field catalog names From override',
    (await sendArticle.textContent())?.includes('From (override)') === true,
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
    'Email-monitor field catalog names Rule Name',
    (await monitorArticle.textContent())?.includes('Rule Name') === true,
  )
  rec(
    'Email-monitor field catalog names Start process when email arrives',
    (await monitorArticle.textContent())?.includes('Start process when email arrives') === true,
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
  rec('llms.txt lists form-events', llmsText.includes('/help/form-events'))
  rec('llms.txt lists form-events-basic', llmsText.includes('/help/form-events-basic'))
  rec('llms.txt lists form-events-extend', llmsText.includes('/help/form-events-extend'))
  rec('llms.txt lists form-events-layout', llmsText.includes('/help/form-events-layout'))

  await page.goto('http://localhost:3000/help/form-events', { waitUntil: 'domcontentloaded' })
  const eventsArticle = page.getByTestId('form-events-guide-page')
  await eventsArticle.waitFor({ state: 'visible', timeout: 15000 })
  rec('Form-events guideline is visible', await eventsArticle.isVisible())
  rec(
    'Form-events names the user object',
    (await eventsArticle.textContent())?.includes('user.activeBusinessUnitName') === true,
  )
  rec(
    'Form-events names script parameters',
    (await eventsArticle.textContent())?.includes('$inject.api') === true,
  )
  rec(
    'Form-events documents required and hide',
    (await eventsArticle.textContent())?.includes('api.required(true') === true
      && (await eventsArticle.textContent())?.includes("api.hidden(true, 'cost_center')") === true,
  )
  rec(
    'Form-events documents lock, options, banner, lookup, focus',
    (await eventsArticle.textContent())?.includes("api.disabled(true, 'cost_center')") === true
      && (await eventsArticle.textContent())?.includes("api.setOptions('scenario'") === true
      && (await eventsArticle.textContent())?.includes('api.setFormNotification(') === true
      && (await eventsArticle.textContent())?.includes('api.setLookupFilter(field') === true
      && (await eventsArticle.textContent())?.includes("api.setFocus('request_title')") === true,
  )
  rec(
    'Form-events failures: error blocks Save, banner does not',
    (await eventsArticle.textContent())?.includes('blocks Save') === true
      && (await eventsArticle.textContent())?.includes('ERROR banner does not block Save') === true,
  )
  rec(
    'Sidebar hangs How to write events under Controls',
    await page.getByTestId('help-nav-dw-forms').isVisible(),
  )
  rec('URL is /help/form-events', page.url().includes('/help/form-events'), page.url())
  const eventsShot = resolve(DW_SHOTS, `${DATE}_help-portal-form-events.png`)
  await page.screenshot({ path: eventsShot, fullPage: true })
  console.log(`screenshot ${eventsShot}`)

  for (const hash of ['disabled', 'options', 'notify', 'lookup', 'chrome']) {
    await page.goto(`http://localhost:3000/help/form-events#${hash}`, {
      waitUntil: 'domcontentloaded',
    })
    await eventsArticle.waitFor({ state: 'visible', timeout: 15000 })
    const hashShot = resolve(DW_SHOTS, `${DATE}_help-portal-form-events-${hash}.png`)
    await page.screenshot({ path: hashShot, fullPage: true })
    console.log(`screenshot ${hashShot}`)
  }

  await page.goto('http://localhost:3000/help/form-events-basic#select', {
    waitUntil: 'domcontentloaded',
  })
  const basicArticle = page.getByTestId('form-events-basic-guide-page')
  await basicArticle.waitFor({ state: 'visible', timeout: 15000 })
  rec('Form-events-basic guideline is visible', await basicArticle.isVisible())
  rec(
    'Form-events-basic documents Select scenario',
    (await basicArticle.textContent())?.includes("value === 'A'") === true,
  )
  rec(
    'Form-events-basic documents setOptions',
    (await basicArticle.textContent())?.includes("api.setOptions('scenario'") === true,
  )
  rec(
    'Sidebar hangs Select under Controls',
    await page.getByTestId('help-nav-dw-form-ctl-select').isVisible(),
  )
  const basicShot = resolve(DW_SHOTS, `${DATE}_help-portal-form-events-basic.png`)
  await page.screenshot({ path: basicShot, fullPage: true })
  console.log(`screenshot ${basicShot}`)

  await page.goto('http://localhost:3000/help/form-events-extend#subTable', {
    waitUntil: 'domcontentloaded',
  })
  const extendArticle = page.getByTestId('form-events-extend-guide-page')
  await extendArticle.waitFor({ state: 'visible', timeout: 15000 })
  rec('Form-events-extend guideline is visible', await extendArticle.isVisible())
  rec(
    'Form-events-extend names Sub-Table',
    (await extendArticle.textContent())?.includes('help_pr_line') === true,
  )
  rec(
    'Form-events-extend documents lookup filter',
    (await extendArticle.textContent())?.includes('api.setLookupFilter(field') === true,
  )
  const extendShot = resolve(DW_SHOTS, `${DATE}_help-portal-form-events-extend.png`)
  await page.screenshot({ path: extendShot, fullPage: true })
  console.log(`screenshot ${extendShot}`)

  await page.goto('http://localhost:3000/help/form-events-layout#elButton', {
    waitUntil: 'domcontentloaded',
  })
  const layoutArticle = page.getByTestId('form-events-layout-guide-page')
  await layoutArticle.waitFor({ state: 'visible', timeout: 15000 })
  rec('Form-events-layout guideline is visible', await layoutArticle.isVisible())
  rec(
    'Form-events-layout names Button click',
    (await layoutArticle.textContent())?.includes('user && user.displayName') === true,
  )
  const layoutShot = resolve(DW_SHOTS, `${DATE}_help-portal-form-events-layout.png`)
  await page.screenshot({ path: layoutShot, fullPage: true })
  console.log(`screenshot ${layoutShot}`)

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
