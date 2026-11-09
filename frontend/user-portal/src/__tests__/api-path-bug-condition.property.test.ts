// @vitest-environment node
import { describe, test, expect } from 'vitest'
import * as fc from 'fast-check'
import * as fs from 'fs'
import * as path from 'path'

/**
 * Bug Condition Exploration Tests — Frontend API Paths (user-portal)
 * Feature: kong-authn-authz-fix
 *
 * **Property 2: Bug Condition** — 前端 API 路径与 Kong 路由不匹配
 *
 * These tests encode the EXPECTED behavior (after fix).
 * They MUST FAIL on unfixed code — failure confirms the bugs exist.
 *
 * **Validates: Requirements 1.1, 1.2**
 */

const PORTAL_API_DIR = path.resolve(__dirname, '..', 'api')

/**
 * Extract baseURL value from an axios.create() call in source code.
 */
function extractBaseURL(sourceContent: string, varName: string): string | null {
  const regex = new RegExp(
    `(?:const|let|var)\\s+${varName}\\s*=\\s*axios\\.create\\(\\s*\\{[^}]*baseURL:\\s*['"]([^'"]+)['"]`,
    's'
  )
  const match = sourceContent.match(regex)
  return match ? match[1] : null
}

// ============================================================
// Test C7: user-portal user.ts user lookups go through the user-portal backend
// (originally: adminCenterAxios pointed at the unrouted /api/admin-center; since the platform access
// model WP1 the browser no longer calls admin-center at all — see noDirectAdminCenterCalls.test.ts)
// ============================================================

describe('C7: user-portal user.ts user lookups', () => {
  const userTsPath = path.resolve(PORTAL_API_DIR, 'user.ts')
  const sourceContent = fs.readFileSync(userTsPath, 'utf-8')

  test('creates no admin-center client and uses neither /api/admin-center nor /api/v1/admin', () => {
    expect(extractBaseURL(sourceContent, 'adminCenterAxios')).toBeNull()
    expect(sourceContent).not.toMatch(/\/api\/admin-center\b/)
    expect(sourceContent).not.toMatch(/\/api\/v1\/admin\b/)
  })

  test('user search and user name lookups call the user-portal backend', () => {
    expect(sourceContent).toMatch(/portalRequest\.get<[^>]*>\('\/tasks\/users\/search'/)
    expect(sourceContent).toMatch(/portalRequest\.post<[^>]*>\(\s*'\/tasks\/users\/summaries'/)
  })
})

// ============================================================
// Test C8: user-portal auth.ts baseURL should be /api/portal/auth
// ============================================================

describe('C8: user-portal auth.ts authRequest baseURL', () => {
  const authTsPath = path.resolve(PORTAL_API_DIR, 'auth.ts')
  const sourceContent = fs.readFileSync(authTsPath, 'utf-8')

  test('authRequest baseURL should be /api/portal/auth (not /api/v1/auth)', () => {
    const baseURL = extractBaseURL(sourceContent, 'authRequest')
    expect(baseURL).not.toBeNull()
    expect(baseURL).toBe('/api/portal/auth')
  })

  test('property: for any auth endpoint, requests route to user-portal backend', () => {
    const authEndpoints = ['/login', '/logout', '/refresh', '/me', '/validate']

    fc.assert(
      fc.property(fc.constantFrom(...authEndpoints), (endpoint: string) => {
        const baseURL = extractBaseURL(sourceContent, 'authRequest')
        expect(baseURL).toBe('/api/portal/auth')

        const fullPath = `${baseURL}${endpoint}`
        // Should go through /api/portal/ route (user-portal backend)
        expect(fullPath).toMatch(/^\/api\/portal\//)
        // Should NOT go through /api/v1/ route (developer-workstation backend)
        expect(fullPath).not.toMatch(/^\/api\/v1\//)
      }),
      { numRuns: 100 }
    )
  })
})

// ============================================================
// Test C9: user-portal request.ts should NOT set X-User-Id when no userId
// ============================================================

describe('C9: user-portal request.ts X-User-Id header behavior', () => {
  const requestTsPath = path.resolve(PORTAL_API_DIR, 'request.ts')
  const sourceContent = fs.readFileSync(requestTsPath, 'utf-8')

  // Platform access model §7.2: the Portal backend takes the user from the credential only.
  test('requests carry no X-User-Id at all', () => {
    expect(sourceContent.includes('X-User-Id')).toBe(false)
  })

  test('source code should NOT contain hardcoded user_1 fallback for X-User-Id', () => {
    // The source should not have a fallback to 'user_1'
    // Expected: userId is set only when it exists, no hardcoded fallback
    const hasHardcodedFallback = sourceContent.includes("userId || 'user_1'") ||
                                  sourceContent.includes('userId || "user_1"')
    expect(hasHardcodedFallback).toBe(false)
  })

  test('property: for any scenario where userId is absent, X-User-Id should not be set', () => {
    fc.assert(
      fc.property(fc.constant(null), () => {
        // Verify the source code pattern: X-User-Id should only be set conditionally
        // The fix should change: config.headers['X-User-Id'] = userId || 'user_1'
        // To: if (userId) { config.headers['X-User-Id'] = userId }

        // Check that the source does NOT contain the hardcoded fallback pattern
        const hasHardcodedFallback = sourceContent.includes("userId || 'user_1'") ||
                                      sourceContent.includes('userId || "user_1"')
        expect(hasHardcodedFallback).toBe(false)

        // Platform access model §7.2: the header is not sent at all (identity comes from the credential).
        expect(sourceContent.includes('X-User-Id')).toBe(false)
      }),
      { numRuns: 100 }
    )
  })
})
