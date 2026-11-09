import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * Guard: Portal pages must not call admin-center from the browser. admin-center does not read the
 * Portal session cookie, and an expired cookie of another app on the same origin makes such a call
 * 401 — which Portal's own session refresh cannot fix. Go through the user-portal backend instead.
 */
const srcRoot = resolve(__dirname, '../..')

/** Sign-in happens before any session exists, so it is not affected. */
const ALLOWED = new Set(['views/login/UnifiedLogin.vue'])

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) return name === '__tests__' ? [] : sourceFiles(full)
    return /\.(ts|vue)$/.test(name) ? [full] : []
  })
}

describe('Portal browser code', () => {
  it('does not call admin-center directly', () => {
    const offenders = sourceFiles(srcRoot)
      .map((file) => relative(srcRoot, file).replace(/\\/g, '/'))
      .filter((file) => !ALLOWED.has(file))
      .filter((file) => readFileSync(join(srcRoot, file), 'utf8').includes('/api/v1/admin'))
    expect(offenders).toEqual([])
  })
})
