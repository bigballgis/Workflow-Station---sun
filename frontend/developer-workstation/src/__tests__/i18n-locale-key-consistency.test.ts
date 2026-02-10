import { describe, it, expect } from 'vitest'
import * as fs from 'fs'
import * as path from 'path'

/**
 * Property-Based Test for Frontend I18n Hardcoded Chinese Feature
 * Feature: frontend-i18n-hardcoded-chinese
 *
 * Property 3: Locale 文件键一致性
 * Parses the locale files from both admin-center and developer-workstation,
 * recursively extracts all keys from each locale object (flattened dot-notation keys),
 * and verifies that en.ts, zh-CN.ts, and zh-TW.ts have exactly the same set of keys
 * for each project.
 *
 * **Validates: Requirements 9.1, 9.2**
 */

// Root paths for the two frontend projects (relative to this test file)
const FRONTEND_ROOT = path.resolve(__dirname, '..', '..', '..')

/**
 * Parse a TypeScript locale file and extract the default export object.
 * The locale files have a structure like:
 * ```
 * export default {
 *   key1: 'value',
 *   namespace: {
 *     key2: 'value',
 *   },
 * }
 * ```
 *
 * We strip the `export default` wrapper and evaluate the object literal
 * using `new Function` to get the actual object.
 */
function parseLocaleFile(filePath: string): Record<string, unknown> {
  const content = fs.readFileSync(filePath, 'utf-8')

  // Remove 'export default' and get the object literal
  const match = content.match(/export\s+default\s+(\{[\s\S]*\})\s*;?\s*$/)
  if (!match) {
    throw new Error(`Could not parse locale file: ${filePath}`)
  }

  const objectLiteral = match[1]

  try {
    const fn = new Function(`return (${objectLiteral})`)
    return fn() as Record<string, unknown>
  } catch (e) {
    throw new Error(`Failed to evaluate locale object from ${filePath}: ${e}`)
  }
}

/**
 * Recursively extract all keys from a nested object, returning them as
 * flattened dot-notation strings.
 *
 * Example:
 *   { a: { b: 'val', c: 'val' }, d: 'val' }
 *   => ['a.b', 'a.c', 'd']
 */
function extractKeys(obj: Record<string, unknown>, prefix = ''): string[] {
  const keys: string[] = []
  for (const key of Object.keys(obj)) {
    const fullKey = prefix ? `${prefix}.${key}` : key
    const value = obj[key]
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...extractKeys(value as Record<string, unknown>, fullKey))
    } else {
      keys.push(fullKey)
    }
  }
  return keys.sort()
}

/**
 * Compute the difference between two sorted key arrays.
 * Returns keys present in `a` but missing from `b`.
 */
function missingKeys(a: string[], b: string[]): string[] {
  const setB = new Set(b)
  return a.filter((k) => !setB.has(k))
}

interface LocaleProject {
  name: string
  enPath: string
  zhCNPath: string
  zhTWPath: string
}

const PROJECTS: LocaleProject[] = [
  {
    name: 'admin-center',
    enPath: path.join(FRONTEND_ROOT, 'admin-center', 'src', 'i18n', 'locales', 'en.ts'),
    zhCNPath: path.join(FRONTEND_ROOT, 'admin-center', 'src', 'i18n', 'locales', 'zh-CN.ts'),
    zhTWPath: path.join(FRONTEND_ROOT, 'admin-center', 'src', 'i18n', 'locales', 'zh-TW.ts'),
  },
  {
    name: 'developer-workstation',
    enPath: path.join(FRONTEND_ROOT, 'developer-workstation', 'src', 'i18n', 'locales', 'en.ts'),
    zhCNPath: path.join(FRONTEND_ROOT, 'developer-workstation', 'src', 'i18n', 'locales', 'zh-CN.ts'),
    zhTWPath: path.join(FRONTEND_ROOT, 'developer-workstation', 'src', 'i18n', 'locales', 'zh-TW.ts'),
  },
]

describe('Property 3: Locale 文件键一致性', () => {
  /**
   * Property 3: Locale file key consistency
   *
   * For any i18n key, if that key exists in any one of en.ts, zh-CN.ts, zh-TW.ts,
   * then it must also exist in the other two files with a non-empty value.
   *
   * **Validates: Requirements 9.1, 9.2**
   */
  for (const project of PROJECTS) {
    it(`${project.name}: en.ts, zh-CN.ts, and zh-TW.ts should have exactly the same set of keys`, () => {
      // Verify all locale files exist
      expect(fs.existsSync(project.enPath), `en.ts should exist for ${project.name}`).toBe(true)
      expect(fs.existsSync(project.zhCNPath), `zh-CN.ts should exist for ${project.name}`).toBe(true)
      expect(fs.existsSync(project.zhTWPath), `zh-TW.ts should exist for ${project.name}`).toBe(true)

      // Parse locale files
      const enObj = parseLocaleFile(project.enPath)
      const zhCNObj = parseLocaleFile(project.zhCNPath)
      const zhTWObj = parseLocaleFile(project.zhTWPath)

      // Extract flattened keys
      const enKeys = extractKeys(enObj)
      const zhCNKeys = extractKeys(zhCNObj)
      const zhTWKeys = extractKeys(zhTWObj)

      const issues: string[] = []

      // Check keys in en but missing from zh-CN
      const missingInZhCN = missingKeys(enKeys, zhCNKeys)
      if (missingInZhCN.length > 0) {
        issues.push(`Keys in en.ts but missing from zh-CN.ts (${missingInZhCN.length}):\n${missingInZhCN.map((k) => `    - ${k}`).join('\n')}`)
      }

      // Check keys in en but missing from zh-TW
      const missingInZhTW = missingKeys(enKeys, zhTWKeys)
      if (missingInZhTW.length > 0) {
        issues.push(`Keys in en.ts but missing from zh-TW.ts (${missingInZhTW.length}):\n${missingInZhTW.map((k) => `    - ${k}`).join('\n')}`)
      }

      // Check keys in zh-CN but missing from en
      const missingInEnFromCN = missingKeys(zhCNKeys, enKeys)
      if (missingInEnFromCN.length > 0) {
        issues.push(`Keys in zh-CN.ts but missing from en.ts (${missingInEnFromCN.length}):\n${missingInEnFromCN.map((k) => `    - ${k}`).join('\n')}`)
      }

      // Check keys in zh-TW but missing from en
      const missingInEnFromTW = missingKeys(zhTWKeys, enKeys)
      if (missingInEnFromTW.length > 0) {
        issues.push(`Keys in zh-TW.ts but missing from en.ts (${missingInEnFromTW.length}):\n${missingInEnFromTW.map((k) => `    - ${k}`).join('\n')}`)
      }

      // Check keys in zh-CN but missing from zh-TW
      const missingInTWFromCN = missingKeys(zhCNKeys, zhTWKeys)
      if (missingInTWFromCN.length > 0) {
        issues.push(`Keys in zh-CN.ts but missing from zh-TW.ts (${missingInTWFromCN.length}):\n${missingInTWFromCN.map((k) => `    - ${k}`).join('\n')}`)
      }

      // Check keys in zh-TW but missing from zh-CN
      const missingInCNFromTW = missingKeys(zhTWKeys, zhCNKeys)
      if (missingInCNFromTW.length > 0) {
        issues.push(`Keys in zh-TW.ts but missing from zh-CN.ts (${missingInCNFromTW.length}):\n${missingInCNFromTW.map((k) => `    - ${k}`).join('\n')}`)
      }

      if (issues.length > 0) {
        expect.fail(
          `[${project.name}] Locale key inconsistencies found:\n\n${issues.join('\n\n')}`
        )
      }

      // Verify all three files have the same number of keys
      expect(enKeys.length).toBe(zhCNKeys.length)
      expect(enKeys.length).toBe(zhTWKeys.length)
    })
  }
})
