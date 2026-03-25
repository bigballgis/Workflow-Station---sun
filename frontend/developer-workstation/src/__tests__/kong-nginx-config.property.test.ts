import { describe, test, expect } from 'vitest'
import * as fc from 'fast-check'
import * as fs from 'fs'
import * as path from 'path'

/**
 * Property-Based Tests for Kong Gateway Frontend Configuration
 * Feature: kong-gateway-integration
 *
 * Property 9: Frontend nginx config only proxies to Kong
 * Property 10: Frontend entrypoint and Dockerfile use KONG_PROXY_URL
 */

const FRONTEND_ROOT = path.resolve(__dirname, '..', '..', '..')
const FRONTEND_APPS = ['admin-center', 'developer-workstation', 'user-portal'] as const
type FrontendApp = (typeof FRONTEND_APPS)[number]

/** Legacy backend URL env vars that should NOT appear as proxy targets */
const LEGACY_PROXY_TARGETS = [
  'ADMIN_CENTER_URL',
  'DEVELOPER_WORKSTATION_URL',
  'USER_PORTAL_URL',
]

/**
 * Extract all proxy_pass directives from an nginx config string.
 * Returns an array of the proxy_pass target values.
 */
function extractProxyPassTargets(nginxContent: string): string[] {
  const targets: string[] = []
  const regex = /proxy_pass\s+([^;]+);/g
  let match: RegExpExecArray | null
  while ((match = regex.exec(nginxContent)) !== null) {
    targets.push(match[1].trim())
  }
  return targets
}

/**
 * Extract all location blocks that match /api/ paths from nginx config.
 * Returns the location path and its block content.
 */
function extractApiLocationBlocks(nginxContent: string): Array<{ path: string; block: string }> {
  const blocks: Array<{ path: string; block: string }> = []
  const regex = /location\s+(\/api\/[^\s{]*)\s*\{([^}]*(?:\{[^}]*\}[^}]*)*)\}/g
  let match: RegExpExecArray | null
  while ((match = regex.exec(nginxContent)) !== null) {
    blocks.push({ path: match[1], block: match[2] })
  }
  return blocks
}

// ============================================================
// Property 9: Frontend nginx config only proxies to Kong
// ============================================================

describe('Property 9: Frontend nginx config only proxies to Kong', () => {
  /**
   * For any frontend app (admin-center, developer-workstation, user-portal),
   * all /api/ path proxy_pass targets should point to ${KONG_PROXY_URL},
   * and NO legacy backend URL variables should appear as proxy targets.
   *
   * **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5**
   */
  test.each(FRONTEND_APPS)(
    '%s nginx.conf: all /api/ proxy_pass targets point to KONG_PROXY_URL',
    (appName: FrontendApp) => {
      const nginxPath = path.resolve(FRONTEND_ROOT, appName, 'nginx.conf')
      expect(fs.existsSync(nginxPath), `nginx.conf should exist for ${appName}`).toBe(true)

      const content = fs.readFileSync(nginxPath, 'utf-8')
      const apiBlocks = extractApiLocationBlocks(content)

      // There should be at least one /api/ location block
      expect(apiBlocks.length).toBeGreaterThan(0)

      // Every proxy_pass in /api/ blocks should target KONG_PROXY_URL
      for (const block of apiBlocks) {
        const targets = extractProxyPassTargets(`location ${block.path} {${block.block}}`)
        for (const target of targets) {
          expect(target).toContain('KONG_PROXY_URL')
        }
      }
    },
  )

  test('property: random frontend app nginx.conf has no legacy proxy targets', () => {
    fc.assert(
      fc.property(fc.constantFrom(...FRONTEND_APPS), (appName: FrontendApp) => {
        const nginxPath = path.resolve(FRONTEND_ROOT, appName, 'nginx.conf')
        const content = fs.readFileSync(nginxPath, 'utf-8')
        const allTargets = extractProxyPassTargets(content)

        // No proxy_pass should reference legacy backend URL variables
        for (const target of allTargets) {
          for (const legacyVar of LEGACY_PROXY_TARGETS) {
            expect(target).not.toContain(legacyVar)
          }
        }

        // All proxy_pass targets in /api/ locations should use KONG_PROXY_URL
        const apiBlocks = extractApiLocationBlocks(content)
        for (const block of apiBlocks) {
          const targets = extractProxyPassTargets(`location ${block.path} {${block.block}}`)
          for (const target of targets) {
            expect(target).toContain('KONG_PROXY_URL')
          }
        }
      }),
      { numRuns: 100 },
    )
  })


  test('property: no direct backend service URLs in nginx config', () => {
    fc.assert(
      fc.property(fc.constantFrom(...FRONTEND_APPS), (appName: FrontendApp) => {
        const nginxPath = path.resolve(FRONTEND_ROOT, appName, 'nginx.conf')
        const content = fs.readFileSync(nginxPath, 'utf-8')

        // The entire nginx config should not contain legacy URL variables as proxy targets
        for (const legacyVar of LEGACY_PROXY_TARGETS) {
          // Check that legacy vars don't appear in proxy_pass directives
          const proxyPassWithLegacy = new RegExp(`proxy_pass\\s+.*\\$\\{?${legacyVar}\\}?`, 'g')
          expect(content).not.toMatch(proxyPassWithLegacy)
        }
      }),
      { numRuns: 100 },
    )
  })
})

// ============================================================
// Property 10: Frontend entrypoint and Dockerfile use KONG_PROXY_URL
// ============================================================

describe('Property 10: Frontend entrypoint and Dockerfile use KONG_PROXY_URL', () => {
  /**
   * For any frontend app, the docker-entrypoint.sh should:
   * - Use envsubst with explicit ${KONG_PROXY_URL} variable list
   * - Validate KONG_PROXY_URL environment variable is set
   *
   * And the Dockerfile.local should:
   * - Set default ENV KONG_PROXY_URL=http://kong:8000
   *
   * **Validates: Requirements 7.7, 7.8, 7.9, 11.7**
   */
  test.each(FRONTEND_APPS)(
    '%s docker-entrypoint.sh: envsubst specifies KONG_PROXY_URL',
    (appName: FrontendApp) => {
      const entrypointPath = path.resolve(FRONTEND_ROOT, appName, 'docker-entrypoint.sh')
      expect(fs.existsSync(entrypointPath), `docker-entrypoint.sh should exist for ${appName}`).toBe(true)

      const content = fs.readFileSync(entrypointPath, 'utf-8')

      // envsubst should explicitly specify ${KONG_PROXY_URL} in its variable list
      expect(content).toMatch(/envsubst\s+'[^']*\$\{KONG_PROXY_URL\}[^']*'/)
    },
  )

  test.each(FRONTEND_APPS)(
    '%s docker-entrypoint.sh: validates KONG_PROXY_URL is set',
    (appName: FrontendApp) => {
      const entrypointPath = path.resolve(FRONTEND_ROOT, appName, 'docker-entrypoint.sh')
      const content = fs.readFileSync(entrypointPath, 'utf-8')

      // Script should check if KONG_PROXY_URL is set (e.g., -z "$KONG_PROXY_URL")
      expect(content).toMatch(/\$KONG_PROXY_URL/)
      expect(content).toMatch(/-z\s+"\$KONG_PROXY_URL"/)
    },
  )

  test.each(FRONTEND_APPS)(
    '%s Dockerfile.local: default KONG_PROXY_URL=http://kong:8000',
    (appName: FrontendApp) => {
      const dockerfilePath = path.resolve(FRONTEND_ROOT, appName, 'Dockerfile.local')
      expect(fs.existsSync(dockerfilePath), `Dockerfile.local should exist for ${appName}`).toBe(true)

      const content = fs.readFileSync(dockerfilePath, 'utf-8')

      // Dockerfile should set default KONG_PROXY_URL
      expect(content).toMatch(/ENV\s+KONG_PROXY_URL=http:\/\/kong:8000/)
    },
  )

  test('property: all frontend apps entrypoint and Dockerfile reference KONG_PROXY_URL', () => {
    fc.assert(
      fc.property(fc.constantFrom(...FRONTEND_APPS), (appName: FrontendApp) => {
        const entrypointPath = path.resolve(FRONTEND_ROOT, appName, 'docker-entrypoint.sh')
        const dockerfilePath = path.resolve(FRONTEND_ROOT, appName, 'Dockerfile.local')

        const entrypoint = fs.readFileSync(entrypointPath, 'utf-8')
        const dockerfile = fs.readFileSync(dockerfilePath, 'utf-8')

        // Entrypoint: envsubst explicitly lists KONG_PROXY_URL
        const envsubstMatch = entrypoint.match(/envsubst\s+'([^']*)'/)
        expect(envsubstMatch).not.toBeNull()
        expect(envsubstMatch![1]).toContain('${KONG_PROXY_URL}')

        // Entrypoint: validates KONG_PROXY_URL
        expect(entrypoint).toContain('KONG_PROXY_URL')
        expect(entrypoint).toMatch(/-z\s+"\$KONG_PROXY_URL"/)

        // Dockerfile: default env KONG_PROXY_URL=http://kong:8000
        expect(dockerfile).toMatch(/ENV\s+KONG_PROXY_URL=http:\/\/kong:8000/)

        // Entrypoint should NOT reference legacy URL variables for validation
        for (const legacyVar of LEGACY_PROXY_TARGETS) {
          expect(entrypoint).not.toMatch(new RegExp(`-z\\s+"\\$${legacyVar}"`))
        }
      }),
      { numRuns: 100 },
    )
  })
})
