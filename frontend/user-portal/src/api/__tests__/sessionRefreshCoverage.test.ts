import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'
import axios, { type AxiosAdapter } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { installSessionRefresh, setSessionRefresher } from '@platform-shared/auth/sessionRefresh'

/**
 * Guard: every axios instance that talks to the platform must renew the session on 401
 * (access tokens live 15 minutes). A new `axios.create(...)` without `installSessionRefresh`
 * right after it would log the user out as soon as the token expires.
 */
const frontendRoot = resolve(__dirname, '../../../..')
const APP_SOURCES = ['admin-center/src', 'user-portal/src', 'developer-workstation/src', 'shared/src']

/** Instances that do not carry the platform session (their 401 means something else). */
const NOT_PLATFORM_SESSION = new Set([
  'developer-workstation/src/api/automation.ts#apAxios', // Activepieces bearer token from the bridge
])

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) return name === '__tests__' || name === 'node_modules' ? [] : sourceFiles(full)
    return /\.(ts|vue)$/.test(name) ? [full] : []
  })
}

describe('session refresh coverage', () => {
  it('installs session refresh right after every platform axios instance', () => {
    const missing: string[] = []
    let instances = 0
    for (const app of APP_SOURCES) {
      for (const file of sourceFiles(join(frontendRoot, app))) {
        const src = readFileSync(file, 'utf8')
        for (const match of src.matchAll(/const (\w+)(?::\s*\w+)?\s*=\s*axios\.create\(/g)) {
          const name = match[1]
          const id = `${relative(frontendRoot, file).replace(/\\/g, '/')}#${name}`
          instances++
          if (NOT_PLATFORM_SESSION.has(id)) continue
          const closing = src.indexOf('\n})', match.index)
          const after = src.slice(closing, closing + 200)
          if (!new RegExp(`installSessionRefresh\\(${name},`).test(after)) missing.push(id)
        }
      }
    }
    expect(instances).toBeGreaterThan(10)
    expect(missing).toEqual([])
  })
})

describe('refresh call of each app', () => {
  afterEach(() => setSessionRefresher(null))

  // A refresh call that itself answered 401 and triggered a refresh would wait on itself forever.
  it.each(['admin-center', 'user-portal', 'developer-workstation'])(
    '%s: a 401 from its real /refresh call does not start another refresh',
    async (app) => {
      const src = readFileSync(join(frontendRoot, app, 'src/api/auth.ts'), 'utf8')
      const baseURL = src.match(/const authRequest = axios\.create\(\{\s*baseURL:\s*['"]([^'"]+)['"]/)?.[1]
      const path = src.match(/authRequest\.post<\w+>\(\s*['"]([^'"]+)['"]/g)
        ?.map((call) => call.match(/['"]([^'"]+)['"]/)?.[1])
        .find((p) => p?.endsWith('/refresh'))
      expect(baseURL).toBeTruthy()
      expect(path).toBe('/refresh')

      const adapter: AxiosAdapter = async (config) => {
        throw new axios.AxiosError('HTTP 401', '401', config, null, {
          data: {}, status: 401, statusText: '401', headers: {}, config,
        })
      }
      const instance = axios.create({ baseURL, adapter })
      installSessionRefresh(instance, (config) => axios.request({ ...config, adapter }))
      const refresher = vi.fn(async () => undefined)
      setSessionRefresher(refresher)

      await expect(instance.post(path!)).rejects.toMatchObject({ response: { status: 401 } })
      expect(refresher).not.toHaveBeenCalled()
    },
  )
})
