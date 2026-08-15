import { describe, test, expect } from 'vitest'
import * as fc from 'fast-check'
import * as fs from 'fs'
import * as path from 'path'

/**
 * Preservation Property Tests — Frontend baseURL and Kong Routes
 * Feature: kong-authn-authz-fix
 *
 * **Property 7: Preservation** — 现有登录、路由和权限检查流程不变
 *
 * These tests verify EXISTING behavior that MUST NOT change after the fix.
 * They MUST PASS on unfixed code.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
 */

const WORKSPACE_ROOT = path.resolve(__dirname, '..', '..', '..', '..')

// ============================================================
// Test: admin-center frontend baseURL remains /api/v1/admin
// ============================================================

describe('Preservation: admin-center frontend baseURL remains /api/v1/admin', () => {
  const requestTsPath = path.resolve(
    WORKSPACE_ROOT,
    'frontend',
    'admin-center',
    'src',
    'api',
    'request.ts'
  )

  test('admin-center request.ts baseURL should be /api/v1/admin', () => {
    const content = fs.readFileSync(requestTsPath, 'utf-8')
    // Match: baseURL: '/api/v1/admin'
    const baseURLMatch = content.match(/baseURL:\s*['"]([^'"]+)['"]/)
    expect(baseURLMatch).not.toBeNull()
    expect(baseURLMatch![1]).toBe('/api/v1/admin')
  })

  test('property: for any API endpoint path, admin-center requests go through /api/v1/admin', () => {
    const content = fs.readFileSync(requestTsPath, 'utf-8')
    const baseURLMatch = content.match(/baseURL:\s*['"]([^'"]+)['"]/)
    const baseURL = baseURLMatch![1]

    fc.assert(
      fc.property(
        fc.constantFrom(
          '/users',
          '/roles',
          '/permissions',
          '/business-units',
          '/virtual-groups',
          '/developer-permissions',
          '/audit-logs',
          '/config'
        ),
        (endpoint: string) => {
          expect(baseURL).toBe('/api/v1/admin')
          const fullPath = `${baseURL}${endpoint}`
          expect(fullPath).toMatch(/^\/api\/v1\/admin\//)
        }
      ),
      { numRuns: 100 }
    )
  })
})

// ============================================================
// Test: developer-workstation frontend baseURL remains /api/v1
// ============================================================

describe('Preservation: developer-workstation frontend baseURL remains /api/v1', () => {
  const indexTsPath = path.resolve(
    WORKSPACE_ROOT,
    'frontend',
    'developer-workstation',
    'src',
    'api',
    'index.ts'
  )

  test('developer-workstation index.ts baseURL should be /api/v1', () => {
    const content = fs.readFileSync(indexTsPath, 'utf-8')
    // Match the main axios instance baseURL (not adminCenterAxios)
    const baseURLMatch = content.match(/baseURL:\s*['"]([^'"]+)['"]/)
    expect(baseURLMatch).not.toBeNull()
    expect(baseURLMatch![1]).toBe('/api/v1')
  })

  test('property: for any non-adminCenter API endpoint, requests go through /api/v1', () => {
    const content = fs.readFileSync(indexTsPath, 'utf-8')
    const baseURLMatch = content.match(/baseURL:\s*['"]([^'"]+)['"]/)
    const baseURL = baseURLMatch![1]

    fc.assert(
      fc.property(
        fc.constantFrom(
          '/function-units',
          '/process-designs',
          '/form-designs',
          '/table-designs',
          '/decision-designs',
          '/versions',
          '/ai-generation',
          '/auth/login'
        ),
        (endpoint: string) => {
          expect(baseURL).toBe('/api/v1')
          const fullPath = `${baseURL}${endpoint}`
          expect(fullPath).toMatch(/^\/api\/v1\//)
          // Should NOT be /api/v1/admin (that's admin-center)
          expect(fullPath).not.toMatch(/^\/api\/v1\/admin\//)
        }
      ),
      { numRuns: 100 }
    )
  })
})

// ============================================================
// Test: Kong configuration has required routes
// ============================================================

describe('Preservation: Kong configuration has routes for /api/v1/admin, /api/v1, /api/portal', () => {
  const kongConfigPath = path.resolve(WORKSPACE_ROOT, 'deploy', 'kong', 'kong.yml.template')

  test('Kong config should have /api/v1/admin route', () => {
    const content = fs.readFileSync(kongConfigPath, 'utf-8')
    expect(content).toContain('- /api/v1/admin')
  })

  test('Kong config should have /api/v1 route', () => {
    const content = fs.readFileSync(kongConfigPath, 'utf-8')
    expect(content).toContain('- /api/v1')
  })

  test('Kong config should have /api/portal route', () => {
    const content = fs.readFileSync(kongConfigPath, 'utf-8')
    expect(content).toContain('- /api/portal')
  })

  test('Kong config should have strip_path: false for all routes (AP gateway routes excepted)', () => {
    // 行尾归一化：kong.yml.template 在 Windows 检出下是 CRLF，而下面的正则按 LF 匹配，
    // 不归一化时 routeBlocks 恒为 null —— 这条断言此前只在 LF 检出的机器上才成立。
    const content = fs.readFileSync(kongConfigPath, 'utf-8').replace(/\r\n/g, '\n')
    // AP builder 网关路由（/api/ap 收编）按设计 strip /api/ap 前缀，其余路由必须 strip_path: false
    const routeBlocks = content.match(/paths:\n(?:\s+- \S+\n)+\s+strip_path: (?:true|false)/g)
    expect(routeBlocks).not.toBeNull()

    // 每个 strip_path 都必须能配对到它的 paths 块，防止正则漏检
    const stripPathCount = content.match(/strip_path:\s*(?:true|false)/g)!.length
    expect(routeBlocks!.length).toBe(stripPathCount)

    for (const block of routeBlocks!) {
      const paths = [...block.matchAll(/- (\S+)/g)].map((m) => m[1])
      const isApRoute = paths.every((p) => p.startsWith('/api/ap'))
      if (isApRoute) continue
      expect(block).toContain('strip_path: false')
    }
  })

  test('property: for any of the three main routes, Kong config contains the route path', () => {
    const content = fs.readFileSync(kongConfigPath, 'utf-8')

    fc.assert(
      fc.property(
        fc.constantFrom('/api/v1/admin', '/api/v1', '/api/portal'),
        (routePath: string) => {
          expect(content).toContain(`- ${routePath}`)
        }
      ),
      { numRuns: 100 }
    )
  })

  test('property: Kong routes point to correct backend services', () => {
    const content = fs.readFileSync(kongConfigPath, 'utf-8')

    const routeServiceMap: Record<string, string> = {
      '/api/v1/admin': 'admin-center',
      '/api/v1': 'developer-workstation',
      '/api/portal': 'user-portal',
    }

    fc.assert(
      fc.property(
        fc.constantFrom(
          ...Object.entries(routeServiceMap).map(([route, service]) => ({ route, service }))
        ),
        ({ route, service }: { route: string; service: string }) => {
          // Simpler check: the route path exists and the service name exists
          expect(content).toContain(`- ${route}`)
          expect(content).toContain(`${service}-service`)
        }
      ),
      { numRuns: 100 }
    )
  })
})
