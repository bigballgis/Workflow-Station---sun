import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const output = resolve(dirname(fileURLToPath(import.meta.url)), '../help/verification-screenshots')
mkdirSync(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1366, height: 768 } })
const errors = []
page.on('pageerror', error => errors.push(error.message))
try {
  await page.goto('http://localhost:3000/help/assignment-exclusion', { waitUntil: 'networkidle' })
  const article = page.getByTestId('assignment-exclusion-guide-page')
  await article.waitFor()
  for (const [locale, title] of [['en', 'Assignment exclusions'], ['zh-CN', '分派互斥'], ['zh-TW', '分派互斥']]) {
    await page.getByTestId('help-locale-select').selectOption(locale)
    await page.waitForFunction(expected => document.querySelector('[data-testid="assignment-exclusion-guide-page"] h1')?.textContent === expected, title)
    const text = await article.innerText()
    assert.ok(!text.includes('assignmentExclusionGuide.'), 'All article keys must resolve')
    assert.equal(await article.locator('section').count(), 4)
    assert.ok(await article.locator('a[href="/help/task-delegate"]').count() > 0, 'Related delegation guide must resolve')
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'No horizontal page overflow')
    await page.locator('.help-main').evaluate(element => { element.scrollTop = 0 })
    const path = resolve(output, `2026-10-27_assignment-exclusion-help-${locale}.png`)
    await page.screenshot({ path })
    console.log(`${locale}: article, links and layout passed; ${path}`)
  }
  assert.deepEqual(errors, [])
} finally { await browser.close() }
