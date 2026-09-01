/**
 * Capture file-preview new-tab + zoom chrome after portal deploy.
 * From frontend/: node scripts/verify-file-preview-tab.mjs
 */
import { mkdirSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const FRONTEND_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const OUT_DIR = join(FRONTEND_ROOT, 'user-portal', 'verification-screenshots')
const ORIGIN = 'http://localhost:3000'

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function outPath(slug) {
  return join(OUT_DIR, `${datePrefix()}_${slug}.png`)
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({ viewport: { width: 1400, height: 900 } })
  const page = await context.newPage()
  await loginViaPortalPassword(page, { loginOrigin: ORIGIN })

  await page.goto(`${ORIGIN}/portal/file-preview`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('[data-test="file-preview-page"]', { timeout: 15000 })
  await page.waitForTimeout(500)
  const emptyPath = outPath('file-preview-empty-tab')
  await page.screenshot({ path: emptyPath, fullPage: true })
  console.log(`[shot] ${emptyPath}`)

  await page.goto(`${ORIGIN}/portal/tasks`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(3500)
  const requestLinks = page.locator('.el-table__body .el-link')
  const linkCount = await requestLinks.count()
  if (linkCount === 0) {
    console.log('No task request links; skipped form/file shots')
    await browser.close()
    return
  }

  let popup = null
  for (let i = 0; i < Math.min(8, linkCount); i++) {
    await page.goto(`${ORIGIN}/portal/tasks`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(2000)
    const popupPromise = page.waitForEvent('popup', { timeout: 12000 }).catch(() => null)
    await page.locator('.el-table__body .el-link').nth(i).click()
    await page.waitForURL(/\/portal\/tasks\//, { timeout: 15000 }).catch(() => {})
    await page.waitForTimeout(2500)
    popup = await popupPromise
    if (popup) {
      console.log(`[popup] auto-preview or click opened tab on task index ${i}`)
      break
    }
    const fileLink = page.locator('.file-preview-link').first()
    if ((await fileLink.count()) > 0) {
      const clickPopup = page.waitForEvent('popup', { timeout: 8000 }).catch(() => null)
      await fileLink.click()
      popup = await clickPopup
      console.log(`[click] file link on task index ${i}, popup=${!!popup}`)
      break
    }
  }

  if (popup) {
    await popup.waitForSelector('[data-test="file-preview-shell"], .el-empty', { timeout: 20000 })
    await popup.waitForTimeout(2000)
    const tabPath = outPath('file-preview-new-tab')
    await popup.screenshot({ path: tabPath, fullPage: true })
    console.log(`[shot] ${tabPath}`)
    const formPath = outPath('file-preview-form-uncovered')
    await page.screenshot({ path: formPath, fullPage: true })
    console.log(`[shot] ${formPath}`)
    const zoom = popup.locator('.file-preview-zoom-bar')
    if ((await zoom.count()) > 0) {
      const zoomPath = outPath('file-preview-zoom-hint')
      await zoom.screenshot({ path: zoomPath })
      console.log(`[shot] ${zoomPath}`)
    }
  } else {
    const dialog = page.locator('[data-test="file-preview-shell"]')
    if ((await dialog.count()) > 0) {
      const dialogPath = outPath('file-preview-dialog-fallback')
      await page.screenshot({ path: dialogPath, fullPage: true })
      console.log(`[shot] ${dialogPath} (dialog fallback)`)
    } else {
      const missPath = outPath('file-preview-task-no-file')
      await page.screenshot({ path: missPath, fullPage: true })
      console.log(`[shot] ${missPath} (no preview opened)`)
    }
  }

  await browser.close()
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
