/**
 * Portal New Request: Upload field accepts two files in one picker.
 * Screenshots land in user-portal/verification-screenshots/.
 */
import { writeFileSync, mkdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT = resolve(__dirname, '../user-portal/verification-screenshots')
mkdirSync(OUT, { recursive: true })
const DATE = new Date().toISOString().slice(0, 10)
const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'

function unwrap(json) {
  return json && typeof json === 'object' && 'data' in json ? json.data : json
}

function tmpPdf(name) {
  const dir = join(tmpdir(), 'ws-form-upload-verify')
  mkdirSync(dir, { recursive: true })
  const path = join(dir, name)
  writeFileSync(path, '%PDF-1.1\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n')
  return path
}

async function processKeys(page) {
  const forced = process.env.FU_CODE?.trim()
  if (forced) return [forced]
  const res = await page.request.get(`${ORIGIN}/api/portal/processes/definitions`)
  if (!res.ok()) throw new Error(`definitions HTTP ${res.status()}`)
  const body = unwrap(await res.json())
  const list = Array.isArray(body) ? body : (body?.records || body?.content || [])
  const keys = list.map((d) => String(d.key || d.processKey || d.code || '')).filter(Boolean)
  const fallback = ['fu-20260422-23tfag', 'atm-20260623-gaevus']
  return [...new Set([...keys, ...fallback])]
}

const browser = await chromium.launch({ headless: true })
const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()

try {
  await loginViaPortalPassword(page, { buCode: 'hase-hmdc', roleCode: 'HMDC_Index_Role' })
  const keys = await processKeys(page)
  let found = ''
  for (const key of keys) {
    await page.goto(`${ORIGIN}/portal/processes/start/${key}`, { waitUntil: 'domcontentloaded' })
    const input = page.locator('.el-upload input[type="file"]').first()
    try {
      await input.waitFor({ state: 'attached', timeout: 15000 })
      found = key
      break
    } catch {
      /* try next startable process */
    }
  }
  if (!found) throw new Error(`No start form with an Upload field (tried ${keys.length} keys)`)

  const input = page.locator('.el-upload input[type="file"]').first()
  await input.setInputFiles([tmpPdf('alpha-upload.pdf'), tmpPdf('beta-upload.pdf')])
  await page.waitForFunction(() => {
    const names = [...document.querySelectorAll('.el-upload-list__item-name, .el-upload-list__item')]
    return names.filter((el) => /alpha-upload|beta-upload/.test(el.textContent || '')).length >= 2
  }, null, { timeout: 25000 })

  const shot = join(OUT, `${DATE}_portal-form-upload-multi.png`)
  const host = page.locator('.el-upload, .field-renderer-root').first()
  if (await host.count()) await host.screenshot({ path: shot })
  else await page.screenshot({ path: shot, fullPage: false })
  console.log(`PASS  two files visible on start form ${found}`)
  console.log(`screenshot ${shot}`)
} finally {
  await browser.close()
}
