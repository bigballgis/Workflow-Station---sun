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
// Test C7: user-portal user.ts adminCenterAxios baseURL should be /api/v1/admin
// ============================================================

describe('C7: user-portal user.ts adminCenterAxios baseURL', () => {
  const userTsPath = path.resolve(PORTAL_API_DIR, 'user.ts')
  const sourceContent = fs.readFileSync(userTsPath, 'utf-8')

  test('adminCenterAxios baseURL should be /api/v1/admin (not /api/admin-center)', () => {
    const baseURL = extractBaseURL(sourceContent, 'adminCenterAxios')
    expect(baseURL).not.toBeNull()
    expect(baseURL).toBe('/api/v1/admin')
  })

  test('property: for any userId, user API requests go through /api/v1/admin route', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 50 }).filter(s => s.trim().length > 0),
        (userId: string) => {
          const baseURL = extractBaseURL(sourceContent, 'adminCenterAxios')
          expect(baseURL).toBe('/api/v1/admin')

          const fullPath = `${baseURL}/users/${userId}/business-units`
          expect(fullPath).toMatch(/^\/api\/v1\/admin\//)
          expect(fullPath).not.toMatch(/^\/api\/admin-center\//)
        }
      ),
      { numRuns: 100 }
    )
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

        // Check that X-User-Id is set conditionally (only when userId exists)
        // The fixed code should have a conditional check before setting the header
        const hasConditionalSet = sourceContent.includes("if (userId)") ||
                                   sourceContent.includes("if(userId)")
        expect(hasConditionalSet).toBe(true)
      }),
      { numRuns: 100 }
    )
  })
})
