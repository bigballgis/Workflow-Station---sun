#!/usr/bin/env node
/**
 * Screenshot SensitiveMaskPropsEditor (Designer props panel) via a local Vite harness.
 *
 * Usage (from frontend/):
 *   node scripts/verify-dw-sensitive-mask-props.mjs
 *
 * Output: developer-workstation/verification-screenshots/{date}_sensitive-mask-props-*.png
 */
import { mkdirSync, writeFileSync } from 'fs'
import { dirname, join, resolve } from 'path'
import { fileURLToPath } from 'url'
import { spawn } from 'child_process'
import { chromium } from 'playwright'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = resolve(__dirname, '..')
const DW_ROOT = join(FRONTEND_ROOT, 'developer-workstation')
const HARNESS_DIR = join(DW_ROOT, 'scripts', 'sensitive-mask-harness')
const OUT_DIR = join(DW_ROOT, 'verification-screenshots')
const HARNESS_URL = 'http://127.0.0.1:5199/'

function datePrefix() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function waitForUrl(url, timeoutMs = 60000) {
  const start = Date.now()
  return new Promise((resolveWait, reject) => {
    const tick = async () => {
      try {
        const res = await fetch(url)
        if (res.ok || res.status === 404) {
          resolveWait()
          return
        }
      } catch {
        /* not up yet */
      }
      if (Date.now() - start > timeoutMs) {
        reject(new Error(`Harness did not become ready at ${url}`))
        return
      }
      setTimeout(tick, 300)
    }
    tick()
  })
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  const prefix = datePrefix()

  const viteBin = join(DW_ROOT, 'node_modules', 'vite', 'bin', 'vite.js')
  const child = spawn(
    process.execPath,
    [viteBin, '--config', join(HARNESS_DIR, 'vite.config.ts')],
    {
      cwd: HARNESS_DIR,
      stdio: ['ignore', 'pipe', 'pipe'],
      env: { ...process.env, BROWSER: 'none' },
    },
  )

  let viteLog = ''
  child.stdout.on('data', (buf) => {
    viteLog += buf.toString()
  })
  child.stderr.on('data', (buf) => {
    viteLog += buf.toString()
  })

  try {
    await waitForUrl(HARNESS_URL)
    const browser = await chromium.launch({ headless: true })
    const page = await (
      await browser.newContext({ viewport: { width: 520, height: 900 } })
    ).newPage()

    await page.goto(HARNESS_URL, { waitUntil: 'networkidle' })
    await page.waitForSelector('.sensitive-mask-props-editor', { timeout: 30000 })
    await page.waitForTimeout(800)

    const rangesPath = join(OUT_DIR, `${prefix}_sensitive-mask-props-ranges.png`)
    await page.locator('.sensitive-mask-props-editor').screenshot({ path: rangesPath })
    console.log('OK', rangesPath)

    // Switch to "Mask entire value" for second evidence shot
    await page.locator('.sensitive-mask-props-editor .el-select').first().click()
    await page.waitForTimeout(300)
    const allOpt = page.locator('.el-select-dropdown__item').filter({
      hasText: /Mask entire value|全部打码|全部遮罩/,
    })
    if ((await allOpt.count()) > 0) {
      await allOpt.first().click()
      await page.waitForTimeout(400)
      const allPath = join(OUT_DIR, `${prefix}_sensitive-mask-props-all.png`)
      await page.locator('.sensitive-mask-props-editor').screenshot({ path: allPath })
      console.log('OK', allPath)
    } else {
      console.warn('WARN: could not find "all" preset option; ranges shot only')
    }

    await browser.close()

    const notePath = join(OUT_DIR, `${prefix}_sensitive-mask-props-NOTE.md`)
    writeFileSync(
      notePath,
      [
        '# Sensitive Mask Props Editor — UI verification',
        '',
        `- Harness: \`scripts/sensitive-mask-harness\` (Vite :5199)`,
        `- Script: \`frontend/scripts/verify-dw-sensitive-mask-props.mjs\``,
        `- Screenshots: \`${prefix}_sensitive-mask-props-ranges.png\`, \`${prefix}_sensitive-mask-props-all.png\``,
        '',
        'Covers Designer props: enabled switch, preset dropdown (all / ranges), interval rows, preview.',
        '',
      ].join('\n'),
      'utf8',
    )
    console.log('OK', notePath)
  } catch (err) {
    console.error('FAIL', err)
    console.error('--- vite log ---\n', viteLog)
    process.exitCode = 1
  } finally {
    child.kill('SIGTERM')
    // Windows: ensure process tree exits
    setTimeout(() => {
      try {
        child.kill('SIGKILL')
      } catch {
        /* ignore */
      }
      process.exit(process.exitCode || 0)
    }, 500)
  }
}

main()
