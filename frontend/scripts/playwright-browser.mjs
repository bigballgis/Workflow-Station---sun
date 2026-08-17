/**
 * Single place that decides which Chromium binary the verify/regression scripts drive.
 *
 * Playwright normally uses its own downloaded build, but that download is blocked on
 * some machines, which used to make every screenshot script — including the
 * `regression:mi` gate — unrunnable. Resolution order:
 *
 *   1. PLAYWRIGHT_EXECUTABLE_PATH  — explicit binary
 *   2. PLAYWRIGHT_CHANNEL          — system channel, e.g. "chrome" / "msedge"
 *   3. Playwright's managed Chromium, when it is actually installed
 *   4. A locally installed Google Chrome
 *
 * Which binary was picked is always logged, so a screenshot can be attributed to a
 * browser. When nothing is available we throw — never silently skip the run.
 */

import { existsSync } from 'fs'

const SYSTEM_CHROME_PATHS = [
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/Applications/Chromium.app/Contents/MacOS/Chromium',
  '/usr/bin/google-chrome',
  '/usr/bin/google-chrome-stable',
  '/usr/bin/chromium',
  '/usr/bin/chromium-browser',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
]

async function loadChromium() {
  try {
    const { chromium } = await import('playwright')
    return chromium
  } catch {
    throw new Error(
      'playwright is not installed. From frontend/ run:\n  pnpm install\n  pnpm exec playwright install chromium',
    )
  }
}

function managedChromiumPath(chromium) {
  try {
    return chromium.executablePath()
  } catch {
    return ''
  }
}

function resolveBrowserSource(chromium) {
  const explicitPath = process.env.PLAYWRIGHT_EXECUTABLE_PATH
  if (explicitPath) {
    if (!existsSync(explicitPath)) {
      throw new Error(`PLAYWRIGHT_EXECUTABLE_PATH does not exist: ${explicitPath}`)
    }
    return { opts: { executablePath: explicitPath }, label: `explicit ${explicitPath}` }
  }

  const channel = process.env.PLAYWRIGHT_CHANNEL
  if (channel) return { opts: { channel }, label: `channel ${channel}` }

  const managed = managedChromiumPath(chromium)
  if (managed && existsSync(managed)) {
    return { opts: {}, label: 'playwright-managed chromium' }
  }

  const systemChrome = SYSTEM_CHROME_PATHS.find((p) => existsSync(p))
  if (systemChrome) {
    return { opts: { executablePath: systemChrome }, label: `system ${systemChrome}` }
  }

  throw new Error(
    'No Chromium available. Either run `pnpm exec playwright install chromium`, ' +
      'install Google Chrome, or set PLAYWRIGHT_EXECUTABLE_PATH / PLAYWRIGHT_CHANNEL.',
  )
}

/** Launch Chromium headless. Caller options win over the resolved binary. */
export async function launchChromium(opts = {}) {
  const chromium = await loadChromium()
  const { opts: source, label } = resolveBrowserSource(chromium)
  console.log(`[playwright] browser: ${label}`)
  return chromium.launch({ headless: true, ...source, ...opts })
}
