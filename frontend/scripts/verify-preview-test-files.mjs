/**
 * Open every file in the preview-test-files pack through Portal /file-preview
 * and check the README expectations (kind, html/svg as source, csv as text,
 * excel truncation, tiff pages, mismatch fail-closed).
 *
 * From frontend/: node scripts/verify-preview-test-files.mjs
 */
import { existsSync, mkdirSync, readdirSync, readFileSync } from 'fs'
import { basename, dirname, join, relative, resolve } from 'path'
import { fileURLToPath } from 'url'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const FRONTEND_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const ORIGIN = 'http://localhost:3000'
const STORAGE_KEY = 'ws-file-preview-snapshot'
const FIXTURE_ROOT = resolve(
  FRONTEND_ROOT,
  '..',
  '..',
  '16.（已解决）文件预览功能的增强与迁移',
  'preview-test-files',
)
const OUT_DIR = join(FRONTEND_ROOT, 'user-portal', 'verification-screenshots')
const SHOTS = new Set([
  '01-text/sample.html',
  '01-text/sample.svg',
  '01-text/sample.csv',
  '02-csv-excel/small.xlsx',
  '02-csv-excel/two-sheets.xlsx',
  '03-image/sample.png',
  '04-pdf/three-pages.pdf',
  '05-tiff/two-pages.tiff',
  '06-office/sample.docx',
  '06-office/sample.pptx',
  '06-office/sample.doc',
  '07-unsupported/archive.zip',
  '08-edge/mismatch.jpg',
  '08-edge/truncated-text.txt',
])

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function listFixtures(dir, acc = []) {
  for (const name of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, name.name)
    if (name.isDirectory()) listFixtures(full, acc)
    else if (name.name !== 'README.md' && name.name !== '_generate.py') acc.push(full)
  }
  return acc
}

function expectation(rel) {
  const posix = rel.replace(/\\/g, '/')
  if (posix.startsWith('01-text/')) return { kind: 'text', htmlSource: posix.endsWith('.html'), svgSource: posix.endsWith('.svg'), csvText: posix.endsWith('.csv') }
  if (posix.startsWith('02-csv-excel/') && posix.endsWith('.csv')) return { kind: 'text', csvText: true }
  if (posix.endsWith('.xlsx') || posix.endsWith('.xls')) {
    return {
      kind: 'table',
      truncatedTable: posix.includes('over-1000'),
      sheetTabs: posix.includes('two-sheets') ? 2 : 0,
    }
  }
  if (posix.startsWith('03-image/')) return { kind: 'image' }
  if (posix.startsWith('04-pdf/')) return { kind: 'pdf', pdfPages: posix.includes('three-pages') ? 3 : 1 }
  if (posix.startsWith('05-tiff/')) return { kind: 'tiff', tiffPages: posix.includes('two-pages') ? 2 : 1 }
  if (posix.endsWith('.docx')) return { kind: 'docx' }
  if (posix.endsWith('.pptx')) return { kind: 'pptx' }
  if (posix.endsWith('.doc')) return { kind: 'doc' }
  if (posix.startsWith('07-unsupported/')) return { kind: 'unsupported' }
  if (posix.includes('mismatch')) return { kind: 'unsupported' }
  if (posix.includes('truncated-text')) return { kind: 'text', truncatedText: true }
  return { kind: 'unknown' }
}

function isHeavy(rel) {
  return /over-1000x80|truncated-text/.test(rel)
}

function previewUrl(rel) {
  return `${ORIGIN}/__preview-fixture/${rel.replace(/\\/g, '/')}`
}

async function waitForPreview(page, name) {
  await page.waitForFunction((fileName) => {
    const title = document.querySelector('.file-preview-title')
    return !!(title && title.textContent && title.textContent.trim() === fileName)
  }, name, { timeout: 20000 })
  await page.waitForFunction(() => {
    const loading = document.querySelector('.file-preview-body .el-loading-mask')
    if (loading) return false
    return !!document.querySelector(
      'pre.file-preview-text, .file-preview-table, .file-preview-image-el, .file-preview-pdf canvas, [data-test="file-preview-tiff-scroll"] canvas, .file-preview-office-frame, .el-empty__description',
    )
  }, { timeout: 25000 })
}

async function openFixture(page, rel, { cold }) {
  const name = basename(rel)
  const url = previewUrl(rel)
  const payload = { url, name, items: [{ url, name }], index: 0 }
  await page.evaluate(({ key, snap, channel, reload }) => {
    localStorage.setItem(key, JSON.stringify(snap))
    if (reload) return
    const ch = new BroadcastChannel(channel)
    ch.postMessage(snap)
    ch.close()
  }, { key: STORAGE_KEY, snap: payload, channel: 'ws-file-preview', reload: cold })
  if (cold) {
    await page.goto(`${ORIGIN}/portal/file-preview`, { waitUntil: 'domcontentloaded', timeout: 30000 })
  }
  await waitForPreview(page, name)
}

async function emptyText(page) {
  return (await page.locator('.el-empty__description').first().textContent().catch(() => '')) || ''
}

async function assertCase(page, rel, spec) {
  const problems = []
  const desc = await emptyText(page)
  if (/Failed to load|Could not preview/i.test(desc)) {
    problems.push(`error: ${desc.trim()}`)
    return problems
  }
  if (spec.kind === 'unsupported') {
    if (!/not available/i.test(desc)) problems.push(`expected unsupported empty, got "${desc.trim()}"`)
    return problems
  }
  if (/not available/i.test(desc)) {
    problems.push('got unsupported empty but file should preview')
    return problems
  }
  if (spec.kind === 'text' || spec.kind === 'doc') {
    await page.waitForFunction(() => {
      const pre = document.querySelector('pre.file-preview-text')
      return !!(pre && pre.textContent && pre.textContent.length > 0)
    }, { timeout: 30000 })
    const pre = page.locator('pre.file-preview-text')
    if ((await pre.count()) === 0) problems.push('missing text <pre>')
    else {
      const body = (await pre.textContent()) || ''
      if (spec.csvText && (await page.locator('.file-preview-table table').count()) > 0) {
        problems.push('csv rendered as table')
      }
      if (spec.htmlSource) {
        if (!body.includes('<h1>') || !body.includes('应显示源码')) problems.push('html source missing in pre')
        if ((await page.locator('.file-preview-body h1').count()) > 0) problems.push('html was rendered')
      }
      if (spec.svgSource) {
        if (!body.includes('<svg') || !body.includes('应显示源码不是红块')) problems.push('svg source missing in pre')
        if ((await page.locator('.file-preview-body img, .file-preview-image-el').count()) > 0) {
          problems.push('svg rendered as image')
        }
      }
      if (spec.truncatedText && (await page.locator('.file-preview-text-note').count()) === 0) {
        problems.push('missing text truncation note')
      }
      if (spec.kind === 'doc' && !/DOC preview sample/i.test(body)) {
        problems.push(`doc extract missing expected text (got ${body.slice(0, 80)})`)
      }
    }
  }
  if (spec.kind === 'table') {
    await page.waitForFunction(
      () => document.querySelectorAll('.file-preview-table table tr').length > 0,
      { timeout: 40000 },
    )
    const n = await page.evaluate(() => document.querySelectorAll('.file-preview-table table tr').length)
    if (n === 0) problems.push('missing spreadsheet table')
    if (spec.truncatedTable) {
      if ((await page.locator('.file-preview-table-note').count()) === 0) problems.push('missing table truncation note')
      if (n > 1000) problems.push(`table has ${n} rows, cap is 1000`)
    }
    if (spec.sheetTabs && (await page.locator('.file-preview-table-tabs .el-radio-button').count()) < spec.sheetTabs) {
      problems.push('missing sheet tabs')
    }
  }
  if (spec.kind === 'image') {
    if ((await page.locator('.file-preview-image-el, [data-test="file-preview-image-scroll"]').count()) === 0) {
      problems.push('missing image preview')
    }
    if ((await page.locator('.file-preview-zoom-bar').count()) === 0) problems.push('missing zoom bar')
  }
  if (spec.kind === 'pdf') {
    const canvases = page.locator('.file-preview-pdf canvas')
    const n = await canvases.count()
    if (n === 0) problems.push('missing pdf canvas')
    else if (spec.pdfPages && n < spec.pdfPages) problems.push(`pdf pages ${n} < ${spec.pdfPages}`)
  }
  if (spec.kind === 'tiff') {
    if ((await page.locator('[data-test="file-preview-tiff-scroll"] canvas').count()) === 0) {
      problems.push('missing tiff canvas')
    }
    const pageLabel = await page.locator('.file-preview-tiff-pages').textContent().catch(() => '')
    if (spec.tiffPages > 1) {
      if (!pageLabel || !pageLabel.includes(String(spec.tiffPages))) problems.push(`tiff pages UI missing (${pageLabel})`)
    }
  }
  if (spec.kind === 'docx' || spec.kind === 'pptx') {
    const frame = page.locator('.file-preview-office-frame')
    if ((await frame.count()) === 0) problems.push('missing office iframe')
    else {
      const filled = await page.waitForFunction(() => {
        const iframe = document.querySelector('.file-preview-office-frame')
        const doc = iframe && iframe.contentDocument
        return !!(doc && doc.body && doc.body.childElementCount > 0)
      }, { timeout: 25000 }).then(() => true).catch(() => false)
      if (!filled) problems.push('office iframe stayed empty')
    }
  }
  return problems
}

async function main() {
  if (!existsSync(FIXTURE_ROOT)) {
    console.error(`Fixture root not found: ${FIXTURE_ROOT}`)
    process.exit(1)
  }
  mkdirSync(OUT_DIR, { recursive: true })
  const files = listFixtures(FIXTURE_ROOT).sort((a, b) => {
    const ha = isHeavy(a)
    const hb = isHeavy(b)
    if (ha !== hb) return ha ? 1 : -1
    return a.localeCompare(b)
  })
  console.log(`[fixtures] ${files.length} files from ${FIXTURE_ROOT}`)

  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({ viewport: { width: 1400, height: 900 } })
  const page = await context.newPage()
  await page.route('**/__preview-fixture/**', async (route) => {
    const u = new URL(route.request().url())
    const rel = decodeURIComponent(u.pathname.replace(/^\/__preview-fixture\//, ''))
    const filePath = join(FIXTURE_ROOT, rel)
    if (!existsSync(filePath)) {
      await route.fulfill({ status: 404, body: 'missing' })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/octet-stream',
      body: readFileSync(filePath),
    })
  })

  await loginViaPortalPassword(page, { loginOrigin: ORIGIN })
  await page.goto(`${ORIGIN}/portal/file-preview`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('[data-test="file-preview-page"]', { timeout: 15000 })

  const results = []
  let prevHeavy = false
  for (const full of files) {
    const rel = relative(FIXTURE_ROOT, full).replace(/\\/g, '/')
    const spec = expectation(rel)
    process.stdout.write(`- ${rel} … `)
    try {
      await openFixture(page, rel, { cold: results.length === 0 || prevHeavy })
      const problems = await assertCase(page, rel, spec)
      if (SHOTS.has(rel)) {
        const slug = `preview-pack-${rel.replace(/[\\/]/g, '_').replace(/\./g, '-')}`
        const shot = join(OUT_DIR, `${datePrefix()}_${slug}.png`)
        await page.screenshot({ path: shot, fullPage: false })
        console.log(`shot ${shot}`)
      }
      if (problems.length) {
        console.log(`FAIL ${problems.join('; ')}`)
        results.push({ rel, ok: false, problems })
      } else {
        console.log('ok')
        results.push({ rel, ok: true, problems: [] })
      }
      prevHeavy = isHeavy(rel)
    } catch (err) {
      console.log(`FAIL ${err.message}`)
      results.push({ rel, ok: false, problems: [err.message] })
      prevHeavy = true
    }
  }

  await browser.close()
  const failed = results.filter((r) => !r.ok)
  console.log(`\n${results.length - failed.length}/${results.length} passed`)
  if (failed.length) {
    for (const row of failed) console.log(`  ✗ ${row.rel}: ${row.problems.join('; ')}`)
    process.exit(1)
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
