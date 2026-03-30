import { describe, test, expect } from 'vitest'
import * as fc from 'fast-check'
import * as fs from 'fs'
import * as path from 'path'

/**
 * Bug Condition Exploration Tests — Frontend API Paths (developer-workstation)
 * Feature: kong-authn-authz-fix
 *
 * **Property 2: Bug Condition** — 前端 API 路径与 Kong 路由不匹配
 *
 * These tests encode the EXPECTED behavior (after fix).
 * They MUST FAIL on unfixed code — failure confirms the bugs exist.
 *
 * **Validates: Requirements 1.1, 1.2**
 */

const DW_API_DIR = path.resolve(__dirname, '..', 'api')

/**
 * Extract baseURL value from an axios.create() call in source code.
 * Returns the string literal value of the baseURL property.
 */
function extractBaseURL(sourceContent: string, varName: string): string | null {
  // Match: const <varName> = axios.create({ baseURL: '<value>' ... })
  // or: const <varName> = axios.create({ baseURL: "<value>" ... })
  const regex = new RegExp(
    `(?:const|let|var)\\s+${varName}\\s*=\\s*axios\\.create\\(\\s*\\{[^}]*baseURL:\\s*['"]([^'"]+)['"]`,
    's'
  )
  const match = sourceContent.match(regex)
  return match ? match[1] : null
}

// ============================================================
// Test C5: developer-workstation user.ts baseURL should be /api/v1/admin
// ============================================================

describe('C5: developer-workstation user.ts adminCenterAxios baseURL', () => {
  const userTsPath = path.resolve(DW_API_DIR, 'user.ts')
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

          // The full request path for any userId should start with /api/v1/admin
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
// Test C6: developer-workstation adminCenter.ts baseURL should be /api/v1/admin
// ============================================================

describe('C6: developer-workstation adminCenter.ts adminCenterAxios baseURL', () => {
  const adminCenterTsPath = path.resolve(DW_API_DIR, 'adminCenter.ts')
  const sourceContent = fs.readFileSync(adminCenterTsPath, 'utf-8')

  test('adminCenterAxios baseURL should be /api/v1/admin (not /api/admin-center)', () => {
    const baseURL = extractBaseURL(sourceContent, 'adminCenterAxios')
    expect(baseURL).not.toBeNull()
    expect(baseURL).toBe('/api/v1/admin')
  })

  test('property: for any API endpoint, requests go through /api/v1/admin route', () => {
    const endpoints = [
      '/virtual-groups',
      '/business-units/tree',
      '/business-units',
      '/roles',
      '/task-assignment/roles/bu-bounded',
      '/task-assignment/roles/bu-unbounded',
    ]

    fc.assert(
      fc.property(fc.constantFrom(...endpoints), (endpoint: string) => {
        const baseURL = extractBaseURL(sourceContent, 'adminCenterAxios')
        expect(baseURL).toBe('/api/v1/admin')

        const fullPath = `${baseURL}${endpoint}`
        expect(fullPath).toMatch(/^\/api\/v1\/admin\//)
        expect(fullPath).not.toMatch(/^\/api\/admin-center\//)
      }),
      { numRuns: 100 }
    )
  })
})
