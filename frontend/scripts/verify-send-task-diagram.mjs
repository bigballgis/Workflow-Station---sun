#!/usr/bin/env node
/**
 * Portal Send Email task — process diagram screenshot verification.
 *
 * Captures application detail / task detail workflow diagrams for code review.
 * Requires a running instance where Send Email has already executed (green node expected).
 *
 * Usage (from frontend/):
 *   node scripts/verify-send-task-diagram.mjs
 *
 * Env:
 *   APPLICATION_ID   — portal application id (applications/detail)
 *   TASK_ID          — optional; task detail diagram (overrides app-only second shot)
 *   LOGIN_ORIGIN     — default http://localhost:3000
 *   LOGIN_USER/PASS  — default developer / password
 *   LOGIN_BU_CODE / LOGIN_ROLE_CODE — multi-UBR workspace pick
 *   SEND_TASK_NAME   — optional legend label to assert in history (e.g. "Send Task")
 */

import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = resolve(__dirname, '..')
const OUT_DIR = join(FRONTEND_ROOT, 'user-portal', 'verification-screenshots')
const ORIGIN = (process.env.LOGIN_ORIGIN ?? 'http://localhost:3000').replace(/\/$/, '')
const APPLICATION_ID = process.env.APPLICATION_ID?.trim()
const TASK_ID = process.env.TASK_ID?.trim()
const SEND_TASK_NAME = process.env.SEND_TASK_NAME?.trim()

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function launchBrowser() {
  const launchOpts = { headless: true }
  if (process.env.PLAYWRIGHT_EXECUTABLE_PATH) {
    launchOpts.executablePath = process.env.PLAYWRIGHT_EXECUTABLE_PATH
  } else if (process.env.PLAYWRIGHT_CHANNEL) {
    launchOpts.channel = process.env.PLAYWRIGHT_CHANNEL
  }
  const browser = await chromium.launch(launchOpts)
  const context = await browser.newContext({ viewport: { width: 1440, height: 1200 } })
  return { browser, page: await context.newPage() }
}

async function waitForDiagram(page) {
  const diagram = page.locator('.process-diagram, .bpmn-canvas').first()
  await diagram.waitFor({ state: 'visible', timeout: 45000 })
  await page.waitForTimeout(1500)
  return diagram
}

async function screenshotDiagram(page, slug) {
  mkdirSync(OUT_DIR, { recursive: true })
  const prefix = datePrefix()
  const outPath = join(OUT_DIR, `${prefix}_portal-send-task-diagram-${slug}.png`)
  const diagram = await waitForDiagram(page)
  await diagram.scrollIntoViewIfNeeded()
  await page.waitForTimeout(800)
  await diagram.screenshot({ path: outPath })
  console.log('[saved]', outPath)
  return outPath
}

async function screenshotHistorySection(page, slug) {
  mkdirSync(OUT_DIR, { recursive: true })
  const prefix = datePrefix()
  const outPath = join(OUT_DIR, `${prefix}_portal-send-task-history-${slug}.png`)
  const history = page.locator('.process-history, [class*="ProcessHistory"]').first()
  if ((await history.count()) === 0) {
    console.warn('[skip] process history section not found')
    return null
  }
  await history.scrollIntoViewIfNeeded()
  await page.waitForTimeout(500)
  await history.screenshot({ path: outPath })
  console.log('[saved]', outPath)
  return outPath
}

async function assertSendHistoryRow(page) {
  if (!SEND_TASK_NAME) return
  const sendAction = page.getByText(/send task|发信|發信|send/i).first()
  if ((await sendAction.count()) === 0) {
    console.warn(`[warn] no SEND history row matching SEND_TASK_NAME hint; check timeline manually`)
  }
}

async function captureApplicationDetail(page) {
  if (!APPLICATION_ID) {
    console.log('[skip] APPLICATION_ID not set — application detail screenshot skipped')
    return []
  }
  const url = `${ORIGIN}/portal/applications/${APPLICATION_ID}`
  console.log('[portal] application detail', url)
  await page.goto(url, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(3000)
  await assertSendHistoryRow(page)
  const shots = []
  shots.push(await screenshotDiagram(page, `app-${APPLICATION_ID}`))
  const hist = await screenshotHistorySection(page, `app-${APPLICATION_ID}`)
  if (hist) shots.push(hist)
  return shots
}

async function captureTaskDetail(page) {
  if (!TASK_ID) return []
  const url = `${ORIGIN}/portal/tasks/${TASK_ID}`
  console.log('[portal] task detail', url)
  await page.goto(url, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(3500)
  await assertSendHistoryRow(page)
  const shots = []
  shots.push(await screenshotDiagram(page, `task-${TASK_ID}`))
  const hist = await screenshotHistorySection(page, `task-${TASK_ID}`)
  if (hist) shots.push(hist)
  return shots
}

async function main() {
  if (!APPLICATION_ID && !TASK_ID) {
    console.error(
      'Set at least one of APPLICATION_ID or TASK_ID.\n' +
      'Example:\n' +
      '  APPLICATION_ID=abc-123 node scripts/verify-send-task-diagram.mjs\n' +
      '  TASK_ID=task-456 APPLICATION_ID=abc-123 node scripts/verify-send-task-diagram.mjs',
    )
    process.exit(1)
  }

  const { browser, page } = await launchBrowser()
  const saved = []
  try {
    console.log('[portal] login (password)')
    await loginViaPortalPassword(page, { loginOrigin: ORIGIN })
    saved.push(...await captureApplicationDetail(page))
    saved.push(...await captureTaskDetail(page))
  } finally {
    await browser.close()
  }

  console.log('\n--- Send task diagram verification ---')
  for (const p of saved) console.log(' ', p)
  if (saved.length === 0) process.exit(1)
}

main().catch(err => {
  console.error(err)
  process.exit(1)
})
