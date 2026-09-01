/**
 * New Request (process start) must honor designer cannotDownload:
 * preview toolbar Download is hidden for main + sub-table uploads.
 *
 * From frontend/:
 *   FU_CODE=<process start code> node scripts/verify-cannot-download-start.mjs
 * Optional:
 *   UPLOAD_FILE  path to a file to upload on the start form
 *   APP_ID       existing application id to also check My Request preview
 */
import { mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const FRONTEND_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const OUT_DIR = join(FRONTEND_ROOT, 'user-portal', 'verification-screenshots')
const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const FU_CODE = process.env.FU_CODE ?? 'fu-20260422-23tfag'
const APP_ID = process.env.APP_ID
const UPLOAD_FILE = process.env.UPLOAD_FILE

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function outPath(slug) {
  return join(OUT_DIR, `${datePrefix()}_${slug}.png`)
}

const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 1600, height: 900 } })
const page = await context.newPage()
mkdirSync(OUT_DIR, { recursive: true })

function downloadButtons(target) {
  return target.locator('.file-preview-actions').getByRole('button', { name: /download|下载|下載/i })
}

async function openPreviewFromFirstFile(hostPage) {
  const link = hostPage.locator('.file-preview-link, .upload-filename-tag, .el-upload-list__item-name, .upload-download-link').first()
  if (await link.count() === 0) return null
  const popupPromise = context.waitForEvent('page', { timeout: 4000 }).catch(() => null)
  await link.click()
  const popup = await popupPromise
  if (popup) {
    await popup.waitForLoadState('domcontentloaded')
    await popup.waitForTimeout(1200)
    return popup
  }
  await hostPage.waitForTimeout(1500)
  if (await hostPage.locator('.file-preview-shell, .file-preview-header').count()) return hostPage
  return hostPage
}

try {
  await loginViaPortalPassword(page, {
    buCode: process.env.LOGIN_BU_CODE ?? 'hase-hmdc',
    roleCode: process.env.LOGIN_ROLE_CODE ?? 'HMDC_Index_Role',
  })

  await page.goto(`${ORIGIN}/portal/processes/start/${FU_CODE}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(4000)
  await page.locator('.form-layout-card, .el-upload').first().waitFor({ timeout: 15000 }).catch(() => {})
  await page.screenshot({ path: outPath('cannot-download-new-request-01-start'), fullPage: true })

  if (UPLOAD_FILE) {
    const fileInput = page.locator('.el-upload input[type="file"]').first()
    if (await fileInput.count()) {
      await fileInput.setInputFiles(UPLOAD_FILE)
      await page.waitForTimeout(3000)
      const startPreview = await openPreviewFromFirstFile(page)
      if (startPreview) {
        await startPreview.screenshot({ path: outPath('cannot-download-new-request-02-start-preview'), fullPage: false })
        const n = await downloadButtons(startPreview).count()
        console.log(`[verify] start-form preview download buttons=${n} url=${startPreview.url()}`)
        if (n > 0) throw new Error('FAIL: New Request start form still shows Download in preview')
        if (startPreview !== page) await startPreview.close()
      } else {
        console.log('[verify] start form upload did not produce a clickable file link')
      }
    }
  } else {
    console.log('[verify] UPLOAD_FILE unset; skipped start-form upload')
  }

  if (!APP_ID) {
    console.log('[verify] APP_ID unset; skipped existing application preview')
    console.log('[verify] PASS — start form checked; set APP_ID to also check a saved request')
  } else {
    await page.goto(`${ORIGIN}/portal/applications/${APP_ID}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(5000)
    await page.screenshot({ path: outPath('cannot-download-new-request-03-application'), fullPage: true })

    const preview = await openPreviewFromFirstFile(page)
    if (!preview) {
      throw new Error(`FAIL: no file link on application ${APP_ID} — cannot check Download`)
    }
    await preview.screenshot({ path: outPath('cannot-download-new-request-04-application-preview'), fullPage: false })
    const n = await downloadButtons(preview).count()
    console.log(`[verify] preview download buttons=${n} url=${preview.url()}`)
    if (n > 0) throw new Error('FAIL: preview still shows Download')
    console.log('[verify] PASS — Download hidden on request file preview')
  }
} finally {
  await browser.close()
}
